package com.impulsive.app.frontend.screens.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.game.SkylineDropResult
import com.impulsive.app.backend.domain.game.SkylineResetPerPerfectControlPoints
import com.impulsive.app.backend.domain.game.SkylineResetRoundSeconds
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
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private val SkylineBackground = Color(0xFF0F0B22)
private val WindowWarm = Color(0xFFFFE0A0)
private val WindowCool = Color(0xFFCFE0FF)
private val WindowOff = Color(0xFF241F40)

@Composable
fun SkylineResetScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: SkylineResetViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
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
            taskType = PsychologyTaskType.SkylineReset,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = launchedFrom(),
            gameType = PsychologyTaskType.SkylineReset.id.uppercase(),
            score = uiState.floorsBuilt,
            durationSec = uiState.secondsPlayed,
            validCompletion = validCompletion,
        )
    }

    fun exitSafely() {
        if (uiState.view == SkylineResetView.Result) {
            viewModel.recordCurrentResult(
                outcome = if (uiState.completed) ScoreSessionOutcome.Completed else ScoreSessionOutcome.Abandoned,
            )
        } else if (uiState.view != SkylineResetView.Ready) {
            viewModel.recordCurrentResult(ScoreSessionOutcome.WalkedAway)
        }
        if (!uiState.completed && uiState.view != SkylineResetView.Ready && uiState.view != SkylineResetView.Result) {
            logCompletion(validCompletion = false)
        }
        if (uiState.completed) {
            taskRewardViewModel.clearLastCompletionResult()
        }
        onExit()
    }

    BackHandler { exitSafely() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.view, uiState.completed, taskLaunch) {
        if (uiState.view == SkylineResetView.Result) {
            logCompletion(validCompletion = true)
            if (taskLaunch && uiState.completed) {
                viewModel.bankPerfectControlPoints()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SkylineBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::exitSafely) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = "SkyStack",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (uiState.view) {
            SkylineResetView.Ready -> SkylineReadyPanel(onStart = viewModel::start)
            SkylineResetView.Playing -> SkylinePlayArea(
                uiState = uiState,
                onTick = viewModel::tick,
                onDrop = viewModel::drop,
            )
            SkylineResetView.Paused -> SkylinePausedPanel(onResume = viewModel::resume, onExit = ::exitSafely)
            SkylineResetView.Result -> SkylineResultPanel(
                uiState = uiState,
                taskCompletionResult = taskCompletionResult,
                taskLaunch = taskLaunch,
                onDone = ::exitSafely,
                onPlayAgain = {
                    taskRewardViewModel.clearLastCompletionResult()
                    rewardLogged = false
                    viewModel.start()
                },
            )
        }
    }
}

private class SkylineFx {
    val falls = ArrayList<FallPiece>()
    val rings = ArrayList<RingFx>()
    var cam = 0f
    var lastMs = 0L
    var scene: SkyScene? = null
}

private class FallPiece(var x: Float, var w: Float, var top: Float, var vy: Float, var alpha: Float, val hue: Int)
private class RingFx(var x: Float, var y: Float, var r: Float, var alpha: Float, val hue: Int)

