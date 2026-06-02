package com.impulsive.app.frontend.screens.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessageKind
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.FutureSelfMessageViewModel
import com.impulsive.app.backend.session.tasks.FutureSelfRecorderState
import com.impulsive.app.backend.session.tasks.MaxRecordingMillis
import com.impulsive.app.backend.session.tasks.MaxTextMessageChars
import com.impulsive.app.frontend.components.FutureSelfActionRow
import com.impulsive.app.frontend.components.FutureSelfHeroPanel
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import kotlinx.coroutines.delay

private enum class RecordMode {
    Voice,
    Text,
}

@Composable
fun FutureSelfRecordScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: FutureSelfMessageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val recordState by viewModel.recordState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedMode by remember {
        mutableStateOf(
            when (recordState.message?.kind) {
                FutureSelfMessageKind.Text -> RecordMode.Text
                else -> RecordMode.Voice
            },
        )
    }

    var micPermissionGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var micPermissionRequested by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermissionGranted = granted
        if (granted) viewModel.startRecording()
    }

    LaunchedEffect(recordState.recorderState) {
        while (recordState.recorderState == FutureSelfRecorderState.Recording) {
            viewModel.updateRecordingTimer()
            delay(100L)
        }
    }

    val seed = onboardingWhySeed(onboardingState.answers.weekOneGoal, onboardingState.answers.name)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onExit = onExit)

        FutureSelfHeroPanel(
            title = "A small message for your future self.",
            subtitle = "Future-self note",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = seed,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Keep this short. It should be easy to hear when the moment gets harder.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ModeToggle(
            selectedMode = selectedMode,
            onModeChange = { selectedMode = it },
        )

        if (!micPermissionGranted && micPermissionRequested) {
            PermissionFallbackNote()
        }

        if (selectedMode == RecordMode.Voice) {
            VoiceRecorderCard(
                recorderState = recordState.recorderState,
                currentRecordingMillis = recordState.currentRecordingMillis,
                hasSavedVoice = recordState.message?.kind == FutureSelfMessageKind.Voice,
                hasPendingVoice = recordState.pendingVoiceFilePath != null,
                pendingDurationMillis = recordState.pendingVoiceDurationMillis,
                onStartRecording = {
                    if (micPermissionGranted) {
                        viewModel.startRecording()
                    } else {
                        micPermissionRequested = true
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = viewModel::stopRecording,
                onSavePending = viewModel::saveVoiceRecording,
                onDiscardPending = viewModel::discardPendingRecording,
                onDeleteSaved = viewModel::deleteSavedMessage,
            )
        } else {
            TextEditorCard(
                textDraft = recordState.textDraft,
                isExistingText = recordState.message?.kind == FutureSelfMessageKind.Text,
                onChange = viewModel::updateTextDraft,
                onSave = viewModel::saveTextMessage,
                prominent = true,
            )
        }

        if (recordState.message != null) {
            DeleteCard(
                kind = recordState.message?.kind,
                onDelete = viewModel::deleteSavedMessage,
            )
        }

        PrivacyNote()
    }
}

@Composable
private fun Header(onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ImpulsiveText,
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = "Future-Self Message",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Record once in a calm moment. Replay during a harder one.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModeToggle(
    selectedMode: RecordMode,
    onModeChange: (RecordMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ImpulsiveSurface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(6.dp)) {
            SegmentedButton(
                label = "Voice",
                selected = selectedMode == RecordMode.Voice,
                onClick = { onModeChange(RecordMode.Voice) },
                modifier = Modifier.weight(1f),
            )
            SegmentedButton(
                label = "Text",
                selected = selectedMode == RecordMode.Text,
                onClick = { onModeChange(RecordMode.Text) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ImpulsivePsychological else Color.Transparent,
            contentColor = if (selected) ImpulsiveText else ImpulsiveMutedText,
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VoiceRecorderCard(
    recorderState: FutureSelfRecorderState,
    currentRecordingMillis: Long,
    hasSavedVoice: Boolean,
    hasPendingVoice: Boolean,
    pendingDurationMillis: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSavePending: () -> Unit,
    onDiscardPending: () -> Unit,
    onDeleteSaved: () -> Unit,
    compact: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (recorderState == FutureSelfRecorderState.Recording) 1f else 0.92f,
        animationSpec = tween(250),
        label = "future_self_recorder_scale",
    )
    val transition = rememberInfiniteTransition(label = "future_self_recorder_wave")
    val wave by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.56f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "future_self_recorder_wave",
    )
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = if (compact) 1.dp else 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Voice note",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Record this when you feel steady. You will hear it when it is hard.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier.size(152.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseRadius = size.minDimension / 2.1f
                    drawCircle(
                        color = ImpulsivePsychological.copy(alpha = 0.12f + wave * 0.10f),
                        radius = baseRadius,
                    )
                    repeat(24) { index ->
                        val angle = index / 24f * 360f
                        val length = baseRadius * (0.18f + wave * 0.22f)
                        drawLine(
                            color = ImpulsivePsychological.copy(alpha = 0.28f + wave * 0.42f),
                            start = polarPoint(size.minDimension / 2f, angle, baseRadius - 18f),
                            end = polarPoint(size.minDimension / 2f, angle, baseRadius - 18f + length),
                            strokeWidth = 3.5f,
                        )
                    }
                    drawCircle(
                        color = surfaceColor,
                        radius = baseRadius * 0.60f,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (recorderState == FutureSelfRecorderState.Recording) ImpulsivePsychological else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.size((92f * scale).dp),
                ) {
                    IconButton(
                        onClick = {
                            if (recorderState == FutureSelfRecorderState.Recording) onStopRecording()
                            else onStartRecording()
                        },
                        modifier = Modifier.size((92f * scale).dp),
                    ) {
                        Icon(
                            imageVector = if (recorderState == FutureSelfRecorderState.Recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (recorderState == FutureSelfRecorderState.Recording) "Stop" else "Record",
                            tint = if (recorderState == FutureSelfRecorderState.Recording) Color.White else ImpulsiveText,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            Text(
                text = when (recorderState) {
                    FutureSelfRecorderState.Recording -> "Recording • ${(currentRecordingMillis / 1000L).toInt()}s / ${(MaxRecordingMillis / 1000L).toInt()}s"
                    FutureSelfRecorderState.Recorded -> "Recorded ${(pendingDurationMillis / 1000L).toInt()}s"
                    FutureSelfRecorderState.Idle -> if (hasSavedVoice) "Saved. You can replace it any time." else "Tap the mic to start."
                },
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            VoiceSecondaryActions(
                recorderState = recorderState,
                hasSavedVoice = hasSavedVoice,
                hasPendingVoice = hasPendingVoice,
                onStopRecording = onStopRecording,
                onDiscardPending = onDiscardPending,
                onSavePending = onSavePending,
                onDeleteSaved = onDeleteSaved,
            )
        }
    }
}

@Composable
private fun VoiceSecondaryActions(
    recorderState: FutureSelfRecorderState,
    hasSavedVoice: Boolean,
    hasPendingVoice: Boolean,
    onStopRecording: () -> Unit,
    onDiscardPending: () -> Unit,
    onSavePending: () -> Unit,
    onDeleteSaved: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (hasPendingVoice) onDiscardPending() },
                enabled = hasPendingVoice,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Re-record") }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Play") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSavePending,
                enabled = recorderState == FutureSelfRecorderState.Recorded && hasPendingVoice,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
            ) { Text("Save") }
            OutlinedButton(
                onClick = onDeleteSaved,
                enabled = hasSavedVoice || hasPendingVoice,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) { Text("Delete") }
        }
    }
}

@Composable
private fun TextEditorCard(
    textDraft: String,
    isExistingText: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    prominent: Boolean = false,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = if (prominent) 2.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Text note",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Write a short message your future self can read quickly.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextField(
                value = textDraft,
                onValueChange = onChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = { Text("A short message your future self should hear...") },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = ImpulsivePsychological,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isExistingText) "Saved text note" else "Draft",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "${textDraft.length} / $MaxTextMessageChars",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = onSave,
                enabled = textDraft.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
            ) {
                Text(if (isExistingText) "Update text note" else "Save text note")
            }
        }
    }
}

