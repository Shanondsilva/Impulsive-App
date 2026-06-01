package com.impulsive.app.backend.session.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.UserDataExporter
import com.impulsive.app.backend.data.UserDataManager
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dataSource = AppSettingsPreferencesDataSource(application)

    val state: StateFlow<AppSettingsState> = combine(
        dataSource.hapticsEnabled,
        dataSource.soundEffectsEnabled,
        dataSource.hideSensitiveNotifications,
    ) { hapticsEnabled, soundEffectsEnabled, hideSensitiveNotifications ->
        AppSettingsState(
            hapticsEnabled = hapticsEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            hideSensitiveNotifications = hideSensitiveNotifications,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettingsState(),
    )

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { dataSource.setHapticsEnabled(enabled) }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { dataSource.setSoundEffectsEnabled(enabled) }
    }

    fun setHideSensitiveNotifications(enabled: Boolean) {
        viewModelScope.launch { dataSource.setHideSensitiveNotifications(enabled) }
    }

    fun deleteAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            UserDataManager(getApplication()).deleteAllData()
            onComplete()
        }
    }

    fun exportData(onReady: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val uri = runCatching { UserDataExporter(getApplication()).writeExportFile() }.getOrNull()
            if (uri != null) onReady(uri)
        }
    }
}
