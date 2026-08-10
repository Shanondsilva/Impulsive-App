package com.impulsive.app.backend.session.safebrowse

import android.app.Activity
import android.net.Uri
import com.impulsive.app.backend.data.repository.SafeBrowsePassOperations
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import com.impulsive.app.backend.service.billing.SafeBrowsePassCatalogState
import com.impulsive.app.backend.service.billing.SafeBrowsePassPeriod
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState
import com.impulsive.app.backend.service.billing.SelectedSafeBrowsePassPlan
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassActivePlanStatus
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassScreenAccessState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeBrowsePassViewModelBehaviorTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun monthlyPlan(offerToken: String = "monthly-token") = SelectedSafeBrowsePassPlan(
        period = SafeBrowsePassPeriod.Monthly,
        productId = "safe_browse_pass",
        basePlanId = "monthly",
        offerId = null,
        offerToken = offerToken,
        formattedPrice = "£1.99",
        billingPeriod = "P1M",
    )

    private fun prepaidPlan(offerToken: String = "prepaid-token") = SelectedSafeBrowsePassPlan(
        period = SafeBrowsePassPeriod.Prepaid,
        productId = "safe_browse_pass",
        basePlanId = "prepaid-30",
        offerId = null,
        offerToken = offerToken,
        formattedPrice = "£3.49",
        billingPeriod = "P30D",
    )

    /**
     * Constructs the ViewModel and immediately starts a background collector on [uiState] --
     * the underlying flows are all `stateIn(WhileSubscribed(...))`, so without an active
     * subscriber the fakes' emissions would never actually propagate.
     */
    private fun kotlinx.coroutines.test.TestScope.newViewModel(
        operations: FakeSafeBrowsePassOperations,
    ): SafeBrowsePassViewModel {
        val viewModel = SafeBrowsePassViewModel(
            operations = operations,
            nowMillis = { testDispatcher.scheduler.currentTime },
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun catalogueReadyBeforeFirstEntitlementRemainsLoading() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        runCurrent()

        assertEquals(
            SafeBrowsePassScreenAccessState.Loading,
            viewModel.uiState.value.accessState,
        )
        assertTrue(viewModel.uiState.value.catalogLoading)
    }

    @Test
    fun purchaseDisabledWhileEntitlementUnresolved() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        runCurrent()

        assertFalse(viewModel.uiState.value.purchaseEnabled)
    }

    @Test
    fun restoreDisabledWhileEntitlementUnresolved() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        runCurrent()

        assertFalse(viewModel.uiState.value.restoreEnabled)
    }

    @Test
    fun firstInactiveEntitlementRevealsNotActive() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        assertEquals(SafeBrowsePassScreenAccessState.NotActive, viewModel.uiState.value.accessState)
        assertFalse(viewModel.uiState.value.catalogLoading)
    }

    @Test
    fun firstActiveEntitlementRevealsActiveWithoutShowingOffers() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(
            SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "monthly",
                expiryTimeMillis = Long.MAX_VALUE / 2,
            ),
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.accessState is SafeBrowsePassScreenAccessState.Active)
        assertFalse(state.purchaseEnabled)
    }

    @Test
    fun monthlyIsSelectedByDefaultWhenBothExist() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        assertEquals(SafeBrowsePassPeriod.Monthly, viewModel.selectedOffer.value?.period)
    }

    @Test
    fun prepaidIsSelectedWhenMonthlyIsAbsent() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(null, prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        assertEquals(SafeBrowsePassPeriod.Prepaid, viewModel.selectedOffer.value?.period)
    }

    @Test
    fun changingPeriodChangesTheRealSelectedOffer() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()
        assertEquals(SafeBrowsePassPeriod.Monthly, viewModel.selectedOffer.value?.period)

        viewModel.selectPeriod(SafeBrowsePassPeriod.Prepaid)
        runCurrent()

        assertEquals(SafeBrowsePassPeriod.Prepaid, viewModel.selectedOffer.value?.period)
    }

    @Test
    fun removedSelectedOfferIsClearedOrReplacedDeterministically() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()
        assertEquals(SafeBrowsePassPeriod.Monthly, viewModel.selectedOffer.value?.period)

        // The monthly offer the user had selected disappears from a fresh catalogue read --
        // reconciliation must deterministically fall back to the remaining prepaid offer.
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(null, prepaidPlan())
        runCurrent()

        assertEquals(SafeBrowsePassPeriod.Prepaid, viewModel.selectedOffer.value?.period)
        assertTrue(operations.clearSelectionCalls > 0)
    }

    @Test
    fun staleForcedSelectionPreventsSubmission() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        // Force a selection whose offerToken no longer belongs to the current catalogue,
        // then submit immediately without an intervening runCurrent() -- this exercises
        // submitPurchase()'s own defensive re-validation rather than the background
        // combine()-driven reconciliation, which would otherwise have already healed it.
        operations.forceSelection(monthlyPlan(offerToken = "stale-token"))

        val result = viewModel.submitPurchase(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertEquals(false, result)
        assertEquals(0, operations.launchCalls)
    }

    @Test
    fun durableAccountReadyFalsePreventsSubmission() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        val result = viewModel.submitPurchase(durableAccountReady = false) {
            operations.recordSubmission()
        }

        assertEquals(false, result)
        assertEquals(0, operations.launchCalls)
    }

    @Test
    fun duplicateConcurrentSubmitPurchaseCallsPermitOnlyOneSubmission() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        val submissionCount = AtomicInteger(0)
        var secondResult: Boolean? = null

        // submitPurchase() is synchronous and sets its in-flight guard before invoking the
        // submit callback -- a second (reentrant) call issued from inside the first
        // submission's own callback deterministically observes that guard still held,
        // exercising the exact same guard a real concurrent second tap would hit, without
        // any real threading or blocking.
        val firstResult = viewModel.submitPurchase(durableAccountReady = true) {
            submissionCount.incrementAndGet()

            secondResult = viewModel.submitPurchase(durableAccountReady = true) {
                submissionCount.incrementAndGet()
                true
            }

            true
        }

        assertEquals(false, secondResult)
        assertEquals(true, firstResult)
        assertEquals(1, submissionCount.get())
    }

    @Test
    fun pendingInitialPurchaseDisablesAnotherPurchase() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        operations.purchaseStateFlow.value = SafeBrowsePassPurchaseState.Pending
        runCurrent()

        assertFalse(viewModel.uiState.value.purchaseEnabled)
    }

    @Test
    fun pendingTopUpDisablesAnotherPurchase() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        operations.purchaseStateFlow.value = SafeBrowsePassPurchaseState.PendingTopUp
        runCurrent()

        assertFalse(viewModel.uiState.value.purchaseEnabled)
    }

    @Test
    fun purchaseSubmissionDoesNotAlterEntitlement() =
        runTest(testDispatcher) {
            val operations =
                FakeSafeBrowsePassOperations()

            val viewModel =
                newViewModel(operations)

            operations.catalogState.value =
                SafeBrowsePassCatalogState
                    .Ready(
                        monthlyPlan(),
                        prepaidPlan(),
                    )

            operations.entitlementEvents.emit(
                SafeBrowsePassEntitlement(
                    active = false,
                ),
            )

            runCurrent()

            val submitted =
                viewModel.submitPurchase(
                    durableAccountReady =
                        true,
                ) {
                    operations
                        .recordSubmission()
                }

            assertTrue(submitted)
            assertEquals(
                1,
                operations.launchCalls,
            )
            assertEquals(
                false,
                viewModel.entitlement
                    .value
                    ?.active,
            )
        }

    @Test
    fun restoreRestoringBlocksDuplicateRestoreCalls() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        operations.restoreStateFlow.value = SafeBrowsePassRestoreState.Restoring
        runCurrent()

        viewModel.restorePurchases()
        runCurrent()

        assertEquals(0, operations.restoreCalls)
    }

    @Test
    fun restoreChangesDoNotMutatePurchaseState() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        operations.restoreStateFlow.value = SafeBrowsePassRestoreState.Restored
        runCurrent()

        assertEquals(SafeBrowsePassPurchaseState.Idle, viewModel.purchaseState.value)
    }

    @Test
    fun purchaseChangesDoNotMutateRestoreState() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        operations.purchaseStateFlow.value = SafeBrowsePassPurchaseState.Launching
        runCurrent()

        assertEquals(SafeBrowsePassRestoreState.Idle, viewModel.restoreState.value)
    }

    @Test
    fun refreshDelegatesOnce() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)

        viewModel.refresh()

        assertEquals(1, operations.refreshCalls)
    }

    @Test
    fun uiStateContainsNoOfferToken() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.monthlyPlan?.let { "offerToken" }?.takeIf { false })
        // The public UI-state model's plan cards must never carry the raw offerToken --
        // only period, formattedPrice, periodLabel and disclosure.
        assertFalse(state.toString().contains("offerToken"))
    }

    // -------------------------------------------------------------------
    // Phase 5A: renewal-aware presentation state and prepaid top-up.
    // -------------------------------------------------------------------

    private fun activePrepaidEntitlement(nowMillis: Long) = SafeBrowsePassEntitlement(
        active = true,
        productId = "safe_browse_pass",
        basePlanId = "prepaid-30",
        expiryTimeMillis = nowMillis + 60_000L,
        isPrepaid = true,
        renewalState = SafeBrowsePassRenewalState.NotApplicable,
        lastVerifiedMillis = nowMillis,
    )

    private fun activeAutoRenewingEntitlement(
        nowMillis: Long,
        renewalState: SafeBrowsePassRenewalState = SafeBrowsePassRenewalState.Renewing,
    ) = SafeBrowsePassEntitlement(
        active = true,
        productId = "safe_browse_pass",
        basePlanId = "monthly",
        expiryTimeMillis = nowMillis + 60_000L,
        isPrepaid = false,
        renewalState = renewalState,
        lastVerifiedMillis = nowMillis,
    )

    private fun inactiveReachedExpiryEntitlement(nowMillis: Long) = SafeBrowsePassEntitlement(
        active = false,
        productId = "safe_browse_pass",
        basePlanId = "monthly",
        expiryTimeMillis = nowMillis - 1L,
        isPrepaid = false,
        renewalState = SafeBrowsePassRenewalState.Unknown,
        lastVerifiedMillis = nowMillis,
    )

    @Test
    fun activePrepaidEntitlementMapsToActivePrepaid() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        val access = viewModel.uiState.value.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.Prepaid, access.planStatus)
    }

    @Test
    fun activeAutoRenewingMapsToActiveAutoRenewing() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activeAutoRenewingEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        val access = viewModel.uiState.value.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.AutoRenewing, access.planStatus)
    }

    @Test
    fun cancelledAutoRenewingMapsToActiveCancelledUntilExpiry() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(
            activeAutoRenewingEntitlement(
                testDispatcher.scheduler.currentTime,
                renewalState = SafeBrowsePassRenewalState.CancelledUntilExpiry,
            ),
        )
        runCurrent()

        val access = viewModel.uiState.value.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.CancelledUntilExpiry, access.planStatus)
    }

    @Test
    fun inactiveReachedExpiryEntitlementMapsToExpired() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())

        // uiState's combine() only recomputes when the ViewModel's entitlement StateFlow
        // actually changes value (StateFlow conflates equal emissions), so advance the
        // scheduler past the recorded expiry first, then emit a structurally-different
        // entitlement (a bumped lastVerifiedMillis) to force recombination against the now
        // later nowMillis().
        val expiryMillis = testDispatcher.scheduler.currentTime + 1L
        operations.entitlementEvents.emit(
            inactiveReachedExpiryEntitlement(expiryMillis).copy(expiryTimeMillis = expiryMillis),
        )
        runCurrent()

        testDispatcher.scheduler.advanceTimeBy(2L)
        operations.entitlementEvents.emit(
            inactiveReachedExpiryEntitlement(expiryMillis)
                .copy(expiryTimeMillis = expiryMillis, lastVerifiedMillis = expiryMillis + 1L),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.accessState is SafeBrowsePassScreenAccessState.Expired)
    }

    @Test
    fun activeAutoRenewingExposesManagementAvailability() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activeAutoRenewingEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        assertTrue(viewModel.uiState.value.manageSubscriptionAvailable)
    }

    @Test
    fun activePrepaidHidesManagement() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        assertFalse(viewModel.uiState.value.manageSubscriptionAvailable)
    }

    @Test
    fun activePrepaidWithCurrentPrepaidOfferExposesTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        assertTrue(viewModel.uiState.value.prepaidTopUpAvailable)
    }

    @Test
    fun activePrepaidWithNoPrepaidOfferHidesTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), null)
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        assertFalse(viewModel.uiState.value.prepaidTopUpAvailable)
    }

    @Test
    fun pendingTopUpRemainsActiveAndDisablesAnotherTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        operations.purchaseStateFlow.value = SafeBrowsePassPurchaseState.PendingTopUp
        runCurrent()

        val access = viewModel.uiState.value.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.Prepaid, access.planStatus)
        assertTrue(access.topUpPending)
        assertFalse(viewModel.uiState.value.prepaidTopUpAvailable)
    }

    @Test
    fun submitPrepaidTopUpSelectsTheCurrentPrepaidOffer() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertEquals(SafeBrowsePassPeriod.Prepaid, operations.selectedOffer.value?.period)
        assertEquals(prepaidPlan().offerToken, operations.selectedOffer.value?.offerToken)
    }

    @Test
    fun submitPrepaidTopUpSubmitsOnce() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        val submitted = viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertTrue(submitted)
        assertEquals(1, operations.launchCalls)
    }

    @Test
    fun duplicateConcurrentPrepaidTopUpSubmissionsAllowOne() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        var secondResult: Boolean? = null

        val firstResult = viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            secondResult = viewModel.submitPrepaidTopUp(durableAccountReady = true) {
                operations.recordSubmission()
            }
            operations.recordSubmission()
        }

        assertEquals(false, secondResult)
        assertEquals(true, firstResult)
        assertEquals(1, operations.launchCalls)
    }

    @Test
    fun durableAccountReadyFalsePreventsTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        val result = viewModel.submitPrepaidTopUp(durableAccountReady = false) {
            operations.recordSubmission()
        }

        assertEquals(false, result)
        assertEquals(0, operations.launchCalls)
    }

    @Test
    fun stalePrepaidOfferPreventsTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        // A stale prepaid catalogue read (offer token changed underneath the request)
        // right before submission -- without an intervening runCurrent() -- exercises
        // submitPrepaidTopUp()'s own defensive re-check against the live catalogue.
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(
            monthlyPlan(),
            prepaidPlan(offerToken = "new-prepaid-token"),
        )
        operations.forceSelection(prepaidPlan(offerToken = "stale-prepaid-token"))

        val result = viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertEquals(false, result)
        assertEquals(0, operations.launchCalls)
    }

    @Test
    fun activeAutoRenewingCannotUsePrepaidTopUp() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activeAutoRenewingEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        val result = viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertEquals(false, result)
        assertEquals(0, operations.launchCalls)
    }

    @Test
    fun topUpSubmissionDoesNotMutateEntitlement() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(activePrepaidEntitlement(testDispatcher.scheduler.currentTime))
        runCurrent()

        viewModel.submitPrepaidTopUp(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertTrue(viewModel.entitlement.value?.isPrepaid == true)
        assertTrue(viewModel.entitlement.value?.active == true)
    }

    @Test
    fun standardInactivePurchaseBehaviourRemainsUnchanged() = runTest(testDispatcher) {
        val operations = FakeSafeBrowsePassOperations()
        val viewModel = newViewModel(operations)
        operations.catalogState.value = SafeBrowsePassCatalogState.Ready(monthlyPlan(), prepaidPlan())
        operations.entitlementEvents.emit(SafeBrowsePassEntitlement(active = false))
        runCurrent()

        assertEquals(SafeBrowsePassScreenAccessState.NotActive, viewModel.uiState.value.accessState)
        assertTrue(viewModel.uiState.value.purchaseEnabled)

        val result = viewModel.submitPurchase(durableAccountReady = true) {
            operations.recordSubmission()
        }

        assertTrue(result)
        assertEquals(1, operations.launchCalls)
    }

    private class FakeSafeBrowsePassOperations : SafeBrowsePassOperations {

        val catalogState = MutableStateFlow<SafeBrowsePassCatalogState>(SafeBrowsePassCatalogState.Loading)
        val entitlementEvents = MutableSharedFlow<SafeBrowsePassEntitlement>(replay = 1)
        val purchaseStateFlow = MutableStateFlow<SafeBrowsePassPurchaseState>(SafeBrowsePassPurchaseState.Idle)
        val restoreStateFlow = MutableStateFlow<SafeBrowsePassRestoreState>(SafeBrowsePassRestoreState.Idle)

        private val _selectedOffer = MutableStateFlow<SelectedSafeBrowsePassPlan?>(null)

        override val catalog: Flow<SafeBrowsePassCatalogState> = catalogState
        override val entitlement: Flow<SafeBrowsePassEntitlement> = entitlementEvents
        override val selectedOffer: StateFlow<SelectedSafeBrowsePassPlan?> = _selectedOffer
        override val purchaseState: Flow<SafeBrowsePassPurchaseState> = purchaseStateFlow
        override val restoreState: Flow<SafeBrowsePassRestoreState> = restoreStateFlow

        var refreshCalls = 0
        var restoreCalls = 0
        var launchCalls = 0
        var clearSelectionCalls = 0

        override fun refresh() {
            refreshCalls += 1
        }

        override fun selectOffer(offerToken: String): Boolean {
            val ready = catalogState.value as? SafeBrowsePassCatalogState.Ready ?: run {
                _selectedOffer.value = null
                return false
            }

            val selected = listOfNotNull(ready.monthly, ready.prepaid).firstOrNull { plan ->
                plan.offerToken == offerToken
            }

            _selectedOffer.value = selected

            return selected != null
        }

        override fun clearStaleSelection(): Boolean {
            val selected = _selectedOffer.value ?: return false

            val ready = catalogState.value as? SafeBrowsePassCatalogState.Ready

            val stillExists = ready != null &&
                listOfNotNull(ready.monthly, ready.prepaid).any { plan ->
                    plan.period == selected.period && plan.offerToken == selected.offerToken
                }

            if (stillExists) {
                return false
            }

            clearSelectionCalls += 1
            _selectedOffer.value = null
            return true
        }

        fun recordSubmission():
            Boolean {
            launchCalls += 1
            return true
        }

        override fun launchPurchase(
            activity: Activity,
        ): Boolean =
            recordSubmission()

        override fun restorePurchases() {
            restoreCalls += 1
        }

        override suspend fun manageSubscriptionUri(): Uri? = null

        fun forceSelection(plan: SelectedSafeBrowsePassPlan?) {
            _selectedOffer.value = plan
        }
    }
}
