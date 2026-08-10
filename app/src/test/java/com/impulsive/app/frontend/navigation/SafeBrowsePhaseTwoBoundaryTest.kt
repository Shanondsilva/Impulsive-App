package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePhaseTwoBoundaryTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val safeBrowseUiState = File(root, "frontend/screens/safebrowse/SafeBrowseUiState.kt").readText()
    private val safeBrowseBrowserScreen =
        File(root, "frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val versionCatalog = File("gradle/libs.versions.toml").let {
        if (it.exists()) it else File("../gradle/libs.versions.toml")
    }.readText()

    private val engineFiles = listOf(
        File(root, "backend/domain/model/safebrowse/SafeBrowseSearchPolicy.kt"),
        File(root, "backend/session/safebrowse/SafeBrowseBrowserModels.kt"),
        File(root, "backend/session/safebrowse/SafeBrowseBrowserViewModel.kt"),
        File(root, "frontend/screens/safebrowse/SafeBrowseWebView.kt"),
        File(root, "frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt"),
    )

    @Test
    fun appNavHostNowConnectsTheLiveSafeBrowseRoute() {
        // Phase 3 connects the authoritative access ledger; see
        // SafeBrowsePhaseThreeProductionConnectionTest for the full live-wiring contract.
        val destinationIndex = navHost.indexOf("composable(AppRoutes.SafeBrowse)")
        assertTrue(destinationIndex >= 0)
        val nextDestinationIndex = navHost.indexOf("composable(AppRoutes.SafeBrowseBrowser)", destinationIndex)
        val destinationBlock = navHost.substring(destinationIndex, nextDestinationIndex)
        assertFalse(destinationBlock.contains("SafeBrowseSetupPendingUiState"))
        assertTrue(navHost.contains("AppRoutes.SafeBrowseBrowser"))
        assertTrue(navHost.contains("SafeBrowseBrowserRoute("))
    }

    @Test
    fun safeBrowseUiStateStillDisablesEveryBackendControl() {
        assertTrue(safeBrowseUiState.contains("browserOpeningAvailable ="))
        assertTrue(safeBrowseUiState.contains("rewardedUnlockAvailable ="))
        assertTrue(safeBrowseUiState.contains("passPurchaseAvailable ="))

        val setupPendingIndex = safeBrowseUiState.indexOf("SafeBrowseSetupPendingUiState")
        assertTrue(setupPendingIndex >= 0)
        val closingIndex = safeBrowseUiState.indexOf(")", safeBrowseUiState.indexOf("passPriceLabel", setupPendingIndex))
        val block = safeBrowseUiState.substring(setupPendingIndex, closingIndex)
        assertTrue(block.contains("rewardedUnlockAvailable = false"))
        assertTrue(block.contains("browserOpeningAvailable = false"))
        assertTrue(block.contains("passPurchaseAvailable = false"))
    }

    @Test
    fun rewardedAdSdkIsConfinedToTheAdsPackageNotTheBrowserEngine() {
        // Phase 3 legitimately adds Google's Mobile Ads SDK for the optional rewarded ad
        // (see SafeBrowseRewardedAdSourceTest), but it must never appear inside the
        // Phase 2 browser-engine files themselves.
        engineFiles.forEach { file ->
            assertFalse(file.readText().contains("RewardedAd"))
            assertFalse(file.readText().contains("MobileAds"))
        }
    }

    @Test
    fun noNewBillingProductIdExists() {
        engineFiles.forEach { file ->
            assertFalse(file.readText().contains("BillingClient"))
            assertFalse(file.readText().contains("safe_browse_pass"))
        }
    }

    @Test
    fun noSafeBrowseTimerDataStoreKeyExists() {
        engineFiles.forEach { file ->
            val content = file.readText()
            assertFalse(content.contains("safe_browse_access_expires"))
            assertFalse(content.contains("DataStore"))
        }
    }

    @Test
    fun websiteProtectionRemainsSeparate() {
        engineFiles.forEach { file ->
            val content = file.readText()
            assertFalse(content.contains("WebsiteProtectionSetupState"))
            assertFalse(content.contains("ProtectionSetupViewModel"))
            assertFalse(content.contains("ImpulsiveVpnController"))
        }
    }

    @Test
    fun manifestInternetPermissionCountIsUnchanged() {
        val count = Regex("<uses-permission\\b").findAll(manifest).count()
        assertEquals(9, count)
    }

    @Test
    fun realEngineFilesExist() {
        engineFiles.forEach { file ->
            assertTrue("missing engine file: ${file.path}", file.exists())
        }
    }

    @Test
    fun browserScreenUsesThePhaseOneAndPhaseTwoContracts() {
        assertTrue(safeBrowseBrowserScreen.contains("SafeBrowseNavigationDecision"))
        assertTrue(safeBrowseBrowserScreen.contains("SafeBrowseSearchPolicy") || engineFiles.any {
            it.name == "SafeBrowseWebView.kt" && it.readText().contains("SafeBrowseSearchPolicy")
        })
        assertTrue(
            engineFiles.first { it.name == "SafeBrowseWebView.kt" }
                .readText()
                .contains("SafeBrowseWebViewClient"),
        )
    }

    @Test
    fun noArbitraryUrlFieldExists() {
        assertFalse(safeBrowseBrowserScreen.contains("KeyboardType.Uri"))
        assertFalse(safeBrowseBrowserScreen.contains("Address bar", ignoreCase = true))
        assertFalse(safeBrowseBrowserScreen.contains("Enter URL", ignoreCase = true))
    }

    @Test
    fun searchInputIsLabelledSearchTheWeb() {
        assertTrue(safeBrowseBrowserScreen.contains("Search the web"))
    }

    @Test
    fun downloadsAndPermissionsAreBlocked() {
        val webViewSource = File(root, "frontend/screens/safebrowse/SafeBrowseWebView.kt").readText()
        assertTrue(webViewSource.contains("onDownloadBlocked"))
        assertTrue(webViewSource.contains("onPermissionBlocked") || webViewSource.contains("request.deny()"))
        assertTrue(webViewSource.contains("setDownloadListener"))
    }
}
