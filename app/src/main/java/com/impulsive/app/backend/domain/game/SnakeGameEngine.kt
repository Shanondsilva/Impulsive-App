package com.impulsive.app.backend.domain.game

import kotlin.random.Random

/**
 * Deterministic rules for Impulsive Snake.
 *
 * Pure Kotlin: the engine owns no clock, no coroutine and no Android type. Time
 * belongs to the caller, which drives movement via [step] and ends an expired
 * round via [finishForTimeLimit].
 */
internal object SnakeGameConfig {
    const val DEFAULT_COLUMNS = 18
    const val DEFAULT_ROWS = 24
    const val INITIAL_LENGTH = 4
    const val POINTS_PER_FRUIT = 10
    const val INITIAL_TICK_INTERVAL_MILLIS = 220L
    const val MINIMUM_TICK_INTERVAL_MILLIS = 120L
    const val TICK_REDUCTION_PER_FRUIT_MILLIS = 5L
    const val MAX_DIRECTION_QUEUE_SIZE = 2

    /** Smallest board that still leaves room for the initial snake and food. */
    const val MINIMUM_COLUMNS = 6
    const val MINIMUM_ROWS = 6
}

/**
 * @param foodIndexPicker chooses an index into the free-cell list. Injected so
 * tests are deterministic; production uses [Random.Default].
 */
