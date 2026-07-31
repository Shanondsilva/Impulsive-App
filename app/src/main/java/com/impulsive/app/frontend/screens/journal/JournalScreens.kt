@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.impulsive.app.frontend.screens.journal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.preferences.PlayStoreRatingPromptState
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.backend.service.journal.FeedbackPromptScheduler
import com.impulsive.app.backend.service.review.findHostActivity
import com.impulsive.app.backend.service.review.launchImpulsiveInAppReview
import com.impulsive.app.backend.session.tasks.ChecklistDraftItem
import com.impulsive.app.backend.session.tasks.FeedbackQueueItemUiState
import com.impulsive.app.backend.session.tasks.JournalViewModel
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun JournalHubScreen(
    onBack: () -> Unit,
    onOpenNormalJournal: () -> Unit,
    onCreateNote: (JournalNoteType) -> Unit,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(),
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    var showNotesInfo by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        JournalHeader(title = "Notes", onBack = onBack, onInfo = { showNotesInfo = true })

        JournalModeCard(
            title = "Notes",
                subtitle = "${state.noteCount} / ${state.maxNotes} saves · notes, lists and reminders.",
            action = "Open",
            iconTint = ImpulsiveSpiritual.copy(alpha = 0.82f),
            icon = { Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            onClick = onOpenNormalJournal,
        )

        if (!state.canCreateMore) {
            SaveLimitCard()
        }

        if (state.recentNotes.isNotEmpty()) {
            Text(
                text = "Recent Notes",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            state.recentNotes.take(4).forEach { note ->
                CompactJournalNoteCard(note = note, onClick = { onOpenNote(note.id) })
            }
        }
    }

    if (showNotesInfo) {
        NotesAboutDialog(onDismiss = { showNotesInfo = false })
    }
}

@Composable
fun JournalListScreen(
    onBack: () -> Unit,
    onCreateNote: (JournalNoteType) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenSavedNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(),
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val context =
        LocalContext.current
    var selectedNoteIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var createMenuExpanded by remember { mutableStateOf(false) }
    var answeringFeedbackResponseId by
        remember {
            mutableStateOf<Long?>(null)
        }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    BackHandler {
        if (isSelectionMode) {
            selectedNoteIds = emptySet()
        } else {
            onBack()
        }
    }
    val pinned = state.notes.filter { it.isPinned }
    val others = state.notes.filterNot { it.isPinned }
    val pendingFeedback =
        state.feedbackQueue.pending
            .firstOrNull()
    val latestSavedNotification =
        state.feedbackQueue.answered
            .firstOrNull()

    fun toggleSelected(noteId: Long) {
        selectedNoteIds =
            if (noteId in selectedNoteIds) {
                selectedNoteIds - noteId
            } else {
                selectedNoteIds + noteId
            }
    }

    fun enterSelection(noteId: Long) {
        selectedNoteIds = selectedNoteIds + noteId
    }

    fun clearSelection() {
        selectedNoteIds = emptySet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 320.dp,
            ),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                JournalHeader(title = "Notes", onBack = onBack)
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                NotesCountRow(
                    count = state.noteCount,
                    max = state.maxNotes,
                )
            }
            if (isSelectionMode) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    NotesSelectionActionRow(
                        selectedCount = selectedNoteIds.size,
                        onCancel = ::clearSelection,
                        onDeleteSelected = {
                            confirmDeleteSelected = true
                        },
                    )
                }
            }
            if (!state.canCreateMore) {
                item(span = StaggeredGridItemSpan.FullLine) { SaveLimitCard() }
            }
            if (state.notes.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyJournalState(
                        canCreate = state.canCreateMore,
                        onCreateNote = { onCreateNote(JournalNoteType.Text) },
                    )
                }
            } else {
                if (pinned.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { NotesSectionLabel("Pinned") }
                    items(pinned, key = { it.id }) { note ->
                        JournalNoteCard(
                            note = note,
                            selected = note.id in selectedNoteIds,
                            selectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelected(note.id)
                                } else {
                                    onOpenNote(note.id)
                                }
                            },
                            onLongPress = {
                                enterSelection(note.id)
                            },
                        )
                    }
                }
                if (others.isNotEmpty()) {
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) { NotesSectionLabel("Others") }
                    }
                    items(others, key = { it.id }) { note ->
                        JournalNoteCard(
                            note = note,
                            selected = note.id in selectedNoteIds,
                            selectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelected(note.id)
                                } else {
                                    onOpenNote(note.id)
                                }
                            },
                            onLongPress = {
                                enterSelection(note.id)
                            },
                        )
                    }
                }
            }
        }
        NotesCreateFab(
            expanded = createMenuExpanded,
            enabled = state.canCreateMore,
            pendingFeedback =
                pendingFeedback,
            latestSavedNotification =
                latestSavedNotification,
            playStoreRatingPrompt =
                state.playStoreRatingPrompt,
            isAnsweringPendingFeedback =
                answeringFeedbackResponseId ==
                    pendingFeedback?.responseId,
            onAnswerPendingFeedback = {
                    responseId,
                    answerIndex ->

                if (
                    answeringFeedbackResponseId ==
                    null
                ) {
                    answeringFeedbackResponseId =
                        responseId

                    viewModel.answerFeedbackResponse(
                        responseId = responseId,
                        answerIndex = answerIndex,
                        onComplete = {
                            answeringFeedbackResponseId =
                                null
                        },
                    )
                }
            },
            onRequestInAppReview = requestReview@{
                val activity = context.findHostActivity()
                    ?: return@requestReview

                viewModel.consumeInAppReviewEligibility {
                    launchImpulsiveInAppReview(activity)
                }
            },
            onToggle = { createMenuExpanded = !createMenuExpanded },
            onCreateText = {
                createMenuExpanded = false
                onCreateNote(JournalNoteType.Text)
            },
            onCreateList = {
                createMenuExpanded = false
                onCreateNote(JournalNoteType.Checklist)
            },
            onCreateDraw = {
                createMenuExpanded = false
                onCreateNote(JournalNoteType.Sketch)
            },
            onOpenSavedNotifications = {
                createMenuExpanded = false
                onOpenSavedNotifications()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = 14.dp,
                    vertical = 24.dp,
                ),
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Delete selected notes?") },
            text = { Text("This removes the selected notes and cancels any reminders set on them.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedNoteIds.toList()
                        viewModel.deleteNotes(idsToDelete) {
                            selectedNoteIds = emptySet()
                            confirmDeleteSelected = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ImpulsiveFocusMode,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteSelected = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ImpulsivePsychological,
                    ),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun SavedNotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel =
        viewModel(),
) {
    val state by
        viewModel.listState
            .collectAsStateWithLifecycle()

    var answeringFeedbackResponseId by
        remember {
            mutableStateOf<Long?>(null)
        }

    var nowMillis by remember {
        mutableStateOf(
            System.currentTimeMillis(),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)

            nowMillis =
                System.currentTimeMillis()
        }
    }

    BackHandler {
        onBack()
    }

    val pending =
        state.feedbackQueue.pending
            .firstOrNull()

    val answered =
        state.feedbackQueue.answered

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme
                    .colorScheme
                    .background,
            )
            .statusBarsPadding(),
        contentPadding =
            PaddingValues(
                horizontal = 18.dp,
                vertical = 16.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        item {
            JournalHeader(
                title =
                    "Saved Notifications",
                onBack = onBack,
            )
        }

        pending?.let { response ->
            item(
                key =
                    "pending_${response.responseId}",
            ) {
                PendingSavedNotificationCard(
                    response = response,
                    isAnswering =
                        answeringFeedbackResponseId ==
                            response.responseId,
                    onAnswer = { answerIndex ->
                        if (
                            answeringFeedbackResponseId ==
                            null
                        ) {
                            answeringFeedbackResponseId =
                                response.responseId

                            viewModel
                                .answerFeedbackResponse(
                                    responseId =
                                        response.responseId,
                                    answerIndex =
                                        answerIndex,
                                    onComplete = {
                                        answeringFeedbackResponseId =
                                            null
                                    },
                                )
                        }
                    },
                )
            }
        }

        if (
            pending == null &&
            answered.isEmpty()
        ) {
            item {
                SavedNotificationsEmptyState(
                    nextScheduledAtMillis =
                        FeedbackPromptScheduler
                            .nextScheduledAtMillis(
                                nowMillis =
                                    nowMillis,
                            ),
                )
            }
        } else {
            lazyItems(
                items = answered,
                key = {
                    it.responseId
                },
            ) { response ->
                SavedNotificationCard(
                    response = response,
                    nowMillis = nowMillis,
                )
            }
        }
    }
}

