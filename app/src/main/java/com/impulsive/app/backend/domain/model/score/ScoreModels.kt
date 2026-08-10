package com.impulsive.app.backend.domain.model.score

import com.impulsive.app.backend.domain.model.release.TaperHistoryEntry
import com.impulsive.app.backend.domain.model.release.WindowOutcomeRecord
import com.impulsive.app.backend.domain.model.release.WindowOutcomeStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val RecentSessionLimit = 10

private val DefaultScoreGameOrder = listOf(
    ScoreGameType.Snake,
    ScoreGameType.BlockCascade,
    ScoreGameType.UrgeSurvival,
    ScoreGameType.FluidRegulation,
)

enum class ScoreRange(val label: String) {
    Today("Today"),
    Week("Week"),
    Month("Month"),
    Year("Year"),
}

enum class ScoreGameType(
    val id: String,
    val displayName: String,
) {
    ReflexOverride("REFLEX_OVERRIDE", "Reflex Override"),
    BlockCascade("BLOCK_CASCADE", "Block Cascade"),
    SkylineReset("SKYLINE_RESET", "SkyStack"),
    UrgeSurvival("URGE_SURVIVAL", "Wave Practice"),
    FluidRegulation("FLUID_REGULATION", "Fluid Regulation"),
    PrecisionFocus("PRECISION_FOCUS", "Precision Focus"),
    DopamineRunner("DOPAMINE_RUNNER", "Dopamine Runner"),
    BreathControl("BREATH_CONTROL", "Breath Control"),
    RageDischarge("RAGE_DISCHARGE", "Rage Discharge"),
    RhythmTiles("RHYTHM_TILES", "Rhythm Tiles"),
    Snake("SNAKE", "Snake"),
    FocusSession("FOCUS_SESSION", "Focus session"),
    Unknown("UNKNOWN", "Pivot Game");

    companion object {
        fun fromId(id: String): ScoreGameType = entries.firstOrNull { it.id == id } ?: Unknown
    }
}

enum class ScoreSessionOutcome(
    val id: String,
    val label: String,
) {
    WalkedAway("WALKED_AWAY", "Walked away"),
    ContinuedWithIntention("CONTINUED_WITH_INTENTION", "Continued with intention"),
    Completed("COMPLETED", "Completed"),
    Replayed("REPLAYED", "Replayed"),
    Abandoned("ABANDONED", "Abandoned");

    companion object {
        fun fromId(id: String): ScoreSessionOutcome = entries.firstOrNull { it.id == id } ?: Completed
    }
}

data class UrgeEventRecord(
    val date: LocalDate,
    val source: String = "app",
    val packageName: String? = null,
    val at: LocalDateTime? = null,
)

data class UrgeTrendDayBar(
    val date: LocalDate,
    val actual: Int,
    val baseline: Int,
) {
    val aboveBaseline: Boolean get() = actual > baseline
}

data class UrgeTrendState(
    val baselinePerDay: Int,
    val bars: List<UrgeTrendDayBar>,
    val totalActual: Int,
    val totalExpected: Int,
    val hasData: Boolean,
) {
    val difference: Int get() = totalActual - totalExpected
    val label: String get() = when {
        !hasData -> "No difficult-moment trend yet"
        difference < 0 -> "Below baseline"
        difference == 0 -> "On baseline"
        else -> "Above baseline"
    }
}

data class WindowUsageState(
    val plannedPerDay: Int,
    val todayUsed: Int,
    val todaySkipped: Int,
    val rangeUsed: Int,
    val rangeSkipped: Int,
    val hasData: Boolean,
)

fun newScoreSessionId(): Long = System.currentTimeMillis()

data class ScoreSessionRecord(
    val id: Long = newScoreSessionId(),
    val gameType: ScoreGameType,
    val score: Int,
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime,
    val durationSec: Int,
    val urgeBefore: Int? = null,
    val urgeAfter: Int? = null,
    val outcome: ScoreSessionOutcome,
    val validCompletion: Boolean = true,
) {
    val endedAt: LocalDateTime get() = completedAt
    val safeExit: Boolean get() = outcome == ScoreSessionOutcome.WalkedAway
    val abandoned: Boolean get() = outcome == ScoreSessionOutcome.Abandoned

    val urgeDrop: Int?
        get() = if (urgeBefore != null && urgeAfter != null) urgeBefore - urgeAfter else null

    val controlPoints: Int
        get() {
            val basePoints = (score.coerceAtLeast(0) / 10)
            val outcomeBonus = when (outcome) {
                ScoreSessionOutcome.WalkedAway -> SAFE_EXIT_CONTROL_POINT_BONUS
                ScoreSessionOutcome.ContinuedWithIntention -> 35
                ScoreSessionOutcome.Completed -> 25
                ScoreSessionOutcome.Replayed -> 10
                ScoreSessionOutcome.Abandoned -> 0
            }
            val urgeBonus = (urgeDrop ?: 0).coerceAtLeast(0) * 30
            return if (validCompletion) basePoints + outcomeBonus + urgeBonus else basePoints / 2
        }
}

