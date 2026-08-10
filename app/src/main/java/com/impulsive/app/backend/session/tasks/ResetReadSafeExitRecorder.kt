package com.impulsive.app.backend.session.tasks

import com.impulsive.app.backend.domain.model.score.SafeExitAction
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult

enum class ResetReadSafeExitRequestStatus {
    Idle,
    Recording,
    Durable,
    Failed,
}

internal object ResetReadSafeExitCandidateFactory {
    fun create(
        session: ResetReadSessionRecord,
    ): SafeExitCandidate {
        return create(
            sessionId =
                session.id,
            completedAt =
                session.completedAt,
            validCompletion =
                session.validCompletion,
        )
    }

    fun create(
        sessionId: Long,
        completedAt:
            java.time.LocalDateTime,
        validCompletion: Boolean,
    ): SafeExitCandidate {
        require(
            sessionId > 0L,
        ) {
            "Reset Reading Safe Exit requires a positive session ID."
        }

        return SafeExitCandidate(
            source =
                SafeExitSource.ResetReading,
            sourceId =
                sessionId.toString(),
            action =
                SafeExitAction.WalkAway,
            completedAt =
                completedAt,
            validCompletion =
                validCompletion,
        )
    }
}

internal fun interface ResetReadWalkAwayRecorder {
    suspend fun recordExplicitWalkAway(
        session: ResetReadSessionRecord,
    ): SafeExitRecordingResult
}

internal class ResetReadSafeExitRecorder(
    private val recorder:
        SafeExitCandidateRecorder,
) : ResetReadWalkAwayRecorder {
    override suspend fun recordExplicitWalkAway(
        session: ResetReadSessionRecord,
    ): SafeExitRecordingResult {
        val candidate =
            ResetReadSafeExitCandidateFactory
                .create(
                    session,
                )

        val firstResult =
            recorder.record(
                candidate,
            )

        return if (
            firstResult ==
            SafeExitRecordingResult
                .RetryableFailure
        ) {
            recorder.record(
                candidate,
            )
        } else {
            firstResult
        }
    }
}

internal fun SafeExitRecordingResult.isDurableSafeExitResult():
    Boolean {
    return this is
        SafeExitRecordingResult.Recorded ||
        this is
        SafeExitRecordingResult.Duplicate
}