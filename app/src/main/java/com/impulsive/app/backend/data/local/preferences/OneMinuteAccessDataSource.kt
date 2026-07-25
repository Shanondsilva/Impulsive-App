package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

fun canonicalAccessKey(raw: String): String =
    raw.trim().trimEnd('.').lowercase(Locale.ROOT)

sealed interface TemporaryAccessGrantResult {
    data class Granted(
        val untilEpochMillis: Long,
    ) : TemporaryAccessGrantResult

    data class OnCooldown(
        val remainingMillis: Long,
    ) : TemporaryAccessGrantResult

    data object Disabled : TemporaryAccessGrantResult

    data class Failed(
        val throwable: Throwable,
    ) : TemporaryAccessGrantResult
}

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
        canonicalAccessKey(activeAllowPackage.orEmpty()) == canonicalAccessKey(packageName) &&
            nowEpochMillis < activeAllowUntilEpochMillis

    /** True if [packageName] used its one-minute access too recently to use it again. */
    fun isOnCooldown(
        packageName: String,
        nowEpochMillis: Long,
        cooldownMillis: Long,
    ): Boolean {
        val lastUsed = lastUsedByPackage[canonicalAccessKey(packageName)] ?: return false
        return nowEpochMillis - lastUsed < cooldownMillis
    }

    fun cooldownUntilEpochMillis(
        packageName: String,
        cooldownMillis: Long,
    ): Long? {
        val lastUsed = lastUsedByPackage[canonicalAccessKey(packageName)] ?: return null
        return lastUsed + cooldownMillis
    }

    /** Milliseconds left before [packageName] can use one-minute access again. */
    fun cooldownRemainingMillis(
        packageName: String,
        nowEpochMillis: Long,
        cooldownMillis: Long,
    ): Long {
        val lastUsed = lastUsedByPackage[canonicalAccessKey(packageName)] ?: return 0L
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
            activeAllowPackage = preferences[ActiveAllowPackageKey]?.let(::canonicalAccessKey),
            activeAllowUntilEpochMillis = preferences[ActiveAllowUntilKey] ?: 0L,
            lastUsedByPackage = canonicalizeLastUsed(parseLastUsed(preferences[LastUsedJsonKey])),
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
        val canonicalKey = canonicalAccessKey(packageName)
        val untilEpochMillis = nowEpochMillis + durationMillis
        dataStore.edit { preferences ->
            preferences[ActiveAllowPackageKey] = canonicalKey
            preferences[ActiveAllowUntilKey] = untilEpochMillis
            val updated = canonicalizeLastUsed(parseLastUsed(preferences[LastUsedJsonKey])).toMutableMap()
            updated[canonicalKey] = nowEpochMillis
            preferences[LastUsedJsonKey] = encodeLastUsed(updated)
        }
        ImmediateGrantCache.set(ImmediateGrant(canonicalKey, untilEpochMillis))
    }

    suspend fun grantIfAvailable(
        key: String,
        nowEpochMillis: Long,
        durationMillis: Long = OneMinuteAccessDurationMillis,
    ): TemporaryAccessGrantResult {
        val canonicalKey = canonicalAccessKey(key)
        return try {
            val current = state.first()
            if (!current.enabled) {
                TemporaryAccessGrantResult.Disabled
            } else {
                val remaining = current.cooldownRemainingMillis(
                    canonicalKey,
                    nowEpochMillis,
                    OneMinuteAccessCooldownMillis,
                )
                if (remaining > 0L) {
                    TemporaryAccessGrantResult.OnCooldown(remaining)
                } else {
                    grant(canonicalKey, nowEpochMillis, durationMillis)
                    TemporaryAccessGrantResult.Granted(nowEpochMillis + durationMillis)
                }
            }
        } catch (throwable: Throwable) {
            TemporaryAccessGrantResult.Failed(throwable)
        }
    }

    fun isAllowActiveImmediately(
        key: String,
        nowEpochMillis: Long,
        persistedState: OneMinuteAccessState,
    ): Boolean {
        val canonicalKey = canonicalAccessKey(key)
        val immediate = ImmediateGrantCache.get()
        if (immediate != null) {
            if (nowEpochMillis >= immediate.untilEpochMillis) {
                ImmediateGrantCache.compareAndSet(immediate, null)
            } else if (immediate.key == canonicalKey) {
                return true
            }
        }
        return persistedState.isAllowActive(canonicalKey, nowEpochMillis)
    }

    fun activeAllowUntilEpochMillisImmediately(
        key: String,
        nowEpochMillis: Long,
        persistedState: OneMinuteAccessState,
    ): Long {
        val canonicalKey = canonicalAccessKey(key)
        val immediate = ImmediateGrantCache.get()
        if (immediate != null && immediate.key == canonicalKey && nowEpochMillis < immediate.untilEpochMillis) {
            return immediate.untilEpochMillis
        }
        return if (persistedState.isAllowActive(canonicalKey, nowEpochMillis)) {
            persistedState.activeAllowUntilEpochMillis
        } else {
            0L
        }
    }

    /** Clears any active grant, used once the minute is over. */
    suspend fun clearActiveAllow() {
        dataStore.edit { preferences ->
            preferences.remove(ActiveAllowPackageKey)
            preferences[ActiveAllowUntilKey] = 0L
        }
        ImmediateGrantCache.set(null)
    }

    private fun canonicalizeLastUsed(raw: Map<String, Long>): Map<String, Long> {
        val canonical = mutableMapOf<String, Long>()
        raw.forEach { (key, value) ->
            val canonicalKey = canonicalAccessKey(key)
            canonical[canonicalKey] = maxOf(canonical[canonicalKey] ?: 0L, value)
        }
        return canonical
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
        private val ImmediateGrantCache = AtomicReference<ImmediateGrant?>(null)
    }

    private data class ImmediateGrant(
        val key: String,
        val untilEpochMillis: Long,
    )
}