@Composable
private fun SkylinePlayArea(
    uiState: SkylineResetUiState,
    onTick: () -> Unit,
    onDrop: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkylineProgressHud(uiState = uiState)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val floorH = with(density) { 26.dp.toPx() }
            val marginPx = with(density) { 28.dp.toPx() }
            val fx = remember { SkylineFx() }
            var frameTick by remember { mutableStateOf(0L) }
            val stateNow by rememberUpdatedState(uiState)

            fun naturalTop(i: Int): Float = (hPx - marginPx) - (i + 1) * floorH

            LaunchedEffect(uiState.dropSeq) {
                if (uiState.dropSeq <= 0) return@LaunchedEffect
                val floorsCount = uiState.floors.size
                val placedIdx = floorsCount - 1
                val topY = naturalTop(placedIdx) + fx.cam
                val hue = uiState.floors.lastOrNull()?.hue ?: 0
                if (uiState.lastTrimWidth > 0f) {
                    fx.falls.add(
                        FallPiece(
                            x = uiState.lastTrimLeft * wPx,
                            w = uiState.lastTrimWidth * wPx,
                            top = topY,
                            vy = 0f,
                            alpha = 1f,
                            hue = hue,
                        ),
                    )
                }
                if (uiState.lastDropResult == SkylineDropResult.Perfect) {
                    val placed = uiState.floors.lastOrNull()
                    if (placed != null) {
                        fx.rings.add(
                            RingFx(
                                x = (placed.left + placed.width / 2f) * wPx,
                                y = topY + floorH / 2f,
                                r = placed.width * wPx * 0.4f,
                                alpha = 0.85f,
                                hue = hue,
                            ),
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                while (isActive) {
                    val ms = withFrameMillis { it }
                    val dt = if (fx.lastMs == 0L) 16f else (ms - fx.lastMs).coerceIn(0L, 50L).toFloat()
                    fx.lastMs = ms
                    val f = dt / 16f
                    onTick()
                    if (fx.scene == null && wPx > 0f && hPx > 0f) fx.scene = genSkyScene(wPx, hPx)
                    val sc = fx.scene
                    if (sc != null) {
                        sc.t += dt
                        for (c in sc.clouds) {
                            c.x += c.sp * f
                            if (c.x - c.r > wPx) c.x = -c.r
                        }
                        for (s in sc.stars) s.tw += s.sp * f
                        val bird = sc.bird
                        if (bird == null) {
                            if (Random.nextFloat() < 0.004f * f) {
                                val dir = if (Random.nextBoolean()) 1 else -1
                                val n = 3 + Random.nextInt(4)
                                val offs = ArrayList<Pair<Float, Float>>()
                                for (k in 0 until n) offs.add(Pair(k * 16f * dir, (k % 2) * 9f))
                                sc.bird = SkyBird(
                                    if (dir > 0) -30f else wPx + 30f,
                                    30f + Random.nextFloat() * hPx * 0.22f,
                                    dir * (0.6f + Random.nextFloat() * 0.5f),
                                    dir,
                                    offs,
                                    0f,
                                )
                            }
                        } else {
                            bird.x += bird.vx * f
                            bird.ph += 0.25f * f
                            if ((bird.dir > 0 && bird.x > wPx + 60f) || (bird.dir < 0 && bird.x < -60f)) sc.bird = null
                        }
                        val shoot = sc.shoot
                        if (shoot == null) {
                            if (Random.nextFloat() < 0.0025f * f) {
                                sc.shoot = SkyShoot(
                                    Random.nextFloat() * wPx * 0.6f + wPx * 0.2f,
                                    Random.nextFloat() * hPx * 0.2f + 10f,
                                    0f,
                                    1f,
                                    6f,
                                    2.4f,
                                )
                            }
                        } else {
                            shoot.x += shoot.vx * f
                            shoot.y += shoot.vy * f
                            shoot.len = (shoot.len + 5f * f).coerceAtMost(70f)
                            shoot.alpha -= 0.02f * f
                            if (shoot.alpha <= 0f) sc.shoot = null
                        }
                    }
                    val floorsCount = stateNow.floors.size
                    val camTarget = (hPx * 0.30f - naturalTop(floorsCount)).coerceAtLeast(0f)
                    fx.cam += (camTarget - fx.cam) * (0.12f * f).coerceAtMost(1f)
                    val itF = fx.falls.iterator()
                    while (itF.hasNext()) {
                        val p = itF.next()
                        p.vy += 0.6f * f
                        p.top += p.vy * f
                        p.alpha -= 0.012f * f
                        if (p.top > hPx + 80f || p.alpha <= 0f) itF.remove()
                    }
                    val itR = fx.rings.iterator()
                    while (itR.hasNext()) {
                        val r = itR.next()
                        r.r += 2.4f * f
                        r.alpha -= 0.04f * f
                        if (r.alpha <= 0f) itR.remove()
                    }
                    frameTick = ms
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onDrop() } },
            ) {
                frameTick
                val nightScene = fx.scene
                if (nightScene != null) {
                    drawNightCity(nightScene, fx.cam, wPx, hPx, marginPx)
                } else {
                    drawRect(color = SkylineBackground, size = Size(wPx, hPx))
                }
                val floors = stateNow.floors
                for (i in floors.indices) {
                    val fl = floors[i]
                    val y = naturalTop(i) + fx.cam
                    if (y > hPx + floorH || y < -floorH) continue
                    drawSkylineFloor(fl.left, fl.width, fl.hue, i, false, wPx, floorH, y)
                }
                drawSkylineFloor(
                    stateNow.movingLeft,
                    stateNow.movingWidth,
                    stateNow.movingHue,
                    floors.size,
                    true,
                    wPx,
                    floorH,
                    naturalTop(floors.size) + fx.cam,
                )
                for (p in fx.falls) {
                    drawRoundRect(
                        color = Color.hsv(p.hue.toFloat(), 0.40f, 0.42f).copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        topLeft = Offset(p.x, p.top),
                        size = Size(p.w, floorH - 3f),
                        cornerRadius = CornerRadius(5f, 5f),
                    )
                }
                for (r in fx.rings) {
                    drawCircle(
                        color = Color.hsv(r.hue.toFloat(), 0.55f, 0.85f).copy(alpha = r.alpha.coerceIn(0f, 1f)),
                        radius = r.r,
                        center = Offset(r.x, r.y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                    )
                }
            }
        }
        Text(
            text = "Tap to drop each floor",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun DrawScope.drawSkylineFloor(
    left01: Float,
    width01: Float,
    hue: Int,
    idx: Int,
    active: Boolean,
    wPx: Float,
    floorH: Float,
    topY: Float,
) {
    val x = left01 * wPx
    val w = width01 * wPx
    drawRoundRect(
        color = Color.hsv(hue.toFloat(), 0.40f, if (active) 0.44f else 0.40f),
        topLeft = Offset(x, topY),
        size = Size(w, floorH - 3f),
        cornerRadius = CornerRadius(5f, 5f),
    )
    drawRoundRect(
        color = Color.hsv(hue.toFloat(), 0.46f, if (active) 0.58f else 0.54f),
        topLeft = Offset(x, topY),
        size = Size(w, 4f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    val cols = ((w - 8f) / 9f).toInt()
    if (cols < 1) return
    val span = cols * 9f - 3f
    val sx = x + (w - span) / 2f
    val wy = topY + 11f
    for (j in 0 until cols) {
        val lit = ((idx * 7 + j * 3) % 5) != 0
        val warm = ((idx + j * 2) % 4) != 0
        val base = if (lit) (if (warm) WindowWarm else WindowCool) else WindowOff
        drawRect(
            color = base.copy(alpha = if (active) 0.32f else 0.95f),
            topLeft = Offset(sx + j * 9f, wy),
            size = Size(4f, 5f),
        )
    }
}

@Composable
private fun SkylineProgressHud(uiState: SkylineResetUiState) {
    val progress = (uiState.secondsPlayed / SkylineResetRoundSeconds.toFloat()).coerceIn(0f, 1f)
    val potentialPoints = uiState.perfectCount.coerceAtLeast(0) * SkylineResetPerPerfectControlPoints

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
                SkylineHud("Time", "${uiState.secondsPlayed}s")
                SkylineHud("Floors", uiState.floorsBuilt.toString())
                SkylineHud("Perfect", uiState.perfectCount.toString())
                SkylineHud("Points", potentialPoints.toString())
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
                text = "Goal: ${SkylineResetRoundSeconds}s. Keep stacking until the round ends.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SkylineHud(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun SkylineReadyPanel(onStart: () -> Unit) {
    SkylineCenterPanel {
        Text(
            text = "Build a calm skyscraper, one floor at a time.",
            color = ImpulsiveText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap to drop each floor. Line it up with the one below to keep climbing.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ImpulsivePsychological, contentColor = ImpulsiveText),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start") }
    }
}

@Composable
private fun SkylinePausedPanel(onResume: () -> Unit, onExit: () -> Unit) {
    SkylineCenterPanel {
        Text(text = "Paused", color = ImpulsiveText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onResume,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ImpulsivePsychological, contentColor = ImpulsiveText),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resume") }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ImpulsiveSurface, contentColor = ImpulsiveText),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Leave") }
    }
}

@Composable
private fun SkylineResultPanel(
    uiState: SkylineResetUiState,
    taskCompletionResult: TaskCompletionResult?,
    taskLaunch: Boolean,
    onDone: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val doneSaving = !taskLaunch ||
        (taskCompletionResult != null && (!uiState.completed || uiState.controlPointsBanked != null))
    val controlPointsText = when {
        !uiState.completed -> "0"
        taskLaunch && uiState.controlPointsBanked == null -> "Saving"
        else -> (uiState.controlPointsBanked ?: 0).toString()
    }
    SkylineCenterPanel {
        Box(
            modifier = Modifier.size(58.dp).background(ImpulsivePsychological.copy(alpha = 0.46f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.floorsBuilt.toString(),
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (uiState.completed) "SkyStack complete" else "Tower toppled",
            color = ImpulsiveText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = skylineResultLabel(uiState, taskCompletionResult, taskLaunch),
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SkylineStatRow("Time", "${uiState.secondsPlayed}s")
        SkylineStatRow("Floors built", uiState.floorsBuilt.toString())
        SkylineStatRow("Perfect drops", uiState.perfectCount.toString())
        SkylineStatRow("Control points", controlPointsText)
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onDone,
            enabled = doneSaving,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ImpulsivePsychological, contentColor = ImpulsiveText),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (doneSaving) "Done" else "Saving") }
        if (!taskLaunch) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ImpulsiveSurface, contentColor = ImpulsiveText),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.completed) "Play again" else "Try again") }
        }
    }
}

