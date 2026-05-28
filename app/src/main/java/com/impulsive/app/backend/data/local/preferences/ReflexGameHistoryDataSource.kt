package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.game.GameHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reflexDataStore by preferencesDataStore(name = "reflex_game_history")
private val PB = intPreferencesKey("pb")
private val PREV = intPreferencesKey("prev")
private val BEST_RT = intPreferencesKey("best_rt")
private val BEST_COMBO = intPreferencesKey("best_combo")

class ReflexGameHistoryDataSource(private val context: Context) {
    val history: Flow<GameHistory> = context.reflexDataStore.data.map { p ->
        val rt = p[BEST_RT] ?: -1
        GameHistory(
            pb = p[PB] ?: 0,
            prev = p[PREV] ?: 0,
            bestReactionMs = if (rt < 0) null else rt,
            bestCombo = p[BEST_COMBO] ?: 0,
        )
    }

    suspend fun save(history: GameHistory) {
        context.reflexDataStore.edit { p ->
            p[PB] = history.pb
            p[PREV] = history.prev
            p[BEST_RT] = history.bestReactionMs ?: -1
            p[BEST_COMBO] = history.bestCombo
        }
    }
}
