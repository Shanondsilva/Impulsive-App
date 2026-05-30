package com.impulsive.app.backend.session.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.data.repository.MoveDirection
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

private val ReminderZone: ZoneId = ZoneId.systemDefault()

data class ChecklistDraftItem(
    val localId: Long,
    val text: String,
    val isChecked: Boolean = false,
)

data class JournalListUiState(
    val notes: List<JournalNoteEntity> = emptyList(),
    val recentNotes: List<JournalNoteEntity> = emptyList(),
    val noteCount: Int = 0,
    val maxNotes: Int = JournalViewModel.MaxNormalJournalSaves,
) {
    val canCreateMore: Boolean get() = noteCount < maxNotes
}

data class JournalEditorUiState(
    val noteId: Long = 0L,
    val type: JournalNoteType = JournalNoteType.Text,
    val titleDraft: String = "",
    val bodyDraft: String = "",
    val sketchDraft: String = "",
    val checklistItems: List<ChecklistDraftItem> = emptyList(),
    val reminderAtMillis: Long? = null,
    val source: String = "normal_journal",
    val createdAtMillis: Long = 0L,
    val isPinned: Boolean = false,
    val category: String = "",
    val highlightColor: String? = null,
    val sortOrder: Long? = null,
    val hasLoaded: Boolean = false,
    val savedNoteId: Long? = null,
    val noteLimitReached: Boolean = false,
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(application)

    companion object {
        const val MaxNormalJournalSaves = 50
    }

    private val _listState = MutableStateFlow(JournalListUiState())
    val listState: StateFlow<JournalListUiState> = _listState

    private val _editorState = MutableStateFlow(JournalEditorUiState())
    val editorState: StateFlow<JournalEditorUiState> = _editorState

    private var editorLoadJob: Job? = null
    private var checklistLoadJob: Job? = null
    private var activeEditorNoteId: Long? = null
    private var nextLocalChecklistId = -1L

    init {
        viewModelScope.launch {
            repository.observeNotes().collect { notes ->
                _listState.update { it.copy(notes = notes) }
            }
        }
        viewModelScope.launch {
            repository.observeRecentNotes(limit = 10).collect { notes ->
                _listState.update { it.copy(recentNotes = notes) }
            }
        }
        viewModelScope.launch {
            repository.observeNoteCount().collect { count ->
                _listState.update { it.copy(noteCount = count) }
            }
        }
    }

    fun startNew(type: JournalNoteType) {
        activeEditorNoteId = 0L
        editorLoadJob?.cancel()
        checklistLoadJob?.cancel()
        _editorState.value = JournalEditorUiState(
            type = type,
            titleDraft = "",
            bodyDraft = "",
            sketchDraft = "",
            checklistItems = if (type == JournalNoteType.Checklist) listOf(newBlankChecklistItem()) else emptyList(),
            hasLoaded = true,
            noteLimitReached = false,
        )
    }

    fun loadExisting(noteId: Long) {
        if (activeEditorNoteId == noteId) return
        activeEditorNoteId = noteId
        editorLoadJob?.cancel()
        checklistLoadJob?.cancel()
        _editorState.update { it.copy(noteId = noteId, hasLoaded = false, savedNoteId = null) }
        editorLoadJob = viewModelScope.launch {
            repository.observeNote(noteId).collect { note ->
                if (note != null) {
                    _editorState.update { current ->
                        note.toEditorState(current.checklistItems)
                    }
                }
            }
        }
        checklistLoadJob = viewModelScope.launch {
            repository.observeChecklistItems(noteId).collect { items ->
                _editorState.update { current ->
                    current.copy(
                        checklistItems = if (items.isEmpty() && current.type == JournalNoteType.Checklist) {
                            listOf(newBlankChecklistItem())
                        } else {
                            items.map { item ->
                                ChecklistDraftItem(
                                    localId = item.id,
                                    text = item.text,
                                    isChecked = item.isChecked,
                                )
                            }.sortedForDisplay()
                        },
                    )
                }
            }
        }
    }

    fun updateType(type: JournalNoteType) {
        _editorState.update { current ->
            current.copy(
                type = type,
                checklistItems = if (type == JournalNoteType.Checklist && current.checklistItems.isEmpty()) {
                    listOf(newBlankChecklistItem())
                } else current.checklistItems,
                savedNoteId = null,
                noteLimitReached = false,
            )
        }
    }

    fun updateTitle(value: String) {
        _editorState.update { it.copy(titleDraft = value.take(90), savedNoteId = null, noteLimitReached = false) }
    }

    fun updateBody(value: String) {
        _editorState.update { it.copy(bodyDraft = value.take(4000), savedNoteId = null, noteLimitReached = false) }
    }

    fun updateSketch(value: String) {
        _editorState.update { it.copy(sketchDraft = value.take(12000), savedNoteId = null, noteLimitReached = false) }
    }

    fun updateChecklistItem(localId: Long, text: String) {
        _editorState.update { current ->
            current.copy(
                checklistItems = current.checklistItems.map { item ->
                    if (item.localId == localId) item.copy(text = text.take(220)) else item
                },
                savedNoteId = null,
                noteLimitReached = false,
            )
        }
    }

    fun toggleChecklistItem(localId: Long) {
        _editorState.update { current ->
            current.copy(
                checklistItems = current.checklistItems.map { item ->
                    if (item.localId == localId) item.copy(isChecked = !item.isChecked) else item
                }.sortedForDisplay(),
                savedNoteId = null,
                noteLimitReached = false,
            )
        }
    }

    fun addChecklistItem() {
        _editorState.update { current ->
            current.copy(
                checklistItems = current.checklistItems + newBlankChecklistItem(),
                savedNoteId = null,
                noteLimitReached = false,
            )
        }
    }

    fun removeChecklistItem(localId: Long) {
        _editorState.update { current ->
            val remaining = current.checklistItems.filterNot { it.localId == localId }
            current.copy(
                checklistItems = if (remaining.isEmpty()) listOf(newBlankChecklistItem()) else remaining,
                savedNoteId = null,
                noteLimitReached = false,
            )
        }
    }

    fun setReminderTodayEvening() {
        val now = LocalDateTime.now(ReminderZone)
        val target = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
        setReminder(if (target.isAfter(now)) target else target.plusDays(1))
    }

    fun setReminderTomorrowMorning() {
        setReminder(LocalDateTime.now(ReminderZone).plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0))
    }

    fun setReminderNextFridayMorning() {
        val now = LocalDateTime.now(ReminderZone)
        var target = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
        while (target.dayOfWeek != DayOfWeek.FRIDAY || !target.isAfter(now)) {
            target = target.plusDays(1)
        }
        setReminder(target)
    }

    fun setCustomReminder(year: Int, monthZeroBased: Int, dayOfMonth: Int, hour: Int, minute: Int) {
        val selected = LocalDateTime.of(year, monthZeroBased + 1, dayOfMonth, hour, minute, 0, 0)
        val now = LocalDateTime.now(ReminderZone)
        setReminder(if (selected.isAfter(now)) selected else now.plusMinutes(15))
    }

    fun clearReminder() {
        _editorState.update { it.copy(reminderAtMillis = null, savedNoteId = null, noteLimitReached = false) }
    }

    private fun setReminder(dateTime: LocalDateTime) {
        val millis = dateTime.atZone(ReminderZone).toInstant().toEpochMilli()
        _editorState.update { it.copy(reminderAtMillis = millis, savedNoteId = null, noteLimitReached = false) }
    }

    fun saveCurrent() {
        val current = _editorState.value
        val isNewNote = current.noteId == 0L
        if (isNewNote && _listState.value.noteCount >= MaxNormalJournalSaves) {
            _editorState.update { it.copy(noteLimitReached = true, savedNoteId = null) }
            return
        }
        val now = System.currentTimeMillis()
        val createdAt = current.createdAtMillis.takeIf { it > 0L } ?: now
        val nonBlankChecklistItems = current.checklistItems.filter { it.text.isNotBlank() }
        val note = JournalNoteEntity(
            id = current.noteId,
            noteType = current.type.storageValue,
            title = current.titleDraft.trim().ifBlank { current.type.defaultTitle() },
            body = current.bodyDraft.trim(),
            checklist = nonBlankChecklistItems.joinToString("\n") { item ->
                if (item.isChecked) "[x] ${item.text.trim()}" else "[ ] ${item.text.trim()}"
            },
            sketch = current.sketchDraft.trim(),
            reminderAtMillis = current.reminderAtMillis,
            source = current.source,
            createdAtMillis = createdAt,
            updatedAtMillis = now,
            isPinned = current.isPinned,
            category = current.category,
            highlightColor = current.highlightColor,
            sortOrder = current.sortOrder,
        )
        val checklistEntities = nonBlankChecklistItems.sortedForDisplay().mapIndexed { index, item ->
            JournalChecklistItemEntity(
                id = if (item.localId > 0) item.localId else 0L,
                noteId = current.noteId,
                text = item.text.trim(),
                isChecked = item.isChecked,
                sortOrder = index.toLong(),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        }
        viewModelScope.launch {
            val savedId = repository.upsertNote(note, checklistEntities)
            activeEditorNoteId = savedId
            _editorState.update {
                it.copy(
                    noteId = savedId,
                    titleDraft = note.title,
                    createdAtMillis = createdAt,
                    savedNoteId = savedId,
                    hasLoaded = true,
                    noteLimitReached = false,
                )
            }
        }
    }

    fun deleteCurrent(onDeleted: () -> Unit) {
        val noteId = _editorState.value.noteId
        if (noteId == 0L) {
            onDeleted()
            return
        }
        deleteNote(noteId, onDeleted)
    }

    fun deleteNote(noteId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            onDeleted()
        }
    }

    fun setPinned(noteId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(noteId, pinned) }
    }

    fun setHighlight(noteId: Long, highlightColor: String?) {
        viewModelScope.launch { repository.setHighlight(noteId, highlightColor) }
    }

    fun setCategory(noteId: Long, category: String) {
        viewModelScope.launch { repository.setCategory(noteId, category) }
    }

    fun moveNoteUp(noteId: Long) {
        viewModelScope.launch { repository.moveNote(noteId, MoveDirection.Up) }
    }

    fun moveNoteDown(noteId: Long) {
        viewModelScope.launch { repository.moveNote(noteId, MoveDirection.Down) }
    }

    private fun newBlankChecklistItem(): ChecklistDraftItem {
        return ChecklistDraftItem(localId = nextLocalChecklistId--, text = "")
    }
}

private fun JournalNoteEntity.toEditorState(checklistItems: List<ChecklistDraftItem>): JournalEditorUiState {
    return JournalEditorUiState(
        noteId = id,
        type = JournalNoteType.fromStorage(noteType),
        titleDraft = title,
        bodyDraft = body,
        sketchDraft = sketch,
        checklistItems = checklistItems,
        reminderAtMillis = reminderAtMillis,
        source = source,
        createdAtMillis = createdAtMillis,
        isPinned = isPinned,
        category = category,
        highlightColor = highlightColor,
        sortOrder = sortOrder,
        hasLoaded = true,
    )
}

private fun JournalNoteType.defaultTitle(): String = when (this) {
    JournalNoteType.Text -> "Untitled note"
    JournalNoteType.Checklist -> "New list"
    JournalNoteType.Sketch -> "New drawing"
    JournalNoteType.Reminder -> "New reminder"
}

private fun List<ChecklistDraftItem>.sortedForDisplay(): List<ChecklistDraftItem> {
    return sortedWith(compareBy<ChecklistDraftItem> { it.isChecked }.thenBy { it.localId })
}
