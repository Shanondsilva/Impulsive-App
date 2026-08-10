package com.impulsive.app.frontend.screens.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlin.random.Random
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.StackBlock
import com.impulsive.app.backend.domain.game.StackBlockHeight
import com.impulsive.app.backend.domain.game.StackDropResult
import com.impulsive.app.backend.domain.game.StackRoundSeconds
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.game.SkylineResetUiState
import com.impulsive.app.backend.session.game.SkylineResetView
import com.impulsive.app.backend.session.game.SkylineResetViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.GameSoundToggle
import com.impulsive.app.frontend.components.UrgeRatingRow
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import com.impulsive.app.frontend.utils.rememberImpulsiveSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val SkyVoidTop = Color(0xFF0A0816)
private val SkyVoidBottom = Color(0xFF140D28)
private val SkyPanel = Color(0xFF181226)
private val SkyPanelStroke = Color(0xFFD9CCFF).copy(alpha = 0.14f)

@Composable
fun SkylineResetScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAnother: () -> Unit = {},
    onAdaptiveCompleted: (() -> Unit)? = null,
    onAdaptiveExit: ((completed: Boolean) -> Unit)? = null,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    gameLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: SkylineResetViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val sounds = rememberImpulsiveSounds(appSettingsState.soundEffectsEnabled)
    LaunchedEffect(gameLaunchContext) {
        if (!viewModel.configureLaunchContext(gameLaunchContext)) onExit()
    }
    LaunchedEffect(uiState.dropSeq) {
        if (uiState.dropSeq > 0) {
            sounds.skySetClick()
        }
    }
    LaunchedEffect(uiState.view, uiState.completed) {
        if (uiState.view == SkylineResetView.Result && uiState.completed) {
            sounds.skyComplete()
        }
    }
    LaunchedEffect(uiState.view, appSettingsState.soundEffectsEnabled) {
        if (uiState.view == SkylineResetView.Playing && appSettingsState.soundEffectsEnabled) {
            sounds.startAmbient()
        } else {
            sounds.stopAmbient()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
    // A block-launched round that ended without completing has no allowed exit:
    // the only way on is to finish a full round. Hub rounds keep their exit.
    val mustReplay = uiState.view == SkylineResetView.Result && !uiState.completed
    var rewardLogged by remember(launchSource) { mutableStateOf(false) }
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

    fun stackScore(): Int = uiState.stackScore()

    fun logTaskResult(validCompletion: Boolean) {
        if (!taskLaunch || rewardLogged) return
        rewardLogged = true
        taskRewardViewModel.completeTask(
            taskType = PsychologyTaskType.SkylineReset,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = "TASK_TO_COMPLETE",
            gameType = "SKYLINE_RESET",
            score = stackScore(),
            durationSec = uiState.secondsPlayed,
            validCompletion = validCompletion,
            completionToken = viewModel.taskRewardCompletionToken(),
        )
    }

    fun exitSafely() {
        if (uiState.view == SkylineResetView.Playing || uiState.view == SkylineResetView.Paused) {
            viewModel.recordCurrentResult(ScoreSessionOutcome.Abandoned)
            logTaskResult(validCompletion = false)
        }
        if (uiState.completed) {
            taskRewardViewModel.clearLastCompletionResult()
        }
        viewModel.finishSupportCycleAfterChoice {
            onAdaptiveExit?.invoke(uiState.completed) ?: onExit()
        }
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
        if (uiState.view == SkylineResetView.Result) {
            logTaskResult(validCompletion = uiState.completed)
        }
    }

    LaunchedEffect(uiState.view) {
        while (isActive && uiState.view == SkylineResetView.Playing) {
            withFrameMillis { }
            viewModel.tick()
        }
    }

    AdaptiveGameContainer(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SkyVoidTop,
                        SkyVoidBottom,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp,
                ),
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
                        tint = Color(0xFFF7F2FF),
                    )
                }
            }
            Text(
                text = "SkyStack",
                color = Color(0xFFF7F2FF),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            GameSoundToggle(
                enabled = appSettingsState.soundEffectsEnabled,
                tint = Color(0xFFF7F2FF),
                onToggle = appSettingsViewModel::setSoundEffectsEnabled,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (uiState.view) {
            SkylineResetView.Ready -> ReadyPanel(
                onStart = {
                    taskRewardViewModel.clearLastCompletionResult()
                    rewardLogged = false
                    viewModel.start()
                },
                onUrgeBeforeSelected = viewModel::setUrgeBefore,
            )
            SkylineResetView.Playing -> PlayingPanel(uiState = uiState, onDrop = viewModel::drop)
            SkylineResetView.Paused -> PausedPanel(onResume = viewModel::resume, onExit = ::exitSafely)
            SkylineResetView.Result -> ResultPanel(
                uiState = uiState,
                onUrgeAfterSelected = viewModel::setUrgeAfter,
                taskCompletionResult = taskCompletionResult,
                taskLaunch = taskLaunch,
                onDone = ::exitSafely,
                onPlayAgain = {
                    viewModel.replayWithRemainingBudget {
                        viewModel.recordCurrentResult(ScoreSessionOutcome.Replayed)
                        taskRewardViewModel.clearLastCompletionResult()
                        rewardLogged = false
                        viewModel.start()
                    }
                },
                onPlayAnother = { viewModel.continueWithAnotherGame(onPlayAnother) },
            )
        }
    }
    }
}

