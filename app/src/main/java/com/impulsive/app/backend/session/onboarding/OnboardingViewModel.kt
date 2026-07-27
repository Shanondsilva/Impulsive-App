package com.impulsive.app.backend.session.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.account.AccountLocalDataResetCoordinator
import com.impulsive.app.backend.data.account.AccountLocalDataResetFailureStage
import com.impulsive.app.backend.data.account.AccountLocalDataResetResult
import com.impulsive.app.backend.data.repository.AuthenticatedOnboardingResolution
import com.impulsive.app.backend.data.repository.CompleteOnboardingResult
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.restore.AccountBoundRestoreCoordinator
import com.impulsive.app.backend.data.restore.AccountBoundRestoreResult
import com.impulsive.app.backend.data.restore.RestoredAccountMigrationCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRestorePostImportRecoveryCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRestorePostImportRecoveryResult
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CloudRestoreFinalizationRetryMessage = "Your setup is saved, but Impulsive could not finish restoring your data. Try again."

class OnboardingViewModel internal constructor(
    application: Application,
    restoredAccountMigrationOperation: RestoredAccountMigrationOperation,
    cloudRestorePostImportRecoveryOperation:
        CloudRestorePostImportRecoveryOperation,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        restoredAccountMigrationOperation = RestoredAccountMigrationOperation {
            RestoredAccountMigrationCoordinator(application)
                .confirmSameGoogleIdentityAndRestore()
        },
        cloudRestorePostImportRecoveryOperation =
            CloudRestorePostImportRecoveryOperation {
                CloudRestorePostImportRecoveryCoordinator(application)
                    .resumeIfNeeded()
            },
    )

    private val repository = OnboardingRepository(application)
    private val accountBoundRestoreCoordinator = AccountBoundRestoreCoordinator(application)
    private val restoredAccountMigrationController =
        RestoredAccountMigrationUiController(
            scope = viewModelScope,
            operation = restoredAccountMigrationOperation,
        )
    private val accountLocalDataResetCoordinator =
        AccountLocalDataResetCoordinator(application)
    private val cloudRestoreOnboardingRecovery =
        CloudRestoreOnboardingRecovery(
            operation = cloudRestorePostImportRecoveryOperation,
        )
    private var accountResolutionJob: Job? = null
    private var accountRestoreJob: Job? = null
    private var accountLocalDataResetJob: Job? = null
    private var completionJob: Job? = null
    private val _completionState =
        MutableStateFlow<OnboardingCompletionState>(OnboardingCompletionState.Idle)
    private val _accountResolutionState =
        MutableStateFlow<OnboardingAccountResolutionState>(OnboardingAccountResolutionState.Idle)
    private val _accountRestoreState =
        MutableStateFlow<AccountRestoreState>(AccountRestoreState.Idle)
    private val _accountLocalDataResetState =
        MutableStateFlow<AccountLocalDataResetState>(
            AccountLocalDataResetState.Idle,
        )

    val accountRestoreState: StateFlow<AccountRestoreState> =
        _accountRestoreState.asStateFlow()
    internal val restoredAccountMigrationState:
        StateFlow<RestoredAccountMigrationUiState> =
        restoredAccountMigrationController.state
    val accountLocalDataResetState: StateFlow<AccountLocalDataResetState> =
        _accountLocalDataResetState.asStateFlow()
    val accountResolutionState: StateFlow<OnboardingAccountResolutionState> =
        _accountResolutionState.asStateFlow()
    val completionState: StateFlow<OnboardingCompletionState> =
        _completionState.asStateFlow()

    val state: StateFlow<OnboardingState> = combine(
        repository.answers,
        repository.isCompleted,
        repository.completedAccountUid,
    ) { answers, isCompleted, completedAccountUid ->
        OnboardingState(
            answers = answers,
            isCompleted = isCompleted,
            completedAccountUid = completedAccountUid,
            isLoading = false,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingState(),
        )

    fun savePersonalization(
        name: String,
        avatarId: String,
        onSaved: () -> Unit,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            repository.setPersonalization(
                name = trimmedName,
                avatarId = avatarId,
            )
            onSaved()
        }
    }

    fun setMultiSelectAnswer(
        questionId: OnboardingQuestionId,
        selectedOptionIds: List<String>,
    ) {
        viewModelScope.launch {
            when (questionId) {
                OnboardingQuestionId.Interrupting -> repository.setInterrupting(selectedOptionIds)
                OnboardingQuestionId.Timing -> repository.setTiming(selectedOptionIds)
                OnboardingQuestionId.Triggers -> repository.setTriggers(selectedOptionIds)
                OnboardingQuestionId.WeekOneGoal -> Unit
            }
        }
    }

    fun setSingleSelectAnswer(
        questionId: OnboardingQuestionId,
        selectedOptionId: String?,
    ) {
        viewModelScope.launch {
            when (questionId) {
                OnboardingQuestionId.WeekOneGoal -> repository.setWeekOneGoal(selectedOptionId)
                OnboardingQuestionId.Interrupting,
                OnboardingQuestionId.Timing,
                OnboardingQuestionId.Triggers,
                -> Unit
            }
        }
    }

    fun setDailyRelapseUrgeCount(count: Int) {
        viewModelScope.launch {
            repository.setDailyRelapseUrgeCount(count)
        }
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        if (completionJob?.isActive == true) return

        completionJob = viewModelScope.launch {
            if (state.value.answers.name.isBlank()) return@launch

            _completionState.value = OnboardingCompletionState.Saving
            val result =
                cloudRestoreOnboardingRecovery.completeOnboarding {
                    repository.completeOnboardingForCurrentAccount()
                }
            dispatchCloudRestoreOnboardingCompletion(
                result = result,
                onCompletionStateChanged = {
                    _completionState.value = it
                },
                onAccountResolutionStateChanged = {
                    _accountResolutionState.value = it
                },
                onCompleted = onCompleted,
            )
        }
    }

    fun clearCompletionFailure() {
        if (_completionState.value is OnboardingCompletionState.RetryableFailure) {
            _completionState.value = OnboardingCompletionState.Idle
        }
    }


    fun resolveAuthenticatedOnboarding(
        onResolved: (AuthenticatedOnboardingResolution) -> Unit,
    ) {
        if (accountResolutionJob?.isActive == true) return

        accountResolutionJob = viewModelScope.launch {
            _accountResolutionState.value = OnboardingAccountResolutionState.Loading
            val result =
                cloudRestoreOnboardingRecovery
                    .resolveAuthenticatedOnboarding {
                        repository.resolveAuthenticatedOnboarding()
                    }
            if (
                result.resolution is
                AuthenticatedOnboardingResolution.RetryableFailure
            ) {
                _accountResolutionState.value = OnboardingAccountResolutionState.RetryableFailure()
            } else {
                _accountResolutionState.value =
                    when (result.message) {
                        CloudRestorePostImportStartupMessage
                            .RefreshPending ->
                            OnboardingAccountResolutionState
                                .CloudRestoreRefreshPending
                        CloudRestorePostImportStartupMessage
                            .CloudRecoverySetupRequired ->
                            OnboardingAccountResolutionState
                                .CloudRecoverySetupRequired
                        null ->
                            OnboardingAccountResolutionState.Idle
                    }
            }
            onResolved(result.resolution)
        }
    }

    fun clearAccountResolutionFailure() {
        if (
            _accountResolutionState.value is
            OnboardingAccountResolutionState.RetryableFailure ||
            _accountResolutionState.value ==
            OnboardingAccountResolutionState
                .CloudRestoreRefreshPending ||
            _accountResolutionState.value ==
            OnboardingAccountResolutionState
                .CloudRecoverySetupRequired
        ) {
            _accountResolutionState.value = OnboardingAccountResolutionState.Idle
        }
    }

    fun restoreAccountDataForAuthenticatedUser(
        onReady: () -> Unit,
    ) {
        if (accountRestoreJob?.isActive == true) return

        accountRestoreJob = viewModelScope.launch {
            _accountRestoreState.value = AccountRestoreState.Restoring
            when (val result = accountBoundRestoreCoordinator.restoreForCurrentAuthenticatedAccount()) {
                AccountBoundRestoreResult.Restored,
                AccountBoundRestoreResult.ExistingLocalData,
                AccountBoundRestoreResult.NothingToRestore,
                -> {
                    _accountRestoreState.value = AccountRestoreState.Idle
                    onReady()
                }

                AccountBoundRestoreResult.AccountMismatch -> {
                    _accountRestoreState.value = AccountRestoreState.AccountMismatch
                }

                AccountBoundRestoreResult.LegacyUnownedBackup -> {
                    _accountRestoreState.value = AccountRestoreState.LocalBackupUnavailable
                }

                AccountBoundRestoreResult.InvalidBackup,
                is AccountBoundRestoreResult.Failed,
                AccountBoundRestoreResult.NotAuthenticated,
                AccountBoundRestoreResult.GuestNotApplicable,
                -> {
                    _accountRestoreState.value = AccountRestoreState.RetryableFailure()
                }
            }
        }
    }

    fun clearAccountRestoreState() {
        if (_accountRestoreState.value !is AccountRestoreState.Restoring) {
            _accountRestoreState.value = AccountRestoreState.Idle
        }
    }

    internal fun confirmRestoredSameGoogleIdentity(
        onReady: () -> Unit,
        onLegacyCloudVerificationRequired: () -> Unit,
    ) {
        restoredAccountMigrationController.confirm(
            onReady = onReady,
            onLegacyCloudVerificationRequired =
                onLegacyCloudVerificationRequired,
        )
    }

    internal fun dismissRestoredAccountMigrationMessage() {
        restoredAccountMigrationController.dismissMessage()
    }

    fun requestEraseUnusableLocalData() {
        if (
            accountLocalDataResetJob?.isActive == true ||
            _accountLocalDataResetState.value is
                AccountLocalDataResetState.Deleting
        ) {
            return
        }

        val currentUid =
            accountLocalDataResetCoordinator.currentAuthenticatedAccountUid()

        if (currentUid == null) {
            _accountLocalDataResetState.value =
                AccountLocalDataResetState.SessionChanged
            return
        }

        _accountLocalDataResetState.value =
            AccountLocalDataResetState.Confirming(
                expectedAccountUid = currentUid,
            )
    }

    fun cancelEraseUnusableLocalData() {
        if (
            accountLocalDataResetJob?.isActive == true ||
            _accountLocalDataResetState.value is
                AccountLocalDataResetState.Deleting
        ) {
            return
        }

        _accountLocalDataResetState.value =
            AccountLocalDataResetState.Idle
    }

    fun confirmEraseUnusableLocalData() {
        if (accountLocalDataResetJob?.isActive == true) {
            return
        }

        val expectedUid =
            when (
                val currentState =
                    _accountLocalDataResetState.value
            ) {
                is AccountLocalDataResetState.Confirming ->
                    currentState.expectedAccountUid

                is AccountLocalDataResetState.Failed ->
                    currentState.expectedAccountUid

                AccountLocalDataResetState.Idle,
                AccountLocalDataResetState.SessionChanged,
                is AccountLocalDataResetState.Deleting,
                -> null
            }

        if (expectedUid == null) {
            _accountLocalDataResetState.value =
                AccountLocalDataResetState.SessionChanged
            return
        }

        _accountLocalDataResetState.value =
            AccountLocalDataResetState.Deleting(
                expectedAccountUid = expectedUid,
            )

        accountLocalDataResetJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            accountLocalDataResetCoordinator
                                .eraseAndRestart(expectedUid)
                    ) {
                        AccountLocalDataResetResult.RestartRequested -> {
                            /*
                             * Production restartApp() terminates this process.
                             * Returning to Idle is a defensive fallback in case
                             * a device delays process termination.
                             */
                            _accountLocalDataResetState.value =
                                AccountLocalDataResetState.Idle
                        }

                        AccountLocalDataResetResult.AlreadyRunning -> {
                            _accountLocalDataResetState.value =
                                AccountLocalDataResetState.Failed(
                                    expectedAccountUid = expectedUid,
                                    message =
                                        "An erase operation is already running. Please wait and try again.",
                                )
                        }

                        AccountLocalDataResetResult.SessionChanged -> {
                            /*
                             * Never reuse the previous confirmation for a new
                             * authenticated session. Return through a dedicated
                             * non-destructive state and require the user to
                             * begin the confirmation again.
                             */
                            _accountLocalDataResetState.value =
                                AccountLocalDataResetState.SessionChanged
                        }

                        is AccountLocalDataResetResult.Failed -> {
                            val message =
                                when (result.stage) {
                                    AccountLocalDataResetFailureStage
                                        .DeleteLocalData ->
                                        "Could not erase the saved data. Please try again."

                                    AccountLocalDataResetFailureStage
                                        .RestartApp ->
                                        "The saved data was erased, but Impulsive could not restart. Close and reopen the app."
                                }

                            _accountLocalDataResetState.value =
                                AccountLocalDataResetState.Failed(
                                    expectedAccountUid = expectedUid,
                                    message = message,
                                )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    if (
                        _accountLocalDataResetState.value is
                            AccountLocalDataResetState.Deleting
                    ) {
                        _accountLocalDataResetState.value =
                            AccountLocalDataResetState.Idle
                    }

                    throw cancellation
                }
            }
    }

    fun backfillAuthenticatedCompletionIfNeeded() {
        viewModelScope.launch {
            repository.backfillAuthenticatedCompletionIfNeeded()
        }
    }
    fun clearAnswers(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clear()
            onCleared()
        }
    }
}

