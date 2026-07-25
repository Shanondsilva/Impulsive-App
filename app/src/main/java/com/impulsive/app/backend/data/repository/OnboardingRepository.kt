package com.impulsive.app.backend.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.account.resolveGoogleAccountIdentity
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.restore.AndroidRestoreProvenanceStore
import com.impulsive.app.backend.data.remote.onboarding.FirebaseOnboardingAccountStateDataSource
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.data.remote.onboarding.OnboardingRemoteAccountStateDataSource
import com.impulsive.app.backend.data.remote.onboarding.RemoteOnboardingCompletionResult
import com.impulsive.app.backend.data.remote.onboarding.RemoteOnboardingMarkResult
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

class OnboardingRepository private constructor(
    private val delegate: OnboardingAccountRepositoryDelegate,
) {
    constructor(
        context: Context,
    ) : this(
        delegate = OnboardingAccountRepositoryDelegate(
            localDataSource = PreferencesOnboardingLocalStateDataSource(
                OnboardingPreferencesDataSource(context),
            ),
            remoteDataSource = FirebaseOnboardingAccountStateDataSource(),
            accountProvider = FirebaseOnboardingAccountProvider(),
            restoreProvenance = RestoreProvenance {
                AndroidRestoreProvenanceStore(context.applicationContext).isRestorePending()
            },
            onAuthenticatedOnboardingCompleted = {
                RestoreSnapshotRefreshScheduler.request(context.applicationContext)
            },
        ),
    )

    internal constructor(
        localDataSource: OnboardingLocalStateDataSource,
        remoteDataSource: OnboardingRemoteAccountStateDataSource,
        accountProvider: OnboardingAccountProvider,
        onAuthenticatedOnboardingCompleted: () -> Unit = {},
        restoreProvenance: RestoreProvenance = NoRestoreProvenance,
    ) : this(
        delegate = OnboardingAccountRepositoryDelegate(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            accountProvider = accountProvider,
            onAuthenticatedOnboardingCompleted = onAuthenticatedOnboardingCompleted,
            restoreProvenance = restoreProvenance,
        ),
    )

    val answers: Flow<OnboardingAnswers> = delegate.answers
    val isCompleted: Flow<Boolean> = delegate.isCompleted
    val completedAccountUid: Flow<String?> = delegate.completedAccountUid

    suspend fun setPersonalization(
        name: String,
        avatarId: String,
    ) {
        delegate.setPersonalization(
            name = name,
            avatarId = avatarId,
        )
    }

    suspend fun setInterrupting(selectedOptionIds: List<String>) {
        delegate.setInterrupting(selectedOptionIds)
    }

    suspend fun setTiming(selectedOptionIds: List<String>) {
        delegate.setTiming(selectedOptionIds)
    }

    suspend fun setTriggers(selectedOptionIds: List<String>) {
        delegate.setTriggers(selectedOptionIds)
    }

    suspend fun setWeekOneGoal(selectedOptionId: String?) {
        delegate.setWeekOneGoal(selectedOptionId)
    }

    suspend fun setDailyRelapseUrgeCount(count: Int) {
        delegate.setDailyRelapseUrgeCount(count)
    }

    suspend fun setCompleted(isCompleted: Boolean) {
        delegate.setCompleted(isCompleted)
    }

    suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
    ) {
        delegate.setCompletedForAccount(
            isCompleted = isCompleted,
            accountUid = accountUid,
        )
    }

    suspend fun resolveAuthenticatedOnboarding(): AuthenticatedOnboardingResolution =
        delegate.resolveAuthenticatedOnboarding()

    suspend fun completeOnboardingForCurrentAccount(): CompleteOnboardingResult =
        delegate.completeOnboardingForCurrentAccount()

    suspend fun backfillAuthenticatedCompletionIfNeeded() {
        delegate.backfillAuthenticatedCompletionIfNeeded()
    }

    suspend fun clear() {
        delegate.clear()
    }
}

sealed interface AuthenticatedOnboardingResolution {
    data object Completed : AuthenticatedOnboardingResolution
    data object Incomplete : AuthenticatedOnboardingResolution
    data object NotApplicable : AuthenticatedOnboardingResolution
    data object RemoteCompletedWithoutLocalData : AuthenticatedOnboardingResolution
    data object AccountMismatch : AuthenticatedOnboardingResolution
    data object RestoredSameGoogleIdentityNeedsConfirmation : AuthenticatedOnboardingResolution
    data object RestoredLegacyOwnershipNeedsDriveVerification : AuthenticatedOnboardingResolution
    data object LegacyUnownedLocalData : AuthenticatedOnboardingResolution