private fun skylineResultLabel(uiState: SkylineResetUiState, result: TaskCompletionResult?, taskLaunch: Boolean): String {
    if (!taskLaunch) {
        return if (uiState.completed) {
            "Practice round complete."
        } else {
            "Stack ended at ${uiState.floorsBuilt} floors."
        }
    }
    if (result == null || (uiState.completed && uiState.controlPointsBanked == null)) return "Saving reward..."
    val banked = uiState.controlPointsBanked ?: 0
    val bankedText = if (uiState.completed && banked > 0) " $banked control points banked." else ""
    return "Task saved. +${result.levelPointsAwarded} LP.$bankedText"
}

@Composable
private fun SkylineStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = ImpulsiveMutedText, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = ImpulsiveText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SkylineCenterPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = ImpulsiveSurface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}

private class SkyWin(val x: Float, val y: Float, val on: Boolean, val warm: Boolean)

private class SkyBuilding(
    val x: Float,
    val w: Float,
    val h: Float,
    val type: Int,
    val wins: List<SkyWin>,
    val hasLight: Boolean,
    val blink: Float,
    val color: Color,
)

private class SkyStar(val x: Float, val y: Float, val r: Float, var tw: Float, val sp: Float)
private class SkyCloud(var x: Float, val y: Float, val r: Float, val sp: Float)
private class SkyBird(var x: Float, val y: Float, val vx: Float, val dir: Int, val offs: List<Pair<Float, Float>>, var ph: Float)
private class SkyShoot(var x: Float, var y: Float, var len: Float, var alpha: Float, val vx: Float, val vy: Float)

