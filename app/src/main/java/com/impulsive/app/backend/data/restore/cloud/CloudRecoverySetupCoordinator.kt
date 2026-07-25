package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.RestoreBundleWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

public class CloudRecoverySetupCoordinator internal constructor(
    private val accountProvider: CloudRecoverySetupAccountProvider,
    private val payloadProvider: CloudRecoverySetupPayloadProvider,
    private val keyMaterialStore: CloudRecoverySetupKeyMaterialStore,
    private val crypto: CloudRecoveryCrypto,
    private val uploadCoordinator: CloudRecoverySetupUploadCoordinator,
    private val scheduler: CloudRecoverySetupScheduler,
    private val preferences: CloudRecoverySetupPreferences,
) {
    public constructor(context: Context) : this(
        accountProvider = FirebaseCloudRecoverySetupAccountProvider(),
        payloadProvider = RestoreBundleCloudRecoverySetupPayloadProvider(
            RestoreBundleWriter(context.applicationContext),
        ),
        keyMaterialStore = LocalCloudRecoverySetupKeyMaterialStore(
            CloudRecoveryLocalKeyStore(context.applicationContext),
            CloudRecoveryLocalMetadataStore(context.applicationContext),
        ),
        crypto = CloudRecoveryCrypto(),
        uploadCoordinator = CoordinatorCloudRecoverySetupUploadCoordinator(
            CloudRecoveryUploadCoordinator(context.applicationContext),
        ),
        scheduler = WorkManagerCloudRecoverySetupScheduler(context.applicationContext),
        preferences = DataStoreCloudRecoverySetupPreferences(
            CloudRecoveryPreferencesDataSource(context.applicationContext),
        ),
    )

    public fun accountEligibility(): CloudRecoveryAccountEligibility =
        when (val account = accountProvider.currentAccount()) {
            null -> CloudRecoveryAccountEligibility.NotSignedIn
            else -> if (account.isAnonymous) {
                CloudRecoveryAccountEligibility.GuestNotSupported
            } else {
                CloudRecoveryAccountEligibility.Eligible
            }
        }

public suspend fun createCloudRecovery(
    password: CharArray,
): CloudRecoverySetupResult {
    return try {
        createCloudRecoveryInternal(
            password,
        )
    } catch (
        cancellation:
            CancellationException,
    ) {
        throw cancellation
    } catch (
        error:
            Throwable,
    ) {
        /*
         * A setup exception must never terminate MainActivity.
         *
         * Complete key material may deliberately remain available so the user
         * can retry an upload. The preference must remain off unless setup
         * completed fully.
         */
        try {
            preferences.setEnabled(
                false,
            )
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            ignored:
                Throwable,
        ) {
            // Preserve the original safe setup result.
        }

        CloudRecoverySetupResult
            .UnexpectedFailure
    } finally {
        password.fill(
            '\u0000',
        )
    }
}

private suspend fun createCloudRecoveryInternal(
    password: CharArray,
): CloudRecoverySetupResult {
    when (
        accountEligibility()
    ) {
        CloudRecoveryAccountEligibility.NotSignedIn ->
            return CloudRecoverySetupResult
                    .NotSignedIn

        CloudRecoveryAccountEligibility.GuestNotSupported ->
            return CloudRecoverySetupResult
                    .GuestNotSupported

        CloudRecoveryAccountEligibility.Eligible ->
            Unit
    }

    if (
        password.size <
        MinimumPasswordLength
    ) {
        return CloudRecoverySetupResult
                .PasswordTooShort
    }

    preferences.setEnabled(
        false,
    )

    preferences.clearBackupStatus()

    val existingDek =
        keyMaterialStore.loadDek()

    if (
        existingDek != null
    ) {
        try {
            if (
                keyMaterialStore
                    .loadWrappedKeyMetadata() !=
                null
            ) {
                return uploadExistingRecovery()
            }
        } finally {
            existingDek.fill(
                0,
            )
        }

        /*
         * A partial local setup cannot be uploaded. Remove it before creating
         * a new internally consistent recovery key pair.
         */
        keyMaterialStore.clear()
    }

    val account =
        accountProvider.currentAccount()
            ?: return CloudRecoverySetupResult
                    .NotSignedIn

    if (
        account.isAnonymous
    ) {
        return CloudRecoverySetupResult
                .GuestNotSupported
    }

    val recovery =
        crypto.createNewRecovery(
            ownerUid =
                account.uid,

            payloadJson =
                payloadProvider
                    .buildPayloadJson(),

            recoveryPassword =
                password,
        )

    try {
        keyMaterialStore.storeDek(
            recovery.rawDek,
        )

        keyMaterialStore
            .storeWrappedKeyMetadata(
                recovery
                    .wrappedKeyMetadata,
            )
    } catch (
        error:
            Throwable,
    ) {
        keyMaterialStore.clear()

        throw error
    } finally {
        recovery.rawDek.fill(
            0,
        )
    }

    return uploadExistingRecovery()
}
    public suspend fun disableCloudRecovery() {
        scheduler.cancel()
        preferences.setEnabled(false)
        preferences.clearBackupStatus()
    }

    private suspend fun uploadExistingRecovery(): CloudRecoverySetupResult =
        when (val uploadResult = uploadCoordinator.uploadCurrentRecovery()) {
            CloudRecoveryUploadResult.Uploaded -> {
                scheduler.request()
                preferences.setEnabled(true)
                CloudRecoverySetupResult.Success
            }

            else -> CloudRecoverySetupResult.InitialUploadFailed(uploadResult)
        }

    private companion object {
        const val MinimumPasswordLength = 10
    }
}

