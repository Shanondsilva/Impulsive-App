package com.impulsive.app.backend.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
            trySend(auth.currentUser?.verifiedAuthUserOrNull())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun currentUserSnapshot(): AuthUser? = firebaseAuth.currentUser?.verifiedAuthUserOrNull()

    override fun pendingEmailVerificationAddress(): String? {
        val user = firebaseAuth.currentUser ?: return null
        return if (user.hasEmailProvider() && !user.isEmailVerified) user.email else null
    }

    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        return when (val result = getGoogleFirebaseCredential(activity)) {
            ProviderCredentialResult.Cancelled -> AuthResult.Cancelled
            is ProviderCredentialResult.Error -> result.result
            is ProviderCredentialResult.Success -> try {
                val authResult = firebaseAuth.signInWithCredential(result.credential).await()
                val firebaseUser = authResult.user
                    ?: return AuthResult.Error("Google sign-in returned no user.")
                AuthResult.Success(firebaseUser.toAuthUser(forced = AuthProvider.Google))
            } catch (e: Exception) {
                e.toAuthError(providerName = "Google")
            }
        }
    }

    override suspend fun linkGoogleAccount(activity: Activity): AuthResult {
        return when (val result = getGoogleFirebaseCredential(activity)) {
            ProviderCredentialResult.Cancelled -> AuthResult.Cancelled
            is ProviderCredentialResult.Error -> result.result
            is ProviderCredentialResult.Success -> linkOrSignInWithCredential(
                provider = AuthProvider.Google,
                providerId = GoogleAuthProvider.PROVIDER_ID,
                credential = result.credential,
            )
        }
    }

    private suspend fun getGoogleFirebaseCredential(activity: Activity): ProviderCredentialResult {
        val serverClientId = appContext.getString(R.string.default_web_client_id)
        if (!serverClientId.isConfiguredValue()) {
            return ProviderCredentialResult.Error(AuthResult.Error(AuthNotConfiguredMessage))
        }

        // Fast path: silently auto-select an account the user has used before.
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val firstAttempt = requestGoogleCredential(activity, googleIdOption)
        if (firstAttempt != GoogleCredentialOutcome.NoCredential) {
            return firstAttempt.toProviderResult()
        }

        // Nothing to auto-select, so show the full Sign in with Google picker,
        // which also lets the user add a Google account.
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        return requestGoogleCredential(activity, signInOption).toProviderResult()
    }

    private suspend fun requestGoogleCredential(
        activity: Activity,
        option: CredentialOption,
    ): GoogleCredentialOutcome {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleCredentialOutcome.Success(GoogleAuthProvider.getCredential(googleIdToken, null))
            } else {
                GoogleCredentialOutcome.Failure(
                    AuthResult.Error("Unexpected credential type returned for Google sign-in."),
                )
            }
        } catch (cancel: GetCredentialCancellationException) {
            GoogleCredentialOutcome.Cancelled
        } catch (noCredential: NoCredentialException) {
            GoogleCredentialOutcome.NoCredential
        } catch (e: GetCredentialException) {
            GoogleCredentialOutcome.Failure(e.toAuthError(providerName = "Google"))
        } catch (e: Exception) {
            GoogleCredentialOutcome.Failure(e.toAuthError(providerName = "Google"))
        }
    }

    private fun GoogleCredentialOutcome.toProviderResult(): ProviderCredentialResult = when (this) {
        is GoogleCredentialOutcome.Success -> ProviderCredentialResult.Success(credential)
        GoogleCredentialOutcome.Cancelled -> ProviderCredentialResult.Cancelled
        GoogleCredentialOutcome.NoCredential -> ProviderCredentialResult.Error(
            AuthResult.Error(
                "No Google account is available on this device. Add a Google account in " +
                    "your device settings, then try again.",
            ),
        )
        is GoogleCredentialOutcome.Failure -> ProviderCredentialResult.Error(result)
    }

    private sealed interface GoogleCredentialOutcome {
        data class Success(val credential: AuthCredential) : GoogleCredentialOutcome
        data object Cancelled : GoogleCredentialOutcome
        data object NoCredential : GoogleCredentialOutcome
        data class Failure(val result: AuthResult.Error) : GoogleCredentialOutcome
    }

    override suspend fun createAccountWithEmail(email: String, password: String): AuthResult = try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: firebaseAuth.currentUser
            ?: return AuthResult.Error("Email account creation returned no user.")
        user.sendEmailVerification(emailVerificationActionCodeSettings()).await()
        AuthResult.EmailVerificationPending(user.email ?: email.trim())
    } catch (e: Exception) {
        e.toAuthError(providerName = "Email")
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: firebaseAuth.currentUser
                ?: return AuthResult.Error("Email login returned no user.")
            if (!user.isEmailVerified) {
                user.sendEmailVerification(emailVerificationActionCodeSettings()).await()
                return AuthResult.EmailVerificationPending(user.email ?: email.trim())
            }
            AuthResult.Success(user.toAuthUser(forced = AuthProvider.Email))
        } catch (e: Exception) {
            e.toAuthError(providerName = "Email")
        }
    }

    override suspend fun refreshEmailVerification(): AuthResult {
        return try {
            val user = firebaseAuth.currentUser
                ?: return AuthResult.Error("Email verification session expired. Please log in again.")
            user.reload().await()
            val reloadedUser = firebaseAuth.currentUser
                ?: return AuthResult.Error("Email verification session expired. Please log in again.")
            if (!reloadedUser.hasEmailProvider()) {
                return AuthResult.Cancelled
            }
            if (reloadedUser.isEmailVerified) {
                AuthResult.Success(reloadedUser.toAuthUser(forced = AuthProvider.Email))
            } else {
                AuthResult.EmailVerificationPending(reloadedUser.email)
            }
        } catch (e: Exception) {
            e.toAuthError(providerName = "Email")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override suspend fun signInWithFacebook(activity: Activity): AuthResult {
        return when (val result = getFacebookFirebaseCredential(activity)) {
            ProviderCredentialResult.Cancelled -> AuthResult.Cancelled
            is ProviderCredentialResult.Error -> result.result
            is ProviderCredentialResult.Success -> try {
                val signInResult = firebaseAuth.signInWithCredential(result.credential).await()
                val firebaseUser = signInResult.user
                    ?: return AuthResult.Error("Facebook sign-in returned no user.")
                AuthResult.Success(firebaseUser.toAuthUser(forced = AuthProvider.Facebook))
            } catch (e: Exception) {
                e.toAuthError(providerName = "Facebook")
            }
        }
    }

    override suspend fun linkFacebookAccount(activity: Activity): AuthResult {
        return when (val result = getFacebookFirebaseCredential(activity)) {
            ProviderCredentialResult.Cancelled -> AuthResult.Cancelled
            is ProviderCredentialResult.Error -> result.result
            is ProviderCredentialResult.Success -> linkOrSignInWithCredential(
                provider = AuthProvider.Facebook,
                providerId = FacebookAuthProvider.PROVIDER_ID,
                credential = result.credential,
            )
        }
    }

    private suspend fun getFacebookFirebaseCredential(activity: Activity): ProviderCredentialResult {
        if (!isFacebookConfigured()) {
            return ProviderCredentialResult.Error(AuthResult.Error(AuthNotConfiguredMessage))
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
            FacebookLoginOutcome.Cancelled -> return ProviderCredentialResult.Cancelled
            is FacebookLoginOutcome.Error -> {
                val cause = loginOutcome.cause
                return ProviderCredentialResult.Error(if (cause is Exception) {
                    cause.toAuthError("Facebook")
                } else {
                    AuthResult.Error(loginOutcome.message, cause)
                })
            }
            is FacebookLoginOutcome.Success -> loginOutcome.accessToken
        }

        return ProviderCredentialResult.Success(FacebookAuthProvider.getCredential(accessToken.token))
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

    override suspend fun deleteAccount(): AccountDeletionResult {
        val user = firebaseAuth.currentUser ?: return AccountDeletionResult.Success
        return try {
            user.delete().await()
            LoginManager.getInstance().logOut()
            AccountDeletionResult.Success
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            AccountDeletionResult.ReauthRequired(inferProvider(user), user.email)
        } catch (e: Exception) {
            AccountDeletionResult.Error(
                e.localizedMessage?.ifBlank { null } ?: "Could not delete your account.",
                e,
            )
        }
    }

    override suspend fun reauthenticateAndDeleteAccount(
        activity: Activity,
        provider: AuthProvider,
        password: String?,
    ): AccountDeletionResult {
        val user = firebaseAuth.currentUser ?: return AccountDeletionResult.Success

        val credentialResult: ProviderCredentialResult = when (provider) {
            AuthProvider.Google -> getGoogleFirebaseCredential(activity)
            AuthProvider.Facebook -> getFacebookFirebaseCredential(activity)
            AuthProvider.Email -> {
                val email = user.email
                if (email.isNullOrBlank() || password.isNullOrBlank()) {
                    return AccountDeletionResult.Error("Enter your password to delete your account.")
                }
                ProviderCredentialResult.Success(EmailAuthProvider.getCredential(email, password))
            }
            AuthProvider.Guest -> {
                // Anonymous users have no reauthentication credential, so attempt a
                // direct delete and surface any failure.
                return try {
                    user.delete().await()
                    AccountDeletionResult.Success
                } catch (e: Exception) {
                    AccountDeletionResult.Error(
                        e.localizedMessage?.ifBlank { null } ?: "Could not delete your account.",
                        e,
                    )
                }
            }
        }

        return when (credentialResult) {
            ProviderCredentialResult.Cancelled -> AccountDeletionResult.Cancelled
            is ProviderCredentialResult.Error -> AccountDeletionResult.Error(
                credentialResult.result.message,
                credentialResult.result.cause,
            )
            is ProviderCredentialResult.Success -> try {
                user.reauthenticate(credentialResult.credential).await()
                user.delete().await()
                LoginManager.getInstance().logOut()
                AccountDeletionResult.Success
            } catch (e: Exception) {
                AccountDeletionResult.Error(
                    e.localizedMessage?.ifBlank { null } ?: "Could not delete your account.",
                    e,
                )
            }
        }
    }

    private suspend fun linkOrSignInWithCredential(
        provider: AuthProvider,
        providerId: String,
        credential: AuthCredential,
    ): AuthResult {
        val currentUser = firebaseAuth.currentUser
            ?: return try {
                val signInResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = signInResult.user
                    ?: return AuthResult.Error("${provider.displayName()} sign-in returned no user.")
                AuthResult.Success(firebaseUser.toAuthUser(forced = provider))
            } catch (e: Exception) {
                e.toAuthError(providerName = provider.displayName())
            }

        if (currentUser.hasProvider(providerId)) {
            return AuthResult.Success(currentUser.toAuthUser(forced = provider))
        }

        return try {
            val linkResult = currentUser.linkWithCredential(credential).await()
            val linkedUser = linkResult.user ?: firebaseAuth.currentUser
                ?: return AuthResult.Error("${provider.displayName()} account linking returned no user.")
            AuthResult.Success(linkedUser.toAuthUser(forced = provider))
        } catch (e: Exception) {
            if (e.isCredentialAlreadyLinkedError()) {
                AuthResult.Error("This ${provider.displayName()} account is already linked to another Impulsive account.", e)
            } else {
                e.toAuthError(providerName = provider.displayName())
            }
        }
    }

    private fun isFacebookConfigured(): Boolean {
        return appContext.getString(R.string.facebook_app_id).isConfiguredValue() &&
            appContext.getString(R.string.facebook_client_token).isConfiguredValue() &&
            appContext.getString(R.string.fb_login_protocol_scheme).isConfiguredValue()
    }

    private fun emailVerificationActionCodeSettings(): ActionCodeSettings =
        ActionCodeSettings.newBuilder()
            .setUrl(EmailVerificationReturnUrl)
            .setHandleCodeInApp(false)
            .build()

    private fun FirebaseUser.toAuthUser(forced: AuthProvider? = null): AuthUser {
        val provider = forced ?: inferProvider(this)
        return AuthUser(
            uid = uid,
            displayName = displayName,
            email = email,
            provider = provider,
            linkedProviders = linkedAuthProviders(forced = provider),
        )
    }

    private fun FirebaseUser.linkedAuthProviders(forced: AuthProvider? = null): Set<AuthProvider> {
        val linked = providerData.mapNotNull { info ->
            when (info.providerId) {
                GoogleAuthProvider.PROVIDER_ID -> AuthProvider.Google
                EmailAuthProvider.PROVIDER_ID -> AuthProvider.Email
                FacebookAuthProvider.PROVIDER_ID -> AuthProvider.Facebook
                else -> null
            }
        }.toMutableSet()

        if (isAnonymous) linked += AuthProvider.Guest
        if (forced != null) linked += forced

        return linked
    }

    private fun FirebaseUser.verifiedAuthUserOrNull(): AuthUser? {
        if (hasEmailProvider() && !isEmailVerified) return null
        return toAuthUser()
    }

    private fun FirebaseUser.hasEmailProvider(): Boolean =
        providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    private fun FirebaseUser.hasProvider(providerId: String): Boolean =
        providerData.any { it.providerId == providerId }

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

    private fun Exception.isCredentialAlreadyLinkedError(): Boolean {
        val code = (this as? FirebaseAuthException)?.errorCode ?: return false
        return code == "ERROR_CREDENTIAL_ALREADY_IN_USE" ||
            code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
    }

    private fun AuthProvider.displayName(): String = when (this) {
        AuthProvider.Google -> "Google"
        AuthProvider.Facebook -> "Facebook"
        AuthProvider.Email -> "Email"
        AuthProvider.Guest -> "Guest"
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

    private sealed interface ProviderCredentialResult {
        data class Success(val credential: AuthCredential) : ProviderCredentialResult
        data object Cancelled : ProviderCredentialResult
        data class Error(val result: AuthResult.Error) : ProviderCredentialResult
    }

    private companion object {
        const val EmailVerificationReturnUrl = "https://useimpulsive.com/auth/verified"
        const val AuthNotConfiguredMessage =
            "Authentication is not configured yet. Continue as guest for now."
    }
}
