package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings_prefs")

class AppSettingsPreferencesDataSource(private val context: Context) {
    val hapticsEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { preferences ->
        preferences[HapticsEnabledKey] ?: true
    }

    val soundEffectsEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { preferences ->
        preferences[SoundEffectsEnabledKey] ?: false
    }

    val hideSensitiveNotifications: Flow<Boolean> = context.appSettingsDataStore.data.map { preferences ->
        preferences[HideSensitiveNotificationsKey] ?: false
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[HapticsEnabledKey] = enabled
        }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SoundEffectsEnabledKey] = enabled
        }
    }

    suspend fun setHideSensitiveNotifications(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[HideSensitiveNotificationsKey] = enabled
        }
    }

    private companion object {
        val HapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
        val SoundEffectsEnabledKey = booleanPreferencesKey("sound_effects_enabled")
        val HideSensitiveNotificationsKey = booleanPreferencesKey("hide_sensitive_notifications")
    }
}
