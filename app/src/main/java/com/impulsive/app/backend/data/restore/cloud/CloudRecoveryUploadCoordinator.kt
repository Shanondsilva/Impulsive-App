package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryStoredUploadOutcome
import com.impulsive.app.backend.data.restore.RestoreBundleWriter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

public class CloudRecoveryUploadCoordinator internal constructor(
    private val enabledStateProvider: CloudRecoveryUploadEnabledStateProvider,
    private val accountProvider: CloudRecoveryUploadAccountProvider,
    private val ownerStateDataSource: CloudRecoveryUploadOwnerStateDataSource,
    private val payloadProvider: CloudRecoveryUploadPayloadProvider,
    private val keyMaterialSource: CloudRecoveryUploadKeyMaterialSource,
    private val authorizationProvider: CloudRecoveryUploadAuthorizationProvider,
    private val transportProvider: CloudRecoveryUploadTransportProvider,
    private val envelopeEncryptor: CloudRecoveryUploadEnvelopeEncryptor,
    private val clock: CloudRecoveryUploadClock,
    private val statusRecorder: CloudRecoveryUploadStatusRecorder,
) {
    public constructor(
        context: Context,
    ) : this(
        enabledStateProvider =
            DataStoreCloudRecoveryUploadEnabledStateProvider(
                CloudRecoveryPreferencesDataSource(
                    context.applicationContext,
                ),
            ),

        accountProvider =
            FirebaseCloudRecoveryUploadAccountProvider(),

        ownerStateDataSource =
            PreferencesCloudRecoveryUploadOwnerStateDataSource(
                OnboardingPreferencesDataSource(
                    context.applicationContext,
                ),
            ),

        payloadProvider =
            RestoreBundleCloudRecoveryUploadPayloadProvider(
                RestoreBundleWriter(
                    context.applicationContext,
                ),
            ),

        keyMaterialSource =
            LocalCloudRecoveryUploadKeyMaterialSource(
                keyStore =
                    CloudRecoveryLocalKeyStore(
                        context.applicationContext,
                    ),

                metadataStore =
                    CloudRecoveryLocalMetadataStore(
                        context.applicationContext,
                    ),
            ),

        authorizationProvider =
            IdentityCloudRecoveryUploadAuthorizationProvider(
                DriveAppDataAuthorization(
                    context.applicationContext,
                ),
            ),
        transportProvider = DefaultCloudRecoveryTransportProvider(),

        envelopeEncryptor =
            CryptoCloudRecoveryUploadEnvelopeEncryptor(
                CloudRecoveryCrypto(),
            ),

        clock =
            SystemCloudRecoveryUploadClock,

        statusRecorder =
            DataStoreCloudRecoveryUploadStatusRecorder(
                CloudRecoveryPreferencesDataSource(
                    context.applicationContext,
                ),
            ),
    )

    public suspend fun uploadCurrentRecovery():
        CloudRecoveryUploadResult =
        performUpload(
            requireEnabled = true,
        )

    internal suspend fun uploadCurrentRecoveryForSetup():
        CloudRecoveryUploadResult =
        performUpload(
            requireEnabled = false,
        )

    private suspend fun performUpload(
        requireEnabled: Boolean,
    ): CloudRecoveryUploadResult {
        if (
            requireEnabled &&
            !enabledStateProvider.isEnabled()
        ) {
            return CloudRecoveryUploadResult.Disabled
        }

        val attemptEpochMillis =
            clock.currentTimeMillis()

        statusRecorder.recordAttempt(
            attemptEpochMillis,
        )

        val result =
            executeUpload()

        val outcomeEpochMillis =
            if (result == CloudRecoveryUploadResult.Uploaded) {
                clock.currentTimeMillis()
            } else {
                attemptEpochMillis
            }

        statusRecorder.recordOutcome(
            result.toStoredOutcome(),
            outcomeEpochMillis,
        )

        return result
    }

    private suspend fun executeUpload(): CloudRecoveryUploadResult {
        return try {
            val account =
                accountProvider.currentAccount()
                    ?: return CloudRecoveryUploadResult
                            .NoAuthenticatedAccount

            if (account.isAnonymous) {
                return CloudRecoveryUploadResult
                        .GuestNotApplicable
            }
            val googleSubjectHash = account.googleSubjectHash

            val isCompleted =
                ownerStateDataSource
                    .isCompleted
                    .first()
            val completedOwnerUid =
                ownerStateDataSource
                    .completedAccountUid
                    .first()

            if (
                !isCompleted ||
                completedOwnerUid == null
            ) {
                return CloudRecoveryUploadResult
                        .NoOwnedCompletedData
            }

            if (
                completedOwnerUid !=
                account.uid
            ) {
                return CloudRecoveryUploadResult
                        .AccountMismatch
            }

            val dek =
                keyMaterialSource
                    .loadDek()
                    ?: return CloudRecoveryUploadResult
                            .SetupRequired

            try {
                val wrappedKeyMetadata =
                    keyMaterialSource
                        .loadWrappedKeyMetadata()
                        ?: return CloudRecoveryUploadResult
                                .SetupRequired

                val transport =
                    transportProvider
                        .transportFor(
                            cloudRecoveryTransportKind(
                                account.hasGoogleProvider,
                            ),
                        )

                val accessToken =
                    if (transport.requiresDriveAuthorization) {
                        when (
                            val authorization =
                                authorizationProvider
                                    .requestAuthorization()
                        ) {
                            is DriveAuthorizationResult.Authorized ->
                                authorization.accessToken

                            is DriveAuthorizationResult.NeedsUserResolution ->
                                return CloudRecoveryUploadResult
                                        .AuthorizationRequired

                            DriveAuthorizationResult.Cancelled ->
                                return CloudRecoveryUploadResult
                                        .Cancelled

                            is DriveAuthorizationResult.Failed ->
                                return CloudRecoveryUploadResult
                                        .PermanentFailure(
                                            authorization.cause
                                                ?: IllegalStateException(
                                                    "Drive appDataFolder authorization failed without a cause.",
                                                ),
                                        )
                        }
                    } else {
                        null
                    }
                val payloadJson =
                    payloadProvider
                        .buildPayloadJson()

                val envelopeBytes =
                    envelopeEncryptor.encrypt(
                        ownerUid =
                            account.uid,

                        ownerGoogleSubjectHash =
                            googleSubjectHash,

                        payloadJson =
                            payloadJson,

                        dek =
                            dek,

                        wrappedKeyMetadata =
                            wrappedKeyMetadata,
                    )

                when (
                    val outcome = transport.upload(envelopeBytes, accessToken)
                ) {
                    is CloudRecoveryTransportOutcome.Success -> Unit
                    CloudRecoveryTransportOutcome.NotFound ->
                        return CloudRecoveryUploadResult.PermanentFailure(
                            IllegalStateException(
                                "Cloud recovery upload returned no matching backup.",
                            ),
                        )
                    CloudRecoveryTransportOutcome.AuthorizationRequired ->
                        return CloudRecoveryUploadResult.AuthorizationRequired
                    is CloudRecoveryTransportOutcome.RetryableFailure ->
                        return CloudRecoveryUploadResult.RetryableFailure(outcome.cause)
                    is CloudRecoveryTransportOutcome.PermanentFailure ->
                        return CloudRecoveryUploadResult.PermanentFailure(outcome.cause)
                }

                CloudRecoveryUploadResult.Uploaded
            } finally {
                dek.fill(0)
            }
        } catch (
            cancellation:
                CancellationException
        ) {
            throw cancellation
        } catch (
            error:
                DriveAppDataHttpException
                    .Unauthorized
        ) {
            CloudRecoveryUploadResult
                .AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException
                    .Forbidden
        ) {
            CloudRecoveryUploadResult
                .AuthorizationRequired
        } catch (
            error:
                DriveAppDataHttpException
                    .RateLimited
        ) {
            CloudRecoveryUploadResult
                .RetryableFailure(
                    error,
                )
        } catch (
            error:
                DriveAppDataHttpException
                    .RetryableServerError
        ) {
            CloudRecoveryUploadResult
                .RetryableFailure(
                    error,
                )
        } catch (
            error:
                DriveAppDataHttpException
                    .NotFound
        ) {
            CloudRecoveryUploadResult
                .RetryableFailure(
                    error,
                )
        } catch (
            error:
                DriveAppDataHttpException
                    .Other
        ) {
            CloudRecoveryUploadResult
                .PermanentFailure(
                    error,
                )
        } catch (
            error:
                IOException
        ) {
            CloudRecoveryUploadResult
                .RetryableFailure(
                    error,
                )
        } catch (
            error:
                Throwable
        ) {
            CloudRecoveryUploadResult
                .PermanentFailure(
                    error,
                )
        }
    }
}

