package com.impulsive.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_target")
data class WeeklyTarget(
    @PrimaryKey val weekStartDate: Long, // epoch ms of Monday 00:00
    val allowedSessions: Int,
    val usedSessions: Int = 0,
    val stallReason: String = ""          // non-empty if user chose to keep current limit
)
