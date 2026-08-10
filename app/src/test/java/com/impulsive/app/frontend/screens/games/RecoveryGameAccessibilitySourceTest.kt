package com.impulsive.app.frontend.screens.games

import com.impulsive.app.backend.domain.game.RecoveryGameCatalog
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-002: every active recovery game must be operable without raw pointer
 * gestures, so TalkBack, Switch Access, Voice Access and non-touch input can
 * reach the same actions.
 */
class RecoveryGameAccessibilitySourceTest {

    private val rhythm = source("RhythmTilesScreen.kt")
    private val skyline = source("SkylineResetScreen.kt")
    private val cascade = source("BlockCascadeScreen.kt")
    private val snakeBoard = source("SnakeGameBoard.kt")

    @Test
    fun `active recovery catalogue is the accessible Snake era four`() {
        val games = RecoveryGameCatalog.games.map { it.id.scoreGameType }.toSet()

        assertEquals(
            setOf(
                ScoreGameType.Snake,
                ScoreGameType.BlockCascade,
                ScoreGameType.SkylineReset,
                ScoreGameType.RhythmTiles,
            ),
            games,
        )
        // Legacy Reflex is out of scope precisely because it is inactive.
        assertFalse(ScoreGameType.ReflexOverride in games)
    }

    @Test
    fun `Rhythm song selection is a semantic single choice group`() {
        assertTrue(rhythm.contains("selectableGroup()"))
        assertTrue(rhythm.contains(".selectable("))
        assertTrue(rhythm.contains("Role.RadioButton"))
        assertTrue(rhythm.contains("minimumInteractiveComponentSize()"))
    }

    @Test
    fun `Rhythm lanes are labelled semantic buttons sharing one action`() {
        assertTrue(rhythm.contains("Role.Button"))
        assertTrue(rhythm.contains("\"Rhythm lane"))
        assertTrue(rhythm.contains("\"Play lane"))
        // Labels are interpolated, so lanes 1-4 come from one contract.
        assertTrue(rhythm.contains("lane + 1"))

        // Hit and miss handling must not diverge between input paths.
        assertTrue(rhythm.contains("val activateLane: (Int) -> Unit"))
        // The lane layer is now extracted, but still driven by the one action.
        assertTrue(rhythm.contains("onLaneActivated = activateLane"))
        assertTrue(rhythm.contains("onClick = { onLaneActivated(lane) }"))
        assertTrue(rhythm.contains("viewModel.tapLane(lane)"))
        assertTrue(rhythm.contains("viewModel.tapEmpty()"))
        assertTrue(rhythm.contains("notePlayer.playNote(semitone)"))
    }

    @Test
    fun `Skyline exposes Drop as a standard semantic action`() {
        assertTrue(skyline.contains(".clickable("))
        assertTrue(skyline.contains("onClickLabel ="))
        assertTrue(skyline.contains("\"Drop block\""))
        assertTrue(skyline.contains("Role.Button"))
        assertTrue(skyline.contains("\"Skyline game board\""))
        // The same callback normal touch already used.
        assertTrue(skyline.contains("onClick = onDrop"))
    }

    @Test
    fun `Block Cascade exposes every gameplay action without coordinates`() {
        assertTrue(cascade.contains("BlockBoardTapRegion("))
        assertTrue(cascade.contains("\"Move left\""))
        assertTrue(cascade.contains("\"Move right\""))
        assertTrue(cascade.contains("onClickLabel ="))
        assertTrue(cascade.contains("Role.Button"))
        assertTrue(cascade.contains("text = \"Rotate\""))
        assertTrue(cascade.contains("text = \"Drop\""))

        // The board halves must not reintroduce coordinate maths.
        assertFalse(cascade.contains("offset.x < size.width"))
    }

    @Test
    fun `Block Cascade supports directional keyboard and D-pad input`() {
        assertTrue(cascade.contains("onPreviewKeyEvent"))
        assertTrue(cascade.contains("KeyEventType.KeyDown"))
        assertTrue(cascade.contains("Key.DirectionLeft"))
        assertTrue(cascade.contains("Key.DirectionRight"))
        assertTrue(cascade.contains("Key.DirectionUp"))
        assertTrue(cascade.contains("Key.DirectionDown"))
    }

    @Test
    fun `the three hardened active games contain no raw tap-only input`() {
        listOf(rhythm, skyline, cascade).forEach { source ->
            assertFalse(source.contains("pointerInput("))
            assertFalse(source.contains("detectTapGestures"))
        }
    }

    @Test
    fun `Snake keeps its existing gesture alternatives`() {
        assertTrue(snakeBoard.contains("CustomAccessibilityAction"))
        assertTrue(snakeBoard.contains("\"Move up\""))
        assertTrue(snakeBoard.contains("\"Move down\""))
        assertTrue(snakeBoard.contains("\"Move left\""))
        assertTrue(snakeBoard.contains("\"Move right\""))
        assertTrue(snakeBoard.contains("onPreviewKeyEvent"))
        assertTrue(snakeBoard.contains("focusable()"))
    }

    @Test
    fun `accessibility hardening keeps the established game renderers`() {
        assertTrue(rhythm.contains("ImpulsiveAmbientBackground"))
        assertTrue(skyline.contains("drawSkyBackground()"))
        assertTrue(cascade.contains("BlockCascadeBoardCanvas("))
    }

    @Test
    fun `no new animation was introduced by the accessibility layer`() {
        listOf(rhythm, skyline, cascade).forEach { source ->
            assertFalse(source.contains("rememberInfiniteTransition"))
        }
    }

    private fun source(fileName: String): String = File(
        "src/main/java/com/impulsive/app/frontend/screens/games/$fileName",
    ).readText()
}
