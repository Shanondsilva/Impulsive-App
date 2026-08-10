package com.impulsive.app.backend.session.safebrowse

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.SafeBrowseAccessRepository
import com.impulsive.app.backend.data.repository.SafeBrowsePassRepository
import com.impulsive.app.backend.domain.model.safebrowse.CheckpointIntervalMillis
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessEffect
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessSnapshot
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessState
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.isValidAt
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseRewardGrantResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SafeBrowseAccessViewModel(
    private val repository: SafeBrowseAccessRepository,
    private val passRepository: SafeBrowsePassRepository,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val epochMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val operationMutex = Mutex()
    private val initialisationComplete = CompletableDeferred<Unit>()

    private val _accessState = MutableStateFlow<SafeBrowseAccessState>(SafeBrowseAccessState.Loading)
    val accessState: StateFlow<SafeBrowseAccessState> = _accessState.asStateFlow()

    private val _effects = MutableSharedFlow<SafeBrowseAccessEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<SafeBrowseAccessEffect> = _effects.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var authoritativeLedgerSnapshot =
        SafeBrowseAccessSnapshot(
            remainingMillis = 0L,
            leaseActive = false,
        )

    private var currentPassEntitlement = SafeBrowsePassEntitlement()

    private var tickerJob: Job? = null
    private var passExpiryJob: Job? = null

    private var usageActive = false
    private var expirationEffectEmitted = false
    private var passGeneration = 0L

    private var displayBaselineRemainingMillis = 0L
    private var displayBaselineElapsedMillis = 0L

    init {
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    val passResult = runCatching { passRepository.currentEntitlement() }
                    val ledgerResult = runCatching { repository.reconcileInterruptedLease() }

                    if (passResult.isFailure || ledgerResult.isFailure) {
                        handlePersistenceFailureLocked(
                            passResult.exceptionOrNull()
                                ?: ledgerResult.exceptionOrNull()
                                ?: IllegalStateException("Safe Browse initialisation failed."),
                        )
                        return@withLock
                    }

                    currentPassEntitlement = requireNotNull(passResult.getOrNull())
                    authoritativeLedgerSnapshot = requireNotNull(ledgerResult.getOrNull())

                    applyCurrentPassStateLocked(
                        forceTimedLedgerClear = currentPassEntitlement.isValidAt(epochMillis()),
                    )
                }
            } finally {
                if (!initialisationComplete.isCompleted) {
                    initialisationComplete.complete(Unit)
                }
            }
        }

        viewModelScope.launch {
            initialisationComplete.await()
            passRepository.entitlement
                .distinctUntilChanged()
                .collect { entitlement ->
                    operationMutex.withLock {
                        applyPassEntitlementLocked(entitlement)
                    }
                }
        }
    }

    private suspend fun awaitInitialisation() {
        initialisationComplete.await()
    }

    fun refresh() {
        viewModelScope.launch {
            awaitInitialisation()
            operationMutex.withLock {
                val passActive = runCatching { synchronisePassLocked() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                if (passActive) {
                    _errorMessage.value = null
                    _accessState.value = SafeBrowseAccessState.PassActive(
                        currentPassEntitlement.expiryTimeMillis,
                    )
                    return@withLock
                }

                val snapshot = runCatching { repository.currentSnapshot() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                authoritativeLedgerSnapshot = snapshot
                _errorMessage.value = null
                applyAuthoritativeLedgerStateLocked()
            }
        }
    }

    fun grantReward(receiptToken: String) {
        viewModelScope.launch {
            awaitInitialisation()
            operationMutex.withLock {
                val passActive = runCatching { synchronisePassLocked() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                val result = runCatching {
                    repository.grantReward(
                        receiptToken = receiptToken,
                        grantTimedAccess = !passActive,
                    )
                }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                expirationEffectEmitted = false
                _errorMessage.value = null
                authoritativeLedgerSnapshot =
                    SafeBrowseAccessSnapshot(
                        remainingMillis = when (result) {
                            is SafeBrowseRewardGrantResult.Granted -> result.remainingMillis
                            is SafeBrowseRewardGrantResult.Duplicate -> result.remainingMillis
                        },
                        leaseActive = false,
                    )

                if (passActive) {
                    _accessState.value = SafeBrowseAccessState.PassActive(
                        currentPassEntitlement.expiryTimeMillis,
                    )
                } else {
                    applyAuthoritativeLedgerStateLocked()
                }
            }
        }
    }

    fun requestOpenBrowser() {
        viewModelScope.launch {
            awaitInitialisation()
            operationMutex.withLock {
                val passActive = runCatching { synchronisePassLocked() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                if (passActive) {
                    _effects.tryEmit(SafeBrowseAccessEffect.OpenBrowser)
                    return@withLock
                }

                val snapshot = runCatching { repository.currentSnapshot() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                authoritativeLedgerSnapshot = snapshot
                applyAuthoritativeLedgerStateLocked()

                if (snapshot.remainingMillis > 0L) {
                    _effects.tryEmit(SafeBrowseAccessEffect.OpenBrowser)
                }
            }
        }
    }

    fun beginBrowserUsage() {
        viewModelScope.launch {
            awaitInitialisation()
            operationMutex.withLock {
                val passActive = runCatching { synchronisePassLocked() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                if (passActive || usageActive) {
                    return@withLock
                }

                val snapshot = runCatching { repository.beginUsage() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                authoritativeLedgerSnapshot = snapshot

                if (snapshot.remainingMillis <= 0L || !snapshot.leaseActive) {
                    applyAuthoritativeLedgerStateLocked()
                    return@withLock
                }

                usageActive = true
                expirationEffectEmitted = false
                displayBaselineRemainingMillis = snapshot.remainingMillis
                displayBaselineElapsedMillis = elapsedRealtimeMillis()
                _accessState.value = SafeBrowseAccessState.Active(snapshot.remainingMillis)
                startTickerLocked()
            }
        }
    }

    fun endBrowserUsage() {
        viewModelScope.launch {
            awaitInitialisation()
            operationMutex.withLock {
                val passActive = runCatching { synchronisePassLocked() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                if (passActive) {
                    usageActive = false
                    stopTickerLocked()
                    _accessState.value = SafeBrowseAccessState.PassActive(
                        currentPassEntitlement.expiryTimeMillis,
                    )
                    return@withLock
                }

                if (!usageActive) {
                    return@withLock
                }

                usageActive = false
                stopTickerLocked()

                val snapshot = runCatching { repository.endUsage() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                authoritativeLedgerSnapshot = snapshot
                _errorMessage.value = null
                applyAuthoritativeLedgerStateLocked()
            }
        }
    }

    private fun startTickerLocked() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var elapsedSinceCheckpoint = 0L

            while (true) {
                delay(1_000L)

                val shouldStop = operationMutex.withLock {
                    if (!usageActive) {
                        return@withLock true
                    }

                    if (currentPassEntitlement.isValidAt(epochMillis())) {
                        usageActive = false
                        stopTickerLocked()
                        _accessState.value = SafeBrowseAccessState.PassActive(
                            currentPassEntitlement.expiryTimeMillis,
                        )
                        return@withLock true
                    }

                    val elapsed = (elapsedRealtimeMillis() - displayBaselineElapsedMillis).coerceAtLeast(0L)
                    val visibleRemaining = (displayBaselineRemainingMillis - elapsed).coerceAtLeast(0L)

                    if (visibleRemaining <= 0L) {
                        return@withLock expireUsageLocked()
                    }

                    _accessState.value = SafeBrowseAccessState.Active(visibleRemaining)
                    elapsedSinceCheckpoint += 1_000L

                    if (elapsedSinceCheckpoint < CheckpointIntervalMillis) {
                        return@withLock false
                    }

                    elapsedSinceCheckpoint = 0L

                    if (runCatching { synchronisePassLocked() }.getOrElse { error ->
                            handlePersistenceFailureLocked(error)
                            return@withLock true
                        }
                    ) {
                        return@withLock true
                    }

                    val snapshot = runCatching { repository.checkpointUsage() }.getOrElse { error ->
                        handlePersistenceFailureLocked(error)
                        return@withLock true
                    }

                    authoritativeLedgerSnapshot = snapshot
                    displayBaselineRemainingMillis = snapshot.remainingMillis
                    displayBaselineElapsedMillis = elapsedRealtimeMillis()
                    _errorMessage.value = null

                    if (!snapshot.leaseActive) {
                        usageActive = false
                        stopTickerLocked()
                        applyAuthoritativeLedgerStateLocked()

                        if (snapshot.remainingMillis <= 0L) {
                            emitAccessExpiredOnceLocked()
                        }

                        true
                    } else {
                        _accessState.value = SafeBrowseAccessState.Active(snapshot.remainingMillis)
                        false
                    }
                }

                if (shouldStop) {
                    break
                }
            }
        }
    }

    private suspend fun expireUsageLocked(): Boolean {
        if (!usageActive) {
            return true
        }

        if (runCatching { synchronisePassLocked() }.getOrElse { error ->
                handlePersistenceFailureLocked(error)
                return true
            }
        ) {
            usageActive = false
            stopTickerLocked()
            return true
        }

        val snapshot = runCatching { repository.endUsage() }.getOrElse { error ->
            handlePersistenceFailureLocked(error)
            return true
        }

        authoritativeLedgerSnapshot = snapshot
        usageActive = false
        stopTickerLocked()
        _errorMessage.value = null

        if (snapshot.remainingMillis <= 0L && !snapshot.leaseActive) {
            _accessState.value = SafeBrowseAccessState.Locked
            emitAccessExpiredOnceLocked()
            return true
        }

        val resumedSnapshot = runCatching { repository.beginUsage() }.getOrElse { error ->
            handlePersistenceFailureLocked(error)
            return true
        }

        authoritativeLedgerSnapshot = resumedSnapshot

        if (resumedSnapshot.remainingMillis > 0L && resumedSnapshot.leaseActive) {
            usageActive = true
            displayBaselineRemainingMillis = resumedSnapshot.remainingMillis
            displayBaselineElapsedMillis = elapsedRealtimeMillis()
            _accessState.value = SafeBrowseAccessState.Active(resumedSnapshot.remainingMillis)
            startTickerLocked()
            return false
        }

        applyAuthoritativeLedgerStateLocked()

        if (resumedSnapshot.remainingMillis <= 0L) {
            emitAccessExpiredOnceLocked()
        }

        return true
    }

    private suspend fun applyPassEntitlementLocked(entitlement: SafeBrowsePassEntitlement) {
        currentPassEntitlement = entitlement
        applyCurrentPassStateLocked(forceTimedLedgerClear = entitlement.isValidAt(epochMillis()))
    }

    private suspend fun applyCurrentPassStateLocked(forceTimedLedgerClear: Boolean) {
        val nowMillis = epochMillis()

        if (currentPassEntitlement.isValidAt(nowMillis)) {
            usageActive = false
            stopTickerLocked()

            if (forceTimedLedgerClear) {
                val clearedSnapshot = runCatching {
                    repository.clearTimedAccessForPassActivation()
                }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return
                }

                authoritativeLedgerSnapshot = clearedSnapshot
            }

            _errorMessage.value = null
            _accessState.value = SafeBrowseAccessState.PassActive(currentPassEntitlement.expiryTimeMillis)
            schedulePassExpiryLocked(currentPassEntitlement)
            return
        }

        cancelPassExpiryLocked()

        if (currentPassEntitlement.active && currentPassEntitlement.expiryTimeMillis > 0L && nowMillis >= currentPassEntitlement.expiryTimeMillis) {
            runCatching {
                passRepository.expireCurrentEntitlementIfRequired(nowMillis)
            }
        }

        currentPassEntitlement = SafeBrowsePassEntitlement()
        applyAuthoritativeLedgerStateLocked()
    }

    private fun schedulePassExpiryLocked(entitlement: SafeBrowsePassEntitlement) {
        passGeneration += 1L
        val generation = passGeneration

        passExpiryJob?.cancel()

        val delayMillis = (entitlement.expiryTimeMillis - epochMillis()).coerceAtLeast(0L)

        passExpiryJob = viewModelScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }

            awaitInitialisation()

            operationMutex.withLock {
                if (generation != passGeneration) {
                    return@withLock
                }

                val latestEntitlement = runCatching { passRepository.currentEntitlement() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                currentPassEntitlement = latestEntitlement
                val nowMillis = epochMillis()

                if (latestEntitlement.isValidAt(nowMillis)) {
                    schedulePassExpiryLocked(latestEntitlement)
                    return@withLock
                }

                runCatching {
                    passRepository.expireCurrentEntitlementIfRequired(nowMillis)
                }.onFailure { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }

                currentPassEntitlement = SafeBrowsePassEntitlement()
                usageActive = false
                stopTickerLocked()
                authoritativeLedgerSnapshot = runCatching { repository.currentSnapshot() }.getOrElse { error ->
                    handlePersistenceFailureLocked(error)
                    return@withLock
                }
                applyAuthoritativeLedgerStateLocked()
            }
        }
    }

    private fun cancelPassExpiryLocked() {
        passGeneration += 1L
        passExpiryJob?.cancel()
        passExpiryJob = null
    }

    private suspend fun synchronisePassLocked(): Boolean {
        val latest = passRepository.currentEntitlement()

        if (latest != currentPassEntitlement) {
            applyPassEntitlementLocked(latest)
        } else if (latest.active && !latest.isValidAt(epochMillis())) {
            applyCurrentPassStateLocked(forceTimedLedgerClear = false)
        }

        return currentPassEntitlement.isValidAt(epochMillis())
    }

    private fun emitAccessExpiredOnceLocked() {
        if (expirationEffectEmitted) {
            return
        }

        expirationEffectEmitted = true
        _effects.tryEmit(SafeBrowseAccessEffect.AccessExpired)
    }

    private fun applyAuthoritativeLedgerStateLocked() {
        _accessState.value =
            if (authoritativeLedgerSnapshot.remainingMillis > 0L) {
                SafeBrowseAccessState.Active(authoritativeLedgerSnapshot.remainingMillis)
            } else {
                SafeBrowseAccessState.Locked
            }
    }

    private fun stopTickerLocked() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun handlePersistenceFailureLocked(throwable: Throwable) {
        usageActive = false
        tickerJob?.cancel()
        tickerJob = null
        _errorMessage.value = "Safe Browse access could not be saved."
        _accessState.value = SafeBrowseAccessState.Error("Safe Browse access could not be saved.")
    }

    override fun onCleared() {
        tickerJob?.cancel()
        tickerJob = null
        passExpiryJob?.cancel()
        passExpiryJob = null
        super.onCleared()
    }
}

class SafeBrowseAccessViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = SafeBrowseAccessRepository(context.applicationContext)
        val passRepository = SafeBrowsePassRepository(context.applicationContext)
        return SafeBrowseAccessViewModel(
            repository = repository,
            passRepository = passRepository,
            elapsedRealtimeMillis = SystemClock::elapsedRealtime,
        ) as T
    }
}
