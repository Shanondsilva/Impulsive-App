package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_domain",
    indices = [Index(value = ["domain"], unique = true)],
)
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domain: String,
    val category: String,
    val isDefault: Boolean,
    val addedByUser: Boolean,
    val createdAtMillis: Long,
)
