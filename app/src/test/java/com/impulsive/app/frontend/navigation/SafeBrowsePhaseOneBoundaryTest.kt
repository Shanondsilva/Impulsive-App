package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePhaseOneBoundaryTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val homeScreen = File(root, "frontend/screens/dashboard/HomeScreen.kt").readText()
    private val safeBrowseScreen = File(root, "frontend/screens/safebrowse/SafeBrowseScreen.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val versionCatalog = File("../gradle/libs.versions.toml").let {
        if (it.exists()) it else File("gradle/libs.versions.toml")
    }.readText()

    private val safeBrowseDestinationBlock: String by lazy {
        val start = navHost.indexOf("composable(AppRoutes.SafeBrowse)")
        val end = navHost.indexOf("composable(AppRoutes.DnsFilterGate)", start)
        navHost.substring(start, end)
    }

    @Test
    fun productionDestinationNowUsesTheLiveSafeBrowseRoute() {
        // Phase 3 connects the authoritative access ledger: the production destination no
        // longer supplies the immutable SetupPending state. See
        // SafeBrowsePhaseThreeProductionConnectionTest for the live-wiring assertions.
        assertTrue(safeBrowseDestinationBlock.contains("SafeBrowseRoute("))
        assertFalse(safeBrowseDestinationBlock.contains("SafeBrowseSetupPendingUiState"))
    }

    @Test
    fun safeBrowseScreenContainsNoBrowserAdOrBillingImplementation() {
        listOf(
            "WebView",
            "AndroidView",
            "loadUrl",
            "CustomTabsIntent",
            "RewardedAd.load",
            "com.google.android.gms.ads",
            "BillingClient",
            "OutlinedTextField",
        ).forEach { forbidden ->
            assertFalse("SafeBrowseScreen unexpectedly contains $forbidden", safeBrowseScreen.contains(forbidden))
        }
    }

    @Test
    fun manifestPermissionCountIsUnchanged() {
        val count = Regex("<uses-permission\\b").findAll(manifest).count()
        assertEquals(9, count)
    }

    @Test
    fun noUnapprovedAdvertisementDependencyExists() {
        // Phase 3 legitimately adds Google's own Mobile Ads SDK for the optional rewarded
        // ad. Only a different, unapproved ad network would be a violation here.
        listOf("unity-ads", "applovin", "facebook-audience-network", "ironsource").forEach {
            assertFalse(versionCatalog.contains(it))
        }
    }

    @Test
    fun noNewBillingProductIdExistsForSafeBrowsePass() {
        assertFalse(navHost.contains("safe_browse_pass"))
        assertFalse(safeBrowseDestinationBlock.contains("BillingClient"))
        assertFalse(safeBrowseDestinationBlock.contains("BillingManager"))
    }

    @Test
    fun safeBrowseRemainsSeparateFromWebsiteProtection() {
        assertFalse(safeBrowseDestinationBlock.contains("WebsiteProtectionApps"))
        assertFalse(safeBrowseDestinationBlock.contains("DnsFilterGate"))
        assertFalse(safeBrowseDestinationBlock.contains("protectionSetupViewModel"))
    }

    @Test
    fun websiteProtectionStatusHomeCardStillExists() {
        assertTrue(homeScreen.contains("WebsiteProtectionStatusHomeCard("))
        assertTrue(homeScreen.contains("private fun WebsiteProtectionStatusHomeCard("))
    }
}
