package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.tasks.InitialLevel
import com.impulsive.app.backend.domain.model.tasks.InitialLevelPoints
import com.impulsive.app.backend.domain.model.tasks.EnergyState
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskRewardDefinitions
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionRecord
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStoreState
import com.impulsive.app.backend.domain.model.tasks.TriggerSource
import com.impulsive.app.backend.domain.model.tasks.TriggerType
import com.impulsive.app.backend.domain.model.tasks.addLevelPoints
import com.impulsive.app.backend.domain.model.tasks.calculateWaitReductionMinutesToApply
import com.impulsive.app.backend.domain.model.tasks.isSameLocalDay
import com.impulsive.app.backend.domain.model.tasks.recommendPsychologyTask
import com.impulsive.app.backend.domain.model.tasks.rewardStatusFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.taskRewardDataStore by preferencesDataStore(name = "task_rewards")

class TaskRewardDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.taskRewardDataStore

    val storeState: Flow<TaskRewardStoreState> = dataStore.data.map { preferences ->
        val today = LocalDate.now()
        val records = PsychologyTaskType.entries.associateWith { taskType ->
            val lastCompletedAt = preferences[lastCompletedAtKey(taskType)]
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            TaskCompletionRecord(
                taskType = taskType,
                completedEver = preferences[completedEverKey(taskType)] == 1,
                completedTodayCount = if (isSameLocalDay(lastCompletedAt, today)) {
                    preferences[completedTodayCountKey(taskType)] ?: 0
                } else {
                    0
                },
                lastCompletedAt = lastCompletedAt,
            )
        }

        TaskRewardStoreState(
            records = records,
            currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
            currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
            rewardedWindowKey = preferences[RewardedWindowKey],
            adjustedNextReleaseWindow = preferences[AdjustedNextReleaseWindowKey]
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            lastRecommendedTaskType = preferences[LastRecommendedTaskTypeKey].toTaskTypeOrNull(),
            lastCompletedTaskType = preferences[LastCompletedTaskTypeKey].toTaskTypeOrNull(),
            recentRecommendedTaskTypes = preferences[RecentRecommendedTaskTypesKey].toTaskTypeList(),
            currentUrgeIntensity = preferences[CurrentUrgeIntensityKey]?.coerceIn(0, 10),
            currentTriggerType = preferences[CurrentTriggerTypeKey].toEnumOrNull<TriggerType>(),
            currentTriggerSource = preferences[CurrentTriggerSourceKey].toEnumOrNull<TriggerSource>(),
            userEnergyState = preferences[UserEnergyStateKey].toEnumOrNull<EnergyState>(),
        )
    }

    suspend fun completeTask(
        taskType: PsychologyTaskType,
        releasePlan: ReleasePlanState,
        now: LocalDateTime,
        launchedFrom: String = "TASK_TO_COMPLETE",
        gameType: String = taskType.id.uppercase(),
        score: Int? = null,
        durationSec: Int? = null,
        validCompletion: Boolean = true,
    ): TaskCompletionResult {
        var result = TaskCompletionResult(
            taskType = taskType,
            taskTitle = taskType.taskTitle,
            waitReductionMinutes = 0,
            levelPointsAwarded = 0,
            currentLevel = InitialLevel,
            currentLevelPoints = InitialLevelPoints,
            pointsNeededForNextLevel = 100,
        )

        dataStore.edit { preferences ->
            if (!validCompletion) {
                preferences[LastGameTypeKey] = gameType
                preferences[LastTaskTypeKey] = taskType.id.uppercase()
                preferences[LastLaunchedFromKey] = launchedFrom
                preferences[LastCompletedAtKey] = now.toString()
                score?.let { preferences[LastScoreKey] = it }
                durationSec?.let { preferences[LastDurationSecKey] = it }
                preferences[LastValidCompletionKey] = 0
                return@edit
            }
            val definition = PsychologyTaskRewardDefinitions.first { it.taskType == taskType }
            val lastCompletedAt = preferences[lastCompletedAtKey(taskType)]
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val completedEver = preferences[completedEverKey(taskType)] == 1
            val completedTodayCount = if (isSameLocalDay(lastCompletedAt, now.toLocalDate())) {
                preferences[completedTodayCountKey(taskType)] ?: 0
            } else {
                0
            }
            val rewardWindowAlreadyUsed = preferences[RewardedWindowKey] == releasePlan.nextReleaseWindow.toString()

            val requestedWaitReduction = when {
                completedTodayCount > 0 -> definition.sameDayWaitReductionMinutes
                completedEver -> definition.repeatWaitReductionMinutes
                else -> definition.firstTimeWaitReductionMinutes
            }
            val levelPointsAwarded = when {
                rewardWindowAlreadyUsed -> minOf(2, definition.sameDayLevelPoints)
                completedTodayCount > 0 -> definition.sameDayLevelPoints
                completedEver -> definition.repeatLevelPoints
                else -> definition.firstTimeLevelPoints
            }
            val waitReductionApplied = calculateWaitReductionMinutesToApply(
                requestedReductionMinutes = if (rewardWindowAlreadyUsed) 0 else requestedWaitReduction,
                now = now,
                releasePlan = releasePlan,
                currentWindowRewardAlreadyUsed = rewardWindowAlreadyUsed,
            )
            val adjustedWindow = if (waitReductionApplied > 0) {
                releasePlan.adjustedNextReleaseWindow.minusMinutes(waitReductionApplied.toLong())
            } else {
                preferences[AdjustedNextReleaseWindowKey]
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    ?: releasePlan.adjustedNextReleaseWindow
            }

            val levelProgress = addLevelPoints(
                currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
                currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                pointsToAdd = levelPointsAwarded,
            )

            preferences[completedEverKey(taskType)] = 1
            preferences[completedTodayCountKey(taskType)] = completedTodayCount + 1
            preferences[lastCompletedAtKey(taskType)] = now.toString()
            preferences[LastCompletedTaskTypeKey] = taskType.id
            preferences[CurrentLevelKey] = levelProgress.currentLevel
            preferences[CurrentLevelPointsKey] = levelProgress.currentLevelPoints
            if (waitReductionApplied > 0) {
                preferences[RewardedWindowKey] = releasePlan.nextReleaseWindow.toString()
                preferences[AdjustedNextReleaseWindowKey] = adjustedWindow.toString()
            }
            preferences[LastGameTypeKey] = gameType
            preferences[LastTaskTypeKey] = taskType.id.uppercase()
            preferences[LastLaunchedFromKey] = launchedFrom
            preferences[LastCompletedAtKey] = now.toString()
            score?.let { preferences[LastScoreKey] = it }
            durationSec?.let { preferences[LastDurationSecKey] = it }
            preferences[LastValidCompletionKey] = 1
            preferences[LastRewardWaitReductionMinutesKey] = waitReductionApplied
            preferences[LastRewardLevelPointsKey] = levelPointsAwarded
            preferences[LastWasFirstTimeRewardKey] = if (!completedEver) 1 else 0
            preferences[LastWasSameDayRepeatKey] = if (completedTodayCount > 0) 1 else 0
            preferences[LastAppliedWaitReductionKey] = if (waitReductionApplied > 0) 1 else 0

            val todayRecords = PsychologyTaskType.entries.associateWith { type ->
                val storedLastCompletedAt = if (type == taskType) {
                    now
                } else {
                    preferences[lastCompletedAtKey(type)]
                        ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                }
                TaskCompletionRecord(
                    taskType = type,
                    completedEver = if (type == taskType) true else preferences[completedEverKey(type)] == 1,
                    completedTodayCount = if (type == taskType) {
                        completedTodayCount + 1
                    } else if (isSameLocalDay(storedLastCompletedAt, now.toLocalDate())) {
                        preferences[completedTodayCountKey(type)] ?: 0
                    } else {
                        0
                    },
                    lastCompletedAt = storedLastCompletedAt,
                )
            }
            val currentWindowRewardAlreadyUsed = preferences[RewardedWindowKey] == releasePlan.nextReleaseWindow.toString()
            val statuses = PsychologyTaskRewardDefinitions.map { definition ->
                rewardStatusFor(
                    definition = definition,
                    record = todayRecords.getValue(definition.taskType),
                    currentWindowRewardAlreadyUsed = currentWindowRewardAlreadyUsed,
                )
            }
            val currentRecentRecommendations = preferences[RecentRecommendedTaskTypesKey].toTaskTypeList()
            val nextRecommendation = recommendPsychologyTask(
                taskStatuses = statuses,
                recentRecommendedTaskTypes = currentRecentRecommendations,
                currentUrgeIntensity = preferences[CurrentUrgeIntensityKey]?.coerceIn(0, 10),
                currentTriggerType = preferences[CurrentTriggerTypeKey].toEnumOrNull<TriggerType>(),
                currentTriggerSource = preferences[CurrentTriggerSourceKey].toEnumOrNull<TriggerSource>(),
                userEnergyState = preferences[UserEnergyStateKey].toEnumOrNull<EnergyState>(),
            )
            preferences[LastRecommendedTaskTypeKey] = nextRecommendation.taskType.id
            preferences[RecentRecommendedTaskTypesKey] = (currentRecentRecommendations + nextRecommendation.taskType)
                .takeLast(6)
                .joinToString(StoredListSeparator) { it.id }

            result = TaskCompletionResult(
                taskType = taskType,
                taskTitle = definition.taskTitle,
                waitReductionMinutes = waitReductionApplied,
                levelPointsAwarded = levelPointsAwarded,
                currentLevel = levelProgress.currentLevel,
                currentLevelPoints = levelProgress.currentLevelPoints,
                pointsNeededForNextLevel = levelProgress.pointsNeededForNextLevel,
            )
        }

        return result
    }

    private fun completedEverKey(taskType: PsychologyTaskType) =
        intPreferencesKey("${taskType.id}_completed_ever")

    private fun completedTodayCountKey(taskType: PsychologyTaskType) =
        intPreferencesKey("${taskType.id}_completed_today_count")

    private fun lastCompletedAtKey(taskType: PsychologyTaskType) =
        stringPreferencesKey("${taskType.id}_last_completed_at")

    private fun String?.toTaskTypeOrNull(): PsychologyTaskType? =
        PsychologyTaskType.entries.firstOrNull { it.id == this }

    private fun String?.toTaskTypeList(): List<PsychologyTaskType> {
        if (isNullOrBlank()) return emptyList()
        return split(StoredListSeparator).mapNotNull { it.toTaskTypeOrNull() }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private companion object {
        const val StoredListSeparator = "\u001F"
        val CurrentLevelKey = intPreferencesKey("current_level")
        val CurrentLevelPointsKey = intPreferencesKey("current_level_points")
        val RewardedWindowKey = stringPreferencesKey("rewarded_window_key")
        val AdjustedNextReleaseWindowKey = stringPreferencesKey("adjusted_next_release_window")
        val LastRecommendedTaskTypeKey = stringPreferencesKey("last_recommended_task_type")
        val LastCompletedTaskTypeKey = stringPreferencesKey("last_completed_task_type")
        val RecentRecommendedTaskTypesKey = stringPreferencesKey("recent_recommended_task_types")
        val CurrentUrgeIntensityKey = intPreferencesKey("current_urge_intensity")
        val CurrentTriggerTypeKey = stringPreferencesKey("current_trigger_type")
        val CurrentTriggerSourceKey = stringPreferencesKey("current_trigger_source")
        val UserEnergyStateKey = stringPreferencesKey("user_energy_state")
        val LastGameTypeKey = stringPreferencesKey("last_game_type")
        val LastTaskTypeKey = stringPreferencesKey("last_task_type")
        val LastLaunchedFromKey = stringPreferencesKey("last_launched_from")
        val LastCompletedAtKey = stringPreferencesKey("last_completed_at")
        val LastScoreKey = intPreferencesKey("last_score")
        val LastDurationSecKey = intPreferencesKey("last_duration_sec")
        val LastValidCompletionKey = intPreferencesKey("last_valid_completion")
        val LastRewardWaitReductionMinutesKey = intPreferencesKey("last_reward_wait_reduction_minutes")
        val LastRewardLevelPointsKey = intPreferencesKey("last_reward_level_points")
        val LastWasFirstTimeRewardKey = intPreferencesKey("last_was_first_time_reward")
        val LastWasSameDayRepeatKey = intPreferencesKey("last_was_same_day_repeat")
        val LastAppliedWaitReductionKey = intPreferencesKey("last_applied_wait_reduction")
    }
}
