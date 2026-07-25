package com.impulsive.app.backend.data.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBoundRestoreSnapshotRefresherTest {
    @Test
    fun noAuthenticatedAccountDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = null,
            completed = true,
            ownerUid = "user-a",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.NoAuthenticatedAccount, result)
        assertEquals(emptyList<String>(), writer.ownerUids)
    }

    @Test
    fun guestDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "guest", isAnonymous = true),
            completed = true,
            ownerUid = "guest",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.GuestNotApplicable, result)
        assertEquals(emptyList<String>(), writer.ownerUids)
    }

    @Test
    fun incompleteLocalOnboardingDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-a", isAnonymous = false),
            completed = false,
            ownerUid = "user-a",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.NoOwnedCompletedData, result)
        assertEquals(emptyList<String>(), writer.ownerUids)
    }

    @Test
    fun missingOwnerUidDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-a", isAnonymous = false),
            completed = true,
            ownerUid = null,
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.NoOwnedCompletedData, result)
        assertEquals(emptyList<String>(), writer.ownerUids)
    }

    @Test
    fun differentCurrentUidDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-b", isAnonymous = false),
            completed = true,
            ownerUid = "user-a",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.AccountMismatch, result)
        assertEquals(emptyList<String>(), writer.ownerUids)
    }

    @Test
    fun matchingUidWritesExactUidAndNotifiesBackupManager() = runBlocking {
        val writer = RecordingWriter()
        var backupNotifications = 0
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-a", isAnonymous = false),
            completed = true,
            ownerUid = "user-a",
            writer = writer,
            backupChangeNotifier = BackupChangeNotifier { backupNotifications += 1 },
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.Written, result)
        assertEquals(listOf("user-a"), writer.ownerUids)
        assertEquals(1, backupNotifications)
    }

    @Test
    fun successfulWriterReturnsWritten() = runBlocking {
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-a", isAnonymous = false),
            completed = true,
            ownerUid = "user-a",
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.Written, result)
    }

    @Test
    fun failingWriterReturnsFailed() = runBlocking {
        val cause = IllegalStateException("disk full")
        val result = refresher(
            account = RestoreSnapshotAccount(uid = "user-a", isAnonymous = false),
            completed = true,
            ownerUid = "user-a",
            writer = RestoreSnapshotWriter { throw cause },
        ).refresh()

        assertTrue(result is RestoreSnapshotRefreshResult.Failed)
        assertEquals(cause, (result as RestoreSnapshotRefreshResult.Failed).cause)
    }

    private fun refresher(
        account: RestoreSnapshotAccount?,
        completed: Boolean,
        ownerUid: String?,
        writer: RestoreSnapshotWriter = RecordingWriter(),
        backupChangeNotifier: BackupChangeNotifier = BackupChangeNotifier {},
    ): AccountBoundRestoreSnapshotRefresher = AccountBoundRestoreSnapshotRefresher(
        accountProvider = object : RestoreSnapshotAccountProvider {
            override fun currentAccount(): RestoreSnapshotAccount? = account
        },
        ownerStateDataSource = FakeOwnerStateDataSource(
            completed = completed,
            ownerUid = ownerUid,
        ),
        writer = writer,
        backupChangeNotifier = backupChangeNotifier,
    )
}

private class RecordingWriter : RestoreSnapshotWriter {
    val ownerUids = mutableListOf<String>()

    override suspend fun write(ownerUid: String) {
        ownerUids += ownerUid
    }
}

private class FakeOwnerStateDataSource(
    completed: Boolean,
    ownerUid: String?,
) : RestoreSnapshotOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = MutableStateFlow(completed)
    override val completedAccountUid: Flow<String?> = MutableStateFlow(ownerUid)
}
