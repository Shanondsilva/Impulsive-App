package com.impulsive.app.backend.session.game

/**
 * Whether an exhausted active support checkpoint may be deleted.
 *
 * This is deliberately not [keepsRestoredResultVisible], which answers a
 * different question: that property returns true for retryable
 * PersistenceFailure and RevisionConflict precisely so a restored Result stays
 * on screen. An exhausted checkpoint is the only surviving proof that the
 * player's allocation was already consumed, so it may only be discarded once
 * the support cycle has actually accepted the mutation.
 */
internal object SnakeSupportCheckpointResolutionPolicy {

    fun shouldClearAfterExhaustedCheckpoint(
        report: SupportCycleGameReportResult,
    ): Boolean =
        report.allowsContinuation ||
            report.allowsExit
}
