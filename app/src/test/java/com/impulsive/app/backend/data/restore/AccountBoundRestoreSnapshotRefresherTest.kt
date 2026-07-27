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
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun guestDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "guest",
                isAnonymous = true,
                hasGoogleProvider = false,
                googleSubjectHash = null,
            ),
            completed = true,
            ownerUid = "guest",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.GuestNotApplicable, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun incompleteLocalOnboardingDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = ValidGoogleSubjectHash,
            ),
            completed = false,
            ownerUid = "user-a",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.NoOwnedCompletedData, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun missingOwnerUidDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = ValidGoogleSubjectHash,
            ),
            completed = true,
            ownerUid = null,
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.NoOwnedCompletedData, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun differentCurrentUidDoesNotWrite() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-b",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = ValidGoogleSubjectHash,
            ),
            completed = true,
            ownerUid = "user-a",
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.AccountMismatch, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun exactUidPlusCurrentValidGoogleHashWritesBothValuesAndNotifiesBackupManager() = runBlocking {
        val writer = RecordingWriter()
        var backupNotifications = 0
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = ValidGoogleSubjectHash,
            ),
            completed = true,
            ownerUid = "user-a",
            ownerGoogleSubjectHash = ValidGoogleSubjectHash,
            writer = writer,
            backupChangeNotifier = BackupChangeNotifier { backupNotifications += 1 },
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.Written, result)
        assertEquals(
            listOf(
                WriteRecord(
                    ownerUid = "user-a",
                    ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                ),
            ),
            writer.records,
        )
        assertEquals(1, backupNotifications)
    }

    @Test
    fun nonGoogleAccountWritesNullHash() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = false,
                googleSubjectHash = null,
            ),
            completed = true,
            ownerUid = "user-a",
            ownerGoogleSubjectHash = null,
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.Written, result)
        assertEquals(listOf(WriteRecord(ownerUid = "user-a", ownerGoogleSubjectHash = null)), writer.records)
    }

    @Test
    fun savedAndCurrentDifferentGoogleHashesReturnAccountMismatch() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = OtherValidGoogleSubjectHash,
            ),
            completed = true,
            ownerUid = "user-a",
            ownerGoogleSubjectHash = ValidGoogleSubjectHash,
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.AccountMismatch, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun invalidCurrentGoogleHashIsNotWritten() = runBlocking {
        val writer = RecordingWriter()
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = "not-a-valid-hash",
            ),
            completed = true,
            ownerUid = "user-a",
            ownerGoogleSubjectHash = null,
            writer = writer,
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.GoogleIdentityUnavailable, result)
        assertEquals(emptyList<WriteRecord>(), writer.records)
    }

    @Test
    fun backupNotificationOccursOnlyAfterSuccessfulWriterCompletion() = runBlocking {
        val cause = IllegalStateException("disk full")
        var backupNotifications = 0
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = true,
                googleSubjectHash = ValidGoogleSubjectHash,
            ),
            completed = true,
            ownerUid = "user-a",
            ownerGoogleSubjectHash = ValidGoogleSubjectHash,
            writer = RestoreSnapshotWriter { _, _ -> throw cause },
            backupChangeNotifier = BackupChangeNotifier { backupNotifications += 1 },
        ).refresh()

        assertTrue(result is RestoreSnapshotRefreshResult.Failed)
        assertEquals(cause, (result as RestoreSnapshotRefreshResult.Failed).cause)
        assertEquals(0, backupNotifications)
    }

    @Test
    fun successfulWriterReturnsWritten() = runBlocking {
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = false,
                googleSubjectHash = null,
            ),
            completed = true,
            ownerUid = "user-a",
        ).refresh()

        assertEquals(RestoreSnapshotRefreshResult.Written, result)
    }

    @Test
    fun failingWriterReturnsFailed() = runBlocking {
        val cause = IllegalStateException("disk full")
        val result = refresher(
            account = RestoreSnapshotAccount(
                uid = "user-a",
                isAnonymous = false,
                hasGoogleProvider = false,
                googleSubjectHash = null,
            ),
            completed = true,
            ownerUid = "user-a",
            writer = RestoreSnapshotWriter { _, _ -> throw cause },
        ).refresh()

        assertTrue(result is RestoreSnapshotRefreshResult.Failed)
        assertEquals(cause, (result as RestoreSnapshotRefreshResult.Failed).cause)
    }

    private fun refresher(
        account: RestoreSnapshotAccount?,
        completed: Boolean,
        ownerUid: String?,
        ownerGoogleSubjectHash: String? = null,
        writer: RestoreSnapshotWriter = RecordingWriter(),
        backupChangeNotifier: BackupChangeNotifier = BackupChangeNotifier {},
    ): AccountBoundRestoreSnapshotRefresher = AccountBoundRestoreSnapshotRefresher(
        accountProvider = object : RestoreSnapshotAccountProvider {
            override fun currentAccount(): RestoreSnapshotAccount? = account
        },
        ownerStateDataSource = FakeOwnerStateDataSource(
            completed = completed,
            ownerUid = ownerUid,
            ownerGoogleSubjectHash = ownerGoogleSubjectHash,
        ),
        writer = writer,
        backupChangeNotifier = backupChangeNotifier,
    )

    private companion object {
        const val ValidGoogleSubjectHash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherValidGoogleSubjectHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}

private data class WriteRecord(
    val ownerUid: String,
    val ownerGoogleSubjectHash: String?,
)

private class RecordingWriter : RestoreSnapshotWriter {
    val records = mutableListOf<WriteRecord>()

    override suspend fun write(
        ownerUid: String,
        ownerGoogleSubjectHash: String?,
    ) {
        records += WriteRecord(
            ownerUid = ownerUid,
            ownerGoogleSubjectHash = ownerGoogleSubjectHash,
        )
    }
}

private class FakeOwnerStateDataSource(
    completed: Boolean,
    ownerUid: String?,
    ownerGoogleSubjectHash: String?,
) : RestoreSnapshotOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = MutableStateFlow(completed)
    override val completedAccountUid: Flow<String?> = MutableStateFlow(ownerUid)
    override val completedGoogleSubjectHash: Flow<String?> =
        MutableStateFlow(ownerGoogleSubjectHash)
}