package com.impulsive.app.backend.session.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnakeSupportElapsedPolicyTest {

    // ------------------------------------------------------------------
    // remainingAfterCheckpoint
    // ------------------------------------------------------------------

    @Test
    fun `a fresh support step keeps its whole allocation`() {
        assertEquals(
            60_000L,
            SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                authoritativeAvailableMillis = 60_000L,
                checkpointMillis = 0L,
            ),
        )
    }

    @Test
    fun `consumed time is subtracted after process death`() {
        assertEquals(
            25_000L,
            SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                authoritativeAvailableMillis = 60_000L,
                checkpointMillis = 35_000L,
            ),
        )
    }

    @Test
    fun `a fully consumed allocation leaves nothing`() {
        assertEquals(
            0L,
            SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                authoritativeAvailableMillis = 60_000L,
                checkpointMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `a checkpoint larger than the allocation fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                authoritativeAvailableMillis = 60_000L,
                checkpointMillis = 60_001L,
            )
        }
    }

    @Test
    fun `a negative checkpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                authoritativeAvailableMillis = 60_000L,
                checkpointMillis = -1L,
            )
        }
    }

    @Test
    fun `a non-positive allocation is rejected`() {
        listOf(0L, -1L).forEach { allocation ->
            assertThrows(IllegalArgumentException::class.java) {
                SnakeSupportElapsedPolicy.remainingAfterCheckpoint(
                    authoritativeAvailableMillis = allocation,
                    checkpointMillis = 0L,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // totalConsumed
    // ------------------------------------------------------------------

    @Test
    fun `total consumption adds the baseline to the current attempt`() {
        assertEquals(
            55_000L,
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 35_000L,
                currentRoundElapsedMillis = 20_000L,
                authoritativeAvailableMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `total consumption may exactly equal the allocation`() {
        assertEquals(
            60_000L,
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 35_000L,
                currentRoundElapsedMillis = 25_000L,
                authoritativeAvailableMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `total consumption beyond the allocation is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 35_000L,
                currentRoundElapsedMillis = 25_001L,
                authoritativeAvailableMillis = 60_000L,
            )
        }
    }

    @Test
    fun `a negative current attempt is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 0L,
                currentRoundElapsedMillis = -1L,
                authoritativeAvailableMillis = 60_000L,
            )
        }
    }

    @Test
    fun `a corrupt baseline is rejected before any addition`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 60_001L,
                currentRoundElapsedMillis = 0L,
                authoritativeAvailableMillis = 60_000L,
            )
        }
    }

    @Test
    fun `arithmetic never silently overflows`() {
        // Checked addition must throw rather than wrapping to a negative total.
        assertThrows(ArithmeticException::class.java) {
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = Long.MAX_VALUE,
                currentRoundElapsedMillis = Long.MAX_VALUE,
                authoritativeAvailableMillis = Long.MAX_VALUE,
            )
        }
    }

    @Test
    fun `a fresh step reports only the current attempt`() {
        assertEquals(
            20_000L,
            SnakeSupportElapsedPolicy.totalConsumed(
                checkpointMillis = 0L,
                currentRoundElapsedMillis = 20_000L,
                authoritativeAvailableMillis = 60_000L,
            ),
        )
    }
}
