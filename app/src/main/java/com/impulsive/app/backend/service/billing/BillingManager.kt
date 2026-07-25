package com.impulsive.app.backend.service.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import com.impulsive.app.backend.domain.model.premium.isValidAt
import com.impulsive.app.backend.service.firebase.AppCheckGatedCallResult
import com.impulsive.app.backend.service.firebase.appCheckReadinessFailureLogMessage
import com.impulsive.app.backend.service.firebase.runAfterAppCheckReadiness
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wraps Google Play Billing for the Plus subscription.
 *
 * The device-side Play Billing response is not trusted as the entitlement source.
 * Plus is granted locally only after the backend callable `verifyPlusSubscription`
 * verifies the purchase token with Google Play, acknowledges the purchase server-side,
 * and returns an active entitlement for the expected product ID.
 */
class BillingManager(
    context: Context,
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val repository = PremiumRepository(appContext)
    private val managerJob = SupervisorJob()
    private val scope = CoroutineScope(managerJob + Dispatchers.IO)
    private val functions = FirebaseFunctions.getInstance(FunctionsRegion)
    // The Play purchase token and product id of the Plus subscription the user
    // currently owns, if any. Populated whenever purchases are queried or
    // updated. Used to turn a period switch into an upgrade/downgrade instead
    // of a second, separately billed purchase.
    @Volatile
    private var ownedPurchaseToken: String? = null

    @Volatile
    private var ownedProductId: String? = null

    private val verifyingPurchaseTokens = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>(),
    )
    private val connectionInProgress = AtomicBoolean(false)
    private val entitlementRefreshInFlight = AtomicBoolean(false)
    private val restorePendingAfterConnection = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val productDetailsById = ConcurrentHashMap<String, ProductDetails>()
    private val selectedPurchaseOffersByPeriod =
        ConcurrentHashMap<BillingPeriod, SelectedPurchaseOffer>()

    private val _subscriptionCatalogState =
        MutableStateFlow<SubscriptionCatalogState>(SubscriptionCatalogState.Loading)
    val subscriptionCatalogState: StateFlow<SubscriptionCatalogState> =
        _subscriptionCatalogState.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _restoreState = MutableStateFlow<BillingRestoreState>(BillingRestoreState.Idle)
    val restoreState: StateFlow<BillingRestoreState> = _restoreState.asStateFlow()

    private val _billingUiState = MutableStateFlow<BillingUiState>(BillingUiState.Connecting)
    val billingUiState: StateFlow<BillingUiState> = _billingUiState.asStateFlow()

    fun connect() {
        if (released.get()) {
            return
        }

        if (billingClient.isReady) {
            reconnectJob?.cancel()
            reconnectJob = null
            _connected.value = true
            onConnected()
            return
        }

        if (!connectionInProgress.compareAndSet(false, true)) {
            return
        }

        _billingUiState.value = BillingUiState.Connecting

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connectionInProgress.set(false)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectJob?.cancel()
                    reconnectJob = null
                    _connected.value = true
                    Log.i(Tag, "Google Play Billing connected.")
                    onConnected()
                } else {
                    _connected.value = false
                    selectedPurchaseOffersByPeriod.clear()
                    _subscriptionCatalogState.value = SubscriptionCatalogState.Unavailable
                    _billingUiState.value =
                        billingFailureStateForResponseCode(result.responseCode)
                            ?: BillingUiState.Error(
                                responseCode = result.responseCode,
                                retryable = false,
                            )
                    if (restorePendingAfterConnection.getAndSet(false)) {
                        _restoreState.value = BillingRestoreState.Error
                    }
                    when (result.responseCode) {
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                        BillingClient.BillingResponseCode.NETWORK_ERROR,
                        BillingClient.BillingResponseCode.ERROR,
                        -> scheduleReconnectAfterDisconnect()
                    }
                    Log.w(
                        Tag,
                        billingResultFailureMessage(
                            operation = "Google Play Billing setup",
                            result = result,
                        ),
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connectionInProgress.set(false)
                _connected.value = false
                _billingUiState.value = BillingUiState.NetworkOrServiceUnavailable(
                    BillingUnavailableReason.ServiceDisconnected,
                )
                if (restorePendingAfterConnection.getAndSet(false)) {
                    _restoreState.value = BillingRestoreState.Error
                }
                scheduleReconnectAfterDisconnect()
                Log.w(Tag, "Google Play Billing disconnected.")
            }
        })
    }

    private fun scheduleReconnectAfterDisconnect() {
        if (released.get()) {
            return
        }

        if (reconnectJob?.isActive == true) {
            return
        }

        reconnectJob = scope.launch {
            var attemptIndex = 0

            while (true) {
                val retryDelayMillis =
                    BillingReconnectPolicy.delayForAttempt(attemptIndex) ?: break

                delay(retryDelayMillis)

                if (released.get()) {
                    return@launch
                }

                if (billingClient.isReady) {
                    _connected.value = true
                    return@launch
                }

                connect()
                attemptIndex += 1
            }
        }
    }

    private fun onConnected() {
        queryProduct()

        val pendingRestore = restorePendingAfterConnection.getAndSet(false)

        if (FirebaseAuth.getInstance().currentUser != null) {
            if (pendingRestore) {
                performRestorePurchases()
            } else {
                refreshAfterAuthentication()
            }
        } else {
            if (pendingRestore) {
                _restoreState.value = BillingRestoreState.Error
            }

            Log.i(Tag, "Billing connected before Firebase authentication was available.")
        }
    }

    /**
     * Reconciles existing Play purchases and server entitlement after
     * Firebase Auth has restored a signed-in user.
     *
     * This method is deliberately safe to call repeatedly. The purchase-token
     * set prevents duplicate verification of the same purchase, while the
     * entitlement in-flight guard prevents concurrent server refreshes.
     */
    fun onAuthenticatedUserAvailable() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.i(
                Tag,
                "Skipped billing entitlement refresh because no authenticated user is available.",
            )
            return
        }

        if (!billingClient.isReady) {
            connect()
            return
        }

        refreshAfterAuthentication()
    }

    fun onAppForegrounded() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return
        }

        if (billingClient.isReady) {
            refreshPurchases()
        } else {
            connect()
        }

        startEntitlementRefresh()
    }

    private fun refreshAfterAuthentication() {
        Log.i(Tag, "Starting authenticated billing entitlement refresh.")
        refreshPurchases()
        startEntitlementRefresh()
    }

    private fun startEntitlementRefresh() {
        if (
            !entitlementRefreshInFlight.compareAndSet(
                false,
                true,
            )
        ) {
            return
        }

        scope.launch {
            try {
                refreshEntitlementWithRetry()
            } finally {
                entitlementRefreshInFlight.set(false)
            }
        }
    }

    private suspend fun refreshEntitlementWithRetry(): ServerEntitlementRefreshResult {
        var failureIndex = 0

        while (true) {
            val outcome = refreshEntitlementFromServerOnce()
            when {
                outcome == EntitlementRefreshOutcome.AppliedActive ->
                    return ServerEntitlementRefreshResult.Active

                outcome == EntitlementRefreshOutcome.AppliedInactive ->
                    return ServerEntitlementRefreshResult.Inactive

                outcome == EntitlementRefreshOutcome.SkippedNoAuthenticatedUser ->
                    return ServerEntitlementRefreshResult.SkippedNoAuthenticatedUser

                outcome.isRetryableFailure() -> {
                    val retryDelayMillis =
                        EntitlementRefreshRetryPolicy.delayAfterFailure(failureIndex)

                    if (retryDelayMillis == null) {
                        break
                    }

                    failureIndex += 1

                    delay(retryDelayMillis)
                }

                else -> error("Unhandled entitlement refresh outcome.")
            }
        }

        enforceOfflineGraceAfterFailedRefresh()
        return ServerEntitlementRefreshResult.Unavailable
    }

    fun restorePurchases() {
        if (_restoreState.value == BillingRestoreState.Loading) {
            return
        }

        if (FirebaseAuth.getInstance().currentUser == null) {
            _restoreState.value = BillingRestoreState.Error
            return
        }

        _restoreState.value = BillingRestoreState.Loading

        if (!billingClient.isReady) {
            restorePendingAfterConnection.set(true)
            connect()
            return
        }

        performRestorePurchases()
    }

    private fun performRestorePurchases() {
        scope.launch {
            val restoreResult = try {
                restorePurchasesInternal()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(Tag, "Restore purchases failed before reconciliation completed.")
                BillingRestoreState.Error
            }

            _restoreState.value = restoreResult

            when (restoreResult) {
                BillingRestoreState.Success ->
                    _billingUiState.value = BillingUiState.Restored

                BillingRestoreState.NoPurchase ->
                    _billingUiState.value = BillingUiState.NoPurchaseFound

                BillingRestoreState.Idle,
                BillingRestoreState.Loading,
                BillingRestoreState.Error,
                -> Unit
            }
        }
    }

    private suspend fun restorePurchasesInternal(): BillingRestoreState {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val queryDeferred = CompletableDeferred<Pair<BillingResult, List<Purchase>>>()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            queryDeferred.complete(result to purchases)
        }

        val queryResult = withTimeoutOrNull(15_000L) {
            queryDeferred.await()
        }

        if (queryResult == null) {
            Log.w(Tag, "Restore purchase query timed out before reconciliation completed.")
            val serverRefreshResult = refreshEntitlementWithRetry()
            return resolveBillingRestoreState(
                playQuerySucceeded = false,
                verifiedActivePurchaseCount = 0,
                verificationFailed = false,
                serverRefreshResult = serverRefreshResult,
            )
        }

        if (queryResult.first.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                Tag,
                billingResultFailureMessage(
                    operation = "Restore purchase query",
                    result = queryResult.first,
                ),
            )
            val serverRefreshResult = refreshEntitlementWithRetry()
            return resolveBillingRestoreState(
                playQuerySucceeded = false,
                verifiedActivePurchaseCount = 0,
                verificationFailed = false,
                serverRefreshResult = serverRefreshResult,
            )
        }

        val eligiblePurchases = queryResult.second.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { productId ->
                    productId == PlusProductId || productId == PlusYearlyProductId
                }
        }

        var verifiedActivePurchaseCount = 0
        var verificationFailed = false

        eligiblePurchases.forEach { purchase ->
            val productId = purchase.products.firstOrNull { candidate ->
                candidate == PlusProductId || candidate == PlusYearlyProductId
            } ?: return@forEach

            try {
                val verified = verifyPurchaseWithBackend(purchase, productId)
                if (verified != null) {
                    grantEntitlement(verified.productId, verified.expiryTimeMillis)
                    verifiedActivePurchaseCount += 1
                }
            } catch (throwable: Throwable) {
                verificationFailed = true
                Log.w(Tag, "Restore purchase verification failed; continuing reconciliation.")
            }
        }

        val serverRefreshResult = refreshEntitlementWithRetry()

        return resolveBillingRestoreState(
            playQuerySucceeded = true,
            verifiedActivePurchaseCount = verifiedActivePurchaseCount,
            verificationFailed = verificationFailed,
            serverRefreshResult = serverRefreshResult,
        )
    }

    private fun queryProduct() {
        _subscriptionCatalogState.value = SubscriptionCatalogState.Loading

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PlusProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PlusYearlyProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsById.clear()
                queryResult.productDetailsList.forEach { details ->
                    productDetailsById[details.productId] = details
                }

                val monthlyDetails = productDetailsById[PlusProductId]
                val yearlyDetails = productDetailsById[PlusYearlyProductId]

                val monthlyPlan = monthlyDetails?.let { details ->
                    selectMonthlySubscriptionPlan(
                        productId = details.productId,
                        offers = details.offerSnapshots(),
                        infiniteRecurringMode =
                            ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                    )
                }
                val yearlyPlan = yearlyDetails?.let { details ->
                    selectYearlySubscriptionPlan(
                        productId = details.productId,
                        offers = details.offerSnapshots(),
                        infiniteRecurringMode =
                            ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                    )
                }

                selectedPurchaseOffersByPeriod.clear()

                if (monthlyDetails != null && monthlyPlan != null) {
                    selectedPurchaseOffersByPeriod[BillingPeriod.Monthly] =
                        SelectedPurchaseOffer(
                            productDetails = monthlyDetails,
                            plan = monthlyPlan,
                        )
                }

                if (yearlyDetails != null && yearlyPlan != null) {
                    selectedPurchaseOffersByPeriod[BillingPeriod.Yearly] =
                        SelectedPurchaseOffer(
                            productDetails = yearlyDetails,
                            plan = yearlyPlan,
                        )
                }

                _subscriptionCatalogState.value =
                    if (monthlyPlan == null && yearlyPlan == null) {
                        _billingUiState.value = BillingUiState.ProductUnavailable
                        SubscriptionCatalogState.Unavailable
                    } else {
                        _billingUiState.value = BillingUiState.Ready
                        SubscriptionCatalogState.Ready(
                            monthly = monthlyPlan,
                            yearly = yearlyPlan,
                        )
                    }
            } else {
                selectedPurchaseOffersByPeriod.clear()
                _subscriptionCatalogState.value = SubscriptionCatalogState.Unavailable
                _billingUiState.value =
                    billingFailureStateForResponseCode(result.responseCode)
                        ?: BillingUiState.Error(
                            responseCode = result.responseCode,
                            retryable = false,
                        )

                if (
                    result.responseCode ==
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                ) {
                    scheduleReconnectAfterDisconnect()
                }

                Log.w(
                    Tag,
                    billingResultFailureMessage(
                        operation = "Subscription product query",
                        result = result,
                    ),
                )
            }
        }
    }

    private fun ProductDetails.offerSnapshots(): List<SubscriptionOfferSnapshot> {
        return subscriptionOfferDetails.orEmpty().map { offer ->
            SubscriptionOfferSnapshot(
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                offerToken = offer.offerToken,
                offerTags = offer.offerTags,
                pricingPhases = offer.pricingPhases.pricingPhaseList.map { phase ->
                    SubscriptionPricingPhaseSnapshot(
                        formattedPrice = phase.formattedPrice,
                        priceAmountMicros = phase.priceAmountMicros,
                        billingPeriod = phase.billingPeriod,
                        recurrenceMode = phase.recurrenceMode,
                        billingCycleCount = phase.billingCycleCount,
                    )
                },
            )
        }
    }

    fun refreshProductDetails() {
        if (billingClient.isReady) {
            queryProduct()
        } else {
            _subscriptionCatalogState.value = SubscriptionCatalogState.Loading
            _billingUiState.value = BillingUiState.Connecting
            connect()
        }
    }

    fun launchPurchase(activity: Activity, period: BillingPeriod) {
        if (!billingClient.isReady) {
            _billingUiState.value = BillingUiState.Connecting
            connect()
            return
        }

        val selectedOffer = selectedPurchaseOffersByPeriod[period]
        if (selectedOffer == null) {
            _billingUiState.value = BillingUiState.ProductUnavailable
            return
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        if (firebaseUser == null || firebaseUser.isAnonymous) {
            Log.w(
                Tag,
                "Blocked Plus purchase launch because a durable account is required.",
            )
            return
        }

        val hasDurableProvider = firebaseUser.providerData.any { provider ->
            provider.providerId == GoogleAuthProvider.PROVIDER_ID ||
                provider.providerId == FacebookAuthProvider.PROVIDER_ID ||
                provider.providerId == EmailAuthProvider.PROVIDER_ID
        }

        if (!hasDurableProvider) {
            Log.w(
                Tag,
                "Blocked Plus purchase launch because no supported durable provider is linked.",
            )
            return
        }

        val obfuscatedAccountId = obfuscatedPlayBillingAccountId(firebaseUser.uid)
        val productId = selectedOffer.productDetails.productId

        // Already own exactly this period: nothing to buy. Prevents a
        // no-op upgrade flow and any chance of a duplicate charge.
        if (ownedProductId == productId && ownedPurchaseToken != null) {
            return
        }

        _billingUiState.value = BillingUiState.PurchaseLaunching(period)
        val builder = BillingFlowParams.newBuilder()
            .setObfuscatedAccountId(obfuscatedAccountId)
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(selectedOffer.productDetails)
                        .setOfferToken(selectedOffer.plan.offerToken)
                        .build(),
                ),
            )

        // If a different Plus period is already owned, tell Play to replace it
        // so the user is upgraded/downgraded with proration, not charged for a
        // second concurrent subscription. CHARGE_PRORATED_PRICE credits the
        // unused time of the current period toward the new one.
        val existingToken = ownedPurchaseToken
        if (existingToken != null && ownedProductId != productId) {
            builder.setSubscriptionUpdateParams(
                SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(existingToken)
                    .setSubscriptionReplacementMode(
                        SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE,
                    )
                    .build(),
            )
        }

        val launchResult = billingClient.launchBillingFlow(activity, builder.build())
        if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
            return
        }

        handlePurchaseFlowFailure(launchResult)
    }

    private fun handlePurchaseFlowFailure(result: BillingResult) {
        val mapped =
            billingFailureStateForResponseCode(result.responseCode)
                ?: BillingUiState.Error(
                    responseCode = result.responseCode,
                    retryable = false,
                )

        _billingUiState.value = mapped

        Log.w(
            Tag,
            billingResultFailureMessage(
                operation = "Purchase flow",
                result = result,
            ),
        )

        when (result.responseCode) {
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
                startEntitlementRefresh()
            }

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                scheduleReconnectAfterDisconnect()
        }
    }

    fun refreshPurchases() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.i(Tag, "Skipped Play purchase refresh because no authenticated user is available.")
            return
        }

        if (!billingClient.isReady) {
            connect()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(Tag, "Play purchase refresh returned ${purchases.size} purchase(s).")
                if (purchases.isNotEmpty()) {
                    handlePurchases(purchases)
                }
            } else {
                billingFailureStateForResponseCode(result.responseCode)?.let { failureState ->
                    _billingUiState.value = failureState
                }

                if (
                    result.responseCode ==
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                ) {
                    scheduleReconnectAfterDisconnect()
                }

                Log.w(
                    Tag,
                    billingResultFailureMessage(
                        operation = "Play purchase refresh",
                        result = result,
                    ),
                )
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases.isNullOrEmpty()) {
                _billingUiState.value = BillingUiState.Error(
                    responseCode = BillingClient.BillingResponseCode.ERROR,
                    retryable = true,
                )
                Log.w(Tag, "Play Billing returned OK without purchase data.")
                return
            }

            if (FirebaseAuth.getInstance().currentUser == null) {
                _billingUiState.value = BillingUiState.VerificationDeferred
                Log.w(
                    Tag,
                    "Purchase completed before authentication was available; verification deferred.",
                )
                return
            }

            handlePurchases(purchases)
        } else {
            handlePurchaseFlowFailure(result)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val activePlus = purchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it == PlusProductId || it == PlusYearlyProductId }
        }
        // Remember the owned subscription so a later period switch can be sent
        // to Play as an upgrade/downgrade rather than a second purchase. When
        // nothing is owned, clear it so we never send a stale token.
        val current = activePlus.firstOrNull()
        ownedPurchaseToken = current?.purchaseToken
        ownedProductId = current?.products?.firstOrNull {
            it == PlusProductId || it == PlusYearlyProductId
        }

        val plusPurchases = purchases.filter { purchase ->
            purchase.products.any { productId ->
                productId == PlusProductId || productId == PlusYearlyProductId
            }
        }

        val purchased = plusPurchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (purchased.isNotEmpty()) {
            _billingUiState.value = BillingUiState.PurchasedAndVerifying
            verifyAndGrantPurchases(purchased)
            return
        }

        val hasPending = plusPurchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        if (hasPending) {
            _billingUiState.value = BillingUiState.Pending
            Log.i(Tag, "Plus purchase is pending; entitlement remains locked.")
            return
        }

        Log.i(Tag, "No grantable Plus purchase was returned.")
    }

    private fun verifyAndGrantPurchases(purchases: List<Purchase>) {
        scope.launch {
            var attemptedVerification = false
            var verifiedAny = false

            for (purchase in purchases) {
                if (!verifyingPurchaseTokens.add(purchase.purchaseToken)) {
                    continue
                }

                attemptedVerification = true

                try {
                    val productId = purchase.products.firstOrNull { candidate ->
                        candidate == PlusProductId || candidate == PlusYearlyProductId
                    } ?: continue

                    val verified = verifyPurchaseWithBackend(
                        purchase = purchase,
                        expectedProductId = productId,
                    )

                    if (verified != null) {
                        grantEntitlement(
                            productId = verified.productId,
                            expiryTimeMillis = verified.expiryTimeMillis,
                        )
                        verifiedAny = true
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Log.w(
                        Tag,
                        backendVerificationFailureMessage(throwable),
                    )
                } finally {
                    verifyingPurchaseTokens.remove(purchase.purchaseToken)
                }
            }

            when {
                verifiedAny -> {
                    if (
                        billingClient.isReady &&
                        selectedPurchaseOffersByPeriod.isNotEmpty()
                    ) {
                        _billingUiState.value = BillingUiState.Ready
                    }
                }

                attemptedVerification ->
                    _billingUiState.value = BillingUiState.VerificationFailed

                else -> Unit
            }
        }
    }

    private suspend fun verifyPurchaseWithBackend(
        purchase: Purchase,
        expectedProductId: String,
    ): VerifiedPurchase? {
        val result = functions
            .getHttpsCallable(VerifyPlusSubscriptionFunction)
            .call(
                mapOf(
                    "productId" to expectedProductId,
                    "purchaseToken" to purchase.purchaseToken,
                ),
            )
            .await()

        val data = result.getData() as? Map<*, *> ?: return null
        val active = data["active"] as? Boolean ?: false
        val productId = data["productId"] as? String
        val expiryTimeMillis = (data["expiryTimeMillis"] as? Number)?.toLong() ?: 0L

        return if (active && productId == expectedProductId && expiryTimeMillis > 0L) {
            VerifiedPurchase(productId, expiryTimeMillis)
        } else {
            null
        }
    }

    private suspend fun grantEntitlement(productId: String, expiryTimeMillis: Long) {
        val period = if (productId == PlusYearlyProductId) {
            BillingPeriod.Yearly
        } else {
            BillingPeriod.Monthly
        }
        repository.setEntitlement(
            PremiumEntitlement(
                tier = PremiumTier.Basic,
                period = period,
                source = EntitlementSource.PlayBilling,
                expiryTimeMillis = expiryTimeMillis,
                lastVerifiedMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun refreshEntitlementFromServerOnce(): EntitlementRefreshOutcome {
        if (
            !shouldAttemptProtectedEntitlementRefresh(
                hasAuthenticatedUser = FirebaseAuth.getInstance().currentUser != null,
            )
        ) {
            Log.i(
                Tag,
                "Skipped server entitlement refresh because no authenticated user is available.",
            )

            return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
        }

        val nowMillis = System.currentTimeMillis()

        val gatedCall = try {
            runAfterAppCheckReadiness {
                functions
                    .getHttpsCallable(CheckPlusEntitlementFunction)
                    .call()
                    .await()
                    .getData()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.w(
                Tag,
                "Server entitlement refresh failed after App Check readiness; retry may follow " +
                    "(exception=${throwable.javaClass.simpleName}).",
            )

            return EntitlementRefreshOutcome.RetryableFailure
        }

        val data = when (gatedCall) {
            is AppCheckGatedCallResult.Executed -> gatedCall.value
            is AppCheckGatedCallResult.TemporarilyUnavailable -> {
                Log.w(
                    Tag,
                    appCheckReadinessFailureLogMessage(gatedCall.cause),
                )

                return EntitlementRefreshOutcome.AppCheckTemporarilyUnavailable
            }
        }

        return when (
            val resolution = resolveServerEntitlementResponse(
                data = data,
                nowMillis = nowMillis,
            )
        ) {
            is ServerEntitlementResolution.Active -> {
                repository.setEntitlement(
                    PremiumEntitlement(
                        tier = PremiumTier.Basic,
                        period = resolution.period,
                        source = EntitlementSource.PlayBilling,
                        expiryTimeMillis = resolution.expiryTimeMillis,
                        lastVerifiedMillis = nowMillis,
                    ),
                )

                Log.i(Tag, "Server entitlement refresh confirmed active Plus access.")

                EntitlementRefreshOutcome.AppliedActive
            }

            is ServerEntitlementResolution.Inactive -> {
                val cached = repository.entitlement.first()

                if (cached.source == EntitlementSource.PlayBilling) {
                    repository.setEntitlement(
                        PremiumEntitlement(
                            tier = PremiumTier.Free,
                            source = EntitlementSource.PlayBilling,
                            lastVerifiedMillis = nowMillis,
                        ),
                    )
                }

                Log.i(
                    Tag,
                    "Server reported Plus inactive; Play entitlement downgraded immediately.",
                )

                EntitlementRefreshOutcome.AppliedInactive
            }

            ServerEntitlementResolution.RetryableFailure -> {
                Log.w(
                    Tag,
                    "Server entitlement response could not be safely applied; retry may follow.",
                )

                EntitlementRefreshOutcome.RetryableFailure
            }
        }
    }

    private suspend fun enforceOfflineGraceAfterFailedRefresh() {
        val cached = repository.entitlement.first()

        if (
            cached.source != EntitlementSource.PlayBilling ||
            cached.tier == PremiumTier.Free
        ) {
            return
        }

        val nowMillis = System.currentTimeMillis()

        val stillLocallyValid = cached.isValidAt(
            nowMillis = nowMillis,
            allowDebugEntitlement = false,
        )

        if (stillLocallyValid) {
            Log.w(
                Tag,
                "Server entitlement refresh exhausted retries; cached Play access remains inside the bounded offline window.",
            )

            return
        }

        repository.setEntitlement(
            PremiumEntitlement(
                tier = PremiumTier.Free,
                source = EntitlementSource.PlayBilling,
                lastVerifiedMillis = cached.lastVerifiedMillis,
            ),
        )

        Log.w(
            Tag,
            "Server entitlement refresh exhausted retries and cached Play access exceeded the offline window; downgraded to Free.",
        )
    }

    fun release() {
        released.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        managerJob.cancel()
        billingClient.endConnection()
        connectionInProgress.set(false)
        _connected.value = false
        entitlementRefreshInFlight.set(false)
        restorePendingAfterConnection.set(false)
        _restoreState.value = BillingRestoreState.Idle
        selectedPurchaseOffersByPeriod.clear()
        verifyingPurchaseTokens.clear()
    }

    companion object {
        private const val Tag = "BillingManager"
        private const val FunctionsRegion = "us-central1"
        private const val VerifyPlusSubscriptionFunction = "verifyPlusSubscription"
        private const val CheckPlusEntitlementFunction = "checkPlusEntitlement"

        // Must match the subscription product IDs created in Google Play Console.
        const val PlusProductId = "impulsive_plus_monthly"
        const val PlusYearlyProductId = "impulsive_plus_yearly"
    }

    private data class VerifiedPurchase(
        val productId: String,
        val expiryTimeMillis: Long,
    )

    private data class SelectedPurchaseOffer(
        val productDetails: ProductDetails,
        val plan: SelectedSubscriptionPlan,
    )
}
