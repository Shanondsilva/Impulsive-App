package com.impulsive.app.backend.domain.model.tasks

import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

enum class PsychologyTaskType(
    val id: String,
    val taskTitle: String,
) {
    ReflexOverride("reflex_override", "Reflex Override"),
    TriggerDecoder("trigger_decoder", "Cue Decoder"),
    ThoughtCapture("thought_capture", "Thought Capture"),
    ShortReadingBurst("short_reading_burst", "Short Reading Burst"),
    BlockCascade("block_cascade", "Block Cascade"),
    SkylineReset("skyline_reset", "SkyStack"),
    ResetRead("reset_read", "Reset Read"),
    FutureSelfMessage("future_self_message", "Future-Self Message"),
}

enum class TriggerType {
    Unknown,
    Stress,
    Loneliness,
    Shame,
    Sadness,
    Rejection,
    Memory,
    RepeatedThought,
}

enum class TriggerSource {
    SocialMedia,
    Browser,
    RiskySearch,
    RepeatedAppOpen,
}

enum class EnergyState {
    LowEnergy,
    Tired,
    Sleepy,
    CannotHandleDemandingTask,
}

data class TaskRewardDefinition(
    val taskType: PsychologyTaskType,
    val taskTitle: String,
    val firstTimeWaitReductionMinutes: Int,
    val firstTimeLevelPoints: Int,
    val repeatWaitReductionMinutes: Int,
    val repeatLevelPoints: Int,
    val sameDayWaitReductionMinutes: Int,
    val sameDayLevelPoints: Int,
    val optionalMonitoredTriggerBonusLevelPoints: Int? = null,
)

data class TaskCompletionRecord(
    val taskType: PsychologyTaskType,
    val completedEver: Boolean,
    val completedTodayCount: Int,
    val lastCompletedAt: LocalDateTime?,
    val firstTimeWaitCutClaimed: Boolean = false,
)

data class TaskRewardStatus(
    val taskType: PsychologyTaskType,
    val taskTitle: String,
    val firstTimeWaitReductionMinutes: Int,
    val firstTimeLevelPoints: Int,
    val repeatWaitReductionMinutes: Int,
    val repeatLevelPoints: Int,
    val sameDayWaitReductionMinutes: Int,
    val sameDayLevelPoints: Int,
    val completedEver: Boolean,
    val completedTodayCount: Int,
    val lastCompletedAt: LocalDateTime?,
    val currentWindowRewardAlreadyUsed: Boolean,
    val waitCutAlreadyUsedToday: Boolean,
    val availableWaitReductionMinutes: Int,
    val optionalMonitoredTriggerBonusLevelPoints: Int? = null,
) {
    val displayWaitReductionMinutes: Int
        get() = availableWaitReductionMinutes

    val displayLevelPoints: Int
        get() = when {
            !completedEver -> firstTimeLevelPoints
            taskType.isGameTask() &&
                completedTodayCount >= GameTaskSameDayOveruseThreshold -> GameTaskSameDayOveruseLevelPoints
            taskType.isGameTask() -> repeatLevelPoints
            completedTodayCount > 0 -> sameDayLevelPoints
            else -> repeatLevelPoints
        }

    val isFirstTimeBoostAvailable: Boolean
        get() = !completedEver
}

data class LevelProgressState(
    val currentLevel: Int,
    val currentLevelPoints: Int,
    val pointsNeededForNextLevel: Int,
)

data class TaskRewardState(
    val taskStatuses: List<TaskRewardStatus>,
    val currentWindowRewardAlreadyUsed: Boolean,
    val waitCutAlreadyUsedToday: Boolean,
    val currentLevel: Int,
    val currentLevelPoints: Int,
    val pointsNeededForNextLevel: Int,
    val nextReleaseWindow: LocalDateTime?,
    val adjustedNextReleaseWindow: LocalDateTime?,
    val lastRecommendedTaskType: PsychologyTaskType?,
    val lastCompletedTaskType: PsychologyTaskType?,
    val recentRecommendedTaskTypes: List<PsychologyTaskType>,
    val currentUrgeIntensity: Int?,
    val currentTriggerType: TriggerType?,
    val currentTriggerSource: TriggerSource?,
    val userEnergyState: EnergyState?,
    val recommendedTaskType: PsychologyTaskType,
    val recommendedTaskReason: String,
    val lastCompletionResult: TaskCompletionResult? = null,
)

