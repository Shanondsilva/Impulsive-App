package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarStepHistoryProductionConnectionTest {
    private val navigation =
        File(
            "src/main/java/com/impulsive/app/" +
                "frontend/navigation/AppNavHost.kt",
        ).readText()

    private val settings =
        File(
            "src/main/java/com/impulsive/app/" +
                "frontend/screens/settings/" +
                "SettingsScreen.kt",
        ).readText()

    @Test
    fun privacyRouteInstantiatesTheHistoryViewModel() {
        val destination =
            navigation
                .substringAfter("AppRoutes.PersonalSupportPrivacy")
                .substringBefore("AppRoutes.Tips")

        assertTrue(destination.contains("FamiliarStepHistoryViewModel"))
        assertTrue(destination.contains("familiarStepHistoryViewModel"))
        assertTrue(destination.contains("PersonalSupportPrivacyAndDataScreen"))
    }

    @Test
    fun productionScreenCollectsTheHistoryState() {
        val screen =
            settings
                .substringAfter("fun PersonalSupportPrivacyAndDataScreen(")
                .substringBefore("private fun PersonalSupportSubScreen(")

        assertTrue(screen.contains("FamiliarStepHistoryViewModel"))
        assertTrue(screen.contains("collectAsStateWithLifecycle"))
        assertTrue(screen.contains("familiarStepHistoryState"))
        assertTrue(screen.contains("familiarStepHistorySummary"))

        assertTrue(
            screen.contains(
                "history" +
                    "\n" +
                    "                .items" +
                    "\n" +
                    "                .size",
            ) ||
                screen.contains(".history.items.size"),
        )
    }

    @Test
    fun historyRefreshesAfterExistingResetOwnerSucceeds() {
        val screen =
            settings
                .substringAfter("fun PersonalSupportPrivacyAndDataScreen(")
                .substringBefore("private fun PersonalSupportSubScreen(")

        assertTrue(screen.contains("controlsState"))
        assertTrue(screen.contains("completionMessage"))
        assertTrue(screen.contains("familiarStepHistoryViewModel"))

        assertTrue(
            screen.contains(".refresh()") ||
                screen.contains(".refresh("),
        )

        /*
         * The screen must not run the adaptive reset a second time through
         * FamiliarStepHistoryViewModel.clearHistory().
         */
        assertFalse(
            screen.contains(
                "familiarStepHistoryViewModel" +
                    ".clearHistory()",
            ),
        )
    }

    @Test
    fun integrationUsesTheExistingHistoryRowOnly() {
        val screen =
            settings
                .substringAfter("fun PersonalSupportPrivacyAndDataScreen(")
                .substringBefore("private fun PersonalSupportSubScreen(")

        val historyTitleCount =
            Regex("\"Personal support history\"")
                .findAll(screen)
                .count()

        /*
         * One retention-dialog title and one existing SettingsRow title are
         * expected. No additional Familiar Step section is introduced.
         */
        assertTrue(historyTitleCount == 2)

        assertFalse(navigation.contains("FamiliarStepHistoryRoute"))
        assertFalse(navigation.contains("AppRoutes.FamiliarStepHistory"))
        assertFalse(settings.contains("FamiliarStepHistoryScreen"))
    }

    @Test
    fun noExperimentalStylesOrPollingWereAdded() {
        val screen =
            settings
                .substringAfter("fun PersonalSupportPrivacyAndDataScreen(")
                .substringBefore("private fun PersonalSupportSubScreen(")

        assertFalse(screen.contains("while (true)"))
        assertFalse(screen.contains("androidx.compose.foundation.style"))
        assertFalse(screen.contains("Modifier.styleable"))
    }
}
