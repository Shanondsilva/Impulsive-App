package com.impulsive.app.frontend.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoverySettingsSourcePolicyTest {
    @Test
    fun settingsObservesUniqueUploadWorkWithoutTreatingSucceededAsBackedUp() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
            ).readText()

        assertTrue(
            source.contains(
                "getWorkInfosForUniqueWorkFlow",
            ),
        )

        assertTrue(
            source.contains(
                "CloudRecoveryUploadScheduler.UniqueWorkName",
            ),
        )

        assertTrue(source.contains("WorkInfo.State.RUNNING"))
        assertTrue(source.contains("WorkInfo.State.ENQUEUED"))
        assertTrue(source.contains("WorkInfo.State.BLOCKED"))

        assertFalse(
            Regex(
                """WorkInfo\.State\.SUCCEEDED[\s\S]{0,120}Backed up""",
            ).containsMatchIn(source),
        )
    }
}