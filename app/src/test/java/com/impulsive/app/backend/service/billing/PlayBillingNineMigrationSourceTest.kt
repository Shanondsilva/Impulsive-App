package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate C1's [BillingLibraryMigrationSourceTest] incorrectly treated every use of the
 * top-level BillingFlowParams.SubscriptionUpdateParams API as deprecated. In fact only
 * setSubscriptionReplacementMode was removed by Billing Library 9.1.0 --
 * SubscriptionUpdateParams itself, setOldPurchaseToken and setSubscriptionUpdateParams
 * remain the required path for Plus's own upgrade/downgrade replacement flow, alongside the
 * newer per-product ProductDetailsParams.SubscriptionProductReplacementParams API. This
 * class locks the corrected contract, plus Major Repair 1's fresh-ProductDetails,
 * unfetched-product handling, and prepaid top-up policy.
 */
class PlayBillingNineMigrationSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()

    private fun blockBetween(startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("marker not found: $startMarker", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("end marker not found after $startMarker: $endMarker", end > start)
        return source.substring(start, end)
    }

    @Test
    fun versionCatalogPinsBillingLibraryToNineOneZero() {
        val versionCatalog = File("../gradle/libs.versions.toml").readText()
        assertTrue(versionCatalog.contains("billing = \"9.1.0\""))
    }

    @Test
    fun exactlyOneBillingClientIsConstructedForBothProductFamilies() {
        val constructions = Regex("BillingClient\\.newBuilder\\(").findAll(source).count()
        assertEquals(1, constructions)
    }

    @Test
    fun pendingPurchasesParamsEnablesBothPrepaidPlansAndOneTimeProducts() {
        assertTrue(source.contains("PendingPurchasesParams"))
        assertTrue(source.contains(".enableOneTimeProducts()"))
        assertTrue(source.contains(".enablePrepaidPlans()"))
    }

    @Test
    fun perProductReplacementApiIsUsedForPlusUpgradeDowngrade() {
        val launchPurchaseBlock = blockBetween("fun launchPurchase(", "private fun handlePurchaseFlowFailure")
        assertTrue(launchPurchaseBlock.contains("SubscriptionProductReplacementParams.newBuilder()"))
        assertTrue(launchPurchaseBlock.contains(".setOldProductId("))
        assertTrue(launchPurchaseBlock.contains(".setReplacementMode("))
    }

    @Test
    fun theLegacySubscriptionUpdateParamsPathSuppliesTheOldPurchaseTokenForPlusReplacement() {
        // Only setSubscriptionReplacementMode was removed by the 9.1.0 migration --
        // BillingFlowParams.SubscriptionUpdateParams itself, setOldPurchaseToken and
        // setSubscriptionUpdateParams remain required alongside the newer per-product
        // replacement API for Plus's own upgrade/downgrade flow.
        val launchPurchaseBlock = blockBetween("fun launchPurchase(", "private fun handlePurchaseFlowFailure")
        assertTrue(
            "BillingFlowParams.SubscriptionUpdateParams is required but absent",
            launchPurchaseBlock.contains("BillingFlowParams.SubscriptionUpdateParams"),
        )
        assertTrue(
            "setOldPurchaseToken is required but absent",
            launchPurchaseBlock.contains(".setOldPurchaseToken("),
        )
        assertTrue(
            "BillingFlowParams.Builder.setSubscriptionUpdateParams is required but absent",
            launchPurchaseBlock.contains(".setSubscriptionUpdateParams("),
        )
    }

    @Test
    fun onlySetSubscriptionReplacementModeWasRemovedByTheMigration() {
        assertFalse(
            "the removed setSubscriptionReplacementMode API must remain absent",
            source.contains(".setSubscriptionReplacementMode("),
        )
    }

    @Test
    fun noLongLivedProductDetailsMapExists() {
        assertFalse(source.contains("ConcurrentHashMap<String, ProductDetails>"))
        assertFalse(source.contains("productDetailsById"))
        assertFalse(source.contains("SelectedPurchaseOffer"))
        assertFalse(source.contains("SelectedSafeBrowsePassOffer"))
    }

    @Test
    fun productDetailsListIsHandledFromTheQueryResponse() {
        val queryBlock = blockBetween("private fun queryProduct()", "private fun ProductDetails.offerSnapshots")
        assertTrue(queryBlock.contains("fetchedProductsById(queryResult)"))
    }

    @Test
    fun unfetchedProductListIsHandledFromTheQueryResponse() {
        assertTrue(source.contains("queryResult.unfetchedProductList"))
        val queryBlock = blockBetween("private fun queryProduct()", "private fun ProductDetails.offerSnapshots")
        assertTrue(
            "queryProduct() never logs unfetched products",
            queryBlock.contains("logUnfetchedProducts("),
        )
    }

    @Test
    fun unfetchedProductLoggingNeverIncludesSensitiveValues() {
        val logBlock = blockBetween(
            "private fun logUnfetchedProducts(",
            "private fun queryProduct()",
        )
        listOf("purchaseToken", "orderId", ".uid", "offerToken").forEach { sensitive ->
            assertFalse(
                "logUnfetchedProducts unexpectedly references: $sensitive",
                logBlock.contains(sensitive),
            )
        }
    }

    @Test
    fun oneProductQueryFailureNeverBlocksTheOtherProductsInTheSameResponse() {
        val queryBlock = blockBetween("private fun queryProduct()", "private fun ProductDetails.offerSnapshots")
        // Each product's plan is independently derived from the fetched map, so one
        // missing entry (null) never throws or blocks deriving the others.
        assertTrue(queryBlock.contains("fetched[PlusProductId]"))
        assertTrue(queryBlock.contains("monthlyDetails?.let"))
        assertTrue(queryBlock.contains("fetched[SafeBrowsePassProductId]"))
    }

    @Test
    fun aSharedQuerySubscriptionProductsHelperIsUsedByEveryQueryCallSite() {
        assertTrue(source.contains("private fun querySubscriptionProducts("))
        val catalogueBlock = blockBetween("private fun queryProduct()", "private fun ProductDetails.offerSnapshots")
        assertTrue(catalogueBlock.contains("querySubscriptionProducts("))
    }

    @Test
    fun freshProductQueryIsInvokedBeforeAPlusPurchaseIsLaunched() {
        // launchPurchase must never rely solely on a catalogue snapshot that could be
        // arbitrarily stale (app backgrounded for hours, a price change since queryProduct()
        // last ran) -- it must re-query product details immediately before launching billing
        // flow, and never launch from the cached selectedPurchasePlansByPeriod map.
        val launchPurchaseBlock = blockBetween("fun launchPurchase(", "private fun handlePurchaseFlowFailure")
        assertTrue(
            "launchPurchase never re-queries product details before launching billing flow",
            launchPurchaseBlock.contains("querySubscriptionProducts("),
        )
        assertFalse(
            "launchPurchase must never build ProductDetailsParams from the cached catalogue plan",
            launchPurchaseBlock.contains("setProductDetails(selectedPurchasePlansByPeriod"),
        )
    }

    @Test
    fun freshProductQueryIsInvokedBeforeASafeBrowsePassPurchaseIsLaunched() {
        val launchPassPurchaseBlock = blockBetween(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure",
        )
        assertTrue(
            "launchSafeBrowsePassPurchase never re-queries product details before launching billing flow",
            launchPassPurchaseBlock.contains("querySubscriptionProducts("),
        )
        assertFalse(
            "launchSafeBrowsePassPurchase must never build ProductDetailsParams from the cached catalogue plan",
            launchPassPurchaseBlock.contains("setProductDetails(selectedSafeBrowsePassPlansByPeriod"),
        )
    }

    @Test
    fun safeBrowsePassUsesOneProductWithMultipleBasePlansNotTwoTopLevelProducts() {
        assertTrue(source.contains("const val SafeBrowsePassProductId = \"safe_browse_pass\""))
        assertFalse(
            "BillingManager still wires two legacy Safe Browse Pass product id constants",
            source.contains("const val SafeBrowsePassMonthlyProductId") ||
                source.contains("const val SafeBrowsePassPrepaidProductId"),
        )
    }

    @Test
    fun aPendingPurchaseNeverGrantsEntitlementForEitherProductFamily() {
        val pendingOccurrences = Regex("Purchase\\.PurchaseState\\.PENDING").findAll(source).count()
        assertTrue("PENDING purchase state must be checked for both product families", pendingOccurrences >= 2)
    }

    @Test
    fun prepaidTopUpIsNotBlockedByAlreadyOwningTheProduct() {
        // A prepaid Safe Browse Pass top-up is a repeatable purchase (buying another block)
        // and must never be short-circuited by an "already own this exact product" guard --
        // that guard correctly applies only to the auto-renewing monthly plan. The purchase
        // intent is instead resolved by resolveSafeBrowsePassPurchaseIntent, which routes an
        // active prepaid Pass to PrepaidTopUp rather than AlreadyActive.
        val launchPassPurchaseBlock = blockBetween(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure",
        )
        assertFalse(
            "the unconditional already-owned guard must be gone",
            launchPassPurchaseBlock.contains(
                "ownedSafeBrowsePassProductId == productId && ownedSafeBrowsePassPurchaseToken != null",
            ),
        )
        assertTrue(launchPassPurchaseBlock.contains("resolveSafeBrowsePassPurchaseIntent("))
        assertTrue(launchPassPurchaseBlock.contains("SafeBrowsePassPurchaseIntent.PrepaidTopUp"))
    }

    @Test
    fun prepaidTopUpUsesNoReplacementParameters() {
        val launchPassPurchaseBlock = blockBetween(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure",
        )
        assertFalse(launchPassPurchaseBlock.contains("SubscriptionProductReplacementParams"))
        assertFalse(launchPassPurchaseBlock.contains("SubscriptionUpdateParams"))
    }

    @Test
    fun successfulPurchaseUpdatePerformsFreshOwnedPurchaseReconciliation() {
        val updateBlock = blockBetween("override fun onPurchasesUpdated(", "private fun handlePurchases(")
        val plusHandleIndex = updateBlock.indexOf("handlePurchases(purchases)")
        val passHandleIndex = updateBlock.indexOf("handleSafeBrowsePassPurchases(purchases)")
        val finalRefreshIndex = updateBlock.lastIndexOf("refreshPurchases()")
        assertTrue(plusHandleIndex >= 0)
        assertTrue(passHandleIndex > plusHandleIndex)
        assertTrue(finalRefreshIndex > passHandleIndex)
    }

    @Test
    fun successfulEmptyPurchaseQueryStillClearsBothFamiliesOwnershipState() {
        val refreshBlock = blockBetween("fun refreshPurchases()", "private fun clearPurchaseLaunchInFlight")
        assertTrue(refreshBlock.contains("handlePurchases(purchases)"))
        assertTrue(refreshBlock.contains("handleSafeBrowsePassPurchases(purchases)"))
        assertFalse(refreshBlock.contains("if (purchases.isNotEmpty())"))
    }

    @Test
    fun emptySuccessfulPurchaseCallbackFallsBackToOwnedPurchaseQuery() {
        val updateBlock = blockBetween("override fun onPurchasesUpdated(", "private fun handlePurchases(")
        val emptyResultIndex = updateBlock.indexOf("purchases.isNullOrEmpty()")
        val firstRefreshIndex = updateBlock.indexOf("refreshPurchases()", emptyResultIndex)
        assertTrue(emptyResultIndex >= 0)
        assertTrue(firstRefreshIndex > emptyResultIndex)
    }}
