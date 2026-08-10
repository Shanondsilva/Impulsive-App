package com.impulsive.app.frontend.tips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V28TipsAndHomeSourceTest {
    private val home = source("frontend/screens/dashboard/HomeScreen.kt")
    private val navigation = source("frontend/navigation/AppNavHost.kt")
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")
    private val coach = source("frontend/screens/protectioncoach/ProtectionCoachScreens.kt")
    private val tips = source("frontend/screens/tips/TipsScreens.kt")
    private val catalogue = source("frontend/screens/tips/ImpulsiveTipCatalogue.kt")
    private val dataStore = source("backend/data/local/preferences/TipsPreferencesDataSource.kt")
    private val topAppBar =
        source("frontend/components/ImpulsiveTopAppBar.kt")

    @Test fun protectionCoachCardIsAbsentFromHome() =
        assertFalse(home.contains("YOUR SUGGESTED SETUP"))

    @Test fun suggestedSetupCardIsAbsentFromHome() =
        assertFalse(home.contains("Review setup >"))

    @Test fun futurePathCardIsAbsentFromHome() {
        assertFalse(home.contains("FUTURE PATH"))
        assertFalse(home.contains("Your Current Path"))
    }

    @Test fun largeMomentPlanCardIsAbsentFromHome() =
        assertFalse(home.contains("MomentPlanHomeCard("))

    @Test fun compactMomentPlanCardIsPresent() =
        assertTrue(home.contains("private fun MomentPlanCompactCard("))

    @Test fun compactTipsCardIsPresent() =
        assertTrue(home.contains("private fun TipsCompactCard("))

    @Test fun websiteProtectionHasOneRenderedCard() {
        assertEquals(2, home.count("WebsiteProtectionStatusHomeCard("))
        assertEquals(1, home.count("WebsiteProtectionHomeCard("))
    }

    @Test fun resetReadingRemainsFullWidth() {
        val dashboard = home.section("private fun DashboardCards(", "private fun MomentPlanAndTipsCards(")
        assertTrue(dashboard.contains(".fillMaxWidth()"))
        assertTrue(dashboard.contains("private fun ResetReadingHomeCard("))
        assertTrue(dashboard.contains("R.string.v28_reset_reading_title"))
    }

    @Test fun momentPlanAndTipsShareNormalWidthRow() {
        val cards = home.section("private fun MomentPlanAndTipsCards(", "private fun MomentPlanCompactCard(")
        assertTrue(cards.contains("Row("))
        assertTrue(cards.count(".weight(1f)") >= 2)
    }

    @Test fun cardsStackAtHighFontScaleOrNarrowWidth() {
        assertTrue(home.contains("configuration.fontScale >= 1.8f"))
        assertTrue(home.contains("maxWidth < 340.dp"))
        assertTrue(home.contains("if (shouldStack)"))
    }

    @Test fun pivotAndNotesRemain() {
        assertTrue(home.contains("label = \"PIVOT GAME\""))
        assertTrue(home.contains("label = \"NOTES\""))
    }

    @Test fun resetReadingUsesApprovedGreen() =
        assertTrue(home.contains("HomeGreenGlow = Color(0xFF93E9BE)"))

    @Test fun momentPlanUsesApprovedYellow() =
        assertTrue(home.contains("HomeYellowGlow = Color(0xFFFEF1AB)"))

    @Test fun tipsUsesApprovedBlue() =
        assertTrue(home.contains("HomeBlueGlow = Color(0xFFBDE0FE)"))

    @Test
    fun homeRotationsUseApprovedNineSecondCadence() {
        assertTrue(
            home.contains(
                "HOME_TIPS_ROTATION_DELAY_MS = 9_000L",
            ),
        )

        assertTrue(
            home.contains(
                "HOME_SHOWCASE_ROTATION_DELAY_MS = 9_000L",
            ),
        )
    }

    @Test fun reducedMotionDisablesRotationAndCrossfadeDuration() {
        assertTrue(home.contains("!reducedMotion"))
        assertTrue(home.contains("if (reducedMotion) 0 else 650"))
    }

    @Test fun rotationPausesWhenHomeIsInactive() =
        assertTrue(home.contains("if (isActive && !reducedMotion"))

    @Test fun touchExplorationDisablesRotation() =
        assertTrue(home.contains("!touchExplorationEnabled"))

    @Test fun decorativePulseHasNoSemantics() {
        assertTrue(home.contains("clearAndSetSemantics { }"))
        assertTrue(home.contains("durationMillis = 7_000"))
    }

    @Test fun currentTipIdIsPassedToExactDetailRoute() {
        assertTrue(home.contains("tipsState.currentTip?.id?.let(onOpenTip)"))
        assertTrue(navigation.contains("fun tipDetail(tipId: ImpulsiveTipId)"))
    }

    @Test fun stableTipsRoutesContainNoPrivateArguments() {
        assertTrue(navigation.contains("const val Tips = \"tips\""))
        assertTrue(navigation.contains("const val TipDetail = \"tip/{tipId}\""))
        assertFalse(navigation.contains("tip/{package"))
        assertFalse(navigation.contains("tip/{url"))
        assertFalse(navigation.contains("tip/{answer"))
    }

    @Test fun unknownTipRedirectsSafelyToList() {
        val detail = navigation.section("route = AppRoutes.TipDetail", "composable(AppRoutes.SuggestedSetup)")
        assertTrue(detail.contains("if (tip == null)"))
        assertTrue(detail.contains("navController.navigate(AppRoutes.Tips)"))
    }

    @Test
    fun tipsScreensReuseSharedAutoMirroredBackButtonWithTouchTarget() {
        assertTrue(
            "Tips screens must reuse the shared Impulsive top app bar",
            tips.contains("ImpulsiveTopAppBar("),
        )

        assertTrue(
            "The shared top app bar must use an RTL-safe back arrow",
            topAppBar.contains(
                "Icons.AutoMirrored.Filled.ArrowBack",
            ),
        )

        assertTrue(
            "The shared back action must retain a 48 dp touch target",
            topAppBar.contains("Modifier.size(48.dp)"),
        )

        assertFalse(
            "Tips must not render a written Back control",
            tips.contains("Text(\"Back\")"),
        )

        assertFalse(
            "The shared top bar must not render a written Back control",
            topAppBar.contains("Text(\"Back\")"),
        )
    }

    @Test fun coachUsesIconBackAndNoLegacyRecommendationList() {
        assertTrue(coach.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertFalse(coach.contains("Review my suggested setup"))
        assertFalse(coach.contains("Website Protection recommendation"))
        assertFalse(coach.contains("Suggested apps, timing and support settings"))
    }

    @Test fun coachSettingsEntryIsRemovedWhileCoachCopyRemainsTimingOnly() {
        assertFalse(settings.contains("R.string.protection_coach_description"))
        assertTrue(coach.contains("protection_coach_timing_title"))
    }

    @Test fun legacySuggestedSetupRouteRedirectsSafely() {
        val legacy = navigation.section("composable(AppRoutes.SuggestedSetup)", "composable(AppRoutes.ProtectionCoach)")
        assertTrue(legacy.contains("activeTimingSuggestion"))
        assertTrue(legacy.contains("AppRoutes.ProtectionSetupGuide"))
        assertFalse(legacy.contains("SuggestedSetupScreen"))
    }

    @Test fun realTimingSuggestionDoesNotApplyAutomatically() {
        assertTrue(coach.contains("protection_coach_timing_title"))
        assertFalse(coach.contains("repository.accept"))
    }

    @Test fun settingsNoLongerDuplicatesCoachFuturePathOrTipsRows() {
        assertFalse(settings.contains("title = \"Protection Coach\""))
        assertFalse(settings.contains("title = \"Future Path\""))
        assertFalse(settings.contains("title = stringResource(R.string.tips_title)"))
        assertTrue(navigation.contains("composable(AppRoutes.Tips)"))
        assertTrue(navigation.contains("composable(AppRoutes.ProtectionCoach)"))
        assertTrue(navigation.section("composable(AppRoutes.SuggestedSetup)", "composable(AppRoutes.ProtectionCoach)").contains("AppRoutes.ProtectionCoach"))
    }

    @Test fun catalogueContainsAtLeastEighteenStableEntries() =
        assertTrue(catalogue.count("TipTemplate(") >= 19)

    @Test fun externalCatalogueUsesOnlyOfficialSources() {
        assertTrue(catalogue.contains("support.google.com/android"))
        assertTrue(catalogue.contains("about.fb.com"))
        assertFalse(catalogue.contains("reddit.com"))
    }

    @Test fun noInstagramUndocumentedIntentIsUsed() {
        val instagram = catalogue.section("\"instagram_sleep_mode\"", "\"social_notification_categories\"")
        assertFalse(instagram.contains("OpenAndroidSetting"))
        assertFalse(instagram.contains("Intent("))
    }

    @Test fun datastoreIsBoundedAndPresentationOnly() {
        assertTrue(dataStore.contains("MAX_TIP_HISTORY = 64"))
        assertTrue(dataStore.contains("never uploaded or added to analytics/recovery"))
        assertFalse(dataStore.contains("Room"))
        assertFalse(dataStore.contains("packageName"))
        assertFalse(dataStore.contains("journal"))
    }

    @Test fun noTipsAnalyticsCallsExist() {
        val tipSources = File("src/main/java/com/impulsive/app/frontend/screens/tips")
            .walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertFalse(tipSources.contains("Analytics"))
        assertFalse(tipSources.contains("logEvent"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
