package com.impulsive.app.backend.session.adaptive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FamiliarStepHistoryUiState(
    val history: FamiliarStepHistorySnapshot = FamiliarStepHistorySnapshot(emptyList()),
    val personalSuggestionsEnabled: Boolean = true,
    val loading: Boolean = false,
)

/** Calm-time backend boundary; it intentionally has no Familiar Steps screen dependency. */
class FamiliarStepHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val historyService = AdaptivePhase4Dependencies.familiarStepHistory(application)
    private val controls = AdaptivePhase4Dependencies.familiarStepControls(application)
    private val preferences = AdaptivePhase4Dependencies.preferences(application)
    private val _state = MutableStateFlow(FamiliarStepHistoryUiState())
    val state: StateFlow<FamiliarStepHistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                _state.value = FamiliarStepHistoryUiState(
                    history = historyService.snapshot(),
                    personalSuggestionsEnabled = preferences.get().personalSuggestionsEnabled,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            if (controls.clearAdaptiveHistory() == AdaptiveLifecycleResult.Applied) {
                _state.value = _state.value.copy(
                    history = FamiliarStepHistorySnapshot(emptyList()),
                )
            }
        }
    }

    fun setPersonalSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            controls.setPersonalSuggestionsEnabled(enabled)
            _state.value = _state.value.copy(personalSuggestionsEnabled = enabled)
            if (enabled) refresh()
        }
    }
}
