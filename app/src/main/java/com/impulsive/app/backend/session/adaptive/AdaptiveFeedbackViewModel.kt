package com.impulsive.app.backend.session.adaptive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdaptiveFeedbackMode {
    Loading,
    Ready,
    Saving,
    Saved,
    Unavailable,
    RetryableFailure,
}

data class AdaptiveFeedbackUiState(
    val mode: AdaptiveFeedbackMode = AdaptiveFeedbackMode.Loading,
    val intervention: InterventionFamily? = null,
    val dismissed: Boolean = false,
    val selectedFeedback: FeedbackCode? = null,
)

class AdaptiveFeedbackViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val decisionId = savedStateHandle.get<String>("decisionId").orEmpty()
    private val coordinator =
        AdaptivePhase4Dependencies.outcomeCoordinator(application)
    private val operationGuard = AdaptiveOutcomeOperationGuard()
    private val _state = MutableStateFlow(AdaptiveFeedbackUiState())
    val state: StateFlow<AdaptiveFeedbackUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val decision = coordinator.load(decisionId)
                if (
                    decision == null ||
                    decision.startedAtMillis == null ||
                    (
                        decision.completedAtMillis == null &&
                            decision.dismissedAtMillis == null
                        )
                ) {
                    _state.value = AdaptiveFeedbackUiState(
                        mode = AdaptiveFeedbackMode.Unavailable,
                    )
                    return@launch
                }
                val answered = decision.feedbackUpdatedAtMillis != null
                _state.value = AdaptiveFeedbackUiState(
                    mode = if (answered) {
                        AdaptiveFeedbackMode.Saved
                    } else {
                        AdaptiveFeedbackMode.Ready
                    },
                    intervention = decision.assignment.actualIntervention,
                    dismissed = decision.dismissedAtMillis != null,
                    selectedFeedback = decision.feedbackCode.takeIf { answered },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.value = AdaptiveFeedbackUiState(
                    mode = AdaptiveFeedbackMode.RetryableFailure,
                )
            }
        }
    }

    fun submitFeedback(feedbackCode: FeedbackCode) {
        if (!operationGuard.tryStart()) return
        _state.update { it.copy(mode = AdaptiveFeedbackMode.Saving) }
        viewModelScope.launch {
            try {
                when (
                    coordinator.submitFeedback(
                        decisionId = decisionId,
                        feedbackCode = feedbackCode,
                        timestamp = System.currentTimeMillis(),
                    )
                ) {
                    AdaptiveOutcomeResult.Applied,
                    AdaptiveOutcomeResult.Idempotent,
                    -> _state.update {
                        it.copy(
                            mode = AdaptiveFeedbackMode.Saved,
                            selectedFeedback = feedbackCode,
                        )
                    }
                    AdaptiveOutcomeResult.RetryableFailure ->
                        _state.update {
                            it.copy(mode = AdaptiveFeedbackMode.RetryableFailure)
                        }
                    else -> _state.update {
                        it.copy(mode = AdaptiveFeedbackMode.Unavailable)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(mode = AdaptiveFeedbackMode.RetryableFailure)
                }
            } finally {
                operationGuard.clear()
            }
        }
    }

    fun skip() {
        submitFeedback(FeedbackCode.NotProvided)
    }

    fun changeAnswer() {
        _state.update {
            if (
                it.mode == AdaptiveFeedbackMode.Saved ||
                it.mode == AdaptiveFeedbackMode.RetryableFailure
            ) {
                it.copy(mode = AdaptiveFeedbackMode.Ready)
            } else {
                it
            }
        }
    }
}