public sealed interface CloudRecoveryUploadResult {
    public data object Uploaded :
        CloudRecoveryUploadResult

    public data object Disabled :
        CloudRecoveryUploadResult

    public data object NoAuthenticatedAccount :
        CloudRecoveryUploadResult

    public data object GuestNotApplicable :
        CloudRecoveryUploadResult

    public data object NoOwnedCompletedData :
        CloudRecoveryUploadResult

    public data object AccountMismatch :
        CloudRecoveryUploadResult

    public data object SetupRequired :
        CloudRecoveryUploadResult

    public data object AuthorizationRequired :
        CloudRecoveryUploadResult

    public data object Cancelled :
        CloudRecoveryUploadResult

    public data class RetryableFailure(
        val cause: Throwable,
    ) : CloudRecoveryUploadResult

    public data class PermanentFailure(
        val cause: Throwable,
    ) : CloudRecoveryUploadResult
}

internal data class CloudRecoveryUploadAccount(
    val uid: String,
    val isAnonymous: Boolean,
    val hasGoogleProvider: Boolean,
    val googleSubjectHash: String? = null,
)

internal fun interface CloudRecoveryUploadEnabledStateProvider {
    suspend fun isEnabled(): Boolean
}

internal fun interface CloudRecoveryUploadClock {
    fun currentTimeMillis(): Long
}

