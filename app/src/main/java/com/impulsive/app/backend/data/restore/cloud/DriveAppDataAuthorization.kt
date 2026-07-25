package com.impulsive.app.backend.data.restore.cloud

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.account.GoogleAccountIdentity
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal const val DriveAppDataScope =
    "https://www.googleapis.com/auth/drive.appdata"

sealed interface DriveAuthorizationResult {
    data class Authorized(
        val accessToken: String,
    ) : DriveAuthorizationResult

    data class NeedsUserResolution(
        val pendingIntent: PendingIntent,
    ) : DriveAuthorizationResult

    data object Cancelled : DriveAuthorizationResult

    data class Failed(
        val cause: Throwable?,
    ) : DriveAuthorizationResult
}

class DriveAppDataAuthorization(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val client = Identity.getAuthorizationClient(appContext)

    suspend fun requestAuthorization(): DriveAuthorizationResult {
        val identity = FirebaseAuth.getInstance().currentUser
            ?.let(::resolveGoogleAccountIdentity)
            ?: return DriveAuthorizationResult.Failed(
                IllegalStateException("Drive authorization requires a linked Google identity."),
            )
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveAppDataScope)))
            .setAccount(driveAuthorizationAccount(identity))
            .build()

        return try {
            client.authorize(request).await().toDriveAuthorizationResult()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: ApiException) {
            error.toDriveAuthorizationFailure()
        } catch (error: Throwable) {
            DriveAuthorizationResult.Failed(error)
        }
    }

    fun resultFromIntent(
        data: Intent,
    ): DriveAuthorizationResult = try {
        client.getAuthorizationResultFromIntent(data).toDriveAuthorizationResult()
    } catch (error: ApiException) {
        error.toDriveAuthorizationFailure()
    } catch (error: Throwable) {
        DriveAuthorizationResult.Failed(error)
    }

    private fun AuthorizationResult.toDriveAuthorizationResult(): DriveAuthorizationResult {
        if (hasResolution()) {
            val pendingIntent = pendingIntent
                ?: return DriveAuthorizationResult.Failed(
                    IllegalStateException("Drive authorization returned a resolution without a PendingIntent."),
                )
            return DriveAuthorizationResult.NeedsUserResolution(pendingIntent)
        }

        return validateDriveAuthorization(
            accessToken = accessToken,
            grantedScopes = grantedScopes.map { scope -> scope.toString() },
            hasResolution = false,
        )
    }
}

internal fun driveAuthorizationAccount(
    identity: GoogleAccountIdentity,
): Account = Account(identity.accountName, GoogleAccountType)

internal const val GoogleAccountType = "com.google"

private fun ApiException.toDriveAuthorizationFailure(): DriveAuthorizationResult =
    if (statusCode == CommonStatusCodes.CANCELED) {
        DriveAuthorizationResult.Cancelled
    } else {
        DriveAuthorizationResult.Failed(this)
    }

internal fun validateDriveAuthorization(
    accessToken: String?,
    grantedScopes: Collection<String>,
    hasResolution: Boolean,
): DriveAuthorizationResult {
    if (hasResolution) {
        return DriveAuthorizationResult.Failed(
            IllegalStateException("Drive authorization still requires user resolution."),
        )
    }

    val token = accessToken?.takeIf { it.isNotBlank() }
        ?: return DriveAuthorizationResult.Failed(
            IllegalStateException("Drive authorization returned no access token."),
        )

    if (DriveAppDataScope !in grantedScopes) {
        return DriveAuthorizationResult.Failed(
            IllegalStateException("Drive authorization did not grant the appDataFolder scope."),
        )
    }

    return DriveAuthorizationResult.Authorized(token)
}
