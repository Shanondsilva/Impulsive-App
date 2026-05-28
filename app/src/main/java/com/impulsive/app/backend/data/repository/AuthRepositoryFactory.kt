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

    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun signInWithApple(activity: Activity): AuthResult {
        return AuthResult.Error(AuthNotConfiguredMessage)
    }

    override suspend fun signInWithFacebook(activity: Activity): AuthResult {
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

    override suspend fun signOut() {
        currentUserState.value = null
    }

    private companion object {
        const val AuthNotConfiguredMessage =
            "Authentication is not configured yet. Continue as guest for now."
    }
}
