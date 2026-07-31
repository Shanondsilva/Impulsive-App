package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PathShiftCycleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(cycle: PathShiftCycleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(cycle: PathShiftCycleEntity)

    @Query("SELECT * FROM path_shift_cycles WHERE cycleId = :cycleId LIMIT 1")
    suspend fun getById(cycleId: String): PathShiftCycleEntity?

    @Query(
        """
        SELECT *
        FROM path_shift_cycles
        WHERE status = 'Active'
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    fun observeActive(): Flow<PathShiftCycleEntity?>

    @Query(
        """
        SELECT *
        FROM path_shift_cycles
        WHERE status = 'Active'
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getActive(): PathShiftCycleEntity?

    @Query(
        """
        SELECT *
        FROM path_shift_cycles
        WHERE status = 'Finalised'
        ORDER BY reviewFinalisedAtMillis DESC, cycleId ASC
        LIMIT :limit
        """,
    )
    fun observeLatestFinalised(limit: Int): Flow<List<PathShiftCycleEntity>>

    @Query(
        """
        SELECT *
        FROM path_shift_cycles
        ORDER BY createdAtMillis ASC, cycleId ASC
        """,
    )
    suspend fun getAllForBackup(): List<PathShiftCycleEntity>

    @Query(
        """
        UPDATE path_shift_cycles
        SET preparedPlanId = :planId,
            preparedPlanContentRevisionId = :contentRevisionId,
            preparedAtMillis = :preparedAtMillis
        WHERE cycleId = :cycleId
            AND status = 'Active'
        """,
    )
    suspend fun attachPreparedPlan(
        cycleId: String,
        planId: String,
        contentRevisionId: String,
        preparedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE path_shift_cycles
        SET preparedPlanId = NULL,
            preparedPlanContentRevisionId = NULL,
            preparedAtMillis = NULL
        WHERE cycleId = :cycleId
            AND status = 'Active'
        """,
    )
    suspend fun clearPreparedPlan(cycleId: String): Int

    @Query(
        """
        UPDATE path_shift_cycles
        SET reviewFinalisedAtMillis = :finalisedAtMillis,
            observedProtectedMomentCount = :observedCount,
            preparedPlanSelectedCount = :selectedCount,
            preparedPlanStartedCount = :startedCount,
            preparedPlanCompletedCount = :completedCount,
            preparedPlanDismissedCount = :dismissedCount,
            wrongTimingCount = :wrongTimingCount,
            repeatDetectedCount = :repeatDetectedCount,
            status = 'Finalised'
        WHERE cycleId = :cycleId
            AND status = 'Active'
            AND forecastWindowEndsAtMillis <= :finalisedAtMillis
        """,
    )
    suspend fun finaliseOnce(
        cycleId: String,
        finalisedAtMillis: Long,
        observedCount: Int,
        selectedCount: Int,
        startedCount: Int,
        completedCount: Int,
        dismissedCount: Int,
        wrongTimingCount: Int,
        repeatDetectedCount: Int,
    ): Int

    @Query(
        """
        UPDATE path_shift_cycles
        SET status = 'Cancelled',
            cancelledAtMillis = :cancelledAtMillis
        WHERE cycleId = :cycleId
            AND status = 'Active'
        """,
    )
    suspend fun cancelOnce(cycleId: String, cancelledAtMillis: Long): Int

    @Query(
        """
        SELECT cycleId
        FROM path_shift_cycles
        WHERE status = 'Finalised'
            AND reviewFinalisedAtMillis < :cutoffMillis
        ORDER BY reviewFinalisedAtMillis ASC, cycleId ASC
        LIMIT :limit
        """,
    )
    suspend fun getExpiredFinalisedIds(cutoffMillis: Long, limit: Int): List<String>

    @Query("DELETE FROM path_shift_cycles WHERE cycleId IN (:cycleIds)")
    suspend fun deleteByIds(cycleIds: List<String>): Int

    @Query("DELETE FROM path_shift_cycles")
    suspend fun clearAll(): Int
}