internal class SnakeGameEngine(
    val columns: Int = SnakeGameConfig.DEFAULT_COLUMNS,
    val rows: Int = SnakeGameConfig.DEFAULT_ROWS,
    private val foodIndexPicker: (Int) -> Int = { bound -> Random.Default.nextInt(bound) },
) {
    init {
        require(columns >= SnakeGameConfig.MINIMUM_COLUMNS) {
            "columns must be at least ${SnakeGameConfig.MINIMUM_COLUMNS}, was $columns"
        }
        require(rows >= SnakeGameConfig.MINIMUM_ROWS) {
            "rows must be at least ${SnakeGameConfig.MINIMUM_ROWS}, was $rows"
        }
        require(columns * rows > SnakeGameConfig.INITIAL_LENGTH) {
            "board must hold more than the initial snake length"
        }
    }

    private var internalState: SnakeGameState = readyState()

    val state: SnakeGameState
        get() = internalState

    /**
     * Returns to [SnakeGamePhase.Ready] with a stationary preview snake. No food
     * is placed, so no randomness is consumed before a real round begins.
     */
    fun reset(): SnakeGameState {
        internalState = readyState()
        return internalState
    }

    /**
     * The player's directional input.
     *
     * From [SnakeGamePhase.Ready] this starts the round: the body is rebuilt
     * trailing behind [direction] from the same central head, food is placed and
     * the phase becomes [SnakeGamePhase.Playing]. It deliberately does not
     * advance a movement tick.
     *
     * While playing the direction is queued if it is a legal turn. After the
     * round finishes it is a no-op.
     */
    fun changeDirection(direction: SnakeDirection): SnakeGameState {
        when (internalState.phase) {
            SnakeGamePhase.Finished -> return internalState

            SnakeGamePhase.Ready -> {
                val snake = createStartingSnake(direction)
                internalState = validated(
                    internalState.copy(
                        phase = SnakeGamePhase.Playing,
                        snake = snake,
                        food = placeFood(snake),
                        direction = direction,
                        queuedDirections = emptyList(),
                    ),
                )
                return internalState
            }

            SnakeGamePhase.Playing -> {
                if (!canQueue(direction)) return internalState
                internalState = validated(
                    internalState.copy(
                        queuedDirections = internalState.queuedDirections + direction,
                    ),
                )
                return internalState
            }
        }
    }

    /**
     * Advances exactly one movement tick, consuming at most one queued
     * direction. A no-op unless the round is playing.
     */
    fun step(): SnakeGameState {
        val current = internalState
        if (current.phase != SnakeGamePhase.Playing) return current

        val direction = current.queuedDirections.firstOrNull() ?: checkNotNull(current.direction) {
            "a playing round always has a direction"
        }
        val remainingQueue = current.queuedDirections.drop(1)
        val snake = current.snake
        val nextHead = wrapped(advance(snake.first(), direction))

        /*
         * Whether the fruit is eaten decides which cells are solid this tick:
         * when not eating, the tail vacates its cell and moving into it is
         * legal. Wrapping across an edge never exempts this check.
         */
        val ateFood = nextHead == current.food
        val collisionBody = if (ateFood) snake else snake.dropLast(1)

        if (nextHead in collisionBody) {
            internalState = validated(
                current.copy(
                    phase = SnakeGamePhase.Finished,
                    direction = direction,
                    queuedDirections = remainingQueue,
                    endReason = SnakeRoundEndReason.SelfCollision,
                ),
            )
            return internalState
        }

        val movedSnake = if (ateFood) {
            listOf(nextHead) + snake
        } else {
            listOf(nextHead) + snake.dropLast(1)
        }

        if (!ateFood) {
            internalState = validated(
                current.copy(
                    snake = movedSnake,
                    direction = direction,
                    queuedDirections = remainingQueue,
                ),
            )
            return internalState
        }

        val fruitsEaten = current.fruitsEaten + 1
        val nextFood = placeFood(movedSnake)
        val boardCleared = nextFood == null

        internalState = validated(
            current.copy(
                phase = if (boardCleared) SnakeGamePhase.Finished else SnakeGamePhase.Playing,
                snake = movedSnake,
                food = nextFood,
                direction = direction,
                queuedDirections = remainingQueue,
                fruitsEaten = fruitsEaten,
                score = current.score + SnakeGameConfig.POINTS_PER_FRUIT,
                tickIntervalMillis = tickIntervalFor(fruitsEaten),
                endReason = if (boardCleared) SnakeRoundEndReason.BoardCleared else null,
            ),
        )
        return internalState
    }

    /**
     * Ends a playing round because the caller's recovery budget expired. The
     * snake, food, fruit count and score are preserved. A Ready or already
     * finished round is left untouched.
     */
    fun finishForTimeLimit(): SnakeGameState {
        if (internalState.phase != SnakeGamePhase.Playing) return internalState
        internalState = validated(
            internalState.copy(
                phase = SnakeGamePhase.Finished,
                endReason = SnakeRoundEndReason.TimeLimit,
            ),
        )
        return internalState
    }

    private fun canQueue(direction: SnakeDirection): Boolean {
        if (internalState.queuedDirections.size >= SnakeGameConfig.MAX_DIRECTION_QUEUE_SIZE) {
            return false
        }
        /*
         * Compare against the last *planned* direction, not the direction
         * currently on screen. Otherwise rapid input between ticks could queue
         * Right -> Up -> Down and reverse through the neck.
         */
        val lastPlanned = internalState.queuedDirections.lastOrNull() ?: internalState.direction
        if (lastPlanned == null) return false
        if (direction == lastPlanned) return false
        return !direction.isOpposite(lastPlanned)
    }

    private fun readyState(): SnakeGameState = SnakeGameState(
        phase = SnakeGamePhase.Ready,
        snake = createReadySnake(),
        food = null,
        direction = null,
        queuedDirections = emptyList(),
        fruitsEaten = 0,
        score = 0,
        tickIntervalMillis = SnakeGameConfig.INITIAL_TICK_INTERVAL_MILLIS,
        endReason = null,
    )

    private fun headStart(): SnakeCell = SnakeCell(x = columns / 2, y = rows / 2)

    /**
     * A stationary horizontal preview. Its orientation is presentation only and
     * does not constrain which direction may start the round.
     */
    private fun createReadySnake(): List<SnakeCell> {
        val head = headStart()
        return (0 until SnakeGameConfig.INITIAL_LENGTH).map { offset ->
            wrapped(SnakeCell(x = head.x - offset, y = head.y))
        }
    }

    /** Body trails behind [direction] from the same central head. */
    private fun createStartingSnake(direction: SnakeDirection): List<SnakeCell> {
        val head = headStart()
        return (0 until SnakeGameConfig.INITIAL_LENGTH).map { offset ->
            val trailing = when (direction) {
                SnakeDirection.Up -> SnakeCell(x = head.x, y = head.y + offset)
                SnakeDirection.Down -> SnakeCell(x = head.x, y = head.y - offset)
                SnakeDirection.Left -> SnakeCell(x = head.x + offset, y = head.y)
                SnakeDirection.Right -> SnakeCell(x = head.x - offset, y = head.y)
            }
            wrapped(trailing)
        }
    }

    private fun advance(cell: SnakeCell, direction: SnakeDirection): SnakeCell = when (direction) {
        SnakeDirection.Up -> SnakeCell(x = cell.x, y = cell.y - 1)
        SnakeDirection.Down -> SnakeCell(x = cell.x, y = cell.y + 1)
        SnakeDirection.Left -> SnakeCell(x = cell.x - 1, y = cell.y)
        SnakeDirection.Right -> SnakeCell(x = cell.x + 1, y = cell.y)
    }

    /** Modular wrapping in both axes; there is no wall-collision branch. */
    private fun wrapped(cell: SnakeCell): SnakeCell = SnakeCell(
        x = ((cell.x % columns) + columns) % columns,
        y = ((cell.y % rows) + rows) % rows,
    )

    /**
     * Enumerates free cells and picks one, so placement always terminates.
     * Returns null when the board is full.
     */
    private fun placeFood(snake: List<SnakeCell>): SnakeCell? {
        val occupied = snake.toHashSet()
        val freeCells = ArrayList<SnakeCell>(columns * rows - occupied.size)
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val cell = SnakeCell(x, y)
                if (cell !in occupied) freeCells.add(cell)
            }
        }
        if (freeCells.isEmpty()) return null

        val index = foodIndexPicker(freeCells.size)
        require(index in freeCells.indices) {
            "foodIndexPicker returned $index outside 0 until ${freeCells.size}"
        }
        return freeCells[index]
    }

    private fun tickIntervalFor(fruitsEaten: Int): Long = maxOf(
        SnakeGameConfig.MINIMUM_TICK_INTERVAL_MILLIS,
        SnakeGameConfig.INITIAL_TICK_INTERVAL_MILLIS -
            fruitsEaten * SnakeGameConfig.TICK_REDUCTION_PER_FRUIT_MILLIS,
    )

    /** Fails loudly rather than letting a broken state escape the engine. */
    private fun validated(state: SnakeGameState): SnakeGameState {
        check(state.snake.isNotEmpty()) { "snake must never be empty" }
        state.snake.forEach { cell ->
            check(cell.x in 0 until columns && cell.y in 0 until rows) {
                "snake cell $cell is outside the ${columns}x$rows board"
            }
        }
        state.food?.let { food ->
            check(food.x in 0 until columns && food.y in 0 until rows) {
                "food $food is outside the ${columns}x$rows board"
            }
            check(food !in state.snake) { "food $food overlaps the snake" }
        }
        check(state.score >= 0) { "score must not be negative" }
        check(state.fruitsEaten >= 0) { "fruitsEaten must not be negative" }
        check(state.queuedDirections.size <= SnakeGameConfig.MAX_DIRECTION_QUEUE_SIZE) {
            "direction queue exceeded ${SnakeGameConfig.MAX_DIRECTION_QUEUE_SIZE}"
        }
        check(state.tickIntervalMillis >= SnakeGameConfig.MINIMUM_TICK_INTERVAL_MILLIS) {
            "tick interval dropped below the floor"
        }
        when (state.phase) {
            SnakeGamePhase.Ready -> {
                check(state.direction == null) { "Ready must not have a direction" }
                check(state.queuedDirections.isEmpty()) { "Ready must not queue directions" }
                check(state.endReason == null) { "Ready must not have an end reason" }
            }

            SnakeGamePhase.Playing -> {
                check(state.direction != null) { "Playing requires a direction" }
                check(state.food != null) { "Playing requires food" }
                check(state.endReason == null) { "Playing must not have an end reason" }
            }

            SnakeGamePhase.Finished -> {
                check(state.endReason != null) { "Finished requires an end reason" }
            }
        }
        return state
    }
}
