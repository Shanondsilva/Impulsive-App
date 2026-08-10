package com.impulsive.app.frontend.screens.safebrowse

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessEffect
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessState as DomainAccessState
import com.impulsive.app.backend.service.billing.BillingManager
import com.impulsive.app.backend.service.billing.SafeBrowsePassCatalogState
import com.impulsive.app.backend.session.safebrowse.SafeBrowseAccessViewModel
import com.impulsive.app.frontend.ads.LocalSafeBrowseConsentManager
import com.impulsive.app.frontend.ads.SafeBrowseAdEligibility
import com.impulsive.app.frontend.ads.SafeBrowseConsentState
import com.impulsive.app.frontend.ads.SafeBrowseRewardedAdController
import com.impulsive.app.frontend.ads.SafeBrowseRewardedAdState

/**
 * Live Safe Browse unlock screen. Owns ad consent and rewarded-ad preparation; the shared
 * [accessViewModel] instance is the single authoritative usage ledger, also used by the
 * secured browser destination. [billingManager] is the same app-wide billing owner used
 * everywhere else -- never a second, independently-constructed one -- read here only for
 * the Safe Browse Pass catalogue price shown on the "View Safe Browse Pass" entry point.
 */
@Composable
fun SafeBrowseRoute(
    accessViewModel: SafeBrowseAccessViewModel,
    billingManager: BillingManager,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenSafeBrowsePass: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // The single app-wide instance provided at the navigation root -- never constructed here.
    val consentManager = requireNotNull(LocalSafeBrowseConsentManager.current) {
        "SafeBrowseConsentManager provider is missing."
    }
    val adController = remember { SafeBrowseRewardedAdController(context.applicationContext) }

    val domainAccessState by accessViewModel.accessState.collectAsStateWithLifecycle()
    val ledgerError by accessViewModel.errorMessage.collectAsStateWithLifecycle()
    val consentState by consentManager.state.collectAsStateWithLifecycle()
    val adState by adController.state.collectAsStateWithLifecycle()
    val passCatalogState by billingManager.safeBrowsePassCatalogState.collectAsStateWithLifecycle()

    var justExpired by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adController.clear()
        }
    }

    // The consent form is only ever shown from this calm route, and only once a resolved
    // info update confirms one is required -- the manager's own atomic guards prevent this
    // from ever presenting a duplicate form.
    LaunchedEffect(consentState, activity) {
        if (activity != null && consentState is SafeBrowseConsentState.Resolved) {
            consentManager.showRequiredFormIfAppropriate(activity)
        }
    }

    LaunchedEffect(consentState, domainAccessState) {
        val canRequestAds = (consentState as? SafeBrowseConsentState.Resolved)?.canRequestAds == true
        adController.preload(
            SafeBrowseAdEligibility(
                isLocked = domainAccessState is DomainAccessState.Locked,
                canRequestAds = canRequestAds,
            ),
        )
    }

    LaunchedEffect(accessViewModel) {
        accessViewModel.effects.collect { effect ->
            when (effect) {
                SafeBrowseAccessEffect.OpenBrowser -> {
                    justExpired = false
                    onOpenBrowser()
                }

                SafeBrowseAccessEffect.AccessExpired -> {
                    justExpired = true
                }
            }
        }
    }

    val uiState = toSafeBrowseUiState(
        domainAccessState = domainAccessState,
        adState = adState,
        ledgerError = ledgerError,
        justExpired = justExpired,
        passCatalogState = passCatalogState,
    )

    SafeBrowseScreen(
        state = uiState,
        onBack = onBack,
        onWatchRewardedAd = {
            justExpired = false
            val currentActivity = activity
            if (currentActivity != null && adState is SafeBrowseRewardedAdState.Ready) {
                adController.show(currentActivity) { receiptToken ->
                    accessViewModel.grantReward(receiptToken)
                }
            }
        },
        onOpenBrowser = { accessViewModel.requestOpenBrowser() },
        onOpenPass = onOpenSafeBrowsePass,
        onRetry = {
            justExpired = false
            val retryActivity = activity
            if (retryActivity != null && consentState is SafeBrowseConsentState.Failed) {
                consentManager.retryConsentInfoUpdate(retryActivity)
            }
            accessViewModel.refresh()
        },
    )
}

private fun toSafeBrowseUiState(
    domainAccessState: DomainAccessState,
    adState: SafeBrowseRewardedAdState,
    ledgerError: String?,
    justExpired: Boolean,
    passCatalogState: SafeBrowsePassCatalogState,
): SafeBrowseUiState {
    val representativePassPlan = (passCatalogState as? SafeBrowsePassCatalogState.Ready)
        ?.let { ready -> ready.monthly ?: ready.prepaid }
    val passPurchaseAvailable = representativePassPlan != null
    val passPriceLabel = representativePassPlan?.formattedPrice

    if (ledgerError != null) {
        return SafeBrowseUiState(
            accessState = SafeBrowseAccessState.Error(ledgerError),
            rewardedUnlockAvailable = false,
            browserOpeningAvailable = false,
            passPurchaseAvailable = false,
            passPriceLabel = null,
        )
    }

    return when (domainAccessState) {
        DomainAccessState.Loading -> SafeBrowseUiState(
            accessState = SafeBrowseAccessState.SetupPending,
            rewardedUnlockAvailable = false,
            browserOpeningAvailable = false,
            passPurchaseAvailable = false,
            passPriceLabel = null,
        )

        is DomainAccessState.Active -> SafeBrowseUiState(
            accessState = SafeBrowseAccessState.Active(
                remainingSeconds = domainAccessState.remainingMillis / 1_000L,
                passActive = false,
            ),
            rewardedUnlockAvailable = false,
            browserOpeningAvailable = true,
            passPurchaseAvailable = passPurchaseAvailable,
            passPriceLabel = passPriceLabel,
        )

        is DomainAccessState.PassActive -> SafeBrowseUiState(
            accessState = SafeBrowseAccessState.Active(
                remainingSeconds = 0L,
                passActive = true,
            ),
            rewardedUnlockAvailable = false,
            browserOpeningAvailable = true,
            passPurchaseAvailable = false,
            passPriceLabel = null,
        )

        is DomainAccessState.Error -> SafeBrowseUiState(
            accessState = SafeBrowseAccessState.Error(domainAccessState.message),
            rewardedUnlockAvailable = false,
            browserOpeningAvailable = false,
            passPurchaseAvailable = false,
            passPriceLabel = null,
        )

        DomainAccessState.Locked -> when {
            justExpired -> SafeBrowseUiState(
                accessState = SafeBrowseAccessState.Expired,
                rewardedUnlockAvailable = adState is SafeBrowseRewardedAdState.Ready,
                browserOpeningAvailable = false,
                passPurchaseAvailable = passPurchaseAvailable,
                passPriceLabel = passPriceLabel,
            )

            adState is SafeBrowseRewardedAdState.Error -> SafeBrowseUiState(
                accessState = SafeBrowseAccessState.Error(adState.message),
                rewardedUnlockAvailable = false,
                browserOpeningAvailable = false,
                passPurchaseAvailable = passPurchaseAvailable,
                passPriceLabel = passPriceLabel,
            )

            else -> SafeBrowseUiState(
                accessState = SafeBrowseAccessState.Locked,
                rewardedUnlockAvailable = adState is SafeBrowseRewardedAdState.Ready,
                browserOpeningAvailable = false,
                passPurchaseAvailable = passPurchaseAvailable,
                passPriceLabel = passPriceLabel,
            )
        }
    }
}
