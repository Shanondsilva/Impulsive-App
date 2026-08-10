package com.impulsive.app.backend.session.game

import android.content.Context
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.boundedDurationMillis
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult
import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCoordinator
import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleDependencies
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SupportCycleGameTerminalOutcome {
    Completed,
    Failed,
    Abandoned,
    TimedOut,
}

data class SupportCycleResolvedStep(
    val outcome: SupportCycleGameTerminalOutcome,
    val elapsedDurationMillis: Long,
)

sealed interface SupportCycleGameBindResult {
    data object Standalone : SupportCycleGameBindResult

    data class Bound(
        val launch: RecoveryGameLaunchContext.SupportCycle,
        val resolvedStep: SupportCycleResolvedStep? = null,
    ) : SupportCycleGameBindResult

    data object UnavailableSupportCycle : SupportCycleGameBindResult
}

sealed interface SupportCycleGameReportResult {
    data class Reported(val result: AdaptiveSupportCycleCommandResult) :
        SupportCycleGameReportResult

    data object IgnoredStandalone : SupportCycleGameReportResult

    /**
     * The same authoritative outcome was already processed.
     * This is safe and idempotent.
     */
    data object Duplicate : SupportCycleGameReportResult

    /**
     * A caller attempted to replace an already-resolved step outcome with a
     * different outcome.
     *
     * This must fail closed because the active cycle has not been finished.
     */
    data object OutcomeConflict : SupportCycleGameReportResult
}

val SupportCycleGameReportResult.allowsContinuation: Boolean
    get() = when (this) {
        is SupportCycleGameReportResult.Reported ->
            result is AdaptiveSupportCycleCommandResult.Active
        SupportCycleGameReportResult.IgnoredStandalone,
        SupportCycleGameReportResult.Duplicate -> true
        SupportCycleGameReportResult.OutcomeConflict -> false
    }

val SupportCycleGameReportResult.allowsExit: Boolean
    get() = when (this) {
        is SupportCycleGameReportResult.Reported ->
            result is AdaptiveSupportCycleCommandResult.Terminal ||
                result == AdaptiveSupportCycleCommandResult.NotFound
        SupportCycleGameReportResult.IgnoredStandalone,
        SupportCycleGameReportResult.Duplicate -> true
        SupportCycleGameReportResult.OutcomeConflict -> false
    }

/**
 * Whether a valid result restored from SavedStateHandle should remain visible
 * after attempting to persist its previously interrupted terminal outcome.
 */
val SupportCycleGameReportResult.keepsRestoredResultVisible: Boolean
    get() = when {
        allowsContinuation -> true

        /*
         * Terminal and NotFound are successful exit-safe outcomes. The
         * result remains visible until the user chooses the final action.
         */
        allowsExit -> true

        /*
         * These failures are explicitly retryable. Preserve both the
         * Result UI state and SavedStateHandle snapshot so another user
         * action or process recreation can retry the same outcome.
         */
        this is SupportCycleGameReportResult.Reported &&
            (
                result == AdaptiveSupportCycleCommandResult.PersistenceFailure ||
                    result == AdaptiveSupportCycleCommandResult.RevisionConflict
            ) -> true

        /*
         * OutcomeConflict and other non-retryable command failures remain
         * fail-closed.
         */
        else -> false
    }

/**
 * Keeps game ViewModels ignorant of persistence while validating every support-cycle reference
 * against the coordinator's authoritative state. A bridge instance belongs to one game session.
 */
