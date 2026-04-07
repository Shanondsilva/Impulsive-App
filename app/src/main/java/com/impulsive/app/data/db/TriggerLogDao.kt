package com.impulsive.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerLogDao {
    @Insert
    suspend fun insert(log: TriggerLog)

    @Query("SELECT * FROM trigger_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TriggerLog>>

    @Query("SELECT * FROM trigger_log WHERE timestamp >= :weekStart ORDER BY timestamp DESC")
    fun observeSince(weekStart: Long): Flow<List<TriggerLog>>

    @Query("SELECT COUNT(*) FROM trigger_log WHERE timestamp >= :weekStart")
    fun countSince(weekStart: Long): Flow<Int>

    @Query("SELECT * FROM trigger_log WHERE timestamp >= :startMs AND timestamp < :endMs ORDER BY timestamp ASC")
    fun observeForDateRange(startMs: Long, endMs: Long): Flow<List<TriggerLog>>

    @Query("SELECT * FROM trigger_log ORDER BY timestamp ASC")
    suspend fun getAll(): List<TriggerLog>
}
