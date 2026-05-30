package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.levelDataStore by preferencesDataStore(name = "level_prefs")
private val CURRENT_LEVEL_KEY = intPreferencesKey("current_level")

class LevelPreferencesDataSource(private val context: Context) {
    val currentLevel: Flow<Int> = context.levelDataStore.data.map { prefs ->
        (prefs[CURRENT_LEVEL_KEY] ?: 1).coerceIn(1, 5)
    }

    suspend fun setLevel(level: Int) {
        context.levelDataStore.edit { it[CURRENT_LEVEL_KEY] = level.coerceIn(1, 5) }
    }
}
