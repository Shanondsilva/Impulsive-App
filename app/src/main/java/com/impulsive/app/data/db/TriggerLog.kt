package com.impulsive.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trigger_log")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val triggerType: String,        // "Bored" | "Stressed" | "Lonely" | "Tired" | "Habit"
    val outcome: String,            // "WalkAway" | "Continue"
    val holdDurationSeconds: Float  // actual seconds held before releasing or completing
)
