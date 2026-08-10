package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreDashboardSafeExitAggregationTest {
    @Test
    fun pivotWalkAwaySessionContributesOneSafeExitAndOneBonus() {
        val state =
            buildScoreDashboardState(
                sessions = listOf(pivotWalkAway()),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
            )

        assertEquals(
            1,
            state.safeExitCount,
        )
        assertEquals(
            80,
            state.totalControlPoints,
        )
        assertEquals(
            1,
            state.gamesCompleted,
        )
    }

    @Test
    fun resetReadingSnapshotAddsOneSafeExitAndOneBonusWithoutTripleCounting() {
        val state =
            buildScoreDashboardState(
                sessions = listOf(pivotWalkAway()),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
                safeExitProgress = SafeExitProgressSnapshot(
                    ledgerSafeExitCount = 1,
                    additionalControlPoints = 80,
                ),
            )

        assertEquals(
            2,
            state.safeExitCount,
        )
        assertEquals(
            160,
            state.totalControlPoints,
        )
        assertTrue(
            state.totalControlPoints != 240,
        )
    }

    @Test
    fun momentPlanSnapshotAddsTheSameAdditionalBonus() {
        val state =
            buildScoreDashboardState(
                sessions = emptyList(),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
                safeExitProgress = SafeExitProgressSnapshot(
                    ledgerSafeExitCount = 1,
                    additionalControlPoints = 80,
                ),
            )

        assertEquals(
            1,
            state.safeExitCount,
        )
        assertEquals(
            80,
            state.totalControlPoints,
        )
        assertEquals(
            0,
            state.gamesCompleted,
        )
    }

    @Test
    fun recentSafeExitsArePreservedInDashboardState() {
        val item =
            SafeExitTimelineItem(
                sourceKey = "moment_plan:decision-1",
                source = SafeExitSource.MomentPlan,
                completedAt = Now.minusMinutes(5),
                additionalControlPoints = 80,
            )

        val state =
            buildScoreDashboardState(
                sessions = listOf(pivotWalkAway()),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
                safeExitProgress = SafeExitProgressSnapshot(
                    ledgerSafeExitCount = 1,
                    additionalControlPoints = 80,
                    recentSafeExits = listOf(item),
                ),
            )

        assertEquals(
            listOf(item),
            state.recentSafeExits,
        )
        assertEquals(
            1,
            state.gamesCompleted,
        )
    }


    @Test
    fun persistedPivotLedgerRowPreventsLegacyFallbackDoubleCount() {
        val pivot =
            pivotWalkAway()

        val state =
            buildScoreDashboardState(
                sessions = listOf(pivot),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
                safeExitProgress = SafeExitProgressSnapshot(
                    ledgerSafeExitCount = 1,
                    persistedPivotSourceKeys =
                        setOf(
                            requireNotNull(
                                PivotGameSafeExitIdentity
                                    .sourceKey(
                                        pivot,
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals(
            1,
            state.safeExitCount,
        )
        assertEquals(
            80,
            state.totalControlPoints,
        )
    }

    @Test
    fun historicalPivotLedgerRowCountsAfterScoreSessionWasPruned() {
        val state =
            buildScoreDashboardState(
                sessions = emptyList(),
                selectedRange = ScoreRange.Year,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
                safeExitProgress =
                    SafeExitProgressSnapshot(
                        ledgerSafeExitCount = 1,
                        additionalControlPoints = 0,
                    ),
            )

        assertEquals(
            1,
            state.safeExitCount,
        )
        assertEquals(
            0,
            state.totalControlPoints,
        )
        assertEquals(
            0,
            state.gamesCompleted,
        )
    }

    @Test
    fun invalidPivotWalkAwayDoesNotContributeLegacyFallbackCount() {
        val state =
            buildScoreDashboardState(
                sessions =
                    listOf(
                        pivotWalkAway(
                            validCompletion = false,
                        ),
                    ),
                selectedRange = ScoreRange.Week,
                currentLevel = 1,
                currentLevelPoints = 0,
                pointsNeededForNextLevel = 100,
                now = Now,
            )

        assertEquals(
            0,
            state.safeExitCount,
        )
        assertEquals(
            0,
            state.gamesCompleted,
        )
    }
    private fun pivotWalkAway(
        validCompletion: Boolean = true,
    ): ScoreSessionRecord {
        return ScoreSessionRecord(
            id = 1L,
            gameType = ScoreGameType.Snake,
            score = 0,
            startedAt = Now.minusMinutes(2),
            completedAt = Now.minusMinutes(1),
            durationSec = 60,
            outcome = ScoreSessionOutcome.WalkedAway,
            validCompletion = validCompletion,
        )
    }

    private companion object {
        val Now: LocalDateTime =
            LocalDateTime.of(2026, 8, 3, 14, 0)
    }
}