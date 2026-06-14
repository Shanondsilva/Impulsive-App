package com.impulsive.app.backend.session.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.FocusSessionRepository
import com.impulsive.app.backend.data.repository.FocusSetupRepository
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class FocusSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FocusSessionRepository(application)
    private val focusSetupRepository = FocusSetupRepository(application)

    /** Null = never configured; the UI falls back to the urge-protection list. */
    val configuredFocusBlockedPackages: StateFlow<Set<String>?> =
        focusSetupRepository.configuredBlockedPackages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val session: StateFlow<FocusSessionState?> = repository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** One second ticker so countdown text recomposes while a screen observes it. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDateTime.now(),
    )

    fun startSession(durationMinutes: Int) {
        viewModelScope.launch { repository.startSession(durationMinutes) }
    }

    fun pause() {
        viewModelScope.launch { repository.pause() }
    }

    fun resume() {
        viewModelScope.launch { repository.resume() }
    }

    fun endEarly() {
        viewModelScope.launch { repository.endEarly() }
    }

    fun clearFinishedSession() {
        viewModelScope.launch { repository.clearFinishedSession() }
    }

    fun setFocusBlockedPackages(packageNames: Set<String>) {
        viewModelScope.launch { focusSetupRepository.setBlockedPackages(packageNames) }
    }
}
