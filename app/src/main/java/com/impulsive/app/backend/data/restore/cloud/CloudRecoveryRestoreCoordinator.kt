package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.RestoreBundleImporter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject

public class CloudRecoveryRestoreCoordinator(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val driveClient =
        DriveAppDataClient()

    private val crypto =
        CloudRecoveryCrypto()

    private val importer =
        RestoreBundleImporter(
            appContext,
        )

    private val keyStore =
        CloudRecoveryLocalKeyStore(
            appContext,
        )

    private val metadataStore =
        CloudRecoveryLocalMetadataStore(
            appContext,
        )

    private val preferences =
        CloudRecoveryPreferencesDataSource(
            appContext,
        )

    public suspend fun discover(
        accessToken: String,
    ): CloudRecoveryRestoreDiscovery {
        return try {
            val user =
                FirebaseAuth
                    .getInstance()
                    .currentUser
                    ?: return CloudRecoveryRestoreDiscovery
                            .NotSignedIn

            if (
                user.isAnonymous
            ) {
                return CloudRecoveryRestoreDiscovery
                        .GuestNotSupported
            }

            val files =
                driveClient.findByName(
                    accessToken =
                        accessToken,

                    fileName =
                        CloudRecoveryDriveFileName,
                )

            if (
                files.isEmpty()
            ) {
                return CloudRecoveryRestoreDiscovery
                        .NoBackupFound
            }

            val bytes =
                driveClient.download(
                    accessToken =
                        accessToken,

                    fileId =
                        files.first().id,

                    maxBytes =
                        CloudRecoveryMaxEnvelopeBytes,
                )

            CloudRecoveryRestoreDiscovery.Downloaded(
                bytes =
                    bytes,

                requiresReplacementConfirmation =
                    importer.hasExistingUserData(),
            )
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            error:
                DriveAppDataHttpException.Unauthorized,
        ) {
            CloudRecoveryRestoreDiscovery
                .AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException.Forbidden,
        ) {
            CloudRecoveryRestoreDiscovery
                .AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException.NotFound,
        ) {
            CloudRecoveryRestoreDiscovery
                .NoBackupFound
        } catch (
            error:
                DriveAppDataHttpException.RateLimited,
        ) {
            CloudRecoveryRestoreDiscovery
                .TemporarilyUnavailable
        } catch (
            error:
                DriveAppDataHttpException.RetryableServerError,
        ) {
            CloudRecoveryRestoreDiscovery
                .TemporarilyUnavailable
        } catch (
            error:
                IOException,
        ) {
            CloudRecoveryRestoreDiscovery
                .TemporarilyUnavailable
        } catch (
            error:
                IllegalArgumentException,
        ) {
            CloudRecoveryRestoreDiscovery
                .InvalidBackup
        } catch (
            error:
                Throwable,
        ) {
            CloudRecoveryRestoreDiscovery
                .Failed
        }
    }

    public suspend fun restore(
        downloadedEnvelope: ByteArray,
        password: CharArray,
        replaceExistingData: Boolean,
        ownerMigrationConfirmed: Boolean = false,
    ): CloudRecoveryRestoreResult {
        try {
            val user =
                FirebaseAuth
                    .getInstance()
                    .currentUser
                    ?: return CloudRecoveryRestoreResult
                            .NotSignedIn

            if (
                user.isAnonymous
            ) {
                return CloudRecoveryRestoreResult
                        .GuestNotSupported
            }

            val decrypted =
                when (
                    val result =
                        crypto.decryptForRestore(
                            downloadedEnvelope,
                            password,
                        )
                ) {
                    is CloudRecoveryRestoreDecryptResult.Success ->
                        result.restoredRecovery

                    CloudRecoveryRestoreDecryptResult.CryptoFailure ->
                        return CloudRecoveryRestoreResult
                                .IncorrectPassword

                    CloudRecoveryRestoreDecryptResult.Malformed,
                    CloudRecoveryRestoreDecryptResult.UnsupportedVersion ->
                        return CloudRecoveryRestoreResult
                                .InvalidBackup
                }

            try {
                when (
                    cloudRecoveryOwnerVerdict(
                        ownerUid = decrypted.recovery.ownerUid,
                        ownerGoogleSubjectHash = decrypted.recovery.ownerGoogleSubjectHash,
                        currentFirebaseUid = user.uid,
                        currentGoogleSubjectHash = resolveGoogleAccountIdentity(user)?.subjectHash,
                    )
                ) {
                    CloudRecoveryOwnerVerdict.ExactUidMatch -> Unit
                    CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid ->
                        if (!ownerMigrationConfirmed) {
                            return CloudRecoveryRestoreResult.OwnerMigrationConfirmationRequired(
                                legacyEnvelope = false,
                            )
                        }
                    CloudRecoveryOwnerVerdict.LegacyEnvelope ->
                        if (!ownerMigrationConfirmed) {
                            return CloudRecoveryRestoreResult.OwnerMigrationConfirmationRequired(
                                legacyEnvelope = true,
                            )
                        }
                    CloudRecoveryOwnerVerdict.DifferentAccount ->
                        return CloudRecoveryRestoreResult.AccountMismatch
                }

                val mode =
                    if (
                        replaceExistingData
                    ) {
                        RestoreBundleImporter
                            .ImportMode
                            .ReplaceRestoreBundleData
                    } else {
                        RestoreBundleImporter
                            .ImportMode
                            .RejectIfExistingData
                    }

                val importOutcome =
                    try {
                        importer.importPayload(
                            parsed =
                                JSONObject(
                                    decrypted
                                        .recovery
                                        .payloadJson,
                                ),

                            mode =
                                mode,
                        )
                    } catch (
                        cancellation:
                            CancellationException,
                    ) {
                        throw cancellation
                    } catch (
                        error:
                            Exception,
                    ) {
                        return CloudRecoveryRestoreResult
                                .ImportFailed
                    }

                if (
                    importOutcome ==
                    RestoreBundleImporter
                        .ImportOutcome
                        .ExistingDataPresent
                ) {
                    return CloudRecoveryRestoreResult
                            .ReplacementConfirmationRequired
                }

                /*
                 * The database import has now committed successfully.
                 *
                 * Any later local cloud-credential failure must not claim the
                 * imported data remained unchanged.
                 */
                return activateRestoredCloudRecovery(

rawDek =
                        decrypted.rawDek,

                    wrappedKeyMetadata =
                        decrypted.wrappedKeyMetadata,

                    keyStore =
                        LocalCloudRecoveryRestoreKeyStore(
                            keyStore,
                        ),

                    metadataStore =
                        LocalCloudRecoveryRestoreMetadataStore(
                            metadataStore,
                        ),

                    preferences =
                        DataStoreCloudRecoveryRestorePreferences(
                            preferences,
                        ),

                    scheduler =
                        WorkManagerCloudRecoveryRestoreScheduler(
                            appContext,
                        ),
                )
            } finally {
                decrypted.rawDek.fill(
                    0,
                )
            }
        } finally {
            password.fill(
                '\u0000',
            )
        }
    }
}

