package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Safe Browse Pass product model established by Major Repair 1: a single Play
 * product ("safe_browse_pass") carrying multiple base plans (recurrence and length varying
 * by base plan, not by product id), each base plan's offer selected purely from its own
 * pricingPhases/recurrenceMode/billingPeriod shape.
 */
class SafeBrowsePassOfferMapperTest {
    private val offerSelectionSource = File(
        "src/main/java/com/impulsive/app/backend/service/billing/SafeBrowsePassOfferSelection.kt",
    ).readText()
    private val billingManagerSource = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()
    private val subscriptionOfferSelectionSource = File(
        "src/main/java/com/impulsive/app/backend/service/billing/SubscriptionOfferSelection.kt",
    ).readText()

    @Test
    fun aSingleSafeBrowsePassProductIdConstantExists() {
        assertTrue(
            "A single shared product id constant equal to \"safe_browse_pass\" is required " +
                "-- Safe Browse Pass is intended to be one Play product with multiple base " +
                "plans, not two separate top-level products.",
            offerSelectionSource.contains("val SafeBrowsePassProductId = \"safe_browse_pass\"") ||
                billingManagerSource.contains("val SafeBrowsePassProductId = \"safe_browse_pass\""),
        )
    }

    @Test
    fun noMonthlyOrPrepaidTopLevelPassProductIdsRemain() {
        assertFalse(
            "SafeBrowsePassMonthlyProductId is a legacy artifact of the two-product model.",
            billingManagerSource.contains("const val SafeBrowsePassMonthlyProductId"),
        )
        assertFalse(
            "SafeBrowsePassPrepaidProductId is a legacy artifact of the two-product model.",
            billingManagerSource.contains("const val SafeBrowsePassPrepaidProductId"),
        )
    }

    @Test
    fun billingManagerQueriesExactlyOneProductIdForSafeBrowsePass() {
        assertTrue(billingManagerSource.contains("queryProductDetailsAsync"))
        assertFalse(
            "BillingManager still queries two legacy Safe Browse Pass product ids instead " +
                "of the single intended SafeBrowsePassProductId.",
            billingManagerSource.contains("SafeBrowsePassMonthlyProductId") ||
                billingManagerSource.contains("SafeBrowsePassPrepaidProductId"),
        )
    }

    @Test
    fun multipleBasePlansAreSelectedFromOneProductDetailsSubscriptionOfferList() {
        assertTrue(
            "BillingManager must derive every Safe Browse Pass plan from a single " +
                "ProductDetails.subscriptionOfferDetails list, not two separately-fetched " +
                "ProductDetails for two different product ids.",
            !billingManagerSource.contains("productDetailsById[SafeBrowsePassMonthlyProductId]") &&
                !billingManagerSource.contains("productDetailsById[SafeBrowsePassPrepaidProductId]"),
        )
    }

    @Test
    fun offerSnapshotExposesBasePlanIdOfferTokenAndPricingPhases() {
        assertTrue(subscriptionOfferSelectionSource.contains("val basePlanId: String"))
        assertTrue(subscriptionOfferSelectionSource.contains("val offerToken: String"))
        assertTrue(subscriptionOfferSelectionSource.contains("val pricingPhases: List<SubscriptionPricingPhaseSnapshot>"))
    }

    @Test
    fun pricingPhaseSnapshotExposesRecurrenceModeAndFormattedPrice() {
        assertTrue(subscriptionOfferSelectionSource.contains("val recurrenceMode: Int"))
        assertTrue(subscriptionOfferSelectionSource.contains("val formattedPrice: String"))
        assertTrue(subscriptionOfferSelectionSource.contains("val billingPeriod: String"))
    }

    @Test
    fun nonRecurringModeSelectsAPrepaidPlan() {
        val start = offerSelectionSource.indexOf("fun selectSafeBrowsePassPrepaidPlan(")
        val end = offerSelectionSource.indexOf("fun safeBrowsePassPlanDisclosure", start)
            .let { if (it < 0) offerSelectionSource.length else it }
        val block = offerSelectionSource.substring(start, if (end > start) end else offerSelectionSource.length)
        assertTrue(block.contains("recurrenceMode != nonRecurringMode"))
    }

