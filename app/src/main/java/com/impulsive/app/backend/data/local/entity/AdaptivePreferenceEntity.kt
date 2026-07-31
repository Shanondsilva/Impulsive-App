package com.impulsive.app.backend.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adaptive_preferences")
data class AdaptivePreferenceEntity(
    @PrimaryKey
    val id: Int = SingleRowId,
    @ColumnInfo(defaultValue = "1")
    val personalSuggestionsEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val gameSuggestionsEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val readingSuggestionsEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val momentPlanSuggestionsEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val randomisedExplorationEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val updatedAtMillis: Long = 0L,
    @ColumnInfo(defaultValue = "1")
    val privateScreenProtectionEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "'SixMonths'")
    val historyRetentionPolicy: String = "SixMonths",
    @ColumnInfo(defaultValue = "0")
    val pathShiftEnabled: Boolean = false,
) {
    companion object {
        const val SingleRowId = 1
    }
}
