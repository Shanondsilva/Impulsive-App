package com.impulsive.app.backend.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * Firebase Authentication implementation of [AuthRepository].
 *
 * Google uses Android Credential Manager to retrieve a Google ID token, then
 * exchanges that token for a Firebase credential. Facebook uses the Facebook
 * Login SDK, then exchanges the AccessToken for a Firebase credential.
 */
class FirebaseAuthRepository(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    private val credentialManager: CredentialManager = CredentialManager.create(appContext)
    private val facebookCallbackManager: CallbackManager = CallbackManager.Factory.create()

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun currentUserSnapshot(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        val serverClientId = appContext.getString(R.string.default_web_client_id)
        if (!serverClientId.isConfiguredValue()) {
            return AuthResult.Error(AuthNotConfiguredMessage)
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = authResult.user
                    ?: return AuthResult.Error("Google sign-in returned no user.")
                AuthResult.Success(firebaseUser.toAuthUser(forced = AuthProvider.Google))
            } else {
                AuthResult.Error("Unexpected credential type returned for Google sign-in.")
            }
        } catch (cancel: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: GetCredentialException) {
            e.toAuthError(providerName = "Google")
        } catch (e: Exception) {
            e.toAuthError(providerName = "Google")
        }
    }

    override suspend fun createAccountWithEmail(email: String, password: String): AuthResult = try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: firebaseAuth.currentUser
            ?: return AuthResult.Error("Email account creation returned no user.")
        user.sendEmailVerification().await()
        firebaseAuth.signOut()
        AuthResult.Error(EmailVerificationSentMessage)
    } catch (e: Exception) {
        e.toAuthError(providerName = "Email")
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: firebaseAuth.currentUser
                ?: return AuthResult.Error("Email login returned no user.")
            if (!user.isEmailVerified) {
                user.sendEmailVerification().await()
                firebaseAuth.signOut()
                return AuthResult.Error(EmailVerificationRequiredMessage)
            }
            AuthResult.Success(user.toAuthUser(forced = AuthProvider.Email))
        } catch (e: Exception) {
            e.toAuthError(providerName = "Email")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override suspend fun signInWithFacebook(activity: Activity): AuthResult {
        if (!isFacebookConfigured()) {
            return AuthResult.Error(AuthNotConfiguredMessage)
        }

        val loginOutcome = suspendCancellableCoroutine<FacebookLoginOutcome> { cont ->
            val loginManager = LoginManager.getInstance()

            loginManager.registerCallback(
                facebookCallbackManager,
                object : FacebookCallback<LoginResult> {
                    override fun onSuccess(result: LoginResult) {
                        loginManager.unregisterCallback(facebookCallbackManager)
                        if (cont.isActive) {
                            cont.resume(FacebookLoginOutcome.Success(result.accessToken))
                        }
                    }

                    override fun onCancel() {
                        loginManager.unregisterCallback(facebookCallbackManager)
                        if (cont.isActive) {
                            cont.resume(FacebookLoginOutcome.Cancelled)
                        }
                    }

                    override fun onError(error: FacebookException) {
                        loginManager.unregisterCallback(facebookCallbackManager)
                        if (cont.isActive) {
                            cont.resume(
                                FacebookLoginOutcome.Error(
                                    message = error.localizedMessage ?: "Facebook sign-in failed.",
                                    cause = error,
                                ),
                            )
                        }
                    }
                },
            )

            try {
                loginManager.logInWithReadPermissions(activity, listOf("email", "public_profile"))
            } catch (e: Exception) {
                loginManager.unregisterCallback(facebookCallbackManager)
                if (cont.isActive) {
                    cont.resume(
                        FacebookLoginOutcome.Error(
                            message = e.localizedMessage ?: "Facebook sign-in failed.",
                            cause = e,
                        ),
                    )
                }
            }

            cont.invokeOnCancellation {
                loginManager.unregisterCallback(facebookCallbackManager)
            }
        }

        val accessToken = when (loginOutcome) {
            FacebookLoginOutcome.Cancelled -> return AuthResult.Cancelled
            is FacebookLoginOutcome.Error -> {
                val cause = loginOutcome.cause
                return if (cause is Exception) {
                    cause.toAuthError("Facebook")
                } else {
                    AuthResult.Error(loginOutcome.message, cause)
                }
            }
            is FacebookLoginOutcome.Success -> loginOutcome.accessToken
        }

        return try {
            val firebaseCredential = FacebookAuthProvider.getCredential(accessToken.token)
            val signInResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = signInResult.user
                ?: return AuthResult.Error("Facebook sign-in returned no user.")
            AuthResult.Success(firebaseUser.toAuthUser(forced = AuthProvider.Facebook))
        } catch (e: Exception) {
            e.toAuthError(providerName = "Facebook")
        }
    }

    override suspend fun continueAsGuest(): AuthResult = try {
        val result = firebaseAuth.signInAnonymously().await()
        val user = result.user ?: return AuthResult.Error("Guest sign-in returned no user.")
        AuthResult.Success(user.toAuthUser(forced = AuthProvider.Guest))
    } catch (e: Exception) {
        e.toAuthError(providerName = "Guest")
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        LoginManager.getInstance().logOut()
    }

    private fun isFacebookConfigured(): Boolean {
        return appContext.getString(R.string.facebook_app_id).isConfiguredValue() &&
            appContext.getString(R.string.facebook_client_token).isConfiguredValue() &&
            appContext.getString(R.string.fb_login_protocol_scheme).isConfiguredValue()
    }

    private fun FirebaseUser.toAuthUser(forced: AuthProvider? = null): AuthUser {
        val provider = forced ?: inferProvider(this)
        return AuthUser(
            uid = uid,
            displayName = displayName,
            email = email,
            provider = provider,
        )
    }

    private fun inferProvider(user: FirebaseUser): AuthProvider {
        if (user.isAnonymous) return AuthProvider.Guest
        for (info in user.providerData) {
            when (info.providerId) {
                GoogleAuthProvider.PROVIDER_ID -> return AuthProvider.Google
                EmailAuthProvider.PROVIDER_ID -> return AuthProvider.Email
                FacebookAuthProvider.PROVIDER_ID -> return AuthProvider.Facebook
            }
        }
        return AuthProvider.Guest
    }

    private fun Exception.isConfigurationError(): Boolean {
        val code = (this as? FirebaseAuthException)?.errorCode
        if (code == "ERROR_OPERATION_NOT_ALLOWED") return true
        if (this is FirebaseAuthInvalidCredentialsException) return true
        // Fallback for errors that arrive as raw messages before Firebase wraps them
        // (e.g. Google Identity Services developer_error on misconfigured SHA-1).
        val msg = message ?: return false
        return msg.contains("operation-not-allowed", ignoreCase = true) ||
            msg.contains("developer_error", ignoreCase = true) ||
            msg.contains("invalid_client", ignoreCase = true)
    }

    private fun Exception.toAuthError(providerName: String): AuthResult.Error {
        return if (isConfigurationError()) {
            AuthResult.Error(AuthNotConfiguredMessage, this)
        } else {
            val msg = localizedMessage?.ifBlank { null } ?: "$providerName sign-in failed."
            AuthResult.Error(msg, this)
        }
    }

    private fun String.isConfiguredValue(): Boolean {
        return isNotBlank() && !contains("PLACEHOLDER", ignoreCase = true)
    }

    private sealed interface FacebookLoginOutcome {
        data class Success(val accessToken: AccessToken) : FacebookLoginOutcome
        data object Cancelled : FacebookLoginOutcome
        data class Error(
            val message: String,
            val cause: Throwable?,
        ) : FacebookLoginOutcome
    }

    private companion object {
        const val AuthNotConfiguredMessage =
            "Authentication is not configured yet. Continue as guest for now."
        const val EmailVerificationSentMessage =
            "Verification email sent. Thank you for using Impulsive. Please verify your email, then log in."
        const val EmailVerificationRequiredMessage =
            "Please verify your email first. We sent the verification link again."
    }
}
