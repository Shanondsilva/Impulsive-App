package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleStepResolution
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTimePolicy
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionPolicy
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionResult
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTiming
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import java.util.UUID
import kotlinx.coroutines.CancellationException

sealed interface AdaptiveSupportCycleCommandResult {
    data class Active(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleCommandResult

    data class ExistingActive(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleCommandResult

    /**
     * Another adaptive decision already owns the single active support cycle.
     *
     * The complete authoritative state is retained so callers can resume the
     * existing lifecycle instead of repeatedly returning to the conflicting new
     * decision.
     */
    data class ActiveDecisionConflict(
        val state: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportCycleCommandResult {
        val existingCycleId: String
            get() = state.cycle.cycleId

        val existingDecisionId: String
            get() = state.cycle.decisionId
    }

    data class Terminal(val cycle: AdaptiveSupportCycle) :
        AdaptiveSupportCycleCommandResult

    data class Rejected(
        val reason: com.impulsive.app.backend.domain.engine.adaptive
            .AdaptiveSupportCycleTransitionRejection,
    ) : AdaptiveSupportCycleCommandResult

    data object NotFound : AdaptiveSupportCycleCommandResult
    data object CycleMismatch : AdaptiveSupportCycleCommandResult
    data object RevisionConflict : AdaptiveSupportCycleCommandResult
    data object InvalidPersistedState : AdaptiveSupportCycleCommandResult
    data object Expired : AdaptiveSupportCycleCommandResult
    data object PersistenceFailure : AdaptiveSupportCycleCommandResult
}

/**
 * Outcome of transferring the current decision ownership of one already-active
 * support cycle to an explicit same-Moment follow-up decision.
 *
 * This is deliberately separate from [AdaptiveSupportCycleCommandResult] so that
 * ownership-validation semantics never leak into ordinary step transitions.
 */
internal sealed interface AdaptiveSupportCycleDecisionHandoffResult {
    /** Ownership moved from the previous decision to the follow-up decision. */
    data class Transferred(
        val state: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportCycleDecisionHandoffResult

    /** The cycle is already owned by the follow-up decision; nothing was written. */
    data class AlreadyTransferred(
        val state: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportCycleDecisionHandoffResult

    /** The supplied decision pair is not a valid same-Moment follow-up lineage. */
    data object InvalidDecisionLineage : AdaptiveSupportCycleDecisionHandoffResult

    /** The cycle has no current step, or its current step is still in progress. */
    data object CurrentStepNotResolved : AdaptiveSupportCycleDecisionHandoffResult

    /** The cycle is owned by some third decision and must not be stolen. */
    data object OwnerMismatch : AdaptiveSupportCycleDecisionHandoffResult

    data object NotFound : AdaptiveSupportCycleDecisionHandoffResult
    data object CycleMismatch : AdaptiveSupportCycleDecisionHandoffResult
    data object RevisionConflict : AdaptiveSupportCycleDecisionHandoffResult
    data object InvalidPersistedState : AdaptiveSupportCycleDecisionHandoffResult
    data object Expired : AdaptiveSupportCycleDecisionHandoffResult
    data object PersistenceFailure : AdaptiveSupportCycleDecisionHandoffResult
}

/**
 * Outcome of the owner-guarded preparation half of one "choose another support"
 * operation.
 *
 * This is deliberately separate from [AdaptiveSupportCycleCommandResult] because
 * it carries two facts an ordinary step transition never needs: whether this
 * invocation actually counted an explicit rejection, and which decision really
 * owns the cycle when the caller's expectation is stale.
 */
internal sealed interface AdaptiveSupportCycleAlternativePreparationResult {
    /**
     * The cycle is ready for a follow-up decision.
     *
     * [countedAlternativeRequest] is true only when this invocation resolved an
     * in-progress step and therefore advanced the durable
     * `alternativeRequestCount`. It is false when the current step was already
     * terminal, which is ordinary continuation rather than another explicit
     * rejection -- and is also what makes a retry after process death safe.
     */
    data class Continue(
        val state: PersistedAdaptiveSupportCycle,
        val countedAlternativeRequest: Boolean,
    ) : AdaptiveSupportCycleAlternativePreparationResult

    /** The second explicit rejection terminated the cycle. No follow-up may be created. */
    data class CycleEnded(
        val cycle: AdaptiveSupportCycle,
    ) : AdaptiveSupportCycleAlternativePreparationResult

    /** A stale caller tried to act on a cycle another decision now owns. */
    data class OwnerMismatch(
        val actualDecisionId: String,
    ) : AdaptiveSupportCycleAlternativePreparationResult

    data class Rejected(
        val reason: com.impulsive.app.backend.domain.engine.adaptive
            .AdaptiveSupportCycleTransitionRejection,
    ) : AdaptiveSupportCycleAlternativePreparationResult

    data object NotFound : AdaptiveSupportCycleAlternativePreparationResult
    data object CycleMismatch : AdaptiveSupportCycleAlternativePreparationResult
    data object RevisionConflict : AdaptiveSupportCycleAlternativePreparationResult
    data object InvalidPersistedState : AdaptiveSupportCycleAlternativePreparationResult
    data object Expired : AdaptiveSupportCycleAlternativePreparationResult
    data object PersistenceFailure : AdaptiveSupportCycleAlternativePreparationResult
}

sealed interface AdaptiveSupportCycleGameLaunchResult {
    data class Ready(
        val launch: RecoveryGameLaunchContext.SupportCycle,
        val state: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportCycleGameLaunchResult

    data class Unavailable(val result: AdaptiveSupportCycleCommandResult) :
        AdaptiveSupportCycleGameLaunchResult
}

class AdaptiveSupportCycleCoordinator(
    private val repository: AdaptiveSupportCycleRepository,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
    private val idSource: AdaptiveIdSource = AdaptiveIdSource {
        UUID.randomUUID().toString()
    },
) {
    /**
     * Creates or recovers the protected Support Cycle for one decision.
     *
     * Every protected cycle uses the same fixed budget, so there is
     * deliberately no caller-selectable duration: no internal path can mint a
     * shorter production cycle. When the repository finds an already-active
     * cycle, that persisted cycle stays authoritative and its budget is never
     * recomputed, shrunk or extended.
     */
    suspend fun createOrRecover(
        decision: AdaptiveDecision,
    ): AdaptiveSupportCycleCommandResult {
        val now = clock.nowMillis()
        val cycle = AdaptiveSupportCycle(
            cycleId = idSource.newId(),
            decisionId = decision.decisionId,
            protectionIncidentToken = decision.protectionIncidentToken,
            initialDurationMillis = AdaptiveSupportCycleTiming.TotalDurationMillis,
        )
        return when (
            val created = repository.create(
                cycle = cycle,
                createdAtEpochMillis = now,
                expiresAtEpochMillis = decision.observationDeadlineAtMillis,
            )
        ) {
            is AdaptiveSupportCycleCreateResult.Created ->
                AdaptiveSupportCycleCommandResult.Active(created.state)
            is AdaptiveSupportCycleCreateResult.ExistingActive -> if (
                created.state.cycle.decisionId == decision.decisionId
            ) {
                AdaptiveSupportCycleCommandResult.ExistingActive(created.state)
            } else {
                AdaptiveSupportCycleCommandResult.ActiveDecisionConflict(
                    state = created.state,
                )
            }
            AdaptiveSupportCycleCreateResult.InvalidPersistedState ->
                AdaptiveSupportCycleCommandResult.InvalidPersistedState
            AdaptiveSupportCycleCreateResult.Expired ->
                AdaptiveSupportCycleCommandResult.Expired
            AdaptiveSupportCycleCreateResult.PersistenceFailure ->
                AdaptiveSupportCycleCommandResult.PersistenceFailure
        }
    }

    suspend fun recover(): AdaptiveSupportCycleCommandResult = when (
        val recovered = AdaptiveSupportCycleRecovery(repository, clock).recover()
    ) {
        is AdaptiveSupportCycleRecoveryResult.Restored ->
            AdaptiveSupportCycleCommandResult.Active(recovered.state)
        AdaptiveSupportCycleRecoveryResult.NotFound ->
            AdaptiveSupportCycleCommandResult.NotFound
        AdaptiveSupportCycleRecoveryResult.InvalidPersistedStateCleared ->
            AdaptiveSupportCycleCommandResult.InvalidPersistedState
        AdaptiveSupportCycleRecoveryResult.ExpiredCleared ->
            AdaptiveSupportCycleCommandResult.Expired
        AdaptiveSupportCycleRecoveryResult.RevisionConflict ->
            AdaptiveSupportCycleCommandResult.RevisionConflict
        AdaptiveSupportCycleRecoveryResult.PersistenceFailure ->
            AdaptiveSupportCycleCommandResult.PersistenceFailure
    }

    suspend fun startStep(
        cycleId: String,
        intervention: InterventionFamily,
        gameType: ScoreGameType? = null,
        requestedDurationMillis: Long,
        minimumUsefulDurationMillis: Long,
    ): AdaptiveSupportCycleCommandResult = mutate(cycleId) { cycle ->
        AdaptiveSupportCycleTransitionPolicy.startStep(
            cycle = cycle,
            intervention = intervention,
            gameType = gameType,
            requestedDurationMillis = requestedDurationMillis,
            minimumUsefulDurationMillis = minimumUsefulDurationMillis,
        )
    }

    suspend fun startGame(
        cycleId: String,
        gameType: ScoreGameType,
        requestedDurationMillis: Long,
        minimumUsefulDurationMillis: Long,
    ): AdaptiveSupportCycleGameLaunchResult {
        val result = startStep(
            cycleId = cycleId,
            intervention = InterventionFamily.PivotGame,
            gameType = gameType,
            requestedDurationMillis = requestedDurationMillis,
            minimumUsefulDurationMillis = minimumUsefulDurationMillis,
        )
        val active = result as? AdaptiveSupportCycleCommandResult.Active
            ?: return AdaptiveSupportCycleGameLaunchResult.Unavailable(result)
        val step = checkNotNull(active.state.cycle.currentStep)
        return AdaptiveSupportCycleGameLaunchResult.Ready(
            launch = RecoveryGameLaunchContext.SupportCycle(
                cycleId = active.state.cycle.cycleId,
                decisionId = active.state.cycle.decisionId,
                gameType = checkNotNull(step.gameType),
                maxDurationMillis = step.remainingDurationMillis,
            ),
            state = active.state,
        )
    }

    suspend fun recordElapsed(
        cycleId: String,
        elapsedDurationMillis: Long,
    ): AdaptiveSupportCycleCommandResult = mutateCycle(cycleId) { cycle ->
        AdaptiveSupportCycleTimePolicy.recordElapsedDuration(cycle, elapsedDurationMillis)
    }

    /**
     * Applies game elapsed time and resolves the current game step through one
     * repository mutation.
     *
     * No elapsed-time update is committed unless the resulting step transition is
     * also accepted. This prevents a failed second write from leaving a partially
     * persisted game result.
     */
    suspend fun recordElapsedAndResolveGameStep(
        cycleId: String,
        elapsedDurationMillis: Long,
        requestedOutcome:
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome,
        endCycle: Boolean,
    ): AdaptiveSupportCycleCommandResult =
        mutate(
            cycleId = cycleId,
        ) { cycle ->
            require(
                elapsedDurationMillis >=
                    0L,
            ) {
                "Elapsed game duration must not be negative."
            }

            require(
                requestedOutcome ==
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Completed ||
                    requestedOutcome ==
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Failed ||
                    requestedOutcome ==
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Abandoned ||
                    requestedOutcome ==
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .TimedOut,
            ) {
                "A game result requires a terminal support-step outcome."
            }

            val afterElapsed =
                AdaptiveSupportCycleTimePolicy
                    .recordElapsedDuration(
                        cycle =
                            cycle,
                        elapsedDurationMillis =
                            elapsedDurationMillis,
                    )

            /*
             * Exhausting the complete cycle budget makes the cycle terminal.
             * Persist that authoritative timeout through this same mutation.
             */
            if (
                afterElapsed
                    .isTerminal
            ) {
                return@mutate AdaptiveSupportCycleTransitionResult
                    .Applied(
                        afterElapsed,
                    )
            }

            val currentStep =
                afterElapsed
                    .currentStep
                    ?: return@mutate AdaptiveSupportCycleTransitionResult
                        .Rejected(
                            com.impulsive.app.backend.domain.engine.adaptive
                                .AdaptiveSupportCycleTransitionRejection
                                .NoCurrentStep,
                        )

            /*
             * The elapsed duration may exhaust the current step while leaving some
             * cycle budget available. In that case TimedOut is authoritative.
             */
            if (
                currentStep
                    .outcome ==
                com.impulsive.app.backend.domain.model.adaptive
                    .AdaptiveSupportStepOutcome
                    .TimedOut
            ) {
                return@mutate if (
                    endCycle
                ) {
                    AdaptiveSupportCycleTransitionPolicy
                        .finishCycleAfterResolvedStep(
                            cycle =
                                afterElapsed,
                            requestedTerminalStatus =
                                AdaptiveSupportCycleStatus
                                    .Failed,
                        )
                } else {
                    AdaptiveSupportCycleTransitionResult
                        .Applied(
                            afterElapsed,
                        )
                }
            }

            val resolution =
                when (
                    requestedOutcome
                ) {
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Completed,
                    ->
                        if (
                            endCycle
                        ) {
                            AdaptiveSupportCycleStepResolution
                                .CompletedAndEndCycle
                        } else {
                            AdaptiveSupportCycleStepResolution
                                .CompletedAndContinue
                        }

                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Failed,
                    ->
                        if (
                            endCycle
                        ) {
                            AdaptiveSupportCycleStepResolution
                                .FailedAndEndCycle
                        } else {
                            AdaptiveSupportCycleStepResolution
                                .FailedAndContinue
                        }

                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Abandoned,
                    ->
                        if (
                            endCycle
                        ) {
                            AdaptiveSupportCycleStepResolution
                                .AbandonedAndEndCycle
                        } else {
                            AdaptiveSupportCycleStepResolution
                                .AbandonedAndContinue
                        }

                    /*
                     * A TimedOut request is valid only when elapsed-time application
                     * actually exhausted the current step or complete cycle. Those
                     * cases were handled above.
                     */
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .TimedOut,
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .InProgress,
                    com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome
                        .Cancelled,
                    ->
                        null
                }
                    ?: return@mutate AdaptiveSupportCycleTransitionResult
                        .Rejected(
                            com.impulsive.app.backend.domain.engine.adaptive
                                .AdaptiveSupportCycleTransitionRejection
                                .InvalidTerminalOutcome,
                        )

            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        afterElapsed,
                    resolution =
                        resolution,
                )
        }

    suspend fun completeStep(cycleId: String, endCycle: Boolean) = resolve(
        cycleId,
        if (endCycle) AdaptiveSupportCycleStepResolution.CompletedAndEndCycle
        else AdaptiveSupportCycleStepResolution.CompletedAndContinue,
    )

    suspend fun failStep(cycleId: String, endCycle: Boolean) = resolve(
        cycleId,
        if (endCycle) AdaptiveSupportCycleStepResolution.FailedAndEndCycle
        else AdaptiveSupportCycleStepResolution.FailedAndContinue,
    )

    suspend fun abandonStep(cycleId: String, endCycle: Boolean) = resolve(
        cycleId,
        if (endCycle) AdaptiveSupportCycleStepResolution.AbandonedAndEndCycle
        else AdaptiveSupportCycleStepResolution.AbandonedAndContinue,
    )

    suspend fun requestAlternative(cycleId: String) = resolve(
        cycleId,
        AdaptiveSupportCycleStepResolution.AlternativeRequested,
    )

    suspend fun cancel(cycleId: String) = resolve(
        cycleId,
        AdaptiveSupportCycleStepResolution.CancelledAndEndCycle,
    )

    /**
     * Reads the decision that currently owns the active cycle, or null when no
     * such active cycle can be read. Read-only; performs no mutation.
     */
    internal suspend fun activeCycleOwner(cycleId: String): String? =
        (active(cycleId) as? ActiveLookup.Found)?.state?.cycle?.decisionId

    /**
     * Owner-guarded preparation for one "choose another support" operation.
     *
     * Unlike the generic [requestAlternative], this refuses to act unless
     * [expectedDecisionId] still owns the cycle. That guard is what stops a
     * stale operation belonging to a previous decision from abandoning the step
     * of the decision that has since taken ownership, and from spending the
     * user's second and final rejection on it.
     *
     * An already-terminal current step is treated as continuation and writes
     * nothing: the rejection it represents was already counted durably, so a
     * retry after process death cannot count it twice.
     */
    internal suspend fun prepareAlternativeChoice(
        cycleId: String,
        expectedDecisionId: String,
    ): AdaptiveSupportCycleAlternativePreparationResult {
        require(cycleId.isNotBlank()) {
            "Support-cycle ID must not be blank."
        }
        require(expectedDecisionId.isNotBlank()) {
            "Expected owning decision ID must not be blank."
        }

        val loaded = active(cycleId)
        if (loaded !is ActiveLookup.Found) return loaded.toPreparationResult()
        val cycle = loaded.state.cycle

        /*
         * Ownership is validated before any mutation. A stale caller must never
         * reach the transition policy.
         */
        if (cycle.decisionId != expectedDecisionId) {
            return AdaptiveSupportCycleAlternativePreparationResult.OwnerMismatch(
                actualDecisionId = cycle.decisionId,
            )
        }

        val currentStep = cycle.currentStep
            ?: return AdaptiveSupportCycleAlternativePreparationResult.Rejected(
                com.impulsive.app.backend.domain.engine.adaptive
                    .AdaptiveSupportCycleTransitionRejection.NoCurrentStep,
            )

        /*
         * Continuation, not another explicit rejection. Recovery may have
         * rewritten transitionReason to Restored, so the durable terminal step
         * state -- not the transition reason -- is the authority here.
         */
        if (currentStep.outcome.isTerminal) {
            return AdaptiveSupportCycleAlternativePreparationResult.Continue(
                state = loaded.state,
                countedAlternativeRequest = false,
            )
        }

        val transition = AdaptiveSupportCycleTransitionPolicy.resolveCurrentStep(
            cycle,
            AdaptiveSupportCycleStepResolution.AlternativeRequested,
        )
        if (transition is AdaptiveSupportCycleTransitionResult.Rejected) {
            return AdaptiveSupportCycleAlternativePreparationResult.Rejected(transition.reason)
        }
        val resolved = (transition as AdaptiveSupportCycleTransitionResult.Applied).cycle

        return when (val persisted = persist(loaded.state, resolved)) {
            is AdaptiveSupportCycleCommandResult.Active -> {
                /*
                 * Fail closed if the durable counter did not advance exactly
                 * once; a silent miscount would let a third rejection through.
                 */
                if (
                    persisted.state.cycle.alternativeRequestCount !=
                    cycle.alternativeRequestCount + 1
                ) {
                    AdaptiveSupportCycleAlternativePreparationResult.InvalidPersistedState
                } else {
                    AdaptiveSupportCycleAlternativePreparationResult.Continue(
                        state = persisted.state,
                        countedAlternativeRequest = true,
                    )
                }
            }

            /*
             * The only terminal outcome an alternative request may produce is the
             * second-rejection Abandoned cycle established by APP-004A.
             */
            is AdaptiveSupportCycleCommandResult.Terminal ->
                AdaptiveSupportCycleAlternativePreparationResult.CycleEnded(persisted.cycle)

            is AdaptiveSupportCycleCommandResult.Rejected ->
                AdaptiveSupportCycleAlternativePreparationResult.Rejected(persisted.reason)

            AdaptiveSupportCycleCommandResult.NotFound ->
                AdaptiveSupportCycleAlternativePreparationResult.NotFound
            AdaptiveSupportCycleCommandResult.CycleMismatch ->
                AdaptiveSupportCycleAlternativePreparationResult.CycleMismatch
            AdaptiveSupportCycleCommandResult.RevisionConflict ->
                AdaptiveSupportCycleAlternativePreparationResult.RevisionConflict
            AdaptiveSupportCycleCommandResult.InvalidPersistedState ->
                AdaptiveSupportCycleAlternativePreparationResult.InvalidPersistedState
            AdaptiveSupportCycleCommandResult.Expired ->
                AdaptiveSupportCycleAlternativePreparationResult.Expired
            AdaptiveSupportCycleCommandResult.PersistenceFailure ->
                AdaptiveSupportCycleAlternativePreparationResult.PersistenceFailure

            is AdaptiveSupportCycleCommandResult.ExistingActive,
            is AdaptiveSupportCycleCommandResult.ActiveDecisionConflict,
            ->
                AdaptiveSupportCycleAlternativePreparationResult.InvalidPersistedState
        }
    }

    suspend fun finishCycleAfterResolvedStep(
        cycleId: String,
        terminalStatus: AdaptiveSupportCycleStatus,
    ): AdaptiveSupportCycleCommandResult = mutate(cycleId) { cycle ->
        AdaptiveSupportCycleTransitionPolicy.finishCycleAfterResolvedStep(
            cycle,
            terminalStatus,
        )
    }

    /**
     * Transfers the current decision ownership of one already-active support
     * cycle from [previousDecision] to an explicit same-Moment
     * [followUpDecision].
     *
     * This binds ownership only. The cycle keeps its identity, its root
     * protection incident token, its complete budget accounting, its current
     * resolved step, its transition reason, its status, its creation time and
     * its expiry. No second support cycle is created, and the follow-up
     * decision's own observation deadline never extends the cycle.
     *
     * The caller is responsible for resolving the current step first; this
     * operation refuses to transfer a cycle whose current step is still in
     * progress.
     *
     * The operation is idempotent against the persisted cycle. Once ownership
     * has durably moved, retrying with the same decision pair reports
     * [AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred] and writes
     * nothing, even when the follow-up decision has since been marked started
     * and its support step has since begun. This is what makes recovery after
     * process death safe: the persisted Support Cycle and the persisted decision
     * record remain the durable authorities, and no retry token is needed.
     */
    internal suspend fun handoffDecision(
        cycleId: String,
        previousDecision: AdaptiveDecision,
        followUpDecision: AdaptiveDecision,
    ): AdaptiveSupportCycleDecisionHandoffResult {
        if (!isValidDecisionHandoffLineage(previousDecision, followUpDecision)) {
            return AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage
        }

        val loaded = active(cycleId)
        if (loaded !is ActiveLookup.Found) return loaded.toHandoffResult()
        val persisted = loaded.state
        val cycle = persisted.cycle

        /*
         * The persisted cycle is the authoritative record of ownership, so
         * idempotency is recognised before every first-transfer-only
         * precondition. A legitimate retry can arrive after ownership already
         * moved, after the follow-up decision was marked started, and after the
         * next step already began -- including after process death. The transfer
         * has nevertheless already succeeded durably, and repeating or rejecting
         * it would be wrong.
         */
        if (cycle.decisionId == followUpDecision.decisionId) {
            return AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred(persisted)
        }

        /*
         * First-transfer-only precondition. A follow-up decision whose lifecycle
         * has already begun must never retroactively claim a cycle it does not
         * already own, so this is enforced ahead of the owner, step and
         * persistence checks.
         */
        if (followUpDecision.startedAtMillis != null) {
            return AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage
        }

        if (cycle.decisionId != previousDecision.decisionId) {
            return AdaptiveSupportCycleDecisionHandoffResult.OwnerMismatch
        }

        val currentStep = cycle.currentStep
            ?: return AdaptiveSupportCycleDecisionHandoffResult.CurrentStepNotResolved
        if (!currentStep.outcome.isTerminal) {
            return AdaptiveSupportCycleDecisionHandoffResult.CurrentStepNotResolved
        }

        val transferredCycle = cycle.copy(decisionId = followUpDecision.decisionId)

        return when (
            val updated = repository.update(
                cycleId = cycle.cycleId,
                expectedRevision = persisted.revision,
                cycle = transferredCycle,
                updatedAtEpochMillis = clock.nowMillis(),
            )
        ) {
            is AdaptiveSupportCycleMutationResult.Updated ->
                AdaptiveSupportCycleDecisionHandoffResult.Transferred(updated.state)

            is AdaptiveSupportCycleMutationResult.RevisionConflict ->
                reconcileHandoffRevisionConflict(cycleId, followUpDecision)

            /*
             * The transferred cycle remains active, so the repository must never
             * clear it. Fail closed rather than reporting a success that did not
             * persist an active cycle.
             */
            AdaptiveSupportCycleMutationResult.Cleared ->
                AdaptiveSupportCycleDecisionHandoffResult.InvalidPersistedState

            AdaptiveSupportCycleMutationResult.NotFound ->
                AdaptiveSupportCycleDecisionHandoffResult.NotFound
            AdaptiveSupportCycleMutationResult.CycleMismatch ->
                AdaptiveSupportCycleDecisionHandoffResult.CycleMismatch
            AdaptiveSupportCycleMutationResult.InvalidPersistedState ->
                AdaptiveSupportCycleDecisionHandoffResult.InvalidPersistedState
            AdaptiveSupportCycleMutationResult.Expired ->
                AdaptiveSupportCycleDecisionHandoffResult.Expired
            AdaptiveSupportCycleMutationResult.PersistenceFailure ->
                AdaptiveSupportCycleDecisionHandoffResult.PersistenceFailure
        }
    }

    /**
     * Performs exactly one authoritative reload after a lost compare-and-set.
     *
     * A duplicate same-target handoff is reconciled as idempotent success. Any
     * other owner means this caller genuinely lost the race, which is reported
     * rather than hidden. No second write and no retry loop occurs.
     */
    private suspend fun reconcileHandoffRevisionConflict(
        cycleId: String,
        followUpDecision: AdaptiveDecision,
    ): AdaptiveSupportCycleDecisionHandoffResult {
        val reloaded = active(cycleId)
        if (reloaded !is ActiveLookup.Found) return reloaded.toHandoffResult()
        return if (
            reloaded.state.cycle.decisionId == followUpDecision.decisionId
        ) {
            AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred(reloaded.state)
        } else {
            AdaptiveSupportCycleDecisionHandoffResult.RevisionConflict
        }
    }

    /**
     * Authoritative validation that [followUpDecision] is structurally allowed
     * to belong to the same explicit follow-up lineage as [previousDecision].
     *
     * Only lifecycle-stable facts belong here: every condition must hold both
     * before and after a successful ownership transfer, so that an idempotent
     * retry validates identically to the original call. The follow-up's started
     * state is deliberately excluded because it legitimately changes once the
     * transferred lifecycle begins; it is enforced separately in
     * [handoffDecision] as a first-transfer-only precondition.
     *
     * These checks are the only thing preventing an unrelated decision from
     * stealing an active support cycle, so none of them may be weakened.
     */
    private fun isValidDecisionHandoffLineage(
        previousDecision: AdaptiveDecision,
        followUpDecision: AdaptiveDecision,
    ): Boolean {
        if (previousDecision.decisionId == followUpDecision.decisionId) return false
        if (previousDecision.startedAtMillis == null) return false
        if (followUpDecision.sourceKind != AdaptiveSourceKind.ExplicitUserSupport) return false
        if (
            previousDecision.momentWindowStartedAtMillis !=
            followUpDecision.momentWindowStartedAtMillis
        ) {
            return false
        }
        if (followUpDecision.createdAtMillis < previousDecision.createdAtMillis) return false
        if (followUpDecision.presentedAtMillis == null) return false
        if (followUpDecision.assignment.actualIntervention == null) return false
        return true
    }

    private suspend fun resolve(
        cycleId: String,
        resolution: AdaptiveSupportCycleStepResolution,
    ): AdaptiveSupportCycleCommandResult = mutate(cycleId) { cycle ->
        AdaptiveSupportCycleTransitionPolicy.resolveCurrentStep(cycle, resolution)
    }

    private suspend fun mutate(
        cycleId: String,
        transition: (AdaptiveSupportCycle) -> AdaptiveSupportCycleTransitionResult,
    ): AdaptiveSupportCycleCommandResult {
        val loaded = active(cycleId)
        if (loaded !is ActiveLookup.Found) return loaded.toCommandResult()
        return when (val result = transition(loaded.state.cycle)) {
            is AdaptiveSupportCycleTransitionResult.Applied ->
                persist(loaded.state, result.cycle)
            is AdaptiveSupportCycleTransitionResult.Rejected ->
                AdaptiveSupportCycleCommandResult.Rejected(result.reason)
        }
    }

    private suspend fun mutateCycle(
        cycleId: String,
        transition: (AdaptiveSupportCycle) -> AdaptiveSupportCycle,
    ): AdaptiveSupportCycleCommandResult {
        val loaded = active(cycleId)
        if (loaded !is ActiveLookup.Found) return loaded.toCommandResult()
        return persist(loaded.state, transition(loaded.state.cycle))
    }

    private suspend fun active(cycleId: String): ActiveLookup = when (
        val loaded = repository.load(clock.nowMillis())
    ) {
        is AdaptiveSupportCycleLoadResult.Active -> if (loaded.state.cycle.cycleId == cycleId) {
            ActiveLookup.Found(loaded.state)
        } else {
            ActiveLookup.CycleMismatch
        }
        AdaptiveSupportCycleLoadResult.NotFound -> ActiveLookup.NotFound
        AdaptiveSupportCycleLoadResult.InvalidPersistedState -> ActiveLookup.Invalid
        AdaptiveSupportCycleLoadResult.Expired -> ActiveLookup.Expired
        AdaptiveSupportCycleLoadResult.PersistenceFailure -> ActiveLookup.Failure
    }

    private suspend fun persist(
        previous: PersistedAdaptiveSupportCycle,
        updatedCycle: AdaptiveSupportCycle,
    ): AdaptiveSupportCycleCommandResult = when (
        val updated = repository.update(
            cycleId = previous.cycle.cycleId,
            expectedRevision = previous.revision,
            cycle = updatedCycle,
            updatedAtEpochMillis = clock.nowMillis(),
        )
    ) {
        is AdaptiveSupportCycleMutationResult.Updated ->
            AdaptiveSupportCycleCommandResult.Active(updated.state)
        AdaptiveSupportCycleMutationResult.Cleared ->
            AdaptiveSupportCycleCommandResult.Terminal(updatedCycle)
        AdaptiveSupportCycleMutationResult.NotFound -> AdaptiveSupportCycleCommandResult.NotFound
        AdaptiveSupportCycleMutationResult.CycleMismatch ->
            AdaptiveSupportCycleCommandResult.CycleMismatch
        is AdaptiveSupportCycleMutationResult.RevisionConflict ->
            AdaptiveSupportCycleCommandResult.RevisionConflict
        AdaptiveSupportCycleMutationResult.InvalidPersistedState ->
            AdaptiveSupportCycleCommandResult.InvalidPersistedState
        AdaptiveSupportCycleMutationResult.Expired -> AdaptiveSupportCycleCommandResult.Expired
        AdaptiveSupportCycleMutationResult.PersistenceFailure ->
            AdaptiveSupportCycleCommandResult.PersistenceFailure
    }

    private sealed interface ActiveLookup {
        data class Found(val state: PersistedAdaptiveSupportCycle) : ActiveLookup
        data object NotFound : ActiveLookup
        data object CycleMismatch : ActiveLookup
        data object Invalid : ActiveLookup
        data object Expired : ActiveLookup
        data object Failure : ActiveLookup
    }

    private fun ActiveLookup.toCommandResult(): AdaptiveSupportCycleCommandResult = when (this) {
        is ActiveLookup.Found -> error("Found state must be handled before conversion.")
        ActiveLookup.NotFound -> AdaptiveSupportCycleCommandResult.NotFound
        ActiveLookup.CycleMismatch -> AdaptiveSupportCycleCommandResult.CycleMismatch
        ActiveLookup.Invalid -> AdaptiveSupportCycleCommandResult.InvalidPersistedState
        ActiveLookup.Expired -> AdaptiveSupportCycleCommandResult.Expired
        ActiveLookup.Failure -> AdaptiveSupportCycleCommandResult.PersistenceFailure
    }

    private fun ActiveLookup.toPreparationResult():
        AdaptiveSupportCycleAlternativePreparationResult = when (this) {
        is ActiveLookup.Found -> error("Found state must be handled before conversion.")
        ActiveLookup.NotFound ->
            AdaptiveSupportCycleAlternativePreparationResult.NotFound
        ActiveLookup.CycleMismatch ->
            AdaptiveSupportCycleAlternativePreparationResult.CycleMismatch
        ActiveLookup.Invalid ->
            AdaptiveSupportCycleAlternativePreparationResult.InvalidPersistedState
        ActiveLookup.Expired ->
            AdaptiveSupportCycleAlternativePreparationResult.Expired
        ActiveLookup.Failure ->
            AdaptiveSupportCycleAlternativePreparationResult.PersistenceFailure
    }

    private fun ActiveLookup.toHandoffResult(): AdaptiveSupportCycleDecisionHandoffResult =
        when (this) {
            is ActiveLookup.Found -> error("Found state must be handled before conversion.")
            ActiveLookup.NotFound -> AdaptiveSupportCycleDecisionHandoffResult.NotFound
            ActiveLookup.CycleMismatch -> AdaptiveSupportCycleDecisionHandoffResult.CycleMismatch
            ActiveLookup.Invalid ->
                AdaptiveSupportCycleDecisionHandoffResult.InvalidPersistedState
            ActiveLookup.Expired -> AdaptiveSupportCycleDecisionHandoffResult.Expired
            ActiveLookup.Failure ->
                AdaptiveSupportCycleDecisionHandoffResult.PersistenceFailure
        }

    companion object {
        /**
         * Requested maximum duration for a single support step, not an attempt
         * budget. A step is still capped against the cycle's remaining time, so
         * a 60- or 45-second cycle never yields more than it has left.
         *
         * It equals the first-attempt pool by construction; a regression test
         * pins that relationship.
         */
        const val DefaultCycleDurationMillis = 90_000L
        const val MinimumUsefulStepDurationMillis = 10_000L
    }
}
