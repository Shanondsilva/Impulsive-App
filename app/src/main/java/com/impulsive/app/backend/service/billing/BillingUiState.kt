package com.impulsive.app.backend.service.billing

import com.android.billingclient.api.BillingClient
import com.impulsive.app.backend.domain.model.premium.BillingPeriod

enum class BillingUnavailableReason {
    NetworkError,
    ServiceDisconnected,
    ServiceUnavailable,
    BillingUnavailable,
}

sealed interface BillingUiState {
    data object Connecting : BillingUiState
    data object Ready : BillingUiState
    data object ProductUnavailable : BillingUiState
    data class PurchaseLaunching(val period: BillingPeriod) : BillingUiState
    data object Pending : BillingUiState
    data object PurchasedAndVerifying : BillingUiState
    data object VerificationDeferred : BillingUiState
    data object UserCancelled : BillingUiState
    data object AlreadyOwned : BillingUiState
    data class NetworkOrServiceUnavailable(
        val reason: BillingUnavailableReason,
    ) : BillingUiState
    data object VerificationFailed : BillingUiState
    data object Restored : BillingUiState
    data object NoPurchaseFound : BillingUiState
    data class Error(
        val responseCode: Int,
        val retryable: Boolean,
    ) : BillingUiState
}

internal fun billingFailureStateForResponseCode(
    responseCode: Int,
): BillingUiState? {
    return when (responseCode) {
        BillingClient.BillingResponseCode.OK -> null
        BillingClient.BillingResponseCode.USER_CANCELED -> BillingUiState.UserCancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingUiState.AlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> BillingUiState.NoPurchaseFound
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingUiState.ProductUnavailable
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            BillingUiState.NetworkOrServiceUnavailable(BillingUnavailableReason.NetworkError)
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceDisconnected,
            )
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceUnavailable,
            )
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.BillingUnavailable,
            )
        BillingClient.BillingResponseCode.ERROR -> BillingUiState.Error(
            responseCode = responseCode,
            retryable = true,
        )
        BillingClient.BillingResponseCode.DEVELOPER_ERROR,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        -> BillingUiState.Error(
            responseCode = responseCode,
            retryable = false,
        )
        else -> BillingUiState.Error(
            responseCode = responseCode,
            retryable = false,
        )
    }
}

internal fun BillingUiState.allowsPurchaseAction(): Boolean {
    return when (this) {
        BillingUiState.Ready,
        BillingUiState.UserCancelled,
        BillingUiState.NoPurchaseFound,
        -> true
        else -> false
    }
}

internal fun shouldReconcileBillingAfterAuthChange(
    previousUserId: String?,
    currentUserId: String?,
): Boolean = currentUserId != null && currentUserId != previousUserId

internal object BillingReconnectPolicy {
    private val delaysMillis = longArrayOf(
        1_000L,
        2_000L,
        5_000L,
    )

    fun delayForAttempt(attemptIndex: Int): Long? {
        if (attemptIndex < 0) {
            return null
        }

        return delaysMillis.getOrNull(attemptIndex)
    }
}
