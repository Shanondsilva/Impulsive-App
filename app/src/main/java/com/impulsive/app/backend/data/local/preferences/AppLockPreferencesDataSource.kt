package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_prefs")

/** Failure count and lockout window for the app-lock PIN. */
data class AppLockAttemptState(
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long = 0L,
) {
    fun isLocked(nowEpochMillis: Long): Boolean = nowEpochMillis < lockedUntilEpochMillis

    fun remainingMillis(nowEpochMillis: Long): Long =
        (lockedUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L)
}

class AppLockPreferencesDataSource(private val context: Context) {

    val enabled: Flow<Boolean> = context.appLockDataStore.data.map { it[EnabledKey] ?: false }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[EnabledKey] = value
            if (!value) {
                prefs.remove(PinHashKey)
                prefs.remove(PinSaltKey)
            }
        }
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        context.appLockDataStore.edit { prefs ->
            prefs[PinSaltKey] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[PinHashKey] = Base64.encodeToString(hash, Base64.NO_WRAP)
            prefs[EnabledKey] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.appLockDataStore.data.first()
        val saltB64 = prefs[PinSaltKey] ?: return false
        val hashB64 = prefs[PinHashKey] ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hash(pin, salt)
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    suspend fun clearPin() {
        context.appLockDataStore.edit { prefs ->
            prefs.remove(PinHashKey)
            prefs.remove(PinSaltKey)
            prefs[EnabledKey] = false
        }
    }

    val attemptState: Flow<AppLockAttemptState> = context.appLockDataStore.data.map { prefs ->
        AppLockAttemptState(
            failedAttempts = prefs[FailedAttemptsKey] ?: 0,
            lockedUntilEpochMillis = prefs[LockedUntilKey] ?: 0L,
        )
    }

    suspend fun currentAttemptState(): AppLockAttemptState = attemptState.first()

    /** Records a wrong PIN and returns the updated state, locking after repeated failures. */
    suspend fun recordFailedAttempt(nowEpochMillis: Long): AppLockAttemptState {
        var updated = AppLockAttemptState()
        context.appLockDataStore.edit { prefs ->
            val attempts = (prefs[FailedAttemptsKey] ?: 0) + 1
            prefs[FailedAttemptsKey] = attempts
            val lockedUntil = if (attempts % MaxAttemptsBeforeLockout == 0) {
                val multiplier = (attempts / MaxAttemptsBeforeLockout).coerceIn(1, MaxLockoutMultiplier)
                nowEpochMillis + LockoutBaseMillis * multiplier
            } else {
                prefs[LockedUntilKey] ?: 0L
            }
            prefs[LockedUntilKey] = lockedUntil
            updated = AppLockAttemptState(attempts, lockedUntil)
        }
        return updated
    }

    /** Clears the failure count and lockout, after a correct PIN or biometric unlock. */
    suspend fun resetAttempts() {
        context.appLockDataStore.edit { prefs ->
            prefs.remove(FailedAttemptsKey)
            prefs.remove(LockedUntilKey)
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val MaxAttemptsBeforeLockout = 5
        const val LockoutBaseMillis = 30_000L
        const val MaxLockoutMultiplier = 10

        val EnabledKey = booleanPreferencesKey("app_lock_enabled")
        val PinHashKey = stringPreferencesKey("app_lock_pin_hash")
        val PinSaltKey = stringPreferencesKey("app_lock_pin_salt")
        val FailedAttemptsKey = intPreferencesKey("app_lock_failed_attempts")
        val LockedUntilKey = longPreferencesKey("app_lock_locked_until")
    }
}
