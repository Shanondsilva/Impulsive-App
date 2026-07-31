package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveDecisionLifecycleTest {
    private lateinit var decisions: FakeDecisionRepository
    private lateinit var plans: FakeMomentPlanRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var clock: FakeClock
    private lateinit var lifecycle: AdaptiveDecisionLifecycle

    @Before
    fun setUp() {
        decisions = FakeDecisionRepository()
        plans = FakeMomentPlanRepository(listOf(momentPlan()))
        scheduler = FakeScheduler()
        clock = FakeClock(10_000L)
        lifecycle = AdaptiveDecisionLifecycle(
            decisions = decisions,
            momentPlans = plans,
            scheduler = scheduler,
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
    }

    @Test
    fun actualChoiceEqualToSuggestionIsNotOverride() = runBlocking {
        decisions.stored += decision()
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.recordActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotGame,
            ),
        )
        assertFalse(decisions.stored.single().assignment.userOverrodeSuggestion)
    }

    @Test
    fun actualChoiceRecordsConcreteProtocolPassport() = runBlocking {
        decisions.stored += decision()

        lifecycle.recordActualChoice(
            decisions.stored.single().decisionId,
            InterventionFamily.PivotGame,
        )

        assertEquals("pivot_game", decisions.stored.single().actualProtocolId)
        assertEquals(1, decisions.stored.single().actualProtocolVersion)
    }

    @Test
    fun differentEligibleChoiceIsOverride() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
        )
        lifecycle.recordActualChoice(
            decisions.stored.single().decisionId,
            InterventionFamily.PivotReading,
        )
        assertTrue(decisions.stored.single().assignment.userOverrodeSuggestion)
    }

    @Test
    fun overridePreservesOriginalAssignmentReason() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
        )
        val originalReason = decisions.stored.single().assignment.reasonCode
        lifecycle.recordActualChoice(
            decisions.stored.single().decisionId,
            InterventionFamily.PivotReading,
        )
        assertEquals(originalReason, decisions.stored.single().assignment.reasonCode)
        assertEquals(
            AdaptiveReasonCode.InsufficientEvidenceExploration,
            decisions.stored.single().assignment.reasonCode,
        )
    }

    @Test
    fun ineligibleChoiceIsRejected() = runBlocking {
        decisions.stored += decision()
        assertEquals(
            AdaptiveLifecycleResult.IneligibleChoice,
            lifecycle.recordActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotReading,
            ),
        )
    }

    @Test
    fun momentPlanChoiceRequiresPlanId() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(InterventionFamily.MomentPlan),
            assigned = InterventionFamily.MomentPlan,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidMomentPlan,
            lifecycle.recordActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.MomentPlan,
            ),
        )
    }

    @Test
    fun momentPlanChoiceRequiresEnabledValidPlan() = runBlocking {
        val disabled = momentPlan(enabled = false)
        plans.plans.value = listOf(disabled)
        decisions.stored += decision(
            eligible = setOf(InterventionFamily.MomentPlan),
            assigned = InterventionFamily.MomentPlan,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidMomentPlan,
            lifecycle.recordActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.MomentPlan,
                disabled.planId,
            ),
        )
    }

    @Test
    fun realMomentPlanChoiceRecordsExactContentRevision() = runBlocking {
        val plan = plans.plans.value.single()
        decisions.stored += decision(
            eligible = setOf(InterventionFamily.MomentPlan),
            assigned = InterventionFamily.MomentPlan,
            planId = plan.planId,
        )

        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.recordActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.MomentPlan,
                plan.planId,
            ),
        )
        assertEquals(
            plan.contentRevisionId,
            decisions.stored.single().assignment.actualPlanContentRevisionId,
        )
        assertEquals("moment_plan_text", decisions.stored.single().actualProtocolId)
        assertEquals(1, decisions.stored.single().actualProtocolVersion)
    }

    @Test
    fun repeatedSameChoiceIsIdempotent() = runBlocking {
        decisions.stored += decision()
        val id = decisions.stored.single().decisionId
        lifecycle.recordActualChoice(id, InterventionFamily.PivotGame)
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.recordActualChoice(id, InterventionFamily.PivotGame),
        )
    }

    @Test
    fun conflictingSecondChoiceIsRejected() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
        )
        val id = decisions.stored.single().decisionId
        lifecycle.recordActualChoice(id, InterventionFamily.PivotGame)
        assertEquals(
            AdaptiveLifecycleResult.ConflictingChoice,
            lifecycle.recordActualChoice(id, InterventionFamily.PivotReading),
        )
    }

    @Test
    fun samePendingReplacementIsIdempotent() = runBlocking {
        decisions.stored += decision(actual = InterventionFamily.PivotGame)
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.replacePendingActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotGame,
            ),
        )
    }

    @Test
    fun differentEligibleChoiceReplacesPendingChoice() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
            actual = InterventionFamily.PivotGame,
        )
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.replacePendingActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotReading,
            ),
        )
        assertEquals(
            InterventionFamily.PivotReading,
            decisions.stored.single().assignment.actualIntervention,
        )
    }

    @Test
    fun pendingReplacementPreservesAssignmentAndRecalculatesOverride() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
            actual = InterventionFamily.PivotGame,
        )
        val original = decisions.stored.single().assignment
        val id = decisions.stored.single().decisionId

        lifecycle.replacePendingActualChoice(id, InterventionFamily.PivotReading)
        val overridden = decisions.stored.single().assignment
        assertEquals(original.assignedSuggestion, overridden.assignedSuggestion)
        assertEquals(original.reasonCode, overridden.reasonCode)
        assertTrue(overridden.userOverrodeSuggestion)

        lifecycle.replacePendingActualChoice(id, InterventionFamily.PivotGame)
        assertFalse(decisions.stored.single().assignment.userOverrodeSuggestion)
    }

    @Test
    fun pendingReplacementRejectsIneligibleChoice() = runBlocking {
        decisions.stored += decision(actual = InterventionFamily.PivotGame)
        assertEquals(
            AdaptiveLifecycleResult.IneligibleChoice,
            lifecycle.replacePendingActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotReading,
            ),
        )
        assertEquals(
            InterventionFamily.PivotGame,
            decisions.stored.single().assignment.actualIntervention,
        )
    }

    @Test
    fun pendingMomentPlanReplacementRequiresEnabledValidPlan() = runBlocking {
        val disabled = momentPlan(enabled = false)
        plans.plans.value = listOf(disabled)
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.MomentPlan,
            ),
            actual = InterventionFamily.PivotGame,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidMomentPlan,
            lifecycle.replacePendingActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.MomentPlan,
                disabled.planId,
            ),
        )
    }

    @Test
    fun startedInterventionCannotBeReplaced() = runBlocking {
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
            started = 3_000L,
        ).copy(
            actualProtocolId = "pivot_game",
            actualProtocolVersion = 1,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidTransition,
            lifecycle.replacePendingActualChoice(
                decisions.stored.single().decisionId,
                InterventionFamily.PivotReading,
            ),
        )
        assertEquals(
            InterventionFamily.PivotGame,
            decisions.stored.single().assignment.actualIntervention,
        )
        assertEquals("pivot_game", decisions.stored.single().actualProtocolId)
        assertEquals(1, decisions.stored.single().actualProtocolVersion)
    }

    @Test
    fun presentationIsIdempotent() = runBlocking {
        decisions.stored += decision()
        val id = decisions.stored.single().decisionId
        assertEquals(AdaptiveLifecycleResult.Applied, lifecycle.markPresented(id, 2_000L))
        assertEquals(AdaptiveLifecycleResult.Idempotent, lifecycle.markPresented(id, 2_000L))
    }

    @Test
    fun presentationSchedulesUniqueObservation() = runBlocking {
        decisions.stored += decision()
        val id = decisions.stored.single().decisionId
        lifecycle.markPresented(id, 2_000L)
        lifecycle.markPresented(id, 2_000L)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(decisions.stored.single().observationDeadlineAtMillis, scheduler.scheduled[id])
    }

    @Test
    fun presentationCannotPrecedeCreation() = runBlocking {
        decisions.stored += decision(created = 1_000L)
        assertEquals(
            AdaptiveLifecycleResult.InvalidTimestamp,
            lifecycle.markPresented(decisions.stored.single().decisionId, 999L),
        )
    }

    @Test
    fun startedRequiresActualIntervention() = runBlocking {
        decisions.stored += decision(presented = 2_000L)
        assertEquals(
            AdaptiveLifecycleResult.InvalidTransition,
            lifecycle.markStarted(decisions.stored.single().decisionId, 3_000L),
        )
    }

    @Test
    fun startedCannotPrecedePresentation() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidTimestamp,
            lifecycle.markStarted(decisions.stored.single().decisionId, 1_999L),
        )
    }

    @Test
    fun completedRequiresStarted() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidTransition,
            lifecycle.markCompleted(decisions.stored.single().decisionId, 4_000L),
        )
    }

    @Test
    fun completionCannotPrecedeStart() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
            started = 3_000L,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidTimestamp,
            lifecycle.markCompleted(decisions.stored.single().decisionId, 2_999L),
        )
    }

    @Test
    fun dismissedAndCompletedCannotCoexist() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
            started = 3_000L,
            completed = 4_000L,
        )
        assertEquals(
            AdaptiveLifecycleResult.InvalidTransition,
            lifecycle.markDismissed(decisions.stored.single().decisionId, 5_000L),
        )
    }

    @Test
    fun duplicateCompletionIsIdempotent() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
            started = 3_000L,
        )
        val id = decisions.stored.single().decisionId
        lifecycle.markCompleted(id, 4_000L)
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.markCompleted(id, 4_000L),
        )
    }

    @Test
    fun completionPreservesDecisionPassport() = runBlocking {
        val original = decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
            started = 3_000L,
        ).withPassport()
        decisions.stored += original

        lifecycle.markCompleted(original.decisionId, 4_000L)

        assertEquals(original.passportSnapshot(), decisions.stored.single().passportSnapshot())
    }

    @Test
    fun dismissalPreservesDecisionPassport() = runBlocking {
        val original = decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
        ).withPassport()
        decisions.stored += original

        lifecycle.markDismissed(original.decisionId, 4_000L)

        assertEquals(original.passportSnapshot(), decisions.stored.single().passportSnapshot())
    }

    @Test
    fun feedbackRevisionPreservesDecisionPassport() = runBlocking {
        val original = decision(presented = 2_000L).withPassport()
        decisions.stored += original

        lifecycle.updateFeedback(original.decisionId, FeedbackCode.Helped, 3_000L)

        assertEquals(original.passportSnapshot(), decisions.stored.single().passportSnapshot())
    }

    @Test
    fun duplicateDismissalIsIdempotent() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
        )
        val id = decisions.stored.single().decisionId
        lifecycle.markDismissed(id, 4_000L)
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.markDismissed(id, 4_000L),
        )
    }

    @Test
    fun unstartedDismissalDoesNotFabricateCompletion() = runBlocking {
        decisions.stored += decision(
            actual = InterventionFamily.PivotGame,
            presented = 2_000L,
        )
        lifecycle.markDismissed(decisions.stored.single().decisionId, 4_000L)
        assertEquals(4_000L, decisions.stored.single().dismissedAtMillis)
        assertEquals(null, decisions.stored.single().completedAtMillis)
    }

    @Test
    fun feedbackRevisionUpdatesSingleDecision() = runBlocking {
        decisions.stored += decision(presented = 2_000L)
        val id = decisions.stored.single().decisionId
        lifecycle.updateFeedback(id, FeedbackCode.HelpedALittle, 3_000L)
        lifecycle.updateFeedback(id, FeedbackCode.Helped, 4_000L)
        assertEquals(1, decisions.stored.size)
        assertEquals(FeedbackCode.Helped, decisions.stored.single().feedbackCode)
    }

    @Test
    fun wrongTimingRemainsSeparateFeedback() = runBlocking {
        decisions.stored += decision(presented = 2_000L)
        lifecycle.updateFeedback(
            decisions.stored.single().decisionId,
            FeedbackCode.WrongTiming,
            3_000L,
        )
        assertEquals(FeedbackCode.WrongTiming, decisions.stored.single().feedbackCode)
    }

    @Test
    fun feedbackSkipIsValid() = runBlocking {
        decisions.stored += decision(presented = 2_000L)
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.updateFeedback(
                decisions.stored.single().decisionId,
                FeedbackCode.NotProvided,
                3_000L,
            ),
        )
    }

    @Test
    fun futureFeedbackTimestampFailsSafely() = runBlocking {
        decisions.stored += decision(presented = 2_000L)
        assertEquals(
            AdaptiveLifecycleResult.InvalidTimestamp,
            lifecycle.updateFeedback(
                decisions.stored.single().decisionId,
                FeedbackCode.Helped,
                clock.current + 1L,
            ),
        )
    }

    @Test
    fun missingDecisionReturnsNotFound() = runBlocking {
        assertEquals(
            AdaptiveLifecycleResult.NotFound,
            lifecycle.markPresented("missing", 2_000L),
        )
    }

    private fun com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision.withPassport() =
        copy(
            recommendationPolicyVersion = 4,
            assignedProtocolId = "pivot_game",
            assignedProtocolVersion = 1,
            actualProtocolId = "pivot_game",
            actualProtocolVersion = 1,
            eligibleMomentPlanCount = 2,
        )

    private fun com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision.passportSnapshot() =
        listOf(
            recommendationPolicyVersion,
            assignedProtocolId,
            assignedProtocolVersion,
            actualProtocolId,
            actualProtocolVersion,
            eligibleMomentPlanCount,
            assignment.eligibleInterventionsMask,
            assignment.assignedPlanContentRevisionId,
            assignment.actualPlanContentRevisionId,
        )
}
