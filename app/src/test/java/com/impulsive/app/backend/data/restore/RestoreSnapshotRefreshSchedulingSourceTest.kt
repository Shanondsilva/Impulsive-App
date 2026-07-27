package com.impulsive.app.backend.data.restore

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSnapshotRefreshSchedulingSourceTest {
    @Test
    fun journalSuccessfulMutationsRequestSnapshotRefresh() {
        val source = source("src/main/java/com/impulsive/app/backend/data/repository/JournalRepository.kt")

        assertTrue(method(source, "upsertNote").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertTrue(method(source, "updateNote").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertTrue(method(source, "deleteNote").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertTrue(method(source, "deleteNotes").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertTrue(method(source, "moveNote").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
    }

    @Test
    fun journalRefreshRequestsAreAfterSuccessfulDaoMutationPoints() {
        val source = source("src/main/java/com/impulsive/app/backend/data/repository/JournalRepository.kt")

        assertOrdered(method(source, "upsertNote"), "dao.upsertNoteWithChecklist", "RestoreSnapshotRefreshScheduler.request(appContext)")
        assertOrdered(method(source, "updateNote"), "dao.update(note)", "RestoreSnapshotRefreshScheduler.request(appContext)")
        assertOrdered(method(source, "deleteNote"), "dao.deleteNoteWithTombstone", "RestoreSnapshotRefreshScheduler.request(appContext)")
        assertOrdered(method(source, "deleteNotes"), "dao.deleteNotesWithTombstones", "RestoreSnapshotRefreshScheduler.request(appContext)")
        assertOrdered(method(source, "moveNote"), "dao.reorder", "RestoreSnapshotRefreshScheduler.request(appContext)")
    }

    @Test
    fun journalDerivedUpdatesDoNotDoubleScheduleAndFeedbackPurgeIsExcluded() {
        val source = source("src/main/java/com/impulsive/app/backend/data/repository/JournalRepository.kt")

        assertFalse(method(source, "setPinned").contains("RestoreSnapshotRefreshScheduler.request"))
        assertFalse(method(source, "setHighlight").contains("RestoreSnapshotRefreshScheduler.request"))
        assertFalse(method(source, "setCategory").contains("RestoreSnapshotRefreshScheduler.request"))
        assertFalse(method(source, "purgeObsoleteFeedbackNotes").contains("RestoreSnapshotRefreshScheduler.request"))
    }

    @Test
    fun customUserDomainAdditionRequestsRefreshButDefaultSeedingDoesNot() {
        val source = source("src/main/java/com/impulsive/app/backend/data/repository/BlockedDomainRepository.kt")

        assertTrue(method(source, "addUserDomain").contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertFalse(method(source, "ensureSeeded").contains("RestoreSnapshotRefreshScheduler.request"))
    }

    @Test
    fun accountDeletionAwaitsRefreshCancellationBeforeDestructiveLocalDeletion() {
        val source = source("src/main/java/com/impulsive/app/backend/data/UserDataManager.kt")
        val deleteAllData = method(source, "deleteAllData")

        assertTrue(deleteAllData.contains("RestoreSnapshotRefreshScheduler.cancelAndAwait(context)"))
        assertFalse(deleteAllData.contains("RestoreSnapshotRefreshScheduler.cancel(context)"))
        assertOrdered(
            deleteAllData,
            "RestoreSnapshotRefreshScheduler.cancelAndAwait(context)",
            "database.clearAllTables()",
        )
        assertOrdered(
            deleteAllData,
            "RestoreSnapshotRefreshScheduler.cancelAndAwait(context)",
            "dataStoreDir.deleteRecursively()",
        )
        assertOrdered(
            deleteAllData,
            "RestoreSnapshotRefreshScheduler.cancelAndAwait(context)",
            "restoreDir.deleteRecursively()",
        )
        assertOrdered(
            deleteAllData,
            "database.clearAllTables()",
            "BackupManager(context.applicationContext).dataChanged()",
        )
        assertOrdered(
            deleteAllData,
            "dataStoreDir.deleteRecursively()",
            "BackupManager(context.applicationContext).dataChanged()",
        )
        assertOrdered(
            deleteAllData,
            "restoreDir.deleteRecursively()",
            "BackupManager(context.applicationContext).dataChanged()",
        )
    }

    @Test
    fun mainActivityOnStopSchedulesWorkerInsteadOfWritingBundleDirectly() {
        val source = source("src/main/java/com/impulsive/app/MainActivity.kt")
        val onStop = method(source, "onStop")

        assertTrue(onStop.contains("unlockedThisSession.value = false"))
        assertTrue(onStop.contains("RestoreSnapshotRefreshScheduler.request(applicationContext)"))
        assertFalse(onStop.contains("RestoreBundleWriter"))
        assertFalse(onStop.contains("lifecycleScope.launch"))
    }

    @Test
    fun schedulerQueuesLaterRefreshInsteadOfDroppingIt() {
        val source = source("src/main/java/com/impulsive/app/backend/data/restore/RestoreSnapshotRefreshScheduler.kt")

        assertTrue(source.contains("impulsive_account_bound_restore_snapshot_refresh"))
        assertTrue(source.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertFalse(source.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(source.contains("OneTimeWorkRequestBuilder<RestoreSnapshotRefreshWorker>()"))
        assertTrue(source.contains("cancelUniqueWork(UniqueWorkName)"))
        assertTrue(source.contains("import androidx.work.await"))
        assertTrue(source.contains("suspend fun cancelAndAwait(context: Context)"))
        assertTrue(source.contains(".await()"))
    }

    @Test
    fun schedulerDoesNotDropRefreshRequestsWithKeepPolicy() {
        val source = source("src/main/java/com/impulsive/app/backend/data/restore/RestoreSnapshotRefreshScheduler.kt")

        assertFalse(source.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(source.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
    }

    @Test
    fun workerRetriesOnlyFailedRefreshes() {
        val source = source("src/main/java/com/impulsive/app/backend/data/restore/RestoreSnapshotRefreshWorker.kt")

        assertTrue(source.contains("RestoreSnapshotRefreshResult.Written"))
        assertTrue(source.contains("RestoreSnapshotRefreshResult.NoAuthenticatedAccount"))
        assertTrue(source.contains("RestoreSnapshotRefreshResult.GuestNotApplicable"))
        assertTrue(source.contains("RestoreSnapshotRefreshResult.NoOwnedCompletedData"))
        assertTrue(source.contains("RestoreSnapshotRefreshResult.AccountMismatch"))
        assertTrue(source.contains("is RestoreSnapshotRefreshResult.Failed"))
        assertOrdered(
            source,
            "is RestoreSnapshotRefreshResult.Failed",
            "Result.retry()",
        )
    }

    @Test
    fun successfulSnapshotRefreshQueuesCloudRecoveryUpdate() {
        val source =
            source(
                "src/main/java/com/impulsive/app/backend/data/restore/RestoreSnapshotRefreshWorker.kt",
            )

        assertTrue(
            source.contains(
                "RestoreSnapshotRefreshResult.Written",
            ),
        )

        assertTrue(
            source.contains(
                "CloudRecoveryUploadScheduler.request",
            ),
        )

        assertOrdered(
            source,
            "RestoreSnapshotRefreshResult.Written",
            "CloudRecoveryUploadScheduler.request",
        )
    }
    private fun source(path: String): String = File(path).readText()

    private fun method(source: String, name: String): String {
        val signature = Regex("suspend fun $name|fun $name|override fun $name")
        val match = signature.find(source) ?: error("Method $name not found")
        val start = match.range.first
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "Method $name has no body" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Method $name body did not close")
    }

    private fun assertOrdered(
        source: String,
        before: String,
        after: String,
    ) {
        val beforeIndex = source.indexOf(before)
        val afterIndex = source.indexOf(after)
        assertTrue("Missing '$before'", beforeIndex >= 0)
        assertTrue("Missing '$after'", afterIndex >= 0)
        assertTrue("Expected '$before' before '$after'", beforeIndex < afterIndex)
    }
}
