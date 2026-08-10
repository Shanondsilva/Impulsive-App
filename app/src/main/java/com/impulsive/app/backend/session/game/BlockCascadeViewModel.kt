package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.BlockCascadeBag
import com.impulsive.app.backend.domain.game.BlockCascadeGameState
import com.impulsive.app.backend.domain.game.BlockCascadeMinimumLines
import com.impulsive.app.backend.domain.game.BlockCascadeMinimumMoves
import com.impulsive.app.backend.domain.game.BlockCascadeRoundSeconds
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.canMoveDown
import com.impulsive.app.backend.domain.game.hardDropPiece
import com.impulsive.app.backend.domain.game.lockAndAdvance
import com.impulsive.app.backend.domain.game.movePiece
import com.impulsive.app.backend.domain.game.newBlockCascadeState
import com.impulsive.app.backend.domain.game.rotatePiece
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class BlockCascadeView {
    Ready,
    Playing,
    Paused,
    Result,
}

data class BlockCascadeUiState(
    val view: BlockCascadeView = BlockCascadeView.Ready,
    val gameState: BlockCascadeGameState? = null,
    val secondsPlayed: Int = 0,
    val linesCleared: Int = 0,
    val validMoves: Int = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val failureReason: String? = null,
)

class BlockCascadeViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val supportCycleRuntime = RecoveryGameSupportCycleRuntime(application)
    private val resultStateStore = RecoveryGameResultStateStore(savedStateHandle)
    private val resultActionCoordinator = RecoveryGameResultActionCoordinator(
        runtime = supportCycleRuntime,
        clearResultState = { resultStateStore.clear() },
    )
    private val scoreRepository = ScoreRepository(application)
    private val gameStoreManager = com.impulsive.app.backend.data.repository.GameStoreManager(application)
    private val _uiState = MutableStateFlow(BlockCascadeUiState())
    val uiState: StateFlow<BlockCascadeUiState> = _uiState

    private var bag = BlockCascadeBag()
    private var resumed = false
    private var lastFrameMs: Long? = null
    private var accumulatedForegroundMs = 0L
    private var fallAccumulatorMs = 0L
    private var resultRecorded = false
    private var activeSessionId: Long = newScoreSessionId()
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()
    private var urgeBeforeRating: Int? = null
    private var urgeAfterRating: Int? = null
    private var lastRecordedSession: ScoreSessionRecord? = null
    private var roundDurationMillis = BlockCascadeRoundSeconds * 1_000L
    private var lastSupportOutcome: SupportCycleGameTerminalOutcome? = null
    private var lastSupportElapsedMillis: Long = 0L
    private var activeLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone

    suspend fun configureLaunchContext(launchContext: RecoveryGameLaunchContext): Boolean {
        val binding = supportCycleRuntime.bindWithRecovery(
            requested = launchContext,
            standaloneDurationMillis = BlockCascadeRoundSeconds * 1_000L,
        ) ?: return false

        roundDurationMillis = binding.durationMillis
        activeLaunchContext = launchContext

        val snapshot = resultStateStore.restore(
            launchContext = launchContext,
            expectedGameType = ScoreGameType.BlockCascade,
        )

        val authoritativeResult = binding.resolvedStep

        /*
         * The repository already contains a terminal game step. Restore only the
         * matching stable Result presentation.
         */
        if (authoritativeResult != null) {
            val snapshotOutcome = snapshot?.supportOutcomeOrNull()

            if (
                snapshot == null ||
                snapshotOutcome != authoritativeResult.outcome ||
                !restoreResultSnapshot(snapshot)
            ) {
                /*
                 * A terminal authoritative step without a trusted presentation
                 * cannot be restarted as a fresh game. Finish its existing cycle
                 * through the recorded outcome and leave through the established
                 * unavailable-binding path.
                 */
                resultStateStore.clear()

                supportCycleRuntime.resolveAndEnd(
                    outcome = authoritativeResult.outcome,
                    elapsedDurationMillis = authoritativeResult.elapsedDurationMillis,
                )

                return false
            }

            lastSupportOutcome = authoritativeResult.outcome
            lastSupportElapsedMillis = authoritativeResult.elapsedDurationMillis

            return true
        }

        /*
         * The snapshot may have been written immediately before process death,
         * while the asynchronous authoritative terminal mutation was still
         * incomplete.
         */
        if (snapshot != null) {
            val snapshotOutcome = snapshot.supportOutcomeOrNull()

            if (snapshotOutcome == null || !restoreResultSnapshot(snapshot)) {
                /*
                 * The repository still owns an InProgress step. A corrupt
                 * presentation snapshot must not resolve or replace it.
                 */
                resultStateStore.clear()

                return true
            }

            lastSupportOutcome = snapshotOutcome
            lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis.coerceAtLeast(0L)

            val resolution = supportCycleRuntime.resolveForContinuation(
                outcome = snapshotOutcome,
                elapsedDurationMillis = lastSupportElapsedMillis,
            )

            if (!resolution.keepsRestoredResultVisible) {
                /*
                 * Non-retryable conflicts remain fail-closed. Do not delete the
                 * valid result snapshot here; it remains available for
                 * authoritative reconciliation and diagnosis.
                 */
                return false
            }

            return true
        }

        return true
    }

    fun abandonSupportCycle() {
        val elapsed = accumulatedForegroundMs.coerceAtLeast(0L)

        viewModelScope.launch {
            resultActionCoordinator.abandon(elapsedDurationMillis = elapsed)
        }
    }

    fun continueWithAnotherGame(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        viewModelScope.launch {
            val allowed = resultActionCoordinator.continueWithAnotherGame(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (allowed) {
                onReady()
            }
        }
    }

    fun replayWithRemainingBudget(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        viewModelScope.launch {
            val duration = resultActionCoordinator.prepareReplay(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
                requestedDurationMillis = BlockCascadeRoundSeconds * 1_000L,
            ) ?: return@launch

            roundDurationMillis = duration
            lastSupportOutcome = null
            lastSupportElapsedMillis = 0L
            onReady()
        }
    }

    fun finishSupportCycleAfterChoice(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: SupportCycleGameTerminalOutcome.Abandoned
        val elapsed = if (lastSupportOutcome == null) {
            accumulatedForegroundMs.coerceAtLeast(0L)
        } else {
            lastSupportElapsedMillis
        }
        viewModelScope.launch {
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (allowed) {
                onReady()
            }
        }
    }

    private fun roundDurationSeconds(): Int =
        ((roundDurationMillis + 999L) / 1_000L).toInt().coerceAtLeast(1)

    fun start() {
        resultStateStore.clear()
        bag = BlockCascadeBag(seed = System.nanoTime() xor SystemClock.elapsedRealtimeNanos())
        resultRecorded = false
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
        urgeAfterRating = null
        val state = newBlockCascadeState(bag)
        accumulatedForegroundMs = 0L
        fallAccumulatorMs = 0L
        lastFrameMs = null
        resumed = true
        _uiState.value = BlockCascadeUiState(
            view = BlockCascadeView.Playing,
            gameState = state,
            completed = false,
            failed = false,
            failureReason = null,
        )
    }

    fun moveLeft() {
        move(dx = -1, dy = 0)
    }

    fun moveRight() {
        move(dx = 1, dy = 0)
    }

    fun rotate() {
        val current = _uiState.value
        if (current.view != BlockCascadeView.Playing) return
        val game = current.gameState ?: return
        val (updated, changed) = rotatePiece(game)
        if (changed) {
            _uiState.update {
                it.copy(
                    gameState = updated,
                    validMoves = it.validMoves + 1,
                )
            }
        }
    }

    fun softDrop() {
        val current = _uiState.value
        if (current.view != BlockCascadeView.Playing) return
        val game = current.gameState ?: return
        val droppedPiece = hardDropPiece(game.board, game.activePiece)
        if (droppedPiece == game.activePiece) {
            lockActivePiece()
        } else {
            val droppedState = game.copy(activePiece = droppedPiece)
            val updated = lockAndAdvance(droppedState, bag)
            _uiState.update {
                it.copy(
                    gameState = updated,
                    validMoves = it.validMoves + 1,
                    linesCleared = updated.linesCleared,
                )
            }
            checkCompletion()
        }
    }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFrameMs
        lastFrameMs = now

        val current = _uiState.value
        if (current.view != BlockCascadeView.Playing || !resumed || current.completed) return
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 100L)
        if (delta <= 0L) return

        accumulatedForegroundMs += delta
        fallAccumulatorMs += delta

        val nextSeconds = (accumulatedForegroundMs / 1_000L).toInt()
        if (nextSeconds != current.secondsPlayed) {
            _uiState.update { it.copy(secondsPlayed = nextSeconds) }
        }

        val interval = fallIntervalFor(nextSeconds)
        while (fallAccumulatorMs >= interval && _uiState.value.view == BlockCascadeView.Playing) {
            fallAccumulatorMs -= interval
            gravityStep()
        }

        checkCompletion()
    }

    fun pause() {
        if (_uiState.value.view == BlockCascadeView.Playing) {
            _uiState.update { it.copy(view = BlockCascadeView.Paused) }
        }
        resumed = false
        lastFrameMs = null
    }

    fun resume() {
        if (_uiState.value.view == BlockCascadeView.Paused) {
            _uiState.update { it.copy(view = BlockCascadeView.Playing) }
        }
        resumed = true
        lastFrameMs = null
    }

    fun setUrgeBefore(rating: Int) {
        urgeBeforeRating = rating.coerceIn(0, 10)
    }

    fun taskRewardCompletionToken(): String =
        "${ScoreGameType.BlockCascade.id}:$activeSessionId"

    /**
     * Captures the post-game rating. The session has already been recorded by
     * the time the Result panel shows, so this re-records the same session id
     * with the rating attached. It calls the repository directly so the game
     * store play counter is not incremented a second time.
     */
    fun setUrgeAfter(rating: Int) {
        val coerced = rating.coerceIn(0, 10)
        urgeAfterRating = coerced
        val recorded = lastRecordedSession ?: return
        val updated = recorded.copy(urgeAfter = coerced)
        lastRecordedSession = updated
        saveCurrentResultSnapshot()
        viewModelScope.launch { scoreRepository.recordSession(updated) }
    }

    fun recordCurrentResult(outcome: ScoreSessionOutcome) {
        val state = _uiState.value
        if (state.view != BlockCascadeView.Result) return
        val record = ScoreSessionRecord(
            id = activeSessionId,
            gameType = ScoreGameType.BlockCascade,
            score = state.blockCascadeScore().coerceAtLeast(0),
            startedAt = sessionStartedAt,
            completedAt = LocalDateTime.now(),
            durationSec = state.secondsPlayed.coerceAtLeast(0),
            urgeBefore = urgeBeforeRating,
            urgeAfter = urgeAfterRating,
            outcome = outcome,
            validCompletion = state.completed,
        )
        lastRecordedSession = record
        viewModelScope.launch {
            scoreRepository.recordSession(record)
            if (outcome != ScoreSessionOutcome.WalkedAway) {
                gameStoreManager.recordPlay(gameId = "BLOCK_CASCADE", won = outcome == ScoreSessionOutcome.Completed)
            }
        }
    }

    private fun move(dx: Int, dy: Int) {
        val current = _uiState.value
        if (current.view != BlockCascadeView.Playing) return
        val game = current.gameState ?: return
        val (updated, changed) = movePiece(game, dx = dx, dy = dy)
        if (changed) {
            _uiState.update {
                it.copy(
                    gameState = updated,
                    validMoves = it.validMoves + 1,
                )
            }
        }
    }

    private fun gravityStep() {
        val game = _uiState.value.gameState ?: return
        if (canMoveDown(game.board, game.activePiece)) {
            val (updated, changed) = movePiece(game, dx = 0, dy = 1)
            if (changed) {
                _uiState.update { it.copy(gameState = updated) }
            }
        } else {
            lockActivePiece()
        }
    }

    private fun lockActivePiece() {
        val current = _uiState.value
        val game = current.gameState ?: return
        val updated = lockAndAdvance(game, bag)
        _uiState.update {
            it.copy(
                gameState = updated,
                linesCleared = updated.linesCleared,
            )
        }
        checkCompletion()
    }

    private fun checkCompletion() {
        val state = _uiState.value
        val enoughActivity = state.linesCleared >= BlockCascadeMinimumLines ||
            state.validMoves >= BlockCascadeMinimumMoves
        if (state.gameState?.topOut == true) {
            _uiState.update {
                it.copy(
                    view = BlockCascadeView.Result,
                    completed = false,
                    failed = true,
                    failureReason = "The board filled up. Reset the round and try again.",
                )
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(ScoreSessionOutcome.Abandoned)
            lastSupportOutcome = SupportCycleGameTerminalOutcome.Abandoned
            lastSupportElapsedMillis = accumulatedForegroundMs

            /*
             * Save the stable result before the asynchronous authoritative support-cycle
             * mutation. Active gameplay state is deliberately excluded.
             */
            saveCurrentResultSnapshot()

            viewModelScope.launch {
                supportCycleRuntime.resolveForContinuation(
                    SupportCycleGameTerminalOutcome.Abandoned,
                    accumulatedForegroundMs,
                )
            }
            return
        }

        if (state.secondsPlayed >= roundDurationSeconds()) {
            val completed = enoughActivity
            _uiState.update {
                if (completed) {
                    it.copy(
                        view = BlockCascadeView.Result,
                        completed = true,
                        failed = false,
                        failureReason = null,
                    )
                } else {
                    it.copy(
                        view = BlockCascadeView.Result,
                        completed = false,
                        failed = true,
                        failureReason = "Round ended. Complete more moves or clear more lines to earn the reward.",
                    )
                }
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(if (completed) ScoreSessionOutcome.Completed else ScoreSessionOutcome.Abandoned)
            val supportOutcome = when {
                roundDurationMillis < BlockCascadeRoundSeconds * 1_000L ->
                    SupportCycleGameTerminalOutcome.TimedOut
                completed -> SupportCycleGameTerminalOutcome.Completed
                else -> SupportCycleGameTerminalOutcome.Failed
            }
            lastSupportOutcome = supportOutcome
            lastSupportElapsedMillis = accumulatedForegroundMs

            saveCurrentResultSnapshot()

            viewModelScope.launch {
                supportCycleRuntime.resolveForContinuation(
                    supportOutcome,
                    accumulatedForegroundMs,
                )
            }
        }
    }

    private fun saveCurrentResultSnapshot() {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return
        val outcome = lastSupportOutcome ?: return
        val state = _uiState.value

        if (state.view != BlockCascadeView.Result) {
            return
        }

        resultStateStore.save(
            RecoveryGameResultSnapshot(
                cycleId = launch.cycleId,
                decisionId = launch.decisionId,
                gameTypeId = ScoreGameType.BlockCascade.id,
                supportOutcomeName = outcome.name,
                supportElapsedDurationMillis = lastSupportElapsedMillis.coerceAtLeast(0L),
                activeSessionId = activeSessionId,
                sessionStartedAtIso = sessionStartedAt.toString(),
                urgeBeforeRating = urgeBeforeRating,
                urgeAfterRating = urgeAfterRating,
                lastRecordedSession = lastRecordedSession?.let { ScoreSessionSnapshot.from(it) },
                payload = RecoveryGameResultPayload.BlockCascade(
                    secondsPlayed = state.secondsPlayed,
                    linesCleared = state.linesCleared,
                    validMoves = state.validMoves,
                    completed = state.completed,
                    failed = state.failed,
                    failureReason = state.failureReason,
                ),
            ),
        )
    }

    private fun restoreResultSnapshot(snapshot: RecoveryGameResultSnapshot): Boolean {
        val payload = snapshot.payload as? RecoveryGameResultPayload.BlockCascade ?: return false
        val restoredSessionStart = snapshot.sessionStartedAtOrNull() ?: return false
        val restoredOutcome = snapshot.supportOutcomeOrNull() ?: return false
        val restoredRecordedSession = snapshot.lastRecordedSession?.toRecordOrNull()

        if (snapshot.lastRecordedSession != null && restoredRecordedSession == null) {
            return false
        }

        /*
         * Every legitimate Block Cascade result is either completed or failed,
         * never both and never neither.
         */
        if (
            snapshot.activeSessionId <= 0L ||
            snapshot.supportElapsedDurationMillis < 0L ||
            payload.secondsPlayed < 0 ||
            payload.linesCleared < 0 ||
            payload.validMoves < 0 ||
            payload.completed == payload.failed
        ) {
            return false
        }

        activeSessionId = snapshot.activeSessionId
        sessionStartedAt = restoredSessionStart
        urgeBeforeRating = snapshot.urgeBeforeRating?.coerceIn(0, 10)
        urgeAfterRating = snapshot.urgeAfterRating?.coerceIn(0, 10)
        lastRecordedSession = restoredRecordedSession
        lastSupportOutcome = restoredOutcome
        lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis
        accumulatedForegroundMs = snapshot.supportElapsedDurationMillis
        resumed = false
        lastFrameMs = null
        fallAccumulatorMs = 0L

        /*
         * Never reconstruct the board or active falling piece after process death.
         * The Result panel only requires these stable summary fields.
         */
        _uiState.value = BlockCascadeUiState(
            view = BlockCascadeView.Result,
            gameState = null,
            secondsPlayed = payload.secondsPlayed,
            linesCleared = payload.linesCleared,
            validMoves = payload.validMoves,
            completed = payload.completed,
            failed = payload.failed,
            failureReason = payload.failureReason,
        )

        return true
    }

    private fun fallIntervalFor(secondsPlayed: Int): Long {
        val speedStep = secondsPlayed / 20
        return (820L - speedStep * 70L).coerceAtLeast(280L)
    }

    private fun BlockCascadeUiState.blockCascadeScore(): Int {
        val activityScore = validMoves.coerceAtLeast(0) * 8
        val lineScore = linesCleared.coerceAtLeast(0) * 250
        val timeScore = secondsPlayed.coerceAtLeast(0) * 2
        val finishBonus = if (completed) 400 else 0
        return activityScore + lineScore + timeScore + finishBonus
    }
}
