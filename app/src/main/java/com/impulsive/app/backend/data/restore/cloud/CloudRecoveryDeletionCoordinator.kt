package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException

public class CloudRecoveryDeletionCoordinator(context: Context) {
    private val appContext =
        context.applicationContext

    private val authorization =
        DriveAppDataAuthorization(
            appContext,
        )

    private val transportProvider =
        DefaultCloudRecoveryTransportProvider()

    public fun requiresDriveAuthorization(): Boolean {
        val user =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?: return false

        if (user.isAnonymous) {
            return false
        }

        return transportProvider
            .transportFor(
                cloudRecoveryTransportKind(
                    user.providerData.any { info ->
                        info.providerId == GoogleAuthProvider.PROVIDER_ID
                    },
                ),
            )
            .requiresDriveAuthorization
    }

    public suspend fun requestAuthorization(): DriveAuthorizationResult =
        authorization.requestAuthorization()

    public fun resultFromIntent(intent: Intent): DriveAuthorizationResult =
        authorization.resultFromIntent(intent)

    public suspend fun deleteAllRecoveryFiles(
        accessToken: String?,
    ): CloudRecoveryDeletionResult {
        val user =
            FirebaseAuth
                .getInstance()
                .currentUser
                ?: return CloudRecoveryDeletionResult.Failed(
                    IllegalStateException(
                        "Cloud recovery deletion requires an authenticated account.",
                    ),
                )

        if (user.isAnonymous) {
            return CloudRecoveryDeletionResult.Failed(
                IllegalStateException(
                    "Cloud recovery deletion is unavailable for guest accounts.",
                ),
            )
        }

        val transport =
            transportProvider.transportFor(
                cloudRecoveryTransportKind(
                    user.providerData.any { info ->
                        info.providerId == GoogleAuthProvider.PROVIDER_ID
                    },
                ),
            )

        return when (
            val outcome =
                transport.deleteAll(
                    if (transport.requiresDriveAuthorization) {
                        accessToken
                    } else {
                        null
                    },
                )
        ) {
            is CloudRecoveryTransportOutcome.Success ->
                CloudRecoveryDeletionResult.Success(outcome.value)

            CloudRecoveryTransportOutcome.NotFound ->
                if (transport.kind == CloudRecoveryTransportKind.FirebaseStorage) {
                    CloudRecoveryDeletionResult.Success(0)
                } else {
                    CloudRecoveryDeletionResult.Failed(
                        IllegalStateException(
                            "Drive recovery deletion returned no matching backup.",
                        ),
                    )
                }

            CloudRecoveryTransportOutcome.AuthorizationRequired ->
                CloudRecoveryDeletionResult.AuthorizationRequired

            is CloudRecoveryTransportOutcome.RetryableFailure ->
                CloudRecoveryDeletionResult.Failed(outcome.cause)

            is CloudRecoveryTransportOutcome.PermanentFailure ->
                CloudRecoveryDeletionResult.Failed(outcome.cause)
        }
    }
}
internal suspend fun deleteAllCloudRecoveryFiles(
    accessToken: String,
    findByName: suspend (String, String) -> List<DriveAppDataFile>,
    deleteById: suspend (String, String) -> Unit,
): CloudRecoveryDeletionResult = try {
    val files = findByName(accessToken, CloudRecoveryDriveFileName)
    files.forEach { file -> deleteById(accessToken, file.id) }
    CloudRecoveryDeletionResult.Success(files.size)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: DriveAppDataHttpException.Unauthorized) {
    CloudRecoveryDeletionResult.AuthorizationRequired
} catch (error: DriveAppDataHttpException.Forbidden) {
    CloudRecoveryDeletionResult.AuthorizationRequired
} catch (error: Throwable) {
    CloudRecoveryDeletionResult.Failed(error)
}

public sealed interface CloudRecoveryDeletionResult {
    public data class Success(val deletedCount: Int) : CloudRecoveryDeletionResult
    public data object AuthorizationRequired : CloudRecoveryDeletionResult
    public data class Failed(val cause: Throwable) : CloudRecoveryDeletionResult
}