internal interface CloudRecoveryUploadStatusRecorder {
    suspend fun recordAttempt(epochMillis: Long)

    suspend fun recordOutcome(
        outcome: CloudRecoveryStoredUploadOutcome,
        epochMillis: Long,
    )
}
internal interface CloudRecoveryUploadAccountProvider {
    fun currentAccount():
        CloudRecoveryUploadAccount?
}

internal interface CloudRecoveryUploadOwnerStateDataSource {
    val isCompleted:
        Flow<Boolean>

    val completedAccountUid:
        Flow<String?>
}

internal fun interface CloudRecoveryUploadPayloadProvider {
    suspend fun buildPayloadJson():
        String
}

internal interface CloudRecoveryUploadKeyMaterialSource {
    fun loadDek():
        ByteArray?

    fun loadWrappedKeyMetadata():
        WrappedKeyMetadata?
}

internal fun interface CloudRecoveryUploadAuthorizationProvider {
    suspend fun requestAuthorization():
        DriveAuthorizationResult
}

internal fun interface CloudRecoveryUploadEnvelopeEncryptor {
    fun encrypt(
        ownerUid: String,
        ownerGoogleSubjectHash: String?,
        payloadJson: String,
        dek: ByteArray,
        wrappedKeyMetadata:
            WrappedKeyMetadata,
    ): ByteArray
}

private class DataStoreCloudRecoveryUploadEnabledStateProvider(
    private val dataSource: CloudRecoveryPreferencesDataSource,
) : CloudRecoveryUploadEnabledStateProvider {
    override suspend fun isEnabled(): Boolean = dataSource.enabled.first()
}

private data object SystemCloudRecoveryUploadClock :
    CloudRecoveryUploadClock {
    override fun currentTimeMillis(): Long =
        System.currentTimeMillis()
}

private class DataStoreCloudRecoveryUploadStatusRecorder(
    private val dataSource: CloudRecoveryPreferencesDataSource,
) : CloudRecoveryUploadStatusRecorder {
    override suspend fun recordAttempt(epochMillis: Long) {
        dataSource.recordUploadAttempt(epochMillis)
    }

    override suspend fun recordOutcome(
        outcome: CloudRecoveryStoredUploadOutcome,
        epochMillis: Long,
    ) {
        dataSource.recordUploadOutcome(
            outcome,
            epochMillis,
        )
    }
}

