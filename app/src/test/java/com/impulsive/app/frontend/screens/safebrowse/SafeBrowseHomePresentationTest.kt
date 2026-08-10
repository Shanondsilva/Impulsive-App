package com.impulsive.app.frontend.screens.safebrowse

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeBrowseHomePresentationTest {

    private fun state(accessState: SafeBrowseAccessState) = SafeBrowseUiState(
        accessState = accessState,
        rewardedUnlockAvailable = false,
        browserOpeningAvailable = false,
        passPurchaseAvailable = false,
        passPriceLabel = null,
    )

    @Test
    fun setupPending() {
        val presentation = state(SafeBrowseAccessState.SetupPending).toHomePresentation()
        assertEquals("Safe browsing setup is being completed", presentation.supportingText)
    }

    @Test
    fun locked() {
        val presentation = state(SafeBrowseAccessState.Locked).toHomePresentation()
        assertEquals("Watch an ad to unlock 2 hours", presentation.supportingText)
    }

    @Test
    fun active() {
        val presentation =
            state(SafeBrowseAccessState.Active(remainingSeconds = 7_140L)).toHomePresentation()
        assertEquals("1h 59m remaining", presentation.supportingText)
    }

    @Test
    fun passActive() {
        val presentation = state(
            SafeBrowseAccessState.Active(remainingSeconds = 100L, passActive = true),
        ).toHomePresentation()
        assertEquals("Ad-free Safe Browse", presentation.supportingText)
    }

    @Test
    fun expired() {
        val presentation = state(SafeBrowseAccessState.Expired).toHomePresentation()
        assertEquals("Unlock another safe session", presentation.supportingText)
    }

    @Test
    fun error() {
        val presentation = state(SafeBrowseAccessState.Error("boom")).toHomePresentation()
        assertEquals("Safe Browse is temporarily unavailable", presentation.supportingText)
    }
}
