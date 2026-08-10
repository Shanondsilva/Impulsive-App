package com.impulsive.app.backend.session.game

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class PivotGameSafeExitReconciliationResult(
    val inspectedSessions:
        Int,
    val eligibleSessions:
        Int,
    val recorded:
        Int,
    val duplicates:
        Int,
    val rejected:
        Int,
    val retryableFailures:
        Int,
) {
    val requiresRetry:
        Boolean
        get() =
            retryableFailures > 0
}

internal class PivotGameSafeExitReconciler(
    private val recorder:
        SafeExitCandidateRecorder,
) {
    suspend fun reconcile(
        sessions:
            List<ScoreSessionRecord>,
    ): PivotGameSafeExitReconciliationResult {
        var eligibleSessions =
            0
        var recorded =
            0
        var duplicates =
            0
        var rejected =
            0
        var retryableFailures =
            0

        sessions.forEach { session ->
            val candidate =
                PivotGameSafeExitCandidateFactory
                    .createOrNull(
                        session,
                    )
                    ?: return@forEach

            eligibleSessions +=
                1

            when (
                recorder.record(
                    candidate,
                )
            ) {
                is SafeExitRecordingResult.Recorded ->
                    recorded += 1

                is SafeExitRecordingResult.Duplicate ->
                    duplicates += 1

                is SafeExitRecordingResult.Rejected ->
                    rejected += 1

                SafeExitRecordingResult.RetryableFailure ->
                    retryableFailures += 1
            }
        }

        return PivotGameSafeExitReconciliationResult(
            inspectedSessions =
                sessions.size,
            eligibleSessions =
                eligibleSessions,
            recorded =
                recorded,
            duplicates =
                duplicates,
            rejected =
                rejected,
            retryableFailures =
                retryableFailures,
        )
    }
}

class WorkManagerPivotGameSafeExitReconciliationScheduler(
    context: Context,
) : PivotGameSafeExitReconciliationScheduler {
    private val workManager =
        WorkManager.getInstance(
            context.applicationContext,
        )

    override fun request():
        Boolean {
        return try {
            val request =
                OneTimeWorkRequestBuilder<
                    PivotGameSafeExitReconciliationWorker
                >()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS,
                    )
                    .addTag(
                        PivotGameSafeExitReconciliationWork
                            .Tag,
                    )
                    .build()

            workManager
                .enqueueUniqueWork(
                    PivotGameSafeExitReconciliationWork
                        .UniqueWorkName,
                    ExistingWorkPolicy
                        .APPEND_OR_REPLACE,
                    request,
                )

            true
        } catch (
            _: Exception,
        ) {
            false
        }
    }
}

object PivotGameSafeExitReconciliationWork {
    const val UniqueWorkName =
        "pivot-game-safe-exit-reconciliation"

    const val Tag =
        "pivot-game-safe-exit-reconciliation"
}

class PivotGameSafeExitReconciliationWorker(
    appContext: Context,
    workerParameters:
        WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters,
) {
    override suspend fun doWork():
        Result {
        return try {
            val sessions =
                ScoreRepository(
                    applicationContext,
                )
                    .sessions
                    .first()

            val reconciliation =
                PivotGameSafeExitReconciler(
                    SafeExitRecordingCoordinator(
                        applicationContext,
                    ),
                )
                    .reconcile(
                        sessions,
                    )

            if (
                reconciliation
                    .requiresRetry
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            _: Exception,
        ) {
            Result.retry()
        }
    }
}