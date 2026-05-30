package com.impulsive.app.backend.domain.model.score

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private const val RecentSessionLimit = 10

private val EmptyGameOrder = listOf(
    ScoreGameType.ReflexOverride,
    ScoreGameType.PatternBreak,
    ScoreGameType.UrgeSurvival,
    ScoreGameType.FluidRegulation,
)

enum class ScoreRange(val label: String) {
    Today("Today"),
    Week("Week"),
    AllTime("All-time"),
}

enum class ScoreGameType(
    val id: String,
    val displayName: String,
) {
    ReflexOverride("REFLEX_OVERRIDE", "Reflex Override"),
    PatternBreak("PATTERN_BREAK", "Pattern Break"),
    BlockCascade("BLOCK_CASCADE", "Block Cascade"),
    UrgeSurvival("URGE_SURVIVAL", "Urge Survival"),
    FluidRegulation("FLUID_REGULATION", "Fluid Regulation"),
    PrecisionFocus("PRECISION_FOCUS", "Precision Focus"),
    DopamineRunner("DOPAMINE_RUNNER", "Dopamine Runner"),
    BreathControl("BREATH_CONTROL", "Breath Control"),
    RageDischarge("RAGE_DISCHARGE", "Rage Discharge"),
    Unknown("UNKNOWN", "Recovery Game");

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

data class ScoreSessionRecord(
    val id: Long = System.currentTimeMillis(),
    val gameType: ScoreGameType,
    val score: Int,
    val completedAt: LocalDateTime,
    val durationSec: Int,
    val urgeBefore: Int? = null,
    val urgeAfter: Int? = null,
    val outcome: ScoreSessionOutcome,
    val validCompletion: Boolean = true,
) {
    val urgeDrop: Int?
        get() = if (urgeBefore != null && urgeAfter != null) urgeBefore - urgeAfter else null

    val controlPoints: Int
        get() {
            val basePoints = (score.coerceAtLeast(0) / 10)
            val outcomeBonus = when (outcome) {
                ScoreSessionOutcome.WalkedAway -> 80
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
    val personalBests: List<ScorePersonalBest> = EmptyGameOrder.map {
        ScorePersonalBest(
            gameType = it,
            bestScore = 0,
            previousScore = null,
            changeFromPrevious = null,
            sessionCount = 0,
        )
    },
    val recentSessions: List<ScoreTimelineItem> = emptyList(),
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
): ScoreDashboardState {
    val filtered = sessions.filter { it.completedAt.isInRange(selectedRange, now) }
    val validSessions = filtered.filter { it.validCompletion }
    val personalBests = buildPersonalBests(sessions)
    val safeExitCount = validSessions.count { it.outcome == ScoreSessionOutcome.WalkedAway }
    val totalControlPoints = validSessions.sumOf { it.controlPoints }
    val bestGame = validSessions
        .groupBy { it.gameType }
        .maxByOrNull { (_, records) -> records.maxOfOrNull { it.score } ?: 0 }
        ?.key
        ?.displayName
        ?: "None yet"
    val urgeDrops = validSessions.mapNotNull { it.urgeDrop }
    val averageUrgeDrop = urgeDrops.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val bestUrgeDrop = urgeDrops.maxOrNull()

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
        personalBests = personalBests,
        recentSessions = filtered
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
    )
}

private fun buildPersonalBests(sessions: List<ScoreSessionRecord>): List<ScorePersonalBest> {
    val playedTypes = sessions.map { it.gameType }.distinct()
    val orderedTypes = (EmptyGameOrder + playedTypes)
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
    ScoreRange.AllTime -> true
}
