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

    data class Failed(
        val cause: Throwable?,
    ) : AccountBoundRestoreResult
}

class AccountBoundRestoreCoordinator internal constructor(
    private val accountProvider: AccountBoundRestoreAccountProvider,
    private val ownerStateDataSource: AccountBoundRestoreOwnerStateDataSource,
    private val importer: AccountBoundRestoreImporter,
) {
    constructor(
        context: Context,
        firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    ) : this(
        accountProvider = FirebaseAccountBoundRestoreAccountProvider(firebaseAuth),
        ownerStateDataSource = OnboardingAccountBoundRestoreOwnerStateDataSource(
            OnboardingPreferencesDataSource(context.applicationContext),
        ),
        importer = RestoreBundleAccountBoundRestoreImporter(
            RestoreBundleImporter(context.applicationContext),
        ),
    )



    suspend fun restoreForCurrentAuthenticatedAccount(): AccountBoundRestoreResult {
        val user = accountProvider.currentAccount()
            ?: return AccountBoundRestoreResult.NotAuthenticated

        if (user.isAnonymous) {
            return AccountBoundRestoreResult.GuestNotApplicable
        }

        val localCompleted = ownerStateDataSource.isCompleted.first()
        val ownerUid = ownerStateDataSource.completedAccountUid.first()

        if (localCompleted && ownerUid == null) {
            return AccountBoundRestoreResult.LegacyUnownedBackup
        }

        if (
            ownerUid != null &&
            ownerUid != user.uid
        ) {
            return AccountBoundRestoreResult.AccountMismatch
        }

        // A verified matching owner may restore the Room payload.
        // ownerUid can be null only when there is no completed restored
        // account-bound state.
        if (ownerUid == null) {
            return AccountBoundRestoreResult.NothingToRestore
        }

        return when (val result = importer.importIfNeeded(
            expectedOwnerUid = user.uid,
        )) {
            AutoRestoreResult.Restored ->
                AccountBoundRestoreResult.Restored

            AutoRestoreResult.NoBundle ->
                AccountBoundRestoreResult.NothingToRestore

            AutoRestoreResult.ExistingDataPresent ->
                AccountBoundRestoreResult.ExistingLocalData

            AutoRestoreResult.InvalidBundle ->
                AccountBoundRestoreResult.InvalidBackup

            AutoRestoreResult.OwnerMismatch ->
                AccountBoundRestoreResult.AccountMismatch

            AutoRestoreResult.LegacyUnownedBundle ->
                AccountBoundRestoreResult.LegacyUnownedBackup

            is AutoRestoreResult.Failed ->
                AccountBoundRestoreResult.Failed(result.cause)
        }
    }
}

internal data class AccountBoundRestoreAccount(
    val uid: String,
    val isAnonymous: Boolean,
)

internal fun interface AccountBoundRestoreAccountProvider {
    fun currentAccount(): AccountBoundRestoreAccount?
}

internal interface AccountBoundRestoreOwnerStateDataSource {
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>
}

internal fun interface AccountBoundRestoreImporter {
    suspend fun importIfNeeded(
        expectedOwnerUid: String,
    ): AutoRestoreResult
}

private class FirebaseAccountBoundRestoreAccountProvider(
    private val firebaseAuth: FirebaseAuth,
) : AccountBoundRestoreAccountProvider {
    override fun currentAccount(): AccountBoundRestoreAccount? =
        firebaseAuth.currentUser?.let { user ->
            AccountBoundRestoreAccount(
                uid = user.uid,
                isAnonymous = user.isAnonymous,
            )
        }
}

private class OnboardingAccountBoundRestoreOwnerStateDataSource(
    private val dataSource: OnboardingPreferencesDataSource,
) : AccountBoundRestoreOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = dataSource.isCompleted
    override val completedAccountUid: Flow<String?> = dataSource.completedAccountUid
}

private class RestoreBundleAccountBoundRestoreImporter(
    private val importer: RestoreBundleImporter,
) : AccountBoundRestoreImporter {
    override suspend fun importIfNeeded(
        expectedOwnerUid: String,
    ): AutoRestoreResult =
        importer.importIfNeeded(
            expectedOwnerUid,
        )
}