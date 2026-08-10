package com.impulsive.app.frontend.screens.premium

import com.impulsive.app.backend.session.protection.WebsiteProtectionBlockingCondition
import com.impulsive.app.backend.session.protection.WebsiteProtectionNextAction
import com.impulsive.app.backend.session.protection.WebsiteProtectionSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionSetupConsumptionTest {
    @Test
    fun browserNotSelectedUsesExistingChooseAppsAction() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.BrowserNotSelected,
                WebsiteProtectionNextAction.SelectBrowser,
            ).toManagementPresentation()

        assertEquals("Choose a browser", presentation.statusText)
        assertEquals("Choose a browser", presentation.chooseAppsLabel)
        assertNull(presentation.setupActionLabel)
    }

    @Test
    fun unsupportedBrowserUsesSupportedBrowserCopy() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.UnsupportedBrowser,
                WebsiteProtectionNextAction.ChooseSupportedBrowser,
            ).toManagementPresentation()

        assertEquals("Unsupported browser selected", presentation.statusText)
        assertEquals("Choose a supported browser", presentation.chooseAppsLabel)
        assertNull(presentation.setupActionLabel)
    }

    @Test
    fun vpnPermissionProvidesOneContinuationAction() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.VpnPermissionRequired,
                WebsiteProtectionNextAction.RequestVpnPermission,
            ).toManagementPresentation()

        assertEquals("VPN permission needed", presentation.statusText)
        assertEquals("Continue setup", presentation.setupActionLabel)
    }

    @Test
    fun competingVpnRoutesToVpnSettingsCopy() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.CompetingVpnActive,
                WebsiteProtectionNextAction.OpenVpnSettings,
            ).toManagementPresentation()

        assertEquals("Another VPN is active", presentation.statusText)
        assertEquals("Open VPN settings", presentation.setupActionLabel)
    }

    @Test
    fun privateDnsConflictRoutesToPrivateDnsSettingsCopy() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.PrivateDnsConflict,
                WebsiteProtectionNextAction.OpenPrivateDnsSettings,
            ).toManagementPresentation()

        assertEquals("Private DNS needs attention", presentation.statusText)
        assertEquals("Open Private DNS settings", presentation.setupActionLabel)
    }

    @Test
    fun unavailableSetupOffersRetry() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.ProtectionUnavailable,
                WebsiteProtectionNextAction.RetryCapabilityCheck,
            ).toManagementPresentation()

        assertEquals("Setup check unavailable", presentation.statusText)
        assertEquals("Check again", presentation.setupActionLabel)
    }

    @Test
    fun readyStateDoesNotReplaceOperationalStatus() {
        val presentation =
            state(
                WebsiteProtectionBlockingCondition.Ready,
                WebsiteProtectionNextAction.None,
            ).toManagementPresentation()

        assertNull(presentation.statusText)
        assertNull(presentation.bodyText)
        assertNull(presentation.setupActionLabel)
    }

    @Test
    fun disclosureReviewRequiredRoutesBackToTheDisclosure() {
        val presentation = disclosureReviewState().toManagementPresentation()

        assertEquals("Review required", presentation.statusText)
        assertEquals(
            "Website Protection is paused until you review and accept the current " +
                "DNS handling disclosure.",
            presentation.bodyText,
        )
        assertEquals("Review disclosure", presentation.setupActionLabel)
    }

    @Test
    fun enabledIntentWithDisclosureReviewShowsReviewAndTurnOff() {
        val plan = websiteProtectionActionPlan(
            setupState = disclosureReviewState(),
            enabledIntent = true,
        )

        assertTrue(plan.showSetupAction)
        assertTrue(plan.showTurnOff)
        assertFalse(plan.showTurnOn)
    }

    @Test
    fun enabledIntentWithBrowserNotSelectedKeepsTurnOffWithoutDuplicateBrowserAction() {
        val plan = websiteProtectionActionPlan(
            setupState = state(
                WebsiteProtectionBlockingCondition.BrowserNotSelected,
                WebsiteProtectionNextAction.SelectBrowser,
            ),
            enabledIntent = true,
        )

        assertFalse(plan.showSetupAction)
        assertTrue(plan.showTurnOff)
        assertFalse(plan.showTurnOn)
    }

    @Test
    fun enabledIntentWithVpnPermissionRequiredKeepsTurnOffReachable() {
        val plan = websiteProtectionActionPlan(
            setupState = state(
                WebsiteProtectionBlockingCondition.VpnPermissionRequired,
                WebsiteProtectionNextAction.RequestVpnPermission,
            ),
            enabledIntent = true,
        )

        assertTrue(plan.showSetupAction)
        assertTrue(plan.showTurnOff)
        assertFalse(plan.showTurnOn)
    }

    @Test
    fun enabledIntentWhenReadyShowsTurnOffOnly() {
        val plan = websiteProtectionActionPlan(
            setupState = readyState(),
            enabledIntent = true,
        )

        assertFalse(plan.showSetupAction)
        assertTrue(plan.showTurnOff)
        assertFalse(plan.showTurnOn)
    }

    @Test
    fun disabledIntentWithBrowserNotSelectedShowsChooseAppsOnly() {
        val plan = websiteProtectionActionPlan(
            setupState = state(
                WebsiteProtectionBlockingCondition.BrowserNotSelected,
                WebsiteProtectionNextAction.SelectBrowser,
            ),
            enabledIntent = false,
        )

        assertFalse(plan.showSetupAction)
        assertFalse(plan.showTurnOff)
        assertFalse(plan.showTurnOn)
    }

    @Test
    fun disabledIntentWhenReadyShowsTurnOn() {
        val plan = websiteProtectionActionPlan(
            setupState = readyState(),
            enabledIntent = false,
        )

        assertFalse(plan.showSetupAction)
        assertFalse(plan.showTurnOff)
        assertTrue(plan.showTurnOn)
    }

    private fun disclosureReviewState(): WebsiteProtectionSetupState =
        state(
            WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            WebsiteProtectionNextAction.ReviewDisclosure,
        )

    private fun readyState(): WebsiteProtectionSetupState =
        state(
            WebsiteProtectionBlockingCondition.Ready,
            WebsiteProtectionNextAction.None,
        )

    private fun state(
        condition: WebsiteProtectionBlockingCondition,
        action: WebsiteProtectionNextAction,
    ): WebsiteProtectionSetupState =
        WebsiteProtectionSetupState(
            condition = condition,
            nextAction = action,
        )
}
