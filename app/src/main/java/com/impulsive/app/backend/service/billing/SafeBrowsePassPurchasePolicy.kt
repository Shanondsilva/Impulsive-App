package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt

internal sealed interface SafeBrowsePassPurchaseIntent {
    data object InitialPurchase : SafeBrowsePassPurchaseIntent

    data class PrepaidTopUp(
        val requiredBasePlanId: String,
    ) : SafeBrowsePassPurchaseIntent

    data object AlreadyActive : SafeBrowsePassPurchaseIntent

    data object RefreshRequired : SafeBrowsePassPurchaseIntent
}

internal fun resolveSafeBrowsePassPurchaseIntent(
    entitlement: SafeBrowsePassEntitlement,
    requestedPeriod: SafeBrowsePassPeriod,
    nowMillis: Long,
): SafeBrowsePassPurchaseIntent {
    if (!entitlement.isValidAt(nowMillis)) {
        return SafeBrowsePassPurchaseIntent.InitialPurchase
    }

    if (!entitlement.isPrepaid) {
        return SafeBrowsePassPurchaseIntent.AlreadyActive
    }

    if (requestedPeriod != SafeBrowsePassPeriod.Prepaid) {
        return SafeBrowsePassPurchaseIntent.AlreadyActive
    }

    val basePlanId = entitlement.basePlanId?.trim()?.takeIf { it.isNotEmpty() }
        ?: return SafeBrowsePassPurchaseIntent.RefreshRequired

    return SafeBrowsePassPurchaseIntent.PrepaidTopUp(
        requiredBasePlanId = basePlanId,
    )
}
