package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the semantic distinction between "a restored Result should stay
 * visible" and "the support cycle accepted this mutation, so the exhausted
 * checkpoint may be deleted". The two are deliberately different.
 */
class SnakeSupportCheckpointResolutionPolicyTest {

    @Test
    fun `persistence failure retains the exhausted checkpoint`() {
        val report = SupportCycleGameReportResult.Reported(
            AdaptiveSupportCycleCommandResult.PersistenceFailure,
        )

        // Retryable: the Result stays on screen, but nothing was accepted.
        assertTrue(report.keepsRestoredResultVisible)
        assertFalse(report.allowsContinuation)
        assertFalse(report.allowsExit)

        assertFalse(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }

    @Test
    fun `revision conflict retains the exhausted checkpoint`() {
        val report = SupportCycleGameReportResult.Reported(
            AdaptiveSupportCycleCommandResult.RevisionConflict,
        )

        assertTrue(report.keepsRestoredResultVisible)
        assertFalse(report.allowsContinuation)
        assertFalse(report.allowsExit)

        assertFalse(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }

    @Test
    fun `outcome conflict retains the exhausted checkpoint`() {
        val report = SupportCycleGameReportResult.OutcomeConflict

        assertFalse(report.allowsContinuation)
        assertFalse(report.allowsExit)

        assertFalse(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }

    @Test
    fun `a duplicate report clears the exhausted checkpoint`() {
        val report = SupportCycleGameReportResult.Duplicate

        // Idempotently accepted, so no stale checkpoint should linger.
        assertTrue(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }

    @Test
    fun `a not-found report clears the exhausted checkpoint`() {
        val report = SupportCycleGameReportResult.Reported(
            AdaptiveSupportCycleCommandResult.NotFound,
        )

        assertTrue(report.allowsExit)
        assertTrue(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }

    @Test
    fun `an ignored standalone report stays consistent with the shared contract`() {
        val report = SupportCycleGameReportResult.IgnoredStandalone

        assertTrue(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(report),
        )
    }
}
