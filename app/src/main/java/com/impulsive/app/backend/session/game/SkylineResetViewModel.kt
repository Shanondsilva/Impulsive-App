package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.GameStoreManager
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.StackBlock
import com.impulsive.app.backend.domain.game.StackBlockHeight
import com.impulsive.app.backend.domain.game.StackDropResult
import com.impulsive.app.backend.domain.game.StackMoveBound
import com.impulsive.app.backend.domain.game.StackPerPerfectControlPoints
import com.impulsive.app.backend.domain.game.StackRoundSeconds
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.newStackBaseBlock
import com.impulsive.app.backend.domain.game.resolveStackDrop
import com.impulsive.app.backend.domain.game.stackAxisIsX
import com.impulsive.app.backend.domain.game.stackHueFor
import com.impulsive.app.backend.domain.game.stackSpeedFor
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.random.Random

enum class SkylineResetView {
    Ready,
    Playing,
    Paused,
    Result,
}

data class SkylineResetUiState(
    val view: SkylineResetView = SkylineResetView.Ready,
    val blocks: List<StackBlock> = emptyList(),
    val activeIndex: Int = 1,
    val activeX: Float = 0f,
    val activeZ: Float = 0f,
    val activeWidth: Float = 0f,
    val activeDepth: Float = 0f,
    val activeAxisIsX: Boolean = true,
    val activeDir: Int = 1,
    val activeHue: Int = stackHueFor(1),
    val floorsBuilt: Int = 0,
    val perfectCount: Int = 0,
    val secondsPlayed: Int = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val dropSeq: Int = 0,
    val lastDropResult: StackDropResult? = null,
    val choppedPresent: Boolean = false,
    val choppedX: Float = 0f,
    val choppedZ: Float = 0f,
    val choppedWidth: Float = 0f,
    val choppedDepth: Float = 0f,
    val choppedY: Float = 0f,
    val choppedDir: Int = 0,
    val choppedAxisIsX: Boolean = true,
    val choppedHue: Int = 0,
    val controlPointsBanked: Int? = null,
)

