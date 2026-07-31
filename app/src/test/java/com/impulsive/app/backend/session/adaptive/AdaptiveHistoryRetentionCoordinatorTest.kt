package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveHistoryRetentionCoordinatorTest {
    @Test
    fun keepUntilResetPerformsNoRecoveryPruneOrRefresh() = runBlocking {
        val fixture = fixture(
            policy = AdaptiveHistoryRetentionPolicy.KeepUntilReset,
        )

        val result = fixture.coordinator.runBounded()

        assertEquals(null, result.cutoffMillis)
        assertEquals(0, fixture.store.calls)
        assertEquals(0, fixture.recoveryCalls)
        assertEquals(0, fixture.backupRequests)
    }

    @Test
    fun deletionCancelsOnlyRemovedDecisionWorkAndCoalescesOneRefresh() = runBlocking {
        val fixture = fixture()
        fixture.store.next = AdaptiveRetentionDeletionBatch(
            decisionIds = listOf("old-a", "old-b"),
            rehearsalIds = listOf("old-practice"),
        )

        val result = fixture.coordinator.runBounded()

        assertEquals(listOf("old-a", "old-b"), result.deletedDecisionIds)
        assertEquals(listOf("old-practice"), result.deletedRehearsalIds)
        assertEquals(listOf("old-a", "old-b"), fixture.cancelledWork)
        assertEquals(1, fixture.backupRequests)
        assertEquals(setOf("old-a", "old-b"), fixture.safety.cleared)
    }

    @Test
    fun noDeletionPreservesWorkAndDoesNotRefreshBackup() = runBlocking {
        val fixture = fixture()

        fixture.coordinator.runBounded()

        assertTrue(fixture.cancelledWork.isEmpty())
        assertEquals(0, fixture.backupRequests)
    }

    @Test
    fun activeFeedbackRouteAndPendingNavigationAreProtected() = runBlocking {
        val fixture = fixture()
        fixture.safety.protected = setOf("feedback", "active-route", "pending")

        fixture.coordinator.runBounded()

        assertEquals(
            fixture.safety.protected,
            fixture.store.protectedDecisionIds,
        )
    }

    @Test
    fun restoreInProgressSkipsAllMutation() = runBlocking {
        val fixture = fixture()
        fixture.safety.restoring = true

        val result = fixture.coordinator.runBounded()

        assertTrue(result.skippedBecauseRestoreActive)
        assertEquals(0, fixture.store.calls)
        assertEquals(0, fixture.recoveryCalls)
    }

    @Test
    fun overdueObservationRecoveryRunsBeforeTransactionalPrune() = runBlocking {
        val order = mutableListOf<String>()
        val fixture = fixture(order)

        fixture.coordinator.runBounded()

        assertEquals(listOf("recover", "prune"), order)
    }

    @Test
    fun invalidClockFailsSafelyWithoutPruning() = runBlocking {
        val fixture = fixture(nowMillis = -1L)

        val result = fixture.coordinator.runBounded()

        assertTrue(result.failedSafely)
        assertEquals(0, fixture.store.calls)
    }

    @Test
    fun transactionalStoreFailureHasNoCancellationOrBackupSideEffects() = runBlocking {
        val fixture = fixture()
        fixture.store.failure = IllegalStateException("transaction rolled back")

        val result = fixture.coordinator.runBounded()

        assertTrue(result.failedSafely)
        assertTrue(fixture.cancelledWork.isEmpty())
        assertEquals(0, fixture.backupRequests)
    }

    @Test
    fun repeatedEmptyCleanupIsIdempotent() = runBlocking {
        val fixture = fixture()

        val first = fixture.coordinator.runBounded()
        val second = fixture.coordinator.runBounded()

        assertFalse(first.failedSafely)
        assertFalse(second.failedSafely)
        assertEquals(2, fixture.store.calls)
        assertEquals(0, fixture.backupRequests)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unboundedCleanupLimitIsRejected() {
        runBlocking {
            fixture().coordinator.runBounded(Int.MAX_VALUE)
        }
    }

    private fun fixture(
        order: MutableList<String> = mutableListOf(),
        policy: AdaptiveHistoryRetentionPolicy =
            AdaptiveHistoryRetentionPolicy.SixMonths,
        nowMillis: Long = 40_000_000_000L,
    ): Fixture {
        val preferences = FakePreferences(policy)
        val store = FakeStore(order)
        val safety = FakeSafety()
        val cancelled = mutableListOf<String>()
        var recoveryCalls = 0
        var backupRequests = 0
        val coordinator = AdaptiveHistoryRetentionCoordinator(
            preferences = preferences,
            store = store,
            observationRecovery = AdaptiveRetentionObservationRecovery {
                recoveryCalls++
                order += "recover"
            },
            workCanceller = AdaptiveRetentionWorkCanceller(cancelled::add),
            backupRequester = AdaptiveRetentionBackupRequester { backupRequests++ },
            safetyState = safety,
            clock = object : AdaptiveClock {
                override fun nowMillis(): Long = nowMillis
            },
        )
        return Fixture(
            coordinator,
            store,
            safety,
            cancelled,
            recoveryCallsValue = { recoveryCalls },
            backupRequestsValue = { backupRequests },
        )
    }

    private class FakePreferences(
        policy: AdaptiveHistoryRetentionPolicy,
    ) : AdaptivePreferenceRepository {
        private var value = AdaptivePreferences(historyRetentionPolicy = policy)
        override fun observe(): Flow<AdaptivePreferences> = flowOf(value)
        override suspend fun get(): AdaptivePreferences = value
        override suspend fun insertDefaults(updatedAtMillis: Long) = Unit
        override suspend fun update(
            preferences: AdaptivePreferences,
            updatedAtMillis: Long,
        ) {
            value = preferences
        }
        override suspend fun resetDefaults(updatedAtMillis: Long) {
            value = AdaptivePreferences()
        }
    }

    private class FakeStore(
        private val order: MutableList<String>,
    ) : AdaptiveRetentionStore {
        var calls = 0
        var protectedDecisionIds = emptySet<String>()
        var next = AdaptiveRetentionDeletionBatch()
        var failure: Throwable? = null

        override suspend fun prune(
            cutoffMillis: Long,
            protectedDecisionIds: Set<String>,
            limit: Int,
        ): AdaptiveRetentionDeletionBatch {
            calls++
            order += "prune"
            this.protectedDecisionIds = protectedDecisionIds
            failure?.let { throw it }
            return next
        }
    }

    private class FakeSafety : AdaptiveRetentionSafetyState {
        var protected = emptySet<String>()
        var restoring = false
        var cleared = emptySet<String>()
        override fun protectedDecisionIds(): Set<String> = protected
        override fun restoreInProgress(): Boolean = restoring
        override fun clearDeletedReferences(decisionIds: Set<String>) {
            cleared = decisionIds
        }
    }

    private data class Fixture(
        val coordinator: AdaptiveHistoryRetentionCoordinator,
        val store: FakeStore,
        val safety: FakeSafety,
        val cancelledWork: List<String>,
        private val recoveryCallsValue: () -> Int,
        private val backupRequestsValue: () -> Int,
    ) {
        val recoveryCalls: Int get() = recoveryCallsValue()
        val backupRequests: Int get() = backupRequestsValue()
    }
}
