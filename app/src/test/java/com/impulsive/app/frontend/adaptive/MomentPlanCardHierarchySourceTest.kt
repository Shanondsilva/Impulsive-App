package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanCardHierarchySourceTest {
    private val screens = source(
        "frontend/screens/adaptive/MomentPlanScreens.kt",
    )
    private val home = source(
        "frontend/screens/dashboard/HomeScreen.kt",
    )

    private fun planListCard(): String = screens.section(
        "private fun PlanListCard(",
        "fun MomentPlanEditorScreen(",
    )

    @Test
    fun planListCardUsesCentralisedDisplayHelpers() {
        val card = planListCard()
        assertTrue(card.contains("MomentPlanPresentation.displayTitle(plan)"))
        assertTrue(card.contains("MomentPlanPresentation.displayAction(plan)"))
        assertTrue(card.contains("displayTitle,"))
        assertTrue(card.contains("displayAction,"))
        assertFalse(card.contains("plan.title,"))
        assertFalse(card.contains("plan.actionText,"))
    }

    @Test
    fun titleRowNoLongerSharesSpaceWithThePreferredChip() {
        val card = planListCard()
        val titleRowToChip = card.substring(
            card.indexOf("Row(verticalAlignment = Alignment.CenterVertically) {"),
            card.indexOf("AssistChip("),
        )
        assertFalse(titleRowToChip.contains("moment_plan_preferred"))
        assertTrue(card.indexOf("FlowRow(") < card.indexOf("AssistChip("))
        assertTrue(card.indexOf("AssistChip(") > card.indexOf("IconButton(onClick = { menuOpen = true })"))
    }

    @Test
    fun openPlanCtaIsFullWidthWithATrailingNavigationIcon() {
        val card = planListCard()
        assertTrue(card.contains("TextButton("))
        val ctaBlock = card.substring(card.indexOf("TextButton("))
        assertTrue(ctaBlock.contains(".fillMaxWidth()"))
        assertTrue(ctaBlock.contains(".heightIn(min = 48.dp)"))
        assertTrue(ctaBlock.contains("Arrangement.SpaceBetween"))
        assertTrue(ctaBlock.contains("Icons.AutoMirrored.Filled.KeyboardArrowRight"))
        assertTrue(ctaBlock.contains("contentDescription = null"))
        assertFalse(card.contains(".align(Alignment.End)"))
    }

    @Test
    fun homeCompactCardUsesCentralisedDisplayHelpers() {
        val compactCard = home.section(
            "private fun MomentPlanCompactCard(",
            "SmallActionCard(",
        )
        assertTrue(compactCard.contains("MomentPlanPresentation.displayTitle(it)"))
        assertTrue(compactCard.contains("MomentPlanPresentation.displayAction(it)"))
        assertFalse(compactCard.contains("plan.actionText"))
        assertFalse(compactCard.contains("\"Your plan\""))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
