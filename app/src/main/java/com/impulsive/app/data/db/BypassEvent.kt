package com.impulsive.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bypass_event")
data class BypassEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,           // "monitoring_revoked"
    val recovered: Boolean = false
)