internal interface CloudRecoveryRestoreKeyStore {
    fun store(rawDek: ByteArray)
    fun clear()
}

internal interface CloudRecoveryRestoreMetadataStore {
    fun store(metadata: WrappedKeyMetadata)
    fun clear()
}

internal interface CloudRecoveryRestorePreferences {
    suspend fun setEnabled(enabled: Boolean)
}

internal interface CloudRecoveryRestoreScheduler {
    fun request()
    fun cancel()
}

internal suspend fun activateRestoredCloudRecovery(
rawDek: ByteArray,
    wrappedKeyMetadata: WrappedKeyMetadata,
    keyStore: CloudRecoveryRestoreKeyStore,
    metadataStore: CloudRecoveryRestoreMetadataStore,
    preferences: CloudRecoveryRestorePreferences,
    scheduler: CloudRecoveryRestoreScheduler,
): CloudRecoveryRestoreResult =
    withContext(
        NonCancellable,
    ) {
        try {
            keyStore.store(
                rawDek,
            )

            metadataStore.store(
                wrappedKeyMetadata,
            )

            preferences.setEnabled(
                true,
            )
        } catch (
            cancellation: CancellationException,
        ) {
            throw cancellation
        } catch (
            error: Exception,
        ) {
            runCatching {
                scheduler.cancel()
            }

            runCatching {
                keyStore.clear()
            }

            runCatching {
                metadataStore.clear()
            }

            try {
                preferences.setEnabled(
                    false,
                )
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (
                ignored: Exception,
            ) {
                // RestoreBundle data has already committed.
            }

            return@withContext CloudRecoveryRestoreResult
                    .RestoredButCloudRecoverySetupFailed
        }

        try {
            scheduler.request()

            CloudRecoveryRestoreResult.Success
        } catch (
            cancellation: CancellationException,
        ) {
            throw cancellation
        } catch (
            error: Exception,
        ) {
            /*
             * Valid restored keys and enabled state must survive a WorkManager
             * enqueue failure. Later data changes or Backup now can request
             * another upload.
             */
            CloudRecoveryRestoreResult
                .SuccessBackupRefreshPending
        }
    }

private class LocalCloudRecoveryRestoreKeyStore(
    private val delegate: CloudRecoveryLocalKeyStore,
) : CloudRecoveryRestoreKeyStore {
    override fun store(rawDek: ByteArray) = delegate.store(rawDek)
    override fun clear() = delegate.clear()
}

private class LocalCloudRecoveryRestoreMetadataStore(
    private val delegate: CloudRecoveryLocalMetadataStore,
) : CloudRecoveryRestoreMetadataStore {
    override fun store(metadata: WrappedKeyMetadata) = delegate.store(metadata)
    override fun clear() = delegate.clear()
}

private class DataStoreCloudRecoveryRestorePreferences(
    private val delegate: CloudRecoveryPreferencesDataSource,
) : CloudRecoveryRestorePreferences {
    override suspend fun setEnabled(enabled: Boolean) = delegate.setEnabled(enabled)
}

private class WorkManagerCloudRecoveryRestoreScheduler(
    private val context: Context,
) : CloudRecoveryRestoreScheduler {
    override fun request() = CloudRecoveryUploadScheduler.request(context)
    override fun cancel() = CloudRecoveryUploadScheduler.cancel(context)
}

public sealed interface CloudRecoveryRestoreDiscovery {
    public data object NotSignedIn :
        CloudRecoveryRestoreDiscovery

    public data object GuestNotSupported :
        CloudRecoveryRestoreDiscovery

    public data object NoBackupFound :
        CloudRecoveryRestoreDiscovery

    public data object AuthorizationRequired :
        CloudRecoveryRestoreDiscovery

    public data object TemporarilyUnavailable :
        CloudRecoveryRestoreDiscovery

    public data object InvalidBackup :
        CloudRecoveryRestoreDiscovery

    public data object Failed :
        CloudRecoveryRestoreDiscovery

    public data class Downloaded(
        val bytes: ByteArray,
        val requiresReplacementConfirmation: Boolean,
    ) : CloudRecoveryRestoreDiscovery
}

public sealed interface CloudRecoveryRestoreResult {
    public data object Success :
        CloudRecoveryRestoreResult

    public data object SuccessBackupRefreshPending :
        CloudRecoveryRestoreResult

    public data object RestoredButCloudRecoverySetupFailed :
        CloudRecoveryRestoreResult

    public data object NotSignedIn :
        CloudRecoveryRestoreResult

    public data object GuestNotSupported :
        CloudRecoveryRestoreResult

    public data object IncorrectPassword :
        CloudRecoveryRestoreResult

    public data object InvalidBackup :
        CloudRecoveryRestoreResult

    public data object AccountMismatch :
        CloudRecoveryRestoreResult

    public data class OwnerMigrationConfirmationRequired(
        val legacyEnvelope: Boolean,
    ) : CloudRecoveryRestoreResult

    public data object ReplacementConfirmationRequired :
        CloudRecoveryRestoreResult

    public data object ImportFailed :
        CloudRecoveryRestoreResult
}