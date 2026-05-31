package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionWindowNotificationDataStore by preferencesDataStore(
    name = "protection_window_notifications",
)

data class ProtectionWindowNotificationState(
    val lastPauseWindowKey: String? = null,
    val lastResumeWindowKey: String? = null,
)

class ProtectionWindowNotificationDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.protectionWindowNotificationDataStore

    val state: Flow<ProtectionWindowNotificationState> = dataStore.data.map { preferences ->
        ProtectionWindowNotificationState(
            lastPauseWindowKey = preferences[LastPauseWindowKey],
            lastResumeWindowKey = preferences[LastResumeWindowKey],
        )
    }

    suspend fun markPauseNotified(windowKey: String) {
        dataStore.edit { preferences ->
            preferences[LastPauseWindowKey] = windowKey
        }
    }

    suspend fun markResumeNotified(windowKey: String) {
        dataStore.edit { preferences ->
            preferences[LastResumeWindowKey] = windowKey
        }
    }

    private companion object {
        val LastPauseWindowKey = stringPreferencesKey("last_pause_window_key")
        val LastResumeWindowKey = stringPreferencesKey("last_resume_window_key")
    }
}
