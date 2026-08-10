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
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.impulsive.app.R
import com.impulsive.app.backend.data.local.preferences.AdaptiveSupportCyclePreferencesDataSource
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthUser
import com.impulsive.app.backend.service.firebase.AppCheckGatedCallResult
import com.impulsive.app.backend.service.firebase.runAfterAppCheckReadiness
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal fun linkedAuthProviders(
    providerIds: Iterable<String>,
    isAnonymous: Boolean,
    forced: AuthProvider? = null,
): Set<AuthProvider> = buildSet {
    providerIds.forEach { providerId ->
        when (providerId) {
            GoogleAuthProvider.PROVIDER_ID -> add(AuthProvider.Google)
            EmailAuthProvider.PROVIDER_ID -> add(AuthProvider.Email)
            FacebookAuthProvider.PROVIDER_ID -> add(AuthProvider.Facebook)
        }
    }
    if (isAnonymous) add(AuthProvider.Guest)
    if (forced != null) add(forced)
}

internal fun requireSuccessfulAccountDeletionResponse(data: Any?) {
    val payload = data as? Map<*, *>
        ?: throw IllegalStateException("The deletion service returned an invalid response.")
    if (payload["success"] != true || payload["authUserDeleted"] != true) {
        throw IllegalStateException("The deletion service did not finish deleting the account.")
    }
}

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

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val observedUser = auth.currentUser
            if (observedUser == null) {
                trySend(null)
            } else {
                launch {
                    // A restored Firebase session can notify before providerData has
                    // been hydrated. Reload before publishing the domain model so a
                    // federated provider is present after process death as well as
                    // immediately after an interactive sign-in.
                    runCatching { observedUser.reload().await() }
                    val refreshedUser = firebaseAuth.currentUser
                    if (refreshedUser?.uid == observedUser.uid) {
                        trySend(refreshedUser.verifiedAuthUserOrNull())
                    }
                }
            }
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

    override suspend fun linkEmailAccount(email: String, password: String): AuthResult {
        firebaseAuth.currentUser
            ?: return AuthResult.Error("Sign in before connecting an email account.")

        val normalizedEmail = email.trim()

        if (normalizedEmail.isBlank()) {
            return AuthResult.Error("Enter your email address.")
        }

        if (password.isBlank()) {
            return AuthResult.Error("Enter your password.")
        }

        val credential = EmailAuthProvider.getCredential(normalizedEmail, password)

        return when (
            val result = linkOrSignInWithCredential(
                provider = AuthProvider.Email,
                providerId = EmailAuthProvider.PROVIDER_ID,
                credential = credential,
            )
        ) {
            is AuthResult.Success -> {
                val linkedUser = firebaseAuth.currentUser
                    ?: return AuthResult.Error("Email account linking returned no user.")

                if (linkedUser.hasEmailProvider() && !linkedUser.isEmailVerified) {
                    runCatching {
                        linkedUser.sendEmailVerification(
                            emailVerificationActionCodeSettings(),
                        ).await()
                    }.getOrElse { error ->
                        val exception = error as? Exception ?: Exception(error)
                        return exception.toAuthError(providerName = "Email")
                    }

                    AuthResult.EmailVerificationPending(
                        linkedUser.email ?: normalizedEmail,
                    )
                } else {
                    result
                }
            }

            else -> result
        }
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
        return when (val result = getFacebookFirebaseCredential(activity, reuseExistingSession = true)) {
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
        return when (val result = getFacebookFirebaseCredential(activity, reuseExistingSession = true)) {
            ProviderCredentialResult.Cancelled -> AuthResult.Cancelled
            is ProviderCredentialResult.Error -> result.result
            is ProviderCredentialResult.Success -> linkOrSignInWithCredential(
                provider = AuthProvider.Facebook,
                providerId = FacebookAuthProvider.PROVIDER_ID,
                credential = result.credential,
            )
        }
    }

    private suspend fun getFacebookFirebaseCredential(
        activity: Activity,
        reuseExistingSession: Boolean = false,
    ): ProviderCredentialResult {
        if (!isFacebookConfigured()) {
            return ProviderCredentialResult.Error(AuthResult.Error(AuthNotConfiguredMessage))
        }

        // Reuse an already-valid Facebook session instead of launching a second
        // login. Re-running logInWithReadPermissions while the user is still
        // signed in to Facebook can return without ever firing the SDK callback,
        // which leaves the caller suspended forever. This was observed during
        // account-deletion reauthentication: the login sheet reappeared, the user
        // continued, and the app sat on "Deleting your account" indefinitely. An
        // active token is enough to build the Firebase credential, so use it.
        if (reuseExistingSession && AccessToken.isCurrentAccessTokenActive()) {
            AccessToken.getCurrentAccessToken()?.let { current ->
                return ProviderCredentialResult.Success(
                    FacebookAuthProvider.getCredential(current.token),
                )
            }
        }

        val loginOutcome = withTimeoutOrNull(FacebookLoginTimeoutMillis) {
            suspendCancellableCoroutine<FacebookLoginOutcome> { cont ->
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
                    loginManager.logInWithReadPermissions(activity, listOf("public_profile"))
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
        } ?: FacebookLoginOutcome.Error(
            message = "Facebook did not return a sign-in result. Please try again.",
            cause = null,
        )

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
        AdaptiveSupportCyclePreferencesDataSource
            .getInstance(appContext)
            .clearAll()

        runCatching {
            SafeBrowsePassRepository(appContext).clear()
        }

        firebaseAuth.signOut()
        LoginManager.getInstance().logOut()
    }

    override suspend fun validateCurrentSession(): SessionValidationResult {
        val user = firebaseAuth.currentUser
            ?: return SessionValidationResult.NoSession

        return try {
            /*
             * reload() performs a round-trip to Firebase rather than trusting the
             * locally cached FirebaseUser.
             */
            user.reload().await()

            SessionValidationResult.Valid
        } catch (error: FirebaseAuthInvalidUserException) {
            /*
             * FirebaseAuthInvalidUserException covers several different invalid
             * account states. Only ERROR_USER_NOT_FOUND proves that the account
             * itself no longer exists.
             *
             * Disabled accounts, expired/revoked credentials, and every other
             * invalid-user result MUST NOT cause destructive local-data deletion.
             */
            if (error.errorCode == "ERROR_USER_NOT_FOUND") {
                SessionValidationResult.RemotelyDeleted
            } else {
                SessionValidationResult.Invalid
            }
        } catch (error: Exception) {
            /*
             * Network failures and unexpected/transient Firebase failures must
             * never sign the user out or erase their local data.
             */
            SessionValidationResult.TransientFailure
        }
    }

    override suspend fun hasValidSession(): Boolean {
        return when (validateCurrentSession()) {
            SessionValidationResult.Valid,
            SessionValidationResult.TransientFailure,
            -> true

            SessionValidationResult.NoSession -> false

            SessionValidationResult.RemotelyDeleted,
            SessionValidationResult.Invalid,
            -> {
                /*
                 * Preserve the existing login-screen behavior: a definitively
                 * invalid persisted session cannot auto-skip the login screen.
                 *
                 * This method does NOT delete local application data.
                 */
                runCatching { signOut() }
                false
            }
        }
    }

    private suspend fun deleteAccountThroughBackend() {
        /*
         * eraseUserData enforces BOTH Firebase Auth and App Check. If either
         * token is missing or stale, the backend rejects the call with the raw
         * code UNAUTHENTICATED before the function body runs - which previously
         * surfaced to the user as "Could not delete account, unauthenticated".
         *
         * 1. Force-refresh the Firebase ID token so request.auth is populated.
         * 2. Wait for App Check readiness (same gate BillingManager uses).
         * 3. Translate UNAUTHENTICATED into an actionable message.
         */
        firebaseAuth.currentUser?.getIdToken(true)?.await()

        val gatedResult = runAfterAppCheckReadiness {
            try {
                FirebaseFunctions.getInstance(FunctionsRegion)
                    .getHttpsCallable(EraseUserDataFunction)
                    .call()
                    .await()
            } catch (e: FirebaseFunctionsException) {
                if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                    throw IllegalStateException(
                        "Your sign-in couldn't be verified just now. Check your " +
                            "internet connection and try again. If it keeps " +
                            "happening, sign out, sign back in, and retry.",
                        e,
                    )
                }
                throw e
            }
        }

        val response = when (gatedResult) {
            is AppCheckGatedCallResult.Executed -> gatedResult.value
            is AppCheckGatedCallResult.TemporarilyUnavailable -> throw IllegalStateException(
                "Impulsive couldn't verify this device right now. Check your " +
                    "internet connection and try again in a moment.",
                gatedResult.cause,
            )
        }

        requireSuccessfulAccountDeletionResponse(response.getData())
    }

    override suspend fun deleteAccount(): AccountDeletionResult {
        if (firebaseAuth.currentUser == null) {
            return AccountDeletionResult.Success
        }

        return try {
            /*
             * eraseUserData now deletes both the known Firestore records and the
             * Firebase Authentication user through the Admin SDK. Server-side
             * deletion prevents an Auth account from surviving when client-side
             * FirebaseUser.delete() would require a recent login.
             */
            deleteAccountThroughBackend()
            firebaseAuth.signOut()
            LoginManager.getInstance().logOut()
            AccountDeletionResult.Success
        } catch (e: Exception) {
            AccountDeletionResult.Error(
                e.localizedMessage?.ifBlank { null }
                    ?: "Could not delete your account.",
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
            AuthProvider.Facebook -> getFacebookFirebaseCredential(activity, reuseExistingSession = true)
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
                    deleteAccountThroughBackend()
                    firebaseAuth.signOut()
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

        return when (credentialResult) {
            ProviderCredentialResult.Cancelled -> AccountDeletionResult.Cancelled
            is ProviderCredentialResult.Error -> AccountDeletionResult.Error(
                credentialResult.result.message,
                credentialResult.result.cause,
            )
            is ProviderCredentialResult.Success -> try {
                user.reauthenticate(credentialResult.credential).await()
                deleteAccountThroughBackend()
                firebaseAuth.signOut()
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
                if (currentUser.isAnonymous) {
                    // Guest hit an account that already exists. Offer a switch
                    // instead of a dead end. The collision's updatedCredential
                    // is preferred because Firebase refreshes it for reuse.
                    val collision = e as? FirebaseAuthUserCollisionException
                    pendingConflictCredential = collision?.updatedCredential ?: credential
                    pendingConflictProvider = provider
                    AuthResult.AccountConflict(
                        provider = provider,
                        providerDisplayName = provider.displayName(),
                        existingAccountEmail = collision?.email,
                    )
                } else {
                    AuthResult.Error("This ${provider.displayName()} account is already linked to another Impulsive account.", e)
                }
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
        return linkedAuthProviders(
            providerIds = providerData.map { it.providerId },
            isAnonymous = isAnonymous,
            forced = forced,
        )
    }

    private fun FirebaseUser.verifiedAuthUserOrNull(): AuthUser? {
        val hasVerifiedFederatedProvider = providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID ||
                it.providerId == FacebookAuthProvider.PROVIDER_ID
        }
        if (hasEmailProvider() && !isEmailVerified && !hasVerifiedFederatedProvider) return null
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

    // Captured when linking collides with an existing account. Held here, not
    // in AuthResult, so Firebase types never cross into UI code per
    // PROJECT_STRUCTURE.md's separation rule.
    @Volatile
    private var pendingConflictCredential: AuthCredential? = null

    @Volatile
    private var pendingConflictProvider: AuthProvider = AuthProvider.Google

    override suspend fun switchToExistingAccount(): AuthResult {
        val credential = pendingConflictCredential
            ?: return AuthResult.Error("Nothing to switch to. Try connecting the account again.")
        val provider = pendingConflictProvider
        val expectedProviderId = when (provider) {
            AuthProvider.Google -> GoogleAuthProvider.PROVIDER_ID
            AuthProvider.Facebook -> FacebookAuthProvider.PROVIDER_ID
            AuthProvider.Email -> EmailAuthProvider.PROVIDER_ID
            AuthProvider.Guest -> return AuthResult.Error(
                "Guest is not valid for an account-conflict switch.",
            )
        }
        val verificationError =
            "The account switch did not persist. Please try connecting ${provider.displayName()} again."
        return try {
            // signInWithCredential replaces the session only on success, so the
            // guest user is never signed out or deleted before this succeeds.
            val signInResult = firebaseAuth.signInWithCredential(credential).await()
            val signedInUser = signInResult.user
                ?: return AuthResult.Error("${provider.displayName()} sign-in returned no user.")
            val expectedUid = signedInUser.uid

            signedInUser.reload().await()
            val verifiedUser = firebaseAuth.currentUser
                ?: return AuthResult.Error(verificationError)
            verifiedUser.getIdToken(true).await()

            if (
                verifiedUser.uid != expectedUid ||
                verifiedUser.isAnonymous ||
                !verifiedUser.hasProvider(expectedProviderId)
            ) {
                return AuthResult.Error(verificationError)
            }

            if (provider == AuthProvider.Email && !verifiedUser.isEmailVerified) {
                verifiedUser.sendEmailVerification(
                    emailVerificationActionCodeSettings(),
                ).await()

                pendingConflictCredential = null
                return AuthResult.EmailVerificationPending(verifiedUser.email)
            }

            pendingConflictCredential = null
            AuthResult.Success(verifiedUser.toAuthUser(forced = provider))
        } catch (e: Exception) {
            e.toAuthError(providerName = provider.displayName())
        }
    }

    override fun abandonAccountSwitch() {
        pendingConflictCredential = null
    }

    private fun Exception.isCredentialAlreadyLinkedError(): Boolean {
        val code = (this as? FirebaseAuthException)?.errorCode ?: return false
        return code == "ERROR_CREDENTIAL_ALREADY_IN_USE" ||
            code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ||
            code == "ERROR_EMAIL_ALREADY_IN_USE"
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
        // One CallbackManager for the whole process. MainActivity delivers
        // Facebook's onActivityResult to the repository owned by the
        // activity-scoped AuthViewModel, while a login flow may have
        // registered its callback on a repository owned by a differently
        // scoped AuthViewModel. With per-instance managers the result never
        // reaches the registered callback and the Facebook coroutine never
        // resumes. A single shared manager makes delivery instance
        // independent. CallbackManager.Factory.create holds no Context, so
        // this leaks nothing.
        val facebookCallbackManager: CallbackManager = CallbackManager.Factory.create()

        const val FunctionsRegion = "us-central1"
        const val EraseUserDataFunction = "eraseUserData"
        const val FacebookLoginTimeoutMillis = 60_000L
        const val EmailVerificationReturnUrl = "https://useimpulsive.com/auth/verified"
        const val AuthNotConfiguredMessage =
            "Authentication is not configured yet. Continue as guest for now."
    }
}
