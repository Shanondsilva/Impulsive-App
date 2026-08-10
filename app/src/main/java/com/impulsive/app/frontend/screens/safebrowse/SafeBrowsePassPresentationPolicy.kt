package com.impulsive.app.frontend.screens.safebrowse

import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState

internal data class SafeBrowsePassPresentationDecision(
    val accessState:
        SafeBrowsePassScreenAccessState,
    val standardPurchaseEligible:
        Boolean,
    val restoreEligible:
        Boolean,
    val manageSubscriptionAvailable:
        Boolean,
    val prepaidTopUpAvailable:
        Boolean,
    val prepaidTopUpInProgress:
        Boolean,
)

internal fun resolveSafeBrowsePassPresentation(
    entitlement:
        SafeBrowsePassEntitlement?,
    catalogLoading:
        Boolean,
    monthlyOfferAvailable:
        Boolean,
    prepaidOfferAvailable:
        Boolean,
    purchaseState:
        SafeBrowsePassPurchaseState,
    restoreState:
        SafeBrowsePassRestoreState,
    nowMillis:
        Long,
): SafeBrowsePassPresentationDecision {
    val entitlementResolved =
        entitlement != null

    val purchaseOperationInProgress =
        purchaseState ==
            SafeBrowsePassPurchaseState
                .RefreshingOffer ||
            purchaseState ==
                SafeBrowsePassPurchaseState
                    .Launching ||
            purchaseState ==
                SafeBrowsePassPurchaseState
                    .Verifying

    val pendingInitialPurchase =
        purchaseState ==
            SafeBrowsePassPurchaseState
                .Pending

    val pendingTopUp =
        purchaseState ==
            SafeBrowsePassPurchaseState
                .PendingTopUp

    val restoreInProgress =
        restoreState ==
            SafeBrowsePassRestoreState
                .Restoring

    if (!entitlementResolved) {
        return SafeBrowsePassPresentationDecision(
            accessState =
                SafeBrowsePassScreenAccessState
                    .Loading,
            standardPurchaseEligible =
                false,
            restoreEligible =
                false,
            manageSubscriptionAvailable =
                false,
            prepaidTopUpAvailable =
                false,
            prepaidTopUpInProgress =
                false,
        )
    }

    val current =
        requireNotNull(entitlement)

    if (current.isValidAt(nowMillis)) {
        if (current.isPrepaid) {
            return SafeBrowsePassPresentationDecision(
                accessState =
                    SafeBrowsePassScreenAccessState
                        .Active(
                            expiryTimeMillis =
                                current
                                    .expiryTimeMillis,
                            planStatus =
                                SafeBrowsePassActivePlanStatus
                                    .Prepaid,
                            topUpPending =
                                pendingTopUp,
                        ),
                standardPurchaseEligible =
                    false,
                restoreEligible =
                    false,
                manageSubscriptionAvailable =
                    false,
                prepaidTopUpAvailable =
                    prepaidOfferAvailable &&
                        !purchaseOperationInProgress &&
                        !pendingInitialPurchase &&
                        !pendingTopUp &&
                        !restoreInProgress,
                prepaidTopUpInProgress =
                    purchaseOperationInProgress ||
                        pendingTopUp,
            )
        }

        val status =
            if (
                current.renewalState ==
                SafeBrowsePassRenewalState
                    .CancelledUntilExpiry
            ) {
                SafeBrowsePassActivePlanStatus
                    .CancelledUntilExpiry
            } else {
                SafeBrowsePassActivePlanStatus
                    .AutoRenewing
            }

        return SafeBrowsePassPresentationDecision(
            accessState =
                SafeBrowsePassScreenAccessState
                    .Active(
                        expiryTimeMillis =
                            current
                                .expiryTimeMillis,
                        planStatus =
                            status,
                        topUpPending =
                            false,
                    ),
            standardPurchaseEligible =
                false,
            restoreEligible =
                false,
            manageSubscriptionAvailable =
                true,
            prepaidTopUpAvailable =
                false,
            prepaidTopUpInProgress =
                false,
        )
    }

    val expired =
        current.expiryTimeMillis > 0L &&
            nowMillis >=
                current.expiryTimeMillis

    val accessState =
        when {
            expired ->
                SafeBrowsePassScreenAccessState
                    .Expired(
                        expiryTimeMillis =
                            current
                                .expiryTimeMillis,
                        wasPrepaid =
                            current.isPrepaid,
                    )

            catalogLoading ->
                SafeBrowsePassScreenAccessState
                    .Loading

            else ->
                SafeBrowsePassScreenAccessState
                    .NotActive
        }

    val inactivePurchaseEligible =
        (
            accessState ==
                SafeBrowsePassScreenAccessState
                    .NotActive ||
                accessState is
                SafeBrowsePassScreenAccessState
                    .Expired
        ) &&
            (
                monthlyOfferAvailable ||
                    prepaidOfferAvailable
            ) &&
            !purchaseOperationInProgress &&
            !pendingInitialPurchase &&
            !pendingTopUp &&
            !restoreInProgress

    return SafeBrowsePassPresentationDecision(
        accessState =
            accessState,
        standardPurchaseEligible =
            inactivePurchaseEligible,
        restoreEligible =
            accessState !=
                SafeBrowsePassScreenAccessState
                    .Loading &&
                !purchaseOperationInProgress &&
                !restoreInProgress,
        manageSubscriptionAvailable =
            false,
        prepaidTopUpAvailable =
            false,
        prepaidTopUpInProgress =
            false,
    )
}
