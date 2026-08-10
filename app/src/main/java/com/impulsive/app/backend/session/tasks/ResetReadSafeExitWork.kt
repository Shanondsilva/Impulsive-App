package com.impulsive.app.backend.session.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import com.impulsive.app.backend.session.progress.SafeExitWorkEnqueueReceipt
import com.impulsive.app.backend.session.progress.toSafeExitWorkEnqueueReceipt
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal fun interface ResetReadSafeExitReconciliationScheduler {
    fun request(
        session: ResetReadSessionRecord,
    ): SafeExitWorkEnqueueReceipt?
}

internal data class ResetReadSafeExitWorkPayload(
    val sessionId: Long,
    val completedAt: LocalDateTime,
    val validCompletion: Boolean,
) {
    fun toCandidate() =
        ResetReadSafeExitCandidateFactory
            .create(
                sessionId =
                    sessionId,
                completedAt =
                    completedAt,
                validCompletion =
                    validCompletion,
            )
}

internal object ResetReadSafeExitWorkDataCodec {
    internal const val CurrentFormatVersion =
        1

    internal const val FormatVersionKey =
        "reset_reading_safe_exit_format_version"

    internal const val SessionIdKey =
        "reset_reading_session_id"

    internal const val CompletedAtKey =
        "reset_reading_completed_at"

    internal const val ValidCompletionKey =
        "reset_reading_valid_completion"

    fun encode(
        session: ResetReadSessionRecord,
    ): Data {
        require(
            session.id > 0L,
        ) {
            "Reset Reading Safe Exit requires a positive session ID."
        }

        return workDataOf(
            FormatVersionKey to
                CurrentFormatVersion,
            SessionIdKey to
                session.id,
            CompletedAtKey to
                session.completedAt
                    .toString(),
            ValidCompletionKey to
                session.validCompletion,
        )
    }

    fun decode(
        data: Data,
    ): ResetReadSafeExitWorkPayload? {
        val values =
            data.keyValueMap

        val formatVersion =
            values[
                FormatVersionKey
            ] as? Int
                ?: return null

        if (
            formatVersion !=
            CurrentFormatVersion
        ) {
            return null
        }

        val sessionId =
            values[
                SessionIdKey
            ] as? Long
                ?: return null

        if (
            sessionId <= 0L
        ) {
            return null
        }

        val completedAtText =
            values[
                CompletedAtKey
            ] as? String
                ?: return null

        if (
            completedAtText.isBlank()
        ) {
            return null
        }

        val validCompletion =
            values[
                ValidCompletionKey
            ] as? Boolean
                ?: return null

        val completedAt =
            runCatching {
                LocalDateTime.parse(
                    completedAtText,
                )
            }.getOrNull()
                ?: return null

        return ResetReadSafeExitWorkPayload(
            sessionId =
                sessionId,
            completedAt =
                completedAt,
            validCompletion =
                validCompletion,
        )
    }
}

internal sealed interface ResetReadSafeExitReconciliationResult {
    data object Recorded :
        ResetReadSafeExitReconciliationResult

    data object Duplicate :
        ResetReadSafeExitReconciliationResult

    data object Rejected :
        ResetReadSafeExitReconciliationResult

    data object RetryableFailure :
        ResetReadSafeExitReconciliationResult
}

internal class ResetReadSafeExitReconciler(
    private val recorder:
        SafeExitCandidateRecorder,
) {
    suspend fun reconcile(
        payload:
            ResetReadSafeExitWorkPayload,
    ): ResetReadSafeExitReconciliationResult {
        return when (
            recorder.record(
                payload.toCandidate(),
            )
        ) {
            is SafeExitRecordingResult.Recorded ->
                ResetReadSafeExitReconciliationResult
                    .Recorded

            is SafeExitRecordingResult.Duplicate ->
                ResetReadSafeExitReconciliationResult
                    .Duplicate

            is SafeExitRecordingResult.Rejected ->
                ResetReadSafeExitReconciliationResult
                    .Rejected

            SafeExitRecordingResult.RetryableFailure ->
                ResetReadSafeExitReconciliationResult
                    .RetryableFailure
        }
    }
}

internal class WorkManagerResetReadSafeExitReconciliationScheduler(
    context: Context,
) : ResetReadSafeExitReconciliationScheduler {
    private val workManager =
        WorkManager.getInstance(
            context.applicationContext,
        )

    override fun request(
        session: ResetReadSessionRecord,
    ): SafeExitWorkEnqueueReceipt? {
        if (
            session.id <= 0L
        ) {
            return null
        }

        return try {
            val request =
                OneTimeWorkRequestBuilder<
                    ResetReadSafeExitReconciliationWorker
                >()
                    .setInputData(
                        ResetReadSafeExitWorkDataCodec
                            .encode(
                                session,
                            ),
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS,
                    )
                    .addTag(
                        ResetReadSafeExitWork
                            .Tag,
                    )
                    .build()

            workManager
                .enqueueUniqueWork(
                    ResetReadSafeExitWork
                        .uniqueWorkName(
                            session.id,
                        ),
                    ExistingWorkPolicy
                        .APPEND_OR_REPLACE,
                    request,
                )
                .toSafeExitWorkEnqueueReceipt()
        } catch (
            _: Exception,
        ) {
            null
        }
    }
}

object ResetReadSafeExitWork {
    const val Tag =
        "reset-reading-safe-exit-reconciliation"

    fun uniqueWorkName(
        sessionId: Long,
    ): String {
        return "reset-reading-safe-exit-$sessionId"
    }
}

class ResetReadSafeExitReconciliationWorker(
    appContext: Context,
    workerParameters:
        WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters,
) {
    override suspend fun doWork():
        Result {
        val payload =
            ResetReadSafeExitWorkDataCodec
                .decode(
                    inputData,
                )
                ?: return Result.failure()

        return try {
            when (
                ResetReadSafeExitReconciler(
                    SafeExitRecordingCoordinator(
                        applicationContext,
                    ),
                )
                    .reconcile(
                        payload,
                    )
            ) {
                ResetReadSafeExitReconciliationResult.Recorded,
                ResetReadSafeExitReconciliationResult.Duplicate,
                ResetReadSafeExitReconciliationResult.Rejected,
                ->
                    Result.success()

                ResetReadSafeExitReconciliationResult.RetryableFailure ->
                    Result.retry()
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