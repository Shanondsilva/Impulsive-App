package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.service.journal.JournalReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class JournalRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).journalNoteDao()
    private val reminderScheduler = JournalReminderScheduler(appContext)

    fun observeNotes(): Flow<List<JournalNoteEntity>> = dao.observeNotes()

    fun observeNote(noteId: Long): Flow<JournalNoteEntity?> = dao.observeNote(noteId)

    fun observeRecentNotes(limit: Int = 10): Flow<List<JournalNoteEntity>> = dao.observeRecentNotes(limit)

    fun observeNoteCount(source: String = "normal_journal"): Flow<Int> = dao.observeNoteCount(source)

    fun observeChecklistItems(noteId: Long): Flow<List<JournalChecklistItemEntity>> = dao.observeChecklistItems(noteId)

    suspend fun getChecklistItems(noteId: Long): List<JournalChecklistItemEntity> = dao.getChecklistItems(noteId)

    suspend fun upsertNote(
        note: JournalNoteEntity,
        checklistItems: List<JournalChecklistItemEntity> = emptyList(),
    ): Long {
        val savedId = if (note.id == 0L) {
            dao.insert(note)
        } else {
            dao.update(note)
            note.id
        }

        if (note.noteType == "CHECKLIST") {
            dao.replaceChecklistItems(
                noteId = savedId,
                items = checklistItems.mapIndexed { index, item ->
                    item.copy(
                        noteId = savedId,
                        sortOrder = index.toLong(),
                    )
                },
            )
        } else {
            dao.deleteChecklistItemsForNote(savedId)
        }

        reminderScheduler.schedule(
            noteId = savedId,
            title = note.title,
            preview = note.previewForNotification(checklistItems),
            reminderAtMillis = note.reminderAtMillis,
        )
        return savedId
    }

    suspend fun updateNote(note: JournalNoteEntity) {
        dao.update(note)
        reminderScheduler.schedule(
            noteId = note.id,
            title = note.title,
            preview = note.previewForNotification(dao.getChecklistItems(note.id)),
            reminderAtMillis = note.reminderAtMillis,
        )
    }

    suspend fun deleteNote(noteId: Long) {
        reminderScheduler.cancel(noteId)
        dao.deleteById(noteId)
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) {
        val note = dao.getNote(noteId) ?: return
        updateNote(note.copy(isPinned = pinned, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun setHighlight(noteId: Long, highlightColor: String?) {
        val note = dao.getNote(noteId) ?: return
        updateNote(note.copy(highlightColor = highlightColor, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun setCategory(noteId: Long, category: String) {
        val note = dao.getNote(noteId) ?: return
        updateNote(note.copy(category = category.trim().take(32), updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun moveNote(noteId: Long, direction: MoveDirection) {
        val notes = dao.getNote(noteId)?.source?.let { source ->
            daoSnapshot(source)
        } ?: return
        val index = notes.indexOfFirst { it.id == noteId }
        if (index == -1) return
        val targetIndex = when (direction) {
            MoveDirection.Up -> (index - 1).coerceAtLeast(0)
            MoveDirection.Down -> (index + 1).coerceAtMost(notes.lastIndex)
        }
        if (targetIndex == index) return

        val mutable = notes.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(targetIndex, moved)
        val now = System.currentTimeMillis()
        mutable.forEachIndexed { order, note ->
            dao.update(note.copy(sortOrder = order.toLong(), updatedAtMillis = if (note.id == noteId) now else note.updatedAtMillis))
        }
    }

    private suspend fun daoSnapshot(source: String): List<JournalNoteEntity> {
        return dao.observeNotes(source).first()
    }
}

enum class MoveDirection { Up, Down }

private fun JournalNoteEntity.previewForNotification(checklistItems: List<JournalChecklistItemEntity>): String {
    return when (noteType) {
        "CHECKLIST" -> checklistItems.firstOrNull { !it.isChecked }?.text
            ?: checklist.lines().firstOrNull { it.isNotBlank() }.orEmpty()
        "SKETCH" -> "Drawing saved in your journal."
        else -> body.ifBlank { checklist.lines().firstOrNull { it.isNotBlank() }.orEmpty() }
    }.ifBlank { "You asked Impulsive to remind you." }
}
