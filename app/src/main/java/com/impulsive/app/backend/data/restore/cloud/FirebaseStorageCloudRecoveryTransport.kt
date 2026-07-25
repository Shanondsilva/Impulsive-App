package com.impulsive.app.backend.data.restore.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal const val CloudRecoveryStorageRootPath = "cloud_recovery"

internal fun cloudRecoveryStoragePath(uid: String): String {
    require(uid.isNotBlank()) { "Firebase UID is required for cloud recovery storage." }
    return "$CloudRecoveryStorageRootPath/$uid/$CloudRecoveryDriveFileName"
}

/**
 * Stores recovery envelopes under the current Firebase UID.
 *
 * If a non-Google account is deleted, Firebase creates a new UID on a later sign-in and this
 * path cannot be listed or recovered. This intentionally avoids unsafe email-based recovery.
 */
internal class FirebaseStorageCloudRecoveryTransport(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firebaseStorage: FirebaseStorage = FirebaseStorage.getInstance(),
) : CloudRecoveryTransport {
    override val kind: CloudRecoveryTransportKind = CloudRecoveryTransportKind.FirebaseStorage
    override val requiresDriveAuthorization: Boolean = false

    override suspend fun upload(
        envelopeBytes: ByteArray,
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Unit> = withStorageOutcome {
        referenceForCurrentAccount().putBytes(envelopeBytes).await()
        Unit
    }

    override suspend fun download(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<ByteArray> = withStorageOutcome {
        referenceForCurrentAccount().getBytes(CloudRecoveryMaxEnvelopeBytes.toLong()).await()
    }

    override suspend fun deleteAll(
        driveAccessToken: String?,
    ): CloudRecoveryTransportOutcome<Int> = withStorageOutcome {
        referenceForCurrentAccount().delete().await()
        1
    }

    private fun referenceForCurrentAccount() = firebaseStorage.reference.child(
        cloudRecoveryStoragePath(
            firebaseAuth.currentUser
                ?.takeUnless { user -> user.isAnonymous }
                ?.uid
                ?: throw IllegalStateException("Firebase Storage recovery requires an authenticated non-guest account."),
        ),
    )

    private suspend fun <T> withStorageOutcome(
        operation: suspend () -> T,
    ): CloudRecoveryTransportOutcome<T> = try {
        CloudRecoveryTransportOutcome.Success(operation())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: StorageException) {
        when (error.errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> CloudRecoveryTransportOutcome.NotFound
            StorageException.ERROR_NOT_AUTHENTICATED,
            StorageException.ERROR_NOT_AUTHORIZED,
            -> CloudRecoveryTransportOutcome.AuthorizationRequired
            StorageException.ERROR_QUOTA_EXCEEDED,
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
            StorageException.ERROR_UNKNOWN,
            -> CloudRecoveryTransportOutcome.RetryableFailure(error)
            else -> CloudRecoveryTransportOutcome.PermanentFailure(error)
        }
    } catch (error: Throwable) {
        CloudRecoveryTransportOutcome.PermanentFailure(error)
    }
}