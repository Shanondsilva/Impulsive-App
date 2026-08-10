package com.impulsive.app.backend.service.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.data.repository.SafeBrowsePassRepository
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import com.impulsive.app.backend.domain.model.premium.isValidAt
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt as isSafeBrowsePassEntitlementValidAt
import com.impulsive.app.backend.service.firebase.AppCheckGatedCallResult
import com.impulsive.app.backend.service.firebase.appCheckReadinessFailureLogMessage
import com.impulsive.app.backend.service.firebase.runAfterAppCheckReadiness
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val safeBrowsePassRepository = SafeBrowsePassRepository(
        context = appContext,
        billingManagerProvider = {
            this@BillingManager
        },
    )
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

    // Same bookkeeping as above, kept entirely separate for the Safe Browse Pass
    // product family so a Plus purchase can never be mistaken for a Pass purchase
    // (or vice versa) and neither family's replacement/upgrade logic ever reads
    // the other's owned token.
    @Volatile
    private var ownedSafeBrowsePassPurchaseToken: String? = null

    @Volatile
    private var ownedSafeBrowsePassProductId: String? = null

    // Which family's launchBillingFlow() call is currently outstanding, so a
    // PurchasesUpdatedListener failure (cancellation, decline, etc.) is routed only to the
    // UI state of the flow that actually launched -- never to both.
    @Volatile
    private var pendingPurchaseFamily: PurchaseFamily? = null

    private val verifyingPurchaseTokens = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>(),
    )
    private val verifyingSafeBrowsePassPurchaseTokens = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>(),
    )
    private val connectionInProgress = AtomicBoolean(false)
    private val entitlementRefreshInFlight = AtomicBoolean(false)
    private val safeBrowsePassEntitlementRefreshInFlight = AtomicBoolean(false)
    private val safeBrowsePassEntitlementRefreshPending = AtomicBoolean(false)
    private val authenticationMutex = Mutex()
    private val authenticationGeneration = AtomicLong(0L)
    @Volatile
    private var lastAuthenticatedSafeBrowsePassUid: String? = null
    private val restorePendingAfterConnection = AtomicBoolean(false)
    private val safeBrowsePassRestorePendingAfterConnection = AtomicBoolean(false)
    private val restoreOperationMutex = Mutex()
    private val released = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val plusPurchaseLaunchInFlight = AtomicBoolean(false)
    private val safeBrowsePassPurchaseLaunchInFlight = AtomicBoolean(false)

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                // Required for the Safe Browse Pass prepaid top-up plan alongside
                // Impulsive Plus's auto-renewing subscriptions on this one BillingClient.
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    // Immutable plan snapshots only -- never a live ProductDetails object. These maps back
    // catalogue presentation availability alone; every purchase launch re-queries
    // ProductDetails fresh instead of reading from here (see querySubscriptionProducts).
    private val selectedPurchasePlansByPeriod =
        ConcurrentHashMap<BillingPeriod, SelectedSubscriptionPlan>()
    private val selectedSafeBrowsePassPlansByPeriod =
        ConcurrentHashMap<SafeBrowsePassPeriod, SelectedSafeBrowsePassPlan>()

    private val _subscriptionCatalogState =
        MutableStateFlow<SubscriptionCatalogState>(SubscriptionCatalogState.Loading)
    val subscriptionCatalogState: StateFlow<SubscriptionCatalogState> =
        _subscriptionCatalogState.asStateFlow()

    private val _safeBrowsePassCatalogState =
        MutableStateFlow<SafeBrowsePassCatalogState>(SafeBrowsePassCatalogState.Loading)
    val safeBrowsePassCatalogState: StateFlow<SafeBrowsePassCatalogState> =
        _safeBrowsePassCatalogState.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _restoreState = MutableStateFlow<BillingRestoreState>(BillingRestoreState.Idle)
    val restoreState: StateFlow<BillingRestoreState> = _restoreState.asStateFlow()

    private val _safeBrowsePassRestoreState =
        MutableStateFlow<SafeBrowsePassRestoreState>(SafeBrowsePassRestoreState.Idle)
    val safeBrowsePassRestoreState: StateFlow<SafeBrowsePassRestoreState> =
        _safeBrowsePassRestoreState.asStateFlow()

    private val _safeBrowsePassPendingKind =
        MutableStateFlow<SafeBrowsePassPendingKind?>(null)
    internal val safeBrowsePassPendingKind: StateFlow<SafeBrowsePassPendingKind?> =
        _safeBrowsePassPendingKind.asStateFlow()

    private val safeBrowsePassSnapshotRevisions =
        SafeBrowsePassSnapshotRevisionTracker<
            SafeBrowsePassPlaySnapshotKey
        >()

    private val _billingUiState = MutableStateFlow<BillingUiState>(BillingUiState.Connecting)
    val billingUiState: StateFlow<BillingUiState> = _billingUiState.asStateFlow()

    private val _safeBrowsePassBillingUiState =
        MutableStateFlow<SafeBrowsePassBillingUiState>(SafeBrowsePassBillingUiState.Connecting)
    val safeBrowsePassBillingUiState: StateFlow<SafeBrowsePassBillingUiState> =
        _safeBrowsePassBillingUiState.asStateFlow()


    internal fun safeBrowsePassRepositoryForViewModel(): SafeBrowsePassRepository =
        safeBrowsePassRepository

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
                    selectedPurchasePlansByPeriod.clear()
                    selectedSafeBrowsePassPlansByPeriod.clear()
                    _subscriptionCatalogState.value = SubscriptionCatalogState.Unavailable
                    _safeBrowsePassCatalogState.value = SafeBrowsePassCatalogState.Unavailable
                    _billingUiState.value =
                        billingFailureStateForResponseCode(result.responseCode)
                            ?: BillingUiState.Error(
                                responseCode = result.responseCode,
                                retryable = false,
                            )
                    if (restorePendingAfterConnection.getAndSet(false)) {
                        _restoreState.value = BillingRestoreState.Error
                    }
                    if (safeBrowsePassRestorePendingAfterConnection.getAndSet(false)) {
                        _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Error(
                            "Safe Browse Pass could not be restored. Please try again.",
                        )
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
                if (safeBrowsePassRestorePendingAfterConnection.getAndSet(false)) {
                    _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Error(
                        "Safe Browse Pass could not be restored. Please try again.",
                    )
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

        val pendingPlusRestore = restorePendingAfterConnection.getAndSet(false)
        val pendingPassRestore = safeBrowsePassRestorePendingAfterConnection.getAndSet(false)

        if (currentNonAnonymousFirebaseUid() != null) {
            if (pendingPlusRestore) {
                performRestorePurchases()
            }
            if (pendingPassRestore) {
                performSafeBrowsePassRestore()
            }
            if (!pendingPlusRestore && !pendingPassRestore) {
                refreshAfterAuthentication()
            }
        } else {
            if (pendingPlusRestore) {
                _restoreState.value = BillingRestoreState.Error
            }
            if (pendingPassRestore) {
                _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Error(
                    "Connect an account before restoring Safe Browse Pass.",
                )
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
    private fun currentNonAnonymousFirebaseUid(): String? =
        FirebaseAuth.getInstance().currentUser
            ?.takeUnless { user -> user.isAnonymous }
            ?.uid
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun clearAccountScopedBillingMemory() {
        ownedPurchaseToken = null
        ownedProductId = null

        ownedSafeBrowsePassPurchaseToken = null
        ownedSafeBrowsePassProductId = null

        verifyingPurchaseTokens.clear()
        verifyingSafeBrowsePassPurchaseTokens.clear()

        pendingPurchaseFamily = null
        restorePendingAfterConnection.set(false)
        safeBrowsePassRestorePendingAfterConnection.set(false)
        _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Idle

        safeBrowsePassSnapshotRevisions
            .invalidate {
                _safeBrowsePassPendingKind
                    .value =
                    null
            }

        plusPurchaseLaunchInFlight.set(false)
        safeBrowsePassPurchaseLaunchInFlight.set(false)

        safeBrowsePassEntitlementRefreshPending.set(false)
    }

    fun onAuthenticatedUserAvailable() {
        onAuthenticationStateChanged(currentNonAnonymousFirebaseUid())
    }

    fun onAuthenticationStateChanged(
        currentAuthenticatedUid: String?,
    ) {
        val normalisedUid =
            currentAuthenticatedUid
                ?.trim()
                ?.takeIf(String::isNotBlank)

        val generation =
            authenticationGeneration.incrementAndGet()

        scope.launch {
            authenticationMutex.withLock {
                if (
                    generation !=
                    authenticationGeneration.get()
                ) {
                    return@withLock
                }

                if (
                    currentNonAnonymousFirebaseUid() !=
                    normalisedUid
                ) {
                    return@withLock
                }

                val accountChanged =
                    normalisedUid !=
                        lastAuthenticatedSafeBrowsePassUid

                if (accountChanged) {
                    clearAccountScopedBillingMemory()
                }

                try {
                    safeBrowsePassRepository
                        .onAccountChanged(
                            normalisedUid,
                        )
                } catch (
                    cancellation: CancellationException
                ) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Log.w(
                        Tag,
                        "Safe Browse Pass account reconciliation failed.",
                    )

                    if (
                        generation ==
                            authenticationGeneration.get() &&
                        currentNonAnonymousFirebaseUid() ==
                            normalisedUid
                    ) {
                        _safeBrowsePassBillingUiState.value =
                            SafeBrowsePassBillingUiState
                                .VerificationDeferred
                    }

                    return@withLock
                }

                if (
                    generation !=
                    authenticationGeneration.get()
                ) {
                    return@withLock
                }

                if (
                    currentNonAnonymousFirebaseUid() !=
                    normalisedUid
                ) {
                    return@withLock
                }

                lastAuthenticatedSafeBrowsePassUid =
                    normalisedUid

                if (normalisedUid == null) {
                    safeBrowsePassEntitlementRefreshPending
                        .set(false)

                    _safeBrowsePassBillingUiState.value =
                        SafeBrowsePassBillingUiState
                            .VerificationDeferred

                    return@withLock
                }

                if (billingClient.isReady) {
                    refreshAfterAuthentication()
                } else {
                    connect()
                }
            }
        }
    }
    fun onAppForegrounded() {
        if (currentNonAnonymousFirebaseUid() == null) {
            return
        }

        if (billingClient.isReady) {
            refreshPurchases()
        } else {
            connect()
        }

        startEntitlementRefresh()
        startSafeBrowsePassEntitlementRefresh()
    }

    private fun refreshAfterAuthentication() {
        Log.i(Tag, "Starting authenticated billing entitlement refresh.")
        refreshPurchases()
        startEntitlementRefresh()
        startSafeBrowsePassEntitlementRefresh()
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

    private fun startSafeBrowsePassEntitlementRefresh() {
        val expectedUid = currentNonAnonymousFirebaseUid() ?: return

        if (!safeBrowsePassEntitlementRefreshInFlight.compareAndSet(false, true)) {
            safeBrowsePassEntitlementRefreshPending.set(true)
            return
        }

        scope.launch {
            try {
                refreshSafeBrowsePassEntitlementWithRetry(expectedUid)
            } finally {
                safeBrowsePassEntitlementRefreshInFlight.set(false)
                if (
                    safeBrowsePassEntitlementRefreshPending.getAndSet(false) &&
                    !released.get() &&
                    currentNonAnonymousFirebaseUid() != null
                ) {
                    startSafeBrowsePassEntitlementRefresh()
                }
            }
        }
    }

    private suspend fun refreshSafeBrowsePassEntitlementWithRetry(
        expectedUid: String,
    ): ServerEntitlementRefreshResult {
        var failureIndex = 0

        while (true) {
            val outcome = refreshSafeBrowsePassEntitlementFromServerOnce(expectedUid)
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

                else -> error("Unhandled Safe Browse Pass entitlement refresh outcome.")
            }
        }

        enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh(expectedUid)
        return ServerEntitlementRefreshResult.Unavailable
    }

    private suspend fun refreshSafeBrowsePassEntitlementFromServerOnce(
        expectedUid: String,
    ): EntitlementRefreshOutcome {
        val normalisedExpectedUid =
            expectedUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser

        if (currentNonAnonymousFirebaseUid() != normalisedExpectedUid) {
            return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
        }

        if (
            !shouldAttemptProtectedEntitlementRefresh(
                hasAuthenticatedUser = true,
            )
        ) {
            return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
        }

        val nowMillis = System.currentTimeMillis()

        val gatedCall = try {
            runAfterAppCheckReadiness {
                functions
                    .getHttpsCallable(CheckSafeBrowsePassEntitlementFunction)
                    .call()
                    .await()
                    .getData()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.w(
                Tag,
                "Safe Browse Pass server entitlement refresh failed after App Check readiness; retry may follow (exception=${throwable.javaClass.simpleName}).",
            )

            return EntitlementRefreshOutcome.RetryableFailure
        }

        if (currentNonAnonymousFirebaseUid() != normalisedExpectedUid) {
            Log.w(
                Tag,
                "Ignored stale Safe Browse Pass result after authentication changed.",
            )
            return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
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
            val resolution = resolveSafeBrowsePassEntitlementResponse(
                data = data,
                nowMillis = nowMillis,
            )
        ) {
            is SafeBrowsePassEntitlementResolution.Active -> {
                val persisted = safeBrowsePassRepository.setVerifiedEntitlement(
                    expectedUid = normalisedExpectedUid,
                    entitlement = SafeBrowsePassEntitlement(
                        active = true,
                        productId = resolution.productId,
                        basePlanId = resolution.basePlanId,
                        expiryTimeMillis = resolution.expiryTimeMillis,
                        isPrepaid = resolution.isPrepaid,
                        renewalState =
                            resolveSafeBrowsePassRenewalState(
                                isPrepaid =
                                    resolution.isPrepaid,
                                subscriptionState =
                                    resolution.subscriptionState,
                            ),
                        lastVerifiedMillis = nowMillis,
                    ),
                )

                if (!persisted) {
                    Log.w(
                        Tag,
                        "Ignored stale active Safe Browse Pass result after authentication changed.",
                    )
                    return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
                }

                Log.i(Tag, "Server entitlement refresh confirmed active Safe Browse Pass access.")

                EntitlementRefreshOutcome.AppliedActive
            }

            is SafeBrowsePassEntitlementResolution.Inactive -> {
                val cached = safeBrowsePassRepository.currentEntitlement()

                if (cached.active) {
                    val inactiveEntitlement =
                        cached.copy(
                            active = false,
                            renewalState =
                                if (cached.isPrepaid) {
                                    SafeBrowsePassRenewalState
                                        .NotApplicable
                                } else {
                                    resolveSafeBrowsePassRenewalState(
                                        isPrepaid = false,
                                        subscriptionState =
                                            resolution
                                                .subscriptionState,
                                    )
                                },
                            lastVerifiedMillis =
                                nowMillis,
                        )

                    val cleared = safeBrowsePassRepository.setVerifiedEntitlement(
                        expectedUid = normalisedExpectedUid,
                        entitlement = inactiveEntitlement,
                    )

                    if (!cleared) {
                        Log.w(
                            Tag,
                            "Ignored stale inactive Safe Browse Pass result after authentication changed.",
                        )
                        return EntitlementRefreshOutcome.SkippedNoAuthenticatedUser
                    }
                }

                Log.i(
                    Tag,
                    "Server reported Safe Browse Pass inactive; entitlement downgraded immediately.",
                )

                EntitlementRefreshOutcome.AppliedInactive
            }

            SafeBrowsePassEntitlementResolution.RetryableFailure -> {
                Log.w(
                    Tag,
                    "Safe Browse Pass server entitlement response could not be safely applied; retry may follow.",
                )

                EntitlementRefreshOutcome.RetryableFailure
            }
        }
    }

    private suspend fun enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh(
        expectedUid: String,
    ) {
        val normalisedExpectedUid =
            expectedUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: return

        if (currentNonAnonymousFirebaseUid() != normalisedExpectedUid) {
            return
        }

        val cached = safeBrowsePassRepository.currentEntitlement()

        if (!cached.active) {
            return
        }

        val nowMillis = System.currentTimeMillis()

        if (cached.isSafeBrowsePassEntitlementValidAt(nowMillis)) {
            Log.w(
                Tag,
                "Safe Browse Pass refresh exhausted retries; cached access remains valid only until its verified expiry.",
            )

            return
        }

        val cleared = safeBrowsePassRepository.setVerifiedEntitlement(
            expectedUid = normalisedExpectedUid,
            entitlement = cached.copy(
                active = false,
                lastVerifiedMillis = nowMillis,
            ),
        )

        if (!cleared) {
            Log.w(
                Tag,
                "Ignored stale Safe Browse Pass expiry result after authentication changed.",
            )
            return
        }

        Log.w(
            Tag,
            "Safe Browse Pass refresh exhausted retries and its verified expiry was reached; access was downgraded to inactive.",
        )
    }

    fun restorePurchases() {
        if (_restoreState.value == BillingRestoreState.Loading) {
            return
        }

        if (currentNonAnonymousFirebaseUid() == null) {
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
            val restoreResult =
                restoreOperationMutex.withLock {
                    try {
                        restorePurchasesInternal()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        Log.w(Tag, "Restore purchases failed before reconciliation completed.")
                        BillingRestoreState.Error
                    }
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

    fun restoreSafeBrowsePassPurchases() {
        if (_safeBrowsePassRestoreState.value == SafeBrowsePassRestoreState.Restoring) {
            return
        }

        if (currentNonAnonymousFirebaseUid() == null) {
            _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Error(
                "Connect an account before restoring Safe Browse Pass.",
            )
            return
        }

        _safeBrowsePassRestoreState.value = SafeBrowsePassRestoreState.Restoring

        if (!billingClient.isReady) {
            safeBrowsePassRestorePendingAfterConnection.set(true)
            connect()
            return
        }

        performSafeBrowsePassRestore()
    }

    private fun performSafeBrowsePassRestore() {
        scope.launch {
            val result =
                restoreOperationMutex.withLock {
                    runCatching {
                        restoreSafeBrowsePassPurchasesInternal()
                    }.getOrElse { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }

                        Log.w(
                            Tag,
                            "Safe Browse Pass restore failed before reconciliation completed.",
                        )

                        SafeBrowsePassRestoreState.Error(
                            "Safe Browse Pass could not be restored. Please try again.",
                        )
                    }
                }

            _safeBrowsePassRestoreState.value = result
        }
    }

    private sealed interface OwnedSubscriptionsQueryResult {
        data class Success(
            val purchases: List<Purchase>,
        ) : OwnedSubscriptionsQueryResult

        data class Failure(
            val responseCode: Int?,
        ) : OwnedSubscriptionsQueryResult
    }

    private suspend fun queryOwnedSubscriptions(): OwnedSubscriptionsQueryResult {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val queryDeferred = CompletableDeferred<Pair<BillingResult, List<Purchase>>>()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            queryDeferred.complete(result to purchases)
        }

        val queryResult = withTimeoutOrNull(15_000L) {
            queryDeferred.await()
        } ?: return OwnedSubscriptionsQueryResult.Failure(null)

        if (queryResult.first.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                Tag,
                billingResultFailureMessage(
                    operation = "Restore purchase query",
                    result = queryResult.first,
                ),
            )
            return OwnedSubscriptionsQueryResult.Failure(queryResult.first.responseCode)
        }

        return OwnedSubscriptionsQueryResult.Success(queryResult.second)
    }

    private suspend fun restorePurchasesInternal(): BillingRestoreState {
        val queryResult = queryOwnedSubscriptions()

        if (queryResult is OwnedSubscriptionsQueryResult.Failure) {
            if (queryResult.responseCode == null) {
                Log.w(Tag, "Restore purchase query timed out before reconciliation completed.")
            }
            val serverRefreshResult = refreshEntitlementWithRetry()
            return resolveBillingRestoreState(
                playQuerySucceeded = false,
                verifiedActivePurchaseCount = 0,
                verificationFailed = false,
                serverRefreshResult = serverRefreshResult,
            )
        }

        val purchases = (queryResult as OwnedSubscriptionsQueryResult.Success).purchases
        val eligiblePurchases = purchases.filter { purchase ->
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
            } catch (cancellation: CancellationException) {
                throw cancellation
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

    private suspend fun restoreSafeBrowsePassPurchasesInternal(): SafeBrowsePassRestoreState {
        val expectedUid = currentNonAnonymousFirebaseUid()
            ?: return SafeBrowsePassRestoreState.Error(
                "Connect an account before restoring Safe Browse Pass.",
            )

        val queryResult = queryOwnedSubscriptions()

        if (queryResult is OwnedSubscriptionsQueryResult.Failure) {
            val serverRefreshResult = refreshSafeBrowsePassEntitlementWithRetry(expectedUid)
            return resolveSafeBrowsePassRestoreState(
                SafeBrowsePassRestoreEvidence(
                    playQuerySucceeded = false,
                    verifiedActivePurchaseCount = 0,
                    verificationFailed = false,
                    serverRefreshResult = serverRefreshResult,
                ),
            )
        }

        val purchases = (queryResult as OwnedSubscriptionsQueryResult.Success).purchases

        val passPurchases = purchases.filter { purchase ->
            purchase.products.any { productId -> productId == SafeBrowsePassProductId }
        }

        val pendingTopUpPurchases = passPurchases.filter { purchase ->
            purchase.hasPendingSafeBrowsePassUpdate()
        }

        val freshPendingPurchases = passPurchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PENDING &&
                !purchase.hasPendingSafeBrowsePassUpdate()
        }

        val purchasedPassPurchases = passPurchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        val playDecision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = pendingTopUpPurchases.isNotEmpty(),
            hasPendingInitialPurchase = freshPendingPurchases.isNotEmpty(),
            hasPurchasedPurchase = purchasedPassPurchases.isNotEmpty(),
        )

        val snapshotKey =
            safeBrowsePassPlaySnapshotKey(
                passPurchases,
            )

        val snapshotAcceptance =
            safeBrowsePassSnapshotRevisions
                .accept(
                    key = snapshotKey,
                ) {
                    _safeBrowsePassPendingKind
                        .value =
                        playDecision.pendingKind

                    _safeBrowsePassBillingUiState
                        .value =
                        playDecision.billingState
                }

        val purchasesToVerify = if (pendingTopUpPurchases.isNotEmpty()) {
            pendingTopUpPurchases.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        } else {
            purchasedPassPurchases
        }

        val summary = verifyAndApplySafeBrowsePassPurchases(
            purchases = purchasesToVerify,
            expectedUid = expectedUid,
            expectedSnapshotRevision = snapshotAcceptance.revision,
        )

        if (currentNonAnonymousFirebaseUid() != expectedUid || summary.accountChanged) {
            return SafeBrowsePassRestoreState.Error(
                "Safe Browse Pass could not be restored. Please try again.",
            )
        }

        if (summary.snapshotSuperseded) {
            Log.i(
                Tag,
                "Safe Browse Pass restore was superseded by a newer Google Play purchase snapshot.",
            )

            return SafeBrowsePassRestoreState.Idle
        }

        val serverRefreshResult = refreshSafeBrowsePassEntitlementWithRetry(expectedUid)

        if (currentNonAnonymousFirebaseUid() != expectedUid) {
            return SafeBrowsePassRestoreState.Error(
                "Safe Browse Pass could not be restored. Please try again.",
            )
        }

        if (
            !safeBrowsePassSnapshotRevisions
                .isCurrent(
                    snapshotAcceptance.revision,
                )
        ) {
            Log.i(
                Tag,
                "Safe Browse Pass restore result was superseded by a newer Google Play purchase snapshot.",
            )

            return SafeBrowsePassRestoreState.Idle
        }

        val restoreState = resolveSafeBrowsePassRestoreState(
            SafeBrowsePassRestoreEvidence(
                playQuerySucceeded = true,
                verifiedActivePurchaseCount = summary.grantedCount,
                verificationFailed = summary.verificationFailed,
                serverRefreshResult = serverRefreshResult,
            ),
        )

        val currentEntitlement = safeBrowsePassRepository.currentEntitlement()
        val entitlementActive = currentEntitlement.isSafeBrowsePassEntitlementValidAt(
            System.currentTimeMillis(),
        )

        val finalBillingState = resolveSafeBrowsePassBillingStateAfterRestore(
            pendingKind = playDecision.pendingKind,
            restoreState = restoreState,
            entitlementActive = entitlementActive,
        )

        val published =
            safeBrowsePassSnapshotRevisions
                .runIfCurrent(
                    snapshotAcceptance.revision,
                ) {
                    _safeBrowsePassPendingKind
                        .value =
                        playDecision.pendingKind

                    _safeBrowsePassBillingUiState
                        .value =
                        finalBillingState
                }

        if (!published) {
            Log.i(
                Tag,
                "Safe Browse Pass restore publication was superseded by a newer Google Play purchase snapshot.",
            )

            return SafeBrowsePassRestoreState.Idle
        }

        return restoreState
    }
    /**
     * The single shared entry point for every product-details query on this one
     * [billingClient] -- both the catalogue query in [queryProduct] and each individual
     * purchase-launch path's fresh re-query use this, never a second BillingClient and
     * never their own duplicated [QueryProductDetailsParams] construction.
     */
    private fun querySubscriptionProducts(
        productIds: Collection<String>,
        onResult: (BillingResult, QueryProductDetailsResult) -> Unit,
    ) {
        val products = productIds.distinct().map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            onResult(result, queryResult)
        }
    }

    private fun fetchedProductsById(
        queryResult: QueryProductDetailsResult,
    ): Map<String, ProductDetails> = queryResult.productDetailsList.associateBy { it.productId }

    /**
     * Logs only the product ID and unfetched status code -- never a purchase token, order
     * ID, Firebase UID or offer token.
     */
    private fun logUnfetchedProducts(operation: String, queryResult: QueryProductDetailsResult) {
        queryResult.unfetchedProductList.forEach { unfetched ->
            Log.w(
                Tag,
                "$operation could not fetch product ${unfetched.productId} " +
                    "(status=${unfetched.statusCode}).",
            )
        }
    }

    private fun queryProduct() {
        _subscriptionCatalogState.value = SubscriptionCatalogState.Loading
        _safeBrowsePassCatalogState.value = SafeBrowsePassCatalogState.Loading

        // One query, one BillingClient, all three products -- never a second
        // queryProductDetailsAsync call and never a second BillingClient instance.
        querySubscriptionProducts(
            listOf(PlusProductId, PlusYearlyProductId, SafeBrowsePassProductId),
        ) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logUnfetchedProducts("Subscription catalogue query", queryResult)

                // One missing product must never erase another valid product -- each is
                // resolved independently from this one query result.
                val fetched = fetchedProductsById(queryResult)

                val monthlyDetails = fetched[PlusProductId]
                val yearlyDetails = fetched[PlusYearlyProductId]

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

                selectedPurchasePlansByPeriod.clear()

                if (monthlyPlan != null) {
                    selectedPurchasePlansByPeriod[BillingPeriod.Monthly] = monthlyPlan
                }

                if (yearlyPlan != null) {
                    selectedPurchasePlansByPeriod[BillingPeriod.Yearly] = yearlyPlan
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

                val passDetails = fetched[SafeBrowsePassProductId]
                val passOffers = passDetails?.offerSnapshots().orEmpty()

                val passMonthly = passDetails?.let {
                    selectSafeBrowsePassMonthlyPlan(
                        productId = it.productId,
                        offers = passOffers,
                        infiniteRecurringMode = ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                    )
                }
                val passPrepaid = passDetails?.let {
                    selectSafeBrowsePassPrepaidPlan(
                        productId = it.productId,
                        offers = passOffers,
                        nonRecurringMode = ProductDetails.RecurrenceMode.NON_RECURRING,
                    )
                }

                selectedSafeBrowsePassPlansByPeriod.clear()

                if (passMonthly != null) {
                    selectedSafeBrowsePassPlansByPeriod[SafeBrowsePassPeriod.Monthly] = passMonthly
                }

                if (passPrepaid != null) {
                    selectedSafeBrowsePassPlansByPeriod[SafeBrowsePassPeriod.Prepaid] = passPrepaid
                }

                _safeBrowsePassCatalogState.value =
                    if (passMonthly == null && passPrepaid == null) {
                        SafeBrowsePassCatalogState.Unavailable
                    } else {
                        SafeBrowsePassCatalogState.Ready(
                            monthly = passMonthly,
                            prepaid = passPrepaid,
                        )
                    }
            } else {
                selectedPurchasePlansByPeriod.clear()
                selectedSafeBrowsePassPlansByPeriod.clear()
                _subscriptionCatalogState.value = SubscriptionCatalogState.Unavailable
                _safeBrowsePassCatalogState.value = SafeBrowsePassCatalogState.Unavailable
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
            _safeBrowsePassCatalogState.value = SafeBrowsePassCatalogState.Loading
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

        // The map is presentation availability only -- the actual purchase always launches
        // from a fresh ProductDetails query below, never from this cached plan.
        if (selectedPurchasePlansByPeriod[period] == null) {
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

        if (!plusPurchaseLaunchInFlight.compareAndSet(false, true)) {
            return
        }

        val targetProductId = when (period) {
            BillingPeriod.Monthly -> PlusProductId
            BillingPeriod.Yearly -> PlusYearlyProductId
        }

        if (ownedProductId == targetProductId && ownedPurchaseToken != null) {
            clearPurchaseLaunchInFlight(PurchaseFamily.Plus)
            _billingUiState.value = BillingUiState.AlreadyOwned
            return
        }

        val obfuscatedAccountId = obfuscatedPlayBillingAccountId(firebaseUser.uid)
        _billingUiState.value = BillingUiState.PurchaseLaunching(period)

        querySubscriptionProducts(listOf(targetProductId)) { result, queryResult ->
            logUnfetchedProducts("Plus purchase-launch product query", queryResult)

            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                clearPurchaseLaunchInFlight(PurchaseFamily.Plus)
                handlePurchaseFlowFailure(result)
                return@querySubscriptionProducts
            }

            val details = fetchedProductsById(queryResult)[targetProductId]

            val freshPlan = details?.let {
                when (period) {
                    BillingPeriod.Monthly ->
                        selectMonthlySubscriptionPlan(
                            productId = it.productId,
                            offers = it.offerSnapshots(),
                            infiniteRecurringMode = ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                        )

                    BillingPeriod.Yearly ->
                        selectYearlySubscriptionPlan(
                            productId = it.productId,
                            offers = it.offerSnapshots(),
                            infiniteRecurringMode = ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                        )
                }
            }

            if (details == null || freshPlan == null) {
                clearPurchaseLaunchInFlight(PurchaseFamily.Plus)
                _billingUiState.value = BillingUiState.ProductUnavailable
                return@querySubscriptionProducts
            }

            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) {
                    clearPurchaseLaunchInFlight(PurchaseFamily.Plus)
                    _billingUiState.value = BillingUiState.Ready
                    return@post
                }

                val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(freshPlan.offerToken)

                // If a different Plus product is already owned, tell Play to replace it so
                // the user is upgraded/downgraded with proration, not charged for a second
                // concurrent subscription. CHARGE_PRORATED_PRICE credits the unused time of
                // the current period toward the new one. Both the product-level replacement
                // (keyed by the old product ID) and the purchase-level old purchase token
                // are required together by the current Billing Library API -- the old
                // product ID alone does not replace the need for the old purchase token.
                val existingProductId = ownedProductId
                val existingPurchaseToken = ownedPurchaseToken
                val billingFlowBuilder = BillingFlowParams.newBuilder()
                    .setObfuscatedAccountId(obfuscatedAccountId)

                if (
                    existingProductId != null &&
                    existingPurchaseToken != null &&
                    existingProductId != targetProductId
                ) {
                    productDetailsParams.setSubscriptionProductReplacementParams(
                        SubscriptionProductReplacementParams.newBuilder()
                            .setOldProductId(existingProductId)
                            .setReplacementMode(
                                SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE,
                            )
                            .build(),
                    )
                    billingFlowBuilder.setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(existingPurchaseToken)
                            .build(),
                    )
                }

                val builder = billingFlowBuilder
                    .setProductDetailsParamsList(listOf(productDetailsParams.build()))

                pendingPurchaseFamily = PurchaseFamily.Plus
                val launchResult = billingClient.launchBillingFlow(activity, builder.build())
                if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Retained until onPurchasesUpdated reports the result.
                    return@post
                }

                clearPurchaseLaunchInFlight(PurchaseFamily.Plus)
                pendingPurchaseFamily = null
                handlePurchaseFlowFailure(launchResult)
            }
        }
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

    /**
     * Launches the Safe Browse Pass purchase flow on this same [billingClient] instance --
     * never a second BillingClient. Unlike [launchPurchase], a Pass purchase is never sent
     * to Play as a subscription-replacement of an existing Pass: Play does not support
     * replacing a prepaid plan the same way it does an auto-renewing one, so each Pass
     * purchase (monthly or prepaid) is always its own independent transaction.
     */
    /**
     * Launches the Safe Browse Pass purchase flow on this same [billingClient] instance --
     * never a second BillingClient. Never sent to Play as a subscription-replacement of
     * anything: an initial purchase and a verified same-base-plan prepaid top-up are each
     * their own independent transaction, gated by [resolveSafeBrowsePassPurchaseIntent]
     * against the latest locally cached, server-verified entitlement.
     */
    fun launchSafeBrowsePassPurchase(
        activity: Activity,
        period: SafeBrowsePassPeriod,
        expectedOfferToken: String? = null,
    ) {
        if (!billingClient.isReady) {
            safeBrowsePassSnapshotRevisions
                .invalidate {
                    _safeBrowsePassPendingKind
                        .value =
                        null

                    _safeBrowsePassBillingUiState
                        .value =
                        SafeBrowsePassBillingUiState
                            .Connecting
                }
            connect()
            return
        }

        if (selectedSafeBrowsePassPlansByPeriod[period] == null) {
            safeBrowsePassSnapshotRevisions
                .invalidate {
                    _safeBrowsePassPendingKind
                        .value =
                        null

                    _safeBrowsePassBillingUiState
                        .value =
                        SafeBrowsePassBillingUiState
                            .ProductUnavailable
                }
            return
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        if (firebaseUser == null || firebaseUser.isAnonymous) {
            Log.w(
                Tag,
                "Blocked Safe Browse Pass purchase launch because a durable account is required.",
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
                "Blocked Safe Browse Pass purchase launch because no supported durable " +
                    "provider is linked.",
            )
            return
        }

        if (!safeBrowsePassPurchaseLaunchInFlight.compareAndSet(false, true)) {
            return
        }

        val purchaseLaunchRevision =
            safeBrowsePassSnapshotRevisions
                .invalidate {
                    _safeBrowsePassPendingKind
                        .value =
                        null

                    _safeBrowsePassBillingUiState
                        .value =
                        SafeBrowsePassBillingUiState
                            .RefreshingOffer(period)
                }

        val obfuscatedAccountId =
            obfuscatedPlayBillingAccountId(
                firebaseUser.uid,
            )

        scope.launch {
            val entitlement = safeBrowsePassRepository.entitlement.first()
            val intent = resolveSafeBrowsePassPurchaseIntent(
                entitlement = entitlement,
                requestedPeriod = period,
                nowMillis = System.currentTimeMillis(),
            )

            val requiredBasePlanId = when (intent) {
                SafeBrowsePassPurchaseIntent.AlreadyActive -> {
                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    safeBrowsePassSnapshotRevisions
                        .invalidateIfCurrent(
                            purchaseLaunchRevision,
                        ) {
                            _safeBrowsePassPendingKind
                                .value =
                                null

                            _safeBrowsePassBillingUiState
                                .value =
                                SafeBrowsePassBillingUiState
                                    .AlreadyOwned
                        }
                    return@launch
                }

                SafeBrowsePassPurchaseIntent.RefreshRequired -> {
                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    safeBrowsePassSnapshotRevisions
                        .invalidateIfCurrent(
                            purchaseLaunchRevision,
                        ) {
                            _safeBrowsePassPendingKind
                                .value =
                                null

                            _safeBrowsePassBillingUiState
                                .value =
                                SafeBrowsePassBillingUiState
                                    .VerificationDeferred
                        }
                    startSafeBrowsePassEntitlementRefresh()
                    return@launch
                }

                SafeBrowsePassPurchaseIntent.InitialPurchase -> null

                is SafeBrowsePassPurchaseIntent.PrepaidTopUp -> intent.requiredBasePlanId
            }

            querySubscriptionProducts(listOf(SafeBrowsePassProductId)) { result, queryResult ->
                logUnfetchedProducts("Safe Browse Pass purchase-launch product query", queryResult)

                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    handleSafeBrowsePassPurchaseFlowFailure(
                        result = result,
                        expectedSnapshotRevision = purchaseLaunchRevision,
                    )
                    return@querySubscriptionProducts
                }

                val details = fetchedProductsById(queryResult)[SafeBrowsePassProductId]
                val offers = details?.offerSnapshots().orEmpty()

                val freshPlan = when (period) {
                    SafeBrowsePassPeriod.Monthly ->
                        selectSafeBrowsePassMonthlyPlan(
                            productId = SafeBrowsePassProductId,
                            offers = offers,
                            infiniteRecurringMode = ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                        )

                    SafeBrowsePassPeriod.Prepaid ->
                        selectSafeBrowsePassPrepaidPlan(
                            productId = SafeBrowsePassProductId,
                            offers = offers,
                            nonRecurringMode = ProductDetails.RecurrenceMode.NON_RECURRING,
                            requiredBasePlanId = requiredBasePlanId,
                        )
                }

                // The exact active prepaid base plan was not returned as eligible -- never
                // substitute a different prepaid base plan or fall back to monthly.
                if (details == null || freshPlan == null) {
                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    val stillPublished =
                        safeBrowsePassSnapshotRevisions
                            .invalidateIfCurrent(
                                purchaseLaunchRevision,
                            ) {
                                _safeBrowsePassPendingKind
                                    .value =
                                    null

                                _safeBrowsePassBillingUiState
                                    .value =
                                    SafeBrowsePassBillingUiState
                                        .ProductUnavailable
                            }
                    if (stillPublished) {
                        refreshProductDetails()
                    }
                    return@querySubscriptionProducts
                }

                val expectedToken = expectedOfferToken?.trim()?.takeIf(String::isNotBlank)
                if (expectedToken != null && freshPlan.offerToken != expectedToken) {
                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    val stillPublished =
                        safeBrowsePassSnapshotRevisions
                            .invalidateIfCurrent(
                                purchaseLaunchRevision,
                            ) {
                                _safeBrowsePassPendingKind
                                    .value =
                                    null

                                _safeBrowsePassBillingUiState
                                    .value =
                                    SafeBrowsePassBillingUiState
                                        .ProductUnavailable
                            }
                    if (stillPublished) {
                        refreshProductDetails()
                    }
                    return@querySubscriptionProducts
                }

                val stillCurrent =
                    safeBrowsePassSnapshotRevisions
                        .runIfCurrent(
                            purchaseLaunchRevision,
                        ) {
                            _safeBrowsePassBillingUiState
                                .value =
                                SafeBrowsePassBillingUiState
                                    .PurchaseLaunching(
                                        period,
                                    )
                        }

                if (!stillCurrent) {
                    clearPurchaseLaunchInFlight(
                        PurchaseFamily.SafeBrowsePass,
                    )
                    return@querySubscriptionProducts
                }

                mainHandler.post {
                    if (activity.isFinishing || activity.isDestroyed) {
                        clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                        safeBrowsePassSnapshotRevisions
                            .invalidateIfCurrent(
                                purchaseLaunchRevision,
                            ) {
                                _safeBrowsePassPendingKind
                                    .value =
                                    null

                                _safeBrowsePassBillingUiState
                                    .value =
                                    SafeBrowsePassBillingUiState
                                        .Ready
                            }
                        return@post
                    }

                    val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(freshPlan.offerToken)

                    // No replacement parameters for any Safe Browse Pass purchase, including
                    // a prepaid top-up: it is always its own independent transaction.
                    val builder = BillingFlowParams.newBuilder()
                        .setObfuscatedAccountId(obfuscatedAccountId)
                        .setProductDetailsParamsList(listOf(productDetailsParams.build()))

                    pendingPurchaseFamily = PurchaseFamily.SafeBrowsePass
                    val launchResult = billingClient.launchBillingFlow(activity, builder.build())
                    if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // Retained until onPurchasesUpdated reports the result.
                        return@post
                    }

                    clearPurchaseLaunchInFlight(PurchaseFamily.SafeBrowsePass)
                    pendingPurchaseFamily = null
                    handleSafeBrowsePassPurchaseFlowFailure(
                        result = launchResult,
                        expectedSnapshotRevision = purchaseLaunchRevision,
                    )
                }
            }
        }
    }

    private fun handleSafeBrowsePassPurchaseFlowFailure(
        result: BillingResult,
        expectedSnapshotRevision:
            Long? =
            null,
    ) {
        val mapped =
            safeBrowsePassBillingFailureStateForResponseCode(
                result.responseCode,
            )
                ?: SafeBrowsePassBillingUiState
                    .Error(
                        responseCode =
                            result.responseCode,
                        retryable = false,
                    )

        val published =
            if (
                expectedSnapshotRevision ==
                null
            ) {
                safeBrowsePassSnapshotRevisions
                    .invalidate {
                        _safeBrowsePassPendingKind
                            .value =
                            null

                        _safeBrowsePassBillingUiState
                            .value =
                            mapped
                    }

                true
            } else {
                safeBrowsePassSnapshotRevisions
                    .invalidateIfCurrent(
                        expectedSnapshotRevision,
                    ) {
                        _safeBrowsePassPendingKind
                            .value =
                            null

                        _safeBrowsePassBillingUiState
                            .value =
                            mapped
                    }
            }

        if (!published) {
            return
        }

        Log.w(
            Tag,
            billingResultFailureMessage(
                operation =
                    "Safe Browse Pass purchase flow",
                result = result,
            ),
        )

        when (result.responseCode) {
            BillingClient
                .BillingResponseCode
                .ITEM_ALREADY_OWNED -> {
                refreshPurchases()
                startSafeBrowsePassEntitlementRefresh()
            }

            BillingClient
                .BillingResponseCode
                .SERVICE_DISCONNECTED ->
                scheduleReconnectAfterDisconnect()
        }
    }

    fun refreshPurchases() {
        if (currentNonAnonymousFirebaseUid() == null) {
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
                Log.i(
                    Tag,
                    "Play purchase refresh returned ${purchases.size} purchase(s).",
                )
                handlePurchases(purchases)
                handleSafeBrowsePassPurchases(purchases)
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

    private fun clearPurchaseLaunchInFlight(family: PurchaseFamily) {
        when (family) {
            PurchaseFamily.Plus -> plusPurchaseLaunchInFlight.set(false)
            PurchaseFamily.SafeBrowsePass -> safeBrowsePassPurchaseLaunchInFlight.set(false)
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        val completedFamily = pendingPurchaseFamily
        pendingPurchaseFamily = null
        completedFamily?.let(::clearPurchaseLaunchInFlight)

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases.isNullOrEmpty()) {
                when (completedFamily) {
                    PurchaseFamily.SafeBrowsePass -> _safeBrowsePassBillingUiState.value = SafeBrowsePassBillingUiState.Error(responseCode = BillingClient.BillingResponseCode.ERROR, retryable = true)
                    PurchaseFamily.Plus, null -> _billingUiState.value = BillingUiState.Error(responseCode = BillingClient.BillingResponseCode.ERROR, retryable = true)
                }
                Log.w(Tag, "Play Billing returned OK without purchase data; querying owned purchases.")
                refreshPurchases()
                return
            }

            if (currentNonAnonymousFirebaseUid() == null) {
                _billingUiState.value = BillingUiState.VerificationDeferred
                _safeBrowsePassBillingUiState.value = SafeBrowsePassBillingUiState.VerificationDeferred
                Log.w(
                    Tag,
                    "Purchase completed before authentication was available; verification deferred.",
                )
                return
            }

            handlePurchases(purchases)
            handleSafeBrowsePassPurchases(purchases)
            refreshPurchases()
        } else {
            // Route the failure only to the family whose launchBillingFlow() call is
            // actually outstanding -- a cancelled/declined Plus purchase must never flip
            // the Safe Browse Pass purchase screen's state, and vice versa.
            when (completedFamily) {
                PurchaseFamily.SafeBrowsePass -> handleSafeBrowsePassPurchaseFlowFailure(result)
                PurchaseFamily.Plus, null -> handlePurchaseFlowFailure(result)
            }
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
                        selectedPurchasePlansByPeriod.isNotEmpty()
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

    private data class SafeBrowsePassPlaySnapshotKey(
        val purchases:
            List<
                SafeBrowsePassPlaySnapshotPurchase
            >,
    )

    private data class SafeBrowsePassPlaySnapshotPurchase(
        val purchaseToken: String,
        val purchaseState: Int,
        val products: List<String>,
        val pendingUpdateProducts:
            List<String>,
    )

    private fun safeBrowsePassPlaySnapshotKey(
        purchases: List<Purchase>,
    ): SafeBrowsePassPlaySnapshotKey =
        SafeBrowsePassPlaySnapshotKey(
            purchases =
                purchases
                    .map { purchase ->
                        SafeBrowsePassPlaySnapshotPurchase(
                            purchaseToken =
                                purchase.purchaseToken,
                            purchaseState =
                                purchase.purchaseState,
                            products =
                                purchase.products
                                    .sorted(),
                            pendingUpdateProducts =
                                purchase
                                    .pendingPurchaseUpdate
                                    ?.products
                                    .orEmpty()
                                    .sorted(),
                        )
                    }
                    .sortedWith(
                        compareBy<
                            SafeBrowsePassPlaySnapshotPurchase
                        >(
                            {
                                it.purchaseToken
                            },
                            {
                                it.purchaseState
                            },
                        ),
                    ),
        )

    /**
     * Mirrors [handlePurchases], operating on the exact same purchase list from the exact
     * same Play query, but filtered to Safe Browse Pass product IDs and writing only
     * [safeBrowsePassRepository] / [_safeBrowsePassBillingUiState] -- never [repository] or
     * [_billingUiState].
     */
    private fun Purchase.hasPendingSafeBrowsePassUpdate(): Boolean =
        pendingPurchaseUpdate
            ?.products
            ?.any { productId -> productId == SafeBrowsePassProductId } == true

    private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>) {
        val passPurchases = purchases.filter { purchase ->
            purchase.products.any { productId -> productId == SafeBrowsePassProductId }
        }

        val purchasedPassPurchases = passPurchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        val current = purchasedPassPurchases.firstOrNull()
        ownedSafeBrowsePassPurchaseToken = current?.purchaseToken
        ownedSafeBrowsePassProductId = current?.products?.firstOrNull { productId ->
            productId == SafeBrowsePassProductId
        }

        val pendingTopUpPurchases = passPurchases.filter { purchase ->
            purchase.hasPendingSafeBrowsePassUpdate()
        }

        val freshPendingPurchases = passPurchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PENDING &&
                !purchase.hasPendingSafeBrowsePassUpdate()
        }

        val decision = resolveSafeBrowsePassPlaySnapshotDecision(
            hasPendingTopUp = pendingTopUpPurchases.isNotEmpty(),
            hasPendingInitialPurchase = freshPendingPurchases.isNotEmpty(),
            hasPurchasedPurchase = purchasedPassPurchases.isNotEmpty(),
        )

        val snapshotKey =
            safeBrowsePassPlaySnapshotKey(
                passPurchases,
            )

        val snapshotAcceptance =
            safeBrowsePassSnapshotRevisions
                .accept(
                    key = snapshotKey,
                ) {
                    _safeBrowsePassPendingKind
                        .value =
                        decision.pendingKind

                    _safeBrowsePassBillingUiState
                        .value =
                        decision.billingState
                }

        val purchasesToVerify = if (pendingTopUpPurchases.isNotEmpty()) {
            pendingTopUpPurchases.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        } else {
            purchasedPassPurchases
        }

        if (purchasesToVerify.isNotEmpty()) {
            verifyAndGrantSafeBrowsePassPurchases(
                purchases = purchasesToVerify,
                expectedSnapshotRevision =
                    snapshotAcceptance.revision,
                keepPendingUiState = decision.keepPendingUiState,
            )
        }

        when {
            pendingTopUpPurchases.isNotEmpty() ->
                Log.i(
                    Tag,
                    "Safe Browse Pass top-up is pending; the existing verified entitlement remains unchanged.",
                )

            freshPendingPurchases.isNotEmpty() ->
                Log.i(Tag, "Safe Browse Pass purchase is pending; entitlement remains locked.")

            purchasedPassPurchases.isNotEmpty() -> Unit

            else -> Log.i(Tag, "No grantable Safe Browse Pass purchase was returned.")
        }
    }

    private data class SafeBrowsePassVerificationSummary(
        val attemptedCount: Int,
        val grantedCount: Int,
        val verificationFailed: Boolean,
        val accountChanged: Boolean,
        val snapshotSuperseded: Boolean,
    )

    private suspend fun verifyAndApplySafeBrowsePassPurchases(
        purchases: List<Purchase>,
        expectedUid: String,
        expectedSnapshotRevision: Long,
    ): SafeBrowsePassVerificationSummary {
        var attemptedCount = 0
        var grantedCount = 0
        var verificationFailed = false
        var accountChanged = false
        var snapshotSuperseded = false

        for (purchase in purchases) {
            if (
                !safeBrowsePassSnapshotRevisions
                    .isCurrent(
                        expectedSnapshotRevision,
                    )
            ) {
                snapshotSuperseded = true
                break
            }

            if (!purchase.products.any { productId -> productId == SafeBrowsePassProductId }) {
                continue
            }

            if (!verifyingSafeBrowsePassPurchaseTokens.add(purchase.purchaseToken)) {
                continue
            }

            attemptedCount += 1

            try {
                val productId = purchase.products.firstOrNull { candidate ->
                    candidate == SafeBrowsePassProductId
                } ?: continue

                val verified = verifySafeBrowsePassPurchaseWithBackend(
                    purchase = purchase,
                    expectedProductId = productId,
                    expectedUid = expectedUid,
                )

                if (
                    !safeBrowsePassSnapshotRevisions
                        .isCurrent(
                            expectedSnapshotRevision,
                        )
                ) {
                    snapshotSuperseded = true
                    break
                }

                if (currentNonAnonymousFirebaseUid() != expectedUid) {
                    accountChanged = true
                    continue
                }

                if (verified != null) {
                    if (
                        !safeBrowsePassSnapshotRevisions
                            .isCurrent(
                                expectedSnapshotRevision,
                            )
                    ) {
                        snapshotSuperseded = true
                        break
                    }

                    val granted = grantSafeBrowsePassEntitlement(
                        expectedUid = verified.expectedUid,
                        productId = verified.productId,
                        basePlanId = verified.basePlanId,
                        expiryTimeMillis = verified.expiryTimeMillis,
                        isPrepaid = verified.isPrepaid,
                        renewalState = verified.renewalState,
                    )

                    if (
                        !safeBrowsePassSnapshotRevisions
                            .isCurrent(
                                expectedSnapshotRevision,
                            )
                    ) {
                        snapshotSuperseded = true
                        break
                    }

                    if (granted) {
                        grantedCount += 1
                    } else {
                        accountChanged = true
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                verificationFailed = true
                Log.w(
                    Tag,
                    backendVerificationFailureMessage(throwable),
                )
            } finally {
                verifyingSafeBrowsePassPurchaseTokens.remove(purchase.purchaseToken)
            }
        }

        if (currentNonAnonymousFirebaseUid() != expectedUid) {
            accountChanged = true
        }

        if (
            !safeBrowsePassSnapshotRevisions
                .isCurrent(
                    expectedSnapshotRevision,
                )
        ) {
            snapshotSuperseded = true
        }

        return SafeBrowsePassVerificationSummary(
            attemptedCount = attemptedCount,
            grantedCount = grantedCount,
            verificationFailed = verificationFailed,
            accountChanged = accountChanged,
            snapshotSuperseded = snapshotSuperseded,
        )
    }

    private fun verifyAndGrantSafeBrowsePassPurchases(
        purchases: List<Purchase>,
        expectedSnapshotRevision: Long,
        keepPendingUiState: Boolean = false,
    ) {
        scope.launch {
            val expectedUid = currentNonAnonymousFirebaseUid() ?: return@launch

            val summary = verifyAndApplySafeBrowsePassPurchases(
                purchases = purchases,
                expectedUid = expectedUid,
                expectedSnapshotRevision = expectedSnapshotRevision,
            )

            if (summary.accountChanged || summary.snapshotSuperseded) {
                return@launch
            }

            when {
                summary.grantedCount > 0 -> {
                    if (
                        billingClient.isReady &&
                        selectedSafeBrowsePassPlansByPeriod.isNotEmpty()
                    ) {
                        safeBrowsePassSnapshotRevisions.runIfCurrent(
                            expectedSnapshotRevision,
                        ) {
                            if (keepPendingUiState) {
                                _safeBrowsePassBillingUiState.value =
                                    SafeBrowsePassBillingUiState.Pending
                            } else {
                                _safeBrowsePassPendingKind.value = null
                                _safeBrowsePassBillingUiState.value =
                                    SafeBrowsePassBillingUiState.Purchased
                            }
                        }
                    }
                }

                summary.attemptedCount > 0 -> {
                    if (!keepPendingUiState) {
                        safeBrowsePassSnapshotRevisions.runIfCurrent(
                            expectedSnapshotRevision,
                        ) {
                            _safeBrowsePassPendingKind.value = null
                            _safeBrowsePassBillingUiState.value =
                                SafeBrowsePassBillingUiState.VerificationFailed
                        }
                    }
                }

                else -> Unit
            }
        }
    }
    /**
     * Reuses [resolveSafeBrowsePassEntitlementResponse] -- the exact same response-shape
     * validation and plan-kind parsing the entitlement-refresh path uses -- rather than
     * duplicating it here.
     */
    private suspend fun verifySafeBrowsePassPurchaseWithBackend(
        purchase: Purchase,
        expectedProductId: String,
        expectedUid: String,
    ): VerifiedSafeBrowsePassPurchase? {
        val normalisedExpectedUid =
            expectedUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: return null

        if (currentNonAnonymousFirebaseUid() != normalisedExpectedUid) {
            return null
        }

        val result = functions
            .getHttpsCallable(VerifySafeBrowsePassSubscriptionFunction)
            .call(
                mapOf(
                    "productId" to expectedProductId,
                    "purchaseToken" to purchase.purchaseToken,
                ),
            )
            .await()

        if (currentNonAnonymousFirebaseUid() != normalisedExpectedUid) {
            Log.w(
                Tag,
                "Ignored stale Safe Browse Pass purchase verification after authentication changed.",
            )
            return null
        }

        val resolution = resolveSafeBrowsePassEntitlementResponse(
            data = result.getData(),
            nowMillis = System.currentTimeMillis(),
        )

        return if (resolution is SafeBrowsePassEntitlementResolution.Active && resolution.productId == expectedProductId) {
            VerifiedSafeBrowsePassPurchase(
                expectedUid = normalisedExpectedUid,
                productId = resolution.productId,
                basePlanId = resolution.basePlanId,
                expiryTimeMillis = resolution.expiryTimeMillis,
                isPrepaid = resolution.isPrepaid,
                renewalState =
                    resolveSafeBrowsePassRenewalState(
                        isPrepaid =
                            resolution.isPrepaid,
                        subscriptionState =
                            resolution.subscriptionState,
                    ),
            )
        } else {
            null
        }
    }

    private suspend fun grantSafeBrowsePassEntitlement(
        expectedUid: String,
        productId: String,
        basePlanId: String,
        expiryTimeMillis: Long,
        isPrepaid: Boolean,
        renewalState:
            SafeBrowsePassRenewalState,
    ): Boolean {
        return safeBrowsePassRepository.setVerifiedEntitlement(
            expectedUid = expectedUid,
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                productId = productId,
                basePlanId = basePlanId,
                expiryTimeMillis = expiryTimeMillis,
                isPrepaid = isPrepaid,
                renewalState = renewalState,
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
        safeBrowsePassEntitlementRefreshInFlight.set(false)
        safeBrowsePassEntitlementRefreshPending.set(false)
        restorePendingAfterConnection.set(false)
        _restoreState.value = BillingRestoreState.Idle
        selectedPurchasePlansByPeriod.clear()
        selectedSafeBrowsePassPlansByPeriod.clear()
        verifyingPurchaseTokens.clear()
        verifyingSafeBrowsePassPurchaseTokens.clear()
        pendingPurchaseFamily = null
        plusPurchaseLaunchInFlight.set(false)
        safeBrowsePassPurchaseLaunchInFlight.set(false)
    }

    private enum class PurchaseFamily {
        Plus,
        SafeBrowsePass,
    }

    companion object {
        private const val Tag = "BillingManager"
        private const val FunctionsRegion = "us-central1"
        private const val VerifyPlusSubscriptionFunction = "verifyPlusSubscription"
        private const val CheckPlusEntitlementFunction = "checkPlusEntitlement"
        private const val VerifySafeBrowsePassSubscriptionFunction =
            "verifySafeBrowsePassSubscription"
        private const val CheckSafeBrowsePassEntitlementFunction =
            "checkSafeBrowsePassEntitlement"

        // Must match the subscription product IDs created in Google Play Console.
        const val PlusProductId = "impulsive_plus_monthly"
        const val PlusYearlyProductId = "impulsive_plus_yearly"

        // Must match the server catalogue in functions/subscriptionCatalog.js and the
        // subscription product created in Google Play Console. Safe Browse Pass is one
        // Play product with independently selected auto-renewing and prepaid base plans --
        // never two separate top-level products.
        const val SafeBrowsePassProductId = "safe_browse_pass"
    }

    private data class VerifiedPurchase(
        val productId: String,
        val expiryTimeMillis: Long,
    )

    private data class VerifiedSafeBrowsePassPurchase(
        val expectedUid: String,
        val productId: String,
        val basePlanId: String,
        val expiryTimeMillis: Long,
        val isPrepaid: Boolean,
        val renewalState:
            SafeBrowsePassRenewalState,
    )
}
