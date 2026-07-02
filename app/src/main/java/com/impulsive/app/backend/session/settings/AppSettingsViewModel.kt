package com.impulsive.app.backend.session.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.UserDataExporter
import com.impulsive.app.backend.data.UserDataManager
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.repository.AuthRepositoryFactory
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
            // Clear the Firebase/Facebook session first so the post-restart
            // login screen actually shows instead of auto-advancing on a
            // lingering signed-in user.
            runCatching { AuthRepositoryFactory.create(getApplication()).signOut() }
            UserDataManager(getApplication()).deleteAllData()
            onComplete()
        }
    }

    fun exportData(destinationUri: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = runCatching {
                UserDataExporter(getApplication()).writeExportToUri(destinationUri)
            }.getOrDefault(false)

            onComplete(success)
        }
    }

    fun cleanupLegacyExportFiles() {
        viewModelScope.launch {
            runCatching {
                UserDataExporter(getApplication()).deleteLegacyTemporaryExportFiles()
            }
        }
    }
}
