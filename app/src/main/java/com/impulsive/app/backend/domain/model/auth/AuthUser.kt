package com.impulsive.app.backend.domain.model.auth

/**
 * Snapshot of the currently signed-in user that downstream code can read
 * without depending on Firebase types directly.
 *
 * [uid] is the stable identifier we'd use for keying remote/local data.
 * For [AuthProvider.Guest], [uid] is a Firebase anonymous-auth uid and [email] is null.
 */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val provider: AuthProvider,
    val linkedProviders: Set<AuthProvider> = emptySet(),
)
