package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safe_exit_records",
    indices = [
        Index(
            value = ["completedAt"],
            name = "index_safe_exit_records_completedAt",
        ),
        Index(
            value = [
                "source",
                "completedAt",
            ],
            name =
                "index_safe_exit_records_source_completedAt",
        ),
    ],
)
data class SafeExitEntity(
    @PrimaryKey
    val sourceKey: String,
    val source: String,
    val sourceId: String,
    val completedAt: String,
)