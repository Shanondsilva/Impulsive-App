package com.impulsive.app.frontend.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamlessModeBackgroundSourceTest {
    private val mindSheet = source("frontend/components/MindModeStatusSheet.kt")
    private val modeSheet = source("frontend/components/ModeSelectionSheet.kt")

    // ---- A. Mind screen ----------------------------------------------

    @Test
    fun mindModeRootBoxHasNoStatusBarPaddingBeforeAmbientLayer() {
        val functionStart = mindSheet.indexOf("fun MindModeStatusSheet(")
        val rootBoxStart = mindSheet.indexOf("Box(", functionStart)
        val ambientStart = mindSheet.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = mindSheet.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertTrue(
            rootBoxModifier.contains(".background(colorScheme.background)") ||
                rootBoxModifier.contains(".background(MaterialTheme.colorScheme.background)"),
        )
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = mindSheet.substring(
            ambientStart,
            mindSheet.indexOf("Column(", ambientStart),
        )
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))
    }

    @Test
    fun mindModeContentColumnOwnsExactlyOneStatusBarPadding() {
        val functionStart = mindSheet.indexOf("fun MindModeStatusSheet(")
        val ambientStart = mindSheet.indexOf("ImpulsiveAmbientBackground(", functionStart)
        val columnStart = mindSheet.indexOf("Column(", ambientStart)
        val columnModifier = mindSheet.substring(
            columnStart,
            mindSheet.indexOf(") {", columnStart),
        )

        assertTrue(columnModifier.contains(".fillMaxSize()"))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
        assertTrue(columnModifier.contains(".navigationBarsPadding()"))
        assertTrue(columnModifier.contains(".verticalScroll("))
    }

    @Test
    fun mindModeStatusSheetContainsNoBorderStroke() {
        assertFalse(mindSheet.contains("BorderStroke"))
    }

    @Test
    fun cardSurfaceIsThemeAwareAndCardBorderRemainsAbsent() {
        assertTrue(mindSheet.contains("val cardSurface = colorScheme.surface.copy("))
        assertFalse(mindSheet.contains("cardBorder"))
        assertFalse(mindSheet.contains("BorderStroke"))
    }

    @Test
    fun carouselAndPathwayAreEachContainedInARoundedSurface() {
        // Three root-level Surface calls: the carousel card, the pathway
        // card, and the Start Mind Pivot CTA.
        val root = mindSheet.section(
            "fun MindModeStatusSheet(",
            "private fun ActiveMindModeBadge(",
        )
        assertEquals(3, root.count("Surface("))
        assertTrue(root.contains("MindModeExplainerCarousel("))
        assertTrue(root.contains("MindModeDecisionTreeVisual("))
        assertEquals(2, root.count("shape = RoundedCornerShape(34.dp)"))
        assertEquals(2, root.count("color = cardSurface"))
        assertEquals(2, root.count("tonalElevation = 0.dp"))
    }

    @Test
    fun activeMindModeBadgeHasNoBorderArgumentOrPulseAnimation() {
        val badge = mindSheet.section(
            "private fun ActiveMindModeBadge(",
            "private enum class MindModeExplainerVisual",
        )
        assertFalse(badge.contains("border"))
        assertFalse(badge.contains("borderAlpha"))
        assertFalse(badge.contains("badgePulse"))
    }

    @Test
    fun oldHardcodedVisualPanelColoursAreAbsent() {
        assertFalse(mindSheet.contains("0xFF202832"))
        assertFalse(mindSheet.contains("0xFFF8F2FF"))
    }

    @Test
    fun mindModeSafeStepVisualNoLongerAppliesARectangularBackground() {
        assertFalse(mindSheet.contains("visualBackground"))
        assertFalse(mindSheet.contains(".clip(RoundedCornerShape(32.dp))"))
    }

    // ---- B. Body and Soul screens --------------------------------------

    @Test
    fun lockedModePreviewSheetUsesThemeBackgroundAndTextRoles() {
        val sheet = modeSheet.section(
            "private fun LockedModePreviewSheet(",
            "private fun LockedModeStep(",
        )
        assertTrue(sheet.contains("colorScheme.background"))
        assertTrue(sheet.contains("colorScheme.onBackground"))
        assertTrue(sheet.contains("colorScheme.onSurface"))
        assertTrue(sheet.contains("colorScheme.onSurfaceVariant"))
        assertTrue(sheet.contains("ImpulsiveAmbientBackground("))
    }

    @Test
    fun lockedModePreviewSheetRootBoxHasNoStatusBarPaddingBeforeAmbientLayer() {
        val functionStart = modeSheet.indexOf("private fun LockedModePreviewSheet(")
        val rootBoxStart = modeSheet.indexOf("Box(", functionStart)
        val ambientStart = modeSheet.indexOf("ImpulsiveAmbientBackground(", rootBoxStart)
        val rootBoxModifier = modeSheet.substring(rootBoxStart, ambientStart)

        assertTrue(rootBoxModifier.contains(".fillMaxSize()"))
        assertTrue(rootBoxModifier.contains(".background(colorScheme.background)"))
        assertFalse(rootBoxModifier.contains(".statusBarsPadding()"))

        val ambientBlock = modeSheet.substring(
            ambientStart,
            modeSheet.indexOf("Column(", ambientStart),
        )
        assertTrue(ambientBlock.contains("modifier = Modifier.fillMaxSize()"))
    }

    @Test
    fun lockedModePreviewSheetContentColumnOwnsExactlyOneStatusBarPadding() {
        val functionStart = modeSheet.indexOf("private fun LockedModePreviewSheet(")
        val ambientStart = modeSheet.indexOf("ImpulsiveAmbientBackground(", functionStart)
        val columnStart = modeSheet.indexOf("Column(", ambientStart)
        val columnModifier = modeSheet.substring(
            columnStart,
            modeSheet.indexOf(") {", columnStart),
        )

        assertTrue(columnModifier.contains(".fillMaxSize()"))
        assertEquals(1, columnModifier.count(".statusBarsPadding()"))
        assertTrue(columnModifier.contains(".navigationBarsPadding()"))
        assertTrue(columnModifier.contains(".verticalScroll("))
    }

    @Test
    fun modeSelectionSheetContainsNoBorderStroke() {
        assertFalse(modeSheet.contains("BorderStroke"))
    }

    @Test
    fun oldScreenBrushAndCardColourLocalsAreAbsent() {
        assertFalse(modeSheet.contains("screenBrush"))
        assertFalse(modeSheet.contains("cardColor"))
    }

    @Test
    fun oldPageAndCardColoursAreAbsentFromLockedModePreviewSheet() {
        listOf(
            "0xFF11161A",
            "0xFFFBF8FE",
            "0xFF171D22",
            "0xFFFFFBFF",
        ).forEach { staleColour ->
            assertFalse(staleColour, modeSheet.contains(staleColour))
        }
    }

    @Test
    fun bodyAndSoulAccentColoursRemainForSmallIdentityElements() {
        // Small identity elements (lock icon, symbol circle, step numbers,
        // LOCKED badge fill) may still use the mode-specific soft/accent pair.
        assertTrue(modeSheet.contains("bodyAccent"))
        assertTrue(modeSheet.contains("soulAccent"))
        assertTrue(modeSheet.contains("bodyBackground"))
        assertTrue(modeSheet.contains("soulBackground"))
    }

    // ---- C. Bubble selector --------------------------------------------

    @Test
    fun modeSelectorRestoresAnimatedThemeSpecificScrim() {
        assertTrue(modeSheet.contains("scrimAlpha"))
        assertTrue(modeSheet.contains("mode_bubble_scrim_alpha"))
        assertTrue(modeSheet.contains("Color(0x660A0710)"))
        assertTrue(modeSheet.contains("Color(0x44FFFFFF)"))
        val outer = modeSheet.section(
            "Box(modifier = Modifier.fillMaxSize()) {",
            ".align(Alignment.BottomCenter)",
        )
        assertTrue(outer.contains(".background("))
        assertTrue(outer.contains(".fillMaxSize()"))
        assertTrue(outer.contains("requestClose()"))
    }

    @Test
    fun outsideDismissBoxStillFillsScreenUsesClickableAndCallsRequestClose() {
        val outer = modeSheet.section(
            "Box(modifier = Modifier.fillMaxSize()) {",
            ".align(Alignment.BottomCenter)",
        )
        assertTrue(outer.contains(".fillMaxSize()"))
        assertTrue(outer.contains("clickable("))
        assertTrue(outer.contains("requestClose()"))
        assertTrue(outer.contains(".background("))
    }

    @Test
    fun modeBubbleHasNoBorderParameterOrBorderStroke() {
        val bubble = modeSheet.substring(modeSheet.indexOf("private fun ModeBubble("))
        assertFalse(bubble.contains("border: Color"))
        assertFalse(bubble.contains("BorderStroke"))
    }

    @Test
    fun allThreeBubblesStillUseModeBubbleWithPositionsAndSizesIntact() {
        val callSites = modeSheet.substring(0, modeSheet.indexOf("private fun ModeBubble("))
        assertEquals(3, callSites.count("ModeBubble("))
        assertTrue(modeSheet.contains(".size(96.dp)"))
        assertTrue(modeSheet.contains("mindX"))
        assertTrue(modeSheet.contains("bodyY"))
        assertTrue(modeSheet.contains("soulX"))
    }

    @Test
    fun reducedMotionAndLevitationBehaviourRemainPresent() {
        assertTrue(modeSheet.contains("reducedMotion"))
        assertTrue(modeSheet.contains("levitation"))
    }

    // ---- D. Mind pathway protection -------------------------------------

    @Test
    fun mindPathwayModelAndCopyRemainUntouchedByTheVisualCleanup() {
        assertTrue(mindSheet.contains("private enum class MindModeExplainerVisual"))
        assertTrue(mindSheet.contains("title = \"Short Pause\""))
        assertTrue(mindSheet.contains("title = \"Pivot Game\""))
        assertTrue(mindSheet.contains("title = \"Reset Reading\""))
        assertTrue(mindSheet.contains("title = \"Moment Plan\""))
        assertTrue(mindSheet.contains("Reflex • Block • SkyStack • Rhythm"))
        assertTrue(mindSheet.contains("Outcome recorded"))
        assertTrue(mindSheet.contains("Private learning"))
        assertTrue(mindSheet.contains("MindModeDecisionTreeList("))
        assertTrue(mindSheet.contains("LocalDensity.current.fontScale >= 1.6f"))
        assertTrue(mindSheet.contains("Settings.Global.ANIMATOR_DURATION_SCALE"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
