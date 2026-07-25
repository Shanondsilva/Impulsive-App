package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

public class CloudRecoveryUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParams,
) {
    override suspend fun doWork():
        Result {
        return when (
            CloudRecoveryUploadCoordinator(
                applicationContext,
            ).uploadCurrentRecovery()
        ) {
            CloudRecoveryUploadResult.Uploaded,
            CloudRecoveryUploadResult.Disabled,
            CloudRecoveryUploadResult.NoAuthenticatedAccount,
            CloudRecoveryUploadResult.GuestNotApplicable,
            CloudRecoveryUploadResult.NoOwnedCompletedData,
            CloudRecoveryUploadResult.AccountMismatch,
            CloudRecoveryUploadResult.SetupRequired,
            CloudRecoveryUploadResult.AuthorizationRequired,
            CloudRecoveryUploadResult.Cancelled,
            is CloudRecoveryUploadResult.PermanentFailure -> Result.success()

            is CloudRecoveryUploadResult.RetryableFailure -> Result.retry()
        }
    }
}