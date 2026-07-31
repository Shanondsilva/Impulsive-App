package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatWorksForMeBuilderTest {
    @Test
    fun emptyStateHasZeroFactualCounts() {
        val report = build()

        assertTrue(report.empty)
        assertEquals(EvidenceQualityTier.CountOnly, report.evidenceQualityTier)
        assertEquals(0, report.summary.protectedMoments)
        assertTrue(report.interventions.isEmpty())
        assertNull(report.primaryComparison)
    }

    @Test
    fun immediateCountsUseActualChoiceNotAssignment() {
        val report = build(
            decisions = listOf(
                decision(
                    actual = InterventionFamily.PivotReading,
                    assigned = InterventionFamily.PivotGame,
                    completed = true,
                    overrode = true,
                ),
            ),
        )

        assertEquals(1, report.summary.protectedMoments)
        assertEquals(1, report.summary.supportOptionsStarted)
        assertEquals(1, report.summary.supportOptionsCompleted)
        assertEquals(1, report.differentChoiceCount)
        assertEquals(
            InterventionFamily.PivotReading,
            report.interventions.single().intervention,
        )
    }

    @Test
    fun completedDismissedAndWrongTimingRemainSeparate() {
        val report = build(
            decisions = listOf(
                decision(completed = true, feedback = FeedbackCode.WrongTiming),
                decision(completed = false, dismissed = true),
            ),
        )
        val summary = report.interventions.single()

        assertEquals(1, summary.completed)
        assertEquals(1, summary.dismissed)
        assertEquals(1, summary.wrongTiming)
        assertEquals(0, summary.didNotHelp)
    }

    @Test
    fun notProvidedIsNotTreatedAsNegative() {
        val summary = build(
            decisions = listOf(
                decision(completed = true, feedback = FeedbackCode.NotProvided),
            ),
        ).interventions.single()

        assertEquals(1, summary.notAnswered)
        assertEquals(0, summary.didNotHelp)
    }

    @Test
    fun withinOptionPatternRequiresThreeTerminalUses() {
        val two = build(
            decisions = List(2) { decision(completed = true) },
        )
        val three = build(
            decisions = List(3) { decision(completed = true) },
        )

        assertTrue(two.withinOptionPatterns.isEmpty())
        assertEquals(EvidenceQualityTier.CountOnly, two.evidenceQualityTier)
        assertEquals(1, three.withinOptionPatterns.size)
        assertEquals(EvidenceQualityTier.EarlyPattern, three.evidenceQualityTier)
        assertTrue(three.withinOptionPatterns.single().contains("3 of the 3"))
    }

    @Test
    fun evidenceTierMayDecreaseAfterRetentionRemovesOldSourceRecord() {
        val before = build(
            decisions = List(3) { decision(completed = true) },
        )
        val after = build(
            decisions = List(2) { decision(completed = true) },
        )

        assertEquals(EvidenceQualityTier.EarlyPattern, before.evidenceQualityTier)
        assertEquals(EvidenceQualityTier.CountOnly, after.evidenceQualityTier)
    }

    @Test
    fun pendingAndFinalisedRepeatObservationsRemainSeparate() {
        val report = build(
            decisions = listOf(
                decision(repeat = RepeatObservation.RepeatDetected, finalised = true),
                decision(repeat = RepeatObservation.NoRepeatDetected, finalised = true),
                decision(repeat = RepeatObservation.NotFinalised, finalised = false),
            ),
        ).interventions.single()

        assertEquals(1, report.laterRepeatDetected)
        assertEquals(1, report.noLaterRepeatObserved)
        assertEquals(1, report.awaitingObservation)
    }

    @Test
    fun atMostOnePrimaryComparisonIsReturned() {
        val decisions =
            List(8) {
                decision(
                    actual = InterventionFamily.PivotGame,
                    feedback = FeedbackCode.Helped,
                    completed = true,
                    repeat = RepeatObservation.NoRepeatDetected,
                    finalised = true,
                )
            } +
                List(8) {
                    decision(
                        actual = InterventionFamily.PivotReading,
                        feedback = FeedbackCode.DidNotHelp,
                        completed = true,
                        repeat = RepeatObservation.NoRepeatDetected,
                        finalised = true,
                    )
                } +
                List(8) {
                    decision(
                        actual = InterventionFamily.MomentPlan,
                        feedback = FeedbackCode.DidNotHelp,
                        completed = true,
                        repeat = RepeatObservation.NoRepeatDetected,
                        finalised = true,
                    )
                }

        val report = build(decisions = decisions)

        assertTrue(report.primaryComparison != null)
        assertEquals(
            EvidenceQualityTier.ComparisonSupported,
            report.evidenceQualityTier,
        )
        assertFalse(report.primaryComparison.orEmpty().contains("best", ignoreCase = true))
    }

    @Test
    fun practiceAndLaterUseRequireSameRevisionWithinSevenDays() {
        val rehearsal = rehearsal(revision = 10L, completedAt = 10_000L)
        val matching = decision(
            actual = InterventionFamily.MomentPlan,
            planId = PlanId,
            planRevision = 10L,
            startedAt = 20_000L,
        )
        val edited = decision(
            actual = InterventionFamily.MomentPlan,
            planId = PlanId,
            planRevision = 11L,
            startedAt = 30_000L,
        )

        val report = build(
            decisions = listOf(matching, edited),
            rehearsals = listOf(rehearsal),
        )

        assertEquals(1, report.practice.completedRehearsals)
        assertEquals(1, report.practice.laterRealUsesWithinSevenDays)
    }

    @Test
    fun recentHistoryContainsOnlyGenericSupportFields() {
        val report = build(
            decisions = List(8) { decision(completed = true) },
        )

        assertEquals(WhatWorksForMeBuilder.RecentHistoryLimit, report.recentHistory.size)
        assertTrue(
            report.recentHistory.all {
                it.intervention == InterventionFamily.PivotGame
            },
        )
    }

    @Test
    fun reportModelContainsNoUtilityOrProbabilityFields() {
        val fieldNames = WhatWorksForMeReport::class.java.declaredFields
            .map { it.name.lowercase() }

        assertTrue(fieldNames.none { "utility" in it || "probability" in it })
    }

    private fun build(
        decisions: List<AdaptiveDecision> = emptyList(),
        rehearsals: List<MomentPlanRehearsal> = emptyList(),
    ) = WhatWorksForMeBuilder.build(
        decisions = decisions,
        rehearsals = rehearsals,
        plans = emptyList(),
        nowMillis = 1_000_000L,
    )

    private fun decision(
        actual: InterventionFamily = InterventionFamily.PivotGame,
        assigned: InterventionFamily = actual,
        completed: Boolean = false,
        dismissed: Boolean = false,
        overrode: Boolean = false,
        feedback: FeedbackCode = FeedbackCode.NotProvided,
        repeat: RepeatObservation = RepeatObservation.NotFinalised,
        finalised: Boolean = false,
        planId: String? = null,
        planRevision: Long? = null,
        startedAt: Long = 2_000L,
    ): AdaptiveDecision {
        val id = UUID.randomUUID().toString()
        return AdaptiveDecision(
            decisionId = id,
            protectionIncidentToken = "opaque-$id",
            sourceKind = AdaptiveSourceKind.App,
            createdAtMillis = 1_000L,
            momentWindowStartedAtMillis = 1_000L,
            momentCue = null,
            baselineUrgeRating = null,
            assignment = AdaptiveAssignment(
                momentIntensity = MomentIntensity.RepeatedAttempt,
                assignmentMode = AssignmentMode.AdaptiveSuggestion,
                eligibleInterventions = setOf(actual, assigned),
                assignedSuggestion = assigned,
                selectionProbability = null,
                reasonCode = AdaptiveReasonCode.StableFallback,
                momentPlanId = planId,
                momentPlanUpdatedAtMillis = planRevision,
                assignedPlanContentRevisionId = planRevision?.let(::revisionId),
                actualPlanContentRevisionId = planRevision?.let(::revisionId),
                actualIntervention = actual,
                userOverrodeSuggestion = overrode,
            ),
            presentedAtMillis = 1_500L,
            startedAtMillis = startedAt,
            completedAtMillis = startedAt.plus(100L).takeIf { completed },
            dismissedAtMillis = startedAt.plus(100L).takeIf { dismissed },
            feedbackCode = feedback,
            feedbackUpdatedAtMillis = 2_200L.takeIf {
                feedback != FeedbackCode.NotProvided
            },
            repeatObservation = repeat,
            observationDeadlineAtMillis = 3_000L,
            observationFinalisedAtMillis = 3_000L.takeIf { finalised },
        )
    }

    private fun rehearsal(
        revision: Long,
        completedAt: Long,
    ) = MomentPlanRehearsal(
        rehearsalId = UUID.randomUUID().toString(),
        planId = PlanId,
        planUpdatedAtMillisAtStart = revision,
        mode = MomentPlanRehearsalMode.Guided,
        startedAtMillis = completedAt - 100L,
        completedAtMillis = completedAt,
        planContentRevisionId = revisionId(revision),
    )

    private fun revisionId(revision: Long): String =
        "00000000-0000-0000-0000-${revision.toString().padStart(12, '0')}"

    private companion object {
        const val PlanId = "00000000-0000-0000-0000-000000000111"
    }
}
