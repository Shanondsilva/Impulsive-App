package com.impulsive.app.backend.service.billing

sealed interface SafeBrowsePassPurchaseState {
    data object Idle : SafeBrowsePassPurchaseState
    data object RefreshingOffer : SafeBrowsePassPurchaseState
    data object Launching : SafeBrowsePassPurchaseState
    data object Pending : SafeBrowsePassPurchaseState
    data object PendingTopUp : SafeBrowsePassPurchaseState
    data object Verifying : SafeBrowsePassPurchaseState
    data object Purchased : SafeBrowsePassPurchaseState
    data object VerificationDeferred : SafeBrowsePassPurchaseState
    data object UserCancelled : SafeBrowsePassPurchaseState
    data object AlreadyOwned : SafeBrowsePassPurchaseState
    data object Unavailable : SafeBrowsePassPurchaseState
    data class Error(val message: String) : SafeBrowsePassPurchaseState
}

sealed interface SafeBrowsePassRestoreState {
    data object Idle : SafeBrowsePassRestoreState
    data object Restoring : SafeBrowsePassRestoreState
    data object Restored : SafeBrowsePassRestoreState
    data object NothingToRestore : SafeBrowsePassRestoreState
    data class Error(val message: String) : SafeBrowsePassRestoreState
}

internal enum class SafeBrowsePassPendingKind {
    InitialPurchase,
    TopUp,
}

internal fun resolveSafeBrowsePassPurchaseState(
    billingState: SafeBrowsePassBillingUiState,
    pendingKind: SafeBrowsePassPendingKind?,
    entitlementActive: Boolean,
): SafeBrowsePassPurchaseState = when (billingState) {
    SafeBrowsePassBillingUiState.Connecting -> SafeBrowsePassPurchaseState.Idle
    SafeBrowsePassBillingUiState.Ready -> SafeBrowsePassPurchaseState.Idle
    is SafeBrowsePassBillingUiState.RefreshingOffer -> SafeBrowsePassPurchaseState.RefreshingOffer
    is SafeBrowsePassBillingUiState.PurchaseLaunching -> SafeBrowsePassPurchaseState.Launching
    SafeBrowsePassBillingUiState.Pending -> when (pendingKind) {
        SafeBrowsePassPendingKind.TopUp -> SafeBrowsePassPurchaseState.PendingTopUp
        SafeBrowsePassPendingKind.InitialPurchase,
        null,
        -> SafeBrowsePassPurchaseState.Pending
    }
    SafeBrowsePassBillingUiState.PurchasedAndVerifying -> SafeBrowsePassPurchaseState.Verifying
    SafeBrowsePassBillingUiState.Purchased -> if (entitlementActive) {
        SafeBrowsePassPurchaseState.Purchased
    } else {
        SafeBrowsePassPurchaseState.Verifying
    }
    SafeBrowsePassBillingUiState.VerificationDeferred ->
        SafeBrowsePassPurchaseState.VerificationDeferred
    SafeBrowsePassBillingUiState.UserCancelled -> SafeBrowsePassPurchaseState.UserCancelled
    SafeBrowsePassBillingUiState.AlreadyOwned -> SafeBrowsePassPurchaseState.AlreadyOwned
    SafeBrowsePassBillingUiState.ProductUnavailable -> SafeBrowsePassPurchaseState.Unavailable
    SafeBrowsePassBillingUiState.NoPurchaseFound -> SafeBrowsePassPurchaseState.Idle
    SafeBrowsePassBillingUiState.Restored -> SafeBrowsePassPurchaseState.Idle
    SafeBrowsePassBillingUiState.VerificationFailed ->
        SafeBrowsePassPurchaseState.Error("We couldn't verify your purchase. Please try again.")
    is SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable ->
        SafeBrowsePassPurchaseState.Error("Google Play is temporarily unavailable.")
    is SafeBrowsePassBillingUiState.Error ->
        SafeBrowsePassPurchaseState.Error("Something went wrong. Please try again.")
}
