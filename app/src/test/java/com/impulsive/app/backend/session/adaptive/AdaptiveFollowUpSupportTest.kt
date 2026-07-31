package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveFollowUpSupportTest {
    @Test
    fun explicitChoiceAfterStartedCreatesNewDecisionAndPreservesOriginal() = runBlocking {
        val harness = harness()
        val original = harness.decisions.stored.single()

        val result = harness.support.chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = original.decisionId,
                intervention = InterventionFamily.PivotReading,
            ),
        )

        assertTrue(result is AdaptiveFollowUpResult.Ready)
        assertEquals(2, harness.decisions.stored.size)
        assertEquals(original, harness.decisions.stored.first())
        val followUp = harness.decisions.stored.last()
        assertNotEquals(original.decisionId, followUp.decisionId)
        assertNotEquals(
            original.protectionIncidentToken,
            followUp.protectionIncidentToken,
        )
        assertEquals(AdaptiveSourceKind.ExplicitUserSupport, followUp.sourceKind)
        assertEquals(
            original.momentWindowStartedAtMillis,
            followUp.momentWindowStartedAtMillis,
        )
        assertEquals(
            InterventionFamily.PivotReading,
            followUp.assignment.actualIntervention,
        )
    }

    @Test
    fun followUpReadingReturnsRouteWithNewDecisionId() = runBlocking {
        val harness = harness()
        val originalId = harness.decisions.stored.single().decisionId
        val result = harness.support.chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = originalId,
                intervention = InterventionFamily.PivotReading,
            ),
        ) as AdaptiveFollowUpResult.Ready

        assertNotEquals(originalId, result.decisionId)
        assertEquals(AdaptiveRouteKind.Reading, result.routeRequest?.kind)
        assertEquals(result.decisionId, result.routeRequest?.decisionId)
    }

    @Test
    fun followUpMomentPlanValidatesPersistsAndRoutes() = runBlocking {
        val plan = momentPlan()
        val harness = harness(enabledPlans = listOf(plan))
        val originalId = harness.decisions.stored.single().decisionId
        val result = harness.support.chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = originalId,
                intervention = InterventionFamily.MomentPlan,
                momentPlanId = plan.planId,
            ),
        ) as AdaptiveFollowUpResult.Ready

        val followUp = requireNotNull(harness.decisions.getById(result.decisionId))
        assertEquals(InterventionFamily.MomentPlan, followUp.assignment.actualIntervention)
        assertEquals(plan.planId, followUp.assignment.momentPlanId)
        assertEquals(AdaptiveRouteKind.MomentPlan, result.routeRequest?.kind)
    }

    @Test
    fun disabledMomentPlanDoesNotCreateFollowUp() = runBlocking {
        val disabled = momentPlan(enabled = false)
        val harness = harness(enabledPlans = listOf(disabled))
        val result = harness.support.chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = harness.decisions.stored.single().decisionId,
                intervention = InterventionFamily.MomentPlan,
                momentPlanId = disabled.planId,
            ),
        )

        assertEquals(AdaptiveFollowUpResult.InvalidMomentPlan, result)
        assertEquals(1, harness.decisions.stored.size)
    }

    @Test
    fun unstartedDecisionUsesNoFollowUp() = runBlocking {
        val harness = harness(started = false)
        val result = harness.support.chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = harness.decisions.stored.single().decisionId,
                intervention = InterventionFamily.PivotReading,
            ),
        )

        assertEquals(AdaptiveFollowUpResult.PreviousDecisionNotStarted, result)
        assertEquals(1, harness.decisions.stored.size)
    }

    @Test
    fun constructionAndReadOnlyScreenEventsCreateNoFollowUp() {
        val harness = harness()

        // Recomposition and Back do not call chooseAnother.
        assertEquals(1, harness.decisions.stored.size)
        assertEquals(0, harness.decisions.insertCalls)
    }

    @Test
    fun followUpTokensAreUniqueAndContainNoPrivateSourceData() {
        val previousId = UUID.randomUUID().toString()
        val first = AdaptiveFollowUpIncidentTokenFactory.create(
            previousId,
            UUID.randomUUID().toString(),
        )
        val second = AdaptiveFollowUpIncidentTokenFactory.create(
            previousId,
            UUID.randomUUID().toString(),
        )

        assertNotEquals(first, second)
        listOf(
            "com.private.browser",
            "https://private.example/path",
            "private.example",
            previousId,
        ).forEach { privateValue ->
            assertFalse(first.contains(privateValue))
            assertFalse(second.contains(privateValue))
        }
        assertTrue(first.startsWith("afu1_"))
    }

    private fun harness(
        started: Boolean = true,
        enabledPlans: List<com.impulsive.app.backend.domain.model.adaptive.MomentPlan> =
            listOf(momentPlan()),
    ): Harness {
        val decisions = FakeDecisionRepository()
        decisions.stored += decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
                InterventionFamily.MomentPlan,
            ),
            actual = InterventionFamily.PivotGame,
            presented = if (started) 2_000L else null,
            started = if (started) 3_000L else null,
        )
        decisions.insertCalls = 0
        val plans = FakeMomentPlanRepository(enabledPlans)
        val preferences = FakePreferenceRepository()
        val scheduler = FakeScheduler()
        val clock = FakeClock(10_000L)
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions = decisions,
            momentPlans = plans,
            scheduler = scheduler,
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
        var attempt = 0
        val support = AdaptiveFollowUpSupport(
            coordinator = coordinatorHarness(
                decisions = decisions,
                preferences = preferences,
                plans = plans,
                clock = clock,
            ),
            decisions = decisions,
            momentPlans = plans,
            lifecycle = lifecycle,
            clock = clock,
            attemptIdSource = AdaptiveIdSource {
                UUID.nameUUIDFromBytes("attempt-${attempt++}".toByteArray()).toString()
            },
        )
        return Harness(decisions, support)
    }

    private data class Harness(
        val decisions: FakeDecisionRepository,
        val support: AdaptiveFollowUpSupport,
    )
}
