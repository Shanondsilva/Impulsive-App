package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassEntitlementRefreshPolicyTest {

    // -------------------------------------------------------------------
    // resolveSafeBrowsePassRenewalState
    // -------------------------------------------------------------------

    @Test
    fun `prepaid always maps to NotApplicable`() {
        assertEquals(
            SafeBrowsePassRenewalState.NotApplicable,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = true,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
        )
        assertEquals(
            SafeBrowsePassRenewalState.NotApplicable,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = true,
                subscriptionState = null,
            ),
        )
    }

    @Test
    fun `active autoRenewing maps to Renewing`() {
        assertEquals(
            SafeBrowsePassRenewalState.Renewing,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
        )
    }

    @Test
    fun `in-grace-period autoRenewing maps to Renewing`() {
        assertEquals(
            SafeBrowsePassRenewalState.Renewing,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
            ),
        )
    }

    @Test
    fun `cancelled autoRenewing maps to CancelledUntilExpiry`() {
        assertEquals(
            SafeBrowsePassRenewalState.CancelledUntilExpiry,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_CANCELED",
            ),
        )
    }

    @Test
    fun `on-hold maps to Unknown`() {
        assertEquals(
            SafeBrowsePassRenewalState.Unknown,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_ON_HOLD",
            ),
        )
    }

    @Test
    fun `paused maps to Unknown`() {
        assertEquals(
            SafeBrowsePassRenewalState.Unknown,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_PAUSED",
            ),
        )
    }

    @Test
    fun `expired maps to Unknown`() {
        assertEquals(
            SafeBrowsePassRenewalState.Unknown,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SUBSCRIPTION_STATE_EXPIRED",
            ),
        )
    }

    @Test
    fun `null maps to Unknown`() {
        assertEquals(
            SafeBrowsePassRenewalState.Unknown,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = null,
            ),
        )
    }

    @Test
    fun `unknown string maps to Unknown`() {
        assertEquals(
            SafeBrowsePassRenewalState.Unknown,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "SOMETHING_UNRECOGNISED",
            ),
        )
    }

    @Test
    fun `mapping is case-normalised and whitespace-trimmed`() {
        assertEquals(
            SafeBrowsePassRenewalState.Renewing,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = "  subscription_state_active  ",
            ),
        )
        assertEquals(
            SafeBrowsePassRenewalState.CancelledUntilExpiry,
            resolveSafeBrowsePassRenewalState(
                isPrepaid = false,
                subscriptionState = " Subscription_State_Canceled ",
            ),
        )
    }

    @Test
    fun `active autoRenewing response resolves active and not prepaid`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing"),
            nowMillis = 100_000L,
        )

        assertEquals(
            SafeBrowsePassEntitlementResolution.Active(
                productId = BillingManager.SafeBrowsePassProductId,
                basePlanId = "monthly",
                isPrepaid = false,
                expiryTimeMillis = 200_000L,
                subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            ),
            resolution,
        )
    }

    @Test
    fun `active prepaid response resolves active and prepaid`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "prepaid", basePlanId = "prepaid-30"),
            nowMillis = 100_000L,
        )

        assertEquals(
            true,
            (resolution as SafeBrowsePassEntitlementResolution.Active).isPrepaid,
        )
        assertEquals("prepaid-30", resolution.basePlanId)
    }

    @Test
    fun `cancelled subscription remains active before expiry`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing", subscriptionState = "SUBSCRIPTION_STATE_CANCELED"),
            nowMillis = 100_000L,
        )

        assertTrue(resolution is SafeBrowsePassEntitlementResolution.Active)
    }

    @Test
    fun `expired response resolves inactive`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = mapOf(
                "active" to false,
                "productId" to BillingManager.SafeBrowsePassProductId,
                "subscriptionState" to "SUBSCRIPTION_STATE_EXPIRED",
                "expiryTimeMillis" to 100_000L,
            ),
            nowMillis = 100_000L,
        )

        assertEquals(
            SafeBrowsePassEntitlementResolution.Inactive("SUBSCRIPTION_STATE_EXPIRED"),
            resolution,
        )
    }

    @Test
    fun `active response at reached expiry resolves inactive`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing", expiryTimeMillis = 100_000L),
            nowMillis = 100_000L,
        )

        assertTrue(resolution is SafeBrowsePassEntitlementResolution.Inactive)
    }

    @Test
    fun `a Plus product id is never accepted as a Safe Browse Pass entitlement`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing", productId = BillingManager.PlusProductId),
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `an old two-product legacy product id is rejected`() {
        listOf("safe_browse_pass_monthly", "safe_browse_pass_prepaid_30_day").forEach { legacyId ->
            val resolution = resolveSafeBrowsePassEntitlementResponse(
                data = activeResponse(planKind = "autoRenewing", productId = legacyId),
                nowMillis = 100_000L,
            )

            assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
        }
    }

    @Test
    fun `an unsupported product id is retryable`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing", productId = "another_product"),
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `prepaid planKind maps isPrepaid true`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "prepaid", basePlanId = "prepaid-30"),
            nowMillis = 100_000L,
        )

        assertTrue((resolution as SafeBrowsePassEntitlementResolution.Active).isPrepaid)
    }

    @Test
    fun `autoRenewing planKind maps isPrepaid false`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing"),
            nowMillis = 100_000L,
        )

        assertTrue(!(resolution as SafeBrowsePassEntitlementResolution.Active).isPrepaid)
    }

    @Test
    fun `missing basePlanId fails`() {
        val data = activeResponse(planKind = "autoRenewing").toMutableMap()
        data.remove("basePlanId")

        val resolution = resolveSafeBrowsePassEntitlementResponse(data = data, nowMillis = 100_000L)

        assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `blank basePlanId fails`() {
        val data = activeResponse(planKind = "autoRenewing", basePlanId = "   ")

        val resolution = resolveSafeBrowsePassEntitlementResponse(data = data, nowMillis = 100_000L)

        assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `missing planKind fails`() {
        val data = activeResponse(planKind = "autoRenewing").toMutableMap()
        data.remove("planKind")

        val resolution = resolveSafeBrowsePassEntitlementResponse(data = data, nowMillis = 100_000L)

        assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
    }

    @Test
    fun `unknown planKind fails`() {
        listOf("unknown", "monthly", "").forEach { planKind ->
            val resolution = resolveSafeBrowsePassEntitlementResponse(
                data = activeResponse(planKind = planKind),
                nowMillis = 100_000L,
            )

            assertEquals(SafeBrowsePassEntitlementResolution.RetryableFailure, resolution)
        }
    }

    @Test
    fun `exact expiry is inactive`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = activeResponse(planKind = "autoRenewing", expiryTimeMillis = 500_000L),
            nowMillis = 500_000L,
        )

        assertTrue(resolution is SafeBrowsePassEntitlementResolution.Inactive)
    }

    @Test
    fun `inactive response remains inactive`() {
        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = mapOf(
                "active" to false,
                "subscriptionState" to "SUBSCRIPTION_STATE_CANCELED",
            ),
            nowMillis = 100_000L,
        )

        assertTrue(resolution is SafeBrowsePassEntitlementResolution.Inactive)
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
                SafeBrowsePassEntitlementResolution.RetryableFailure,
                resolveSafeBrowsePassEntitlementResponse(data = data, nowMillis = 100_000L),
            )
        }
    }

    private fun activeResponse(
        planKind: String,
        productId: String = BillingManager.SafeBrowsePassProductId,
        basePlanId: String = "monthly",
        subscriptionState: String = "SUBSCRIPTION_STATE_ACTIVE",
        expiryTimeMillis: Long = 200_000L,
    ): Map<String, Any> = mapOf(
        "active" to true,
        "productId" to productId,
        "basePlanId" to basePlanId,
        "planKind" to planKind,
        "subscriptionState" to subscriptionState,
        "expiryTimeMillis" to expiryTimeMillis,
    )
}
