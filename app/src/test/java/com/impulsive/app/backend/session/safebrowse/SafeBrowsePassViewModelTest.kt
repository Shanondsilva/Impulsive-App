package com.impulsive.app.backend.session.safebrowse

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassViewModelTest {
    private val viewModelSource = File(
        "src/main/java/com/impulsive/app/backend/session/safebrowse/SafeBrowsePassViewModel.kt",
    ).readText()
    private val routeSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassRoute.kt",
    ).readText()

    @Test
    fun dedicatedViewModelOwnsSafePassStateAndActions() {
        assertTrue(viewModelSource.contains("class SafeBrowsePassViewModel"))
        assertTrue(viewModelSource.contains(": ViewModel()"))
        assertTrue(viewModelSource.contains("billingManager: BillingManager"))
        assertTrue(viewModelSource.contains("safeBrowsePassRepositoryForViewModel()"))
        assertTrue(viewModelSource.contains("StateFlow<SafeBrowsePassUiState>"))
        assertTrue(viewModelSource.contains("SafeBrowsePassCatalogState"))
        assertTrue(viewModelSource.contains("SafeBrowsePassEntitlement"))
        assertTrue(viewModelSource.contains("SafeBrowsePassPurchaseState"))
        assertTrue(viewModelSource.contains("SafeBrowsePassRestoreState"))
        assertTrue(viewModelSource.contains("selectedPeriod"))
        assertTrue(viewModelSource.contains("fun selectPeriod("))
        assertTrue(viewModelSource.contains("fun launchPurchase("))
        assertTrue(viewModelSource.contains("fun restorePurchases("))
        assertTrue(viewModelSource.contains("manageSubscriptionUri"))
    }

    @Test
    fun viewModelDoesNotExposeRawBillingObjectsOrRetainActivity() {
        listOf(
            "import com.android.billingclient.api.ProductDetails",
            "import com.android.billingclient.api.Purchase",
            "import com.android.billingclient.api.BillingResult",
            "BillingClient",
            "private val activity",
            "private var activity",
            "grantSafeBrowsePassEntitlement",
        ).forEach { forbidden ->
            assertFalse(viewModelSource.contains(forbidden))
        }
        assertFalse(viewModelSource.contains("BillingManager("))
    }

    @Test
    fun routeConsumesViewModelInsteadOfBillingManagerDirectly() {
        assertTrue(routeSource.contains("passViewModel: SafeBrowsePassViewModel"))
        assertTrue(routeSource.contains("passViewModel.uiState"))
        assertFalse(routeSource.contains("billingManager.safeBrowsePassCatalogState"))
        assertFalse(routeSource.contains("billingManager.safeBrowsePassBillingUiState"))
    }

    // -------------------------------------------------------------------
    // Phase 4 correction: entitlement resolution must be explicit. These
    // are structural-only guards -- they prove the shape exists, not that
    // it behaves correctly. Behavioural coverage lives in
    // SafeBrowsePassViewModelBehaviorTest.
    // -------------------------------------------------------------------

    @Test
    fun entitlementStateFlowIsNullableWithANullInitialValue() {
        assertTrue(viewModelSource.contains("StateFlow<SafeBrowsePassEntitlement?>"))
        assertTrue(viewModelSource.contains("initialValue = null"))
    }

    @Test
    fun entitlementResolvedTracksWhetherTheFirstRealValueHasArrived() {
        assertTrue(viewModelSource.contains("entitlementResolved"))
        assertTrue(viewModelSource.contains("currentEntitlement != null"))
    }

    @Test
    fun uiStateIsLoadingBeforeTheFirstEntitlementResolves() {
        // NOTE (Phase 5A correction): the access-state when-branching that used to live
        // inline in toUiState() was extracted into the pure resolveSafeBrowsePassPresentation()
        // policy function (SafeBrowsePassPresentationPolicy.kt), which is the only place
        // "!entitlementResolved -> ... Loading" now appears -- toUiState() delegates to it
        // rather than duplicating the branch. The guarantee (unresolved entitlement means
        // Loading, never NotActive) still holds; it is proven behaviourally in
        // SafeBrowsePassViewModelBehaviorTest.catalogueReadyBeforeFirstEntitlementRemainsLoading
        // and structurally here by requiring the delegation itself.
        assertTrue(viewModelSource.contains("resolveSafeBrowsePassPresentation("))
        assertTrue(viewModelSource.contains("accessState = presentation.accessState"))
    }

    @Test
    fun submitPurchaseExistsAsTheTestableSynchronousSubmissionGuard() {
        assertTrue(viewModelSource.contains("internal fun submitPurchase("))
    }

    @Test
    fun launchPurchaseDelegatesThroughSubmitPurchaseRatherThanDuplicatingItsGuardLogic() {
        val launch = viewModelSource.substringAfter("fun launchPurchase(").substringBefore("fun refresh(")
        assertTrue(launch.contains("submitPurchase(durableAccountReady = durableAccountReady)"))
    }

    @Test
    fun viewModelNeverStoresAnActivityFieldAcrossCalls() {
        assertFalse(viewModelSource.contains("private val activity"))
        assertFalse(viewModelSource.contains("private var activity"))
        assertFalse(viewModelSource.contains("var storedActivity"))
        assertFalse(viewModelSource.contains("val storedActivity"))
    }

    // -------------------------------------------------------------------
    // Phase 5A: presentation policy delegation and prepaid top-up actions.
    // These are structural-only guards -- behavioural coverage lives in
    // SafeBrowsePassViewModelBehaviorTest.
    // -------------------------------------------------------------------

    @Test
    fun toUiStateDelegatesToTheSharedPresentationPolicy() {
        assertTrue(viewModelSource.contains("import com.impulsive.app.frontend.screens.safebrowse.resolveSafeBrowsePassPresentation"))
        assertTrue(viewModelSource.contains("resolveSafeBrowsePassPresentation("))
    }

    @Test
    fun submitPrepaidTopUpExistsAsTheTestableSynchronousSubmissionCore() {
        assertTrue(viewModelSource.contains("internal fun submitPrepaidTopUp("))
    }

    @Test
    fun launchPrepaidTopUpExistsAndDelegatesThroughSubmitPrepaidTopUp() {
        assertTrue(viewModelSource.contains("fun launchPrepaidTopUp("))
        val launch = viewModelSource.substringAfter("fun launchPrepaidTopUp(").substringBefore("fun refresh(")
        assertTrue(launch.contains("submitPrepaidTopUp("))
    }

    @Test
    fun exactlyOneAtomicBooleanLaunchGuardExistsAndItIsSharedByBothSubmissionCores() {
        assertEquals(1, Regex("AtomicBoolean\\(false\\)").findAll(viewModelSource).count())
        assertTrue(viewModelSource.contains("internal fun submitPurchase("))
        assertTrue(viewModelSource.contains("internal fun submitPrepaidTopUp("))

        val submitPurchase = viewModelSource
            .substringAfter("internal fun submitPurchase(")
            .substringBefore("fun launchPurchase(")
        val submitTopUp = viewModelSource
            .substringAfter("internal fun submitPrepaidTopUp(")
            .substringBefore("fun launchPrepaidTopUp(")

        assertTrue(submitPurchase.contains("launchInProgress"))
        assertTrue(submitTopUp.contains("launchInProgress"))
    }

    @Test
    fun viewModelNeverWritesAnEntitlementDirectly() {
        listOf(
            "setVerifiedEntitlement",
            "grantSafeBrowsePassEntitlement",
        ).forEach { forbidden ->
            assertFalse(
                "SafeBrowsePassViewModel unexpectedly writes entitlement state directly via: $forbidden",
                viewModelSource.contains(forbidden),
            )
        }
    }

    @Test
    fun manageSubscriptionUriRemainsDelegatedToOperations() {
        assertTrue(viewModelSource.contains("suspend fun manageSubscriptionUri(): Uri? = operations.manageSubscriptionUri()"))
    }

    @Test
    fun uiStateContainsNoOfferTokenProductIdOrBasePlanId() {
        val uiStateSource = File(
            "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassUiState.kt",
        ).readText()
        val presentationPolicySource = File(
            "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassPresentationPolicy.kt",
        ).readText()

        listOf("offerToken", "productId", "basePlanId").forEach { sensitive ->
            assertFalse(
                "SafeBrowsePassUiState.kt unexpectedly references: $sensitive",
                uiStateSource.contains(sensitive),
            )
            assertFalse(
                "SafeBrowsePassPresentationPolicy.kt unexpectedly references: $sensitive",
                presentationPolicySource.contains(sensitive),
            )
        }
    }
}
