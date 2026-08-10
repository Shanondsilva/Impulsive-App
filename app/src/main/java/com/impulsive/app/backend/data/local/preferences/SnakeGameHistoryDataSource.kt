package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.game.SnakeGameHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Snake keeps its own store; Reflex history is never reinterpreted as Snake. */
private val Context.snakeGameHistoryDataStore by preferencesDataStore(
    name = "snake_game_history",
)
private val PersonalBestKey = intPreferencesKey("personal_best")
private val PreviousScoreKey = intPreferencesKey("previous_score")

class SnakeGameHistoryDataSource(private val context: Context) {
    val history: Flow<SnakeGameHistory> =
        context.snakeGameHistoryDataStore.data.map { preferences ->
            SnakeGameHistory(
                personalBest = (preferences[PersonalBestKey] ?: 0).coerceAtLeast(0),
                // Absent means "no previous round", which is not the same as zero.
                previousScore = preferences[PreviousScoreKey]?.coerceAtLeast(0),
            )
        }

    suspend fun currentHistory(): SnakeGameHistory = history.first()

    suspend fun save(history: SnakeGameHistory) {
        context.snakeGameHistoryDataStore.edit { preferences ->
            preferences[PersonalBestKey] = history.personalBest

            val previousScore = history.previousScore
            if (previousScore == null) {
                preferences.remove(PreviousScoreKey)
            } else {
                preferences[PreviousScoreKey] = previousScore
            }
        }
    }
}
