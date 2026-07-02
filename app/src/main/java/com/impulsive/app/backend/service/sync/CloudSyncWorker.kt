package com.impulsive.app.backend.service.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.sync.JournalChecklistCloudSync
import com.impulsive.app.backend.data.sync.JournalNoteCloudSync
import com.impulsive.app.backend.data.sync.RecoverySessionCloudSync
import com.impulsive.app.backend.data.sync.SyncTombstoneCloudSync
import java.util.concurrent.TimeUnit

/**
 * Runs cloud sync in the background with a network requirement and retry/backoff, instead
 * of an inline best-effort attempt on app start.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            return Result.success()
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val database = AppDatabase.getInstance(applicationContext)
        val tombstoneDao = database.syncTombstoneDao()
        return try {
            SyncTombstoneCloudSync().sync(tombstoneDao, uid)
            RecoverySessionCloudSync().sync(database.recoverySessionDao(), tombstoneDao, uid)
            JournalNoteCloudSync().sync(database.journalNoteDao(), tombstoneDao, uid)
            JournalChecklistCloudSync().sync(database.journalNoteDao(), tombstoneDao, uid)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < MaxAttempts) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WorkName = "cloud_sync"
        private const val MaxAttempts = 4

        fun enqueue(context: Context) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WorkName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