class RecoveryGameSupportCycleSessionBridge(
    private val coordinator: AdaptiveSupportCycleCoordinator,
    private val terminalSink: (suspend (String, SupportCycleGameTerminalOutcome) -> Unit)? = null,
) {
    private val mutex = Mutex()
    private var boundLaunch: RecoveryGameLaunchContext.SupportCycle? = null
    private var resolvedOutcome: SupportCycleGameTerminalOutcome? = null

    /**
     * Outcome selected by a report whose authoritative repository transition has
     * not yet succeeded.
     *
     * Retrying the same outcome is allowed. Replacing it with a different outcome
     * fails closed.
     */
    private var pendingOutcome: SupportCycleGameTerminalOutcome? = null
    private var terminalSinkReported = false

    suspend fun bind(
        launchContext: RecoveryGameLaunchContext,
    ): SupportCycleGameBindResult = mutex.withLock {
        resolvedOutcome = null
        pendingOutcome = null
        terminalSinkReported = false
        if (launchContext === RecoveryGameLaunchContext.Standalone) {
            boundLaunch = null
            return@withLock SupportCycleGameBindResult.Standalone
        }

        val requested = launchContext as RecoveryGameLaunchContext.SupportCycle
        val recovered = coordinator.recover() as? AdaptiveSupportCycleCommandResult.Active
            ?: run {
                boundLaunch = null
                return@withLock SupportCycleGameBindResult.UnavailableSupportCycle
            }
        val cycle = recovered.state.cycle
        val step = cycle.currentStep
        if (
            cycle.cycleId != requested.cycleId ||
            cycle.decisionId != requested.decisionId ||
            step == null ||
            step.gameType != requested.gameType ||
            step.outcome == AdaptiveSupportStepOutcome.Cancelled
        ) {
            boundLaunch = null
            return@withLock SupportCycleGameBindResult.UnavailableSupportCycle
        }

        val restoredOutcome = step.outcome.toGameTerminalOutcomeOrNull()
        val authoritativeDurationMillis = if (restoredOutcome == null) {
            minOf(
                requested.maxDurationMillis,
                step.remainingDurationMillis,
                cycle.remainingDurationMillis,
            )
        } else {
            minOf(
                requested.maxDurationMillis,
                cycle.remainingDurationMillis,
            )
        }
        if (authoritativeDurationMillis <= 0L) {
            boundLaunch = null
            return@withLock SupportCycleGameBindResult.UnavailableSupportCycle
        }

        val authoritative = requested.copy(
            maxDurationMillis = authoritativeDurationMillis,
        )
        boundLaunch = authoritative
        resolvedOutcome = restoredOutcome
        SupportCycleGameBindResult.Bound(
            launch = authoritative,
            resolvedStep = restoredOutcome?.let { outcome ->
                SupportCycleResolvedStep(
                    outcome = outcome,
                    elapsedDurationMillis = step.consumedDurationMillis,
                )
            },
        )
    }

    suspend fun report(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
        endCycle: Boolean,
    ): SupportCycleGameReportResult = mutex.withLock {
        require(elapsedDurationMillis >= 0L)

        val launch = boundLaunch
            ?: return@withLock SupportCycleGameReportResult.IgnoredStandalone

        val previousOutcome = resolvedOutcome

        /*
         * Duplicate is permitted only after an authoritative transition was
         * successfully accepted and persisted.
         */
        if (previousOutcome != null) {
            if (previousOutcome != outcome) {
                return@withLock SupportCycleGameReportResult.OutcomeConflict
            }

            if (!endCycle) {
                return@withLock SupportCycleGameReportResult.Duplicate
            }

            if (terminalSinkReported) {
                return@withLock SupportCycleGameReportResult.Duplicate
            }

            /*
             * The step was already resolved for continuation. Finishing the
             * active cycle remains separately retryable if persistence fails.
             */
            val result = coordinator.finishCycleAfterResolvedStep(
                cycleId = launch.cycleId,
                terminalStatus = previousOutcome.terminalStatus(),
            )

            notifyTerminalOnce(
                decisionId = launch.decisionId,
                outcome = previousOutcome,
                result = result,
            )

            return@withLock SupportCycleGameReportResult.Reported(result)
        }

        val pending = pendingOutcome

        if (pending != null && pending != outcome) {
            return@withLock SupportCycleGameReportResult.OutcomeConflict
        }

        /*
         * This is a retry marker, not a successful-resolution marker.
         */
        pendingOutcome = outcome

        val elapsed = elapsedDurationMillis.coerceAtMost(launch.maxDurationMillis)

        val result = coordinator.recordElapsedAndResolveGameStep(
            cycleId = launch.cycleId,
            elapsedDurationMillis = elapsed,
            requestedOutcome = outcome.toAdaptiveStepOutcome(),
            endCycle = endCycle,
        )

        /*
         * Only Active and Terminal results prove that the combined elapsed and
         * step transition was accepted by the authoritative coordinator.
         *
         * RevisionConflict, PersistenceFailure, Rejected, CycleMismatch and
         * other failures leave pendingOutcome in place so the same operation
         * can be retried.
         */
        val authoritativeOutcome = result.authoritativeResolvedGameOutcomeOrNull()

        if (authoritativeOutcome != null) {
            resolvedOutcome = authoritativeOutcome
            pendingOutcome = null
        } else if (result == AdaptiveSupportCycleCommandResult.NotFound) {
            /*
             * No authoritative cycle remains. There is nothing left to retry.
             * NotFound still follows the existing allowsExit policy.
             */
            pendingOutcome = null
        }

        if (result is AdaptiveSupportCycleCommandResult.Terminal) {
            notifyTerminalOnce(
                decisionId = launch.decisionId,
                outcome = authoritativeOutcome ?: outcome,
                result = result,
            )
        }

        SupportCycleGameReportResult.Reported(result)
    }

    suspend fun resolveForContinuation(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): SupportCycleGameReportResult = report(outcome, elapsedDurationMillis, endCycle = false)

    suspend fun resolveAndEnd(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): SupportCycleGameReportResult = report(outcome, elapsedDurationMillis, endCycle = true)

    private suspend fun notifyTerminalOnce(
        decisionId: String,
        outcome: SupportCycleGameTerminalOutcome,
        result: AdaptiveSupportCycleCommandResult,
    ) {
        if (result !is AdaptiveSupportCycleCommandResult.Terminal || terminalSinkReported) return
        terminalSinkReported = true
        terminalSink?.invoke(decisionId, outcome)
    }

    private fun AdaptiveSupportStepOutcome.toGameTerminalOutcomeOrNull():
        SupportCycleGameTerminalOutcome? =
        when (this) {
            AdaptiveSupportStepOutcome.InProgress -> null
            AdaptiveSupportStepOutcome.Completed ->
                SupportCycleGameTerminalOutcome.Completed
            AdaptiveSupportStepOutcome.Failed ->
                SupportCycleGameTerminalOutcome.Failed
            AdaptiveSupportStepOutcome.Abandoned ->
                SupportCycleGameTerminalOutcome.Abandoned
            AdaptiveSupportStepOutcome.TimedOut ->
                SupportCycleGameTerminalOutcome.TimedOut
            AdaptiveSupportStepOutcome.Cancelled -> null
        }

    private fun SupportCycleGameTerminalOutcome.terminalStatus(): AdaptiveSupportCycleStatus =
        when (this) {
            SupportCycleGameTerminalOutcome.Completed -> AdaptiveSupportCycleStatus.Completed
            SupportCycleGameTerminalOutcome.Failed,
            SupportCycleGameTerminalOutcome.TimedOut -> AdaptiveSupportCycleStatus.Failed
            SupportCycleGameTerminalOutcome.Abandoned -> AdaptiveSupportCycleStatus.Abandoned
        }

    private fun SupportCycleGameTerminalOutcome.toAdaptiveStepOutcome(): AdaptiveSupportStepOutcome =
        when (this) {
            SupportCycleGameTerminalOutcome.Completed -> AdaptiveSupportStepOutcome.Completed
            SupportCycleGameTerminalOutcome.Failed -> AdaptiveSupportStepOutcome.Failed
            SupportCycleGameTerminalOutcome.Abandoned -> AdaptiveSupportStepOutcome.Abandoned
            SupportCycleGameTerminalOutcome.TimedOut -> AdaptiveSupportStepOutcome.TimedOut
        }

    private fun AdaptiveSupportCycleCommandResult.authoritativeResolvedGameOutcomeOrNull():
        SupportCycleGameTerminalOutcome? {
        val stepOutcome = when (this) {
            is AdaptiveSupportCycleCommandResult.Active -> state.cycle.currentStep?.outcome
            is AdaptiveSupportCycleCommandResult.Terminal -> cycle.currentStep?.outcome
            else -> null
        }

        return stepOutcome?.toGameTerminalOutcomeOrNull()
    }
}

