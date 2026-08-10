package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionSetupProductionConnectionTest {
    private val navigation =
        File(
            "src/main/java/com/impulsive/app/" +
                "frontend/navigation/AppNavHost.kt",
        ).readText()

    private val screen =
        File(
            "src/main/java/com/impulsive/app/" +
                "frontend/screens/premium/" +
                "WebsiteProtectionPlusScreen.kt",
        ).readText()

    @Test
    fun productionRouteCollectsBackendWebsiteSetupState() {
        val destination =
            navigation
                .substringAfter("composable(AppRoutes.WebsiteProtectionPlus)")
                .substringBefore("composable(AppRoutes.WebsiteProtectionApps)")

        assertTrue(destination.contains("websiteSetupState"))
        assertTrue(destination.contains("collectAsStateWithLifecycle"))
        assertTrue(destination.contains("onRefreshWebsiteSetup"))
        assertTrue(destination.contains("onWebsiteSetupAction"))
    }

    @Test
    fun everyBackendActionHasAProductionOwner() {
        listOf(
            "SelectBrowser",
            "ChooseSupportedBrowser",
            "RequestVpnPermission",
            "OpenVpnSettings",
            "OpenPrivateDnsSettings",
            "RetryCapabilityCheck",
            "None",
        ).forEach { action ->
            assertTrue(navigation.contains(action))
        }
    }

    @Test
    fun screenRefreshesAfterReturningFromAndroidSettings() {
        assertTrue(screen.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(screen.contains("currentRefreshWebsiteSetup"))
        assertTrue(screen.contains("LifecycleEventObserver"))
    }

    @Test
    fun screenConsumesStateWithoutAddingAnotherManagementCard() {
        assertTrue(screen.contains("WebsiteProtectionSetupState"))
        assertTrue(screen.contains("toManagementPresentation"))

        assertTrue(
            screen.contains("setupState.nextAction") ||
                screen.contains(
                    "setupState" +
                        "\n" +
                        "                        .nextAction",
                ),
        )

        val cardCount =
            Regex("WebsiteProtectionManagementCard\\(")
                .findAll(screen)
                .count()

        /*
         * One function declaration and one production invocation.
         */
        assertTrue(cardCount == 2)
    }

    @Test
    fun noPollingOrExperimentalStylesWereIntroduced() {
        assertFalse(screen.contains("while (true)"))
        assertFalse(screen.contains("androidx.compose.foundation.style"))
        assertFalse(screen.contains("Modifier.styleable"))
    }
}
