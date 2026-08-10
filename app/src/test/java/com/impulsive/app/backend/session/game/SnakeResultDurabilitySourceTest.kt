package com.impulsive.app.backend.session.game

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigating away from a Snake result clears its ViewModel and cancels any
 * in-flight write, so the result must not become actionable until the Game
 * Store receipt is durable.
 */
class SnakeResultDurabilitySourceTest {

    private val viewModelSource = source(
        "backend/session/game/SnakeGameViewModel.kt",
    )
    private val modelsSource = source(
        "backend/domain/game/SnakeGameSessionModels.kt",
    )
    private val compactViewModel = viewModelSource.replace(Regex("\\s+"), " ")

    @Test
    fun `ui state carries a game store persistence state`() {
        assertTrue(modelsSource.contains("enum class SnakeGameStorePersistenceState"))
        assertTrue(modelsSource.contains("val gameStorePersistenceState"))
        assertTrue(modelsSource.contains("isGameStoreResultDurable"))
    }

    @Test
    fun `a new result starts pending`() {
        assertTrue(
            compactViewModel.contains(
                "gameStorePersistenceState = SnakeGameStorePersistenceState.Pending",
            ),
        )
    }

    @Test
    fun `a restored result reconfirms rather than trusting pre-death state`() {
        val restore = compactViewModel
            .substringAfter("private fun restoreResultSnapshot(")

        assertTrue(
            restore.contains(
                "gameStorePersistenceState = SnakeGameStorePersistenceState.Pending",
            ),
        )
    }

    @Test
    fun `the result uses one persistence path`() {
        assertTrue(compactViewModel.contains("beginGameStorePersistence("))
        // The old fire-and-forget write must be gone.
        assertFalse(
            compactViewModel.contains(
                "viewModelScope.launch { recordGameStorePlayOnce(",
            ),
        )
    }

    @Test
    fun `durability is confirmed rather than assumed`() {
        assertTrue(compactViewModel.contains("recordPlayOnce("))
        // A false from recordPlayOnce is ambiguous and must be disambiguated.
        assertTrue(compactViewModel.contains("isPlayRecorded("))
        assertTrue(compactViewModel.contains("confirmGameStorePlayRecorded("))
    }

    @Test
    fun `a failed write can be retried`() {
        assertTrue(compactViewModel.contains("fun retryResultPersistence()"))
        assertTrue(
            compactViewModel.contains("SnakeGameStorePersistenceState.RetryableFailure"),
        )
    }

    @Test
    fun `every result action is guarded by durability`() {
        assertTrue(compactViewModel.contains("private fun resultActionPersistenceReady()"))

        listOf("fun walkAway()", "fun continueWithAnotherGame(", "fun replayWithRemainingBudget(")
            .forEach { signature ->
                val body = compactViewModel.substringAfter(signature).take(200)

                assertTrue(
                    "$signature must check durability first",
                    body.contains("if (!resultActionPersistenceReady()) return"),
                )
            }

        val finish = compactViewModel.substringAfter("fun finishSupportCycleAfterChoice(").take(300)
        assertTrue(finish.contains("!current.isGameStoreResultDurable"))
    }

    @Test
    fun `a store failure never discards a trusted restored result`() {
        val repair = compactViewModel
            .substringAfter("private suspend fun repairRestoredResultPersistence()")
            .substringBefore("private suspend fun confirmGameStorePlayRecorded(")

        // Score/history integrity still fails closed.
        assertTrue(repair.contains("if (!repaired) return false"))
        // But a store failure leaves the result visible for retry.
        assertTrue(repair.contains("SnakeGameStorePersistenceState.RetryableFailure"))
        assertTrue(repair.contains("return true"))
    }

    @Test
    fun `no scope-escaping workaround is used`() {
        listOf("GlobalScope", "NonCancellable", "runBlocking", "Thread(", "Handler(")
            .forEach { forbidden ->
                assertFalse(
                    "$forbidden must not be used to outlive the ViewModel",
                    viewModelSource.contains(forbidden),
                )
            }
    }

    private fun source(relative: String): String = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()
}
