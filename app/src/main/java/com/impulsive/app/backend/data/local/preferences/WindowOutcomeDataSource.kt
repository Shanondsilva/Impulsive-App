package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.release.WindowOutcomeRecord
import com.impulsive.app.backend.domain.model.release.WindowOutcomeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

private const val MaxStoredWindowOutcomes = 400
private const val RecordSeparator = "\u001e"
private const val FieldSeparator = "\u001f"
private val Context.windowOutcomeDataStore by preferencesDataStore(name = "window_outcomes")

class WindowOutcomeDataSource(context: Context) {
    private val dataStore = context.applicationContext.windowOutcomeDataStore

    val outcomes: Flow<List<WindowOutcomeRecord>> = dataStore.data.map { preferences ->
        preferences[OutcomesKey]
            .orEmpty()
            .split(RecordSeparator)
            .mapNotNull { it.decodeWindowOutcomeOrNull() }
            .sortedByDescending { it.windowStart }
    }

    suspend fun markUsed(windowStart: LocalDateTime, now: LocalDateTime) {
        dataStore.edit { preferences ->
            val current = preferences[OutcomesKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeWindowOutcomeOrNull() }
                .toMutableList()
            val existing = current.firstOrNull { it.windowStart == windowStart }
            if (existing?.status == WindowOutcomeStatus.Used) return@edit
            if (existing != null) current.remove(existing)
            current += WindowOutcomeRecord(
                windowStart = windowStart,
                status = WindowOutcomeStatus.Used,
                recordedAt = now,
            )
            preferences[OutcomesKey] = current.encodeAll()
        }
    }

    suspend fun markEndedWindowsSkipped(
        plannedWindowStarts: List<LocalDateTime>,
        windowMinutes: Long,
        now: LocalDateTime,
    ) {
        dataStore.edit { preferences ->
            val current = preferences[OutcomesKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeWindowOutcomeOrNull() }
                .toMutableList()
            val recordedStarts = current.map { it.windowStart }.toSet()
            val newlySkipped = plannedWindowStarts.filter { start ->
                start !in recordedStarts && !now.isBefore(start.plusMinutes(windowMinutes))
            }
            if (newlySkipped.isEmpty()) return@edit
            newlySkipped.forEach { start ->
                current += WindowOutcomeRecord(
                    windowStart = start,
                    status = WindowOutcomeStatus.Skipped,
                    recordedAt = now,
                )
            }
            preferences[OutcomesKey] = current.encodeAll()
        }
    }

    private fun List<WindowOutcomeRecord>.encodeAll(): String =
        sortedByDescending { it.windowStart }
            .take(MaxStoredWindowOutcomes)
            .joinToString(RecordSeparator) { it.encode() }

    private fun WindowOutcomeRecord.encode(): String =
        listOf(
            windowStart.toString(),
            status.name,
            recordedAt.toString(),
        ).joinToString(FieldSeparator)

    private fun String.decodeWindowOutcomeOrNull(): WindowOutcomeRecord? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 3) return null
        return runCatching {
            WindowOutcomeRecord(
                windowStart = LocalDateTime.parse(parts[0]),
                status = WindowOutcomeStatus.valueOf(parts[1]),
                recordedAt = LocalDateTime.parse(parts[2]),
            )
        }.getOrNull()
    }

    private companion object {
        val OutcomesKey = stringPreferencesKey("window_outcomes")
    }
}
