package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionInterruptionOverlaySourceTest {
    private val source =
        File(
            "src/main/java/com/impulsive/app/backend/service/protection/ProtectionInterruptionOverlay.kt",
        ).readText()

    @Test
    fun `overlay commits visible root state when shown`() {
        val markShown =
            source.substring(
                source.indexOf("fun markShown()"),
                source.indexOf("view.addOnAttachStateChangeListener"),
            )

        assertFalse(markShown.contains("view.alpha = 0f"))
        assertTrue(markShown.contains("view.visibility = View.VISIBLE"))
        assertTrue(markShown.contains("view.alpha = 1f"))
        assertTrue(markShown.contains("view.invalidate()"))
        assertTrue(markShown.contains("view.postInvalidateOnAnimation()"))
        assertFalse(source.contains("FadeInMillis"))
    }

    @Test
    fun `create view leaves required controls visible before attachment`() {
        val createView =
            source.substring(
                source.indexOf("private fun createView"),
                source.indexOf("private fun configureResetStatus"),
            )

        assertTrue(createView.contains("visibility = View.VISIBLE"))
        assertTrue(createView.contains("alpha = 1f"))
        assertTrue(createView.contains("resetChoices.alpha = 1f"))
        assertTrue(createView.contains("footer.alpha = 1f"))
        assertFalse(createView.contains("section.alpha = 0f"))
        assertFalse(createView.contains("ChoicesFadeDelayMillis"))
        assertFalse(createView.contains("ChoicesFadeDurationMillis"))
    }

    @Test
    fun `focus branch renders a distinct truthful focus identity`() {
        assertTrue(source.contains("private const val FocusEyebrowText = \"FOCUS MODE\""))
        assertTrue(
            source.contains(
                "private const val FocusPrimaryMessage = \"Focus Mode is active.\"",
            ),
        )
        assertTrue(
            source.contains(
                "private const val FocusSupportingMessage = " +
                    "\"This app is blocked until your focus timer ends.\"",
            ),
        )
        assertTrue(
            source.contains(
                "private const val FocusPrimaryActionLabel = \"Review focus options\"",
            ),
        )
        assertTrue(source.contains("private val FocusCoralColor = Color.parseColor(\"#F5A7A6\")"))
    }

    @Test
    fun `focus choice branch routes exactly once to focus recovery without game or reading`() {
        val resetChoicesBlock = source.substring(
            source.indexOf("// One explicit branch per interruption identity:"),
            source.indexOf("val footer = LinearLayout"),
        )
        val focusChoiceBranch = resetChoicesBlock.substring(
            resetChoicesBlock.indexOf("isFocusSession ->"),
            resetChoicesBlock.indexOf("adaptiveDecisionId != null ->"),
        )
        val genericChoiceBranch = resetChoicesBlock.substring(
            resetChoicesBlock.indexOf("else -> {"),
        )

        assertTrue(focusChoiceBranch.contains("FocusPrimaryActionLabel"))
        assertTrue(focusChoiceBranch.contains("FocusCoralColor"))
        assertTrue(focusChoiceBranch.contains("BlockLaunchTarget.FocusRecovery"))
        assertEquals(1, focusChoiceBranch.split("resetChoices.addView(").size - 1)
        assertFalse(focusChoiceBranch.contains("Pivot by Game"))
        assertFalse(focusChoiceBranch.contains("Pivot by Reading"))
        assertFalse(focusChoiceBranch.contains("BlockLaunchTarget.RandomRecoveryGame"))
        assertFalse(focusChoiceBranch.contains("BlockLaunchTarget.ReadingReset"))

        assertTrue(genericChoiceBranch.contains("Pivot by Game"))
        assertTrue(genericChoiceBranch.contains("Pivot by Reading"))
    }

    @Test
    fun `focus footer never renders continue deliberately or ordinary cooldown copy`() {
        val footerBlock = source.substring(
            source.indexOf("footer.addView(softAction(context, \"Leave this app\")"),
            source.indexOf("card.addView(\n            footer,"),
        )
        val focusFooterBranch = footerBlock.substring(
            footerBlock.indexOf("isFocusSession -> {"),
            footerBlock.indexOf("else -> {"),
        )

        assertTrue(footerBlock.contains("\"Leave this app\""))
        assertFalse(focusFooterBranch.contains("Continue deliberately"))
        assertFalse(focusFooterBranch.contains("configureResetStatus"))

        val genericAppMonitorBranch = footerBlock.substring(
            footerBlock.indexOf("!isFocusSession && owner == Owner.AppMonitor -> {"),
            footerBlock.indexOf("isFocusSession -> {"),
        )
        assertTrue(genericAppMonitorBranch.contains("Continue deliberately"))
    }

    @Test
    fun `overlay avoids full-screen intent behaviour`() {
        assertFalse(source.contains("setFullScreenIntent"))
        assertFalse(source.contains("FullScreenIntent"))
    }
}