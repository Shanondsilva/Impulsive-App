package com.impulsive.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eval_metrics")
data class EvalMetrics(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phaseNumber: Int,
    val metricName: String,
    val metricValue: String,
    val timestamp: Long
)
