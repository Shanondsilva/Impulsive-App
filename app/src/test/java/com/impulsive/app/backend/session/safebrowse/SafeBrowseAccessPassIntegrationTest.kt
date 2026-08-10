package com.impulsive.app.backend.session.safebrowse

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.data.local.preferences.SafeBrowseAccessDataSource
import com.impulsive.app.backend.data.local.preferences.SafeBrowsePassEntitlementDataSource
import com.impulsive.app.backend.data.repository.SafeBrowseAccessRepository
import com.impulsive.app.backend.data.repository.SafeBrowsePassRepository
import com.impulsive.app.backend.data.repository.TestSafeBrowsePassAccountProvider
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessState
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseRewardGrantResult
import com.impulsive.app.backend.domain.model.safebrowse.TwoHourGrantMillis
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TEST-ONLY diagnostic (Phase 4 regression suite, corrected). Executable integration
 * coverage for how [SafeBrowseAccessViewModel] combines the timed reward ledger with the
 * Safe Browse Pass entitlement. Corrects a prior inaccurate assertion in this class that
 * framed a reward granted while a Pass is active as "usable" -- it is not usable in any
 * visible sense (the UI reports PassActive throughout); the correct contract is only that
 * the receipt token is durably stored and deduplicated, independent of Pass state.
 */
