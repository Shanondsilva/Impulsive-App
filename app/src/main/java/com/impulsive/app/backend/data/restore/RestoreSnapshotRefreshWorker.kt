package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryUploadScheduler

class RestoreSnapshotRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParams,
) {
    override suspend fun doWork():
        Result {
        return when (
            AccountBoundRestoreSnapshotRefresher(
                applicationContext,
            ).refresh()
        ) {
            RestoreSnapshotRefreshResult.Written -> {
                /*
                 * Every successful RestoreBundle snapshot represents the
                 * current recoverable state. Queue a cloud update as well.
                 *
                 * The cloud worker itself verifies that cloud recovery is
                 * enabled, so this request is safe for users who have never
                 * enabled Drive recovery or have turned it off.
                 */
                CloudRecoveryUploadScheduler.request(
                    applicationContext,
                )

                Result.success()
            }

            RestoreSnapshotRefreshResult.NoAuthenticatedAccount,
            RestoreSnapshotRefreshResult.GuestNotApplicable,
            RestoreSnapshotRefreshResult.NoOwnedCompletedData,
            RestoreSnapshotRefreshResult.AccountMismatch,
            RestoreSnapshotRefreshResult.GoogleIdentityUnavailable ->
                Result.success()

            is RestoreSnapshotRefreshResult.Failed ->
                Result.retry()
        }
    }
}