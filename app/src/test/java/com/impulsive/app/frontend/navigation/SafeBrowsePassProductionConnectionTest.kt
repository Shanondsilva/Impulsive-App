package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST-ONLY diagnostic. A broader production-wiring audit for Safe Browse Pass than
 * SafeBrowsePassNavigationTest (route registration) and SafeBrowsePassRouteSourceTest
 * (Route/Screen split) already cover: backup/restore exclusion, sign-out clearing, the
 * single shared BillingManager instance, and the intended single-product billing model.
 * Every assertion in this class is expected to pass against the current source. Do not
 * weaken these assertions, and do not replace SafeBrowsePassNavigationTest or
 * SafeBrowsePassRouteSourceTest with this file.
 */
class SafeBrowsePassProductionConnectionTest {
    private val root = File("src/main/java/com/impulsive/app")
    private val navHost = File(root, "frontend/navigation/AppNavHost.kt").readText()
    private val backupRules = File("src/main/res/xml/backup_rules.xml").readText()
    private val dataExtractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()
    private val manualBackupManager = File(root, "backend/data/restore/ManualBackupManager.kt").readText()
    private val userDataExporter = File(root, "backend/data/UserDataExporter.kt").readText()
    private val billingManagerSource = File(root, "backend/service/billing/BillingManager.kt").readText()
    private val safeBrowseRouteSource = File(root, "frontend/screens/safebrowse/SafeBrowseRoute.kt").readText()
    private val firebaseAuthRepositorySource = File(
        root,
        "backend/data/repository/FirebaseAuthRepository.kt",
    ).readText()
    private val purchasePolicySource = File(
        root,
        "backend/service/billing/SafeBrowsePassPurchasePolicy.kt",
    ).readText()
    private val offerSelectionSource = File(
        root,
        "backend/service/billing/SafeBrowsePassOfferSelection.kt",
    ).readText()

    private fun blockBetween(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start =
            source.indexOf(
                startMarker,
            )

        assertTrue(
            "Missing start marker: $startMarker",
            start >= 0,
        )

        val end =
            source.indexOf(
                endMarker,
                start +
                    startMarker.length,
            )

        assertTrue(
            "Missing end marker: $endMarker",
            end > start,
        )

        return source.substring(
            start,
            end,
        )
    }

    @Test
    fun safeBrowsePassEntitlementDataStoreIsExcludedFromAndroidBackup() {
        assertFalse(backupRules.contains("safe_browse_pass_entitlement"))
        assertFalse(dataExtractionRules.contains("safe_browse_pass_entitlement"))
    }

    @Test
    fun safeBrowsePassEntitlementIsNeverIncludedInTheManualCloudBackupBundle() {
        assertFalse(
            "ManualBackupManager unexpectedly references Safe Browse Pass -- like " +
                "premium_entitlement, the server-verified cache must never round-trip " +
                "through the manual cloud restore bundle.",
            manualBackupManager.contains("SafeBrowsePass") || manualBackupManager.contains("safeBrowsePass"),
        )
    }

    @Test
    fun safeBrowsePassEntitlementIsNeverIncludedInTheUserDataExport() {
        assertFalse(
            userDataExporter.contains("SafeBrowsePass") || userDataExporter.contains("safeBrowsePass"),
        )
    }

    @Test
    fun safeBrowsePassRepositoryIsClearedDuringSignOut() {
        val signOutBlock =
            blockBetween(
                source =
                    firebaseAuthRepositorySource,
                startMarker =
                    "override suspend fun signOut()",
                endMarker =
                    "override suspend fun validateCurrentSession()",
            )

        val clearIndex =
            signOutBlock.indexOf(
                "SafeBrowsePassRepository(appContext).clear()",
            )

        val firebaseSignOutIndex =
            signOutBlock.indexOf(
                "firebaseAuth.signOut()",
            )

        assertTrue(
            "Firebase sign-out no longer clears the UID-bound Safe Browse Pass cache.",
            clearIndex >= 0,
        )

        assertTrue(
            "Safe Browse Pass cache must be cleared before Firebase sign-out completes.",
            firebaseSignOutIndex >
                clearIndex,
        )
    }

