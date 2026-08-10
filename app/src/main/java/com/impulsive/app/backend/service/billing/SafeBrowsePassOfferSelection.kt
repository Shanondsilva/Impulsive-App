package com.impulsive.app.backend.service.billing

import java.time.Period

enum class SafeBrowsePassPeriod {
    Monthly,
    Prepaid,
}

data class SelectedSafeBrowsePassPlan(
    val period: SafeBrowsePassPeriod,
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriod: String,
)

sealed interface SafeBrowsePassCatalogState {
    data object Loading : SafeBrowsePassCatalogState

    data class Ready(
        val monthly: SelectedSafeBrowsePassPlan?,
        val prepaid: SelectedSafeBrowsePassPlan?,
    ) : SafeBrowsePassCatalogState

    data object Unavailable : SafeBrowsePassCatalogState
}

private fun SubscriptionOfferSnapshot.isSafeBrowseStructurallyValid(): Boolean =
    basePlanId.isNotBlank() &&
        offerToken.isNotBlank() &&
        pricingPhases.isNotEmpty()

internal fun safeBrowsePassPeriodLabel(billingPeriod: String): String? {
    return when (billingPeriod) {
        "P1W",
        "P7D",
        -> "7 days"

        "P15D" -> "15 days"

        "P1M" -> "1 month"

        "P3M" -> "3 months"

        "P1Y" -> "1 year"

        else -> {
            val parsed = runCatching { Period.parse(billingPeriod) }.getOrNull()
                ?: return null

            if (parsed.isZero || parsed.isNegative) {
                return null
            }

            when {
                parsed.years > 0 && parsed.months == 0 && parsed.days == 0 ->
                    if (parsed.years == 1) "1 year" else "${parsed.years} years"

                parsed.years == 0 && parsed.months > 0 && parsed.days == 0 ->
                    if (parsed.months == 1) "1 month" else "${parsed.months} months"

                parsed.years == 0 && parsed.months == 0 && parsed.days > 0 ->
                    if (parsed.days == 1) "1 day" else "${parsed.days} days"

                else -> billingPeriod
            }
        }
    }
}

private fun safeBrowsePassPeriodSortValue(billingPeriod: String): Long? {
    val parsed = runCatching { Period.parse(billingPeriod) }.getOrNull()
        ?: return null

    if (parsed.isZero || parsed.isNegative) {
        return null
    }

    /*
     * This approximate value is used only to choose a stable display order between
     * eligible prepaid offers. It is never used to calculate access, expiry, price,
     * renewal or entitlement.
     */
    return parsed.years.toLong() * 372L +
        parsed.months.toLong() * 31L +
        parsed.days.toLong()
}

internal fun selectSafeBrowsePassMonthlyPlan(
    productId: String,
    offers: List<SubscriptionOfferSnapshot>,
    infiniteRecurringMode: Int,
): SelectedSafeBrowsePassPlan? {
    if (productId != BillingManager.SafeBrowsePassProductId) {
        return null
    }

    return offers.mapNotNull { offer ->
        if (!offer.isSafeBrowseStructurallyValid()) {
            return@mapNotNull null
        }

        val recurringPhases = offer.pricingPhases.filter { phase ->
            phase.priceAmountMicros > 0L &&
                phase.recurrenceMode == infiniteRecurringMode &&
                phase.billingPeriod == MonthlyRecurringBillingPeriod
        }

        if (recurringPhases.size != 1) {
            return@mapNotNull null
        }

        val recurringPhase = recurringPhases.single()

        val otherPhases = offer.pricingPhases - recurringPhase

        val hasSupportedTrial = otherPhases.size == 1 &&
            otherPhases.single().priceAmountMicros == 0L &&
            otherPhases.single().billingPeriod == PreferredMonthlyTrialPeriod

        if (otherPhases.isNotEmpty() && !hasSupportedTrial) {
            return@mapNotNull null
        }

        val priority = if (hasSupportedTrial) 0 else 1

        priority to SelectedSafeBrowsePassPlan(
            period = SafeBrowsePassPeriod.Monthly,
            productId = productId,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            formattedPrice = recurringPhase.formattedPrice,
            billingPeriod = recurringPhase.billingPeriod,
        )
    }.sortedWith(
        compareBy<Pair<Int, SelectedSafeBrowsePassPlan>>(
            { it.first },
            { it.second.basePlanId },
            { it.second.offerId.orEmpty() },
            { it.second.offerToken },
        ),
    ).firstOrNull()?.second
}

internal fun selectSafeBrowsePassPrepaidPlan(
    productId: String,
    offers: List<SubscriptionOfferSnapshot>,
    nonRecurringMode: Int,
    requiredBasePlanId: String? = null,
): SelectedSafeBrowsePassPlan? {
    if (productId != BillingManager.SafeBrowsePassProductId) {
        return null
    }

    val normalizedRequiredBasePlan = requiredBasePlanId?.trim()?.takeIf { it.isNotEmpty() }

    return offers.mapNotNull { offer ->
        if (!offer.isSafeBrowseStructurallyValid()) {
            return@mapNotNull null
        }

        if (normalizedRequiredBasePlan != null && offer.basePlanId != normalizedRequiredBasePlan) {
            return@mapNotNull null
        }

        if (offer.pricingPhases.size != 1) {
            return@mapNotNull null
        }

        val phase = offer.pricingPhases.single()

        if (
            phase.priceAmountMicros <= 0L ||
            phase.recurrenceMode != nonRecurringMode ||
            safeBrowsePassPeriodLabel(phase.billingPeriod) == null
        ) {
            return@mapNotNull null
        }

        val sortValue = safeBrowsePassPeriodSortValue(phase.billingPeriod)
            ?: return@mapNotNull null

        sortValue to SelectedSafeBrowsePassPlan(
            period = SafeBrowsePassPeriod.Prepaid,
            productId = productId,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            formattedPrice = phase.formattedPrice,
            billingPeriod = phase.billingPeriod,
        )
    }.sortedWith(
        compareBy<Pair<Long, SelectedSafeBrowsePassPlan>>(
            { it.first },
            { it.second.basePlanId },
            { it.second.offerId.orEmpty() },
            { it.second.offerToken },
        ),
    ).firstOrNull()?.second
}

internal fun safeBrowsePassPlanDisclosure(period: SafeBrowsePassPeriod): String =
    when (period) {
        SafeBrowsePassPeriod.Monthly -> "Auto-renews until cancelled."
        SafeBrowsePassPeriod.Prepaid -> "Prepaid access. Top up again when you choose."
    }
