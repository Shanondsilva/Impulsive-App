package com.impulsive.app.frontend

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V28VisibleCopyEncodingTest {

    private val projectRoot = File(System.getProperty("user.dir"))
    private val appRoot =
        if (projectRoot.name == "app") projectRoot else File(projectRoot, "app")

    @Test
    fun visibleSourceCopy_containsNoMojibakeCharacter() {
        val sourceRoot = File(appRoot, "src/main/java")
        val affectedFiles = listOf(
            File(
                sourceRoot,
                "com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt",
            ),
            File(
                sourceRoot,
                "com/impulsive/app/frontend/screens/journal/JournalScreens.kt",
            ),
            File(
                sourceRoot,
                "com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
            ),
        )

        affectedFiles.forEach { file ->
            val source = file.readText()
            assertFalse(
                "${file.name} still contains the corrupted Â character",
                source.contains("Â"),
            )
        }
    }

    @Test
    fun correctedCopy_isPresent() {
        val home = File(
            appRoot,
            "src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt",
        ).readText()
        val journal = File(
            appRoot,
            "src/main/java/com/impulsive/app/frontend/screens/journal/JournalScreens.kt",
        ).readText()
        val settings = File(
            appRoot,
            "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        ).readText()

        assertTrue(home.contains("Plus · from £4.99/month"))
        assertTrue(journal.contains("saves · notes, lists and reminders."))
        assertTrue(settings.contains(" · Screen privacy "))
    }
}
