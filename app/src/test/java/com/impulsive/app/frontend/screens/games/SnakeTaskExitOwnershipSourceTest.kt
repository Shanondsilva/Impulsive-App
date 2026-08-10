package com.impulsive.app.frontend.screens.games

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A task Result progresses only through its explicit actions, and a task left
 * before completing must report an adaptive outcome of false.
 */
class SnakeTaskExitOwnershipSourceTest {

    private val screenSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/games/SnakeGameScreen.kt",
    ).readText()
    private val compact = screenSource.replace(Regex("\\s+"), " ")

    @Test
    fun `every task result locks Back, valid or not`() {
        assertTrue(
            compact.contains(
                "val taskResultLocksBack = taskLaunch && " +
                    "uiState.view == SnakeGameView.Result",
            ),
        )
    }

    @Test
    fun `pending persistence also blocks Back`() {
        assertTrue(compact.contains("val resultPersistenceBlocksBack"))
        assertTrue(compact.contains("!uiState.isGameStoreResultDurable"))
        assertTrue(
            compact.contains(
                "taskRewardPersistenceState != SnakeTaskRewardPersistenceState.Persisted",
            ),
        )
    }

    @Test
    fun `one policy drives both header and system Back`() {
        assertTrue(
            compact.contains(
                "val backAllowed = !taskResultLocksBack && !resultPersistenceBlocksBack",
            ),
        )
        // System Back.
        assertTrue(compact.contains("if (backAllowed) exitCurrentFlow()"))
        // Header Back.
        assertTrue(compact.contains("if (backAllowed) { IconButton(onClick = onBack)"))
    }

    @Test
    fun `a task abandoned before its result reports completed false`() {
        assertTrue(
            compact.contains(
                "val exitCurrentFlow: () -> Unit = if (taskLaunch) " +
                    "{ { exitWithAdaptiveOutcome(completed = false) } } else { exitSafely }",
            ),
        )
    }

    @Test
    fun `valid task completion reports completed true`() {
        assertTrue(compact.contains("onTaskReturnProtected = { exitWithAdaptiveOutcome(completed = true) }"))
        assertTrue(compact.contains("onTaskViewNextWindow = { exitWithAdaptiveOutcome(completed = true) }"))
    }

    @Test
    fun `support cycle ownership always resolves before leaving`() {
        val adaptiveExit = compact
            .substringAfter("fun exitWithAdaptiveOutcome(completed: Boolean)")
            .take(200)

        assertTrue(adaptiveExit.contains("viewModel.finishSupportCycleAfterChoice"))
        // Never bypassed by calling the navigation callback directly.
        assertFalse(adaptiveExit.contains("onAdaptiveExit?.invoke(completed) } }"))
    }

    @Test
    fun `a recovery hub launch keeps the normal safe exit`() {
        assertTrue(
            compact.contains(
                "val exitSafely: () -> Unit = " +
                    "{ viewModel.finishSupportCycleAfterChoice(onExit) }",
            ),
        )
    }

    @Test
    fun `Walked keeps its direct exit because Walk Away already resolved`() {
        assertTrue(compact.contains("onDone = onExit"))
        assertTrue(compact.contains("uiState.view != SnakeGameView.Walked"))
    }
}