    data class RetryableFailure(
        val cause: Throwable?,
    ) : AuthenticatedOnboardingResolution
}

sealed interface CompleteOnboardingResult {
    data object Completed : CompleteOnboardingResult

    data class RetryableFailure(
        val cause: Throwable?,
    ) : CompleteOnboardingResult
}

internal data class CurrentOnboardingAccount(
    val uid: String,
    val isAnonymous: Boolean,
    val googleSubjectHash: String? = null,
)

internal interface OnboardingAccountProvider {
    fun currentAccount(): CurrentOnboardingAccount?
}

internal interface OnboardingLocalStateDataSource {
    val answers: Flow<OnboardingAnswers>
    val isCompleted: Flow<Boolean>
    val completedAccountUid: Flow<String?>

    suspend fun setPersonalization(name: String, avatarId: String)
    suspend fun setInterrupting(selectedOptionIds: List<String>)
    suspend fun setTiming(selectedOptionIds: List<String>)
    suspend fun setTriggers(selectedOptionIds: List<String>)
    suspend fun setWeekOneGoal(selectedOptionId: String?)
    suspend fun setDailyRelapseUrgeCount(count: Int)
    suspend fun setCompleted(isCompleted: Boolean)
    suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?)
    suspend fun clear()
}

private class OnboardingAccountRepositoryDelegate(
    private val localDataSource: OnboardingLocalStateDataSource,
    private val remoteDataSource: OnboardingRemoteAccountStateDataSource,
    private val accountProvider: OnboardingAccountProvider,
    private val onAuthenticatedOnboardingCompleted: () -> Unit = {},
    private val restoreProvenance: RestoreProvenance = NoRestoreProvenance,
) {
    val answers: Flow<OnboardingAnswers> = localDataSource.answers
    val isCompleted: Flow<Boolean> = localDataSource.isCompleted
    val completedAccountUid: Flow<String?> = localDataSource.completedAccountUid

    suspend fun setPersonalization(name: String, avatarId: String) {
        localDataSource.setPersonalization(name, avatarId)
    }

    suspend fun setInterrupting(selectedOptionIds: List<String>) {
        localDataSource.setInterrupting(selectedOptionIds)
    }

    suspend fun setTiming(selectedOptionIds: List<String>) {
        localDataSource.setTiming(selectedOptionIds)
    }

    suspend fun setTriggers(selectedOptionIds: List<String>) {
        localDataSource.setTriggers(selectedOptionIds)
    }

    suspend fun setWeekOneGoal(selectedOptionId: String?) {
        localDataSource.setWeekOneGoal(selectedOptionId)
    }

    suspend fun setDailyRelapseUrgeCount(count: Int) {
        localDataSource.setDailyRelapseUrgeCount(count)
    }

    suspend fun setCompleted(isCompleted: Boolean) {
        localDataSource.setCompleted(isCompleted)
    }

    suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?) {
        localDataSource.setCompletedForAccount(isCompleted, accountUid)
    }

    suspend fun resolveAuthenticatedOnboarding(): AuthenticatedOnboardingResolution {
        val account = accountProvider.currentAccount()
            ?: return AuthenticatedOnboardingResolution.NotApplicable

        if (account.isAnonymous) {
            return AuthenticatedOnboardingResolution.NotApplicable
        }

        val uid = account.uid
        val localCompleted = isCompleted.first()
        val localOwnerUid = completedAccountUid.first()

        if (localCompleted && localOwnerUid == uid) {
            remoteDataSource.markCompleted()
            return AuthenticatedOnboardingResolution.Completed
        }

        if (localCompleted && localOwnerUid != null && localOwnerUid != uid) {
            return AuthenticatedOnboardingResolution.AccountMismatch
        }

        if (localCompleted && localOwnerUid == null) {
            return AuthenticatedOnboardingResolution.LegacyUnownedLocalData
        }

        return when (val remote = remoteDataSource.getCompletion()) {
            RemoteOnboardingCompletionResult.Completed ->
                AuthenticatedOnboardingResolution.RemoteCompletedWithoutLocalData

            RemoteOnboardingCompletionResult.Incomplete ->
                AuthenticatedOnboardingResolution.Incomplete

            RemoteOnboardingCompletionResult.NotApplicable ->
                AuthenticatedOnboardingResolution.NotApplicable

            is RemoteOnboardingCompletionResult.RetryableFailure ->
                AuthenticatedOnboardingResolution.RetryableFailure(remote.cause)
        }
    }

    suspend fun completeOnboardingForCurrentAccount(): CompleteOnboardingResult {
        val account = accountProvider.currentAccount()

        if (account == null || account.isAnonymous) {
            localDataSource.setCompleted(true)
            return CompleteOnboardingResult.Completed
        }

        return when (val remote = remoteDataSource.markCompleted()) {
            RemoteOnboardingMarkResult.Completed -> {
                (localDataSource as? OnboardingGoogleOwnerStateDataSource)?.setCompletedForAccount(
                    isCompleted = true,
                    accountUid = account.uid,
                    googleSubjectHash = account.googleSubjectHash,
                ) ?: localDataSource.setCompletedForAccount(true, account.uid)
                onAuthenticatedOnboardingCompleted()
                CompleteOnboardingResult.Completed
            }

            RemoteOnboardingMarkResult.NotApplicable ->
                CompleteOnboardingResult.RetryableFailure(
                    IllegalStateException(
                        "Authenticated onboarding completion became unavailable.",
                    ),
                )

            is RemoteOnboardingMarkResult.RetryableFailure ->
                CompleteOnboardingResult.RetryableFailure(remote.cause)
        }
    }

    suspend fun backfillAuthenticatedCompletionIfNeeded() {
        val account = accountProvider.currentAccount() ?: return
        if (account.isAnonymous || !isCompleted.first()) return

        val ownerUid = completedAccountUid.first()
        if (ownerUid == account.uid) {
            remoteDataSource.markCompleted()
        }
    }

    suspend fun clear() {
        localDataSource.clear()
    }
}

