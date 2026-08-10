package com.impulsive.app.frontend.ads

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.impulsive.app.BuildConfig
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SafeBrowseRewardedAdState {
    data object Unavailable : SafeBrowseRewardedAdState
    data object Loading : SafeBrowseRewardedAdState
    data object Ready : SafeBrowseRewardedAdState
    data object Showing : SafeBrowseRewardedAdState
    data class Error(val message: String) : SafeBrowseRewardedAdState
}

/**
 * Whether a rewarded ad should be preloaded right now: only while Safe Browse is genuinely
 * Locked and ad consent has been resolved in a way that permits a request.
 */
data class SafeBrowseAdEligibility(
    val isLocked: Boolean,
    val canRequestAds: Boolean,
) {
    val eligibleToPreload: Boolean
        get() = isLocked && canRequestAds
}

/** Google's official rewarded test ad-unit ID. Debug builds must never request a live ad. */
internal const val SafeBrowseDebugRewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"

/**
 * Google's documented AdMob application-ID and ad-unit-ID formats. A release build's
 * configured IDs are validated against these both at build time (the Gradle task tied to
 * `preReleaseBuild`) and again here at runtime, so a malformed or placeholder value can
 * never reach a live [MobileAds.initialize] call.
 */
internal val SafeBrowseAdMobAppIdPattern = Regex("^ca-app-pub-\\d{16}~\\d{10}$")
internal val SafeBrowseRewardedUnitIdPattern = Regex("^ca-app-pub-\\d{16}/\\d{10}$")

/**
 * One successfully loaded ad, paired with the single receipt token it will ever grant and
 * the load [generation] it belongs to. [consumed] guards against the SDK's reward callback
 * ever being allowed to grant more than once for this instance.
 */
private class LoadedRewardedAd(
    val ad: RewardedAd,
    val receiptToken: String,
    val generation: Long,
) {
    val consumed = AtomicBoolean(false)
}

/**
 * Owns the transient [RewardedAd] instance behind Safe Browse's optional "Watch ad to
 * unlock 2 hours" action.
 *
 * The reward is only ever granted from the SDK's own OnUserEarnedRewardListener. No other
 * callback (dismissal, show failure, click, impression) grants anything. Never loads or
 * retains an ad while the secured browser is active.
 *
 * [generation] is bumped by every new load request and by [clear]. Every asynchronous SDK
 * callback captures the generation it belongs to and checks it -- together with the
 * currently retained [currentEligibility] -- before touching state, so a callback that
 * arrives after a newer load, after eligibility was withdrawn, or after the controller was
 * cleared is discarded instead of reviving or reloading state that no longer applies. This
 * closes the specific gap where a dismissal or show-failure callback landing after access
 * changed or the owning route was disposed could otherwise start a fresh, unwanted ad load.
 */