data class ScorePersonalBest(
    val gameType: ScoreGameType,
    val bestScore: Int,
    val previousScore: Int?,
    val changeFromPrevious: Int?,
    val sessionCount: Int,
) {
    val hasRecord: Boolean
        get() = sessionCount > 0
}

data class ScoreTimelineItem(
    val id: Long,
    val gameName: String,
    val score: Int,
    val urgeBefore: Int?,
    val urgeAfter: Int?,
    val outcome: ScoreSessionOutcome,
    val completedAt: LocalDateTime,
)

data class ScoreDashboardState(
    val selectedRange: ScoreRange = ScoreRange.Week,
    val controlScore: Int = 0,
    val currentLevel: Int = 1,
    val currentLevelPoints: Int = 0,
    val pointsNeededForNextLevel: Int = 100,
    val gamesCompleted: Int = 0,
    val bestGameName: String = "None yet",
    val totalControlPoints: Int = 0,
    val safeExitCount: Int = 0,
    val bestSafeExitStreak: Int = 0,
    val averageUrgeDrop: Float? = null,
    val bestUrgeDrop: Int? = null,
    val urgeTrend: UrgeTrendState = UrgeTrendState(baselinePerDay = 0, bars = emptyList(), totalActual = 0, totalExpected = 0, hasData = false),
    val windowUsage: WindowUsageState = WindowUsageState(plannedPerDay = 0, todayUsed = 0, todaySkipped = 0, rangeUsed = 0, rangeSkipped = 0, hasData = false),
    val personalBests: List<ScorePersonalBest> = DefaultScoreGameOrder.map {
        ScorePersonalBest(
            gameType = it,
            bestScore = 0,
            previousScore = null,
            changeFromPrevious = null,
            sessionCount = 0,
        )
    },
    val recentSessions: List<ScoreTimelineItem> = emptyList(),
    val recentSafeExits:
        List<SafeExitTimelineItem> =
        emptyList(),
) {
    val levelProgress: Float
        get() = if (pointsNeededForNextLevel <= 0) {
            0f
        } else {
            currentLevelPoints / pointsNeededForNextLevel.toFloat()
        }.coerceIn(0f, 1f)

    val pointsUntilNextLevel: Int
        get() = (pointsNeededForNextLevel - currentLevelPoints).coerceAtLeast(0)
}

