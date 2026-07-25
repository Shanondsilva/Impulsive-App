package com.impulsive.app.frontend.screens.games

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlin.random.Random
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.game.GameView
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.game.RhythmTilesCatalog
import com.impulsive.app.backend.domain.game.RhythmTilesConfig
import com.impulsive.app.backend.domain.game.RhythmTilesResult
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.game.RhythmTilesUiState
import com.impulsive.app.backend.session.game.RhythmTilesViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.GameSoundToggle
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.UrgeRatingRow
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.RhythmNotePlayer
import com.impulsive.app.frontend.utils.rememberRhythmNotePlayer
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

@Composable
fun RhythmTilesScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAnother: () -> Unit = {},
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.RECOVERY_GAME,
    viewModel: RhythmTilesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    LockPortraitOrientation()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val notePlayer = rememberRhythmNotePlayer(appSettingsState.soundEffectsEnabled)
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
    var rewardLogged by remember(launchSource) { mutableStateOf(false) }

    LaunchedEffect(launchSource) {
        if (launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE) {
            viewModel.startTaskCountdown()
        }
    }

    LaunchedEffect(uiState.result, launchSource) {
        val result = uiState.result
        if (
            launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE &&
            result != null &&
            result.validCompletion &&
            !result.gameOver &&
            !rewardLogged
        ) {
            rewardLogged = true
            taskRewardViewModel.completeTask(
                taskType = PsychologyTaskType.RhythmTiles,
                releasePlan = releasePlan,
                now = LocalDateTime.now(),
                launchedFrom = "TASK_TO_COMPLETE",
                gameType = "RHYTHM_TILES",
                score = result.score,
                durationSec = result.durationSec,
                validCompletion = true,
            )
        }
    }

    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
    // A block-launched round that ended early has no allowed exit: the only way on
    // is to finish a full round. Hub rounds keep back.
    val mustReplay = uiState.view == GameView.Result && uiState.result?.gameOver == true
    BackHandler {
        if (!(mustReplay && taskLaunch)) {
            onExit()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
        ) {
            if (!(mustReplay && taskLaunch)) {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = "Rhythm Tiles",
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

        when (uiState.view) {
            GameView.Ready -> {
                if (launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE) {
                    RhythmCountdownView(uiState.countdown)
                } else {
                    RhythmReadyView(
                        uiState = uiState,
                        onSelectSong = viewModel::selectSong,
                        onUrgeBeforeSelected = viewModel::setUrgeBefore,
                        onStart = viewModel::startCountdown,
                    )
                }
            }
            GameView.Countdown -> RhythmCountdownView(uiState.countdown)
            GameView.Playing -> RhythmPlayingView(
                uiState = uiState,
                viewModel = viewModel,
                notePlayer = notePlayer,
            )
            GameView.Result -> RhythmResultView(
                result = uiState.result,
                onUrgeAfterSelected = viewModel::setUrgeAfter,
                onWalkAway = viewModel::walkAway,
                onPlayAgain = viewModel::playAgain,
                onPlayAnother = onPlayAnother,
                onExit = onExit,
            )
            GameView.Walked -> RhythmWalkedView(score = uiState.walkScore, onExit = onExit)
        }
    }
}

@Composable
private fun RhythmCenterPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun RhythmReadyView(
    uiState: RhythmTilesUiState,
    onSelectSong: (String) -> Unit,
    onUrgeBeforeSelected: (Int) -> Unit,
    onStart: () -> Unit,
) {
    RhythmCenterPanel {
        Text(
            text = "Rhythm Tiles",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap the falling tiles and every tap plays the next note of the song.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Turn your sound on. This one's better loud.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
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
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            RhythmTilesCatalog.songs.forEach { song ->
                val selected = song.id == uiState.selectedSong.id
                Surface(
                    color = if (selected) {
                        ImpulsivePsychological
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .pointerInput(song.id) {
                            detectTapGestures { onSelectSong(song.id) }
                        },
                ) {
                    Text(
                        text = song.title,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start 90-second round")
        }
    }
}

@Composable
private fun RhythmCountdownView(countdown: Int) {
    val scale by animateFloatAsState(
        targetValue = if (countdown == 0) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "rhythmCountdownScale",
    )
    RhythmCenterPanel {
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
private fun RhythmPlayingView(
    uiState: RhythmTilesUiState,
    viewModel: RhythmTilesViewModel,
    notePlayer: RhythmNotePlayer,
) {
    var frameNowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive && viewModel.uiState.value.view == GameView.Playing) {
            withFrameMillis { }
            frameNowMs = SystemClock.uptimeMillis()
            viewModel.tick()
        }
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (uiState.shake) 6f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "rhythmShake",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RhythmHudStat(label = "Time", value = "${uiState.timeLeft}s")
            RhythmHudStat(label = "Score", value = uiState.score.toString())
            RhythmHudStat(label = "Combo", value = "x${uiState.combo}")
            RhythmHudStat(label = "Lives", value = uiState.lives.toString())
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .graphicsLayer { translationX = shakeOffset },
        ) {
            val laneWidth = maxWidth / RhythmTilesConfig.LANES
            val arenaHeight = maxHeight
            val tileHeight = 160.dp
            val density = LocalDensity.current
            val arenaHeightPx = with(density) { arenaHeight.toPx() }
            val tileHeightPx = with(density) { tileHeight.toPx() }
            val laneWidthPx = with(density) { laneWidth.toPx() }
            val isDarkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val laneDividerColor = if (isDarkUi) {
                Color.White.copy(alpha = 0.24f)
            } else {
                Color.Black.copy(alpha = 0.22f)
            }
            val tileFillColor = if (isDarkUi) {
                Color.White
            } else {
                Color.Black
            }
            val tileBorderColor = if (isDarkUi) {
                Color.White.copy(alpha = 0.92f)
            } else {
                Color.Black.copy(alpha = 0.88f)
            }

            ImpulsiveAmbientBackground(modifier = Modifier.fillMaxSize())

            Row(modifier = Modifier.fillMaxSize()) {
                repeat(RhythmTilesConfig.LANES) { lane ->
                    Box(
                        modifier = Modifier
                            .width(laneWidth)
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = laneDividerColor,
                            )
                            .pointerInput(lane) {
                                detectTapGestures {
                                    val semitone = viewModel.tapLane(lane)
                                    if (semitone != null) {
                                        notePlayer.playNote(semitone)
                                    } else {
                                        viewModel.tapEmpty()
                                    }
                                }
                            },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f),
                            ),
                        ),
                    ),
            )

            uiState.tiles.forEach { tile ->
                val rawProgress =
                    (frameNowMs - tile.spawnAtMs).toFloat() / tile.fallDurationMs.toFloat()
                val progress = rawProgress.coerceIn(0f, 1.08f)
                val travelPx = arenaHeightPx + tileHeightPx
                val yPx = travelPx * progress - tileHeightPx
                val xPx = laneWidthPx * tile.lane

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xPx
                            translationY = yPx
                        }
                        .width(laneWidth)
                        .height(tileHeight)
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .background(tileFillColor)
                        .border(
                            width = 1.dp,
                            color = tileBorderColor,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RhythmHudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun RhythmResultView(
    result: RhythmTilesResult?,
    onUrgeAfterSelected: (Int) -> Unit,
    onWalkAway: () -> Unit,
    onPlayAgain: () -> Unit,
    onPlayAnother: () -> Unit,
    onExit: () -> Unit,
) {
    if (result == null) return
    RhythmCenterPanel {
        Text(
            text = if (result.gameOver) "Round didn't finish" else "Rhythm Tiles complete",
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
        RhythmStatRow("Max combo", result.maxCombo.toString())
        RhythmStatRow("Hits", result.hits.toString())
        RhythmStatRow("Misses", result.misses.toString())
        RhythmStatRow("Loops", result.loopsCompleted.toString())
        RhythmStatRow("Duration", "${result.durationSec}s")
        Spacer(modifier = Modifier.height(18.dp))
        if (result.gameOver) {
            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play same game")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onPlayAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Play another")
            }
        } else {
            Button(
                onClick = onWalkAway,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Walk away (+${RhythmTilesConfig.WALK_AWAY_BONUS})")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Play again")
            }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun RhythmStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
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
private fun RhythmWalkedView(score: Int, onExit: () -> Unit) {
    RhythmCenterPanel {
        Text(
            text = "You walked away.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = score.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 54.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done")
        }
    }
}