private class SkyScene(
    val far: List<SkyBuilding>,
    val mid: List<SkyBuilding>,
    val near: List<SkyBuilding>,
    val stars: List<SkyStar>,
    val clouds: List<SkyCloud>,
) {
    var bird: SkyBird? = null
    var shoot: SkyShoot? = null
    var t: Float = 0f
}

private fun genSkyLayer(
    rng: Random,
    wPx: Float,
    color: Color,
    minH: Float,
    maxH: Float,
    density: Float,
    winChance: Float,
): List<SkyBuilding> {
    val list = ArrayList<SkyBuilding>()
    var x = -20f
    while (x < wPx + 50f) {
        val w = 22f + rng.nextFloat() * 42f
        val h = minH + rng.nextFloat() * (maxH - minH)
        val wins = ArrayList<SkyWin>()
        var wy = 9f
        while (wy < h - 9f) {
            var wx = 5f
            while (wx < w - 7f) {
                wins.add(SkyWin(wx, wy, rng.nextFloat() < winChance, rng.nextFloat() < 0.78f))
                wx += 10f
            }
            wy += 13f
        }
        val hasLight = h > maxH * 0.7f && rng.nextFloat() < 0.6f
        list.add(SkyBuilding(x, w, h, rng.nextInt(3), wins, hasLight, rng.nextFloat() * 6.28f, color))
        x += w + 5f + rng.nextFloat() * density
    }
    return list
}

