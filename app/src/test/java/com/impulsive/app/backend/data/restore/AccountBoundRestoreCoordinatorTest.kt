package com.impulsive.app.backend.data.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        assertEquals("user-a", importer.lastExpectedOwnerUid)
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
        assertEquals("user-a", importer.lastExpectedOwnerUid)
    }

    private fun coordinator(
        account: AccountBoundRestoreAccount?,
        completed: Boolean = true,
        ownerUid: String? = "user-a",
        importer: FakeAccountBoundRestoreImporter,
    ): AccountBoundRestoreCoordinator =
        AccountBoundRestoreCoordinator(
            accountProvider = AccountBoundRestoreAccountProvider { account },
            ownerStateDataSource = FakeAccountBoundRestoreOwnerStateDataSource(
                completed = completed,
                ownerUid = ownerUid,
            ),
            importer = importer,
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
    var lastExpectedOwnerUid: String? = null

    override suspend fun importIfNeeded(
        expectedOwnerUid: String,
    ): AutoRestoreResult {
        lastExpectedOwnerUid = expectedOwnerUid
        calls += 1
        return result
    }
}