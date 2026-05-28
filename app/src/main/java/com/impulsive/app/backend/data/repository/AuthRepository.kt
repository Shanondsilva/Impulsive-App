package com.impulsive.app.backend.data.repository

import android.app.Activity
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
 * launching an Activity (Google bottom sheet, Facebook custom tab, Apple OAuth
 * web flow) and awaiting the result. They take an [Activity] because the
 * Facebook and Firebase OAuthProvider flows need a host activity to attach to.
 */
interface AuthRepository {

    /** Stream of the current user, or null if signed out. Emits on every auth change. */
    val currentUser: Flow<AuthUser?>

    /** Synchronous snapshot of the current user, useful for navigation gating. */
    fun currentUserSnapshot(): AuthUser?

    suspend fun signInWithGoogle(activity: Activity): AuthResult

    suspend fun signInWithApple(activity: Activity): AuthResult

    suspend fun signInWithFacebook(activity: Activity): AuthResult

    /**
     * Mark the user as a guest. No remote call — we just create a stable
     * local UID and remember the choice so the rest of the app can still key
     * data off [AuthUser.uid].
     */
    suspend fun continueAsGuest(): AuthResult

    suspend fun signOut()
}

/**
 * Outcome of a sign-in attempt. We distinguish [Cancelled] from [Error] because
 * the UI shouldn't show an error toast when the user just dismisses the sheet.
 */
sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data object Cancelled : AuthResult
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult
}