private class PreferencesOnboardingLocalStateDataSource(
    private val dataSource: OnboardingPreferencesDataSource,
) : OnboardingLocalStateDataSource, OnboardingGoogleOwnerStateDataSource {
    override val answers: Flow<OnboardingAnswers> = dataSource.answers
    override val isCompleted: Flow<Boolean> = dataSource.isCompleted
    override val completedAccountUid: Flow<String?> = dataSource.completedAccountUid
    override val completedGoogleSubjectHash: Flow<String?> = dataSource.completedGoogleSubjectHash

    override suspend fun setPersonalization(name: String, avatarId: String) {
        dataSource.setPersonalization(name, avatarId)
    }

    override suspend fun setInterrupting(selectedOptionIds: List<String>) {
        dataSource.setInterrupting(selectedOptionIds)
    }

    override suspend fun setTiming(selectedOptionIds: List<String>) {
        dataSource.setTiming(selectedOptionIds)
    }

    override suspend fun setTriggers(selectedOptionIds: List<String>) {
        dataSource.setTriggers(selectedOptionIds)
    }

    override suspend fun setWeekOneGoal(selectedOptionId: String?) {
        dataSource.setWeekOneGoal(selectedOptionId)
    }

    override suspend fun setDailyRelapseUrgeCount(count: Int) {
        dataSource.setDailyRelapseUrgeCount(count)
    }

    override suspend fun setCompleted(isCompleted: Boolean) {
        dataSource.setCompleted(isCompleted)
    }

    override suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?) {
        dataSource.setCompletedForAccount(isCompleted, accountUid)
    }

    override suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?, googleSubjectHash: String?) {
        dataSource.setCompletedForAccount(isCompleted, accountUid, googleSubjectHash)
    }

    override suspend fun clear() {
        dataSource.clear()
    }
}

private class FirebaseOnboardingAccountProvider(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : OnboardingAccountProvider {
    override fun currentAccount(): CurrentOnboardingAccount? {
        val user = firebaseAuth.currentUser ?: return null
        return CurrentOnboardingAccount(
            uid = user.uid,
            isAnonymous = user.isAnonymous,
        )
    }
}

internal interface OnboardingGoogleOwnerStateDataSource {
    val completedGoogleSubjectHash: Flow<String?>
    suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?, googleSubjectHash: String?)
}

internal fun interface RestoreProvenance {
    fun isRestorePending(): Boolean
}

private data object NoRestoreProvenance : RestoreProvenance {
    override fun isRestorePending(): Boolean = false
}
