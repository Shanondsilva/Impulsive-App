package com.impulsive.app.backend.data.local.device

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundProtectionSettingsNavigatorSourceTest {
    private val source =
        File(
            "src/main/java/com/impulsive/app/backend/data/local/device/BackgroundProtectionSettingsNavigator.kt",
        ).readText()

    @Test
    fun `open always attempts App Info before battery optimization fallback`() {
        val openSource =
            source.substring(
                source.indexOf("fun open()"),
                source.indexOf("private fun safelyStart"),
            )

        val appDetails =
            openSource.indexOf("Settings.ACTION_APPLICATION_DETAILS_SETTINGS")
        val optimizationFallback =
            openSource.indexOf("Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS")

        assertFalse(openSource.contains("isAllowed()"))
        assertTrue(appDetails >= 0)
        assertTrue(optimizationFallback > appDetails)
        assertTrue(openSource.contains("Uri.parse(\"package:\${context.packageName}\")"))
    }

    @Test
    fun `already allowed result no longer exists`() {
        assertFalse(source.contains("AlreadyAllowed"))
    }
}