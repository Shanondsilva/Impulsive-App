package com.impulsive.app.backend.session.safebrowse

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.data.local.preferences.SafeBrowseAccessDataSource
import com.impulsive.app.backend.data.local.preferences.SafeBrowsePassEntitlementDataSource
import com.impulsive.app.backend.data.repository.SafeBrowseAccessRepository
import com.impulsive.app.backend.data.repository.SafeBrowsePassRepository
import com.impulsive.app.backend.data.repository.TestSafeBrowsePassAccountProvider
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessEffect
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessState
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.TwoHourGrantMillis
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private suspend fun SafeBrowsePassRepository.setEntitlement(entitlement: SafeBrowsePassEntitlement) {
    setVerifiedEntitlement(
        expectedUid = "test-safe-browse-user",
        entitlement = entitlement,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class SafeBrowseAccessViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newRepository(): SafeBrowseAccessRepository {
        val directory = Files.createTempDirectory("safe-browse-access-vm").toFile()
        val file = File(directory, "safe_browse_access.preferences_pb")
        // DataStore's own actor must run on the SAME virtual dispatcher as the test so
        // runCurrent()/advanceTimeBy() deterministically drive its I/O to completion.
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val dataSource = SafeBrowseAccessDataSource(dataStore)
        return SafeBrowseAccessRepository(
            dataSource = dataSource,
            elapsedRealtimeMillis = { testDispatcher.scheduler.currentTime },
            epochMillis = { testDispatcher.scheduler.currentTime },
        )
    }

    private fun newPassRepository(): SafeBrowsePassRepository {
        val directory = Files.createTempDirectory("safe-browse-pass-vm").toFile()
        val file = File(directory, "safe_browse_pass_entitlement.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return SafeBrowsePassRepository(
            SafeBrowsePassEntitlementDataSource(dataStore),
            TestSafeBrowsePassAccountProvider(),
        )
    }

    private fun newViewModel(
        repository: SafeBrowseAccessRepository = newRepository(),
        passRepository: SafeBrowsePassRepository = newPassRepository(),
    ) = SafeBrowseAccessViewModel(
        repository = repository,
        passRepository = passRepository,
        elapsedRealtimeMillis = { testDispatcher.scheduler.currentTime },
        epochMillis = { testDispatcher.scheduler.currentTime },
    )

    @Test
    fun initialStateIsLoadingUntilTheFirstLedgerReadResolves() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        // Before the init coroutine has had a chance to run, direct entry (including
        // process recreation) must never be treated as "no access" -- that would incorrectly
        // exit a screen that is still waiting on its first read.
        assertEquals(SafeBrowseAccessState.Loading, viewModel.accessState.value)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )
    }

    @Test
    fun concurrentEndUsageAndCheckpointDoNotDoubleDeduct() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()
        viewModel.beginBrowserUsage()
        runCurrent()

        // Land exactly on the ticker's 15-second checkpoint boundary, then immediately
        // request endBrowserUsage before the checkpoint's own coroutine has a chance to
        // finish. Without the Mutex, the checkpoint and the explicit end-of-usage deduction
        // could interleave and charge the elapsed interval twice.
        advanceTimeBy(15_000L)
        viewModel.endBrowserUsage()
        runCurrent()

        val persisted = repository.currentSnapshot()
        assertTrue(persisted.remainingMillis <= TwoHourGrantMillis - 15_000L)
        assertTrue(persisted.remainingMillis >= TwoHourGrantMillis - 16_000L)
        assertTrue(!persisted.leaseActive)
    }

    @Test
    fun openRequestRejectedWhileLocked() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceTimeBy(1)
        runCurrent()

        var openBrowserEmitted = false
        val collectJob = launch { viewModel.effects.collect { openBrowserEmitted = true } }

        viewModel.requestOpenBrowser()
        runCurrent()

        assertTrue(!openBrowserEmitted)
        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun rewardEnablesOpenRequest() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceTimeBy(1)
        runCurrent()

        viewModel.grantReward("receipt-1")
        runCurrent()

        assertTrue(viewModel.accessState.value is SafeBrowseAccessState.Active)

        var effects = 0
        val collectJob = launch {
            viewModel.effects.collect { effect ->
                if (effect is SafeBrowseAccessEffect.OpenBrowser) effects++
            }
        }
        viewModel.requestOpenBrowser()
        runCurrent()

        assertEquals(1, effects)
        collectJob.cancel()
    }

    @Test
    fun duplicateRewardDoesNotDuplicateAllowance() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceTimeBy(1)
        runCurrent()

        viewModel.grantReward("receipt-1")
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        val state = viewModel.accessState.value as SafeBrowseAccessState.Active
        assertEquals(TwoHourGrantMillis, state.remainingMillis)
    }

    @Test
    fun countdownUpdatesEverySecond() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()

        advanceTimeBy(3_000L)
        runCurrent()

        val state = viewModel.accessState.value as SafeBrowseAccessState.Active
        assertTrue(state.remainingMillis <= TwoHourGrantMillis - 3_000L)
    }

    @Test
    fun checkpointOccursEveryFifteenSeconds() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()

        advanceTimeBy(15_000L)
        runCurrent()

        val persisted = repository.currentSnapshot()
        assertTrue(persisted.remainingMillis <= TwoHourGrantMillis - 15_000L)
    }

    @Test
    fun stopPausesUsage() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        viewModel.endBrowserUsage()
        runCurrent()

        val afterStop = repository.currentSnapshot()
        val remainingAtStop = afterStop.remainingMillis

        // Time passing with no active lease must not deduct anything further.
        advanceTimeBy(60_000L)
        runCurrent()

        val muchLater = repository.currentSnapshot()
        assertEquals(remainingAtStop, muchLater.remainingMillis)
    }

    @Test
    fun resumeContinuesFromPersistedBalance() = runTest(testDispatcher) {
        val repository = newRepository()
        val first = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        first.grantReward("receipt-1")
        runCurrent()
        first.beginBrowserUsage()
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()
        first.endBrowserUsage()
        runCurrent()

        val second = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()

        val state = second.accessState.value as SafeBrowseAccessState.Active
        assertTrue(state.remainingMillis <= TwoHourGrantMillis - 5_000L)
    }

    @Test
    fun expirationEmitsOnce() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1") // default two-hour grant
        runCurrent()

        // Shrink to a tiny balance so expiry is reachable in the test quickly by ending
        // usage almost immediately after starting with nearly the full balance consumed.
        viewModel.beginBrowserUsage()
        runCurrent()
        advanceTimeBy(TwoHourGrantMillis - 1_000L)
        runCurrent()

        var expiredCount = 0
        val collectJob = launch {
            viewModel.effects.collect { effect ->
                if (effect is SafeBrowseAccessEffect.AccessExpired) expiredCount++
            }
        }

        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, expiredCount)
        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun normalPersistedExpirationEmitsOnceWithAnAuthoritativeExhaustedSnapshot() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()
        advanceTimeBy(TwoHourGrantMillis - 1_000L)
        runCurrent()

        var expiredCount = 0
        val collectJob = launch {
            viewModel.effects.collect { effect ->
                if (effect is SafeBrowseAccessEffect.AccessExpired) expiredCount++
            }
        }

        advanceTimeBy(2_000L)
        runCurrent()

        // The effect fires exactly once for one exhaustion event.
        assertEquals(1, expiredCount)
        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )

        // The published state was derived from the snapshot persistence actually
        // returned -- prove the persisted ledger genuinely reflects zero remaining time
        // and no active lease, not merely a locally-assumed "expired" guess.
        val persisted = repository.currentSnapshot()
        assertEquals(0L, persisted.remainingMillis)
        assertTrue(!persisted.leaseActive)

        collectJob.cancel()
    }

    @Test
    fun onlyOneTickerExists() = runTest(testDispatcher) {
        val repository = newRepository()
        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()
        viewModel.beginBrowserUsage() // second call must not start a second ticker
        runCurrent()

        advanceTimeBy(15_000L)
        runCurrent()

        // If two tickers were both checkpointing, twice the usage would have been
        // deducted by now.
        val persisted = repository.currentSnapshot()
        assertTrue(persisted.remainingMillis >= TwoHourGrantMillis - 16_000L)
    }

    @Test
    fun persistenceFailureRemovesAccessRatherThanGrantingUnlimitedUse() = runTest(testDispatcher) {
        // A directory used as the DataStore's target file forces every read/write to fail.
        val brokenDirectory = Files.createTempDirectory("safe-browse-access-broken").toFile()
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { brokenDirectory },
        )
        val dataSource = SafeBrowseAccessDataSource(dataStore)
        val repository = SafeBrowseAccessRepository(
            dataSource = dataSource,
            elapsedRealtimeMillis = { testDispatcher.scheduler.currentTime },
            epochMillis = { testDispatcher.scheduler.currentTime },
        )

        val viewModel = newViewModel(repository)
        advanceTimeBy(1)
        runCurrent()

        viewModel.grantReward("receipt-1")
        runCurrent()

        assertTrue(viewModel.accessState.value is SafeBrowseAccessState.Error)
        assertTrue(viewModel.errorMessage.value != null)
    }

    @Test
    fun activeSafeBrowsePassReportsPassActiveEvenWithNoTimedBalance() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val viewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                expiryTimeMillis = testDispatcher.scheduler.currentTime + 60_000L,
            ),
        )
        runCurrent()

        val state = viewModel.accessState.value
        assertTrue(state is SafeBrowseAccessState.PassActive)
    }

    @Test
    fun inactiveSafeBrowsePassFallsBackToTheTimedLedgerState() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(
                active = false,
                productId = "safe_browse_pass",
                expiryTimeMillis = testDispatcher.scheduler.currentTime + 60_000L,
            ),
        )
        runCurrent()

        assertEquals(
            SafeBrowseAccessState.Locked,
            viewModel.accessState.value,
        )
    }

    @Test
    fun activatingASafeBrowsePassClearsAnExistingTimedBalance() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        viewModel.grantReward("receipt-1")
        runCurrent()
        assertTrue(viewModel.accessState.value is SafeBrowseAccessState.Active)

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                expiryTimeMillis = testDispatcher.scheduler.currentTime + 60_000L,
            ),
        )
        runCurrent()

        // The ledger itself (not just the exposed state) must be cleared, so the timed
        // balance never silently resurfaces later if the Pass lapses.
        val ledgerSnapshot = repository.currentSnapshot()
        assertEquals(0L, ledgerSnapshot.remainingMillis)
        assertTrue(!ledgerSnapshot.leaseActive)
    }

    @Test
    fun beginBrowserUsageIsANoOpWhileASafeBrowsePassIsActive() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                expiryTimeMillis = testDispatcher.scheduler.currentTime + 60_000L,
            ),
        )
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()

        // No timed lease was ever started -- the Pass alone grants access.
        val ledgerSnapshot = repository.currentSnapshot()
        assertTrue(!ledgerSnapshot.leaseActive)
        assertTrue(viewModel.accessState.value is SafeBrowseAccessState.PassActive)
    }

    @Test
    fun requestOpenBrowserEmitsWhileASafeBrowsePassIsActiveWithNoTimedBalance() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val viewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                expiryTimeMillis = testDispatcher.scheduler.currentTime + 60_000L,
            ),
        )
        runCurrent()

        var openBrowserEmitted = false
        val collectJob = launch { viewModel.effects.collect { openBrowserEmitted = true } }

        viewModel.requestOpenBrowser()
        runCurrent()

        assertTrue(openBrowserEmitted)
        collectJob.cancel()
    }
}
