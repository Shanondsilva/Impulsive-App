package com.impulsive.app.backend.session.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.FocusSessionRepository
import com.impulsive.app.backend.data.repository.FocusSetupRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.focusCompletionScore
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.service.protection.ProtectionServiceController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class FocusSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FocusSessionRepository(application)
    private val focusSetupRepository = FocusSetupRepository(application)
    private val taskRewardRepository = TaskRewardRepository(application)
    private val scoreRepository = ScoreRepository(application)

    /** Null = never configured; the UI falls back to the urge-protection list. */
    val configuredFocusBlockedPackages: StateFlow<Set<String>?> =
        focusSetupRepository.configuredBlockedPackages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val session: StateFlow<FocusSessionState?> = repository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val lastFocusTimeAward: StateFlow<Pair<String, Int>?> =
        taskRewardRepository.lastFocusTimeAward.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** One second ticker so countdown text recomposes while a screen observes it. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDateTime.now(),
    )

    fun startSession(durationMinutes: Int) {
        viewModelScope.launch {
            repository.startSession(durationMinutes)
            ProtectionServiceController.start(getApplication())
        }
    }

    fun pause() {
        viewModelScope.launch { repository.pause() }
    }

    fun resume() {
        viewModelScope.launch { repository.resume() }
    }

    fun endEarly() {
        viewModelScope.launch { repository.endEarly() }
    }

    fun clearFinishedSession() {
        viewModelScope.launch { repository.clearFinishedSession() }
    }

    fun completeElapsedSessionIfNeeded(now: LocalDateTime = LocalDateTime.now()) {
        viewModelScope.launch {
            val completed = repository.completeIfElapsed(now) ?: return@launch
            val completedAt = completed.endedAt ?: now
            taskRewardRepository.awardFocusTimePointsIfEligible(
                focusSessionId = completed.sessionId,
                completedAtMillis = completedAt.toEpochMillisInUserZone(),
            )
            scoreRepository.recordSession(
                ScoreSessionRecord(
                    gameType = ScoreGameType.FocusSession,
                    score = focusCompletionScore(completed.durationMinutes),
                    startedAt = completed.startedAt,
                    completedAt = completed.endedAt ?: now,
                    durationSec = completed.durationMinutes * 60,
                    outcome = ScoreSessionOutcome.Completed,
                    validCompletion = true,
                ),
            )
        }
    }

    fun setFocusBlockedPackages(packageNames: Set<String>) {
        viewModelScope.launch { focusSetupRepository.setBlockedPackages(packageNames) }
    }

    private fun LocalDateTime.toEpochMillisInUserZone(): Long =
        atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
