package com.impulsive.app.backend.session.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionBackendStateTest {
    @Test
    fun setupPriorityIsDeterministicAndReturnsExactlyOneAction() {
        val base = WebsiteProtectionCapabilitySnapshot(
            capabilitiesLoaded = true,
            browserSelected = true,
            selectedBrowserSupported = true,
            vpnPermissionGranted = true,
            competingVpnActive = false,
            privateDnsConflict = false,
            websiteProtectionEnableIntent = false,
            websiteProtectionDisclosureAccepted = true,
        )
        val cases = listOf(
            base.copy(capabilitiesLoaded = false) to WebsiteProtectionBlockingCondition.ProtectionUnavailable,
            base.copy(browserSelected = false, vpnPermissionGranted = false) to
                WebsiteProtectionBlockingCondition.BrowserNotSelected,
            base.copy(selectedBrowserSupported = false, vpnPermissionGranted = false) to
                WebsiteProtectionBlockingCondition.UnsupportedBrowser,
            base.copy(
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = false,
                vpnPermissionGranted = false,
                competingVpnActive = true,
            ) to WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            base.copy(vpnPermissionGranted = false, competingVpnActive = true) to
                WebsiteProtectionBlockingCondition.VpnPermissionRequired,
            base.copy(competingVpnActive = true, privateDnsConflict = true) to
                WebsiteProtectionBlockingCondition.CompetingVpnActive,
            base.copy(privateDnsConflict = true) to WebsiteProtectionBlockingCondition.PrivateDnsConflict,
            base to WebsiteProtectionBlockingCondition.Ready,
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, WebsiteProtectionSetupStatePolicy.evaluate(input).condition)
        }
    }

    @Test
    fun blockedSiteStateContainsOneCoordinatorActionNoDomainAndNoDisableAction() {
        val state = BlockedSiteInterruptionState("decision")
        assertEquals(BlockedSitePrimaryAction.OpenCoordinatorRecommendation, state.primaryAction)
        assertEquals(BlockedSiteQuietFallback.DismissInterruption, state.quietFallback)
        val names = BlockedSiteInterruptionState::class.java.declaredFields.map { it.name }
        assertTrue(names.none { it.contains("domain", true) || it.contains("url", true) })
        assertTrue(BlockedSitePrimaryAction.entries.none { it.name.contains("disable", true) })
    }
}