fun buildScoreDashboardState(
    sessions: List<ScoreSessionRecord>,
    selectedRange: ScoreRange,
    currentLevel: Int,
    currentLevelPoints: Int,
    pointsNeededForNextLevel: Int,
    now: LocalDateTime = LocalDateTime.now(),
    recoveryGameTypes: List<ScoreGameType> = DefaultScoreGameOrder,
    baselineDailyUrgeCount: Int = 0,
    urgeEvents: List<UrgeEventRecord> = emptyList(),
    windowOutcomes: List<WindowOutcomeRecord> = emptyList(),
    taperHistory: List<TaperHistoryEntry> = emptyList(),
    safeExitProgress:
        SafeExitProgressSnapshot =
        SafeExitProgressSnapshot(),
): ScoreDashboardState {
    val visibleGameTypes = recoveryGameTypes
        .distinct()
        .filterNot { it == ScoreGameType.Unknown }
        .ifEmpty { DefaultScoreGameOrder }
    // Focus sessions are a scored activity: they appear in the control-points total
    // and the recent timeline, but never in games-completed, personal bests, or the
    // best-game pick, which stay games-only.
    val activityTypes = visibleGameTypes + ScoreGameType.FocusSession
    val gameSessions = sessions.filter { it.gameType in visibleGameTypes }
    val activitySessions = sessions.filter { it.gameType in activityTypes }
    val filtered = gameSessions.filter { it.completedAt.isInRange(selectedRange, now) }
    val filteredActivities = activitySessions.filter { it.completedAt.isInRange(selectedRange, now) }
    val validSessions = filtered.filter { it.validCompletion }
    val personalBests = buildPersonalBests(gameSessions, visibleGameTypes)
    val validPivotWalkAwaySourceKeys =
        filtered
            .asSequence()
            .filter {
                it.validCompletion &&
                    it.outcome ==
                    ScoreSessionOutcome.WalkedAway
            }
            .mapNotNull(
                PivotGameSafeExitIdentity::
                    sourceKey,
            )
            .toSet()

    val legacyPivotFallbackCount =
        validPivotWalkAwaySourceKeys
            .count {
                it !in
                    safeExitProgress
                        .persistedPivotSourceKeys
            }

    val safeExitCount =
        (
            safeExitProgress
                .ledgerSafeExitCount
                .toLong() +
                legacyPivotFallbackCount
                    .toLong()
        )
            .coerceAtMost(
                Int.MAX_VALUE
                    .toLong(),
            )
            .toInt()
    val totalControlPoints =
        (
            filteredActivities
                .sumOf {
                    it.controlPoints
                } +
                safeExitProgress
                    .additionalControlPoints
            )
            .coerceAtLeast(
                0,
            )
    val bestGame = validSessions.maxByOrNull { it.score }?.gameType?.displayName ?: "None yet"
    val urgeDrops = filtered.mapNotNull { it.urgeDrop }
    val averageUrgeDrop = urgeDrops.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val bestUrgeDrop = urgeDrops.maxOrNull()
    val urgeTrend = buildUrgeTrend(
        urgeEvents = urgeEvents,
        selectedRange = selectedRange,
        baselineDailyUrgeCount = baselineDailyUrgeCount,
        taperHistory = taperHistory,
        now = now.toLocalDate(),
    )
    val windowUsage = buildWindowUsage(
        windowOutcomes = windowOutcomes,
        selectedRange = selectedRange,
        plannedPerDay = baselineDailyUrgeCount,
        now = now.toLocalDate(),
    )

    return ScoreDashboardState(
        selectedRange = selectedRange,
        controlScore = (totalControlPoints + currentLevelPoints).coerceAtLeast(0),
        currentLevel = currentLevel.coerceAtLeast(1),
        currentLevelPoints = currentLevelPoints.coerceAtLeast(0),
        pointsNeededForNextLevel = pointsNeededForNextLevel.coerceAtLeast(1),
        gamesCompleted = validSessions.size,
        bestGameName = bestGame,
        totalControlPoints = totalControlPoints,
        safeExitCount = safeExitCount,
        bestSafeExitStreak = bestSafeExitStreak(filtered),
        averageUrgeDrop = averageUrgeDrop,
        bestUrgeDrop = bestUrgeDrop,
        urgeTrend = urgeTrend,
        windowUsage = windowUsage,
        personalBests = personalBests,
        recentSessions = filteredActivities
            .sortedByDescending { it.completedAt }
            .take(RecentSessionLimit)
            .map {
                ScoreTimelineItem(
                    id = it.id,
                    gameName = it.gameType.displayName,
                    score = it.score,
                    urgeBefore = it.urgeBefore,
                    urgeAfter = it.urgeAfter,
                    outcome = it.outcome,
                    completedAt = it.completedAt,
                )
            },
        recentSafeExits =
            safeExitProgress
                .recentSafeExits,
    )
}

private fun buildWindowUsage(
    windowOutcomes: List<WindowOutcomeRecord>,
    selectedRange: ScoreRange,
    plannedPerDay: Int,
    now: LocalDate,
): WindowUsageState {
    val days: List<LocalDate> = when (selectedRange) {
        ScoreRange.Today -> listOf(now)
        ScoreRange.Week -> (0L..6L).map { now.minusDays(it) }.reversed()
        ScoreRange.Month -> {
            val firstDay = now.withDayOfMonth(1)
            val span = ChronoUnit.DAYS.between(firstDay, now).coerceAtLeast(0)
            (0L..span).map { firstDay.plusDays(it) }
        }
        ScoreRange.Year -> {
            val firstDay = now.withDayOfYear(1)
            val span = ChronoUnit.DAYS.between(firstDay, now).coerceAtLeast(0)
            (0L..span).map { firstDay.plusDays(it) }
        }
    }
    val inRange = windowOutcomes.filter { it.windowStart.toLocalDate() in days }
    val todayOutcomes = windowOutcomes.filter { it.windowStart.toLocalDate() == now }
    return WindowUsageState(
        plannedPerDay = plannedPerDay.coerceAtLeast(0),
        todayUsed = todayOutcomes.count { it.status == WindowOutcomeStatus.Used },
        todaySkipped = todayOutcomes.count { it.status == WindowOutcomeStatus.Skipped },
        rangeUsed = inRange.count { it.status == WindowOutcomeStatus.Used },
        rangeSkipped = inRange.count { it.status == WindowOutcomeStatus.Skipped },
        hasData = inRange.isNotEmpty(),
    )
}

