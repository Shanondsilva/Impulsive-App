package com.impulsive.app.frontend.screens.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.LessonCard
import com.impulsive.app.backend.domain.model.tasks.MindLesson
import com.impulsive.app.backend.domain.model.tasks.NormRect
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.SceneKind
import com.impulsive.app.backend.domain.model.tasks.SceneSpec
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.MindLessonPhase
import com.impulsive.app.backend.session.tasks.MindLessonUiState
import com.impulsive.app.backend.session.tasks.MindLessonViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
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
fun MindLessonScreen(
    onExit: () -> Unit,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    mindLessonViewModel: MindLessonViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by mindLessonViewModel.uiState.collectAsState()
    val onboardingState by onboardingViewModel.state.collectAsState()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsState()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var rewardLogged by remember { mutableStateOf(false) }
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

    fun logCompletion(validCompletion: Boolean) {
        if (rewardLogged) return
        rewardLogged = true
        if (launchSource != ReflexGameLaunchSource.TASK_TO_COMPLETE) return
        taskRewardViewModel.completeTask(
            taskType = PsychologyTaskType.MindLesson,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = "TASK_TO_COMPLETE",
            gameType = PsychologyTaskType.MindLesson.id.uppercase(),
            score = uiState.selectedOptionIndex,
            durationSec = uiState.secondsSpent,
            validCompletion = validCompletion,
        )
    }

    fun exitSafely() {
        if (uiState.validCompletion) {
            taskRewardViewModel.clearLastCompletionResult()
        } else if (uiState.lesson != null && !uiState.answeredCorrectly) {
            logCompletion(validCompletion = false)
        }
        onExit()
    }

    BackHandler { exitSafely() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mindLessonViewModel.resume()
                Lifecycle.Event.ON_STOP -> mindLessonViewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.validCompletion) {
        if (uiState.validCompletion) {
            logCompletion(validCompletion = true)
        }
    }

    LaunchedEffect(uiState.phase, uiState.lesson?.id) {
        while (isActive && uiState.phase != MindLessonPhase.Success && uiState.lesson != null) {
            withFrameMillis { }
            mindLessonViewModel.tick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        LessonHeader(onExit = ::exitSafely)
        Spacer(modifier = Modifier.height(18.dp))

        val lesson = uiState.lesson
        if (lesson == null) {
            LoadingPanel()
        } else {
            when (uiState.phase) {
                MindLessonPhase.Cards -> LessonCards(
                    lesson = lesson,
                    uiState = uiState,
                    onNext = mindLessonViewModel::next,
                    onPrevious = mindLessonViewModel::previous,
                    onPuzzleTap = mindLessonViewModel::onPuzzleTap,
                    onPuzzleMiss = mindLessonViewModel::onPuzzleMiss,
                    onResetCard = mindLessonViewModel::resetCurrentCard,
                )
                MindLessonPhase.Question -> CheckQuestion(
                    lesson = lesson,
                    uiState = uiState,
                    onSelectAnswer = mindLessonViewModel::selectAnswer,
                )
                MindLessonPhase.Success -> LessonSuccess(
                    lesson = lesson,
                    uiState = uiState,
                    taskCompletionResult = taskCompletionResult,
                    onDone = ::exitSafely,
                )
            }
        }
    }
}

@Composable
private fun LessonHeader(onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "Mind Lesson",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LessonCards(
    lesson: MindLesson,
    uiState: MindLessonUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPuzzleTap: (Int) -> Unit,
    onPuzzleMiss: () -> Unit,
    onResetCard: () -> Unit,
) {
    var dragTotal by remember(uiState.cardIndex) { mutableFloatStateOf(0f) }
    val card = lesson.cards[uiState.cardIndex]
    val isLastCard = uiState.cardIndex == lesson.cards.lastIndex
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        uiState.feedbackMessage?.let { message ->
            Surface(
                color = ImpulsivePsychological.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        LessonProgress(
            cardIndex = uiState.cardIndex,
            totalCards = lesson.cards.size,
            cardProgress = uiState.currentCardProgress,
            cardLabel = when (card) {
                is LessonCard.Text -> "Read first"
                is LessonCard.SpotTheDifference -> if (uiState.currentCardSolved) "Solved" else "${uiState.currentPuzzleRemainingTaps} tries left"
                is LessonCard.FindTarget -> if (uiState.currentCardSolved) "Solved" else "${uiState.currentPuzzleRemainingTaps} tries left"
            },
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (card) {
                is LessonCard.Text -> TextCardContent(
                    lesson = lesson,
                    card = card,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uiState.cardIndex, uiState.currentCardCanAdvance) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        dragTotal < -80f && uiState.currentCardCanAdvance -> onNext()
                                        dragTotal > 80f -> onPrevious()
                                    }
                                    dragTotal = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragTotal += dragAmount
                                },
                            )
                        },
                )
                is LessonCard.SpotTheDifference -> SpotTheDifferenceCard(
                    card = card,
                    foundIndices = uiState.currentPuzzleFoundIndices,
                    wrongTapCount = uiState.currentPuzzleWrongTaps,
                    remainingTaps = uiState.currentPuzzleRemainingTaps,
                    locked = uiState.currentPuzzleLocked,
                    onPuzzleTap = onPuzzleTap,
                    onPuzzleMiss = onPuzzleMiss,
                    onResetCard = onResetCard,
                )
                is LessonCard.FindTarget -> FindTargetCard(
                    card = card,
                    solved = uiState.currentCardSolved,
                    wrongTapCount = uiState.currentPuzzleWrongTaps,
                    remainingTaps = uiState.currentPuzzleRemainingTaps,
                    locked = uiState.currentPuzzleLocked,
                    onPuzzleTap = onPuzzleTap,
                    onPuzzleMiss = onPuzzleMiss,
                    onResetCard = onResetCard,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPrevious,
                enabled = uiState.cardIndex > 0,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(0.8f)
                    .height(54.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                )
            }
            Button(
                onClick = onNext,
                enabled = uiState.currentCardCanAdvance,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .height(54.dp),
            ) {
                Text(
                    text = if (isLastCard) "Check" else "Next",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun TextCardContent(
    lesson: MindLesson,
    card: LessonCard.Text,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IllustrationBubble(key = card.illustrationKey)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = lesson.title,
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = card.line,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpotTheDifferenceCard(
    card: LessonCard.SpotTheDifference,
    foundIndices: Set<Int>,
    wrongTapCount: Int,
    remainingTaps: Int,
    locked: Boolean,
    onPuzzleTap: (Int) -> Unit,
    onPuzzleMiss: () -> Unit,
    onResetCard: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        val stacked = maxWidth < 420.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = card.prompt,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            PuzzleStatusRow(
                found = foundIndices.size,
                total = card.diffHotspots.size,
                wrongTapCount = wrongTapCount,
                remainingTaps = remainingTaps,
                locked = locked,
            )
            if (stacked) {
                ScenePane(
                    scene = card.baseScene,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    variant = true,
                    diffHotspots = card.diffHotspots,
                    foundIndices = foundIndices,
                    enabled = !locked,
                    onHotspotTap = onPuzzleTap,
                    onMiss = onPuzzleMiss,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScenePane(
                        scene = card.baseScene,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        variant = false,
                        diffHotspots = card.diffHotspots,
                        foundIndices = emptySet(),
                        enabled = false,
                        onHotspotTap = null,
                        onMiss = null,
                    )
                    ScenePane(
                        scene = card.baseScene,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        variant = true,
                        diffHotspots = card.diffHotspots,
                        foundIndices = foundIndices,
                        enabled = !locked,
                        onHotspotTap = onPuzzleTap,
                        onMiss = onPuzzleMiss,
                    )
                }
            }
            if (locked) {
                Button(
                    onClick = onResetCard,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImpulsivePsychological,
                        contentColor = ImpulsiveText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Reset card", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = if (foundIndices.size == card.diffHotspots.size) {
                        "All differences found."
                    } else if (stacked) {
                        "Tap only the differences you can see. Guessing locks the card."
                    } else {
                        "Use the left image as reference. Tap differences on the right."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FindTargetCard(
    card: LessonCard.FindTarget,
    solved: Boolean,
    wrongTapCount: Int,
    remainingTaps: Int,
    locked: Boolean,
    onPuzzleTap: (Int) -> Unit,
    onPuzzleMiss: () -> Unit,
    onResetCard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = card.prompt,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        PuzzleStatusRow(
            found = if (solved) 1 else 0,
            total = 1,
            wrongTapCount = wrongTapCount,
            remainingTaps = remainingTaps,
            locked = locked,
        )
        ScenePane(
            scene = card.scene,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            variant = true,
            diffHotspots = listOf(card.targetHotspot),
            foundIndices = if (solved) setOf(0) else emptySet(),
            enabled = !locked,
            onHotspotTap = onPuzzleTap,
            onMiss = onPuzzleMiss,
        )
        if (locked) {
            Button(
                onClick = onResetCard,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Reset card", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = if (solved) "Target found." else "Tap the one item that stands out. Guessing locks the card.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PuzzleStatusRow(
    found: Int,
    total: Int,
    wrongTapCount: Int,
    remainingTaps: Int,
    locked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = ImpulsivePsychological.copy(alpha = 0.24f),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "Found $found / $total",
                color = ImpulsiveText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Surface(
            color = if (locked) {
                ImpulsivePsychological.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = if (locked) "Locked" else "Misses $wrongTapCount / ${wrongTapCount + remainingTaps}",
                color = if (locked) ImpulsiveText else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ScenePane(
    scene: SceneSpec,
    modifier: Modifier = Modifier,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
    enabled: Boolean,
    onHotspotTap: ((Int) -> Unit)?,
    onMiss: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F0FB)),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (enabled && onHotspotTap != null) {
                    Modifier.pointerInput(scene, foundIndices, enabled) {
                        detectTapGestures { offset ->
                            val size = this.size
                            val hotspotIndex = diffHotspots.indexOfFirst { rect ->
                                val hit = rect.toPixelRect(size.width.toFloat(), size.height.toFloat())
                                offset.x in hit.left..hit.right && offset.y in hit.top..hit.bottom
                            }
                            if (hotspotIndex >= 0) {
                                onHotspotTap(hotspotIndex)
                            } else {
                                onMiss?.invoke()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(scene = scene, variant = variant, diffHotspots = diffHotspots, foundIndices = foundIndices)
        }
    }
}

private fun NormRect.toPixelRect(width: Float, height: Float): Rect =
    Rect(
        left = x * width,
        top = y * height,
        right = (x + w) * width,
        bottom = (y + h) * height,
    )

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScene(
    scene: SceneSpec,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
) {
    drawRoundRect(
        color = Color(0xFFF4F0FB),
        cornerRadius = CornerRadius(32f, 32f),
    )
    when (scene.kind) {
        SceneKind.Window -> drawWindowScene(scene.seed, variant, diffHotspots, foundIndices)
        SceneKind.Orbs -> drawOrbScene(scene.seed, variant, diffHotspots, foundIndices)
        SceneKind.Tiles -> drawTileScene(scene.seed, variant, diffHotspots, foundIndices)
        SceneKind.Path -> drawPathScene(scene.seed, variant, diffHotspots, foundIndices)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWindowScene(
    seed: Int,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
) {
    val bg = Color(0xFFEDE8FA)
    drawRoundRect(color = bg)
    val columns = 4
    val rows = 3
    val tileW = size.width / (columns + 1)
    val tileH = size.height / (rows + 1)
    for (row in 0 until rows) {
        for (col in 0 until columns) {
            val left = tileW * (col + 0.5f)
            val top = tileH * (row + 0.5f)
            val color = when ((seed + row + col) % 3) {
                0 -> ImpulsivePhysical.copy(alpha = 0.70f)
                1 -> ImpulsiveSpiritual.copy(alpha = 0.76f)
                else -> ImpulsivePsychological.copy(alpha = 0.68f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(tileW * 0.56f, tileH * 0.56f),
                cornerRadius = CornerRadius(18f, 18f),
            )
        }
    }
    if (variant) {
        diffHotspots.forEachIndexed { index, hotspot ->
            val rect = hotspot.toPixelRect(size.width, size.height)
            if (index !in foundIndices) {
                drawRoundRect(
                    color = ImpulsivePsychological.copy(alpha = 0.92f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 4f),
                )
            } else {
                drawRoundRect(
                    color = ImpulsivePsychological.copy(alpha = 0.42f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(18f, 18f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbScene(
    seed: Int,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
) {
    drawRoundRect(color = Color(0xFFEDE8FA))
    drawCircle(color = ImpulsivePhysical.copy(alpha = 0.30f), radius = size.minDimension * 0.46f)
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until 5) {
        val angle = (seed * 17 + i * 53) * 0.017f
        val r = size.minDimension * (0.12f + i * 0.05f)
        drawCircle(
            color = if (i % 2 == 0) ImpulsivePsychological.copy(alpha = 0.76f) else ImpulsiveSpiritual.copy(alpha = 0.78f),
            radius = r,
            center = Offset(cx + kotlin.math.cos(angle) * 60f, cy + kotlin.math.sin(angle) * 40f),
        )
    }
    if (variant) {
        diffHotspots.forEachIndexed { index, hotspot ->
            val rect = hotspot.toPixelRect(size.width, size.height)
            val center = Offset(rect.center.x, rect.center.y)
            drawCircle(
                color = if (index in foundIndices) ImpulsivePsychological.copy(alpha = 0.42f) else ImpulsivePsychological,
                radius = minOf(rect.width, rect.height) * 0.36f,
                center = center,
                style = if (index in foundIndices) Fill else Stroke(width = 4f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTileScene(
    seed: Int,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
) {
    drawRoundRect(color = Color(0xFFEDE8FA))
    val cols = 4
    val rows = 3
    val tileW = size.width / cols
    val tileH = size.height / rows
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val left = col * tileW
            val top = row * tileH
            val isOdd = seed % cols == col && seed % rows == row
            val color = if (isOdd && variant) {
                ImpulsivePsychological.copy(alpha = 0.86f)
            } else {
                Color.White.copy(alpha = 0.92f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left + 12f, top + 12f),
                size = Size(tileW - 24f, tileH - 24f),
                cornerRadius = CornerRadius(20f, 20f),
            )
        }
    }
    if (variant) {
        diffHotspots.forEachIndexed { index, hotspot ->
            val rect = hotspot.toPixelRect(size.width, size.height)
            val center = Offset(rect.center.x, rect.center.y)
            drawCircle(
                color = if (index in foundIndices) ImpulsivePsychological.copy(alpha = 0.44f) else ImpulsivePsychological,
                radius = minOf(rect.width, rect.height) * 0.38f,
                center = center,
                style = if (index in foundIndices) Fill else Stroke(width = 4f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPathScene(
    seed: Int,
    variant: Boolean,
    diffHotspots: List<NormRect>,
    foundIndices: Set<Int>,
) {
    drawRoundRect(color = Color(0xFFEDE8FA))
    val path = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.72f)
        cubicTo(
            size.width * 0.28f,
            size.height * 0.34f,
            size.width * 0.56f + seed % 7,
            size.height * 0.88f,
            size.width * 0.88f,
            size.height * 0.32f,
        )
    }
    drawPath(path = path, color = ImpulsivePhysical.copy(alpha = 0.86f), style = Stroke(width = 14f))
    drawCircle(color = ImpulsivePsychological.copy(alpha = 0.54f), radius = size.minDimension * 0.10f, center = Offset(size.width * 0.72f, size.height * 0.26f))
    drawCircle(color = ImpulsiveSpiritual.copy(alpha = 0.46f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.26f, size.height * 0.58f))
    if (variant) {
        diffHotspots.forEachIndexed { index, hotspot ->
            val rect = hotspot.toPixelRect(size.width, size.height)
            val center = Offset(rect.center.x, rect.center.y)
            drawCircle(
                color = if (index in foundIndices) ImpulsivePsychological.copy(alpha = 0.44f) else ImpulsivePsychological,
                radius = minOf(rect.width, rect.height) * 0.38f,
                center = center,
                style = if (index in foundIndices) Fill else Stroke(width = 4f),
            )
        }
    }
}

@Composable
private fun LessonProgress(
    cardIndex: Int,
    totalCards: Int,
    cardProgress: Float,
    cardLabel: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
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
                Text(
                    text = "Card ${cardIndex + 1} of $totalCards",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = cardLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LinearProgressIndicator(
                progress = { cardProgress },
                color = ImpulsivePsychological,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun CheckQuestion(
    lesson: MindLesson,
    uiState: MindLessonUiState,
    onSelectAnswer: (Int) -> Unit,
) {
    val question = lesson.checkQuestion
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Quick check",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = question.prompt,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        question.options.forEachIndexed { index, option ->
            val hintDimmed = uiState.hintedWrongOptionIndex == index
            Surface(
                color = ImpulsiveSurface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (hintDimmed) 0.02f else 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                onClick = { onSelectAnswer(index) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(ImpulsivePsychological.copy(alpha = if (hintDimmed) 0.18f else 0.34f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ('A' + index).toString(),
                            color = ImpulsiveText.copy(alpha = if (hintDimmed) 0.48f else 1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hintDimmed) 0.48f else 1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (hintDimmed) {
                            Text(
                                text = "Less likely",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonSuccess(
    lesson: MindLesson,
    uiState: MindLessonUiState,
    taskCompletionResult: TaskCompletionResult?,
    onDone: () -> Unit,
) {
    val selected = uiState.selectedOptionIndex
    var allowFinishAfterDelay by remember(taskCompletionResult) { mutableStateOf(taskCompletionResult != null) }
    LaunchedEffect(taskCompletionResult) {
        if (taskCompletionResult == null) {
            delay(2_500L)
            allowFinishAfterDelay = true
        }
    }
    val explanation = selected?.let {
        lesson.checkQuestion.shortExplanationForEachOption.getOrNull(it)
    } ?: "Lesson saved."
    CenterPanel {
        Icon(
            imageVector = Icons.Filled.CheckCircleOutline,
            contentDescription = null,
            tint = ImpulsiveText,
            modifier = Modifier
                .size(58.dp)
                .background(ImpulsivePsychological.copy(alpha = 0.44f), CircleShape)
                .padding(14.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = uiState.feedbackMessage ?: "Lesson complete",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = explanation,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resultLabel(taskCompletionResult, allowFinishAfterDelay),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onDone,
            enabled = taskCompletionResult != null || allowFinishAfterDelay,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ImpulsiveText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (taskCompletionResult == null && !allowFinishAfterDelay) "Saving" else "Done")
        }
    }
}

@Composable
private fun IllustrationBubble(key: String?) {
    val icon = illustrationIcon(key)
    val color = when (key) {
        "moon", "timer", "battery" -> ImpulsivePsychological.copy(alpha = 0.42f)
        "wave", "bridge", "route", "trail" -> ImpulsivePhysical.copy(alpha = 0.48f)
        "seed", "leaf", "map" -> ImpulsiveSpiritual.copy(alpha = 0.58f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ImpulsiveText.copy(alpha = 0.82f),
            modifier = Modifier.size(44.dp),
        )
    }
}

private fun illustrationIcon(key: String?): ImageVector = when (key) {
    "moon" -> Icons.Filled.NightlightRound
    "wave" -> Icons.Filled.Waves
    "timer" -> Icons.Filled.Timer
    "route", "path", "trail", "map" -> Icons.Filled.Route
    "spark", "dot", "switch", "label" -> Icons.Filled.Lightbulb
    "steps", "bridge", "seed", "leaf", "battery", "thread", "lantern" -> Icons.Filled.Spa
    else -> Icons.AutoMirrored.Outlined.Article
}

@Composable
private fun LoadingPanel() {
    CenterPanel {
        Icon(
            imageVector = Icons.Filled.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading lesson",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
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

private fun resultLabel(result: TaskCompletionResult?, allowFinishAfterDelay: Boolean): String {
    if (result == null && allowFinishAfterDelay) return "Task complete. Saving is taking longer than expected."
    if (result == null) return "Saving reward..."
    val wait = if (result.waitReductionMinutes > 0) {
        "Wait cut by ${result.waitReductionMinutes.formatMinutes()}"
    } else {
        "Window already protected"
    }
    return "$wait  +${result.levelPointsAwarded} LP"
}

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