@Composable
private fun DeleteCard(
    kind: FutureSelfMessageKind?,
    onDelete: () -> Unit,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Saved ${if (kind == FutureSelfMessageKind.Voice) "voice note" else "text note"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Stored on this device only.",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete saved message",
                    tint = ImpulsivePsychological,
                )
            }
        }
    }
}

@Composable
private fun PermissionFallbackNote() {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.14f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Microphone access is off. You can still type a note below.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        text = "Your note stays on this device. It is never uploaded.",
        color = ImpulsiveMutedText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

private fun polarPoint(center: Float, angleDegrees: Float, radius: Float): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble() - 90.0)
    return Offset(
        x = center + kotlin.math.cos(radians).toFloat() * radius,
        y = center + kotlin.math.sin(radians).toFloat() * radius,
    )
}

internal fun onboardingWhySeed(weekOneGoal: String?, name: String): String {
    val goalLine = when (weekOneGoal) {
        "notice_triggers" -> "I want to start noticing my cues."
        "cut_down_a_little" -> "I want to cut down a little this week."
        "daily_reset_habit" -> "I want to build one daily reset habit."
        "cut_down_by_half" -> "I want to cut down by half this week."
        else -> "I want to take small steps toward the version of me I'm choosing."
    }
    val who = name.takeIf { it.isNotBlank() } ?: "me"
    return "Hey $who - $goalLine"
}
