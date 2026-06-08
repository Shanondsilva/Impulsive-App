package com.impulsive.app.backend.session.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    constructor(application: Application) : this(
        application = application,
        repository = AuthRepositoryFactory.create(application),
    )

    internal constructor(
        application: Application,
        repository: AuthRepository,
    ) : super(application) {
        this.repository = repository
        _state = MutableStateFlow(AuthState(user = repository.currentUserSnapshot()))
        state = _state.asStateFlow()
    }

    fun signInWithGoogle(activity: Activity) = launchSignIn(AuthProvider.Google) {
        repository.signInWithGoogle(activity)
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

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _state.update { AuthState() }
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
                when (result) {
                    is AuthResult.Success -> {
                        succeeded = true
                        current.copy(
                            user = result.user,
                            inFlightProvider = null,
                            errorMessage = null,
                        )
                    }
                    AuthResult.Cancelled -> current.copy(
                        inFlightProvider = null,
                        errorMessage = null,
                    )
                    is AuthResult.Error -> current.copy(
                        inFlightProvider = null,
                        errorMessage = result.message,
                    )
                }
            }
            if (succeeded) onSuccess?.invoke()
        }
    }
}
