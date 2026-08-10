package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhase5IntegrationTest {
    @Test
    fun genuineIncidentCreatesOneDecisionAndDuplicateReturnsIt() = runBlocking {
        val decisions = FakeDecisionRepository()
        val bridge = AdaptiveProtectionBridge(
            coordinatorHarness(decisions = decisions),
        )
        val signal = appSignal()

        val first = bridge.recognise(signal)
        val duplicateOverlay = bridge.recognise(signal)
        val duplicateNotification = bridge.recognise(signal)

        assertNotNull(first.decisionId)
        assertEquals(first.decisionId, duplicateOverlay.decisionId)
        assertEquals(first.decisionId, duplicateNotification.decisionId)
        assertTrue(duplicateOverlay.duplicate)
        assertTrue(duplicateNotification.duplicate)
        assertEquals(1, decisions.stored.size)
    }

    @Test
    fun laterGenuineIncidentCreatesANewDecision() = runBlocking {
        val decisions = FakeDecisionRepository()
        val bridge = AdaptiveProtectionBridge(
            coordinatorHarness(decisions = decisions),
        )
        val first = bridge.recognise(appSignal(at = 1_000_000L))
        val later = bridge.recognise(appSignal(at = 2_300_000L))

        assertNotEquals(first.decisionId, later.decisionId)
        assertEquals(2, decisions.stored.size)
    }

    @Test
    fun tokenIsStableOpaqueAndContainsNoRawSource() {
        val signal = appSignal(source = "com.private.browser")
        val first = AdaptiveIncidentTokenFactory.create(signal)
        val second = AdaptiveIncidentTokenFactory.create(signal)

        assertEquals(first, second)
        assertTrue(first.startsWith("ai1_"))
        assertEquals(68, first.length)
        assertFalse(first.contains("private"))
        assertFalse(first.contains("browser"))
        assertFalse(first.contains("com."))
    }

    @Test
    fun tokenChangesForLaterIncidentAndDifferentCoarseSource() {
        val first = AdaptiveIncidentTokenFactory.create(appSignal(at = 1_000L))
        val later = AdaptiveIncidentTokenFactory.create(appSignal(at = 2_000L))
        val website = AdaptiveIncidentTokenFactory.create(
            AdaptiveIncidentSignal(
                AdaptiveProtectionSource.VpnWebsite,
                1_000L,
                "com.example.browser",
            ),
        )

        assertNotEquals(first, later)
        assertNotEquals(first, website)
    }

    @Test
    fun bridgePersistsOnlyCoarseAppSourceKind() = runBlocking {
        val decisions = FakeDecisionRepository()
        AdaptiveProtectionBridge(coordinatorHarness(decisions = decisions))
            .recognise(appSignal())

        assertEquals(AdaptiveSourceKind.App, decisions.stored.single().sourceKind)
        assertFalse(
            decisions.stored.single().protectionIncidentToken.contains("com.example"),
        )
    }

    @Test
    fun bridgePersistsOnlyCoarseWebsiteSourceKind() = runBlocking {
        val decisions = FakeDecisionRepository()
        AdaptiveProtectionBridge(coordinatorHarness(decisions = decisions))
            .recognise(
                AdaptiveIncidentSignal(
                    AdaptiveProtectionSource.VpnWebsite,
                    1_000_000L,
                    "com.example.browser",
                ),
            )

        assertEquals(AdaptiveSourceKind.Website, decisions.stored.single().sourceKind)
    }

    @Test
    fun coordinatorFailureRequiresStableProtectionFallback() = runBlocking {
        val decisions = FakeDecisionRepository().apply { throwOnRead = true }
        val result = AdaptiveProtectionBridge(
            coordinatorHarness(decisions = decisions),
        ).recognise(appSignal())

        assertNull(result.decisionId)
        assertTrue(result.fallbackRequired)
    }

    @Test
    /**
     * A protected incident is game-only.
     *
     * It used to assign a Short Pause and widen into Reading/Moment Plan
     * overrides so the user could escape it. The protected Moment now starts
     * the game directly, so no other intervention is admitted.
     */
    fun protectedIncidentAssignsTheGameAndAdmitsNoOtherIntervention() = runBlocking {
        val decisions = FakeDecisionRepository()
        val plans = FakeMomentPlanRepository(listOf(momentPlan()))
        val bridge = AdaptiveProtectionBridge(
            coordinator = coordinatorHarness(
                decisions = decisions,
                plans = plans,
            ),
            decisions = decisions,
            momentPlans = plans,
        )

        val result = bridge.recognise(appSignal())
        val stored = decisions.getById(requireNotNull(result.decisionId))

        assertEquals(
            InterventionFamily.PivotGame,
            stored?.assignment?.assignedSuggestion,
        )
        assertEquals(
            AdaptiveReasonCode.MinimumEffectiveFriction,
            stored?.assignment?.reasonCode,
        )
        assertTrue(InterventionFamily.PivotGame in stored!!.assignment.eligibleInterventions)
        assertFalse(InterventionFamily.ShortPause in stored.assignment.eligibleInterventions)
        assertFalse(InterventionFamily.PivotReading in stored.assignment.eligibleInterventions)
        assertFalse(InterventionFamily.MomentPlan in stored.assignment.eligibleInterventions)
    }

    @Test
    fun everyReasonHasCautiousNonClinicalCopy() {
        AdaptiveReasonCode.entries.forEach { reason ->
            val copy = AdaptiveWhyThisCopy.forReason(reason)
            assertTrue(copy.isNotBlank())
            assertFalse(copy.contains(Regex("""\d+(?:\.\d+)?%?""")))
            assertFalse(copy.contains("clinically", ignoreCase = true))
            assertFalse(copy.contains("will work", ignoreCase = true))
            assertFalse(copy.contains("guarantee", ignoreCase = true))
            assertFalse(copy.contains("recommended by AI", ignoreCase = true))
        }
    }

    @Test
    fun minimumFrictionAndRandomVariationUseApprovedMeaning() {
        assertEquals(
            "A short pause keeps the first step simple.",
            AdaptiveWhyThisCopy.forReason(
                AdaptiveReasonCode.MinimumEffectiveFriction,
            ),
        )
        assertTrue(
            AdaptiveWhyThisCopy.forReason(
                AdaptiveReasonCode.RandomisedExploration,
            ).contains("occasionally varies"),
        )
    }

    @Test
    fun eachInterventionRoutesWithoutProtectedSourceData() {
        val id = "decision-id"
        assertNull(
            AdaptiveMomentRoutingPolicy.forChoice(id, InterventionFamily.ShortPause),
        )
        assertEquals(
            AdaptiveRouteKind.Game,
            AdaptiveMomentRoutingPolicy.forChoice(
                id,
                InterventionFamily.PivotGame,
            )?.kind,
        )
        assertEquals(
            AdaptiveRouteKind.Reading,
            AdaptiveMomentRoutingPolicy.forChoice(
                id,
                InterventionFamily.PivotReading,
            )?.kind,
        )
        assertEquals(
            AdaptiveRouteKind.MomentPlan,
            AdaptiveMomentRoutingPolicy.forChoice(
                id,
                InterventionFamily.MomentPlan,
            )?.kind,
        )
    }

    @Test
    fun approvedImpulsiveDestinationsAreAllowlisted() {
        val expected = mapOf(
            ImpulsiveDestination.Focus to AdaptiveRouteKind.Focus,
            ImpulsiveDestination.Journal to AdaptiveRouteKind.Journal,
            ImpulsiveDestination.PivotGames to AdaptiveRouteKind.Game,
            ImpulsiveDestination.ResetReading to AdaptiveRouteKind.Reading,
        )
        expected.forEach { (destination, kind) ->
            val plan = momentPlan(
                actionType = MomentPlanActionType.OpenImpulsiveDestination,
                target = destination.storageValue,
            )
            assertEquals(
                kind,
                AdaptiveMomentRoutingPolicy.forPlanAction("decision", plan)?.kind,
            )
        }
    }

    @Test
    fun unknownImpulsiveDestinationIsRejected() {
        val unsafe = momentPlan(
            actionType = MomentPlanActionType.OpenImpulsiveDestination,
            target = "https://example.com/private",
        )
        assertNull(
            AdaptiveMomentRoutingPolicy.forPlanAction("decision", unsafe),
        )
    }

    @Test
    fun selectedApplicationRequiresAValidPackageAndNoUri() {
        val valid = momentPlan(
            actionType = MomentPlanActionType.LaunchSelectedApp,
            target = "com.example.safe",
        )
        val url = valid.copy(actionTarget = "https://example.com")
        val malformed = valid.copy(actionTarget = "not a package")

        assertEquals(
            AdaptiveRouteKind.ExternalApplication,
            AdaptiveMomentRoutingPolicy.forPlanAction("decision", valid)?.kind,
        )
        assertNull(AdaptiveMomentRoutingPolicy.forPlanAction("decision", url))
        assertNull(AdaptiveMomentRoutingPolicy.forPlanAction("decision", malformed))
    }

    @Test
    fun textActionDoesNotClaimAutomaticExecution() {
        val text = momentPlan(actionType = MomentPlanActionType.TextOnly)
        assertNull(AdaptiveMomentRoutingPolicy.forPlanAction("decision", text))
    }

    @Test
    fun validContextPersistsOnceAndInvalidRatingIsRejected() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision()

        assertTrue(
            repository.recordMomentContextOnce(
                decision().decisionId,
                MomentCue.Stress,
                10,
            ),
        )
        assertEquals(MomentCue.Stress, repository.stored.single().momentCue)
        assertEquals(10, repository.stored.single().baselineUrgeRating)
        assertFalse(
            repository.recordMomentContextOnce(
                decision().decisionId,
                MomentCue.Boredom,
                11,
            ),
        )
        assertEquals(MomentCue.Stress, repository.stored.single().momentCue)
    }

    private fun appSignal(
        at: Long = 1_000_000L,
        source: String = "com.example.browser",
    ) = AdaptiveIncidentSignal(
        source = AdaptiveProtectionSource.MonitoredApplication,
        incidentStartedAtMillis = at,
        ephemeralSourceIdentity = source,
    )
}
