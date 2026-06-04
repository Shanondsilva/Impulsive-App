package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.ServedGamesDataSource
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import kotlinx.coroutines.flow.Flow

class ServedGamesRepository(context: Context) {
    private val dataSource = ServedGamesDataSource(context)

    val served: Flow<List<ScoreGameType>> = dataSource.served

    suspend fun recordServed(game: ScoreGameType) = dataSource.recordServed(game)
}