@Composable
private fun SavedNotificationsEmptyState(
    nextScheduledAtMillis: Long,
) {
    val cardShape =
        RoundedCornerShape(24.dp)

    Surface(
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant,
        shape = cardShape,
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    "No saved notifications yet",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Text(
                text =
                    "Your answered feedback notifications will remain here for seven days.",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )

            Text(
                text =
                    "Next notification scheduled for ${
                        formatSavedNotificationSchedule(
                            nextScheduledAtMillis,
                        )
                    }.",
                color =
                    ImpulsivePsychological,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PendingSavedNotificationCard(
    response: FeedbackQueueItemUiState,
    isAnswering: Boolean,
    onAnswer: (Int) -> Unit,
) {
    val cardShape =
        RoundedCornerShape(24.dp)

    Surface(
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant,
        shape = cardShape,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    ImpulsivePsychological
                        .copy(alpha = 0.28f),
            ),
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(11.dp),
        ) {
            PendingFeedbackQuestionContent(
                response = response,
                isAnswering = isAnswering,
                onAnswer = onAnswer,
            )
        }
    }
}

@Composable
private fun SavedNotificationCard(
    response: FeedbackQueueItemUiState,
    nowMillis: Long,
) {
    val cardShape =
        RoundedCornerShape(24.dp)

    val answeredAtMillis =
        response.answeredAtMillis
            ?: response.createdAtMillis

    val selectedAnswer =
        response.selectedAnswer
            .orEmpty()
            .ifBlank {
                "Answer unavailable"
            }

    Surface(
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant,
        shape = cardShape,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    ImpulsivePsychological
                        .copy(alpha = 0.18f),
            ),
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        formatSavedNotificationDate(
                            answeredAtMillis,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        savedNotificationDeletionLabel(
                            expiresAtMillis =
                                response
                                    .expiresAtMillis,
                            nowMillis =
                                nowMillis,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                )
            }

            Text(
                text = response.question,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
                fontWeight =
                    FontWeight.Bold,
            )

            Surface(
                color =
                    ImpulsivePsychological
                        .copy(alpha = 0.20f),
                shape =
                    RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = selectedAnswer,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    modifier =
                        Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 10.dp,
                        ),
                )
            }
        }
    }
}

@Composable
fun JournalEditorScreen(
    noteId: Long,
    initialType: JournalNoteType,
    onBack: () -> Unit,
    onAdaptiveSaved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(),
) {
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val isAtSaveLimit = noteId == 0L && !listState.canCreateMore
    var reminderDialogOpen by remember { mutableStateOf(false) }
    var labelDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(noteId, initialType) {
        if (noteId == 0L) viewModel.startNew(initialType) else viewModel.loadExisting(noteId)
    }

    fun exitEditor() {
        viewModel.saveCurrentIfNeeded(
            onSaved = onBack,
            onPersisted = { onAdaptiveSaved?.invoke() },
        )
    }

    BackHandler { exitEditor() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JournalEditorHeader(
            title = if (noteId == 0L) "New note" else "Edit note",
            isPinned = state.isPinned,
            hasReminder = state.reminderAtMillis != null,
            hasLabel = state.highlightColor != null,
            onBack = ::exitEditor,
            onReminderClick = { reminderDialogOpen = true },
            onPinClick = viewModel::toggleCurrentPinned,
            onLabelClick = { labelDialogOpen = true },
        )

        OutlinedTextField(
            value = state.titleDraft,
            onValueChange = viewModel::updateTitle,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Title") },
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        when (state.type) {
            JournalNoteType.Text -> TextEditorBody(state.bodyDraft, viewModel::updateBody)
            JournalNoteType.Checklist -> ChecklistEditorBody(
                items = state.checklistItems,
                onItemChanged = viewModel::updateChecklistItem,
                onToggle = viewModel::toggleChecklistItem,
                onAddItem = viewModel::addChecklistItem,
                onRemoveItem = viewModel::removeChecklistItem,
            )
            JournalNoteType.Sketch -> SketchEditorBody(state.sketchDraft, viewModel::updateSketch)
            JournalNoteType.Reminder -> ReminderEditorBody(
                body = state.bodyDraft,
                onBodyChanged = viewModel::updateBody,
            )
        }

        if (isAtSaveLimit || state.noteLimitReached) {
            SaveLimitCard()
        }

    }

    if (reminderDialogOpen) {
        EditorReminderDialog(
            reminderAtMillis = state.reminderAtMillis,
            onDismiss = { reminderDialogOpen = false },
            onTimeSelected = { hour, minute ->
                viewModel.setReminderTodayAt(hour, minute)
                reminderDialogOpen = false
            },
            onPickDateTime = { year, monthZeroBased, dayOfMonth, hour, minute ->
                viewModel.setCustomReminder(year, monthZeroBased, dayOfMonth, hour, minute)
                reminderDialogOpen = false
            },
            onClear = {
                viewModel.clearReminder()
                reminderDialogOpen = false
            },
        )
    }

    if (labelDialogOpen) {
        EditorLabelDialog(
            selectedKey = state.highlightColor,
            onDismiss = { labelDialogOpen = false },
            onSelected = { colorKey ->
                viewModel.setCurrentHighlight(colorKey)
                labelDialogOpen = false
            },
        )
    }
}

@Composable
private fun JournalHeader(title: String, onBack: () -> Unit, onInfo: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (onInfo != null) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onInfo) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "About Notes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotesAboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = ImpulsiveSpiritual,
            )
        },
        title = { Text("About Notes") },
        text = {
            Text(
                "Notes is your space to capture what helps. Save plain notes, build " +
                    "checklists, and set gentle reminders, so a thought, a plan, or a coping " +
                    "step is ready when you need it. Pin the ones that matter most to keep " +
                    "them at the top.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ImpulsivePsychological,
                ),
            ) { Text("Got it") }
        },
    )
}

