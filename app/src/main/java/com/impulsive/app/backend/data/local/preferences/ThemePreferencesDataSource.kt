package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.core.util.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

class ThemePreferencesDataSource(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        val raw = prefs[THEME_MODE_KEY] ?: ThemeMode.AsPerTime.name
        runCatching { ThemeMode.valueOf(raw) }
            .getOrDefault(ThemeMode.AsPerTime)
            .takeUnless { it == ThemeMode.System }
            ?: ThemeMode.AsPerTime
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        val modeToStore = if (mode == ThemeMode.System) ThemeMode.AsPerTime else mode
        context.themeDataStore.edit { it[THEME_MODE_KEY] = modeToStore.name }
    }
}
