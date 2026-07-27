package com.impulsive.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityOverlayDismissalPolicyTest {
    private val source = File(
        "src/main/java/com/impulsive/app/MainActivity.kt",
    ).readText()

    @Test
    fun resumeKeepsOverlayWhileBlockRequestIsPending() {
        val onResume = source.substring(
            source.indexOf("override fun onResume()"),
            source.indexOf("/**", source.indexOf("override fun onResume()")),
        )

        assertTrue(onResume.contains("if (pendingBlockRequest.value == null)"))
        assertTrue(onResume.contains("ProtectionInterruptionOverlay.dismissAny()"))
    }

    @Test
    fun destinationReadyCallbackDismissesThenClearsRequest() {
        val callback = source.substring(
            source.indexOf("onBlockRequestConsumed ="),
            source.indexOf("initialJournalNoteId ="),
        )
        val dismissIndex = callback.indexOf("ProtectionInterruptionOverlay.dismissAny()")
        val clearIndex = callback.indexOf("pendingBlockRequest.value = null")

        assertTrue(dismissIndex >= 0)
        assertTrue(clearIndex > dismissIndex)
    }

    @Test
    fun appLockDismissesOverlayWithoutConsumingPendingRequest() {
        val lockPolicy = source.substring(
            source.indexOf("LaunchedEffect(locked, pendingBlockRequest.value)"),
            source.indexOf("ImpulsiveTheme(darkTheme = useDark)"),
        )

        assertTrue(lockPolicy.contains("locked && pendingBlockRequest.value != null"))
        assertTrue(lockPolicy.contains("ProtectionInterruptionOverlay.dismissAny()"))
        assertFalse(lockPolicy.contains("pendingBlockRequest.value = null"))
        assertTrue(source.contains("onUnlocked = { unlockedThisSession.value = true }"))
    }
}
