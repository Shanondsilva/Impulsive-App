package com.impulsive.app.backend.data.repository

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object AuthRepositoryFactory {
    fun create(context: Context): AuthRepository {
        return if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseAuthRepository(context)
        } else {
            GuestOnlyAuthRepository()
        }
    }
}

private class GuestOnlyAuthRepository : AuthRepository {
    private val currentUserState = MutableStateFlow<AuthUser?>(null)

    override val currentUser: Flow<AuthUser?> = currentUserState

    override fun currentUserSnapshot(): AuthUser? = currentUserState.value

    override fun pendingEmailVerificationAddress(): String? = null

    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun linkGoogleAccount(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun createAccountWithEmail(email: String, password: String): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun refreshEmailVerification(): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun signInWithFacebook(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun linkFacebookAccount(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun continueAsGuest(): AuthResult {
        val guest = AuthUser(
            uid = "guest-" + UUID.randomUUID().toString(),
            displayName = null,
            email = null,
            provider = AuthProvider.Guest,
        )
        currentUserState.value = guest
        return AuthResult.Success(guest)
    }

    override suspend fun switchToExistingAccount(): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override fun abandonAccountSwitch() = Unit

    override suspend fun signOut() {
        currentUserState.value = null
    }

    override suspend fun validateCurrentSession(): SessionValidationResult {
        return if (currentUserState.value != null) {
            SessionValidationResult.Valid
        } else {
            SessionValidationResult.NoSession
        }
    }

    override suspend fun hasValidSession(): Boolean = currentUserState.value != null

    override suspend fun deleteAccount(): AccountDeletionResult {
        currentUserState.value = null
        return AccountDeletionResult.Success
    }

    override suspend fun reauthenticateAndDeleteAccount(
        activity: Activity,
        provider: AuthProvider,
        password: String?,
    ): AccountDeletionResult {
        currentUserState.value = null
        return AccountDeletionResult.Success
    }

    private companion object {
        const val AuthNotConfiguredMessage =
            "Authentication is not configured yet. Continue as guest for now."
    }
}
