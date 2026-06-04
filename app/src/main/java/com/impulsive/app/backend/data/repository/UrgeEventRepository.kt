package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.UrgeEventDataSource
import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

class UrgeEventRepository(context: Context) {
    private val dataSource = UrgeEventDataSource(context)

    val events: Flow<List<UrgeEventRecord>> = dataSource.events

    suspend fun recordEvent(
        source: String = "app",
        date: LocalDate = LocalDate.now(),
        packageName: String? = null,
        at: LocalDateTime = LocalDateTime.now(),
    ) {
        dataSource.recordEvent(
            UrgeEventRecord(date = date, source = source, packageName = packageName, at = at),
        )
    }
}
