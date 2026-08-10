package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAccessRemovalSourceTest {
    private val mainRoot = File("src/main")

    private fun source(path: String): String =
        File(mainRoot, path).readText()

    @Test
    fun temporaryAccessRuntimeEntrypointsAreRemoved() {
        val appMonitor = source(
            "java/com/impulsive/app/backend/service/protection/AppMonitorService.kt",
        )
        val overlay = source(
            "java/com/impulsive/app/backend/service/protection/ProtectionInterruptionOverlay.kt",
        )
        val blockScreen = source(
            "java/com/impulsive/app/frontend/screens/protection/ImpulsiveBlockScreen.kt",
        )
        val navHost = source(
            "java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        )

        listOf(appMonitor, overlay, blockScreen, navHost).forEach { text ->
            assertFalse(text.contains("Continue deliberately"))
            assertFalse(text.contains("OneMinuteAccess"))
            assertFalse(text.contains("oneMinuteAccess"))
            assertFalse(text.contains("TemporaryAccessGrantResult"))
            assertFalse(text.contains("block_screen_btn_one_minute"))
        }
    }

    @Test
    fun settingsAndResourcesNoLongerExposeTemporaryAccess() {
        val settings = source(
            "java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        )
        val strings = source("res/values/strings.xml")

        assertFalse(settings.contains("45-second access"))
        assertFalse(settings.contains("oneMinuteAccess"))
        assertFalse(strings.contains("block_screen_btn_one_minute"))
        assertFalse(strings.contains("Open for 45 seconds"))
    }

    @Test
    fun onlyLegacyCleanupReferencesRemovedPersistenceAndNotificationId() {
        val mainActivity = source("java/com/impulsive/app/MainActivity.kt")
        val notificationHelper = source(
            "java/com/impulsive/app/backend/service/protection/ProtectionNotificationHelper.kt",
        )
        val backupRules = source("res/xml/data_extraction_rules.xml")

        assertTrue(mainActivity.contains("\"one_minute_access\""))
        assertTrue(notificationHelper.contains("cancelLegacyTemporaryAccessNotification"))
        assertTrue(notificationHelper.contains("LegacyTemporaryAccessNotificationId = 4208"))
        assertFalse(notificationHelper.contains("showOneMinuteAccessCountdown"))
        assertFalse(notificationHelper.contains("cancelOneMinuteAccessCountdown"))
        assertFalse(backupRules.contains("one_minute_access"))
    }
}
