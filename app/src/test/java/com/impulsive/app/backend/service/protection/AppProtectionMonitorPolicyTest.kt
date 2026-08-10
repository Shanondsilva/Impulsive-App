package com.impulsive.app.backend.service.protection

import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.protection.WebsiteProtectionDisclosurePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProtectionMonitorPolicyTest {
    private val selectedPackages = setOf("com.example.protected")
    private val browserPackage = "com.brave.browser"

    @Test
    fun `monitoring requires enabled selected apps and Usage Access`() {
        assertTrue(
            shouldMonitorProtectedApps(
                appProtectionEnabled = true,
                selectedPackages = selectedPackages,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun `monitoring is disabled when any required input is missing`() {
        assertFalse(shouldMonitorProtectedApps(false, selectedPackages, true))
        assertFalse(shouldMonitorProtectedApps(true, emptySet(), true))
        assertFalse(shouldMonitorProtectedApps(true, selectedPackages, false))
    }

    @Test
    fun `overlay permission is not part of monitoring eligibility`() {
        assertTrue(shouldMonitorProtectedApps(true, selectedPackages, true))
    }

    @Test
    fun `website protection can recover service without app monitoring eligibility`() {
        assertTrue(
            shouldRecoverProtectionService(
                appProtectionEnabled = false,
                selectedPackages = emptySet(),
                usageAccessGranted = false,
                websiteProtectionEnabled = true,
            ),
        )
    }

    @Test
    fun `service recovery is not requested for app monitoring without Usage Access`() {
        assertFalse(
            shouldRecoverProtectionService(
                appProtectionEnabled = true,
                selectedPackages = selectedPackages,
                usageAccessGranted = false,
                websiteProtectionEnabled = false,
            ),
        )
    }

    @Test
    fun `managed browser bypasses generic interception even without website incident`() {
        assertTrue(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = browserPackage,
                websiteProtectionEnabled = true,
                websiteProtectedPackages = setOf(browserPackage),
            ),
        )
    }

    @Test
    fun `non browser selected app remains eligible for generic app protection`() {
        assertFalse(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = "com.example.protected",
                websiteProtectionEnabled = true,
                websiteProtectedPackages = setOf(browserPackage),
            ),
        )
        assertTrue(
            shouldMonitorProtectedApps(
                appProtectionEnabled = true,
                selectedPackages = selectedPackages,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun `website browser bypass is disabled when Website Protection is off`() {
        assertFalse(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = browserPackage,
                websiteProtectionEnabled = false,
                websiteProtectedPackages = setOf(browserPackage),
            ),
        )
    }

    @Test
    fun `confirmed website fallback remains eligible without activity lease input`() {
        assertTrue(
            eligibleWebsiteFallback(),
        )
    }

    @Test
    fun `website fallback ends for each explicit eligibility condition`() {
        assertFalse(eligibleWebsiteFallback(incidentMatches = false))
        assertFalse(eligibleWebsiteFallback(websiteProtectionEnabled = false))
        assertFalse(eligibleWebsiteFallback(sameBrowserForeground = false))
        assertFalse(eligibleWebsiteFallback(browserIsWebsiteProtected = false))
        assertFalse(eligibleWebsiteFallback(protectionPaused = true))
        assertTrue(
            eligibleWebsiteFallback(
                protectionPaused = true,
                websiteProtectionAlwaysOn = true,
            ),
        )
        assertFalse(eligibleWebsiteFallback(overlayShowing = true))
        assertFalse(eligibleWebsiteFallback(terminatingActionSelected = true))
    }

    @Test
    fun `raw enabled with version 0 consent yields runtime disabled`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = true,
                websiteProtectionDisclosureConsentVersion = 0,
            )

        assertFalse(state.websiteProtectionRuntimeEnabled)
    }

    @Test
    fun `raw enabled with current disclosure consent yields runtime enabled`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = true,
                websiteProtectionDisclosureConsentVersion =
                    WebsiteProtectionDisclosurePolicy.CurrentVersion,
            )

        assertTrue(state.websiteProtectionRuntimeEnabled)
    }

    @Test
    fun `App Protection monitoring eligibility is independent of Website Protection disclosure`() {
        assertTrue(
            shouldMonitorProtectedApps(
                appProtectionEnabled = true,
                selectedPackages = selectedPackages,
                usageAccessGranted = true,
            ),
        )

        val runtimeDisabledWebsiteProtection =
            ProtectionSetupState(
                websiteProtectionEnabled = true,
                websiteProtectionDisclosureConsentVersion = 0,
            )

        assertFalse(runtimeDisabledWebsiteProtection.websiteProtectionRuntimeEnabled)
        assertTrue(
            shouldMonitorProtectedApps(
                appProtectionEnabled = true,
                selectedPackages = selectedPackages,
                usageAccessGranted = true,
            ),
        )
    }

    private fun eligibleWebsiteFallback(
        incidentMatches: Boolean = true,
        websiteProtectionEnabled: Boolean = true,
        sameBrowserForeground: Boolean = true,
        browserIsWebsiteProtected: Boolean = true,
        protectionPaused: Boolean = false,
        websiteProtectionAlwaysOn: Boolean = false,
        overlayShowing: Boolean = false,
        terminatingActionSelected: Boolean = false,
    ): Boolean =
        isWebsiteFallbackIncidentEligible(
            incidentMatches = incidentMatches,
            websiteProtectionEnabled = websiteProtectionEnabled,
            sameBrowserForeground = sameBrowserForeground,
            browserIsWebsiteProtected = browserIsWebsiteProtected,
            protectionPaused = protectionPaused,
            websiteProtectionAlwaysOn = websiteProtectionAlwaysOn,
            overlayShowing = overlayShowing,
            terminatingActionSelected = terminatingActionSelected,
        )
}
