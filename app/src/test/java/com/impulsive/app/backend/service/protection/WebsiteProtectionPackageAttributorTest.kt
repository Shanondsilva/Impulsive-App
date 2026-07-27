package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteProtectionPackageAttributorTest {
    @Test
    fun `exact owner equals current foreground protected browser is accepted`() {
        val decision = decide(
            exactAttribution = exact(Chrome),
            currentForegroundPackage = Chrome,
        )

        assertEquals(Chrome, decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.ExactOwnerMatchesCurrentForeground,
            decision.reason,
        )
    }

    @Test
    fun `exact owner equals fresh recently observed foreground browser is accepted`() {
        val decision = decide(
            exactAttribution = exact(Brave),
            currentForegroundPackage = "com.android.systemui",
            recentForegroundBrowser = recent(Brave),
        )

        assertEquals(Brave, decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.ExactOwnerMatchesRecentForeground,
            decision.reason,
        )
    }

    @Test
    fun `owner unavailable plus current protected browser is accepted`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.Unavailable,
            currentForegroundPackage = Brave,
        )

        assertEquals(Brave, decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.CurrentForegroundUsedBecauseOwnerUnavailable,
            decision.reason,
        )
    }

    @Test
    fun `owner unavailable plus fresh recent protected browser is accepted`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.Unavailable,
            currentForegroundPackage = "com.android.systemui",
            recentForegroundBrowser = recent(Brave),
        )

        assertEquals(Brave, decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.RecentForegroundUsedBecauseOwnerUnavailable,
            decision.reason,
        )
    }

    @Test
    fun `ambiguous owner plus fresh recent protected browser is accepted`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.Ambiguous,
            currentForegroundPackage = "com.android.systemui",
            recentForegroundBrowser = recent(Brave),
        )

        assertEquals(Brave, decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.RecentForegroundUsedBecauseOwnerAmbiguous,
            decision.reason,
        )
    }

    @Test
    fun `exact different selected browser from current is rejected`() {
        val decision = decide(
            exactAttribution = exact(Chrome),
            currentForegroundPackage = Brave,
            recentForegroundBrowser = recent(Chrome),
        )

        assertNull(decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.RejectedExactDifferentBrowser,
            decision.reason,
        )
    }

    @Test
    fun `background browser without current or recent match is rejected`() {
        val decision = decide(
            exactAttribution = exact(Chrome),
            currentForegroundPackage = "com.example.other",
            recentForegroundBrowser = null,
        )

        assertNull(decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.RejectedNoCurrentOrRecentBrowser,
            decision.reason,
        )
    }

    @Test
    fun `stale recent browser cannot be used as attribution fallback`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.Unavailable,
            currentForegroundPackage = "com.android.systemui",
            recentForegroundBrowser = RecentForegroundWebsiteBrowser(
                packageName = Brave,
                observedAtEpochMillis = Now -
                    RecentForegroundWebsiteBrowserFreshnessMillis - 1L,
            ),
        )

        assertNull(decision.packageName)
    }

    @Test
    fun `foreground browser outside VPN allowed set is rejected`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.Unavailable,
            currentForegroundPackage = Brave,
            vpnAllowedPackages = setOf(Chrome),
        )

        assertNull(decision.packageName)
    }

    @Test
    fun `exact non selected connection owner cannot be replaced by foreground browser`() {
        val decision = decide(
            exactAttribution = ExactWebsitePackageAttribution.NonSelectedOwner,
            currentForegroundPackage = Brave,
        )

        assertNull(decision.packageName)
        assertEquals(
            WebsiteIncidentAttributionReason.RejectedExactOwnerNotEligible,
            decision.reason,
        )
    }

    @Test
    fun `exact owner package selected from protected set`() {
        assertEquals(
            Chrome,
            selectAttributedWebsitePackage(
                ownerPackages = setOf(Chrome),
                selectedPackages = SelectedBrowsers,
            ),
        )
    }

    @Test
    fun `foreground package cannot override exact owner package selection`() {
        assertEquals(
            Chrome,
            selectAttributedWebsitePackage(
                ownerPackages = setOf(Chrome),
                selectedPackages = SelectedBrowsers,
            ),
        )
    }

    @Test
    fun `non selected uid package returns no attribution`() {
        assertNull(
            selectAttributedWebsitePackage(
                ownerPackages = setOf("com.instagram.android"),
                selectedPackages = setOf(Chrome),
            ),
        )
    }

    @Test
    fun `shared uid with multiple selected packages is ambiguous`() {
        assertNull(
            selectAttributedWebsitePackage(
                ownerPackages = SelectedBrowsers,
                selectedPackages = SelectedBrowsers,
            ),
        )
    }

    private fun decide(
        exactAttribution: ExactWebsitePackageAttribution,
        currentForegroundPackage: String?,
        recentForegroundBrowser: RecentForegroundWebsiteBrowser? = null,
        websiteProtectedPackages: Set<String> = SelectedBrowsers,
        vpnAllowedPackages: Set<String> = SelectedBrowsers,
    ): WebsiteIncidentAttributionDecision =
        decideWebsiteIncidentAttribution(
            exactAttribution = exactAttribution,
            currentForegroundPackage = currentForegroundPackage,
            recentForegroundBrowser = recentForegroundBrowser,
            websiteProtectedPackages = websiteProtectedPackages,
            vpnAllowedPackages = vpnAllowedPackages,
            nowEpochMillis = Now,
        )

    private fun exact(packageName: String) =
        ExactWebsitePackageAttribution.SelectedPackage(packageName)

    private fun recent(packageName: String) =
        RecentForegroundWebsiteBrowser(
            packageName = packageName,
            observedAtEpochMillis = Now - 1_000L,
        )

    private companion object {
        const val Now = 100_000L
        const val Chrome = "com.android.chrome"
        const val Brave = "com.brave.browser"
        val SelectedBrowsers = setOf(Chrome, Brave)
    }
}