internal fun interface CloudRestorePostImportRecoveryOperation {
    suspend fun resumeIfNeeded(): CloudRestorePostImportRecoveryResult
}

internal enum class CloudRestorePostImportStartupMessage {
    RefreshPending,
    CloudRecoverySetupRequired,
}

internal sealed interface CloudRestorePostImportStartupDecision {
    data class Continue(
        val message: CloudRestorePostImportStartupMessage? = null,
    ) : CloudRestorePostImportStartupDecision

    data object StartOnboardingWithRestoredData :
        CloudRestorePostImportStartupDecision

    data object AccountMismatch :
        CloudRestorePostImportStartupDecision

    data class Retryable(
        val cause: Throwable?,
    ) : CloudRestorePostImportStartupDecision
}

internal fun CloudRestorePostImportRecoveryResult.toStartupDecision():
    CloudRestorePostImportStartupDecision =
    when (this) {
        CloudRestorePostImportRecoveryResult.NothingPending,
        CloudRestorePostImportRecoveryResult.Finalized,
        CloudRestorePostImportRecoveryResult
            .AuthorizationWithoutCommittedImportCleared,
        -> CloudRestorePostImportStartupDecision.Continue()

        CloudRestorePostImportRecoveryResult.RequiresOnboardingSetup ->
            CloudRestorePostImportStartupDecision
                .StartOnboardingWithRestoredData

        CloudRestorePostImportRecoveryResult.FinalizedRefreshPending ->
            CloudRestorePostImportStartupDecision.Continue(
                CloudRestorePostImportStartupMessage.RefreshPending,
            )

        CloudRestorePostImportRecoveryResult
            .RequiresCloudRecoverySetup ->
            CloudRestorePostImportStartupDecision.Continue(
                CloudRestorePostImportStartupMessage
                    .CloudRecoverySetupRequired,
            )

        CloudRestorePostImportRecoveryResult.RequiresCorrectAccount ->
            CloudRestorePostImportStartupDecision.AccountMismatch

        CloudRestorePostImportRecoveryResult.FinalizationPending ->
            CloudRestorePostImportStartupDecision.Retryable(null)

        is CloudRestorePostImportRecoveryResult.Failed ->
            CloudRestorePostImportStartupDecision.Retryable(cause)
    }

