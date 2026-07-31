package com.impulsive.app.backend.session.tasks

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.entity.FeedbackResponseEntity
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.preferences.PlayStoreRatingPromptDataSource
import com.impulsive.app.backend.data.local.preferences.PlayStoreRatingPromptState
import com.impulsive.app.backend.data.repository.FeedbackResponseRepository
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.data.repository.MoveDirection
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.backend.service.journal.FeedbackAnswerReceiver
import com.impulsive.app.backend.service.protection.ProtectionNotificationGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

data class FeedbackQueueItemUiState(
    val responseId: Long,
    val question: String,
    val positiveAnswer: String,
    val honestAnswer: String,
    val selectedAnswerIndex: Int?,
    val selectedAnswer: String?,
    val createdAtMillis: Long,
    val answeredAtMillis: Long?,
    val expiresAtMillis: Long,
)

data class FeedbackQueueUiState(
    val pending: List<FeedbackQueueItemUiState> = emptyList(),
    val answered: List<FeedbackQueueItemUiState> = emptyList(),
    val pendingCount: Int = 0,
    val badgeText: String? = null,
)

data class JournalListUiState(
    val notes: List<JournalNoteEntity> = emptyList(),
    val recentNotes: List<JournalNoteEntity> = emptyList(),
    val noteCount: Int = 0,
    val maxNotes: Int =
        JournalViewModel.MaxNormalJournalSaves,
    val feedbackQueue: FeedbackQueueUiState =
        FeedbackQueueUiState(),
    val playStoreRatingPrompt:
        PlayStoreRatingPromptState =
        PlayStoreRatingPromptState(),
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
    private val taskRewardRepository =
        TaskRewardRepository(application)
    private val feedbackResponseRepository =
        FeedbackResponseRepository(application)
    private val playStoreRatingPromptDataSource =
        PlayStoreRatingPromptDataSource(
            application,
        )

    companion object {
        const val MaxNormalJournalSaves = 50
        const val NoteCreationLevelPoints = 10
    }

    private val _listState = MutableStateFlow(JournalListUiState())
    val listState: StateFlow<JournalListUiState> = _listState

    private val _editorState = MutableStateFlow(JournalEditorUiState())
    val editorState: StateFlow<JournalEditorUiState> = _editorState

    private var editorLoadJob: Job? = null
    private var checklistLoadJob: Job? = null
    private val feedbackQueueNowMillis =
        MutableStateFlow(System.currentTimeMillis())

    private var feedbackQueueExpiryJob: Job? = null
    private var activeEditorNoteId: Long? = null
    private var nextLocalChecklistId = -1L

    init {
        viewModelScope.launch {
            playStoreRatingPromptDataSource
                .state
                .collect { promptState ->
                    _listState.update { current ->
                        current.copy(
                            playStoreRatingPrompt =
                                promptState,
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeNotes().collect { notes ->
                _listState.update {
                    it.copy(
                        notes = notes,
                    )
                }
            }
        }
        viewModelScope.launch {
            val startupNowMillis =
                System.currentTimeMillis()

            feedbackResponseRepository.deleteExpired(
                startupNowMillis,
            )

            feedbackQueueNowMillis.value =
                startupNowMillis

            feedbackQueueNowMillis.collectLatest {
                    queryNowMillis ->

                combine(
                    feedbackResponseRepository.observePending(
                        queryNowMillis,
                    ),
                    feedbackResponseRepository.observeAnswered(
                        queryNowMillis,
                    ),
                ) { pending, answered ->
                    pending to answered
                }.collect { (pending, answered) ->
                    val pendingUi =
                        pending.map {
                            it.toFeedbackQueueItemUiState()
                        }

                    val answeredUi =
                        answered.map {
                            it.toFeedbackQueueItemUiState()
                        }

                    val pendingCount = pendingUi.size

                    _listState.update { current ->
                        current.copy(
                            feedbackQueue =
                                FeedbackQueueUiState(
                                    pending = pendingUi,
                                    answered = answeredUi,
                                    pendingCount =
                                        pendingCount,
                                    badgeText =
                                        pendingBadgeText(
                                            pendingCount,
                                        ),
                                ),
                        )
                    }

                    scheduleFeedbackQueueExpiryRefresh(
                        responses = pending + answered,
                    )
                }
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

    fun startNew(
        type: JournalNoteType,
    ) {
        activeEditorNoteId = 0L
        editorLoadJob?.cancel()
        checklistLoadJob?.cancel()

        _editorState.value =
            JournalEditorUiState(
                type = type,
                titleDraft = "",
                bodyDraft = "",
                sketchDraft = "",
                checklistItems =
                    if (
                        type ==
                        JournalNoteType.Checklist
                    ) {
                        listOf(
                            newBlankChecklistItem(),
                        )
                    } else {
                        emptyList()
                    },
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
            val fromType = current.type
            val newChecklistItems = when {
                type == JournalNoteType.Checklist && current.checklistItems.all { it.text.isBlank() } -> {
                    val bodyLines = current.bodyDraft.lines().filter { it.isNotBlank() }
                    if (bodyLines.isNotEmpty()) {
                        bodyLines.map { line -> ChecklistDraftItem(localId = nextLocalChecklistId--, text = line.trim()) }
                    } else {
                        listOf(newBlankChecklistItem())
                    }
                }
                type == JournalNoteType.Checklist && current.checklistItems.isEmpty() ->
                    listOf(newBlankChecklistItem())
                else -> current.checklistItems
            }
            val newBody = when {
                (type == JournalNoteType.Text || type == JournalNoteType.Reminder) &&
                    fromType == JournalNoteType.Checklist &&
                    current.bodyDraft.isBlank() -> {
                    current.checklistItems
                        .filter { it.text.isNotBlank() }
                        .joinToString("\n") { it.text.trim() }
                }
                else -> current.bodyDraft
            }
            current.copy(
                type = type,
                bodyDraft = newBody,
                checklistItems = newChecklistItems,
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

    fun setReminderTodayAt(hour: Int, minute: Int = 0) {
        val now = LocalDateTime.now(ReminderZone)
        val target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
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

    fun saveCurrent(onSaved: (() -> Unit)? = null) {
        val current = _editorState.value
        val isNewNote = current.noteId == 0L
        val hasMeaningfulContent = current.bodyDraft.isNotBlank() ||
            current.sketchDraft.isNotBlank() ||
            current.titleDraft.isNotBlank() ||
            current.checklistItems.any { it.text.isNotBlank() } ||
            current.reminderAtMillis != null
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
            // A newly created note with real content earns level points once.
            // Re-saving an existing note does not, since by then noteId is no longer
            // 0, so the reward cannot be farmed by saving the same note repeatedly.
            if (
                isNewNote &&
                hasMeaningfulContent
            ) {
                taskRewardRepository
                    .awardLevelPoints(
                        NoteCreationLevelPoints,
                    )
            }
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
            onSaved?.invoke()
        }
    }

    fun saveCurrentIfNeeded(
        onSaved: () -> Unit,
        onPersisted: () -> Unit = {},
    ) {
        val current = _editorState.value
        val hasMeaningfulDraft =
            current.noteId != 0L ||
                current.titleDraft.isNotBlank() ||
                current.bodyDraft.isNotBlank() ||
                current.sketchDraft.isNotBlank() ||
                current.checklistItems.any { it.text.isNotBlank() } ||
                current.reminderAtMillis != null

        if (!hasMeaningfulDraft) {
            onSaved()
            return
        }

        saveCurrent {
            onPersisted()
            onSaved()
        }
    }

    fun answerFeedbackResponse(
        responseId: Long,
        answerIndex: Int,
        onComplete: () -> Unit = {},
    ) {
        if (
            responseId <= 0L ||
            answerIndex !in 0..1
        ) {
            onComplete()
            return
        }

        viewModelScope.launch {
            try {
                val answeredAtMillis =
                    System.currentTimeMillis()

                val markedAnswered =
                    feedbackResponseRepository.markAnswered(
                        responseId = responseId,
                        answerIndex = answerIndex,
                        answeredAtMillis =
                            answeredAtMillis,
                    )

                if (!markedAnswered) {
                    return@launch
                }

                taskRewardRepository
                    .awardFeedbackAnswerPointsIfNewDay(
                        FeedbackAnswerReceiver
                            .FeedbackAnswerPoints,
                    )

                ProtectionNotificationGate.cancelQueued(
                    FeedbackAnswerReceiver.FeedbackNotificationId,
                )
                NotificationManagerCompat
                    .from(
                        getApplication<Application>(),
                    )
                    .cancel(
                        FeedbackAnswerReceiver
                            .FeedbackNotificationId,
                    )
            } finally {
                onComplete()
            }
        }
    }

    fun consumeInAppReviewEligibility(
        onConsumed: () -> Unit,
    ) {
        viewModelScope.launch {
            val consumed = runCatching {
                playStoreRatingPromptDataSource
                    .consumeInAppReviewEligibility()
            }.getOrDefault(false)

            if (consumed) {
                onConsumed()
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

    fun deleteNotes(noteIds: List<Long>, onDeleted: () -> Unit = {}) {
        if (noteIds.isEmpty()) {
            onDeleted()
            return
        }

        viewModelScope.launch {
            repository.deleteNotes(noteIds)
            onDeleted()
        }
    }

    fun setPinned(noteId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(noteId, pinned) }
    }

    fun toggleCurrentPinned() {
        val current = _editorState.value
        val nextPinned = !current.isPinned

        _editorState.update {
            it.copy(
                isPinned = nextPinned,
                savedNoteId = null,
                noteLimitReached = false,
            )
        }

        if (current.noteId != 0L) {
            viewModelScope.launch {
                repository.setPinned(current.noteId, nextPinned)
            }
        }
    }

    fun setHighlight(noteId: Long, highlightColor: String?) {
        viewModelScope.launch { repository.setHighlight(noteId, highlightColor) }
    }

    fun setCurrentHighlight(highlightColor: String?) {
        val current = _editorState.value

        _editorState.update {
            it.copy(
                highlightColor = highlightColor,
                savedNoteId = null,
                noteLimitReached = false,
            )
        }

        if (current.noteId != 0L) {
            viewModelScope.launch {
                repository.setHighlight(current.noteId, highlightColor)
            }
        }
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

    private fun scheduleFeedbackQueueExpiryRefresh(
        responses: List<FeedbackResponseEntity>,
    ) {
        feedbackQueueExpiryJob?.cancel()
        feedbackQueueExpiryJob = null

        val nearestExpiryMillis =
            responses.minOfOrNull {
                it.expiresAtMillis
            } ?: return

        feedbackQueueExpiryJob =
            viewModelScope.launch {
                while (true) {
                    val remainingMillis =
                        nearestExpiryMillis -
                            System.currentTimeMillis()

                    if (remainingMillis <= 0L) {
                        break
                    }

                    delay(remainingMillis)
                }

                feedbackQueueExpiryJob = null

                val refreshNowMillis =
                    System.currentTimeMillis()

                feedbackQueueNowMillis.value =
                    refreshNowMillis

                feedbackResponseRepository.deleteExpired(
                    refreshNowMillis,
                )
            }
    }

    private fun newBlankChecklistItem(): ChecklistDraftItem {
        return ChecklistDraftItem(localId = nextLocalChecklistId--, text = "")
    }
}

private fun FeedbackResponseEntity
    .toFeedbackQueueItemUiState():
    FeedbackQueueItemUiState {

    val selectedAnswer = when (
        selectedAnswerIndex
    ) {
        0 -> positiveAnswerText
        1 -> honestAnswerText
        else -> null
    }

    return FeedbackQueueItemUiState(
        responseId = id,
        question = questionText,
        positiveAnswer = positiveAnswerText,
        honestAnswer = honestAnswerText,
        selectedAnswerIndex =
            selectedAnswerIndex,
        selectedAnswer = selectedAnswer,
        createdAtMillis = createdAtMillis,
        answeredAtMillis = answeredAtMillis,
        expiresAtMillis = expiresAtMillis,
    )
}

private fun pendingBadgeText(
    pendingCount: Int,
): String? {
    return when {
        pendingCount <= 0 -> null
        pendingCount <= 9 ->
            pendingCount.toString()
        else -> "9+"
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
