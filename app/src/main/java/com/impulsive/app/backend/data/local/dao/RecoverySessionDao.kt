package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity

@Dao
interface RecoverySessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: RecoverySessionEntity): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM recovery_sessions
        WHERE completedAt >= :dayStartMillis
            AND completedAt < :nextDayStartMillis
        """,
    )
    suspend fun getTodaySessionCount(
        dayStartMillis: Long,
        nextDayStartMillis: Long,
    ): Int

    @Query(
        """
        SELECT *
        FROM recovery_sessions
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestSession(): RecoverySessionEntity?

    @Query("SELECT * FROM recovery_sessions ORDER BY completedAt DESC")
    suspend fun getAllSessions(): List<RecoverySessionEntity>

    @Query("DELETE FROM recovery_sessions WHERE startedAt = :startedAt AND completedAt = :completedAt")
    suspend fun deleteByContentKey(startedAt: Long, completedAt: Long): Int

    @Query("DELETE FROM recovery_sessions")
    suspend fun clearAllForRestore(): Int
}
