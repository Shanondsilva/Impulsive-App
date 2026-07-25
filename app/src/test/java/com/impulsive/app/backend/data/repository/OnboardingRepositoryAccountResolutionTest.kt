package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.remote.onboarding.OnboardingRemoteAccountStateDataSource
import com.impulsive.app.backend.data.remote.onboarding.RemoteOnboardingCompletionResult
import com.impulsive.app.backend.data.remote.onboarding.RemoteOnboardingMarkResult
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRepositoryAccountResolutionTest {
    @Test
    fun remoteCompletedWithoutLocalDataDoesNotFabricateLocalCompletion() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Completed,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.RemoteCompletedWithoutLocalData, result)
        assertFalse(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
        assertEquals(0, local.setCompletedForAccountCalls)
    }

    @Test
    fun remoteIncompleteShowsOnboarding() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.Incomplete, result)
        assertFalse(local.isCompleted.value)
    }

    @Test
    fun remoteFailureExposesRetryableState() = runBlocking {
        val cause = IllegalStateException("network")
        val local = FakeLocalOnboardingDataSource(completed = false)
        val repository = repository(
            local = local,
            remote = FakeRemoteOnboardingDataSource(
                completion = RemoteOnboardingCompletionResult.RetryableFailure(cause),
            ),
            uid = "user-a",
        )

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.RetryableFailure(cause), result)
        assertFalse(local.isCompleted.value)
    }

    @Test
    fun localCompletedWithMatchingUidUsesFastPathAndBackfillsRemote() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = "user-a",
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.Completed, result)
        assertEquals(0, remote.getCalls)
        assertEquals(1, remote.markCalls)
    }

    @Test
    fun restoredLocalAnswersRemainPresentAfterMatchingUidResolution() = runBlocking {
        val restoredAnswers = OnboardingAnswers(
            name = "Ada",
            avatarId = "spark",
            interrupting = listOf("late_night"),
            timing = listOf("evening"),
            triggers = listOf("stress"),
            weekOneGoal = "reduce",
            dailyRelapseUrgeCount = 4,
        )
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = "user-a",
            answers = restoredAnswers,
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.Completed, result)
        assertEquals(restoredAnswers, local.answers.value)
        assertEquals(0, local.setCompletedForAccountCalls)
    }

    @Test
    fun localCompletedWithDifferentUidIsBlockedBeforeRemoteLookup() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = "user-a",
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Completed,
        )
        val repository = repository(local, remote, uid = "user-b")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.AccountMismatch, result)
        assertTrue(local.isCompleted.value)
        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(0, remote.getCalls)
        assertEquals(0, remote.markCalls)
    }

    @Test
    fun legacyUnownedLocalCompletionIsBlockedFromSilentRebinding() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = null,
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.resolveAuthenticatedOnboarding()

        assertEquals(AuthenticatedOnboardingResolution.LegacyUnownedLocalData, result)
        assertTrue(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
        assertEquals(0, local.setCompletedForAccountCalls)
        assertEquals(0, remote.getCalls)
        assertEquals(0, remote.markCalls)
    }

    @Test
    fun backfillMatchingOwnerMarksRemote() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = "user-a",
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        repository.backfillAuthenticatedCompletionIfNeeded()

        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(0, local.setCompletedForAccountCalls)
        assertEquals(1, remote.markCalls)
    }

    @Test
    fun backfillLegacyUnownedCompletionDoesNotClaimOwner() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = null,
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        repository.backfillAuthenticatedCompletionIfNeeded()

        assertTrue(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
        assertEquals(0, local.setCompletedForAccountCalls)
        assertEquals(0, remote.markCalls)
    }

    @Test
    fun backfillDifferentOwnerDoesNothing() = runBlocking {
        val local = FakeLocalOnboardingDataSource(
            completed = true,
            ownerUid = "user-a",
        )
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-b")

        repository.backfillAuthenticatedCompletionIfNeeded()

        assertTrue(local.isCompleted.value)
        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(0, local.setCompletedForAccountCalls)
        assertEquals(0, remote.markCalls)
    }

    @Test
    fun authenticatedCompletionSavesLocalOwnerOnlyAfterRemoteSuccess() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.completeOnboardingForCurrentAccount()

        assertEquals(CompleteOnboardingResult.Completed, result)
        assertTrue(local.isCompleted.value)
        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(1, remote.markCalls)
    }

    @Test
    fun authenticatedCompletionRequestsSnapshotRefreshAfterRemoteAndLocalSuccess() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
        )
        var refreshRequests = 0
        val repository = repository(
            local = local,
            remote = remote,
            uid = "user-a",
            onAuthenticatedOnboardingCompleted = { refreshRequests += 1 },
        )

        val result = repository.completeOnboardingForCurrentAccount()

        assertEquals(CompleteOnboardingResult.Completed, result)
        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(1, refreshRequests)
    }
    @Test
    fun authenticatedCompletionFailureLeavesLocalIncomplete() = runBlocking {
        val cause = IllegalStateException("app check unavailable")
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
            markResults = listOf(RemoteOnboardingMarkResult.RetryableFailure(cause)),
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.completeOnboardingForCurrentAccount()

        assertEquals(CompleteOnboardingResult.RetryableFailure(cause), result)
        assertFalse(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
        assertEquals(1, remote.markCalls)
    }

    @Test
    fun retryAfterCompletionFailureCanSucceed() = runBlocking {
        val cause = IllegalStateException("network")
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
            markResults = listOf(
                RemoteOnboardingMarkResult.RetryableFailure(cause),
                RemoteOnboardingMarkResult.Completed,
            ),
        )
        val repository = repository(local, remote, uid = "user-a")

        val first = repository.completeOnboardingForCurrentAccount()
        val second = repository.completeOnboardingForCurrentAccount()

        assertEquals(CompleteOnboardingResult.RetryableFailure(cause), first)
        assertEquals(CompleteOnboardingResult.Completed, second)
        assertTrue(local.isCompleted.value)
        assertEquals("user-a", local.completedAccountUid.value)
        assertEquals(2, remote.markCalls)
    }

    @Test
    fun remoteMarkNotApplicableForAuthenticatedAccountIsRetryable() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Incomplete,
            markResults = listOf(RemoteOnboardingMarkResult.NotApplicable),
        )
        val repository = repository(local, remote, uid = "user-a")

        val result = repository.completeOnboardingForCurrentAccount()

        assertTrue(result is CompleteOnboardingResult.RetryableFailure)
        assertFalse(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
    }

    @Test
    fun guestCompletionPreservesLocalOnlyBehaviour() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Completed,
        )
        val repository = OnboardingRepository(
            localDataSource = local,
            remoteDataSource = remote,
            accountProvider = FakeAccountProvider(
                CurrentOnboardingAccount(uid = "guest", isAnonymous = true),
            ),
        )

        val completionResult = repository.completeOnboardingForCurrentAccount()
        val resolution = repository.resolveAuthenticatedOnboarding()

        assertEquals(CompleteOnboardingResult.Completed, completionResult)
        assertTrue(local.isCompleted.value)
        assertEquals(null, local.completedAccountUid.value)
        assertEquals(0, remote.markCalls)
        assertEquals(AuthenticatedOnboardingResolution.NotApplicable, resolution)
    }

    @Test
    fun remoteRequestsCarryNoOnboardingAnswers() = runBlocking {
        val local = FakeLocalOnboardingDataSource(completed = false)
        val remote = FakeRemoteOnboardingDataSource(
            completion = RemoteOnboardingCompletionResult.Completed,
        )
        val repository = repository(local, remote, uid = "user-a")

        repository.resolveAuthenticatedOnboarding()
        repository.completeOnboardingForCurrentAccount()

        assertEquals(1, remote.getCalls)
        assertEquals(1, remote.markCalls)
        assertEquals(emptyList<OnboardingAnswers>(), remote.receivedAnswers)
    }

    private fun repository(
        local: FakeLocalOnboardingDataSource,
        remote: FakeRemoteOnboardingDataSource,
        uid: String,
        onAuthenticatedOnboardingCompleted: () -> Unit = {},
    ): OnboardingRepository = OnboardingRepository(
        localDataSource = local,
        remoteDataSource = remote,
        accountProvider = FakeAccountProvider(
            CurrentOnboardingAccount(uid = uid, isAnonymous = false),
        ),
        onAuthenticatedOnboardingCompleted = onAuthenticatedOnboardingCompleted,
    )
}

