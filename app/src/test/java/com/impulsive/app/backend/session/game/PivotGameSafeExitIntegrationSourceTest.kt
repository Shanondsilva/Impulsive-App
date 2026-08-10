package com.impulsive.app.backend.session.game

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotGameSafeExitIntegrationSourceTest {
    @Test
    fun reflexAndRhythmUseTheDurableCommitCoordinator() {
        listOf(
            "SnakeGameViewModel.kt",
            // Legacy: historical pending Safe Exit reconciliation.
            "ReflexGameViewModel.kt",
            "RhythmTilesViewModel.kt",
        ).forEach { fileName ->
            val source =
                gameSource(
                    fileName,
                )

            assertTrue(
                source.contains(
                    "PivotGameSessionCommitCoordinator(",
                ),
            )

            assertTrue(
                source.contains(
                    "WorkManagerPivotGameSafeExitReconciliationScheduler(",
                ),
            )

            assertTrue(
                source.contains(
                    ".commit(",
                ),
            )

            assertFalse(
                source.contains(
                    "pivotGameSafeExitRecorder\n                    .recordIfWalkedAway",
                ),
            )
        }
    }

    @Test
    fun commitCoordinatorPersistsThenSchedulesBeforeImmediateRecording() {
        val source =
            gameSource(
                "PivotGameSessionCommitCoordinator.kt",
            )

        assertOrdered(
            source =
                source,
            "scoreSessionWriter.record(",
            "finally",
            "reconciliationScheduler",
            "immediateSafeExitRecorder",
        )
    }

    @Test
    fun applicationStartupRequestsReconciliation() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/ImpulsiveApplication.kt",
            )

        assertTrue(
            source.contains(
                "WorkManagerPivotGameSafeExitReconciliationScheduler(",
            ),
        )

        assertTrue(
            source.contains(
                ".request()",
            ),
        )
    }

    @Test
    fun unsupportedGamesRemainDisconnected() {
        listOf(
            "BlockCascadeViewModel.kt",
            "SkylineResetViewModel.kt",
        ).forEach { fileName ->
            val source =
                gameSource(
                    fileName,
                )

            assertFalse(
                source.contains(
                    "PivotGameSessionCommitCoordinator",
                ),
            )

            assertFalse(
                source.contains(
                    "PivotGameSafeExitReconciliation",
                ),
            )
        }
    }

    @Test
    fun gameScreensRemainFreeOfSafeExitPersistenceLogic() {
        listOf(
            "SnakeGameScreen.kt",
            "ReflexGameScreen.kt",
            "RhythmTilesScreen.kt",
        ).forEach { fileName ->
            val source =
                source(
                    "app/src/main/java/com/impulsive/app/" +
                        "frontend/screens/games/" +
                        fileName,
                )

            assertFalse(
                source.contains(
                    "SafeExitRepository",
                ),
            )

            assertFalse(
                source.contains(
                    "SafeExitRecordingCoordinator",
                ),
            )

            assertFalse(
                source.contains(
                    "PivotGameSessionCommitCoordinator",
                ),
            )
        }
    }

    private fun gameSource(
        fileName:
            String,
    ): String {
        return source(
            "app/src/main/java/com/impulsive/app/" +
                "backend/session/game/" +
                fileName,
        )
    }

    private fun source(
        path:
            String,
    ): String {
        val direct =
            File(
                path,
            )

        return if (
            direct.exists()
        ) {
            direct.readText()
        } else {
            File(
                "..",
                path,
            )
                .readText()
        }
    }

    private fun assertOrdered(
        source:
            String,
        vararg tokens:
            String,
    ) {
        var previous =
            -1

        tokens.forEach { token ->
            val current =
                source.indexOf(
                    token,
                    previous + 1,
                )

            assertTrue(
                "Missing or out-of-order token: $token",
                current >
                    previous,
            )

            previous =
                current
        }
    }
}