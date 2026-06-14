package com.impulsive.app.backend.session.auth

import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthUser

/**
 * UI-facing snapshot of the auth flow.
 *
 * [inFlightProvider] is null while no sign-in is happening; while a provider's
 * sheet/Custom Tab is open it holds that provider so the UI can disable the
 * relevant button and show a spinner on it specifically (not the entire screen).
 *
 * [errorMessage] is set after a sign-in failure (not on user cancel) and gets
 * cleared by [com.impulsive.app.backend.session.auth.AuthViewModel.consumeError].
 */
data class AuthState(
    val user: AuthUser? = null,
    val inFlightProvider: AuthProvider? = null,
    val errorMessage: String? = null,
    val pendingEmailVerificationAddress: String? = null,
) {
    val isLoading: Boolean get() = inFlightProvider != null
    val isWaitingForEmailVerification: Boolean get() = pendingEmailVerificationAddress != null
}

/**
 * UI-facing state for the delete-account flow surfaced in Settings.
 *
 * [NeedsPassword] is emitted for Email accounts that need the user to confirm
 * their password before deletion. [Deleted] tells the UI to wipe local data and
 * restart. [Failed] carries a message to show the user.
 */
sealed interface AccountDeletionUiState {
    data object Idle : AccountDeletionUiState
    data object InProgress : AccountDeletionUiState
    data class NeedsPassword(val email: String?) : AccountDeletionUiState
    data object Deleted : AccountDeletionUiState
    data class Failed(val message: String) : AccountDeletionUiState
}