data class RecoveryGameSupportCycleBinding(
    val durationMillis: Long,
    val resolvedStep: SupportCycleResolvedStep? = null,
)

/** Android-facing convenience owned by a game ViewModel; repository access remains in the bridge. */
class RecoveryGameSupportCycleRuntime private constructor(
    private val coordinator: AdaptiveSupportCycleCoordinator,
    terminalSink: (suspend (String, SupportCycleGameTerminalOutcome) -> Unit)?,
) {
    private val bridge = RecoveryGameSupportCycleSessionBridge(
        coordinator = coordinator,
        terminalSink = terminalSink,
    )

    constructor(context: Context) : this(
        coordinator = AdaptiveSupportCycleDependencies.coordinator(
            context.applicationContext,
        ),
        terminalSink = { decisionId, outcome ->
            com.impulsive.app.backend.session.adaptive
                .AdaptiveSupportCycleDecisionOutcomeCoordinator(
                    com.impulsive.app.backend.session.adaptive
                        .AdaptivePhase4Dependencies
                        .outcomeCoordinator(context.applicationContext),
                ).recordTerminal(decisionId, outcome)
        },
    )

    internal constructor(
        coordinator: AdaptiveSupportCycleCoordinator,
    ) : this(
        coordinator = coordinator,
        terminalSink = null,
    )

    @Volatile
    private var activeContext: RecoveryGameLaunchContext =
        RecoveryGameLaunchContext.Standalone

    private val actionMutex = Mutex()

    /**
     * Returns authoritative binding information for both an active game and a
     * terminal game-result step restored after process recreation.
     */
    suspend fun bindWithRecovery(
        requested: RecoveryGameLaunchContext,
        standaloneDurationMillis: Long,
    ): RecoveryGameSupportCycleBinding? {
        val result = bridge.bind(requested)

        activeContext = when (result) {
            SupportCycleGameBindResult.Standalone ->
                RecoveryGameLaunchContext.Standalone

            SupportCycleGameBindResult.UnavailableSupportCycle ->
                requested

            is SupportCycleGameBindResult.Bound ->
                result.launch
        }

        return when (result) {
            SupportCycleGameBindResult.UnavailableSupportCycle ->
                null

            SupportCycleGameBindResult.Standalone ->
                RecoveryGameSupportCycleBinding(
                    durationMillis =
                        RecoveryGameLaunchContext.Standalone
                            .boundedDurationMillis(standaloneDurationMillis),
                )

            is SupportCycleGameBindResult.Bound ->
                RecoveryGameSupportCycleBinding(
                    durationMillis =
                        result.launch.boundedDurationMillis(
                            standaloneDurationMillis,
                        ),
                    resolvedStep = result.resolvedStep,
                )
        }
    }

    /**
     * Temporary compatibility method for game ViewModels that have not yet
     * implemented result-screen restoration.
     *
     * A restored terminal result deliberately returns null here. This keeps
     * old ViewModels from presenting a false Ready game state while the
     * SavedStateHandle restoration pieces are introduced.
     */
    suspend fun bind(
        requested: RecoveryGameLaunchContext,
        standaloneDurationMillis: Long,
    ): Long? {
        val binding = bindWithRecovery(
            requested = requested,
            standaloneDurationMillis = standaloneDurationMillis,
        ) ?: return null

        if (binding.resolvedStep != null) return null

        return binding.durationMillis
    }

    fun isSupportCycle(): Boolean =
        activeContext is RecoveryGameLaunchContext.SupportCycle

    suspend fun resolveForContinuation(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): SupportCycleGameReportResult =
        bridge.resolveForContinuation(
            outcome,
            elapsedDurationMillis,
        )

    suspend fun resolveAndEnd(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): SupportCycleGameReportResult =
        bridge.resolveAndEnd(
            outcome,
            elapsedDurationMillis,
        )

    suspend fun prepareReplay(
        standaloneDurationMillis: Long,
    ): Long? = actionMutex.withLock {
        val current = activeContext

        if (current === RecoveryGameLaunchContext.Standalone) {
            return@withLock standaloneDurationMillis
        }

        val support = current as RecoveryGameLaunchContext.SupportCycle

        val recovered =
            coordinator.recover() as? AdaptiveSupportCycleCommandResult.Active
                ?: return@withLock null

        if (
            recovered.state.cycle.cycleId != support.cycleId ||
            recovered.state.cycle.decisionId != support.decisionId ||
            recovered.state.cycle.currentStep?.outcome?.isTerminal != true
        ) {
            return@withLock null
        }

        val started = coordinator.startGame(
            cycleId = support.cycleId,
            gameType = support.gameType,
            requestedDurationMillis = standaloneDurationMillis,
            minimumUsefulDurationMillis =
                AdaptiveSupportCycleCoordinator
                    .MinimumUsefulStepDurationMillis,
        ) as? com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready
            ?: return@withLock null

        val rebound =
            bridge.bind(started.launch) as? SupportCycleGameBindResult.Bound
                ?: return@withLock null

        if (rebound.resolvedStep != null) return@withLock null

        activeContext = rebound.launch
        rebound.launch.maxDurationMillis
    }
}

