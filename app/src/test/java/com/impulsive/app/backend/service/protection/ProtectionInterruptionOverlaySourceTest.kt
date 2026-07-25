package com.impulsive.app.backend.service.protection

import java.io.File
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
}