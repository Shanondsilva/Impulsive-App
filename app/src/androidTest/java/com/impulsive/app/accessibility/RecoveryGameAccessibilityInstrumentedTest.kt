package com.impulsive.app.accessibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.domain.game.SnakeCell
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGamePhase
import com.impulsive.app.backend.domain.game.SnakeGameState
import com.impulsive.app.backend.domain.game.SnakeGameView
import com.impulsive.app.backend.session.game.BlockCascadeUiState
import com.impulsive.app.backend.session.game.BlockCascadeView
import com.impulsive.app.backend.session.game.SkylineResetUiState
import com.impulsive.app.backend.session.game.SkylineResetView
import com.impulsive.app.frontend.screens.games.BlockCascadePlayingPanel
import com.impulsive.app.frontend.screens.games.RhythmLaneInteractionLayer
import com.impulsive.app.frontend.screens.games.SkyStackGameScene
import com.impulsive.app.frontend.screens.games.SnakeGameBoard
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Compose semantics tests for the four active recovery games.
 *
 * These exercise the same semantics tree that TalkBack, Switch Access and Voice
 * Access consume: each test locates a production control, asserts its role and
 * action, invokes it, and verifies the production callback fired. That is
 * materially stronger than asserting the package exists — but it still does not
 * replace manual assistive-technology verification on a device.
 *
 * Timers, audio, persistence and randomness are deliberately out of scope here;
 * those already have JVM coverage.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecoveryGameAccessibilityInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    // ------------------------------------------------------------------
    // Rhythm Tiles
    // ------------------------------------------------------------------

    @Test
    fun rhythmTiles_exposesFourButtonLanes_andSemanticClickUsesCorrectLane() {
        var activatedLane: Int? = null

        composeRule.setContent {
            ImpulsiveTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 300.dp)) {
                    RhythmLaneInteractionLayer(
                        laneCount = 4,
                        laneWidth = 100.dp,
                        laneDividerColor = Color.Black,
                        enabled = true,
                        onLaneActivated = { activatedLane = it },
                    )
                }
            }
        }

        composeRule
            .onAllNodesWithContentDescription("Rhythm lane", substring = true)
            .assertCountEquals(4)

        composeRule
            .onNodeWithContentDescription("Rhythm lane 3")
            .assert(hasRole(Role.Button))
            .assert(hasClickAction())
            .performClick()

        // Lane 3 is the zero-based index 2 the production callback receives.
        composeRule.runOnIdle {
            assertEquals(2, activatedLane)
        }
    }

    @Test
    fun rhythmTiles_doesNotExposeEnabledLaneActionsWhenNotPlaying() {
        var calls = 0

        composeRule.setContent {
            ImpulsiveTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 300.dp)) {
                    RhythmLaneInteractionLayer(
                        laneCount = 4,
                        laneWidth = 100.dp,
                        laneDividerColor = Color.Black,
                        enabled = false,
                        onLaneActivated = { calls++ },
                    )
                }
            }
        }

        /*
         * Node presence alone would not prove the control is unavailable to
         * assistive tech; the disabled semantic state is the real evidence.
         */
        composeRule
            .onNodeWithContentDescription("Rhythm lane 1")
            .assert(hasRole(Role.Button))
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertEquals(0, calls)
        }
    }

    // ------------------------------------------------------------------
    // Skyline Reset
    // ------------------------------------------------------------------

    @Test
    fun skylineReset_dropSemanticAction_invokesProductionCallback() {
        var drops = 0

        composeRule.setContent {
            ImpulsiveTheme {
                SkyStackGameScene(
                    uiState = SkylineResetUiState(view = SkylineResetView.Playing),
                    modifier = Modifier.size(320.dp),
                    onDrop = { drops++ },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Skyline game board")
            .assert(hasRole(Role.Button))
            .assert(hasClickAction())
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, drops)
        }
    }

    @Test
    fun skylineReset_dropAction_isDisabledOutsidePlayingState() {
        var drops = 0

        composeRule.setContent {
            ImpulsiveTheme {
                SkyStackGameScene(
                    uiState = SkylineResetUiState(view = SkylineResetView.Ready),
                    modifier = Modifier.size(320.dp),
                    onDrop = { drops++ },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Skyline game board")
            .assert(hasRole(Role.Button))
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertEquals(0, drops)
        }
    }

    // ------------------------------------------------------------------
    // Block Cascade
    // ------------------------------------------------------------------

    @Test
    fun blockCascade_exposesFourGameplayActions_andClicksReachCallbacks() {
        var left = 0
        var right = 0
        var rotate = 0
        var drop = 0

        composeRule.setContent {
            ImpulsiveTheme {
                BlockCascadePlayingPanel(
                    uiState = BlockCascadeUiState(
                        view = BlockCascadeView.Playing,
                        gameState = null,
                    ),
                    onMoveLeft = { left++ },
                    onMoveRight = { right++ },
                    onRotate = { rotate++ },
                    onSoftDrop = { drop++ },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Move left")
            .assert(hasRole(Role.Button))
            .assert(hasClickAction())
            .performClick()

        composeRule
            .onNodeWithContentDescription("Move right")
            .assert(hasRole(Role.Button))
            .assert(hasClickAction())
            .performClick()

        composeRule.onNodeWithText("Rotate").performClick()
        composeRule.onNodeWithText("Drop").performClick()

        composeRule.runOnIdle {
            assertEquals(1, left)
            assertEquals(1, right)
            assertEquals(1, rotate)
            assertEquals(1, drop)
        }
    }

    @Test
    fun blockCascade_dpadUp_routesToRotate() {
        var rotate = 0

        composeRule.setContent {
            ImpulsiveTheme {
                BlockCascadePlayingPanel(
                    uiState = BlockCascadeUiState(
                        view = BlockCascadeView.Playing,
                        gameState = null,
                    ),
                    onMoveLeft = {},
                    onMoveRight = {},
                    onRotate = { rotate++ },
                    onSoftDrop = {},
                )
            }
        }

        val focusNode = composeRule.onNodeWithContentDescription("Move left")

        focusNode.performSemanticsAction(SemanticsActions.RequestFocus)

        focusNode.performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }

        composeRule.runOnIdle {
            assertEquals(1, rotate)
        }
    }

    // ------------------------------------------------------------------
    // Snake
    // ------------------------------------------------------------------

    private fun snakeState(): SnakeGameState = SnakeGameState(
        phase = SnakeGamePhase.Ready,
        snake = listOf(
            SnakeCell(9, 12),
            SnakeCell(8, 12),
            SnakeCell(7, 12),
            SnakeCell(6, 12),
        ),
        food = SnakeCell(12, 12),
        direction = null,
        queuedDirections = emptyList(),
        fruitsEaten = 0,
        score = 0,
        tickIntervalMillis = 220L,
        endReason = null,
    )

    @Test
    fun snakeBoard_exposesFourCustomAccessibilityActions_andActionRoutesDirection() {
        var direction: SnakeDirection? = null

        composeRule.setContent {
            ImpulsiveTheme {
                SnakeGameBoard(
                    state = snakeState(),
                    view = SnakeGameView.Ready,
                    modifier = Modifier.size(320.dp),
                    onDirection = { direction = it },
                    onResume = {},
                )
            }
        }

        val semantics = composeRule
            .onNodeWithContentDescription("Snake game board")
            .fetchSemanticsNode()

        val actions = semantics.config[SemanticsActions.CustomActions]

        assertEquals(
            listOf("Move up", "Move down", "Move left", "Move right"),
            actions.map { it.label },
        )

        // Invoke the real production action stored in the semantics node.
        composeRule.runOnIdle {
            val moveLeft = actions.single { it.label == "Move left" }
            assertTrue(moveLeft.action())
        }

        composeRule.runOnIdle {
            assertEquals(SnakeDirection.Left, direction)
        }
    }
}
