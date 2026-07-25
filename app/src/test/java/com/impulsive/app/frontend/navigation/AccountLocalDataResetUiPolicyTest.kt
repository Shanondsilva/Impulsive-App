package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLocalDataResetUiPolicyTest {
    @Test
    fun everyUnusableLocalDataDialogOffersEraseAndAnotherAccount() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
            ).readText()

        assertTrue(
            source.countOccurrences(
                "Text(\"Erase saved data\")",
            ) >= 4,
        )

        assertTrue(
            source.countOccurrences(
                "Text(\"Use another account\")",
            ) >= 4,
        )
    }

    @Test
    fun eraseRequiresExplicitSecondConfirmation() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
            ).readText()

        assertTrue(
            source.contains(
                "Erase saved data from this device?",
            ),
        )

        assertTrue(
            source.contains(
                "Erase and continue",
            ),
        )

        assertTrue(
            source.contains(
                "It will not transfer that data to the account currently signed in.",
            ),
        )

        assertTrue(
            source.contains(
                "This cannot be undone.",
            ),
        )
    }

    @Test
    fun deletingDialogCannotBeDismissed() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
            ).readText()

        val deletingBranchStart =
            source.indexOf(
                "is AccountLocalDataResetState.Deleting",
            )

        assertTrue(deletingBranchStart >= 0)

        val failedBranchStart =
            source.indexOf(
                "is AccountLocalDataResetState.Failed",
                deletingBranchStart,
            )

        assertTrue(failedBranchStart > deletingBranchStart)

        val deletingBranch =
            source.substring(
                deletingBranchStart,
                failedBranchStart,
            )

        assertTrue(
            deletingBranch.contains(
                "onDismissRequest = { }",
            ),
        )

        assertTrue(
            deletingBranch.contains(
                "CircularProgressIndicator()",
            ),
        )
    }

    @Test
    fun mismatchResetPreservesCurrentAuthentication() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/session/onboarding/OnboardingViewModel.kt",
            ).readText()

        val start =
            source.indexOf(
                "fun requestEraseUnusableLocalData",
            )

        val end =
            source.indexOf(
                "fun backfillAuthenticatedCompletionIfNeeded",
                start,
            )

        assertTrue(start >= 0)
        assertTrue(end > start)

        val resetBlock =
            source.substring(start, end)

        assertFalse(resetBlock.contains("signOut("))
        assertFalse(resetBlock.contains("deleteAccount("))
        assertFalse(
            resetBlock.contains(
                "setCompletedForAccount(",
            ),
        )
        assertFalse(resetBlock.contains("clearAnswers("))
    }

    @Test
    fun changedSessionCannotReusePreviousDestructiveConfirmation() {
        val stateSource =
            File(
                "src/main/java/com/impulsive/app/backend/session/onboarding/OnboardingState.kt",
            ).readText()

        assertTrue(
            stateSource.contains(
                "data object SessionChanged",
            ),
        )

        assertTrue(
            stateSource.contains(
                "val expectedAccountUid: String,",
            ),
        )

        assertFalse(
            stateSource.contains(
                "val expectedAccountUid: String?,",
            ),
        )

        val viewModelSource =
            File(
                "src/main/java/com/impulsive/app/backend/session/onboarding/OnboardingViewModel.kt",
            ).readText()

        val resetStart =
            viewModelSource.indexOf(
                "fun requestEraseUnusableLocalData",
            )

        val resetEnd =
            viewModelSource.indexOf(
                "fun backfillAuthenticatedCompletionIfNeeded",
                resetStart,
            )

        assertTrue(resetStart >= 0)
        assertTrue(resetEnd > resetStart)

        val resetBlock =
            viewModelSource.substring(
                resetStart,
                resetEnd,
            )

        assertTrue(
            resetBlock.contains(
                "AccountLocalDataResetState.SessionChanged",
            ),
        )

        assertFalse(
            Regex(
                """expectedAccountUid\s*\?:\s*accountLocalDataResetCoordinator""",
            ).containsMatchIn(resetBlock),
        )

        val uiSource =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
            ).readText()

        val sessionChangedStart =
            uiSource.indexOf(
                "AccountLocalDataResetState.SessionChanged ->",
            )

        assertTrue(sessionChangedStart >= 0)

        val failedStart =
            uiSource.indexOf(
                "is AccountLocalDataResetState.Failed",
                sessionChangedStart,
            )

        assertTrue(failedStart > sessionChangedStart)

        val sessionChangedBranch =
            uiSource.substring(
                sessionChangedStart,
                failedStart,
            )

        assertTrue(
            sessionChangedBranch.contains(
                "No saved data was erased.",
            ),
        )

        assertTrue(
            sessionChangedBranch.contains(
                "onClick = onCancel",
            ),
        )

        assertFalse(
            sessionChangedBranch.contains(
                "onRetry",
            ),
        )

        assertFalse(
            sessionChangedBranch.contains(
                "onConfirm",
            ),
        )

        assertFalse(
            sessionChangedBranch.contains(
                "Erase and continue",
            ),
        )

        assertFalse(
            sessionChangedBranch.contains(
                "Try again",
            ),
        )
    }
    private fun String.countOccurrences(
        value: String,
    ): Int {
        if (value.isEmpty()) {
            return 0
        }

        var count = 0
        var index = 0

        while (true) {
            index = indexOf(
                string = value,
                startIndex = index,
            )

            if (index < 0) {
                return count
            }

            count += 1
            index += value.length
        }
    }
}
