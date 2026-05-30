package com.impulsive.app.backend.session.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
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
    private val selectedRange = MutableStateFlow(ScoreRange.Week)

    val uiState: StateFlow<ScoreDashboardState> = combine(
        scoreRepository.sessions,
        taskRewardRepository.storeState,
        selectedRange,
    ) { sessions, rewardState, range ->
        buildScoreDashboardState(
            sessions = sessions,
            selectedRange = range,
            currentLevel = rewardState.currentLevel,
            currentLevelPoints = rewardState.currentLevelPoints,
            pointsNeededForNextLevel = pointsNeededForNextLevel(rewardState.currentLevel),
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
