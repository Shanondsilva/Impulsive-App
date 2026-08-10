package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
import com.impulsive.app.backend.domain.model.tasks.calculateDynamicRequestedWaitReductionMinutes
import com.impulsive.app.backend.domain.model.tasks.calculateLevelPointsForTask
import com.impulsive.app.backend.domain.model.tasks.calculateWaitReductionMinutesToApply
import com.impulsive.app.backend.domain.model.tasks.isSameLocalDay
import com.impulsive.app.backend.domain.model.tasks.pointsNeededForNextLevel
import com.impulsive.app.backend.domain.model.tasks.recommendPsychologyTask
import com.impulsive.app.backend.domain.model.tasks.rewardStatusFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val Context.taskRewardDataStore by preferencesDataStore(name = "task_rewards")

class TaskRewardDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.taskRewardDataStore)

    val storeState: Flow<TaskRewardStoreState> = dataStore.data.map { preferences ->
        val today = LocalDate.now()
        val rewardedWaitCutDate = preferences[RewardedWaitCutDateKey]
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
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
                firstTimeWaitCutClaimed = preferences[firstTimeWaitCutClaimedKey(taskType)] == 1,
            )
        }

        TaskRewardStoreState(
            records = records,
            currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
            currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
            rewardedWindowKey = preferences[RewardedWindowKey],
            rewardedWaitCutDate = rewardedWaitCutDate,
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

    val lastFocusTimeAward: Flow<Pair<String, Int>?> = dataStore.data.map { preferences ->
        val sessionId = preferences[LastFocusTimeAwardedSessionIdKey] ?: return@map null
        sessionId to (preferences[LastFocusTimeAwardedPointsKey] ?: 0)
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
        completionToken: String? = null,
    ): TaskCompletionResult {
        val normalizedCompletionToken = TaskCompletionReceiptLedger.normalizeToken(completionToken)

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
            val existingReceipts = TaskCompletionReceiptLedger.decode(
                preferences[TaskCompletionReceiptsKey],
            )

            val existingReceipt = normalizedCompletionToken?.let { token ->
                existingReceipts.lastOrNull { it.completionToken == token }
            }

            /*
             * The reward and its receipt are committed through the same DataStore edit.
             * A recreated process therefore receives the original result instead of
             * applying another completion.
             */
            if (existingReceipt != null) {
                result = existingReceipt.result

                return@edit
            }

            if (!validCompletion) {
                preferences[LastGameTypeKey] = gameType
                preferences[LastTaskTypeKey] = taskType.id.uppercase()
                preferences[LastLaunchedFromKey] = launchedFrom
                preferences[LastCompletedAtKey] = now.toString()
                score?.let { preferences[LastScoreKey] = it }
                durationSec?.let { preferences[LastDurationSecKey] = it }
                preferences[LastValidCompletionKey] = 0

                val currentLevel = preferences[CurrentLevelKey] ?: InitialLevel

                result = TaskCompletionResult(
                    taskType = taskType,
                    taskTitle = taskType.taskTitle,
                    waitReductionMinutes = 0,
                    levelPointsAwarded = 0,
                    currentLevel = currentLevel,
                    currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                    pointsNeededForNextLevel = pointsNeededForNextLevel(currentLevel),
                )

                preferences.storeCompletionReceipt(
                    existingReceipts = existingReceipts,
                    completionToken = normalizedCompletionToken,
                    result = result,
                )

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
            val firstTimeWaitCutClaimed = preferences[firstTimeWaitCutClaimedKey(taskType)] == 1
            val waitCutAlreadyUsedToday = preferences[RewardedWaitCutDateKey]
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { it == now.toLocalDate() } == true

            val requestedWaitReduction = calculateDynamicRequestedWaitReductionMinutes(
                firstTimeWaitCutAvailable = !firstTimeWaitCutClaimed,
                releasePlan = releasePlan,
            )
            val levelPointsAwarded = calculateLevelPointsForTask(
                definition = definition,
                record = TaskCompletionRecord(
                    taskType = taskType,
                    completedEver = completedEver,
                    completedTodayCount = completedTodayCount,
                    lastCompletedAt = lastCompletedAt,
                    firstTimeWaitCutClaimed = firstTimeWaitCutClaimed,
                ),
            )
            val waitReductionApplied = calculateWaitReductionMinutesToApply(
                requestedReductionMinutes = requestedWaitReduction,
                now = now,
                releasePlan = releasePlan,
                waitCutAlreadyUsedToday = waitCutAlreadyUsedToday,
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
                preferences[RewardedWaitCutDateKey] = now.toLocalDate().toString()
                if (!firstTimeWaitCutClaimed) {
                    preferences[firstTimeWaitCutClaimedKey(taskType)] = 1
                }
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
                    firstTimeWaitCutClaimed = if (type == taskType) {
                        if (waitReductionApplied > 0 && !firstTimeWaitCutClaimed) {
                            true
                        } else {
                            firstTimeWaitCutClaimed
                        }
                    } else {
                        preferences[firstTimeWaitCutClaimedKey(type)] == 1
                    },
                )
            }
            val currentWindowRewardAlreadyUsed = preferences[RewardedWindowKey] == releasePlan.nextReleaseWindow.toString()
            val waitCutAlreadyUsedTodayForStatuses = preferences[RewardedWaitCutDateKey]
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { it == now.toLocalDate() } == true
            val statuses = PsychologyTaskRewardDefinitions.map { definition ->
                val record = todayRecords.getValue(definition.taskType)
                val requestedWaitCut = calculateDynamicRequestedWaitReductionMinutes(
                    firstTimeWaitCutAvailable = !record.firstTimeWaitCutClaimed,
                    releasePlan = releasePlan,
                )
                val availableWaitCut = calculateWaitReductionMinutesToApply(
                    requestedReductionMinutes = requestedWaitCut,
                    now = now,
                    releasePlan = releasePlan,
                    waitCutAlreadyUsedToday = waitCutAlreadyUsedTodayForStatuses,
                )
                rewardStatusFor(
                    definition = definition,
                    record = record,
                    currentWindowRewardAlreadyUsed = currentWindowRewardAlreadyUsed,
                    waitCutAlreadyUsedToday = waitCutAlreadyUsedTodayForStatuses,
                    availableWaitReductionMinutes = availableWaitCut,
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

            preferences.storeCompletionReceipt(
                existingReceipts = existingReceipts,
                completionToken = normalizedCompletionToken,
                result = result,
            )
        }

        return result
    }

    private fun MutablePreferences.storeCompletionReceipt(
        existingReceipts: List<TaskCompletionReceipt>,
        completionToken: String?,
        result: TaskCompletionResult,
    ) {
        if (completionToken == null) {
            return
        }

        this[TaskCompletionReceiptsKey] = TaskCompletionReceiptLedger.encode(
            TaskCompletionReceiptLedger.upsert(
                receipts = existingReceipts,
                receipt = TaskCompletionReceipt(
                    completionToken = completionToken,
                    result = result,
                ),
            ),
        )
    }

    private fun completedEverKey(taskType: PsychologyTaskType) =
        intPreferencesKey("${taskType.id}_completed_ever")

    private fun completedEverKey(taskId: String) =
        intPreferencesKey("${taskId}_completed_ever")

    private fun completedTodayCountKey(taskType: PsychologyTaskType) =
        intPreferencesKey("${taskType.id}_completed_today_count")

    private fun completedTodayCountKey(taskId: String) =
        intPreferencesKey("${taskId}_completed_today_count")

    private fun lastCompletedAtKey(taskType: PsychologyTaskType) =
        stringPreferencesKey("${taskType.id}_last_completed_at")

    private fun lastCompletedAtKey(taskId: String) =
        stringPreferencesKey("${taskId}_last_completed_at")

    private fun firstTimeWaitCutClaimedKey(taskType: PsychologyTaskType) =
        intPreferencesKey("first_time_wait_cut_claimed_${taskType.id}")

    private fun firstTimeWaitCutClaimedKey(taskId: String) =
        intPreferencesKey("first_time_wait_cut_claimed_${taskId}")

    private fun String?.toTaskTypeOrNull(): PsychologyTaskType? =
        PsychologyTaskType.entries.firstOrNull { it.id == this }

    /** Adds Level Points through the same level-up math tasks use. */
    suspend fun awardLevelPoints(points: Int) {
        if (points <= 0) return
        dataStore.edit { preferences ->
            val levelProgress = addLevelPoints(
                currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
                currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                pointsToAdd = points,
            )
            preferences[CurrentLevelKey] = levelProgress.currentLevel
            preferences[CurrentLevelPointsKey] = levelProgress.currentLevelPoints
        }
    }

    suspend fun awardFocusTimePointsIfEligible(
        focusSessionId: String,
        completedAtMillis: Long,
    ): Int {
        if (focusSessionId.isBlank()) return 0
        val completedDate = completedAtMillis.toLocalDateInUserZone().toString()
        var awardedPoints = 0

        dataStore.edit { preferences ->
            val awardedSessionIds = preferences[FocusTimeAwardedSessionIdsKey].toStringList()
            if (focusSessionId in awardedSessionIds) {
                preferences[LastFocusTimeAwardedSessionIdKey] = focusSessionId
                preferences[LastFocusTimeAwardedPointsKey] = 0
                return@edit
            }

            val storedDate = preferences[FocusTimePointsAwardedDateKey]
            val normalPointsAwardedToday = if (storedDate == completedDate) {
                preferences[FocusTimeNormalPointsAwardedTodayKey] ?: 0
            } else {
                0
            }
            val remainingNormalPointsToday =
                (FocusTimeDailyNormalCap - normalPointsAwardedToday).coerceAtLeast(0)
            val firstBonusAlreadyAwarded = preferences[FocusTimeFirstBonusAwardedKey] == 1
            val normalPoints = minOf(FocusTimeNormalPoints, remainingNormalPointsToday)
            val bonusPoints = if (firstBonusAlreadyAwarded) 0 else FocusTimeFirstBonusPoints
            awardedPoints = normalPoints + bonusPoints

            preferences[FocusTimeAwardedSessionIdsKey] =
                (awardedSessionIds + focusSessionId)
                    .takeLast(FocusTimeMaxAwardedSessionIds)
                    .joinToString(StoredListSeparator)
            preferences[FocusTimePointsAwardedDateKey] = completedDate
            preferences[FocusTimeNormalPointsAwardedTodayKey] = normalPointsAwardedToday + normalPoints
            if (!firstBonusAlreadyAwarded) {
                preferences[FocusTimeFirstBonusAwardedKey] = 1
            }
            preferences[LastFocusTimeAwardedSessionIdKey] = focusSessionId
            preferences[LastFocusTimeAwardedPointsKey] = awardedPoints

            if (awardedPoints > 0) {
                val levelProgress = addLevelPoints(
                    currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
                    currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                    pointsToAdd = awardedPoints,
                )
                preferences[CurrentLevelKey] = levelProgress.currentLevel
                preferences[CurrentLevelPointsKey] = levelProgress.currentLevelPoints
            }
        }

        return awardedPoints
    }

    /**
     * Awards Level Points for a daily journal reward, but only once per calendar day,
     * so the reward cannot be farmed. Returns true if it awarded, false if already
     * given today.
     */
    suspend fun awardNoteCreationPointsIfNewDay(points: Int): Boolean {
        if (points <= 0) return false
        val today = LocalDate.now().toString()
        var awarded = false
        dataStore.edit { preferences ->
            if (preferences[NoteRewardDateKey] == today) return@edit
            val levelProgress = addLevelPoints(
                currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
                currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                pointsToAdd = points,
            )
            preferences[CurrentLevelKey] = levelProgress.currentLevel
            preferences[CurrentLevelPointsKey] = levelProgress.currentLevelPoints
            preferences[NoteRewardDateKey] = today
            awarded = true
        }
        return awarded
    }

    /**
     * Awards Level Points for answering the end-of-day feedback notification, once per
     * calendar day, tracked separately from the written-note reward.
     */
    suspend fun awardFeedbackAnswerPointsIfNewDay(points: Int): Boolean {
        if (points <= 0) return false
        val today = LocalDate.now().toString()
        var awarded = false
        dataStore.edit { preferences ->
            if (preferences[FeedbackAnswerRewardDateKey] == today) return@edit
            val levelProgress = addLevelPoints(
                currentLevel = preferences[CurrentLevelKey] ?: InitialLevel,
                currentLevelPoints = preferences[CurrentLevelPointsKey] ?: InitialLevelPoints,
                pointsToAdd = points,
            )
            preferences[CurrentLevelKey] = levelProgress.currentLevel
            preferences[CurrentLevelPointsKey] = levelProgress.currentLevelPoints
            preferences[FeedbackAnswerRewardDateKey] = today
            awarded = true
        }
        return awarded
    }

    suspend fun removeLegacyDeletedTaskRewards() {
        dataStore.edit { preferences ->
            LegacyDeletedTaskIds.forEach { taskId ->
                preferences.remove(completedEverKey(taskId))
                preferences.remove(completedTodayCountKey(taskId))
                preferences.remove(lastCompletedAtKey(taskId))
                preferences.remove(firstTimeWaitCutClaimedKey(taskId))
            }

            val lastRecommendedTaskType = preferences[LastRecommendedTaskTypeKey]
            if (lastRecommendedTaskType != null && lastRecommendedTaskType in LegacyDeletedTaskIds) {
                preferences.remove(LastRecommendedTaskTypeKey)
            }

            val lastCompletedTaskType = preferences[LastCompletedTaskTypeKey]
            if (lastCompletedTaskType != null && lastCompletedTaskType in LegacyDeletedTaskIds) {
                preferences.remove(LastCompletedTaskTypeKey)
            }

            val cleanedRecent = preferences[RecentRecommendedTaskTypesKey]
                ?.split(StoredListSeparator)
                ?.filterNot { it in LegacyDeletedTaskIds }
                ?.joinToString(StoredListSeparator)

            if (cleanedRecent.isNullOrBlank()) {
                preferences.remove(RecentRecommendedTaskTypesKey)
            } else {
                preferences[RecentRecommendedTaskTypesKey] = cleanedRecent
            }

            val legacyUppercaseIds = LegacyDeletedTaskIds.map { it.uppercase() }.toSet()

            val lastTaskType = preferences[LastTaskTypeKey]
            if (lastTaskType != null && lastTaskType in legacyUppercaseIds) {
                preferences.remove(LastTaskTypeKey)
            }

            val lastGameType = preferences[LastGameTypeKey]
            if (lastGameType != null && lastGameType in legacyUppercaseIds) {
                preferences.remove(LastGameTypeKey)
            }
        }
    }

    private fun String?.toTaskTypeList(): List<PsychologyTaskType> {
        if (isNullOrBlank()) return emptyList()
        return split(StoredListSeparator).mapNotNull { it.toTaskTypeOrNull() }
    }

    private fun String?.toStringList(): List<String> {
        if (isNullOrBlank()) return emptyList()
        return split(StoredListSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun Long.toLocalDateInUserZone(): LocalDate =
        Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private companion object {
        const val StoredListSeparator = "\u001F"
        const val FocusTimeDailyNormalCap = 3
        const val FocusTimeNormalPoints = 3
        const val FocusTimeFirstBonusPoints = 12
        const val FocusTimeMaxAwardedSessionIds = 200
        val LegacyDeletedTaskIds = setOf(
            "trigger_decoder",
            "thought_capture",
            "short_reading_burst",
        )
        val CurrentLevelKey = intPreferencesKey("current_level")
        val CurrentLevelPointsKey = intPreferencesKey("current_level_points")
        val RewardedWindowKey = stringPreferencesKey("rewarded_window_key")
        val RewardedWaitCutDateKey = stringPreferencesKey("rewarded_wait_cut_date")
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
        val NoteRewardDateKey = stringPreferencesKey("note_reward_date")
        val FeedbackAnswerRewardDateKey = stringPreferencesKey("feedback_answer_reward_date")
        val LastRewardWaitReductionMinutesKey = intPreferencesKey("last_reward_wait_reduction_minutes")
        val LastRewardLevelPointsKey = intPreferencesKey("last_reward_level_points")
        val LastWasFirstTimeRewardKey = intPreferencesKey("last_was_first_time_reward")
        val LastWasSameDayRepeatKey = intPreferencesKey("last_was_same_day_repeat")
        val LastAppliedWaitReductionKey = intPreferencesKey("last_applied_wait_reduction")
        val FocusTimeFirstBonusAwardedKey = intPreferencesKey("focus_time_first_bonus_awarded")
        val FocusTimePointsAwardedDateKey = stringPreferencesKey("focus_time_points_awarded_date")
        val FocusTimeNormalPointsAwardedTodayKey =
            intPreferencesKey("focus_time_normal_points_awarded_today")
        val FocusTimeAwardedSessionIdsKey = stringPreferencesKey("focus_time_awarded_session_ids")
        val LastFocusTimeAwardedSessionIdKey =
            stringPreferencesKey("last_focus_time_awarded_session_id")
        val LastFocusTimeAwardedPointsKey = intPreferencesKey("last_focus_time_awarded_points")
        val TaskCompletionReceiptsKey = stringPreferencesKey("task_completion_receipts")
    }
}
