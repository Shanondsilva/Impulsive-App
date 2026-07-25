package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.blockedDomainVersionDataStore by preferencesDataStore(
    name = "blocked_domain_defaults",
)

class BlockedDomainVersionDataSource(context: Context) {
    private val dataStore = context.applicationContext.blockedDomainVersionDataStore

    suspend fun readAppliedVersion(): Int =
        dataStore.data.first()[AppliedVersionKey] ?: 0

    suspend fun writeAppliedVersion(version: Int) {
        require(version > 0)

        dataStore.edit { preferences ->
            preferences[AppliedVersionKey] = version
        }
    }

    private companion object {
        val AppliedVersionKey = intPreferencesKey("applied_default_blocklist_version")
    }
}
