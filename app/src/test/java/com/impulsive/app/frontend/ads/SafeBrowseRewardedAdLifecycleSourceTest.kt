package com.impulsive.app.frontend.ads

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contained repair 1 of 2: rewarded ads must never reload or grant through a stale
 * asynchronous callback that lands after route disposal, an access change, or Safe Browse
 * Pass activation. Every SDK callback path is checked here at the source level against the
 * generation + [SafeBrowseAdEligibility] guard added to [SafeBrowseRewardedAdController].
 */
class SafeBrowseRewardedAdLifecycleSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseRewardedAdController.kt",
    ).readText()

    private fun blockBetween(startMarker: String, endMarker: String, from: Int = 0): String {
        val start = source.indexOf(startMarker, from)
        assertTrue("marker not found: $startMarker", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("end marker not found after $startMarker: $endMarker", end > start)
        return source.substring(start, end)
    }

    @Test
    fun clearPreventsALateOnAdLoadedCallbackFromPublishingReady() {
        // clear() bumps generation via invalidateCurrentAd(), so a late onAdLoaded for a
        // now-stale requestGeneration must be rejected before ever setting state to Ready.
        val onAdLoadedBlock = blockBetween(
            "override fun onAdLoaded(ad: RewardedAd) {",
            "override fun onAdFailedToLoad",
        )
        val guardIndex = onAdLoadedBlock.indexOf("isCurrentAndEligible(requestGeneration)")
        val readyIndex = onAdLoadedBlock.indexOf("SafeBrowseRewardedAdState.Ready")
        assertTrue("onAdLoaded must check isCurrentAndEligible", guardIndex >= 0)
        assertTrue("Ready must only be set after the guard", guardIndex in 0 until readyIndex)

        val clearBlock = source.substring(source.indexOf("fun clear()"))
        assertTrue(clearBlock.contains("invalidateCurrentAd()"))
        val invalidateBlock = blockBetween("private fun invalidateCurrentAd()", "private fun maybeLoadAd()")
        assertTrue(invalidateBlock.contains("generation += 1L"))
    }

    @Test
    fun clearPreventsADismissalCallbackFromStartingAFreshReload() {
        val dismissedBlock = blockBetween(
            "override fun onAdDismissedFullScreenContent()",
            "override fun onAdFailedToShowFullScreenContent",
        )
        assertTrue(dismissedBlock.contains("reloadAfterShow(showGeneration)"))

        val reloadBlock = blockBetween("private fun reloadAfterShow(showGeneration: Long) {", "fun clear()")
        // reloadAfterShow rejects a stale showGeneration (bumped by the clear() that
        // happened while the ad was on screen) before it can call maybeLoadAd().
        assertTrue(reloadBlock.contains("isCurrentAndEligible(showGeneration)"))
        assertTrue(reloadBlock.contains("if (!isCurrentAndEligible(showGeneration)) {"))
    }

    @Test
    fun preloadWithIneligibleStateInvalidatesTheCurrentAdAndItsGeneration() {
        val preloadBlock = blockBetween("fun preload(eligibility: SafeBrowseAdEligibility) {", "private fun invalidateCurrentAd()")
        assertTrue(preloadBlock.contains("currentEligibility = eligibility"))
        assertTrue(preloadBlock.contains("!eligibility.eligibleToPreload"))
        assertTrue(preloadBlock.contains("invalidateCurrentAd()"))

        val invalidateBlock = blockBetween("private fun invalidateCurrentAd() {", "private fun maybeLoadAd()")
        assertTrue(invalidateBlock.contains("generation += 1L"))
        assertTrue(invalidateBlock.contains("loaded = null"))
        assertTrue(invalidateBlock.contains("SafeBrowseRewardedAdState.Unavailable"))
    }

    @Test
    fun showCannotStartWhileCurrentlyIneligibleEvenIfAnAdIsStillLoaded() {
        val showBlock = blockBetween("fun show(", "private fun reloadAfterShow(showGeneration: Long)")
        val guardBlock = showBlock.substring(0, showBlock.indexOf("val showGeneration ="))
        assertTrue(guardBlock.contains("!currentEligibility.eligibleToPreload"))
        assertTrue(guardBlock.contains("current == null"))
        assertTrue(guardBlock.contains("current.generation != generation"))
    }

    @Test
    fun aLateRewardCallbackAfterIneligibilityCannotCallOnReward() {
        val showBlock = blockBetween("fun show(", "private fun reloadAfterShow(showGeneration: Long)")
        val listenerIndex = showBlock.indexOf("current.ad.show(activity)")
        val listenerBlock = showBlock.substring(listenerIndex)

        val guardIndex = listenerBlock.indexOf("showGeneration == generation")
        val eligibilityIndex = listenerBlock.indexOf("currentEligibility.eligibleToPreload")
        val onRewardIndex = listenerBlock.indexOf("onReward(")

        assertTrue("listener must check generation", guardIndex >= 0)
        assertTrue("listener must check current eligibility", eligibilityIndex >= 0)
        assertTrue("both guards must precede onReward()", guardIndex < onRewardIndex && eligibilityIndex < onRewardIndex)
    }

    @Test
    fun validRewardUsesTheTokenMintedAtLoadTimeNotAFreshOne() {
        val showBlock = blockBetween("fun show(", "private fun reloadAfterShow(showGeneration: Long)")
        val listenerIndex = showBlock.indexOf("current.ad.show(activity)")
        val listenerBlock = showBlock.substring(listenerIndex)

        assertTrue(listenerBlock.contains("onReward(current.receiptToken)"))
        assertFalse(listenerBlock.contains("UUID.randomUUID()"))
    }

    @Test
    fun duplicateRewardCallsAreBlockedByTheAtomicConsumedGuard() {
        val showBlock = blockBetween("fun show(", "private fun reloadAfterShow(showGeneration: Long)")
        val listenerIndex = showBlock.indexOf("current.ad.show(activity)")
        val listenerBlock = showBlock.substring(listenerIndex)

        val consumedIndex = listenerBlock.indexOf("current.consumed.compareAndSet(false, true)")
        val onRewardIndex = listenerBlock.indexOf("onReward(")
        assertTrue("consumed guard must gate onReward", consumedIndex in 0 until onRewardIndex)

        // The guard is combined with the other two checks via && so any single failing
        // condition -- including a second call for the same already-consumed ad -- blocks
        // the grant.
        val conditionBlock = listenerBlock.substring(
            listenerBlock.indexOf("if ("),
            listenerBlock.indexOf(") {", listenerBlock.indexOf("if (")),
        )
        assertTrue(conditionBlock.contains("&&"))
    }

    @Test
    fun dismissalAndShowFailureCallbacksContainNoRewardCall() {
        val dismissedBlock = blockBetween(
            "override fun onAdDismissedFullScreenContent()",
            "override fun onAdFailedToShowFullScreenContent",
        )
        val failedBlock = blockBetween(
            "override fun onAdFailedToShowFullScreenContent(adError: AdError) {",
            "override fun onAdShowedFullScreenContent",
        )
        assertFalse(dismissedBlock.contains("onReward("))
        assertFalse(failedBlock.contains("onReward("))
    }

    @Test
    fun reloadAfterShowNeverCallsLoadAdDirectlyOnlyMaybeLoadAd() {
        val reloadBlock = blockBetween("private fun reloadAfterShow(showGeneration: Long) {", "fun clear()")
        assertTrue(reloadBlock.contains("maybeLoadAd()"))
        assertFalse(reloadBlock.contains("loadAd("))

        // maybeLoadAd() itself re-derives the request generation and rechecks eligibility
        // and current controller state before ever calling the real loadAd(unitId, ...).
        val maybeLoadBlock = blockBetween(
            "private fun maybeLoadAd() {",
            "private fun ensureMobileAdsInitialized",
        )
        assertTrue(maybeLoadBlock.contains("!currentEligibility.eligibleToPreload"))
        assertTrue(maybeLoadBlock.contains("SafeBrowseRewardedAdState.Loading,"))
        assertTrue(maybeLoadBlock.contains("SafeBrowseRewardedAdState.Ready,"))
        assertTrue(maybeLoadBlock.contains("SafeBrowseRewardedAdState.Showing,"))
    }
}
