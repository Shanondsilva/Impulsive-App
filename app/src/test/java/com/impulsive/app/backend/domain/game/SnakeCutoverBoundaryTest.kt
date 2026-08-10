package com.impulsive.app.backend.domain.game

import com.impulsive.app.backend.domain.model.score.PivotGameSafeExitIdentity
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.store.GameStoreCatalog
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.isGameTask
import com.impulsive.app.backend.domain.usecase.GameSelectionEngine
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snake is the active recovery game, task and store slot from SNAKE-04.
 *
 * Reflex Override remains decodable and routable purely for upgrade
 * compatibility: historical records, a restored back stack, or a support step
 * that was already running before the cutover. It is never offered anew.
 */
class SnakeCutoverBoundaryTest {

    @Test
    fun `Snake has a stable score identity`() {
        assertEquals("SNAKE", ScoreGameType.Snake.id)
        assertEquals(ScoreGameType.Snake, ScoreGameType.fromId("SNAKE"))
    }

    @Test
    fun `historical Reflex data still resolves as Reflex`() {
        assertEquals(
            ScoreGameType.ReflexOverride,
            ScoreGameType.fromId("REFLEX_OVERRIDE"),
        )
        assertEquals("Reflex Override", ScoreGameType.ReflexOverride.displayName)
    }

