package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingRestorePolicyTest {

    @Test
    fun `backend verified purchase resolves success`() {
        assertEquals(
            BillingRestoreState.Success,
            resolve(
                verifiedActivePurchaseCount = 1,
                serverRefreshResult = ServerEntitlementRefreshResult.Unavailable,
            ),
        )
    }

    @Test
    fun `server active resolves success without verified local purchase`() {
        assertEquals(
            BillingRestoreState.Success,
            resolve(serverRefreshResult = ServerEntitlementRefreshResult.Active),
        )
    }

    @Test
    fun `no Play purchase and inactive server resolves no purchase`() {
        assertEquals(
            BillingRestoreState.NoPurchase,
            resolve(serverRefreshResult = ServerEntitlementRefreshResult.Inactive),
        )
    }

    @Test
    fun `returned Play purchase no longer active resolves no purchase`() {
        assertEquals(
            BillingRestoreState.NoPurchase,
            resolve(
                verifiedActivePurchaseCount = 0,
                verificationFailed = false,
                serverRefreshResult = ServerEntitlementRefreshResult.Inactive,
            ),
        )
    }

    @Test
    fun `definitive inactive server overrides earlier verified purchase`() {
        assertEquals(
            BillingRestoreState.NoPurchase,
            resolve(
                playQuerySucceeded = true,
                verifiedActivePurchaseCount = 1,
                verificationFailed = false,
                serverRefreshResult = ServerEntitlementRefreshResult.Inactive,
            ),
        )
    }

    @Test
    fun `verified purchase succeeds when final server refresh is temporarily unavailable`() {
        assertEquals(
            BillingRestoreState.Success,
            resolve(
                playQuerySucceeded = true,
                verifiedActivePurchaseCount = 1,
                verificationFailed = false,
                serverRefreshResult = ServerEntitlementRefreshResult.Unavailable,
            ),
        )
    }

    @Test
    fun `definitive active server overrides local verification failure`() {
        assertEquals(
            BillingRestoreState.Success,
            resolve(
                playQuerySucceeded = true,
                verifiedActivePurchaseCount = 0,
                verificationFailed = true,
                serverRefreshResult = ServerEntitlementRefreshResult.Active,
            ),
        )
    }

    @Test
    fun `Play query failure resolves error even when server is inactive`() {
        assertEquals(
            BillingRestoreState.Error,
            resolve(
                playQuerySucceeded = false,
                serverRefreshResult = ServerEntitlementRefreshResult.Inactive,
            ),
        )
    }

    @Test
    fun `backend verification failure and unavailable server resolves error`() {
        assertEquals(
            BillingRestoreState.Error,
            resolve(
                verificationFailed = true,
                serverRefreshResult = ServerEntitlementRefreshResult.Unavailable,
            ),
        )
    }

    @Test
    fun `unavailable server without verified purchase resolves error`() {
        assertEquals(
            BillingRestoreState.Error,
            resolve(serverRefreshResult = ServerEntitlementRefreshResult.Unavailable),
        )
    }

    @Test
    fun `missing authenticated user resolves error`() {
        assertEquals(
            BillingRestoreState.Error,
            resolve(
                serverRefreshResult =
                    ServerEntitlementRefreshResult.SkippedNoAuthenticatedUser,
            ),
        )
    }

    @Test
    fun `negative verified count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolve(verifiedActivePurchaseCount = -1)
        }
    }

    private fun resolve(
        playQuerySucceeded: Boolean = true,
        verifiedActivePurchaseCount: Int = 0,
        verificationFailed: Boolean = false,
        serverRefreshResult: ServerEntitlementRefreshResult =
            ServerEntitlementRefreshResult.Unavailable,
    ): BillingRestoreState = resolveBillingRestoreState(
        playQuerySucceeded = playQuerySucceeded,
        verifiedActivePurchaseCount = verifiedActivePurchaseCount,
        verificationFailed = verificationFailed,
        serverRefreshResult = serverRefreshResult,
    )
}
