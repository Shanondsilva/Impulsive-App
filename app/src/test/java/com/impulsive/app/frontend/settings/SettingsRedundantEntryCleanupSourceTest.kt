package com.impulsive.app.frontend.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRedundantEntryCleanupSourceTest {
    private val settings =
        source("frontend/screens/settings/SettingsScreen.kt")
    private val navHost =
        source("frontend/navigation/AppNavHost.kt")
    private val home =
        source("frontend/screens/dashboard/HomeScreen.kt")

    private val settingsSignature =
        settings.section(
            from = "fun SettingsScreen(",
            to = ") {\n    val onboardingState",
        )
    private val personalSupportGroup =
        settings.section(
            from = "private fun PersonalSupportSettingsGroup(",
            to = "private fun MultiSelectEditDialog(",
        )
    private val protectionFocusGroup =
        settings.section(
            from = "private fun ProtectionFocusGroup(",
            to = "private fun PrivacyAccountGroup(",
        )
    private val settingsCall =
        navHost.section(
            from = "SettingsScreen(",
            to = "composable(AppRoutes.MomentPlanList)",
        )
    private val homeRoute =
        navHost.section(
            from = "composable(AppRoutes.Home)",
            to = "composable(AppRoutes.Score)",
        )
    private val safeBrowseRoute =
        navHost.section(
            from = "SafeBrowseRoute(",
            to = "composable(AppRoutes.SafeBrowsePass)",
        )

    @Test
    fun personalSupportContainsOnlyApprovedRowsAndConditionalAdPrivacyChoices() {
        listOf(
            "personal_support_plans",
            "tips_title",
            "\"Future Path\"",
            "\"Suggestion preferences\"",
            "\"Safe Browse Pass\"",
        ).forEach { removed ->
            assertFalse(removed, personalSupportGroup.contains(removed))
        }

        assertTrue(personalSupportGroup.contains("\"What Works for Me\""))
        assertTrue(personalSupportGroup.contains("\"Privacy and data\""))
        assertTrue(personalSupportGroup.contains("SafeBrowseAdPrivacyChoicesRow()"))
    }

    @Test
    fun protectionFocusContainsOnlyApprovedOperationalRows() {
        listOf(
            "\"Protection Coach\"",
            "\"Website Protection & DNS Blocking\"",
            "title = \"Website Protection\"",
            "\"45-second access\"",
        ).forEach { removed ->
            assertFalse(removed, protectionFocusGroup.contains(removed))
        }

        listOf(
            "\"Choose apps to protect\"",
            "\"App protection\"",
            "\"App detection\"",
            "\"Let Impulsive step in\"",
            "\"Notifications\"",
            "\"Background protection\"",
        ).forEach { retained ->
            assertTrue(retained, protectionFocusGroup.contains(retained))
        }
    }

    @Test
    fun settingsScreenSignatureOnlyKeepsNeededSettingsCallbacks() {
        listOf(
            "onOpenMomentPlans",
            "onOpenTips",
            "onOpenSuggestionPreferences",
            "onOpenSafeBrowsePass",
            "onOpenProtectionCoach",
        ).forEach { removed ->
            assertFalse(removed, settingsSignature.contains(removed))
            assertFalse(removed, settingsCall.contains(removed))
        }

        listOf(
            "onOpenWebsiteProtectionPlus",
            "onOpenWhatWorksForMe",
            "onOpenPrivacyAndData",
        ).forEach { retained ->
            assertTrue(retained, settingsSignature.contains(retained))
            assertTrue(retained, settingsCall.contains(retained))
        }
    }

    @Test
    fun removedSettingsEntriesDidNotDeleteFeatureRoutes() {
        listOf(
            "composable(AppRoutes.MomentPlanList)",
            "composable(AppRoutes.Tips)",
            "composable(AppRoutes.PersonalSupportSuggestions)",
            "composable(AppRoutes.SafeBrowsePass)",
            "composable(AppRoutes.ProtectionCoach)",
            "composable(AppRoutes.WebsiteProtectionPlus)",
        ).forEach { route ->
            assertTrue(route, navHost.contains(route))
        }
    }

    @Test
    fun nonSettingsEntryPointsRemainWired() {
        assertTrue(home.contains("onOpenMomentPlans: () -> Unit"))
        assertTrue(home.contains("onOpenTips: () -> Unit"))
        assertTrue(home.contains("onOpenWebsiteProtectionPlus: () -> Unit"))
        assertTrue(homeRoute.contains("onOpenMomentPlans = dropUnlessResumed"))
        assertTrue(homeRoute.contains("navController.navigate(AppRoutes.MomentPlanList)"))
        assertTrue(homeRoute.contains("onOpenTips = dropUnlessResumed"))
        assertTrue(homeRoute.contains("navController.navigate(AppRoutes.Tips)"))
        assertTrue(homeRoute.contains("onOpenWebsiteProtectionPlus = dropUnlessResumed"))
        assertTrue(homeRoute.contains("navController.navigate(AppRoutes.WebsiteProtectionPlus)"))
        assertTrue(safeBrowseRoute.contains("onOpenSafeBrowsePass = {"))
        assertTrue(safeBrowseRoute.contains("navController.navigate(AppRoutes.SafeBrowsePass)"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path")
            .readText()
            .replace("\r\n", "\n")
            .replace('\r', '\n')

    private fun String.section(from: String, to: String): String {
        val start = indexOf(from)
        require(start >= 0) { "Missing section start: $from" }
        val end = indexOf(to, start + from.length)
        require(end > start) { "Missing section end: $to" }
        return substring(start, end)
    }
}
