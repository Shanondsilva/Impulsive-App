package com.impulsive.app.frontend.pathshift

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathShiftUiSourceTest {
    private val screen = source("frontend/screens/pathshift/PathShiftScreen.kt")
    private val home = source("frontend/screens/dashboard/HomeScreen.kt")
    private val navigation = source("frontend/navigation/AppNavHost.kt")
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")

    @Test
    fun `route contains no personal data and is screen protected`() {
        assertTrue(navigation.contains("const val PathShift = \"path_shift\""))
        assertFalse(navigation.contains("path_shift/{"))
        assertTrue(
            source("frontend/privacy/RouteSensitiveScreenPrivacy.kt")
                .contains("\"path_shift\""),
        )
    }

    @Test
    fun `consumer copy is cautious and non causal`() {
        assertTrue(screen.contains("It is not a promise about what will happen."))
        assertTrue(screen.contains("It does not prove that the plan caused the result."))
        assertFalse(screen.contains("AI prediction"))
        assertFalse(screen.contains("risk score"))
        assertFalse(screen.contains("relapse forecast"))
        assertFalse(screen.contains("—"))
    }

    @Test
    fun `privacy explanation names used and unused inputs`() {
        assertTrue(screen.contains("Why this estimate?"))
        assertTrue(screen.contains("Protected source identity, URL, domain, package"))
        assertTrue(screen.contains("camera, microphone or location"))
        assertTrue(screen.contains("Report this estimate as unhelpful"))
    }

    @Test
    fun `future path requires affirmative consent and supports confirmed disable`() {
        assertTrue(settings.contains("Turn On Future Path"))
        assertTrue(settings.contains("Not Now"))
        assertTrue(settings.contains("No preselected consent").not())
        assertTrue(settings.contains("Turn Off Future Path"))
        assertTrue(settings.contains("pathShiftEnabled = true"))
        assertTrue(settings.contains("pathShiftEnabled = false"))
    }

    @Test
    fun `home omits PathShift while settings retain it`() {
        assertFalse(home.contains("HomeSupportFeatureCard("))
        assertFalse(home.contains("eyebrow = \"FUTURE PATH\""))
        assertFalse(home.contains("title = \"Your Current Path\""))
        assertTrue(settings.contains("title = \"Future Path\""))
        assertFalse(home.contains("private fun PathShiftHomeCard("))
        assertFalse(home.contains("secondaryContainer.copy(alpha = 0.66f)"))
        assertFalse(home.contains("PathShiftChart"))
    }

    @Test
    fun `existing character renderer is reused with reduced motion`() {
        assertTrue(screen.contains("MindCoreScene("))
        assertTrue(screen.contains("reducedMotion = character.reducedMotion"))
        assertFalse(screen.contains("painterResource"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
