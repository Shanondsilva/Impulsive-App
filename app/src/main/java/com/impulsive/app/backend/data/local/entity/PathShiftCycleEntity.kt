package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "path_shift_cycles",
    indices = [
        Index(value = ["status", "forecastWindowEndsAtMillis"]),
        Index(value = ["createdAtMillis"]),
        Index(value = ["preparedPlanId", "preparedPlanContentRevisionId"]),
        Index(value = ["reviewFinalisedAtMillis"]),
    ],
)
data class PathShiftCycleEntity(
    @PrimaryKey
    val cycleId: String,
    val createdAtMillis: Long,
    val lookbackStartedAtMillis: Long,
    val lookbackEndedAtMillis: Long,
    val forecastWindowStartedAtMillis: Long,
    val forecastWindowEndsAtMillis: Long,
    val forecastPolicyVersion: Int,
    val evidenceStrength: String,
    val inputProtectedMomentCount: Int,
    val inputDistinctDayCount: Int,
    val estimatedLowerCount: Int,
    val estimatedUpperCount: Int,
    val commonWindowStartMinute: Int?,
    val commonWindowEndMinute: Int?,
    val preparedPlanId: String?,
    val preparedPlanContentRevisionId: String?,
    val preparedAtMillis: Long?,
    val reviewFinalisedAtMillis: Long?,
    val observedProtectedMomentCount: Int,
    val preparedPlanSelectedCount: Int,
    val preparedPlanStartedCount: Int,
    val preparedPlanCompletedCount: Int,
    val preparedPlanDismissedCount: Int,
    val wrongTimingCount: Int,
    val repeatDetectedCount: Int,
    val status: String,
    val cancelledAtMillis: Long?,
)
