package com.impulsive.app.frontend.protectioncoach

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtectionCoachNavigationSourceTest {
    @Test
    fun routesUseOpaqueIdsAndNoSensitiveRouteFields() {
        val source = File(
            "../app/src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        ).readText()

        assertTrue(source.contains("suggested_setup"))
        assertTrue(source.contains("protection_coach"))
        assertTrue(source.contains("protection_coach_suggestion/{suggestionId}"))
        assertTrue(source.contains("protection_transition"))
        assertFalse(source.contains("protection_coach_suggestion/{package"))
        assertFalse(source.contains("protection_coach_suggestion/{domain"))
        assertFalse(source.contains("protection_coach_suggestion/{time"))
        assertFalse(source.contains("protection_coach_suggestion/{answer"))
    }

    @Test
    fun screensAreTimingOnlyAndKeepExplicitUserControl() {
        val source = File(
            "../app/src/main/java/com/impulsive/app/frontend/screens/protectioncoach/ProtectionCoachScreens.kt",
        ).readText()

        assertTrue(source.contains("protection_coach_timing_title"))
        assertTrue(source.contains("protection_coach_review_time"))
        assertTrue(source.contains("protection_coach_not_now"))
        assertTrue(source.contains("protection_coach_never"))
        assertFalse(source.contains("Use this setup"))
        assertFalse(source.contains("Website Protection"))
    }

    @Test
    fun settingsKeepsCoachWhileHomeHasNoCoachEntryPoint() {
        val settings = File(
            "../app/src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        ).readText()
        val home = File(
            "../app/src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt",
        ).readText()

        assertTrue(settings.contains("Protection Coach"))
        assertTrue(settings.contains("R.string.protection_coach_description"))
        assertFalse(home.contains("YOUR SUGGESTED SETUP"))
        assertFalse(home.contains("Review setup >"))
    }

    @Test
    fun permanentMonitorToggleIsRemovedFromVisibleCopy() {
        val settings = File(
            "../app/src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        ).readText()
        val onboarding = File(
            "../app/src/main/java/com/impulsive/app/frontend/screens/onboarding/ProtectionSetupOnboardingScreens.kt",
        ).readText()

        assertFalse(settings.contains("Protection monitor"))
        assertFalse(onboarding.contains("Protection monitor"))
        assertFalse(onboarding.contains("Switch("))
    }
}
