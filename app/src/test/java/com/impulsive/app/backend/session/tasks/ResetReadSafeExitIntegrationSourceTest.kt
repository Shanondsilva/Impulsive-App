package com.impulsive.app.backend.session.tasks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetReadSafeExitIntegrationSourceTest {
    @Test
    fun viewModelKeepsHistoryFailureSeparateFromSafeExitDurability() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/session/tasks/" +
                    "ResetReadViewModel.kt",
            )

        val methodStart =
            source.indexOf(
                "fun requestExplicitWalkAway()",
            )

        val methodEnd =
            source.indexOf(
                "fun rateHelpfulness(",
                methodStart,
            )

        assertTrue(
            methodStart >= 0,
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
            "session,",
            "viewModelScope.launch",
            "val enqueueAccepted",
            ".awaitAccepted()",
            "repository.recordSession(",
            "val immediateResult",
            "safeExitRecorder",
            ".recordExplicitWalkAway(",
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

        val schedulerIndex =
            method.indexOf(
                ".request(",
            )

        val launchIndex =
            method.indexOf(
                "viewModelScope.launch",
            )

        val awaitIndex =
            method.indexOf(
                ".awaitAccepted()",
            )

        val historyIndex =
            method.indexOf(
                "repository.recordSession(",
            )

        val immediateIndex =
            method.indexOf(
                "val immediateResult",
            )

        val recorderIndex =
            method.indexOf(
                "safeExitRecorder",
                immediateIndex,
            )

        assertTrue(
            "WorkManager request must occur before viewModelScope.",
            schedulerIndex >= 0 &&
                launchIndex >
                schedulerIndex,
        )

        assertTrue(
            "The enqueue receipt must be resolved before optional history work.",
            awaitIndex >
                launchIndex &&
                historyIndex >
                awaitIndex,
        )

        assertTrue(
            "Immediate recording must remain after the isolated history write.",
            immediateIndex >
                historyIndex &&
                recorderIndex >
                immediateIndex,
        )

        val historySection =
            method.substring(
                historyIndex,
                immediateIndex,
            )

        assertTrue(
            "History persistence must have its own exception boundary.",
            historySection.contains(
                "catch (",
            ),
        )

        assertTrue(
            "History cancellation must be rethrown.",
            historySection.contains(
                "CancellationException",
            ) &&
                historySection.contains(
                    "throw cancellation",
                ),
        )

        assertFalse(
            "An ordinary history failure must not update final Safe Exit state.",
            historySection.contains(
                "safeExitRequestStatus",
            ),
        )

        assertFalse(
            "An ordinary history failure must not exit before immediate recording.",
            historySection.contains(
                "return@launch",
            ),
        )

        val selectAnswerSection =
            source.substring(
                source.indexOf(
                    "fun selectAnswer(",
                ),
                source.indexOf(
                    "fun recordAbandonedSessionIfNeeded(",
                ),
            )

        assertFalse(
            selectAnswerSection.contains(
                "requestExplicitWalkAway",
            ),
        )
    }
    @Test
    fun resetReadScreenStillTreatsDoneAsNormalExitOnly() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "frontend/screens/tasks/" +
                    "ResetReadScreen.kt",
            )

        assertTrue(
            source.contains(
                "Text(if (taskCompletionResult == null) \"Saving\" else \"Done\")",
            ),
        )

        assertFalse(
            source.contains(
                "requestExplicitWalkAway",
            ),
        )

        assertFalse(
            source.contains(
                "ResetReadSafeExit",
            ),
        )
    }

    @Test
    fun workInputContainsOnlyTheMinimalCanonicalSafeExitRequest() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/session/tasks/" +
                    "ResetReadSafeExitWork.kt",
            )

        listOf(
            "reset_reading_safe_exit_format_version",
            "reset_reading_session_id",
            "reset_reading_completed_at",
            "reset_reading_valid_completion",
            "ResetReadSafeExitWorkDataCodec",
            "data.keyValueMap",
        ).forEach { expected ->
            assertTrue(
                source.contains(
                    expected,
                ),
            )
        }

        listOf(
            "ResetReadRepository",
            "MissingSession",
            "MaximumMissingSessionRetries",
            "articleId",
            "articleTitle",
            "answerText",
            "selectedOptionIndex",
            "completionQuality",
            "failureReason",
            "rewardApplied",
            "waitCutMinutes",
            "helpfulnessRating",
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

        return if (direct.isFile) {
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