package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.release.TaperHistoryEntry
import com.impulsive.app.backend.domain.model.release.TaperStoreState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

private const val MaxStoredTaperHistoryEntries = 60
private const val RecordSeparator = "\u001e"
private const val FieldSeparator = "\u001f"
private val Context.taperDataStore by preferencesDataStore(name = "taper_preferences")

class TaperPreferencesDataSource(context: Context) {
    private val dataStore = context.applicationContext.taperDataStore

    val state: Flow<TaperStoreState> = dataStore.data.map { preferences ->
        TaperStoreState(
            lastAcceptedAt = preferences[LastAcceptedAtKey]?.toLocalDateTimeOrNull(),
            lastDeclinedAt = preferences[LastDeclinedAtKey]?.toLocalDateTimeOrNull(),
            proposalsDisabled = preferences[ProposalsDisabledKey] ?: false,
            history = preferences[HistoryKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeTaperHistoryEntryOrNull() }
                .sortedByDescending { it.date },
        )
    }

    suspend fun recordAccepted(
        fromCount: Int,
        toCount: Int,
        acceptedAt: LocalDateTime,
    ) {
        dataStore.edit { preferences ->
            preferences[LastAcceptedAtKey] = acceptedAt.toString()
            val current = preferences[HistoryKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeTaperHistoryEntryOrNull() }
                .toMutableList()
            current += TaperHistoryEntry(
                date = acceptedAt.toLocalDate(),
                fromCount = fromCount,
                toCount = toCount,
            )
            preferences[HistoryKey] = current
                .sortedByDescending { it.date }
                .take(MaxStoredTaperHistoryEntries)
                .joinToString(RecordSeparator) { it.encode() }
        }
    }

    suspend fun recordDeclined(declinedAt: LocalDateTime) {
        dataStore.edit { preferences ->
            preferences[LastDeclinedAtKey] = declinedAt.toString()
        }
    }

    suspend fun setProposalsDisabled(disabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ProposalsDisabledKey] = disabled
        }
    }

    private fun TaperHistoryEntry.encode(): String =
        listOf(
            date.toString(),
            fromCount.toString(),
            toCount.toString(),
        ).joinToString(FieldSeparator)

    private fun String.decodeTaperHistoryEntryOrNull(): TaperHistoryEntry? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 3) return null
        return runCatching {
            TaperHistoryEntry(
                date = LocalDate.parse(parts[0]),
                fromCount = parts[1].toInt(),
                toCount = parts[2].toInt(),
            )
        }.getOrNull()
    }

    private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
        runCatching { LocalDateTime.parse(this) }.getOrNull()

    private companion object {
        val LastAcceptedAtKey = stringPreferencesKey("taper_last_accepted_at")
        val LastDeclinedAtKey = stringPreferencesKey("taper_last_declined_at")
        val HistoryKey = stringPreferencesKey("taper_history")
        val ProposalsDisabledKey = booleanPreferencesKey("taper_proposals_disabled")
    }
}
