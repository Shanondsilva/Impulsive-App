package com.impulsive.app.backend.service.billing

import com.android.billingclient.api.BillingClient

/**
 * UI state for the Safe Browse Pass purchase flow. Deliberately a separate sealed type from
 * [BillingUiState] -- reusing Plus's UI state (or its [com.impulsive.app.backend.domain.model.premium.BillingPeriod]-typed
 * [BillingUiState.PurchaseLaunching]) would let a Pass purchase overwrite the Plus screen's
 * status (or vice versa) whenever either flow updates its shared state.
 */
sealed interface SafeBrowsePassBillingUiState {
    data object Connecting : SafeBrowsePassBillingUiState
    data object Ready : SafeBrowsePassBillingUiState
    data object ProductUnavailable : SafeBrowsePassBillingUiState
    data class RefreshingOffer(val period: SafeBrowsePassPeriod) : SafeBrowsePassBillingUiState
    data class PurchaseLaunching(val period: SafeBrowsePassPeriod) : SafeBrowsePassBillingUiState
    data object Pending : SafeBrowsePassBillingUiState
    data object PurchasedAndVerifying : SafeBrowsePassBillingUiState
    data object Purchased : SafeBrowsePassBillingUiState
    data object VerificationDeferred : SafeBrowsePassBillingUiState
    data object UserCancelled : SafeBrowsePassBillingUiState
    data object AlreadyOwned : SafeBrowsePassBillingUiState
    data class NetworkOrServiceUnavailable(
        val reason: BillingUnavailableReason,
    ) : SafeBrowsePassBillingUiState
    data object VerificationFailed : SafeBrowsePassBillingUiState
    data object Restored : SafeBrowsePassBillingUiState
    data object NoPurchaseFound : SafeBrowsePassBillingUiState
    data class Error(
        val responseCode: Int,
        val retryable: Boolean,
    ) : SafeBrowsePassBillingUiState
}

internal fun safeBrowsePassBillingFailureStateForResponseCode(
    responseCode: Int,
): SafeBrowsePassBillingUiState? {
    return when (responseCode) {
        BillingClient.BillingResponseCode.OK -> null
        BillingClient.BillingResponseCode.USER_CANCELED ->
            SafeBrowsePassBillingUiState.UserCancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            SafeBrowsePassBillingUiState.AlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
            SafeBrowsePassBillingUiState.NoPurchaseFound
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            SafeBrowsePassBillingUiState.ProductUnavailable
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.NetworkError,
            )
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceDisconnected,
            )
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
            SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceUnavailable,
            )
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.BillingUnavailable,
            )
        BillingClient.BillingResponseCode.ERROR -> SafeBrowsePassBillingUiState.Error(
            responseCode = responseCode,
            retryable = true,
        )
        BillingClient.BillingResponseCode.DEVELOPER_ERROR,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        -> SafeBrowsePassBillingUiState.Error(
            responseCode = responseCode,
            retryable = false,
        )
        else -> SafeBrowsePassBillingUiState.Error(
            responseCode = responseCode,
            retryable = false,
        )
    }
}

internal fun SafeBrowsePassBillingUiState.allowsPurchaseAction(): Boolean {
    return when (this) {
        SafeBrowsePassBillingUiState.Ready,
        SafeBrowsePassBillingUiState.UserCancelled,
        SafeBrowsePassBillingUiState.NoPurchaseFound,
        -> true
        else -> false
    }
}
