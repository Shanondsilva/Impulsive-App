package com.impulsive.app.frontend.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalUiAccessibilitySettingsSourceTest {
    private val settings = File(
        "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
    ).readText()

    @Test
    fun longSupportDialogsUseBoundedScrollableBodyWithReachableAction() {
        val dialog = settings.section(
            "private fun ScrollableSettingsInfoDialog",
            "private fun PlusGroup",
        )

        assertTrue(
            "The data-storage explanation must use ScrollableSettingsInfoDialog",
            Regex(
                "ScrollableSettingsInfoDialog\\(\\s*" +
                    "title = \"How your data is stored\"",
            ).containsMatchIn(settings),
        )

        assertTrue(
            "The About explanation must use ScrollableSettingsInfoDialog",
            Regex(
                "ScrollableSettingsInfoDialog\\(\\s*" +
                    "title = \"About Impulsive\"",
            ).containsMatchIn(settings),
        )

        assertTrue(dialog.contains("Dialog("))
        assertTrue(dialog.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialog.contains(".heightIn(max = 560.dp)"))
        assertTrue(dialog.contains(".navigationBarsPadding()"))
        assertTrue(dialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(dialog.contains(".heightIn(min = 48.dp)"))
    }

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
