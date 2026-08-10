package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePhaseThreeProductionConnectionTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val safeBrowseUiState = File(root, "frontend/screens/safebrowse/SafeBrowseUiState.kt").readText()
    private val safeBrowseRoute = File(root, "frontend/screens/safebrowse/SafeBrowseRoute.kt").readText()
    private val safeBrowseBrowserScreen =
        File(root, "frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun safeBrowseBrowserRouteExists() {
        assertTrue(navHost.contains("const val SafeBrowseBrowser ="))
        assertTrue(navHost.contains("composable(AppRoutes.SafeBrowseBrowser)"))
    }

    @Test
    fun productionDestinationNoLongerUsesSetupPendingState() {
        val destinationIndex = navHost.indexOf("composable(AppRoutes.SafeBrowse)")
        val nextDestinationIndex = navHost.indexOf("composable(AppRoutes.SafeBrowseBrowser)", destinationIndex)
        val destinationBlock = navHost.substring(destinationIndex, nextDestinationIndex)
        assertFalse(destinationBlock.contains("SafeBrowseSetupPendingUiState"))
        assertTrue(destinationBlock.contains("SafeBrowseRoute("))
    }

    @Test
    fun safeBrowseRouteIsUsedByProductionNavigation() {
        assertTrue(navHost.contains("SafeBrowseRoute("))
        assertTrue(navHost.contains("SafeBrowseBrowserRoute("))
    }

    @Test
    fun openBrowserEffectGatesNavigation() {
        assertTrue(safeBrowseRoute.contains("SafeBrowseAccessEffect.OpenBrowser"))
        assertTrue(safeBrowseRoute.contains("onOpenBrowser()"))
        assertTrue(safeBrowseRoute.contains("requestOpenBrowser"))
    }

    @Test
    fun browserRouteSharesTheAuthoritativeAccessViewModel() {
        assertTrue(navHost.contains("val safeBrowseAccessViewModel: SafeBrowseAccessViewModel"))

        // Exactly one shared instance is created, and both destinations reference it by name.
        val creationCount = Regex("val safeBrowseAccessViewModel: SafeBrowseAccessViewModel")
            .findAll(navHost)
            .count()
        assertEquals(1, creationCount)

        val safeBrowseUsage = Regex("SafeBrowseRoute\\(\\s*accessViewModel = safeBrowseAccessViewModel")
        val browserUsage = Regex("SafeBrowseBrowserRoute\\(\\s*accessViewModel = safeBrowseAccessViewModel")
        assertTrue(safeBrowseUsage.containsMatchIn(navHost))
        assertTrue(browserUsage.containsMatchIn(navHost))
    }

    @Test
    fun browserRouteObservesLifecycleAndMetersUsage() {
        assertTrue(safeBrowseBrowserScreen.contains("LifecycleEventObserver"))
        assertTrue(safeBrowseBrowserScreen.contains("Lifecycle.Event.ON_START"))
        assertTrue(safeBrowseBrowserScreen.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(safeBrowseBrowserScreen.contains("accessViewModel.beginBrowserUsage()"))
        assertTrue(safeBrowseBrowserScreen.contains("accessViewModel.endBrowserUsage()"))
    }

    @Test
    fun directBrowserEntryChecksAccessBeforeRendering() {
        // The guard must be continuous, not a one-shot check on the first observed value --
        // the ledger's first emission is Loading while it resolves, so a one-shot guard would
        // mark itself "checked" on that Loading value and then ignore a genuine Locked value
        // that follows for an unauthorised entry.
        assertFalse(safeBrowseBrowserScreen.contains("hasCheckedInitialAccess"))
        assertTrue(safeBrowseBrowserScreen.contains("DomainAccessState.Loading"))
        assertTrue(safeBrowseBrowserScreen.contains("DomainAccessState.Locked"))
        assertTrue(safeBrowseBrowserScreen.contains("is DomainAccessState.Error"))
        assertTrue(safeBrowseBrowserScreen.contains("SafeBrowseAccessEffect.AccessExpired"))
    }

    @Test
    fun adControllerIsNotPresentInsideTheBrowserScreen() {
        listOf(
            "SafeBrowseRewardedAdController",
            "SafeBrowseConsentManager",
            "RewardedAd",
            "MobileAds",
        ).forEach { forbidden ->
            assertFalse("browser screen unexpectedly references $forbidden", safeBrowseBrowserScreen.contains(forbidden))
        }
    }

    @Test
    fun passPurchaseAvailabilityIsDrivenByTheRealSafeBrowsePassCatalogue() {
        // Gate D: Safe Browse Pass billing is now genuinely wired in -- purchase
        // availability is computed from BillingManager's own catalogue state, never
        // hard-coded to false.
        assertTrue(safeBrowseRoute.contains("passPurchaseAvailable = passPurchaseAvailable"))
        assertTrue(safeBrowseRoute.contains("SafeBrowsePassCatalogState"))
    }

    @Test
    fun safeBrowseBrowserScreenAndUiStateNeverContainBillingCode() {
        // The secured browser engine and the plain UI-state model must never touch
        // BillingClient directly -- only the Route layer (SafeBrowseRoute.kt) does, via the
        // shared BillingManager instance.
        listOf(safeBrowseBrowserScreen, safeBrowseUiState).forEach { source ->
            assertFalse(source.contains("BillingClient"))
            assertFalse(source.contains("BillingManager"))
        }
    }

    @Test
    fun safeBrowseRouteNeverConstructsItsOwnBillingClientOrBillingManager() {
        // Safe Browse Pass purchasing must go through the single app-wide BillingManager
        // instance threaded in as a parameter -- never a second one constructed here.
        assertFalse(safeBrowseRoute.contains("BillingClient"))
        assertFalse(safeBrowseRoute.contains("BillingManager("))
        assertTrue(safeBrowseRoute.contains("billingManager: BillingManager"))
    }

    @Test
    fun websiteProtectionRemainsSeparateFromTheLiveSafeBrowseFlow() {
        assertFalse(safeBrowseRoute.contains("WebsiteProtectionSetupState"))
        assertFalse(safeBrowseRoute.contains("ProtectionSetupViewModel"))
        assertFalse(safeBrowseBrowserScreen.contains("ProtectionSetupViewModel"))
    }

    @Test
    fun manifestPermissionCountIsUnchanged() {
        val count = Regex("<uses-permission\\b").findAll(manifest).count()
        assertEquals(9, count)
    }

    @Test
    fun adMobApplicationMetadataIsDeclaredWithoutARealProductionId() {
        assertTrue(manifest.contains("com.google.android.gms.ads.APPLICATION_ID"))
        assertTrue(manifest.contains("\${admobApplicationId}"))
        // The manifest itself never hard-codes a literal AdMob ID -- only the Gradle
        // placeholder, resolved per build type in app/build.gradle.kts.
        assertFalse(manifest.contains("ca-app-pub-"))
    }
}
