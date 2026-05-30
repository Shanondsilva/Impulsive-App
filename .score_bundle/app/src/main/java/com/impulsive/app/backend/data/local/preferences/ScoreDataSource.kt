package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

private const val MaxStoredScoreSessions = 50
private const val RecordSeparator = "\u001E"
private const val FieldSeparator = "\u001F"
private val Context.scoreDataStore by preferencesDataStore(name = "score_sessions")

class ScoreDataSource(context: Context) {
    private val dataStore = context.applicationContext.scoreDataStore

    val sessions: Flow<List<ScoreSessionRecord>> = dataStore.data.map { preferences ->
        preferences[SessionsKey]
            .orEmpty()
            .split(RecordSeparator)
            .mapNotNull { encoded -> encoded.decodeScoreSessionOrNull() }
            .sortedByDescending { it.completedAt }
    }

    suspend fun recordSession(session: ScoreSessionRecord) {
        dataStore.edit { preferences ->
            val current = preferences[SessionsKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeScoreSessionOrNull() }
                .toMutableList()
            val alreadyStored = current.any { stored ->
                stored.id == session.id ||
                    (
                        stored.gameType == session.gameType &&
                            stored.score == session.score &&
                            stored.completedAt == session.completedAt
                    )
            }
            if (!alreadyStored) {
                current += session
            }
            preferences[SessionsKey] = current
                .sortedByDescending { it.completedAt }
                .take(MaxStoredScoreSessions)
                .joinToString(RecordSeparator) { it.encode() }
        }
    }

    private fun ScoreSessionRecord.encode(): String = listOf(
        id.toString(),
        gameType.id,
        score.toString(),
        completedAt.toString(),
        durationSec.toString(),
        urgeBefore?.toString().orEmpty(),
        urgeAfter?.toString().orEmpty(),
        outcome.id,
        if (validCompletion) "1" else "0",
    ).joinToString(FieldSeparator)

    private fun String.decodeScoreSessionOrNull(): ScoreSessionRecord? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 9) return null
        return runCatching {
            ScoreSessionRecord(
                id = parts[0].toLong(),
                gameType = ScoreGameType.fromId(parts[1]),
                score = parts[2].toInt(),
                completedAt = LocalDateTime.parse(parts[3]),
                durationSec = parts[4].toInt(),
                urgeBefore = parts[5].takeIf { it.isNotBlank() }?.toIntOrNull(),
                urgeAfter = parts[6].takeIf { it.isNotBlank() }?.toIntOrNull(),
                outcome = ScoreSessionOutcome.fromId(parts[7]),
                validCompletion = parts[8] == "1",
            )
        }.getOrNull()
    }

    private companion object {
        val SessionsKey = stringPreferencesKey("sessions")
    }
}
