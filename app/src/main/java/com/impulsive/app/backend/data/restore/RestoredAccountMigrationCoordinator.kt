package com.impulsive.app.backend.data.restore

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryUploadScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

internal data class RestoredAccountMigrationAccount(
    val uid: String,
    val isAnonymous: Boolean,
    val hasGoogleProvider: Boolean,
    val googleSubjectHash: String?,
)

internal sealed interface RestoredAccountMigrationResult {
    data object Migrated : RestoredAccountMigrationResult
    data object ClaimedWithoutAutomaticBundle : RestoredAccountMigrationResult
    data object MigratedRefreshPending : RestoredAccountMigrationResult
    data object AlreadyRunning : RestoredAccountMigrationResult
    data object NotAuthenticated : RestoredAccountMigrationResult
    data object GuestNotApplicable : RestoredAccountMigrationResult
    data object RestoreNotPending : RestoredAccountMigrationResult
    data object OwnershipChanged : RestoredAccountMigrationResult
    data object LegacyCloudVerificationRequired : RestoredAccountMigrationResult
    data object ExistingLocalData : RestoredAccountMigrationResult
    data object InvalidBackup : RestoredAccountMigrationResult
    data class Failed(val cause: Throwable) : RestoredAccountMigrationResult
}

internal fun interface RestoredAccountMigrationAccountProvider {
    fun currentAccount(): RestoredAccountMigrationAccount?
}

internal interface RestoredAccountMigrationOwnerState {
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>
    val completedGoogleSubjectHash: Flow<String?>

    suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String?,
    )
}

internal interface RestoredAccountMigrationProvenance {
    fun isRestorePending(): Boolean
    fun clearRestorePending()
}

internal fun interface RestoredAccountMigrationImporter {
    suspend fun importIfNeeded(ownerProof: AutoRestoreOwnerProof): AutoRestoreResult
}

internal fun interface RestoredAccountMigrationScheduler {
    fun request()
}

internal fun interface RestoredAccountMigrationCloudEnabled {
    suspend fun isEnabled(): Boolean
}