    @Test
    fun `every score game identity remains unique`() {
        val ids = ScoreGameType.entries.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `Snake is the active selectable recovery game`() {
        assertTrue(ScoreGameType.Snake in GameSelectionEngine.candidates)
        assertFalse(ScoreGameType.ReflexOverride in GameSelectionEngine.candidates)
    }

    @Test
    fun `Snake replaced Reflex in the recovery game catalogue`() {
        assertTrue(
            RecoveryGameCatalog.games.any { it.id.scoreGameType == ScoreGameType.Snake },
        )
        assertFalse(
            RecoveryGameCatalog.games.any {
                it.id.scoreGameType == ScoreGameType.ReflexOverride
            },
        )
    }

    @Test
    fun `Snake holds the default progress slot`() {
        val order = File(
            "src/main/java/com/impulsive/app/backend/domain/model/score/ScoreModels.kt",
        ).readText()
            .substringAfter("private val DefaultScoreGameOrder")
            .substringBefore(")")

        assertTrue(order.contains("Snake"))
        assertFalse(order.contains("ReflexOverride"))
    }

    @Test
    fun `Snake Safe Exit identity produces the expected stable keys`() {
        val record = walkedAwayRecord(ScoreGameType.Snake, id = 4242L)

        assertTrue(PivotGameSafeExitIdentity.isSupported(ScoreGameType.Snake))
        assertEquals("SNAKE:4242", PivotGameSafeExitIdentity.sourceId(record))
        assertEquals(
            "${SafeExitSource.PivotGame.storageValue}:SNAKE:4242",
            PivotGameSafeExitIdentity.sourceKey(record),
        )
    }

    @Test
    fun `Reflex and Rhythm Safe Exit support is unchanged`() {
        assertTrue(PivotGameSafeExitIdentity.isSupported(ScoreGameType.ReflexOverride))
        assertTrue(PivotGameSafeExitIdentity.isSupported(ScoreGameType.RhythmTiles))
        assertNotNull(
            PivotGameSafeExitIdentity.sourceId(
                walkedAwayRecord(ScoreGameType.ReflexOverride, id = 7L),
            ),
        )
    }

    @Test
    fun `the Snake screen is wired to active routes`() {
        val navHost = navHostSource()

        assertTrue(navHost.contains("const val SnakeGame = \"snake_game\""))
        assertTrue(navHost.contains("const val SnakeGameTask = \"snake_game_task\""))
        assertTrue(navHost.contains("composable(AppRoutes.SnakeGame)"))
        assertTrue(navHost.contains("composable(AppRoutes.SnakeGameTask)"))
        assertTrue(navHost.contains("SnakeGameScreen("))
    }

    @Test
    fun `legacy Reflex routes keep their original strings`() {
        val navHost = navHostSource()

        // A restored pre-cutover back stack must still resolve.
        assertTrue(navHost.contains("const val LegacyReflexGame = \"reflex_game\""))
        assertTrue(navHost.contains("const val LegacyReflexGameTask = \"reflex_game_task\""))
        assertTrue(navHost.contains("composable(AppRoutes.LegacyReflexGame)"))
        assertTrue(navHost.contains("ReflexGameScreen("))
    }

    @Test
    fun `active navigation callbacks target Snake`() {
        val navHost = navHostSource()

        assertTrue(navHost.contains("navController.navigate(AppRoutes.SnakeGameTask)"))
        assertTrue(navHost.contains("navController.navigate(AppRoutes.SnakeGame)"))
        assertFalse(navHost.contains("navigate(AppRoutes.LegacyReflexGame"))
    }

    @Test
    fun `recovery game routing is explicit and cannot fall through to Reflex`() {
        val routing = navHostSource()
            .substringAfter("private fun recoveryGameRoute(")
            .substringBefore("private suspend fun selectAndRecordGuidedGame")

        assertTrue(routing.contains("ScoreGameType.Snake ->"))
        assertTrue(routing.contains("ScoreGameType.ReflexOverride ->"))
        assertTrue(routing.contains("error(\"Unsupported recovery game route"))
        // A generic fallback could disguise an unsupported game as Reflex.
        val collapsedRouting = routing.replace(Regex("\\s+"), " ")
        assertFalse(collapsedRouting.contains("else -> if (asTask)"))
    }

    @Test
    fun `the Game Store active slot is Snake`() {
        assertEquals("Snake", GameStoreCatalog.byId("SNAKE")?.displayName)
        assertTrue(GameStoreCatalog.byId("SNAKE")?.defaultOwned == true)
        assertNull(GameStoreCatalog.byId("REFLEX_OVERRIDE"))
    }

    @Test
    fun `Snake is a distinct task identity that inherits nothing from Reflex`() {
        assertEquals("snake", PsychologyTaskType.Snake.id)
        assertEquals("Snake", PsychologyTaskType.Snake.taskTitle)
        assertNotEquals(PsychologyTaskType.Snake.id, PsychologyTaskType.ReflexOverride.id)
        assertTrue(PsychologyTaskType.Snake.isGameTask())

        val tasks = File(
            "src/main/java/com/impulsive/app/backend/domain/model/tasks/TaskRewardModels.kt",
        ).readText()

        // No migration may copy Reflex completion state onto Snake.
        assertFalse(tasks.contains("reflex_override_completed_ever"))
    }

    @Test
    fun `visible task list offers Snake and not Reflex`() {
        val tasksScreen = File(
            "src/main/java/com/impulsive/app/frontend/screens/tasks/TaskToCompleteScreen.kt",
        ).readText()
        val visible = tasksScreen
            .substringAfter("private val VisiblePsychologyTasks")
            .substringBefore("@Composable")

        assertTrue(visible.contains("PsychologyTaskType.Snake"))
        assertFalse(visible.contains("PsychologyTaskType.ReflexOverride"))
    }

    @Test
    fun `Snake history joins the same backup boundary as Reflex history`() {
        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(backup.contains("snake_game_history"))
        assertTrue(extraction.contains("snake_game_history"))
        // Historical Reflex history must remain recoverable.
        assertTrue(backup.contains("reflex_game_history"))
        assertTrue(extraction.contains("reflex_game_history"))
    }

    @Test
    fun `the active Home pivot card uses Snake`() {
        val dashboardCards = File(
            "src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt",
        ).readText()
            .substringAfter("private fun DashboardCards(")
            .substringBefore("private fun PsychologyTaskType.homePreview()")

        assertTrue(dashboardCards.contains("Snake"))
        // The legacy homePreview branch is excluded above and still retained.
        assertFalse(dashboardCards.contains("Reflex Override"))
    }

    private fun navHostSource(): String = File(
        "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    ).readText()

    // ------------------------------------------------------------------
    // SNAKE-02H wiring guards
    // ------------------------------------------------------------------

    @Test
    fun `the ViewModel wires the process-death hardening collaborators`() {
        val source = viewModelSource()

        assertTrue(source.contains("SnakeActiveSupportCheckpointStateStore("))
        assertTrue(source.contains("SnakeSupportElapsedPolicy."))
        assertTrue(source.contains("SnakeRestoredResultPersistenceRepair("))
    }

    @Test
    fun `the active support checkpoint interval is one second`() {
        assertTrue(
            viewModelSource().contains("ActiveSupportCheckpointIntervalMillis = 1_000L"),
        )
    }

    @Test
    fun `no gameplay board state is ever written to saved state`() {
        val checkpointSource = File(
            "src/main/java/com/impulsive/app/backend/session/game/" +
                "SnakeActiveSupportCheckpointStateStore.kt",
        ).readText()
        // Strip comments: the file documents what it forbids.
        val code = checkpointSource
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")

        listOf("SnakeGameState", "SnakeCell", "queuedDirections", "food").forEach { forbidden ->
            assertFalse(
                "checkpoint must not store $forbidden",
                code.contains(forbidden),
            )
        }
    }

    @Test
    fun `the terminal result snapshot is saved before the checkpoint is released`() {
        val source = viewModelSource().replace(Regex("\\s+"), " ")

        val saveIndex = source.indexOf(
            "saveCurrentResultSnapshot() activeSupportCheckpointStore.clear()",
        )

        assertTrue(
            "the stable Result must exist before the checkpoint is cleared",
            saveIndex >= 0,
        )
    }

    @Test
    fun `Walk Away does not resurrect the cleared result snapshot`() {
        val source = viewModelSource().replace(Regex("\\s+"), " ")
        val walkAway = source.substringAfter("fun walkAway()").substringBefore("fun abandon")

        assertTrue(walkAway.contains("refreshResultSnapshot = false"))
        assertTrue(viewModelSource().contains("refreshResultSnapshot: Boolean = true"))
    }

    @Test
    fun `a restored result repairs interrupted persistence`() {
        val source = viewModelSource()

        assertTrue(source.contains("restoredResultPersistenceRepair.repair("))
        assertTrue(source.contains("repairRestoredResultPersistence()"))
    }

    private fun viewModelSource(): String = File(
        "src/main/java/com/impulsive/app/backend/session/game/SnakeGameViewModel.kt",
    ).readText()

    private fun walkedAwayRecord(
        gameType: ScoreGameType,
        id: Long,
    ): ScoreSessionRecord = ScoreSessionRecord(
        id = id,
        gameType = gameType,
        score = 120,
        startedAt = LocalDateTime.of(2026, 1, 1, 10, 0),
        completedAt = LocalDateTime.of(2026, 1, 1, 10, 1),
        durationSec = 60,
        urgeBefore = null,
        urgeAfter = null,
        outcome = ScoreSessionOutcome.WalkedAway,
        validCompletion = true,
    )
}
