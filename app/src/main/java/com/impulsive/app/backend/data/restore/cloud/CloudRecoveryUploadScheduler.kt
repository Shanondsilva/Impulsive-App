package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import java.util.concurrent.TimeUnit

public object CloudRecoveryUploadScheduler {
    public const val UniqueWorkName =
        "impulsive_cloud_recovery_upload"

    public fun request(
        context: Context,
    ) {
        val request =
            OneTimeWorkRequestBuilder<CloudRecoveryUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.CONNECTED,
                        )
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

        WorkManager
            .getInstance(
                context.applicationContext,
            )
            .enqueueUniqueWork(
                UniqueWorkName,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
    }

    public fun cancel(
        context: Context,
    ) {
        WorkManager
            .getInstance(
                context.applicationContext,
            )
            .cancelUniqueWork(UniqueWorkName)
    }

    public suspend fun cancelAndAwait(context: Context) {
        WorkManager
            .getInstance(
                context.applicationContext,
            )
            .cancelUniqueWork(UniqueWorkName)
            .await()
    }
}