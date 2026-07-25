package com.impulsive.app.backend.session.onboarding

import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers

data class OnboardingState(
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val isCompleted: Boolean = false,
    val completedAccountUid: String? = null,
    val isLoading: Boolean = true,
)

sealed interface OnboardingAccountResolutionState {
    data object Idle : OnboardingAccountResolutionState
    data object Loading : OnboardingAccountResolutionState

    data class RetryableFailure(
        val message: String = "Impulsive couldn't check your account setup right now. Check your connection and try again.",
    ) : OnboardingAccountResolutionState
}
sealed interface AccountRestoreState {
    data object Idle : AccountRestoreState
    data object Restoring : AccountRestoreState

    data class RetryableFailure(
        val message: String = "Impulsive couldn't restore your saved data right now. Check your connection and try again.",
    ) : AccountRestoreState

    data object AccountMismatch : AccountRestoreState
    data object LocalBackupUnavailable : AccountRestoreState
}

sealed interface AccountLocalDataResetState {
    data object Idle : AccountLocalDataResetState

    data class Confirming(
        val expectedAccountUid: String,
    ) : AccountLocalDataResetState

    data class Deleting(
        val expectedAccountUid: String,
    ) : AccountLocalDataResetState

    /*
     * The authenticated Firebase session changed before destructive work
     * began. This state deliberately has no UID and no destructive retry.
     * The user must return to Idle and begin a fresh confirmation.
     */
    data object SessionChanged : AccountLocalDataResetState

    /*
     * Failed is reserved for operations that were already confirmed for this
     * exact authenticated Firebase UID. Its UID is intentionally non-null.
     */
    data class Failed(
        val expectedAccountUid: String,
        val message: String,
    ) : AccountLocalDataResetState
}

sealed interface OnboardingCompletionState {
    data object Idle : OnboardingCompletionState
    data object Saving : OnboardingCompletionState

    data class RetryableFailure(
        val message: String = "Impulsive couldn't finish saving your account setup right now. Check your connection and try again.",
    ) : OnboardingCompletionState
}
