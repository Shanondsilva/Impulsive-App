package com.impulsive.app.frontend.screens.safebrowse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePresentationTest {

    private fun state(
        accessState: SafeBrowseAccessState,
        rewardedUnlockAvailable: Boolean = false,
        browserOpeningAvailable: Boolean = false,
        passPurchaseAvailable: Boolean = false,
        passPriceLabel: String? = null,
    ) = SafeBrowseUiState(
        accessState = accessState,
        rewardedUnlockAvailable = rewardedUnlockAvailable,
        browserOpeningAvailable = browserOpeningAvailable,
        passPurchaseAvailable = passPurchaseAvailable,
        passPriceLabel = passPriceLabel,
    )

    @Test
    fun setupPendingHasBothActionsDisabledAndNoPrimaryAction() {
        val presentation = state(SafeBrowseAccessState.SetupPending).toPresentation()
        assertEquals("Safe Browse is being prepared", presentation.title)
        assertNull(presentation.primaryAction)
        assertEquals(false, presentation.primaryActionEnabled)
        assertEquals(false, presentation.secondaryActionEnabled)
    }

    @Test
    fun lockedActionIsWatchRewardedAdAndFollowsAvailability() {
        val enabled = state(
            SafeBrowseAccessState.Locked,
            rewardedUnlockAvailable = true,
        ).toPresentation()
        assertEquals(SafeBrowseAction.WatchRewardedAd, enabled.primaryAction)
        assertTrue(enabled.primaryActionEnabled)

        val disabled = state(
            SafeBrowseAccessState.Locked,
            rewardedUnlockAvailable = false,
        ).toPresentation()
        assertEquals(SafeBrowseAction.WatchRewardedAd, disabled.primaryAction)
        assertEquals(false, disabled.primaryActionEnabled)
    }

    @Test
    fun activeActionIsOpenBrowserAndFollowsAvailability() {
        val enabled = state(
            SafeBrowseAccessState.Active(remainingSeconds = 7_140L),
            browserOpeningAvailable = true,
        ).toPresentation()
        assertEquals(SafeBrowseAction.OpenBrowser, enabled.primaryAction)
        assertTrue(enabled.primaryActionEnabled)
        assertTrue(enabled.stateDescription.contains("1h 59m"))

        val disabled = state(
            SafeBrowseAccessState.Active(remainingSeconds = 7_140L),
            browserOpeningAvailable = false,
        ).toPresentation()
        assertEquals(false, disabled.primaryActionEnabled)
    }

    @Test
    fun activeWithPassActiveShowsAdFreeCopy() {
        val presentation = state(
            SafeBrowseAccessState.Active(remainingSeconds = 100L, passActive = true),
            browserOpeningAvailable = true,
        ).toPresentation()
        assertEquals("Safe Browse Pass is active", presentation.title)
        assertEquals("Ad-free Safe Browse is available", presentation.stateDescription)
    }

    @Test
    fun expiredReturnsToWatchRewardedAd() {
        val presentation = state(
            SafeBrowseAccessState.Expired,
            rewardedUnlockAvailable = true,
        ).toPresentation()
        assertEquals(SafeBrowseAction.WatchRewardedAd, presentation.primaryAction)
        assertEquals("Time is up", presentation.title)
        assertTrue(presentation.primaryActionEnabled)
    }

    @Test
    fun errorUsesRealNonBlankMessage() {
        val presentation = state(
            SafeBrowseAccessState.Error("Network unavailable"),
        ).toPresentation()
        assertEquals("Network unavailable", presentation.body)
        assertEquals(SafeBrowseAction.Retry, presentation.primaryAction)
        assertTrue(presentation.primaryActionEnabled)
    }

    @Test
    fun errorWithBlankMessageUsesFallback() {
        val presentation = state(SafeBrowseAccessState.Error("   ")).toPresentation()
        assertEquals(
            "Safe Browse could not be loaded. Try again shortly.",
            presentation.body,
        )
        assertTrue(presentation.primaryActionEnabled)
    }

    @Test
    fun remainingTimeFormatting() {
        assertEquals("Less than 1 min", formatSafeBrowseRemainingTime(-1L))
        assertEquals("Less than 1 min", formatSafeBrowseRemainingTime(59L))
        assertEquals("1m", formatSafeBrowseRemainingTime(60L))
        assertEquals("59m", formatSafeBrowseRemainingTime(3_599L))
        assertEquals("1h", formatSafeBrowseRemainingTime(3_600L))
        assertEquals("1h 59m", formatSafeBrowseRemainingTime(7_140L))
    }
}
