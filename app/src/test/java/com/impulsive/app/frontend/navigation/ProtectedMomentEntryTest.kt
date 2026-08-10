package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate-2 contract: a protected app or site enters the game-only Support Cycle
 * automatically, with no questionnaire, no intervention choice and no Short
 * Pause. Focus and the manual Adaptive Moment keep their own behaviour.
 */
class ProtectedMomentEntryTest {

    // ---------- A. ROUTING ----------

    /*
     * Route patterns are asserted rather than built route strings: building one
     * calls android.net.Uri, which is unavailable in a plain JVM unit test.
     */

    @Test
    fun protectedAppWithADecisionRoutesToTheProtectedMoment() {
        assertEquals(
            AppRoutes.ProtectedMoment,
            blockRequestDestinationRoutePattern(protectedRequest()),
        )
        assertTrue(AppRoutes.ProtectedMoment.startsWith("protected_moment/"))
    }

    @Test
    fun protectedWebsiteWithADecisionRoutesToTheProtectedMoment() {
        assertEquals(
            AppRoutes.ProtectedMoment,
            blockRequestDestinationRoutePattern(
                protectedRequest(sourcePackageName = "com.android.chrome"),
            ),
        )
    }

    @Test
    fun protectedEntryNeverRoutesToTheQuestionnaire() {
        val pattern = blockRequestDestinationRoutePattern(protectedRequest())

        assertEquals(AppRoutes.ProtectedMoment, pattern)
        assertNotEquals(AppRoutes.AdaptiveMoment, pattern)
        assertNotEquals(AppRoutes.ResetReadFallbackTask, pattern)
    }

    @Test
    fun focusInterruptionStillUsesFocusRecovery() {
        val focus = BlockRequest(
            sourcePackageName = "com.example.app",
            sourceLabel = "Example",
            detectedAtMillis = 1_000L,
            launchTarget = BlockLaunchTarget.FocusRecovery,
            adaptiveDecisionId = null,
        )

        assertEquals(AppRoutes.FocusRecovery, blockRequestDestinationRoutePattern(focus))
        assertNotEquals(AppRoutes.ProtectedMoment, blockRequestDestinationRoutePattern(focus))
    }

    @Test
    fun theManualAdaptiveMomentRouteStillExists() {
        val manual = BlockRequest(
            sourcePackageName = "com.example.app",
            sourceLabel = "Example",
            detectedAtMillis = 1_000L,
            launchTarget = BlockLaunchTarget.AdaptiveMoment,
            adaptiveDecisionId = "decision-1",
        )

        assertEquals(
            AppRoutes.AdaptiveMoment,
            blockRequestDestinationRoutePattern(manual),
        )
        assertTrue(AppRoutes.AdaptiveMoment.startsWith("adaptive_moment/"))
    }

    /** Without a usable decision the protected path falls back to the block screen. */
    @Test
    fun aProtectedRequestWithoutADecisionFallsBackToTheBlockScreen() {
        val navHost = navHostSource()
        val mapping = navHost.substringAfter("BlockLaunchTarget.ProtectedMoment ->")
            .substringBefore("BlockLaunchTarget.RandomRecoveryGame ->")

        assertTrue(mapping.contains("AppRoutes.impulsiveBlock("))
        assertFalse(mapping.contains("adaptiveMoment("))
    }

    /** A duplicate protected intent must not interrupt a running game. */
    @Test
    fun aRunningProtectedGameAlreadySatisfiesTheRequest() {
        assertTrue(
            blockRequestDestinationMatches(
                currentRoutePattern = AppRoutes.SnakeGameTask,
                currentSourcePackageName = null,
                currentSourceLabel = null,
                request = protectedRequest(),
            ),
        )
        assertTrue(
            blockRequestDestinationMatches(
                currentRoutePattern = AppRoutes.ProtectedMoment,
                currentSourcePackageName = null,
                currentSourceLabel = null,
                request = protectedRequest(),
                currentAdaptiveDecisionId = "decision-1",
            ),
        )
        assertFalse(
            blockRequestDestinationMatches(
                currentRoutePattern = AppRoutes.AdaptiveMoment,
                currentSourcePackageName = null,
                currentSourceLabel = null,
                request = protectedRequest(),
                currentAdaptiveDecisionId = "decision-1",
            ),
        )
    }

    // ---------- B. ORB BRIDGE ----------

    @Test
    fun theProtectedBridgeShowsNoCopyLogoOrChoice() {
        val bridge = protectedBridgeSource()

        listOf(
            "You know where this usually leads.",
            "Choose a different direction",
            "Leave this app",
            "IMPULSIVE",
            "Pivot by Reading",
            "Pivot by Game",
        ).forEach { removed ->
            assertFalse("bridge must not contain: $removed", bridge.contains(removed))
        }
    }

