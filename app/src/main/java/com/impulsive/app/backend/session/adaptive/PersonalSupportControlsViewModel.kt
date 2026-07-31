package com.impulsive.app.backend.session.adaptive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonalSupportControlsUiState(
    val busy: Boolean = false,
    val completionMessage: String? = null,
    val errorMessage: String? = null,
)

class PersonalSupportControlsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val resetCoordinator =
        AdaptivePhase4Dependencies.resetCoordinator(application)
    private val _state = MutableStateFlow(PersonalSupportControlsUiState())
    val state: StateFlow<PersonalSupportControlsUiState> = _state.asStateFlow()

    fun resetPersonalLearning() = runReset(
        operation = "reset personal learning",
        successMessage = "Personal learning was reset.",
        reset = resetCoordinator::resetPersonalLearning,
    )

    fun deleteAllMomentData() = runReset(
        operation = "delete all Moment data",
        successMessage = "Moment data was deleted.",
        reset = resetCoordinator::clearAllAdaptiveData,
    )

    fun clearMessages() {
        _state.update {
            it.copy(completionMessage = null, errorMessage = null)
        }
    }

    private fun runReset(
        operation: String,
        successMessage: String,
        reset: suspend () -> AdaptiveLifecycleResult,
    ) {
        if (_state.value.busy) return
        _state.value = PersonalSupportControlsUiState(busy = true)
        viewModelScope.launch {
            try {
                when (reset()) {
                    AdaptiveLifecycleResult.Applied,
                    AdaptiveLifecycleResult.Idempotent,
                    -> _state.value = PersonalSupportControlsUiState(
                        completionMessage = successMessage,
                    )
                    else -> _state.value = PersonalSupportControlsUiState(
                        errorMessage = "That change could not be completed. Please try again.",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(
                    "PersonalSupport",
                    "$operation failed (${error.javaClass.simpleName})",
                )
                _state.value = PersonalSupportControlsUiState(
                    errorMessage = "That change could not be completed. Please try again.",
                )
            }
        }
    }
}
