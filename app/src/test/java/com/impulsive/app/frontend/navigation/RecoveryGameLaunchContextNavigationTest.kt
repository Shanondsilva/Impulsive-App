package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecoveryGameLaunchContextNavigationTest {
    @Test
    fun actualNavigationHelperReconstructsTransferredSupportCycleContext() {
        val launch = supportCycleGameLaunchContext(
            cycleId = "cycle",
            decisionId = "decision",
            maxDurationMillis = 50_000L,
            gameType = ScoreGameType.RhythmTiles,
        ) as RecoveryGameLaunchContext.SupportCycle

        assertEquals("cycle", launch.cycleId)
        assertEquals("decision", launch.decisionId)
        assertEquals(50_000L, launch.maxDurationMillis)
        assertEquals(ScoreGameType.RhythmTiles, launch.gameType)
    }

    @Test
    fun actualNavigationHelperPreservesTrueStandaloneLaunches() {
        assertSame(
            RecoveryGameLaunchContext.Standalone,
            supportCycleGameLaunchContext(null, null, null, ScoreGameType.ReflexOverride),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun actualNavigationHelperRejectsPartialSupportCycleContext() {
        supportCycleGameLaunchContext(
            cycleId = "cycle",
            decisionId = null,
            maxDurationMillis = 50_000L,
            gameType = ScoreGameType.ReflexOverride,
        )
    }
}
