package com.impulsive.app.backend.session.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.AccountDeletionResult
import com.impulsive.app.backend.data.repository.AuthRepository
import com.impulsive.app.backend.data.repository.AuthRepositoryFactory
import com.impulsive.app.backend.data.repository.AuthResult
import com.impulsive.app.backend.data.repository.FirebaseAuthRepository
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates sign-in for the login screen.
 *
 * Exposes one entry point per provider plus [continueAsGuest]. While a provider
 * is in flight we set [AuthState.inFlightProvider] so the UI can disable the
 * other buttons and show a spinner on the right one. Cancellations are silent;
 * real errors land in [AuthState.errorMessage] and the screen surfaces them.
 *
 * The repository stays private; the hosting Activity forwards
 * `onActivityResult` to the Facebook SDK through [forwardActivityResult].
 */
class AuthViewModel : AndroidViewModel {
    private val repository: AuthRepository

    private val _state: MutableStateFlow<AuthState>
    val state: StateFlow<AuthState>

    private val _deletionState = MutableStateFlow<AccountDeletionUiState>(AccountDeletionUiState.Idle)
    val deletionState: StateFlow<AccountDeletionUiState> = _deletionState.asStateFlow()

    constructor(application: Application) : this(
        application = application,
        repository = AuthRepositoryFactory.create(application),
    )

    internal constructor(
        application: Application,
        repository: AuthRepository,
    ) : super(application) {
        this.repository = repository
        _state = MutableStateFlow(
            AuthState(
                user = repository.currentUserSnapshot(),
                pendingEmailVerificationAddress = repository.pendingEmailVerificationAddress(),
            ),
        )
        state = _state.asStateFlow()
    }

    fun signInWithGoogle(activity: Activity) = launchSignIn(AuthProvider.Google) {
        repository.signInWithGoogle(activity)
    }

    fun linkGoogleAccount(activity: Activity) = launchSignIn(AuthProvider.Google) {
        repository.linkGoogleAccount(activity)
    }

    fun createAccountWithEmail(email: String, password: String) = launchSignIn(AuthProvider.Email) {
        repository.createAccountWithEmail(email, password)
    }

    fun signInWithEmail(email: String, password: String) = launchSignIn(AuthProvider.Email) {
        repository.signInWithEmail(email, password)
    }

    fun signInWithFacebook(activity: Activity) = launchSignIn(AuthProvider.Facebook) {
        repository.signInWithFacebook(activity)
    }

    fun linkFacebookAccount(activity: Activity) = launchSignIn(AuthProvider.Facebook) {
        repository.linkFacebookAccount(activity)
    }

    fun signInWithGoogleForAppLockReset(activity: Activity, onSuccess: () -> Unit) =
        launchSignIn(AuthProvider.Google, onSuccess) {
            repository.signInWithGoogle(activity)
        }

    fun signInWithFacebookForAppLockReset(activity: Activity, onSuccess: () -> Unit) =
        launchSignIn(AuthProvider.Facebook, onSuccess) {
            repository.signInWithFacebook(activity)
        }

    fun continueAsGuest() = launchSignIn(AuthProvider.Guest) {
        repository.continueAsGuest()
    }

    fun refreshEmailVerification() {
        val current = _state.value
        if (!current.isWaitingForEmailVerification || current.inFlightProvider != null) return
        _state.update { it.copy(inFlightProvider = AuthProvider.Email, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.refreshEmailVerification()
            _state.update { state -> state.withAuthResult(result) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _state.update { AuthState() }
        }
    }

    /**
     * Starts account deletion. On Firebase's recent-login requirement this either
     * launches provider reauthentication (Google/Facebook) automatically, or asks
     * the UI for a password (Email). The UI observes [deletionState].
     */
    fun deleteAccount(activity: Activity) {
        if (_deletionState.value == AccountDeletionUiState.InProgress) return
        _deletionState.value = AccountDeletionUiState.InProgress
        viewModelScope.launch {
            when (val result = repository.deleteAccount()) {
                AccountDeletionResult.Success ->
                    _deletionState.value = AccountDeletionUiState.Deleted
                is AccountDeletionResult.ReauthRequired ->
                    if (result.provider == AuthProvider.Email) {
                        _deletionState.value = AccountDeletionUiState.NeedsPassword(result.email)
                    } else {
                        applyDeletionResult(
                            repository.reauthenticateAndDeleteAccount(activity, result.provider, null),
                        )
                    }
                AccountDeletionResult.Cancelled ->
                    _deletionState.value = AccountDeletionUiState.Idle
                is AccountDeletionResult.Error ->
                    _deletionState.value = AccountDeletionUiState.Failed(result.message)
            }
        }
    }

    /** Completes Email account deletion once the UI has collected the password. */
    fun submitPasswordAndDeleteAccount(activity: Activity, password: String) {
        if (_deletionState.value !is AccountDeletionUiState.NeedsPassword) return
        _deletionState.value = AccountDeletionUiState.InProgress
        viewModelScope.launch {
            applyDeletionResult(
                repository.reauthenticateAndDeleteAccount(activity, AuthProvider.Email, password),
            )
        }
    }

    /** Clears a finished (failed or password-prompt) deletion back to idle. */
    fun cancelAccountDeletion() {
        if (_deletionState.value != AccountDeletionUiState.InProgress) {
            _deletionState.value = AccountDeletionUiState.Idle
        }
    }

    private fun applyDeletionResult(result: AccountDeletionResult) {
        _deletionState.value = when (result) {
            AccountDeletionResult.Success -> AccountDeletionUiState.Deleted
            AccountDeletionResult.Cancelled -> AccountDeletionUiState.Idle
            is AccountDeletionResult.Error -> AccountDeletionUiState.Failed(result.message)
            is AccountDeletionResult.ReauthRequired ->
                AccountDeletionUiState.Failed("Could not verify your sign-in. Please try again.")
        }
    }

    /** Called by the screen once it's read & displayed the error. */
    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * MainActivity should call this from its `onActivityResult` so Facebook's
     * Custom Tab result can reach the SDK.
     */
    fun forwardActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        (repository as? FirebaseAuthRepository)
            ?.onActivityResult(requestCode, resultCode, data)
    }

    private fun launchSignIn(
        provider: AuthProvider,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> AuthResult,
    ) {
        // Guard against double-tap while another provider is in flight.
        if (_state.value.inFlightProvider != null) return
        _state.update { it.copy(inFlightProvider = provider, errorMessage = null) }
        viewModelScope.launch {
            val result = block()
            var succeeded = false
            _state.update { current ->
                val next = current.withAuthResult(result)
                if (result is AuthResult.Success) {
                    succeeded = true
                }
                next
            }
            if (succeeded) onSuccess?.invoke()
        }
    }

    private fun AuthState.withAuthResult(result: AuthResult): AuthState = when (result) {
        is AuthResult.Success -> copy(
            user = result.user,
            inFlightProvider = null,
            errorMessage = null,
            pendingEmailVerificationAddress = null,
        )
        is AuthResult.EmailVerificationPending -> copy(
            user = null,
            inFlightProvider = null,
            errorMessage = null,
            pendingEmailVerificationAddress = result.email,
        )
        AuthResult.Cancelled -> copy(
            inFlightProvider = null,
            errorMessage = null,
        )
        is AuthResult.Error -> copy(
            inFlightProvider = null,
            errorMessage = result.message,
        )
    }
}
