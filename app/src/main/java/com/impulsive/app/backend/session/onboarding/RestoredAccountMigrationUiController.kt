package com.impulsive.app.backend.session.onboarding

import com.impulsive.app.backend.data.restore.RestoredAccountMigrationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal fun interface RestoredAccountMigrationOperation {
    suspend fun confirmSameGoogleIdentityAndRestore(): RestoredAccountMigrationResult
}

internal class RestoredAccountMigrationUiController(
    private val scope: CoroutineScope,
    private val operation: RestoredAccountMigrationOperation,
) {
    private var migrationJob: Job? = null
    private val _state = MutableStateFlow<RestoredAccountMigrationUiState>(
        RestoredAccountMigrationUiState.Idle,
    )
    val state: StateFlow<RestoredAccountMigrationUiState> = _state.asStateFlow()

    fun confirm(
        onReady: () -> Unit,
        onLegacyCloudVerificationRequired: () -> Unit,
    ) {
        if (migrationJob?.isActive == true) return
        _state.value = RestoredAccountMigrationUiState.Restoring
        migrationJob = scope.launch {
            try {
                when (operation.confirmSameGoogleIdentityAndRestore()) {
                    RestoredAccountMigrationResult.Migrated,
                    RestoredAccountMigrationResult.ClaimedWithoutAutomaticBundle,
                    -> {
                        _state.value = RestoredAccountMigrationUiState.Idle
                        onReady()
                    }

                    RestoredAccountMigrationResult.MigratedRefreshPending -> {
                        _state.value = RestoredAccountMigrationUiState.RefreshPending
                        onReady()
                    }

                    RestoredAccountMigrationResult.LegacyCloudVerificationRequired -> {
                        _state.value =
                            RestoredAccountMigrationUiState.LegacyCloudVerificationRequired
                        onLegacyCloudVerificationRequired()
                    }

                    RestoredAccountMigrationResult.AlreadyRunning -> {
                        _state.value = RestoredAccountMigrationUiState.Restoring
                    }

                    RestoredAccountMigrationResult.NotAuthenticated,
                    RestoredAccountMigrationResult.GuestNotApplicable,
                    RestoredAccountMigrationResult.RestoreNotPending,
                    RestoredAccountMigrationResult.OwnershipChanged,
                    -> {
                        _state.value = RestoredAccountMigrationUiState.OwnershipChanged
                    }

                    RestoredAccountMigrationResult.ExistingLocalData -> {
                        _state.value = RestoredAccountMigrationUiState.ExistingLocalData
                    }

                    RestoredAccountMigrationResult.InvalidBackup -> {
                        _state.value = RestoredAccountMigrationUiState.InvalidBackup
                    }

                    is RestoredAccountMigrationResult.Failed -> {
                        _state.value = RestoredAccountMigrationUiState.Failed(
                            message =
                                "Impulsive couldn't finish restoring your saved data. " +
                                    "Nothing was erased. Please try again.",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                _state.value = RestoredAccountMigrationUiState.Idle
                throw cancellation
            }
        }
    }

    fun dismissMessage() {
        when (_state.value) {
            RestoredAccountMigrationUiState.RefreshPending,
            is RestoredAccountMigrationUiState.Failed,
            -> _state.value = RestoredAccountMigrationUiState.Idle

            RestoredAccountMigrationUiState.Idle,
            RestoredAccountMigrationUiState.Restoring,
            RestoredAccountMigrationUiState.LegacyCloudVerificationRequired,
            RestoredAccountMigrationUiState.OwnershipChanged,
            RestoredAccountMigrationUiState.ExistingLocalData,
            RestoredAccountMigrationUiState.InvalidBackup,
            -> Unit
        }
    }
}
