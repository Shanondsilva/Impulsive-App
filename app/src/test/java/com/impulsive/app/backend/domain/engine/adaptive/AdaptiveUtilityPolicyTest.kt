package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveUtilityPolicyTest {
    @Test
    fun feedbackPointsMatchOperationalPolicy() {
        assertEquals(2.0, AdaptiveUtilityPolicy.feedbackPoints(FeedbackCode.Helped), 0.0)
        assertEquals(1.0, AdaptiveUtilityPolicy.feedbackPoints(FeedbackCode.HelpedALittle), 0.0)
        assertEquals(-2.0, AdaptiveUtilityPolicy.feedbackPoints(FeedbackCode.DidNotHelp), 0.0)
        assertEquals(0.0, AdaptiveUtilityPolicy.feedbackPoints(FeedbackCode.WrongTiming), 0.0)
        assertEquals(0.0, AdaptiveUtilityPolicy.feedbackPoints(FeedbackCode.NotProvided), 0.0)
    }

    @Test
    fun wrongTimingChangesReceptivityWithoutChangingEffectivenessPoints() {
        val wrongTiming = outcome(
            id = 1,
            feedback = FeedbackCode.WrongTiming,
            engagement = EngagementOutcome.NotStarted,
        )
        val noFeedback = outcome(
            id = 2,
            feedback = FeedbackCode.NotProvided,
            engagement = EngagementOutcome.NotStarted,
        )

        assertEquals(0.0, AdaptiveUtilityPolicy.feedbackPoints(wrongTiming.feedbackCode), 0.0)
        assertEquals(0.0, AdaptiveUtilityPolicy.feedbackPoints(noFeedback.feedbackCode), 0.0)
        val wrongTimingScore = score(listOf(wrongTiming))
        val noFeedbackScore = score(listOf(noFeedback))
        assertTrue(wrongTimingScore.receptivityPenalty > noFeedbackScore.receptivityPenalty)
        assertEquals(
            wrongTimingScore.shrunkEvidenceScore,
            noFeedbackScore.shrunkEvidenceScore,
            0.0,
        )
    }

    @Test
    fun completionAndDismissalHaveSeparateEngagementPoints() {
        assertEquals(
            0.5,
            AdaptiveUtilityPolicy.engagementPoints(EngagementOutcome.Completed),
            0.0,
        )
        assertEquals(
            -0.5,
            AdaptiveUtilityPolicy.engagementPoints(EngagementOutcome.Dismissed),
            0.0,
        )
        assertEquals(
            -0.25,
            AdaptiveUtilityPolicy.engagementPoints(EngagementOutcome.StartedNotCompleted),
            0.0,
        )
        assertEquals(
            0.0,
            AdaptiveUtilityPolicy.engagementPoints(EngagementOutcome.NotStarted),
            0.0,
        )
    }

    @Test
    fun repeatSignalUsesOnlyDefinedWeakWeight() {
        assertEquals(
            0.25,
            AdaptiveUtilityPolicy.repeatObservationPoints(RepeatObservation.NoRepeatDetected),
            0.0,
        )
        assertEquals(
            -0.25,
            AdaptiveUtilityPolicy.repeatObservationPoints(RepeatObservation.RepeatDetected),
            0.0,
        )
        assertEquals(
            0.0,
            AdaptiveUtilityPolicy.repeatObservationPoints(RepeatObservation.NotFinalised),
            0.0,
        )
    }

    @Test
    fun shrinkagePreventsExtremeOneEventResult() {
        val record = outcome(
            id = 1,
            feedback = FeedbackCode.Helped,
            engagement = EngagementOutcome.Completed,
            repeat = RepeatObservation.NoRepeatDetected,
        )

        val result = score(listOf(record))

        assertEquals(2.75, result.evidencePoints, 0.0)
        assertEquals(2.75 / 5.0, result.shrunkEvidenceScore, 0.0)
        assertTrue(result.shrunkEvidenceScore < result.evidencePoints)
    }

    @Test
    fun twoMostRecentSelectionsApplyFullFatiguePenalty() {
        assertEquals(
            1.0,
            AdaptiveFatiguePolicy.penalty(
                InterventionFamily.PivotGame,
                listOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotReading,
                ),
            ),
            0.0,
        )
    }

    @Test
    fun twoOfLatestThreeApplyPartialFatiguePenalty() {
        assertEquals(
            0.5,
            AdaptiveFatiguePolicy.penalty(
                InterventionFamily.PivotGame,
                listOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotReading,
                    InterventionFamily.PivotGame,
                ),
            ),
            0.0,
        )
    }

    @Test
    fun cueMatchedSubsetRequiresFourFinalisedActualUses() {
        val threeCueMatched = (1..3).map { id ->
            outcome(id = id, cue = MomentCue.Stress)
        }
        val fourthCueMatched = outcome(id = 4, cue = MomentCue.Stress)
        val broad = outcome(
            id = 5,
            cue = MomentCue.Boredom,
            feedback = FeedbackCode.DidNotHelp,
        )

        val belowThreshold = score(
            history = threeCueMatched + broad,
            selectedCue = MomentCue.Stress,
        )
        val atThreshold = score(
            history = threeCueMatched + fourthCueMatched + broad,
            selectedCue = MomentCue.Stress,
        )

        assertFalse(belowThreshold.usedCueMatchedHistory)
        assertTrue(atThreshold.usedCueMatchedHistory)
        assertEquals(4, atThreshold.finalisedDecisionCount)
    }

    @Test
    fun evidenceHistoryIsLimitedToMostRecentThirtyRecords() {
        val history = (1..35).map { id ->
            outcome(
                id = id,
                feedback = if (id <= 5) FeedbackCode.DidNotHelp else FeedbackCode.Helped,
            )
        }

        val result = score(history)

        assertEquals(30, result.finalisedDecisionCount)
        assertEquals(67.5, result.evidencePoints, 0.0)
    }

    @Test
    fun unfinalisedRecordsDoNotEnterUtility() {
        val unfinalised = outcome(id = 1).copy(
            repeatObservation = RepeatObservation.NotFinalised,
            observationFinalisedAtMillis = null,
        )

        val result = score(listOf(unfinalised))

        assertEquals(0, result.finalisedDecisionCount)
        assertEquals(0.0, result.evidencePoints, 0.0)
    }

    private fun score(
        history: List<AdaptiveOutcomeRecord>,
        selectedCue: MomentCue? = null,
    ): AdaptiveUtilityBreakdown = AdaptiveUtilityPolicy.score(
        intervention = InterventionFamily.PivotGame,
        selectedCue = selectedCue,
        history = history,
        recentActualSelections = emptyList(),
    )

    private fun outcome(
        id: Int,
        cue: MomentCue? = null,
        feedback: FeedbackCode = FeedbackCode.NotProvided,
        engagement: EngagementOutcome = EngagementOutcome.NotStarted,
        repeat: RepeatObservation = RepeatObservation.NoRepeatDetected,
    ): AdaptiveOutcomeRecord = AdaptiveOutcomeRecord(
        decisionId = id.asUuid(),
        actualIntervention = InterventionFamily.PivotGame,
        selectedCue = cue,
        feedbackCode = feedback,
        engagementOutcome = engagement,
        repeatObservation = repeat,
        decisionAtMillis = id.toLong(),
        observationFinalisedAtMillis = id.toLong() + 1_000L,
    )

    private fun Int.asUuid(): String =
        "00000000-0000-0000-0000-${toString().padStart(12, '0')}"
}
