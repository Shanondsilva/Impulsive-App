package com.impulsive.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyTargetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(target: WeeklyTarget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(target: WeeklyTarget)

    @Update
    suspend fun update(target: WeeklyTarget)

    @Query("SELECT * FROM weekly_target WHERE weekStartDate = :weekStart")
    fun observeForWeek(weekStart: Long): Flow<WeeklyTarget?>

    @Query("SELECT * FROM weekly_target WHERE weekStartDate = :weekStart")
    suspend fun getForWeek(weekStart: Long): WeeklyTarget?

    @Query("SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT 1")
    fun observeCurrent(): Flow<WeeklyTarget?>

    @Query("SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT :n")
    fun observeLastN(n: Int): Flow<List<WeeklyTarget>>

    @Query("SELECT * FROM weekly_target ORDER BY weekStartDate DESC LIMIT 10")
    fun observeRecent(): Flow<List<WeeklyTarget>>

    @Query("SELECT * FROM weekly_target ORDER BY weekStartDate DESC")
    suspend fun getAll(): List<WeeklyTarget>

    @Query("UPDATE weekly_target SET usedSessions = usedSessions + 1 WHERE weekStartDate = :weekStart")
    suspend fun incrementUsed(weekStart: Long)
}
