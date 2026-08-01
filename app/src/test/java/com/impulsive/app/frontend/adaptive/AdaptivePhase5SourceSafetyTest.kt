package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhase5SourceSafetyTest {
    private val root = File("src/main")
    private val nav = File(root, "java/com/impulsive/app/frontend/navigation/AppNavHost.kt").readText()
    private val screen = File(
        root,
        "java/com/impulsive/app/frontend/screens/adaptive/AdaptiveMomentScreens.kt",
    ).readText()
    private val overlay = File(
        root,
        "java/com/impulsive/app/backend/service/protection/ProtectionInterruptionOverlay.kt",
    ).readText()
    private val notification = File(
        root,
        "java/com/impulsive/app/backend/service/protection/ProtectionNotificationHelper.kt",
    ).readText()

    @Test
    fun adaptiveRoutesContainOnlyDecisionId() {
        assertTrue(nav.contains("""const val AdaptiveMoment = "adaptive_moment/{decisionId}""""))
        assertTrue(nav.contains("""const val MomentPlanRun = "moment_plan_run/{decisionId}""""))
        val declarations = nav.lines().filter {
            it.contains("adaptive_moment/") || it.contains("moment_plan_run/")
        }.joinToString()
        assertFalse(declarations.contains("sourcePackageName"))
        assertFalse(declarations.contains("sourceLabel"))
        assertFalse(declarations.contains("cue"))
        assertFalse(declarations.contains("urge"))
    }

    @Test
    fun firstAttemptIsMinimumFrictionNotEqualArms() {
        val first = screen.substring(
            screen.indexOf("private fun FirstAttemptPause"),
            screen.indexOf("private fun RepeatedChoice"),
        )
        assertTrue(first.contains("Take a short pause"))
        assertTrue(first.contains("Start pause"))
        assertFalse(first.contains("Pivot Game"))
        assertFalse(first.contains("Reset Reading"))
        assertFalse(first.contains("My Moment Plan"))
    }

    @Test
    fun repeatedChoiceLabelsSuggestionWithoutClaims() {
        assertTrue(screen.contains("Suggested for this moment"))
        assertTrue(screen.contains("Why this?"))
        assertFalse(screen.contains("recommended by AI", ignoreCase = true))
        assertFalse(screen.contains("most effective", ignoreCase = true))
        assertFalse(screen.contains("clinically matched", ignoreCase = true))
    }

    @Test
    fun overlayFallsBackToExistingChoicesWhenDecisionUnavailable() {
        assertTrue(overlay.contains("adaptiveDecisionId != null"))
        assertTrue(overlay.contains("Pivot by Game"))
        assertTrue(overlay.contains("Pivot by Reading"))
        assertTrue(overlay.contains("Continue deliberately"))
    }

    @Test
    fun adaptiveChoiceDoesNotCreateAnUnrestrictedContinueBypass() {
        val adaptiveChoice = overlay.substring(
            overlay.indexOf("// One explicit branch per interruption identity:"),
            overlay.indexOf("val footer ="),
        )
        assertFalse(adaptiveChoice.contains("Continue deliberately"))
        assertFalse(adaptiveChoice.contains("grantTemporaryAccessSafely"))
        assertFalse(adaptiveChoice.contains("startActivity(launchIntent)"))
    }

    @Test
    fun plusWebsiteOwnerStillCannotReceiveTheAppMonitorContinuePath() {
        val footer = overlay.substring(
            overlay.indexOf("val footer ="),
            overlay.indexOf("card.addView(\n            footer"),
        )
        assertTrue(footer.contains("owner == Owner.AppMonitor"))
        assertTrue(footer.contains("Continue deliberately"))
        assertFalse(footer.contains("owner == Owner.Vpn"))
    }

    @Test
    fun nonPlusTemporaryAccessCountdownAndCooldownRemainIntact() {
        assertTrue(overlay.contains("configureResetStatus("))
        assertTrue(overlay.contains("grantTemporaryAccessSafely("))
        assertTrue(overlay.contains("grantIfAvailable("))
        assertTrue(overlay.contains("TemporaryAccessGrantResult.OnCooldown"))
        assertTrue(overlay.contains("continueAction.isEnabled = false"))
    }

    @Test
    fun adaptiveNotificationUsesGenericCopyAndNoFullScreenIntent() {
        assertTrue(notification.contains("Choose a different direction"))
        assertTrue(notification.contains("createAdaptiveMomentIntent"))
        assertFalse(notification.contains("setFullScreenIntent"))
    }

    @Test
    fun phaseFiveDoesNotRecordFeedbackOrCompletion() {
        assertFalse(screen.contains("markCompleted"))
        assertFalse(screen.contains("updateFeedback"))
        assertFalse(nav.contains("markCompleted("))
        assertFalse(nav.contains("updateFeedback("))
    }
}
