package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AdaptiveSupportCyclePreferencesDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    internal suspend fun <T> edit(block: MutablePreferences.() -> T): T {
        var result: Any? = null
        dataStore.edit { preferences ->
            result = preferences.block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    suspend fun clearAll() {
        dataStore.edit(MutablePreferences::clear)
    }

    companion object {
        internal const val FileName = "adaptive_support_cycle.preferences_pb"

        @Volatile
        private var instance: AdaptiveSupportCyclePreferencesDataSource? = null

        fun getInstance(context: Context): AdaptiveSupportCyclePreferencesDataSource =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): AdaptiveSupportCyclePreferencesDataSource {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = {
                    File(context.noBackupFilesDir, "datastore/$FileName").also {
                        check(it.parentFile?.mkdirs() != false)
                    }
                },
            )
            return AdaptiveSupportCyclePreferencesDataSource(store)
        }
    }
}
