package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safe Browse Pass navigation wiring. Exactly one destination, reached from the Safe Browse
 * unlock screen's "View Safe Browse Pass" entry point using the single shared BillingManager
 * instance -- never a second one.
 */
class SafeBrowsePassNavigationTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val settingsScreen = File(root, "frontend/screens/settings/SettingsScreen.kt").readText()

    @Test
    fun safeBrowsePassRouteConstantExists() {
        assertTrue(navHost.contains("const val SafeBrowsePass ="))
    }

    @Test
    fun safeBrowsePassDestinationIsRegisteredExactlyOnce() {
        val occurrences = Regex("composable\\(AppRoutes\\.SafeBrowsePass\\)")
            .findAll(navHost)
            .count()
        assertEquals(1, occurrences)
    }

    @Test
    fun safeBrowsePassDestinationUsesDedicatedViewModelBackedBySharedBillingManager() {
        val start =
            navHost.indexOf(
                "composable(AppRoutes.SafeBrowsePass)",
            )

        val end =
            navHost.indexOf(
                "composable(AppRoutes.DnsFilterGate)",
                start,
            )

        assertTrue(
            start >= 0,
        )

        assertTrue(
            end > start,
        )

        val block =
            navHost.substring(
                start,
                end,
            )

        assertTrue(
            block.contains(
                "SafeBrowsePassViewModelFactory",
            ),
        )

        assertTrue(
            block.contains(
                "billingManager",
            ),
        )

        assertTrue(
            block.contains(
                "passViewModel = safeBrowsePassViewModel",
            ),
        )

        assertTrue(
            block.contains(
                "SafeBrowsePassRoute(",
            ),
        )

        assertFalse(
            block.contains(
                "accessViewModel = safeBrowseAccessViewModel",
            ),
        )

        assertFalse(
            block.contains(
                "billingManager = billingManager",
            ),
        )

        assertFalse(
            block.contains(
                "BillingManager(",
            ),
        )
    }

    @Test
    fun safeBrowseUnlockScreenNavigatesToSafeBrowsePass() {
        val start = navHost.indexOf("composable(AppRoutes.SafeBrowse)")
        val end = navHost.indexOf("composable(AppRoutes.SafeBrowseBrowser)", start)
        val block = navHost.substring(start, end)
        assertTrue(block.contains("onOpenSafeBrowsePass = {"))
        assertTrue(block.contains("navController.navigate(AppRoutes.SafeBrowsePass)"))
    }

    @Test
    fun settingsNoLongerNavigatesToSafeBrowsePass() {
        val settingsStart = navHost.indexOf("SettingsScreen(")
        val settingsEnd = navHost.indexOf("composable(AppRoutes.MomentPlanList)", settingsStart)
        val settingsBlock = navHost.substring(settingsStart, settingsEnd)

        assertFalse(settingsBlock.contains("onOpenSafeBrowsePass = {"))
        assertFalse(settingsBlock.contains("navController.navigate(AppRoutes.SafeBrowsePass)"))
        assertFalse(settingsScreen.contains("onOpenSafeBrowsePass: () -> Unit = {}"))
        assertFalse(settingsScreen.contains("title = \"Safe Browse Pass\""))
    }

    @Test
    fun safeBrowseEntryStillNavigatesToSafeBrowsePass() {
        val callbackIndex = navHost.indexOf("onOpenSafeBrowsePass = {", navHost.indexOf("SafeBrowseRoute("))
        val callbackBlock = navHost.substring(callbackIndex, callbackIndex + 200)
        assertTrue(callbackBlock.contains("navController.navigate(AppRoutes.SafeBrowsePass)"))
    }
}
