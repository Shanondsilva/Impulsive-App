package com.impulsive.app.backend.session.protectioncoach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.preferences.ProtectionCoachPreferencesDataSource
import com.impulsive.app.backend.data.repository.protectioncoach.ProtectionCoachSuggestionRepository
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProtectionCoachUiState(
    val loading: Boolean = true,
    val activeTimingSuggestion: ProtectionCoachSuggestion? = null,
)

class ProtectionCoachViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProtectionCoachSuggestionRepository(
        AppDatabase.getInstance(application).protectionCoachSuggestionDao(),
    )
    private val preferences = ProtectionCoachPreferencesDataSource(application)

    val state: StateFlow<ProtectionCoachUiState> =
        repository.observeActive(System.currentTimeMillis())
            .map { suggestions ->
                ProtectionCoachUiState(
                    loading = false,
                    activeTimingSuggestion = suggestions
                        .filter { it.suggestionType in GenuineTimingTypes }
                        .minByOrNull { it.createdAtMillis },
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ProtectionCoachUiState(),
            )

    fun dismiss(suggestionId: String) {
        viewModelScope.launch {
            repository.dismiss(suggestionId, System.currentTimeMillis())
            preferences.dismissTimingSuggestion(System.currentTimeMillis())
        }
    }

    fun suppress(suggestionId: String) {
        viewModelScope.launch {
            repository.suppress(suggestionId, System.currentTimeMillis())
            preferences.suppressTimingSuggestions()
        }
    }

    private companion object {
        val GenuineTimingTypes = setOf(
            ProtectionCoachSuggestionType.CreateEveningWindow,
            ProtectionCoachSuggestionType.CreateMorningWindow,
            ProtectionCoachSuggestionType.StartProtectionEarlier,
        )
    }
}
