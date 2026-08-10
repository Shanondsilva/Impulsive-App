package com.impulsive.app.frontend.screens.safebrowse

import androidx.compose.runtime.Immutable
import com.impulsive.app.backend.service.billing.SafeBrowsePassPeriod
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState

enum class SafeBrowsePassActivePlanStatus {
    Prepaid,
    AutoRenewing,
    CancelledUntilExpiry,
}

@Immutable
sealed interface SafeBrowsePassScreenAccessState {

    data object Loading :
        SafeBrowsePassScreenAccessState

    data object NotActive :
        SafeBrowsePassScreenAccessState

    data class Active(
        val expiryTimeMillis: Long,
        val planStatus:
            SafeBrowsePassActivePlanStatus,
        val topUpPending:
            Boolean,
    ) : SafeBrowsePassScreenAccessState

    data class Expired(
        val expiryTimeMillis: Long,
        val wasPrepaid: Boolean,
    ) : SafeBrowsePassScreenAccessState
}

@Immutable
data class SafeBrowsePassPlanUiModel(
    val period: SafeBrowsePassPeriod,
    val formattedPrice: String,
    val periodLabel: String,
    val disclosure: String,
)

@Immutable
data class SafeBrowsePassUiState(
    val accessState: SafeBrowsePassScreenAccessState,
    val monthlyPlan: SafeBrowsePassPlanUiModel?,
    val prepaidPlan: SafeBrowsePassPlanUiModel?,
    val selectedPeriod: SafeBrowsePassPeriod?,
    val catalogLoading: Boolean,
    val catalogUnavailable: Boolean,
    val purchaseState: SafeBrowsePassPurchaseState,
    val restoreState: SafeBrowsePassRestoreState,
    val statusMessage: String?,
    val purchaseInProgress: Boolean,
    val purchaseEnabled: Boolean,
    val restoreEnabled: Boolean,
    val showRetry: Boolean,
    val manageSubscriptionAvailable:
        Boolean,
    val prepaidTopUpAvailable:
        Boolean,
    val prepaidTopUpInProgress:
        Boolean,
)

internal fun defaultSafeBrowsePassUiState(): SafeBrowsePassUiState = SafeBrowsePassUiState(
    accessState = SafeBrowsePassScreenAccessState.Loading,
    monthlyPlan = null,
    prepaidPlan = null,
    selectedPeriod = null,
    catalogLoading = true,
    catalogUnavailable = false,
    purchaseState = SafeBrowsePassPurchaseState.Idle,
    restoreState = SafeBrowsePassRestoreState.Idle,
    statusMessage = null,
    purchaseInProgress = false,
    purchaseEnabled = false,
    restoreEnabled = false,
    showRetry = false,
    manageSubscriptionAvailable =
        false,
    prepaidTopUpAvailable =
        false,
    prepaidTopUpInProgress =
        false,
)

internal fun SafeBrowsePassPeriod.label(): String = when (this) {
    SafeBrowsePassPeriod.Monthly -> "Monthly"
    SafeBrowsePassPeriod.Prepaid -> "30-day pass"
}
