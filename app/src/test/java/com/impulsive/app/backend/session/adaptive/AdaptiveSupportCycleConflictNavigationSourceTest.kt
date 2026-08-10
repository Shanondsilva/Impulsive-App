package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleConflictNavigationSourceTest {
    private val navigation = File(
        "src/main/java/com/impulsive/app/" +
            "frontend/navigation/AppNavHost.kt",
    ).readText()

    private val familiarCoordinator = File(
        "src/main/java/com/impulsive/app/" +
            "backend/session/adaptive/" +
            "FamiliarStepCoordinator.kt",
    ).readText()

    private val viewModel = File(
        "src/main/java/com/impulsive/app/" +
            "backend/session/adaptive/" +
            "AdaptiveMomentViewModel.kt",
    ).readText()

    @Test
    fun adaptiveGameConflictUsesAuthoritativeResumePolicy() {
        val destination = navigation
            .substringAfter("route = AppRoutes.AdaptiveGame")
            .substringBefore("route = AppRoutes.AdaptiveReading")

        assertTrue(destination.contains("ActiveDecisionConflict"))

        assertTrue(destination.contains("AdaptiveSupportCycleResumePolicy"))

        assertTrue(destination.contains("routeAdaptiveInternal"))

        assertTrue(destination.contains("replaceAdaptiveGame"))

        assertFalse(
            destination.contains(
                "takeIf { it.outcome == " +
                    "AdaptiveSupportStepOutcome." +
                    "InProgress }",
            ),
        )

        val compactDestination = destination.replace(Regex("\\s+"), " ")

        assertTrue(
            compactDestination.contains("requiresResumeBeforeStartingGame"),
        )

        assertFalse(
            compactDestination.contains("activeState .cycle .currentStep != null"),
        )
    }

    @Test
    fun supportCycleGameRequestRoutesDirectlyToConcreteGame() {
        val router = navigation
            .substringAfter("fun routeAdaptiveInternal")
            .substringBefore("fun routeAdaptive(")

        assertTrue(router.contains("gameLaunchContext as?"))

        assertTrue(router.contains("RecoveryGameLaunchContext"))

        assertTrue(router.contains("recoveryGameRoute("))

        assertTrue(router.contains("AdaptiveSupportCycleIdStateKey"))

        assertTrue(router.contains("AdaptiveSupportCycleMaxDurationStateKey"))
    }

    @Test
    fun familiarStepConflictIsRoutedInsteadOfReportedUnavailable() {
        assertTrue(familiarCoordinator.contains("ResumeExistingCycle"))

        assertTrue(familiarCoordinator.contains("AdaptiveSupportCycleResumePolicy"))

        assertTrue(familiarCoordinator.contains(".toRouteRequest()"))

        assertTrue(
            viewModel.contains(
                "FamiliarStepStartResult" +
                    ".ResumeExistingCycle",
            ) ||
                viewModel.contains(
                    "FamiliarStepStartResult\n" +
                        "    .ResumeExistingCycle",
                ),
        )
    }
}
