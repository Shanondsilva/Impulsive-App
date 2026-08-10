package com.impulsive.app.backend.domain.repository.score

import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import kotlinx.coroutines.flow.Flow

interface SafeExitRecordRepository {
    val records:
        Flow<List<SafeExitRecord>>

    suspend fun recordIfAbsent(
        record: SafeExitRecord,
    ): Boolean
}