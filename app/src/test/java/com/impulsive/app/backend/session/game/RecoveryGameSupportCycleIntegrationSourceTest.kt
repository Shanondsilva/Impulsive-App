package com.impulsive.app.backend.session.game

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGameSupportCycleIntegrationSourceTest {
    @Test
    fun allFourViewModelsUseTheBridgeAndNeverAccessDaos() {
        viewModels.forEach { name ->
            val source = source("backend/session/game/${name}ViewModel.kt")
            assertTrue("$name must own the support-cycle runtime", source.contains("RecoveryGameSupportCycleRuntime"))
            assertTrue("$name must accept a typed launch", source.contains("configureLaunchContext"))
            assertTrue("$name must report early abandonment", source.contains("abandonSupportCycle"))
            assertFalse("$name must not access a DAO", source.contains("Dao("))
            assertFalse("$name must not access a DAO", source.contains(".dao()"))
        }
    }

    @Test
    fun allFourScreensDefaultToStandaloneAndRouteTypedLaunches() {
        viewModels.forEach { name ->
            val source = source("frontend/screens/games/${name.removeSuffix("ViewModel")}Screen.kt")
            assertTrue(source.contains("gameLaunchContext: RecoveryGameLaunchContext"))
            assertTrue(source.contains("RecoveryGameLaunchContext.Standalone"))
            assertTrue(source.contains("viewModel.configureLaunchContext(gameLaunchContext)"))
        }
    }

    @Test
    fun snakeGameRestoresTerminalResultPresentation() {
        val source =
            source(
                "backend/session/game/" +
                    "SnakeGameViewModel.kt",
            )

        assertTrue(source.contains("RecoveryGameResultPayload.Snake"))
        assertTrue(source.contains("SnakeGameView.Result"))
        assertTrue(source.contains("bindWithRecovery"))
        assertTrue(source.contains("saveCurrentResultSnapshot"))
        assertTrue(source.contains("restoreResultSnapshot"))
    }

    /** Legacy upgrade compatibility: a pre-cutover Reflex result must still restore. */
    @Test
    fun reflexGameRestoresTerminalResultPresentation() {
        val source =
            source(
                "backend/session/game/" +
                    "ReflexGameViewModel.kt",
            )

        assertTrue(
            source.contains(
                "SavedStateHandle",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultStateStore",
            ),
        )

        assertTrue(
            source.contains(
                "bindWithRecovery",
            ),
        )

        assertTrue(
            source.contains(
                "saveCurrentResultSnapshot()",
            ),
        )

        assertTrue(
            source.contains(
                "restoreResultSnapshot",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultPayload" +
                    ".Reflex",
            ) ||
                source.contains(
                    "RecoveryGameResultPayload\n" +
                        "                .Reflex",
                ),
        )

        assertTrue(
            source.contains(
                "GameView.Result",
            ),
        )

        assertTrue(
            source.contains(
                "resultStateStore" +
                    ".clear()",
            ) ||
                source.contains(
                    "resultStateStore\n" +
                        "                .clear()",
                ),
        )
    }

    @Test
    fun rhythmTilesRestoresTerminalResultPresentation() {
        val source =
            source(
                "backend/session/game/" +
                    "RhythmTilesViewModel.kt",
            )

        assertTrue(
            source.contains(
                "SavedStateHandle",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultStateStore",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultActionCoordinator",
            ),
        )

        assertTrue(
            source.contains(
                "bindWithRecovery",
            ),
        )

        assertTrue(
            source.contains(
                "saveCurrentResultSnapshot()",
            ),
        )

        assertTrue(
            source.contains(
                "restoreResultSnapshot",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultPayload" +
                    ".RhythmTiles",
            ) ||
                source.contains(
                    "RecoveryGameResultPayload\n" +
                        "                .RhythmTiles",
                ),
        )

        assertTrue(
            source.contains(
                "GameView.Result",
            ),
        )
    }

    @Test
    fun blockCascadeRestoresTerminalResultPresentation() {
        val source =
            source(
                "backend/session/game/" +
                    "BlockCascadeViewModel.kt",
            )

        assertTrue(
            source.contains(
                "SavedStateHandle",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultStateStore",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultActionCoordinator",
            ),
        )

        assertTrue(
            source.contains(
                "bindWithRecovery",
            ),
        )

        assertTrue(
            source.contains(
                "keepsRestoredResultVisible",
            ),
        )

        assertTrue(
            source.contains(
                "saveCurrentResultSnapshot()",
            ),
        )

        assertTrue(
            source.contains(
                "restoreResultSnapshot",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultPayload" +
                    ".BlockCascade",
            ) ||
                source.contains(
                    "RecoveryGameResultPayload\n" +
                        "                        .BlockCascade",
                ),
        )

        assertTrue(
            source.contains(
                "BlockCascadeView" +
                    ".Result",
            ) ||
                source.contains(
                    "BlockCascadeView\n" +
                        "                    .Result",
                ),
        )

        assertFalse(
            source.contains(
                "fun completeWalkAwaySupportCycle",
            ),
        )
    }

    @Test
    fun skylineResetRestoresTerminalResultPresentation() {
        val source =
            source(
                "backend/session/game/" +
                    "SkylineResetViewModel.kt",
            )

        assertTrue(
            source.contains(
                "SavedStateHandle",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultStateStore",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultActionCoordinator",
            ),
        )

        assertTrue(
            source.contains(
                "bindWithRecovery",
            ),
        )

        assertTrue(
            source.contains(
                "keepsRestoredResultVisible",
            ),
        )

        assertTrue(
            source.contains(
                "saveCurrentResultSnapshot()",
            ),
        )

        assertTrue(
            source.contains(
                "restoreResultSnapshot",
            ),
        )

        assertTrue(
            source.contains(
                "RecoveryGameResultPayload" +
                    ".SkylineReset",
            ) ||
                source.contains(
                    "RecoveryGameResultPayload\n" +
                        "                        .SkylineReset",
                ),
        )

        assertTrue(
            source.contains(
                "SkylineResetView" +
                    ".Result",
            ) ||
                source.contains(
                    "SkylineResetView\n" +
                        "                    .Result",
                ),
        )

        assertTrue(
            source.contains(
                "resultRecorded = payload.resultRecorded",
            ) ||
                source.contains(
                    "resultRecorded =\n" +
                        "        payload",
                ),
        )

        assertTrue(
            source.contains(
                "perfectPointsBanked = payload.perfectPointsBanked",
            ) ||
                source.contains(
                    "perfectPointsBanked =\n" +
                        "        payload",
                ),
        )

        assertFalse(
            source.contains(
                "fun completeWalkAwaySupportCycle",
            ),
        )
    }

    @Test
    fun navigationPersistsOnlyOpaqueCycleReferenceFields() {
        val source = source("frontend/navigation/AppNavHost.kt")
        assertTrue(source.contains("AdaptiveSupportCycleIdStateKey"))
        assertTrue(source.contains("AdaptiveSupportCycleMaxDurationStateKey"))
        assertFalse(source.contains("savedStateHandle\n                            ?.set(\"url\""))
        assertFalse(source.contains("savedStateHandle\n                            ?.set(\"domain\""))
    }

    private fun source(relative: String): String = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()

    private companion object {
        // The four active recovery games.
        val viewModels = listOf(
            "SnakeGame",
            "RhythmTiles",
            "BlockCascade",
            "SkylineReset",
        )
    }
}
