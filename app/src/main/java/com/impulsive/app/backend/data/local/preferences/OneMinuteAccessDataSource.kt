package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.oneMinuteAccessDataStore by preferencesDataStore(
    name = "one_minute_access",
)

/**
 * State for the one-minute access feature: a brief, non-extendable window that lets a
 * protected app open for a short time before the pause screen returns.
 */
data class OneMinuteAccessState(
    val enabled: Boolean = true,
    val activeAllowPackage: String? = null,
    val activeAllowUntilEpochMillis: Long = 0L,
    val lastUsedByPackage: Map<String, Long> = emptyMap(),
) {
    /** True while [packageName] still has time left in its active one-minute grant. */
    fun isAllowActive(packageName: String, nowEpochMillis: Long): Boolean =
        activeAllowPackage == packageName && nowEpochMillis < activeAllowUntilEpochMillis

    /** True if [packageName] used its one-minute access too recently to use it again. */
    fun isOnCooldown(
        packageName: String,
        nowEpochMillis: Long,
        cooldownMillis: Long,
    ): Boolean {
        val lastUsed = lastUsedByPackage[packageName] ?: return false
        return nowEpochMillis - lastUsed < cooldownMillis
    }

    /** Milliseconds left before [packageName] can use one-minute access again. */
    fun cooldownRemainingMillis(
        packageName: String,
        nowEpochMillis: Long,
        cooldownMillis: Long,
    ): Long {
        val lastUsed = lastUsedByPackage[packageName] ?: return 0L
        val remaining = cooldownMillis - (nowEpochMillis - lastUsed)
        return if (remaining > 0L) remaining else 0L
    }
}

class OneMinuteAccessDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.oneMinuteAccessDataStore

    val state: Flow<OneMinuteAccessState> = dataStore.data.map { preferences ->
        OneMinuteAccessState(
            enabled = preferences[EnabledKey] ?: true,
            activeAllowPackage = preferences[ActiveAllowPackageKey],
            activeAllowUntilEpochMillis = preferences[ActiveAllowUntilKey] ?: 0L,
            lastUsedByPackage = parseLastUsed(preferences[LastUsedJsonKey]),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[EnabledKey] = enabled
        }
    }

    /** Grants a one-minute access for [packageName] and records the use time for cooldown. */
    suspend fun grant(
        packageName: String,
        nowEpochMillis: Long,
        durationMillis: Long = OneMinuteAccessDurationMillis,
    ) {
        dataStore.edit { preferences ->
            preferences[ActiveAllowPackageKey] = packageName
            preferences[ActiveAllowUntilKey] = nowEpochMillis + durationMillis
            val updated = parseLastUsed(preferences[LastUsedJsonKey]).toMutableMap()
            updated[packageName] = nowEpochMillis
            preferences[LastUsedJsonKey] = encodeLastUsed(updated)
        }
    }

    /** Clears any active grant, used once the minute is over. */
    suspend fun clearActiveAllow() {
        dataStore.edit { preferences ->
            preferences.remove(ActiveAllowPackageKey)
            preferences[ActiveAllowUntilKey] = 0L
        }
    }

    private fun parseLastUsed(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, Long>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.getLong(key)
            }
            result.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun encodeLastUsed(map: Map<String, Long>): String {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    companion object {
        const val OneMinuteAccessDurationMillis = 45_000L
        const val OneMinuteAccessCooldownMillis = 15 * 60_000L

        private val EnabledKey = booleanPreferencesKey("one_minute_access_enabled")
        private val ActiveAllowPackageKey = stringPreferencesKey("active_allow_package")
        private val ActiveAllowUntilKey = longPreferencesKey("active_allow_until")
        private val LastUsedJsonKey = stringPreferencesKey("last_used_by_package")
    }
}
