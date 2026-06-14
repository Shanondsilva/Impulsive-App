package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.game.GameHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rhythmTilesDataStore by preferencesDataStore(name = "rhythm_tiles_history")
private val PB = intPreferencesKey("pb")
private val PREV = intPreferencesKey("prev")
private val BEST_COMBO = intPreferencesKey("best_combo")

class RhythmTilesHistoryDataSource(private val context: Context) {
    val history: Flow<GameHistory> = context.rhythmTilesDataStore.data.map { p ->
        GameHistory(
            pb = p[PB] ?: 0,
            prev = p[PREV] ?: 0,
            bestReactionMs = null,
            bestCombo = p[BEST_COMBO] ?: 0,
        )
    }

    suspend fun save(history: GameHistory) {
        context.rhythmTilesDataStore.edit { p ->
            p[PB] = history.pb
            p[PREV] = history.prev
            p[BEST_COMBO] = history.bestCombo
        }
    }
}
