package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.focusSetupDataStore by preferencesDataStore(name = "focus_setup")

class FocusSetupDataSource(context: Context) {
    private val dataStore = context.applicationContext.focusSetupDataStore

    /**
     * Null means the user has never configured a focus list, in which case the
     * caller falls back to the urge-protection list. A stored empty set is a
     * deliberate choice and is returned as an empty set, not null.
     */
    val configuredBlockedPackages: Flow<Set<String>?> = dataStore.data.map { preferences ->
        preferences[BlockedPackagesKey]
    }

    suspend fun setBlockedPackages(packageNames: Set<String>) {
        dataStore.edit { preferences ->
            preferences[BlockedPackagesKey] = packageNames
        }
    }

    private companion object {
        val BlockedPackagesKey = stringSetPreferencesKey("focus_blocked_packages")
    }
}
