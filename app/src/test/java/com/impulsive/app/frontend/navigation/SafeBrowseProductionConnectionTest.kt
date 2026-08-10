package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseProductionConnectionTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val homeScreen = File(root, "frontend/screens/dashboard/HomeScreen.kt").readText()
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val safeBrowseUiState = File(root, "frontend/screens/safebrowse/SafeBrowseUiState.kt").readText()
    private val safeBrowseScreen = File(root, "frontend/screens/safebrowse/SafeBrowseScreen.kt").readText()
    private val backendState = File(root, "backend/session/protection/WebsiteProtectionBackendState.kt").readText()
    private val viewModel = File(root, "backend/session/protection/ProtectionSetupViewModel.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private val safeBrowseDestinationBlock: String by lazy {
        val start = navHost.indexOf("composable(AppRoutes.SafeBrowse)")
        val end = navHost.indexOf("composable(AppRoutes.DnsFilterGate)", start)
        navHost.substring(start, end)
    }

    @Test
    fun momentPlanAndTipsCardsOccursBeforeSafeBrowseHomeCard() {
        val momentPlanIndex = homeScreen.indexOf("MomentPlanAndTipsCards(")
        val safeBrowseCardIndex = homeScreen.indexOf("SafeBrowseHomeCard(", momentPlanIndex)
        assertTrue(momentPlanIndex >= 0)
        assertTrue(safeBrowseCardIndex > momentPlanIndex)
    }

    @Test
    fun safeBrowseHomeCardOccursBeforeWebsiteProtectionStatusHomeCard() {
        val safeBrowseCardIndex = homeScreen.indexOf("SafeBrowseHomeCard(")
        val websiteStatusIndex = homeScreen.indexOf(
            "WebsiteProtectionStatusHomeCard(",
            safeBrowseCardIndex,
        )
        assertTrue(safeBrowseCardIndex >= 0)
        assertTrue(websiteStatusIndex > safeBrowseCardIndex)
    }

    @Test
    fun exactlyOneSafeBrowseRouteDeclarationExists() {
        val occurrences = Regex("""const val SafeBrowse = "safe_browse"""").findAll(navHost).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun browserProtectionRouteNoLongerExists() {
        assertFalse(navHost.contains("const val BrowserProtection"))
        assertFalse(navHost.contains("AppRoutes.BrowserProtection"))
    }

    @Test
    fun homeNavigatesToSafeBrowseWithLaunchSingleTop() {
        val callbackIndex = navHost.indexOf("onOpenSafeBrowse = dropUnlessResumed")
        assertTrue(callbackIndex >= 0)
        val callbackBlock = navHost.substring(callbackIndex, callbackIndex + 200)
        assertTrue(callbackBlock.contains("AppRoutes.SafeBrowse"))
        assertTrue(callbackBlock.contains("launchSingleTop = true"))
    }

    @Test
    fun productionDestinationNowUsesTheLiveSafeBrowseRoute() {
        // Phase 3 connects the authoritative access ledger; see
        // SafeBrowsePhaseThreeProductionConnectionTest for the full live-wiring contract.
        assertFalse(safeBrowseDestinationBlock.contains("SafeBrowseSetupPendingUiState"))
        assertTrue(safeBrowseDestinationBlock.contains("SafeBrowseRoute("))
    }

    @Test
    fun homeAndNavigationDoNotCollectBrowserProtectionUiState() {
        assertFalse(homeScreen.contains("browserProtectionUiState"))
        assertFalse(navHost.contains("browserProtectionUiState"))
    }

    @Test
    fun websiteProtectionBackendStateNoLongerDeclaresBrowserProtectionUiState() {
        assertFalse(backendState.contains("BrowserProtectionUiState"))
    }

    @Test
    fun protectionSetupViewModelNoLongerDeclaresRemovedSymbols() {
        assertFalse(viewModel.contains("browserProtectionUiState"))
        assertFalse(viewModel.contains("hasCheckedWebsiteSetup"))
    }

    @Test
    fun safeBrowseUiDoesNotReferenceWebsiteProtectionSymbols() {
        listOf(safeBrowseUiState, safeBrowseScreen, safeBrowseDestinationBlock).forEach { source ->
            assertFalse(source.contains("WebsiteProtectionNextAction"))
            assertFalse(source.contains("RequestVpnPermission"))
            assertFalse(source.contains("OpenVpnSettings"))
            assertFalse(source.contains("OpenPrivateDnsSettings"))
            assertFalse(source.contains("WebsiteProtectionApps"))
            assertFalse(source.contains("Choose browsers"))
        }
    }

    @Test
    fun allRequiredSafeBrowseTagStringsExist() {
        listOf(
            "home_safe_browse_card" to homeScreen,
            "safe_browse_back" to safeBrowseScreen,
            "safe_browse_heading" to safeBrowseScreen,
            "safe_browse_setup_pending" to safeBrowseScreen,
            "safe_browse_watch_ad" to safeBrowseScreen,
            "safe_browse_open_browser" to safeBrowseScreen,
            "safe_browse_retry" to safeBrowseScreen,
            "safe_browse_promise_card" to safeBrowseScreen,
            "safe_browse_pass" to safeBrowseScreen,
        ).forEach { (tag, source) ->
            assertTrue("missing tag $tag", source.contains("\"$tag\""))
        }
        // The placeholder SafeBrowseBrowserShell was replaced by the real
        // SafeBrowseBrowserScreen engine (see SafeBrowseWebViewSecuritySourceTest
        // for its "safe_browse_webview" and browser-control test tags).
    }

    @Test
    fun safeBrowseFilesContainAllRequiredStates() {
        listOf("SetupPending", "Locked", "Active", "Expired", "Error").forEach { stateName ->
            assertTrue("missing state $stateName", safeBrowseUiState.contains(stateName))
        }
    }

    @Test
    fun noProhibitedBackendImplementationInSafeBrowseFilesOrDestination() {
        listOf(safeBrowseUiState, safeBrowseScreen, safeBrowseDestinationBlock).forEach { source ->
            assertFalse(source.contains("WebView"))
            assertFalse(source.contains("AndroidView"))
            assertFalse(source.contains("loadUrl"))
            assertFalse(source.contains("CustomTabsIntent"))
            assertFalse(source.contains("com.google.android.gms.ads"))
            assertFalse(source.contains("RewardedAd.load"))
            assertFalse(source.contains("BillingClient"))
            assertFalse(source.contains("OutlinedTextField"))
        }
    }

    @Test
    fun internetPermissionCountIsUnchanged() {
        val internetPermissionCount = Regex("""android.permission.INTERNET""").findAll(manifest).count()
        assertTrue(internetPermissionCount <= 1)
    }
}
