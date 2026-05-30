package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
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

class ReflexGameViewModel(application: Application) : AndroidViewModel(application) {
    private val dataSource = ReflexGameHistoryDataSource(application)
    private val scoreRepository = ScoreRepository(application)
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
        score = 0
        combo = 0
        resultRecorded = false
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
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
                timeLeft = ReflexGameConfig.ROUND_SECONDS,
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
        if (elapsedSec >= ReflexGameConfig.ROUND_SECONDS) {
            finishRound(earlyExit = false)
            return
        }

        val timeLeft = max(0, ceil(ReflexGameConfig.ROUND_SECONDS - elapsedSec).toInt())
        difficulty = min(4, floor(elapsedSec / 15).toInt() + 1)
        val tier = ReflexGameConfig.DIFFICULTY[difficulty - 1]

        if (now >= nextSpawnMs) {
            spawnTarget(now)
            nextSpawnMs = now + tier.spawnMs + Random.nextLong(-90, 91)
        }

        val expired = targets.filter { now - it.createdAtMs > it.lifetimeMs }
        if (expired.any { it.type == TargetType.Hit }) {
            combo = 0
            noMiss = false
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
            durationSec = min(ReflexGameConfig.ROUND_SECONDS, ((SystemClock.uptimeMillis() - startMs) / 1000L).toInt()),
            validCompletion = score > 0 && hits > 0 && ((SystemClock.uptimeMillis() - startMs) / 1000L) >= 5L,
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
    }

    fun walkAway() {
        val old = _uiState.value.history
        val base = _uiState.value.result?.score ?: score
        val total = base + ReflexGameConfig.WALK_AWAY_BONUS
        recordCurrentResult(
            outcome = ScoreSessionOutcome.WalkedAway,
            scoreOverride = total,
        )
        val nextHistory = old.copy(
            pb = max(old.pb, total),
            prev = total,
        )
        viewModelScope.launch { dataSource.save(nextHistory) }
        _uiState.update {
            it.copy(
                view = GameView.Walked,
                walkScore = total,
                history = nextHistory,
                targets = emptyList(),
                flashes = emptyList(),
            )
        }
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
        viewModelScope.launch {
            scoreRepository.recordSession(
                ScoreSessionRecord(
                    id = activeSessionId,
                    gameType = ScoreGameType.ReflexOverride,
                    score = scoreValue.coerceAtLeast(0),
                    startedAt = sessionStartedAt,
                    completedAt = LocalDateTime.now(),
                    durationSec = result.durationSec.coerceAtLeast(0),
                    outcome = outcome,
                    validCompletion = when (outcome) {
                        ScoreSessionOutcome.Abandoned -> false
                        else -> result.validCompletion || outcome == ScoreSessionOutcome.WalkedAway
                    },
                ),
            )
        }
    }

    fun playAgain() {
        startCountdown()
    }

    fun reset() {
        targets = emptyList()
        resultRecorded = false
        _uiState.update {
            it.copy(
                view = GameView.Ready,
                countdown = 3,
                timeLeft = ReflexGameConfig.ROUND_SECONDS,
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
