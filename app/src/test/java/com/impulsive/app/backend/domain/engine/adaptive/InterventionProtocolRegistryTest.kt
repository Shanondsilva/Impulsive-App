package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.session.adaptive.AdaptiveCompletionGate
import com.impulsive.app.backend.session.adaptive.AdaptiveMomentRoutingPolicy
import com.impulsive.app.backend.session.adaptive.AdaptiveRouteKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionProtocolRegistryTest {
    @Test
    fun registryContainsEverySupportedRoute() {
        assertEquals(
            setOf(
                "short_pause",
                "pivot_game",
                "reset_reading",
                "moment_plan_text",
                "moment_plan_external_app",
                "moment_plan_focus",
                "moment_plan_journal",
                "moment_plan_pivot_game",
                "moment_plan_reset_reading",
            ),
            contracts.mapTo(linkedSetOf()) { it.protocolId.value },
        )
    }

    @Test
    fun noDuplicateProtocolIdAndVersionPairs() {
        assertEquals(
            contracts.size,
            contracts.distinctBy { it.protocolId to it.version }.size,
        )
    }

    @Test
    fun everyProtocolMapsToKnownFamily() {
        assertTrue(contracts.all { it.family in InterventionFamily.entries })
    }

    @Test
    fun everyProtocolDefinesStartedSemantics() {
        assertTrue(contracts.all { it.startRule in InterventionStartRule.entries })
    }

    @Test
    fun everyProtocolDefinesValidTerminalSemantics() {
        assertTrue(InterventionProtocolValidator.validate(contracts).isEmpty())
        assertTrue(
            contracts
                .filter {
                    it.completionRule ==
                        InterventionCompletionRule.ExplicitManualConfirmation
                }
                .all { it.manualCompletionAvailable },
        )
    }

    @Test
    fun everyProtocolDefinesSafeFallback() {
        assertTrue(contracts.all { it.safeFallback is InterventionSafeFallback })
        assertTrue(
            contracts
                .filter { it.protocolId.value != "short_pause" }
                .all {
                    (it.safeFallback as? InterventionSafeFallback.Protocol)
                        ?.protocolId
                        ?.value == "short_pause"
                },
        )
    }

    @Test
    fun everyProtocolProhibitsProtectedSourceIdentityAndContent() {
        val required = setOf(
            InterventionProhibitedField.ProtectedSourceIdentity,
            InterventionProhibitedField.ProtectedPackage,
            InterventionProhibitedField.Url,
            InterventionProhibitedField.Domain,
            InterventionProhibitedField.JournalContent,
        )
        assertTrue(
            contracts.all {
                it.dataPolicy.prohibitedStoredFields.containsAll(required)
            },
        )
    }

    @Test
    fun everyProtocolPassesAccessibilityRequirements() {
        assertTrue(
            contracts.all {
                it.accessibilityPolicy.supportsTalkBack &&
                    it.accessibilityPolicy.supportsLargeText &&
                    it.accessibilityPolicy.hasNonAudioPath &&
                    it.accessibilityPolicy.avoidsColourOnlyState
            },
        )
    }

    @Test
    fun unknownProtocolFailsClosed() {
        assertNull(
            InterventionProtocolRegistry.resolveExecutable(
                InterventionProtocolId("unknown_protocol"),
                InterventionProtocolVersion(1),
            ),
        )
        assertNull(
            AdaptiveMomentRoutingPolicy.forPlanAction(
                DecisionId,
                plan(
                    type = MomentPlanActionType.OpenImpulsiveDestination,
                    target = "untrusted_destination",
                ),
            ),
        )
    }

    @Test
    fun removedProtocolVersionRemainsReadableButNotExecutable() {
        val historical = InterventionProtocolRegistry.historical(
            InterventionProtocolId("pivot_game"),
            InterventionProtocolVersion(2),
        )

        assertNotNull(historical)
        assertEquals(InterventionFamily.PivotGame, historical?.family)
        assertEquals("Pivot Game", historical?.consumerDisplayName)
        assertNull(historical?.executableContract)
    }

    @Test
    fun lookupDoesNotMutateMomentPlan() {
        val before = plan(MomentPlanActionType.TextOnly, null)
        val after = before.copy()

        assertNotNull(InterventionProtocolRegistry.resolveForPlan(before))
        assertEquals(after, before)
    }

    @Test
    fun registryUsesNoNetworkAccess() {
        val source = registrySource()
        assertFalse(source.contains("java.net"))
        assertFalse(source.contains("okhttp", ignoreCase = true))
        assertFalse(source.contains("retrofit", ignoreCase = true))
    }

    @Test
    fun registryUsesNoReflection() {
        val source = registrySource()
        assertFalse(source.contains("kotlin.reflect"))
        assertFalse(source.contains("java.lang.reflect"))
        assertFalse(source.contains("Class.forName"))
    }

    @Test
    fun registryDoesNotCreateRoutesFromJsonOrUntrustedText() {
        val source = registrySource()
        assertFalse(source.contains("org.json"))
        assertFalse(source.contains("JSONObject"))
        assertFalse(source.contains("ClassLoader"))
    }

    @Test
    fun existingShortPauseCompletionRemainsThirtySeconds() {
        assertFalse(AdaptiveCompletionGate.pauseFinished(1_000L, 30_999L))
        assertTrue(AdaptiveCompletionGate.pauseFinished(1_000L, 31_000L))
        assertEquals(
            InterventionCompletionRule.PauseDurationElapsed,
            current("short_pause").completionRule,
        )
    }

    @Test
    fun existingGameCompletionRemainsGenuineCompletion() {
        assertFalse(AdaptiveCompletionGate.gameCompleted(false))
        assertTrue(AdaptiveCompletionGate.gameCompleted(true))
        assertEquals(
            InterventionCompletionRule.GenuineGameCompletion,
            current("pivot_game").completionRule,
        )
    }

    @Test
    fun existingReadingCompletionStillRequiresTimeEndAndValidCompletion() {
        assertFalse(AdaptiveCompletionGate.readingCompleted(89, true, true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(90, false, true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(90, true, false))
        assertTrue(AdaptiveCompletionGate.readingCompleted(90, true, true))
        assertEquals(
            InterventionCompletionRule.ReadingMinimumAndArticleEnd,
            current("reset_reading").completionRule,
        )
    }

    @Test
    fun existingMomentPlanRoutesMapToExplicitProtocols() {
        val mappings = listOf(
            plan(MomentPlanActionType.TextOnly, null) to "moment_plan_text",
            plan(MomentPlanActionType.LaunchSelectedApp, "com.example.safe") to
                "moment_plan_external_app",
            plan(
                MomentPlanActionType.OpenImpulsiveDestination,
                ImpulsiveDestination.Focus.storageValue,
            ) to "moment_plan_focus",
            plan(
                MomentPlanActionType.OpenImpulsiveDestination,
                ImpulsiveDestination.Journal.storageValue,
            ) to "moment_plan_journal",
            plan(
                MomentPlanActionType.OpenImpulsiveDestination,
                ImpulsiveDestination.PivotGames.storageValue,
            ) to "moment_plan_pivot_game",
            plan(
                MomentPlanActionType.OpenImpulsiveDestination,
                ImpulsiveDestination.ResetReading.storageValue,
            ) to "moment_plan_reset_reading",
        )

        mappings.forEach { (plan, expectedId) ->
            assertEquals(
                expectedId,
                InterventionProtocolRegistry.resolveForPlan(plan)?.protocolId?.value,
            )
        }
        assertEquals(
            AdaptiveRouteKind.Focus,
            AdaptiveMomentRoutingPolicy.forPlanAction(
                DecisionId,
                mappings[2].first,
            )?.kind,
        )
    }

    private fun current(id: String): InterventionProtocolContract =
        requireNotNull(
            InterventionProtocolRegistry.resolveExecutable(
                InterventionProtocolId(id),
                InterventionProtocolRegistry.CurrentVersion,
            ),
        )

    private fun plan(
        type: MomentPlanActionType,
        target: String?,
    ) = MomentPlan(
        planId = "11111111-1111-4111-8111-111111111111",
        title = "Plan",
        momentCue = null,
        actionText = "Take one safe step",
        futureCueText = "Feel clearer",
        actionType = type,
        actionTarget = target,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun registrySource(): String {
        val relative =
            "app/src/main/java/com/impulsive/app/backend/domain/engine/adaptive/" +
                "InterventionProtocolRegistry.kt"
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull(File::isFile)
        return requireNotNull(file) { "Could not find $relative" }.readText()
    }

    private val contracts
        get() = InterventionProtocolRegistry.contracts

    private companion object {
        const val DecisionId = "22222222-2222-4222-8222-222222222222"
    }
}
