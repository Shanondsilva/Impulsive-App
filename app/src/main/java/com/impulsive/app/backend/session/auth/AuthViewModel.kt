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
import com.impulsive.app.backend.data.repository.SessionValidationResult
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

    @Volatile
    private var accountSwitchInProgress = false

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
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _state.update { current ->
                    if (accountSwitchInProgress && user == null) {
                        current
                    } else if (current.user == user) {
                        current
                    } else {
                        current.copy(user = user)
                    }
                }
            }
        }
    }

    fun signInWithGoogle(activity: Activity) = launchSignIn(AuthProvider.Google) {
        repository.signInWithGoogle(activity)
    }

    fun linkGoogleAccount(activity: Activity) = launchSignIn(AuthProvider.Google) {
        repository.linkGoogleAccount(activity)
    }

    fun linkEmailAccount(email: String, password: String) = launchSignIn(AuthProvider.Email) {
        repository.linkEmailAccount(email = email, password = password)
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
                AccountDeletionResult.Success -> {
                    _state.value = AuthState()
                    _deletionState.value = AccountDeletionUiState.Deleted
                }
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
            AccountDeletionResult.Success -> {
                _state.value = AuthState()
                AccountDeletionUiState.Deleted
            }
            AccountDeletionResult.Cancelled -> AccountDeletionUiState.Idle
            is AccountDeletionResult.Error -> AccountDeletionUiState.Failed(result.message)
            is AccountDeletionResult.ReauthRequired ->
                AccountDeletionUiState.Failed("Could not verify your sign-in. Please try again.")
        }
    }

    /**
     * Confirms the switch offered by an [AuthResult.AccountConflict] dialog.
     * Account switching does not erase local recovery data or restart or
     * terminate the process. The successfully verified Firebase user is
     * applied directly to [AuthState], and Settings updates through that state
     * and the existing Firebase auth-state collector. A failed switch leaves
     * the existing session and local data unchanged.
     */
    fun confirmAccountSwitch() {
        confirmAccountSwitchInternal(navigateAfterSuccess = true)
    }

    fun confirmAccountSwitchForPurchase() {
        confirmAccountSwitchInternal(navigateAfterSuccess = false)
    }

    private fun confirmAccountSwitchInternal(navigateAfterSuccess: Boolean) {
        val conflict = _state.value.pendingAccountConflict ?: return
        if (_state.value.inFlightProvider != null) return
        _state.update {
            it.copy(
                inFlightProvider = conflict.provider,
                pendingAccountConflict = null,
                errorMessage = null,
            )
        }
        accountSwitchInProgress = true
        viewModelScope.launch {
            val result = try {
                repository.switchToExistingAccount()
            } catch (error: Exception) {
                AuthResult.Error(
                    error.localizedMessage?.ifBlank { null }
                        ?: "Could not switch accounts. Please try again.",
                    error,
                )
            }
            accountSwitchInProgress = false
            _state.update {
                it.withAuthResult(result).copy(
                    accountSwitchCompleted =
                        navigateAfterSuccess && result is AuthResult.Success,
                )
            }
        }
    }

    /** Dismisses the switch dialog. Guest session and local data untouched. */
    fun dismissAccountSwitch() {
        repository.abandonAccountSwitch()
        _state.update { it.copy(pendingAccountConflict = null) }
    }

    fun consumeAccountSwitchNavigation() {
        _state.update { it.copy(accountSwitchCompleted = false) }
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

    /**
     * Server-verifies the current session before the login screen is allowed
     * to auto-advance past itself. See [AuthRepository.hasValidSession].
     */
    suspend fun confirmSessionStillValid(): Boolean = repository.hasValidSession()

    /**
     * Validates an already-running authenticated session when the app returns to
     * the foreground.
     *
     * Guest accounts are intentionally excluded. They are local/anonymous app
     * sessions and must never trigger the remote account-deletion wipe flow.
     */
    suspend fun validateForegroundSession(): SessionValidationResult {
        val currentUser = _state.value.user
            ?: return SessionValidationResult.NoSession

        if (currentUser.provider == AuthProvider.Guest) {
            return SessionValidationResult.Valid
        }

        return repository.validateCurrentSession()
    }

    /**
     * Clears SDK and UI authentication state after foreground validation has
     * established that the cached session can no longer be used.
     *
     * Local Impulsive data is deliberately NOT touched here. MainActivity owns the
     * destructive local wipe and invokes this only at the appropriate point.
     */
    suspend fun clearValidatedSession() {
        repository.signOut()
        _state.value = AuthState()
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
        is AuthResult.AccountConflict -> copy(
            inFlightProvider = null,
            errorMessage = null,
            pendingAccountConflict = result,
        )
    }
}
