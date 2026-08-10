package com.impulsive.app.backend.session.tasks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A task reward started as fire-and-forget can be cancelled when the user
 * navigates away, so Snake awaits the repository write and only then treats the
 * completion token as spent.
 */
class SnakeTaskRewardDurabilitySourceTest {

    private val taskViewModelSource = source(
        "backend/session/tasks/TaskRewardViewModel.kt",
    )
    private val screenSource = source(
        "frontend/screens/games/SnakeGameScreen.kt",
    )
    private val compactScreen = screenSource.replace(Regex("\\s+"), " ")

    @Test
    fun `the task view model exposes an awaitable completion`() {
        assertTrue(taskViewModelSource.contains("suspend fun completeTaskAndAwait("))
        assertTrue(taskViewModelSource.contains("): TaskCompletionResult {"))

        val awaitable = taskViewModelSource
            .substringAfter("suspend fun completeTaskAndAwait(")
            .substringBefore("\n    }")

        assertTrue(awaitable.contains("repository.completeTask("))
        assertTrue(awaitable.contains("lastCompletionResult.value = result"))
        assertTrue(awaitable.contains("return result"))
        // Awaiting is the point: it must not launch and return immediately.
        assertFalse(awaitable.contains("viewModelScope.launch"))
    }

    @Test
    fun `the existing fire-and-forget api is preserved for other games`() {
        assertTrue(taskViewModelSource.contains("fun completeTask("))

        val legacy = taskViewModelSource
            .substringAfter("fun completeTask(")
            .substringBefore("suspend fun completeTaskAndAwait(")

        assertTrue(legacy.contains("viewModelScope.launch"))
        assertTrue(legacy.contains("completeTaskAndAwait("))
    }

    @Test
    fun `Snake awaits its reward instead of firing and forgetting`() {
        assertTrue(compactScreen.contains("taskRewardViewModel.completeTaskAndAwait("))
        assertFalse(compactScreen.contains("taskRewardViewModel.completeTask("))
    }

    @Test
    fun `the token is marked spent only after the write returns`() {
        val effect = compactScreen
            .substringAfter("taskRewardViewModel.completeTaskAndAwait(")

        val assignmentIndex = effect.indexOf("persistedTaskCompletionToken = taskCompletionToken")

        assertTrue(
            "the successful token must be assigned after the awaited call",
            assignmentIndex >= 0,
        )
    }

    @Test
    fun `a failed reward becomes retryable and cancellation is not swallowed`() {
        assertTrue(compactScreen.contains("SnakeTaskRewardPersistenceState.RetryableFailure"))
        assertTrue(compactScreen.contains("catch (cancellation: CancellationException) { throw cancellation }"))
        assertTrue(compactScreen.contains("taskRewardRetryGeneration += 1"))
        assertTrue(compactScreen.contains("Retry reward"))
    }

    @Test
    fun `the reward waits for the game store receipt first`() {
        assertTrue(
            compactScreen.contains(
                "if (uiState.gameStorePersistenceState != " +
                    "SnakeGameStorePersistenceState.Persisted) { " +
                    "taskRewardPersistenceState = " +
                    "SnakeTaskRewardPersistenceState.WaitingForGameStore",
            ),
        )
    }

    @Test
    fun `task completion actions appear only once the reward is persisted`() {
        assertTrue(compactScreen.contains("Return protected"))
        assertTrue(compactScreen.contains("View next window"))
        assertTrue(
            compactScreen.contains("SnakeTaskRewardPersistenceState.Persisted -> Unit"),
        )
    }

    @Test
    fun `no scope-escaping workaround is used`() {
        listOf("GlobalScope", "NonCancellable", "runBlocking").forEach { forbidden ->
            assertFalse(screenSource.contains(forbidden))
            assertFalse(taskViewModelSource.contains(forbidden))
        }
    }

    private fun source(relative: String): String = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()
}
