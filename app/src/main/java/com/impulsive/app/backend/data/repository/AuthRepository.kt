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

    suspend fun linkEmailAccount(email: String, password: String): AuthResult =
        AuthResult.Error("Email account linking is unavailable.")

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

    /**
     * Completes the switch offered by [AuthResult.AccountConflict]: signs in
     * to the existing account using the credential captured at collision
     * time. The current guest session is never signed out or deleted before
     * this sign-in succeeds, so a failure leaves the guest fully intact.
     */
    suspend fun switchToExistingAccount(): AuthResult

    /** Drops the credential captured at collision time without switching. */
    fun abandonAccountSwitch()

    /**
     * Server-validates the currently cached Firebase session and reports why it
     * is no longer valid. Callers must only perform destructive local data
     * deletion for [SessionValidationResult.RemotelyDeleted].
     */
    suspend fun validateCurrentSession(): SessionValidationResult

    /**
     * Verifies the persisted session against the backend. Returns false and
     * clears the session when the account behind it no longer exists, so a
     * stale persisted user cannot auto-skip the login screen after account
     * deletion. Network failures return true: an offline user with a locally
     * valid session must never be signed out for lack of connectivity.
     */
    suspend fun hasValidSession(): Boolean

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
 * Result of validating the cached authentication session against Firebase.
 *
 * Only [RemotelyDeleted] means the Firebase Authentication user was
 * definitively deleted and may trigger permanent local-data deletion.
 */
sealed interface SessionValidationResult {

    /** There is no cached authenticated session to validate. */
    data object NoSession : SessionValidationResult

    /** Firebase confirmed that the account still exists. */
    data object Valid : SessionValidationResult

    /**
     * Firebase definitively reported ERROR_USER_NOT_FOUND.
     * This is the only result that may trigger destructive local cleanup.
     */
    data object RemotelyDeleted : SessionValidationResult

    /**
     * The session is invalid for another reason, such as a disabled account
     * or another Firebase invalid-user condition.
     *
     * The app may sign out, but MUST NOT erase local user data.
     */
    data object Invalid : SessionValidationResult

    /**
     * Validation could not be completed because of a network, timeout,
     * Firebase service, or other unknown/transient failure.
     *
     * Keep the current session and local data untouched.
     */
    data object TransientFailure : SessionValidationResult
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

    /**
     * Linking collided with an existing account while the current user is a
     * guest. The credential needed to switch is held inside the repository,
     * not here, so Firebase types never cross into UI code. The UI offers a
     * switch-or-cancel dialog and calls
     * [AuthRepository.switchToExistingAccount] or
     * [AuthRepository.abandonAccountSwitch].
     */
    data class AccountConflict(
        val provider: AuthProvider,
        val providerDisplayName: String,
        val existingAccountEmail: String?,
    ) : AuthResult
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
