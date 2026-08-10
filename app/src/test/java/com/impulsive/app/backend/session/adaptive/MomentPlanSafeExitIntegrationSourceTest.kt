package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanSafeExitIntegrationSourceTest {
    @Test
    fun viewModelExposesExplicitBackendEntryWithoutAutomaticInvocation() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/session/adaptive/" +
                    "AdaptiveMomentViewModel.kt",
            )

        assertTrue(
            source.contains(
                "fun requestCompletedMomentPlanWalkAway()",
            ),
        )

        val methodStart =
            source.indexOf(
                "fun requestCompletedMomentPlanWalkAway()",
            )

        val schedulerIndex =
            source.indexOf(
                "momentPlanSafeExitReconciliationScheduler" +
                    System.lineSeparator() +
                    "                .request(",
                methodStart,
            )
                .takeIf { it >= 0 }
                ?: source.indexOf(
                    ".request(",
                    methodStart,
                )

        val launchIndex =
            source.indexOf(
                "viewModelScope.launch",
                methodStart,
            )

        assertTrue(
            "Durable work must be requested before cancellable ViewModel work.",
            schedulerIndex >= 0 &&
                launchIndex >
                schedulerIndex,
        )

        val methodEnd =
            source.indexOf(
                "fun dismissCurrentIntervention()",
                methodStart,
            )

        assertTrue(
            methodEnd >
                methodStart,
        )

        val method =
            source.substring(
                methodStart,
                methodEnd,
            )

        listOf(
            "val enqueueReceipt",
            ".request(",
            "viewModelScope.launch",
            "decisions.getById(",
            ".awaitAccepted()",
            "is SafeExitRecordingResult.Recorded",
            "is SafeExitRecordingResult.Duplicate",
            "is SafeExitRecordingResult.Rejected",
            "SafeExitRecordingResult.RetryableFailure",
        ).forEach { expected ->
            assertTrue(
                "Missing expected token: $expected",
                method.contains(
                    expected,
                ),
            )
        }

        val requestIndex =
            method.indexOf(
                ".request(",
            )

        val launchIndexInMethod =
            method.indexOf(
                "viewModelScope.launch",
            )

        val decisionReadIndex =
            method.indexOf(
                "decisions.getById(",
            )

        val awaitIndex =
            method.indexOf(
                ".awaitAccepted()",
            )

        assertTrue(
            "Moment Plan work must be requested before cancellable work.",
            requestIndex >= 0 &&
                launchIndexInMethod >
                requestIndex,
        )

        assertTrue(
            "The completed decision must be read from Room before status resolution.",
            decisionReadIndex >
                launchIndexInMethod,
        )

        assertTrue(
            "The enqueue Operation must be awaited before final status.",
            awaitIndex >
                decisionReadIndex,
        )
        val completeSection =
            source.substring(
                source.indexOf(
                    "fun completeCurrentIntervention()",
                ),
                methodStart,
            )

        assertFalse(
            completeSection.contains(
                "requestCompletedMomentPlanWalkAway",
            ),
        )

        val dismissSection =
            source.substring(
                source.indexOf(
                    "fun dismissCurrentIntervention()",
                ),
                source.indexOf(
                    "private fun finishCurrentIntervention(",
                ),
            )

        assertFalse(
            dismissSection.contains(
                "requestCompletedMomentPlanWalkAway",
            ),
        )
    }

    @Test
    fun adaptiveMomentScreensDoNotReferenceMomentPlanSafeExitBackend() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "frontend/screens/adaptive/" +
                    "AdaptiveMomentScreens.kt",
            )

        listOf(
            "requestCompletedMomentPlanWalkAway",
            "MomentPlanSafeExitRecorder",
            "MomentPlanSafeExitReconciliationScheduler",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(
                    forbidden,
                ),
            )
        }
    }

    @Test
    fun appNavHostDoesNotReferenceMomentPlanSafeExitBackend() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "frontend/navigation/" +
                    "AppNavHost.kt",
            )

        listOf(
            "requestCompletedMomentPlanWalkAway",
            "MomentPlanSafeExitRecorder",
            "MomentPlanSafeExitReconciliationScheduler",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(
                    forbidden,
                ),
            )
        }
    }

    @Test
    fun workInputContainsOnlyTheTechnicalDecisionId() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/session/adaptive/" +
                    "MomentPlanSafeExitWork.kt",
            )

        assertTrue(
            source.contains(
                "moment_plan_decision_id",
            ),
        )

        listOf(
            "actionText",
            "futureCueText",
            "protectionIncidentToken",
            "packageName",
            "url",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(
                    forbidden,
                ),
            )
        }
    }

    private fun source(
        path: String,
    ): String {
        val direct =
            File(
                path,
            )

        return if (
            direct.isFile
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
}