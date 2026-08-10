package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import kotlinx.coroutines.flow.Flow

data class SafeExitSourceCountRow(
    val source:
        String,
    val recordCount:
        Long,
)

@Dao
interface SafeExitDao {
    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    suspend fun insertOnce(
        record: SafeExitEntity,
    ): Long

    @Query(
        """
        SELECT *
        FROM safe_exit_records
        ORDER BY completedAt DESC, sourceKey ASC
        """,
    )
    fun observeAll():
        Flow<List<SafeExitEntity>>

    @Query(
        """
        SELECT
            source,
            COUNT(*) AS recordCount
        FROM safe_exit_records
        WHERE completedAt >= :startInclusive
            AND completedAt < :endExclusive
        GROUP BY source
        ORDER BY source ASC
        """,
    )
    fun observeSourceCountsInRange(
        startInclusive:
            String,
        endExclusive:
            String,
    ): Flow<List<SafeExitSourceCountRow>>

    @Query(
        """
        SELECT *
        FROM safe_exit_records
        WHERE completedAt >= :startInclusive
            AND completedAt < :endExclusive
            AND source != :excludedSource
        ORDER BY completedAt DESC, sourceKey ASC
        LIMIT :limit
        """,
    )
    fun observeRecentNonPivotInRange(
        startInclusive:
            String,
        endExclusive:
            String,
        excludedSource:
            String,
        limit:
            Int,
    ): Flow<List<SafeExitEntity>>

    @Query(
        """
        SELECT sourceKey
        FROM safe_exit_records
        WHERE completedAt >= :startInclusive
            AND completedAt < :endExclusive
            AND source = :source
            AND sourceKey IN (:sourceKeys)
        ORDER BY sourceKey ASC
        """,
    )
    fun observeExistingSourceKeysInRange(
        startInclusive:
            String,
        endExclusive:
            String,
        source:
            String,
        sourceKeys:
            List<String>,
    ): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(*)
        FROM safe_exit_records
        """,
    )
    fun observeRecordCount(): Flow<Long>

    @Query(
        """
        SELECT *
        FROM safe_exit_records
        ORDER BY completedAt DESC, sourceKey ASC
        """,
    )
    suspend fun getAllForBackup():
        List<SafeExitEntity>

    @Insert(
        onConflict = OnConflictStrategy.ABORT,
    )
    suspend fun insertForRestore(
        records: List<SafeExitEntity>,
    )

    @Query(
        """
        DELETE FROM safe_exit_records
        """,
    )
    suspend fun clearAllForRestore()

    @Query(
        """
        SELECT *
        FROM safe_exit_records
        WHERE sourceKey = :sourceKey
        LIMIT 1
        """,
    )
    suspend fun getBySourceKey(
        sourceKey: String,
    ): SafeExitEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM safe_exit_records
        """,
    )
    suspend fun count(): Int
}