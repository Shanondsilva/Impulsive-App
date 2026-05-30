package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private const val MaxStoredUrgeEvents = 500
private const val RecordSeparator = ""
private const val FieldSeparator = ""
private val Context.urgeEventDataStore by preferencesDataStore(name = "urge_events")

class UrgeEventDataSource(context: Context) {
    private val dataStore = context.applicationContext.urgeEventDataStore

    val events: Flow<List<UrgeEventRecord>> = dataStore.data.map { preferences ->
        preferences[EventsKey]
            .orEmpty()
            .split(RecordSeparator)
            .mapNotNull { it.decodeUrgeEventOrNull() }
            .sortedByDescending { it.date }
    }

    suspend fun recordEvent(event: UrgeEventRecord) {
        dataStore.edit { preferences ->
            val current = preferences[EventsKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeUrgeEventOrNull() }
                .toMutableList()
            current += event
            preferences[EventsKey] = current
                .sortedByDescending { it.date }
                .take(MaxStoredUrgeEvents)
                .joinToString(RecordSeparator) { it.encode() }
        }
    }

    private fun UrgeEventRecord.encode(): String =
        listOf(date.toString(), source).joinToString(FieldSeparator)

    private fun String.decodeUrgeEventOrNull(): UrgeEventRecord? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.isEmpty()) return null
        return runCatching {
            UrgeEventRecord(
                date = LocalDate.parse(parts[0]),
                source = parts.getOrElse(1) { "unknown" },
            )
        }.getOrNull()
    }

    private companion object {
        val EventsKey = stringPreferencesKey("urge_events")
    }
}
