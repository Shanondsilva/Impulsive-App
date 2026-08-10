package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.SnakeGameHistoryDataSource
import com.impulsive.app.backend.data.repository.GameStoreManager
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGameCompletionPolicy
import com.impulsive.app.backend.domain.game.SnakeGameHistory
import com.impulsive.app.backend.domain.game.SnakeGamePhase
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.game.SnakeGameSessionRuntime
import com.impulsive.app.backend.domain.game.SnakeGameStorePersistenceState
import com.impulsive.app.backend.domain.game.SnakeGameUiState
import com.impulsive.app.backend.domain.game.SnakeGameView
import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import com.impulsive.app.backend.domain.game.SnakeStandaloneRoundDurationMillis
import com.impulsive.app.backend.domain.game.afterResult
import com.impulsive.app.backend.domain.game.isGameStoreResultDurable
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Session runtime for Snake.
 *
 * Gameplay rules live in [SnakeGameSessionRuntime] and the engine beneath it;
 * this class owns only Android session concerns — history, score identity,
 * support-cycle resolution and process-death result recovery.
 *
 * Snake is the active recovery game from SNAKE-04.
 */
class SnakeGameViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val supportCycleRuntime = RecoveryGameSupportCycleRuntime(application)
    private val resultStateStore = RecoveryGameResultStateStore(savedStateHandle)
    private val resultActionCoordinator = RecoveryGameResultActionCoordinator(
        runtime = supportCycleRuntime,
        clearResultState = { resultStateStore.clear() },
    )
    private val historyDataSource = SnakeGameHistoryDataSource(application)
    private val scoreRepository = ScoreRepository(application)
    private val pivotGameSessionCommitCoordinator =
        PivotGameSessionCommitCoordinator(
            scoreRepository = scoreRepository,
            immediateSafeExitRecorder =
                PivotGameSafeExitRecorder(
                    SafeExitRecordingCoordinator(
                        application,
                    ),
                ),
            reconciliationScheduler =
                WorkManagerPivotGameSafeExitReconciliationScheduler(
                    application,
                ),
        )
    private val gameStoreManager = GameStoreManager(application)
    private val runtime = SnakeGameSessionRuntime()
    private val activeSupportCheckpointStore =
        SnakeActiveSupportCheckpointStateStore(savedStateHandle)
    private val restoredResultPersistenceRepair =
        SnakeRestoredResultPersistenceRepair(
            scoreWriter = SnakeRestoredScoreWriter { record ->
                scoreRepository.recordSession(record)
            },
            historyWriter = SnakeRestoredHistoryWriter { history ->
                historyDataSource.save(history)
            },
        )

    private val _uiState = MutableStateFlow(
        SnakeGameUiState(
            view = SnakeGameView.Ready,
            gameState = runtime.state,
            elapsedDurationMillis = 0L,
            timeLeftSeconds = runtime.timeLeftSeconds,
        ),
    )
    val uiState: StateFlow<SnakeGameUiState> = _uiState

    private var resultRecorded = false

    /** Allocated on the first real interaction, not when the screen opens. */
    private var activeSessionId: Long = 0L
    private var sessionStartedAt: LocalDateTime? = null
    private var urgeBeforeRating: Int? = null
    private var urgeAfterRating: Int? = null
    private var lastRecordedSession: ScoreSessionRecord? = null
    private var roundDurationMillis = SnakeStandaloneRoundDurationMillis
    private var lastSupportOutcome: SupportCycleGameTerminalOutcome? = null
    private var lastSupportElapsedMillis = 0L
    private var activeLaunchContext: RecoveryGameLaunchContext =
        RecoveryGameLaunchContext.Standalone

    /** Support time consumed by earlier, process-killed attempts at this step. */
    private var supportElapsedBaselineMillis = 0L

    /** The step's full allocation before the local checkpoint is applied. */
    private var supportAuthoritativeAvailableMillis = 0L
    private var lastCheckpointWrittenMillis = 0L

    suspend fun configureLaunchContext(
        launchContext: RecoveryGameLaunchContext,
    ): Boolean {
        val binding = supportCycleRuntime.bindWithRecovery(
            requested = launchContext,
            standaloneDurationMillis = SnakeStandaloneRoundDurationMillis,
        ) ?: return false

        roundDurationMillis = binding.durationMillis
        activeLaunchContext = launchContext

        // Load history before Ready so the first interaction cannot race it.
        val history = historyDataSource.currentHistory()

        val supportLaunch = launchContext as? RecoveryGameLaunchContext.SupportCycle
        val activeSupportCheckpoint = activeSupportCheckpointStore.restore(launchContext) ?: 0L

        if (supportLaunch == null) {
            // Standalone has no authoritative step to protect.
            activeSupportCheckpointStore.clear()
            resetSupportElapsedTracking()
        } else {
            supportAuthoritativeAvailableMillis = binding.durationMillis

            /*
             * A checkpoint claiming more than the allocation is corrupt. Failing
             * closed is required: clearing it would silently hand back time the
             * player already consumed.
             */
            if (activeSupportCheckpoint > binding.durationMillis) return false

            supportElapsedBaselineMillis = activeSupportCheckpoint
            lastCheckpointWrittenMillis = activeSupportCheckpoint
        }

        val snapshot = resultStateStore.restore(
            launchContext = launchContext,
            expectedGameType = ScoreGameType.Snake,
        )

        val authoritativeResult = binding.resolvedStep

        /*
         * The repository says this game step is already terminal, so a fresh
         * Ready board must never be shown. The snapshot has to agree with it.
         */
        if (authoritativeResult != null) {
            val snapshotOutcome = snapshot?.supportOutcomeOrNull()

            if (
                snapshot == null ||
                snapshotOutcome != authoritativeResult.outcome ||
                !restoreResultSnapshot(snapshot)
            ) {
                resultStateStore.clear()

                supportCycleRuntime.resolveAndEnd(
                    outcome = authoritativeResult.outcome,
                    elapsedDurationMillis = authoritativeResult.elapsedDurationMillis,
                )

                return false
            }

            // A stable Result now owns recovery; the active checkpoint is spent.
            activeSupportCheckpointStore.clear()
            resetSupportElapsedTracking()

            // Repository state wins over a stale snapshot elapsed value.
            lastSupportOutcome = authoritativeResult.outcome
            lastSupportElapsedMillis = authoritativeResult.elapsedDurationMillis

            if (!repairRestoredResultPersistence()) return false

            return true
        }

        /*
         * Process death after the result snapshot was written but before the
         * asynchronous terminal-step persistence completed.
         */
        if (snapshot != null) {
            val snapshotOutcome = snapshot.supportOutcomeOrNull()

            if (snapshotOutcome == null || !restoreResultSnapshot(snapshot)) {
                /*
                 * A corrupt presentation must not terminalise a legitimate
                 * in-progress step, but consumed support time still survives.
                 */
                resultStateStore.clear()

                return resumeReadyWithRemainingSupportTime(
                    supportLaunch = supportLaunch,
                    fallbackDurationMillis = binding.durationMillis,
                    checkpointMillis = activeSupportCheckpoint,
                    history = history,
                )
            }

            // The Result snapshot owns the elapsed support value from here on.
            activeSupportCheckpointStore.clear()
            resetSupportElapsedTracking()

            lastSupportOutcome = snapshotOutcome
            lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis.coerceAtLeast(0L)

            if (!repairRestoredResultPersistence()) return false

            val resolution = supportCycleRuntime.resolveForContinuation(
                outcome = snapshotOutcome,
                elapsedDurationMillis = lastSupportElapsedMillis,
            )

            if (!resolution.keepsRestoredResultVisible) {
                resultStateStore.clear()

                return false
            }

            return true
        }

        /*
         * Fresh launch, re-entry before play, or process recreation during
         * active gameplay. The transient board is deliberately not rebuilt;
         * only consumed support time carries over.
         */
        return resumeReadyWithRemainingSupportTime(
            supportLaunch = supportLaunch,
            fallbackDurationMillis = binding.durationMillis,
            checkpointMillis = activeSupportCheckpoint,
            history = history,
        )
    }

    /**
     * Shows a fresh Ready board for whatever support time is left, or refuses
     * when the allocation is already spent.
     */
    private suspend fun resumeReadyWithRemainingSupportTime(
        supportLaunch: RecoveryGameLaunchContext.SupportCycle?,
        fallbackDurationMillis: Long,
        checkpointMillis: Long,
        history: SnakeGameHistory,
    ): Boolean {
        if (supportLaunch == null) {
            resetSnakeRoundToReady(fallbackDurationMillis, history)

            return true
        }

        val remaining = SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
            authoritativeAvailableMillis = fallbackDurationMillis,
            checkpointMillis = checkpointMillis,
        )

        if (remaining > 0L) {
            resetSnakeRoundToReady(remaining, history)

            return true
        }

        /*
         * The whole allocation was consumed before process death. The board and
         * score were intentionally discarded, so no Result can be fabricated:
         * the step is simply reported as timed out.
         */
        val report = supportCycleRuntime.resolveForContinuation(
            outcome = SupportCycleGameTerminalOutcome.TimedOut,
            elapsedDurationMillis = checkpointMillis,
        )

        /*
         * Persistence and revision failures intentionally keep a restored
         * Result visible, but they are not accepted support mutations and must
         * not erase the only proof that this allocation was already consumed.
         */
        if (
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report)
        ) {
            activeSupportCheckpointStore.clear()
            resetSupportElapsedTracking()
        }

        // Otherwise the checkpoint is retained so re-entry can retry.
        return false
    }

    private suspend fun repairRestoredResultPersistence(): Boolean {
        val result = _uiState.value.result ?: return false
        val record = lastRecordedSession ?: return false

        val repaired = restoredResultPersistenceRepair.repair(
            result = result,
            history = _uiState.value.history,
            record = record,
        )

        if (!repaired) return false

        /*
         * The store write may also have been lost with the process. A failure
         * here must not discard an otherwise trusted result: the visible result
         * is the retry surface, and the user cannot leave until it succeeds.
         */
        val gameStoreDurable = confirmGameStorePlayRecorded(
            result = result,
            sessionId = record.id,
        )

        setGameStorePersistenceState(
            activeSessionId,
            if (gameStoreDurable) {
                SnakeGameStorePersistenceState.Persisted
            } else {
                SnakeGameStorePersistenceState.RetryableFailure
            },
        )

        return true
    }

    /**
     * Records this session's play in the Game Store exactly once.
     *
     * A valid completion is the Snake "win", so a qualifying self-collision
     * counts. Returns false on an ordinary persistence failure so the caller
     * can fail closed and retry rather than corrupting a trusted result.
     */
    private suspend fun confirmGameStorePlayRecorded(
        result: SnakeGameResult,
        sessionId: Long,
    ): Boolean {
        if (sessionId <= 0L) return false

        return try {
            val newlyApplied = gameStoreManager.recordPlayOnce(
                gameId = ScoreGameType.Snake.id,
                sessionId = sessionId,
                won = result.validCompletion,
            )

            if (newlyApplied) return true

            /*
             * A false return is ambiguous — already receipted, or an unknown
             * game — so durability must be confirmed independently rather than
             * assumed.
             */
            gameStoreManager.isPlayRecorded(
                gameId = ScoreGameType.Snake.id,
                sessionId = sessionId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
    }

    /** Guards against a stale coroutine restyling a newer round's result. */
    private fun setGameStorePersistenceState(
        sessionId: Long,
        state: SnakeGameStorePersistenceState,
    ) {
        val current = _uiState.value

        if (current.view != SnakeGameView.Result) return
        if (current.result == null) return
        if (activeSessionId != sessionId) return

        _uiState.update { it.copy(gameStorePersistenceState = state) }
    }

    private fun beginGameStorePersistence(
        result: SnakeGameResult,
        sessionId: Long,
    ) {
        if (sessionId <= 0L) {
            setGameStorePersistenceState(
                activeSessionId,
                SnakeGameStorePersistenceState.RetryableFailure,
            )
            return
        }

        setGameStorePersistenceState(sessionId, SnakeGameStorePersistenceState.Pending)

        viewModelScope.launch {
            val durable = confirmGameStorePlayRecorded(result, sessionId)

            setGameStorePersistenceState(
                sessionId,
                if (durable) {
                    SnakeGameStorePersistenceState.Persisted
                } else {
                    SnakeGameStorePersistenceState.RetryableFailure
                },
            )
        }
    }

    /** Retries a failed store write; receipts make this safe to repeat. */
    fun retryResultPersistence() {
        val state = _uiState.value
        val result = state.result ?: return

        if (state.view != SnakeGameView.Result) return
        if (state.gameStorePersistenceState !=
            SnakeGameStorePersistenceState.RetryableFailure
        ) {
            return
        }
        if (activeSessionId <= 0L) return

        beginGameStorePersistence(result = result, sessionId = activeSessionId)
    }

    /**
     * A backend guard so a future UI change cannot let a result action run
     * before its store write is durable.
     */
    private fun resultActionPersistenceReady(): Boolean =
        _uiState.value.isGameStoreResultDurable

    /**
     * The player's directional input. The first one starts the round; there is
     * no start button and no countdown.
     */
    fun changeDirection(direction: SnakeDirection) {
        val now = SystemClock.elapsedRealtime()

        when (_uiState.value.view) {
            SnakeGameView.Ready -> {
                resultStateStore.clear()
                resultRecorded = false
                activeSessionId = newScoreSessionId()
                sessionStartedAt = LocalDateTime.now()
                urgeAfterRating = null
                lastRecordedSession = null
                lastSupportOutcome = null
                lastSupportElapsedMillis = 0L

                runtime.changeDirection(direction, now)

                if (runtime.state.phase != SnakeGamePhase.Playing) return

                _uiState.update {
                    it.copy(
                        view = SnakeGameView.Playing,
                        gameState = runtime.state,
                        elapsedDurationMillis = runtime.elapsedDurationMillis,
                        timeLeftSeconds = runtime.timeLeftSeconds,
                        result = null,
                        urgeAfterRating = null,
                    )
                }
            }

            SnakeGameView.Playing -> {
                runtime.changeDirection(direction, now)
                _uiState.update { it.copy(gameState = runtime.state) }
            }

            SnakeGameView.Paused,
            SnakeGameView.Result,
            SnakeGameView.Walked,
            -> Unit
        }
    }

    fun tick() {
        if (_uiState.value.view != SnakeGameView.Playing) return

        runtime.frame(SystemClock.elapsedRealtime())
        syncRuntimeState()

        // Once a Result exists it owns recovery, so stop checkpointing.
        if (runtime.state.phase == SnakeGamePhase.Playing) {
            checkpointActiveSupportElapsed()
        }

        finalizeTerminalResultIfNeeded()
    }

    fun pause() {
        if (_uiState.value.view != SnakeGameView.Playing) return

        runtime.pause(SystemClock.elapsedRealtime())
        syncRuntimeState()

        // A final frame may have ended the round; a result must not become Paused.
        if (runtime.state.phase == SnakeGamePhase.Finished) {
            finalizeTerminalResultIfNeeded()
            return
        }

        // Capture the latest foreground duration before the screen is left.
        checkpointActiveSupportElapsed(force = true)

        _uiState.update { it.copy(view = SnakeGameView.Paused) }
    }

    fun resume() {
        if (_uiState.value.view != SnakeGameView.Paused) return
        if (runtime.state.phase != SnakeGamePhase.Playing) return

        runtime.resume(SystemClock.elapsedRealtime())

        _uiState.update { it.copy(view = SnakeGameView.Playing) }
    }

    fun setUrgeBefore(rating: Int) {
        urgeBeforeRating = rating.coerceIn(0, 10)
    }

    fun setUrgeAfter(rating: Int) {
        val coerced = rating.coerceIn(0, 10)
        urgeAfterRating = coerced

        /*
         * Reflect the choice before touching persistence, so the selector still
         * responds even if no recorded session exists yet.
         */
        _uiState.update { it.copy(urgeAfterRating = coerced) }

        val previous = lastRecordedSession ?: return
        val updated = previous.copy(urgeAfter = coerced)
        lastRecordedSession = updated
        saveCurrentResultSnapshot()

        /*
         * Metadata-only rewrite: deliberately not routed through the commit
         * coordinator so the Safe Exit side effect cannot run twice.
         */
        viewModelScope.launch {
            scoreRepository.recordSession(updated)
        }
    }

    /**
     * The token the Task system will consume in SNAKE-04. Null unless this
     * round was a genuinely valid completion.
     */
    fun taskRewardCompletionToken(): String? {
        if (_uiState.value.result?.validCompletion != true) return null
        if (activeSessionId <= 0L) return null

        return "${ScoreGameType.Snake.id}:$activeSessionId"
    }

    fun walkAway() {
        if (!resultActionPersistenceReady()) return

        val result = _uiState.value.result ?: return
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis

        viewModelScope.launch {
            /*
             * Finish through the outcome that already resolved this step.
             * TimedOut/Abandoned/Failed must not be rewritten as Completed.
             */
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (!allowed) return@launch

            /*
             * The same session is rewritten as WalkedAway. The Snake score is
             * unchanged: there is no walk-away bonus, and an invalid attempt
             * stays invalid so it cannot become a reward loophole.
             */
            recordScoreSession(
                outcome = ScoreSessionOutcome.WalkedAway,
                result = result,
                refreshResultSnapshot = false,
            )

            _uiState.update { it.copy(view = SnakeGameView.Walked) }
        }
    }

    fun abandonSupportCycle() {
        // Report everything consumed, including pre-process-death time.
        val elapsed = totalSupportElapsedMillis()

        viewModelScope.launch {
            val allowed = resultActionCoordinator.abandon(elapsedDurationMillis = elapsed)

            // The checkpoint is retained for retry when the report is refused.
            if (allowed) {
                activeSupportCheckpointStore.clear()
                resetSupportElapsedTracking()
            }
        }
    }

    fun continueWithAnotherGame(onReady: () -> Unit) {
        if (!resultActionPersistenceReady()) return

        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis

        viewModelScope.launch {
            val allowed = resultActionCoordinator.continueWithAnotherGame(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (allowed) onReady()
        }
    }

    fun replayWithRemainingBudget(onReady: () -> Unit) {
        if (!resultActionPersistenceReady()) return

        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis

        viewModelScope.launch {
            val duration = resultActionCoordinator.prepareReplay(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
                requestedDurationMillis = SnakeStandaloneRoundDurationMillis,
            ) ?: return@launch

            roundDurationMillis = duration

            /*
             * A replay is a brand-new authoritative step, so the previous
             * step's checkpoint must never reduce it.
             */
            activeSupportCheckpointStore.clear()
            resetSupportElapsedTracking()

            if (activeLaunchContext is RecoveryGameLaunchContext.SupportCycle) {
                supportAuthoritativeAvailableMillis = duration
            }

            // The urge-before rating and loaded history survive a replay.
            resetSnakeRoundToReady(duration, _uiState.value.history)

            onReady()
        }
    }

    fun finishSupportCycleAfterChoice(onReady: () -> Unit) {
        // A stable result must be durable first; pre-result exits stay allowed.
        val current = _uiState.value
        if (current.view == SnakeGameView.Result && !current.isGameStoreResultDurable) {
            return
        }

        val hasStableResult = lastSupportOutcome != null
        val outcome = lastSupportOutcome ?: SupportCycleGameTerminalOutcome.Abandoned
        // Without a stable result, pre-process-death time must still be reported.
        val elapsed = if (hasStableResult) {
            lastSupportElapsedMillis
        } else {
            totalSupportElapsedMillis()
        }

        viewModelScope.launch {
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (!allowed) return@launch

            if (!hasStableResult) {
                activeSupportCheckpointStore.clear()
                resetSupportElapsedTracking()
            }

            onReady()
        }
    }

    private fun syncRuntimeState() {
        _uiState.update {
            it.copy(
                gameState = runtime.state,
                elapsedDurationMillis = runtime.elapsedDurationMillis,
                timeLeftSeconds = runtime.timeLeftSeconds,
            )
        }
    }

    /**
     * Total support time consumed: the pre-process-death baseline plus this
     * attempt. Deliberately distinct from the current attempt's elapsed time,
     * which alone governs Snake completion validity.
     */
    private fun totalSupportElapsedMillis(): Long {
        if (activeLaunchContext !is RecoveryGameLaunchContext.SupportCycle) {
            return runtime.elapsedDurationMillis
        }

        return SnakeSupportElapsedPolicy.totalConsumed(
            checkpointMillis = supportElapsedBaselineMillis,
            currentRoundElapsedMillis = runtime.elapsedDurationMillis,
            authoritativeAvailableMillis = supportAuthoritativeAvailableMillis,
        )
    }

    /** Persists consumed support time; never touches the board. */
    private fun checkpointActiveSupportElapsed(force: Boolean = false) {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return

        val total = totalSupportElapsedMillis()

        if (total <= 0L && lastCheckpointWrittenMillis <= 0L) return

        if (!force && total - lastCheckpointWrittenMillis < ActiveSupportCheckpointIntervalMillis) {
            return
        }

        activeSupportCheckpointStore.save(
            launch = launch,
            consumedSupportMillis = total,
        )
        lastCheckpointWrittenMillis = total
    }

    private fun resetSupportElapsedTracking() {
        supportElapsedBaselineMillis = 0L
        supportAuthoritativeAvailableMillis = 0L
        lastCheckpointWrittenMillis = 0L
    }

    /**
     * Resets the board and round-local session fields only. The support elapsed
     * baseline is deliberately preserved so a process-death Ready reconstruction
     * cannot hand consumed time back.
     */
    private fun resetSnakeRoundToReady(
        durationMillis: Long,
        history: SnakeGameHistory,
    ) {
        runtime.reset(roundDurationMillis = durationMillis)
        roundDurationMillis = durationMillis

        resultRecorded = false
        activeSessionId = 0L
        sessionStartedAt = null
        urgeAfterRating = null
        lastRecordedSession = null
        lastSupportOutcome = null
        lastSupportElapsedMillis = 0L

        _uiState.value = SnakeGameUiState(
            view = SnakeGameView.Ready,
            gameState = runtime.state,
            elapsedDurationMillis = 0L,
            timeLeftSeconds = runtime.timeLeftSeconds,
            result = null,
            history = history,
            urgeAfterRating = null,
            gameStorePersistenceState = SnakeGameStorePersistenceState.NotRequired,
        )
    }

    /** The single path from a terminal engine state to an Impulsive result. */
    private fun finalizeTerminalResultIfNeeded() {
        if (resultRecorded) return
        if (runtime.state.phase != SnakeGamePhase.Finished) return

        val endReason = runtime.state.endReason ?: return
        val score = runtime.state.score
        val fruitsEaten = runtime.state.fruitsEaten
        val elapsed = runtime.elapsedDurationMillis

        val validCompletion = SnakeGameCompletionPolicy.isValidCompletion(
            endReason = endReason,
            elapsedDurationMillis = elapsed,
            fruitsEaten = fruitsEaten,
        )

        // Read history before it is updated so the result shows the old marks.
        val oldHistory = _uiState.value.history

        // This result has not been rated yet.
        urgeAfterRating = null

        val result = SnakeGameResult(
            score = score,
            fruitsEaten = fruitsEaten,
            previousBest = oldHistory.personalBest,
            previousScore = oldHistory.previousScore,
            durationSec = (elapsed / 1_000L).toInt(),
            elapsedDurationMillis = elapsed,
            endReason = endReason,
            validCompletion = validCompletion,
        )

        val nextHistory = oldHistory.afterResult(
            score = score,
            validCompletion = validCompletion,
        )

        _uiState.update {
            it.copy(
                view = SnakeGameView.Result,
                gameState = runtime.state,
                elapsedDurationMillis = elapsed,
                timeLeftSeconds = runtime.timeLeftSeconds,
                result = result,
                history = nextHistory,
                // A new result has not asked for an after-rating yet.
                urgeAfterRating = null,
                // Actionable only once the store receipt is durable.
                gameStorePersistenceState = SnakeGameStorePersistenceState.Pending,
            )
        }

        resultRecorded = true

        val supportOutcome = SnakeGameSupportOutcomePolicy.terminalOutcome(
            endReason = endReason,
            validCompletion = validCompletion,
            supportCycle = activeLaunchContext is RecoveryGameLaunchContext.SupportCycle,
        )

        /*
         * The support cycle is told about *all* time consumed at this step,
         * including attempts lost to process death, while the Snake result
         * above reports only this attempt.
         */
        val supportElapsed = totalSupportElapsedMillis()

        lastSupportOutcome = supportOutcome
        lastSupportElapsedMillis = supportElapsed

        /*
         * Build the record before the snapshot so the snapshot it writes is
         * complete. The outcome and elapsed values above are already assigned,
         * so saveCurrentResultSnapshot() has everything it needs.
         */
        recordScoreSession(
            outcome = if (validCompletion) {
                ScoreSessionOutcome.Completed
            } else {
                ScoreSessionOutcome.Abandoned
            },
            result = result,
        )

        /*
         * The stable Result snapshot must exist before the active checkpoint is
         * released: exactly one process-death recovery authority stays live.
         */
        saveCurrentResultSnapshot()
        activeSupportCheckpointStore.clear()
        resetSupportElapsedTracking()

        if (validCompletion) {
            viewModelScope.launch {
                historyDataSource.save(nextHistory)
            }
        }

        // Keyed to the score session, so Walk Away cannot double-count it.
        beginGameStorePersistence(result = result, sessionId = activeSessionId)

        viewModelScope.launch {
            supportCycleRuntime.resolveForContinuation(
                outcome = supportOutcome,
                elapsedDurationMillis = supportElapsed,
            )
        }
    }

    private fun recordScoreSession(
        outcome: ScoreSessionOutcome,
        result: SnakeGameResult,
        refreshResultSnapshot: Boolean = true,
    ) {
        val sessionId = activeSessionId
        val startedAt = sessionStartedAt

        if (sessionId <= 0L || startedAt == null) return

        /*
         * A rewrite of the same session (urge-after, Walk Away) keeps the
         * original completion time rather than inventing a new one.
         */
        val completedAt = lastRecordedSession
            ?.takeIf { it.id == sessionId }
            ?.completedAt
            ?: LocalDateTime.now()

        val record = ScoreSessionRecord(
            id = sessionId,
            gameType = ScoreGameType.Snake,
            // Snake's score stays the pure engine score in every outcome.
            score = result.score,
            startedAt = startedAt,
            completedAt = completedAt,
            durationSec = result.durationSec,
            urgeBefore = urgeBeforeRating,
            urgeAfter = urgeAfterRating,
            outcome = outcome,
            validCompletion =
                result.validCompletion &&
                    outcome != ScoreSessionOutcome.Abandoned,
        )

        lastRecordedSession = record

        /*
         * Walk Away deliberately passes false: the action coordinator has
         * already cleared the Result snapshot on a successful finish, and
         * re-saving it here would resurrect state the coordinator owns.
         */
        if (refreshResultSnapshot) {
            saveCurrentResultSnapshot()
        }

        viewModelScope.launch {
            pivotGameSessionCommitCoordinator.commit(record)
        }
    }

    private fun saveCurrentResultSnapshot() {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return
        val outcome = lastSupportOutcome ?: return
        val state = _uiState.value
        val result = state.result ?: return
        val startedAt = sessionStartedAt ?: return

        if (state.view != SnakeGameView.Result) return
        if (activeSessionId <= 0L) return

        resultStateStore.save(
            RecoveryGameResultSnapshot(
                cycleId = launch.cycleId,
                decisionId = launch.decisionId,
                gameTypeId = ScoreGameType.Snake.id,
                supportOutcomeName = outcome.name,
                supportElapsedDurationMillis = lastSupportElapsedMillis.coerceAtLeast(0L),
                activeSessionId = activeSessionId,
                sessionStartedAtIso = startedAt.toString(),
                urgeBeforeRating = urgeBeforeRating,
                urgeAfterRating = urgeAfterRating,
                lastRecordedSession = lastRecordedSession?.let { ScoreSessionSnapshot.from(it) },
                payload = RecoveryGameResultPayload.Snake(
                    historyPersonalBest = state.history.personalBest,
                    historyPreviousScore = state.history.previousScore,
                    resultScore = result.score,
                    resultFruitsEaten = result.fruitsEaten,
                    resultPreviousBest = result.previousBest,
                    resultPreviousScore = result.previousScore,
                    resultDurationSec = result.durationSec,
                    resultElapsedDurationMillis = result.elapsedDurationMillis,
                    resultEndReasonName = result.endReason.name,
                    resultValidCompletion = result.validCompletion,
                ),
            ),
        )
    }

    /**
     * Rebuilds a stable result screen after process death. Every field is
     * re-validated; the transient board is never reconstructed.
     */
    private fun restoreResultSnapshot(
        snapshot: RecoveryGameResultSnapshot,
    ): Boolean {
        val payload = snapshot.payload as? RecoveryGameResultPayload.Snake ?: return false
        val startedAt = snapshot.sessionStartedAtOrNull() ?: return false
        val outcome = snapshot.supportOutcomeOrNull() ?: return false

        val endReason = SnakeRoundEndReason.entries
            .firstOrNull { it.name == payload.resultEndReasonName }
            ?: return false

        if (snapshot.activeSessionId <= 0L) return false
        if (snapshot.supportElapsedDurationMillis < 0L) return false
        if (payload.historyPersonalBest < 0) return false
        if (payload.historyPreviousScore != null && payload.historyPreviousScore < 0) return false
        if (payload.resultScore < 0) return false
        if (payload.resultFruitsEaten < 0) return false
        if (payload.resultPreviousBest < 0) return false
        if (payload.resultPreviousScore != null && payload.resultPreviousScore < 0) return false
        if (payload.resultDurationSec < 0) return false
        if (payload.resultElapsedDurationMillis < 0L) return false

        /*
         * A trusted Snake Result always carries its recorded session: the
         * persistence repair needs it, and its absence means the snapshot is
         * not a complete result.
         */
        val restoredSession = snapshot.lastRecordedSession?.toRecordOrNull() ?: return false

        if (restoredSession.gameType != ScoreGameType.Snake) return false
        if (restoredSession.id != snapshot.activeSessionId) return false
        if (restoredSession.score != payload.resultScore) return false
        if (restoredSession.durationSec != payload.resultDurationSec) return false
        if (restoredSession.validCompletion != payload.resultValidCompletion) return false

        /*
         * A retained Result snapshot only ever holds the initial recording.
         * Walk Away clears snapshot ownership, so WalkedAway here is corrupt.
         */
        val expectedOutcome = if (payload.resultValidCompletion) {
            ScoreSessionOutcome.Completed
        } else {
            ScoreSessionOutcome.Abandoned
        }

        if (restoredSession.outcome != expectedOutcome) return false

        /*
         * The persisted completion flag is recomputed rather than trusted, so a
         * forged snapshot cannot promote an invalid attempt to a valid one.
         */
        val expectedValid = SnakeGameCompletionPolicy.isValidCompletion(
            endReason = endReason,
            elapsedDurationMillis = payload.resultElapsedDurationMillis,
            fruitsEaten = payload.resultFruitsEaten,
        )

        if (expectedValid != payload.resultValidCompletion) return false

        /*
         * The saved history must be exactly what applying this result to the
         * pre-result marks produces, so forged history cannot ride along on an
         * otherwise trusted snapshot.
         */
        val oldHistory = SnakeGameHistory(
            personalBest = payload.resultPreviousBest,
            previousScore = payload.resultPreviousScore,
        )
        val expectedHistory = oldHistory.afterResult(
            score = payload.resultScore,
            validCompletion = payload.resultValidCompletion,
        )

        if (
            expectedHistory != SnakeGameHistory(
                personalBest = payload.historyPersonalBest,
                previousScore = payload.historyPreviousScore,
            )
        ) {
            return false
        }

        activeSessionId = snapshot.activeSessionId
        sessionStartedAt = startedAt
        urgeBeforeRating = snapshot.urgeBeforeRating
        urgeAfterRating = snapshot.urgeAfterRating
        lastRecordedSession = restoredSession
        lastSupportOutcome = outcome
        lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis
        resultRecorded = true

        runtime.reset(roundDurationMillis = roundDurationMillis)

        /*
         * Support time left comes from total consumption at this step, not from
         * this attempt's duration: after a process-death restart those differ.
         */
        val timeLeftSeconds = (
            (roundDurationMillis - snapshot.supportElapsedDurationMillis)
                .coerceAtLeast(0L) + 999L
            ).div(1_000L).toInt()

        _uiState.value = SnakeGameUiState(
            view = SnakeGameView.Result,
            gameState = null,
            elapsedDurationMillis = payload.resultElapsedDurationMillis,
            timeLeftSeconds = timeLeftSeconds,
            result = SnakeGameResult(
                score = payload.resultScore,
                fruitsEaten = payload.resultFruitsEaten,
                previousBest = payload.resultPreviousBest,
                previousScore = payload.resultPreviousScore,
                durationSec = payload.resultDurationSec,
                elapsedDurationMillis = payload.resultElapsedDurationMillis,
                endReason = endReason,
                validCompletion = payload.resultValidCompletion,
            ),
            history = SnakeGameHistory(
                personalBest = payload.historyPersonalBest,
                previousScore = payload.historyPreviousScore,
            ),
            // Restores the visible selection to match the persisted session.
            urgeAfterRating = snapshot.urgeAfterRating,
            // The receipt must be reconfirmed after process recreation.
            gameStorePersistenceState = SnakeGameStorePersistenceState.Pending,
        )

        return true
    }

    private companion object {
        /**
         * Active support time is checkpointed about once a second, and exactly
         * on pause. This is a scalar duration write, never board state.
         */
        const val ActiveSupportCheckpointIntervalMillis = 1_000L
    }
}
