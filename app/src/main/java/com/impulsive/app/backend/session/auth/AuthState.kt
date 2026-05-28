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
) {
    val isLoading: Boolean get() = inFlightProvider != null
}
