package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.local.dao.CloudRestoreReceiptDao
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import com.impulsive.app.backend.data.local.entity.requireValid
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.AndroidRestoreProvenanceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

internal sealed interface CloudRestorePostImportRecoveryResult {
    data object NothingPending : CloudRestorePostImportRecoveryResult
    data object Finalized : CloudRestorePostImportRecoveryResult
    data object FinalizedRefreshPending :
        CloudRestorePostImportRecoveryResult
    data object RequiresCorrectAccount :
        CloudRestorePostImportRecoveryResult
    data object RequiresCloudRecoverySetup :
        CloudRestorePostImportRecoveryResult
    data object RequiresOnboardingSetup :
        CloudRestorePostImportRecoveryResult
    data object AuthorizationWithoutCommittedImportCleared :
        CloudRestorePostImportRecoveryResult
    data object FinalizationPending :
        CloudRestorePostImportRecoveryResult

    data class Failed(
        val cause: Throwable,
    ) : CloudRestorePostImportRecoveryResult
}

internal interface CloudRestoreReceiptStore {
    suspend fun latest(): CloudRestoreReceiptEntity?
    suspend fun find(receiptId: String): CloudRestoreReceiptEntity?
    suspend fun delete(receiptId: String): Int
}

internal fun interface CloudRestorePostImportFinalizer {
    suspend fun finalize(
        proof: VerifiedCloudRestoreOwnerProof,
    ): CloudRestoreOwnershipFinalizationResult
}

internal fun interface CloudRestoreActivatedCredentialsProvider {
    suspend fun hasActivatedCredentials(): Boolean
}

internal fun interface CloudRestorePostImportProvenance {
    fun clearRestorePending()
}

private val ProcessCloudRestoreResumeMutex = Mutex()

