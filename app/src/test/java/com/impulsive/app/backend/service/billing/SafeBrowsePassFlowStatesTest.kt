package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [resolveSafeBrowsePassPurchaseState] directly -- the pure mapper from
 * BillingManager's raw [SafeBrowsePassBillingUiState] (plus pending kind and entitlement
 * activity) to the public [SafeBrowsePassPurchaseState] the ViewModel and Compose UI
 * observe. No raw Play Billing type or debug message may ever reach the public state.
 */
class SafeBrowsePassFlowStatesTest {

    @Test
    fun rawPurchasedPlusInactiveEntitlementIsVerifying() {
        assertEquals(
            SafeBrowsePassPurchaseState.Verifying,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Purchased,
                pendingKind = null,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun rawPurchasedPlusActiveEntitlementIsPurchased() {
        assertEquals(
            SafeBrowsePassPurchaseState.Purchased,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Purchased,
                pendingKind = null,
                entitlementActive = true,
            ),
        )
    }

    @Test
    fun rawPendingPlusInitialPurchaseIsPending() {
        assertEquals(
            SafeBrowsePassPurchaseState.Pending,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Pending,
                pendingKind = SafeBrowsePassPendingKind.InitialPurchase,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun rawPendingPlusTopUpIsPendingTopUp() {
        assertEquals(
            SafeBrowsePassPurchaseState.PendingTopUp,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Pending,
                pendingKind = SafeBrowsePassPendingKind.TopUp,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun rawPendingPlusNullKindIsPending() {
        assertEquals(
            SafeBrowsePassPurchaseState.Pending,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Pending,
                pendingKind = null,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun purchasedAndVerifyingIsVerifying() {
        assertEquals(
            SafeBrowsePassPurchaseState.Verifying,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.PurchasedAndVerifying,
                pendingKind = null,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun noPurchaseFoundIsIdle() {
        assertEquals(
            SafeBrowsePassPurchaseState.Idle,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.NoPurchaseFound,
                pendingKind = null,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun restoredRawLegacyStateIsIdle() {
        assertEquals(
            SafeBrowsePassPurchaseState.Idle,
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Restored,
                pendingKind = null,
                entitlementActive = false,
            ),
        )
    }

    @Test
    fun managerErrorProducesStableAppOwnedErrorText() {
        val first = resolveSafeBrowsePassPurchaseState(
            billingState = SafeBrowsePassBillingUiState.Error(responseCode = 6, retryable = false),
            pendingKind = null,
            entitlementActive = false,
        )
        val second = resolveSafeBrowsePassPurchaseState(
            billingState = SafeBrowsePassBillingUiState.Error(responseCode = 3, retryable = true),
            pendingKind = null,
            entitlementActive = false,
        )
        first as SafeBrowsePassPurchaseState.Error
        second as SafeBrowsePassPurchaseState.Error
        // Different raw response codes must still produce the exact same stable, app-owned
        // message -- the message must never vary with (or embed) the raw BillingResult.
        assertEquals(first.message, second.message)
        assertEquals("Something went wrong. Please try again.", first.message)
    }

    @Test
    fun noRawBillingResultDebugMessageReachesPublicState() {
        val states = listOf(
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.Error(responseCode = 6, retryable = false),
                pendingKind = null,
                entitlementActive = false,
            ),
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.VerificationFailed,
                pendingKind = null,
                entitlementActive = false,
            ),
            resolveSafeBrowsePassPurchaseState(
                billingState = SafeBrowsePassBillingUiState.NetworkOrServiceUnavailable(
                    reason = BillingUnavailableReason.ServiceDisconnected,
                ),
                pendingKind = null,
                entitlementActive = false,
            ),
        )

        states.forEach { state ->
            assertTrue(state is SafeBrowsePassPurchaseState.Error)
            val message = (state as SafeBrowsePassPurchaseState.Error).message
            listOf("BillingResponseCode", "responseCode", "6", "retryable", "ServiceDisconnected")
                .forEach { rawFragment ->
                    assertTrue(
                        "public error message unexpectedly leaked a raw fragment: $rawFragment",
                        !message.contains(rawFragment),
                    )
                }
        }
    }
}
