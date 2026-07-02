package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.TaskRewardDataSource
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStoreState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class TaskRewardRepository(
    context: Context,
) {
    private val dataSource = TaskRewardDataSource(context)

    val storeState: Flow<TaskRewardStoreState> = dataSource.storeState
    val lastFocusTimeAward: Flow<Pair<String, Int>?> = dataSource.lastFocusTimeAward

    suspend fun completeTask(
        taskType: PsychologyTaskType,
        releasePlan: ReleasePlanState,
        now: LocalDateTime,
        launchedFrom: String = "TASK_TO_COMPLETE",
        gameType: String = taskType.id.uppercase(),
        score: Int? = null,
        durationSec: Int? = null,
        validCompletion: Boolean = true,
    ): TaskCompletionResult = dataSource.completeTask(
        taskType = taskType,
        releasePlan = releasePlan,
        now = now,
        launchedFrom = launchedFrom,
        gameType = gameType,
        score = score,
        durationSec = durationSec,
        validCompletion = validCompletion,
    )

    suspend fun awardLevelPoints(points: Int) {
        dataSource.awardLevelPoints(points)
    }

    suspend fun awardFocusTimePointsIfEligible(
        focusSessionId: String,
        completedAtMillis: Long,
    ): Int = dataSource.awardFocusTimePointsIfEligible(
        focusSessionId = focusSessionId,
        completedAtMillis = completedAtMillis,
    )

    suspend fun awardNoteCreationPointsIfNewDay(points: Int): Boolean =
        dataSource.awardNoteCreationPointsIfNewDay(points)

    suspend fun awardFeedbackAnswerPointsIfNewDay(points: Int): Boolean =
        dataSource.awardFeedbackAnswerPointsIfNewDay(points)

    suspend fun removeLegacyDeletedTaskRewards() {
        dataSource.removeLegacyDeletedTaskRewards()
    }
}
