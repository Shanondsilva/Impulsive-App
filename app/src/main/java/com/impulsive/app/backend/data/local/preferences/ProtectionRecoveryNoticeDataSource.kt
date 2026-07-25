package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionRecoveryNoticeDataStore by preferencesDataStore(
    name = "protection_recovery_notice",
)

class ProtectionRecoveryNoticeDataSource(
    context: Context,
) {
    private val dataStore =
        context.applicationContext.protectionRecoveryNoticeDataStore

    val lastShownAtMillis: Flow<Long?> =
        dataStore.data.map { preferences ->
            preferences[LastShownAtMillisKey]
        }

    suspend fun markShown(
        nowMillis: Long,
    ) {
        dataStore.edit { preferences ->
            preferences[LastShownAtMillisKey] = nowMillis
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(LastShownAtMillisKey)
        }
    }

    private companion object {
        val LastShownAtMillisKey =
            longPreferencesKey("last_shown_at_millis")
    }
}
