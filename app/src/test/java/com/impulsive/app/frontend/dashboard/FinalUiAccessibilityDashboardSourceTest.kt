package com.impulsive.app.frontend.dashboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalUiAccessibilityDashboardSourceTest {
    private val homeCard = source("frontend/components/HomeSupportFeatureCard.kt")
    private val modeSheet = source("frontend/components/ModeSelectionSheet.kt")
    private val progress = source("frontend/screens/progress/ProgressDashboardScreen.kt")
    private val mindSheet = source("frontend/components/MindModeStatusSheet.kt")

    @Test
    fun sharedHomeSupportCardsUseThemeRolesAndFixedAlignmentGrid() {
        assertTrue(homeCard.contains("contentColorFor(surfaceColor)"))
        assertTrue(homeCard.contains("scheme.onSurfaceVariant"))
        assertTrue(homeCard.contains("scheme.primary"))
        assertTrue(homeCard.contains(".size(48.dp)"))
        assertTrue(homeCard.contains(".width(14.dp)"))
        assertTrue(homeCard.contains(".heightIn(min = 96.dp)"))
        assertFalse(homeCard.contains("Color(0xFF171D22)"))
        assertFalse(homeCard.contains("Color(0xFFF7F2FF)"))
    }

    @Test
    fun mindBodySoulModeBubblesUseEqualCircularBounds() {
        val bubble = modeSheet.substring(modeSheet.indexOf("private fun ModeBubble"))
        assertTrue(modeSheet.contains(".height(180.dp)"))
        assertTrue(bubble.contains("shape = CircleShape"))
        assertTrue(bubble.contains(".size(96.dp)"))
        assertTrue(bubble.contains("translationY = verticalTranslationDp * density"))
        assertFalse(bubble.contains(".heightIn(min = 122.dp)"))
    }

    @Test
    fun progressMetricsStackAndRespectBottomNavigationSpace() {
        val records = progress.section("private fun ScoreRecordsCard", "private fun ScoreFlipFaceSurface")
        assertTrue(progress.contains(".navigationBarsPadding()"))
        assertTrue(progress.contains("bottom = bottomNavReservedSpace + 40.dp"))
        assertTrue(records.contains("Box("))
        assertTrue(records.contains(".height(ScoreFlipCardHeight)"))
        assertFalse(records.contains(".width(1.dp)\n                        .height(96.dp)"))
        val resetMetric = progress.section("private fun ResetReadMetricPill", "private fun SafeExitAndUrgeCards")
        assertFalse(resetMetric.contains("overflow = TextOverflow.Ellipsis"))
    }

    @Test
    fun mindPivotExplanationUsesVerticalSequenceAtHighFontScale() {
        assertTrue(mindSheet.contains("LocalDensity.current.fontScale >= 1.6f"))
        assertTrue(mindSheet.contains("MindModeDecisionTreeList("))
        assertTrue(mindSheet.contains("\"Wait cut + LP\""))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
