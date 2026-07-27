package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.restore.AndroidPendingRestoredOwnershipClaimStore
import com.impulsive.app.backend.data.restore.AndroidRestoreProvenanceStore
import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaim
import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaimStore
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.data.restore.hasSameIdentityAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal sealed interface VerifiedCloudRestoreOwnerProof {
    data class ExactUid(
        val currentUid: String,
    ) : VerifiedCloudRestoreOwnerProof

    data class SameGoogleIdentity(
        val previousUid: String,
        val previousGoogleSubjectHash: String,
        val currentUid: String,
        val currentGoogleSubjectHash: String,
    ) : VerifiedCloudRestoreOwnerProof

    data class LegacyEnvelope(
        val previousUid: String?,
        val currentUid: String,
        val currentGoogleSubjectHash: String?,
    ) : VerifiedCloudRestoreOwnerProof
}

internal sealed interface CloudRestoreOwnershipFinalizationResult {
    data object Success : CloudRestoreOwnershipFinalizationResult
    data object SuccessBackupRefreshPending : CloudRestoreOwnershipFinalizationResult
    data object SuccessRequiresOnboardingSetup : CloudRestoreOwnershipFinalizationResult
    data object RestoredButOwnershipFinalizationPending :
        CloudRestoreOwnershipFinalizationResult
}

internal data class CloudRestoreOwnershipAccount(
    val uid: String,
    val googleSubjectHash: String?,
    val isAnonymous: Boolean = false,
    val hasGoogleProvider: Boolean = googleSubjectHash != null,
)

internal fun interface CloudRestoreOwnershipAccountProvider {
    fun currentAccount(): CloudRestoreOwnershipAccount?
}

internal interface CloudRestoreOwnershipState {
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>
    val completedGoogleSubjectHash: Flow<String?>

    suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String?,
    )
}

internal interface CloudRestoreOwnershipProvenance {
    fun isRestorePending(): Boolean
    fun clearRestorePending()
}

internal fun interface CloudRestoreOwnershipScheduler {
    fun request()
}

