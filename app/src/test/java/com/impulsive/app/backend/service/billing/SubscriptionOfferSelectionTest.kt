package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionOfferSelectionTest {

    @Test
    fun `seven-day trial uses paid recurring phase for display`() {
        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(trialMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertEquals("£4.99", plan.recurringFormattedPrice)
        assertEquals(PreferredMonthlyTrialPeriod, plan.trialBillingPeriod)
        assertEquals("7 days free, then £4.99/month", subscriptionPlanTitle(plan))
        assertEquals("Auto-renews until cancelled.", subscriptionPlanDisclosure(plan))
    }

    @Test
    fun `recurring-only monthly plan has no trial`() {
        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(recurringMonthlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertNull(plan.trialBillingPeriod)
        assertEquals("£4.99/month", subscriptionPlanTitle(plan))
    }

    @Test
    fun `trial-ineligible user falls back to recurring-only monthly`() {
        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(recurringMonthlyOffer(basePlanId = "monthly-base")),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertEquals("monthly-base", plan?.basePlanId)
    }

    @Test
    fun `eligible trial is preferred over recurring-only regardless of input order`() {
        val recurring = recurringMonthlyOffer(basePlanId = "a-recurring")
        val trial = trialMonthlyOffer(basePlanId = "z-trial")

        listOf(
            listOf(recurring, trial),
            listOf(trial, recurring),
        ).forEach { offers ->
            val plan = selectMonthlySubscriptionPlan(
                productId = BillingManager.PlusProductId,
                offers = offers,
                infiniteRecurringMode = InfiniteRecurring,
            )
            assertEquals("z-trial", plan?.basePlanId)
            assertEquals(PreferredMonthlyTrialPeriod, plan?.trialBillingPeriod)
        }
    }

    @Test
    fun `three-day free phase is not represented as seven-day trial`() {
        val threeDayOffer = offer(
            basePlanId = "three-day",
            phases = listOf(
                phase("£0.00", 0L, "P3D", FiniteRecurring, 1),
                monthlyRecurringPhase(),
            ),
        )
        val recurring = recurringMonthlyOffer(basePlanId = "recurring")

        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(threeDayOffer, recurring),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertEquals("recurring", plan?.basePlanId)
        assertNull(plan?.trialBillingPeriod)
    }

    @Test
    fun `unmodeled discounted introductory offer is rejected`() {
        val discounted = offer(
            basePlanId = "discounted",
            phases = listOf(
                phase("£0.99", 990_000L, "P1M", FiniteRecurring, 1),
                monthlyRecurringPhase(),
            ),
        )
        val recurring = recurringMonthlyOffer(basePlanId = "supported")

        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(discounted, recurring),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertEquals("supported", plan?.basePlanId)
    }

    @Test
    fun `equivalent trial offers use stable identifier ordering`() {
        val later = trialMonthlyOffer(
            basePlanId = "base-b",
            offerId = "offer-a",
            offerToken = "token-a",
        )
        val earlier = trialMonthlyOffer(
            basePlanId = "base-a",
            offerId = "offer-z",
            offerToken = "token-z",
        )

        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(later, earlier),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertEquals("base-a", plan?.basePlanId)
    }

    @Test
    fun `selected plan retains exact winning offer token`() {
        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(
                trialMonthlyOffer(
                    basePlanId = "base",
                    offerId = "offer",
                    offerToken = "winning-offer-token",
                ),
            ),
            infiniteRecurringMode = InfiniteRecurring,
        )

        assertEquals("winning-offer-token", plan?.offerToken)
    }

    @Test
    fun `yearly recurring plan uses paid yearly phase`() {
        val plan = selectYearlySubscriptionPlan(
            productId = BillingManager.PlusYearlyProductId,
            offers = listOf(recurringYearlyOffer()),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertEquals("£39.99", plan.recurringFormattedPrice)
        assertEquals(YearlyRecurringBillingPeriod, plan.recurringBillingPeriod)
        assertNull(plan.trialBillingPeriod)
        assertEquals("£39.99/year", subscriptionPlanTitle(plan))
    }

    @Test
    fun `yearly trial offer is rejected`() {
        val offer = offer(
            basePlanId = "yearly-trial",
            phases = listOf(
                phase("£0.00", 0L, "P7D", FiniteRecurring, 1),
                yearlyRecurringPhase(),
            ),
        )

        assertNull(
            selectYearlySubscriptionPlan(
                productId = BillingManager.PlusYearlyProductId,
                offers = listOf(offer),
                infiniteRecurringMode = InfiniteRecurring,
            ),
        )
    }

    @Test
    fun `empty monthly offers are unavailable`() {
        assertNull(
            selectMonthlySubscriptionPlan(
                productId = BillingManager.PlusProductId,
                offers = emptyList(),
                infiniteRecurringMode = InfiniteRecurring,
            ),
        )
    }

    @Test
    fun `empty yearly offers are unavailable`() {
        assertNull(
            selectYearlySubscriptionPlan(
                productId = BillingManager.PlusYearlyProductId,
                offers = emptyList(),
                infiniteRecurringMode = InfiniteRecurring,
            ),
        )
    }

    @Test
    fun `monthly offer without infinite monthly phase is rejected`() {
        val offer = offer(
            basePlanId = "wrong-monthly",
            phases = listOf(yearlyRecurringPhase()),
        )

        assertNull(
            selectMonthlySubscriptionPlan(
                productId = BillingManager.PlusProductId,
                offers = listOf(offer),
                infiniteRecurringMode = InfiniteRecurring,
            ),
        )
    }

    @Test
    fun `yearly offer without infinite yearly phase is rejected`() {
        val offer = offer(
            basePlanId = "wrong-yearly",
            phases = listOf(monthlyRecurringPhase()),
        )

        assertNull(
            selectYearlySubscriptionPlan(
                productId = BillingManager.PlusYearlyProductId,
                offers = listOf(offer),
                infiniteRecurringMode = InfiniteRecurring,
            ),
        )
    }

    @Test
    fun `localized recurring price is preserved exactly`() {
        val localized = recurringMonthlyOffer(
            recurringPhase = monthlyRecurringPhase(formattedPrice = "€5,49"),
        )
        val plan = selectMonthlySubscriptionPlan(
            productId = BillingManager.PlusProductId,
            offers = listOf(localized),
            infiniteRecurringMode = InfiniteRecurring,
        )!!

        assertEquals("€5,49/month", subscriptionPlanTitle(plan))
        assertTrue(plan.recurringFormattedPrice == "€5,49")
    }

    private fun trialMonthlyOffer(
        basePlanId: String = "monthly",
        offerId: String? = "trial",
        offerToken: String = "trial-token",
    ): SubscriptionOfferSnapshot = offer(
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = offerToken,
        phases = listOf(
            phase("£0.00", 0L, "P7D", FiniteRecurring, 1),
            monthlyRecurringPhase(),
        ),
    )

    private fun recurringMonthlyOffer(
        basePlanId: String = "monthly",
        recurringPhase: SubscriptionPricingPhaseSnapshot = monthlyRecurringPhase(),
    ): SubscriptionOfferSnapshot = offer(
        basePlanId = basePlanId,
        offerId = null,
        offerToken = "$basePlanId-token",
        phases = listOf(recurringPhase),
    )

    private fun recurringYearlyOffer(): SubscriptionOfferSnapshot = offer(
        basePlanId = "yearly",
        offerId = null,
        offerToken = "yearly-token",
        phases = listOf(yearlyRecurringPhase()),
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
        formattedPrice: String = "£4.99",
    ) = phase(
        formattedPrice = formattedPrice,
        priceAmountMicros = 4_990_000L,
        billingPeriod = MonthlyRecurringBillingPeriod,
        recurrenceMode = InfiniteRecurring,
        billingCycleCount = 0,
    )

    private fun yearlyRecurringPhase() = phase(
        formattedPrice = "£39.99",
        priceAmountMicros = 39_990_000L,
        billingPeriod = YearlyRecurringBillingPeriod,
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