data class TaskRewardStoreState(
    val records: Map<PsychologyTaskType, TaskCompletionRecord>,
    val currentLevel: Int,
    val currentLevelPoints: Int,
    val rewardedWindowKey: String?,
    val rewardedWaitCutDate: LocalDate?,
    val adjustedNextReleaseWindow: LocalDateTime?,
    val lastRecommendedTaskType: PsychologyTaskType?,
    val lastCompletedTaskType: PsychologyTaskType?,
    val recentRecommendedTaskTypes: List<PsychologyTaskType>,
    val currentUrgeIntensity: Int?,
    val currentTriggerType: TriggerType?,
    val currentTriggerSource: TriggerSource?,
    val userEnergyState: EnergyState?,
)

data class TaskCompletionResult(
    val taskType: PsychologyTaskType,
    val taskTitle: String,
    val waitReductionMinutes: Int,
    val levelPointsAwarded: Int,
    val currentLevel: Int,
    val currentLevelPoints: Int,
    val pointsNeededForNextLevel: Int,
)

val PsychologyTaskRewardDefinitions = listOf(
    // Game Task LP rule:
    // Easier 90-second games: 10 LP first-time, 2 LP repeat.
    // Harder 90-second games: 15 LP first-time, 3 LP repeat.
    // From the 6th same-game completion on the same local day, award 1 LP.
    // Do not exceed these values for future games without explicit founder approval.
    TaskRewardDefinition(PsychologyTaskType.ReflexOverride, "Reflex Override", 120, 10, 45, 2, 0, 1),
    TaskRewardDefinition(PsychologyTaskType.TriggerDecoder, "Trigger Decoder", 45, 15, 30, 10, 10, 3, optionalMonitoredTriggerBonusLevelPoints = 5),
    TaskRewardDefinition(PsychologyTaskType.ThoughtCapture, "Thought Capture", 60, 18, 45, 15, 20, 6),
    TaskRewardDefinition(PsychologyTaskType.ShortReadingBurst, "Short Reading Burst", 60, 15, 30, 8, 10, 2),
    TaskRewardDefinition(PsychologyTaskType.BlockCascade, "Block Cascade", 90, 15, 45, 3, 10, 1),
    TaskRewardDefinition(PsychologyTaskType.SkylineReset, "SkyStack", 90, 15, 45, 3, 10, 1),
    TaskRewardDefinition(PsychologyTaskType.ResetRead, "Reset Read", 60, 15, 30, 8, 10, 2),
    TaskRewardDefinition(PsychologyTaskType.FutureSelfMessage, "Future-Self Message", 45, 12, 30, 10, 15, 5),
)

fun PsychologyTaskType.isGameTask(): Boolean =
    this == PsychologyTaskType.ReflexOverride ||
        this == PsychologyTaskType.BlockCascade ||
        this == PsychologyTaskType.SkylineReset

fun pointsNeededForNextLevel(level: Int): Int = when (level) {
    1 -> 100
    2 -> 150
    3 -> 220
    4 -> 300
    else -> 300
}

fun addLevelPoints(
    currentLevel: Int,
    currentLevelPoints: Int,
    pointsToAdd: Int,
): LevelProgressState {
    var level = currentLevel.coerceAtLeast(1)
    var points = currentLevelPoints.coerceAtLeast(0) + pointsToAdd.coerceAtLeast(0)
    var threshold = pointsNeededForNextLevel(level)

    while (level < 5 && points >= threshold) {
        points -= threshold
        level += 1
        threshold = pointsNeededForNextLevel(level)
    }

    if (level >= 5) {
        level = 5
        threshold = pointsNeededForNextLevel(level)
        points = points.coerceAtMost(threshold)
    }

    return LevelProgressState(
        currentLevel = level,
        currentLevelPoints = points,
        pointsNeededForNextLevel = threshold,
    )
}