    @Test
    fun theProtectedBridgeIsAutomaticAndNonInteractive() {
        val bridge = protectedBridgeSource()

        // Decorative orb: never actionable, never announced as a control.
        assertTrue(bridge.contains("IMPORTANT_FOR_ACCESSIBILITY_NO"))
        assertTrue(bridge.contains("isClickable = false"))
        assertTrue(bridge.contains("isFocusable = false"))
        assertFalse(bridge.contains("setOnClickListener"))
        assertFalse(bridge.contains("setOnTouchListener"))
        // Launches by itself, exactly once.
        assertTrue(bridge.contains("launchProtectedMomentOnce()"))
        assertTrue(bridge.contains("if (launched) return"))
        assertTrue(bridge.contains("BlockLaunchTarget.ProtectedMoment"))
    }

    @Test
    fun theProtectedBridgeRespectsDisabledSystemAnimations() {
        val overlay = overlaySource()

        assertTrue(overlay.contains("areAnimatorsEnabled()"))
        assertTrue(protectedBridgeSource().contains("if (animationsEnabled())"))
    }

    /** Back still leaves: the bridge is brief, not a trap. */
    @Test
    fun theProtectedBridgeKeepsSystemBackEscape() {
        assertTrue(protectedBridgeSource().contains("leaveProtectedApp"))
    }

    @Test
    fun focusDoesNotUseTheProtectedBridge() {
        val overlay = overlaySource()

        // The bridge is chosen only for a non-Focus interruption with a decision.
        assertTrue(overlay.contains("if (!isFocusSession && adaptiveDecisionId != null)"))
        // Focus keeps its own presentation.
        assertTrue(overlay.contains("FocusPrimaryActionLabel"))
        assertTrue(overlay.contains("BlockLaunchTarget.FocusRecovery"))
    }

    // ---------- C/D. GAME-ONLY PROTECTED ELIGIBILITY ----------

    @Test
    fun protectedIncidentsAdmitOnlyTheGame() {
        val bridge = protectionBridgeSource()
        val allowed = bridge.substringAfter("currentlyAllowedInterventions = setOf(")
            .substringBefore(")")

        assertTrue(allowed.contains("InterventionFamily.PivotGame"))
        assertFalse(allowed.contains("ShortPause"))
        assertFalse(allowed.contains("PivotReading"))
        assertFalse(allowed.contains("MomentPlan"))
        assertTrue(bridge.contains("readingProductEligible = false"))
        assertTrue(bridge.contains("momentPlansProductEligible = false"))
    }

    @Test
    fun protectedDecisionsAreNotWidenedIntoOtherInterventions() {
        // The old first-attempt "alternatives" widening is gone.
        assertFalse(protectionBridgeSource().contains("addEligibleInterventions"))
    }

    // ---------- E. SHORT PAUSE RETIREMENT ----------

    @Test
    fun noNewRecommendationCanReturnShortPause() {
        assertFalse(recommendationPolicySource().contains("InterventionFamily.ShortPause"))
    }

    /**
     * The enum constant itself must survive: persisted history, exports and
     * restores still contain it.
     */
    @Test
    fun theLegacyShortPauseIdentityRemainsReadable() {
        val models = source(
            "app/src/main/java/com/impulsive/app/backend/domain/model/adaptive/" +
                "AdaptiveMomentModels.kt",
        )

        assertTrue(models.contains("ShortPause"))
    }

    // ---------- F. RESET READING ----------

    @Test
    fun protectedEntryCannotReachResetReading() {
        val bridge = protectedBridgeSource()

        assertFalse(bridge.contains("BlockLaunchTarget.ReadingReset"))
        assertFalse(bridge.contains("AdaptiveReading"))
        assertNotEquals(
            AppRoutes.ResetReadFallbackTask,
            blockRequestDestinationRoutePattern(protectedRequest()),
        )
    }

    @Test
    fun standaloneResetReadingIsPreserved() {
        val navHost = navHostSource()

        assertTrue(navHost.contains("ResetReadTask"))
        assertTrue(navHost.contains("AppRoutes.ResetReadFallbackTask"))
    }

    // ---------- G/H. BOOTSTRAP ----------

    @Test
    fun theBootstrapResumesAnExistingStepBeforeSelectingAGame() {
        val bootstrap = protectedMomentRouteSource()

        // Resume check precedes selection.
        val resumeAt = bootstrap.indexOf("requiresResumeBeforeStartingGame")
        val selectAt = bootstrap.indexOf("selectAndRecordGuidedGame")
        assertTrue(resumeAt in 1 until selectAt)
        assertTrue(bootstrap.contains("return@LaunchedEffect"))
    }

    @Test
    fun theBootstrapUsesTheAuthoritativeCycleAndSelectionEngine() {
        val bootstrap = protectedMomentRouteSource()

        assertTrue(bootstrap.contains("createOrRecover(decision)"))
        assertTrue(bootstrap.contains("selectAndRecordGuidedGame"))
        assertTrue(bootstrap.contains("coordinator.startGame("))
        assertTrue(bootstrap.contains("AdaptiveSupportCycleTiming.TotalDurationMillis"))
        // No second catalogue or hand-rolled timer.
        assertFalse(bootstrap.contains("ScoreGameType.Snake"))
    }

