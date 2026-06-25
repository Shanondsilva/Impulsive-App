package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeGuideDataStore by preferencesDataStore(name = "home_guide")

/** Remembers whether the one-time home guide has been shown. */
class HomeGuideStore(private val context: Context) {
    val seen: Flow<Boolean> = context.homeGuideDataStore.data.map { it[SeenKey] ?: false }

    suspend fun markSeen() {
        context.homeGuideDataStore.edit { it[SeenKey] = true }
    }

    suspend fun reset() {
        context.homeGuideDataStore.edit { it[SeenKey] = false }
    }

    companion object {
        val SeenKey = booleanPreferencesKey("home_guide_seen")
    }
}
