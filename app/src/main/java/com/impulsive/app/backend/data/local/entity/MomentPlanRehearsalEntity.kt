package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moment_plan_rehearsals",
    indices = [
        Index(
            value = ["planId", "startedAtMillis"],
            name = "index_moment_plan_rehearsals_planId_startedAtMillis",
        ),
        Index(
            value = ["planId", "completedAtMillis"],
            name = "index_moment_plan_rehearsals_planId_completedAtMillis",
        ),
        Index(
            value = ["completedAtMillis", "dismissedAtMillis", "startedAtMillis"],
            name =
                "index_moment_plan_rehearsals_completedAtMillis_" +
                    "dismissedAtMillis_startedAtMillis",
        ),
        Index(
            value = ["completedAtMillis"],
            name = "index_moment_plan_rehearsals_completedAtMillis",
        ),
        Index(
            value = ["planId", "planContentRevisionId", "completedAtMillis"],
            name =
                "index_moment_plan_rehearsals_planId_" +
                    "planContentRevisionId_completedAtMillis",
        ),
    ],
)
data class MomentPlanRehearsalEntity(
    @PrimaryKey
    val rehearsalId: String,
    val planId: String,
    val planUpdatedAtMillisAtStart: Long,
    val mode: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val dismissedAtMillis: Long?,
    @ColumnInfo(defaultValue = "''")
    val planContentRevisionId: String = "",
)
