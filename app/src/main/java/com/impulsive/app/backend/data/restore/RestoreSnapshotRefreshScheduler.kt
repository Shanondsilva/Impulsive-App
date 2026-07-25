package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import java.util.concurrent.TimeUnit

object RestoreSnapshotRefreshScheduler {
    const val UniqueWorkName = "impulsive_account_bound_restore_snapshot_refresh"

    fun request(context: Context) {
        val request = OneTimeWorkRequestBuilder<RestoreSnapshotRefreshWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UniqueWorkName,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
    }

    fun cancel(context: Context) {
        WorkManager
            .getInstance(context.applicationContext)
            .cancelUniqueWork(UniqueWorkName)
    }

    suspend fun cancelAndAwait(context: Context) {
        WorkManager
            .getInstance(context.applicationContext)
            .cancelUniqueWork(UniqueWorkName)
            .await()
    }
}