    /** Keyed on the decision, so recomposition cannot re-run the bootstrap. */
    @Test
    fun theBootstrapIsKeyedOnStableAuthoritativeIdentity() {
        val bootstrap = protectedMomentRouteSource()

        assertTrue(bootstrap.contains("LaunchedEffect(decisionId)"))
        assertFalse(bootstrap.contains("LaunchedEffect(Unit)"))
    }

    @Test
    fun theBootstrapShowsNoQuestionnaireOrControls() {
        val bootstrap = protectedMomentRouteSource()

        listOf(
            "AdaptiveMomentScreen(",
            "Button(",
            "Text(",
            "Skip cue",
            "Something else",
            "Why this?",
        ).forEach { absent ->
            assertFalse("bootstrap must not contain: $absent", bootstrap.contains(absent))
        }
        // Only a matching background is drawn while the bootstrap runs.
        assertTrue(bootstrap.contains("ProtectedMomentBootstrapBackground"))
    }

    // ---------- I. FAILURE ----------

    @Test
    fun bootstrapFailureExitsSafelyRatherThanOpeningOldMenus() {
        val bootstrap = protectedMomentRouteSource()
        val failure = bootstrap.substringAfter("} catch (cancellation: CancellationException) {")

        assertTrue(failure.contains("navigateBackToHome()"))
        assertFalse(failure.contains("adaptiveMoment("))
        assertFalse(failure.contains("ReadingReset"))
        assertFalse(failure.contains("randomRecoveryGame"))
        // Structured cancellation still propagates.
        assertTrue(bootstrap.contains("throw cancellation"))
    }

    /** A legacy non-game step must never restart an obsolete intervention. */
    @Test
    fun onlyAGameStepMayBeResumedInsideAProtectedMoment() {
        val router = navHostSource().substringAfter("fun routeProtectedMomentInternal(")
            .substringBefore("fun routeAdaptiveInternal(")

        assertTrue(router.contains("request.kind != AdaptiveRouteKind.Game"))
        assertTrue(router.contains("return false"))
        assertFalse(router.contains("AdaptiveRouteKind.Reading"))
        assertFalse(router.contains("AdaptiveRouteKind.MomentPlan"))
    }

    // ---------- J. APP-004 DORMANCY ----------

    @Test
    fun theProtectedMomentHasNoAlternativeInterventionCaller() {
        val bootstrap = protectedMomentRouteSource()

        listOf(
            "AdaptiveSupportAlternativeCoordinator",
            "prepareAlternativeChoice",
            "chooseAnother",
            "showOtherOptions",
        ).forEach { absent ->
            assertFalse("protected route must not call: $absent", bootstrap.contains(absent))
        }
    }

    // ---------- K. APP-001A PRESERVATION ----------

    @Test
    fun theFixedNinetySecondTimingContractIsUnchanged() {
        val timing = source(
            "app/src/main/java/com/impulsive/app/backend/domain/model/adaptive/" +
                "AdaptiveSupportCycleTiming.kt",
        )

        assertTrue(timing.contains("TotalDurationMillis: Long = 90_000L"))
        assertTrue(timing.contains("SettlingStartsAtRemainingMillis: Long = 45_000L"))
        assertTrue(timing.contains("MomentPlanStartsAtRemainingMillis: Long = 20_000L"))
    }

    // ---------- helpers ----------

    private fun protectedRequest(
        sourcePackageName: String = "com.example.app",
    ) = BlockRequest(
        sourcePackageName = sourcePackageName,
        sourceLabel = "Example",
        detectedAtMillis = 1_000L,
        launchTarget = BlockLaunchTarget.ProtectedMoment,
        adaptiveDecisionId = "decision-1",
    )

    private fun protectedBridgeSource(): String = overlaySource()
        .substringAfter("private fun createProtectedMomentBridge(")
        .substringBefore("private fun animationsEnabled()")

    private fun protectedMomentRouteSource(): String = navHostSource()
        .substringAfter("route = AppRoutes.ProtectedMoment,")
        .substringBefore("route = AppRoutes.AdaptiveExplanation,")

    private fun overlaySource(): String = source(
        "app/src/main/java/com/impulsive/app/backend/service/protection/" +
            "ProtectionInterruptionOverlay.kt",
    )

    private fun protectionBridgeSource(): String = source(
        "app/src/main/java/com/impulsive/app/backend/session/adaptive/" +
            "AdaptivePhase5Integration.kt",
    )

    private fun recommendationPolicySource(): String = source(
        "app/src/main/java/com/impulsive/app/backend/domain/engine/adaptive/" +
            "AdaptiveRecommendationPolicy.kt",
    )

    private fun navHostSource(): String = source(
        "app/src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    )

    private fun source(path: String): String {
        val file = listOf(File(path), File("../$path")).firstOrNull(File::exists)
            ?: error("Source not found: $path")
        return file.readText()
    }
}
