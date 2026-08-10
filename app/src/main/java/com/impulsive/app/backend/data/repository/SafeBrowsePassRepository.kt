package com.impulsive.app.backend.data.repository

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.local.preferences.SafeBrowsePassEntitlementDataSource
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt
import com.impulsive.app.backend.service.billing.BillingManager
import com.impulsive.app.backend.service.billing.SafeBrowsePassCatalogState
import com.impulsive.app.backend.service.billing.SafeBrowsePassPurchaseState
import com.impulsive.app.backend.service.billing.SafeBrowsePassRestoreState
import com.impulsive.app.backend.service.billing.SelectedSafeBrowsePassPlan
import com.impulsive.app.backend.service.billing.buildGooglePlaySubscriptionManagementUrl
import com.impulsive.app.backend.service.billing.resolveSafeBrowsePassPurchaseState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal interface SafeBrowsePassOperations {
    val catalog: Flow<SafeBrowsePassCatalogState>
    val entitlement: Flow<SafeBrowsePassEntitlement>
    val selectedOffer: StateFlow<SelectedSafeBrowsePassPlan?>
    val purchaseState: Flow<SafeBrowsePassPurchaseState>
    val restoreState: Flow<SafeBrowsePassRestoreState>

    fun refresh()
    fun selectOffer(offerToken: String): Boolean
    fun clearStaleSelection(): Boolean
    fun launchPurchase(activity: Activity): Boolean
    fun restorePurchases()
    suspend fun manageSubscriptionUri(): Uri?
}

internal interface SafeBrowsePassAccountProvider {
    val authenticatedUid: Flow<String?>

    fun currentAuthenticatedUid(): String?
}

private class FirebaseSafeBrowsePassAccountProvider(
    private val firebaseAuth: FirebaseAuth,
) : SafeBrowsePassAccountProvider {
    override val authenticatedUid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(
                auth.currentUser
                    ?.takeUnless { user -> user.isAnonymous }
                    ?.uid
                    ?.trim()
                    ?.takeIf(String::isNotBlank),
            )
        }

        firebaseAuth.addAuthStateListener(listener)
        trySend(currentAuthenticatedUid())

        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()

    override fun currentAuthenticatedUid(): String? =
        firebaseAuth.currentUser
            ?.takeUnless { user -> user.isAnonymous }
            ?.uid
            ?.trim()
            ?.takeIf(String::isNotBlank)
}

/**
 * The only production layer that reads or mutates the cached Safe Browse Pass entitlement.
 * Never shares storage, keys, or an in-memory instance with [PremiumRepository] or
 * [SafeBrowseAccessRepository] -- Safe Browse Pass, Impulsive Plus, and the timed reward
 * ledger are three independent entitlement axes.
 */
