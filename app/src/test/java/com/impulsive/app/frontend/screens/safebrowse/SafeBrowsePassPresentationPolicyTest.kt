package com.impulsive.app.frontend.screens.safebrowse

import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassPresentationPolicyTest {

    private fun decide(
        entitlement: SafeBrowsePassEntitlement? = null,
        catalogLoading: Boolean = false,
        monthlyOfferAvailable: Boolean = true,
        prepaidOfferAvailable: Boolean = true,
        purchaseState: SafeBrowsePassPurchaseState = SafeBrowsePassPurchaseState.Idle,
        restoreState: SafeBrowsePassRestoreState = SafeBrowsePassRestoreState.Idle,
        nowMillis: Long = 1_000_000L,
    ) = resolveSafeBrowsePassPresentation(
        entitlement = entitlement,
        catalogLoading = catalogLoading,
        monthlyOfferAvailable = monthlyOfferAvailable,
        prepaidOfferAvailable = prepaidOfferAvailable,
        purchaseState = purchaseState,
        restoreState = restoreState,
        nowMillis = nowMillis,
    )

    private fun activePrepaid(nowMillis: Long = 1_000_000L) = SafeBrowsePassEntitlement(
        active = true,
        productId = "safe_browse_pass",
        basePlanId = "prepaid-30",
        expiryTimeMillis = nowMillis + 60_000L,
        isPrepaid = true,
        renewalState = SafeBrowsePassRenewalState.NotApplicable,
        lastVerifiedMillis = nowMillis,
    )

    private fun activeAutoRenewing(
        nowMillis: Long = 1_000_000L,
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

    private fun inactiveWithNoHistory() = SafeBrowsePassEntitlement()

    private fun inactiveReachedExpiry(
        nowMillis: Long = 1_000_000L,
        isPrepaid: Boolean = false,
    ) = SafeBrowsePassEntitlement(
        active = false,
        productId = "safe_browse_pass",
        basePlanId = if (isPrepaid) "prepaid-30" else "monthly",
        expiryTimeMillis = nowMillis - 1L,
        isPrepaid = isPrepaid,
        renewalState = if (isPrepaid) {
            SafeBrowsePassRenewalState.NotApplicable
        } else {
            SafeBrowsePassRenewalState.Unknown
        },
        lastVerifiedMillis = nowMillis,
    )

    @Test
    fun nullEntitlementReturnsLoadingAndDisablesEveryAction() {
        val decision = decide(entitlement = null)

        assertEquals(SafeBrowsePassScreenAccessState.Loading, decision.accessState)
        assertFalse(decision.standardPurchaseEligible)
        assertFalse(decision.restoreEligible)
        assertFalse(decision.manageSubscriptionAvailable)
        assertFalse(decision.prepaidTopUpAvailable)
        assertFalse(decision.prepaidTopUpInProgress)
    }

    @Test
    fun activePrepaidReturnsActivePrepaid() {
        val decision = decide(entitlement = activePrepaid())

        val access = decision.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.Prepaid, access.planStatus)
        assertFalse(access.topUpPending)
    }

    @Test
    fun activePrepaidExposesTopUpWhenAPrepaidOfferExists() {
        val decision = decide(
            entitlement = activePrepaid(),
            prepaidOfferAvailable = true,
        )

        assertTrue(decision.prepaidTopUpAvailable)
    }

    @Test
    fun activePrepaidWithoutAPrepaidOfferHidesTopUp() {
        val decision = decide(
            entitlement = activePrepaid(),
            prepaidOfferAvailable = false,
        )

        assertFalse(decision.prepaidTopUpAvailable)
    }

    @Test
    fun pendingTopUpKeepsActivePrepaidAndMarksTopUpPending() {
        val decision = decide(
            entitlement = activePrepaid(),
            purchaseState = SafeBrowsePassPurchaseState.PendingTopUp,
        )

        val access = decision.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.Prepaid, access.planStatus)
        assertTrue(access.topUpPending)
    }

    @Test
    fun pendingTopUpDisablesAnotherTopUp() {
        val decision = decide(
            entitlement = activePrepaid(),
            purchaseState = SafeBrowsePassPurchaseState.PendingTopUp,
        )

        assertFalse(decision.prepaidTopUpAvailable)
        assertTrue(decision.prepaidTopUpInProgress)
    }

    @Test
    fun refreshingOrVerifyingDisablesTopUp() {
        listOf(
            SafeBrowsePassPurchaseState.RefreshingOffer,
            SafeBrowsePassPurchaseState.Launching,
            SafeBrowsePassPurchaseState.Verifying,
        ).forEach { state ->
            val decision = decide(
                entitlement = activePrepaid(),
                purchaseState = state,
            )

            assertFalse(
                "expected prepaidTopUpAvailable=false for purchaseState=$state",
                decision.prepaidTopUpAvailable,
            )
            assertTrue(
                "expected prepaidTopUpInProgress=true for purchaseState=$state",
                decision.prepaidTopUpInProgress,
            )
        }
    }

    @Test
    fun activeAutoRenewingReturnsActiveAutoRenewing() {
        val decision = decide(entitlement = activeAutoRenewing())

        val access = decision.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.AutoRenewing, access.planStatus)
    }

    @Test
    fun activeAutoRenewingExposesManagement() {
        val decision = decide(entitlement = activeAutoRenewing())

        assertTrue(decision.manageSubscriptionAvailable)
    }

    @Test
    fun cancelledButUnexpiredReturnsActiveCancelledUntilExpiry() {
        val decision = decide(
            entitlement = activeAutoRenewing(
                renewalState = SafeBrowsePassRenewalState.CancelledUntilExpiry,
            ),
        )

        val access = decision.accessState as SafeBrowsePassScreenAccessState.Active
        assertEquals(SafeBrowsePassActivePlanStatus.CancelledUntilExpiry, access.planStatus)
    }

    @Test
    fun cancelledButUnexpiredStillExposesManagement() {
        val decision = decide(
            entitlement = activeAutoRenewing(
                renewalState = SafeBrowsePassRenewalState.CancelledUntilExpiry,
            ),
        )

        assertTrue(decision.manageSubscriptionAvailable)
    }

    @Test
    fun activePlansNeverExposeTheStandardPurchaseAction() {
        listOf(
            activePrepaid(),
            activeAutoRenewing(),
            activeAutoRenewing(renewalState = SafeBrowsePassRenewalState.CancelledUntilExpiry),
        ).forEach { entitlement ->
            val decision = decide(entitlement = entitlement)
            assertFalse(decision.standardPurchaseEligible)
        }
    }

    @Test
    fun inactiveEntitlementWithAFutureOrAbsentExpiryReturnsNotActive() {
        assertEquals(
            SafeBrowsePassScreenAccessState.NotActive,
            decide(entitlement = inactiveWithNoHistory()).accessState,
        )

        val futureExpiry = SafeBrowsePassEntitlement(
            active = false,
            expiryTimeMillis = 2_000_000L,
            lastVerifiedMillis = 1_000_000L,
        )
        assertEquals(
            SafeBrowsePassScreenAccessState.NotActive,
            decide(entitlement = futureExpiry, nowMillis = 1_000_000L).accessState,
        )
    }

    @Test
    fun inactiveEntitlementWithAReachedExpiryReturnsExpired() {
        val decision = decide(entitlement = inactiveReachedExpiry())

        assertTrue(decision.accessState is SafeBrowsePassScreenAccessState.Expired)
    }

    @Test
    fun expiredPrepaidRecordsWasPrepaidTrue() {
        val decision = decide(entitlement = inactiveReachedExpiry(isPrepaid = true))

        val expired = decision.accessState as SafeBrowsePassScreenAccessState.Expired
        assertTrue(expired.wasPrepaid)
    }

    @Test
    fun expiredAutoRenewingRecordsWasPrepaidFalse() {
        val decision = decide(entitlement = inactiveReachedExpiry(isPrepaid = false))

        val expired = decision.accessState as SafeBrowsePassScreenAccessState.Expired
        assertFalse(expired.wasPrepaid)
    }

    @Test
    fun expiredStateAllowsAStandardPurchaseWhenCatalogueOffersExist() {
        val decision = decide(
            entitlement = inactiveReachedExpiry(),
            monthlyOfferAvailable = true,
            prepaidOfferAvailable = false,
        )

        assertTrue(decision.accessState is SafeBrowsePassScreenAccessState.Expired)
        assertTrue(decision.standardPurchaseEligible)
    }

    @Test
    fun expiredStateAllowsRestoreWhenNoOperationIsInProgress() {
        val decision = decide(entitlement = inactiveReachedExpiry())

        assertTrue(decision.restoreEligible)
    }

    @Test
    fun catalogueLoadingKeepsInactiveNonExpiredStateLoading() {
        val decision = decide(
            entitlement = inactiveWithNoHistory(),
            catalogLoading = true,
        )

        assertEquals(SafeBrowsePassScreenAccessState.Loading, decision.accessState)
    }

    @Test
    fun purchaseOrRestoreWorkDisablesCompetingActions() {
        val purchasing = decide(
            entitlement = inactiveReachedExpiry(),
            purchaseState = SafeBrowsePassPurchaseState.Launching,
        )
        assertFalse(purchasing.standardPurchaseEligible)
        assertFalse(purchasing.restoreEligible)

        val restoring = decide(
            entitlement = inactiveReachedExpiry(),
            restoreState = SafeBrowsePassRestoreState.Restoring,
        )
        assertFalse(restoring.standardPurchaseEligible)
        assertFalse(restoring.restoreEligible)

        val pendingInitial = decide(
            entitlement = inactiveReachedExpiry(),
            purchaseState = SafeBrowsePassPurchaseState.Pending,
        )
        assertFalse(pendingInitial.standardPurchaseEligible)
    }
}
