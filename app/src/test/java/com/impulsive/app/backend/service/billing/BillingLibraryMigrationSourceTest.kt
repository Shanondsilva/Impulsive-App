package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Gate C1 Billing Library 8.0.0 -> 9.1.0 migration in BillingManager.kt: the
 * removed setSubscriptionReplacementMode API is gone, the per-product
 * SubscriptionProductReplacementParams API is used, and PendingPurchasesParams enables
 * prepaid plans for the Safe Browse Pass product.
 */
class BillingLibraryMigrationSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()

    private val versionCatalog = File("../gradle/libs.versions.toml").readText()

    @Test
    fun versionCatalogPinsBillingLibraryToNineOneZero() {
        assertTrue(versionCatalog.contains("billing = \"9.1.0\""))
    }

    @Test
    fun subscriptionReplacementUsesTheCurrentPerProductApi() {
        assertTrue(source.contains("SubscriptionProductReplacementParams"))
        assertTrue(source.contains(".setOldProductId("))
        assertTrue(source.contains(".setReplacementMode("))
        assertTrue(source.contains("SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE"))
    }

    @Test
    fun onlySetSubscriptionReplacementModeWasRemovedByTheMigration() {
        // Only the top-level setSubscriptionReplacementMode API was removed by 9.1.0.
        // BillingFlowParams.SubscriptionUpdateParams itself, setOldPurchaseToken and
        // setSubscriptionUpdateParams remain required alongside the newer per-product
        // SubscriptionProductReplacementParams API for Plus's own upgrade/downgrade flow --
        // see PlayBillingNineMigrationSourceTest for that corrected, more detailed contract.
        assertFalse("still references removed API: setSubscriptionReplacementMode", source.contains(".setSubscriptionReplacementMode("))
    }

    @Test
    fun pendingPurchasesEnablesPrepaidPlansForSafeBrowsePass() {
        assertTrue(source.contains(".enablePrepaidPlans()"))
        assertTrue(source.contains(".enableOneTimeProducts()"))
    }
}