@Composable
private fun Modifier.impulsiveNoSquareRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    enabled = enabled,
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
private fun Modifier.impulsiveNoSquareRippleCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick,
)

@Composable
private fun JournalEditorHeader(
    title: String,
    isPinned: Boolean,
    hasReminder: Boolean,
    hasLabel: Boolean,
    onBack: () -> Unit,
    onReminderClick: () -> Unit,
    onPinClick: () -> Unit,
    onLabelClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(onClick = onReminderClick, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "Reminder",
                tint = if (hasReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }

        IconButton(onClick = onPinClick, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = "Pin note",
                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }

        IconButton(onClick = onLabelClick, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Outlined.Label,
                contentDescription = "Label colour",
                tint = if (hasLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun JournalModeCard(
    title: String,
    subtitle: String,
    action: String,
    iconTint: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(28.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = cardShape,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconTint.copy(alpha = 0.58f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotesCountRow(count: Int, max: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$count / $max saves",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NotesSelectionActionRow(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ImpulsivePsychological.copy(alpha = 0.16f),
        border = BorderStroke(
            width = 1.dp,
            color = ImpulsivePsychological.copy(alpha = 0.34f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "$selectedCount selected",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Cancel")
            }

            TextButton(
                onClick = onDeleteSelected,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ImpulsiveFocusMode,
                ),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete selected")
            }
        }
    }
}

@Composable
private fun NotesCreateFab(
    expanded: Boolean,
    enabled: Boolean,
    pendingFeedback:
        FeedbackQueueItemUiState?,
    latestSavedNotification:
        FeedbackQueueItemUiState?,
    playStoreRatingPrompt:
        PlayStoreRatingPromptState,
    isAnsweringPendingFeedback:
        Boolean,
    onAnswerPendingFeedback:
        (
            responseId: Long,
            answerIndex: Int,
        ) -> Unit,
    onRequestInAppReview: () -> Unit,
    onToggle: () -> Unit,
    onCreateText: () -> Unit,
    onCreateList: () -> Unit,
    onCreateDraw: () -> Unit,
    onOpenSavedNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "NotesCreateFabRotation",
    )

    Column(
        modifier =
            modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expanded) {
            NotesCreateOption(
                label = "Draw",
                icon = { Icon(Icons.Outlined.Brush, contentDescription = null) },
                enabled = enabled,
                onClick = onCreateDraw,
            )
            NotesCreateOption(
                label = "List",
                icon = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
                enabled = enabled,
                onClick = onCreateList,
            )
            NotesCreateOption(
                label = "Text",
                icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                enabled = enabled,
                onClick = onCreateText,
            )
        }

        Surface(
            shape = CircleShape,
            color = if (enabled) ImpulsivePsychological else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 12.dp,
            tonalElevation = 8.dp,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .impulsiveNoSquareRippleClickable(enabled = enabled) { onToggle() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create note",
                    tint = Color(0xFF281D38),
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer(rotationZ = rotation),
                )
            }
        }
        SavedNotificationsEntry(
            pendingResponse =
                pendingFeedback,
            latestResponse =
                latestSavedNotification,
            playStoreRatingPrompt =
                playStoreRatingPrompt,
            isAnswering =
                isAnsweringPendingFeedback,
            onAnswer = { answerIndex ->
                val responseId =
                    pendingFeedback
                        ?.responseId
                        ?: return@SavedNotificationsEntry

                onAnswerPendingFeedback(
                    responseId,
                    answerIndex,
                )
            },
            onRequestInAppReview =
                onRequestInAppReview,
            onClick =
                onOpenSavedNotifications,
            modifier =
                Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PendingFeedbackQuestionContent(
    response: FeedbackQueueItemUiState,
    isAnswering: Boolean,
    onAnswer: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = "Today's question",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            fontWeight =
                FontWeight.SemiBold,
        )

        Text(
            text = "Answer before midnight",
            color =
                ImpulsivePsychological,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            fontWeight =
                FontWeight.SemiBold,
        )
    }

    Text(
        text = response.question,
        color =
            MaterialTheme
                .colorScheme
                .onSurface,
        style =
            MaterialTheme
                .typography
                .bodyMedium,
        fontWeight =
            FontWeight.Bold,
        maxLines = 2,
        overflow =
            TextOverflow.Ellipsis,
    )

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                onAnswer(0)
            },
            enabled = !isAnswering,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape =
                RoundedCornerShape(18.dp),
            border =
                BorderStroke(
                    width = 1.dp,
                    color =
                        ImpulsivePsychological
                            .copy(alpha = 0.45f),
                ),
            colors =
                ButtonDefaults
                    .outlinedButtonColors(
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    ),
            contentPadding =
                PaddingValues(
                    horizontal = 8.dp,
                    vertical = 8.dp,
                ),
        ) {
            Text(
                text =
                    response.positiveAnswer,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }

        OutlinedButton(
            onClick = {
                onAnswer(1)
            },
            enabled = !isAnswering,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape =
                RoundedCornerShape(18.dp),
            border =
                BorderStroke(
                    width = 1.dp,
                    color =
                        ImpulsivePsychological
                            .copy(alpha = 0.45f),
                ),
            colors =
                ButtonDefaults
                    .outlinedButtonColors(
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    ),
            contentPadding =
                PaddingValues(
                    horizontal = 8.dp,
                    vertical = 8.dp,
                ),
        ) {
            Text(
                text =
                    response.honestAnswer,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SavedFeedbackSuccessContent(
    nextScheduledAtMillis: Long,
    nowMillis: Long,
) {
    val relativeSchedule =
        formatRelativeFeedbackSchedule(
            scheduledAtMillis =
                nextScheduledAtMillis,
            nowMillis =
                nowMillis,
        ).replaceFirstChar { character ->
            character.uppercase()
        }

    Column(
        modifier =
            Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                "Great work. Impulsive is working.",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurface,
            style =
                MaterialTheme
                    .typography
                    .titleSmall,
            fontWeight =
                FontWeight.Bold,
            maxLines = 2,
            overflow =
                TextOverflow.Ellipsis,
        )

        Surface(
            color =
                ImpulsivePsychological
                    .copy(alpha = 0.18f),
            shape =
                RoundedCornerShape(18.dp),
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text =
                        "Next feedback notification",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        relativeSchedule,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text =
                "Your answer is saved for seven days.",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SavedNotificationsEntry(
    pendingResponse:
        FeedbackQueueItemUiState?,
    latestResponse:
        FeedbackQueueItemUiState?,
    playStoreRatingPrompt:
        PlayStoreRatingPromptState,
    isAnswering: Boolean,
    onAnswer: (Int) -> Unit,
    onRequestInAppReview: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryShape =
        RoundedCornerShape(26.dp)

    val isDark =
        MaterialTheme
            .colorScheme
            .background
            .luminance() < 0.5f

    var nowMillis by remember(
        latestResponse?.responseId,
    ) {
        mutableStateOf(
            System.currentTimeMillis(),
        )
    }

    LaunchedEffect(
        latestResponse?.responseId,
    ) {
        if (latestResponse == null) {
            return@LaunchedEffect
        }

        while (true) {
            delay(60_000L)

            nowMillis =
                System.currentTimeMillis()
        }
    }

    val answeredAtMillis =
        latestResponse
            ?.answeredAtMillis
            ?: latestResponse
                ?.createdAtMillis

    val selectedAnswer =
        latestResponse
            ?.selectedAnswer
            .orEmpty()
            .ifBlank {
                "Answer unavailable"
            }

    val answeredToday =
        latestResponse
            ?.answeredAtMillis
            ?.let { answeredAtMillis ->
                isSameSavedNotificationDate(
                    firstMillis =
                        answeredAtMillis,
                    secondMillis =
                        nowMillis,
                )
            } == true

    val currentEpochDay =
        Instant
            .ofEpochMilli(nowMillis)
            .atZone(DateZone)
            .toLocalDate()
            .toEpochDay()

    val shouldRequestInAppReview =
        pendingResponse == null &&
            answeredToday &&
            playStoreRatingPrompt.isEligibleOn(
                currentEpochDay,
            )

    LaunchedEffect(
        shouldRequestInAppReview,
        currentEpochDay,
    ) {
        if (shouldRequestInAppReview) {
            onRequestInAppReview()
        }
    }

    Surface(
        shape = entryShape,
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    ImpulsivePsychological
                        .copy(
                            alpha =
                                if (isDark) {
                                    0.30f
                                } else {
                                    0.20f
                                },
                        ),
        ),
        shadowElevation = 8.dp,
        modifier = modifier
            .height(220.dp)
            .clip(entryShape)
            .impulsiveNoSquareRippleClickable {
                onClick()
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color =
                                ImpulsivePsychological
                                    .copy(
                                        alpha = 0.26f,
                                    ),
                            shape = CircleShape,
                        ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined
                                .Notifications,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        modifier =
                            Modifier.size(18.dp),
                    )
                }

                Text(
                    text =
                        "Saved Notifications",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                when {
                    pendingResponse != null -> {
                        PendingFeedbackQuestionContent(
                            response =
                                pendingResponse,
                            isAnswering =
                                isAnswering,
                            onAnswer =
                                onAnswer,
                        )
                    }

                    answeredToday -> {
                        SavedFeedbackSuccessContent(
                            nextScheduledAtMillis =
                                FeedbackPromptScheduler
                                    .nextScheduledAtMillis(
                                        nowMillis =
                                            nowMillis,
                                    ),
                            nowMillis =
                                nowMillis,
                        )
                    }

                    latestResponse != null &&
                        answeredAtMillis != null -> {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    formatSavedNotificationDate(
                                        answeredAtMillis,
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                fontWeight =
                                    FontWeight.SemiBold,
                            )

                            Text(
                                text =
                                    savedNotificationDeletionLabel(
                                        expiresAtMillis =
                                            latestResponse
                                                .expiresAtMillis,
                                        nowMillis =
                                            nowMillis,
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                            )
                        }

                        Text(
                            text =
                                latestResponse.question,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.Bold,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                        )

                        Surface(
                            color =
                                ImpulsivePsychological
                                    .copy(alpha = 0.20f),
                            shape =
                                RoundedCornerShape(16.dp),
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = selectedAnswer,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                fontWeight =
                                    FontWeight.SemiBold,
                                maxLines = 2,
                                overflow =
                                    TextOverflow.Ellipsis,
                                modifier =
                                    Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 9.dp,
                                    ),
                            )
                        }
                    }

                    else -> {
                        Spacer(
                            modifier =
                                Modifier.height(6.dp),
                        )

                        Text(
                            text =
                                "No saved notifications yet",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.SemiBold,
                        )

                        Text(
                            text =
                                "Today's unanswered question or your latest saved answer will appear here.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            maxLines = 3,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesCreateOption(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val optionShape = RoundedCornerShape(50)
    Surface(
        shape = optionShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = 0.24f))
        } else {
            BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = 0.18f))
        },
        shadowElevation = 6.dp,
        modifier = Modifier
            .clip(optionShape)
            .impulsiveNoSquareRippleClickable(enabled = enabled) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(ImpulsivePsychological.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NotesSectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun CreateNoteCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    Surface(
        color = if (enabled) ImpulsivePsychological.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = cardShape,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = if (enabled) 0.24f else 0.08f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .impulsiveNoSquareRippleClickable(enabled = enabled) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        ImpulsivePsychological.copy(alpha = if (enabled) 0.42f else 0.16f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Create note",
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Write first. Turn it into a list, drawing or reminder inside the note.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NoteToolDock(
    selectedType: JournalNoteType,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectType: (JournalNoteType) -> Unit,
) {
    val dockShape = RoundedCornerShape(28.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = dockShape,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .impulsiveNoSquareRippleClickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(selectedType.accentColor().copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        selectedType.smallIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Note tools",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Current: ${selectedType.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = if (expanded) "Close" else "Open",
                    color = Color(0xFF6C5A8F),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (expanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JournalNoteType.entries
                        .forEach { type ->
                            ToolChoiceChip(
                                type = type,
                                selected = selectedType == type,
                                onClick = { onSelectType(type) },
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun ToolChoiceChip(
    type: JournalNoteType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chipShape = RoundedCornerShape(50)
    Surface(
        color = if (selected) type.accentColor().copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = chipShape,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.0f else 0.08f))
        } else {
            null
        },
        modifier = Modifier
            .clip(chipShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = type.smallIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = type.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SaveLimitCard() {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.14f),
        shape = RoundedCornerShape(22.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = 0.25f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "You have reached 50 saved notes. Existing notes can still be edited.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun EmptyJournalState(canCreate: Boolean, onCreateNote: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(ImpulsivePsychological.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
            Text("No notes yet", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Create a note when your mind feels full.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreateNote, enabled = canCreate, shape = RoundedCornerShape(24.dp)) { Text("Create note") }
        }
    }
}

@Composable
private fun JournalNoteCard(
    note: JournalNoteEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val noteShape = RoundedCornerShape(26.dp)
    Surface(
        color = note.cardColor(),
        shape = noteShape,
        border = when {
            selected -> BorderStroke(2.dp, ImpulsivePsychological.copy(alpha = 0.86f))
            MaterialTheme.colorScheme.background.luminance() < 0.5f ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(noteShape)
            .graphicsLayer {
                alpha = if (selectionMode && !selected) 0.82f else 1f
            }
            .impulsiveNoSquareRippleCombinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).background(note.type().accentColor().copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(note.type().smallIcon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(note.displayTitle(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(note.metaLine(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                if (note.isPinned) {
                    Text("Pinned", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(ImpulsivePsychological.copy(alpha = 0.9f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Text(note.preview(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (note.category.isNotBlank()) NoteBadge(note.category)
                note.reminderAtMillis?.let { NoteBadge("Reminder ${formatReminderCompact(it)}") }
            }
        }
    }
}

@Composable
private fun CompactJournalNoteCard(note: JournalNoteEntity, onClick: () -> Unit) {
    val noteShape = RoundedCornerShape(22.dp)
    Surface(
        color = note.cardColor(),
        shape = noteShape,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(noteShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(note.type().accentColor().copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(note.type().smallIcon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(note.displayTitle(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(note.preview(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NoteBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun NewNoteTypeDialog(
    canCreate: Boolean,
    onDismiss: () -> Unit,
    onCreate: (JournalNoteType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create journal note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (canCreate) "Choose the note type." else "You have reached 50 saved notes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                JournalNoteType.entries.forEach { type ->
                    val typeShape = RoundedCornerShape(18.dp)
                    Surface(
                        color = type.accentColor().copy(alpha = 0.25f),
                        shape = typeShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(typeShape)
                            .impulsiveNoSquareRippleClickable(enabled = canCreate) { onCreate(type) },
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(type.smallIcon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            Text(type.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextEditorBody(
    body: String,
    onBodyChanged: (String) -> Unit,
    heading: String = "Write freely",
    placeholder: String = "What happened, what helped, or what should I remember?",
    readOnly: Boolean = false,
) {
    val editorShape = RoundedCornerShape(28.dp)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = editorShape,
        border = if (isDark) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 430.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = heading,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            BasicTextField(
                value = body,
                onValueChange = onBodyChanged,
                readOnly = readOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 350.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp),
                    ) {
                        if (body.isBlank()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun ChecklistEditorBody(
    items: List<ChecklistDraftItem>,
    onItemChanged: (Long, String) -> Unit,
    onToggle: (Long) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
) {
    val openItems = items.filterNot { it.isChecked }.sortedBy { it.localId }
    val checkedItems = items.filter { it.isChecked }.sortedBy { it.localId }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Checklist", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            openItems.forEach { item ->
                ChecklistItemRow(
                    item = item,
                    onItemChanged = onItemChanged,
                    onToggle = onToggle,
                    onRemoveItem = onRemoveItem,
                )
            }
            OutlinedButton(onClick = onAddItem, shape = RoundedCornerShape(22.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add item")
            }
            if (checkedItems.isNotEmpty()) {
                Text(
                    text = "Checked Items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                checkedItems.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        onItemChanged = onItemChanged,
                        onToggle = onToggle,
                        onRemoveItem = onRemoveItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistDraftItem,
    onItemChanged: (Long, String) -> Unit,
    onToggle: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle(item.localId) },
        )
        OutlinedTextField(
            value = item.text,
            onValueChange = { onItemChanged(item.localId, it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("List item") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            shape = RoundedCornerShape(18.dp),
        )
        IconButton(onClick = { onRemoveItem(item.localId) }) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove item",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SketchEditorBody(sketch: String, onSketchChanged: (String) -> Unit) {
    val defaultStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    var strokes by remember { mutableStateOf(decodeSketch(sketch, defaultStrokeColor)) }
    var activeTool by remember { mutableStateOf(SketchTool.Pencil) }
    var activePanel by remember { mutableStateOf(SketchPanel.None) }
    var activeColor by remember { mutableStateOf(defaultStrokeColor) }
    var activeWidth by remember { mutableStateOf(5f) }

    LaunchedEffect(sketch) {
        if (sketch.isBlank()) {
            strokes = emptyList()
        } else if (strokes.isEmpty()) {
            strokes = decodeSketch(sketch, defaultStrokeColor)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = Color(0xFFFFFCFF),
            shape = RoundedCornerShape(30.dp),
            border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .pointerInput(activeTool, activeColor, activeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (activeTool == SketchTool.Rubber) {
                                    val updated = eraseStrokesNear(strokes, offset)
                                    strokes = updated
                                    onSketchChanged(encodeSketch(updated))
                                } else {
                                    val newStroke = SketchStroke(
                                        points = listOf(offset),
                                        color = activeColor,
                                        width = activeWidth,
                                        tool = activeTool,
                                    )
                                    val updated = strokes + newStroke
                                    strokes = updated
                                    onSketchChanged(encodeSketch(updated))
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()

                                if (activeTool == SketchTool.Rubber) {
                                    val updated = eraseStrokesNear(strokes, change.position)
                                    strokes = updated
                                    onSketchChanged(encodeSketch(updated))
                                } else {
                                    val updated = if (strokes.isEmpty()) {
                                        listOf(
                                            SketchStroke(
                                                points = listOf(change.position),
                                                color = activeColor,
                                                width = activeWidth,
                                                tool = activeTool,
                                            )
                                        )
                                    } else {
                                        strokes.dropLast(1) + strokes.last().copy(
                                            points = strokes.last().points + change.position,
                                        )
                                    }
                                    strokes = updated
                                    onSketchChanged(encodeSketch(updated))
                                }
                            },
                        )
                    },
            ) {
                strokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = stroke.color.copy(alpha = stroke.tool.visibleAlpha()),
                            style = Stroke(
                                width = stroke.tool.visibleWidth(stroke.width),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }

        SketchToolBar(
            activeTool = activeTool,
            activePanel = activePanel,
            activeColor = activeColor,
            activeWidth = activeWidth,
            onRubberSelected = {
                activeTool = SketchTool.Rubber
                activePanel = SketchPanel.None
            },
            onColorPanelSelected = {
                activePanel = if (activePanel == SketchPanel.Color) SketchPanel.None else SketchPanel.Color
                if (activeTool == SketchTool.Rubber) activeTool = SketchTool.Pencil
            },
            onPencilPanelSelected = {
                activePanel = if (activePanel == SketchPanel.Pencil) SketchPanel.None else SketchPanel.Pencil
                if (activeTool == SketchTool.Rubber) activeTool = SketchTool.Pencil
            },
            onColorSelected = { color ->
                activeColor = color
                if (activeTool == SketchTool.Rubber) activeTool = SketchTool.Pencil
            },
            onToolSelected = { tool ->
                activeTool = tool
            },
            onWidthSelected = { width ->
                activeWidth = width
            },
        )

        OutlinedButton(
            onClick = {
                strokes = emptyList()
                onSketchChanged("")
            },
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear drawing")
        }
    }
}

@Composable
private fun SketchToolBar(
    activeTool: SketchTool,
    activePanel: SketchPanel,
    activeColor: Color,
    activeWidth: Float,
    onRubberSelected: () -> Unit,
    onColorPanelSelected: () -> Unit,
    onPencilPanelSelected: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onToolSelected: (SketchTool) -> Unit,
    onWidthSelected: (Float) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(26.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SketchToolButton(
                    label = "Rubber",
                    selected = activeTool == SketchTool.Rubber,
                    icon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = onRubberSelected,
                    modifier = Modifier.weight(1f),
                )
                SketchToolButton(
                    label = "Color",
                    selected = activePanel == SketchPanel.Color,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(activeColor, CircleShape)
                        )
                    },
                    onClick = onColorPanelSelected,
                    modifier = Modifier.weight(1f),
                )
                SketchToolButton(
                    label = "Pencil",
                    selected = activePanel == SketchPanel.Pencil || activeTool == SketchTool.Pencil || activeTool == SketchTool.Marker || activeTool == SketchTool.SketchPen,
                    icon = { Icon(Icons.Outlined.Brush, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = onPencilPanelSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            if (activePanel == SketchPanel.Color) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SketchColorChip("Ink", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f), activeColor, onColorSelected)
                    SketchColorChip("Lavender", ImpulsivePsychological, activeColor, onColorSelected)
                    SketchColorChip("Blue", ImpulsivePhysical, activeColor, onColorSelected)
                    SketchColorChip("Coral", ImpulsiveFocusMode, activeColor, onColorSelected)
                    SketchColorChip("Green", Color(0xFF93E9BE), activeColor, onColorSelected)
                    SketchColorChip("Amber", Color(0xFFFFD58A), activeColor, onColorSelected)
                }
            }

            if (activePanel == SketchPanel.Pencil) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SketchModeChip("Pencil", activeTool == SketchTool.Pencil) { onToolSelected(SketchTool.Pencil) }
                        SketchModeChip("Marker", activeTool == SketchTool.Marker) { onToolSelected(SketchTool.Marker) }
                        SketchModeChip("Sketch pen", activeTool == SketchTool.SketchPen) { onToolSelected(SketchTool.SketchPen) }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SketchSizeChip("Small", activeWidth == 3f) { onWidthSelected(3f) }
                        SketchSizeChip("Medium", activeWidth == 5f) { onWidthSelected(5f) }
                        SketchSizeChip("Large", activeWidth == 8f) { onWidthSelected(8f) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SketchToolButton(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        color = if (selected) {
            ImpulsivePsychological.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (selected) {
                ImpulsivePsychological.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            },
        ),
        modifier = modifier
            .clip(shape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SketchColorChip(
    label: String,
    color: Color,
    activeColor: Color,
    onSelected: (Color) -> Unit,
) {
    val selected = color.toArgb() == activeColor.toArgb()
    val shape = RoundedCornerShape(50)

    Surface(
        color = if (selected) color.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (selected) color.copy(alpha = 0.76f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
        modifier = Modifier
            .clip(shape)
            .impulsiveNoSquareRippleClickable { onSelected(color) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SketchModeChip(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val shape = RoundedCornerShape(50)

    Surface(
        color = if (selected) ImpulsivePsychological.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (selected) ImpulsivePsychological.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
        modifier = Modifier
            .clip(shape)
            .impulsiveNoSquareRippleClickable { onSelected() },
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SketchSizeChip(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val shape = RoundedCornerShape(50)

    Surface(
        color = if (selected) ImpulsivePsychological.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (selected) ImpulsivePsychological.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
        modifier = Modifier
            .clip(shape)
            .impulsiveNoSquareRippleClickable { onSelected() },
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ReminderEditorBody(
    body: String,
    onBodyChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChanged,
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            label = { Text("Reminder note") },
            placeholder = { Text("What should I remember?") },
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun EditorReminderDialog(
    reminderAtMillis: Long?,
    onDismiss: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onPickDateTime: (year: Int, monthZeroBased: Int, dayOfMonth: Int, hour: Int, minute: Int) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind later?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderOptionRow(label = "9:00 AM", time = null, onClick = { onTimeSelected(9, 0) })
                ReminderOptionRow(label = "12:00 PM", time = null, onClick = { onTimeSelected(12, 0) })
                ReminderOptionRow(label = "3:00 PM", time = null, onClick = { onTimeSelected(15, 0) })
                ReminderOptionRow(label = "6:00 PM", time = null, onClick = { onTimeSelected(18, 0) })

                ReminderOptionRow(
                    label = "Pick a date & time",
                    time = null,
                    onClick = {
                        showDateTimePicker(
                            context = context,
                            onPicked = onPickDateTime,
                        )
                    },
                )

                reminderAtMillis?.let {
                    TextButton(onClick = onClear) {
                        Text("Clear reminder")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun EditorLabelDialog(
    selectedKey: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelColorRow("Green", HighlightGreen, Color(0xFF93E9BE), selectedKey, onSelected)
                LabelColorRow("Blue", HighlightBlue, Color(0xFFBDE0FE), selectedKey, onSelected)
                LabelColorRow("Yellow", HighlightYellow, Color(0xFFFEF1AB), selectedKey, onSelected)
                LabelColorRow("Red", HighlightRed, Color(0xFFF5A7A6), selectedKey, onSelected)
                LabelColorRow("Purple", HighlightPurple, Color(0xFFD0C3F1), selectedKey, onSelected)
                LabelColorRow("Amber", HighlightAmber, Color(0xFFFFD58A), selectedKey, onSelected)

                if (selectedKey != null) {
                    TextButton(onClick = { onSelected(null) }) {
                        Text("Clear label")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun LabelColorRow(
    label: String,
    key: String,
    color: Color,
    selectedKey: String?,
    onSelected: (String?) -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        color = if (selectedKey == key) color.copy(alpha = 0.38f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = rowShape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .impulsiveNoSquareRippleClickable { onSelected(key) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, CircleShape),
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminderAtMillis: Long?,
    onLaterToday: () -> Unit,
    onTomorrowMorning: () -> Unit,
    onNextFriday: () -> Unit,
    onPickDateTime: (year: Int, monthZeroBased: Int, dayOfMonth: Int, hour: Int, minute: Int) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).background(ImpulsiveFocusMode.copy(alpha = 0.42f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Remind me later", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Your reminders stay private on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))

            ReminderOptionRow(label = "Later today", time = "6:00 PM", onClick = onLaterToday)
            ReminderOptionRow(label = "Tomorrow morning", time = "8:00 AM", onClick = onTomorrowMorning)
            ReminderOptionRow(label = "Next Friday", time = "8:00 AM", onClick = onNextFriday)
            ReminderOptionRow(
                label = "Pick a date & time",
                time = null,
                onClick = { showDateTimePicker(context = context, onPicked = onPickDateTime) },
            )

            reminderAtMillis?.let { selected ->
                Surface(
                    color = ImpulsivePsychological.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Selected: ${formatReminder(selected)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Clear",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.impulsiveNoSquareRippleClickable { onClear() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderOptionRow(label: String, time: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .impulsiveNoSquareRippleClickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        time?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun showDateTimePicker(
    context: android.content.Context,
    onPicked: (year: Int, monthZeroBased: Int, dayOfMonth: Int, hour: Int, minute: Int) -> Unit,
) {
    val now = LocalDateTime.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(year, month, day, hour, minute) },
                now.hour,
                now.minute,
                false,
            ).show()
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth,
    ).show()
}

private fun JournalNoteEntity.type(): JournalNoteType = JournalNoteType.fromStorage(noteType)

private fun JournalNoteEntity.displayTitle(): String = title.ifBlank { preview().take(42).ifBlank { type().label } }

private fun JournalNoteEntity.preview(): String {
    return when (type()) {
        JournalNoteType.Text -> body.ifBlank { "No body yet" }
        JournalNoteType.Checklist -> checklist.lines()
            .filter { it.isNotBlank() }
            .joinToString(" • ") { it.removePrefix("[x]").removePrefix("[ ]").trim() }
            .ifBlank { "Empty list" }
        JournalNoteType.Sketch -> if (sketch.isBlank()) "Blank drawing" else "Drawing saved"
        JournalNoteType.Reminder -> body.ifBlank { "Reminder note" }
    }
}

private fun JournalNoteEntity.metaLine(): String {
    val pieces = buildList {
        add(type().label)
        add(updatedLabel())
        if (highlightColor != null) add("Highlighted")
    }
    return pieces.joinToString(" • ")
}

private fun JournalNoteEntity.updatedLabel(): String = formatShortDate(updatedAtMillis)

@Composable
private fun JournalNoteEntity.cardColor(): Color {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = highlightColorForKey(highlightColor) ?: return base
    return highlight.copy(alpha = 0.28f).compositeOver(base)
}

private fun JournalNoteType.accentColor(): Color = when (this) {
    JournalNoteType.Text -> ImpulsivePsychological
    JournalNoteType.Checklist -> ImpulsivePhysical
    JournalNoteType.Sketch -> ImpulsiveSpiritual
    JournalNoteType.Reminder -> ImpulsiveFocusMode
}

private fun JournalNoteType.smallIcon() = when (this) {
    JournalNoteType.Text -> Icons.Outlined.EditNote
    JournalNoteType.Checklist -> Icons.Outlined.Checklist
    JournalNoteType.Sketch -> Icons.Outlined.Brush
    JournalNoteType.Reminder -> Icons.Outlined.Notifications
}

private const val HighlightPsychology = "PSYCHOLOGY"
private const val HighlightPhysical = "PHYSICAL"
private const val HighlightSpiritual = "SPIRITUAL"
private const val HighlightFocus = "FOCUS"
private const val HighlightGreen = "GREEN"
private const val HighlightBlue = "BLUE"
private const val HighlightYellow = "YELLOW"
private const val HighlightRed = "RED"
private const val HighlightPurple = "PURPLE"
private const val HighlightAmber = "AMBER"

private fun highlightColorForKey(key: String?): Color? = when (key) {
    HighlightGreen -> Color(0xFF93E9BE)
    HighlightBlue -> Color(0xFFBDE0FE)
    HighlightYellow -> Color(0xFFFEF1AB)
    HighlightRed -> Color(0xFFF5A7A6)
    HighlightPurple -> Color(0xFFD0C3F1)
    HighlightAmber -> Color(0xFFFFD58A)

    HighlightPsychology -> ImpulsivePsychological
    HighlightPhysical -> ImpulsivePhysical
    HighlightSpiritual -> ImpulsiveSpiritual
    HighlightFocus -> ImpulsiveFocusMode
    else -> null
}

private enum class SketchTool {
    Pencil,
    Marker,
    SketchPen,
    Rubber,
}

private enum class SketchPanel {
    None,
    Color,
    Pencil,
}

private data class SketchStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val tool: SketchTool,
)

private const val SketchEncodingV2Prefix = "v2:"

private fun encodeSketch(strokes: List<SketchStroke>): String {
    if (strokes.isEmpty()) return ""

    return SketchEncodingV2Prefix + strokes.joinToString("|") { stroke ->
        val header = listOf(
            stroke.tool.name,
            stroke.width.roundToInt().toString(),
            stroke.color.toArgb().toString(),
        ).joinToString(",")

        val points = stroke.points.joinToString(";") { point ->
            "${point.x.roundToInt()},${point.y.roundToInt()}"
        }

        "$header#$points"
    }
}

private fun decodeSketch(
    value: String,
    fallbackColor: Color = Color(0xFF2B2636),
): List<SketchStroke> {
    if (value.isBlank()) return emptyList()

    if (!value.startsWith(SketchEncodingV2Prefix)) {
        return value.split("|").mapNotNull { pathRaw ->
            val points = pathRaw.split(";").mapNotNull { pointRaw ->
                val pieces = pointRaw.split(",")
                val x = pieces.getOrNull(0)?.toFloatOrNull()
                val y = pieces.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) Offset(x, y) else null
            }

            points.takeIf { it.size > 1 }?.let {
                SketchStroke(
                    points = it,
                    color = fallbackColor,
                    width = 5f,
                    tool = SketchTool.Pencil,
                )
            }
        }
    }

    return value.removePrefix(SketchEncodingV2Prefix).split("|").mapNotNull { strokeRaw ->
        val parts = strokeRaw.split("#", limit = 2)
        val header = parts.getOrNull(0)?.split(",").orEmpty()
        val pointsRaw = parts.getOrNull(1).orEmpty()

        val tool = header.getOrNull(0)
            ?.let { runCatching { SketchTool.valueOf(it) }.getOrNull() }
            ?: SketchTool.Pencil

        val width = header.getOrNull(1)?.toFloatOrNull() ?: 5f
        val color = header.getOrNull(2)?.toIntOrNull()?.let { Color(it) } ?: fallbackColor

        val points = pointsRaw.split(";").mapNotNull { pointRaw ->
            val pieces = pointRaw.split(",")
            val x = pieces.getOrNull(0)?.toFloatOrNull()
            val y = pieces.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) Offset(x, y) else null
        }

        points.takeIf { it.size > 1 }?.let {
            SketchStroke(
                points = it,
                color = color,
                width = width,
                tool = tool,
            )
        }
    }
}

private fun SketchTool.visibleWidth(baseWidth: Float): Float = when (this) {
    SketchTool.Pencil -> baseWidth
    SketchTool.Marker -> baseWidth + 7f
    SketchTool.SketchPen -> (baseWidth - 1f).coerceAtLeast(2f)
    SketchTool.Rubber -> baseWidth
}

private fun SketchTool.visibleAlpha(): Float = when (this) {
    SketchTool.Pencil -> 0.82f
    SketchTool.Marker -> 0.38f
    SketchTool.SketchPen -> 0.94f
    SketchTool.Rubber -> 1f
}

private fun eraseStrokesNear(
    strokes: List<SketchStroke>,
    point: Offset,
    radius: Float = 34f,
): List<SketchStroke> {
    return strokes.filterNot { stroke ->
        stroke.points.any { strokePoint ->
            strokePoint.distanceTo(point) <= radius
        }
    }
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val ReminderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
private val ReminderCompactFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM h:mm a")
private const val SavedNotificationDayMillis = 24L * 60L * 60L * 1000L
private val SavedNotificationDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val SavedNotificationScheduleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a")
private val SavedNotificationTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val DateZone: ZoneId = ZoneId.systemDefault()

private fun formatShortDate(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(DateZone).format(ShortDateFormatter)
}

private fun formatReminder(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(DateZone).format(ReminderFormatter)
}

private fun formatReminderCompact(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(DateZone).format(ReminderCompactFormatter)
}

private fun formatSavedNotificationDate(
    millis: Long,
): String {
    return Instant
        .ofEpochMilli(millis)
        .atZone(DateZone)
        .format(
            SavedNotificationDateFormatter,
        )
}

private fun formatSavedNotificationSchedule(
    millis: Long,
): String {
    return Instant
        .ofEpochMilli(millis)
        .atZone(DateZone)
        .format(
            SavedNotificationScheduleFormatter,
        )
}

private fun isSameSavedNotificationDate(
    firstMillis: Long,
    secondMillis: Long,
): Boolean {
    val firstDate =
        Instant
            .ofEpochMilli(firstMillis)
            .atZone(DateZone)
            .toLocalDate()

    val secondDate =
        Instant
            .ofEpochMilli(secondMillis)
            .atZone(DateZone)
            .toLocalDate()

    return firstDate == secondDate
}

private fun formatRelativeFeedbackSchedule(
    scheduledAtMillis: Long,
    nowMillis: Long,
): String {
    val nowDate =
        Instant
            .ofEpochMilli(nowMillis)
            .atZone(DateZone)
            .toLocalDate()

    val scheduled =
        Instant
            .ofEpochMilli(
                scheduledAtMillis,
            )
            .atZone(DateZone)

    val scheduledTime =
        scheduled.format(
            SavedNotificationTimeFormatter,
        )

    return when (
        scheduled.toLocalDate()
    ) {
        nowDate ->
            "today at $scheduledTime"

        nowDate.plusDays(1L) ->
            "tomorrow at $scheduledTime"

        else ->
            scheduled.format(
                SavedNotificationScheduleFormatter,
            )
    }
}

private fun savedNotificationDeletionLabel(
    expiresAtMillis: Long,
    nowMillis: Long,
): String {
    val remainingMillis =
        (
            expiresAtMillis -
                nowMillis
        ).coerceAtLeast(1L)

    val remainingDays =
        (
            remainingMillis +
                SavedNotificationDayMillis -
                1L
        ) / SavedNotificationDayMillis

    return if (remainingDays == 1L) {
        "Deletes in 1 day"
    } else {
        "Deletes in $remainingDays days"
    }
}
