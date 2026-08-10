package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SafeExitModelsTest {
    private val completedAt: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)

    @Test
    fun validPivotGameWalkAwayIsAccepted() {
        val result = evaluateAccepted(
            candidate(
                source = SafeExitSource.PivotGame,
                sourceId = "REFLEX_OVERRIDE:1720000000000",
            ),
        )

        assertEquals("pivot_game:REFLEX_OVERRIDE:1720000000000", result.sourceKey)
        assertEquals(SafeExitSource.PivotGame, result.source)
        assertEquals("REFLEX_OVERRIDE:1720000000000", result.sourceId)
        assertEquals(completedAt, result.completedAt)
    }

    @Test
    fun validResetReadingWalkAwayIsAccepted() {
        val result = evaluateAccepted(
            candidate(
                source = SafeExitSource.ResetReading,
                sourceId = "1720000000001",
            ),
        )

        assertEquals("reset_reading:1720000000001", result.sourceKey)
    }

    @Test
    fun validMomentPlanWalkAwayIsAccepted() {
        val result = evaluateAccepted(
            candidate(
                source = SafeExitSource.MomentPlan,
                sourceId = "decision-uuid",
            ),
        )

        assertEquals("moment_plan:decision-uuid", result.sourceKey)
    }

    @Test
    fun sourceIdWhitespaceIsTrimmedBeforeKeyIsCreated() {
        val result = evaluateAccepted(
            candidate(
                source = SafeExitSource.PivotGame,
                sourceId = "  REFLEX_OVERRIDE:1720000000000  ",
            ),
        )

        assertEquals("pivot_game:REFLEX_OVERRIDE:1720000000000", result.sourceKey)
        assertEquals("REFLEX_OVERRIDE:1720000000000", result.sourceId)
    }

    @Test
    fun emptySourceIdIsRejectedWithBlankSourceId() {
        assertRejected(
            SafeExitRejectionReason.BlankSourceId,
            candidate(sourceId = ""),
        )
    }

    @Test
    fun whitespaceOnlySourceIdIsRejectedWithBlankSourceId() {
        assertRejected(
            SafeExitRejectionReason.BlankSourceId,
            candidate(sourceId = "   "),
        )
    }

    @Test
    fun invalidCompletionIsRejectedWithInvalidCompletion() {
        assertRejected(
            SafeExitRejectionReason.InvalidCompletion,
            candidate(validCompletion = false),
        )
    }

    @Test
    fun doneIsRejectedWithNonWalkAwayAction() {
        assertRejected(
            SafeExitRejectionReason.NonWalkAwayAction,
            candidate(action = SafeExitAction.Done),
        )
    }

    @Test
    fun notNowIsRejectedWithNonWalkAwayAction() {
        assertRejected(
            SafeExitRejectionReason.NonWalkAwayAction,
            candidate(action = SafeExitAction.NotNow),
        )
    }

    @Test
    fun leaveThisAppIsRejectedWithNonWalkAwayAction() {
        assertRejected(
            SafeExitRejectionReason.NonWalkAwayAction,
            candidate(action = SafeExitAction.LeaveThisApp),
        )
    }

    @Test
    fun identicalCandidatesProduceIdenticalSourceKeys() {
        val first = evaluateAccepted(candidate()).sourceKey
        val second = evaluateAccepted(candidate()).sourceKey

        assertEquals(first, second)
    }

    @Test
    fun sameSourceIdForDifferentSourcesProducesDifferentKeys() {
        val pivot = evaluateAccepted(
            candidate(source = SafeExitSource.PivotGame, sourceId = "shared-id"),
        ).sourceKey
        val reading = evaluateAccepted(
            candidate(source = SafeExitSource.ResetReading, sourceId = "shared-id"),
        ).sourceKey
        val momentPlan = evaluateAccepted(
            candidate(source = SafeExitSource.MomentPlan, sourceId = "shared-id"),
        ).sourceKey

        assertNotEquals(pivot, reading)
        assertNotEquals(pivot, momentPlan)
        assertNotEquals(reading, momentPlan)
    }

    @Test
    fun acceptedRecordsExposeExactlyEightyControlPoints() {
        val result = evaluateAccepted(candidate())

        assertEquals(80, result.controlPoints)
        assertEquals(SAFE_EXIT_CONTROL_POINT_BONUS, result.controlPoints)
    }

    @Test
    fun zeroScoreValidWalkedAwayScoreSessionStillProducesEightyControlPoints() {
        val session = scoreSession(
            score = 0,
            outcome = ScoreSessionOutcome.WalkedAway,
        )

        assertEquals(80, session.controlPoints)
        assertEquals(SAFE_EXIT_CONTROL_POINT_BONUS, session.controlPoints)
    }

    @Test
    fun existingCompletedContinuedReplayedAndAbandonedBonusesRemainUnchanged() {
        assertEquals(25, scoreSession(outcome = ScoreSessionOutcome.Completed).controlPoints)
        assertEquals(35, scoreSession(outcome = ScoreSessionOutcome.ContinuedWithIntention).controlPoints)
        assertEquals(10, scoreSession(outcome = ScoreSessionOutcome.Replayed).controlPoints)
        assertEquals(0, scoreSession(outcome = ScoreSessionOutcome.Abandoned).controlPoints)
    }

    @Test
    fun scoreGameTypeHasNoResetReadingOrMomentPlanEntry() {
        val ids = ScoreGameType.entries.map { it.id }
        val names = ScoreGameType.entries.map { it.name }

        assertFalse("RESET_READING" in ids)
        assertFalse("MOMENT_PLAN" in ids)
        assertFalse("ResetReading" in names)
        assertFalse("MomentPlan" in names)
    }

    private fun candidate(
        source: SafeExitSource = SafeExitSource.PivotGame,
        sourceId: String = "REFLEX_OVERRIDE:1720000000000",
        action: SafeExitAction = SafeExitAction.WalkAway,
        validCompletion: Boolean = true,
    ): SafeExitCandidate = SafeExitCandidate(
        source = source,
        sourceId = sourceId,
        action = action,
        completedAt = completedAt,
        validCompletion = validCompletion,
    )

    private fun evaluateAccepted(candidate: SafeExitCandidate): SafeExitRecord {
        val result = SafeExitPolicy.evaluate(candidate)
        return (result as SafeExitEvaluation.Accepted).record
    }

    private fun assertRejected(
        reason: SafeExitRejectionReason,
        candidate: SafeExitCandidate,
    ) {
        val result = SafeExitPolicy.evaluate(candidate)
        assertEquals(SafeExitEvaluation.Rejected(reason), result)
    }

    private fun scoreSession(
        score: Int = 0,
        outcome: ScoreSessionOutcome,
    ): ScoreSessionRecord = ScoreSessionRecord(
        id = 1720000000000L,
        gameType = ScoreGameType.ReflexOverride,
        score = score,
        startedAt = completedAt.minusMinutes(1),
        completedAt = completedAt,
        durationSec = 60,
        urgeBefore = null,
        urgeAfter = null,
        outcome = outcome,
        validCompletion = true,
    )
}