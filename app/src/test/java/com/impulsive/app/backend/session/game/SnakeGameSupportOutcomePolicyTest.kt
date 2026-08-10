package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import org.junit.Assert.assertEquals
import org.junit.Test

class SnakeGameSupportOutcomePolicyTest {

    @Test
    fun `standalone survival of the full round completes`() {
        assertEquals(
            SupportCycleGameTerminalOutcome.Completed,
            SnakeGameSupportOutcomePolicy.terminalOutcome(
                endReason = SnakeRoundEndReason.TimeLimit,
                validCompletion = true,
                supportCycle = false,
            ),
        )
    }

    @Test
    fun `a support cycle always treats the time limit as timed out`() {
        /*
         * The coordinator applies elapsed duration before resolving the step, so
         * a local "Completed" guess would contradict the authoritative outcome
         * even when the allocation happens to be a full 90 seconds.
         */
        assertEquals(
            SupportCycleGameTerminalOutcome.TimedOut,
            SnakeGameSupportOutcomePolicy.terminalOutcome(
                endReason = SnakeRoundEndReason.TimeLimit,
                validCompletion = true,
                supportCycle = true,
            ),
        )
    }

    @Test
    fun `clearing the board completes in both launch contexts`() {
        listOf(true, false).forEach { supportCycle ->
            assertEquals(
                SupportCycleGameTerminalOutcome.Completed,
                SnakeGameSupportOutcomePolicy.terminalOutcome(
                    endReason = SnakeRoundEndReason.BoardCleared,
                    validCompletion = true,
                    supportCycle = supportCycle,
                ),
            )
        }
    }

    @Test
    fun `a valid self collision completes`() {
        listOf(true, false).forEach { supportCycle ->
            assertEquals(
                SupportCycleGameTerminalOutcome.Completed,
                SnakeGameSupportOutcomePolicy.terminalOutcome(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = true,
                    supportCycle = supportCycle,
                ),
            )
        }
    }

    @Test
    fun `an invalid self collision is abandoned`() {
        listOf(true, false).forEach { supportCycle ->
            assertEquals(
                SupportCycleGameTerminalOutcome.Abandoned,
                SnakeGameSupportOutcomePolicy.terminalOutcome(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = false,
                    supportCycle = supportCycle,
                ),
            )
        }
    }
}
