package com.impulsive.app.frontend.refinement

import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.buildScoreDashboardState
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V28UiRefinementSourceTest {
    private val home = source("frontend/screens/dashboard/HomeScreen.kt")
    private val progress = source("frontend/screens/progress/ProgressDashboardScreen.kt")
    private val modes = source("frontend/components/ModeSelectionSheet.kt")
    private val bottomNav = source("frontend/components/BottomNavBar.kt")
    private val resources = File("src/main/res/values/v28_ui_refinement_strings.xml").readText()

    @Test fun test01_homeChangesAreConfinedToApprovedFunctions() {
        listOf(
            "private fun LevelCard(",
            "private fun TaskToCompletePreviewCard(",
            "private fun TaskCompletedPreviewCard(",
            "private fun ResetReadingHomeCard(",
            "private fun TipsCompactCard(",
        ).forEach { assertTrue(home.contains(it)) }
    }

    @Test fun test02_pivotGameRemainsPresent() =
        assertTrue(home.contains("label = \"PIVOT GAME\""))

    @Test fun test03_notesRemainsPresent() =
        assertTrue(home.contains("private fun DiagonalNotesCard("))

    @Test fun test04_momentPlanRemainsPresent() =
        assertTrue(home.contains("private fun MomentPlanCompactCard("))

    @Test fun test05_websiteProtectionRemainsPresent() =
        assertTrue(home.contains("private fun WebsiteProtectionHomeCard("))

    @Test fun test06_bottomNavigationSourceRemainsSeparate() =
        assertTrue(bottomNav.contains("fun BottomNavBar("))

    @Test fun test07_tipsUsesStableNormalHeight() =
        assertTrue(home.contains("HomeShowcaseSmallCardHeight = 214.dp"))

    @Test fun test08_tipCopyCannotMeasureOuterCard() {
        val section = home.section("private fun TipsCompactCard(", "private fun DiagonalNotesCard(")
        assertTrue(section.contains(".height(copyRegionHeight)"))
        assertFalse(section.contains("animateContentSize"))
    }

    @Test fun test09_momentPlanAndTipsUseSameHeight() {
        val section = home.section("private fun MomentPlanAndTipsCards(", "private fun MomentPlanCompactCard(")
        assertTrue(section.contains(".height(stableCardHeight)"))
        assertTrue(section.count(".height(stableCardHeight)") >= 3)
    }

    @Test fun test10_visibleTipIdOpensVisibleTip() =
        assertTrue(home.contains("tipsState.currentTip?.id?.let(onOpenTip)"))

    @Test fun test11_reducedMotionStopsTipRotation() =
        assertTrue(home.contains("isActive && !reducedMotion && !touchExplorationEnabled"))

    @Test fun test12_largeFontsStackWithAccessibleFixedHeight() {
        assertTrue(home.contains("configuration.fontScale >= 1.8f"))
        assertTrue(home.contains("HomeShowcaseAccessibleCardHeight = 286.dp"))
        assertTrue(home.contains("if (shouldStack)"))
    }

    @Test fun test13_todayIsAbsentFromLevelCard() {
        val section = home.section("private fun LevelCard(", "private fun TaskToCompletePreviewCard(")
        assertFalse(section.contains("\"TODAY\""))
    }

    @Test fun test14_lpValuesRemainBoundToTaskRewardState() {
        val section = home.section("private fun LevelCard(", "private fun TaskToCompletePreviewCard(")
        assertTrue(section.contains("taskRewardState.currentLevelPoints"))
        assertTrue(section.contains("taskRewardState.pointsNeededForNextLevel"))
    }

    @Test fun test15_levelCardDoesNotIntroduceStreakCalculation() {
        val section = home.section("private fun LevelCard(", "private fun TaskToCompletePreviewCard(")
        assertFalse(section.contains("calculateStreak"))
    }

    @Test fun test16_levelCardUsesCompactPaddingAndThinProgress() {
        val section = home.section("private fun LevelCard(", "private fun TaskToCompletePreviewCard(")
        assertTrue(section.contains("vertical = 14.dp"))
        assertTrue(section.contains(".height(4.dp)"))
    }

    @Test fun test17_taskCardWholeSurfaceIsClickable() {
        val section = home.section("private fun TaskToCompletePreviewCard(", "private fun TaskCompletedPreviewCard(")
        assertTrue(section.contains("onClick = onViewAllTasks"))
    }

    @Test fun test18_taskRewardLogicRemainsAuthoritative() {
        val section = home.section("private fun TaskToCompletePreviewCard(", "private fun TaskCompletedPreviewCard(")
        assertTrue(section.contains("recommendedReward.displayRewardLabel()"))
        assertTrue(section.contains("recommendedReward.hasVisibleWaitCut()"))
    }

    @Test fun test19_resetReadingVisibleTitleIsSingleResource() {
        val section = home.section("private fun ResetReadingHomeCard(", "private fun MomentPlanAndTipsCards(")
        assertEquals(1, section.count("R.string.v28_reset_reading_title"))
    }

    @Test fun test20_resetReadingDuplicateLabelIsAbsent() {
        val section = home.section("private fun ResetReadingHomeCard(", "private fun MomentPlanAndTipsCards(")
        assertFalse(section.contains("\"READING\""))
    }

    @Test fun test21_resetReadingInternalActionIsAbsent() {
        val section = home.section("private fun ResetReadingHomeCard(", "private fun MomentPlanAndTipsCards(")
        assertFalse(section.contains("Open reading"))
    }

    @Test fun test22_resetReadingWholeCardUsesExistingCallback() {
        val section = home.section("private fun DashboardCards(", "private fun MomentPlanAndTipsCards(")
        assertTrue(section.contains("onClick = onOpenReading"))
    }

    @Test fun test23_resetReadingExposesOneClickAction() {
        val section = home.section("private fun ResetReadingHomeCard(", "private fun MomentPlanAndTipsCards(")
        assertEquals(1, section.count(".clickable("))
    }

    @Test fun test24_resetReadingDoesNotTouchCompletionLogic() {
        val section = home.section("private fun ResetReadingHomeCard(", "private fun MomentPlanAndTipsCards(")
        assertFalse(section.contains("complete"))
        assertFalse(section.contains("adaptive"))
    }

    @Test fun test25_scoreFrontUsesRecentSessionResource() =
        assertTrue(progress.contains("R.string.v28_recent_session_eyebrow"))

    @Test fun test26_scoreBackUsesPersonalBestResource() =
        assertTrue(progress.contains("R.string.v28_personal_best_eyebrow"))

    @Test fun test27_scorePresentationHasNoHardcodedReflexFallback() {
        val section = progress.section("private fun ScoreRecordsCard(", "private fun PersonalBestsSection(")
        assertFalse(section.contains("Reflex Override"))
    }

    @Test
    fun test28_latestCompletedSessionUsesOrderedTimelineAndRejectsAbandoned() {
        val section = progress.section(
            "private fun ScoreRecordsCard(",
            "private fun PersonalBestsSection(",
        )

        val firstCompletedLookup =
            section.indexOf("recentSessions.firstOrNull")

        val abandonedPredicate =
            section.indexOf(
                "it.outcome != ScoreSessionOutcome.Abandoned",
                startIndex = firstCompletedLookup.coerceAtLeast(0),
            )

        assertTrue(
            "Recent Session must use the already ordered recentSessions timeline",
            firstCompletedLookup >= 0,
        )

        assertTrue(
            "Recent Session must reject abandoned sessions inside firstOrNull",
            abandonedPredicate > firstCompletedLookup,
        )
    }

    @Test
    fun test29_personalBestUsesAuthoritativeRecord() {
        val section = progress.section(
            "private fun ScoreRecordsCard(",
            "private fun PersonalBestsSection(",
        )

        val validRecordFilter =
            section.indexOf(".filter { it.hasRecord }")

        val highestScoreSelection =
            section.indexOf(".maxByOrNull { it.bestScore }")

        assertTrue(
            "Personal Best must exclude entries without an authoritative record",
            validRecordFilter >= 0,
        )

        assertTrue(
            "Personal Best must choose the highest valid recorded score",
            highestScoreSelection > validRecordFilter,
        )
    }

    @Test fun test30_emptyStatesAreTruthfulResources() {
        assertTrue(resources.contains("Complete a game to see your recent session."))
        assertTrue(resources.contains("Complete a game to set your first score."))
    }

    @Test fun test31_flipOuterBoundsAreFixed() {
        assertTrue(progress.contains(".height(ScoreFlipCardHeight)"))
        assertTrue(progress.contains("ScoreFlipCardHeight = 218.dp"))
    }

    @Test fun test32_manualTapSelectsOneOppositeFace() =
        assertTrue(progress.contains("val target = if (frontVisible) 180f else 0f"))

    @Test fun test33_duplicateTapIsIgnoredDuringAnimation() =
        assertTrue(progress.contains("if (!isFlipping)"))

    @Test fun test34_automaticFlipIsInfrequentAndSingleCycle() {
        assertTrue(progress.contains("ScoreFlipInitialPauseMs = 15_000L"))
        assertTrue(progress.contains("autoCycleCompleted = true"))
    }

    @Test fun test35_automaticFlipRequiresResumedActiveScreen() =
        assertTrue(progress.contains("isActive && lifecycleResumed"))

    @Test fun test36_reducedMotionDisablesAutomatic3dFlip() =
        assertTrue(progress.contains("!reducedMotion && !touchExplorationEnabled"))

    @Test fun test37_touchExplorationDisablesAutomaticFlip() =
        assertTrue(progress.contains("isTouchExplorationEnabled == true"))

    @Test fun test38_processRecreationSavesValidFace() {
        assertTrue(progress.contains("var showingBack by rememberSaveable"))
        assertTrue(progress.contains("rotation.snapTo(if (resolvedBack) 180f else 0f)"))
    }

    @Test fun test39_mindModeUsesCircularSharedBubble() =
        assertTrue(modes.contains("title = \"Mind\""))

    @Test fun test40_bodyModeUsesCircularSharedBubble() =
        assertTrue(modes.contains("title = \"Body\""))

    @Test fun test41_soulModeUsesCircularSharedBubble() =
        assertTrue(modes.contains("title = \"Soul\""))

    @Test fun test42_nexusTriggerRemainsCircular() {
        assertTrue(bottomNav.contains("item = BottomNavItem.Trigger"))
        assertTrue(bottomNav.contains(".clip(CircleShape)"))
    }

    @Test fun test43_modeBubblesUseEqualWidthAndHeight() {
        val section = modes.substring(modes.indexOf("private fun ModeBubble("))
        assertTrue(section.contains(".size(96.dp)"))
        assertTrue(section.contains("shape = CircleShape"))
    }

    @Test fun test44_levitationUsesDrawingTranslationNotLayoutSize() {
        val section = modes.substring(modes.indexOf("private fun ModeBubble("))
        assertTrue(section.contains("translationY = verticalTranslationDp * density"))
        assertFalse(section.contains(".offset("))
    }

    @Test fun test45_reducedMotionStopsLevitation() =
        assertTrue(modes.contains("val floatTransition = if (reducedMotion)"))

    @Test fun test46_modeDestinationsRemainMappedToOriginalCallbacks() {
        assertTrue(modes.contains("requestOpenMode(onOpenMindMode)"))
        assertTrue(modes.contains("requestOpenMode(onOpenBodyMode)"))
        assertTrue(modes.contains("requestOpenMode(onOpenSoulMode)"))
    }

    @Test fun authoritativeScoreStateOrdersLatestAndStoresBest() {
        val now = LocalDateTime.of(2026, 7, 30, 12, 0)
        val older = session(1, ScoreGameType.BlockCascade, 400, now.minusHours(2))
        val latest = session(2, ScoreGameType.SkylineReset, 250, now.minusMinutes(10))
        val state = buildScoreDashboardState(
            sessions = listOf(older, latest),
            selectedRange = ScoreRange.Week,
            currentLevel = 1,
            currentLevelPoints = 0,
            pointsNeededForNextLevel = 100,
            now = now,
            recoveryGameTypes = listOf(
                ScoreGameType.BlockCascade,
                ScoreGameType.SkylineReset,
            ),
        )
        assertEquals("SkyStack", state.recentSessions.first().gameName)
        assertEquals(400, state.personalBests.first { it.gameType == ScoreGameType.BlockCascade }.bestScore)
        assertNotNull(state.personalBests.firstOrNull { it.hasRecord })
    }

    private fun session(
        id: Long,
        type: ScoreGameType,
        score: Int,
        completedAt: LocalDateTime,
    ) = ScoreSessionRecord(
        id = id,
        gameType = type,
        score = score,
        startedAt = completedAt.minusMinutes(1),
        completedAt = completedAt,
        durationSec = 60,
        outcome = ScoreSessionOutcome.Completed,
        validCompletion = true,
    )

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path")
            .readText()
            .normalizeLineEndings()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }

    private fun String.normalizeLineEndings(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
}
