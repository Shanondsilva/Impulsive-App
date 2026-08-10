package com.impulsive.app.frontend.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalSupportInformationArchitectureSourceTest {
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")
    private val navigation = source("frontend/navigation/AppNavHost.kt")
    private val privacy = source("frontend/privacy/RouteSensitiveScreenPrivacy.kt")
    private val home = source("frontend/screens/dashboard/HomeScreen.kt")
    private val momentPlan = source("frontend/screens/adaptive/MomentPlanScreens.kt")

    private val mainPersonalSupportGroup = settings.section(
        from = "private fun PersonalSupportSettingsGroup",
        to = "private fun MultiSelectEditDialog",
    )
    private val suggestionScreen = settings.section(
        from = "fun PersonalSupportSuggestionPreferencesScreen",
        to = "fun PersonalSupportPrivacyAndDataScreen",
    )
    private val privacyScreen = settings.section(
        from = "fun PersonalSupportPrivacyAndDataScreen",
        to = "private fun PersonalSupportSubScreen",
    )
    private val settingsCall = navigation.section(
        from = "SettingsScreen(",
        to = "onOpenUsageAccessPermission",
    )
    private val momentPlanHomeCard = momentPlan.section(
        from = "fun MomentPlanHomeCard(",
        to = "fun MomentPlanEditorScreen",
    )

    @Test
    fun mainPersonalSupportGroupHasTwoCoreDestinationsAndConditionalAdPrivacyChoices() {
        assertEquals(
            2,
            Regex("""SettingsRow\(""").findAll(mainPersonalSupportGroup).count(),
        )
        assertTrue(mainPersonalSupportGroup.contains("\"What Works for Me\""))
        assertTrue(mainPersonalSupportGroup.contains("\"Privacy and data\""))
        assertTrue(mainPersonalSupportGroup.contains("SafeBrowseAdPrivacyChoicesRow()"))
        assertFalse(mainPersonalSupportGroup.contains("stringResource(R.string.personal_support_plans)"))
        assertFalse(mainPersonalSupportGroup.contains("stringResource(R.string.tips_title)"))
        assertFalse(mainPersonalSupportGroup.contains("\"Future Path\""))
        assertFalse(mainPersonalSupportGroup.contains("\"Suggestion preferences\""))
        assertFalse(mainPersonalSupportGroup.contains("\"Safe Browse Pass\""))
        assertFalse(mainPersonalSupportGroup.contains("onOpenPlans"))
        assertFalse(mainPersonalSupportGroup.contains("onOpenTips"))
        assertFalse(mainPersonalSupportGroup.contains("onOpenSuggestionPreferences"))
        assertFalse(mainPersonalSupportGroup.contains("onOpenSafeBrowsePass"))
        assertFalse(mainPersonalSupportGroup.contains("\"How suggestions work\""))
        assertFalse(mainPersonalSupportGroup.contains("\"Reset personal learning\""))
        assertFalse(mainPersonalSupportGroup.contains("\"Delete all Moment data\""))
    }

    @Test
    fun futurePathIsRemovedFromSettingsWithoutToggleOrDisableDialog() {
        assertEquals(
            0,
            Regex(
                """SettingsSwitch\(""",
            )
                .findAll(
                    mainPersonalSupportGroup,
                )
                .count(),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "\"Future Path\"",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "\"Always on\"",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "Uses encrypted on-device history for cautious estimates.",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "pathShiftConsentVisible",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "pathShiftDisableVisible",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "Turn On Future Path",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "Turn Off Future Path",
            ),
        )

        assertFalse(
            mainPersonalSupportGroup.contains(
                "pathShiftEnabled = false",
            ),
        )
    }

    @Test
    fun suggestionPreferencesScreenContainsAllExistingSwitchesAndUpdatesFields() {
        mapOf(
            "personal_suggestions" to "personalSuggestionsEnabled = checked",
            "game_suggestions" to "gameSuggestionsEnabled = checked",
            "reading_suggestions" to "readingSuggestionsEnabled = checked",
            "moment_plan_suggestions" to "momentPlanSuggestionsEnabled = checked",
        ).forEach { (labelResource, fieldUpdate) ->
            assertTrue(suggestionScreen.contains("R.string.$labelResource"))
            assertTrue(suggestionScreen.contains(fieldUpdate))
        }
        assertEquals(
            4,
            Regex("""SettingsSwitch\(""").findAll(suggestionScreen).count(),
        )
        assertTrue(suggestionScreen.contains("\"About suggestions\""))
        assertTrue(suggestionScreen.contains("onOpenHowSuggestionsWork"))
    }

    @Test
    fun privacyAndDataScreenContainsScreenPrivacyRetentionResetAndTwoStepDeletion() {
        assertTrue(privacyScreen.contains("\"Screen privacy\""))
        assertTrue(privacyScreen.contains("privateScreenProtectionEnabled = checked"))
        assertTrue(privacyScreen.contains("\"Personal support history\""))
        assertTrue(privacyScreen.contains("historyRetentionPolicy = policy"))
        assertTrue(privacyScreen.contains("\"DATA CONTROL\""))
        assertTrue(privacyScreen.contains("\"Reset personal learning\""))
        assertTrue(privacyScreen.contains("resetPersonalLearning()"))
        assertTrue(privacyScreen.contains("\"Delete all Moment data\""))
        assertTrue(privacyScreen.contains("confirmation = \"delete-first\""))
        assertTrue(privacyScreen.contains("confirmation = \"delete-final\""))
        assertTrue(privacyScreen.contains("deleteAllMomentData()"))
    }

    @Test
    fun routesContainNoPersonalDataAndAreScreenPrivacyProtected() {
        assertTrue(navigation.contains("const val PersonalSupportSuggestions = \"personal_support_suggestions\""))
        assertTrue(navigation.contains("const val PersonalSupportPrivacy = \"personal_support_privacy\""))
        assertFalse(navigation.contains("personal_support_suggestions/{"))
        assertFalse(navigation.contains("personal_support_privacy/{"))
        assertTrue(privacy.contains("\"personal_support_suggestions\""))
        assertTrue(privacy.contains("\"personal_support_privacy\""))
    }

    @Test
    fun remainingPersonalSupportSubscreensAreReachedThroughExistingAppLockGuard() {
        assertFalse(settingsCall.contains("onOpenSuggestionPreferences = {"))
        assertTrue(settingsCall.contains("onOpenPrivacyAndData = {"))
        assertTrue(settingsCall.contains("onOpenWhatWorksForMe = {"))
        assertTrue(settings.contains("appLockGuard.run("))
        assertTrue(settings.contains("action = onOpenWhatWorksForMe"))
        assertTrue(settings.contains("action = onOpenPrivacyAndData"))
        assertTrue(navigation.contains("launchSingleTop = true"))
        assertTrue(navigation.contains("navController.safePopBackStack()"))
    }

    @Test
    fun homeUsesCompactMomentPlanAndOmitsPathShiftCard() {
        assertTrue(home.contains("private fun MomentPlanCompactCard("))
        assertTrue(home.contains("R.string.moment_plan_home_create"))
        assertFalse(home.contains("HomeSupportFeatureCard("))
        assertTrue(momentPlanHomeCard.contains("HomeSupportFeatureCard("))
        assertTrue(momentPlanHomeCard.contains("eyebrow = \"MOMENT PLAN\""))
        assertTrue(momentPlanHomeCard.contains("\"Create plan >\""))
        assertTrue(momentPlanHomeCard.contains("\"Practise plan >\""))
        assertFalse(momentPlanHomeCard.contains("\"Create My Plan\""))
        assertFalse(momentPlanHomeCard.contains("\"Practise My Plan\""))
        assertFalse(momentPlanHomeCard.contains("CardDefaults.cardElevation"))
        assertFalse(home.contains("private fun PathShiftHomeCard("))
        assertFalse(home.contains("secondaryContainer.copy(alpha = 0.66f)"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String {
        val start = indexOf(from)
        val end = indexOf(to, start + from.length)
        assertTrue("Missing section start: $from", start >= 0)
        assertTrue("Missing section end: $to", end > start)
        return substring(start, end)
    }
}