private suspend fun SafeBrowsePassRepository.setEntitlement(entitlement: SafeBrowsePassEntitlement) {
    setVerifiedEntitlement(
        expectedUid = "test-safe-browse-user",
        entitlement = entitlement,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class SafeBrowseAccessPassIntegrationTest {
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
        val directory = Files.createTempDirectory("safe-browse-access-integration").toFile()
        val file = File(directory, "safe_browse_access.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return SafeBrowseAccessRepository(
            dataSource = SafeBrowseAccessDataSource(dataStore),
            elapsedRealtimeMillis = { testDispatcher.scheduler.currentTime },
            epochMillis = { testDispatcher.scheduler.currentTime },
        )
    }

    private fun newPassRepository(): SafeBrowsePassRepository {
        val directory = Files.createTempDirectory("safe-browse-pass-integration").toFile()
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
    fun loadingNeverPretendsToBeLockedBeforeTheFirstLedgerReadResolves() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        assertEquals(SafeBrowseAccessState.Loading, viewModel.accessState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)
    }

    @Test
    fun aValidPassReportsPassActiveEvenWithAZeroTimedBalance() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val viewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 1_000_000L),
        )
        runCurrent()

        assertEquals(
            SafeBrowseAccessState.PassActive(1_000_000L),
            viewModel.accessState.value,
        )
    }

    @Test
    fun aValidPassTakesPriorityOverAnActiveTimedBrowsingSession() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val viewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()
        viewModel.beginBrowserUsage()
        runCurrent()
        assertEquals(SafeBrowseAccessState.Active(TwoHourGrantMillis), viewModel.accessState.value)

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 2_000_000L),
        )
        runCurrent()

        assertEquals(SafeBrowseAccessState.PassActive(2_000_000L), viewModel.accessState.value)
    }

    @Test
    fun passActivationClearsTheUnderlyingTimedRemainingBalance() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 3_000_000L),
        )
        runCurrent()

        val ledgerAfterActivation = repository.currentSnapshot()
        assertEquals(0L, ledgerAfterActivation.remainingMillis)
    }

    @Test
    fun passActivationClearsAnActiveTimedLease() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()
        viewModel.beginBrowserUsage()
        runCurrent()
        assertTrue(repository.currentSnapshot().leaseActive)

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 3_500_000L),
        )
        runCurrent()

        assertFalse(repository.currentSnapshot().leaseActive)
    }

    @Test
    fun passExpiresAtItsVerifiedExpiryWithoutAnotherFlowEmission() = runTest(testDispatcher) {
        // Intended contract: once a Safe Browse Pass's exact expiry passes, accessState
        // should stop reporting PassActive without requiring any unrelated event. The
        // current combine(_ledgerState, passEntitlementState) only re-evaluates isValidAt()
        // when one of those two upstream flows actually emits a new value -- neither does
        // on the mere passage of wall-clock time, so PassActive can visibly outlive its own
        // expiry until some incidental ledger or repository write happens to occur. This is
        // a genuine Phase 4 gap distinct from (and not fixed by) the exact-expiry repair
        // already made to SafeBrowsePassEntitlement.isValidAt() itself. Expected to FAIL.
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = testDispatcher.scheduler.currentTime + 500L),
        )
        runCurrent()
        assertTrue(viewModel.accessState.value is SafeBrowseAccessState.PassActive)

        advanceTimeBy(1_000L)
        runCurrent()

        // Exact Pass expiry with no new reward granted must become Locked, not silently
        // stay PassActive.
        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)
    }

    @Test
    fun passExpiryDoesNotRestoreThePreExistingTimedBalance() = runTest(testDispatcher) {
        // Even setting aside the reactivity gap above: whenever accessState IS re-evaluated
        // after a Pass lapses, the pre-Pass timed balance (already cleared at activation)
        // must never resurface -- Locked, never Active(TwoHourGrantMillis).
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()
        viewModel.grantReward("receipt-1")
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = testDispatcher.scheduler.currentTime + 500L),
        )
        runCurrent()

        advanceTimeBy(1_000L)
        // A second, unrelated repository write forces accessState to re-evaluate against
        // the now-lapsed Pass, isolating this assertion from the separate reactivity gap.
        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = false, expiryTimeMillis = 0L),
        )
        runCurrent()

        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)
    }

    @Test
    fun anInactiveOrExpiredCachedPassNeverOverridesTheLedgerState() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val viewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 1L),
        )
        runCurrent()

        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = false, expiryTimeMillis = 5_000_000L),
        )
        runCurrent()

        assertEquals(SafeBrowseAccessState.Locked, viewModel.accessState.value)
    }

    @Test
    fun passActiveNeverStartsTheTicker() = runTest(testDispatcher) {
        // beginBrowserUsage() while PassActive must never start the timed ledger's ticker --
        // there is nothing to meter while a Pass is active. This is enforced at the browser
        // route layer (SafeBrowseBrowserLifecycleSourceTest), but the ledger itself must
        // also never silently accrue a lease if called directly while a Pass is active.
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 5_000_000L),
        )
        runCurrent()

        viewModel.beginBrowserUsage()
        runCurrent()

        assertFalse(
            "beginBrowserUsage() must not start a timed lease while a Pass is active",
            repository.currentSnapshot().leaseActive,
        )
    }

    @Test
    fun aRewardReceiptGrantedWhileAPassIsActiveRemainsStoredNotUsable() = runTest(testDispatcher) {
        // Corrected contract: the reward is never "usable" while a Pass is active (the UI
        // keeps reporting PassActive throughout, never a timed balance) -- it is only
        // durably stored for later use once the Pass eventually lapses.
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 4_000_000L),
        )
        runCurrent()
        assertEquals(SafeBrowseAccessState.PassActive(4_000_000L), viewModel.accessState.value)

        viewModel.grantReward("receipt-under-pass")
        runCurrent()
        assertEquals(
            "the reward must never become visibly usable while the Pass remains active",
            SafeBrowseAccessState.PassActive(4_000_000L),
            viewModel.accessState.value,
        )

        val ledgerSnapshot = repository.currentSnapshot()
        assertEquals(0L, ledgerSnapshot.remainingMillis)
    }

    @Test
    fun aDuplicateReceiptSubmittedAfterPassExpiryCannotGrantAgain() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        // Redeem once, before any Pass exists.
        viewModel.grantReward("receipt-duplicate-check")
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = testDispatcher.scheduler.currentTime + 500L),
        )
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        // Replaying the exact same receipt token after the Pass has lapsed must be rejected
        // as a duplicate -- it must never grant a second time.
        val result = repository.grantReward("receipt-duplicate-check")
        assertTrue(result is SafeBrowseRewardGrantResult.Duplicate)
    }

    @Test
    fun rewardAndPassActivationRacingEachOtherLeavesPassActiveWithAZeroTimedBalance() = runTest(testDispatcher) {
        val repository = newRepository()
        val passRepository = newPassRepository()
        val viewModel = newViewModel(repository, passRepository)
        advanceTimeBy(1)
        runCurrent()

        // Both the reward grant and the Pass activation land in the same dispatcher batch.
        viewModel.grantReward("receipt-race")
        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 7_000_000L),
        )
        runCurrent()

        assertEquals(SafeBrowseAccessState.PassActive(7_000_000L), viewModel.accessState.value)
        assertEquals(0L, repository.currentSnapshot().remainingMillis)
    }

    @Test
    fun impulsivePlusEntitlementNeverActivatesASafeBrowsePass() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/session/safebrowse/SafeBrowseAccessViewModel.kt",
        ).readText()
        assertFalse(source.contains("PremiumRepository"))
        assertFalse(source.contains("PremiumEntitlement"))
    }

    @Test
    fun aSafeBrowsePassNeverActivatesWebsiteProtection() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/session/safebrowse/SafeBrowseAccessViewModel.kt",
        ).readText()
        assertFalse(source.contains("WebsiteProtectionSetupState"))
        assertFalse(source.contains("ProtectionSetupViewModel"))
    }

    @Test
    fun aPassSharedAcrossSeparateViewModelInstancesReflectsTheSamePersistedEntitlement() = runTest(testDispatcher) {
        val passRepository = newPassRepository()
        val firstViewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()

        passRepository.setEntitlement(
            SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 6_000_000L),
        )
        runCurrent()
        assertEquals(SafeBrowseAccessState.PassActive(6_000_000L), firstViewModel.accessState.value)

        val secondViewModel = newViewModel(passRepository = passRepository)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(SafeBrowseAccessState.PassActive(6_000_000L), secondViewModel.accessState.value)
    }
}
