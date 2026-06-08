@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.impulsive.app.frontend.screens.journal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.backend.session.tasks.ChecklistDraftItem
import com.impulsive.app.backend.session.tasks.JournalViewModel
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
        JournalHeader(title = "Notes", onBack = onBack)

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
}

@Composable
fun JournalListScreen(
    onBack: () -> Unit,
    onCreateNote: (JournalNoteType) -> Unit,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(),
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    var actionNote by remember { mutableStateOf<JournalNoteEntity?>(null) }
    var highlightNote by remember { mutableStateOf<JournalNoteEntity?>(null) }
    var categorizeNote by remember { mutableStateOf<JournalNoteEntity?>(null) }
    var deleteNote by remember { mutableStateOf<JournalNoteEntity?>(null) }

    BackHandler { onBack() }
    val pinned = state.notes.filter { it.isPinned }
    val others = state.notes.filterNot { it.isPinned }

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
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                JournalHeader(title = "Notes", onBack = onBack)
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                NotesCountRow(
                    count = state.noteCount,
                    max = state.maxNotes,
                    canCreate = state.canCreateMore,
                    onNew = { onCreateNote(JournalNoteType.Text) },
                )
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
                            onClick = { onOpenNote(note.id) },
                            onLongPress = { actionNote = note },
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
                            onClick = { onOpenNote(note.id) },
                            onLongPress = { actionNote = note },
                        )
                    }
                }
            }
        }
    }

    actionNote?.let { note ->
        NoteActionDialog(
            note = note,
            onDismiss = { actionNote = null },
            onPinToggle = {
                viewModel.setPinned(note.id, !note.isPinned)
                actionNote = null
            },
            onHighlight = {
                highlightNote = note
                actionNote = null
            },
            onCategorize = {
                categorizeNote = note
                actionNote = null
            },
            onMoveUp = {
                viewModel.moveNoteUp(note.id)
                actionNote = null
            },
            onMoveDown = {
                viewModel.moveNoteDown(note.id)
                actionNote = null
            },
            onDelete = {
                deleteNote = note
                actionNote = null
            },
        )
    }

    highlightNote?.let { note ->
        HighlightDialog(
            onDismiss = { highlightNote = null },
            onSelected = { colorKey ->
                viewModel.setHighlight(note.id, colorKey)
                highlightNote = null
            },
        )
    }

    categorizeNote?.let { note ->
        CategoryDialog(
            initialValue = note.category,
            onDismiss = { categorizeNote = null },
            onSave = { category ->
                viewModel.setCategory(note.id, category)
                categorizeNote = null
            },
        )
    }

    deleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteNote = null },
            title = { Text("Delete note?") },
            text = { Text("This removes the note and cancels its reminder if one is set.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        deleteNote = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteNote = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun JournalEditorScreen(
    noteId: Long,
    initialType: JournalNoteType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(),
) {
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val isAtSaveLimit = noteId == 0L && !listState.canCreateMore
    var toolsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(noteId, initialType) {
        if (noteId == 0L) viewModel.startNew(initialType) else viewModel.loadExisting(noteId)
    }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JournalHeader(title = if (noteId == 0L) "New note" else "Edit note", onBack = onBack)

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
                reminderAtMillis = state.reminderAtMillis,
                onBodyChanged = viewModel::updateBody,
                onLaterToday = viewModel::setReminderTodayEvening,
                onTomorrowMorning = viewModel::setReminderTomorrowMorning,
                onNextFriday = viewModel::setReminderNextFridayMorning,
                onPickDateTime = viewModel::setCustomReminder,
                onClear = viewModel::clearReminder,
            )
        }

        if (state.type != JournalNoteType.Reminder) {
            ReminderCard(
                reminderAtMillis = state.reminderAtMillis,
                onLaterToday = viewModel::setReminderTodayEvening,
                onTomorrowMorning = viewModel::setReminderTomorrowMorning,
                onNextFriday = viewModel::setReminderNextFridayMorning,
                onPickDateTime = viewModel::setCustomReminder,
                onClear = viewModel::clearReminder,
            )
        }

        NoteToolDock(
            selectedType = state.type,
            expanded = toolsExpanded,
            onToggleExpanded = { toolsExpanded = !toolsExpanded },
            onSelectType = { type ->
                viewModel.updateType(type)
                toolsExpanded = false
            },
        )

        if (isAtSaveLimit || state.noteLimitReached) {
            SaveLimitCard()
        }

        Button(
            onClick = viewModel::saveCurrent,
            enabled = !isAtSaveLimit,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C5A8F),
                contentColor = Color.White,
            ),
        ) {
            Icon(if (state.savedNoteId != null) Icons.Outlined.Check else Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.savedNoteId != null) "Saved" else "Save note")
        }

        if (state.noteId != 0L) {
            OutlinedButton(
                onClick = { viewModel.deleteCurrent(onBack) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete note")
            }
        }
    }
}

