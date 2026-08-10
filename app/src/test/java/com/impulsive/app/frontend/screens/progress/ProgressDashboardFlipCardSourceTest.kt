package com.impulsive.app.frontend.screens.progress

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDashboardFlipCardSourceTest {
    private val screen = source(
        "frontend/screens/progress/ProgressDashboardScreen.kt",
    )

    @Test
    fun scoreAndResetReadingCardsRemainSideBySideAtAllWidths() {
        val arrangement = cardArrangementBlock()

        assertTrue(arrangement.contains("ScoreRecordsCard("))
        assertTrue(arrangement.contains("ResetReadingProgressCard("))
        assertTrue(arrangement.count(".weight(1f)") >= 2)
        assertTrue(arrangement.contains("Arrangement.spacedBy(12.dp)"))
        assertTrue(arrangement.contains(".height(ScoreFlipCardHeight)"))
        assertFalse(arrangement.contains("Column("))
    }

    @Test
    fun scoreCardsDoNotUseCompactStackingBreakpoint() {
        assertFalse(screen.contains("BoxWithConstraints("))
        assertFalse(screen.contains("maxWidth < 600.dp"))
    }

    private fun cardArrangementBlock(): String = screen.section(
        "Row(",
        "Spacer(modifier = Modifier.height(26.dp))",
    )

    @Test
    fun resetReadingUsesApprovedHomeBookIcon() {
        val face = screen.section(
            "private fun ResetReadingFlipFaceSurface(",
            "private fun ResetReadingCompactMetric(",
        )
        assertTrue(face.contains("Icons.AutoMirrored.Outlined.MenuBook"))
        assertFalse(face.contains("Icons.Filled.AutoAwesome"))
    }

    @Test
    fun scoreRecordsCardHasNoWholeCardClickHandlingAndUsesFlipButton() {
        val card = screen.section(
            "private fun ScoreRecordsCard(",
            "private fun ScoreFlipFaceSurface(",
        )
        assertFalse(card.contains(".clickable("))
        assertFalse(card.contains("Role.Button"))
        assertTrue(card.contains("ScoreFlipActionButton("))
    }

    @Test
    fun resetReadingProgressCardHasNoWholeCardClickHandlingAndUsesFlipButton() {
        val card = screen.section(
            "private fun ResetReadingProgressCard(",
            "private fun ResetReadingFlipFaceSurface(",
        )
        assertFalse(card.contains(".clickable("))
        assertFalse(card.contains("Role.Button"))
        assertTrue(card.contains("ScoreFlipActionButton("))
    }

    @Test
    fun sharedFlipActionButtonUsesFortyEightDpTouchTargetAndRequestManualFlip() {
        val button = screen.section(
            "private fun ScoreFlipActionButton(",
            "private fun PersonalBestsSection(",
        )
        assertTrue(button.contains("IconButton("))
        assertTrue(button.contains(".size(48.dp)"))
        assertTrue(card().contains("onClick = requestManualFlip"))
        assertTrue(resetCard().contains("onClick = requestManualFlip"))
    }

    @Test
    fun scoreFlipHeaderPermitsTwoLinesWithoutEllipsis() {
        val header = screen.section(
            "private fun ScoreFlipHeader(",
            "private fun ScoreFlipActionButton(",
        )
        assertTrue(
            header.contains(
                "min = ScoreFlipHeaderMinHeight",
            ),
        )
        assertTrue(header.contains("maxLines = 2"))
        assertTrue(header.contains("TextOverflow.Clip"))
        assertFalse(header.contains("TextOverflow.Ellipsis"))
        assertFalse(header.contains(".height(44.dp)"))
    }

    @Test
    fun scoreFlipHeaderUsesColumnWithIconRowSeparateFromHeading() {
        val header = screen.section(
            "private fun ScoreFlipHeader(",
            "private fun ScoreFlipActionButton(",
        )
        assertTrue(header.contains("Column("))
        val iconRow = header.section("Row(", "Spacer(modifier = Modifier.height(6.dp))")
        assertFalse(iconRow.contains("Text("))
        assertTrue(header.indexOf("Text(") > header.indexOf("Spacer(modifier = Modifier.height(6.dp))"))
    }

    @Test
    fun scoreAndResetReadingHeadersUseTheSameSharedIconDimensions() {
        assertTrue(
            screen.contains(
                "private val ScoreFlipHeaderMinHeight = 40.dp",
            ),
        )
        assertTrue(
            screen.contains(
                "private val ScoreFlipHeaderBadgeSize = 40.dp",
            ),
        )
        assertTrue(
            screen.contains(
                "private val ScoreFlipHeaderIconSize = 22.dp",
            ),
        )

        val scoreHeader =
            screen.section(
                "private fun ScoreFlipHeader(",
                "private fun ScoreFlipActionButton(",
            )

        val resetReadingHeader =
            screen.section(
                "private fun ResetReadingFlipFaceSurface(",
                "private fun ResetReadingCompactMetric(",
            )

        listOf(
            scoreHeader,
            resetReadingHeader,
        ).forEach { header ->
            assertTrue(
                header.contains(
                    "min = ScoreFlipHeaderMinHeight",
                ),
            )
            assertTrue(
                header.contains(
                    "ScoreFlipHeaderBadgeSize",
                ),
            )
            assertTrue(
                header.contains(
                    "ScoreFlipHeaderIconSize",
                ),
            )
        }

        assertFalse(
            resetReadingHeader.contains(
                "heightIn(min = 32.dp)",
            ),
        )
        assertFalse(
            resetReadingHeader.contains(
                ".size(32.dp)",
            ),
        )
        assertFalse(
            resetReadingHeader.contains(
                ".size(17.dp)",
            ),
        )
    }

    @Test
    fun scoreFlipHeaderRetainsExactEyebrowStringResourcesAndIcons() {
        assertTrue(screen.contains("R.string.v28_personal_best_eyebrow"))
        assertTrue(screen.contains("R.string.v28_recent_session_eyebrow"))
        assertTrue(screen.contains("Icons.Outlined.EmojiEvents"))
        assertTrue(screen.contains("Icons.Outlined.History"))
    }

    @Test
    fun resetReadingHeaderUsesColumnWithIconRowSeparateFromHeading() {
        val face = screen.section(
            "private fun ResetReadingFlipFaceSurface(",
            "private fun ResetReadingCompactMetric(",
        )
        assertTrue(face.contains("Column(modifier = Modifier.fillMaxWidth())"))
        val iconRow = face.section("Row(", "Spacer(modifier = Modifier.height(6.dp))")
        assertFalse(iconRow.contains("Text("))
        assertTrue(face.contains("maxLines = 2"))
        assertTrue(face.contains("TextOverflow.Clip"))
    }

    @Test
    fun resetReadingDefinesAndUsesHomeGreenAccentForShadowBorderIconAndHeading() {
        assertTrue(screen.contains("private val ResetReadingGreenGlow = Color(0xFF93E9BE)"))

        val card = screen.section(
            "private fun ResetReadingProgressCard(",
            "private fun ResetReadingFlipFaceSurface(",
        )
        assertTrue(card.contains("val accent = ResetReadingGreenGlow"))
        assertTrue(card.contains("ambientColor = accent.copy(alpha = if (isDark) 0.22f else 0.08f)"))
        assertTrue(card.contains("spotColor = accent.copy(alpha = if (isDark) 0.16f else 0.08f)"))

        val face = screen.section(
            "private fun ResetReadingFlipFaceSurface(",
            "private fun ResetReadingCompactMetric(",
        )
        assertTrue(face.contains("BorderStroke(1.dp, accent.copy(alpha = 0.58f))"))
        assertTrue(face.contains("tint = if (isDark) accent else colors.text.copy(alpha = 0.74f)"))
        assertTrue(face.contains("color = if (isDark) accent else colors.muted"))
        assertFalse(face.contains("colors.lavenderGlow"))
        assertFalse(face.contains("ImpulsivePsychological"))
    }

    private fun card(): String = screen.section(
        "private fun ScoreRecordsCard(",
        "private fun ScoreFlipFaceSurface(",
    )

    private fun resetCard(): String = screen.section(
        "private fun ResetReadingProgressCard(",
        "private fun ResetReadingFlipFaceSurface(",
    )

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
