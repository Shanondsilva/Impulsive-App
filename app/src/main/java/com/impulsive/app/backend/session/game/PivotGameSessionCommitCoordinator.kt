package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult

internal fun interface PivotGameScoreSessionWriter {
    suspend fun record(
        record: ScoreSessionRecord,
    )
}

internal fun interface PivotGameSafeExitReconciliationScheduler {
    fun request():
        Boolean
}

internal data class PivotGameSessionCommitResult(
    val reconciliationScheduled:
        Boolean,
    val immediateSafeExitResult:
        SafeExitRecordingResult?,
)

internal class PivotGameSessionCommitCoordinator(
    private val scoreSessionWriter:
        PivotGameScoreSessionWriter,
    private val immediateSafeExitRecorder:
        PivotGameWalkAwayRecorder,
    private val reconciliationScheduler:
        PivotGameSafeExitReconciliationScheduler,
) {
    constructor(
        scoreRepository:
            ScoreRepository,
        immediateSafeExitRecorder:
            PivotGameWalkAwayRecorder,
        reconciliationScheduler:
            PivotGameSafeExitReconciliationScheduler,
    ) : this(
        scoreSessionWriter =
            PivotGameScoreSessionWriter { record ->
                scoreRepository.recordSession(
                    record,
                )
            },
        immediateSafeExitRecorder =
            immediateSafeExitRecorder,
        reconciliationScheduler =
            reconciliationScheduler,
    )

    suspend fun commit(
        record: ScoreSessionRecord,
    ): PivotGameSessionCommitResult {
        val isWalkAway =
            record.outcome ==
                ScoreSessionOutcome.WalkedAway

        var reconciliationScheduled =
            false

        try {
            scoreSessionWriter.record(
                record,
            )
        } finally {
            if (
                isWalkAway
            ) {
                reconciliationScheduled =
                    reconciliationSchedulerRequestSafely()
            }
        }

        val immediateResult =
            if (
                isWalkAway
            ) {
                immediateSafeExitRecorder
                    .recordIfWalkedAway(
                        record,
                    )
            } else {
                null
            }

        return PivotGameSessionCommitResult(
            reconciliationScheduled =
                reconciliationScheduled,
            immediateSafeExitResult =
                immediateResult,
        )
    }

    private fun reconciliationSchedulerRequestSafely():
        Boolean {
        return try {
            reconciliationScheduler
                .request()
        } catch (
            _: Exception,
        ) {
            false
        }
    }
}