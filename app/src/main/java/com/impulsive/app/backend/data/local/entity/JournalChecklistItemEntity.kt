package com.impulsive.app.backend.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = JournalNoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["noteId"])],
)
data class JournalChecklistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val text: String,
    @ColumnInfo(defaultValue = "0")
    val isChecked: Boolean = false,
    val sortOrder: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
