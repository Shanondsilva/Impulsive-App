package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MaxStoredServedGames = 20
private const val ServedSeparator = ","
private val Context.servedGamesDataStore by preferencesDataStore(name = "served_games")

/**
 * Remembers, oldest first, which pivot games were served from the block flow.
 * Used only to stop the same game being served more than a few times in a row.
 */
class ServedGamesDataSource(context: Context) {
    private val dataStore = context.applicationContext.servedGamesDataStore

    val served: Flow<List<ScoreGameType>> = dataStore.data.map { preferences ->
        preferences[ServedKey]
            .orEmpty()
            .split(ServedSeparator)
            .filter { it.isNotBlank() }
            .map { ScoreGameType.fromId(it) }
    }

    suspend fun recordServed(game: ScoreGameType) {
        dataStore.edit { preferences ->
            val current = preferences[ServedKey]
                .orEmpty()
                .split(ServedSeparator)
                .filter { it.isNotBlank() }
                .toMutableList()
            current += game.id
            preferences[ServedKey] = current
                .takeLast(MaxStoredServedGames)
                .joinToString(ServedSeparator)
        }
    }

    private companion object {
        val ServedKey = stringPreferencesKey("served_games")
    }
}
