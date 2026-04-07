package com.impulsive.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BypassEventDao {
    @Insert
    suspend fun insert(event: BypassEvent)

    @Update
    suspend fun update(event: BypassEvent)

    @Query("SELECT * FROM bypass_event ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BypassEvent>>

    @Query("SELECT * FROM bypass_event WHERE recovered = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnrecovered(): BypassEvent?

    @Query("SELECT * FROM bypass_event ORDER BY timestamp ASC")
    suspend fun getAll(): List<BypassEvent>
}
