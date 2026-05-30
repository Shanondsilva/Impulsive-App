package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.ScoreDataSource
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import kotlinx.coroutines.flow.Flow

class ScoreRepository(context: Context) {
    private val dataSource = ScoreDataSource(context)

    val sessions: Flow<List<ScoreSessionRecord>> = dataSource.sessions

    suspend fun recordSession(session: ScoreSessionRecord) {
        dataSource.recordSession(session)
    }
}
