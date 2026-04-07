package com.impulsive.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvalMetricsDao {
    @Insert
    suspend fun insert(metric: EvalMetrics)

    @Query("SELECT * FROM eval_metrics ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EvalMetrics>>

    @Query("SELECT * FROM eval_metrics WHERE phaseNumber = :phase ORDER BY timestamp DESC")
    suspend fun getAllForPhase(phase: Int): List<EvalMetrics>

    @Query("SELECT * FROM eval_metrics ORDER BY timestamp ASC")
    suspend fun getAll(): List<EvalMetrics>
}
