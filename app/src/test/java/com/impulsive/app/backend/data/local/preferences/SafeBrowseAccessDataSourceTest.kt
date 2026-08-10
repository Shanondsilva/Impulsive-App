package com.impulsive.app.backend.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.impulsive.app.backend.domain.model.safebrowse.MaximumInterruptedLeaseChargeMillis
import com.impulsive.app.backend.domain.model.safebrowse.MaximumRewardReceiptCount
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseRewardGrantResult
import com.impulsive.app.backend.domain.model.safebrowse.TwoHourGrantMillis
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafeBrowseAccessDataSourceTest {

    private fun newFile(): File {
        val directory = Files.createTempDirectory("safe-browse-access").toFile()
        return File(directory, "safe_browse_access.preferences_pb")
    }

    private fun newSource(file: File): Pair<SafeBrowseAccessDataSource, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return SafeBrowseAccessDataSource(dataStore) to scope
    }

    // Re-declared with the exact same names as the private keys inside
    // SafeBrowseAccessDataSource -- DataStore preference keys are identified by
    // name+type, so these address the same underlying storage.
    private val remainingMillisKey = longPreferencesKey("safe_browse_remaining_millis")
    private val leaseActiveKey = booleanPreferencesKey("safe_browse_lease_active")
    private val leaseBaselineElapsedKey = longPreferencesKey("safe_browse_lease_baseline_elapsed")
    private val leaseBaselineEpochKey = longPreferencesKey("safe_browse_lease_baseline_epoch")
    private val rewardTokensKey = stringPreferencesKey("safe_browse_reward_tokens")

    @Test
    fun firstRewardGrantsExactlyTwoHours() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            val result = source.grantReward(
                receiptToken = "token-1",
                nowElapsedMillis = 0L,
                nowEpochMillis = 0L,
            )
            assertTrue(result is SafeBrowseRewardGrantResult.Granted)
            assertEquals(TwoHourGrantMillis, (result as SafeBrowseRewardGrantResult.Granted).remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateReceiptReturnsDuplicate() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            val second = source.grantReward("token-1", nowElapsedMillis = 1_000L, nowEpochMillis = 1_000L)
            assertTrue(second is SafeBrowseRewardGrantResult.Duplicate)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateReceiptDoesNotChangeRemainingTime() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.checkpointUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L) // no-op, no active lease
            val before = source.currentSnapshot().remainingMillis
            val duplicate = source.grantReward("token-1", nowElapsedMillis = 5_000L, nowEpochMillis = 5_000L)
                as SafeBrowseRewardGrantResult.Duplicate
            assertEquals(before, duplicate.remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun secondDistinctRewardDoesNotStackAboveTwoHours() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            val second = source.grantReward("token-2", nowElapsedMillis = 1_000L, nowEpochMillis = 1_000L)
                as SafeBrowseRewardGrantResult.Granted
            assertEquals(TwoHourGrantMillis, second.remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun usageCheckpointDeductsElapsedTime() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            val snapshot = source.checkpointUsage(nowElapsedMillis = 15_000L, nowEpochMillis = 15_000L)
            assertEquals(TwoHourGrantMillis - 15_000L, snapshot.remainingMillis)
            assertTrue(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun endUsageDeductsTheRemainingPartialInterval() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            val snapshot = source.endUsage(nowElapsedMillis = 7_000L, nowEpochMillis = 7_000L)
            assertEquals(TwoHourGrantMillis - 7_000L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun zeroBalanceClearsActiveLease() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", grantMillis = 5_000L, nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            val snapshot = source.checkpointUsage(nowElapsedMillis = 10_000L, nowEpochMillis = 10_000L)
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun beginUsageWithZeroDoesNothing() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            val snapshot = source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun repeatedBeginUsageIsIdempotent() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            // A second begin call at a later time must NOT reset the baseline.
            source.beginUsage(nowElapsedMillis = 5_000L, nowEpochMillis = 5_000L)
            val snapshot = source.checkpointUsage(nowElapsedMillis = 10_000L, nowEpochMillis = 10_000L)
            assertEquals(TwoHourGrantMillis - 10_000L, snapshot.remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun interruptedLeaseChargesAtMostThirtySeconds() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            // Simulate a process kill: reconcile a long time later without ever calling endUsage.
            val snapshot = source.reconcileInterruptedLease(
                nowElapsedMillis = 10 * 60_000L,
                nowEpochMillis = 10 * 60_000L,
            )
            assertEquals(TwoHourGrantMillis - MaximumInterruptedLeaseChargeMillis, snapshot.remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun interruptedLeaseClearsAfterReconciliation() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.reconcileInterruptedLease(nowElapsedMillis = 60_000L, nowEpochMillis = 60_000L)
            val snapshot = source.currentSnapshot()
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun invalidClockValuesNeverIncreaseBalance() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 100_000L, nowEpochMillis = 100_000L)
            source.beginUsage(nowElapsedMillis = 100_000L, nowEpochMillis = 100_000L)
            // Elapsed realtime goes backwards (reboot signal); epoch also moves backwards --
            // never a legitimate wall-clock delta, so the fallback must still be non-negative
            // and capped, never granting more access than existed before.
            val before = source.currentSnapshot().remainingMillis
            val snapshot = source.reconcileInterruptedLease(
                nowElapsedMillis = 0L,
                nowEpochMillis = 0L,
            )
            assertTrue(snapshot.remainingMillis <= before)
            assertTrue(snapshot.remainingMillis >= before - MaximumInterruptedLeaseChargeMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun receiptLedgerIsBoundedToOneHundred() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            repeat(MaximumRewardReceiptCount + 10) { index ->
                source.grantReward("token-$index", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            }
            // The very first token must have been evicted, so redeeming it again grants
            // (Granted), not (Duplicate) -- proving the ledger truly dropped it.
            val result = source.grantReward("token-0", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            assertTrue(result is SafeBrowseRewardGrantResult.Granted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun dataStoreRecreationRetainsBalanceAndReceipts() = runBlocking {
        val file = newFile()
        val (firstSource, firstScope) = newSource(file)
        firstSource.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
        firstScope.cancel()
        firstScope.coroutineContext[Job]?.join()

        val (secondSource, secondScope) = newSource(file)
        try {
            val snapshot = secondSource.currentSnapshot()
            assertEquals(TwoHourGrantMillis, snapshot.remainingMillis)

            val duplicate = secondSource.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            assertTrue(duplicate is SafeBrowseRewardGrantResult.Duplicate)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun malformedReceiptJsonFailsClosedAndGrantsNothing() = runBlocking {
        val file = newFile()
        corruptRewardTokens(file, "not valid json")

        val (source, scope) = newSource(file)
        try {
            var threw = false
            try {
                source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            } catch (expected: IllegalStateException) {
                threw = true
            }
            assertTrue("malformed JSON must throw rather than fail open", threw)

            val remaining = source.currentSnapshot().remainingMillis
            assertEquals(0L, remaining)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nonStringArrayElementFailsClosed() = runBlocking {
        val file = newFile()
        corruptRewardTokens(file, "[1,2,3]")

        val (source, scope) = newSource(file)
        try {
            var threw = false
            try {
                source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            } catch (expected: IllegalStateException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun blankStoredTokenFailsClosed() = runBlocking {
        val file = newFile()
        corruptRewardTokens(file, "[\"   \"]")

        val (source, scope) = newSource(file)
        try {
            var threw = false
            try {
                source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            } catch (expected: IllegalStateException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun oversizedStoredTokenFailsClosed() = runBlocking {
        val file = newFile()
        val oversized = "a".repeat(500)
        corruptRewardTokens(file, "[\"$oversized\"]")

        val (source, scope) = newSource(file)
        try {
            var threw = false
            try {
                source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            } catch (expected: IllegalStateException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun missingElapsedBaselineNeverDefaultsToNowAndChargesTheCappedAmount() = runBlocking {
        val file = newFile()
        val (source, scope) = newSource(file)
        source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
        source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
        scope.cancel()
        scope.coroutineContext[Job]?.join()

        // Corrupt storage: lease marked active but elapsed baseline missing.
        removePreferenceKey(file, leaseBaselineElapsedKey)

        val (reopened, reopenedScope) = newSource(file)
        try {
            val snapshot = reopened.reconcileInterruptedLease(
                nowElapsedMillis = 10 * 60_000L,
                nowEpochMillis = 10 * 60_000L,
            )

            assertEquals(
                TwoHourGrantMillis - MaximumInterruptedLeaseChargeMillis,
                snapshot.remainingMillis,
            )
            assertFalse(snapshot.leaseActive)
        } finally {
            reopenedScope.cancel()
        }
    }

    @Test
    fun missingEpochBaselineNeverDefaultsToNowAndChargesTheCappedAmount() = runBlocking {
        val file = newFile()
        val (source, scope) = newSource(file)
        source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
        source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
        scope.cancel()
        scope.coroutineContext[Job]?.join()

        removePreferenceKey(file, leaseBaselineEpochKey)

        val (reopened, reopenedScope) = newSource(file)
        try {
            val snapshot = reopened.reconcileInterruptedLease(
                nowElapsedMillis = 10 * 60_000L,
                nowEpochMillis = 10 * 60_000L,
            )

            assertEquals(
                TwoHourGrantMillis - MaximumInterruptedLeaseChargeMillis,
                snapshot.remainingMillis,
            )
            assertFalse(snapshot.leaseActive)
        } finally {
            reopenedScope.cancel()
        }
    }

    @Test
    fun negativeBaselineChargesTheCappedAmountAndNeverIncreasesBalance() = runBlocking {
        val file = newFile()
        val (source, scope) = newSource(file)
        source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
        source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)
        val before = source.currentSnapshot().remainingMillis
        scope.cancel()
        scope.coroutineContext[Job]?.join()

        val corruptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = corruptScope, produceFile = { file })
        dataStore.edit { preferences ->
            preferences[leaseBaselineElapsedKey] = -1L
            preferences[leaseBaselineEpochKey] = -1L
        }
        corruptScope.cancel()
        corruptScope.coroutineContext[Job]?.join()

        val (reopened, reopenedScope) = newSource(file)
        try {
            val snapshot = reopened.reconcileInterruptedLease(
                nowElapsedMillis = 10 * 60_000L,
                nowEpochMillis = 10 * 60_000L,
            )

            assertTrue(snapshot.remainingMillis <= before)
            assertEquals(before - MaximumInterruptedLeaseChargeMillis, snapshot.remainingMillis)
        } finally {
            reopenedScope.cancel()
        }
    }

    @Test
    fun corruptReceiptStorageGrantsNothingAndLeavesBalanceUnchanged() = runBlocking {
        val file = newFile()
        val (source, scope) = newSource(file)
        source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
        val before = source.currentSnapshot().remainingMillis
        scope.cancel()
        scope.coroutineContext[Job]?.join()

        corruptRewardTokens(file, "{not even an array}")

        val (reopened, reopenedScope) = newSource(file)
        try {
            try {
                reopened.grantReward("token-2", nowElapsedMillis = 0L, nowEpochMillis = 0L)
                fail("expected corrupted receipt storage to throw")
            } catch (expected: IllegalStateException) {
                // expected
            }

            assertEquals(before, reopened.currentSnapshot().remainingMillis)
        } finally {
            reopenedScope.cancel()
        }
    }

    private suspend fun corruptRewardTokens(file: File, rawValue: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        dataStore.edit { preferences ->
            preferences[rewardTokensKey] = rawValue
        }
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private suspend fun removePreferenceKey(file: File, key: androidx.datastore.preferences.core.Preferences.Key<Long>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    @Test
    fun clearTimedAccessForPassActivationZeroesBalanceAndClearsAnActiveLease() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)

            val snapshot = source.clearTimedAccessForPassActivation()

            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)

            val persisted = source.currentSnapshot()
            assertEquals(0L, persisted.remainingMillis)
            assertFalse(persisted.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearTimedAccessForPassActivationNeverConsumesRewardReceiptTokens() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            source.clearTimedAccessForPassActivation()

            // The same receipt token must still be rejected as a duplicate -- clearing
            // timed access must never let an already-redeemed rewarded-ad receipt be
            // granted again.
            val duplicate = source.grantReward("token-1", nowElapsedMillis = 0L, nowEpochMillis = 0L)
            assertTrue(duplicate is SafeBrowseRewardGrantResult.Duplicate)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearTimedAccessForPassActivationIsIdempotentWhenAlreadyEmpty() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            val snapshot = source.clearTimedAccessForPassActivation()
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun suppressedRewardStoresReceiptButKeepsTimedAccessZero() = runBlocking {
        val (source, scope) = newSource(newFile())

        try {
            val first = source.grantReward(
                receiptToken = "pass-active-token",
                grantTimedAccess = false,
                nowElapsedMillis = 0L,
                nowEpochMillis = 0L,
            )

            assertTrue(first is SafeBrowseRewardGrantResult.Granted)
            assertEquals(0L, (first as SafeBrowseRewardGrantResult.Granted).remainingMillis)

            val snapshot = source.currentSnapshot()
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)

            val replay = source.grantReward(
                receiptToken = "pass-active-token",
                grantTimedAccess = true,
                nowElapsedMillis = 1_000L,
                nowEpochMillis = 1_000L,
            )

            assertTrue(replay is SafeBrowseRewardGrantResult.Duplicate)
            assertEquals(0L, (replay as SafeBrowseRewardGrantResult.Duplicate).remainingMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun suppressedRewardClearsExistingBalanceAndLease() = runBlocking {
        val (source, scope) = newSource(newFile())

        try {
            source.grantReward(
                receiptToken = "existing-timed-token",
                nowElapsedMillis = 0L,
                nowEpochMillis = 0L,
            )
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)

            val suppressed = source.grantReward(
                receiptToken = "new-pass-token",
                grantTimedAccess = false,
                nowElapsedMillis = 5_000L,
                nowEpochMillis = 5_000L,
            )

            assertTrue(suppressed is SafeBrowseRewardGrantResult.Granted)
            assertEquals(0L, (suppressed as SafeBrowseRewardGrantResult.Granted).remainingMillis)

            val snapshot = source.currentSnapshot()
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)

            val reconciled = source.reconcileInterruptedLease(
                nowElapsedMillis = 60_000L,
                nowEpochMillis = 60_000L,
            )

            assertEquals(0L, reconciled.remainingMillis)
            assertFalse(reconciled.leaseActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateSuppressedRewardAlsoClearsStaleLease() = runBlocking {
        val (source, scope) = newSource(newFile())

        try {
            source.grantReward(
                receiptToken = "duplicate-pass-token",
                nowElapsedMillis = 0L,
                nowEpochMillis = 0L,
            )
            source.beginUsage(nowElapsedMillis = 0L, nowEpochMillis = 0L)

            val duplicate = source.grantReward(
                receiptToken = "duplicate-pass-token",
                grantTimedAccess = false,
                nowElapsedMillis = 2_000L,
                nowEpochMillis = 2_000L,
            )

            assertTrue(duplicate is SafeBrowseRewardGrantResult.Duplicate)
            assertEquals(0L, (duplicate as SafeBrowseRewardGrantResult.Duplicate).remainingMillis)

            val snapshot = source.currentSnapshot()
            assertEquals(0L, snapshot.remainingMillis)
            assertFalse(snapshot.leaseActive)
        } finally {
            scope.cancel()
        }
    }
}
