package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionDisclosurePolicyTest {

    @Test
    fun `version 0 is not current`() {
        assertFalse(
            WebsiteProtectionDisclosurePolicy.isCurrent(0),
        )
    }

    @Test
    fun `negative version is not current`() {
        assertFalse(
            WebsiteProtectionDisclosurePolicy.isCurrent(-1),
        )
    }

    @Test
    fun `CurrentVersion is current`() {
        assertTrue(
            WebsiteProtectionDisclosurePolicy.isCurrent(
                WebsiteProtectionDisclosurePolicy.CurrentVersion,
            ),
        )
    }

    @Test
    fun `versions greater than CurrentVersion remain current`() {
        assertTrue(
            WebsiteProtectionDisclosurePolicy.isCurrent(
                WebsiteProtectionDisclosurePolicy.CurrentVersion + 1,
            ),
        )
    }

    @Test
    fun `enabled state with version 0 has disclosure not accepted and runtime disabled`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = true,
                websiteProtectionDisclosureConsentVersion = 0,
            )

        assertFalse(state.websiteProtectionDisclosureAccepted)
        assertFalse(state.websiteProtectionRuntimeEnabled)
        assertTrue(state.websiteProtectionDisclosureReviewRequired)
    }

    @Test
    fun `enabled state with current version has disclosure accepted and runtime enabled`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = true,
                websiteProtectionDisclosureConsentVersion =
                    WebsiteProtectionDisclosurePolicy.CurrentVersion,
            )

        assertTrue(state.websiteProtectionDisclosureAccepted)
        assertTrue(state.websiteProtectionRuntimeEnabled)
        assertFalse(state.websiteProtectionDisclosureReviewRequired)
    }

    @Test
    fun `disabled state with current version has disclosure accepted but runtime disabled`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = false,
                websiteProtectionDisclosureConsentVersion =
                    WebsiteProtectionDisclosurePolicy.CurrentVersion,
            )

        assertTrue(state.websiteProtectionDisclosureAccepted)
        assertFalse(state.websiteProtectionRuntimeEnabled)
        assertFalse(state.websiteProtectionDisclosureReviewRequired)
    }

    @Test
    fun `default state does not consent to Website Protection`() {
        val state = ProtectionSetupState()

        assertFalse(state.websiteProtectionDisclosureAccepted)
        assertFalse(state.websiteProtectionRuntimeEnabled)
        assertFalse(state.websiteProtectionDisclosureReviewRequired)
    }

    @Test
    fun `disabled state with stale disclosure does not require review`() {
        val state =
            ProtectionSetupState(
                websiteProtectionEnabled = false,
                websiteProtectionDisclosureConsentVersion = 0,
            )

        assertFalse(state.websiteProtectionDisclosureReviewRequired)
        assertFalse(state.websiteProtectionRuntimeEnabled)
    }
}
