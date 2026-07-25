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

class OnboardingViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = OnboardingRepository(application)
    private val accountBoundRestoreCoordinator = AccountBoundRestoreCoordinator(application)
    private val accountLocalDataResetCoordinator =
        AccountLocalDataResetCoordinator(application)
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
            when (repository.completeOnboardingForCurrentAccount()) {
                CompleteOnboardingResult.Completed -> {
                    _completionState.value = OnboardingCompletionState.Idle
                    onCompleted()
                }

                is CompleteOnboardingResult.RetryableFailure -> {
                    _completionState.value = OnboardingCompletionState.RetryableFailure()
                }
            }
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
            val resolution = repository.resolveAuthenticatedOnboarding()
            if (resolution is AuthenticatedOnboardingResolution.RetryableFailure) {
                _accountResolutionState.value = OnboardingAccountResolutionState.RetryableFailure()
            } else {
                _accountResolutionState.value = OnboardingAccountResolutionState.Idle
            }
            onResolved(resolution)
        }
    }

    fun clearAccountResolutionFailure() {
        if (_accountResolutionState.value is OnboardingAccountResolutionState.RetryableFailure) {
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
