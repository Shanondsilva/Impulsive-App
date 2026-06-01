package com.impulsive.app.frontend.screens.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessageKind
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.FutureSelfFinalChoice
import com.impulsive.app.backend.session.tasks.FutureSelfMessageViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.FutureSelfChoiceChip
import com.impulsive.app.frontend.components.FutureSelfHeroPanel
import com.impulsive.app.frontend.components.FutureSelfPlaybackRing
import com.impulsive.app.frontend.components.FutureSelfQuoteCard
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

@Composable
fun FutureSelfMessageScreen(
    onExit: () -> Unit,
    onRecordYours: () -> Unit,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: FutureSelfMessageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var rewardLogged by remember { mutableStateOf(false) }

    val fallbackText = remember(onboardingState.answers.weekOneGoal, onboardingState.answers.name) {
        onboardingWhySeed(onboardingState.answers.weekOneGoal, onboardingState.answers.name)
    }

    LaunchedEffect(fallbackText) {
        viewModel.initPlayback(fallbackText)
    }

    val currentNow by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L)
        }
    }
    val baseReleasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = onboardingState.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(onboardingState.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(onboardingState.answers.activeDayEndMinute),
    )
    val taskRewardState = taskRewardStoreState.toTaskRewardState(baseReleasePlan)
    val releasePlan = calculateRewardedReleasePlan(
        releasePlan = baseReleasePlan,
        adjustedNextReleaseWindow = taskRewardState.adjustedNextReleaseWindow,
        now = currentNow,
    )

    fun logReward(validCompletion: Boolean) {
        if (rewardLogged) return
        rewardLogged = true
        taskRewardViewModel.completeTask(
            taskType = PsychologyTaskType.FutureSelfMessage,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = "TASK_TO_COMPLETE",
            gameType = PsychologyTaskType.FutureSelfMessage.id.uppercase(),
            durationSec = (playback.dwellMillis / 1000L).toInt(),
            validCompletion = validCompletion,
        )
    }

    fun exitSafely() {
        if (playback.validCompletion) {
            taskRewardViewModel.clearLastCompletionResult()
        } else if (playback.dwellMillis > 0L || playback.playbackPositionMillis > 0L) {
            logReward(validCompletion = false)
        }
        onExit()
    }

    BackHandler { exitSafely() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumePlayback()
                Lifecycle.Event.ON_STOP -> viewModel.pausePlayback()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playback.showSuccess) {
        while (isActive && !playback.showSuccess) {
            withFrameMillis { }
            viewModel.tickPlayback()
        }
    }

    LaunchedEffect(playback.validCompletion) {
        if (playback.validCompletion) {
            logReward(validCompletion = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onExit = ::exitSafely)

        if (playback.showSuccess) {
            SuccessPanel(
                taskCompletionResult = taskCompletionResult,
                onDone = ::exitSafely,
            )
        } else {
            FutureSelfHeroPanel(
                title = "A message you left yourself.",
                subtitle = "Future-self message",
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    playback.message?.kind == FutureSelfMessageKind.Voice -> {
                        VoicePlaybackHero(
                            isPlaying = playback.isPlaying,
                            completedFraction = playback.playbackCompletedFraction,
                            positionMillis = playback.playbackPositionMillis,
                            durationMillis = playback.playbackDurationMillis,
                            canChoose = playback.canChoose,
                            onTogglePlay = viewModel::togglePlayback,
                        )
                    }
                    playback.message?.kind == FutureSelfMessageKind.Text -> {
                        FutureSelfQuoteCard(
                            text = playback.message?.text.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        FutureSelfQuoteCard(
                            text = playback.fallbackMessage ?: fallbackText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onRecordYours,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
                        ) {
                            Text("Record yours later")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = playback.canChoose,
                enter = fadeIn(animationSpec = tween(250)) + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(targetOffsetY = { it / 3 }),
            ) {
                ChoiceSection(
                    enabled = playback.canChoose,
                    onChoice = viewModel::chooseFinal,
                )
            }

            if (!playback.canChoose) {
                Surface(
                    color = ImpulsiveSurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (playback.message?.kind == FutureSelfMessageKind.Voice) {
                            "Listen through the full message to unlock the next step."
                        } else {
                            "Read the message for a moment to unlock the next step."
                        },
                        color = ImpulsiveMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
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
        Text(
            text = "Future-Self Message",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VoicePlaybackHero(
    isPlaying: Boolean,
    completedFraction: Float,
    positionMillis: Long,
    durationMillis: Long,
    canChoose: Boolean,
    onTogglePlay: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(178.dp),
            contentAlignment = Alignment.Center,
        ) {
            FutureSelfPlaybackRing(
                progress = completedFraction.coerceIn(0f, 1f),
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(92.dp)
                    .background(ImpulsivePsychological, androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else if (completedFraction >= 1f) "Replay" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            text = "${(positionMillis / 1000L).toInt()}s / ${(durationMillis / 1000L).toInt().coerceAtLeast(1)}s",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onTogglePlay,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
            ) {
                Text(if (isPlaying) "Pause" else if (completedFraction >= 1f) "Replay" else "Play")
            }
            OutlinedButton(
                onClick = onTogglePlay,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
            ) {
                Text(if (canChoose) "Ready" else "Listen")
            }
        }
    }
}

@Composable
private fun ChoiceSection(
    enabled: Boolean,
    onChoice: (FutureSelfFinalChoice) -> Unit,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "What's next?",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            FutureSelfChoiceChip(
                label = "I'll wait",
                letter = "A",
                enabled = enabled,
                onClick = { onChoice(FutureSelfFinalChoice.WillWait) },
                modifier = Modifier.fillMaxWidth(),
            )
            FutureSelfChoiceChip(
                label = "I'll do another task",
                letter = "B",
                enabled = enabled,
                onClick = { onChoice(FutureSelfFinalChoice.AnotherTask) },
                modifier = Modifier.fillMaxWidth(),
            )
            FutureSelfChoiceChip(
                label = "I'm okay now",
                letter = "C",
                enabled = enabled,
                onClick = { onChoice(FutureSelfFinalChoice.ImOkayNow) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SuccessPanel(
    taskCompletionResult: TaskCompletionResult?,
    onDone: () -> Unit,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                tint = ImpulsiveText,
                modifier = Modifier
                    .size(58.dp)
                    .background(ImpulsivePsychological.copy(alpha = 0.44f), androidx.compose.foundation.shape.CircleShape)
                    .padding(14.dp),
            )
            Text(
                text = "Noted",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "You heard yourself out. The next minute is yours.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = resultLabel(taskCompletionResult),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onDone,
                enabled = taskCompletionResult != null,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (taskCompletionResult == null) "Saving" else "Done")
            }
        }
    }
}

private fun resultLabel(result: TaskCompletionResult?): String {
    if (result == null) return "Saving reward..."
    val wait = if (result.waitReductionMinutes > 0) {
        "Wait cut by ${formatMinutes(result.waitReductionMinutes)}"
    } else {
        "Window already protected"
    }
    return "$wait  •  +${result.levelPointsAwarded} LP"
}

private fun formatMinutes(value: Int): String =
    if (value >= 60 && value % 60 == 0) "${value / 60}h" else "$value min"
