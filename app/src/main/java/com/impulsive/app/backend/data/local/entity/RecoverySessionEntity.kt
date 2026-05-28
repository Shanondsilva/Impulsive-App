package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_sessions")
data class RecoverySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long,
    val durationSeconds: Int = 90,
    val urgeBefore: Int?,
    val urgeAfter: Int?,
    val helped: Boolean?,
    val triggerSource: String = "manual_demo",
    val recoveryType: String = "psychological_90_second_reset",
)
