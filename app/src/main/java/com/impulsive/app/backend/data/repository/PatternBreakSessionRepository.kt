package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.PatternBreakSessionDataSource
import com.impulsive.app.backend.domain.model.tasks.PatternBreakSession

class PatternBreakSessionRepository(
    context: Context,
) {
    private val dataSource = PatternBreakSessionDataSource(context)

    suspend fun saveSession(session: PatternBreakSession) {
        dataSource.saveSession(session)
    }
}
