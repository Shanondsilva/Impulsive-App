package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.ReflexGameHistoryDataSource
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.Flash
import com.impulsive.app.backend.domain.game.GameHistory
import com.impulsive.app.backend.domain.game.GameResult
import com.impulsive.app.backend.domain.game.GameView
import com.impulsive.app.backend.domain.game.ReflexGameConfig
import com.impulsive.app.backend.domain.game.Target
import com.impulsive.app.backend.domain.game.TargetType
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class ReflexGameUiState(
    val view: GameView = GameView.Ready,
    val countdown: Int = 3,
    val timeLeft: Int = ReflexGameConfig.ROUND_SECONDS,
    val score: Int = 0,
    val combo: Int = 0,
    val lives: Int = ReflexGameConfig.MAX_BOMBS,
    val targets: List<Target> = emptyList(),
    val flashes: List<Flash> = emptyList(),
    val result: GameResult? = null,
    val walkScore: Int = 0,
    val shake: Boolean = false,
    val history: GameHistory = GameHistory(),
)

class ReflexGameViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val supportCycleRuntime = RecoveryGameSupportCycleRuntime(application)
    private val resultStateStore = RecoveryGameResultStateStore(savedStateHandle)
    private val resultActionCoordinator = RecoveryGameResultActionCoordinator(
        runtime = supportCycleRuntime,
        clearResultState = { resultStateStore.clear() },
    )
    private val dataSource = ReflexGameHistoryDataSource(application)
    private val scoreRepository = ScoreRepository(application)
    private val pivotGameSessionCommitCoordinator =
        PivotGameSessionCommitCoordinator(
            scoreRepository =
                scoreRepository,
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
    private val gameStoreManager = com.impulsive.app.backend.data.repository.GameStoreManager(application)
    private val _uiState = MutableStateFlow(ReflexGameUiState())
    val uiState: StateFlow<ReflexGameUiState> = _uiState

    private var score = 0
    private var combo = 0
    private var maxCombo = 0
    private var hits = 0
    private var misses = 0
    private var noMiss = true
    private val reactionTimes = mutableListOf<Int>()
    private var difficulty = 1
    private var bombStreak = 0
    private var gameOver = false
    private var startMs = 0L
    private var nextSpawnMs = 0L
    private var targetIdCounter = 0L
    private var flashIdCounter = 0L
    private var arenaW = 320
    private var arenaH = 420
    private var targets = emptyList<Target>()
    private var resultRecorded = false
    private var activeSessionId: Long = newScoreSessionId()
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()
    private var urgeBeforeRating: Int? = null
    private var urgeAfterRating: Int? = null
    private var lastRecordedSession: ScoreSessionRecord? = null
    private var roundDurationMillis = ReflexGameConfig.ROUND_SECONDS * 1_000L
    private var lastSupportOutcome: SupportCycleGameTerminalOutcome? = null
    private var lastSupportElapsedMillis: Long = 0L
    private var activeLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone

    suspend fun configureLaunchContext(launchContext: RecoveryGameLaunchContext): Boolean {
        val binding = supportCycleRuntime.bindWithRecovery(
            requested = launchContext,
            standaloneDurationMillis = ReflexGameConfig.ROUND_SECONDS * 1_000L,
        ) ?: return false

        roundDurationMillis = binding.durationMillis
        activeLaunchContext = launchContext

        val snapshot = resultStateStore.restore(
            launchContext = launchContext,
            expectedGameType = ScoreGameType.ReflexOverride,
        )

        val authoritativeResult = binding.resolvedStep

        /*
         * The support-cycle repository says the current step is already terminal.
         * The result presentation must therefore be restored from SavedStateHandle.
         */
        if (authoritativeResult != null) {
            val snapshotOutcome = snapshot?.supportOutcomeOrNull()

            if (
                snapshot == null ||
                snapshotOutcome != authoritativeResult.outcome ||
                !restoreResultSnapshot(snapshot)
            ) {
                /*
                 * The authoritative step is terminal but the presentation state
                 * cannot be trusted. Finish the active cycle so it cannot remain
                 * stranded, then allow the existing screen-exit path to run.
                 */
                resultStateStore.clear()

                supportCycleRuntime.resolveAndEnd(
                    outcome = authoritativeResult.outcome,
                    elapsedDurationMillis = authoritativeResult.elapsedDurationMillis,
                )

                return false
            }

            /*
             * The repository is authoritative for the terminal outcome and
             * consumed duration, even when the snapshot contains older values.
             */
            lastSupportOutcome = authoritativeResult.outcome
            lastSupportElapsedMillis = authoritativeResult.elapsedDurationMillis

            return true
        }

        /*
         * Snapshot exists while the authoritative step remains InProgress.
         *
         * This covers process death after the result was saved but before the
         * asynchronous resolveForContinuation write completed.
         */
        if (snapshot != null) {
            val snapshotOutcome = snapshot.supportOutcomeOrNull()

            if (snapshotOutcome == null || !restoreResultSnapshot(snapshot)) {
                /*
                 * The support-cycle step is still legitimately InProgress.
                 * A corrupt presentation snapshot must not terminalise that step.
                 * Remove the bad snapshot and continue from the normal Ready state.
                 */
                resultStateStore.clear()

                if (_uiState.value.view == GameView.Ready) {
                    _uiState.update { it.copy(timeLeft = roundDurationSeconds()) }
                }

                return true
            }

            lastSupportOutcome = snapshotOutcome
            lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis.coerceAtLeast(0L)

            val resolution = supportCycleRuntime.resolveForContinuation(
                outcome = snapshotOutcome,
                elapsedDurationMillis = lastSupportElapsedMillis,
            )

            /*
             * A restored result must remain visible whenever the retry keeps
             * it valid: continuation succeeded, the outcome was already
             * persisted as terminal/NotFound, or the failure is explicitly
             * retryable. Only a non-retryable failure (e.g. OutcomeConflict)
             * discards the snapshot.
             */
            if (!resolution.keepsRestoredResultVisible) {
                resultStateStore.clear()

                return false
            }

            return true
        }

        if (_uiState.value.view == GameView.Ready) {
            _uiState.update { it.copy(timeLeft = roundDurationSeconds()) }
        }

        return true
    }

    fun abandonSupportCycle() {
        val elapsed = if (startMs > 0L) SystemClock.uptimeMillis() - startMs else 0L
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
                requestedDurationMillis = ReflexGameConfig.ROUND_SECONDS * 1_000L,
            ) ?: return@launch

            roundDurationMillis = duration
            lastSupportOutcome = null
            lastSupportElapsedMillis = 0L
            onReady()
        }
    }

    fun finishSupportCycleAfterChoice(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: SupportCycleGameTerminalOutcome.Abandoned
        val elapsed = if (lastSupportOutcome == null && startMs > 0L) {
            SystemClock.uptimeMillis() - startMs
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

    init {
        viewModelScope.launch {
            dataSource.history.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun setArenaSize(widthDp: Int, heightDp: Int) {
        arenaW = widthDp.coerceAtLeast(1)
        arenaH = heightDp.coerceAtLeast(1)
    }

    fun startCountdown() {
        resultStateStore.clear()
        _uiState.update {
            it.copy(
                view = GameView.Countdown,
                countdown = 3,
                result = null,
                walkScore = 0,
                targets = emptyList(),
                flashes = emptyList(),
            )
        }
        viewModelScope.launch {
            for (n in 3 downTo 1) {
                _uiState.update { it.copy(countdown = n) }
                delay(600)
            }
            _uiState.update { it.copy(countdown = 0) }
            delay(320)
            startGame()
        }
    }

    fun startGame() {
        resultStateStore.clear()
        score = 0
        combo = 0
        resultRecorded = false
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
        urgeAfterRating = null
        maxCombo = 0
        hits = 0
        misses = 0
        noMiss = true
        reactionTimes.clear()
        difficulty = 1
        bombStreak = 0
        gameOver = false
        startMs = SystemClock.uptimeMillis()
        nextSpawnMs = startMs + 400
        targets = emptyList()
        _uiState.update {
            it.copy(
                view = GameView.Playing,
                countdown = 3,
                timeLeft = roundDurationSeconds(),
                score = 0,
                combo = 0,
                lives = ReflexGameConfig.MAX_BOMBS,
                targets = emptyList(),
                flashes = emptyList(),
                result = null,
                walkScore = 0,
                shake = false,
            )
        }
    }

    fun tick() {
        if (_uiState.value.view != GameView.Playing || gameOver) return

        val now = SystemClock.uptimeMillis()
        val elapsedSec = (now - startMs) / 1000.0
        val roundSeconds = roundDurationMillis / 1_000.0
        if (elapsedSec >= roundSeconds) {
            finishRound(earlyExit = false)
            return
        }

        val timeLeft = max(0, ceil(roundSeconds - elapsedSec).toInt())
        difficulty = min(
            ReflexGameConfig.DIFFICULTY.size,
            floor(elapsedSec / ReflexGameConfig.DIFFICULTY_STEP_SECONDS).toInt() + 1,
        )
        val tier = ReflexGameConfig.DIFFICULTY[difficulty - 1]

        if (now >= nextSpawnMs) {
            spawnTarget(now)
            nextSpawnMs = now + tier.spawnMs + Random.nextLong(-90, 91)
        }

        val expired = targets.filter { now - it.createdAtMs > it.lifetimeMs }
        val expiredHits = expired.count { it.type == TargetType.Hit }
        if (expiredHits > 0) {
            combo = 0
            noMiss = false
            misses += expiredHits
            bombStreak += expiredHits   // a missed target costs a life, same weight as tapping a bomb
        }
        targets = targets.filter { target -> expired.none { it.id == target.id } }

        _uiState.update {
            it.copy(
                timeLeft = timeLeft,
                score = score,
                combo = combo,
                lives = max(0, ReflexGameConfig.MAX_BOMBS - bombStreak),
                targets = targets,
            )
        }
        if (bombStreak >= ReflexGameConfig.MAX_BOMBS) {
            endWithGameOver()
        }
    }

    private fun spawnTarget(now: Long) {
        val tier = ReflexGameConfig.DIFFICULTY[difficulty - 1]
        if (targets.size >= maxActiveTargets()) return

        val size = randomTargetSize()
        var x = safeRandomFraction(size, arenaW)
        var y = safeRandomFraction(size, arenaH)

        repeat(10) {
            val candidateX = safeRandomFraction(size, arenaW)
            val candidateY = safeRandomFraction(size, arenaH)
            if (!overlapsExisting(candidateX, candidateY, size)) {
                x = candidateX
                y = candidateY
                return@repeat
            }
        }

        val isDecoy = Random.nextFloat() < tier.decoyProb
        val target = Target(
            id = ++targetIdCounter,
            type = if (isDecoy) TargetType.Decoy else TargetType.Hit,
            xFraction = x,
            yFraction = y,
            sizeDp = size,
            colorHex = if (isDecoy) null else ReflexGameConfig.TARGET_COLORS.random(),
            createdAtMs = now,
            lifetimeMs = tier.lifetimeMs,
        )
        targets = targets + target
    }

    private fun randomTargetSize(): Int = when (difficulty) {
        1 -> Random.nextInt(64, 79)
        2 -> Random.nextInt(58, 73)
        3 -> Random.nextInt(52, 67)
        else -> Random.nextInt(46, 61)
    }

    private fun maxActiveTargets(): Int = when (difficulty) {
        1 -> 2
        2 -> 3
        3 -> 4
        else -> 5
    }

    private fun safeRandomFraction(size: Int, arenaDimension: Int): Float {
        val travel = (arenaDimension - size).coerceAtLeast(1)
        val margin = max(10, (size * 0.25f).toInt())
        val minPx = margin.coerceAtMost(travel)
        val maxPx = (travel - margin).coerceAtLeast(minPx)
        val topLeft = if (maxPx == minPx) minPx else Random.nextInt(minPx, maxPx + 1)
        return (topLeft / travel.toFloat()).coerceIn(0f, 1f)
    }

    private fun overlapsExisting(x: Float, y: Float, size: Int): Boolean {
        val cx = x * (arenaW - size).coerceAtLeast(1) + size / 2f
        val cy = y * (arenaH - size).coerceAtLeast(1) + size / 2f
        return targets.any { existing ->
            val ex = existing.xFraction * (arenaW - existing.sizeDp).coerceAtLeast(1) + existing.sizeDp / 2f
            val ey = existing.yFraction * (arenaH - existing.sizeDp).coerceAtLeast(1) + existing.sizeDp / 2f
            val minDistance = (size + existing.sizeDp) * 0.72f
            hypot(cx - ex, cy - ey) < minDistance
        }
    }

    fun tapTarget(id: Long) {
        if (_uiState.value.view != GameView.Playing || gameOver) return
        val target = targets.firstOrNull { it.id == id } ?: return
        val now = SystemClock.uptimeMillis()
        val rt = (now - target.createdAtMs).toInt()

        if (target.type == TargetType.Hit) {
            hits++
            combo++
            maxCombo = max(maxCombo, combo)
            reactionTimes += rt
            val base = if (rt < 400) 150 else if (rt < 900) 100 else 60
            val points = base + combo * 10
            score += points
            addFlash(target.xFraction, target.yFraction, "+$points")
        } else {
            misses++
            noMiss = false
            combo = 0
            score = max(0, score - 50)
            bombStreak++
            pulseShake()
        }

        targets = targets.filterNot { it.id == id }
        _uiState.update {
            it.copy(
                score = score,
                combo = combo,
                lives = max(0, ReflexGameConfig.MAX_BOMBS - bombStreak),
                targets = targets,
            )
        }
        if (bombStreak >= ReflexGameConfig.MAX_BOMBS) {
            endWithGameOver()
        }
    }

    fun tapArena(xFraction: Float? = null, yFraction: Float? = null) {
        if (_uiState.value.view != GameView.Playing || gameOver) return
        combo = 0
        noMiss = false
        misses++
        score = max(0, score - 25)
        if (xFraction != null && yFraction != null) {
            addFlash(
                xFraction = xFraction.coerceIn(0f, 1f),
                yFraction = yFraction.coerceIn(0f, 1f),
                text = "Miss",
            )
        }
        pulseShake()
        _uiState.update { it.copy(score = score, combo = combo) }
    }

    private fun addFlash(xFraction: Float, yFraction: Float, text: String) {
        val flash = Flash(++flashIdCounter, xFraction, yFraction, text)
        _uiState.update { it.copy(flashes = it.flashes + flash) }
        viewModelScope.launch {
            delay(700)
            _uiState.update { it.copy(flashes = it.flashes.filterNot { f -> f.id == flash.id }) }
        }
    }

    private fun pulseShake() {
        _uiState.update { it.copy(shake = true) }
        viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(shake = false) }
        }
    }

    private fun endWithGameOver() {
        if (gameOver) return
        gameOver = true
        pulseShake()
        viewModelScope.launch {
            delay(900)
            finishRound(earlyExit = true)
        }
    }

    private fun finishRound(earlyExit: Boolean) {
        if (_uiState.value.view == GameView.Result || _uiState.value.view == GameView.Walked) return
        if (!earlyExit) {
            score += 500
            if (noMiss && hits > 0) score += 1000
        }
        val old = _uiState.value.history
        val bestReaction = reactionTimes.minOrNull()
        val nextBestReaction = when {
            old.bestReactionMs == null -> bestReaction
            bestReaction == null -> old.bestReactionMs
            else -> min(old.bestReactionMs, bestReaction)
        }
        val nextHistory = GameHistory(
            pb = max(old.pb, score),
            prev = score,
            bestReactionMs = nextBestReaction,
            bestCombo = max(old.bestCombo, maxCombo),
        )
        val result = GameResult(
            score = score,
            previousBest = old.pb,
            previousScore = old.prev,
            bestReactionMs = bestReaction,
            maxCombo = maxCombo,
            hits = hits,
            misses = misses,
            difficulty = difficulty,
            gameOver = earlyExit,
            durationSec = min(roundDurationSeconds(), ((SystemClock.uptimeMillis() - startMs) / 1000L).toInt()),
            validCompletion = !earlyExit && score > 0 && hits > 0 && ((SystemClock.uptimeMillis() - startMs) / 1000L) >= 5L,
        )
        targets = emptyList()
        viewModelScope.launch { dataSource.save(nextHistory) }
        _uiState.update {
            it.copy(
                view = GameView.Result,
                score = score,
                combo = combo,
                lives = max(0, ReflexGameConfig.MAX_BOMBS - bombStreak),
                targets = emptyList(),
                result = result,
                history = nextHistory,
            )
        }
        val outcome = if (result.validCompletion) ScoreSessionOutcome.Completed else ScoreSessionOutcome.Abandoned
        recordScoreSession(outcome = outcome, scoreValue = score, result = result)
        val elapsedMillis = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(0L)
        val supportOutcome = when {
            roundDurationMillis < ReflexGameConfig.ROUND_SECONDS * 1_000L &&
                elapsedMillis >= roundDurationMillis -> SupportCycleGameTerminalOutcome.TimedOut
            !result.validCompletion -> SupportCycleGameTerminalOutcome.Abandoned
            else -> SupportCycleGameTerminalOutcome.Completed
        }
        lastSupportOutcome = supportOutcome
        lastSupportElapsedMillis = elapsedMillis

        /*
         * SavedStateHandle is updated synchronously before the asynchronous
         * support-cycle mutation. This closes the process-death race between result
         * presentation and terminal-step persistence.
         */
        saveCurrentResultSnapshot()

        viewModelScope.launch {
            supportCycleRuntime.resolveForContinuation(
                outcome = supportOutcome,
                elapsedDurationMillis = elapsedMillis,
            )
        }
    }

    fun walkAway() {
        val result = _uiState.value.result ?: return
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        val oldHistory = _uiState.value.history
        val total = result.score + ReflexGameConfig.WALK_AWAY_BONUS
        val nextHistory = oldHistory.copy(
            pb = max(oldHistory.pb, total),
            prev = total,
        )

        viewModelScope.launch {
            /*
             * Finish using the authoritative outcome already assigned when the
             * result appeared. Do not replace Failed or TimedOut with Completed.
             */
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (!allowed) {
                return@launch
            }

            recordCurrentResult(
                outcome = ScoreSessionOutcome.WalkedAway,
                scoreOverride = total,
            )

            _uiState.update {
                it.copy(
                    view = GameView.Walked,
                    walkScore = total,
                    history = nextHistory,
                    targets = emptyList(),
                    flashes = emptyList(),
                )
            }

            dataSource.save(nextHistory)
        }
    }

    fun setUrgeBefore(rating: Int) {
        urgeBeforeRating = rating.coerceIn(0, 10)
    }

    fun taskRewardCompletionToken(): String =
        "${ScoreGameType.ReflexOverride.id}:$activeSessionId"

    /**
     * Captures the post-game rating. The session has already been recorded by
     * the time the Result screen shows, so this re-records the same session id
     * with the rating attached. It deliberately calls the repository directly
     * instead of recordScoreSession so the game store play counter is not
     * incremented a second time.
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

    fun recordCurrentResult(
        outcome: ScoreSessionOutcome,
        scoreOverride: Int? = null,
    ) {
        val result = _uiState.value.result ?: return
        recordScoreSession(outcome = outcome, scoreValue = scoreOverride ?: result.score, result = result)
    }

    private fun recordScoreSession(
        outcome: ScoreSessionOutcome,
        scoreValue: Int,
        result: GameResult,
    ) {
        val record = ScoreSessionRecord(
            id = activeSessionId,
            gameType = ScoreGameType.ReflexOverride,
            score = scoreValue.coerceAtLeast(0),
            startedAt = sessionStartedAt,
            completedAt = LocalDateTime.now(),
            durationSec = result.durationSec.coerceAtLeast(0),
            urgeBefore = urgeBeforeRating,
            urgeAfter = urgeAfterRating,
            outcome = outcome,
            validCompletion =
                result.validCompletion &&
                outcome !=
                ScoreSessionOutcome.Abandoned,
        )
        lastRecordedSession = record
        viewModelScope.launch {
            pivotGameSessionCommitCoordinator
                .commit(
                    record,
                )

            if (
                outcome !=
                ScoreSessionOutcome.WalkedAway
            ) {
                gameStoreManager
                    .recordPlay(
                        gameId =
                            "REFLEX_OVERRIDE",
                        won =
                            !result.gameOver,
                    )
            }
        }
    }

    private fun saveCurrentResultSnapshot() {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return
        val outcome = lastSupportOutcome ?: return
        val state = _uiState.value
        val result = state.result ?: return

        resultStateStore.save(
            RecoveryGameResultSnapshot(
                cycleId = launch.cycleId,
                decisionId = launch.decisionId,
                gameTypeId = ScoreGameType.ReflexOverride.id,
                supportOutcomeName = outcome.name,
                supportElapsedDurationMillis = lastSupportElapsedMillis.coerceAtLeast(0L),
                activeSessionId = activeSessionId,
                sessionStartedAtIso = sessionStartedAt.toString(),
                urgeBeforeRating = urgeBeforeRating,
                urgeAfterRating = urgeAfterRating,
                lastRecordedSession = lastRecordedSession?.let { ScoreSessionSnapshot.from(it) },
                payload = RecoveryGameResultPayload.Reflex(
                    score = state.score,
                    combo = state.combo,
                    lives = state.lives,
                    historyPersonalBest = state.history.pb,
                    historyPrevious = state.history.prev,
                    historyBestReactionMs = state.history.bestReactionMs,
                    historyBestCombo = state.history.bestCombo,
                    resultScore = result.score,
                    resultPreviousBest = result.previousBest,
                    resultPreviousScore = result.previousScore,
                    resultBestReactionMs = result.bestReactionMs,
                    resultMaxCombo = result.maxCombo,
                    resultHits = result.hits,
                    resultMisses = result.misses,
                    resultDifficulty = result.difficulty,
                    resultGameOver = result.gameOver,
                    resultDurationSec = result.durationSec,
                    resultValidCompletion = result.validCompletion,
                ),
            ),
        )
    }

    private fun restoreResultSnapshot(snapshot: RecoveryGameResultSnapshot): Boolean {
        val payload = snapshot.payload as? RecoveryGameResultPayload.Reflex ?: return false
        val restoredSessionStart = snapshot.sessionStartedAtOrNull() ?: return false
        val restoredOutcome = snapshot.supportOutcomeOrNull() ?: return false
        val restoredRecordedSession = snapshot.lastRecordedSession?.toRecordOrNull()

        if (snapshot.lastRecordedSession != null && restoredRecordedSession == null) {
            return false
        }

        if (snapshot.activeSessionId <= 0L || snapshot.supportElapsedDurationMillis < 0L) {
            return false
        }

        activeSessionId = snapshot.activeSessionId
        sessionStartedAt = restoredSessionStart
        urgeBeforeRating = snapshot.urgeBeforeRating?.coerceIn(0, 10)
        urgeAfterRating = snapshot.urgeAfterRating?.coerceIn(0, 10)
        lastRecordedSession = restoredRecordedSession
        lastSupportOutcome = restoredOutcome
        lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis

        score = payload.score
        combo = payload.combo
        maxCombo = payload.resultMaxCombo
        hits = payload.resultHits
        misses = payload.resultMisses
        difficulty = payload.resultDifficulty
        gameOver = payload.resultGameOver
        bombStreak = (ReflexGameConfig.MAX_BOMBS - payload.lives).coerceAtLeast(0)
        targets = emptyList()
        reactionTimes.clear()
        resultRecorded = true

        /*
         * A restored result must never resume the old gameplay clock or loop.
         */
        startMs = 0L
        nextSpawnMs = 0L

        _uiState.value = ReflexGameUiState(
            view = GameView.Result,
            countdown = 0,
            timeLeft = 0,
            score = payload.score,
            combo = payload.combo,
            lives = payload.lives.coerceIn(0, ReflexGameConfig.MAX_BOMBS),
            targets = emptyList(),
            flashes = emptyList(),
            result = GameResult(
                score = payload.resultScore,
                previousBest = payload.resultPreviousBest,
                previousScore = payload.resultPreviousScore,
                bestReactionMs = payload.resultBestReactionMs,
                maxCombo = payload.resultMaxCombo,
                hits = payload.resultHits,
                misses = payload.resultMisses,
                difficulty = payload.resultDifficulty,
                gameOver = payload.resultGameOver,
                durationSec = payload.resultDurationSec,
                validCompletion = payload.resultValidCompletion,
            ),
            walkScore = 0,
            shake = false,
            history = GameHistory(
                pb = payload.historyPersonalBest,
                prev = payload.historyPrevious,
                bestReactionMs = payload.historyBestReactionMs,
                bestCombo = payload.historyBestCombo,
            ),
        )

        return true
    }

    fun playAgain() {
        startCountdown()
    }

    fun reset() {
        resultStateStore.clear()
        targets = emptyList()
        resultRecorded = false
        _uiState.update {
            it.copy(
                view = GameView.Ready,
                countdown = 3,
                timeLeft = roundDurationSeconds(),
                score = 0,
                combo = 0,
                lives = ReflexGameConfig.MAX_BOMBS,
                targets = emptyList(),
                flashes = emptyList(),
                result = null,
                walkScore = 0,
                shake = false,
            )
        }
    }
}