private fun genSkyScene(wPx: Float, hPx: Float): SkyScene {
    val rng = Random(System.nanoTime())
    val far = genSkyLayer(rng, wPx, Color(0xFF2A2550), 70f, 130f, 16f, 0.5f)
    val mid = genSkyLayer(rng, wPx, Color(0xFF201B40), 95f, 180f, 11f, 0.62f)
    val near = genSkyLayer(rng, wPx, Color(0xFF15112C), 120f, 230f, 8f, 0.72f)
    val stars = ArrayList<SkyStar>()
    repeat(115) {
        stars.add(
            SkyStar(
                rng.nextFloat() * wPx,
                rng.nextFloat() * hPx * 0.58f,
                rng.nextFloat() * 1.8f + 0.55f,
                rng.nextFloat() * 6.28f,
                0.025f + rng.nextFloat() * 0.075f,
            ),
        )
    }
    val clouds = ArrayList<SkyCloud>()
    repeat(6) {
        clouds.add(
            SkyCloud(
                rng.nextFloat() * wPx,
                20f + rng.nextFloat() * hPx * 0.24f,
                42f + rng.nextFloat() * 42f,
                0.035f + rng.nextFloat() * 0.065f,
            ),
        )
    }
    return SkyScene(far, mid, near, stars, clouds)
}

private fun DrawScope.drawSkyLayer(
    arr: List<SkyBuilding>,
    parallax: Float,
    cam: Float,
    t: Float,
    wPx: Float,
    hPx: Float,
    marginPx: Float,
) {
    val baseY = (hPx - marginPx) + cam * parallax
    for (b in arr) {
        val top = baseY - b.h
        drawRect(color = b.color, topLeft = Offset(b.x, top), size = Size(b.w, hPx - top + 240f))
        drawRect(
            color = Color.Black.copy(alpha = 0.10f),
            topLeft = Offset(b.x + b.w * 0.72f, top),
            size = Size(b.w * 0.28f, hPx - top + 240f),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.035f),
            topLeft = Offset(b.x + 2f, top),
            size = Size(2f, hPx - top + 240f),
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(b.x, top),
            size = Size(b.w, 2f),
        )
        if (b.type == 1) {
            drawRect(color = b.color, topLeft = Offset(b.x + b.w * 0.25f, top - 10f), size = Size(b.w * 0.5f, 10f))
        } else if (b.type == 2) {
            drawRect(color = b.color, topLeft = Offset(b.x + b.w / 2f - 1.5f, top - 16f), size = Size(3f, 16f))
        }
        for (wn in b.wins) {
            if (!wn.on) continue
            val wc = if (wn.warm) Color(0xFFFFD37A) else Color(0xFFBBD2FF)
            val wx = b.x + wn.x
            val wy = top + wn.y
            drawRect(
                color = wc.copy(alpha = 0.22f),
                topLeft = Offset(wx - 1f, wy - 1f),
                size = Size(5f, 6f),
            )
            drawRect(
                color = wc.copy(alpha = 0.92f),
                topLeft = Offset(wx, wy),
                size = Size(3f, 4f),
            )
        }
        if (b.hasLight) {
            val bl = (0.4f + 0.6f * abs(sin(b.blink + t * 0.004f))).coerceIn(0f, 1f)
            val ly = top - if (b.type == 2) 17f else 2f
            drawCircle(color = Color(0xFFFF5A5A).copy(alpha = bl), radius = 2f, center = Offset(b.x + b.w / 2f, ly))
        }
    }
}