fun rewardStatusFor(
    definition: TaskRewardDefinition,
    record: TaskCompletionRecord,
    currentWindowRewardAlreadyUsed: Boolean,
    waitCutAlreadyUsedToday: Boolean,
    availableWaitReductionMinutes: Int,
): TaskRewardStatus = TaskRewardStatus(
    taskType = definition.taskType,
    taskTitle = definition.taskTitle,
    firstTimeWaitReductionMinutes = definition.firstTimeWaitReductionMinutes,
    firstTimeLevelPoints = definition.firstTimeLevelPoints,
    repeatWaitReductionMinutes = definition.repeatWaitReductionMinutes,
    repeatLevelPoints = definition.repeatLevelPoints,
    sameDayWaitReductionMinutes = definition.sameDayWaitReductionMinutes,
    sameDayLevelPoints = definition.sameDayLevelPoints,
    completedEver = record.completedEver,
    completedTodayCount = record.completedTodayCount,
    lastCompletedAt = record.lastCompletedAt,
    currentWindowRewardAlreadyUsed = currentWindowRewardAlreadyUsed,
    waitCutAlreadyUsedToday = waitCutAlreadyUsedToday,
    availableWaitReductionMinutes = availableWaitReductionMinutes,
    optionalMonitoredTriggerBonusLevelPoints = definition.optionalMonitoredTriggerBonusLevelPoints,
)

fun calculateLevelPointsForTask(
    definition: TaskRewardDefinition,
    record: TaskCompletionRecord,
): Int {
    return when {
        !record.completedEver -> definition.firstTimeLevelPoints
        definition.taskType.isGameTask() &&
            record.completedTodayCount >= GameTaskSameDayOveruseThreshold -> GameTaskSameDayOveruseLevelPoints
        definition.taskType.isGameTask() -> definition.repeatLevelPoints
        record.completedTodayCount > 0 -> definition.sameDayLevelPoints
        else -> definition.repeatLevelPoints
    }
}

fun TaskRewardStoreState.toTaskRewardState(
    releasePlan: ReleasePlanState,
): TaskRewardState {
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val currentWindowRewardAlreadyUsed = rewardedWindowKey == releasePlan.nextReleaseWindow.toString()
    val waitCutAlreadyUsedToday = rewardedWaitCutDate == today
    val statuses = PsychologyTaskRewardDefinitions.map { definition ->
        val record = records[definition.taskType] ?: TaskCompletionRecord(
            taskType = definition.taskType,
            completedEver = false,
            completedTodayCount = 0,
            lastCompletedAt = null,
        )
        val firstTimeWaitCutAvailable = !record.firstTimeWaitCutClaimed
        val requestedWaitCut = calculateDynamicRequestedWaitReductionMinutes(
            firstTimeWaitCutAvailable = firstTimeWaitCutAvailable,
            releasePlan = releasePlan,
        )
        val availableWaitCut = calculateWaitReductionMinutesToApply(
            requestedReductionMinutes = requestedWaitCut,
            now = now,
            releasePlan = releasePlan,
            waitCutAlreadyUsedToday = waitCutAlreadyUsedToday,
        )
        rewardStatusFor(
            definition = definition,
            record = record,
            currentWindowRewardAlreadyUsed = currentWindowRewardAlreadyUsed,
            waitCutAlreadyUsedToday = waitCutAlreadyUsedToday,
            availableWaitReductionMinutes = availableWaitCut,
        )
    }
    val recommendation = recommendPsychologyTask(
        taskStatuses = statuses,
        recentRecommendedTaskTypes = recentRecommendedTaskTypes,
        currentUrgeIntensity = currentUrgeIntensity,
        currentTriggerType = currentTriggerType,
        currentTriggerSource = currentTriggerSource,
        userEnergyState = userEnergyState,
    )

    return TaskRewardState(
        taskStatuses = statuses,
        currentWindowRewardAlreadyUsed = currentWindowRewardAlreadyUsed,
        waitCutAlreadyUsedToday = waitCutAlreadyUsedToday,
        currentLevel = currentLevel,
        currentLevelPoints = currentLevelPoints,
        pointsNeededForNextLevel = pointsNeededForNextLevel(currentLevel),
        nextReleaseWindow = releasePlan.nextReleaseWindow,
        adjustedNextReleaseWindow = adjustedNextReleaseWindow
            ?.takeIf { rewardedWindowKey == releasePlan.nextReleaseWindow.toString() },
        lastRecommendedTaskType = lastRecommendedTaskType,
        lastCompletedTaskType = lastCompletedTaskType,
        recentRecommendedTaskTypes = recentRecommendedTaskTypes,
        currentUrgeIntensity = currentUrgeIntensity,
        currentTriggerType = currentTriggerType,
        currentTriggerSource = currentTriggerSource,
        userEnergyState = userEnergyState,
        recommendedTaskType = recommendation.taskType,
        recommendedTaskReason = recommendation.reason,
    )
}

