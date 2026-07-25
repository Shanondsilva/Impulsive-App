package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProtectionMonitorPolicyTest {
    private val selectedPackages = setOf("com.example.protected")

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
    fun `website protected package bypasses generic interception while website protection is enabled`() {
        assertTrue(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = "com.brave.browser",
                websiteProtectionEnabled = true,
                websiteProtectedPackages = setOf("com.brave.browser"),
            ),
        )
    }

    @Test
    fun `website protected package does not bypass generic interception when website protection is disabled`() {
        assertFalse(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = "com.brave.browser",
                websiteProtectionEnabled = false,
                websiteProtectedPackages = setOf("com.brave.browser"),
            ),
        )
    }

    @Test
    fun `unrelated foreground package does not bypass generic interception`() {
        assertFalse(
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = "com.example.other",
                websiteProtectionEnabled = true,
                websiteProtectedPackages = setOf("com.brave.browser"),
            ),
        )
    }
}
