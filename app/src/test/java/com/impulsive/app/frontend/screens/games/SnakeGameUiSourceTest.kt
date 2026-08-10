package com.impulsive.app.frontend.screens.games

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural guards for the Snake UI that a local JVM test cannot express
 * through the Compose runtime: shell reuse, input paths, accessibility,
 * reduced-motion safety and the absence of Reflex leftovers.
 */
class SnakeGameUiSourceTest {

    private val screenSource = source("SnakeGameScreen.kt")
    private val boardSource = source("SnakeGameBoard.kt")
    private val uiSources = listOf(screenSource, boardSource)

    @Test
    fun `the screen reuses the shared Impulsive game shell`() {
        assertTrue(screenSource.contains("AdaptiveGameContainer("))
        assertTrue(screenSource.contains("text = \"Snake\""))
        assertTrue(screenSource.contains("GameSoundToggle("))
    }

    @Test
    fun `the board is drawn on a Canvas with the approved palette`() {
        assertTrue(boardSource.contains("Canvas("))
        assertTrue(boardSource.contains("0xFFAAD751"))
        assertTrue(boardSource.contains("0xFFA2D149"))
        assertTrue(boardSource.contains("0xFF4E7CF6"))
        assertTrue(boardSource.contains("0xFFE7471D"))
    }

    @Test
    fun `the board keeps the engine grid ratio`() {
        assertTrue(boardSource.contains("SnakeGameConfig.DEFAULT_COLUMNS"))
        assertTrue(boardSource.contains("SnakeGameConfig.DEFAULT_ROWS"))
        assertTrue(boardSource.contains("BoxWithConstraints("))
        assertTrue(boardSource.contains("SnakeBoardAspectRatio"))
    }

    @Test
    fun `the board accepts swipe input`() {
        assertTrue(boardSource.contains("detectDragGestures("))
        // One physical swipe must emit at most one direction.
        assertTrue(boardSource.contains("directionSent"))
    }

    @Test
    fun `the board accepts keyboard and D-pad input`() {
        assertTrue(boardSource.contains("onPreviewKeyEvent"))
        assertTrue(boardSource.contains("Key.DirectionUp"))
        assertTrue(boardSource.contains("Key.DirectionDown"))
        assertTrue(boardSource.contains("Key.DirectionLeft"))
        assertTrue(boardSource.contains("Key.DirectionRight"))
        assertTrue(boardSource.contains("KeyEventType.KeyDown"))
    }

    @Test
    fun `the board is reachable without touch gestures`() {
        assertTrue(boardSource.contains("semantics {"))
        assertTrue(boardSource.contains("customActions"))
        assertTrue(boardSource.contains("CustomAccessibilityAction("))
        assertTrue(boardSource.contains("contentDescription = \"Snake game board\""))
        assertTrue(boardSource.contains("stateDescription"))

        listOf("Move up", "Move down", "Move left", "Move right").forEach { action ->
            assertTrue("missing custom action $action", boardSource.contains("\"$action\""))
        }
    }

    @Test
    fun `pause offers a real Material button and Back is a labelled IconButton`() {
        assertTrue(boardSource.contains("Button("))
        assertTrue(boardSource.contains("Text(\"Resume\")"))
        assertTrue(screenSource.contains("IconButton("))
        assertTrue(screenSource.contains("contentDescription = \"Back\""))
    }

    @Test
    fun `lifecycle drives pause and resume`() {
        assertTrue(screenSource.contains("Lifecycle.Event.ON_RESUME -> viewModel.resume()"))
        assertTrue(screenSource.contains("Lifecycle.Event.ON_STOP -> viewModel.pause()"))
    }

    @Test
    fun `the play loop uses the frame clock and owns no timer`() {
        assertTrue(screenSource.contains("withFrameMillis"))
        assertTrue(screenSource.contains("viewModel.tick()"))
        /*
         * No second *game* clock. The only delay is the one-minute release-plan
         * refresh shared by every task screen, and BackHandler is navigation.
         */
        assertFalse(screenSource.contains("delay(16"))
        assertFalse(screenSource.contains("Timer("))
        assertFalse(screenSource.contains("android.os.Handler"))
        assertFalse(screenSource.contains("SystemClock"))
    }

    @Test
    fun `there is no start gate or countdown`() {
        uiSources.forEach { source ->
            assertFalse(source.contains("startCountdown"))
            assertFalse(source.contains("countdown"))
            assertFalse(source.contains("Text(\"Start\")"))
        }
    }

    @Test
    fun `no Reflex implementation leaks into Snake`() {
        uiSources.forEach { source ->
            assertFalse(source.contains("ReflexGameViewModel"))
            assertFalse(source.contains("ScoreGameType.ReflexOverride"))
            assertFalse(source.contains("REFLEX_OVERRIDE"))
        }
    }

    @Test
    fun `the Snake UI adds no navigation route`() {
        uiSources.forEach { source ->
            assertFalse(source.contains("AppRoutes"))
            assertFalse(source.contains("navController"))
            assertFalse(source.contains("NavHost"))
        }
    }

    @Test
    fun `results scroll for large font scales`() {
        assertTrue(screenSource.contains("verticalScroll("))
        assertTrue(screenSource.contains("rememberScrollState()"))
        assertTrue(screenSource.contains("FlowRow("))
    }

    @Test
    fun `no decorative animation is used`() {
        uiSources.forEach { source ->
            assertFalse(source.contains("rememberInfiniteTransition"))
            assertFalse(source.contains("animateFloatAsState"))
            assertFalse(source.contains("animateDpAsState"))
            assertFalse(source.contains("AnimatedVisibility"))
            assertFalse(source.contains("AnimatedContent"))
        }
    }

