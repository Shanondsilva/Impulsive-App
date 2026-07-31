package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveInsightBuilderTest {
    @Test
    fun fewerThanFiveCompletedInterventionsShowsLearningState() {
        val result = AdaptiveInsightBuilder.build(
            (1..4).map { id -> outcome(id, InterventionFamily.PivotGame) },
        )

        assertEquals("Impulsive is learning what helps you.", result.headline)
        assertEquals(4, result.completedInterventionCount)
        assertNull(result.comparativeInsight)
    }

    @Test
    fun fiveCompletedInterventionsShowsFactualCounts() {
        val history = listOf(
            outcome(1, InterventionFamily.PivotGame, FeedbackCode.Helped),
            outcome(2, InterventionFamily.PivotGame, FeedbackCode.HelpedALittle),
            outcome(3, InterventionFamily.PivotReading, FeedbackCode.DidNotHelp),
            outcome(4, InterventionFamily.MomentPlan, FeedbackCode.WrongTiming),
            outcome(5, InterventionFamily.MomentPlan, FeedbackCode.NotProvided),
        )

        val result = AdaptiveInsightBuilder.build(history)
        val planSummary = result.summaries.single {
            it.intervention == InterventionFamily.MomentPlan
        }

        assertEquals("Your recent support moments", result.headline)
        assertEquals(5, result.completedInterventionCount)
        assertEquals(2, planSummary.actualUses)
        assertEquals(1, planSummary.wrongTiming)
    }

    @Test
    fun dashboardNeverEmitsForbiddenCertaintyWords() {
        val result = AdaptiveInsightBuilder.build(comparisonHistory())
        val allCopy = buildString {
            append(result.headline)
            append(' ')
            append(result.comparativeInsight?.copy.orEmpty())
        }.lowercase()

        listOf("best for you", "proven", "guaranteed", "established").forEach { forbidden ->
            assertFalse("Found forbidden copy: $forbidden", forbidden in allCopy)
        }
        assertTrue("early pattern" in allCopy)
        assertTrue("recent records" in allCopy)
        assertTrue("not a conclusion" in allCopy)
    }

    @Test
    fun onlyOneLargestSampleComparisonIsProduced() {
        val result = AdaptiveInsightBuilder.build(comparisonHistory())
        val insight = result.comparativeInsight

        assertNotNull(insight)
        assertEquals(InterventionFamily.PivotGame, insight?.firstIntervention)
        assertEquals(InterventionFamily.PivotReading, insight?.secondIntervention)
    }

    @Test
    fun comparisonRequiresEightUsesFourResponsesAndTwentyFivePointDifference() {
        assertEquals(8, AdaptiveInsightBuilder.MinimumComparisonActualUses)
        assertEquals(4, AdaptiveInsightBuilder.MinimumComparisonFeedbackResponses)
        assertEquals(0.25, AdaptiveInsightBuilder.MinimumHelpfulRateDifference, 0.0)
        val tooFewUses =
            (1..7).map { outcome(it, InterventionFamily.PivotGame, FeedbackCode.Helped) } +
                (8..15).map {
                    outcome(it, InterventionFamily.PivotReading, FeedbackCode.DidNotHelp)
                }
        val belowDifference =
            (1..8).map { id ->
                outcome(
                    id,
                    InterventionFamily.PivotGame,
                    if (id <= 4) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
                )
            } +
                (9..16).map { id ->
                    outcome(
                        id,
                        InterventionFamily.PivotReading,
                        if (id <= 13) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
                    )
                }

        assertNull(AdaptiveInsightBuilder.build(tooFewUses).comparativeInsight)
        assertNull(AdaptiveInsightBuilder.build(belowDifference).comparativeInsight)
    }

    @Test
    fun onlyFinalisedObservationsEnterComparisons() {
        val finalisedGame = (1..8).map {
            outcome(it, InterventionFamily.PivotGame, FeedbackCode.Helped)
        }
        val pendingReading = (9..16).map {
            outcome(
                it,
                InterventionFamily.PivotReading,
                FeedbackCode.DidNotHelp,
                finalised = false,
            )
        }

        val result = AdaptiveInsightBuilder.build(finalisedGame + pendingReading)

        assertNull(result.comparativeInsight)
        assertEquals(8, result.finalisedRecordCount)
    }

    @Test
    fun wrongTimingDoesNotCountAsSubstantiveComparisonFeedback() {
        val game = (1..8).map {
            outcome(it, InterventionFamily.PivotGame, FeedbackCode.Helped)
        }
        val reading = (9..16).map {
            outcome(it, InterventionFamily.PivotReading, FeedbackCode.WrongTiming)
        }

        assertNull(AdaptiveInsightBuilder.build(game + reading).comparativeInsight)
    }

    private fun comparisonHistory(): List<AdaptiveOutcomeRecord> {
        val game = (1..12).map { id ->
            outcome(
                id = id,
                family = InterventionFamily.PivotGame,
                feedback = if (id <= 9) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
            )
        }
        val reading = (13..24).map { id ->
            outcome(
                id = id,
                family = InterventionFamily.PivotReading,
                feedback = if (id <= 15) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
            )
        }
        val plan = (25..32).map { id ->
            outcome(
                id = id,
                family = InterventionFamily.MomentPlan,
                feedback = FeedbackCode.Helped,
            )
        }
        return game + reading + plan
    }

    private fun outcome(
        id: Int,
        family: InterventionFamily,
        feedback: FeedbackCode = FeedbackCode.NotProvided,
        finalised: Boolean = true,
    ): AdaptiveOutcomeRecord = AdaptiveOutcomeRecord(
        decisionId = "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}",
        actualIntervention = family,
        selectedCue = if (family == InterventionFamily.MomentPlan) {
            MomentCue.Boredom
        } else {
            null
        },
        feedbackCode = feedback,
        engagementOutcome = EngagementOutcome.Completed,
        repeatObservation = RepeatObservation.NoRepeatDetected,
        decisionAtMillis = id.toLong(),
        observationFinalisedAtMillis =
            (id.toLong() + 1_000L).takeIf { finalised },
    )
}