internal fun hasValidCloudRecoveryPassword(
    password: CharSequence,
    confirmation: CharSequence,
): Boolean =
    password.length >= 10 && password == confirmation
public sealed interface CloudRecoveryAccountEligibility {
    public data object Eligible : CloudRecoveryAccountEligibility
    public data object NotSignedIn : CloudRecoveryAccountEligibility
    public data object GuestNotSupported : CloudRecoveryAccountEligibility
}

public sealed interface CloudRecoverySetupResult {
    public data object Success : CloudRecoverySetupResult
    public data object NotSignedIn : CloudRecoverySetupResult
    public data object GuestNotSupported : CloudRecoverySetupResult
    public data object PasswordTooShort : CloudRecoverySetupResult
    public data object UnexpectedFailure : CloudRecoverySetupResult
    public data class InitialUploadFailed(
        val uploadResult: CloudRecoveryUploadResult,
    ) : CloudRecoverySetupResult
}

internal data class CloudRecoverySetupAccount(
    val uid: String,
    val isAnonymous: Boolean,
)

internal fun interface CloudRecoverySetupAccountProvider {
    fun currentAccount(): CloudRecoverySetupAccount?
}

internal fun interface CloudRecoverySetupPayloadProvider {
    suspend fun buildPayloadJson(): String
}

internal interface CloudRecoverySetupKeyMaterialStore {
    fun loadDek(): ByteArray?
    fun loadWrappedKeyMetadata(): WrappedKeyMetadata?
    fun storeDek(dek: ByteArray)
    fun storeWrappedKeyMetadata(metadata: WrappedKeyMetadata)
    fun clear()
}

internal fun interface CloudRecoverySetupUploadCoordinator {
    suspend fun uploadCurrentRecovery(): CloudRecoveryUploadResult
}

internal interface CloudRecoverySetupScheduler {
    fun request()
    fun cancel()
}

internal interface CloudRecoverySetupPreferences {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun clearBackupStatus()
}

private class FirebaseCloudRecoverySetupAccountProvider :
    CloudRecoverySetupAccountProvider {
    override fun currentAccount(): CloudRecoverySetupAccount? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return CloudRecoverySetupAccount(user.uid, user.isAnonymous)
    }
}

private class RestoreBundleCloudRecoverySetupPayloadProvider(
    private val writer: RestoreBundleWriter,
) : CloudRecoverySetupPayloadProvider {
    override suspend fun buildPayloadJson(): String = writer.buildPayloadJson()
}

private class LocalCloudRecoverySetupKeyMaterialStore(
    private val keyStore: CloudRecoveryLocalKeyStore,
    private val metadataStore: CloudRecoveryLocalMetadataStore,
) : CloudRecoverySetupKeyMaterialStore {
    override fun loadDek(): ByteArray? = keyStore.load()
    override fun loadWrappedKeyMetadata(): WrappedKeyMetadata? = metadataStore.load()
    override fun storeDek(dek: ByteArray) = keyStore.store(dek)
    override fun storeWrappedKeyMetadata(metadata: WrappedKeyMetadata) = metadataStore.store(metadata)
    override fun clear() {
        keyStore.clear()
        metadataStore.clear()
    }
}

private class CoordinatorCloudRecoverySetupUploadCoordinator(
    private val coordinator: CloudRecoveryUploadCoordinator,
) : CloudRecoverySetupUploadCoordinator {
    override suspend fun uploadCurrentRecovery(): CloudRecoveryUploadResult =
        coordinator.uploadCurrentRecoveryForSetup()
}

private class WorkManagerCloudRecoverySetupScheduler(
    private val context: Context,
) : CloudRecoverySetupScheduler {
    override fun request() = CloudRecoveryUploadScheduler.request(context)
    override fun cancel() = CloudRecoveryUploadScheduler.cancel(context)
}

private class DataStoreCloudRecoverySetupPreferences(
    private val dataSource: CloudRecoveryPreferencesDataSource,
) : CloudRecoverySetupPreferences {
    override val enabled: Flow<Boolean> = dataSource.enabled
    override suspend fun setEnabled(enabled: Boolean) = dataSource.setEnabled(enabled)
    override suspend fun clearBackupStatus() = dataSource.clearBackupStatus()
}