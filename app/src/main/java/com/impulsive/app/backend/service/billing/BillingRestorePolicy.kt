package com.impulsive.app.backend.service.billing

sealed interface BillingRestoreState {
    data object Idle : BillingRestoreState
    data object Loading : BillingRestoreState
    data object Success : BillingRestoreState
    data object NoPurchase : BillingRestoreState
    data object Error : BillingRestoreState
}

internal enum class ServerEntitlementRefreshResult {
    Active,
    Inactive,
    Unavailable,
    SkippedNoAuthenticatedUser,
}

internal fun resolveBillingRestoreState(
    playQuerySucceeded: Boolean,
    verifiedActivePurchaseCount: Int,
    verificationFailed: Boolean,
    serverRefreshResult: ServerEntitlementRefreshResult,
): BillingRestoreState {
    require(verifiedActivePurchaseCount >= 0)

    /*
     * The final authoritative server reconciliation wins when
     * it produced a definitive answer.
     *
     * A purchase may have verified successfully immediately
     * before this refresh, but if checkPlusEntitlement then
     * definitively reports inactive, BillingManager has already
     * downgraded the local Play entitlement. The UI must not
     * report restore success in that state.
     */
    when (serverRefreshResult) {
        ServerEntitlementRefreshResult.Active ->
            return BillingRestoreState.Success

        ServerEntitlementRefreshResult.Inactive -> {
            return if (playQuerySucceeded) {
                BillingRestoreState.NoPurchase
            } else {
                BillingRestoreState.Error
            }
        }

        ServerEntitlementRefreshResult.Unavailable,
        ServerEntitlementRefreshResult.SkippedNoAuthenticatedUser,
        -> Unit
    }

    /*
     * When the final server reconciliation is temporarily
     * unavailable, a purchase that was independently verified
     * through the existing verifyPlusSubscription backend
     * remains authoritative enough to report restore success.
     */
    if (verifiedActivePurchaseCount > 0) {
        return BillingRestoreState.Success
    }

    if (!playQuerySucceeded) {
        return BillingRestoreState.Error
    }

    if (verificationFailed) {
        return BillingRestoreState.Error
    }

    return BillingRestoreState.Error
}
