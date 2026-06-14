package com.impulsive.app.frontend.screens.games

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.backend.domain.game.GameResult
import com.impulsive.app.backend.domain.game.TargetType
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.frontend.utils.ImpulsiveSounds
import com.impulsive.app.frontend.utils.rememberImpulsiveSounds
import com.impulsive.app.backend.domain.game.GameView
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.game.ReflexGameConfig
import com.impulsive.app.backend.domain.game.Target
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.formattedTimeUntilNextWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.game.ReflexGameUiState
import com.impulsive.app.backend.session.game.ReflexGameViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.GameSoundToggle
import com.impulsive.app.frontend.components.UrgeRatingRow
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

private val ReflexPrimaryButtonText = Color(0xFF281D38)

@Composable
fun ReflexGameScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.RECOVERY_GAME,
    viewModel: ReflexGameViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    LockPortraitOrientation()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val sounds = rememberImpulsiveSounds(appSettingsState.soundEffectsEnabled)
    LaunchedEffect(uiState.result) {
        val soundResult = uiState.result
        if (soundResult != null && soundResult.validCompletion) {
            sounds.reflexSuccess()
        }
    }
    val currentNow by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000L)
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
    var rewardedResultScore by remember(launchSource) { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.result, launchSource) {
        val result = uiState.result
        if (
            launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE &&
            result != null &&
            result.validCompletion &&
            rewardedResultScore != result.score
        ) {
            rewardedResultScore = result.score
            taskRewardViewModel.completeTask(
                taskType = PsychologyTaskType.ReflexOverride,
                releasePlan = releasePlan,
                now = LocalDateTime.now(),
                launchedFrom = "TASK_TO_COMPLETE",
                gameType = "REFLEX_OVERRIDE",
                score = result.score,
                durationSec = result.durationSec,
                validCompletion = result.validCompletion,
            )
        }
    }

    LaunchedEffect(uiState.view) {
        if (uiState.view == GameView.Playing) {
            while (isActive) {
                withFrameMillis { }
                viewModel.tick()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (uiState.view == GameView.Result) {
                        viewModel.recordCurrentResult(
                            outcome = if (launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE) {
                                if (uiState.result?.validCompletion == true) {
                                    ScoreSessionOutcome.Completed
                                } else {
                                    ScoreSessionOutcome.Abandoned
                                }
                            } else {
                                ScoreSessionOutcome.ContinuedWithIntention
                            },
                        )
                    }
                    onExit()
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Reflex Override",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            GameSoundToggle(
                enabled = appSettingsState.soundEffectsEnabled,
                tint = MaterialTheme.colorScheme.onBackground,
                onToggle = appSettingsViewModel::setSoundEffectsEnabled,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (uiState.view) {
            GameView.Ready -> ReadyView(
                uiState = uiState,
                onStart = viewModel::startCountdown,
                onUrgeBeforeSelected = viewModel::setUrgeBefore,
            )
            GameView.Countdown -> CountdownView(countdown = uiState.countdown)
            GameView.Playing -> PlayingView(uiState = uiState, viewModel = viewModel, sounds = sounds)
            GameView.Result -> ResultView(
                result = uiState.result,
                onUrgeAfterSelected = viewModel::setUrgeAfter,
                launchSource = launchSource,
                taskCompletionResult = taskCompletionResult,
                nextWindowText = releasePlan.formattedTimeUntilNextWindow(),
                onWalkAway = viewModel::walkAway,
                onPlayAgain = {
                    viewModel.recordCurrentResult(ScoreSessionOutcome.Replayed)
                    viewModel.startCountdown()
                },
                onExit = {
                    viewModel.recordCurrentResult(
                        outcome = if (launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE) {
                            if (uiState.result?.validCompletion == true) {
                                ScoreSessionOutcome.Completed
                            } else {
                                ScoreSessionOutcome.Abandoned
                            }
                        } else {
                            ScoreSessionOutcome.ContinuedWithIntention
                        },
                    )
                    onExit()
                },
            )
            GameView.Walked -> WalkedView(score = uiState.walkScore, onExit = onExit)
        }
    }
}

@Composable
private fun ReadyView(
    uiState: ReflexGameUiState,
    onStart: () -> Unit,
    onUrgeBeforeSelected: (Int) -> Unit,
) {
    CenterPanel {
        Text(
            text = "Reflex Override",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Pivot out of autopilot with a short reaction round.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (uiState.history.pb > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Best: ${uiState.history.pb}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        var selectedUrgeBefore by remember { mutableStateOf<Int?>(null) }
        UrgeRatingRow(
            label = "How strong is the urge right now?",
            selected = selectedUrgeBefore,
            onSelect = { value ->
                selectedUrgeBefore = value
                onUrgeBeforeSelected(value)
            },
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ReflexPrimaryButtonText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start 90-second challenge")
        }
    }
}

@Composable
private fun CountdownView(countdown: Int) {
    val scale by animateFloatAsState(
        targetValue = if (countdown == 0) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "countdownScale",
    )
    CenterPanel {
        Text(
            text = "Get ready",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (countdown == 0) "Go" else countdown.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 72.sp,
            lineHeight = 78.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
private fun PlayingView(uiState: ReflexGameUiState, viewModel: ReflexGameViewModel, sounds: ImpulsiveSounds) {
    Column(modifier = Modifier.fillMaxSize()) {
        GameHud(uiState = uiState)
        Text(
            text = "Tap the bright bubbles. Avoid the marked ones.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF241B3A))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        sounds.reflexMiss()
                        viewModel.tapArena(
                            xFraction = offset.x / size.width.toFloat(),
                            yFraction = offset.y / size.height.toFloat(),
                        )
                    }
                },
        ) {
            val widthDp = maxWidth
            val heightDp = maxHeight
            LaunchedEffect(widthDp, heightDp) {
                viewModel.setArenaSize(widthDp.value.toInt(), heightDp.value.toInt())
            }

            val shakeOffset by animateDpAsState(
                targetValue = if (uiState.shake) 6.dp else 0.dp,
                animationSpec = tween(80),
                label = "shake",
            )

            uiState.targets.forEach { target ->
                var visible by remember(target.id) { mutableStateOf(false) }
                LaunchedEffect(target.id) {
                    visible = true
                }
                val popScale by animateFloatAsState(
                    targetValue = if (visible) 1f else 0.85f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    label = "targetScale",
                )
                val popAlpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0.6f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    label = "targetAlpha",
                )
                val color = target.colorHex?.let { Color(it) }
                Box(
                    modifier = Modifier
                        .offset(
                            x = (target.xFraction * (widthDp.value - target.sizeDp)).dp + shakeOffset,
                            y = (target.yFraction * (heightDp.value - target.sizeDp)).dp,
                        )
                        .size(target.sizeDp.dp)
                        .scale(popScale)
                        .clip(CircleShape)
                        .background(
                            brush = if (color == null) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF3A263E).copy(alpha = popAlpha),
                                        Color(0xFF1A1320).copy(alpha = popAlpha),
                                    ),
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.30f * popAlpha),
                                        color.copy(alpha = popAlpha),
                                        color.copy(alpha = 0.74f * popAlpha),
                                    ),
                                )
                            },
                        )
                        .then(
                            if (color == null) {
                                Modifier.border(2.dp, Color(0xFFD989A1).copy(alpha = popAlpha), CircleShape)
                            } else {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.24f * popAlpha), CircleShape)
                            },
                        )
                        .pointerInput(target.id) {
                            detectTapGestures(onTap = {
                                if (target.type == TargetType.Hit) {
                                    sounds.reflexCorrect()
                                } else {
                                    sounds.reflexMiss()
                                }
                                viewModel.tapTarget(target.id)
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == null) {
                        Text(
                            text = "X",
                            color = Color(0xFFE5A1B3).copy(alpha = popAlpha),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            uiState.flashes.forEach { flash ->
                Text(
                    text = flash.text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(
                        x = (flash.xFraction * widthDp.value).dp,
                        y = (flash.yFraction * heightDp.value).dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResultView(
    result: GameResult?,
    onUrgeAfterSelected: (Int) -> Unit,
    launchSource: ReflexGameLaunchSource,
    taskCompletionResult: TaskCompletionResult?,
    nextWindowText: String,
    onWalkAway: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    if (result == null) return
    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
    CenterPanel {
        Text(
            text = if (taskLaunch && result.validCompletion) {
                "Reflex Override complete"
            } else if (result.gameOver) {
                "Round complete"
            } else {
                "Reflex Override complete"
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        var selectedUrgeAfter by remember { mutableStateOf<Int?>(null) }
        UrgeRatingRow(
            label = "How strong is it now?",
            selected = selectedUrgeAfter,
            onSelect = { value ->
                selectedUrgeAfter = value
                onUrgeAfterSelected(value)
            },
        )
        if (result.score > result.previousBest && result.score > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "New best!",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = result.score.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 54.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatRow("Best reaction", result.bestReactionMs?.let { "${it}ms" } ?: "-")
        StatRow("Max combo", result.maxCombo.toString())
        StatRow("Hits", result.hits.toString())
        StatRow("Misses", result.misses.toString())
        StatRow("Duration", "${result.durationSec}s")
        StatRow("Difficulty reached", result.difficulty.toString())
        if (taskLaunch && taskCompletionResult != null) {
            StatRow(
                "Wait reduced",
                if (taskCompletionResult.waitReductionMinutes > 0) {
                    taskCompletionResult.waitReductionMinutes.formatMinutes()
                } else {
                    "-"
                },
            )
            StatRow("Level Points earned", "+${taskCompletionResult.levelPointsAwarded} LP")
            StatRow("Next window", nextWindowText.removePrefix("Next window in "))
        }
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = if (taskLaunch) onExit else onWalkAway,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ReflexPrimaryButtonText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (taskLaunch) "Return protected" else "Walk away (+${ReflexGameConfig.WALK_AWAY_BONUS})")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Choosing to stop is the strongest move.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (taskLaunch) {
            OutlinedButton(
                onClick = onExit,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View next window")
            }
        } else {
            OutlinedButton(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play again")
            }
            TextButton(onClick = onExit) {
                Text("Back")
            }
        }
    }
}

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"

@Composable
private fun WalkedView(score: Int, onExit: () -> Unit) {
    CenterPanel {
        Text(
            text = "You walked away.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = score.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 54.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "That's the skill that counts: noticing the pull and stepping back.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ReflexPrimaryButtonText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun GameHud(uiState: ReflexGameUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HudStat("Time", "${uiState.timeLeft}s")
            HudStat("Score", uiState.score.toString())
            HudStat("Combo", uiState.combo.toString())
        }
        LinearProgressIndicator(
            progress = { uiState.timeLeft / ReflexGameConfig.ROUND_SECONDS.toFloat() },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            drawStopIndicator = {},
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Focus",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(ReflexGameConfig.MAX_BOMBS) { index ->
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 8.dp)
                            .background(
                                color = if (index < uiState.lives) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                shape = RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CenterPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}
