package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEntitlementRefreshPolicyTest {

    @Test
    fun `active monthly response resolves active`() {
        val resolution = resolveServerEntitlementResponse(
            data = activeResponse(
                productId = BillingManager.PlusProductId,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
            nowMillis = 100_000L,
        )

        assertEquals(
            ServerEntitlementResolution.Active(
                productId = BillingManager.PlusProductId,
                period = BillingPeriod.Monthly,
                expiryTimeMillis = 200_000L,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
            resolution,
        )
    }

    @Test
    fun `cancelled subscription remains active before expiry`() {
        val resolution = resolveServerEntitlementResponse(
            data = activeResponse(
                productId = BillingManager.PlusProductId,
                subscriptionState = "SUBSCRIPTION_STATE_CANCELED",
            ),
            nowMillis = 100_000L,
        )

        assertTrue(resolution is ServerEntitlementResolution.Active)
    }

    @Test
    fun `grace period subscription remains active before expiry`() {
        val resolution = resolveServerEntitlementResponse(
            data = activeResponse(
                productId = BillingManager.PlusYearlyProductId,
                subscriptionState = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
            ),
            nowMillis = 100_000L,
        )

        assertEquals(
            BillingPeriod.Yearly,
            (resolution as ServerEntitlementResolution.Active).period,
        )
    }

    @Test
    fun `expired response resolves inactive`() {
        val resolution = resolveServerEntitlementResponse(
            data = mapOf(
                "active" to false,
                "productId" to BillingManager.PlusProductId,
                "subscriptionState" to "SUBSCRIPTION_STATE_EXPIRED",
                "expiryTimeMillis" to 100_000L,
            ),
            nowMillis = 100_000L,
        )

        assertEquals(
            ServerEntitlementResolution.Inactive("SUBSCRIPTION_STATE_EXPIRED"),
            resolution,
        )
    }

    @Test
    fun `revoked response resolves inactive despite future expiry`() {
        val resolution = resolveServerEntitlementResponse(
            data = mapOf(
                "active" to false,
                "productId" to BillingManager.PlusProductId,
                "subscriptionState" to "SUBSCRIPTION_STATE_REVOKED",
                "expiryTimeMillis" to 200_000L,
            ),
            nowMillis = 100_000L,
        )

        assertEquals(
            ServerEntitlementResolution.Inactive("SUBSCRIPTION_STATE_REVOKED"),
            resolution,
        )
    }

    @Test
    fun `generic definitive inactive response resolves inactive`() {
        assertEquals(
            ServerEntitlementResolution.Inactive(subscriptionState = null),
            resolveServerEntitlementResponse(
                data = mapOf("active" to false),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `active response at reached expiry resolves inactive`() {
        val resolution = resolveServerEntitlementResponse(
            data = activeResponse(
                productId = BillingManager.PlusProductId,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
                expiryTimeMillis = 100_000L,
            ),
            nowMillis = 100_000L,
        )

        assertTrue(resolution is ServerEntitlementResolution.Inactive)
    }

    @Test
    fun `unsupported active product is retryable`() {
        val resolution = resolveServerEntitlementResponse(
            data = activeResponse(
                productId = "another_product",
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
            nowMillis = 100_000L,
        )

        assertEquals(ServerEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `malformed responses are retryable`() {
        val malformedResponses = listOf<Any?>(
            null,
            "not a map",
            emptyMap<String, Any>(),
        )

        malformedResponses.forEach { data ->
            assertEquals(
                ServerEntitlementResolution.RetryableFailure,
                resolveServerEntitlementResponse(data = data, nowMillis = 100_000L),
            )
        }
    }

    @Test
    fun `retry delays are bounded`() {
        assertEquals(2_000L, EntitlementRefreshRetryPolicy.delayAfterFailure(0))
        assertEquals(5_000L, EntitlementRefreshRetryPolicy.delayAfterFailure(1))
        assertEquals(15_000L, EntitlementRefreshRetryPolicy.delayAfterFailure(2))
        assertEquals(null, EntitlementRefreshRetryPolicy.delayAfterFailure(3))
        assertEquals(null, EntitlementRefreshRetryPolicy.delayAfterFailure(-1))
    }

    @Test
    fun `backend and App Check temporary failures are retryable outcomes`() {
        assertTrue(EntitlementRefreshOutcome.RetryableFailure.isRetryableFailure())
        assertTrue(
            EntitlementRefreshOutcome.AppCheckTemporarilyUnavailable.isRetryableFailure(),
        )
        assertTrue(!EntitlementRefreshOutcome.AppliedInactive.isRetryableFailure())
    }

    @Test
    fun `protected entitlement work requires an authenticated user`() {
        assertTrue(shouldAttemptProtectedEntitlementRefresh(hasAuthenticatedUser = true))
        assertTrue(!shouldAttemptProtectedEntitlementRefresh(hasAuthenticatedUser = false))
    }

    private fun activeResponse(
        productId: String,
        subscriptionState: String,
        expiryTimeMillis: Long = 200_000L,
    ): Map<String, Any> = mapOf(
        "active" to true,
        "productId" to productId,
        "subscriptionState" to subscriptionState,
        "expiryTimeMillis" to expiryTimeMillis,
    )
}
