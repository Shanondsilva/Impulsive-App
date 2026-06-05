package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.GameAccessState
import com.impulsive.app.backend.data.local.preferences.GameStorePreferencesDataSource
import com.impulsive.app.backend.domain.model.store.GameAccess
import com.impulsive.app.backend.domain.model.store.GameStoreCatalog
import kotlinx.coroutines.flow.Flow

sealed interface StoreResult {
    data object Success : StoreResult
    data object NotEnoughPoints : StoreResult
    data object Unavailable : StoreResult
}

class GameStoreManager(context: Context) {
    private val ds = GameStorePreferencesDataSource(context.applicationContext)

    val spendablePoints: Flow<Int> = ds.spendablePoints
    val lifetimePoints: Flow<Int> = ds.lifetimePoints
    val dailyEarned = ds.dailyEarned
    val accessByGame = ds.accessByGame

    suspend fun recordPlay(gameId: String, won: Boolean) {
        val state = ds.accessFor(gameId)
        ds.recordGlobalWinStreak(
            won = won,
            pointsPerTwoWinStreak = GameStoreCatalog.TwoWinStreakControlPoints,
        )
        if (state.access == GameAccess.RENTED) {
            val left = state.playsLeft - 1
            if (left <= 0) {
                ds.setAccess(gameId, GameAccessState(GameAccess.LOCKED, 0))
            } else {
                ds.setAccess(gameId, GameAccessState(GameAccess.RENTED, left))
            }
        }
    }

    suspend fun tryAwardWeekly(key: String, points: Int): Boolean = ds.tryAwardWeekly(key, points)

    suspend fun buy(gameId: String): StoreResult {
        val game = GameStoreCatalog.byId(gameId) ?: return StoreResult.Unavailable
        if (!ds.trySpend(game.buyPrice)) return StoreResult.NotEnoughPoints
        ds.setAccess(gameId, GameAccessState(GameAccess.OWNED, 0))
        return StoreResult.Success
    }

    suspend fun rent(gameId: String): StoreResult {
        val game = GameStoreCatalog.byId(gameId) ?: return StoreResult.Unavailable
        if (!ds.trySpend(game.rentPrice)) return StoreResult.NotEnoughPoints
        ds.setAccess(gameId, GameAccessState(GameAccess.RENTED, GameStoreCatalog.RentPlays))
        return StoreResult.Success
    }
}
