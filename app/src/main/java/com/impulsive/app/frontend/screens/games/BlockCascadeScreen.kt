package com.impulsive.app.frontend.screens.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import kotlin.random.Random
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.backend.domain.game.BlockCascadeColumns
import com.impulsive.app.backend.domain.game.BlockCascadeGameState
import com.impulsive.app.backend.domain.game.BlockCascadeMinimumLines
import com.impulsive.app.backend.domain.game.BlockCascadeMinimumMoves
import com.impulsive.app.backend.domain.game.BlockCascadeRows
import com.impulsive.app.backend.domain.game.BlockCascadeRoundSeconds
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.session.game.BlockCascadeUiState
import com.impulsive.app.backend.session.game.BlockCascadeView
import com.impulsive.app.backend.session.game.BlockCascadeViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.GameSoundToggle
import com.impulsive.app.frontend.components.UrgeRatingRow
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import com.impulsive.app.frontend.utils.rememberImpulsiveSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

@Composable
fun BlockCascadeScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAnother: () -> Unit = {},
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: BlockCascadeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    LockPortraitOrientation()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val sounds = rememberImpulsiveSounds(appSettingsState.soundEffectsEnabled)
    LaunchedEffect(uiState.linesCleared) {
        if (uiState.linesCleared > 0) {
            sounds.cascadeClear()
        }
    }
    LaunchedEffect(uiState.view, uiState.failed) {
        if (uiState.view == BlockCascadeView.Result && uiState.failed) {
            sounds.cascadeOver()
        }
    }
    LaunchedEffect(uiState.view, appSettingsState.soundEffectsEnabled) {
        if (uiState.view == BlockCascadeView.Playing && appSettingsState.soundEffectsEnabled) {
            sounds.startCascadeMusic()
        } else {
            sounds.stopCascadeMusic()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var rewardLogged by remember { mutableStateOf(false) }
    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
    // A block-launched round that ended without completing has no allowed exit:
    // the only way on is to finish a full round. Hub rounds keep their exit.
    val mustReplay = uiState.view == BlockCascadeView.Result && !uiState.completed
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

    fun launchedFrom(): String = if (launchSource == ReflexGameLaunchSource.RECOVERY_GAME) {
        "RECOVERY_GAME"
    } else {
        "TASK_TO_COMPLETE"
    }

    fun logCompletion(validCompletion: Boolean) {
        if (!taskLaunch || rewardLogged) return
        rewardLogged = true
        taskRewardViewModel.completeTask(
            taskType = PsychologyTaskType.BlockCascade,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = launchedFrom(),
            gameType = PsychologyTaskType.BlockCascade.id.uppercase(),
            score = uiState.linesCleared,
            durationSec = uiState.secondsPlayed,
            validCompletion = validCompletion,
        )
    }

    fun exitSafely() {
        if (uiState.view == BlockCascadeView.Result) {
            viewModel.recordCurrentResult(
                outcome = if (uiState.completed) {
                    ScoreSessionOutcome.Completed
                } else {
                    ScoreSessionOutcome.Abandoned
                },
            )
        }
        if (!uiState.completed && uiState.view != BlockCascadeView.Ready) {
            logCompletion(validCompletion = false)
        }
        if (uiState.completed) {
            taskRewardViewModel.clearLastCompletionResult()
        }
        onExit()
    }

    BackHandler {
        if (!(mustReplay && taskLaunch)) {
            exitSafely()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.view, uiState.completed, uiState.failed) {
        if (uiState.view == BlockCascadeView.Result) {
            logCompletion(validCompletion = uiState.completed)
        }
    }

    LaunchedEffect(uiState.view) {
        while (isActive && uiState.view == BlockCascadeView.Playing) {
            withFrameMillis { }
            viewModel.tick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImpulsiveBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!(mustReplay && taskLaunch)) {
                IconButton(onClick = ::exitSafely) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ImpulsiveText,
                    )
                }
            }
            Text(
                text = "Block Cascade",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            GameSoundToggle(
                enabled = appSettingsState.soundEffectsEnabled,
                tint = ImpulsiveText,
                onToggle = appSettingsViewModel::setSoundEffectsEnabled,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (uiState.view) {
            BlockCascadeView.Ready -> ReadyPanel(
                onStart = viewModel::start,
                onUrgeBeforeSelected = viewModel::setUrgeBefore,
            )
            BlockCascadeView.Playing -> PlayingPanel(
                uiState = uiState,
                onMoveLeft = viewModel::moveLeft,
                onMoveRight = viewModel::moveRight,
                onRotate = viewModel::rotate,
                onSoftDrop = {
                    sounds.cascadePlace()
                    viewModel.softDrop()
                },
            )
            BlockCascadeView.Paused -> PausedPanel(onResume = viewModel::resume, onExit = ::exitSafely)
            BlockCascadeView.Result -> ResultPanel(
                uiState = uiState,
                onUrgeAfterSelected = viewModel::setUrgeAfter,
                taskCompletionResult = taskCompletionResult,
                taskLaunch = taskLaunch,
                onDone = ::exitSafely,
                onPlayAgain = {
                    viewModel.recordCurrentResult(ScoreSessionOutcome.Replayed)
                    taskRewardViewModel.clearLastCompletionResult()
                    rewardLogged = false
                    viewModel.start()
                },
                onPlayAnother = onPlayAnother,
            )
        }
    }
}

@Composable
private fun ReadyPanel(
    onStart: () -> Unit,
    onUrgeBeforeSelected: (Int) -> Unit,
) {
    CenterPanel {
        Text(
            text = "A visual focus game to occupy the image loop.",
            color = ImpulsiveText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Play one 90-second round. Move, rotate, and clear lines at a calm pace.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        val showUrgeGate = remember { Random.nextInt(10) < 4 }
        var selectedUrgeBefore by remember { mutableStateOf<Int?>(null) }
        if (showUrgeGate) {
            UrgeRatingRow(
                label = "How strong is the urge right now?",
                selected = selectedUrgeBefore,
                onSelect = { value ->
                    selectedUrgeBefore = value
                    onUrgeBeforeSelected(value)
                },
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onStart,
            enabled = !showUrgeGate || selectedUrgeBefore != null,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ImpulsiveText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start")
        }
    }
}

@Composable
private fun PlayingPanel(
    uiState: BlockCascadeUiState,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressHud(uiState = uiState)
        BlockCascadeBoardCanvas(
            gameState = uiState.gameState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width / 2f) {
                            onMoveLeft()
                        } else {
                            onMoveRight()
                        }
                    }
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlButton(
                text = "Rotate",
                icon = Icons.AutoMirrored.Filled.RotateRight,
                onClick = onRotate,
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                text = "Drop",
                icon = Icons.Filled.KeyboardArrowDown,
                onClick = onSoftDrop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgressHud(uiState: BlockCascadeUiState) {
    val progress = (uiState.secondsPlayed / BlockCascadeRoundSeconds.toFloat()).coerceIn(0f, 1f)
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HudStat("Time", "${uiState.secondsPlayed}s")
                HudStat("Lines", uiState.linesCleared.toString())
                HudStat("Moves", uiState.validMoves.toString())
            }
            LinearProgressIndicator(
                progress = { progress },
                color = ImpulsivePsychological,
                trackColor = ImpulsivePsychological.copy(alpha = 0.18f),
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Text(
                text = "Goal: ${BlockCascadeRoundSeconds}s. Reward needs ${BlockCascadeMinimumLines} lines or ${BlockCascadeMinimumMoves} valid moves.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BlockCascadeBoardCanvas(
    gameState: BlockCascadeGameState?,
    modifier: Modifier = Modifier,
) {
    val palette = blockPalette()
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val boardModifier = Modifier
            .fillMaxHeight()
            .aspectRatio(BlockCascadeColumns / BlockCascadeRows.toFloat(), matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFFFFFCFF))
            .border(1.dp, ImpulsiveText.copy(alpha = 0.08f), RoundedCornerShape(26.dp))

        Canvas(modifier = boardModifier) {
            val cell = size.width / BlockCascadeColumns
            val verticalPad = (size.height - cell * BlockCascadeRows) / 2f
            val corner = CornerRadius(cell * 0.24f, cell * 0.24f)
            for (x in 0 until BlockCascadeColumns) {
                for (y in 0 until BlockCascadeRows) {
                    drawRoundRect(
                        color = palette.gridLine,
                        topLeft = Offset(x * cell + cell * 0.07f, verticalPad + y * cell + cell * 0.07f),
                        size = Size(cell * 0.86f, cell * 0.86f),
                        cornerRadius = corner,
                    )
                    val value = gameState?.board?.valueAt(x, y)
                    if (value != null) {
                        drawBlock(
                            color = palette.blocks[value % palette.blocks.size],
                            x = x,
                            y = y,
                            cell = cell,
                            verticalPad = verticalPad,
                            corner = corner,
                        )
                    }
                }
            }
            gameState?.activePiece?.cells?.forEach { cellPosition ->
                if (cellPosition.y in 0 until BlockCascadeRows) {
                    drawBlock(
                        color = palette.blocks[gameState.activePiece.kind.paletteIndex % palette.blocks.size],
                        x = cellPosition.x,
                        y = cellPosition.y,
                        cell = cell,
                        verticalPad = verticalPad,
                        corner = corner,
                    )
                }
            }
            gameState?.lastClearedRows?.forEach { row ->
                drawRoundRect(
                    color = ImpulsivePsychological.copy(alpha = 0.26f),
                    topLeft = Offset(0f, verticalPad + row * cell),
                    size = Size(size.width, cell),
                    cornerRadius = CornerRadius(cell * 0.2f, cell * 0.2f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlock(
    color: Color,
    x: Int,
    y: Int,
    cell: Float,
    verticalPad: Float,
    corner: CornerRadius,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * cell + cell * 0.08f, verticalPad + y * cell + cell * 0.08f),
        size = Size(cell * 0.84f, cell * 0.84f),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(x * cell + cell * 0.18f, verticalPad + y * cell + cell * 0.16f),
        size = Size(cell * 0.42f, cell * 0.16f),
        cornerRadius = corner,
    )
}

@Composable
private fun blockPalette(): BlockPalette = BlockPalette(
    gridLine = Color(0xFFF2EEF7),
    blocks = listOf(
        Color(0xFF7C3AED), // vivid violet
        Color(0xFF2563EB), // strong blue
        Color(0xFF06B6D4), // cyan
        Color(0xFF10B981), // emerald
        Color(0xFFF59E0B), // amber
        Color(0xFFEF4444), // red
        Color(0xFFEC4899), // magenta/pink
    ),
)

private data class BlockPalette(
    val gridLine: Color,
    val blocks: List<Color>,
)

@Composable
private fun ControlButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .height(58.dp)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ImpulsiveText.copy(alpha = 0.82f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = text,
                color = ImpulsiveText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PausedPanel(
    onResume: () -> Unit,
    onExit: () -> Unit,
) {
    CenterPanel {
        Text(
            text = "Paused",
            color = ImpulsiveText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The timer only counts while this screen is open.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onResume,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = Color(0xFF281D38),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Resume")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsiveSurface,
                contentColor = ImpulsiveText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Exit")
        }
    }
}

@Composable
private fun ResultPanel(
    uiState: BlockCascadeUiState,
    onUrgeAfterSelected: (Int) -> Unit,
    taskCompletionResult: TaskCompletionResult?,
    taskLaunch: Boolean,
    onDone: () -> Unit,
    onPlayAgain: () -> Unit,
    onPlayAnother: () -> Unit,
) {
    CenterPanel {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(ImpulsivePsychological.copy(alpha = 0.46f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.linesCleared.toString(),
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (uiState.completed) "Block Cascade complete" else "Round ended",
            color = ImpulsiveText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
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
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = resultLabel(uiState, taskCompletionResult, taskLaunch),
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatRow("Time", "${uiState.secondsPlayed}s")
        StatRow("Lines cleared", uiState.linesCleared.toString())
        StatRow("Valid moves", uiState.validMoves.toString())
        Spacer(modifier = Modifier.height(22.dp))
        if (!uiState.completed) {
            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play same game")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onPlayAnother,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsiveSurface,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play another")
            }
        } else {
            Button(
                onClick = onDone,
                enabled = !taskLaunch || taskCompletionResult != null,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (taskLaunch && taskCompletionResult == null) "Saving" else "Done")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsiveSurface,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play again")
            }
        }
    }
}

private fun resultLabel(uiState: BlockCascadeUiState, result: TaskCompletionResult?, taskLaunch: Boolean): String {
    if (!uiState.completed) {
        return uiState.failureReason ?: "This round did not qualify for a reward."
    }
    if (!taskLaunch) return "Pivot game complete. Open it from Tasks when you want wait reduction."
    if (result == null) return "Saving reward..."
    val wait = if (result.waitReductionMinutes > 0) {
        "Wait cut by ${result.waitReductionMinutes.formatMinutes()}"
    } else {
        "LP only this time"
    }
    return "$wait • +${result.levelPointsAwarded} LP"
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = ImpulsiveText,
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
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = ImpulsiveText,
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
            color = ImpulsiveSurface,
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

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
