package com.impulsive.app.backend.session.safebrowse

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.SafeBrowsePassOperations
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt
import com.impulsive.app.backend.service.billing.BillingManager
import com.impulsive.app.backend.service.billing.SafeBrowsePassCatalogState
import com.impulsive.app.backend.service.billing.SafeBrowsePassPeriod
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState
import com.impulsive.app.backend.service.billing.SelectedSafeBrowsePassPlan
import com.impulsive.app.backend.service.billing.safeBrowsePassPeriodLabel
import com.impulsive.app.backend.service.billing.safeBrowsePassPlanDisclosure
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassActivePlanStatus
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassPlanUiModel
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassScreenAccessState
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassUiState
import com.impulsive.app.frontend.screens.safebrowse.defaultSafeBrowsePassUiState
import com.impulsive.app.frontend.screens.safebrowse.resolveSafeBrowsePassPresentation
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SafeBrowsePassViewModel internal constructor(
    private val operations: SafeBrowsePassOperations,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    internal constructor(
        billingManager: BillingManager,
    ) : this(
        operations = billingManager.safeBrowsePassRepositoryForViewModel(),
    )

    private val launchInProgress = AtomicBoolean(false)

    val catalogue: StateFlow<SafeBrowsePassCatalogState> = operations.catalog.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SafeBrowsePassCatalogState.Loading,
    )

    val entitlement: StateFlow<SafeBrowsePassEntitlement?> = operations.entitlement
        .map<SafeBrowsePassEntitlement, SafeBrowsePassEntitlement?> { entitlement -> entitlement }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    val selectedOffer: StateFlow<SelectedSafeBrowsePassPlan?> = operations.selectedOffer

    val purchaseState: StateFlow<SafeBrowsePassPurchaseState> = operations.purchaseState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SafeBrowsePassPurchaseState.Idle,
    )

    val restoreState: StateFlow<SafeBrowsePassRestoreState> = operations.restoreState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SafeBrowsePassRestoreState.Idle,
    )

    val uiState: StateFlow<SafeBrowsePassUiState> = combine(
        catalogue,
        entitlement,
        selectedOffer,
        purchaseState,
        restoreState,
    ) { catalogState, currentEntitlement, currentSelection, currentPurchaseState, currentRestoreState ->
        reconcileSelection(catalogState, currentSelection)
        val selected = operations.selectedOffer.value
        toUiState(
            catalogState = catalogState,
            currentEntitlement = currentEntitlement,
            currentSelection = selected,
            currentPurchaseState = currentPurchaseState,
            currentRestoreState = currentRestoreState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = defaultSafeBrowsePassUiState(),
    )

    private fun reconcileSelection(
        catalogState: SafeBrowsePassCatalogState,
        currentSelection: SelectedSafeBrowsePassPlan?,
    ) {
        when (catalogState) {
            SafeBrowsePassCatalogState.Loading -> Unit
            SafeBrowsePassCatalogState.Unavailable -> operations.clearStaleSelection()
            is SafeBrowsePassCatalogState.Ready -> {
                val offers = listOfNotNull(catalogState.monthly, catalogState.prepaid)
                val stillValid = currentSelection != null && offers.any { plan ->
                    plan.period == currentSelection.period &&
                        plan.offerToken == currentSelection.offerToken
                }
                if (!stillValid) {
                    operations.clearStaleSelection()
                    val defaultPlan = catalogState.monthly ?: catalogState.prepaid
                    defaultPlan?.let { plan -> operations.selectOffer(plan.offerToken) }
                }
            }
        }
    }

    private fun toUiState(
        catalogState: SafeBrowsePassCatalogState,
        currentEntitlement: SafeBrowsePassEntitlement?,
        currentSelection: SelectedSafeBrowsePassPlan?,
        currentPurchaseState: SafeBrowsePassPurchaseState,
        currentRestoreState: SafeBrowsePassRestoreState,
    ): SafeBrowsePassUiState {
        val entitlementResolved = currentEntitlement != null
        val ready = catalogState as? SafeBrowsePassCatalogState.Ready
        val selectedPeriod = currentSelection?.period
        val purchaseInProgress = currentPurchaseState == SafeBrowsePassPurchaseState.RefreshingOffer ||
            currentPurchaseState == SafeBrowsePassPurchaseState.Launching ||
            currentPurchaseState == SafeBrowsePassPurchaseState.Verifying
        val pendingPurchase = currentPurchaseState == SafeBrowsePassPurchaseState.Pending ||
            currentPurchaseState == SafeBrowsePassPurchaseState.PendingTopUp
        val launchable = currentPurchaseState == SafeBrowsePassPurchaseState.Idle ||
            currentPurchaseState == SafeBrowsePassPurchaseState.UserCancelled ||
            currentPurchaseState == SafeBrowsePassPurchaseState.AlreadyOwned ||
            currentPurchaseState == SafeBrowsePassPurchaseState.Unavailable ||
            currentPurchaseState is SafeBrowsePassPurchaseState.Error

        val presentation = resolveSafeBrowsePassPresentation(
            entitlement = currentEntitlement,
            catalogLoading = catalogState == SafeBrowsePassCatalogState.Loading,
            monthlyOfferAvailable = ready?.monthly != null,
            prepaidOfferAvailable = ready?.prepaid != null,
            purchaseState = currentPurchaseState,
            restoreState = currentRestoreState,
            nowMillis = nowMillis(),
        )

        return SafeBrowsePassUiState(
            accessState = presentation.accessState,
            monthlyPlan = ready?.monthly?.toUiModel(),
            prepaidPlan = ready?.prepaid?.toUiModel(),
            selectedPeriod = selectedPeriod,
            catalogLoading = !entitlementResolved || catalogState == SafeBrowsePassCatalogState.Loading,
            catalogUnavailable = catalogState == SafeBrowsePassCatalogState.Unavailable,
            purchaseState = currentPurchaseState,
            restoreState = currentRestoreState,
            statusMessage = statusMessage(currentPurchaseState, currentRestoreState),
            purchaseInProgress = purchaseInProgress,
            purchaseEnabled = presentation.standardPurchaseEligible &&
                selectedPeriod != null &&
                launchable,
            restoreEnabled = presentation.restoreEligible,
            showRetry = catalogState == SafeBrowsePassCatalogState.Unavailable ||
                currentPurchaseState is SafeBrowsePassPurchaseState.Error ||
                currentRestoreState is SafeBrowsePassRestoreState.Error,
            manageSubscriptionAvailable = presentation.manageSubscriptionAvailable,
            prepaidTopUpAvailable = presentation.prepaidTopUpAvailable,
            prepaidTopUpInProgress = presentation.prepaidTopUpInProgress,
        )
    }

    private fun SelectedSafeBrowsePassPlan.toUiModel(): SafeBrowsePassPlanUiModel =
        SafeBrowsePassPlanUiModel(
            period = period,
            formattedPrice = formattedPrice,
            periodLabel = safeBrowsePassPeriodLabel(billingPeriod) ?: "Subscription period",
            disclosure = safeBrowsePassPlanDisclosure(period),
        )

    fun selectPeriod(period: SafeBrowsePassPeriod) {
        val ready = catalogue.value as? SafeBrowsePassCatalogState.Ready ?: return
        val plan = when (period) {
            SafeBrowsePassPeriod.Monthly -> ready.monthly
            SafeBrowsePassPeriod.Prepaid -> ready.prepaid
        } ?: return
        operations.selectOffer(plan.offerToken)
    }

    /**
     * The testable purchase-submission core, kept separate from [launchPurchase] so
     * duplicate-launch and stale-selection behaviour can be exercised without constructing
     * or retaining an [Activity].
     */
    internal fun submitPurchase(
        durableAccountReady: Boolean,
        submit: () -> Boolean,
    ): Boolean {
        if (!durableAccountReady) {
            return false
        }

        if (!launchInProgress.compareAndSet(false, true)) {
            return false
        }

        return try {
            val state = uiState.value

            if (!state.purchaseEnabled) {
                return false
            }

            val selected = operations.selectedOffer.value ?: return false

            val ready = catalogue.value as? SafeBrowsePassCatalogState.Ready ?: return false

            val exists = listOfNotNull(ready.monthly, ready.prepaid).any { plan ->
                plan.period == selected.period && plan.offerToken == selected.offerToken
            }

            if (!exists) {
                operations.clearStaleSelection()
                return false
            }

            submit()
        } finally {
            launchInProgress.set(false)
        }
    }

    fun launchPurchase(
        activity: Activity,
        durableAccountReady: Boolean,
    ) {
        submitPurchase(durableAccountReady = durableAccountReady) {
            operations.launchPurchase(activity)
        }
    }

    /**
     * The testable prepaid top-up submission core -- separate from [launchPrepaidTopUp] so
     * it can be exercised without constructing or retaining an [Activity]. Uses the same
     * [launchInProgress] guard as [submitPurchase], never a second launch guard.
     */
    internal fun submitPrepaidTopUp(
        durableAccountReady:
            Boolean,
        submit:
            () -> Boolean,
    ): Boolean {
        if (!durableAccountReady) {
            return false
        }

        if (
            !launchInProgress
                .compareAndSet(
                    false,
                    true,
                )
        ) {
            return false
        }

        return try {
            val state =
                uiState.value

            if (
                !state
                    .prepaidTopUpAvailable
            ) {
                return false
            }

            val active =
                state.accessState as?
                    SafeBrowsePassScreenAccessState
                        .Active
                    ?: return false

            if (
                active.planStatus !=
                SafeBrowsePassActivePlanStatus
                    .Prepaid
            ) {
                return false
            }

            val ready =
                catalogue.value as?
                    SafeBrowsePassCatalogState
                        .Ready
                    ?: return false

            val prepaidPlan =
                ready.prepaid
                    ?: return false

            val selected =
                operations.selectOffer(
                    prepaidPlan.offerToken,
                )

            if (!selected) {
                return false
            }

            val selectedOffer =
                operations.selectedOffer
                    .value
                    ?: return false

            if (
                selectedOffer.period !=
                    SafeBrowsePassPeriod
                        .Prepaid ||
                selectedOffer.offerToken !=
                    prepaidPlan.offerToken
            ) {
                operations
                    .clearStaleSelection()

                return false
            }

            submit()
        } finally {
            launchInProgress
                .set(false)
        }
    }

    fun launchPrepaidTopUp(
        activity: Activity,
        durableAccountReady:
            Boolean,
    ) {
        submitPrepaidTopUp(
            durableAccountReady =
                durableAccountReady,
        ) {
            operations.launchPurchase(
                activity,
            )
        }
    }

    fun refresh() {
        operations.refresh()
    }

    fun restorePurchases() {
        if (restoreState.value == SafeBrowsePassRestoreState.Restoring) {
            return
        }
        operations.restorePurchases()
    }

    suspend fun manageSubscriptionUri(): Uri? = operations.manageSubscriptionUri()

    private fun statusMessage(
        currentPurchaseState: SafeBrowsePassPurchaseState,
        currentRestoreState: SafeBrowsePassRestoreState,
    ): String? {
        val purchaseMessage = when (currentPurchaseState) {
            SafeBrowsePassPurchaseState.Idle -> null
            SafeBrowsePassPurchaseState.RefreshingOffer -> "Refreshing this Google Play offer..."
            SafeBrowsePassPurchaseState.Launching -> null
            SafeBrowsePassPurchaseState.Pending -> "Your purchase is pending."
            SafeBrowsePassPurchaseState.PendingTopUp ->
                "Your top-up is pending. Your current Pass remains active."
            SafeBrowsePassPurchaseState.Verifying -> "Verifying your purchase..."
            SafeBrowsePassPurchaseState.Purchased -> "Your Safe Browse Pass is active."
            SafeBrowsePassPurchaseState.VerificationDeferred ->
                "Connect your account to finish verifying this purchase."
            SafeBrowsePassPurchaseState.UserCancelled -> null
            SafeBrowsePassPurchaseState.AlreadyOwned -> "This Pass is already linked to your account."
            SafeBrowsePassPurchaseState.Unavailable -> "This plan is not available right now."
            is SafeBrowsePassPurchaseState.Error -> currentPurchaseState.message
        }

        if (currentPurchaseState != SafeBrowsePassPurchaseState.Idle && purchaseMessage != null) {
            return purchaseMessage
        }

        return when (currentRestoreState) {
            SafeBrowsePassRestoreState.Idle -> purchaseMessage
            SafeBrowsePassRestoreState.Restoring -> "Checking your Google Play purchases..."
            SafeBrowsePassRestoreState.Restored -> "Your Safe Browse Pass was restored."
            SafeBrowsePassRestoreState.NothingToRestore -> "No Safe Browse Pass purchase was found."
            is SafeBrowsePassRestoreState.Error -> currentRestoreState.message
        }
    }
}

class SafeBrowsePassViewModelFactory(
    private val billingManager: BillingManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SafeBrowsePassViewModel::class.java)) {
            return SafeBrowsePassViewModel(billingManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
