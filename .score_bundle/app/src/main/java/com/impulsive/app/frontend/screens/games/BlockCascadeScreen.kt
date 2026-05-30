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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.game.BlockCascadeUiState
import com.impulsive.app.backend.session.game.BlockCascadeView
import com.impulsive.app.backend.session.game.BlockCascadeViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

@Composable
fun BlockCascadeScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: BlockCascadeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val onboardingState by onboardingViewModel.state.collectAsState()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsState()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var rewardLogged by remember { mutableStateOf(false) }
    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
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
        if (!uiState.completed && uiState.view != BlockCascadeView.Ready) {
            logCompletion(validCompletion = false)
        }
        if (uiState.view == BlockCascadeView.Result) {
            viewModel.recordScore(
                outcome = if (uiState.completed) {
                    ScoreSessionOutcome.WalkedAway
                } else {
                    ScoreSessionOutcome.Abandoned
                },
            )
        }
        if (uiState.completed) {
            taskRewardViewModel.clearLastCompletionResult()
        }
        onExit()
    }

    BackHandler {
        exitSafely()
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
            IconButton(onClick = ::exitSafely) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ImpulsiveText,
                )
            }
            Text(
                text = "Block Cascade",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (uiState.view) {
            BlockCascadeView.Ready -> ReadyPanel(onStart = viewModel::start)
            BlockCascadeView.Playing -> PlayingPanel(
                uiState = uiState,
                onMoveLeft = viewModel::moveLeft,
                onMoveRight = viewModel::moveRight,
                onRotate = viewModel::rotate,
                onSoftDrop = viewModel::softDrop,
            )
            BlockCascadeView.Paused -> PausedPanel(onResume = viewModel::resume, onExit = ::exitSafely)
            BlockCascadeView.Result -> ResultPanel(
                uiState = uiState,
                taskCompletionResult = taskCompletionResult,
                taskLaunch = taskLaunch,
                onDone = ::exitSafely,
                onPlayAgain = {
                    viewModel.recordScore(ScoreSessionOutcome.Replayed)
                    taskRewardViewModel.clearLastCompletionResult()
                    rewardLogged = false
                    viewModel.start()
                },
            )
        }
    }
}

@Composable
private fun ReadyPanel(onStart: () -> Unit) {
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
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onStart,
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
            .fillMaxWidth()
            .aspectRatio(BlockCascadeColumns / BlockCascadeRows.toFloat())
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
        ImpulsivePsychological,
        ImpulsivePsychological.copy(alpha = 0.72f),
        Color(0xFFE8E2F8),
        ImpulsivePhysical.copy(alpha = 0.72f),
        Color(0xFFEAF5FE),
        ImpulsiveSpiritual.copy(alpha = 0.74f),
        Color(0xFFFFF7C6),
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
    taskCompletionResult: TaskCompletionResult?,
    taskLaunch: Boolean,
    onDone: () -> Unit,
    onPlayAgain: () -> Unit,
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
            Text(if (uiState.completed) "Play again" else "Reset round")
        }
    }
}

private fun resultLabel(uiState: BlockCascadeUiState, result: TaskCompletionResult?, taskLaunch: Boolean): String {
    if (!uiState.completed) {
        return uiState.failureReason ?: "This round did not qualify for a reward."
    }
    if (!taskLaunch) return "Recovery game complete. Use Task to Complete when you want wait reduction."
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