private fun DrawScope.drawNightCity(scene: SkyScene, cam: Float, wPx: Float, hPx: Float, marginPx: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to SkylineBackground,
            0.42f to Color(0xFF171236),
            0.74f to Color(0xFF241A44),
            1f to Color(0xFF33224D),
        ),
        size = Size(wPx, hPx),
    )
    val mx = wPx * 0.76f
    val my = hPx * 0.16f
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x8CDCE1FF), Color(0x00DCE1FF)), center = Offset(mx, my), radius = 70f),
        radius = 70f,
        center = Offset(mx, my),
    )
    drawCircle(color = Color(0xFFECEAF6), radius = 21f, center = Offset(mx, my))
    drawCircle(color = Color(0x80C8CDEB), radius = 4f, center = Offset(mx - 7f, my - 5f))
    drawCircle(color = Color(0x80C8CDEB), radius = 3f, center = Offset(mx + 6f, my + 4f))
    for (s in scene.stars) {
        val a = (0.42f + 0.58f * abs(sin(s.tw))).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = a),
            radius = s.r,
            center = Offset(s.x, s.y),
        )
        if (s.r > 1.6f) {
            drawLine(
                color = Color.White.copy(alpha = a * 0.42f),
                start = Offset(s.x - s.r * 2.1f, s.y),
                end = Offset(s.x + s.r * 2.1f, s.y),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color.White.copy(alpha = a * 0.36f),
                start = Offset(s.x, s.y - s.r * 2.1f),
                end = Offset(s.x, s.y + s.r * 2.1f),
                strokeWidth = 1f,
            )
        }
    }
    val sh = scene.shoot
    if (sh != null) {
        drawLine(
            color = Color.White.copy(alpha = sh.alpha.coerceIn(0f, 1f)),
            start = Offset(sh.x, sh.y),
            end = Offset(sh.x - sh.len * 0.93f, sh.y - sh.len * 0.37f),
            strokeWidth = 2f,
        )
    }
    for (c in scene.clouds) {
        drawMoonlitCloud(c)
    }
    val bird = scene.bird
    if (bird != null) {
        val wsp = 4f + 3f * sin(bird.ph)
        for (o in bird.offs) {
            val bx = bird.x + o.first
            val by = bird.y + o.second
            drawLine(Color(0xFF2C2750), Offset(bx - 6f, by), Offset(bx, by - wsp), strokeWidth = 2f)
            drawLine(Color(0xFF2C2750), Offset(bx, by - wsp), Offset(bx + 6f, by), strokeWidth = 2f)
        }
    }
    val hy = (hPx - marginPx) + cam * 0.55f
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x59785AA0), Color(0x00785AA0)), center = Offset(wPx * 0.5f, hy), radius = 240f),
        radius = 240f,
        center = Offset(wPx * 0.5f, hy),
    )
    drawSkyLayer(scene.far, 0.25f, cam, scene.t, wPx, hPx, marginPx)
    drawSkyLayer(scene.mid, 0.40f, cam, scene.t, wPx, hPx, marginPx)
    drawSkyLayer(scene.near, 0.55f, cam, scene.t, wPx, hPx, marginPx)
}

private fun DrawScope.drawMoonlitCloud(cloud: SkyCloud) {
    val base = Color(0xFFB7B8DD)
    val shadow = Color(0xFF5B5688)
    val x = cloud.x
    val y = cloud.y
    val r = cloud.r

    drawOval(
        brush = Brush.radialGradient(
            listOf(shadow.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(x, y + r * 0.16f),
            radius = r * 1.7f,
        ),
        topLeft = Offset(x - r * 1.55f, y - r * 0.32f),
        size = Size(r * 3.1f, r * 0.96f),
    )

    val puffs = listOf(
        Offset(-0.92f, 0.06f) to 0.58f,
        Offset(-0.45f, -0.18f) to 0.72f,
        Offset(0.10f, -0.25f) to 0.86f,
        Offset(0.68f, -0.08f) to 0.62f,
        Offset(1.08f, 0.10f) to 0.48f,
    )

    for ((offset, scale) in puffs) {
        val center = Offset(x + offset.x * r, y + offset.y * r)
        val radius = r * scale
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    base.copy(alpha = 0.23f),
                    shadow.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
