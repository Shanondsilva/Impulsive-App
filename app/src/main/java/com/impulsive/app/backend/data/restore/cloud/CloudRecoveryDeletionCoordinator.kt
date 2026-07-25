package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException

public class CloudRecoveryDeletionCoordinator(context: Context) {
    private val authorization = DriveAppDataAuthorization(context.applicationContext)
    private val driveClient = DriveAppDataClient()

    public suspend fun requestAuthorization(): DriveAuthorizationResult = authorization.requestAuthorization()
    public fun resultFromIntent(intent: Intent): DriveAuthorizationResult = authorization.resultFromIntent(intent)
    public suspend fun deleteAllRecoveryFiles(accessToken: String): CloudRecoveryDeletionResult =
        deleteAllCloudRecoveryFiles(
            accessToken = accessToken,
            findByName = { token, fileName -> driveClient.findByName(token, fileName) },
            deleteById = { token, fileId -> driveClient.delete(token, fileId) },
        )
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