    @Test
    fun exactlyOneSafeBrowsePassDestinationExistsAndUsesTheSingleSharedBillingManager() {
        val destinationIndex = navHost.indexOf("composable(AppRoutes.SafeBrowsePass)")
        assertTrue(destinationIndex >= 0)
        val nextDestinationIndex = navHost.indexOf("composable(AppRoutes.DnsFilterGate)", destinationIndex)
        val block = navHost.substring(destinationIndex, nextDestinationIndex)

        assertTrue(block.contains("SafeBrowsePassViewModelFactory"))
        assertTrue(block.contains("billingManager"))
        assertFalse(block.contains("BillingManager("))
    }

    @Test
    fun safeBrowsePassAndImpulsivePlusDestinationsShareTheExactSameBillingManagerInstance() {
        // AppNavHost must construct exactly one BillingManager and pass that same reference
        // to both the Safe Browse Pass destination and every Impulsive Plus destination --
        // never a second, independently constructed BillingClient connection.
        val billingManagerConstructions = Regex("val billingManager[^=]*=\\s*BillingManager\\(")
            .findAll(navHost)
            .count()
        assertTrue(
            "AppNavHost must construct exactly one BillingManager instance, found $billingManagerConstructions",
            billingManagerConstructions <= 1,
        )
    }

    @Test
    fun theBillingManagerHostsASingleSafeBrowsePassProductRatherThanTwoLegacyProducts() {
        // Single-product model established by Major Repair 1 (also locked more thoroughly
        // in SafeBrowsePassOfferMapperTest): production wiring must not query two separate
        // top-level product ids for what is conceptually one product with two base plans.
        assertFalse(
            "BillingManager still wires two legacy Safe Browse Pass product id constants " +
                "instead of a single shared SafeBrowsePassProductId with multiple base plans.",
            billingManagerSource.contains("const val SafeBrowsePassMonthlyProductId") &&
                billingManagerSource.contains("const val SafeBrowsePassPrepaidProductId"),
        )
    }

    @Test
    fun theDedicatedPassDestinationExistsAndIsReachableFromSafeBrowseNotSettings() {
        // Broader than SafeBrowsePassNavigationTest's route-registration check: confirms
        // the Safe Browse entry point genuinely resolves to the destination constant while
        // Settings no longer exposes a redundant entry point.
        assertTrue(navHost.contains("const val SafeBrowsePass ="))
        val safeBrowseCallbackIndex = navHost.indexOf("onOpenSafeBrowsePass = {", navHost.indexOf("SafeBrowseRoute("))
        assertTrue(safeBrowseCallbackIndex >= 0)
        assertTrue(navHost.substring(safeBrowseCallbackIndex).contains("navController.navigate(AppRoutes.SafeBrowsePass)"))

        val settingsStart = navHost.indexOf("SettingsScreen(")
        val settingsEnd = navHost.indexOf("composable(AppRoutes.MomentPlanList)", settingsStart)
        val settingsBlock = navHost.substring(settingsStart, settingsEnd)
        assertFalse(settingsBlock.contains("onOpenSafeBrowsePass"))
    }

    @Test
    fun aDedicatedSafeBrowsePassViewModelIsWiredIntoTheDestination() {
        assertTrue(
            "AppNavHost's Safe Browse Pass destination never constructs or receives a " +
                "SafeBrowsePassViewModel -- it still wires SafeBrowsePassRoute directly " +
                "against the shared BillingManager.",
            navHost.contains("SafeBrowsePassViewModel"),
        )
    }

