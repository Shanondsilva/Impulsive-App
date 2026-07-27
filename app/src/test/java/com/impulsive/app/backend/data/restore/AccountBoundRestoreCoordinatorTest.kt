package com.impulsive.app.backend.data.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBoundRestoreCoordinatorTest {
    @Test
    fun noAuthenticatedUserReturnsNotAuthenticatedWithoutImporting() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = null,
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.NotAuthenticated, result)
        assertEquals(0, importer.calls)
    }

    @Test
    fun anonymousUserReturnsGuestNotApplicableWithoutImporting() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "guest",
                isAnonymous = true,
            ),
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.GuestNotApplicable, result)
        assertEquals(0, importer.calls)
    }

    @Test
    fun matchingOwnerUidAllowsImporterToRun() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.Restored, result)
        assertEquals(1, importer.calls)
    }

    @Test
    fun differentOwnerUidBlocksImport() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-b",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.AccountMismatch, result)
        assertEquals(0, importer.calls)
    }

    @Test
    fun completedLegacyDataWithoutOwnerBlocksAutomaticImport() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            completed = true,
            ownerUid = null,
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.LegacyUnownedBackup, result)
        assertEquals(0, importer.calls)
    }

    @Test
    fun invalidBundleMapsToInvalidBackup() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.InvalidBundle)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.InvalidBackup, result)
        assertEquals(1, importer.calls)
    }

    @Test
    fun importerFailureMapsToFailedWithoutThrowing() = runBlocking {
        val cause = IllegalStateException("database unavailable")
        val importer = FakeAccountBoundRestoreImporter(
            AutoRestoreResult.Failed(cause),
        )
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.Failed(cause), result)
        assertSame(cause, (result as AccountBoundRestoreResult.Failed).cause)
        assertEquals(1, importer.calls)
    }

    @Test
    fun automaticOwnerMismatchMapsToAccountMismatch() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.OwnerMismatch)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.AccountMismatch, result)
        assertEquals(1, importer.calls)
        assertEquals(
            AutoRestoreOwnerProof.ExactUid(currentUid = "user-a"),
            importer.lastOwnerProof,
        )
    }

    @Test
    fun legacyUnownedAutomaticBundleMapsToLegacyUnownedBackup() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.LegacyUnownedBundle)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.LegacyUnownedBackup, result)
        assertEquals(1, importer.calls)
        assertEquals(
            AutoRestoreOwnerProof.ExactUid(currentUid = "user-a"),
            importer.lastOwnerProof,
        )
    }

    @Test
    fun legacyOwnerVerificationRequiredMapsToLegacyUnownedBackup() = runBlocking {
        val importer = FakeAccountBoundRestoreImporter(
            AutoRestoreResult.LegacyOwnerVerificationRequired,
        )
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount(
                uid = "user-a",
                isAnonymous = false,
            ),
            ownerUid = "user-a",
            importer = importer,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertEquals(AccountBoundRestoreResult.LegacyUnownedBackup, result)
        assertEquals(1, importer.calls)
        assertEquals(
            AutoRestoreOwnerProof.ExactUid(currentUid = "user-a"),
            importer.lastOwnerProof,
        )
    }

    @Test
    fun terminalResultsClearPendingClaimBeforeProvenanceAndReturnOriginalResult() = runBlocking {
        listOf(
            AutoRestoreResult.Restored to AccountBoundRestoreResult.Restored,
            AutoRestoreResult.NoBundle to AccountBoundRestoreResult.NothingToRestore,
            AutoRestoreResult.ExistingDataPresent to AccountBoundRestoreResult.ExistingLocalData,
        ).forEach { (importResult, expected) ->
            val events = mutableListOf<String>()
            val coordinator = coordinator(
                account = AccountBoundRestoreAccount("user-a", false),
                importer = FakeAccountBoundRestoreImporter(importResult),
                pendingCleanup = FakeAccountBoundPendingCleanup(events),
                provenance = FakeAccountBoundProvenance(events),
            )

            assertEquals(expected, coordinator.restoreForCurrentAuthenticatedAccount())
            assertEquals(listOf("clearClaim", "clearProvenance"), events)
        }
    }

    @Test
    fun earlyNothingToRestoreAlsoClearsTerminalMarkersInOrder() = runBlocking {
        val events = mutableListOf<String>()
        val importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount("user-a", false),
            completed = false,
            ownerUid = null,
            importer = importer,
            pendingCleanup = FakeAccountBoundPendingCleanup(events),
            provenance = FakeAccountBoundProvenance(events),
        )

        assertEquals(
            AccountBoundRestoreResult.NothingToRestore,
            coordinator.restoreForCurrentAuthenticatedAccount(),
        )
        assertEquals(0, importer.calls)
        assertEquals(listOf("clearClaim", "clearProvenance"), events)
    }

    @Test
    fun pendingClaimCleanupFailureDoesNotClearProvenance() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("claim cleanup failed")
        val provenance = FakeAccountBoundProvenance(events)
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount("user-a", false),
            importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.Restored),
            pendingCleanup = FakeAccountBoundPendingCleanup(events, failure),
            provenance = provenance,
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertSame(failure, (result as AccountBoundRestoreResult.Failed).cause)
        assertEquals(listOf("clearClaim"), events)
        assertFalse(provenance.cleared)
    }

    @Test
    fun provenanceCleanupFailureReturnsTruthfulFailure() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("provenance cleanup failed")
        val coordinator = coordinator(
            account = AccountBoundRestoreAccount("user-a", false),
            importer = FakeAccountBoundRestoreImporter(AutoRestoreResult.NoBundle),
            pendingCleanup = FakeAccountBoundPendingCleanup(events),
            provenance = FakeAccountBoundProvenance(events, failure),
        )

        val result = coordinator.restoreForCurrentAuthenticatedAccount()

        assertSame(failure, (result as AccountBoundRestoreResult.Failed).cause)
        assertEquals(listOf("clearClaim", "clearProvenance"), events)
    }

    @Test
    fun nonTerminalResultsDoNotClearEitherMarker() = runBlocking {
        listOf(
            AutoRestoreResult.OwnerMismatch,
            AutoRestoreResult.LegacyOwnerVerificationRequired,
            AutoRestoreResult.LegacyUnownedBundle,
            AutoRestoreResult.InvalidBundle,
            AutoRestoreResult.Failed(IllegalStateException("import failed")),
        ).forEach { importResult ->
            val events = mutableListOf<String>()
            val coordinator = coordinator(
                account = AccountBoundRestoreAccount("user-a", false),
                importer = FakeAccountBoundRestoreImporter(importResult),
                pendingCleanup = FakeAccountBoundPendingCleanup(events),
                provenance = FakeAccountBoundProvenance(events),
            )

            coordinator.restoreForCurrentAuthenticatedAccount()

            assertTrue("result=$importResult events=$events", events.isEmpty())
        }
    }

    private fun coordinator(
        account: AccountBoundRestoreAccount?,
        completed: Boolean = true,
        ownerUid: String? = "user-a",
        importer: FakeAccountBoundRestoreImporter,
        pendingCleanup: AccountBoundRestorePendingClaimCleanup =
            AccountBoundRestorePendingClaimCleanup { },
        provenance: AccountBoundRestoreProvenance =
            object : AccountBoundRestoreProvenance {
                override fun clearRestorePending() = Unit
            },
    ): AccountBoundRestoreCoordinator =
        AccountBoundRestoreCoordinator(
            accountProvider = AccountBoundRestoreAccountProvider { account },
            ownerStateDataSource = FakeAccountBoundRestoreOwnerStateDataSource(
                completed = completed,
                ownerUid = ownerUid,
            ),
            importer = importer,
            provenance = provenance,
            pendingClaimCleanup = pendingCleanup,
        )
}

