package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.GameStoreManager
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.SkylineDropResult
import com.impulsive.app.backend.domain.game.SkylineFloor
import com.impulsive.app.backend.domain.game.SkylineResetPerPerfectControlPoints
import com.impulsive.app.backend.domain.game.SkylineResetRoundSeconds
import com.impulsive.app.backend.domain.game.newSkylineBaseFloor
import com.impulsive.app.backend.domain.game.resolveSkylineDrop
import com.impulsive.app.backend.domain.game.skylineHueFor
import com.impulsive.app.backend.domain.game.skylineSpeedFor
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class SkylineResetView {
    Ready,
    Playing,
    Paused,
    Result,
}

data class SkylineResetUiState(
    val view: SkylineResetView = SkylineResetView.Ready,
    val floors: List<SkylineFloor> = emptyList(),
    val movingLeft: Float = 0f,
    val movingWidth: Float = 0f,
    val movingDir: Int = 1,
    val movingHue: Int = skylineHueFor(0),
    val floorsBuilt: Int = 0,
    val perfectCount: Int = 0,
    val secondsPlayed: Int = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val dropSeq: Int = 0,
    val lastDropResult: SkylineDropResult? = null,
    val lastTrimLeft: Float = 0f,
    val lastTrimWidth: Float = 0f,
    val controlPointsBanked: Int? = null,
)

private const val SkylineSpeedScale = 0.16f

class SkylineResetViewModel(application: Application) : AndroidViewModel(application) {
    private val scoreRepository = ScoreRepository(application)
    private val gameStoreManager = GameStoreManager(application)
    private val _uiState = MutableStateFlow(SkylineResetUiState())
    val uiState: StateFlow<SkylineResetUiState> = _uiState

    private var resumed = false
    private var lastFrameMs: Long? = null
    private var accumulatedForegroundMs = 0L
    private var resultRecorded = false
    private var perfectPointsBanked = false
    private var activeSessionId: Long = newScoreSessionId()
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()

    fun start() {
        resultRecorded = false
        perfectPointsBanked = false
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
        accumulatedForegroundMs = 0L
        lastFrameMs = null
        resumed = true
        val base = newSkylineBaseFloor(1f)
        _uiState.value = SkylineResetUiState(
            view = SkylineResetView.Playing,
            floors = listOf(base),
            movingLeft = 0f,
            movingWidth = base.width,
            movingDir = 1,
            movingHue = skylineHueFor(1),
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

        if (nextSeconds >= SkylineResetRoundSeconds) {
            _uiState.update {
                it.copy(
                    view = SkylineResetView.Result,
                    completed = true,
                    secondsPlayed = SkylineResetRoundSeconds,
                )
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(ScoreSessionOutcome.Completed)
            return
        }

        val perSec = skylineSpeedFor(current.floorsBuilt) * SkylineSpeedScale
        var x = current.movingLeft + current.movingDir * perSec * (delta / 1_000f)
        var dir = current.movingDir
        val maxLeft = (1f - current.movingWidth).coerceAtLeast(0f)
        if (x <= 0f) {
            x = 0f
            dir = 1
        } else if (x >= maxLeft) {
            x = maxLeft
            dir = -1
        }

        _uiState.update {
            it.copy(movingLeft = x, movingDir = dir, secondsPlayed = nextSeconds)
        }
    }

    fun drop() {
        val current = _uiState.value
        if (current.view != SkylineResetView.Playing) return
        val top = current.floors.lastOrNull() ?: return
        val outcome = resolveSkylineDrop(top, current.movingLeft, current.movingWidth, current.movingHue, 1f)
        if (outcome.result == SkylineDropResult.Missed) {
            _uiState.update {
                it.copy(
                    view = SkylineResetView.Result,
                    failed = true,
                    completed = false,
                    dropSeq = it.dropSeq + 1,
                    lastDropResult = SkylineDropResult.Missed,
                    lastTrimLeft = outcome.trimLeft,
                    lastTrimWidth = outcome.trimWidth,
                )
            }
            resumed = false
            lastFrameMs = null
            recordCurrentResult(ScoreSessionOutcome.Abandoned)
            return
        }
        val placed = outcome.placedFloor ?: return
        val newFloorsBuilt = current.floorsBuilt + 1
        _uiState.update {
            it.copy(
                floors = it.floors + placed,
                floorsBuilt = newFloorsBuilt,
                perfectCount = it.perfectCount + if (outcome.result == SkylineDropResult.Perfect) 1 else 0,
                movingWidth = placed.width,
                movingLeft = if (newFloorsBuilt % 2 == 0) 0f else (1f - placed.width).coerceAtLeast(0f),
                movingDir = if (newFloorsBuilt % 2 == 0) 1 else -1,
                movingHue = skylineHueFor(newFloorsBuilt + 1),
                dropSeq = it.dropSeq + 1,
                lastDropResult = outcome.result,
                lastTrimLeft = outcome.trimLeft,
                lastTrimWidth = outcome.trimWidth,
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
        val points = _uiState.value.perfectCount.coerceAtLeast(0) * SkylineResetPerPerfectControlPoints
        if (points <= 0) {
            _uiState.update { it.copy(controlPointsBanked = 0) }
            return
        }
        viewModelScope.launch {
            val awarded = gameStoreManager.tryAwardWeekly(key = "skyline_perfect", points = points)
            _uiState.update { it.copy(controlPointsBanked = if (awarded) points else 0) }
        }
    }

    fun recordCurrentResult(outcome: ScoreSessionOutcome) {
        if (resultRecorded) return
        resultRecorded = true
        val state = _uiState.value
        viewModelScope.launch {
            scoreRepository.recordSession(
                ScoreSessionRecord(
                    id = activeSessionId,
                    gameType = ScoreGameType.SkylineReset,
                    score = state.skylineScore().coerceAtLeast(0),
                    startedAt = sessionStartedAt,
                    completedAt = LocalDateTime.now(),
                    durationSec = state.secondsPlayed.coerceAtLeast(0),
                    outcome = outcome,
                    validCompletion = state.completed,
                ),
            )
        }
    }

    private fun SkylineResetUiState.skylineScore(): Int =
        floorsBuilt.coerceAtLeast(0) * 10 +
            perfectCount.coerceAtLeast(0) * 15 +
            if (completed) 200 else 0
}
