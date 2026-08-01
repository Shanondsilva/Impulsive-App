package com.impulsive.app.frontend.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirms every full-screen host owns a full-window ambient background
 * (no statusBarsPadding before ImpulsiveAmbientBackground) while its normal
 * content stays inset by exactly one statusBarsPadding, and that mode
 * overlays / BottomNavBar are never nested inside that inset content.
 */
class ModeOverlayInsetOwnershipSourceTest {
    private val home = source("frontend/screens/dashboard/HomeScreen.kt")
    private val focus = source("frontend/screens/focus/FocusScreen.kt")
    private val progress = source("frontend/screens/progress/ProgressDashboardScreen.kt")
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")
    private val mindSheet = source("frontend/components/MindModeStatusSheet.kt")
    private val modeSheet = source("frontend/components/ModeSelectionSheet.kt")

    // ---- A. Home ---------------------------------------------------------

    @Test
    fun homeRootSeparatesFullWindowAmbientFromInsetContent() {
        val functionStart = home.indexOf("fun HomeScreen(")
        val rootBoxStart = home.indexOf("Box(", functionStart)
        val ambientStart = home.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = home.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = home.substring(ambientStart, home.indexOf("Column(", ambientStart))
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))

        val columnStart = home.indexOf("Column(", ambientStart)
        val columnModifier = home.substring(columnStart, home.indexOf(") {", columnStart))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
    }

    @Test
    fun homeModeOverlaysAndBottomNavRemainOutsideTheScrollingColumn() {
        val functionStart = home.indexOf("fun HomeScreen(")
        val columnStart = home.indexOf(
            "Column(",
            home.indexOf("ImpulsiveAmbientBackground(", functionStart),
        )
        val overlaysStart = home.indexOf("if (mindModeSheetVisible)", columnStart)
        val columnSection = home.substring(columnStart, overlaysStart)

        assertFalse(columnSection.contains("MindModeStatusSheet("))
        assertFalse(columnSection.contains("ModeSelectionSheet("))
        assertFalse(columnSection.contains("BottomNavBar("))

        assertTrue(home.indexOf("MindModeStatusSheet(", overlaysStart) > overlaysStart)
        assertTrue(home.indexOf("ModeSelectionSheet(", overlaysStart) > overlaysStart)
        assertTrue(home.indexOf("BottomNavBar(", overlaysStart) > overlaysStart)
    }

    // ---- B. Focus ----------------------------------------------------------

    @Test
    fun focusRootSeparatesFullWindowAmbientFromInsetContent() {
        val functionStart = focus.indexOf("fun FocusScreen(")
        val rootBoxStart = focus.indexOf("Box(", functionStart)
        val ambientStart = focus.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = focus.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = focus.substring(
            ambientStart,
            focus.indexOf("BoxWithConstraints(", ambientStart),
        )
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))

        val constraintsStart = focus.indexOf("BoxWithConstraints(", ambientStart)
        val constraintsModifier = focus.substring(
            constraintsStart,
            focus.indexOf(") {", constraintsStart),
        )
        assertEquals(1, constraintsModifier.count(".statusBarsPadding()"))
    }

    @Test
    fun focusModeOverlaysAndBottomNavRemainOutsideBoxWithConstraints() {
        val functionStart = focus.indexOf("fun FocusScreen(")
        val constraintsStart = focus.indexOf(
            "BoxWithConstraints(",
            focus.indexOf("ImpulsiveAmbientBackground(", functionStart),
        )
        val overlaysStart = focus.indexOf("if (mindModeSheetVisible)", constraintsStart)
        val constraintsSection = focus.substring(constraintsStart, overlaysStart)

        assertFalse(constraintsSection.contains("MindModeStatusSheet("))
        assertFalse(constraintsSection.contains("ModeSelectionSheet("))
        assertFalse(constraintsSection.contains("BottomNavBar("))

        assertTrue(focus.indexOf("MindModeStatusSheet(", overlaysStart) > overlaysStart)
        assertTrue(focus.indexOf("ModeSelectionSheet(", overlaysStart) > overlaysStart)
        assertTrue(focus.indexOf("BottomNavBar(", overlaysStart) > overlaysStart)
    }

    // ---- C. Progress ---------------------------------------------------------

    @Test
    fun progressRootSeparatesFullWindowAmbientFromInsetContent() {
        val functionStart = progress.indexOf("fun ProgressDashboardScreen(")
        val rootBoxStart = progress.indexOf("Box(", functionStart)
        val ambientStart = progress.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = progress.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = progress.substring(
            ambientStart,
            progress.indexOf("Column(", ambientStart),
        )
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))

        val columnStart = progress.indexOf("Column(", ambientStart)
        val columnModifier = progress.substring(columnStart, progress.indexOf(") {", columnStart))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
    }

    @Test
    fun progressScoreCardsStayInsideColumnWhileOverlaysAndBottomNavStayOutside() {
        val functionStart = progress.indexOf("fun ProgressDashboardScreen(")
        val columnStart = progress.indexOf(
            "Column(",
            progress.indexOf("ImpulsiveAmbientBackground(", functionStart),
        )
        val dialogStart = progress.indexOf("if (showScoreInfo)", columnStart)
        val columnSection = progress.substring(columnStart, dialogStart)

        assertTrue(columnSection.contains("ScoreRecordsCard("))
        assertTrue(columnSection.contains("ResetReadingProgressCard("))
        assertFalse(columnSection.contains("MindModeStatusSheet("))
        assertFalse(columnSection.contains("BottomNavBar("))

        val overlaysStart = progress.indexOf("if (mindModeSheetVisible)", dialogStart)
        assertTrue(progress.indexOf("MindModeStatusSheet(", overlaysStart) > overlaysStart)
        assertTrue(progress.indexOf("BottomNavBar(", overlaysStart) > overlaysStart)
    }

    // ---- D. Settings -----------------------------------------------------

    @Test
    fun settingsMainRootSeparatesFullWindowAmbientFromInsetContent() {
        val functionStart = settings.indexOf("fun SettingsScreen(")
        val rootBoxStart = settings.indexOf("Box(", functionStart)
        val ambientStart = settings.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = settings.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = settings.substring(
            ambientStart,
            settings.indexOf("Column(", ambientStart),
        )
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))

        val columnStart = settings.indexOf("Column(", ambientStart)
        val columnModifier = settings.substring(columnStart, settings.indexOf(") {", columnStart))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
    }

    @Test
    fun settingsModeOverlaysDeletionFlowAndBottomNavRemainOutsideTheMainColumn() {
        val functionStart = settings.indexOf("fun SettingsScreen(")
        val columnStart = settings.indexOf(
            "Column(",
            settings.indexOf("ImpulsiveAmbientBackground(", functionStart),
        )
        val overlaysStart = settings.indexOf("if (mindModeSheetVisible)", columnStart)
        val columnSection = settings.substring(columnStart, overlaysStart)

        assertFalse(columnSection.contains("MindModeStatusSheet("))
        assertFalse(columnSection.contains("ModeSelectionSheet("))
        assertFalse(columnSection.contains("AccountDeletionFlow("))
        assertFalse(columnSection.contains("BottomNavBar("))

        assertTrue(settings.indexOf("MindModeStatusSheet(", overlaysStart) > overlaysStart)
        assertTrue(settings.indexOf("ModeSelectionSheet(", overlaysStart) > overlaysStart)
        assertTrue(settings.indexOf("AccountDeletionFlow(", overlaysStart) > overlaysStart)
        assertTrue(settings.indexOf("BottomNavBar(", overlaysStart) > overlaysStart)
    }

    // ---- E. Mode pages -----------------------------------------------------

    @Test
    fun mindOuterBoxHasNoStatusBarPaddingWhileContentColumnHasExactlyOne() {
        val functionStart = mindSheet.indexOf("fun MindModeStatusSheet(")
        val rootBoxStart = mindSheet.indexOf("Box(", functionStart)
        val ambientStart = mindSheet.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = mindSheet.substring(rootBoxStart, ambientStart)
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val columnStart = mindSheet.indexOf("Column(", ambientStart)
        val columnModifier = mindSheet.substring(columnStart, mindSheet.indexOf(") {", columnStart))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
    }

    @Test
    fun lockedModePreviewSheetOuterBoxHasNoStatusBarPaddingWhileContentColumnHasExactlyOne() {
        val functionStart = modeSheet.indexOf("private fun LockedModePreviewSheet(")
        val rootBoxStart = modeSheet.indexOf("Box(", functionStart)
        val ambientStart = modeSheet.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = modeSheet.substring(rootBoxStart, ambientStart)
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val columnStart = modeSheet.indexOf("Column(", ambientStart)
        val columnModifier = modeSheet.substring(
            columnStart,
            modeSheet.indexOf(") {", columnStart),
        )
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
    }

    // ---- F. Prohibited regressions ---------------------------------------

    @Test
    fun insetOwnershipChangeDidNotRemoveRestoredMindCardsOrSelectorScrim() {
        assertTrue(mindSheet.contains("MindModeExplainerCarousel("))
        assertTrue(mindSheet.contains("MindModeDecisionTreeVisual("))
        assertEquals(2, mindSheet.count("shape = RoundedCornerShape(34.dp)"))

        assertTrue(modeSheet.contains("scrimAlpha"))
        assertTrue(modeSheet.contains("Color(0x660A0710)"))
        assertTrue(modeSheet.contains("Color(0x44FFFFFF)"))
    }

    @Test
    fun insetOwnershipChangeDidNotRemoveProgressSideBySideRowOrGreenAccent() {
        assertTrue(progress.contains("ScoreRecordsCard("))
        assertTrue(progress.contains("ResetReadingProgressCard("))
        assertTrue(progress.contains("Arrangement.spacedBy(12.dp)"))
        assertTrue(progress.contains("private val ResetReadingGreenGlow = Color(0xFF93E9BE)"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
