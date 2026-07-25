package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.premium.BillingPeriod

internal const val MonthlyRecurringBillingPeriod = "P1M"
internal const val YearlyRecurringBillingPeriod = "P1Y"
internal const val PreferredMonthlyTrialPeriod = "P7D"

internal data class SubscriptionPricingPhaseSnapshot(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val billingPeriod: String,
    val recurrenceMode: Int,
    val billingCycleCount: Int,
)

internal data class SubscriptionOfferSnapshot(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val offerTags: List<String>,
    val pricingPhases: List<SubscriptionPricingPhaseSnapshot>,
)

data class SelectedSubscriptionPlan(
    val period: BillingPeriod,
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val recurringFormattedPrice: String,
    val recurringBillingPeriod: String,
    val trialBillingPeriod: String?,
)

sealed interface SubscriptionCatalogState {
    data object Loading : SubscriptionCatalogState

    data class Ready(
        val monthly: SelectedSubscriptionPlan?,
        val yearly: SelectedSubscriptionPlan?,
    ) : SubscriptionCatalogState

    data object Unavailable : SubscriptionCatalogState
}

internal fun SubscriptionPricingPhaseSnapshot.isInfinitePaidRecurringPhase(
    expectedBillingPeriod: String,
    infiniteRecurringMode: Int,
): Boolean {
    return priceAmountMicros > 0L &&
        recurrenceMode == infiniteRecurringMode &&
        billingPeriod == expectedBillingPeriod
}

internal fun SubscriptionPricingPhaseSnapshot.isFreeTrialPhase(
    expectedTrialPeriod: String,
): Boolean {
    return priceAmountMicros == 0L &&
        billingPeriod == expectedTrialPeriod
}

internal fun selectMonthlySubscriptionPlan(
    productId: String,
    offers: List<SubscriptionOfferSnapshot>,
    infiniteRecurringMode: Int,
): SelectedSubscriptionPlan? {
    return offers.mapNotNull { offer ->
        val recurringPhase = offer.pricingPhases.firstOrNull { phase ->
            phase.isInfinitePaidRecurringPhase(
                expectedBillingPeriod = MonthlyRecurringBillingPeriod,
                infiniteRecurringMode = infiniteRecurringMode,
            )
        } ?: return@mapNotNull null

        val exactTrial = offer.pricingPhases.size == 2 &&
            offer.pricingPhases.any { phase ->
                phase.isFreeTrialPhase(PreferredMonthlyTrialPeriod)
            }
        val recurringOnly = offer.pricingPhases.size == 1

        if (!exactTrial && !recurringOnly) {
            return@mapNotNull null
        }

        val priority = if (exactTrial) 0 else 1
        priority to SelectedSubscriptionPlan(
            period = BillingPeriod.Monthly,
            productId = productId,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            recurringFormattedPrice = recurringPhase.formattedPrice,
            recurringBillingPeriod = recurringPhase.billingPeriod,
            trialBillingPeriod = if (exactTrial) PreferredMonthlyTrialPeriod else null,
        )
    }.sortedWith(
        compareBy<Pair<Int, SelectedSubscriptionPlan>>(
            { it.first },
            { it.second.basePlanId },
            { it.second.offerId.orEmpty() },
            { it.second.offerToken },
        ),
    ).firstOrNull()?.second
}

internal fun selectYearlySubscriptionPlan(
    productId: String,
    offers: List<SubscriptionOfferSnapshot>,
    infiniteRecurringMode: Int,
): SelectedSubscriptionPlan? {
    return offers.mapNotNull { offer ->
        if (offer.pricingPhases.size != 1) {
            return@mapNotNull null
        }

        val recurringPhase = offer.pricingPhases.single().takeIf { phase ->
            phase.isInfinitePaidRecurringPhase(
                expectedBillingPeriod = YearlyRecurringBillingPeriod,
                infiniteRecurringMode = infiniteRecurringMode,
            )
        } ?: return@mapNotNull null

        SelectedSubscriptionPlan(
            period = BillingPeriod.Yearly,
            productId = productId,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            recurringFormattedPrice = recurringPhase.formattedPrice,
            recurringBillingPeriod = recurringPhase.billingPeriod,
            trialBillingPeriod = null,
        )
    }.sortedWith(
        compareBy<SelectedSubscriptionPlan>(
            { it.basePlanId },
            { it.offerId.orEmpty() },
            { it.offerToken },
        ),
    ).firstOrNull()
}

internal fun recurringPeriodLabel(billingPeriod: String): String? =
    when (billingPeriod) {
        MonthlyRecurringBillingPeriod -> "month"
        YearlyRecurringBillingPeriod -> "year"
        else -> null
    }

internal fun trialPeriodLabel(billingPeriod: String): String? =
    when (billingPeriod) {
        PreferredMonthlyTrialPeriod -> "7 days"
        else -> null
    }

internal fun subscriptionPlanTitle(plan: SelectedSubscriptionPlan): String {
    val recurringPeriod = recurringPeriodLabel(plan.recurringBillingPeriod)
        ?: return plan.recurringFormattedPrice

    val trialPeriod = plan.trialBillingPeriod?.let(::trialPeriodLabel)

    return if (trialPeriod != null) {
        "$trialPeriod free, then ${plan.recurringFormattedPrice}/$recurringPeriod"
    } else {
        "${plan.recurringFormattedPrice}/$recurringPeriod"
    }
}

internal fun subscriptionPlanDisclosure(
    plan: SelectedSubscriptionPlan,
): String = "Auto-renews until cancelled."
