package com.impulsive.app.backend.session.progress

import android.content.Context
import com.impulsive.app.backend.data.repository.SafeExitRepository
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitEvaluation
import com.impulsive.app.backend.domain.model.score.SafeExitPolicy
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitRejectionReason
import com.impulsive.app.backend.domain.repository.score.SafeExitRecordRepository
import kotlinx.coroutines.CancellationException

sealed interface SafeExitRecordingResult {
    data class Recorded(
        val record: SafeExitRecord,
    ) : SafeExitRecordingResult

    data class Duplicate(
        val record: SafeExitRecord,
    ) : SafeExitRecordingResult

    data class Rejected(
        val reason:
            SafeExitRejectionReason,
    ) : SafeExitRecordingResult

    data object RetryableFailure :
        SafeExitRecordingResult
}

fun interface SafeExitCandidateRecorder {
    suspend fun record(
        candidate: SafeExitCandidate,
    ): SafeExitRecordingResult
}

class SafeExitRecordingCoordinator(
    private val repository:
        SafeExitRecordRepository,
) : SafeExitCandidateRecorder {
    constructor(
        context: Context,
    ) : this(
        SafeExitRepository(
            context.applicationContext,
        ),
    )

    override suspend fun record(
        candidate: SafeExitCandidate,
    ): SafeExitRecordingResult {
        return when (
            val evaluation =
                SafeExitPolicy.evaluate(
                    candidate,
                )
        ) {
            is SafeExitEvaluation.Rejected ->
                SafeExitRecordingResult
                    .Rejected(
                        evaluation.reason,
                    )

            is SafeExitEvaluation.Accepted -> {
                try {
                    val inserted =
                        repository
                            .recordIfAbsent(
                                evaluation.record,
                            )

                    if (
                        inserted
                    ) {
                        SafeExitRecordingResult
                            .Recorded(
                                evaluation.record,
                            )
                    } else {
                        SafeExitRecordingResult
                            .Duplicate(
                                evaluation.record,
                            )
                    }
                } catch (
                    cancellation:
                    CancellationException,
                ) {
                    throw cancellation
                } catch (
                    _: Exception,
                ) {
                    SafeExitRecordingResult
                        .RetryableFailure
                }
            }
        }
    }
}