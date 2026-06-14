package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity

@Dao
interface BlockedDomainDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(domains: List<BlockedDomainEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: BlockedDomainEntity): Long

    @Query("SELECT domain FROM blocked_domain")
    suspend fun getAllDomains(): List<String>

    @Query("SELECT * FROM blocked_domain ORDER BY domain")
    suspend fun getAll(): List<BlockedDomainEntity>

    @Query("SELECT COUNT(*) FROM blocked_domain")
    suspend fun count(): Int

    @Query("DELETE FROM blocked_domain WHERE id = :id AND addedByUser = 1")
    suspend fun deleteUserDomain(id: Long)
}
