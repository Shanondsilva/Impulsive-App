package com.impulsive.app.backend.session.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionSetupStatePolicyTest {
    @Test
    fun setupState_exposesOnlyHighestPriorityCondition() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                browserSelected = false,
                vpnPermissionGranted = false,
                competingVpnActive = true,
                privateDnsConflict = true,
            ),
        )
        assertEquals(WebsiteProtectionBlockingCondition.BrowserNotSelected, state.condition)
        assertEquals(WebsiteProtectionNextAction.SelectBrowser, state.nextAction)
    }

    @Test
    fun setupState_refreshesAfterSettingsReturn() {
        val producer = WebsiteProtectionSetupStateProducer()
        producer.refresh(snapshot(vpnPermissionGranted = false))
        assertEquals(
            WebsiteProtectionBlockingCondition.VpnPermissionRequired,
            producer.state.value.condition,
        )
        producer.refresh(snapshot(vpnPermissionGranted = true))
        assertEquals(WebsiteProtectionBlockingCondition.Ready, producer.state.value.condition)
    }

    @Test
    fun legacyEnableIntentWithStaleDisclosureRequiresReview() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = false,
            ),
        )

        assertEquals(
            WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            state.condition,
        )
        assertEquals(WebsiteProtectionNextAction.ReviewDisclosure, state.nextAction)
    }

    @Test
    fun browserSelectionOutranksDisclosureReview() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                browserSelected = false,
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = false,
            ),
        )

        assertEquals(WebsiteProtectionBlockingCondition.BrowserNotSelected, state.condition)
        assertEquals(WebsiteProtectionNextAction.SelectBrowser, state.nextAction)
    }

    @Test
    fun unsupportedBrowserOutranksDisclosureReview() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                selectedBrowserSupported = false,
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = false,
            ),
        )

        assertEquals(WebsiteProtectionBlockingCondition.UnsupportedBrowser, state.condition)
        assertEquals(WebsiteProtectionNextAction.ChooseSupportedBrowser, state.nextAction)
    }

    @Test
    fun disclosureReviewOutranksMissingVpnPermissionOnceBrowserIsValid() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                vpnPermissionGranted = false,
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = false,
            ),
        )

        assertEquals(
            WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            state.condition,
        )
        assertEquals(WebsiteProtectionNextAction.ReviewDisclosure, state.nextAction)
    }

    @Test
    fun acceptedDisclosureFallsThroughToVpnPermission() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                vpnPermissionGranted = false,
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = true,
            ),
        )

        assertEquals(
            WebsiteProtectionBlockingCondition.VpnPermissionRequired,
            state.condition,
        )
        assertEquals(WebsiteProtectionNextAction.RequestVpnPermission, state.nextAction)
    }

    @Test
    fun newUserWithoutEnableIntentIsNotForcedIntoDisclosureReview() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                websiteProtectionEnableIntent = false,
                websiteProtectionDisclosureAccepted = false,
            ),
        )

        assertNotEquals(
            WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            state.condition,
        )
        assertEquals(WebsiteProtectionBlockingCondition.Ready, state.condition)
        assertEquals(WebsiteProtectionNextAction.None, state.nextAction)
    }

    @Test
    fun enabledIntentWithCurrentDisclosureAndFullCapabilitiesIsReady() {
        val state = WebsiteProtectionSetupStatePolicy.evaluate(
            snapshot(
                websiteProtectionEnableIntent = true,
                websiteProtectionDisclosureAccepted = true,
            ),
        )

        assertEquals(WebsiteProtectionBlockingCondition.Ready, state.condition)
        assertEquals(WebsiteProtectionNextAction.None, state.nextAction)
    }

    @Test
    fun setupState_usesExistingCapabilityEvidenceOnly() {
        assertTrue(viewModelSource.contains("DnsFilterGate(application)"))
        assertTrue(viewModelSource.contains("InstalledAppScanner(application)"))
        assertTrue(viewModelSource.contains("ImpulsiveVpnController.consentIntent"))
        assertTrue(viewModelSource.contains("ProtectedAppCategory.BrowserSearch"))
    }

    @Test
    fun setupState_snapshotCarriesEnableIntentAndDisclosureSeparately() {
        val collapsed = viewModelSource.replace(Regex("\\s+"), " ")

        assertTrue(
            collapsed.contains(
                "websiteProtectionEnableIntent = state.value.websiteProtectionEnabled,",
            ),
        )
        assertTrue(
            collapsed.contains(
                "websiteProtectionDisclosureAccepted = " +
                    "state.value.websiteProtectionDisclosureAccepted,",
            ),
        )
    }

    private val viewModelSource: String
        get() = File(
            "src/main/java/com/impulsive/app/backend/session/protection/ProtectionSetupViewModel.kt",
        ).readText()

    private fun snapshot(
        browserSelected: Boolean = true,
        selectedBrowserSupported: Boolean = true,
        vpnPermissionGranted: Boolean = true,
        competingVpnActive: Boolean = false,
        privateDnsConflict: Boolean = false,
        websiteProtectionEnableIntent: Boolean = false,
        websiteProtectionDisclosureAccepted: Boolean = true,
    ) = WebsiteProtectionCapabilitySnapshot(
        capabilitiesLoaded = true,
        browserSelected = browserSelected,
        selectedBrowserSupported = selectedBrowserSupported,
        vpnPermissionGranted = vpnPermissionGranted,
        competingVpnActive = competingVpnActive,
        privateDnsConflict = privateDnsConflict,
        websiteProtectionEnableIntent = websiteProtectionEnableIntent,
        websiteProtectionDisclosureAccepted = websiteProtectionDisclosureAccepted,
    )
}