internal class CloudRestorePostImportRecoveryCoordinator internal constructor(
    private val receipts: CloudRestoreReceiptStore,
    private val authorizations: PendingCloudRestoreAuthorizationStore,
    private val accountProvider: CloudRestoreOwnershipAccountProvider,
    private val finalizer: CloudRestorePostImportFinalizer,
    private val credentials: CloudRestoreActivatedCredentialsProvider,
    private val provenance: CloudRestorePostImportProvenance,
    private val resumeMutex: Mutex = ProcessCloudRestoreResumeMutex,
) {
    constructor(context: Context) : this(
        receipts =
            RoomCloudRestoreReceiptStore(
                AppDatabase.getInstance(context.applicationContext)
                    .cloudRestoreReceiptDao(),
            ),
        authorizations =
            AndroidPendingCloudRestoreAuthorizationStore(
                context.applicationContext,
            ),
        accountProvider =
            CloudRestoreFirebaseAccountProvider(
                FirebaseAuth.getInstance(),
            ),
        finalizer =
            CloudRestorePostImportFinalizer { proof ->
                CloudRestoreOwnershipFinalizer(
                    context.applicationContext,
                ).finalizeAfterVerifiedCloudRestore(
                    proof = proof,
                    deferProvenanceCleanup = true,
                )
            },
        credentials =
            AndroidCloudRestoreActivatedCredentialsProvider(
                context.applicationContext,
            ),
        provenance =
            CloudRestorePostImportProvenance {
                AndroidRestoreProvenanceStore(
                    context.applicationContext,
                ).clearRestorePending()
            },
    )

    suspend fun resumeIfNeeded(): CloudRestorePostImportRecoveryResult {
        if (!resumeMutex.tryLock()) {
            return CloudRestorePostImportRecoveryResult
                .FinalizationPending
        }
        try {
            return resumeLocked()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return CloudRestorePostImportRecoveryResult.Failed(error)
        } finally {
            resumeMutex.unlock()
        }
    }

    internal suspend fun cleanupCommittedReceipt(
        receiptId: String,
    ): Boolean =
        try {
            authorizations.clear()
            provenance.clearRestorePending()
            when (receipts.delete(receiptId)) {
                1 -> true
                0 -> receipts.find(receiptId) == null
                else -> false
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }

    private suspend fun resumeLocked():
        CloudRestorePostImportRecoveryResult {
        val receipt = receipts.latest()
        val authorization = authorizations.read()

        if (receipt == null && authorization == null) {
            return CloudRestorePostImportRecoveryResult.NothingPending
        }
        if (receipt == null) {
            authorizations.clear()
            return CloudRestorePostImportRecoveryResult
                .AuthorizationWithoutCommittedImportCleared
        }

        val validatedReceipt =
            runCatching { receipt.requireValid() }
                .getOrNull()
                ?: return CloudRestorePostImportRecoveryResult
                    .FinalizationPending
        if (
            authorization != null &&
            !authorization.matches(validatedReceipt)
        ) {
            return CloudRestorePostImportRecoveryResult
                .FinalizationPending
        }

        val proof =
            validatedReceipt.toVerifiedOwnerProof()
                ?: return CloudRestorePostImportRecoveryResult
                    .FinalizationPending
        if (!proof.matchesCurrentSession(accountProvider.currentAccount())) {
            return CloudRestorePostImportRecoveryResult
                .RequiresCorrectAccount
        }

        val finalization =
            finalizer.finalize(proof)
        when (finalization) {
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending ->
                return CloudRestorePostImportRecoveryResult
                    .FinalizationPending

            CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup ->
                return CloudRestorePostImportRecoveryResult
                    .RequiresOnboardingSetup

            CloudRestoreOwnershipFinalizationResult.Success,
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            -> Unit
        }

        val hasActivatedCredentials =
            credentials.hasActivatedCredentials()
        if (!cleanupCommittedReceipt(validatedReceipt.receiptId)) {
            return CloudRestorePostImportRecoveryResult
                .FinalizationPending
        }
        if (!hasActivatedCredentials) {
            return CloudRestorePostImportRecoveryResult
                .RequiresCloudRecoverySetup
        }
        return if (
            finalization ==
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending
        ) {
            CloudRestorePostImportRecoveryResult
                .FinalizedRefreshPending
        } else {
            CloudRestorePostImportRecoveryResult.Finalized
        }
    }
}

private fun CloudRestoreReceiptEntity.toVerifiedOwnerProof():
    VerifiedCloudRestoreOwnerProof? =
    when (
        CloudRestoreProofType.fromPersistedValue(proofType)
    ) {
        CloudRestoreProofType.ExactUid ->
            VerifiedCloudRestoreOwnerProof.ExactUid(
                currentUid = currentUid,
            )

        CloudRestoreProofType.SameGoogleIdentity ->
            VerifiedCloudRestoreOwnerProof.SameGoogleIdentity(
                previousUid = previousUid ?: return null,
                previousGoogleSubjectHash =
                    previousGoogleSubjectHash ?: return null,
                currentUid = currentUid,
                currentGoogleSubjectHash =
                    currentGoogleSubjectHash ?: return null,
            )

        CloudRestoreProofType.LegacyEnvelope ->
            VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                previousUid = previousUid,
                currentUid = currentUid,
                currentGoogleSubjectHash = currentGoogleSubjectHash,
            )

        null -> null
    }

private class RoomCloudRestoreReceiptStore(
    private val dao: CloudRestoreReceiptDao,
) : CloudRestoreReceiptStore {
    override suspend fun latest(): CloudRestoreReceiptEntity? = dao.latest()

    override suspend fun find(
        receiptId: String,
    ): CloudRestoreReceiptEntity? = dao.find(receiptId)

    override suspend fun delete(receiptId: String): Int =
        dao.delete(receiptId)
}

private class AndroidCloudRestoreActivatedCredentialsProvider(
    context: Context,
) : CloudRestoreActivatedCredentialsProvider {
    private val keyStore =
        CloudRecoveryLocalKeyStore(context.applicationContext)
    private val metadataStore =
        CloudRecoveryLocalMetadataStore(context.applicationContext)
    private val preferences =
        CloudRecoveryPreferencesDataSource(context.applicationContext)

    override suspend fun hasActivatedCredentials(): Boolean {
        if (!preferences.enabled.first()) return false
        val rawKey = keyStore.load() ?: return false
        return try {
            metadataStore.load() != null
        } finally {
            rawKey.fill(0)
        }
    }
}
