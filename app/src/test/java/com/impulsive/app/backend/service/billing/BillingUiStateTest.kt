package com.impulsive.app.backend.service.billing

import com.android.billingclient.api.BillingClient
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingUiStateTest {
    @Test
    fun `OK does not imply purchase success`() {
        assertNull(billingFailureStateForResponseCode(BillingClient.BillingResponseCode.OK))
    }

    @Test
    fun `user cancellation is informational and allows retry`() {
        val state = billingFailureStateForResponseCode(
            BillingClient.BillingResponseCode.USER_CANCELED,
        )

        assertEquals(BillingUiState.UserCancelled, state)
        assertFalse(state is BillingUiState.Error)
        assertTrue(requireNotNull(state).allowsPurchaseAction())
    }

    @Test
    fun `ownership and product response codes map to typed states`() {
        assertEquals(
            BillingUiState.AlreadyOwned,
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            ),
        )
        assertEquals(
            BillingUiState.ProductUnavailable,
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            ),
        )
        assertEquals(
            BillingUiState.NoPurchaseFound,
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
            ),
        )
    }

    @Test
    fun `network and service response codes preserve their reason`() {
        assertEquals(
            BillingUiState.NetworkOrServiceUnavailable(BillingUnavailableReason.NetworkError),
            billingFailureStateForResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR),
        )
        assertEquals(
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceDisconnected,
            ),
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            ),
        )
        assertEquals(
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceUnavailable,
            ),
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            ),
        )
        assertEquals(
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.BillingUnavailable,
            ),
            billingFailureStateForResponseCode(
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `generic error is retryable`() {
        assertEquals(
            BillingUiState.Error(
                responseCode = BillingClient.BillingResponseCode.ERROR,
                retryable = true,
            ),
            billingFailureStateForResponseCode(BillingClient.BillingResponseCode.ERROR),
        )
    }

    @Test
    fun `developer feature and unknown errors are not retryable`() {
        listOf(
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            999,
        ).forEach { responseCode ->
            assertEquals(
                BillingUiState.Error(responseCode = responseCode, retryable = false),
                billingFailureStateForResponseCode(responseCode),
            )
        }
    }

    @Test
    fun `only eligible terminal states allow purchase`() {
        listOf(
            BillingUiState.Ready,
            BillingUiState.UserCancelled,
            BillingUiState.NoPurchaseFound,
        ).forEach { state ->
            assertTrue(state.allowsPurchaseAction())
        }

        listOf(
            BillingUiState.Connecting,
            BillingUiState.ProductUnavailable,
            BillingUiState.PurchaseLaunching(BillingPeriod.Monthly),
            BillingUiState.Pending,
            BillingUiState.PurchasedAndVerifying,
            BillingUiState.VerificationDeferred,
            BillingUiState.AlreadyOwned,
            BillingUiState.NetworkOrServiceUnavailable(
                BillingUnavailableReason.ServiceDisconnected,
            ),
            BillingUiState.VerificationFailed,
            BillingUiState.Restored,
            BillingUiState.Error(responseCode = 999, retryable = false),
        ).forEach { state ->
            assertFalse(state.allowsPurchaseAction())
        }
    }

    @Test
    fun `billing reconciliation runs only when an authenticated user becomes available`() {
        assertTrue(shouldReconcileBillingAfterAuthChange(null, "user-a"))
        assertTrue(shouldReconcileBillingAfterAuthChange("user-a", "user-b"))
        assertFalse(shouldReconcileBillingAfterAuthChange("user-a", "user-a"))
        assertFalse(shouldReconcileBillingAfterAuthChange("user-a", null))
        assertFalse(shouldReconcileBillingAfterAuthChange(null, null))
    }

    @Test
    fun `reconnect policy has exactly three bounded delays`() {
        assertEquals(1_000L, BillingReconnectPolicy.delayForAttempt(0))
        assertEquals(2_000L, BillingReconnectPolicy.delayForAttempt(1))
        assertEquals(5_000L, BillingReconnectPolicy.delayForAttempt(2))
        assertNull(BillingReconnectPolicy.delayForAttempt(3))
        assertNull(BillingReconnectPolicy.delayForAttempt(-1))
    }
}