internal class CloudRestoreOwnershipFinalizer internal constructor(
    private val accountProvider: CloudRestoreOwnershipAccountProvider,
    private val ownerState: CloudRestoreOwnershipState,
    private val provenance: CloudRestoreOwnershipProvenance,
    private val pendingClaims: PendingRestoredOwnershipClaimStore,
    private val snapshotScheduler: CloudRestoreOwnershipScheduler,
    private val cloudScheduler: CloudRestoreOwnershipScheduler,
) {
    constructor(
        context: Context,
        firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    ) : this(
        accountProvider = CloudRestoreFirebaseAccountProvider(firebaseAuth),
        ownerState = CloudRestorePreferencesOwnershipState(
            OnboardingPreferencesDataSource(context.applicationContext),
        ),
        provenance = CloudRestoreAndroidProvenance(
            AndroidRestoreProvenanceStore(context.applicationContext),
        ),
        pendingClaims =
            AndroidPendingRestoredOwnershipClaimStore(context.applicationContext),
        snapshotScheduler = CloudRestoreOwnershipScheduler {
            RestoreSnapshotRefreshScheduler.request(context.applicationContext)
        },
        cloudScheduler = CloudRestoreOwnershipScheduler {
            CloudRecoveryUploadScheduler.request(context.applicationContext)
        },
    )

    suspend fun finalizeAfterVerifiedCloudRestore(
        proof: VerifiedCloudRestoreOwnerProof,
        deferProvenanceCleanup: Boolean = false,
    ): CloudRestoreOwnershipFinalizationResult {
        val account =
            accountProvider.currentAccount()
                ?: return CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
        if (!proof.matchesCurrentSession(account)) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (!ownerState.isCompleted.first()) {
            return CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup
        }
        if (proof is VerifiedCloudRestoreOwnerProof.ExactUid) {
            return finalizeExactUid(
                proof = proof,
                deferProvenanceCleanup = deferProvenanceCleanup,
            )
        }
        return when (proof) {
            is VerifiedCloudRestoreOwnerProof.SameGoogleIdentity ->
                finalizeSameGoogleIdentity(
                    proof = proof,
                    deferProvenanceCleanup = deferProvenanceCleanup,
                )
            is VerifiedCloudRestoreOwnerProof.LegacyEnvelope ->
                finalizeLegacyEnvelope(
                    proof = proof,
                    deferProvenanceCleanup = deferProvenanceCleanup,
                )
            is VerifiedCloudRestoreOwnerProof.ExactUid ->
                finalizeExactUid(
                    proof = proof,
                    deferProvenanceCleanup = deferProvenanceCleanup,
                )
        }
    }

    private suspend fun finalizeExactUid(
        proof: VerifiedCloudRestoreOwnerProof.ExactUid,
        deferProvenanceCleanup: Boolean,
    ): CloudRestoreOwnershipFinalizationResult {
        val account = accountProvider.currentAccount()
        if (
            account == null ||
            account.isAnonymous ||
            account.uid != proof.currentUid
        ) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (!ownerState.isCompleted.first()) {
            return CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup
        }

        val verifiedGoogleSubjectHash =
            account.googleSubjectHash
                ?.takeIf(::isValidGoogleSubjectHash)
        val completedAccountUid =
            ownerState.completedAccountUid.first()
        val completedGoogleSubjectHash =
            ownerState.completedGoogleSubjectHash.first()
        if (
            completedAccountUid != proof.currentUid ||
            completedGoogleSubjectHash != verifiedGoogleSubjectHash
        ) {
            try {
                ownerState.setCompletedForAccount(
                    isCompleted = true,
                    accountUid = proof.currentUid,
                    googleSubjectHash = verifiedGoogleSubjectHash,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
            }
        }
        if (!proof.matchesCurrentSession(accountProvider.currentAccount())) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        try {
            if (
                !ownerState.isCompleted.first() ||
                ownerState.completedAccountUid.first() != proof.currentUid ||
                ownerState.completedGoogleSubjectHash.first() !=
                verifiedGoogleSubjectHash
            ) {
                return CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        return finishRefreshAndCleanup(deferProvenanceCleanup)
    }

    private suspend fun finalizeSameGoogleIdentity(
        proof: VerifiedCloudRestoreOwnerProof.SameGoogleIdentity,
        deferProvenanceCleanup: Boolean,
    ): CloudRestoreOwnershipFinalizationResult {
        val savedUid = ownerState.completedAccountUid.first()
        val savedGoogleSubjectHash =
            ownerState.completedGoogleSubjectHash.first()
        if (
            savedUid == proof.currentUid &&
            savedGoogleSubjectHash ==
            proof.currentGoogleSubjectHash
        ) {
            return finishRefreshAndCleanup(deferProvenanceCleanup)
        }
        if (!provenance.isRestorePending()) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (
            savedUid != proof.previousUid ||
            savedGoogleSubjectHash !=
            proof.previousGoogleSubjectHash ||
            !isValidGoogleSubjectHash(proof.previousGoogleSubjectHash) ||
            !isValidGoogleSubjectHash(proof.currentGoogleSubjectHash)
        ) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }

        val claim = PendingRestoredOwnershipClaim(
            previousOwnerUid = proof.previousUid,
            previousGoogleSubjectHash = proof.previousGoogleSubjectHash,
            currentUid = proof.currentUid,
            currentGoogleSubjectHash = proof.currentGoogleSubjectHash,
            createdAtMillis = System.currentTimeMillis(),
        )
        try {
            val existing = pendingClaims.read()
            when {
                existing == null -> pendingClaims.write(claim)
                !existing.hasSameIdentityAs(claim) -> {
                    pendingClaims.clear()
                    return CloudRestoreOwnershipFinalizationResult
                        .RestoredButOwnershipFinalizationPending
                }
            }
        } catch (_: Throwable) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }

        if (!proof.matchesCurrentSession(accountProvider.currentAccount())) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        try {
            ownerState.setCompletedForAccount(
                isCompleted = true,
                accountUid = proof.currentUid,
                googleSubjectHash = proof.currentGoogleSubjectHash,
            )
        } catch (_: Throwable) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        return finishRefreshAndCleanup(deferProvenanceCleanup)
    }

    private suspend fun finalizeLegacyEnvelope(
        proof: VerifiedCloudRestoreOwnerProof.LegacyEnvelope,
        deferProvenanceCleanup: Boolean,
    ): CloudRestoreOwnershipFinalizationResult {
        val savedUid = ownerState.completedAccountUid.first()
        val savedGoogleSubjectHash =
            ownerState.completedGoogleSubjectHash.first()
        if (
            savedUid == proof.currentUid &&
            savedGoogleSubjectHash ==
            proof.currentGoogleSubjectHash
        ) {
            return finishRefreshAndCleanup(deferProvenanceCleanup)
        }
        if (!provenance.isRestorePending()) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (
            (proof.previousUid == null && savedUid != null) ||
            (proof.previousUid != null && savedUid != proof.previousUid)
        ) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (!proof.matchesCurrentSession(accountProvider.currentAccount())) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        try {
            ownerState.setCompletedForAccount(
                isCompleted = true,
                accountUid = proof.currentUid,
                googleSubjectHash =
                    proof.currentGoogleSubjectHash
                        ?.takeIf(::isValidGoogleSubjectHash),
            )
        } catch (_: Throwable) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        return finishRefreshAndCleanup(deferProvenanceCleanup)
    }

    private fun finishRefreshAndCleanup(
        deferProvenanceCleanup: Boolean,
    ):
        CloudRestoreOwnershipFinalizationResult {
        var refreshPending = false
        try {
            snapshotScheduler.request()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            refreshPending = true
        }
        try {
            cloudScheduler.request()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            refreshPending = true
        }
        try {
            pendingClaims.clear()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending
        }
        if (!deferProvenanceCleanup) {
            try {
                provenance.clearRestorePending()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                return CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
            }
        }
        return if (refreshPending) {
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending
        } else {
            CloudRestoreOwnershipFinalizationResult.Success
        }
    }
}

internal fun VerifiedCloudRestoreOwnerProof.matchesCurrentSession(
    account: CloudRestoreOwnershipAccount?,
): Boolean {
    if (account == null || account.isAnonymous) return false
    return when (this) {
        is VerifiedCloudRestoreOwnerProof.ExactUid ->
            account.uid == currentUid
        is VerifiedCloudRestoreOwnerProof.SameGoogleIdentity ->
            account.uid == currentUid &&
                account.hasGoogleProvider &&
                isValidGoogleSubjectHash(currentGoogleSubjectHash) &&
                isValidGoogleSubjectHash(account.googleSubjectHash.orEmpty()) &&
                account.googleSubjectHash == currentGoogleSubjectHash
        is VerifiedCloudRestoreOwnerProof.LegacyEnvelope ->
            account.uid == currentUid &&
                if (currentGoogleSubjectHash != null) {
                    account.hasGoogleProvider &&
                        isValidGoogleSubjectHash(currentGoogleSubjectHash) &&
                        isValidGoogleSubjectHash(
                            account.googleSubjectHash.orEmpty(),
                        ) &&
                        account.googleSubjectHash == currentGoogleSubjectHash
                } else {
                    !account.hasGoogleProvider &&
                        account.googleSubjectHash == null
                }
    }
}

internal class CloudRestoreFirebaseAccountProvider(
    private val auth: FirebaseAuth,
) : CloudRestoreOwnershipAccountProvider {
    override fun currentAccount(): CloudRestoreOwnershipAccount? =
        auth.currentUser?.let { user ->
            CloudRestoreOwnershipAccount(
                uid = user.uid,
                googleSubjectHash =
                    resolveGoogleAccountIdentity(user)?.subjectHash,
                isAnonymous = user.isAnonymous,
                hasGoogleProvider =
                    user.providerData.any { provider ->
                        provider.providerId ==
                            GoogleAuthProvider.PROVIDER_ID
                    },
            )
        }
}

private class CloudRestorePreferencesOwnershipState(
    private val delegate: OnboardingPreferencesDataSource,
) : CloudRestoreOwnershipState {
    override val isCompleted: Flow<Boolean> = delegate.isCompleted
    override val completedAccountUid: Flow<String?> =
        delegate.completedAccountUid
    override val completedGoogleSubjectHash: Flow<String?> =
        delegate.completedGoogleSubjectHash

    override suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String?,
    ) {
        delegate.setCompletedForAccount(
            isCompleted = isCompleted,
            accountUid = accountUid,
            googleSubjectHash = googleSubjectHash,
        )
    }
}

private class CloudRestoreAndroidProvenance(
    private val delegate: AndroidRestoreProvenanceStore,
) : CloudRestoreOwnershipProvenance {
    override fun isRestorePending(): Boolean = delegate.isRestorePending()
    override fun clearRestorePending() = delegate.clearRestorePending()
}
