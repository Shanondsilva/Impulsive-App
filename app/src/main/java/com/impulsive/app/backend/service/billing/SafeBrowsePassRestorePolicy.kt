package com.impulsive.app.backend.service.billing

private const val SafeBrowsePassRestoreErrorMessage =
    "Safe Browse Pass could not be restored. Please try again."

internal data class SafeBrowsePassRestoreEvidence(
    val playQuerySucceeded: Boolean,
    val verifiedActivePurchaseCount: Int,
    val verificationFailed: Boolean,
    val serverRefreshResult: ServerEntitlementRefreshResult,
)

internal fun resolveSafeBrowsePassRestoreState(
    evidence: SafeBrowsePassRestoreEvidence,
): SafeBrowsePassRestoreState {
    require(evidence.verifiedActivePurchaseCount >= 0) {
        "verifiedActivePurchaseCount must be non-negative."
    }

    return when (evidence.serverRefreshResult) {
        ServerEntitlementRefreshResult.Active -> SafeBrowsePassRestoreState.Restored
        ServerEntitlementRefreshResult.Inactive -> if (evidence.playQuerySucceeded) {
            SafeBrowsePassRestoreState.NothingToRestore
        } else {
            SafeBrowsePassRestoreState.Error(SafeBrowsePassRestoreErrorMessage)
        }
        ServerEntitlementRefreshResult.SkippedNoAuthenticatedUser ->
            SafeBrowsePassRestoreState.Error(SafeBrowsePassRestoreErrorMessage)
        ServerEntitlementRefreshResult.Unavailable -> when {
            evidence.verifiedActivePurchaseCount > 0 -> SafeBrowsePassRestoreState.Restored
            !evidence.playQuerySucceeded ->
                SafeBrowsePassRestoreState.Error(SafeBrowsePassRestoreErrorMessage)
            evidence.verificationFailed ->
                SafeBrowsePassRestoreState.Error(SafeBrowsePassRestoreErrorMessage)
            else -> SafeBrowsePassRestoreState.Error(SafeBrowsePassRestoreErrorMessage)
        }
    }
}

/**
 * The pending classification, raw billing state, and whether an existing Pending UI state
 * must be preserved, derived purely from what one current Play purchases snapshot contains
 * -- never from a previously published raw state.
 */
internal data class SafeBrowsePassPlaySnapshotDecision(
    val pendingKind: SafeBrowsePassPendingKind?,
    val billingState: SafeBrowsePassBillingUiState,
    val keepPendingUiState: Boolean,
)

internal fun resolveSafeBrowsePassPlaySnapshotDecision(
    hasPendingTopUp: Boolean,
    hasPendingInitialPurchase: Boolean,
    hasPurchasedPurchase: Boolean,
): SafeBrowsePassPlaySnapshotDecision {
    val pendingKind = when {
        hasPendingTopUp -> SafeBrowsePassPendingKind.TopUp
        hasPendingInitialPurchase -> SafeBrowsePassPendingKind.InitialPurchase
        else -> null
    }

    val billingState = when {
        pendingKind != null -> SafeBrowsePassBillingUiState.Pending
        hasPurchasedPurchase -> SafeBrowsePassBillingUiState.PurchasedAndVerifying
        else -> SafeBrowsePassBillingUiState.NoPurchaseFound
    }

    return SafeBrowsePassPlaySnapshotDecision(
        pendingKind = pendingKind,
        billingState = billingState,
        keepPendingUiState = pendingKind != null,
    )
}

internal fun resolveSafeBrowsePassBillingStateAfterRestore(
    pendingKind: SafeBrowsePassPendingKind?,
    restoreState: SafeBrowsePassRestoreState,
    entitlementActive: Boolean,
): SafeBrowsePassBillingUiState {
    if (pendingKind != null) {
        return SafeBrowsePassBillingUiState.Pending
    }

    return when (restoreState) {
        SafeBrowsePassRestoreState.Restored -> if (entitlementActive) {
            SafeBrowsePassBillingUiState.Purchased
        } else {
            SafeBrowsePassBillingUiState.VerificationFailed
        }

        SafeBrowsePassRestoreState.NothingToRestore -> SafeBrowsePassBillingUiState.NoPurchaseFound

        is SafeBrowsePassRestoreState.Error -> SafeBrowsePassBillingUiState.NoPurchaseFound

        SafeBrowsePassRestoreState.Idle,
        SafeBrowsePassRestoreState.Restoring,
        -> error("A final restore state is required.")
    }
}