private class FakeLocalOnboardingDataSource(
    completed: Boolean,
    ownerUid: String? = null,
    answers: OnboardingAnswers = OnboardingAnswers(),
) : OnboardingLocalStateDataSource {
    override val answers = MutableStateFlow(answers)
    override val isCompleted = MutableStateFlow(completed)
    override val completedAccountUid = MutableStateFlow(ownerUid)
    var setCompletedForAccountCalls = 0

    override suspend fun setPersonalization(name: String, avatarId: String) = Unit
    override suspend fun setInterrupting(selectedOptionIds: List<String>) = Unit
    override suspend fun setTiming(selectedOptionIds: List<String>) = Unit
    override suspend fun setTriggers(selectedOptionIds: List<String>) = Unit
    override suspend fun setWeekOneGoal(selectedOptionId: String?) = Unit
    override suspend fun setDailyRelapseUrgeCount(count: Int) = Unit

    override suspend fun setCompleted(isCompleted: Boolean) {
        this.isCompleted.value = isCompleted
        if (!isCompleted) completedAccountUid.value = null
    }

    override suspend fun setCompletedForAccount(isCompleted: Boolean, accountUid: String?) {
        setCompletedForAccountCalls += 1
        this.isCompleted.value = isCompleted
        if (isCompleted && !accountUid.isNullOrBlank()) {
            completedAccountUid.value = accountUid
        } else if (!isCompleted) {
            completedAccountUid.value = null
        }
    }

    override suspend fun clear() {
        isCompleted.value = false
        completedAccountUid.value = null
    }
}

private class FakeRemoteOnboardingDataSource(
    private val completion: RemoteOnboardingCompletionResult,
    private val markResults: List<RemoteOnboardingMarkResult> = listOf(
        RemoteOnboardingMarkResult.Completed,
    ),
) : OnboardingRemoteAccountStateDataSource {
    var getCalls = 0
    var markCalls = 0
    val receivedAnswers = mutableListOf<OnboardingAnswers>()

    override suspend fun getCompletion(): RemoteOnboardingCompletionResult {
        getCalls += 1
        return completion
    }

    override suspend fun markCompleted(): RemoteOnboardingMarkResult {
        markCalls += 1
        return markResults.getOrElse(markCalls - 1) { markResults.last() }
    }
}

private class FakeAccountProvider(
    private val account: CurrentOnboardingAccount?,
) : OnboardingAccountProvider {
    override fun currentAccount(): CurrentOnboardingAccount? = account
}
