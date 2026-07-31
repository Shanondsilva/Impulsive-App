package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentPlanRehearsalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(rehearsal: MomentPlanRehearsalEntity): Long

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        WHERE rehearsalId = :rehearsalId
        LIMIT 1
        """,
    )
    suspend fun getById(rehearsalId: String): MomentPlanRehearsalEntity?

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        ORDER BY startedAtMillis ASC, rehearsalId ASC
        """,
    )
    suspend fun getAllForBackup(): List<MomentPlanRehearsalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(rehearsal: MomentPlanRehearsalEntity)

    @Query(
        """
        UPDATE moment_plan_rehearsals
        SET completedAtMillis = :completedAtMillis
        WHERE rehearsalId = :rehearsalId
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markCompletedOnce(
        rehearsalId: String,
        completedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE moment_plan_rehearsals
        SET dismissedAtMillis = :dismissedAtMillis
        WHERE rehearsalId = :rehearsalId
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markDismissedOnce(
        rehearsalId: String,
        dismissedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        WHERE completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        ORDER BY startedAtMillis DESC, rehearsalId ASC
        LIMIT 1
        """,
    )
    suspend fun getOpenRehearsal(): MomentPlanRehearsalEntity?

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        WHERE completedAtMillis IS NOT NULL
        ORDER BY completedAtMillis DESC, rehearsalId ASC
        LIMIT :limit
        """,
    )
    suspend fun getRecentCompleted(limit: Int): List<MomentPlanRehearsalEntity>

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        WHERE completedAtMillis IS NOT NULL
        ORDER BY completedAtMillis DESC, rehearsalId ASC
        LIMIT :limit
        """,
    )
    fun observeRecentCompleted(limit: Int): Flow<List<MomentPlanRehearsalEntity>>

    @Query(
        """
        SELECT *
        FROM moment_plan_rehearsals
        WHERE planId = :planId
            AND completedAtMillis IS NOT NULL
        ORDER BY completedAtMillis DESC, rehearsalId ASC
        """,
    )
    suspend fun getCompletedByPlan(planId: String): List<MomentPlanRehearsalEntity>

    @Query(
        """
        SELECT rehearsalId
        FROM moment_plan_rehearsals
        WHERE (
                completedAtMillis IS NOT NULL
                AND completedAtMillis >= 0
                AND completedAtMillis < :cutoffMillis
            )
            OR (
                dismissedAtMillis IS NOT NULL
                AND dismissedAtMillis >= 0
                AND dismissedAtMillis < :cutoffMillis
            )
        ORDER BY COALESCE(completedAtMillis, dismissedAtMillis) ASC, rehearsalId ASC
        LIMIT :limit
        """,
    )
    suspend fun getSafeRetentionCandidateIds(
        cutoffMillis: Long,
        limit: Int,
    ): List<String>

    @Query("DELETE FROM moment_plan_rehearsals WHERE rehearsalId IN (:rehearsalIds)")
    suspend fun deleteByIds(rehearsalIds: List<String>): Int

    @Query(
        """
        DELETE FROM moment_plan_rehearsals
        WHERE completedAtMillis IS NOT NULL
            OR dismissedAtMillis IS NOT NULL
        """,
    )
    suspend fun clearHistory(): Int

    @Query("DELETE FROM moment_plan_rehearsals")
    suspend fun clearAll(): Int
}
