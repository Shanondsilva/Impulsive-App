package com.impulsive.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.data.repository.ImpulsiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RelapseState(
    val isRestoring: Boolean = false,
    val restored: Boolean = false
)

class RelapseViewModel(
    private val repository: ImpulsiveRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RelapseState())
    val state: StateFlow<RelapseState> = _state.asStateFlow()

    // Restore: mark bypass recovered, keep WeeklyTarget unchanged (no punishment)
    fun restoreSanctuary(onDone: () -> Unit) {
        _state.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            repository.markBypassRecovered()
            repository.logEval(
                phase = 4,
                name = "bypass_recovered",
                value = "user_restored_manually"
            )
            _state.update { it.copy(isRestoring = false, restored = true) }
            onDone()
        }
    }

    // Return to Focus: dismiss without restoring (user just wants to go home)
    fun returnToFocus(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.markBypassRecovered()
            onDone()
        }
    }
}
