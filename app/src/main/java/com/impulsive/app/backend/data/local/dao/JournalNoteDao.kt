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
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalNoteDao {
    @Query(
        """
        SELECT * FROM journal_notes
        WHERE source = :source
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        ORDER BY
            isPinned DESC,
            CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END ASC,
            sortOrder ASC,
            updatedAtMillis DESC
        """,
    )
    fun observeNotes(source: String = "normal_journal"): Flow<List<JournalNoteEntity>>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE id = :noteId
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        LIMIT 1
        """,
    )
    fun observeNote(
        noteId: Long,
    ): Flow<JournalNoteEntity?>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE source = :source
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    fun observeLatestNoteBySource(
        source: String,
    ): Flow<JournalNoteEntity?>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE source = :source
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        ORDER BY
            isPinned DESC,
            CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END ASC,
            sortOrder ASC,
            updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecentNotes(limit: Int, source: String = "normal_journal"): Flow<List<JournalNoteEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM journal_notes
        WHERE source = :source
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        """,
    )
    fun observeNoteCount(source: String): Flow<Int>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE id = :noteId
          AND noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        LIMIT 1
        """,
    )
    suspend fun getNote(
        noteId: Long,
    ): JournalNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: JournalNoteEntity): Long

    @Update
    suspend fun update(note: JournalNoteEntity)

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE noteType != 'FEEDBACK'
          AND source != 'feedback_notification'
        """,
    )
    suspend fun getAllNotesForSync():
        List<JournalNoteEntity>

    @Query(
        """
        SELECT * FROM journal_notes
        WHERE noteType = 'FEEDBACK'
           OR source = 'feedback_notification'
        """,
    )
    suspend fun getObsoleteFeedbackNotes():
        List<JournalNoteEntity>

    @Query(
        """
        DELETE FROM journal_notes
        WHERE noteType = 'FEEDBACK'
           OR source = 'feedback_notification'
        """,
    )
    suspend fun deleteObsoleteFeedbackNotes(): Int

    @Delete
    suspend fun delete(note: JournalNoteEntity)

    @Query("DELETE FROM journal_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query("DELETE FROM journal_notes WHERE id IN (:noteIds)")
    suspend fun deleteByIds(noteIds: List<Long>)
    @Query("DELETE FROM journal_notes WHERE noteType != 'FEEDBACK' AND source != 'feedback_notification'")
    suspend fun clearAllUserNotesForRestore(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstones(tombstones: List<SyncTombstoneEntity>)

    @Transaction
    suspend fun deleteNoteWithTombstone(noteId: Long, deletedAtMillis: Long) {
        val note = getNote(noteId) ?: return
        upsertTombstone(
            SyncTombstoneEntity.journalNote(
                recordKey = note.createdAtMillis.toString(),
                deletedAtMillis = deletedAtMillis,
            ),
        )
        deleteById(noteId)
    }

    @Transaction
    suspend fun deleteNotesWithTombstones(noteIds: List<Long>, deletedAtMillis: Long) {
        noteIds.distinct().forEach { noteId ->
            deleteNoteWithTombstone(
                noteId = noteId,
                deletedAtMillis = deletedAtMillis,
            )
        }
    }

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChecklistItemsForRestore(
        items: List<JournalChecklistItemEntity>,
    )

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
    suspend fun replaceChecklistItemsWithTombstones(
        noteId: Long,
        noteCreatedAtMillis: Long,
        items: List<JournalChecklistItemEntity>,
        deletedAtMillis: Long,
    ) {
        val existing = getChecklistItems(noteId)
        val nextKeys = items.map { it.createdAtMillis }.toHashSet()
        val tombstones = existing
            .filter { item -> !nextKeys.contains(item.createdAtMillis) }
            .map { item ->
                SyncTombstoneEntity.checklistItem(
                    parentKey = noteCreatedAtMillis.toString(),
                    recordKey = item.createdAtMillis.toString(),
                    deletedAtMillis = deletedAtMillis,
                )
            }

        if (tombstones.isNotEmpty()) {
            upsertTombstones(tombstones)
        }

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
            replaceChecklistItemsWithTombstones(
                noteId = savedId,
                noteCreatedAtMillis = note.createdAtMillis,
                items = items.mapIndexed { index, item -> item.copy(noteId = savedId, sortOrder = index.toLong()) },
                deletedAtMillis = note.updatedAtMillis,
            )
        } else {
            replaceChecklistItemsWithTombstones(
                noteId = savedId,
                noteCreatedAtMillis = note.createdAtMillis,
                items = emptyList(),
                deletedAtMillis = note.updatedAtMillis,
            )
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