    @Test
    fun `the board draws itself without any asset`() {
        uiSources.forEach { source ->
            assertFalse(source.contains("painterResource"))
            assertFalse(source.contains("R.drawable"))
            assertFalse(source.contains("ImageBitmap"))
            assertFalse(source.contains("AsyncImage"))
            assertFalse(source.contains("http"))
        }
    }

    @Test
    fun `the result shows Snake metrics and no Reflex metrics`() {
        listOf(
            "Personal best",
            "Fruits eaten",
            "Time survived",
            "Previous score",
        ).forEach { stat ->
            assertTrue("missing stat $stat", screenSource.contains("\"$stat\""))
        }

        listOf(
            "Max combo",
            "Hits",
            "Misses",
            "Best reaction",
            "Difficulty reached",
            "Length",
        ).forEach { stat ->
            assertFalse("unexpected stat $stat", screenSource.contains("\"$stat\""))
        }
    }

    @Test
    fun `result actions match the completion rules`() {
        assertTrue(screenSource.contains("Text(\"Walk away\")"))
        assertTrue(screenSource.contains("Text(\"Play again\")"))
        assertTrue(screenSource.contains("Text(\"Play another\")"))
        assertTrue(screenSource.contains("Text(\"Play same game\")"))
        assertTrue(screenSource.contains("Text(\"Back\")"))
        assertTrue(screenSource.contains("Choosing to stop is the strongest move."))
    }

    @Test
    fun `walking away advertises no score bonus`() {
        assertFalse(screenSource.contains("WALK_AWAY_BONUS"))
        assertFalse(screenSource.contains("+2000"))
        assertFalse(screenSource.contains("2000"))
        assertFalse(screenSource.contains("bonus"))
    }

    @Test
    fun `walking away does not finish the support cycle twice`() {
        val walked = screenSource.substringAfter("SnakeGameView.Walked -> SnakeWalkedPanel")
            .substringBefore("private fun SnakePlayPanel")

        assertTrue(walked.contains("onDone"))
        assertFalse(walked.contains("finishSupportCycleAfterChoice"))
    }

    // ------------------------------------------------------------------
    // SNAKE-03H hardening
    // ------------------------------------------------------------------

    @Test
    fun `the body is drawn as one continuous snake`() {
        assertTrue(boardSource.contains("snake.zipWithNext()"))
        assertTrue(boardSource.contains("snakeCellConnection("))

        // The disconnected-bead rendering must be gone.
        assertFalse(boardSource.contains("cellWidth * 0.08f"))
        assertFalse(boardSource.contains("cellHeight * 0.08f"))
        assertFalse(boardSource.contains("indexFromHead"))
        assertFalse("body must not alternate colours", boardSource.contains("SnakeBodyDark"))
    }

    @Test
    fun `wrapped neighbours are drawn as two edge stubs`() {
        assertTrue(boardSource.contains("SnakeCellConnection.WrappedHorizontal"))
        assertTrue(boardSource.contains("SnakeCellConnection.WrappedVertical"))
        assertTrue(boardSource.contains("SnakeCellConnection.Disconnected"))

        val wrapped = boardSource
            .substringAfter("SnakeCellConnection.WrappedHorizontal ->")
            .substringBefore("SnakeCellConnection.Disconnected")

        // Stubs run off the board edges rather than spanning it.
        assertTrue(wrapped.contains("size.width - rightCenter.x"))
        assertTrue(wrapped.contains("size.height - bottomCenter.y"))
        assertTrue(wrapped.contains("Offset(0f, leftCenter.y - half)"))
        assertTrue(wrapped.contains("Offset(topCenter.x - half, 0f)"))
    }

    @Test
    fun `apple geometry stays inside its own cell`() {
        // Radius-relative offsets could reach above row 0; cell-relative cannot.
        assertFalse(boardSource.contains("radius * 1.5f"))
        assertFalse(boardSource.contains("radius * 1.6f"))
        assertTrue(boardSource.contains("val cellTop = food.y * cellHeight"))
        assertTrue(boardSource.contains("cellTop + cellHeight * 0.17f"))
        assertTrue(boardSource.contains("cellTop + cellHeight * 0.12f"))
    }

    @Test
    fun `the rating row owns no local selection state`() {
        assertFalse(screenSource.contains("rememberSaveable"))
        assertFalse(screenSource.contains("mutableStateOf<Int?>"))
        assertTrue(screenSource.contains("selectedRating = uiState.urgeAfterRating"))
        assertTrue(screenSource.contains("selectedRating: Int?"))
    }

    @Test
    fun `the rating row is an accessible selection group sized for large text`() {
        assertTrue(screenSource.contains("selectableGroup()"))
        assertTrue(screenSource.contains("Role.RadioButton"))
        assertTrue(screenSource.contains("LocalDensity.current.fontScale"))
        assertTrue(screenSource.contains("minWidth = 48.dp, minHeight = 48.dp"))
        assertTrue(screenSource.contains("fontScale >= 1.75f -> 48.dp"))
        assertTrue(screenSource.contains("maxLines = 1"))
        assertTrue(screenSource.contains("softWrap = false"))
    }

    @Test
    fun `previews cover the wrap seam and the hardest rating label`() {
        assertTrue(screenSource.contains("Snake edge wrap + top fruit"))
        assertTrue(screenSource.contains("SnakeCell(5, 0)"))
        assertTrue(screenSource.contains("fontScale = 2f"))
        assertTrue(screenSource.contains("urgeAfterRating = 10"))
    }

    private fun source(fileName: String): String = File(
        "src/main/java/com/impulsive/app/frontend/screens/games/$fileName",
    ).readText()
}
