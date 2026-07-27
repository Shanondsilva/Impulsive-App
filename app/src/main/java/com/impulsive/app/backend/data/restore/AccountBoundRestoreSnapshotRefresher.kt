package com.impulsive.app.backend.data.restore

import android.app.backup.BackupManager
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.CancellationException

class AccountBoundRestoreSnapshotRefresher private constructor(
    private val delegate: AccountBoundRestoreSnapshotRefreshDelegate,
) {
    constructor(
        context: Context,
    ) : this(
        delegate = AccountBoundRestoreSnapshotRefreshDelegate(
            accountProvider = FirebaseRestoreSnapshotAccountProvider(),
            ownerStateDataSource = PreferencesRestoreSnapshotOwnerStateDataSource(
                OnboardingPreferencesDataSource(context.applicationContext),
            ),
            writer = RestoreSnapshotWriter { ownerUid, ownerGoogleSubjectHash ->
                RestoreBundleWriter(context.applicationContext)
                    .writeBundle(
                        ownerUid = ownerUid,
                        ownerGoogleSubjectHash = ownerGoogleSubjectHash,
                    )
            },
            backupChangeNotifier = BackupChangeNotifier {
                BackupManager(context.applicationContext).dataChanged()
            },
        ),
    )

    internal constructor(
        accountProvider: RestoreSnapshotAccountProvider,
        ownerStateDataSource: RestoreSnapshotOwnerStateDataSource,
        writer: RestoreSnapshotWriter,
        backupChangeNotifier: BackupChangeNotifier = BackupChangeNotifier {},
    ) : this(
        delegate = AccountBoundRestoreSnapshotRefreshDelegate(
            accountProvider = accountProvider,
            ownerStateDataSource = ownerStateDataSource,
            writer = writer,
            backupChangeNotifier = backupChangeNotifier,
        ),
    )

    suspend fun refresh(): RestoreSnapshotRefreshResult = delegate.refresh()
}

sealed interface RestoreSnapshotRefreshResult {
    data object Written : RestoreSnapshotRefreshResult
    data object NoAuthenticatedAccount : RestoreSnapshotRefreshResult
    data object GuestNotApplicable : RestoreSnapshotRefreshResult
    data object NoOwnedCompletedData : RestoreSnapshotRefreshResult
    data object AccountMismatch : RestoreSnapshotRefreshResult
    data object GoogleIdentityUnavailable : RestoreSnapshotRefreshResult

    data class Failed(
        val cause: Throwable,
    ) : RestoreSnapshotRefreshResult
}

internal data class RestoreSnapshotAccount(
    val uid: String,
    val isAnonymous: Boolean,
    val hasGoogleProvider: Boolean,
    val googleSubjectHash: String?,
)

internal interface RestoreSnapshotAccountProvider {
    fun currentAccount(): RestoreSnapshotAccount?
}

internal interface RestoreSnapshotOwnerStateDataSource {
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>
    val completedGoogleSubjectHash: Flow<String?>
}

internal fun interface RestoreSnapshotWriter {
    suspend fun write(
        ownerUid: String,
        ownerGoogleSubjectHash: String?,
    )
}

internal fun interface BackupChangeNotifier {
    fun dataChanged()
}

private class AccountBoundRestoreSnapshotRefreshDelegate(
    private val accountProvider: RestoreSnapshotAccountProvider,
    private val ownerStateDataSource: RestoreSnapshotOwnerStateDataSource,
    private val writer: RestoreSnapshotWriter,
    private val backupChangeNotifier: BackupChangeNotifier,
) {
    suspend fun refresh(): RestoreSnapshotRefreshResult {
        return try {
            val account = accountProvider.currentAccount()
                ?: return RestoreSnapshotRefreshResult.NoAuthenticatedAccount

            if (account.isAnonymous) {
                return RestoreSnapshotRefreshResult.GuestNotApplicable
            }

            val isCompleted = ownerStateDataSource.isCompleted.first()
            val ownerUid = ownerStateDataSource.completedAccountUid.first()
            val savedGoogleSubjectHash =
                ownerStateDataSource.completedGoogleSubjectHash.first()
            val currentGoogleSubjectHash = account.googleSubjectHash
            val savedValidGoogleSubjectHash =
                savedGoogleSubjectHash?.takeIf(::isValidGoogleSubjectHash)

            if (!isCompleted || ownerUid == null) {
                return RestoreSnapshotRefreshResult.NoOwnedCompletedData
            }

            if (ownerUid != account.uid) {
                return RestoreSnapshotRefreshResult.AccountMismatch
            }

            if (savedGoogleSubjectHash != null && savedValidGoogleSubjectHash == null) {
                return RestoreSnapshotRefreshResult.AccountMismatch
            }

            if (account.hasGoogleProvider && !isValidGoogleSubjectHash(currentGoogleSubjectHash.orEmpty())) {
                return RestoreSnapshotRefreshResult.GoogleIdentityUnavailable
            }

            if (
                savedValidGoogleSubjectHash != null &&
                currentGoogleSubjectHash != null &&
                savedValidGoogleSubjectHash != currentGoogleSubjectHash
            ) {
                return RestoreSnapshotRefreshResult.AccountMismatch
            }

            writer.write(
                ownerUid = account.uid,
                ownerGoogleSubjectHash = if (account.hasGoogleProvider) currentGoogleSubjectHash else null,
            )
            backupChangeNotifier.dataChanged()
            RestoreSnapshotRefreshResult.Written
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            RestoreSnapshotRefreshResult.Failed(throwable)
        }
    }
}

private class FirebaseRestoreSnapshotAccountProvider(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : RestoreSnapshotAccountProvider {
    override fun currentAccount(): RestoreSnapshotAccount? {
        val user = firebaseAuth.currentUser ?: return null
        return RestoreSnapshotAccount(
            uid = user.uid,
            isAnonymous = user.isAnonymous,
            hasGoogleProvider = user.providerData.any { provider ->
                provider.providerId == GoogleAuthProvider.PROVIDER_ID
            },
            googleSubjectHash = resolveGoogleAccountIdentity(user)?.subjectHash,
        )
    }
}

private class PreferencesRestoreSnapshotOwnerStateDataSource(
    private val dataSource: OnboardingPreferencesDataSource,
) : RestoreSnapshotOwnerStateDataSource {
    override val isCompleted: Flow<Boolean> = dataSource.isCompleted
    override val completedAccountUid: Flow<String?> = dataSource.completedAccountUid
    override val completedGoogleSubjectHash: Flow<String?> =
        dataSource.completedGoogleSubjectHash
}