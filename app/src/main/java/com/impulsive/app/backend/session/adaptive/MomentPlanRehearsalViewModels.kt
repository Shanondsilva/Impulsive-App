package com.impulsive.app.backend.session.adaptive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MomentPlanRehearsalLaunchUiState(
    val busy: Boolean = false,
    val message: String? = null,
)

class MomentPlanRehearsalLauncherViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val coordinator =
        AdaptivePhase4Dependencies.rehearsalCoordinator(application)
    private val _state = MutableStateFlow(MomentPlanRehearsalLaunchUiState())
    val state: StateFlow<MomentPlanRehearsalLaunchUiState> = _state.asStateFlow()

    fun startGuided(
        planId: String,
        onStarted: (String) -> Unit,
    ) = start(planId, guided = true, onStarted)

    fun startQuick(
        planId: String,
        onStarted: (String) -> Unit,
    ) = start(planId, guided = false, onStarted)

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun start(
        planId: String,
        guided: Boolean,
        onStarted: (String) -> Unit,
    ) {
        if (_state.value.busy) return
        _state.value = MomentPlanRehearsalLaunchUiState(busy = true)
        viewModelScope.launch {
            try {
                val result = if (guided) {
                    coordinator.startGuided(planId)
                } else {
                    coordinator.startQuick(planId)
                }
                val rehearsalId = result.session?.rehearsal?.rehearsalId
                if (rehearsalId != null) {
                    _state.value = MomentPlanRehearsalLaunchUiState()
                    onStarted(rehearsalId)
                } else {
                    _state.value = MomentPlanRehearsalLaunchUiState(
                        message = when (result.failure) {
                            RehearsalStartFailure.PlanDisabled ->
                                "Enable this Moment Plan before practising it."
                            RehearsalStartFailure.AnotherPracticeIsOpen ->
                                "Finish or leave your open practice first."
                            else -> "Practice could not be started. Please try again."
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(
                    "MomentPractice",
                    "start failed (${error.javaClass.simpleName})",
                )
                _state.value = MomentPlanRehearsalLaunchUiState(
                    message = "Practice could not be started. Please try again.",
                )
            }
        }
    }
}

data class MomentPlanRehearsalUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val plan: MomentPlan? = null,
    val mode: MomentPlanRehearsalMode? = null,
    val stage: Int = 1,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val dismissed: Boolean = false,
    val message: String? = null,
)

class MomentPlanRehearsalViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val coordinator =
        AdaptivePhase4Dependencies.rehearsalCoordinator(application)
    private val rehearsalId = savedStateHandle.get<String>("rehearsalId").orEmpty()
    private val _state = MutableStateFlow(
        MomentPlanRehearsalUiState(
            stage = savedStateHandle[StageKey] ?: 1,
        ),
    )
    val state: StateFlow<MomentPlanRehearsalUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val session = rehearsalId.takeIf { it.isNotBlank() }
                    ?.let { coordinator.reload(it) }
                _state.update {
                    if (session == null) {
                        it.copy(loading = false, missing = true)
                    } else {
                        it.copy(
                            loading = false,
                            missing = false,
                            plan = session.plan,
                            mode = session.rehearsal.mode,
                            completed = session.rehearsal.completedAtMillis != null,
                            dismissed = session.rehearsal.dismissedAtMillis != null,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(
                    "MomentPractice",
                    "reload failed (${error.javaClass.simpleName})",
                )
                _state.update {
                    it.copy(
                        loading = false,
                        missing = true,
                        message = "Practice could not be loaded.",
                    )
                }
            }
        }
    }

    fun nextStage() {
        val next = (_state.value.stage + 1).coerceAtMost(4)
        savedStateHandle[StageKey] = next
        _state.update { it.copy(stage = next) }
    }

    fun previousStage() {
        val previous = (_state.value.stage - 1).coerceAtLeast(1)
        savedStateHandle[StageKey] = previous
        _state.update { it.copy(stage = previous) }
    }

    fun finish() = terminal(complete = true)

    fun leave() = terminal(complete = false)

    private fun terminal(complete: Boolean) {
        if (_state.value.busy || rehearsalId.isBlank()) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = if (complete) {
                    coordinator.complete(rehearsalId)
                } else {
                    coordinator.dismiss(rehearsalId)
                }
                when {
                    complete && result in setOf(
                        RehearsalTerminalResult.Applied,
                        RehearsalTerminalResult.AlreadyCompleted,
                    ) -> _state.update { it.copy(busy = false, completed = true) }
                    !complete && result in setOf(
                        RehearsalTerminalResult.Applied,
                        RehearsalTerminalResult.AlreadyDismissed,
                    ) -> _state.update { it.copy(busy = false, dismissed = true) }
                    else -> _state.update {
                        it.copy(
                            busy = false,
                            message = "That practice update could not be saved.",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(
                    "MomentPractice",
                    "terminal update failed (${error.javaClass.simpleName})",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "That practice update could not be saved.",
                    )
                }
            }
        }
    }

    private companion object {
        const val StageKey = "momentPlanRehearsalStage"
    }
}
