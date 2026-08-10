package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * APP-015 (M2): the monitoring notification must state only what is operational.
 */
class ProtectionMonitoringNotificationPolicyTest {

    private fun mode(
        setupLoaded: Boolean = true,
        app: Boolean = false,
        website: Boolean = false,
    ) = resolveProtectionMonitoringNotificationMode(
        setupLoaded = setupLoaded,
        appProtectionOperational = app,
        websiteProtectionOperational = website,
    )

    @Test
    fun `setup not loaded is Checking`() {
        assertEquals(
            ProtectionMonitoringNotificationMode.Checking,
            mode(setupLoaded = false),
        )
    }

    @Test
    fun `setup not loaded stays Checking even when reasons look operational`() {
        // Nothing is known yet, so nothing may be claimed.
        assertEquals(
            ProtectionMonitoringNotificationMode.Checking,
            mode(setupLoaded = false, app = true, website = true),
        )
    }

    @Test
    fun `app only is AppProtection`() {
        assertEquals(
            ProtectionMonitoringNotificationMode.AppProtection,
            mode(app = true),
        )
    }

    @Test
    fun `website only is WebsiteProtection`() {
        assertEquals(
            ProtectionMonitoringNotificationMode.WebsiteProtection,
            mode(website = true),
        )
    }

    @Test
    fun `both operational is AppAndWebsiteProtection`() {
        assertEquals(
            ProtectionMonitoringNotificationMode.AppAndWebsiteProtection,
            mode(app = true, website = true),
        )
    }

    @Test
    fun `nothing operational is Checking rather than a protection claim`() {
        /*
         * The defect: this state used to render "Impulsive protection is on"
         * while Usage Access was revoked and nothing could be intercepted.
         */
        assertEquals(
            ProtectionMonitoringNotificationMode.Checking,
            mode(app = false, website = false),
        )
    }
}