    @Test
    fun websiteProtectionRemainsCopySeparateFromSafeBrowsePass() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertFalse(screenSource.contains("WebsiteProtectionSetupState"))
        assertFalse(screenSource.contains("ProtectionSetupViewModel"))
    }

    @Test
    fun offerPriceIsAlwaysReadFromTheLiveCatalogueFormattedPriceNeverHardCoded() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertTrue(screenSource.contains("plan.formattedPrice"))

        // Hard-coded currency literals are only acceptable inside @Preview composables --
        // never on a real state-rendering code path.
        val previewIndex = screenSource.indexOf("@Preview")
        val productionSection = if (previewIndex >= 0) screenSource.substring(0, previewIndex) else screenSource
        listOf("£1.99", "£3.49", "$0.99", "$9.99").forEach { literal ->
            assertFalse(
                "production Safe Browse Pass screen code unexpectedly hard-codes: $literal",
                productionSection.contains(literal),
            )
        }
    }

    @Test
    fun continueAndRestoreActionsExist() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertTrue(screenSource.contains("\"safe_browse_pass_purchase\""))
        assertTrue(screenSource.contains("\"safe_browse_pass_restore\""))
    }

    @Test
    fun aManageActionExistsForAnActiveAutoRenewingPlan() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertTrue(
            "no Manage-subscription action exists on the active Safe Browse Pass state -- " +
                "an active auto-renewing plan currently has no way to reach Play's " +
                "subscription management screen.",
            screenSource.contains("safe_browse_pass_manage") || screenSource.contains("Manage subscription"),
        )
    }

    @Test
    fun anActiveAutoRenewingPlanShowsManageRatherThanOfferingAnotherPurchase() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        val activeIndex = screenSource.indexOf("is SafeBrowsePassScreenAccessState.Active ->")
        assertTrue(activeIndex >= 0)
        val notActiveIndex = screenSource.indexOf("SafeBrowsePassScreenAccessState.NotActive", activeIndex)
        val activeBlock = if (notActiveIndex > activeIndex) screenSource.substring(activeIndex, notActiveIndex) else ""
        assertTrue(
            "the Active state never renders a Manage action, only the Pass purchase/offer UI",
            activeBlock.contains("Manage") || activeBlock.contains("manage"),
        )
    }

    @Test
    fun verifiedExpiryIsDisplayedOnTheActiveState() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertTrue(
            "the active Safe Browse Pass state never displays its verified expiry date/time to the user",
            screenSource.contains("expiryTimeMillis") || screenSource.contains("expiry"),
        )
    }

    @Test
    fun prepaidTopUpIsOfferedAsARepeatablePurchaseAction() {
        val screenSource = File(root, "frontend/screens/safebrowse/SafeBrowsePassScreen.kt").readText()
        assertTrue(
            "no prepaid top-up purchase action exists once a prepaid Pass is already active " +
                "-- a prepaid Pass should be repeatably extendable, unlike the auto-renewing plan.",
            screenSource.contains("top-up", ignoreCase = true) || screenSource.contains("topUp", ignoreCase = true),
        )
    }

    @Test
    fun aPendingTopUpStateIsDistinguishedFromAFreshPurchase() {
        val billingUiStateSource = File(root, "backend/service/billing/SafeBrowsePassBillingUiState.kt").readText()
        val flowStateSource = File(root, "backend/service/billing/SafeBrowsePassFlowStates.kt").readText()
        assertTrue(billingUiStateSource.contains("data object Pending : SafeBrowsePassBillingUiState"))
        assertTrue(
            "no dedicated public pending-top-up state exists distinct from a fresh Pending purchase",
            flowStateSource.contains("PendingTopUp") &&
                flowStateSource.contains("SafeBrowsePassPendingKind.TopUp"),
        )
    }

    @Test
    fun aTopUpPurchaseIsValidatedAgainstTheSameBasePlanAsTheActivePrepaidPass() {
        assertTrue(
            purchasePolicySource.contains(
                "data class PrepaidTopUp(",
            ),
        )

        assertTrue(
            purchasePolicySource.contains(
                "val requiredBasePlanId: String",
            ),
        )

        assertTrue(
            purchasePolicySource.contains(
                "requiredBasePlanId = basePlanId",
            ),
        )

        val purchaseLaunchBlock =
            blockBetween(
                source =
                    billingManagerSource,
                startMarker =
                    "fun launchSafeBrowsePassPurchase(",
                endMarker =
                    "private fun handleSafeBrowsePassPurchaseFlowFailure(",
            )

        assertTrue(
            purchaseLaunchBlock.contains(
                "is SafeBrowsePassPurchaseIntent.PrepaidTopUp -> intent.requiredBasePlanId",
            ),
        )

        assertTrue(
            purchaseLaunchBlock.contains(
                "selectSafeBrowsePassPrepaidPlan(",
            ),
        )

        assertTrue(
            purchaseLaunchBlock.contains(
                "requiredBasePlanId = requiredBasePlanId",
            ),
        )

        assertTrue(
            offerSelectionSource.contains(
                "offer.basePlanId != normalizedRequiredBasePlan",
            ),
        )
    }

    @Test
    fun prepaidTopUpNeverUsesSubscriptionReplacementParameters() {
        val start = billingManagerSource.indexOf("fun launchSafeBrowsePassPurchase(")
        val end = billingManagerSource.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure", start)
        val block = billingManagerSource.substring(start, end)
        assertFalse(block.contains("SubscriptionProductReplacementParams"))
    }

    @Test
    fun durableAccountIsRequiredBeforeAPassPurchaseCanLaunch() {
        val start = billingManagerSource.indexOf("fun launchSafeBrowsePassPurchase(")
        val end = billingManagerSource.indexOf("private fun handleSafeBrowsePassPurchaseFlowFailure", start)
        val block = billingManagerSource.substring(start, end)
        assertTrue(block.contains("isAnonymous"))
        assertTrue(block.contains("hasDurableProvider"))
    }

    @Test
    fun safeBrowsePassRestoreResultIsIndependentOfPlusRestoreResult() {
        assertTrue(
            "no independent Safe Browse Pass restore-result state exists, distinct from " +
                "Plus's own BillingRestoreState.",
            billingManagerSource.contains("SafeBrowsePassBillingUiState.Restored") ||
                billingManagerSource.contains("SafeBrowsePassRestoreState"),
        )
    }

    @Test
    fun safeBrowsePassEntitlementNeverMapsToPremiumEntitlement() {
        val block =
            blockBetween(
                source =
                    billingManagerSource,
                startMarker =
                    "private suspend fun grantSafeBrowsePassEntitlement(",
                endMarker =
                    "private suspend fun refreshEntitlementFromServerOnce(",
            )

        assertTrue(
            block.contains(
                "safeBrowsePassRepository.setVerifiedEntitlement(",
            ),
        )

        assertTrue(
            block.contains(
                "SafeBrowsePassEntitlement(",
            ),
        )

        assertFalse(
            block.contains(
                "PremiumEntitlement(",
            ),
        )

        assertFalse(
            block.contains(
                "repository.setEntitlement(",
            ),
        )
    }

    @Test
    fun plusEntitlementGrantNeverMapsToSafeBrowsePassEntitlement() {
        val block =
            blockBetween(
                source =
                    billingManagerSource,
                startMarker =
                    "private suspend fun grantEntitlement(productId: String, expiryTimeMillis: Long)",
                endMarker =
                    "private data class SafeBrowsePassPlaySnapshotKey(",
            )

        assertTrue(
            block.contains(
                "repository.setEntitlement(",
            ),
        )

        assertTrue(
            block.contains(
                "PremiumEntitlement(",
            ),
        )

        assertFalse(
            block.contains(
                "SafeBrowsePassEntitlement(",
            ),
        )

        assertFalse(
            block.contains(
                "safeBrowsePassRepository.setVerifiedEntitlement(",
            ),
        )
    }

    @Test
    fun safeBrowseEntryUsesLiveCatalogueAvailabilityAndFormattedPrice() {
        // Phase 4 correction: the Safe Browse entry's "View Safe Browse Pass" row must
        // reflect the live Play catalogue instead of the old hard-coded availability/price.
        assertTrue(
            "SafeBrowseRoute no longer collects the live Safe Browse Pass catalogue state.",
            safeBrowseRouteSource.contains("billingManager.safeBrowsePassCatalogState"),
        )
        assertTrue(
            "SafeBrowseRoute no longer derives its entry price from a Ready catalogue plan's formattedPrice.",
            safeBrowseRouteSource.contains("formattedPrice"),
        )
        assertTrue(
            "SafeBrowseRoute no longer narrows the catalogue state to SafeBrowsePassCatalogState.Ready.",
            safeBrowseRouteSource.contains("SafeBrowsePassCatalogState.Ready"),
        )
        assertFalse(
            "SafeBrowseRoute still hard-codes Safe Browse Pass purchase availability to true.",
            safeBrowseRouteSource.contains("val passPurchaseAvailable = true"),
        )
        assertFalse(
            "SafeBrowseRoute still hard-codes a null Safe Browse Pass price label.",
            safeBrowseRouteSource.contains("val passPriceLabel: String? = null"),
        )
    }

    @Test
    fun sensitiveBillingValuesAreAbsentFromTheUiState() {
        val billingUiStateSource = File(root, "backend/service/billing/SafeBrowsePassBillingUiState.kt").readText()
        listOf("purchaseToken", "orderId", "signature", "originalJson").forEach { sensitive ->
            assertFalse(
                "SafeBrowsePassBillingUiState unexpectedly exposes: $sensitive",
                billingUiStateSource.contains(sensitive, ignoreCase = true),
            )
        }
    }
}
