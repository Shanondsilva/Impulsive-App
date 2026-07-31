package com.impulsive.app.backend.domain.repository.pathshift

import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftReviewCounts
import kotlinx.coroutines.flow.Flow

interface PathShiftCycleRepository {
    suspend fun insertOnce(cycle: PathShiftCycle): Boolean
    suspend fun getById(cycleId: String): PathShiftCycle?
    fun observeActive(): Flow<PathShiftCycle?>
    suspend fun getActive(): PathShiftCycle?
    fun observeLatestFinalised(limit: Int): Flow<List<PathShiftCycle>>

    suspend fun attachPreparedPlan(
        cycleId: String,
        planId: String,
        contentRevisionId: String,
        preparedAtMillis: Long,
    ): Boolean

    suspend fun clearPreparedPlan(cycleId: String): Boolean

    suspend fun finaliseOnce(
        cycleId: String,
        finalisedAtMillis: Long,
        counts: PathShiftReviewCounts,
    ): Boolean

    suspend fun cancelOnce(cycleId: String, cancelledAtMillis: Long): Boolean
    suspend fun deleteExpiredFinalised(cutoffMillis: Long, limit: Int): Int
    suspend fun clearAll()
}