sealed interface RecoveryGameHandOffResult {
    data class Standalone(val gameType: ScoreGameType) : RecoveryGameHandOffResult
    data class Ready(val launch: RecoveryGameLaunchContext.SupportCycle) : RecoveryGameHandOffResult
    data object Unavailable : RecoveryGameHandOffResult
}

/** Suspend navigation helper: starts the authoritative next step before navigation. */
class RecoveryGameSupportCycleHandOff(
    private val coordinator: AdaptiveSupportCycleCoordinator,
) {
    private val mutex = Mutex()

    suspend fun prepareNext(
        currentLaunch: RecoveryGameLaunchContext,
        nextGame: ScoreGameType,
    ): RecoveryGameHandOffResult = mutex.withLock {
        if (currentLaunch === RecoveryGameLaunchContext.Standalone) {
            return@withLock RecoveryGameHandOffResult.Standalone(nextGame)
        }
        val support = currentLaunch as RecoveryGameLaunchContext.SupportCycle
        val recovered = coordinator.recover() as? AdaptiveSupportCycleCommandResult.Active
            ?: return@withLock RecoveryGameHandOffResult.Unavailable
        val cycle = recovered.state.cycle
        if (
            cycle.cycleId != support.cycleId ||
            cycle.decisionId != support.decisionId ||
            cycle.currentStep?.outcome?.isTerminal != true
        ) return@withLock RecoveryGameHandOffResult.Unavailable
        val started = coordinator.startGame(
            cycleId = support.cycleId,
            gameType = nextGame,
            requestedDurationMillis = AdaptiveSupportCycleCoordinator.DefaultCycleDurationMillis,
            minimumUsefulDurationMillis = AdaptiveSupportCycleCoordinator.MinimumUsefulStepDurationMillis,
        ) as? com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
            ?: return@withLock RecoveryGameHandOffResult.Unavailable
        if (started.launch.decisionId != support.decisionId) {
            return@withLock RecoveryGameHandOffResult.Unavailable
        }
        RecoveryGameHandOffResult.Ready(started.launch)
    }
}