private fun buildUrgeTrend(
    urgeEvents: List<UrgeEventRecord>,
    selectedRange: ScoreRange,
    baselineDailyUrgeCount: Int,
    taperHistory: List<TaperHistoryEntry>,
    now: LocalDate,
): UrgeTrendState {
    val currentBaseline = baselineDailyUrgeCount.coerceAtLeast(0)
    val days: List<LocalDate> = when (selectedRange) {
        ScoreRange.Today -> listOf(now)
        ScoreRange.Week -> (0L..6L).map { now.minusDays(it) }.reversed()
        ScoreRange.Month -> {
            val firstDay = now.withDayOfMonth(1)
            val span = ChronoUnit.DAYS.between(firstDay, now).coerceAtLeast(0)
            (0L..span).map { firstDay.plusDays(it) }
        }
        ScoreRange.Year -> {
            val firstDay = now.withDayOfYear(1)
            val span = ChronoUnit.DAYS.between(firstDay, now).coerceAtLeast(0)
            (0L..span).map { firstDay.plusDays(it) }
        }
    }
    val countsByDay = urgeEvents.groupBy { it.date }.mapValues { it.value.size }
    val bars = days.map { day ->
        val actual = countsByDay[day] ?: 0
        UrgeTrendDayBar(
            date = day,
            actual = actual,
            baseline = baselineForDay(day, currentBaseline, taperHistory),
        )
    }
    val totalActual = bars.sumOf { it.actual }
    val totalExpected = bars.sumOf { it.baseline }
    val hasData = urgeEvents.any { it.date in days }
    return UrgeTrendState(
        baselinePerDay = currentBaseline,
        bars = bars,
        totalActual = totalActual,
        totalExpected = totalExpected,
        hasData = hasData,
    )
}

/**
 * Resolves the daily urge count that was in effect on a given day. Days on or
 * after a taper acceptance use that taper's new count. Days before the first
 * taper use the first taper's previous count. With no taper history every day
 * uses the current baseline, which preserves the original behaviour exactly.
 */
private fun baselineForDay(
    day: LocalDate,
    currentBaseline: Int,
    taperHistory: List<TaperHistoryEntry>,
): Int {
    val appliedOnOrBefore = taperHistory
        .filter { !it.date.isAfter(day) }
        .maxByOrNull { it.date }
    if (appliedOnOrBefore != null) return appliedOnOrBefore.toCount.coerceAtLeast(0)
    val earliestAfter = taperHistory
        .filter { it.date.isAfter(day) }
        .minByOrNull { it.date }
    return (earliestAfter?.fromCount ?: currentBaseline).coerceAtLeast(0)
}

private fun buildPersonalBests(
    sessions: List<ScoreSessionRecord>,
    visibleGameTypes: List<ScoreGameType>,
): List<ScorePersonalBest> {
    val playedTypes = sessions.map { it.gameType }.distinct()
    val orderedTypes = (visibleGameTypes + playedTypes)
        .distinct()
        .filterNot { it == ScoreGameType.Unknown }
    return orderedTypes.map { gameType ->
        val gameSessions = sessions
            .filter { it.gameType == gameType && it.validCompletion }
            .sortedByDescending { it.completedAt }
        val bestScore = gameSessions.maxOfOrNull { it.score } ?: 0
        val previousScore = gameSessions.drop(1).firstOrNull()?.score
        val changeFromPrevious = previousScore?.let { bestScore - it }
        ScorePersonalBest(
            gameType = gameType,
            bestScore = bestScore,
            previousScore = previousScore,
            changeFromPrevious = changeFromPrevious,
            sessionCount = gameSessions.size,
        )
    }
}

private fun bestSafeExitStreak(sessions: List<ScoreSessionRecord>): Int {
    var best = 0
    var current = 0
    sessions.sortedBy { it.completedAt }.forEach { session ->
        if (session.validCompletion && session.outcome == ScoreSessionOutcome.WalkedAway) {
            current += 1
            best = maxOf(best, current)
        } else if (session.validCompletion) {
            current = 0
        }
    }
    return best
}

private fun LocalDateTime.isInRange(range: ScoreRange, now: LocalDateTime): Boolean = when (range) {
    ScoreRange.Today -> toLocalDate() == now.toLocalDate()
    ScoreRange.Week -> {
        val days = Duration.between(this, now).toDays()
        !isAfter(now) && days in 0..6
    }
    ScoreRange.Month -> {
        val completedDate = toLocalDate()
        val nowDate = now.toLocalDate()
        completedDate.year == nowDate.year && completedDate.monthValue == nowDate.monthValue
    }
    ScoreRange.Year -> {
        val completedDate = toLocalDate()
        val nowDate = now.toLocalDate()
        completedDate.year == nowDate.year
    }
}
