package com.impulsive.app.backend.domain.engine.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveReplayIsolationSourceTest {
    private val engine = source(
        "src/main/java/com/impulsive/app/backend/domain/engine/adaptive/" +
            "AdaptivePolicyReplayEngine.kt",
    )
    private val navigation = source(
        "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    )
    private val livePolicy = source(
        "src/main/java/com/impulsive/app/backend/session/adaptive/" +
            "AdaptiveMomentCoordinator.kt",
    )
    private val debugFixtures = source(
        "src/debug/java/com/impulsive/app/debug/adaptive/" +
            "AdaptiveReplayDebugScenarios.kt",
    )

    @Test
    fun pureEngineHasNoRoomWorkOrAndroidDependencies() {
        listOf(
            "AppDatabase",
            "Room",
            "Dao",
            "WorkManager",
            "android.content.Context",
            "Repository",
            "insert",
            "update",
            "delete",
        ).forEach { forbidden ->
            assertFalse(engine.contains(forbidden))
        }
    }

    @Test
    fun replayCannotCreateFeedbackObservationOrProtectionMutation() {
        listOf(
            "FeedbackCode.",
            "finaliseOnce",
            "markFirstRepeat",
            "schedule(",
            "BlockRequest",
            "Protection",
        ).forEach { forbidden ->
            assertFalse(engine.contains(forbidden))
        }
    }

    @Test
    fun debugSurfaceIsBuildGatedAndOutsideMainSource() {
        assertTrue(debugFixtures.contains("check(BuildConfig.DEBUG)"))
        assertTrue(debugFixtures.contains("AdaptiveReplayDebugScenarios"))
        assertFalse(
            File(
                "src/main/java/com/impulsive/app/debug/adaptive/" +
                    "AdaptiveReplayDebugScenarios.kt",
            ).exists(),
        )
    }

    @Test
    fun releaseNavigationHasNoReplayDestinationOrDeepLink() {
        assertFalse(navigation.contains("AdaptiveReplay"))
        assertFalse(navigation.contains("adaptive_replay"))
        assertFalse(navigation.contains("policy_replay"))
    }

    @Test
    fun candidateReplayNeverEntersLiveRecommendationCoordinator() {
        assertFalse(livePolicy.contains("AdaptivePolicyReplayEngine"))
        assertFalse(livePolicy.contains("AdaptiveReplayPolicy"))
        assertTrue(livePolicy.contains("AdaptiveRecommendationPolicy"))
    }

    @Test
    fun debugCatalogIncludesEveryRequiredSyntheticScenario() {
        listOf(
            "first-attempt",
            "repeated-attempt",
            "cue-matched-plan",
            "recently-rehearsed-plan",
            "intervention-fatigue",
            "wrong-timing",
            "insufficient-evidence",
            "randomised-exploration",
            "disabled-families",
            "no-valid-plans",
        ).forEach { scenario ->
            assertTrue(debugFixtures.contains(scenario))
        }
    }

    private fun source(path: String): String = File(path).readText()
}