data class TaskRecommendation(
    val taskType: PsychologyTaskType,
    val reason: String,
)

fun recommendPsychologyTask(
    taskStatuses: List<TaskRewardStatus>,
    recentRecommendedTaskTypes: List<PsychologyTaskType>,
    currentUrgeIntensity: Int?,
    currentTriggerType: TriggerType?,
    currentTriggerSource: TriggerSource?,
    userEnergyState: EnergyState?,
): TaskRecommendation {
    val candidates = when {
        userEnergyState != null -> listOf(
            PsychologyTaskType.ResetRead,
            PsychologyTaskType.FutureSelfMessage,
        )
        currentUrgeIntensity != null && currentUrgeIntensity >= 7 ->
            listOf(
                PsychologyTaskType.BlockCascade,
                PsychologyTaskType.SkylineReset,
                PsychologyTaskType.ReflexOverride,
            )
        currentTriggerSource != null -> listOf(PsychologyTaskType.BlockCascade, PsychologyTaskType.SkylineReset, PsychologyTaskType.ReflexOverride)
        currentTriggerType == TriggerType.RepeatedThought || currentTriggerType == TriggerType.Memory ->
            listOf(PsychologyTaskType.FutureSelfMessage)
        currentTriggerType in setOf(
            TriggerType.Stress,
            TriggerType.Loneliness,
            TriggerType.Shame,
            TriggerType.Sadness,
            TriggerType.Rejection,
        ) -> listOf(PsychologyTaskType.FutureSelfMessage, PsychologyTaskType.ResetRead)
        currentTriggerType == TriggerType.Unknown -> listOf(PsychologyTaskType.ResetRead)
        else -> listOf(
            PsychologyTaskType.ReflexOverride,
            PsychologyTaskType.BlockCascade,
            PsychologyTaskType.SkylineReset,
            PsychologyTaskType.ResetRead,
            PsychologyTaskType.FutureSelfMessage,
        )
    }
    val selected = chooseRecommendedTask(
        candidates = candidates,
        taskStatuses = taskStatuses,
        recentRecommendedTaskTypes = recentRecommendedTaskTypes,
    )
    return TaskRecommendation(
        taskType = selected,
        reason = recommendationReasonFor(selected),
    )
}

private fun chooseRecommendedTask(
    candidates: List<PsychologyTaskType>,
    taskStatuses: List<TaskRewardStatus>,
    recentRecommendedTaskTypes: List<PsychologyTaskType>,
): PsychologyTaskType {
    val allCandidates = candidates.ifEmpty { listOf(PsychologyTaskType.ReflexOverride) }
    val repeatedTooOften = recentRecommendedTaskTypes.takeLast(2)
        .takeIf { it.size == 2 && it[0] == it[1] }
        ?.firstOrNull()
    val antiRepeatCandidates = if (repeatedTooOften != null && allCandidates.size > 1) {
        allCandidates.filterNot { it == repeatedTooOften }
    } else {
        allCandidates
    }
    val statusesByType = taskStatuses.associateBy { it.taskType }
    return antiRepeatCandidates.firstOrNull { statusesByType[it]?.isFirstTimeBoostAvailable == true }
        ?: antiRepeatCandidates.first()
}