private fun CloudRecoveryUploadResult.toStoredOutcome():
    CloudRecoveryStoredUploadOutcome =
    when (this) {
        CloudRecoveryUploadResult.Uploaded ->
            CloudRecoveryStoredUploadOutcome.Uploaded

        CloudRecoveryUploadResult.NoAuthenticatedAccount ->
            CloudRecoveryStoredUploadOutcome.NoAuthenticatedAccount

        CloudRecoveryUploadResult.GuestNotApplicable ->
            CloudRecoveryStoredUploadOutcome.GuestNotApplicable

        CloudRecoveryUploadResult.NoOwnedCompletedData ->
            CloudRecoveryStoredUploadOutcome.NoOwnedCompletedData

        CloudRecoveryUploadResult.AccountMismatch ->
            CloudRecoveryStoredUploadOutcome.AccountMismatch

        CloudRecoveryUploadResult.SetupRequired ->
            CloudRecoveryStoredUploadOutcome.SetupRequired

        CloudRecoveryUploadResult.AuthorizationRequired ->
            CloudRecoveryStoredUploadOutcome.AuthorizationRequired

        CloudRecoveryUploadResult.Cancelled ->
            CloudRecoveryStoredUploadOutcome.Cancelled

        is CloudRecoveryUploadResult.RetryableFailure ->
            CloudRecoveryStoredUploadOutcome.RetryableFailure

        is CloudRecoveryUploadResult.PermanentFailure ->
            CloudRecoveryStoredUploadOutcome.PermanentFailure

        CloudRecoveryUploadResult.Disabled ->
            error("Disabled upload results are not persisted as attempts.")
    }
private class FirebaseCloudRecoveryUploadAccountProvider(
    private val firebaseAuth:
        FirebaseAuth =
        FirebaseAuth.getInstance(),
) : CloudRecoveryUploadAccountProvider {
    override fun currentAccount():
        CloudRecoveryUploadAccount? {
        val user =
            firebaseAuth.currentUser
                ?: return null

        return CloudRecoveryUploadAccount(
            uid =
                user.uid,

            isAnonymous =
                user.isAnonymous,

            hasGoogleProvider =
                user.providerData.any { info ->
                    info.providerId == GoogleAuthProvider.PROVIDER_ID
                },

            googleSubjectHash =
                resolveGoogleAccountIdentity(user)?.subjectHash,
        )
    }
}

private class PreferencesCloudRecoveryUploadOwnerStateDataSource(
    private val dataSource:
        OnboardingPreferencesDataSource,
) : CloudRecoveryUploadOwnerStateDataSource {
    override val isCompleted:
        Flow<Boolean> =
        dataSource.isCompleted

    override val completedAccountUid:
        Flow<String?> =
        dataSource.completedAccountUid
}

private class RestoreBundleCloudRecoveryUploadPayloadProvider(
    private val writer:
        RestoreBundleWriter,
) : CloudRecoveryUploadPayloadProvider {
    override suspend fun buildPayloadJson():
        String =
        writer.buildPayloadJson()
}

private class LocalCloudRecoveryUploadKeyMaterialSource(
    private val keyStore:
        CloudRecoveryLocalKeyStore,

    private val metadataStore:
        CloudRecoveryLocalMetadataStore,
) : CloudRecoveryUploadKeyMaterialSource {
    override fun loadDek():
        ByteArray? =
        keyStore.load()

    override fun loadWrappedKeyMetadata():
        WrappedKeyMetadata? =
        metadataStore.load()
}

private class IdentityCloudRecoveryUploadAuthorizationProvider(
    private val authorization:
        DriveAppDataAuthorization,
) : CloudRecoveryUploadAuthorizationProvider {
    override suspend fun requestAuthorization():
        DriveAuthorizationResult =
        authorization.requestAuthorization()
}

private class CryptoCloudRecoveryUploadEnvelopeEncryptor(
    private val crypto:
        CloudRecoveryCrypto,
) : CloudRecoveryUploadEnvelopeEncryptor {
    override fun encrypt(
        ownerUid: String,
        ownerGoogleSubjectHash: String?,
        payloadJson: String,
        dek: ByteArray,
        wrappedKeyMetadata:
            WrappedKeyMetadata,
    ): ByteArray =
        crypto.encryptPayloadWithExistingDek(
            ownerUid =
                ownerUid,

            ownerGoogleSubjectHash =
                ownerGoogleSubjectHash,

            payloadJson =
                payloadJson,

            dek =
                dek,

            existingWrappedKeyMetadata =
                wrappedKeyMetadata,
        )
}