internal data class CloudRestoreAuthenticatedOnboardingResult(
    val resolution: AuthenticatedOnboardingResolution,
    val message: CloudRestorePostImportStartupMessage? = null,
)

internal sealed interface CloudRestoreOnboardingCompletionResult {
    data class Completed(
        val message: CloudRestorePostImportStartupMessage? = null,
    ) : CloudRestoreOnboardingCompletionResult

    data class RetryableFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : CloudRestoreOnboardingCompletionResult

    data object AlreadyRunning :
        CloudRestoreOnboardingCompletionResult
}

internal class CloudRestoreOnboardingRecovery(
    private val operation: CloudRestorePostImportRecoveryOperation,
) {
    private val recoveryMutex = Mutex()
    private val completionMutex = Mutex()
    private var finalizationPending = false
    private var onboardingCompletionRecorded = false

    suspend fun resolveAuthenticatedOnboarding(
        resolveAccount:
            suspend () -> AuthenticatedOnboardingResolution,
    ): CloudRestoreAuthenticatedOnboardingResult {
        val recoveryDecision =
            try {
                recoveryMutex.withLock {
                    operation.resumeIfNeeded()
                        .toStartupDecision()
                        .also { decision ->
                            if (
                                decision ==
                                CloudRestorePostImportStartupDecision
                                    .StartOnboardingWithRestoredData
                            ) {
                                finalizationPending = true
                                onboardingCompletionRecorded = false
                            }
                        }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                CloudRestorePostImportStartupDecision
                    .Retryable(failure)
            }

        return when (recoveryDecision) {
            CloudRestorePostImportStartupDecision
                .StartOnboardingWithRestoredData ->
                CloudRestoreAuthenticatedOnboardingResult(
                    resolution =
                        AuthenticatedOnboardingResolution.Incomplete,
                )

            CloudRestorePostImportStartupDecision.AccountMismatch ->
                CloudRestoreAuthenticatedOnboardingResult(
                    resolution =
                        AuthenticatedOnboardingResolution.AccountMismatch,
                )

            is CloudRestorePostImportStartupDecision.Retryable ->
                CloudRestoreAuthenticatedOnboardingResult(
                    resolution =
                        AuthenticatedOnboardingResolution
                            .RetryableFailure(
                                recoveryDecision.cause,
                            ),
                )

            is CloudRestorePostImportStartupDecision.Continue ->
                CloudRestoreAuthenticatedOnboardingResult(
                    resolution = resolveAccount(),
                    message = recoveryDecision.message,
                )
        }
    }

    suspend fun completeOnboarding(
        completeAccount: suspend () -> CompleteOnboardingResult,
    ): CloudRestoreOnboardingCompletionResult {
        if (!completionMutex.tryLock()) {
            return CloudRestoreOnboardingCompletionResult.AlreadyRunning
        }

        var finalizingRestoredData = false
        return try {
            val completionAlreadyRecorded =
                recoveryMutex.withLock {
                    finalizationPending &&
                        onboardingCompletionRecorded
                }

            if (!completionAlreadyRecorded) {
                when (val completion = completeAccount()) {
                    CompleteOnboardingResult.Completed -> {
                        recoveryMutex.withLock {
                            if (finalizationPending) {
                                onboardingCompletionRecorded = true
                            }
                        }
                    }

                    is CompleteOnboardingResult.RetryableFailure ->
                        return CloudRestoreOnboardingCompletionResult
                            .RetryableFailure(
                                message =
                                    OnboardingCompletionState
                                        .RetryableFailure()
                                        .message,
                                cause = completion.cause,
                            )
                }
            }

            recoveryMutex
                .withLock<CloudRestoreOnboardingCompletionResult> {
                if (!finalizationPending) {
                    CloudRestoreOnboardingCompletionResult.Completed()
                } else {
                    finalizingRestoredData = true
                    when (val recovery = operation.resumeIfNeeded()) {
                        CloudRestorePostImportRecoveryResult.Finalized,
                        CloudRestorePostImportRecoveryResult.NothingPending,
                        CloudRestorePostImportRecoveryResult
                            .AuthorizationWithoutCommittedImportCleared,
                        -> acceptedFinalization()

                        CloudRestorePostImportRecoveryResult
                            .FinalizedRefreshPending ->
                            acceptedFinalization(
                                CloudRestorePostImportStartupMessage
                                    .RefreshPending,
                            )

                        CloudRestorePostImportRecoveryResult
                            .RequiresCloudRecoverySetup ->
                            acceptedFinalization(
                                CloudRestorePostImportStartupMessage
                                    .CloudRecoverySetupRequired,
                            )

                        CloudRestorePostImportRecoveryResult
                            .RequiresCorrectAccount,
                        CloudRestorePostImportRecoveryResult
                            .FinalizationPending,
                        CloudRestorePostImportRecoveryResult
                            .RequiresOnboardingSetup,
                        -> retryableFinalization()

                        is CloudRestorePostImportRecoveryResult.Failed ->
                            retryableFinalization(recovery.cause)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            CloudRestoreOnboardingCompletionResult
                .RetryableFailure(
                    message =
                        if (finalizingRestoredData) {
                            CloudRestoreFinalizationRetryMessage
                        } else {
                            OnboardingCompletionState
                                .RetryableFailure()
                                .message
                        },
                    cause = failure,
                )
        } finally {
            completionMutex.unlock()
        }
    }

    private fun acceptedFinalization(
        message: CloudRestorePostImportStartupMessage? = null,
    ): CloudRestoreOnboardingCompletionResult {
        finalizationPending = false
        onboardingCompletionRecorded = false
        return CloudRestoreOnboardingCompletionResult
            .Completed(message)
    }

    private fun retryableFinalization(
        cause: Throwable? = null,
    ): CloudRestoreOnboardingCompletionResult =
        CloudRestoreOnboardingCompletionResult
            .RetryableFailure(
                message = CloudRestoreFinalizationRetryMessage,
                cause = cause,
            )
}

internal fun dispatchCloudRestoreOnboardingCompletion(
    result: CloudRestoreOnboardingCompletionResult,
    onCompletionStateChanged: (OnboardingCompletionState) -> Unit,
    onAccountResolutionStateChanged:
        (OnboardingAccountResolutionState) -> Unit,
    onCompleted: () -> Unit,
) {
    when (result) {
        is CloudRestoreOnboardingCompletionResult.Completed -> {
            onAccountResolutionStateChanged(
                when (result.message) {
                    CloudRestorePostImportStartupMessage.RefreshPending ->
                        OnboardingAccountResolutionState
                            .CloudRestoreRefreshPending

                    CloudRestorePostImportStartupMessage
                        .CloudRecoverySetupRequired ->
                        OnboardingAccountResolutionState
                            .CloudRecoverySetupRequired

                    null -> OnboardingAccountResolutionState.Idle
                },
            )
            onCompletionStateChanged(OnboardingCompletionState.Idle)
            onCompleted()
        }

        is CloudRestoreOnboardingCompletionResult.RetryableFailure ->
            onCompletionStateChanged(
                OnboardingCompletionState
                    .RetryableFailure(result.message),
            )

        CloudRestoreOnboardingCompletionResult.AlreadyRunning -> Unit
    }
}