class SkylineResetViewModel(
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
    private val gameStoreManager = GameStoreManager(application)
    private val _uiState = MutableStateFlow(SkylineResetUiState())
    val uiState: StateFlow<SkylineResetUiState> = _uiState

    private var resumed = false
    private var lastFrameMs: Long? = null
    private var accumulatedForegroundMs = 0L
    private var resultRecorded = false
    private var perfectPointsBanked = false
    private var urgeSpeedFactor = 1f
    private var activeSessionId: Long = newScoreSessionId()
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()
    private var urgeBeforeRating: Int? = null
    private var urgeAfterRating: Int? = null
    private var lastRecordedSession: ScoreSessionRecord? = null
    private var roundDurationMillis = StackRoundSeconds * 1_000L
    private var lastSupportOutcome: SupportCycleGameTerminalOutcome? = null
    private var lastSupportElapsedMillis: Long = 0L
    private var activeLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone

    suspend fun configureLaunchContext(launchContext: RecoveryGameLaunchContext): Boolean {
        val binding = supportCycleRuntime.bindWithRecovery(
            requested = launchContext,
            standaloneDurationMillis = StackRoundSeconds * 1_000L,
        ) ?: return false

        roundDurationMillis = binding.durationMillis
        activeLaunchContext = launchContext

        val snapshot = resultStateStore.restore(
            launchContext = launchContext,
            expectedGameType = ScoreGameType.SkylineReset,
        )

        val authoritativeResult = binding.resolvedStep

        /*
         * The repository already contains a terminal game step. Restore only a
         * trusted matching Result presentation.
         */
        if (authoritativeResult != null) {
            val snapshotOutcome = snapshot?.supportOutcomeOrNull()

            if (
                snapshot == null ||
                snapshotOutcome != authoritativeResult.outcome ||
                !restoreResultSnapshot(snapshot)
            ) {
                /*
                 * A terminal authoritative step must never restart as a fresh
                 * Skyline round. Finalise it through its recorded outcome and
                 * leave through the existing unavailable-binding path.
                 */
                resultStateStore.clear()

                supportCycleRuntime.resolveAndEnd(
                    outcome = authoritativeResult.outcome,
                    elapsedDurationMillis = authoritativeResult.elapsedDurationMillis,
                )

                return false
            }

            /*
             * The persisted support-cycle step remains authoritative for terminal
             * outcome and consumed duration.
             */
            lastSupportOutcome = authoritativeResult.outcome
            lastSupportElapsedMillis = authoritativeResult.elapsedDurationMillis

            return true
        }

        /*
         * This covers process death after the stable result snapshot was written
         * but before asynchronous terminal-step persistence completed.
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
                 * OutcomeConflict and other non-retryable failures remain
                 * fail-closed. Preserve the valid snapshot for authoritative
                 * reconciliation instead of silently deleting the user's result.
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
                requestedDurationMillis = StackRoundSeconds * 1_000L,
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
        resultRecorded = false
        perfectPointsBanked = false
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
        urgeAfterRating = null
        accumulatedForegroundMs = 0L
        lastFrameMs = null
        resumed = true
        urgeSpeedFactor = stackUrgeSpeedFactor(urgeBeforeRating)
        val base = newStackBaseBlock()
        val firstIndex = 1
        val axisIsX = stackAxisIsX(firstIndex)
        val startPositive = Random.nextBoolean()
        val startPos = if (startPositive) StackMoveBound else -StackMoveBound
        _uiState.value = SkylineResetUiState(
            view = SkylineResetView.Playing,
            blocks = listOf(base),
            activeIndex = firstIndex,
            activeX = if (axisIsX) startPos else base.x,
            activeZ = if (axisIsX) base.z else startPos,
            activeWidth = base.width,
            activeDepth = base.depth,
            activeAxisIsX = axisIsX,
            activeDir = if (startPositive) -1 else 1,
            activeHue = stackHueFor(firstIndex),
        )
    }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFrameMs
        lastFrameMs = now
        val current = _uiState.value
        if (current.view != SkylineResetView.Playing || !resumed) return
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 100L)
        if (delta <= 0L) return

        accumulatedForegroundMs += delta
        val nextSeconds = (accumulatedForegroundMs / 1_000L).toInt()

        if (nextSeconds >= roundDurationSeconds()) {
            _uiState.update {
                it.copy(
                    view = SkylineResetView.Result,
                    completed = true,
                    secondsPlayed = roundDurationSeconds(),
                )
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(ScoreSessionOutcome.Completed)
            val supportOutcome = if (roundDurationMillis < StackRoundSeconds * 1_000L) {
                SupportCycleGameTerminalOutcome.TimedOut
            } else {
                SupportCycleGameTerminalOutcome.Completed
            }
            lastSupportOutcome = supportOutcome
            lastSupportElapsedMillis = accumulatedForegroundMs

            /*
             * Save the stable Result presentation before the asynchronous authoritative
             * support-cycle mutation. Active tower and animation state are excluded.
             */
            saveCurrentResultSnapshot()

            viewModelScope.launch {
                supportCycleRuntime.resolveForContinuation(
                    supportOutcome,
                    accumulatedForegroundMs,
                )
            }
            return
        }

        val speed = stackSpeedFor(
            floorsBuilt = current.floorsBuilt,
            perfectCount = current.perfectCount,
        ) * urgeSpeedFactor
        val step = current.activeDir * speed * (delta / 1_000f)
        var pos = if (current.activeAxisIsX) current.activeX else current.activeZ
        var dir = current.activeDir
        pos += step
        if (pos >= StackMoveBound) {
            pos = StackMoveBound
            dir = -1
        } else if (pos <= -StackMoveBound) {
            pos = -StackMoveBound
            dir = 1
        }

        _uiState.update {
            it.copy(
                activeX = if (it.activeAxisIsX) pos else it.activeX,
                activeZ = if (it.activeAxisIsX) it.activeZ else pos,
                activeDir = dir,
                secondsPlayed = nextSeconds,
            )
        }
    }

    fun drop() {
        val current = _uiState.value
        if (current.view != SkylineResetView.Playing) return
        val top = current.blocks.lastOrNull() ?: return
        val outcome = resolveStackDrop(top, current.activeX, current.activeZ, current.activeHue)

        if (outcome.result == StackDropResult.Missed) {
            _uiState.update {
                it.copy(
                    view = SkylineResetView.Result,
                    failed = true,
                    completed = false,
                    dropSeq = it.dropSeq + 1,
                    lastDropResult = StackDropResult.Missed,
                    choppedPresent = false,
                )
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(ScoreSessionOutcome.Abandoned)
            lastSupportOutcome = SupportCycleGameTerminalOutcome.Abandoned
            lastSupportElapsedMillis = accumulatedForegroundMs

            saveCurrentResultSnapshot()

            viewModelScope.launch {
                supportCycleRuntime.resolveForContinuation(
                    SupportCycleGameTerminalOutcome.Abandoned,
                    accumulatedForegroundMs,
                )
            }
            return
        }

        val placed = outcome.placed ?: return
        val nextIndex = placed.index + 1
        val nextAxisIsX = stackAxisIsX(nextIndex)
        val startPositive = Random.nextBoolean()
        val startPos = if (startPositive) StackMoveBound else -StackMoveBound
        val choppedBottomY = placed.index * StackBlockHeight

        _uiState.update {
            it.copy(
                blocks = it.blocks + placed,
                floorsBuilt = it.floorsBuilt + 1,
                perfectCount = it.perfectCount + if (outcome.result == StackDropResult.Perfect) 1 else 0,
                activeIndex = nextIndex,
                activeWidth = placed.width,
                activeDepth = placed.depth,
                activeAxisIsX = nextAxisIsX,
                activeX = if (nextAxisIsX) startPos else placed.x,
                activeZ = if (nextAxisIsX) placed.z else startPos,
                activeDir = if (startPositive) -1 else 1,
                activeHue = stackHueFor(nextIndex),
                dropSeq = it.dropSeq + 1,
                lastDropResult = outcome.result,
                choppedPresent = outcome.choppedPresent,
                choppedX = outcome.choppedX,
                choppedZ = outcome.choppedZ,
                choppedWidth = outcome.choppedWidth,
                choppedDepth = outcome.choppedDepth,
                choppedY = choppedBottomY,
                choppedDir = outcome.choppedDir,
                choppedAxisIsX = outcome.axisIsX,
                choppedHue = placed.hue,
            )
        }
    }

    fun pause() {
        if (_uiState.value.view == SkylineResetView.Playing) {
            _uiState.update { it.copy(view = SkylineResetView.Paused) }
        }
        resumed = false
        lastFrameMs = null
    }

    fun resume() {
        if (_uiState.value.view == SkylineResetView.Paused) {
            _uiState.update { it.copy(view = SkylineResetView.Playing) }
        }
        resumed = true
        lastFrameMs = null
    }

    /** Banks perfect-drop control points, gated to once every seven days. Call only on a Task-flow survival. */
    fun bankPerfectControlPoints() {
        if (perfectPointsBanked) return
        perfectPointsBanked = true
        val points = _uiState.value.perfectCount.coerceAtLeast(0) * StackPerPerfectControlPoints
        if (points <= 0) {
            _uiState.update { it.copy(controlPointsBanked = 0) }
            saveCurrentResultSnapshot()
            return
        }
        viewModelScope.launch {
            val awarded = gameStoreManager.tryAwardWeekly(key = "skyline_perfect", points = points)
            _uiState.update { it.copy(controlPointsBanked = if (awarded) points else 0) }
            saveCurrentResultSnapshot()
        }
    }

    private fun stackUrgeSpeedFactor(urge: Int?): Float {
        val rating = (urge ?: 5).coerceIn(0, 10)
        return 1f + (rating / 10f) * 0.30f
    }

    fun setUrgeBefore(rating: Int) {
        urgeBeforeRating = rating.coerceIn(0, 10)
    }

    fun taskRewardCompletionToken(): String =
        "${ScoreGameType.SkylineReset.id}:$activeSessionId"

    /**
     * Captures the post-game rating. SkyStack records its session once (guarded
     * by resultRecorded), so this re-records the same session id with the rating
     * attached. It calls the repository directly so the game store play counter
     * is not incremented a second time.
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
        if (resultRecorded) return
        resultRecorded = true
        val state = _uiState.value
        val record = ScoreSessionRecord(
            id = activeSessionId,
            gameType = ScoreGameType.SkylineReset,
            score = state.stackScore().coerceAtLeast(0),
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
            gameStoreManager.recordPlay(
                gameId = "SKYLINE_RESET",
                won = outcome == ScoreSessionOutcome.Completed,
            )
        }
    }

    private fun saveCurrentResultSnapshot() {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return
        val outcome = lastSupportOutcome ?: return
        val state = _uiState.value

        if (state.view != SkylineResetView.Result) {
            return
        }

        val recordedSession = lastRecordedSession ?: return

        resultStateStore.save(
            RecoveryGameResultSnapshot(
                cycleId = launch.cycleId,
                decisionId = launch.decisionId,
                gameTypeId = ScoreGameType.SkylineReset.id,
                supportOutcomeName = outcome.name,
                supportElapsedDurationMillis = lastSupportElapsedMillis.coerceAtLeast(0L),
                activeSessionId = activeSessionId,
                sessionStartedAtIso = sessionStartedAt.toString(),
                urgeBeforeRating = urgeBeforeRating,
                urgeAfterRating = urgeAfterRating,
                lastRecordedSession = ScoreSessionSnapshot.from(recordedSession),
                payload = RecoveryGameResultPayload.SkylineReset(
                    floorsBuilt = state.floorsBuilt,
                    perfectCount = state.perfectCount,
                    secondsPlayed = state.secondsPlayed,
                    completed = state.completed,
                    failed = state.failed,
                    controlPointsBanked = state.controlPointsBanked,
                    resultRecorded = resultRecorded,
                    perfectPointsBanked = perfectPointsBanked,
                ),
            ),
        )
    }

    private fun restoreResultSnapshot(snapshot: RecoveryGameResultSnapshot): Boolean {
        val payload = snapshot.payload as? RecoveryGameResultPayload.SkylineReset ?: return false
        val restoredSessionStart = snapshot.sessionStartedAtOrNull() ?: return false
        val restoredOutcome = snapshot.supportOutcomeOrNull() ?: return false
        val restoredRecordedSession = snapshot.lastRecordedSession?.toRecordOrNull() ?: return false

        val presentationMatchesOutcome = when {
            payload.completed ->
                restoredOutcome == SupportCycleGameTerminalOutcome.Completed ||
                    restoredOutcome == SupportCycleGameTerminalOutcome.TimedOut

            payload.failed ->
                restoredOutcome == SupportCycleGameTerminalOutcome.Abandoned

            else -> false
        }

        /*
         * Every legitimate Skyline result is either completed or failed, never
         * both and never neither. Perfect drops cannot exceed placed floors.
         */
        if (
            snapshot.activeSessionId <= 0L ||
            snapshot.supportElapsedDurationMillis < 0L ||
            payload.floorsBuilt < 0 ||
            payload.perfectCount < 0 ||
            payload.perfectCount > payload.floorsBuilt ||
            payload.secondsPlayed < 0 ||
            payload.completed == payload.failed ||
            !payload.resultRecorded ||
            !presentationMatchesOutcome
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
        resultRecorded = payload.resultRecorded
        perfectPointsBanked = payload.perfectPointsBanked
        resumed = false
        lastFrameMs = null

        /*
         * Never reconstruct the tower, moving block, chopped fragments, or frame
         * loop after process death. The Result panel only needs stable summary
         * values.
         */
        _uiState.value = SkylineResetUiState(
            view = SkylineResetView.Result,
            blocks = emptyList(),
            floorsBuilt = payload.floorsBuilt,
            perfectCount = payload.perfectCount,
            secondsPlayed = payload.secondsPlayed,
            completed = payload.completed,
            failed = payload.failed,
            controlPointsBanked = payload.controlPointsBanked,
        )

        return true
    }

    private fun SkylineResetUiState.stackScore(): Int =
        floorsBuilt.coerceAtLeast(0) * 10 +
            perfectCount.coerceAtLeast(0) * 15 +
            if (completed) 200 else 0
}
