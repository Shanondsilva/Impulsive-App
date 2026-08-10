package com.impulsive.app.frontend.ads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseRewardedAdSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseRewardedAdController.kt",
    ).readText()

    @Test
    fun rewardIsGrantedOnlyFromTheEarnedRewardListener() {
        val showIndex = source.indexOf("fun show(")
        assertTrue(showIndex >= 0)
        val showBlock = source.substring(showIndex, source.indexOf("private fun reloadAfterShow(", showIndex))

        assertTrue(showBlock.contains("ad.show(activity)"))
        assertTrue(showBlock.contains("onReward("))

        // The reward call must appear only inside the ad.show(...) trailing lambda
        // (the OnUserEarnedRewardListener), not anywhere else in this block.
        val onRewardCallCount = Regex("onReward\\(").findAll(showBlock).count()
        assertEquals(1, onRewardCallCount)

        val adShowIndex = showBlock.indexOf("ad.show(activity)")
        val onRewardIndex = showBlock.indexOf("onReward(")
        assertTrue(onRewardIndex > adShowIndex)
    }

    @Test
    fun dismissalDoesNotGrantAReward() {
        val dismissedIndex = source.indexOf("override fun onAdDismissedFullScreenContent")
        val nextCallbackIndex = source.indexOf(
            "override fun onAdFailedToShowFullScreenContent",
            dismissedIndex,
        )
        val dismissedBlock = source.substring(dismissedIndex, nextCallbackIndex)
        assertFalse(dismissedBlock.contains("onReward("))
    }

    @Test
    fun showFailureDoesNotGrantAReward() {
        val failedIndex = source.indexOf("override fun onAdFailedToShowFullScreenContent")
        val nextCallbackIndex = source.indexOf("override fun onAdShowedFullScreenContent", failedIndex)
        val failedBlock = source.substring(failedIndex, nextCallbackIndex)
        assertFalse(failedBlock.contains("onReward("))
    }

    @Test
    fun shownCallbackDoesNotGrantAReward() {
        val shownIndex = source.indexOf("override fun onAdShowedFullScreenContent")
        // Bounded to just this override's body -- "ad.show(activity)" is the next line
        // of code after the FullScreenContentCallback object literal closes.
        val callbackObjectEndIndex = source.indexOf("ad.show(activity)", shownIndex)
        val shownBlock = source.substring(shownIndex, callbackObjectEndIndex)
        assertFalse(shownBlock.contains("onReward("))
    }

    @Test
    fun debugTestAdUnitIdExists() {
        assertTrue(source.contains("ca-app-pub-3940256099942544/5224354917"))
    }

    @Test
    fun releaseConfigurationIsChecked() {
        assertTrue(source.contains("BuildConfig.IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID"))
        assertTrue(source.contains("isDebugBuild"))
    }

    @Test
    fun publisherFirstPartyIdIsDisabled() {
        assertTrue(source.contains("MobileAds.putPublisherFirstPartyIdEnabled(false)"))
    }

    @Test
    fun maxContentRatingIsConfigured() {
        assertTrue(source.contains("RequestConfiguration.MAX_AD_CONTENT_RATING_G"))
    }

    @Test
    fun npaRequestConfigurationExists() {
        assertTrue(source.contains("\"npa\""))
        assertTrue(source.contains("addNetworkExtrasBundle"))
    }

    @Test
    fun receiptTokenIsMintedOnceAtLoadNotInsideTheRewardCallback() {
        // The token must be generated exactly once, inside onAdLoaded, and carried on the
        // LoadedRewardedAd wrapper -- never freshly generated inside the reward listener,
        // which would mint a brand new (and never-deduplicated) token on every reward.
        val onAdLoadedIndex = source.indexOf("override fun onAdLoaded")
        assertTrue(onAdLoadedIndex >= 0)
        val onAdLoadedBlock = source.substring(
            onAdLoadedIndex,
            source.indexOf("override fun onAdFailedToLoad", onAdLoadedIndex),
        )
        assertTrue(onAdLoadedBlock.contains("UUID.randomUUID()"))

        val showIndex = source.indexOf("fun show(")
        val showBlock = source.substring(showIndex, source.indexOf("private fun reloadAfterShow(", showIndex))
        assertFalse(showBlock.contains("UUID.randomUUID()"))
        assertTrue(showBlock.contains("current.receiptToken"))
    }

    @Test
    fun loadedAdCarriesAConsumedGuardAndAGeneration() {
        assertTrue(source.contains("class LoadedRewardedAd"))
        assertTrue(source.contains("AtomicBoolean"))
        assertTrue(source.contains("val generation: Long"))
        assertTrue(source.contains("consumed.compareAndSet(false, true)"))
    }

    @Test
    fun staleLoadAndShowCallbacksAreRejectedByGeneration() {
        assertTrue(source.contains("isCurrentAndEligible(requestGeneration)"))
        assertTrue(source.contains("showGeneration == generation"))
        assertTrue(source.contains("private fun isCurrentAndEligible(expectedGeneration: Long): Boolean ="))
    }

    @Test
    fun eligibilityIsRetainedAsStateNotJustAOneOffParameter() {
        assertTrue(source.contains("private var currentEligibility = SafeBrowseAdEligibility("))
        assertTrue(source.contains("currentEligibility = eligibility"))
    }

    @Test
    fun mobileAdsInitialisationHasAnInFlightGuard() {
        assertTrue(source.contains("private var mobileAdsInitialisationInFlight = false"))
        val ensureIndex = source.indexOf("private fun ensureMobileAdsInitialized")
        val ensureBlock = source.substring(ensureIndex, source.indexOf("private fun loadAd", ensureIndex))
        assertTrue(ensureBlock.contains("if (mobileAdsInitialisationInFlight)"))
        assertTrue(ensureBlock.contains("mobileAdsInitialisationInFlight = true"))
        assertTrue(ensureBlock.contains("mobileAdsInitialisationInFlight = false"))
        // Readiness is rechecked against generation+eligibility inside the init callback,
        // not assumed just because initialisation itself succeeded.
        assertTrue(ensureBlock.contains("isCurrentAndEligible(expectedGeneration)"))
    }

    @Test
    fun clearWithdrawsEligibilityAndInvalidatesTheCurrentAd() {
        val clearIndex = source.indexOf("fun clear()")
        assertTrue(clearIndex >= 0)
        val clearBlock = source.substring(clearIndex, source.length)
        assertTrue(clearBlock.contains("currentEligibility = SafeBrowseAdEligibility("))
        assertTrue(clearBlock.contains("invalidateCurrentAd()"))
    }

    @Test
    fun showRequiresCurrentEligibilityInAdditionToAReadyAd() {
        val showIndex = source.indexOf("fun show(")
        val showBlock = source.substring(showIndex, source.indexOf("private fun reloadAfterShow(", showIndex))
        assertTrue(showBlock.contains("!currentEligibility.eligibleToPreload"))
    }

    @Test
    fun rewardListenerRequiresCurrentEligibilityNotJustGeneration() {
        val showIndex = source.indexOf("fun show(")
        val showBlock = source.substring(showIndex, source.indexOf("private fun reloadAfterShow(", showIndex))
        val listenerIndex = showBlock.indexOf("current.ad.show(activity)")
        assertTrue(listenerIndex >= 0)
        val listenerBlock = showBlock.substring(listenerIndex)
        assertTrue(listenerBlock.contains("showGeneration == generation"))
        assertTrue(listenerBlock.contains("currentEligibility.eligibleToPreload"))
        assertTrue(listenerBlock.contains("consumed.compareAndSet(false, true)"))
    }

    @Test
    fun reloadAfterShowTakesTheShowGenerationAndOnlyEverCallsMaybeLoadAd() {
        assertTrue(source.contains("private fun reloadAfterShow(showGeneration: Long)"))
        val reloadIndex = source.indexOf("private fun reloadAfterShow(showGeneration: Long)")
        assertTrue(reloadIndex >= 0)
        val reloadEnd = source.indexOf("fun clear()", reloadIndex)
        val reloadBlock = source.substring(reloadIndex, reloadEnd)

        assertTrue(reloadBlock.contains("isCurrentAndEligible(showGeneration)"))
        assertTrue(reloadBlock.contains("maybeLoadAd()"))
        // Must never call loadAd(...) directly -- only through maybeLoadAd()'s own
        // eligibility and in-flight-state checks.
        assertFalse(reloadBlock.contains("loadAd("))

        val dismissedIndex = source.indexOf("override fun onAdDismissedFullScreenContent")
        val failedIndex = source.indexOf("override fun onAdFailedToShowFullScreenContent", dismissedIndex)
        assertTrue(source.substring(dismissedIndex, failedIndex).contains("reloadAfterShow(showGeneration)"))
    }

    @Test
    fun onAdLoadedAndOnAdFailedToLoadBothRecheckEligibilityBeforeMutatingState() {
        val onAdLoadedIndex = source.indexOf("override fun onAdLoaded")
        val onAdFailedIndex = source.indexOf("override fun onAdFailedToLoad", onAdLoadedIndex)
        val onAdLoadedBlock = source.substring(onAdLoadedIndex, onAdFailedIndex)
        assertTrue(onAdLoadedBlock.contains("isCurrentAndEligible(requestGeneration)"))

        val callbackEnd = source.indexOf("},", onAdFailedIndex)
        val onAdFailedBlock = source.substring(onAdFailedIndex, callbackEnd)
        assertTrue(onAdFailedBlock.contains("isCurrentAndEligible(requestGeneration)"))
    }

    @Test
    fun releaseAdIdsAreValidatedAgainstGooglesDocumentedFormatBeforeMobileAdsInit() {
        assertTrue(source.contains("val SafeBrowseAdMobAppIdPattern = Regex(\"^ca-app-pub-\\\\d{16}~\\\\d{10}\$\")"))
        assertTrue(source.contains("val SafeBrowseRewardedUnitIdPattern = Regex(\"^ca-app-pub-\\\\d{16}/\\\\d{10}\$\")"))

        val ensureIndex = source.indexOf("private fun ensureMobileAdsInitialized")
        val ensureBlock = source.substring(ensureIndex, source.indexOf("private fun loadAd", ensureIndex))
        assertTrue(ensureBlock.contains("SafeBrowseAdMobAppIdPattern.matches(BuildConfig.IMPULSIVE_ADMOB_APP_ID)"))
        // Validation must happen before MobileAds is ever initialised.
        val validationIndex = ensureBlock.indexOf("SafeBrowseAdMobAppIdPattern.matches")
        val initIndex = ensureBlock.indexOf("MobileAds.initialize")
        assertTrue(validationIndex in 0 until initIndex)

        assertTrue(source.contains("BuildConfig.IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID.takeIf(SafeBrowseRewardedUnitIdPattern::matches)"))
    }

    @Test
    fun eligibilityDrivesPreloadingInsteadOfARawBoolean() {
        assertTrue(source.contains("data class SafeBrowseAdEligibility"))
        assertTrue(source.contains("val eligibleToPreload"))
        assertTrue(source.contains("fun preload(eligibility: SafeBrowseAdEligibility)"))
    }

    @Test
    fun noBrowsingOrRecoveryStateEntersTheAdRequest() {
        listOf(
            "canonicalUrl",
            "displayHost",
            "searchText",
            "SafeBrowseNavigationDecision",
            "currentDisplayHost",
            "triggeringPackageName",
            "urge",
        ).forEach { forbidden ->
            assertFalse("ad controller unexpectedly references $forbidden", source.contains(forbidden))
        }
    }
}
