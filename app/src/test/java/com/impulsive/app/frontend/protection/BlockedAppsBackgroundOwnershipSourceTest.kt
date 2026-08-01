package com.impulsive.app.frontend.protection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedAppsBackgroundOwnershipSourceTest {
    private val content = source(
        "frontend/screens/protection/BlockedAppsSelectionContent.kt",
    )
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")
    private val focus = source("frontend/screens/focus/FocusScreen.kt")
    private val navHost = source("frontend/navigation/AppNavHost.kt")

    @Test
    fun exposesContainerColorParameterDefaultingToDestinationBackground() {
        assertTrue(
            content.contains("containerColor: Color = MaterialTheme.colorScheme.background"),
        )
        assertFalse(content.contains("containerColor: Color = MaterialTheme.colorScheme.surface"))
    }

    @Test
    fun outerRootIsAnOpaqueFullScreenBoxOwningTheBackground() {
        assertTrue(content.contains("Box("))
        val rootBox = content.substring(
            content.indexOf("Box("),
            content.indexOf("Column(", content.indexOf("Box(")),
        )
        assertTrue(rootBox.contains(".fillMaxSize()"))
        assertTrue(rootBox.contains(".background(containerColor)"))
    }

    @Test
    fun oldTransparentRootSizingCombinationIsRemoved() {
        assertFalse(content.contains("fillMaxHeight()"))
        assertFalse(content.contains("import androidx.compose.foundation.layout.fillMaxHeight"))
    }

    @Test
    fun infoDialogStillRendersOutsideTheMainVisualContainer() {
        assertTrue(content.contains("if (showAppsInfo) {"))
        assertTrue(content.contains("AlertDialog("))
        // The dialog block must start after the outer Box's own closing brace
        // (the Box body closes, then the Column inside it closes one line
        // above), not nested inside the background-owning container.
        assertTrue(
            content.contains("        }\n    }\n\n    if (showAppsInfo) {"),
        )
        assertTrue(content.indexOf("AlertDialog(") > content.indexOf("if (showAppsInfo) {"))
    }

    @Test
    fun settingsAndFocusSheetsPassTransparentContainerColor() {
        assertTrue(settings.contains("containerColor = Color.Transparent"))
        assertTrue(focus.contains("containerColor = Color.Transparent"))
    }

    @Test
    fun fullScreenNavHostDestinationsKeepTheOpaqueDefault() {
        val onboardingDestination = navHost.substring(
            navHost.indexOf("composable(OnboardingRoutes.ProtectionBlockedApps) {"),
            navHost.indexOf("composable(OnboardingRoutes.StartingPoint) {"),
        )
        val setupGuideDestination = navHost.substring(
            navHost.indexOf("composable(AppRoutes.ProtectionSetupGuideBlockedApps) {"),
            navHost.indexOf("composable(AppRoutes.HelpFaq) {"),
        )
        val websiteProtectionDestination = navHost.substring(
            navHost.indexOf("composable(AppRoutes.WebsiteProtectionApps) {"),
            navHost.indexOf("composable(AppRoutes.DnsFilterGate) {"),
        )

        listOf(onboardingDestination, setupGuideDestination, websiteProtectionDestination)
            .forEach { destination ->
                assertTrue(destination.contains("BlockedAppsSelectionContent("))
                assertFalse(destination.contains("containerColor"))
                assertFalse(destination.contains("Color.Transparent"))
            }
    }

    @Test
    fun implementationAvoidsPredictiveBackWorkarounds() {
        assertFalse(content.contains("BackHandler"))
        assertFalse(content.contains("PredictiveBackHandler"))
        assertFalse(content.contains("backProgress"))
        assertFalse(content.contains("BackEventCompat"))
        assertFalse(content.contains("delay("))
        assertFalse(content.contains("zIndex"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/impulsive/app/$relativePath").readText()
}