internal class RestoredAccountMigrationCoordinator internal constructor(
    private val accountProvider: RestoredAccountMigrationAccountProvider,
    private val ownerState: RestoredAccountMigrationOwnerState,
    private val provenance: RestoredAccountMigrationProvenance,
    private val pendingClaims: PendingRestoredOwnershipClaimStore,
    private val importer: RestoredAccountMigrationImporter,
    private val snapshotScheduler: RestoredAccountMigrationScheduler,
    private val cloudUploadScheduler: RestoredAccountMigrationScheduler,
    private val cloudEnabled: RestoredAccountMigrationCloudEnabled,
) {
    constructor(
        context: Context,
        firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    ) : this(
        accountProvider = FirebaseRestoredAccountMigrationAccountProvider(firebaseAuth),
        ownerState = PreferencesRestoredAccountMigrationOwnerState(
            OnboardingPreferencesDataSource(context.applicationContext),
        ),
        provenance = AndroidRestoredAccountMigrationProvenance(
            AndroidRestoreProvenanceStore(context.applicationContext),
        ),
        pendingClaims = AndroidPendingRestoredOwnershipClaimStore(context.applicationContext),
        importer = RestoredAccountMigrationImporter { proof ->
            RestoreBundleImporter(context.applicationContext).importIfNeeded(proof)
        },
        snapshotScheduler = RestoredAccountMigrationScheduler {
            RestoreSnapshotRefreshScheduler.request(context.applicationContext)
        },
        cloudUploadScheduler = RestoredAccountMigrationScheduler {
            CloudRecoveryUploadScheduler.request(context.applicationContext)
        },
        cloudEnabled = RestoredAccountMigrationCloudEnabled {
            CloudRecoveryPreferencesDataSource(context.applicationContext).enabled.first()
        },
    )

    private val mutex = Mutex()

    suspend fun confirmSameGoogleIdentityAndRestore(): RestoredAccountMigrationResult {
        if (!mutex.tryLock()) return RestoredAccountMigrationResult.AlreadyRunning
        try {
            return execute()
        } catch (throwable: Throwable) {
            return RestoredAccountMigrationResult.Failed(throwable)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun execute(): RestoredAccountMigrationResult {
        val account =
            accountProvider.currentAccount()
                ?: return RestoredAccountMigrationResult.NotAuthenticated
        if (account.isAnonymous) {
            return RestoredAccountMigrationResult.GuestNotApplicable
        }
        if (!provenance.isRestorePending()) {
            return clearStaleClaimOr(RestoredAccountMigrationResult.RestoreNotPending)
        }

        val completed = ownerState.isCompleted.first()
        val ownerUid = ownerState.completedAccountUid.first()
        val ownerHash = ownerState.completedGoogleSubjectHash.first()
        val existingClaim = pendingClaims.read()

        if (existingClaim != null) {
            return when {
                describesAdoptedState(
                    claim = existingClaim,
                    account = account,
                    completed = completed,
                    ownerUid = ownerUid,
                    ownerHash = ownerHash,
                ) -> finalizeAdoptedClaimIfSafe()

                describesPreAdoptionState(
                    claim = existingClaim,
                    account = account,
                    completed = completed,
                    ownerUid = ownerUid,
                    ownerHash = ownerHash,
                ) -> importAndAdopt(existingClaim)

                else -> clearStaleClaimOr(RestoredAccountMigrationResult.OwnershipChanged)
            }
        }

        val generatedClaim =
            createAuthorizedClaim(
                account = account,
                completed = completed,
                ownerUid = ownerUid,
                ownerHash = ownerHash,
            ) ?: return RestoredAccountMigrationResult.OwnershipChanged

        pendingClaims.write(generatedClaim)
        return importAndAdopt(generatedClaim)
    }

    private suspend fun importAndAdopt(
        authorized: PendingRestoredOwnershipClaim,
    ): RestoredAccountMigrationResult {
        val import = importer.importIfNeeded(
            AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity(
                currentUid = authorized.currentUid,
                currentGoogleSubjectHash = authorized.currentGoogleSubjectHash,
            ),
        )
        val noBundle = import == AutoRestoreResult.NoBundle
        when (import) {
            AutoRestoreResult.Restored,
            AutoRestoreResult.NoBundle -> Unit
            AutoRestoreResult.LegacyOwnerVerificationRequired,
            AutoRestoreResult.LegacyUnownedBundle -> {
                pendingClaims.clear()
                return RestoredAccountMigrationResult.LegacyCloudVerificationRequired
            }
            AutoRestoreResult.OwnerMismatch -> {
                pendingClaims.clear()
                return RestoredAccountMigrationResult.OwnershipChanged
            }
            AutoRestoreResult.ExistingDataPresent -> return RestoredAccountMigrationResult.ExistingLocalData
            AutoRestoreResult.InvalidBundle -> {
                pendingClaims.clear()
                return RestoredAccountMigrationResult.InvalidBackup
            }
            is AutoRestoreResult.Failed -> return RestoredAccountMigrationResult.Failed(
                import.cause ?: IllegalStateException("Automatic restore import failed."),
            )
        }

        if (!authorisedStateStillMatches(authorized)) {
            return RestoredAccountMigrationResult.OwnershipChanged
        }

        try {
            ownerState.setCompletedForAccount(
                isCompleted = true,
                accountUid = authorized.currentUid,
                googleSubjectHash = authorized.currentGoogleSubjectHash,
            )
        } catch (throwable: Throwable) {
            return RestoredAccountMigrationResult.Failed(throwable)
        }

        return finishAdoptedClaim(
            fullSuccess = if (noBundle) {
                RestoredAccountMigrationResult.ClaimedWithoutAutomaticBundle
            } else {
                RestoredAccountMigrationResult.Migrated
            },
        )
    }

    private suspend fun finishAdoptedClaim(
        fullSuccess: RestoredAccountMigrationResult,
    ): RestoredAccountMigrationResult {
        var refreshPending = false
        try {
            snapshotScheduler.request()
        } catch (_: Throwable) {
            refreshPending = true
        }
        try {
            if (cloudEnabled.isEnabled()) cloudUploadScheduler.request()
        } catch (_: Throwable) {
            refreshPending = true
        }
        if (refreshPending) {
            return RestoredAccountMigrationResult.MigratedRefreshPending
        }

        try {
            pendingClaims.clear()
        } catch (_: Throwable) {
            return RestoredAccountMigrationResult.MigratedRefreshPending
        }

        return try {
            provenance.clearRestorePending()
            fullSuccess
        } catch (_: Throwable) {
            RestoredAccountMigrationResult.MigratedRefreshPending
        }
    }

    private suspend fun finalizeAdoptedClaimIfSafe(): RestoredAccountMigrationResult {
        val claim =
            pendingClaims.read()
                ?: return RestoredAccountMigrationResult.OwnershipChanged
        val account = accountProvider.currentAccount()
        if (account == null || account.isAnonymous || !account.hasGoogleProvider ||
            account.uid != claim.currentUid || account.googleSubjectHash != claim.currentGoogleSubjectHash ||
            !provenance.isRestorePending() || !ownerState.isCompleted.first() ||
            ownerState.completedAccountUid.first() != claim.currentUid ||
            ownerState.completedGoogleSubjectHash.first() != claim.currentGoogleSubjectHash
        ) {
            return clearStaleClaimOr(RestoredAccountMigrationResult.OwnershipChanged)
        }
        return finishAdoptedClaim(RestoredAccountMigrationResult.Migrated)
    }

    private fun createAuthorizedClaim(
        account: RestoredAccountMigrationAccount,
        completed: Boolean,
        ownerUid: String?,
        ownerHash: String?,
    ): PendingRestoredOwnershipClaim? {
        if (!completed) return null
        val previousUid = ownerUid?.trim()
        val previousHash = ownerHash
        val currentUid = account.uid.trim()
        val currentHash = account.googleSubjectHash
        if (
            previousUid.isNullOrBlank() || previousUid.length > MaxUidChars ||
            currentUid.isBlank() || currentUid.length > MaxUidChars ||
            previousUid == currentUid || !account.hasGoogleProvider ||
            !isValidGoogleSubjectHash(previousHash.orEmpty()) ||
            !isValidGoogleSubjectHash(currentHash.orEmpty()) || previousHash != currentHash
        ) return null
        return PendingRestoredOwnershipClaim(
            previousOwnerUid = previousUid,
            previousGoogleSubjectHash = requireNotNull(previousHash),
            currentUid = currentUid,
            currentGoogleSubjectHash = requireNotNull(currentHash),
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    private fun describesPreAdoptionState(
        claim: PendingRestoredOwnershipClaim,
        account: RestoredAccountMigrationAccount,
        completed: Boolean,
        ownerUid: String?,
        ownerHash: String?,
    ): Boolean =
        completed &&
            accountMatchesClaim(account, claim) &&
            ownerUid == claim.previousOwnerUid &&
            ownerHash == claim.previousGoogleSubjectHash

    private fun describesAdoptedState(
        claim: PendingRestoredOwnershipClaim,
        account: RestoredAccountMigrationAccount,
        completed: Boolean,
        ownerUid: String?,
        ownerHash: String?,
    ): Boolean =
        completed &&
            accountMatchesClaim(account, claim) &&
            ownerUid == claim.currentUid &&
            ownerHash == claim.currentGoogleSubjectHash

    private fun accountMatchesClaim(
        account: RestoredAccountMigrationAccount,
        claim: PendingRestoredOwnershipClaim,
    ): Boolean =
        !account.isAnonymous &&
            account.hasGoogleProvider &&
            account.uid == claim.currentUid &&
            account.googleSubjectHash == claim.currentGoogleSubjectHash

    private fun clearStaleClaimOr(
        result: RestoredAccountMigrationResult,
    ): RestoredAccountMigrationResult =
        try {
            if (pendingClaims.read() != null) {
                pendingClaims.clear()
            }
            result
        } catch (throwable: Throwable) {
            RestoredAccountMigrationResult.Failed(throwable)
        }

    private suspend fun authorisedStateStillMatches(claim: PendingRestoredOwnershipClaim): Boolean {
        val account = accountProvider.currentAccount() ?: return false
        return account.uid == claim.currentUid &&
            !account.isAnonymous && account.hasGoogleProvider &&
            account.googleSubjectHash == claim.currentGoogleSubjectHash &&
            provenance.isRestorePending() && ownerState.isCompleted.first() &&
            ownerState.completedAccountUid.first() == claim.previousOwnerUid &&
            ownerState.completedGoogleSubjectHash.first() == claim.previousGoogleSubjectHash
    }

    private companion object {
        const val MaxUidChars = 128
    }
}

private class FirebaseRestoredAccountMigrationAccountProvider(
    private val auth: FirebaseAuth,
) : RestoredAccountMigrationAccountProvider {
    override fun currentAccount(): RestoredAccountMigrationAccount? = auth.currentUser?.let { user ->
        RestoredAccountMigrationAccount(
            uid = user.uid,
            isAnonymous = user.isAnonymous,
            hasGoogleProvider = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
            googleSubjectHash = resolveGoogleAccountIdentity(user)?.subjectHash,
        )
    }
}

private class PreferencesRestoredAccountMigrationOwnerState(
    private val delegate: OnboardingPreferencesDataSource,
) : RestoredAccountMigrationOwnerState {
    override val isCompleted: Flow<Boolean> = delegate.isCompleted
    override val completedAccountUid: Flow<String?> = delegate.completedAccountUid
    override val completedGoogleSubjectHash: Flow<String?> = delegate.completedGoogleSubjectHash
    override suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?, googleSubjectHash: String?) =
        delegate.setCompletedForAccount(isCompleted, accountUid, googleSubjectHash)
}

private class AndroidRestoredAccountMigrationProvenance(
    private val delegate: AndroidRestoreProvenanceStore,
) : RestoredAccountMigrationProvenance {
    override fun isRestorePending(): Boolean = delegate.isRestorePending()
    override fun clearRestorePending() = delegate.clearRestorePending()
}
