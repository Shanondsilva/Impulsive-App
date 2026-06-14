package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.WindowOutcomeDataSource
import com.impulsive.app.backend.domain.model.release.WindowOutcomeRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class WindowOutcomeRepository(context: Context) {
    private val dataSource = WindowOutcomeDataSource(context)

    val outcomes: Flow<List<WindowOutcomeRecord>> = dataSource.outcomes

    suspend fun markWindowUsed(
        windowStart: LocalDateTime,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        dataSource.markUsed(windowStart = windowStart, now = now)
    }

    suspend fun markEndedWindowsSkipped(
        plannedWindowStarts: List<LocalDateTime>,
        windowMinutes: Long,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        dataSource.markEndedWindowsSkipped(
            plannedWindowStarts = plannedWindowStarts,
            windowMinutes = windowMinutes,
            now = now,
        )
    }
}
