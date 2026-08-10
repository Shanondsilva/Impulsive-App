package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeBrowsePassRestorePolicyTest {
    @Test
    fun serverActiveReturnsRestored() {
        assertEquals(
            SafeBrowsePassRestoreState.Restored,
            state(ServerEntitlementRefreshResult.Active),
        )
    }

    @Test
    fun serverInactiveWithSuccessfulQueryReturnsNothingToRestore() {
        assertEquals(
            SafeBrowsePassRestoreState.NothingToRestore,
            state(ServerEntitlementRefreshResult.Inactive, playQuerySucceeded = true),
        )
    }

    @Test
    fun serverInactiveWithFailedQueryReturnsError() {
        assertError(state(ServerEntitlementRefreshResult.Inactive, playQuerySucceeded = false))
    }

    @Test
    fun verifiedPurchaseWithServerUnavailableReturnsRestored() {
        assertEquals(
            SafeBrowsePassRestoreState.Restored,
            state(ServerEntitlementRefreshResult.Unavailable, verifiedActivePurchaseCount = 1),
        )
    }

    @Test
    fun queryFailedWithNoVerifiedPurchaseReturnsError() {
        assertError(state(ServerEntitlementRefreshResult.Unavailable, playQuerySucceeded = false))
    }

    @Test
    fun verificationFailedWithNoGrantReturnsError() {
        assertError(
            state(
                ServerEntitlementRefreshResult.Unavailable,
                verificationFailed = true,
            ),
        )
    }

    @Test
    fun skippedAccountReturnsError() {
        assertError(state(ServerEntitlementRefreshResult.SkippedNoAuthenticatedUser))
    }

    @Test
    fun negativeVerifiedCountRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            state(ServerEntitlementRefreshResult.Active, verifiedActivePurchaseCount = -1)
        }
    }

    @Test
    fun successfulEmptyQueryAloneDoesNotFabricateRestored() {
        assertError(state(ServerEntitlementRefreshResult.Unavailable, playQuerySucceeded = true))
    }

    private fun state(
        serverResult: ServerEntitlementRefreshResult,
        playQuerySucceeded: Boolean = true,
        verifiedActivePurchaseCount: Int = 0,
        verificationFailed: Boolean = false,
    ): SafeBrowsePassRestoreState = resolveSafeBrowsePassRestoreState(
        SafeBrowsePassRestoreEvidence(
            playQuerySucceeded = playQuerySucceeded,
            verifiedActivePurchaseCount = verifiedActivePurchaseCount,
            verificationFailed = verificationFailed,
            serverRefreshResult = serverResult,
        ),
    )

    private fun assertError(state: SafeBrowsePassRestoreState) {
        state as SafeBrowsePassRestoreState.Error
        assertEquals("Safe Browse Pass could not be restored. Please try again.", state.message)
    }

    // -------------------------------------------------------------------
    // resolveSafeBrowsePassPlaySnapshotDecision
    // -------------------------------------------------------------------

    @Test
    fun pendingTopUpResolvesToTopUpAndRawPending() {
        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = true,
            hasPendingInitialPurchase = false,
            hasPurchasedPurchase = false,
        )
        assertEquals(SafeBrowsePassPendingKind.TopUp, decision.pendingKind)
        assertEquals(SafeBrowsePassBillingUiState.Pending, decision.billingState)
        assertEquals(true, decision.keepPendingUiState)
    }

    @Test
    fun pendingInitialPurchaseResolvesToInitialPurchaseAndRawPending() {
        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = false,
            hasPendingInitialPurchase = true,
            hasPurchasedPurchase = false,
        )
        assertEquals(SafeBrowsePassPendingKind.InitialPurchase, decision.pendingKind)
        assertEquals(SafeBrowsePassBillingUiState.Pending, decision.billingState)
        assertEquals(true, decision.keepPendingUiState)
    }

    @Test
    fun topUpHasPrecedenceWhenBothPendingFlagsAreSupplied() {
        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = true,
            hasPendingInitialPurchase = true,
            hasPurchasedPurchase = false,
        )
        assertEquals(SafeBrowsePassPendingKind.TopUp, decision.pendingKind)
    }

    @Test
    fun completedPurchaseWithNoCurrentPendingStateResolvesToNullPendingKindAndPurchasedAndVerifying() {
        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = false,
            hasPendingInitialPurchase = false,
            hasPurchasedPurchase = true,
        )
        assertNull(decision.pendingKind)
        assertEquals(SafeBrowsePassBillingUiState.PurchasedAndVerifying, decision.billingState)
        assertEquals(false, decision.keepPendingUiState)
    }

    @Test
    fun emptyCurrentPlaySnapshotResolvesToNullPendingKindAndNoPurchaseFound() {
        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = false,
            hasPendingInitialPurchase = false,
            hasPurchasedPurchase = false,
        )
        assertNull(decision.pendingKind)
        assertEquals(SafeBrowsePassBillingUiState.NoPurchaseFound, decision.billingState)
        assertEquals(false, decision.keepPendingUiState)
    }

    // -------------------------------------------------------------------
    // resolveSafeBrowsePassBillingStateAfterRestore
    // -------------------------------------------------------------------

    @Test
    fun pendingTopUpRemainsRawPendingAfterRestore() {
        assertEquals(
            SafeBrowsePassBillingUiState.Pending,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = SafeBrowsePassPendingKind.TopUp,
                restoreState = SafeBrowsePassRestoreState.Restored,
                entitlementActive = true,
            ),
        )
    }

    @Test
    fun pendingInitialPurchaseRemainsRawPendingAfterRestore() {
        assertEquals(
            SafeBrowsePassBillingUiState.Pending,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = SafeBrowsePassPendingKind.InitialPurchase,
                restoreState = SafeBrowsePassRestoreState.NothingToRestore,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun completedTopUpPlusActiveEntitlementBecomesPurchased() {
        assertEquals(
            SafeBrowsePassBillingUiState.Purchased,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.Restored,
                entitlementActive = true,
            ),
        )
    }

    @Test
    fun restoredWithoutAnActiveEntitlementBecomesVerificationFailed() {
        assertEquals(
            SafeBrowsePassBillingUiState.VerificationFailed,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.Restored,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun nothingToRestoreBecomesNoPurchaseFound() {
        assertEquals(
            SafeBrowsePassBillingUiState.NoPurchaseFound,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.NothingToRestore,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun restoreErrorBecomesNoPurchaseFound() {
        assertEquals(
            SafeBrowsePassBillingUiState.NoPurchaseFound,
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.Error("failed"),
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun idleAndRestoringAreRejectedByTheFinalStateResolver() {
        assertThrows(IllegalStateException::class.java) {
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.Idle,
                entitlementActive = false,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            resolveSafeBrowsePassBillingStateAfterRestore(
                pendingKind = null,
                restoreState = SafeBrowsePassRestoreState.Restoring,
                entitlementActive = false,
            )
        }
    }
}