class SafeBrowsePassRepository internal constructor(
    private val dataSource: SafeBrowsePassEntitlementDataSource,
    private val accountProvider: SafeBrowsePassAccountProvider,
    private val billingManagerProvider: (() -> BillingManager)? = null,
    private val packageNameProvider: (() -> String)? = null,
) : SafeBrowsePassOperations {
    constructor(
        context: Context,
        billingManagerProvider: (() -> BillingManager)? = null,
    ) : this(
        dataSource = SafeBrowsePassEntitlementDataSource(context.applicationContext),
        accountProvider = FirebaseSafeBrowsePassAccountProvider(FirebaseAuth.getInstance()),
        billingManagerProvider = billingManagerProvider,
        packageNameProvider = { context.applicationContext.packageName },
    )

    private val _selectedOffer = MutableStateFlow<SelectedSafeBrowsePassPlan?>(null)

    override val selectedOffer: StateFlow<SelectedSafeBrowsePassPlan?> =
        _selectedOffer.asStateFlow()

    override val entitlement: Flow<SafeBrowsePassEntitlement> =
        combine(
            dataSource.record,
            accountProvider.authenticatedUid,
        ) { record, authenticatedUid ->
            if (authenticatedUid != null && record.ownerUid == authenticatedUid) {
                record.entitlement
            } else {
                SafeBrowsePassEntitlement()
            }
        }.distinctUntilChanged()

    override val catalog: Flow<SafeBrowsePassCatalogState>
        get() = requireBillingManager().safeBrowsePassCatalogState

    override val purchaseState: Flow<SafeBrowsePassPurchaseState>
        get() = combine(
            requireBillingManager().safeBrowsePassBillingUiState,
            requireBillingManager().safeBrowsePassPendingKind,
            entitlement,
        ) { billingState, pendingKind, currentEntitlement ->
            resolveSafeBrowsePassPurchaseState(
                billingState = billingState,
                pendingKind = pendingKind,
                entitlementActive = currentEntitlement.isValidAt(System.currentTimeMillis()),
            )
        }.distinctUntilChanged()

    override val restoreState: Flow<SafeBrowsePassRestoreState>
        get() = requireBillingManager().safeBrowsePassRestoreState

    private fun requireBillingManager(): BillingManager =
        checkNotNull(billingManagerProvider?.invoke()) {
            "Safe Browse Pass billing operations require the shared BillingManager."
        }

    private fun currentEligibleOffers(): List<SelectedSafeBrowsePassPlan> {
        val state = requireBillingManager().safeBrowsePassCatalogState.value
            as? SafeBrowsePassCatalogState.Ready
            ?: return emptyList()
        return listOfNotNull(state.monthly, state.prepaid)
    }

    override fun refresh() {
        clearStaleSelection()
        requireBillingManager().refreshProductDetails()
    }

    override fun selectOffer(offerToken: String): Boolean {
        val normalizedToken = offerToken.trim().takeIf(String::isNotBlank)
            ?: run {
                _selectedOffer.value = null
                return false
            }
        val selected = currentEligibleOffers().firstOrNull { plan ->
            plan.offerToken == normalizedToken
        }
        _selectedOffer.value = selected
        return selected != null
    }

    override fun clearStaleSelection(): Boolean {
        val current = _selectedOffer.value ?: return false
        val stillExists = currentEligibleOffers().any { plan ->
            plan.offerToken == current.offerToken && plan.period == current.period
        }
        if (stillExists) {
            return false
        }
        _selectedOffer.value = null
        return true
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val selected = _selectedOffer.value ?: return false
        val current = currentEligibleOffers().firstOrNull { plan ->
            plan.offerToken == selected.offerToken && plan.period == selected.period
        } ?: run {
            _selectedOffer.value = null
            return false
        }

        requireBillingManager().launchSafeBrowsePassPurchase(
            activity = activity,
            period = current.period,
            expectedOfferToken = current.offerToken,
        )
        return true
    }

    override fun restorePurchases() {
        requireBillingManager().restoreSafeBrowsePassPurchases()
    }

    internal suspend fun manageSubscriptionUrl(
        nowMillis: Long =
            System.currentTimeMillis(),
    ): String? {
        val current =
            currentEntitlement()

        val packageName =
            packageNameProvider
                ?.invoke()
                ?.trim()
                ?.takeIf(
                    String::isNotBlank,
                )
                ?: return null

        if (!current.isValidAt(nowMillis)) {
            return null
        }

        if (current.isPrepaid) {
            return null
        }

        val productId =
            current.productId
                ?.takeIf { product ->
                    product ==
                        BillingManager
                            .SafeBrowsePassProductId
                }
                ?: return null

        return buildGooglePlaySubscriptionManagementUrl(
            packageName =
                packageName,
            productId =
                productId,
        )
    }

    override suspend fun manageSubscriptionUri():
        Uri? =
        manageSubscriptionUrl()
            ?.let { url ->
                Uri.parse(url)
            }

    suspend fun currentEntitlement(): SafeBrowsePassEntitlement {
        val authenticatedUid =
            accountProvider.currentAuthenticatedUid()
                ?: return SafeBrowsePassEntitlement()

        val record = dataSource.currentRecord()

        return if (record.ownerUid == authenticatedUid) {
            record.entitlement
        } else {
            SafeBrowsePassEntitlement()
        }
    }

    suspend fun setVerifiedEntitlement(
        expectedUid: String,
        entitlement: SafeBrowsePassEntitlement,
    ): Boolean {
        val normalisedExpectedUid =
            expectedUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: return false

        if (accountProvider.currentAuthenticatedUid() != normalisedExpectedUid) {
            return false
        }

        dataSource.setEntitlement(
            ownerUid = normalisedExpectedUid,
            entitlement = entitlement,
        )

        val currentUid = accountProvider.currentAuthenticatedUid()
        val stillCurrent = currentUid == normalisedExpectedUid

        if (!stillCurrent) {
            dataSource.clearUnlessOwnedBy(currentUid)
        }

        return stillCurrent
    }

    suspend fun onAccountChanged(currentAuthenticatedUid: String?) {
        val normalisedUid =
            currentAuthenticatedUid
                ?.trim()
                ?.takeIf(String::isNotBlank)

        val actualUid = accountProvider.currentAuthenticatedUid()
        val safeUid = normalisedUid?.takeIf { suppliedUid -> suppliedUid == actualUid }

        dataSource.clearUnlessOwnedBy(safeUid)
    }

    suspend fun expireCurrentEntitlementIfRequired(nowMillis: Long): Boolean {
        val uid = accountProvider.currentAuthenticatedUid() ?: return false
        return dataSource.expireIfOwnedBy(expectedOwnerUid = uid, nowMillis = nowMillis)
    }

    suspend fun clear() {
        dataSource.clear()
    }
}
