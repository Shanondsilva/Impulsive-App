package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moment_plans",
    indices = [
        Index(
            value = ["enabled", "updatedAtMillis"],
            name = "index_moment_plans_enabled_updatedAtMillis",
        ),
        Index(
            value = ["momentCue", "enabled", "preferredForCue"],
            name = "index_moment_plans_momentCue_enabled_preferredForCue",
        ),
    ],
)
data class MomentPlanEntity(
    @PrimaryKey
    val planId: String,
    val title: String,
    val momentCue: String?,
    val actionText: String,
    val futureCueText: String,
    val actionType: String,
    val actionTarget: String?,
    val enabled: Boolean,
    val preferredForCue: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val rehearsedAtMillis: Long?,
    @ColumnInfo(defaultValue = "''")
    val contentRevisionId: String = "",
)
