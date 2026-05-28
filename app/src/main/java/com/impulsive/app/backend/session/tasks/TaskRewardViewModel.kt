package com.impulsive.app.backend.session.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.tasks.InitialLevel
import com.impulsive.app.backend.domain.model.tasks.InitialLevelPoints
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStoreState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class TaskRewardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = TaskRewardRepository(application)

    val storeState: StateFlow<TaskRewardStoreState> = repository.storeState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskRewardStoreState(
                records = emptyMap(),
                currentLevel = InitialLevel,
                currentLevelPoints = InitialLevelPoints,
                rewardedWindowKey = null,
                adjustedNextReleaseWindow = null,
                lastRecommendedTaskType = null,
                lastCompletedTaskType = null,
                recentRecommendedTaskTypes = emptyList(),
                currentUrgeIntensity = null,
                currentTriggerType = null,
                currentTriggerSource = null,
                userEnergyState = null,
            ),
        )

    val lastCompletionResult = MutableStateFlow<TaskCompletionResult?>(null)

    fun completeTask(
        taskType: PsychologyTaskType,
        releasePlan: ReleasePlanState,
        now: LocalDateTime,
        launchedFrom: String = "TASK_TO_COMPLETE",
        gameType: String = taskType.id.uppercase(),
        score: Int? = null,
        durationSec: Int? = null,
        validCompletion: Boolean = true,
    ) {
        viewModelScope.launch {
            lastCompletionResult.value = repository.completeTask(
                taskType = taskType,
                releasePlan = releasePlan,
                now = now,
                launchedFrom = launchedFrom,
                gameType = gameType,
                score = score,
                durationSec = durationSec,
                validCompletion = validCompletion,
            )
        }
    }

    fun clearLastCompletionResult() {
        lastCompletionResult.value = null
    }
}
