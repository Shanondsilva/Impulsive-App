package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: SyncTombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tombstones: List<SyncTombstoneEntity>)

    @Query(
        """
        SELECT *
        FROM sync_tombstones
        ORDER BY deletedAtMillis DESC
        """,
    )
    suspend fun getAllForSync(): List<SyncTombstoneEntity>

    @Query(
        """
        SELECT *
        FROM sync_tombstones
        WHERE recordType = :recordType
        """,
    )
    suspend fun getByType(recordType: String): List<SyncTombstoneEntity>

    @Query(
        """
        SELECT *
        FROM sync_tombstones
        WHERE recordType = :recordType
          AND parentKey = :parentKey
        """,
    )
    suspend fun getByTypeAndParent(
        recordType: String,
        parentKey: String,
    ): List<SyncTombstoneEntity>
}