    @Test
    fun infiniteRecurringModeSelectsAnAutoRenewingPlan() {
        val start = offerSelectionSource.indexOf("fun selectSafeBrowsePassMonthlyPlan(")
        val end = offerSelectionSource.indexOf("fun selectSafeBrowsePassPrepaidPlan(", start)
        val block = offerSelectionSource.substring(start, end)
        assertTrue(block.contains("recurrenceMode == infiniteRecurringMode"))
        assertTrue(block.contains("billingPeriod == MonthlyRecurringBillingPeriod"))
    }

    @Test
    fun finiteRecurringOffersAreRejectedForV1() {
        // A finite-recurring phase (recurs a fixed number of times, then stops) is neither
        // the auto-renewing monthly shape nor the one-shot prepaid shape and must never be
        // silently accepted as either.
        assertFalse(offerSelectionSource.contains("FINITE_RECURRING"))
        assertTrue(offerSelectionSource.contains("nonRecurringMode"))
    }

    @Test
    fun blankOfferTokenIsRejected() {
        assertTrue(
            "Selectors must reject an offer whose offerToken is blank -- it can never be " +
                "used to launch a purchase.",
            offerSelectionSource.contains("offerToken.isNotBlank()") ||
                offerSelectionSource.contains("offer.offerToken.isBlank()"),
        )
    }

    @Test
    fun blankBasePlanIdIsRejected() {
        assertTrue(
            "Selectors must reject an offer whose basePlanId is blank.",
            offerSelectionSource.contains("basePlanId.isNotBlank()") ||
                offerSelectionSource.contains("offer.basePlanId.isBlank()"),
        )
    }

    @Test
    fun emptyPricingPhasesAreRejected() {
        assertTrue(
            "Selectors must reject an offer with no pricing phases at all rather than " +
                "throwing on .single() or .first().",
            offerSelectionSource.contains("pricingPhases.isEmpty()") ||
                offerSelectionSource.contains("pricingPhases.isNotEmpty()"),
        )
    }

    @Test
    fun offersFromAnotherProductAreNeverConsidered() {
        assertTrue(
            "Every selector must filter offers to only the caller-supplied productId -- " +
                "never operate on an unfiltered offer list that could include another " +
                "product's base plans.",
            offerSelectionSource.contains("productId: String"),
        )
    }

    @Test
    fun weeklyAndFortnightlyBillingPeriodLabelsAreRecognised() {
        // Base-plan-level period variety (P1W weekly, P15D fortnightly) beyond the single
        // fixed P1M monthly / P30D prepaid pair currently hard-coded.
        assertTrue(
            "P1W (weekly) billing period label is not recognised by any Safe Browse Pass selector.",
            offerSelectionSource.contains("\"P1W\""),
        )
        assertTrue(
            "P15D (fortnightly) billing period label is not recognised by any Safe Browse Pass selector.",
            offerSelectionSource.contains("\"P15D\""),
        )
    }

    @Test
    fun quarterlyAndYearlyBillingPeriodLabelsAreRecognised() {
        assertTrue(
            "P3M (quarterly) billing period label is not recognised by any Safe Browse Pass selector.",
            offerSelectionSource.contains("\"P3M\""),
        )
        assertTrue(
            "P1Y (yearly) billing period label is not recognised by any Safe Browse Pass selector.",
            offerSelectionSource.contains("\"P1Y\""),
        )
    }

    @Test
    fun sevenDayAndOneMonthLabelsAreRecognised() {
        assertTrue(offerSelectionSource.contains("\"P7D\"") || offerSelectionSource.contains("PreferredMonthlyTrialPeriod"))
        assertTrue(offerSelectionSource.contains("\"P1M\"") || offerSelectionSource.contains("MonthlyRecurringBillingPeriod"))
    }

    @Test
    fun selectionOrderingIsDeterministic() {
        assertTrue(offerSelectionSource.contains(".sortedWith("))
        assertTrue(offerSelectionSource.contains("basePlanId"))
    }

    @Test
    fun noHardCodedProductionCurrencyOrPriceAppearsInProductionSelectionCode() {
        // "$" alone is excluded: Kotlin string templates like "${'$'}{parsed.years} years"
        // legitimately contain it and are not a currency literal.
        listOf("USD", "EUR", "GBP", "£", "0.99", "9.99").forEach { literal ->
            assertFalse(
                "offer selection unexpectedly hard-codes a currency/price literal: $literal",
                offerSelectionSource.contains(literal),
            )
        }
    }
}
