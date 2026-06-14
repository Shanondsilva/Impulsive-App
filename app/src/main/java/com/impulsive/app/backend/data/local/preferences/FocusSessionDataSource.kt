package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

private const val FieldSeparator = "\u001f"
private val Context.focusSessionDataStore by preferencesDataStore(name = "focus_session")

class FocusSessionDataSource(context: Context) {
    private val dataStore = context.applicationContext.focusSessionDataStore

    val session: Flow<FocusSessionState?> = dataStore.data.map { preferences ->
        preferences[SessionKey]?.decodeFocusSessionOrNull()
    }

    suspend fun saveSession(session: FocusSessionState) {
        dataStore.edit { preferences ->
            preferences[SessionKey] = session.encode()
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(SessionKey)
        }
    }

    private fun FocusSessionState.encode(): String = listOf(
        sessionId,
        durationMinutes.toString(),
        startedAt.toString(),
        phase.name,
        pausedAt?.toString().orEmpty(),
        totalPausedSeconds.toString(),
        interruptionCount.toString(),
        endedAt?.toString().orEmpty(),
    ).joinToString(FieldSeparator)

    private fun String.decodeFocusSessionOrNull(): FocusSessionState? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 8) return null
        return runCatching {
            FocusSessionState(
                sessionId = parts[0],
                durationMinutes = parts[1].toInt(),
                startedAt = LocalDateTime.parse(parts[2]),
                phase = FocusSessionPhase.valueOf(parts[3]),
                pausedAt = parts[4].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) },
                totalPausedSeconds = parts[5].toLong(),
                interruptionCount = parts[6].toInt(),
                endedAt = parts[7].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) },
            )
        }.getOrNull()
    }

    private companion object {
        val SessionKey = stringPreferencesKey("active_focus_session")
    }
}
