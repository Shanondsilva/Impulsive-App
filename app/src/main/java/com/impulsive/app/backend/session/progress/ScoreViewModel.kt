package com.impulsive.app.backend.session.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.data.repository.TaperRepository
import com.impulsive.app.backend.data.repository.UrgeEventRepository
import com.impulsive.app.backend.data.repository.WindowOutcomeRepository
import com.impulsive.app.backend.domain.game.RecoveryGameCatalog
import com.impulsive.app.backend.domain.model.score.ScoreDashboardState
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.buildScoreDashboardState
import com.impulsive.app.backend.domain.model.tasks.pointsNeededForNextLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ScoreViewModel(application: Application) : AndroidViewModel(application) {
    private val scoreRepository = ScoreRepository(application)
    private val taskRewardRepository = TaskRewardRepository(application)
    private val onboardingRepository = OnboardingRepository(application)
    private val urgeEventRepository = UrgeEventRepository(application)
    private val windowOutcomeRepository = WindowOutcomeRepository(application)
    private val taperRepository = TaperRepository(application)
    private val selectedRange = MutableStateFlow(ScoreRange.Week)

    private val urgeAndWindowSignals = combine(
        urgeEventRepository.events,
        windowOutcomeRepository.outcomes,
        taperRepository.state,
    ) { urgeEvents, windowOutcomes, taperState ->
        Triple(urgeEvents, windowOutcomes, taperState)
    }

    val uiState: StateFlow<ScoreDashboardState> = combine(
        scoreRepository.sessions,
        taskRewardRepository.storeState,
        onboardingRepository.answers,
        urgeAndWindowSignals,
        selectedRange,
    ) { sessions, rewardState, onboardingAnswers, signals, range ->
        val (urgeEvents, windowOutcomes, taperState) = signals
        buildScoreDashboardState(
            sessions = sessions,
            selectedRange = range,
            currentLevel = rewardState.currentLevel,
            currentLevelPoints = rewardState.currentLevelPoints,
            pointsNeededForNextLevel = pointsNeededForNextLevel(rewardState.currentLevel),
            recoveryGameTypes = RecoveryGameCatalog.scoreGameTypes,
            baselineDailyUrgeCount = onboardingAnswers.dailyRelapseUrgeCount,
            urgeEvents = urgeEvents,
            windowOutcomes = windowOutcomes,
            taperHistory = taperState.history,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScoreDashboardState(),
    )

    fun selectRange(range: ScoreRange) {
        selectedRange.value = range
    }
}
