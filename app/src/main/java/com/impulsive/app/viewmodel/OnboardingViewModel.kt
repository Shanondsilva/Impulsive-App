package com.impulsive.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.data.db.UserProfile
import com.impulsive.app.data.repository.ImpulsiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val step: Int = 1,
    val baselineSessions: Int = 7,
    val selectedTriggers: Set<String> = emptySet(),
    val identityAnchors: Set<String> = emptySet(),
    val path: String = "",
    val isSaving: Boolean = false
)

class OnboardingViewModel(
    private val repository: ImpulsiveRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setBaseline(sessions: Int) {
        _state.update { it.copy(baselineSessions = sessions) }
    }

    fun toggleTrigger(trigger: String) {
        _state.update { s ->
            val updated = if (trigger in s.selectedTriggers)
                s.selectedTriggers - trigger
            else
                s.selectedTriggers + trigger
            s.copy(selectedTriggers = updated)
        }
    }

    fun skipTriggers() {
        // All triggers active by default — store empty string (interpreted as all)
        _state.update { it.copy(selectedTriggers = emptySet()) }
        nextStep()
    }

    fun toggleIdentityAnchor(anchor: String) {
        _state.update { s ->
            val updated = if (anchor in s.identityAnchors)
                s.identityAnchors - anchor
            else
                s.identityAnchors + anchor
            s.copy(identityAnchors = updated)
        }
    }

    fun setPath(path: String) {
        _state.update { it.copy(path = path) }
    }

    fun nextStep() {
        _state.update { it.copy(step = it.step + 1) }
    }

    fun prevStep() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        val s = _state.value
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val triggers = if (s.selectedTriggers.isEmpty())
                "Bored,Stressed,Lonely,Tired,Habit"
            else
                s.selectedTriggers.joinToString(",")

            val anchors = s.identityAnchors.joinToString(",")
            repository.saveProfile(
                UserProfile(
                    id = 1,
                    baselineSessionsPerWeek = s.baselineSessions,
                    path = s.path,
                    identityAnchor = anchors,
                    triggers = triggers,
                    onboardingComplete = true
                )
            )
            repository.logEval(
                phase = 0,
                name = "onboarding_complete",
                value = "baseline=${s.baselineSessions},path=${s.path},anchors=$anchors"
            )
            _state.update { it.copy(isSaving = false) }
            onDone()
        }
    }
}
