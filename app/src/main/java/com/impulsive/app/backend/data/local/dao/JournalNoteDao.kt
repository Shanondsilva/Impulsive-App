package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalNoteDao {
    @Query(
        """
        SELECT * FROM journal_notes
        WHERE source = :source
        ORDER BY
            isPinned DESC,
            CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END ASC,
            sortOrder ASC,
            updatedAtMillis DESC
        """,
    )
    fun observeNotes(source: String = "normal_journal"): Flow<List<JournalNoteEntity>>

    @Query("SELECT * FROM journal_notes WHERE id = :noteId LIMIT 1")
    fun observeNote(noteId: Long): Flow<JournalNoteEntity?>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE source = :source
        ORDER BY
            isPinned DESC,
            CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END ASC,
            sortOrder ASC,
            updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecentNotes(limit: Int, source: String = "normal_journal"): Flow<List<JournalNoteEntity>>

    @Query("SELECT COUNT(*) FROM journal_notes WHERE source = :source")
    fun observeNoteCount(source: String): Flow<Int>

    @Query("SELECT * FROM journal_notes WHERE id = :noteId LIMIT 1")
    suspend fun getNote(noteId: Long): JournalNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: JournalNoteEntity): Long

    @Update
    suspend fun update(note: JournalNoteEntity)

    @Query("SELECT * FROM journal_notes")
    suspend fun getAllNotesForSync(): List<JournalNoteEntity>

    @Delete
    suspend fun delete(note: JournalNoteEntity)

    @Query("DELETE FROM journal_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query(
        """
        SELECT * FROM journal_checklist_items
        WHERE noteId = :noteId
        ORDER BY isChecked ASC, sortOrder ASC, updatedAtMillis ASC
        """,
    )
    fun observeChecklistItems(noteId: Long): Flow<List<JournalChecklistItemEntity>>

    @Query(
        """
        SELECT * FROM journal_checklist_items
        WHERE noteId = :noteId
        ORDER BY isChecked ASC, sortOrder ASC, updatedAtMillis ASC
        """,
    )
    suspend fun getChecklistItems(noteId: Long): List<JournalChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItems(items: List<JournalChecklistItemEntity>)

    @Query("DELETE FROM journal_checklist_items WHERE noteId = :noteId")
    suspend fun deleteChecklistItemsForNote(noteId: Long)

    @Query("UPDATE journal_notes SET sortOrder = :sortOrder, updatedAtMillis = :updatedAtMillis WHERE id = :noteId")
    suspend fun updateSortOrder(noteId: Long, sortOrder: Long, updatedAtMillis: Long)

    @Transaction
    suspend fun replaceChecklistItems(noteId: Long, items: List<JournalChecklistItemEntity>) {
        deleteChecklistItemsForNote(noteId)
        if (items.isNotEmpty()) {
            insertChecklistItems(items)
        }
    }

    @Transaction
    suspend fun upsertNoteWithChecklist(
        note: JournalNoteEntity,
        items: List<JournalChecklistItemEntity>,
    ): Long {
        val savedId = if (note.id == 0L) insert(note) else { update(note); note.id }
        if (note.noteType == "CHECKLIST") {
            replaceChecklistItems(
                noteId = savedId,
                items = items.mapIndexed { index, item -> item.copy(noteId = savedId, sortOrder = index.toLong()) },
            )
        } else {
            deleteChecklistItemsForNote(savedId)
        }
        return savedId
    }

    @Transaction
    suspend fun reorder(notes: List<JournalNoteEntity>, movedNoteId: Long, now: Long) {
        notes.forEachIndexed { index, note ->
            updateSortOrder(
                noteId = note.id,
                sortOrder = index.toLong(),
                updatedAtMillis = if (note.id == movedNoteId) now else note.updatedAtMillis,
            )
        }
    }
}
