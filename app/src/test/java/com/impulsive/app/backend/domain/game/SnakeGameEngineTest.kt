package com.impulsive.app.backend.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeGameEngineTest {

    // ------------------------------------------------------------------
    // Construction / Ready
    // ------------------------------------------------------------------

    @Test
    fun `default engine uses an 18 by 24 board`() {
        val engine = engine()

        assertEquals(18, engine.columns)
        assertEquals(24, engine.rows)
    }

    @Test
    fun `initial phase is Ready`() {
        assertEquals(SnakeGamePhase.Ready, engine().state.phase)
    }

    @Test
    fun `initial snake has four segments`() {
        assertEquals(4, engine().state.snake.size)
    }

    @Test
    fun `initial direction is null`() {
        assertNull(engine().state.direction)
    }

    @Test
    fun `food is null before the first interaction`() {
        assertNull(engine().state.food)
    }

    @Test
    fun `initial score is zero`() {
        assertEquals(0, engine().state.score)
    }

    @Test
    fun `initial fruits eaten is zero`() {
        assertEquals(0, engine().state.fruitsEaten)
    }

    @Test
    fun `initial tick interval is 220 millis`() {
        assertEquals(220L, engine().state.tickIntervalMillis)
    }

    @Test
    fun `too few columns is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameEngine(columns = 5, rows = 24, foodIndexPicker = { 0 })
        }
    }

    @Test
    fun `too few rows is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameEngine(columns = 18, rows = 5, foodIndexPicker = { 0 })
        }
    }

    @Test
    fun `initial preview snake is inside the board`() {
        val engine = engine()

        engine.state.snake.forEach { cell ->
            assertTrue(cell.x in 0 until engine.columns)
            assertTrue(cell.y in 0 until engine.rows)
        }
    }

    // ------------------------------------------------------------------
    // First interaction
    // ------------------------------------------------------------------

    @Test
    fun `first Up interaction starts the game`() {
        assertFirstDirectionStarts(SnakeDirection.Up)
    }

    @Test
    fun `first Down interaction starts the game`() {
        assertFirstDirectionStarts(SnakeDirection.Down)
    }

    @Test
    fun `first Left interaction starts the game`() {
        assertFirstDirectionStarts(SnakeDirection.Left)
    }

    @Test
    fun `first Right interaction starts the game`() {
        assertFirstDirectionStarts(SnakeDirection.Right)
    }

    @Test
    fun `each first direction trails the body behind the head`() {
        mapOf(
            SnakeDirection.Right to SnakeCell(-1, 0),
            SnakeDirection.Left to SnakeCell(1, 0),
            SnakeDirection.Up to SnakeCell(0, 1),
            SnakeDirection.Down to SnakeCell(0, -1),
        ).forEach { (direction, trailStep) ->
            val engine = engine()
            val previewHead = engine.state.snake.first()
            val snake = engine.changeDirection(direction).snake

            assertEquals("head must not move on the first input", previewHead, snake.first())
            snake.forEachIndexed { index, cell ->
                assertEquals(
                    "body segment $index for $direction",
                    SnakeCell(
                        x = previewHead.x + trailStep.x * index,
                        y = previewHead.y + trailStep.y * index,
                    ),
                    cell,
                )
            }
        }
    }

    @Test
    fun `first interaction does not advance a movement tick`() {
        val engine = engine()
        val previewHead = engine.state.snake.first()

        val started = engine.changeDirection(SnakeDirection.Right)

        assertEquals(previewHead, started.snake.first())
        assertEquals(4, started.snake.size)
    }

    @Test
    fun `first interaction places food outside the snake`() {
        val engine = engine()

        val started = engine.changeDirection(SnakeDirection.Right)

        val food = requireNotNull(started.food)
        assertFalse(food in started.snake)
        assertTrue(food.x in 0 until engine.columns)
        assertTrue(food.y in 0 until engine.rows)
    }

    // ------------------------------------------------------------------
    // Normal movement
    // ------------------------------------------------------------------

    @Test
    fun `Right moves one cell right`() {
        assertSingleStepMoves(SnakeDirection.Right, dx = 1, dy = 0)
    }

    @Test
    fun `Left moves one cell left`() {
        assertSingleStepMoves(SnakeDirection.Left, dx = -1, dy = 0)
    }

    @Test
    fun `Up moves one cell up`() {
        assertSingleStepMoves(SnakeDirection.Up, dx = 0, dy = -1)
    }

    @Test
    fun `Down moves one cell down`() {
        assertSingleStepMoves(SnakeDirection.Down, dx = 0, dy = 1)
    }

    @Test
    fun `length is unchanged when no fruit is eaten`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(3) { engine.step() }

        assertEquals(4, engine.state.snake.size)
        assertEquals(0, engine.state.fruitsEaten)
    }

    @Test
    fun `old tail is removed when no fruit is eaten`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)
        val previousTail = engine.state.snake.last()

        engine.step()

        assertFalse(previousTail in engine.state.snake)
    }

    // ------------------------------------------------------------------
    // Wall wrap
    // ------------------------------------------------------------------

    @Test
    fun `moving right from the last column appears in column zero`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)

        val head = stepUntilWrap(engine) { it.x }

        assertEquals(0, head.x)
        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
    }

    @Test
    fun `moving left from column zero appears in the last column`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Left)

        val head = stepUntilWrap(engine) { it.x }

        assertEquals(engine.columns - 1, head.x)
        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
    }

    @Test
    fun `moving up from row zero appears in the last row`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Up)

        val head = stepUntilWrap(engine) { it.y }

        assertEquals(engine.rows - 1, head.y)
        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
    }

    @Test
    fun `moving down from the last row appears in row zero`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Down)

        val head = stepUntilWrap(engine) { it.y }

        assertEquals(0, head.y)
        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
    }

    @Test
    fun `every segment stays in bounds across repeated wrapping`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(engine.columns * 3) {
            engine.step()
            engine.state.snake.forEach { cell ->
                assertTrue(cell.x in 0 until engine.columns)
                assertTrue(cell.y in 0 until engine.rows)
            }
        }
    }

    @Test
    fun `wrapping does not end the game`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(engine.columns * 2) { engine.step() }

        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
        assertNull(engine.state.endReason)
    }

    // ------------------------------------------------------------------
    // Direction safety
    // ------------------------------------------------------------------

    @Test
    fun `Right cannot reverse into Left`() {
        assertReversalRejected(SnakeDirection.Right, SnakeDirection.Left)
    }

    @Test
    fun `Left cannot reverse into Right`() {
        assertReversalRejected(SnakeDirection.Left, SnakeDirection.Right)
    }

    @Test
    fun `Up cannot reverse into Down`() {
        assertReversalRejected(SnakeDirection.Up, SnakeDirection.Down)
    }

    @Test
    fun `Down cannot reverse into Up`() {
        assertReversalRejected(SnakeDirection.Down, SnakeDirection.Up)
    }

    @Test
    fun `repeated same direction input is ignored`() {
        val engine = startedEngine(SnakeDirection.Right)

        engine.changeDirection(SnakeDirection.Right)

        assertTrue(engine.state.queuedDirections.isEmpty())
    }

    @Test
    fun `queue never exceeds two entries`() {
        val engine = startedEngine(SnakeDirection.Right)

        engine.changeDirection(SnakeDirection.Up)
        engine.changeDirection(SnakeDirection.Left)
        engine.changeDirection(SnakeDirection.Down)

        assertEquals(
            listOf(SnakeDirection.Up, SnakeDirection.Left),
            engine.state.queuedDirections,
        )
    }

    @Test
    fun `Right then queued Up then Left executes across two ticks`() {
        val engine = startedEngine(SnakeDirection.Right)
        val start = engine.state.snake.first()

        engine.changeDirection(SnakeDirection.Up)
        engine.changeDirection(SnakeDirection.Left)
        assertEquals(
            listOf(SnakeDirection.Up, SnakeDirection.Left),
            engine.state.queuedDirections,
        )

        engine.step()
        assertEquals(SnakeDirection.Up, engine.state.direction)
        assertEquals(SnakeCell(start.x, start.y - 1), engine.state.snake.first())

        engine.step()
        assertEquals(SnakeDirection.Left, engine.state.direction)
        assertEquals(SnakeCell(start.x - 1, start.y - 1), engine.state.snake.first())
        assertTrue(engine.state.queuedDirections.isEmpty())
    }

    @Test
    fun `Down is rejected when Up is already the last planned direction`() {
        val engine = startedEngine(SnakeDirection.Right)

        engine.changeDirection(SnakeDirection.Up)
        engine.changeDirection(SnakeDirection.Down)

        assertEquals(listOf(SnakeDirection.Up), engine.state.queuedDirections)
    }

    @Test
    fun `rapid queued input cannot manufacture a reversal`() {
        val engine = startedEngine(SnakeDirection.Right)
        val start = engine.state.snake.first()

        // Up is legal; Down would reverse the queued Up and must be dropped.
        engine.changeDirection(SnakeDirection.Up)
        engine.changeDirection(SnakeDirection.Down)

        engine.step()
        engine.step()

        // The head never re-entered the cell directly behind the original neck.
        assertNotEquals(SnakeCell(start.x - 1, start.y), engine.state.snake.first())
        assertEquals(engine.state.snake.distinct().size, engine.state.snake.size)
    }

    // ------------------------------------------------------------------
    // Food / growth
    // ------------------------------------------------------------------

    @Test
    fun `eating fruit grows the snake by exactly one`() {
        val engine = engineWithFoodAhead()

        engine.step()

        assertEquals(5, engine.state.snake.size)
    }

    @Test
    fun `eating fruit increments fruits eaten`() {
        val engine = engineWithFoodAhead()

        engine.step()

        assertEquals(1, engine.state.fruitsEaten)
    }

    @Test
    fun `eating fruit adds exactly ten score`() {
        val engine = engineWithFoodAhead()

        engine.step()

        assertEquals(10, engine.state.score)
    }

    @Test
    fun `replacement fruit never occupies the snake`() {
        val engine = engine(foodIndexPicker = { 0 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(60) {
            engine.step()
            engine.state.food?.let { food ->
                assertFalse("food $food overlapped the snake", food in engine.state.snake)
            }
        }
    }

    @Test
    fun `deterministic picker selects the expected free cell`() {
        // Free cells are enumerated row-major, so index 0 is the first
        // unoccupied cell scanning from (0,0).
        val engine = engine(foodIndexPicker = { 0 })

        val started = engine.changeDirection(SnakeDirection.Right)

        assertEquals(SnakeCell(0, 0), started.food)
    }

    @Test
    fun `negative picker index fails loudly`() {
        val engine = engine(foodIndexPicker = { -1 })

        assertThrows(IllegalArgumentException::class.java) {
            engine.changeDirection(SnakeDirection.Right)
        }
    }

    @Test
    fun `picker index at or beyond the free cell count fails loudly`() {
        val engine = engine(foodIndexPicker = { bound -> bound })

        assertThrows(IllegalArgumentException::class.java) {
            engine.changeDirection(SnakeDirection.Right)
        }
    }

    // ------------------------------------------------------------------
    // Tail collision correctness
    // ------------------------------------------------------------------

    @Test
    fun `moving into a non-tail body segment ends with self collision`() {
        // Grow to six so a tight loop reaches a mid-body segment, not the tail.
        val aimed = AimedEngine(columns = 18, rows = 24)
        startAndGrow(aimed, targetLength = 6, direction = SnakeDirection.Right)
        aimed.target = null
        val engine = aimed.engine

        // A four-cell box turns back into the body rather than the tail.
        engine.changeDirection(SnakeDirection.Up)
        engine.step()
        engine.changeDirection(SnakeDirection.Left)
        engine.step()
        engine.changeDirection(SnakeDirection.Down)
        engine.step()

        assertEquals(SnakeGamePhase.Finished, engine.state.phase)
        assertEquals(SnakeRoundEndReason.SelfCollision, engine.state.endReason)
    }

    @Test
    fun `moving into the current tail cell while not eating is legal`() {
        // A length-4 snake turning in a tight square re-enters the cell the
        // tail vacates on the same tick. That must survive.
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)
        val tailBeforeTurn = engine.state.snake.last()

        engine.changeDirection(SnakeDirection.Up)
        engine.step()
        engine.changeDirection(SnakeDirection.Left)
        engine.step()
        engine.changeDirection(SnakeDirection.Down)
        val targetTail = engine.state.snake.last()
        engine.step()

        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
        assertNull(engine.state.endReason)
        assertEquals(targetTail, engine.state.snake.first())
        assertEquals(4, engine.state.snake.size)
        assertNotEquals(tailBeforeTurn, engine.state.snake.last())
    }

    @Test
    fun `a growing move never creates duplicate snake cells`() {
        val engine = engine(foodIndexPicker = { 0 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(80) {
            engine.step()
            if (engine.state.phase == SnakeGamePhase.Playing) {
                assertEquals(
                    "duplicate cells in ${engine.state.snake}",
                    engine.state.snake.distinct().size,
                    engine.state.snake.size,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Edge + self collision
    // ------------------------------------------------------------------

    @Test
    fun `wrapping into empty space is safe`() {
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(engine.columns) { engine.step() }

        assertEquals(SnakeGamePhase.Playing, engine.state.phase)
        assertNull(engine.state.endReason)
    }

    @Test
    fun `wrapping directly into the body ends with self collision`() {
        // The head is at the last column; wrapping right lands on a mid-body
        // segment. Wrapping must never exempt the collision check.
        val engine = wrapIntoBodyEngine()
        val head = engine.state.snake.first()
        assertEquals(engine.columns - 1, head.x)
        assertNotEquals(SnakeCell(0, head.y), engine.state.snake.last())
        assertTrue(SnakeCell(0, head.y) in engine.state.snake.dropLast(1))

        engine.step()

        assertEquals(SnakeGamePhase.Finished, engine.state.phase)
        assertEquals(SnakeRoundEndReason.SelfCollision, engine.state.endReason)
    }

    @Test
    fun `self collision preserves the previous valid snake`() {
        val engine = wrapIntoBodyEngine()
        val snakeBefore = engine.state.snake
        val scoreBefore = engine.state.score

        engine.step()

        assertEquals(snakeBefore, engine.state.snake)
        assertEquals(scoreBefore, engine.state.score)
    }

    // ------------------------------------------------------------------
    // Speed
    // ------------------------------------------------------------------

    @Test
    fun `speed progression is deterministic and floors at 120 millis`() {
        mapOf(0 to 220L, 1 to 215L, 10 to 170L, 20 to 120L, 40 to 120L).forEach { (fruit, expected) ->
            val aimed = AimedEngine(columns = 18, rows = 24)
            val engine = aimed.engine
            val path = serpentinePath(engine.columns, engine.rows)
            val startIndex = path.indexOf(engine.state.snake.first())

            aimed.occupiedDuringPlacement = engine.state.snake
            aimed.target = path[(startIndex + 1) % path.size]
            engine.changeDirection(SnakeDirection.Right)

            eatFruit(aimed, times = fruit)

            assertEquals("after $fruit fruit", expected, engine.state.tickIntervalMillis)
        }
    }

    @Test
    fun `tick interval never drops below the floor across long play`() {
        val engine = engine(foodIndexPicker = { 0 })
        engine.changeDirection(SnakeDirection.Right)

        repeat(200) {
            engine.step()
            assertTrue(engine.state.tickIntervalMillis >= 120L)
        }
    }

    // ------------------------------------------------------------------
    // Terminal state
    // ------------------------------------------------------------------

    @Test
    fun `step is a no-op after the round finishes`() {
        val engine = finishedBySelfCollision()
        val finished = engine.state

        engine.step()

        assertEquals(finished, engine.state)
    }

    @Test
    fun `direction input is a no-op after the round finishes`() {
        val engine = finishedBySelfCollision()
        val finished = engine.state

        engine.changeDirection(SnakeDirection.Up)

        assertEquals(finished, engine.state)
    }

    @Test
    fun `finishForTimeLimit while playing sets TimeLimit and preserves progress`() {
        val engine = engineWithFoodAhead()
        engine.step()
        val playing = engine.state

        val finished = engine.finishForTimeLimit()

        assertEquals(SnakeGamePhase.Finished, finished.phase)
        assertEquals(SnakeRoundEndReason.TimeLimit, finished.endReason)
        assertEquals(playing.snake, finished.snake)
        assertEquals(playing.food, finished.food)
        assertEquals(playing.fruitsEaten, finished.fruitsEaten)
        assertEquals(playing.score, finished.score)
    }

    @Test
    fun `finishForTimeLimit while Ready is a no-op`() {
        val engine = engine()
        val ready = engine.state

        val result = engine.finishForTimeLimit()

        assertEquals(ready, result)
        assertEquals(SnakeGamePhase.Ready, result.phase)
        assertNull(result.endReason)
    }

    @Test
    fun `finishForTimeLimit after finishing preserves the original reason`() {
        val engine = finishedBySelfCollision()

        val result = engine.finishForTimeLimit()

        assertEquals(SnakeRoundEndReason.SelfCollision, result.endReason)
    }

    // ------------------------------------------------------------------
    // Board completion
    // ------------------------------------------------------------------

    @Test
    fun `filling the board finishes with BoardCleared and no food`() {
        /*
         * A 6x6 board holds 36 cells. The snake walks a serpentine path that
         * visits every cell exactly once, and each fruit is placed on the very
         * next cell of that path, so it grows on every tick until the board is
         * full. Everything happens through legal moves.
         */
        val aimed = AimedEngine(columns = 6, rows = 6)
        val engine = aimed.engine
        val path = serpentinePath(columns = 6, rows = 6)

        // Start the head at the beginning of the path, heading right.
        val startIndex = path.indexOf(engine.state.snake.first())
        assertTrue("preview head must lie on the serpentine path", startIndex >= 0)

        var snake = engine.state.snake
        aimed.occupiedDuringPlacement = snake
        aimed.target = path[(startIndex + 1) % path.size]
        engine.changeDirection(SnakeDirection.Right)

        var guard = 0
        while (engine.state.phase == SnakeGamePhase.Playing && guard < 200) {
            // Re-derive the cursor from the live head so it can never desync.
            val cursor = path.indexOf(engine.state.snake.first())
            assertTrue("head left the serpentine path", cursor >= 0)
            val nextCell = path[(cursor + 1) % path.size]
            val direction = directionBetween(engine, engine.state.snake.first(), nextCell)

            snake = listOf(nextCell) + engine.state.snake
            aimed.occupiedDuringPlacement = snake
            aimed.target = path[(cursor + 2) % path.size]

            engine.changeDirection(direction)
            engine.step()
            guard++
        }

        assertEquals(SnakeGamePhase.Finished, engine.state.phase)
        assertEquals(SnakeRoundEndReason.BoardCleared, engine.state.endReason)
        assertNull(engine.state.food)
        assertEquals(36, engine.state.snake.size)
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    @Test
    fun `reset after active play returns a clean Ready state`() {
        val engine = engineWithFoodAhead()
        repeat(4) { engine.step() }

        assertCleanReadyState(engine.reset(), engine)
    }

    @Test
    fun `reset after self collision returns a clean Ready state`() {
        val engine = finishedBySelfCollision()

        assertCleanReadyState(engine.reset(), engine)
    }

    @Test
    fun `reset after a time limit finish returns a clean Ready state`() {
        val engine = engineWithFoodAhead()
        engine.step()
        engine.finishForTimeLimit()

        assertCleanReadyState(engine.reset(), engine)
    }

    @Test
    fun `reset clears queued directions`() {
        val engine = startedEngine(SnakeDirection.Right)
        engine.changeDirection(SnakeDirection.Up)
        engine.changeDirection(SnakeDirection.Left)

        assertTrue(engine.reset().queuedDirections.isEmpty())
    }

    @Test
    fun `reset does not consume a food random selection`() {
        var pickerCalls = 0
        val engine = engine(
            foodIndexPicker = {
                pickerCalls++
                0
            },
        )

        engine.changeDirection(SnakeDirection.Right)
        val callsAfterStart = pickerCalls

        engine.reset()

        assertEquals(callsAfterStart, pickerCalls)
        assertNull(engine.state.food)
    }

    // ------------------------------------------------------------------
    // Invariant / stress
    // ------------------------------------------------------------------

    @Test
    fun `deterministic long run preserves every engine invariant`() {
        val engine = engine(foodIndexPicker = { bound -> (bound - 1) / 2 })
        engine.changeDirection(SnakeDirection.Right)

        // A repeating legal steering cycle: never reverses, exercises turns,
        // wrapping and growth without depending on randomness.
        val cycle = listOf(
            SnakeDirection.Right,
            SnakeDirection.Up,
            SnakeDirection.Left,
            SnakeDirection.Down,
        )
        var previousScore = 0
        var previousFruits = 0
        var steps = 0

        for (index in 0 until 600) {
            if (engine.state.phase != SnakeGamePhase.Playing) break
            if (index % 3 == 0) engine.changeDirection(cycle[(index / 3) % cycle.size])
            engine.step()
            steps++

            val current = engine.state
            current.snake.forEach { cell ->
                assertTrue("out of bounds $cell", cell.x in 0 until engine.columns)
                assertTrue("out of bounds $cell", cell.y in 0 until engine.rows)
            }
            current.food?.let { food ->
                assertTrue(food.x in 0 until engine.columns)
                assertTrue(food.y in 0 until engine.rows)
                assertFalse("food inside snake", food in current.snake)
            }
            assertTrue("score decreased", current.score >= previousScore)
            assertTrue("fruits decreased", current.fruitsEaten >= previousFruits)
            assertTrue(current.tickIntervalMillis >= 120L)
            if (current.phase == SnakeGamePhase.Playing) {
                assertEquals(
                    "duplicate snake cells",
                    current.snake.distinct().size,
                    current.snake.size,
                )
            }
            previousScore = current.score
            previousFruits = current.fruitsEaten
        }

        assertTrue("stress run should advance many ticks", steps > 100)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun engine(
        columns: Int = 18,
        rows: Int = 24,
        foodIndexPicker: (Int) -> Int = { 0 },
    ) = SnakeGameEngine(columns = columns, rows = rows, foodIndexPicker = foodIndexPicker)

    /**
     * An engine whose food always lands on [AimedEngine.target], letting a test
     * grow the snake deterministically through legal moves only. The engine
     * itself exposes no state mutation.
     */
    private class AimedEngine(val columns: Int, val rows: Int) {
        var target: SnakeCell? = null

        /**
         * The snake the engine is placing food against. During [step] the engine
         * has already grown the snake but not yet published the new state, so
         * the picker must resolve indices against this snapshot rather than
         * `engine.state`.
         */
        var occupiedDuringPlacement: List<SnakeCell> = emptyList()

        val engine: SnakeGameEngine = SnakeGameEngine(columns = columns, rows = rows) { bound ->
            val aim = target
            // Falls back to the last free cell when nothing is aimed or the
            // aimed cell is occupied, which parks food far from the head.
            val index = if (aim == null) -1 else freeCellIndex(aim)
            if (index in 0 until bound) index else bound - 1
        }

        /** Row-major index of [cell] within the current free-cell list. */
        fun freeCellIndex(cell: SnakeCell): Int {
            val occupied = occupiedDuringPlacement.toSet()
            if (cell in occupied) return -1
            var index = 0
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    val candidate = SnakeCell(x, y)
                    if (candidate in occupied) continue
                    if (candidate == cell) return index
                    index++
                }
            }
            return -1
        }
    }

    /**
     * Starts a round heading [direction] and grows the snake to [targetLength].
     *
     * Each fruit is aimed at the cell directly ahead of the head *before* it is
     * placed, so every tick is a legal move that eats. The engine exposes no
     * state mutation, so growth is produced only through real gameplay.
     */
    private fun startAndGrow(
        aimed: AimedEngine,
        targetLength: Int,
        direction: SnakeDirection,
    ) {
        val engine = aimed.engine
        // The round's first fruit must already sit one cell ahead of the head.
        val previewSnake = engine.state.snake
        aimed.occupiedDuringPlacement = previewSnake
        aimed.target = aheadOf(engine, previewSnake.first(), direction)
        engine.changeDirection(direction)

        var guard = 0
        while (engine.state.snake.size < targetLength && guard < 200) {
            val snake = engine.state.snake
            val nextHead = aheadOf(engine, snake.first(), direction)
            // Eating grows the snake, so the replacement fruit is placed against
            // head + previous body.
            val grownSnake = listOf(nextHead) + snake
            aimed.occupiedDuringPlacement = grownSnake
            /*
             * Stepping onto the fruit also places its replacement, so aim two
             * cells ahead: that is where the head will be on the next tick.
             */
            aimed.target = aheadOf(engine, nextHead, direction)
            engine.step()
            guard++
        }
        assertEquals(targetLength, engine.state.snake.size)
    }

    private fun aheadOf(
        engine: SnakeGameEngine,
        head: SnakeCell,
        direction: SnakeDirection,
    ): SnakeCell = when (direction) {
        SnakeDirection.Right -> SnakeCell((head.x + 1) % engine.columns, head.y)
        SnakeDirection.Left -> SnakeCell((head.x - 1 + engine.columns) % engine.columns, head.y)
        SnakeDirection.Up -> SnakeCell(head.x, (head.y - 1 + engine.rows) % engine.rows)
        SnakeDirection.Down -> SnakeCell(head.x, (head.y + 1) % engine.rows)
    }

    /**
     * Drives a snake, using only legal gameplay, into a shape where continuing
     * Right wraps the head from the last column into a **mid-body** segment
     * rather than the vacating tail:
     *
     * ```
     *   row 6:  b . . . b b          H = head, b = body, T = tail
     *   row 7:  b . . . b H
     *   row 0:  T . . . . .
     * ```
     *
     * A snake that merely fills its row would only ever chase its own vacating
     * tail, which is legal, so the route deliberately leaves the tail elsewhere.
     * The remaining fruit sits off the head's path, so the wrapping step is a
     * collision and not a bite.
     *
     * The `{ it - 1 }` picker takes the last free cell in row-major order, which
     * makes every fruit placement along this route deterministic.
     */
    private fun wrapIntoBodyEngine(): SnakeGameEngine {
        val engine = SnakeGameEngine(
            columns = 6,
            rows = 8,
            foodIndexPicker = { it - 1 },
        )

        engine.changeDirection(SnakeDirection.Right)

        repeat(2) {
            engine.step()
        }

        engine.changeDirection(SnakeDirection.Down)
        repeat(4) {
            engine.step()
        }

        engine.changeDirection(SnakeDirection.Right)
        engine.step()

        engine.changeDirection(SnakeDirection.Up)
        repeat(2) {
            engine.step()
        }

        engine.changeDirection(SnakeDirection.Left)
        repeat(2) {
            engine.step()
        }

        engine.changeDirection(SnakeDirection.Down)
        engine.step()

        engine.changeDirection(SnakeDirection.Right)
        engine.step()

        /*
         * Guards the route itself: if movement or food placement ever changes,
         * these fail here rather than silently weakening the collision test.
         */
        check(engine.state.phase == SnakeGamePhase.Playing)
        check(engine.state.snake.first() == SnakeCell(5, 7))
        check(engine.state.direction == SnakeDirection.Right)
        check(SnakeCell(0, 7) in engine.state.snake.dropLast(1))
        check(engine.state.snake.last() != SnakeCell(0, 7))

        return engine
    }

    private fun startedEngine(direction: SnakeDirection): SnakeGameEngine {
        // Park food in the last free cell so it is never eaten by accident.
        val engine = engine(foodIndexPicker = { it - 1 })
        engine.changeDirection(direction)
        return engine
    }

    /** A snake heading right with food in the cell directly ahead of the head. */
    private fun engineWithFoodAhead(): SnakeGameEngine {
        val aimed = AimedEngine(columns = 18, rows = 24)
        val preview = aimed.engine.state.snake.first()
        aimed.occupiedDuringPlacement = aimed.engine.state.snake
        aimed.target = SnakeCell(preview.x + 1, preview.y)
        val started = aimed.engine.changeDirection(SnakeDirection.Right)

        assertEquals(SnakeCell(preview.x + 1, preview.y), started.food)
        // Later fruit lands far away so only the first bite is under test.
        aimed.target = null
        return aimed.engine
    }

    private fun assertFirstDirectionStarts(direction: SnakeDirection) {
        val engine = engine()

        val started = engine.changeDirection(direction)

        assertEquals(SnakeGamePhase.Playing, started.phase)
        assertEquals(direction, started.direction)
        assertEquals(4, started.snake.size)
        assertTrue(started.queuedDirections.isEmpty())
        assertNull(started.endReason)
    }

    private fun assertSingleStepMoves(direction: SnakeDirection, dx: Int, dy: Int) {
        val engine = startedEngine(direction)
        val head = engine.state.snake.first()

        engine.step()

        assertEquals(SnakeCell(head.x + dx, head.y + dy), engine.state.snake.first())
    }

    private fun assertReversalRejected(from: SnakeDirection, into: SnakeDirection) {
        val engine = startedEngine(from)

        engine.changeDirection(into)

        assertTrue(engine.state.queuedDirections.isEmpty())
        engine.step()
        assertEquals(from, engine.state.direction)
    }

    private fun assertCleanReadyState(state: SnakeGameState, engine: SnakeGameEngine) {
        assertEquals(SnakeGamePhase.Ready, state.phase)
        assertEquals(4, state.snake.size)
        assertNull(state.direction)
        assertTrue(state.queuedDirections.isEmpty())
        assertEquals(0, state.fruitsEaten)
        assertEquals(0, state.score)
        assertEquals(220L, state.tickIntervalMillis)
        assertNull(state.endReason)
        assertNull(state.food)
        state.snake.forEach { cell ->
            assertTrue(cell.x in 0 until engine.columns)
            assertTrue(cell.y in 0 until engine.rows)
        }
    }

    /** Steps straight until the head wraps on the axis read by [axis]. */
    private fun stepUntilWrap(engine: SnakeGameEngine, axis: (SnakeCell) -> Int): SnakeCell {
        var previous = axis(engine.state.snake.first())
        repeat(engine.columns + engine.rows) {
            engine.step()
            val current = axis(engine.state.snake.first())
            val delta = current - previous
            if (delta > 1 || delta < -1) return engine.state.snake.first()
            previous = current
        }
        throw AssertionError("head never wrapped")
    }

    /**
     * A boustrophedon tour visiting every cell exactly once: left-to-right on
     * even rows, right-to-left on odd rows. Consecutive entries are always
     * orthogonally adjacent, so following it is always a legal snake move.
     */
    private fun serpentinePath(columns: Int, rows: Int): List<SnakeCell> = buildList {
        for (y in 0 until rows) {
            val xs = if (y % 2 == 0) 0 until columns else (columns - 1) downTo 0
            for (x in xs) add(SnakeCell(x, y))
        }
    }

    /** The direction moving from [from] to the adjacent cell [to], allowing wrap. */
    private fun directionBetween(
        engine: SnakeGameEngine,
        from: SnakeCell,
        to: SnakeCell,
    ): SnakeDirection = SnakeDirection.entries.firstOrNull { aheadOf(engine, from, it) == to }
        ?: throw AssertionError("$from and $to are not adjacent")

    /**
     * Feeds the snake exactly [times] fruit by walking the serpentine tour and
     * placing each fruit on the next cell of that tour.
     */
    private fun eatFruit(aimed: AimedEngine, times: Int) {
        if (times == 0) return
        val engine = aimed.engine
        val path = serpentinePath(engine.columns, engine.rows)

        var guard = 0
        while (engine.state.fruitsEaten < times && guard < 5_000) {
            // Re-derive the cursor from the live head so it can never desync.
            val cursor = path.indexOf(engine.state.snake.first())
            assertTrue("head must lie on the serpentine path", cursor >= 0)
            val nextCell = path[(cursor + 1) % path.size]
            val direction = directionBetween(engine, engine.state.snake.first(), nextCell)

            aimed.occupiedDuringPlacement = listOf(nextCell) + engine.state.snake
            aimed.target = path[(cursor + 2) % path.size]

            engine.changeDirection(direction)
            engine.step()
            guard++
            if (engine.state.phase != SnakeGamePhase.Playing) break
        }
        assertEquals("could not feed the snake $times times", times, engine.state.fruitsEaten)
    }

    private fun finishedBySelfCollision(): SnakeGameEngine {
        val engine = wrapIntoBodyEngine()
        engine.step()
        assertEquals(SnakeRoundEndReason.SelfCollision, engine.state.endReason)
        return engine
    }
}
