package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.model.score.SafeExitAction
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.PivotGameSafeExitIdentity
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult

internal fun interface PivotGameWalkAwayRecorder {
    suspend fun recordIfWalkedAway(
        record: ScoreSessionRecord,
    ): SafeExitRecordingResult?
}

internal object PivotGameSafeExitCandidateFactory {
    fun createOrNull(
        record: ScoreSessionRecord,
    ): SafeExitCandidate? {
        val sourceId =
            PivotGameSafeExitIdentity
                .sourceId(
                    record,
                )
                ?: return null

        return SafeExitCandidate(
            source =
                SafeExitSource.PivotGame,
            sourceId =
                sourceId,
            action =
                SafeExitAction.WalkAway,
            completedAt =
                record.completedAt,
            validCompletion =
                record.validCompletion,
        )
    }

    fun create(
        record: ScoreSessionRecord,
    ): SafeExitCandidate {
        return requireNotNull(
            createOrNull(
                record,
            ),
        ) {
            "Pivot Game Safe Exit requires a supported WalkedAway score session."
        }
    }
}

internal class PivotGameSafeExitRecorder(
    private val recorder:
        SafeExitCandidateRecorder,
) : PivotGameWalkAwayRecorder {
    override suspend fun recordIfWalkedAway(
        record: ScoreSessionRecord,
    ): SafeExitRecordingResult? {
        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    record,
                )
                ?: return null

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