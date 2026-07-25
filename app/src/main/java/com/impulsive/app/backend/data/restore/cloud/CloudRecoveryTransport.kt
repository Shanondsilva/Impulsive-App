package com.impulsive.app.backend.data.restore.cloud

import kotlinx.coroutines.CancellationException

internal enum class CloudRecoveryTransportKind {
    DriveAppData,
    FirebaseStorage,
}

internal fun cloudRecoveryTransportKind(
    hasGoogleProvider: Boolean,
): CloudRecoveryTransportKind =
    if (hasGoogleProvider) {
        CloudRecoveryTransportKind.DriveAppData
    } else {
        CloudRecoveryTransportKind.FirebaseStorage
    }

internal sealed interface CloudRecoveryTransportOutcome<out T> {
    data class Success<T>(val value: T) : CloudRecoveryTransportOutcome<T>
    data object NotFound : CloudRecoveryTransportOutcome<Nothing>
    data object AuthorizationRequired : CloudRecoveryTransportOutcome<Nothing>
    data class RetryableFailure(val cause: Throwable) : CloudRecoveryTransportOutcome<Nothing>
    data class PermanentFailure(val cause: Throwable) : CloudRecoveryTransportOutcome<Nothing>
}

internal fun interface CloudRecoveryUploadTransportProvider {
    fun transportFor(kind: CloudRecoveryTransportKind): CloudRecoveryTransport
}

internal interface CloudRecoveryTransport {
    val kind: CloudRecoveryTransportKind
    val requiresDriveAuthorization: Boolean

    suspend fun upload(
        envelopeBytes: ByteArray,
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Unit>

    suspend fun download(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<ByteArray>

    suspend fun deleteAll(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Int>
}

internal class DriveCloudRecoveryTransport(
    private val driveClient: DriveAppDataClient = DriveAppDataClient(),
) : CloudRecoveryTransport {
    override val kind: CloudRecoveryTransportKind = CloudRecoveryTransportKind.DriveAppData
    override val requiresDriveAuthorization: Boolean = true

    override suspend fun upload(
        envelopeBytes: ByteArray,
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Unit> = withDriveOutcome {
        val accessToken = driveAccessToken.requireDriveAccessToken()
        val files = driveClient.findByName(accessToken, CloudRecoveryDriveFileName)
        if (files.isEmpty()) {
            driveClient.create(
                accessToken = accessToken,
                fileName = CloudRecoveryDriveFileName,
                contentType = CloudRecoveryDriveContentType,
                bytes = envelopeBytes,
            )
        } else {
            driveClient.updateContent(
                accessToken = accessToken,
                fileId = files.first().id,
                contentType = CloudRecoveryDriveContentType,
                bytes = envelopeBytes,
            )
        }
        Unit
    }

    override suspend fun download(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<ByteArray> = withDriveOutcome {
        val accessToken = driveAccessToken.requireDriveAccessToken()
        val file = driveClient.findByName(accessToken, CloudRecoveryDriveFileName)
            .firstOrNull() ?: throw DriveFileNotFoundException
        driveClient.download(
            accessToken = accessToken,
            fileId = file.id,
            maxBytes = CloudRecoveryMaxEnvelopeBytes,
        )
    }

    override suspend fun deleteAll(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Int> = withDriveOutcome {
        val accessToken = driveAccessToken.requireDriveAccessToken()
        val files = driveClient.findByName(accessToken, CloudRecoveryDriveFileName)
        files.forEach { file -> driveClient.delete(accessToken, file.id) }
        files.size
    }

    private suspend fun <T> withDriveOutcome(
        operation: suspend () -> T,
    ): CloudRecoveryTransportOutcome<T> = try {
        CloudRecoveryTransportOutcome.Success(operation())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (notFound: DriveFileNotFoundException) {
        CloudRecoveryTransportOutcome.NotFound
    } catch (error: DriveAppDataHttpException.Unauthorized) {
        CloudRecoveryTransportOutcome.AuthorizationRequired
    } catch (error: DriveAppDataHttpException.Forbidden) {
        CloudRecoveryTransportOutcome.AuthorizationRequired
    } catch (error: DriveAppDataHttpException.NotFound) {
        CloudRecoveryTransportOutcome.NotFound
    } catch (error: DriveAppDataHttpException.RateLimited) {
        CloudRecoveryTransportOutcome.RetryableFailure(error)
    } catch (error: DriveAppDataHttpException.RetryableServerError) {
        CloudRecoveryTransportOutcome.RetryableFailure(error)
    } catch (error: Throwable) {
        CloudRecoveryTransportOutcome.PermanentFailure(error)
    }
}

private object DriveFileNotFoundException : IllegalStateException()

private fun String?.requireDriveAccessToken(): String =
    this?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Drive transport requires an access token.")