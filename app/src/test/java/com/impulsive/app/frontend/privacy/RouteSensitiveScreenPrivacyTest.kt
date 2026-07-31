package com.impulsive.app.frontend.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSensitiveScreenPrivacyTest {
    @Test
    fun privateRoutesRequireSecureWindow() {
        listOf(
            "moment_plan_editor?planId={planId}",
            "moment_plan_detail/{planId}",
            "moment_plan_rehearsal/{rehearsalId}",
            "adaptive_feedback/{decisionId}",
            "what_works_for_me",
            "adaptive_explanation/{decisionId}",
            "path_shift",
        ).forEach { route ->
            assertTrue("Expected private route: $route", PrivateScreenRoutePolicy.isPrivate(route))
        }
    }

    @Test
    fun publicRoutesDoNotRequireSecureWindow() {
        listOf(
            null,
            "level_one_reveal",
            "settings",
            "recovery_games",
            "adaptive_game/{decisionId}",
            "website_protection_plus",
            "dns_filter_gate",
        ).forEach { route ->
            assertFalse("Expected public route: $route", PrivateScreenRoutePolicy.isPrivate(route))
        }
    }

    @Test
    fun enteringPrivateRouteAppliesSecureFlag() {
        val window = FakeSecureWindow()
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)

        assertTrue(window.secure)
        assertTrue(window.changes == listOf(true))
    }

    @Test
    fun leavingPrivateRouteClearsOwnedSecureFlag() {
        val window = FakeSecureWindow()
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)
        controller.apply(protect = false)

        assertFalse(window.secure)
        assertTrue(window.changes == listOf(true, false))
    }

    @Test
    fun disablingAndReenablingPreferenceUpdatesProtection() {
        val window = FakeSecureWindow()
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)
        controller.apply(protect = false)
        controller.apply(protect = true)

        assertTrue(window.secure)
        assertTrue(window.changes == listOf(true, false, true))
    }

    @Test
    fun existingSecureFlagIsNeverClearedByThisController() {
        val window = FakeSecureWindow(secure = true)
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)
        controller.apply(protect = false)
        controller.release()

        assertTrue(window.secure)
        assertTrue(window.changes.isEmpty())
    }

    @Test
    fun releaseClearsOnlyFlagOwnedByController() {
        val window = FakeSecureWindow()
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)
        controller.release()
        controller.release()

        assertFalse(window.secure)
        assertTrue(window.changes == listOf(true, false))
    }

    @Test
    fun repeatedPrivateUpdatesDoNotToggleOrFlicker() {
        val window = FakeSecureWindow()
        val controller = RouteSensitiveScreenPrivacyController(window)

        controller.apply(protect = true)
        controller.apply(protect = true)
        controller.apply(protect = true)

        assertTrue(window.secure)
        assertTrue(window.changes == listOf(true))
    }

    private class FakeSecureWindow(
        secure: Boolean = false,
    ) : SecureWindowHandle {
        private var secureState = secure
        override val secure: Boolean
            get() = secureState
        val changes = mutableListOf<Boolean>()

        override fun setSecure(secure: Boolean) {
            secureState = secure
            changes += secure
        }
    }
}