private class FakeAccountBoundRestoreOwnerStateDataSource(
    completed: Boolean,
    ownerUid: String?,
) : AccountBoundRestoreOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = MutableStateFlow(completed)
    override val completedAccountUid: Flow<String?> = MutableStateFlow(ownerUid)
}

private class FakeAccountBoundRestoreImporter(
    private val result: AutoRestoreResult,
) : AccountBoundRestoreImporter {
    var calls = 0
    var lastOwnerProof: AutoRestoreOwnerProof? = null

    override suspend fun importIfNeeded(
        ownerProof: AutoRestoreOwnerProof,
    ): AutoRestoreResult {
        lastOwnerProof = ownerProof
        calls += 1
        return result
    }
}

private class FakeAccountBoundPendingCleanup(
    private val events: MutableList<String>,
    private val failure: Throwable? = null,
) : AccountBoundRestorePendingClaimCleanup {
    override fun clear() {
        events += "clearClaim"
        failure?.let { throw it }
    }
}

private class FakeAccountBoundProvenance(
    private val events: MutableList<String>,
    private val failure: Throwable? = null,
) : AccountBoundRestoreProvenance {
    var cleared = false

    override fun clearRestorePending() {
        events += "clearProvenance"
        failure?.let { throw it }
        cleared = true
    }
}