@Composable
private fun ReadyPanel(
    onStart: () -> Unit,
    onUrgeBeforeSelected: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SkyStackGameScene(
            uiState = SkylineResetUiState(
                blocks = listOf(com.impulsive.app.backend.domain.game.newStackBaseBlock()),
            ),
            modifier = Modifier.fillMaxSize(),
            onDrop = {},
        )
        CenterPanel {
            Text(
                text = "Stack a calm tower.",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to place the sliding block. Only the overlap stays.",
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
}

@Composable
private fun PlayingPanel(
    uiState: SkylineResetUiState,
    onDrop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkyStackHud(uiState = uiState)
        SkyStackGameScene(
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onDrop = onDrop,
        )
    }
}

@Composable
private fun SkyStackHud(uiState: SkylineResetUiState) {
    Surface(
        color = SkyPanel.copy(alpha = 0.64f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyPanelStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudStat("Time", "${(StackRoundSeconds - uiState.secondsPlayed).coerceAtLeast(0)}s")
            HudStat("Blocks", uiState.floorsBuilt.toString())
            HudStat("Perfect", uiState.perfectCount.toString())
            HudStat("Points", uiState.stackScore().toString())
        }
    }
}

@Composable
internal fun SkyStackGameScene(
    uiState: SkylineResetUiState,
    modifier: Modifier = Modifier,
    onDrop: () -> Unit,
) {
    val targetCameraY = uiState.activeIndex * StackBlockHeight
    val cameraY by animateFloatAsState(
        targetValue = targetCameraY,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "skyStackCameraY",
    )
    val choppedProgress = remember { Animatable(1f) }
    val perfectPulse = remember { Animatable(0f) }

    LaunchedEffect(uiState.dropSeq, uiState.choppedPresent) {
        if (uiState.choppedPresent) {
            choppedProgress.snapTo(0f)
            choppedProgress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        } else {
            choppedProgress.snapTo(1f)
        }
    }

    LaunchedEffect(uiState.dropSeq, uiState.lastDropResult) {
        if (uiState.lastDropResult == StackDropResult.Perfect) {
            perfectPulse.snapTo(0f)
            perfectPulse.animateTo(1f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                // The scene has never shown a ripple.
                indication = null,
                enabled = uiState.view == SkylineResetView.Playing,
                onClickLabel = "Drop block",
                role = Role.Button,
                onClick = onDrop,
            )
            .semantics {
                contentDescription = "Skyline game board"
            },
    ) {
        drawSkyBackground()
        val tile = min(size.width / 5.4f, size.height / 6.2f).coerceAtLeast(42f)
        val center = Offset(size.width / 2f, size.height * 0.64f)
        val lowerBlocks = uiState.blocks.sortedBy { it.index }
        lowerBlocks.forEach { block ->
            drawSkyStackBlock(
                block = block,
                center = center,
                tile = tile,
                cameraY = cameraY,
                active = false,
            )
        }

        if (uiState.choppedPresent && choppedProgress.value < 1f) {
            val drift = uiState.choppedDir * choppedProgress.value * 0.95f
            val fall = choppedProgress.value * choppedProgress.value * 3.4f
            val choppedBlock = StackBlock(
                index = uiState.activeIndex - 1,
                x = uiState.choppedX + if (uiState.choppedAxisIsX) drift else 0f,
                z = uiState.choppedZ + if (uiState.choppedAxisIsX) 0f else drift,
                width = uiState.choppedWidth,
                depth = uiState.choppedDepth,
                hue = uiState.choppedHue,
            )
            drawSkyStackBlock(
                block = choppedBlock,
                center = center,
                tile = tile,
                cameraY = cameraY + fall,
                active = true,
                alpha = (1f - choppedProgress.value * 0.9f).coerceIn(0f, 1f),
                yOverride = uiState.choppedY - fall,
            )
        }

        if (uiState.view == SkylineResetView.Playing) {
            val activeBlock = StackBlock(
                index = uiState.activeIndex,
                x = uiState.activeX,
                z = uiState.activeZ,
                width = uiState.activeWidth,
                depth = uiState.activeDepth,
                hue = uiState.activeHue,
            )
            drawSkyStackBlock(
                block = activeBlock,
                center = center,
                tile = tile,
                cameraY = cameraY,
                active = true,
            )
        }

        if (perfectPulse.value > 0f && lowerBlocks.isNotEmpty()) {
            val top = lowerBlocks.last()
            val pulse = perfectPulse.value
            val centerPoint = isoProject(
                worldX = top.x + top.width / 2f,
                worldZ = top.z + top.depth / 2f,
                worldY = (top.index + 1) * StackBlockHeight,
                center = center,
                tile = tile,
                cameraY = cameraY,
            )
            drawCircle(
                color = Color(0xFFE7D9FF).copy(alpha = (1f - pulse) * 0.34f),
                radius = tile * (0.38f + pulse * 0.54f),
                center = centerPoint,
            )
        }
    }
}

private fun DrawScope.drawSkyBackground() {
    drawRect(Brush.verticalGradient(listOf(SkyVoidTop, SkyVoidBottom)))
    val stars = 58
    repeat(stars) { index ->
        val x = ((sin(index * 12.9898) * 43758.5453) % 1.0).toFloat().let { (it + 1f) % 1f }
        val y = ((cos(index * 78.233) * 24634.6345) % 1.0).toFloat().let { (it + 1f) % 1f }
        val alpha = 0.08f + ((index % 7) / 7f) * 0.12f
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = if (index % 9 == 0) 1.7f else 1.05f,
            center = Offset(x * size.width, y * size.height),
        )
    }
}

private fun DrawScope.drawSkyStackBlock(
    block: StackBlock,
    center: Offset,
    tile: Float,
    cameraY: Float,
    active: Boolean,
    alpha: Float = 1f,
    yOverride: Float? = null,
) {
    if (block.width <= 0f || block.depth <= 0f) return
    val y0 = yOverride ?: block.index * StackBlockHeight
    val y1 = y0 + StackBlockHeight
    val x0 = block.x
    val x1 = block.x + block.width
    val z0 = block.z
    val z1 = block.z + block.depth
    val b00 = isoProject(x0, z0, y0, center, tile, cameraY)
    val b10 = isoProject(x1, z0, y0, center, tile, cameraY)
    val b11 = isoProject(x1, z1, y0, center, tile, cameraY)
    val b01 = isoProject(x0, z1, y0, center, tile, cameraY)
    val t00 = isoProject(x0, z0, y1, center, tile, cameraY)
    val t10 = isoProject(x1, z0, y1, center, tile, cameraY)
    val t11 = isoProject(x1, z1, y1, center, tile, cameraY)
    val t01 = isoProject(x0, z1, y1, center, tile, cameraY)
    val colors = blockFaceColors(block.index, active, alpha)

    drawPath(pathOf(t10, b10, b11, t11), colors.right)
    drawPath(pathOf(t01, t11, b11, b01), colors.left)
    drawPath(pathOf(t00, t10, t11, t01), colors.top)
    drawPath(pathOf(t00, t10, t11, t01), Color.White.copy(alpha = 0.08f * alpha))
}

private fun isoProject(
    worldX: Float,
    worldZ: Float,
    worldY: Float,
    center: Offset,
    tile: Float,
    cameraY: Float,
): Offset = Offset(
    x = center.x + (worldX - worldZ) * tile,
    y = center.y + (worldX + worldZ) * tile * 0.48f - (worldY - cameraY) * tile * 0.78f,
)

private data class BlockFaceColors(
    val top: Color,
    val left: Color,
    val right: Color,
)

private fun blockFaceColors(index: Int, active: Boolean, alpha: Float = 1f): BlockFaceColors {
    val lift = if (active) 1.08f else 1f
    val hueDeg = ((index * 8f + 250f) % 360f + 360f) % 360f
    val sat = 0.62f
    fun face(value: Float): Color {
        val v = (value * lift).coerceIn(0f, 1f)
        val c = v * sat
        val hp = hueDeg / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val m = v - c
        val r: Float
        val g: Float
        val b: Float
        when {
            hp < 1f -> { r = c; g = x; b = 0f }
            hp < 2f -> { r = x; g = c; b = 0f }
            hp < 3f -> { r = 0f; g = c; b = x }
            hp < 4f -> { r = 0f; g = x; b = c }
            hp < 5f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }
        return Color(
            red = ((r + m) * 255f).toInt().coerceIn(0, 255),
            green = ((g + m) * 255f).toInt().coerceIn(0, 255),
            blue = ((b + m) * 255f).toInt().coerceIn(0, 255),
            alpha = (alpha * 255f).toInt().coerceIn(0, 255),
        )
    }
    return BlockFaceColors(
        top = face(0.96f),
        left = face(0.78f),
        right = face(0.60f),
    )
}

private fun pathOf(p0: Offset, p1: Offset, p2: Offset, p3: Offset): Path = Path().apply {
    val points = listOf(p0, p1, p2, p3)
    moveTo(p0.x, p0.y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
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
            text = "The tower waits here.",
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
                containerColor = SkyPanel,
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
    uiState: SkylineResetUiState,
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
                .size(62.dp)
                .background(ImpulsivePsychological.copy(alpha = 0.46f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.stackScore().toString(),
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when {
                uiState.completed -> "SkyStack complete"
                uiState.failed -> "Stack missed"
                else -> "Round ended"
            },
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
        StatRow("Score", uiState.stackScore().toString())
        StatRow("Blocks placed", uiState.floorsBuilt.toString())
        StatRow("Perfect drops", uiState.perfectCount.toString())
        StatRow("Time", "${uiState.secondsPlayed}s")
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
            if (!taskLaunch) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                    Text("Play again")
                }
            }
        }
    }
}

private fun resultLabel(
    uiState: SkylineResetUiState,
    result: TaskCompletionResult?,
    taskLaunch: Boolean,
): String {
    if (uiState.failed) return "The block missed the tower. No task reward was applied."
    if (!uiState.completed) return "This round ended before the 90-second completion."
    if (!taskLaunch) return "Recovery game complete."
    if (result == null) return "Saving reward..."
    val wait = if (result.waitReductionMinutes > 0) {
        "Wait cut by ${result.waitReductionMinutes.formatMinutes()}"
    } else {
        "LP only this time"
    }
    return "$wait, +${result.levelPointsAwarded} LP"
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFFE7D9FF).copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = Color(0xFFF7F2FF),
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
            color = ImpulsiveSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SkyPanelStroke),
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

private fun SkylineResetUiState.stackScore(): Int =
    floorsBuilt.coerceAtLeast(0) * 10 +
        perfectCount.coerceAtLeast(0) * 15 +
        if (completed) 200 else 0

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
