package com.impulsive.app.backend.data.repository

import android.app.Activity
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * The single seam UI and session code use to sign in / out.
 *
 * Concrete implementations (see [FirebaseAuthRepository]) own the SDK choreography
 * for each provider. Keeping this an interface means the UI never imports Firebase,
 * Facebook, or Google identity types — important per PROJECT_STRUCTURE.md's
 * strict separation rule.
 *
 * All sign-in methods are suspend because each provider's flow involves
 * launching an Activity (Google bottom sheet, Facebook custom tab) and awaiting
 * the result. They take an [Activity] because those SDK flows need a host
 * activity to attach to.
 */
interface AuthRepository {

    /** Stream of the current user, or null if signed out. Emits on every auth change. */
    val currentUser: Flow<AuthUser?>

    /** Synchronous snapshot of the current user, useful for navigation gating. */
    fun currentUserSnapshot(): AuthUser?

    /** Email address for a signed-in Firebase email user still waiting on verification. */
    fun pendingEmailVerificationAddress(): String?

    suspend fun signInWithGoogle(activity: Activity): AuthResult

    suspend fun linkGoogleAccount(activity: Activity): AuthResult

    suspend fun createAccountWithEmail(email: String, password: String): AuthResult

    suspend fun signInWithEmail(email: String, password: String): AuthResult

    suspend fun refreshEmailVerification(): AuthResult

    suspend fun signInWithFacebook(activity: Activity): AuthResult

    suspend fun linkFacebookAccount(activity: Activity): AuthResult

    /**
     * Mark the user as a guest. No remote call — we just create a stable
     * local UID and remember the choice so the rest of the app can still key
     * data off [AuthUser.uid].
     */
    suspend fun continueAsGuest(): AuthResult

    suspend fun signOut()

    /**
     * Permanently deletes the signed-in account from the auth provider.
     *
     * Firebase requires a recent login to delete, so this may return
     * [AccountDeletionResult.ReauthRequired]. The caller then collects a fresh
     * credential and calls [reauthenticateAndDeleteAccount].
     */
    suspend fun deleteAccount(): AccountDeletionResult

    /**
     * Re-signs the user in with [provider] (for Email, using [password]) to
     * satisfy Firebase's recent-login requirement, then deletes the account.
     */
    suspend fun reauthenticateAndDeleteAccount(
        activity: Activity,
        provider: AuthProvider,
        password: String? = null,
    ): AccountDeletionResult
}

/**
 * Outcome of a sign-in attempt. We distinguish [Cancelled] from [Error] because
 * the UI shouldn't show an error toast when the user just dismisses the sheet.
 */
sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class EmailVerificationPending(val email: String?) : AuthResult
    data object Cancelled : AuthResult
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult
}

/**
 * Outcome of an account-deletion attempt. [ReauthRequired] carries the provider
 * (and email, for the password prompt) the caller needs to reauthenticate with.
 */
sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult
    data class ReauthRequired(val provider: AuthProvider, val email: String?) : AccountDeletionResult
    data object Cancelled : AccountDeletionResult
    data class Error(val message: String, val cause: Throwable? = null) : AccountDeletionResult
}
