package com.impulsive.app.backend.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_notes")
data class JournalNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteType: String,
    val title: String,
    val body: String = "",
    val checklist: String = "",
    val sketch: String = "",
    val reminderAtMillis: Long? = null,
    val source: String = "normal_journal",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    val category: String = "",
    val highlightColor: String? = null,
    val sortOrder: Long? = null,
)