private fun recommendationReasonFor(taskType: PsychologyTaskType): String = when (taskType) {
    PsychologyTaskType.ReflexOverride -> "Strong novelty and a quick attention pivot."
    PsychologyTaskType.BlockCascade -> "Loads visual attention so the difficult image has less room."
    PsychologyTaskType.SkylineReset -> "Steady visual stacking gives attention somewhere calm to land."
    PsychologyTaskType.ResetRead -> "A short, focused read to redirect attention."
    PsychologyTaskType.FutureSelfMessage -> "Replays your own reason in your own words."
    PsychologyTaskType.TriggerDecoder -> "Hidden for now; not shown in the UI."
    PsychologyTaskType.ThoughtCapture -> "Hidden for now; Future-Self Message owns the journal slot."
    PsychologyTaskType.ShortReadingBurst -> "Hidden for now; Reset Read owns the reading slot."
}

fun calculateRewardedReleasePlan(
    releasePlan: ReleasePlanState,
    adjustedNextReleaseWindow: LocalDateTime?,
    now: LocalDateTime,
): ReleasePlanState {
    val adjusted = adjustedNextReleaseWindow
        ?.takeIf { it.isAfter(now) && it.isBefore(releasePlan.nextReleaseWindow) }
        ?: releasePlan.nextReleaseWindow
    return releasePlan.copy(
        adjustedNextReleaseWindow = adjusted,
        timeUntilNextReleaseWindow = Duration.between(now, adjusted).coerceAtLeast(Duration.ZERO),
    )
}

fun calculateDynamicRequestedWaitReductionMinutes(
    firstTimeWaitCutAvailable: Boolean,
    releasePlan: ReleasePlanState,
): Int {
    val averageGapMinutes = releasePlan.averagePlannedWindowGapMinutes()
    val scheduleCap = if (firstTimeWaitCutAvailable) {
        (averageGapMinutes * 0.50f).roundToInt()
    } else {
        (averageGapMinutes * 0.18f).roundToInt()
    }
    val maximum = if (firstTimeWaitCutAvailable) 90 else 30
    return minOf(maximum, scheduleCap).coerceAtLeast(0)
}

private fun ReleasePlanState.averagePlannedWindowGapMinutes(): Int {
    val gaps = plannedWindowsToday
        .zipWithNext { first, second -> Duration.between(first, second).toMinutes() }
        .filter { it > 0L }

    if (gaps.isNotEmpty()) {
        return gaps.average().roundToInt().coerceAtLeast(1)
    }

    val start = LocalDateTime.of(nextReleaseWindow.toLocalDate(), activeDayStart)
    val endDate = if (activeDayEnd.isAfter(activeDayStart)) {
        nextReleaseWindow.toLocalDate()
    } else {
        nextReleaseWindow.toLocalDate().plusDays(1)
    }
    val end = LocalDateTime.of(endDate, activeDayEnd)
    val activeMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(1L)
    return (activeMinutes / selectedDailyUrgeCount.coerceAtLeast(1)).toInt().coerceAtLeast(1)
}

fun calculateWaitReductionMinutesToApply(
    requestedReductionMinutes: Int,
    now: LocalDateTime,
    releasePlan: ReleasePlanState,
    waitCutAlreadyUsedToday: Boolean,
): Int {
    if (releasePlan.isInsideReleaseWindow || waitCutAlreadyUsedToday) return 0
    val currentWaitMinutes = Duration.between(now, releasePlan.adjustedNextReleaseWindow).toMinutes()
    if (currentWaitMinutes <= MinimumProtectionFloorMinutes) return 0
    val maximumReduction = currentWaitMinutes - MinimumProtectionFloorMinutes
    return minOf(requestedReductionMinutes.toLong(), maximumReduction).coerceAtLeast(0L).toInt()
}

fun isSameLocalDay(
    value: LocalDateTime?,
    today: LocalDate,
): Boolean = value?.toLocalDate() == today

const val MinimumProtectionFloorMinutes = 20L
const val GameTaskSameDayOveruseThreshold = 5
const val GameTaskSameDayOveruseLevelPoints = 1
const val InitialLevel = 1
const val InitialLevelPoints = 0

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this