class SafeBrowseRewardedAdController(
    private val context: Context,
) {
    private val _state = MutableStateFlow<SafeBrowseRewardedAdState>(SafeBrowseRewardedAdState.Unavailable)
    val state: StateFlow<SafeBrowseRewardedAdState> = _state.asStateFlow()

    private var mobileAdsInitialized = false
    private var mobileAdsInitialisationInFlight = false
    private var loaded: LoadedRewardedAd? = null
    private var generation: Long = 0L

    /** The eligibility supplied by the most recent [preload] call. */
    private var currentEligibility = SafeBrowseAdEligibility(
        isLocked = false,
        canRequestAds = false,
    )

    private val isDebugBuild: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Null in a release build with no configured production ad unit, or one that does not
     * match Google's documented ad-unit-ID format -- a malformed value must never reach
     * [RewardedAd.load].
     */
    private val adUnitId: String?
        get() = if (isDebugBuild) {
            SafeBrowseDebugRewardedAdUnitId
        } else {
            BuildConfig.IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID.takeIf(SafeBrowseRewardedUnitIdPattern::matches)
        }

    /** True only when [expectedGeneration] is still current and eligibility still holds. */
    private fun isCurrentAndEligible(expectedGeneration: Long): Boolean =
        expectedGeneration == generation && currentEligibility.eligibleToPreload

    /** Preloads a rewarded ad only while [eligibility] permits it. */
    fun preload(eligibility: SafeBrowseAdEligibility) {
        currentEligibility = eligibility

        if (!eligibility.eligibleToPreload) {
            invalidateCurrentAd()
            return
        }

        maybeLoadAd()
    }

    /** Invalidates any in-flight load and the currently loaded ad, without a new request. */
    private fun invalidateCurrentAd() {
        generation += 1L
        loaded = null
        _state.value = SafeBrowseRewardedAdState.Unavailable
    }

    private fun maybeLoadAd() {
        if (!currentEligibility.eligibleToPreload) {
            return
        }

        when (_state.value) {
            is SafeBrowseRewardedAdState.Loading,
            is SafeBrowseRewardedAdState.Ready,
            is SafeBrowseRewardedAdState.Showing,
            -> return
            else -> Unit
        }

        val unitId = adUnitId ?: run {
            _state.value = SafeBrowseRewardedAdState.Unavailable
            return
        }

        val requestGeneration = generation + 1L
        generation = requestGeneration
        loaded = null
        _state.value = SafeBrowseRewardedAdState.Loading

        ensureMobileAdsInitialized(expectedGeneration = requestGeneration) {
            loadAd(unitId = unitId, requestGeneration = requestGeneration)
        }
    }

    private fun ensureMobileAdsInitialized(expectedGeneration: Long, onReady: () -> Unit) {
        if (mobileAdsInitialized) {
            if (isCurrentAndEligible(expectedGeneration)) {
                onReady()
            }
            return
        }

        if (mobileAdsInitialisationInFlight) {
            return
        }

        // Defence in depth: the Gradle task tied to `preReleaseBuild` already refuses to
        // produce a release artifact with a missing or malformed AdMob application ID, but
        // Mobile Ads must never be initialised from a release build that somehow still
        // carries an invalid one.
        if (!isDebugBuild && !SafeBrowseAdMobAppIdPattern.matches(BuildConfig.IMPULSIVE_ADMOB_APP_ID)) {
            _state.value = SafeBrowseRewardedAdState.Unavailable
            return
        }

        mobileAdsInitialisationInFlight = true

        MobileAds.putPublisherFirstPartyIdEnabled(false)
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
        MobileAds.initialize(context) {
            mobileAdsInitialisationInFlight = false
            mobileAdsInitialized = true

            if (isCurrentAndEligible(expectedGeneration)) {
                onReady()
            }
        }
    }

    private fun loadAd(unitId: String, requestGeneration: Long) {
        if (!isCurrentAndEligible(requestGeneration)) {
            return
        }

        // Non-personalised request only. No Safe Browse URL, search query, browsing
        // history, trigger or recovery data is ever attached to an ad request.
        val nonPersonalisedExtras = Bundle().apply { putString("npa", "1") }
        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, nonPersonalisedExtras)
            .build()

        RewardedAd.load(
            context,
            unitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (!isCurrentAndEligible(requestGeneration)) {
                        // A newer load, a withdrawal of eligibility, or a clear()
                        // happened while this request was in flight. Discard the stale
                        // ad rather than reviving old state.
                        return
                    }
                    // The single receipt token this ad will ever grant is minted exactly
                    // once, here -- never inside the reward callback, which can otherwise
                    // be re-entered or re-triggered by the SDK for the same ad instance.
                    loaded = LoadedRewardedAd(
                        ad = ad,
                        receiptToken = UUID.randomUUID().toString(),
                        generation = requestGeneration,
                    )
                    _state.value = SafeBrowseRewardedAdState.Ready
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (!isCurrentAndEligible(requestGeneration)) return
                    loaded = null
                    _state.value = SafeBrowseRewardedAdState.Error("Ad temporarily unavailable.")
                }
            },
        )
    }

    /**
     * Shows the currently loaded ad. [onReward] is invoked with the fixed receipt token
     * minted for this ad at load time ONLY from the SDK's OnUserEarnedRewardListener, and
     * at most once — never from onAdDismissedFullScreenContent, onAdShowedFullScreenContent
     * or onAdClicked.
     */
    fun show(
        activity: Activity,
        onReward: (receiptToken: String) -> Unit,
    ) {
        val current = loaded
        val ready = _state.value is SafeBrowseRewardedAdState.Ready

        if (
            activity.isFinishing ||
            activity.isDestroyed ||
            current == null ||
            !ready ||
            !currentEligibility.eligibleToPreload ||
            current.generation != generation
        ) {
            return
        }

        val showGeneration = current.generation
        loaded = null
        _state.value = SafeBrowseRewardedAdState.Showing

        current.ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (showGeneration == generation) {
                    _state.value = SafeBrowseRewardedAdState.Unavailable
                }
                reloadAfterShow(showGeneration)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                if (showGeneration == generation) {
                    _state.value = SafeBrowseRewardedAdState.Error("Ad temporarily unavailable.")
                }
                reloadAfterShow(showGeneration)
            }

            override fun onAdShowedFullScreenContent() {
                // Never grant here. Reward is only earned via OnUserEarnedRewardListener.
            }
        }

        current.ad.show(activity) { _ ->
            // OnUserEarnedRewardListener. The RewardItem's own amount/type is ignored --
            // the two-hour grant duration is fixed by SafeBrowseAccessDataSource policy.
            // `consumed` ensures this ad instance can grant its token at most once even if
            // the SDK were to invoke this listener again; `showGeneration == generation`
            // rejects a listener call surviving past a clear() or eligibility change that
            // happened meanwhile.
            if (
                showGeneration == generation &&
                currentEligibility.eligibleToPreload &&
                current.consumed.compareAndSet(false, true)
            ) {
                onReward(current.receiptToken)
            }
        }
    }

    /**
     * Reloads a fresh ad after the previous one was dismissed or failed to show -- but only
     * if [showGeneration] is still current and eligibility still holds. A route disposal,
     * access change or Pass activation that happened while the ad was on screen must never
     * be revived by this late callback starting a brand-new load.
     */
    private fun reloadAfterShow(showGeneration: Long) {
        if (!isCurrentAndEligible(showGeneration)) {
            return
        }

        _state.value = SafeBrowseRewardedAdState.Unavailable
        maybeLoadAd()
    }

    /** Clears the current ad reference. Call when the owning route is disposed. */
    fun clear() {
        currentEligibility = SafeBrowseAdEligibility(
            isLocked = false,
            canRequestAds = false,
        )
        invalidateCurrentAd()
    }
}
