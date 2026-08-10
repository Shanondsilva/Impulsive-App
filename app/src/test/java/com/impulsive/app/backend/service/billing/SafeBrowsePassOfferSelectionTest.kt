package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassOfferSelectionTest {

    @Test
    fun `both monthly and prepaid plans use the single shared Safe Browse Pass product id`() {
        val monthly = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(recurringMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!
        val prepaid = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(prepaidOffer(billingPeriod = "P30D")),
            nonRecurringMode = NonRecurring,
        )!!

        assertEquals("safe_browse_pass", monthly.productId)
        assertEquals("safe_browse_pass", prepaid.productId)
    }

    @Test
    fun `both plans are selected from one shared offer list`() {
        val offers = listOf(recurringMonthlyOffer(), prepaidOffer(billingPeriod = "P30D"))

        val monthly = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = offers,
            infiniteRecurringMode = InfiniteRecurring,
        )
        val prepaid = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = offers,
            nonRecurringMode = NonRecurring,
        )

        assertEquals("monthly", monthly?.basePlanId)
        assertEquals("prepaid-30", prepaid?.basePlanId)
    }

    @Test
    fun `an unrecognised product id is rejected by both selectors`() {
        val monthly = selectSafeBrowsePassMonthlyPlan(
            productId = "some_other_product",
            offers = listOf(recurringMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )
        val prepaid = selectSafeBrowsePassPrepaidPlan(
            productId = "some_other_product",
            offers = listOf(prepaidOffer(billingPeriod = "P30D")),
            nonRecurringMode = NonRecurring,
        )

        assertNull(monthly)
        assertNull(prepaid)
    }

    @Test
    fun `monthly offer selects the positive infinite recurring P1M phase`() {
        val plan = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(recurringMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertEquals(SafeBrowsePassPeriod.Monthly, plan.period)
        assertEquals("P1M", plan.billingPeriod)
        assertEquals("£2.99", plan.formattedPrice)
        assertEquals("Auto-renews until cancelled.", safeBrowsePassPlanDisclosure(plan.period))
    }

    @Test
    fun `monthly offer accepts an optional seven-day free trial phase`() {
        val plan = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(trialMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertEquals("£2.99", plan.formattedPrice)
    }

    @Test
    fun `monthly selection never returns a prepaid offer`() {
        val plan = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(prepaidOffer(billingPeriod = "P30D")),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `prepaid accepts P1W`() {
        assertPrepaidAccepted("P1W")
    }

    @Test
    fun `prepaid accepts P7D`() {
        assertPrepaidAccepted("P7D")
    }

    @Test
    fun `prepaid accepts P15D`() {
        assertPrepaidAccepted("P15D")
    }

    @Test
    fun `prepaid accepts P1M`() {
        assertPrepaidAccepted("P1M")
    }

    @Test
    fun `prepaid accepts P3M`() {
        assertPrepaidAccepted("P3M")
    }

    @Test
    fun `prepaid accepts P1Y`() {
        assertPrepaidAccepted("P1Y")
    }

    private fun assertPrepaidAccepted(billingPeriod: String) {
        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(prepaidOffer(billingPeriod = billingPeriod)),
            nonRecurringMode = NonRecurring,
        )

        assertEquals(SafeBrowsePassPeriod.Prepaid, plan?.period)
        assertEquals(billingPeriod, plan?.billingPeriod)
        assertEquals(
            "Prepaid access. Top up again when you choose.",
            safeBrowsePassPlanDisclosure(SafeBrowsePassPeriod.Prepaid),
        )
    }

    @Test
    fun `prepaid selection never returns a recurring monthly offer`() {
        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(recurringMonthlyOffer()),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `a blank offer token is rejected`() {
        val blankTokenOffer = offer(
            basePlanId = "prepaid-30",
            offerToken = "",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(blankTokenOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `a blank base plan id is rejected`() {
        val blankBasePlanOffer = offer(
            basePlanId = " ",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(blankBasePlanOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `empty pricing phases are rejected`() {
        val emptyPhasesOffer = offer(basePlanId = "prepaid-30", phases = emptyList())

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(emptyPhasesOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `a paid finite-recurring phase is rejected for both monthly and prepaid`() {
        val finiteRecurringOffer = offer(
            basePlanId = "finite",
            phases = listOf(phase("£2.99", 2_990_000L, "P1M", FiniteRecurring, 3)),
        )

        val monthly = selectSafeBrowsePassMonthlyPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(finiteRecurringOffer),
            infiniteRecurringMode = InfiniteRecurring,
        )
        val prepaid = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(finiteRecurringOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(monthly)
        assertNull(prepaid)
    }

    @Test
    fun `a free-only offer is never accepted as prepaid`() {
        val freeOnlyOffer = offer(
            basePlanId = "free-only",
            phases = listOf(phase("£0.00", 0L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(freeOnlyOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `prepaid offer with more than one phase is rejected`() {
        val multiPhaseOffer = offer(
            basePlanId = "prepaid-with-trial",
            phases = listOf(
                phase("£0.00", 0L, "P7D", NonRecurring, 1),
                phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1),
            ),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(multiPhaseOffer),
            nonRecurringMode = NonRecurring,
        )

        assertNull(plan)
    }

    @Test
    fun `a requested prepaid base plan must match exactly`() {
        val offerA = offer(
            basePlanId = "prepaid-a",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )
        val offerB = offer(
            basePlanId = "prepaid-b",
            phases = listOf(phase("£4.49", 4_490_000L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(offerA, offerB),
            nonRecurringMode = NonRecurring,
            requiredBasePlanId = "prepaid-b",
        )

        assertEquals("prepaid-b", plan?.basePlanId)
    }

    @Test
    fun `a required base plan that is not offered returns null rather than substituting another`() {
        val offerA = offer(
            basePlanId = "prepaid-a",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(offerA),
            nonRecurringMode = NonRecurring,
            requiredBasePlanId = "prepaid-missing",
        )

        assertNull(plan)
    }

    @Test
    fun `lowest base plan id wins when multiple valid prepaid offers exist`() {
        val offerA = offer(
            basePlanId = "a-prepaid",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )
        val offerZ = offer(
            basePlanId = "z-prepaid",
            phases = listOf(phase("£3.99", 3_990_000L, "P30D", NonRecurring, 1)),
        )

        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(offerZ, offerA),
            nonRecurringMode = NonRecurring,
        )

        assertEquals("a-prepaid", plan?.basePlanId)
    }

    @Test
    fun `ordering is deterministic regardless of input order`() {
        val offerA = offer(
            basePlanId = "a-prepaid",
            phases = listOf(phase("£3.49", 3_490_000L, "P30D", NonRecurring, 1)),
        )
        val offerZ = offer(
            basePlanId = "z-prepaid",
            phases = listOf(phase("£3.99", 3_990_000L, "P30D", NonRecurring, 1)),
        )

        val forward = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(offerA, offerZ),
            nonRecurringMode = NonRecurring,
        )
        val reversed = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(offerZ, offerA),
            nonRecurringMode = NonRecurring,
        )

        assertEquals(forward?.basePlanId, reversed?.basePlanId)
    }

    @Test
    fun `formattedPrice is preserved exactly as returned by the offer`() {
        val plan = selectSafeBrowsePassPrepaidPlan(
            productId = BillingManager.SafeBrowsePassProductId,
            offers = listOf(prepaidOffer(billingPeriod = "P30D", formattedPrice = "CA$4.29")),
            nonRecurringMode = NonRecurring,
        )!!

        assertEquals("CA$4.29", plan.formattedPrice)
    }

    @Test
    fun `no hard-coded product-specific price is embedded in the selection logic`() {
        // Every price used in these tests comes from the fixture's formattedPrice
        // parameter -- the production selection functions never hard-code a price.
        assertTrue(
            selectSafeBrowsePassPrepaidPlan(
                productId = BillingManager.SafeBrowsePassProductId,
                offers = listOf(prepaidOffer(billingPeriod = "P30D", formattedPrice = "€9.99")),
                nonRecurringMode = NonRecurring,
            )?.formattedPrice == "€9.99",
        )
    }

    private fun trialMonthlyOffer(): SubscriptionOfferSnapshot = offer(
        basePlanId = "monthly",
        offerId = "trial",
        offerToken = "trial-token",
        phases = listOf(
            phase("£0.00", 0L, "P7D", FiniteRecurring, 1),
            monthlyRecurringPhase(),
        ),
    )

    private fun recurringMonthlyOffer(): SubscriptionOfferSnapshot = offer(
        basePlanId = "monthly",
        offerId = null,
        offerToken = "monthly-token",
        phases = listOf(monthlyRecurringPhase()),
    )

    private fun prepaidOffer(
        billingPeriod: String,
        formattedPrice: String = "£3.49",
    ): SubscriptionOfferSnapshot = offer(
        basePlanId = "prepaid-30",
        offerId = null,
        offerToken = "prepaid-token",
        phases = listOf(phase(formattedPrice, 3_490_000L, billingPeriod, NonRecurring, 1)),
    )

    private fun offer(
        basePlanId: String,
        offerId: String? = null,
        offerToken: String = "$basePlanId-token",
        phases: List<SubscriptionPricingPhaseSnapshot>,
    ) = SubscriptionOfferSnapshot(
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = offerToken,
        offerTags = emptyList(),
        pricingPhases = phases,
    )

    private fun monthlyRecurringPhase(
        formattedPrice: String = "£2.99",
    ) = phase(
        formattedPrice = formattedPrice,
        priceAmountMicros = 2_990_000L,
        billingPeriod = MonthlyRecurringBillingPeriod,
        recurrenceMode = InfiniteRecurring,
        billingCycleCount = 0,
    )

    private fun phase(
        formattedPrice: String,
        priceAmountMicros: Long,
        billingPeriod: String,
        recurrenceMode: Int,
        billingCycleCount: Int,
    ) = SubscriptionPricingPhaseSnapshot(
        formattedPrice = formattedPrice,
        priceAmountMicros = priceAmountMicros,
        billingPeriod = billingPeriod,
        recurrenceMode = recurrenceMode,
        billingCycleCount = billingCycleCount,
    )

    private companion object {
        const val InfiniteRecurring = 1
        const val FiniteRecurring = 2
        const val NonRecurring = 3
    }
}
