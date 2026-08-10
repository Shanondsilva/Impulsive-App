package com.impulsive.app.backend.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeGameSessionRuntimeTest {

    // ------------------------------------------------------------------
    // Ready / first input
    // ------------------------------------------------------------------

    @Test
    fun `runtime starts Ready with a full clock and no elapsed time`() {
        val runtime = runtime()

        assertEquals(SnakeGamePhase.Ready, runtime.state.phase)
        assertEquals(0L, runtime.elapsedDurationMillis)
        assertEquals(90, runtime.timeLeftSeconds)
        assertFalse(runtime.isResumed)
    }

    @Test
    fun `first direction starts play and begins timing without moving`() {
        val runtime = runtime()
        val head = runtime.state.snake.first()

        runtime.changeDirection(SnakeDirection.Right, 1_000L)

        assertEquals(SnakeGamePhase.Playing, runtime.state.phase)
        assertEquals(SnakeDirection.Right, runtime.state.direction)
        assertEquals("first input must not advance a tick", head, runtime.state.snake.first())
        assertTrue(runtime.isResumed)
        assertEquals(0L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `all four first directions start the round`() {
        SnakeDirection.entries.forEach { direction ->
            val runtime = runtime()

            runtime.changeDirection(direction, 0L)

            assertEquals(SnakeGamePhase.Playing, runtime.state.phase)
            assertEquals(direction, runtime.state.direction)
        }
    }

    // ------------------------------------------------------------------
    // Frame timing
    // ------------------------------------------------------------------

    @Test
    fun `a frame below the tick interval does not move the snake`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        val head = runtime.state.snake.first()

        runtime.frame(219L)

        assertEquals(head, runtime.state.snake.first())
        assertEquals(219L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `a frame at exactly the tick interval moves one cell`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        val head = runtime.state.snake.first()

        runtime.frame(220L)

        assertEquals(SnakeCell(head.x + 1, head.y), runtime.state.snake.first())
        assertEquals(220L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `a late frame contributes its full delta to foreground time`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)

        runtime.frame(500L)

        assertEquals("recovery timing must stay accurate", 500L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `a late frame only earns capped movement catch-up`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        val head = runtime.state.snake.first()

        runtime.frame(500L)

        /*
         * 500 ms of wall clock would be two 220 ms ticks, but movement catch-up
         * is capped at 250 ms, so only one cell is earned.
         */
        assertEquals(SnakeCell(head.x + 1, head.y), runtime.state.snake.first())
    }

    @Test
    fun `a very long stall cannot teleport the snake across the board`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        val head = runtime.state.snake.first()

        runtime.frame(5_000L)

        assertEquals(SnakeCell(head.x + 1, head.y), runtime.state.snake.first())
        assertEquals(5_000L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `the tick interval is reread after fruit changes the speed`() {
        /*
         * The snake runs right along its start row, so placing food in that row
         * guarantees it is eaten; wall wrapping brings the head back around.
         */
        val engine = SnakeGameEngine(columns = 18, rows = 24) { bound ->
            // Free cell 224 is (12,12), three cells ahead of the starting head.
            224.coerceIn(0, bound - 1)
        }
        val runtime = SnakeGameSessionRuntime(engine = engine)
        runtime.changeDirection(SnakeDirection.Right, 0L)

        assertEquals(SnakeCell(12, 12), runtime.state.food)
        assertEquals(220L, runtime.state.tickIntervalMillis)

        var now = 0L
        var guard = 0
        while (runtime.state.fruitsEaten == 0 && guard < 200) {
            now += 220L
            runtime.frame(now)
            guard++
        }

        assertTrue("expected at least one fruit", runtime.state.fruitsEaten >= 1)
        assertEquals(
            220L - 5L * runtime.state.fruitsEaten,
            runtime.state.tickIntervalMillis,
        )
    }

    // ------------------------------------------------------------------
    // Pause / resume
    // ------------------------------------------------------------------

    @Test
    fun `pause captures the foreground interval since the last frame`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.frame(100L)

        runtime.pause(180L)

        assertEquals(180L, runtime.elapsedDurationMillis)
        assertFalse(runtime.isResumed)
    }

    @Test
    fun `frames while paused do not advance elapsed time`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.pause(100L)

        runtime.frame(5_000L)
        runtime.frame(9_000L)

        assertEquals(100L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `a long background gap contributes nothing to the recovery clock`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.pause(10_000L)

        runtime.resume(40_000L)
        runtime.frame(40_100L)

        // The 30-second gap is excluded; only 10,000 + 100 counts.
        assertEquals(10_100L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `resume does not immediately move the snake`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.pause(50L)
        val head = runtime.state.snake.first()

        runtime.resume(40_000L)

        assertEquals(head, runtime.state.snake.first())
    }

    @Test
    fun `resume is ignored unless a round is in progress`() {
        val runtime = runtime()

        runtime.resume(1_000L)

        assertFalse(runtime.isResumed)
        assertEquals(SnakeGamePhase.Ready, runtime.state.phase)
    }

    // ------------------------------------------------------------------
    // Time limit
    // ------------------------------------------------------------------

    @Test
    fun `a standalone round ends exactly at ninety seconds`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)

        advance(runtime, from = 0L, to = 90_000L)

        assertEquals(SnakeGamePhase.Finished, runtime.state.phase)
        assertEquals(SnakeRoundEndReason.TimeLimit, runtime.state.endReason)
        assertEquals(90_000L, runtime.elapsedDurationMillis)
        assertEquals(0, runtime.timeLeftSeconds)
    }

    @Test
    fun `elapsed time never exceeds the configured allocation`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)

        advance(runtime, from = 0L, to = 95_000L)

        assertTrue(runtime.elapsedDurationMillis <= 90_000L)
    }

    @Test
    fun `a shorter configured allocation ends at its own deadline`() {
        val runtime = SnakeGameSessionRuntime(
            engine = SnakeGameEngine(foodIndexPicker = { it - 1 }),
            roundDurationMillis = 30_000L,
        )
        runtime.changeDirection(SnakeDirection.Right, 0L)

        advance(runtime, from = 0L, to = 30_000L)

        assertEquals(SnakeGamePhase.Finished, runtime.state.phase)
        assertEquals(SnakeRoundEndReason.TimeLimit, runtime.state.endReason)
        assertEquals(30_000L, runtime.elapsedDurationMillis)
    }

    @Test
    fun `frames and directions after the round finishes are no-ops`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        advance(runtime, from = 0L, to = 90_000L)
        val finished = runtime.state
        val elapsed = runtime.elapsedDurationMillis

        runtime.frame(120_000L)
        runtime.changeDirection(SnakeDirection.Up, 120_000L)

        assertEquals(finished, runtime.state)
        assertEquals(elapsed, runtime.elapsedDurationMillis)
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    @Test
    fun `reset returns a clean Ready round`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.frame(3_000L)

        runtime.reset()

        assertEquals(SnakeGamePhase.Ready, runtime.state.phase)
        assertEquals(0L, runtime.elapsedDurationMillis)
        assertFalse(runtime.isResumed)
        assertNull(runtime.state.direction)
        assertEquals(90, runtime.timeLeftSeconds)
    }

    @Test
    fun `reset clears the movement accumulator`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        // Bank almost a full tick, then reset before it can be spent.
        runtime.frame(219L)
        runtime.reset()

        runtime.changeDirection(SnakeDirection.Right, 0L)
        val head = runtime.state.snake.first()
        runtime.frame(100L)

        assertEquals("stale movement credit must not survive reset", head, runtime.state.snake.first())
    }

    @Test
    fun `reset accepts a new allocation`() {
        val runtime = runtime()

        runtime.reset(roundDurationMillis = 30_000L)

        assertEquals(30_000L, runtime.roundDurationMillis)
        assertEquals(30, runtime.timeLeftSeconds)
    }

    @Test
    fun `reset rejects a non-positive allocation`() {
        val runtime = runtime()

        assertThrows(IllegalArgumentException::class.java) { runtime.reset(0L) }
        assertThrows(IllegalArgumentException::class.java) { runtime.reset(-1L) }
    }

    @Test
    fun `reset does not spawn food before the first interaction`() {
        var pickerCalls = 0
        val engine = SnakeGameEngine(columns = 18, rows = 24) {
            pickerCalls++
            0
        }
        val runtime = SnakeGameSessionRuntime(engine = engine)

        runtime.reset()

        assertEquals(0, pickerCalls)
        assertNull(runtime.state.food)
    }

    @Test
    fun `an active round cannot have its allocation changed underneath it`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)

        assertThrows(IllegalStateException::class.java) {
            runtime.configureRoundDuration(30_000L)
        }
    }

    // ------------------------------------------------------------------
    // Clock validation
    // ------------------------------------------------------------------

    @Test
    fun `construction rejects a non-positive allocation`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameSessionRuntime(roundDurationMillis = 0L)
        }
    }

    @Test
    fun `negative timestamps are rejected`() {
        val runtime = runtime()

        assertThrows(IllegalArgumentException::class.java) {
            runtime.changeDirection(SnakeDirection.Right, -1L)
        }
        assertThrows(IllegalArgumentException::class.java) { runtime.frame(-1L) }
        assertThrows(IllegalArgumentException::class.java) { runtime.pause(-1L) }
        assertThrows(IllegalArgumentException::class.java) { runtime.resume(-1L) }
    }

    @Test
    fun `a backwards clock is rejected while a round is active`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 1_000L)
        runtime.frame(2_000L)

        assertThrows(IllegalArgumentException::class.java) { runtime.frame(1_500L) }
    }

    @Test
    fun `mid-round direction changes do not disturb timing`() {
        val runtime = runtime()
        runtime.changeDirection(SnakeDirection.Right, 0L)
        runtime.frame(300L)
        val elapsed = runtime.elapsedDurationMillis

        runtime.changeDirection(SnakeDirection.Up, 350L)

        assertEquals(elapsed, runtime.elapsedDurationMillis)
        assertNotEquals(SnakeGamePhase.Ready, runtime.state.phase)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Food is parked in the far corner so straight runs never eat by accident. */
    private fun runtime() = SnakeGameSessionRuntime(
        engine = SnakeGameEngine(foodIndexPicker = { it - 1 }),
    )

    /** Feeds regular ~60fps frames so movement and timing both advance normally. */
    private fun advance(
        runtime: SnakeGameSessionRuntime,
        from: Long,
        to: Long,
        stepMillis: Long = 16L,
    ) {
        var now = from
        while (now < to) {
            now = minOf(now + stepMillis, to)
            runtime.frame(now)
        }
    }
}