@Composable
private fun JournalHeader(title: String, onBack: () -> Unit) {
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
private fun NotesCountRow(count: Int, max: Int, canCreate: Boolean, onNew: () -> Unit) {
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
        Button(
            onClick = onNew,
            enabled = canCreate,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("New note")
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
    Surface(
        color = if (enabled) ImpulsivePsychological.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = if (enabled) 0.24f else 0.08f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
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
                    .clickable { onToggleExpanded() },
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
                    JournalNoteType.entries.forEach { type ->
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
    Surface(
        color = if (selected) type.accentColor().copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.0f else 0.08f))
        } else {
            null
        },
        modifier = Modifier.clickable { onClick() },
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
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        color = note.cardColor(),
        shape = RoundedCornerShape(26.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
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
    Surface(
        color = note.cardColor(),
        shape = RoundedCornerShape(22.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
                    Surface(
                        color = type.accentColor().copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable(enabled = canCreate) { onCreate(type) },
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
private fun NoteActionDialog(
    note: JournalNoteEntity,
    onDismiss: () -> Unit,
    onPinToggle: () -> Unit,
    onHighlight: () -> Unit,
    onCategorize: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.displayTitle()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(if (note.isPinned) "Unpin" else "Pin", onPinToggle)
                ActionRow("Highlight", onHighlight)
                ActionRow("Categorize", onCategorize)
                ActionRow("Move up", onMoveUp)
                ActionRow("Move down", onMoveDown)
                ActionRow("Delete", onDelete)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(13.dp))
    }
}

@Composable
private fun HighlightDialog(onDismiss: () -> Unit, onSelected: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightOption("Lavender", HighlightPsychology, onSelected)
                HighlightOption("Blue", HighlightPhysical, onSelected)
                HighlightOption("Yellow", HighlightSpiritual, onSelected)
                HighlightOption("Coral", HighlightFocus, onSelected)
                HighlightOption("None", null, onSelected)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HighlightOption(label: String, key: String?, onSelected: (String?) -> Unit) {
    val color = highlightColorForKey(key) ?: MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = color.copy(alpha = if (key == null) 1f else 0.34f),
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().clickable { onSelected(key) },
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(13.dp))
    }
}

@Composable
private fun CategoryDialog(initialValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(32) },
                label = { Text("Label or tag") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextEditorBody(body: String, onBodyChanged: (String) -> Unit) {
    OutlinedTextField(
        value = body,
        onValueChange = onBodyChanged,
        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
        label = { Text("Write freely") },
        placeholder = { Text("What happened, what helped, or what should I remember?") },
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun ChecklistEditorBody(
    items: List<ChecklistDraftItem>,
    onItemChanged: (Long, String) -> Unit,
    onToggle: (Long) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
) {
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
            items.sortedWith(compareBy<ChecklistDraftItem> { it.isChecked }.thenBy { it.localId }).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = item.isChecked, onCheckedChange = { onToggle(item.localId) })
                    OutlinedTextField(
                        value = item.text,
                        onValueChange = { onItemChanged(item.localId, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("List item") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    )
                    IconButton(onClick = { onRemoveItem(item.localId) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onAddItem, shape = RoundedCornerShape(22.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add item")
            }
            Text(
                text = "Checked items move to the bottom and stay visible.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SketchEditorBody(sketch: String, onSketchChanged: (String) -> Unit) {
    var paths by remember { mutableStateOf(decodeSketch(sketch)) }
    val sketchStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    LaunchedEffect(sketch) {
        if (sketch.isNotBlank() && paths.isEmpty()) paths = decodeSketch(sketch)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = Color(0xFFFFFCFF),
            shape = RoundedCornerShape(28.dp),
            border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().height(310.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                paths = paths + listOf(listOf(offset))
                                onSketchChanged(encodeSketch(paths))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val updated = if (paths.isEmpty()) listOf(listOf(change.position))
                                else paths.dropLast(1) + listOf(paths.last() + change.position)
                                paths = updated
                                onSketchChanged(encodeSketch(updated))
                            },
                        )
                    },
            ) {
                paths.forEach { points ->
                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = sketchStrokeColor,
                            style = Stroke(width = 5f),
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                paths = emptyList()
                onSketchChanged("")
            },
            shape = RoundedCornerShape(22.dp),
        ) { Text("Clear drawing") }
    }
}

@Composable
private fun ReminderEditorBody(
    body: String,
    reminderAtMillis: Long?,
    onBodyChanged: (String) -> Unit,
    onLaterToday: () -> Unit,
    onTomorrowMorning: () -> Unit,
    onNextFriday: () -> Unit,
    onPickDateTime: (year: Int, monthZeroBased: Int, dayOfMonth: Int, hour: Int, minute: Int) -> Unit,
    onClear: () -> Unit,
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
        ReminderCard(
            reminderAtMillis = reminderAtMillis,
            onLaterToday = onLaterToday,
            onTomorrowMorning = onTomorrowMorning,
            onNextFriday = onNextFriday,
            onPickDateTime = onPickDateTime,
            onClear = onClear,
        )
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
                        Text("Clear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClear() })
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
            .clickable { onClick() }
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

private fun highlightColorForKey(key: String?): Color? = when (key) {
    HighlightPsychology -> ImpulsivePsychological
    HighlightPhysical -> ImpulsivePhysical
    HighlightSpiritual -> ImpulsiveSpiritual
    HighlightFocus -> ImpulsiveFocusMode
    else -> null
}

private fun encodeSketch(paths: List<List<Offset>>): String {
    return paths.joinToString("|") { path ->
        path.joinToString(";") { point ->
            "${point.x.roundToInt()},${point.y.roundToInt()}"
        }
    }
}

private fun decodeSketch(value: String): List<List<Offset>> {
    if (value.isBlank()) return emptyList()
    return value.split("|").mapNotNull { pathRaw ->
        val points = pathRaw.split(";").mapNotNull { pointRaw ->
            val pieces = pointRaw.split(",")
            val x = pieces.getOrNull(0)?.toFloatOrNull()
            val y = pieces.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) Offset(x, y) else null
        }
        points.takeIf { it.size > 1 }
    }
}

private val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val ReminderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
private val ReminderCompactFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM h:mm a")
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
