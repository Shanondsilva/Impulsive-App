package com.impulsive.app.backend.data.restore

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface AccountBoundRestoreResult {
    data object Restored : AccountBoundRestoreResult
    data object NothingToRestore : AccountBoundRestoreResult
    data object ExistingLocalData : AccountBoundRestoreResult
    data object NotAuthenticated : AccountBoundRestoreResult
    data object GuestNotApplicable : AccountBoundRestoreResult
    data object AccountMismatch : AccountBoundRestoreResult
    data object LegacyUnownedBackup : AccountBoundRestoreResult
    data object InvalidBackup : AccountBoundRestoreResult
    data class Failed(val cause: Throwable?) : AccountBoundRestoreResult
}

class AccountBoundRestoreCoordinator internal constructor(
    private val accountProvider: AccountBoundRestoreAccountProvider,
    private val ownerStateDataSource: AccountBoundRestoreOwnerStateDataSource,
    private val importer: AccountBoundRestoreImporter,
    private val provenance: AccountBoundRestoreProvenance = NoAccountBoundRestoreProvenance,
    private val pendingClaimCleanup: AccountBoundRestorePendingClaimCleanup = NoAccountBoundRestorePendingClaimCleanup,
) {
    constructor(context: Context, firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()) : this(
        accountProvider = FirebaseAccountBoundRestoreAccountProvider(firebaseAuth),
        ownerStateDataSource = OnboardingAccountBoundRestoreOwnerStateDataSource(
            OnboardingPreferencesDataSource(context.applicationContext),
        ),
        importer = RestoreBundleAccountBoundRestoreImporter(
            RestoreBundleImporter(context.applicationContext),
        ),
        provenance = AndroidAccountBoundRestoreProvenance(
            AndroidRestoreProvenanceStore(context.applicationContext),
        ),
        pendingClaimCleanup = AccountBoundRestorePendingClaimCleanup {
            AndroidPendingRestoredOwnershipClaimStore(context.applicationContext).clear()
        },
    )

    suspend fun restoreForCurrentAuthenticatedAccount(): AccountBoundRestoreResult {
        val user = accountProvider.currentAccount() ?: return AccountBoundRestoreResult.NotAuthenticated
        if (user.isAnonymous) return AccountBoundRestoreResult.GuestNotApplicable
        val localCompleted = ownerStateDataSource.isCompleted.first()
        val ownerUid = ownerStateDataSource.completedAccountUid.first()
        if (localCompleted && ownerUid == null) return AccountBoundRestoreResult.LegacyUnownedBackup
        if (ownerUid != null && ownerUid != user.uid) return AccountBoundRestoreResult.AccountMismatch
        if (ownerUid == null) {
            return finishTerminalResult(AccountBoundRestoreResult.NothingToRestore)
        }
        val mapped = when (val result = importer.importIfNeeded(AutoRestoreOwnerProof.ExactUid(user.uid))) {
            AutoRestoreResult.Restored -> AccountBoundRestoreResult.Restored
            AutoRestoreResult.NoBundle -> AccountBoundRestoreResult.NothingToRestore
            AutoRestoreResult.ExistingDataPresent -> AccountBoundRestoreResult.ExistingLocalData
            AutoRestoreResult.InvalidBundle -> AccountBoundRestoreResult.InvalidBackup
            AutoRestoreResult.OwnerMismatch -> AccountBoundRestoreResult.AccountMismatch
            AutoRestoreResult.LegacyUnownedBundle,
            AutoRestoreResult.LegacyOwnerVerificationRequired -> AccountBoundRestoreResult.LegacyUnownedBackup
            is AutoRestoreResult.Failed -> AccountBoundRestoreResult.Failed(result.cause)
        }
        if (
            mapped !in setOf(
                AccountBoundRestoreResult.Restored,
                AccountBoundRestoreResult.NothingToRestore,
                AccountBoundRestoreResult.ExistingLocalData,
            )
        ) {
            return mapped
        }
        return finishTerminalResult(mapped)
    }

    private fun finishTerminalResult(
        result: AccountBoundRestoreResult,
    ): AccountBoundRestoreResult {
        return try {
            pendingClaimCleanup.clear()
            provenance.clearRestorePending()
            result
        } catch (throwable: Throwable) {
            AccountBoundRestoreResult.Failed(throwable)
        }
    }
}

internal data class AccountBoundRestoreAccount(val uid: String, val isAnonymous: Boolean)
internal fun interface AccountBoundRestoreAccountProvider { fun currentAccount(): AccountBoundRestoreAccount? }
internal interface AccountBoundRestoreOwnerStateDataSource {
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>
}
internal fun interface AccountBoundRestoreImporter { suspend fun importIfNeeded(ownerProof: AutoRestoreOwnerProof): AutoRestoreResult }
internal interface AccountBoundRestoreProvenance { fun clearRestorePending() }
internal fun interface AccountBoundRestorePendingClaimCleanup { fun clear() }
private data object NoAccountBoundRestorePendingClaimCleanup : AccountBoundRestorePendingClaimCleanup { override fun clear() = Unit }
private data object NoAccountBoundRestoreProvenance : AccountBoundRestoreProvenance { override fun clearRestorePending() = Unit }
private class FirebaseAccountBoundRestoreAccountProvider(private val firebaseAuth: FirebaseAuth) : AccountBoundRestoreAccountProvider {
    override fun currentAccount(): AccountBoundRestoreAccount? = firebaseAuth.currentUser?.let { AccountBoundRestoreAccount(it.uid, it.isAnonymous) }
}
private class OnboardingAccountBoundRestoreOwnerStateDataSource(private val dataSource: OnboardingPreferencesDataSource) : AccountBoundRestoreOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = dataSource.isCompleted
    override val completedAccountUid: Flow<String?> = dataSource.completedAccountUid
}
private class RestoreBundleAccountBoundRestoreImporter(private val importer: RestoreBundleImporter) : AccountBoundRestoreImporter {
    override suspend fun importIfNeeded(ownerProof: AutoRestoreOwnerProof): AutoRestoreResult = importer.importIfNeeded(ownerProof)
}
private class AndroidAccountBoundRestoreProvenance(private val delegate: AndroidRestoreProvenanceStore) : AccountBoundRestoreProvenance {
    override fun clearRestorePending() = delegate.clearRestorePending()
}
