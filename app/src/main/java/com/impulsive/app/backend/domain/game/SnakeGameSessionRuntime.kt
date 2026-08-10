package com.impulsive.app.backend.domain.game

/**
 * Drives a [SnakeGameEngine] against a caller-supplied monotonic clock.
 *
 * Pure Kotlin: it owns no Android type, no coroutine and no persistence. The
 * caller passes `SystemClock.elapsedRealtime()` into every entry point, which
 * keeps the whole session testable without a device.
 *
 * Only foreground time counts toward the recovery allocation; a backgrounded
 * gap contributes nothing.
 */
internal class SnakeGameSessionRuntime(
    private val engine: SnakeGameEngine = SnakeGameEngine(),
    roundDurationMillis: Long = SnakeStandaloneRoundDurationMillis,
) {
    init {
        require(roundDurationMillis > 0L) { "roundDurationMillis must be positive" }
    }

    private var configuredRoundDurationMillis = roundDurationMillis
    private var accumulatedForegroundMillis = 0L
    private var movementAccumulatorMillis = 0L
    private var lastFrameMillis: Long? = null
    private var resumed = false

    val state: SnakeGameState
        get() = engine.state

    val elapsedDurationMillis: Long
        get() = accumulatedForegroundMillis

    val roundDurationMillis: Long
        get() = configuredRoundDurationMillis

    val remainingDurationMillis: Long
        get() = (configuredRoundDurationMillis - accumulatedForegroundMillis).coerceAtLeast(0L)

    val timeLeftSeconds: Int
        get() = ((remainingDurationMillis + 999L) / 1_000L).toInt()

    val isResumed: Boolean
        get() = resumed

    /**
     * The player's directional input.
     *
     * From Ready this starts the round and begins timing without advancing a
     * movement tick, preserving the engine's first-interaction contract.
     */
    fun changeDirection(
        direction: SnakeDirection,
        nowElapsedRealtimeMillis: Long,
    ) {
        require(nowElapsedRealtimeMillis >= 0L) { "clock must not be negative" }

        when (engine.state.phase) {
            SnakeGamePhase.Ready -> {
                engine.changeDirection(direction)
                resumed = true
                lastFrameMillis = nowElapsedRealtimeMillis
                movementAccumulatorMillis = 0L
            }

            SnakeGamePhase.Playing -> engine.changeDirection(direction)

            SnakeGamePhase.Finished -> Unit
        }
    }

    /**
     * Advances foreground time and any movement ticks it earned.
     *
     * Elapsed foreground time uses the real delta so the recovery timer stays
     * accurate across a late frame, but movement catch-up is capped so a stalled
     * frame cannot teleport the snake across the board.
     */
    fun frame(nowElapsedRealtimeMillis: Long) {
        require(nowElapsedRealtimeMillis >= 0L) { "clock must not be negative" }

        if (!resumed || engine.state.phase != SnakeGamePhase.Playing) return

        val previous = lastFrameMillis ?: run {
            lastFrameMillis = nowElapsedRealtimeMillis
            return
        }
        require(nowElapsedRealtimeMillis >= previous) { "clock must not move backwards" }

        val rawDelta = nowElapsedRealtimeMillis - previous
        lastFrameMillis = nowElapsedRealtimeMillis

        val remainingBeforeFrame = remainingDurationMillis
        if (remainingBeforeFrame <= 0L) {
            engine.finishForTimeLimit()
            stopTiming()
            return
        }

        val activeDelta = minOf(rawDelta, remainingBeforeFrame)
        accumulatedForegroundMillis += activeDelta
        movementAccumulatorMillis += minOf(activeDelta, MaxMovementCatchUpMillis)

        while (
            movementAccumulatorMillis >= engine.state.tickIntervalMillis &&
            engine.state.phase == SnakeGamePhase.Playing
        ) {
            // Re-read each iteration: eating fruit shortens the interval.
            movementAccumulatorMillis -= engine.state.tickIntervalMillis
            engine.step()
        }

        if (engine.state.phase == SnakeGamePhase.Finished) {
            stopTiming()
            return
        }

        /*
         * Movement that happened before the deadline is honoured first, then the
         * round ends exactly when the allocation is consumed.
         */
        if (accumulatedForegroundMillis >= configuredRoundDurationMillis) {
            engine.finishForTimeLimit()
            stopTiming()
        }
    }

    /** Captures the foreground interval since the last frame, then stops timing. */
    fun pause(nowElapsedRealtimeMillis: Long) {
        require(nowElapsedRealtimeMillis >= 0L) { "clock must not be negative" }

        if (resumed && engine.state.phase == SnakeGamePhase.Playing) {
            frame(nowElapsedRealtimeMillis)
        }

        stopTiming()
    }

    /** Restarts timing from now, so the background gap contributes nothing. */
    fun resume(nowElapsedRealtimeMillis: Long) {
        require(nowElapsedRealtimeMillis >= 0L) { "clock must not be negative" }

        if (engine.state.phase != SnakeGamePhase.Playing) return

        resumed = true
        lastFrameMillis = nowElapsedRealtimeMillis
    }

    fun reset(roundDurationMillis: Long = configuredRoundDurationMillis) {
        require(roundDurationMillis > 0L) { "roundDurationMillis must be positive" }

        configuredRoundDurationMillis = roundDurationMillis
        engine.reset()
        accumulatedForegroundMillis = 0L
        movementAccumulatorMillis = 0L
        stopTiming()
    }

    /** Only legal while no round is in progress; never re-budget a live game. */
    fun configureRoundDuration(roundDurationMillis: Long) {
        require(roundDurationMillis > 0L) { "roundDurationMillis must be positive" }
        check(engine.state.phase != SnakeGamePhase.Playing) {
            "cannot change the allocation of an active round"
        }

        configuredRoundDurationMillis = roundDurationMillis
    }

    private fun stopTiming() {
        resumed = false
        lastFrameMillis = null
    }

    private companion object {
        /**
         * Foreground timing keeps the true delta; only movement catch-up is
         * capped, so one late frame cannot advance the snake many cells.
         */
        const val MaxMovementCatchUpMillis = 250L
    }
}
