package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.RestoreBundleImporter
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject

public class CloudRecoveryRestoreCoordinator(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val transportProvider =
        DefaultCloudRecoveryTransportProvider()

    private val crypto =
        CloudRecoveryCrypto()

    private val database =
        AppDatabase.getInstance(appContext)

    private val importer =
        RestoreBundleImporter(
            appContext,
            database,
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

    private val ownershipFinalizer =
        CloudRestoreOwnershipFinalizer(
            appContext,
        )

    private val onboardingPreferences =
        OnboardingPreferencesDataSource(
            appContext,
        )

    private val activationSessionProvider =
        CloudRestoreFirebaseAccountProvider(
            FirebaseAuth.getInstance(),
        )

    private val pendingAuthorizationStore =
        AndroidPendingCloudRestoreAuthorizationStore(appContext)

    private val postImportRecoveryCoordinator =
        CloudRestorePostImportRecoveryCoordinator(appContext)

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

    public suspend fun discover(
        driveAccessToken: String?,
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

            when (
                val outcome =
                    downloadCloudRecoveryEnvelope(
                        hasGoogleProvider =
                            user.providerData.any { info ->
                                info.providerId == GoogleAuthProvider.PROVIDER_ID
                            },
                        driveAccessToken = driveAccessToken,
                        transportProvider = transportProvider,
                    )
            ) {
                is CloudRecoveryTransportOutcome.Success ->
                    CloudRecoveryRestoreDiscovery.Downloaded(
                        bytes = outcome.value,
                        requiresReplacementConfirmation =
                            importer.hasExistingUserData(),
                    )

                CloudRecoveryTransportOutcome.NotFound ->
                    CloudRecoveryRestoreDiscovery.NoBackupFound

                CloudRecoveryTransportOutcome.AuthorizationRequired ->
                    CloudRecoveryRestoreDiscovery.AuthorizationRequired

                is CloudRecoveryTransportOutcome.RetryableFailure ->
                    CloudRecoveryRestoreDiscovery.TemporarilyUnavailable

                is CloudRecoveryTransportOutcome.PermanentFailure ->
                    if (outcome.cause is IllegalArgumentException) {
                        CloudRecoveryRestoreDiscovery.InvalidBackup
                    } else {
                        CloudRecoveryRestoreDiscovery.Failed
                    }
            }
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            error:
                DriveAppDataHttpException.Unauthorized,
        ) {
            CloudRecoveryRestoreDiscovery.AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException.Forbidden,
        ) {
            CloudRecoveryRestoreDiscovery.AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException.NotFound,
        ) {
            CloudRecoveryRestoreDiscovery.NoBackupFound
        } catch (
            error:
                DriveAppDataHttpException.RateLimited,
        ) {
            CloudRecoveryRestoreDiscovery.TemporarilyUnavailable
        } catch (
            error:
                DriveAppDataHttpException.RetryableServerError,
        ) {
            CloudRecoveryRestoreDiscovery.TemporarilyUnavailable
        } catch (
            error:
                IOException,
        ) {
            CloudRecoveryRestoreDiscovery.TemporarilyUnavailable
        } catch (
            error:
                IllegalArgumentException,
        ) {
            CloudRecoveryRestoreDiscovery.InvalidBackup
        } catch (
            error:
                Throwable,
        ) {
            CloudRecoveryRestoreDiscovery.Failed
        }
    }
    public suspend fun restore(
        downloadedEnvelope: ByteArray,
        password: CharArray,
        replaceExistingData: Boolean,
        ownerConfirmation: CloudRecoveryOwnerConfirmation = CloudRecoveryOwnerConfirmation.None,
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
                val parsedPayload =
                    try {
                        JSONObject(
                            decrypted.recovery.payloadJson,
                        )
                    } catch (_: Exception) {
                        return CloudRecoveryRestoreResult.InvalidBackup
                    }
                val onboardingSnapshot =
                    when (
                        val decoded =
                            CloudRecoveryOnboardingSnapshotCodec.decode(
                                parsedPayload,
                            )
                    ) {
                        CloudRecoveryOnboardingSnapshotDecodeResult.Missing ->
                            null
                        CloudRecoveryOnboardingSnapshotDecodeResult.Malformed ->
                            return CloudRecoveryRestoreResult.InvalidBackup
                        is CloudRecoveryOnboardingSnapshotDecodeResult.Success ->
                            decoded.snapshot
                    }

                val currentGoogleSubjectHash =
                    resolveGoogleAccountIdentity(user)?.subjectHash

                val ownerVerdict =
                    cloudRecoveryOwnerVerdict(
                        ownerUid = decrypted.recovery.ownerUid,
                        ownerGoogleSubjectHash = decrypted.recovery.ownerGoogleSubjectHash,
                        currentFirebaseUid = user.uid,
                        currentGoogleSubjectHash = currentGoogleSubjectHash,
                    )

                when (
                    val authorization =
                        cloudRecoveryOwnerAuthorization(
                            verdict = ownerVerdict,
                            confirmation = ownerConfirmation,
                        )
                ) {
                    CloudRecoveryOwnerAuthorization.Authorized -> Unit
                    CloudRecoveryOwnerAuthorization.Blocked ->
                        return CloudRecoveryRestoreResult.AccountMismatch
                    is CloudRecoveryOwnerAuthorization.ConfirmationRequired ->
                        return CloudRecoveryRestoreResult
                            .OwnerMigrationConfirmationRequired(
                                authorization.kind,
                            )
                }

                val ownerProof =
                    when (ownerVerdict) {
                        CloudRecoveryOwnerVerdict.ExactUidMatch ->
                            VerifiedCloudRestoreOwnerProof.ExactUid(
                                currentUid = user.uid,
                            )
                        CloudRecoveryOwnerVerdict
                            .SameGoogleIdentityNewFirebaseUid ->
                            VerifiedCloudRestoreOwnerProof
                                .SameGoogleIdentity(
                                    previousUid =
                                        decrypted.recovery.ownerUid,
                                    previousGoogleSubjectHash =
                                        requireNotNull(
                                            decrypted.recovery
                                                .ownerGoogleSubjectHash,
                                        ),
                                    currentUid = user.uid,
                                    currentGoogleSubjectHash =
                                        requireNotNull(
                                            currentGoogleSubjectHash,
                                        ),
                                )
                        CloudRecoveryOwnerVerdict.LegacyEnvelope ->
                            VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                                previousUid = decrypted.recovery.ownerUid,
                                currentUid = user.uid,
                                currentGoogleSubjectHash =
                                    currentGoogleSubjectHash,
                            )
                        CloudRecoveryOwnerVerdict.DifferentAccount ->
                            error(
                                "Different-account verdict must be blocked before import.",
                            )
                    }

                val pendingAuthorization =
                    try {
                        if (
                            database.cloudRestoreReceiptDao().latest() !=
                            null
                        ) {
                            return CloudRecoveryRestoreResult
                                .RestoredButOwnershipFinalizationPending
                        }
                        pendingAuthorizationStore.read()?.let {
                            pendingAuthorizationStore.clear()
                        }
                        PendingCloudRestoreAuthorization(
                            receiptId = UUID.randomUUID().toString(),
                            payloadSha256 =
                                decrypted.recovery.payloadJson
                                    .cloudRestoreSha256(),
                            proofType = ownerProof.proofType(),
                            previousUid = ownerProof.previousUid(),
                            previousGoogleSubjectHash =
                                ownerProof.previousGoogleSubjectHash(),
                            currentUid = user.uid,
                            currentGoogleSubjectHash =
                                currentGoogleSubjectHash,
                            authorisedAtMillis =
                                System.currentTimeMillis(),
                        ).also(pendingAuthorizationStore::write)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        return CloudRecoveryRestoreResult.ImportFailed
                    }

                if (
                    !ownerProof.matchesCurrentSession(
                        activationSessionProvider.currentAccount(),
                    )
                ) {
                    return CloudRecoveryRestoreResult
                        .RestoredButOwnershipFinalizationPending
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
                            parsed = parsedPayload,

                            mode =
                                mode,
                            cloudRestoreReceipt =
                                pendingAuthorization.toReceipt(
                                    importedAtMillis =
                                        System.currentTimeMillis(),
                                ),
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
                    try {
                        pendingAuthorizationStore.clear()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        return CloudRecoveryRestoreResult.ImportFailed
                    }
                    return CloudRecoveryRestoreResult
                            .ReplacementConfirmationRequired
                }

                /*
                 * The database import has now committed successfully.
                 *
                 * Any later local cloud-credential failure must not claim the
                 * imported data remained unchanged.
                 */
                restoreCloudRecoveryOnboardingAfterCommittedImport(
                    snapshot = onboardingSnapshot,
                    currentUid = user.uid,
                    currentGoogleSubjectHash = currentGoogleSubjectHash,
                    persist = { answers, accountUid, googleSubjectHash ->
                        onboardingPreferences
                            .restoreCompletedSnapshotForAccount(
                                answers = answers,
                                accountUid = accountUid,
                                googleSubjectHash = googleSubjectHash,
                            )
                    },
                )
                val completion =
                    finalizeThenActivateRestoredCloudRecovery(
                        rawDek = decrypted.rawDek,
                        ownerProof = ownerProof,
                        finalizeOwnership = { proof ->
                            ownershipFinalizer
                                .finalizeAfterVerifiedCloudRestore(
                                    proof = proof,
                                    deferProvenanceCleanup = true,
                                )
                        },
                        currentSession = {
                            activationSessionProvider.currentAccount()
                        },
                        activateCloudRecovery = {
                            activateRestoredCloudRecovery(
                                rawDek = decrypted.rawDek,
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
                        },
                    )
                if (
                    completion ==
                    CloudRecoveryRestoreResult
                        .RestoredButOwnershipFinalizationPending ||
                    completion ==
                    CloudRecoveryRestoreResult
                        .SuccessRequiresOnboardingSetup ||
                    completion ==
                    CloudRecoveryRestoreResult
                        .SuccessRequiresOnboardingSetupCloudRecoverySetupFailed
                ) {
                    return completion
                }
                return if (
                    postImportRecoveryCoordinator
                        .cleanupCommittedReceipt(
                            pendingAuthorization.receiptId,
                        )
                ) {
                    completion
                } else {
                    CloudRecoveryRestoreResult
                        .RestoredButOwnershipFinalizationPending
                }
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

internal suspend fun restoreCloudRecoveryOnboardingAfterCommittedImport(
    snapshot: CloudRecoveryOnboardingSnapshot?,
    currentUid: String,
    currentGoogleSubjectHash: String?,
    persist:
        suspend (
            answers: OnboardingAnswers,
            accountUid: String,
            googleSubjectHash: String?,
        ) -> Unit,
): Boolean {
    if (snapshot == null) return false
    return try {
        persist(
            snapshot.answers,
            currentUid,
            currentGoogleSubjectHash,
        )
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}

private fun VerifiedCloudRestoreOwnerProof.proofType():
    CloudRestoreProofType =
    when (this) {
        is VerifiedCloudRestoreOwnerProof.ExactUid ->
            CloudRestoreProofType.ExactUid
        is VerifiedCloudRestoreOwnerProof.SameGoogleIdentity ->
            CloudRestoreProofType.SameGoogleIdentity
        is VerifiedCloudRestoreOwnerProof.LegacyEnvelope ->
            CloudRestoreProofType.LegacyEnvelope
    }

private fun VerifiedCloudRestoreOwnerProof.previousUid(): String? =
    when (this) {
        is VerifiedCloudRestoreOwnerProof.ExactUid -> null
        is VerifiedCloudRestoreOwnerProof.SameGoogleIdentity ->
            previousUid
        is VerifiedCloudRestoreOwnerProof.LegacyEnvelope ->
            previousUid
    }

private fun VerifiedCloudRestoreOwnerProof.previousGoogleSubjectHash():
    String? =
    when (this) {
        is VerifiedCloudRestoreOwnerProof.ExactUid -> null
        is VerifiedCloudRestoreOwnerProof.SameGoogleIdentity ->
            previousGoogleSubjectHash
        is VerifiedCloudRestoreOwnerProof.LegacyEnvelope -> null
    }

private fun String.cloudRestoreSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

internal suspend fun finalizeThenActivateRestoredCloudRecovery(
    rawDek: ByteArray,
    ownerProof: VerifiedCloudRestoreOwnerProof,
    finalizeOwnership:
        suspend (VerifiedCloudRestoreOwnerProof) ->
            CloudRestoreOwnershipFinalizationResult,
    currentSession: () -> CloudRestoreOwnershipAccount?,
    activateCloudRecovery: suspend () -> CloudRecoveryRestoreResult,
): CloudRecoveryRestoreResult {
    try {
        val ownershipFinalization =
            finalizeOwnership(ownerProof)
        if (
            ownershipFinalization ==
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        ) {
            return CloudRecoveryRestoreResult
                .RestoredButOwnershipFinalizationPending
        }
        if (!ownerProof.matchesCurrentSession(currentSession())) {
            return CloudRecoveryRestoreResult
                .RestoredButOwnershipFinalizationPending
        }
        return combineCloudRestoreCompletion(
            ownershipFinalization = ownershipFinalization,
            activation = activateCloudRecovery(),
        )
    } finally {
        rawDek.fill(0)
    }
}

internal fun combineCloudRestoreCompletion(
    ownershipFinalization: CloudRestoreOwnershipFinalizationResult,
    activation: CloudRecoveryRestoreResult,
): CloudRecoveryRestoreResult =
    when (ownershipFinalization) {
        CloudRestoreOwnershipFinalizationResult.Success -> activation
        CloudRestoreOwnershipFinalizationResult.SuccessBackupRefreshPending ->
            if (
                activation ==
                CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed
            ) {
                activation
            } else {
                CloudRecoveryRestoreResult.SuccessBackupRefreshPending
            }
        CloudRestoreOwnershipFinalizationResult.SuccessRequiresOnboardingSetup ->
            if (
                activation ==
                CloudRecoveryRestoreResult
                    .RestoredButCloudRecoverySetupFailed
            ) {
                CloudRecoveryRestoreResult
                    .SuccessRequiresOnboardingSetupCloudRecoverySetupFailed
            } else {
                CloudRecoveryRestoreResult.SuccessRequiresOnboardingSetup
            }
        CloudRestoreOwnershipFinalizationResult
            .RestoredButOwnershipFinalizationPending ->
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending
    }

internal suspend fun downloadCloudRecoveryEnvelope(
    hasGoogleProvider: Boolean,
    driveAccessToken: String?,
    transportProvider: CloudRecoveryUploadTransportProvider,
): CloudRecoveryTransportOutcome<ByteArray> {
    val primaryTransport =
        transportProvider.transportFor(
            cloudRecoveryTransportKind(
                hasGoogleProvider,
            ),
        )

    val primaryOutcome =
        primaryTransport.download(
            if (primaryTransport.requiresDriveAuthorization) {
                driveAccessToken
            } else {
                null
            },
        )

    if (
        hasGoogleProvider &&
            primaryOutcome == CloudRecoveryTransportOutcome.NotFound
    ) {
        val storageTransport =
            transportProvider.transportFor(
                CloudRecoveryTransportKind.FirebaseStorage,
            )

        return storageTransport.download(null)
    }

    return primaryOutcome
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

public sealed interface CloudRecoveryOwnerConfirmation {
    public data object None : CloudRecoveryOwnerConfirmation
    public data object ConfirmedSameGoogleIdentity : CloudRecoveryOwnerConfirmation
    public data object ConfirmedLegacyEnvelope : CloudRecoveryOwnerConfirmation
}

public enum class CloudRecoveryOwnerConfirmationKind {
    SameGoogleIdentity,
    LegacyEnvelope,
}

public sealed interface CloudRecoveryRestoreResult {
    public data object Success :
        CloudRecoveryRestoreResult

    public data object SuccessBackupRefreshPending :
        CloudRecoveryRestoreResult

    public data object RestoredButCloudRecoverySetupFailed :
        CloudRecoveryRestoreResult

    public data object SuccessRequiresOnboardingSetup :
        CloudRecoveryRestoreResult

    public data object SuccessRequiresOnboardingSetupCloudRecoverySetupFailed :
        CloudRecoveryRestoreResult

    public data object RestoredButOwnershipFinalizationPending :
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
        val kind: CloudRecoveryOwnerConfirmationKind,
    ) : CloudRecoveryRestoreResult

    public data object ReplacementConfirmationRequired :
        CloudRecoveryRestoreResult

    public data object ImportFailed :
        CloudRecoveryRestoreResult
}
