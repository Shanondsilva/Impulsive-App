package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural guarantees for the Safe Browse Pass extension to BillingManager: one
 * BillingClient shared by both product families, one shared querySubscriptionProducts
 * helper used by the catalogue query and every purchase-launch path, completely separate
 * owned-purchase bookkeeping, completely separate UI-state flows, and a single Safe Browse
 * Pass product ID.
 */
class BillingManagerSafeBrowsePassSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()

    @Test
    fun exactlyOneBillingClientIsConstructed() {
        val occurrences = Regex("BillingClient\\.newBuilder\\(").findAll(source).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun exactlyOneSharedQuerySubscriptionProductsHelperExists() {
        assertTrue(source.contains("private fun querySubscriptionProducts("))
        // The real SDK call lives only inside the shared helper -- every other query call
        // site goes through it, never a second direct billingClient.queryProductDetailsAsync.
        val occurrences = Regex("billingClient\\.queryProductDetailsAsync\\(").findAll(source).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun catalogueQueryUsesAllThreeProductsThroughTheSharedHelper() {
        val queryProductIndex = source.indexOf("private fun queryProduct()")
        val queryProductEnd = source.indexOf("private fun ProductDetails.offerSnapshots", queryProductIndex)
        val block = source.substring(queryProductIndex, queryProductEnd)

        assertTrue(block.contains("querySubscriptionProducts("))
        assertTrue(block.contains("PlusProductId"))
        assertTrue(block.contains("PlusYearlyProductId"))
        assertTrue(block.contains("SafeBrowsePassProductId"))
    }

    @Test
    fun plusAndPassLaunchPathsBothUseFreshQueriesNotTheCachedCatalogue() {
        val launchPlusIndex = source.indexOf("fun launchPurchase(")
        val launchPlusEnd = source.indexOf("private fun handlePurchaseFlowFailure")
        val launchPlusBlock = source.substring(launchPlusIndex, launchPlusEnd)
        assertTrue(launchPlusBlock.contains("querySubscriptionProducts("))

        val launchPassIndex = source.indexOf("fun launchSafeBrowsePassPurchase(")
        val launchPassEnd = source.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure")
        val launchPassBlock = source.substring(launchPassIndex, launchPassEnd)
        assertTrue(launchPassBlock.contains("querySubscriptionProducts("))
    }

    @Test
    fun onlyOneSafeBrowsePassProductIdExists() {
        assertTrue(source.contains("const val SafeBrowsePassProductId = \"safe_browse_pass\""))
        assertFalse(source.contains("SafeBrowsePassMonthlyProductId"))
        assertFalse(source.contains("SafeBrowsePassPrepaidProductId"))
    }

    @Test
    fun safeBrowsePassHasItsOwnOwnedPurchaseBookkeeping() {
        assertTrue(source.contains("ownedSafeBrowsePassPurchaseToken"))
        assertTrue(source.contains("ownedSafeBrowsePassProductId"))
        // Never read Plus's owned-token fields when deciding a Pass purchase/replacement,
        // and never read Pass's when deciding a Plus one.
        val launchPassIndex = source.indexOf("fun launchSafeBrowsePassPurchase(")
        val launchPassEnd = source.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure")
        val launchPassBlock = source.substring(launchPassIndex, launchPassEnd)
        assertFalse(launchPassBlock.contains("ownedProductId"))
        assertFalse(launchPassBlock.contains("ownedPurchaseToken"))
    }

    @Test
    fun safeBrowsePassNeverReceivesReplacementParametersEvenForATopUp() {
        // No Safe Browse Pass purchase, including a prepaid top-up, is ever sent to Play as
        // a subscription-replacement -- each is its own independent transaction.
        val launchPassIndex = source.indexOf("fun launchSafeBrowsePassPurchase(")
        val launchPassEnd = source.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure")
        val launchPassBlock = source.substring(launchPassIndex, launchPassEnd)
        assertFalse(launchPassBlock.contains("SubscriptionProductReplacementParams"))
        assertFalse(launchPassBlock.contains("SubscriptionUpdateParams"))
    }

    @Test
    fun safeBrowsePassPurchaseLaunchUsesTheTopUpPolicy() {
        val launchPassIndex = source.indexOf("fun launchSafeBrowsePassPurchase(")
        val launchPassEnd = source.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure")
        val launchPassBlock = source.substring(launchPassIndex, launchPassEnd)
        assertTrue(launchPassBlock.contains("resolveSafeBrowsePassPurchaseIntent("))
        assertTrue(launchPassBlock.contains("SafeBrowsePassPurchaseIntent.PrepaidTopUp"))
        assertTrue(launchPassBlock.contains("SafeBrowsePassPurchaseIntent.AlreadyActive"))
        assertTrue(launchPassBlock.contains("SafeBrowsePassPurchaseIntent.RefreshRequired"))
    }

    @Test
    fun safeBrowsePassHasItsOwnUiStateFlowNeverThePlusOne() {
        assertTrue(source.contains("_safeBrowsePassBillingUiState"))
        assertTrue(source.contains("val safeBrowsePassBillingUiState: StateFlow<SafeBrowsePassBillingUiState>"))
    }

    @Test
    fun safeBrowsePassHasItsOwnCatalogStateFlow() {
        assertTrue(source.contains("_safeBrowsePassCatalogState"))
        assertTrue(source.contains("val safeBrowsePassCatalogState: StateFlow<SafeBrowsePassCatalogState>"))
    }

    @Test
    fun aFailedPurchaseFlowIsRoutedOnlyToTheFamilyThatLaunchedIt() {
        assertTrue(source.contains("pendingPurchaseFamily"))
        val updatedIndex = source.indexOf("override fun onPurchasesUpdated(")
        val updatedEnd = source.indexOf("private fun handleSafeBrowsePassPurchases")
        val updatedBlock = source.substring(updatedIndex, updatedEnd)
        assertTrue(updatedBlock.contains("PurchaseFamily.SafeBrowsePass ->"))
        assertTrue(updatedBlock.contains("PurchaseFamily.Plus, null ->"))
    }

    @Test
    fun safeBrowsePassVerificationUsesItsOwnCallableNames() {
        assertTrue(source.contains("\"verifySafeBrowsePassSubscription\""))
        assertTrue(source.contains("\"checkSafeBrowsePassEntitlement\""))
        // Never call the Plus callables from a Safe Browse Pass function.
        val verifyPassIndex = source.indexOf("private suspend fun verifySafeBrowsePassPurchaseWithBackend")
        val verifyPassEnd = source.indexOf("private suspend fun grantSafeBrowsePassEntitlement")
        val verifyPassBlock = source.substring(verifyPassIndex, verifyPassEnd)
        assertFalse(verifyPassBlock.contains("VerifyPlusSubscriptionFunction"))
    }

    @Test
    fun safeBrowsePassGrantsIntoItsOwnRepositoryNeverPremiumRepository() {
        val grantPassIndex =
            source.indexOf(
                "private suspend fun grantSafeBrowsePassEntitlement",
            )

        val grantPassEnd =
            source.indexOf(
                "private suspend fun refreshEntitlementFromServerOnce",
                grantPassIndex,
            )

        assertTrue(grantPassIndex >= 0)
        assertTrue(grantPassEnd > grantPassIndex)

        val grantPassBlock =
            source.substring(
                grantPassIndex,
                grantPassEnd,
            )

        assertTrue(
            grantPassBlock.contains(
                "safeBrowsePassRepository.setVerifiedEntitlement(",
            ),
        )

        assertTrue(
            grantPassBlock.contains(
                "expectedUid: String",
            ),
        )

        assertTrue(
            grantPassBlock.contains(
                "expectedUid = expectedUid",
            ),
        )

        assertTrue(
            grantPassBlock.contains(
                "): Boolean",
            ),
        )

        assertFalse(
            grantPassBlock.contains(
                "safeBrowsePassRepository.setEntitlement(",
            ),
        )

        assertFalse(
            grantPassBlock.contains(
                "repository.setEntitlement",
            ),
        )

        assertFalse(
            grantPassBlock.contains(
                "PremiumEntitlement(",
            ),
        )
    }

    @Test
    fun pendingPurchasesEnablesPrepaidPlansForTheSafeBrowsePassPrepaidBasePlan() {
        assertTrue(source.contains(".enablePrepaidPlans()"))
    }

    @Test
    fun restorePurchasesUsesSharedQueryButSeparatesPlusAndPassRestoration() {
        val queryCallCount = Regex("queryPurchasesAsync\\(").findAll(source).count()
        assertEquals(2, queryCallCount)
        assertTrue(source.contains("private suspend fun queryOwnedSubscriptions()"))
        assertTrue(source.contains("private suspend fun restorePurchasesInternal()"))
        assertTrue(source.contains("private suspend fun restoreSafeBrowsePassPurchasesInternal()"))

        val plusIndex = source.indexOf("private suspend fun restorePurchasesInternal()")
        val passIndex = source.indexOf("private suspend fun restoreSafeBrowsePassPurchasesInternal()")
        val plusBlock = source.substring(plusIndex, passIndex)
        val passEnd = source.indexOf("/**", passIndex)
        val passBlock = source.substring(passIndex, passEnd)

        assertTrue(plusBlock.contains("queryOwnedSubscriptions()"))
        assertTrue(passBlock.contains("queryOwnedSubscriptions()"))
        assertTrue(plusBlock.contains("PlusProductId"))
        assertFalse(plusBlock.contains("SafeBrowsePassProductId"))
        assertTrue(passBlock.contains("SafeBrowsePassProductId"))
        assertFalse(passBlock.contains("PlusProductId"))
    }

    @Test
    fun restoringSafeBrowsePassNeverAffectsThePlusRestoreOutcome() {
        val plusIndex = source.indexOf("private suspend fun restorePurchasesInternal()")
        val passIndex = source.indexOf("private suspend fun restoreSafeBrowsePassPurchasesInternal()")
        val plusBlock = source.substring(plusIndex, passIndex)
        val passEnd = source.indexOf("/**", passIndex)
        val passBlock = source.substring(passIndex, passEnd)

        assertFalse(passBlock.contains("_restoreState"))
        assertFalse(passBlock.contains("BillingRestoreState"))
        assertFalse(plusBlock.contains("_safeBrowsePassRestoreState"))
        assertFalse(plusBlock.contains("SafeBrowsePassRestoreState"))
    }
    @Test
    fun releaseClearsBothFamiliesStateAndBothPurchaseLaunchGuards() {
        val releaseIndex = source.indexOf("fun release() {")
        val releaseEnd = source.indexOf("private enum class PurchaseFamily")
        val releaseBlock = source.substring(releaseIndex, releaseEnd)
        assertTrue(releaseBlock.contains("selectedSafeBrowsePassPlansByPeriod.clear()"))
        assertTrue(releaseBlock.contains("verifyingSafeBrowsePassPurchaseTokens.clear()"))
        assertTrue(releaseBlock.contains("safeBrowsePassEntitlementRefreshInFlight.set(false)"))
        assertTrue(releaseBlock.contains("plusPurchaseLaunchInFlight.set(false)"))
        assertTrue(releaseBlock.contains("safeBrowsePassPurchaseLaunchInFlight.set(false)"))
    }

    @Test
    fun pendingSafeBrowsePassUpdateIsDetectedWithoutTrustingItsToken() {
        val helperStart = source.indexOf("private fun Purchase.hasPendingSafeBrowsePassUpdate()")
        val helperEnd = source.indexOf("private fun handleSafeBrowsePassPurchases(", helperStart)
        assertTrue(helperStart >= 0)
        assertTrue(helperEnd > helperStart)
        val helperBlock = source.substring(helperStart, helperEnd)
        assertTrue(helperBlock.contains("pendingPurchaseUpdate"))
        assertTrue(helperBlock.contains(".products"))
        assertTrue(helperBlock.contains("SafeBrowsePassProductId"))
        assertFalse(helperBlock.contains("purchaseToken"))
    }

    @Test
    fun pendingSafeBrowsePassTopUpKeepsPendingStateWhileVerifyingOnlyExistingPurchase() {
        // NOTE (Phase 4 correction): handleSafeBrowsePassPurchases() was rewritten to
        // route its pending/top-up/purchased classification through the shared pure
        // resolveSafeBrowsePassPlaySnapshotDecision() function (see
        // SafeBrowsePassRestorePolicy.kt and
        // BillingManagerSafeBrowsePassRestoreIsolationTest's decision-function guards).
        // The old local variable names (pendingUpdatePurchases, existingPurchasedPasses)
        // and the literal `keepPendingUiState = true` were implementation details of the
        // pre-correction inline branching, not the guaranteed behaviour, so they are
        // updated here to the current names rather than dropped. The guarantee itself --
        // a pending top-up keeps SafeBrowsePassBillingUiState.Pending while only the
        // existing top-up purchase is verified -- still holds and is asserted below.
        val handleStart = source.indexOf("private fun handleSafeBrowsePassPurchases(")
        val handleEnd = source.indexOf("private data class SafeBrowsePassVerificationSummary", handleStart)
        assertTrue(handleStart >= 0)
        assertTrue(handleEnd > handleStart)
        val handleBlock = source.substring(handleStart, handleEnd)
        assertTrue(handleBlock.contains("hasPendingSafeBrowsePassUpdate()"))
        assertTrue(handleBlock.contains("pendingTopUpPurchases"))
        assertTrue(handleBlock.contains("purchasedPassPurchases"))
        assertTrue(handleBlock.contains("decision.keepPendingUiState"))
        assertTrue(handleBlock.contains("SafeBrowsePassBillingUiState.Pending") || handleBlock.contains("decision.billingState"))
    }

    @Test
    fun pendingTopUpVerificationCannotReplacePendingStateWithReadyOrFailure() {
        // NOTE (identity-aware revision tracker correction): final-state publication is
        // now additionally guarded by the snapshot revision tracker so an obsolete
        // verification cannot publish over a newer snapshot's state. The original
        // pending-top-up/keepPendingUiState protection below is retained unchanged and
        // supplemented with the new revision-guard requirements -- no coverage removed.
        val verifyStart = source.indexOf("private fun verifyAndGrantSafeBrowsePassPurchases(")
        val verifyEnd = source.indexOf("private suspend fun verifySafeBrowsePassPurchaseWithBackend", verifyStart)
        assertTrue(verifyStart >= 0)
        assertTrue(verifyEnd > verifyStart)
        val verifyBlock = source.substring(verifyStart, verifyEnd)
        assertTrue(verifyBlock.contains("keepPendingUiState: Boolean = false"))
        assertTrue(verifyBlock.contains("if (keepPendingUiState)"))
        assertTrue(verifyBlock.contains("SafeBrowsePassBillingUiState.Pending"))
        assertTrue(verifyBlock.contains("if (!keepPendingUiState)"))
        assertTrue(verifyBlock.contains("expectedSnapshotRevision: Long"))
        assertTrue(verifyBlock.contains("summary.snapshotSuperseded"))
        assertTrue(verifyBlock.contains("runIfCurrent(\n                            expectedSnapshotRevision,\n                        )") || verifyBlock.contains("runIfCurrent("))
        assertTrue(Regex("runIfCurrent\\([\\s\\S]*?SafeBrowsePassBillingUiState\\.Pending").containsMatchIn(verifyBlock))
        assertTrue(Regex("runIfCurrent\\([\\s\\S]*?SafeBrowsePassBillingUiState\\.VerificationFailed").containsMatchIn(verifyBlock))
    }

    // -------------------------------------------------------------------
    // Phase 5A: renewal-state propagation through server refresh, verified
    // purchase, and entitlement grant.
    // -------------------------------------------------------------------

    @Test
    fun activeRefreshMapsRenewalStateFromTheVerifiedServerResponse() {
        val start = source.indexOf("private suspend fun refreshSafeBrowsePassEntitlementFromServerOnce(")
        val end = source.indexOf("private suspend fun enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(
            Regex("SafeBrowsePassEntitlementResolution\\.Active[\\s\\S]*?resolveSafeBrowsePassRenewalState\\(")
                .containsMatchIn(block),
        )
        assertTrue(block.contains("resolveSafeBrowsePassRenewalState("))
        assertTrue(
            Regex("isPrepaid\\s*=\\s*\\n?\\s*resolution\\.isPrepaid").containsMatchIn(block),
        )
        assertTrue(
            Regex("subscriptionState\\s*=\\s*\\n?\\s*resolution\\.subscriptionState")
                .containsMatchIn(block),
        )
    }

    @Test
    fun verifiedPurchaseCarriesRenewalState() {
        val start = source.indexOf("private suspend fun verifySafeBrowsePassPurchaseWithBackend(")
        val end = source.indexOf("private suspend fun grantSafeBrowsePassEntitlement(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(block.contains("renewalState ="))
        assertTrue(block.contains("resolveSafeBrowsePassRenewalState("))

        val purchaseStart = source.indexOf("private data class VerifiedSafeBrowsePassPurchase(")
        assertTrue(purchaseStart >= 0)
        val purchaseEnd = source.indexOf("\n    }\n}", purchaseStart)
        val purchaseBlock = if (purchaseEnd > purchaseStart) {
            source.substring(purchaseStart, purchaseEnd)
        } else {
            source.substring(purchaseStart)
        }
        assertTrue(purchaseBlock.contains("val renewalState:"))
    }

    @Test
    fun grantSafeBrowsePassEntitlementAcceptsRenewalState() {
        val start = source.indexOf("private suspend fun grantSafeBrowsePassEntitlement(")
        val end = source.indexOf("private suspend fun refreshEntitlementFromServerOnce(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(block.contains("renewalState:"))
        assertTrue(block.contains("SafeBrowsePassRenewalState"))
    }

    @Test
    fun grantPersistsRenewalState() {
        val start = source.indexOf("private suspend fun grantSafeBrowsePassEntitlement(")
        val end = source.indexOf("private suspend fun refreshEntitlementFromServerOnce(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(block.contains("renewalState = renewalState"))

        val callSiteStart = source.indexOf("val granted = grantSafeBrowsePassEntitlement(")
        assertTrue(callSiteStart >= 0)
        val callSiteEnd = source.indexOf(")", callSiteStart + "val granted = grantSafeBrowsePassEntitlement(".length)
        val callSiteBlock = source.substring(callSiteStart, callSiteEnd)
        assertTrue(callSiteBlock.contains("renewalState = verified.renewalState"))
    }

    @Test
    fun inactiveRefreshUsesCachedCopyActiveFalse() {
        val start = source.indexOf("private suspend fun refreshSafeBrowsePassEntitlementFromServerOnce(")
        val end = source.indexOf("private suspend fun enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(block.contains("cached.copy("))
        assertTrue(
            Regex("cached\\.copy\\(\\s*\\n?\\s*active = false").containsMatchIn(block),
        )
        assertFalse(
            "inactive refresh must never construct a blank SafeBrowsePassEntitlement, discarding cached metadata",
            Regex("SafeBrowsePassEntitlement\\(\\s*\\n?\\s*active = false,\\s*\\n?\\s*lastVerifiedMillis")
                .containsMatchIn(block),
        )
    }

    @Test
    fun failedRefreshExpiryUsesCachedCopyActiveFalse() {
        val start = source.indexOf("private suspend fun enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh(")
        val end = source.indexOf("fun restorePurchases()", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val block = source.substring(start, end)
        assertTrue(
            Regex("cached\\.copy\\(\\s*\\n?\\s*active = false").containsMatchIn(block),
        )
    }

    @Test
    fun noClientSelectedPlanDeterminesRenewalState() {
        // The ViewModel and repository must never pass their own renewal guess into
        // BillingManager -- resolveSafeBrowsePassRenewalState() is only ever called with
        // the server-verified isPrepaid/subscriptionState pair inside BillingManager itself.
        assertEquals(
            3,
            Regex("resolveSafeBrowsePassRenewalState\\(").findAll(source).count(),
        )
    }

    @Test
    fun pendingPurchaseUpdatePurchaseTokenRemainsAbsentUnderRenewalStatePropagation() {
        assertFalse(source.contains("pendingPurchaseUpdate.purchaseToken"))
        assertFalse(source.contains("pendingPurchaseUpdate?.purchaseToken"))
    }

    @Test
    fun exactlyOneBillingClientRemainsUnderRenewalStatePropagation() {
        assertEquals(1, Regex("BillingClient\\.newBuilder").findAll(source).count())
    }
}
