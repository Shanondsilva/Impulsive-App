package com.impulsive.app.backend.session.game

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGameTaskRewardIdempotencySourceTest {
    private fun source(relative: String): String = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()

    @Test
    fun everyRestorableGameExposesItsStableSessionToken() {
        listOf(
            "backend/session/game/SnakeGameViewModel.kt",
            // Legacy: a restored pre-cutover Reflex task must still finish.
            "backend/session/game/ReflexGameViewModel.kt",
            "backend/session/game/RhythmTilesViewModel.kt",
            "backend/session/game/BlockCascadeViewModel.kt",
            "backend/session/game/SkylineResetViewModel.kt",
        ).forEach { path ->
            val compact = source(path).replace(Regex("\\s+"), " ")

            assertTrue(
                "$path must expose a reward completion token",
                compact.contains("taskRewardCompletionToken"),
            )

            assertTrue(
                "$path token must use the restored score-session identity",
                compact.contains("activeSessionId"),
            )
        }
    }

    @Test
    fun everyGamePassesTheStableTokenToTaskRewardPersistence() {
        listOf(
            "frontend/screens/games/SnakeGameScreen.kt",
            // Legacy: retains its stable token for restored tasks.
            "frontend/screens/games/ReflexGameScreen.kt",
            "frontend/screens/games/RhythmTilesScreen.kt",
            "frontend/screens/games/BlockCascadeScreen.kt",
            "frontend/screens/games/SkylineResetScreen.kt",
        ).forEach { path ->
            val compact = source(path).replace(Regex("\\s+"), " ")

            assertTrue(
                "$path must pass the stable completion token",
                compact.contains("completionToken =") &&
                    compact.contains("taskRewardCompletionToken()"),
            )
        }
    }

    @Test
    fun snakeNoLongerUsesScoreAsRewardIdentity() {
        val snake = source("frontend/screens/games/SnakeGameScreen.kt")

        assertFalse(snake.contains("rewardedResultScore"))
        /*
         * Named for its stricter meaning: the token is recorded only after the
         * reward write returns, never when the attempt starts.
         */
        assertTrue(snake.contains("persistedTaskCompletionToken"))
        assertTrue(snake.contains("taskRewardCompletionToken()"))
    }

    @Test
    fun legacyReflexKeepsItsStableTokenForRestoredTasks() {
        val reflex = source("frontend/screens/games/ReflexGameScreen.kt")

        assertFalse(reflex.contains("rewardedResultScore"))
        assertTrue(reflex.contains("rewardedCompletionToken"))
    }

    @Test
    fun rhythmReplayUsesSessionTokenInsteadOfScreenLifetimeBoolean() {
        val rhythm =
            source("frontend/screens/games/RhythmTilesScreen.kt")
                .replace(Regex("\\s+"), " ")

        assertFalse(rhythm.contains("var rewardLogged"))

        assertTrue(rhythm.contains("rewardedCompletionToken"))

        assertTrue(rhythm.contains("rewardedCompletionToken != completionToken"))

        assertTrue(rhythm.contains("rewardedCompletionToken = completionToken"))

        assertTrue(rhythm.contains("completionToken = checkNotNull"))
    }

    @Test
    fun taskRewardPersistenceStoresAnAtomicBoundedReceipt() {
        val dataSource = source("backend/data/local/preferences/TaskRewardDataSource.kt")

        val ledger = source("backend/data/local/preferences/TaskCompletionReceiptLedger.kt")

        assertTrue(dataSource.contains("TaskCompletionReceiptsKey"))

        assertTrue(dataSource.contains("existingReceipt"))

        assertTrue(dataSource.contains("storeCompletionReceipt"))

        assertTrue(ledger.contains("MaximumReceiptCount"))

        assertTrue(ledger.contains("takeLast"))
    }
}
