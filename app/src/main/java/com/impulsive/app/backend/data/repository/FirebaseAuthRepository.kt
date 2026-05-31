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
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWebException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
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
 * exchanges that token for a Firebase credential. Apple uses Firebase's generic
 * OAuth provider. Facebook uses the Facebook Login SDK, then exchanges the
 * AccessToken for a Firebase credential.
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

    override suspend fun signInWithApple(activity: Activity): AuthResult {
        val provider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
        }.build()

        return try {
            val pending = firebaseAuth.pendingAuthResult
            val resultTask = pending ?: firebaseAuth.startActivityForSignInWithProvider(activity, provider)
            val result = resultTask.await()
            val firebaseUser = result.user
                ?: return AuthResult.Error("Apple sign-in returned no user.")
            AuthResult.Success(firebaseUser.toAuthUser(forced = AuthProvider.Apple))
        } catch (e: Exception) {
            val message = e.localizedMessage.orEmpty()
            if (message.isCancellationMessage()) {
                AuthResult.Cancelled
            } else {
                e.toAuthError(providerName = "Apple")
            }
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
                val message = loginOutcome.message
                return if (message.isConfigurationMessage()) {
                    AuthResult.Error(AuthNotConfiguredMessage, loginOutcome.cause)
                } else {
                    AuthResult.Error(message, loginOutcome.cause)
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
                FacebookAuthProvider.PROVIDER_ID -> return AuthProvider.Facebook
                "apple.com" -> return AuthProvider.Apple
            }
        }
        return AuthProvider.Guest
    }

    private fun Exception.toAuthError(providerName: String): AuthResult.Error {
        val message = localizedMessage.orEmpty()
        return if (message.isConfigurationMessage()) {
            AuthResult.Error(AuthNotConfiguredMessage, this)
        } else {
            AuthResult.Error(message.ifEmpty { "$providerName sign-in failed." }, this)
        }
    }

    private fun String.isConfiguredValue(): Boolean {
        return isNotBlank() && !contains("PLACEHOLDER", ignoreCase = true)
    }

    private fun String.isCancellationMessage(): Boolean {
        return contains("cancel", ignoreCase = true) ||
            contains("dismiss", ignoreCase = true)
    }

    private fun String.isConfigurationMessage(): Boolean {
        return contains("configuration", ignoreCase = true) ||
            contains("configured", ignoreCase = true) ||
            contains("operation-not-allowed", ignoreCase = true) ||
            contains("provider is disabled", ignoreCase = true) ||
            contains("developer_error", ignoreCase = true) ||
            contains("invalid_client", ignoreCase = true)
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
    }
}
