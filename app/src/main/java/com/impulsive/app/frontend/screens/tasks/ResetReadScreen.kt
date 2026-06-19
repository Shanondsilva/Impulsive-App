package com.impulsive.app.frontend.screens.tasks

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.RESET_READ_REMOTE_ENABLED
import com.impulsive.app.backend.domain.model.tasks.ArticleBlock
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticle
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.ResetReadLaunchMode
import com.impulsive.app.backend.session.tasks.ResetReadPhase
import com.impulsive.app.backend.session.tasks.ResetReadUiState
import com.impulsive.app.backend.session.tasks.ResetReadViewModel
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
fun ResetReadScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    launchMode: ResetReadLaunchMode = ResetReadLaunchMode.Normal,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    resetReadViewModel: ResetReadViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by resetReadViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var rewardLogged by remember { mutableStateOf(false) }
    LaunchedEffect(launchMode) {
        resetReadViewModel.configureLaunchMode(launchMode)
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

    fun logCompletion(validCompletion: Boolean) {
        if (rewardLogged) return
        rewardLogged = true
        taskRewardViewModel.completeTask(
            taskType = PsychologyTaskType.ResetRead,
            releasePlan = releasePlan,
            now = LocalDateTime.now(),
            launchedFrom = "TASK_TO_COMPLETE",
            gameType = PsychologyTaskType.ResetRead.id.uppercase(),
            score = uiState.selectedOptionIndex,
            durationSec = uiState.secondsSpent,
            validCompletion = validCompletion,
        )
    }

    fun exitSafely() {
        if (uiState.validCompletion) {
            taskRewardViewModel.clearLastCompletionResult()
        } else if (uiState.article != null) {
            val failureReason = when (uiState.phase) {
                ResetReadPhase.Reading -> "exit_during_reading"
                ResetReadPhase.Question -> "exit_before_answer"
                ResetReadPhase.Success -> "exit_after_incomplete_success_state"
            }
            resetReadViewModel.recordAbandonedSessionIfNeeded(failureReason)
            logCompletion(validCompletion = false)
        }
        onExit()
    }

    BackHandler { exitSafely() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resetReadViewModel.resume()
                Lifecycle.Event.ON_STOP -> resetReadViewModel.pause()
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

    LaunchedEffect(uiState.phase, uiState.article?.id) {
        while (isActive && uiState.phase == ResetReadPhase.Reading && uiState.article != null) {
            withFrameMillis { }
            resetReadViewModel.tick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ReaderHeader(onExit = ::exitSafely)
        Spacer(modifier = Modifier.height(18.dp))

        val article = uiState.article
        if (article == null) {
            LoadingPanel()
        } else {
            when (uiState.phase) {
                ResetReadPhase.Reading -> ReadingView(
                    article = article,
                    uiState = uiState,
                    onReachedEnd = resetReadViewModel::markReachedEnd,
                    onScrollProgress = resetReadViewModel::updateScrollProgress,
                    onOpenQuestion = resetReadViewModel::openQuestion,
                )
                ResetReadPhase.Question -> ClosingQuestion(
                    article = article,
                    onSelectAnswer = resetReadViewModel::selectAnswer,
                )
                ResetReadPhase.Success -> ResetReadSuccess(
                    uiState = uiState,
                    taskCompletionResult = taskCompletionResult,
                    onRateHelpfulness = resetReadViewModel::rateHelpfulness,
                    onDone = ::exitSafely,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeader(onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            text = "Reset Reading",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReadingView(
    article: ResetReadArticle,
    uiState: ResetReadUiState,
    onReachedEnd: () -> Unit,
    onScrollProgress: (Float) -> Unit,
    onOpenQuestion: () -> Unit,
) {
    val useRemote = RESET_READ_REMOTE_ENABLED && article.articleUrl != null
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReadProgressPanel(uiState = uiState)
        if (useRemote) {
            RemoteArticleView(
                articleUrl = article.articleUrl.orEmpty(),
                onReadConfirmed = onReachedEnd,
                modifier = Modifier.weight(1f),
            )
        } else {
            NativeArticleView(
                article = article,
                onReachedEnd = onReachedEnd,
                onScrollProgress = onScrollProgress,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = onOpenQuestion,
            enabled = uiState.canOpenQuestion,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ImpulsiveText,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = if (uiState.canOpenQuestion) "Answer reflection" else "Keep reading",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReadProgressPanel(uiState: ResetReadUiState) {
    val article = uiState.article
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article?.title.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = resetReadDurationLabel(uiState.requiredReadSeconds),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = if (uiState.reachedEnd) "Article end reached" else "Scroll to the end and let the read timer finish",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "Read timer: ${uiState.secondsSpent}s / ${((uiState.minimumReadMillis / 1_000L).toInt()).coerceAtLeast(0)}s",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NativeArticleView(
    article: ResetReadArticle,
    onReachedEnd: () -> Unit,
    onScrollProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var nonScrollableAutoMarked by remember(article.id) { mutableStateOf(false) }

    LaunchedEffect(article.id, scrollState.value, scrollState.maxValue) {
        val maxValue = scrollState.maxValue
        if (maxValue > 0) {
            val progress = (scrollState.value / maxValue.toFloat()).coerceIn(0f, 1f)
            onScrollProgress(progress)
            if (scrollState.value >= maxValue - 8) {
                onReachedEnd()
            }
        }
    }

    LaunchedEffect(article.id) {
        withFrameMillis { }
        withFrameMillis { }
        if (scrollState.maxValue <= 0 && !nonScrollableAutoMarked) {
            nonScrollableAutoMarked = true
            onScrollProgress(1f)
            onReachedEnd()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 2.dp,
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
        ) {
            Text(
                text = article.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(18.dp))
            article.blocks.forEachIndexed { index, block ->
                when (block) {
                    is ArticleBlock.Heading -> {
                        Text(
                            text = block.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    is ArticleBlock.Paragraph -> {
                        Text(
                            text = block.text,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        )
                    }
                    is ArticleBlock.Img -> InlineArticleImage(
                        key = block.key,
                        caption = block.caption,
                    )
                    is ArticleBlock.Video -> InlineArticleVideo(
                        assetFileName = block.assetFileName,
                        title = block.title,
                        caption = block.caption,
                    )
                }
                if (index != article.blocks.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            Surface(
                color = ImpulsivePsychological.copy(alpha = 0.25f),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(
                    text = "Pause here. Let the idea become the next action.",
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineArticleImage(
    key: String,
    caption: String? = null,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(22.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                drawAbstractIllustration(key)
            }
            if (caption != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun InlineArticleVideo(
    assetFileName: String,
    title: String,
    caption: String? = null,
) {
    val context = LocalContext.current
    val safeAssetFileName = remember(assetFileName) {
        assetFileName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .trim()
    }
    val mediaUri = remember(safeAssetFileName) {
        Uri.parse("asset:///reset_reading/videos/$safeAssetFileName")
    }
    val exoPlayer = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(22.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(10.dp))

            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )

            if (caption != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun DrawScope.drawAbstractIllustration(key: String) {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(
        color = ImpulsivePsychological.copy(alpha = 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
    )
    when (key) {
        "lavender_window" -> {
            drawRoundRect(
                color = ImpulsiveSurface.copy(alpha = 0.76f),
                topLeft = Offset(size.width * 0.14f, size.height * 0.16f),
                size = Size(size.width * 0.72f, size.height * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
            )
            drawCircle(
                color = ImpulsivePsychological.copy(alpha = 0.58f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.72f, size.height * 0.30f),
            )
            drawLine(
                color = ImpulsivePhysical.copy(alpha = 0.58f),
                start = Offset(size.width * 0.26f, size.height * 0.58f),
                end = Offset(size.width * 0.74f, size.height * 0.58f),
                strokeWidth = 10f,
            )
        }
        "soft_steps" -> {
            listOf(0.2f, 0.38f, 0.58f, 0.76f).forEachIndexed { index, x ->
                drawCircle(
                    color = if (index % 2 == 0) ImpulsivePsychological.copy(alpha = 0.52f) else ImpulsiveSpiritual.copy(alpha = 0.48f),
                    radius = size.minDimension * 0.10f,
                    center = Offset(size.width * x, size.height * (0.28f + index * 0.12f)),
                )
            }
        }
        "steady_wave" -> {
            drawLine(
                color = ImpulsiveSurface.copy(alpha = 0.72f),
                start = Offset(size.width * 0.12f, size.height * 0.70f),
                end = Offset(size.width * 0.88f, size.height * 0.70f),
                strokeWidth = 8f,
            )
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.66f)
                    cubicTo(
                        size.width * 0.26f,
                        size.height * 0.18f,
                        size.width * 0.42f,
                        size.height * 0.18f,
                        size.width * 0.54f,
                        size.height * 0.52f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        size.height * 0.86f,
                        size.width * 0.80f,
                        size.height * 0.74f,
                        size.width * 0.88f,
                        size.height * 0.54f,
                    )
                },
                color = ImpulsivePsychological.copy(alpha = 0.78f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f),
            )
            drawCircle(
                color = ImpulsivePhysical.copy(alpha = 0.50f),
                radius = size.minDimension * 0.07f,
                center = Offset(size.width * 0.54f, size.height * 0.52f),
            )
            drawCircle(
                color = ImpulsiveSpiritual.copy(alpha = 0.42f),
                radius = size.minDimension * 0.05f,
                center = Offset(size.width * 0.84f, size.height * 0.55f),
            )
        }
        "steady_line" -> {
            drawLine(
                color = ImpulsiveSurface.copy(alpha = 0.72f),
                start = Offset(size.width * 0.12f, size.height * 0.68f),
                end = Offset(size.width * 0.88f, size.height * 0.68f),
                strokeWidth = 8f,
            )
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.36f)
                    cubicTo(
                        size.width * 0.30f,
                        size.height * 0.20f,
                        size.width * 0.38f,
                        size.height * 0.78f,
                        size.width * 0.54f,
                        size.height * 0.58f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        size.height * 0.46f,
                        size.width * 0.76f,
                        size.height * 0.68f,
                        size.width * 0.88f,
                        size.height * 0.66f,
                    )
                },
                color = ImpulsivePsychological.copy(alpha = 0.78f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f),
            )
            drawCircle(
                color = ImpulsivePhysical.copy(alpha = 0.44f),
                radius = size.minDimension * 0.06f,
                center = Offset(size.width * 0.14f, size.height * 0.36f),
            )
            drawCircle(
                color = ImpulsiveSpiritual.copy(alpha = 0.46f),
                radius = size.minDimension * 0.06f,
                center = Offset(size.width * 0.88f, size.height * 0.66f),
            )
        }
        "calm_task" -> {
            drawRoundRect(
                color = ImpulsiveSurface.copy(alpha = 0.78f),
                topLeft = Offset(size.width * 0.18f, size.height * 0.24f),
                size = Size(size.width * 0.64f, size.height * 0.48f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
            )
            drawCircle(
                color = ImpulsivePhysical.copy(alpha = 0.45f),
                radius = size.minDimension * 0.12f,
                center = Offset(size.width * 0.34f, size.height * 0.48f),
            )
            drawLine(
                color = ImpulsivePsychological.copy(alpha = 0.70f),
                start = Offset(size.width * 0.46f, size.height * 0.48f),
                end = Offset(size.width * 0.66f, size.height * 0.48f),
                strokeWidth = 8f,
            )
            drawRoundRect(
                color = ImpulsivePsychological.copy(alpha = 0.52f),
                topLeft = Offset(size.width * 0.62f, size.height * 0.36f),
                size = Size(size.width * 0.16f, size.height * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
            )
            drawLine(
                color = ImpulsiveText.copy(alpha = 0.42f),
                start = Offset(size.width * 0.66f, size.height * 0.48f),
                end = Offset(size.width * 0.70f, size.height * 0.54f),
                strokeWidth = 5f,
            )
            drawLine(
                color = ImpulsiveText.copy(alpha = 0.42f),
                start = Offset(size.width * 0.70f, size.height * 0.54f),
                end = Offset(size.width * 0.76f, size.height * 0.42f),
                strokeWidth = 5f,
            )
        }
        "task_stack" -> {
            repeat(3) { index ->
                drawRoundRect(
                    color = if (index == 1) ImpulsivePsychological.copy(alpha = 0.58f) else ImpulsiveSurface,
                    topLeft = Offset(size.width * (0.18f + index * 0.16f), size.height * (0.20f + index * 0.12f)),
                    size = Size(size.width * 0.30f, size.height * 0.24f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                )
            }
        }
        "clear_bell" -> {
            drawCircle(color = ImpulsivePsychological.copy(alpha = 0.42f), radius = size.minDimension * 0.22f, center = center)
            drawLine(
                color = ImpulsiveSpiritual.copy(alpha = 0.60f),
                start = Offset(size.width * 0.44f, size.height * 0.35f),
                end = Offset(size.width * 0.56f, size.height * 0.35f),
                strokeWidth = 8f,
            )
        }
        "first_turn" -> drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.14f, size.height * 0.76f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.42f,
                    size.width * 0.50f,
                    size.height * 0.56f,
                    size.width * 0.82f,
                    size.height * 0.28f,
                )
            },
            color = ImpulsivePsychological.copy(alpha = 0.76f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f),
        )
        "door_open" -> {
            drawRoundRect(
                color = ImpulsiveSurface.copy(alpha = 0.80f),
                topLeft = Offset(size.width * 0.28f, size.height * 0.18f),
                size = Size(size.width * 0.44f, size.height * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
            )
            drawCircle(color = ImpulsivePsychological.copy(alpha = 0.40f), radius = size.minDimension * 0.12f, center = Offset(size.width * 0.72f, size.height * 0.30f))
        }
        "steady_gate" -> drawLine(
            color = ImpulsivePhysical.copy(alpha = 0.72f),
            start = Offset(size.width * 0.24f, size.height * 0.74f),
            end = Offset(size.width * 0.76f, size.height * 0.34f),
            strokeWidth = 10f,
        )
        "shortcut_map" -> {
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.72f)
                    cubicTo(
                        size.width * 0.34f,
                        size.height * 0.44f,
                        size.width * 0.48f,
                        size.height * 0.76f,
                        size.width * 0.64f,
                        size.height * 0.50f,
                    )
                    cubicTo(
                        size.width * 0.72f,
                        size.height * 0.38f,
                        size.width * 0.78f,
                        size.height * 0.32f,
                        size.width * 0.84f,
                        size.height * 0.26f,
                    )
                },
                color = ImpulsivePhysical.copy(alpha = 0.54f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f),
            )
            drawLine(
                color = ImpulsivePsychological.copy(alpha = 0.76f),
                start = Offset(size.width * 0.20f, size.height * 0.32f),
                end = Offset(size.width * 0.82f, size.height * 0.66f),
                strokeWidth = 10f,
            )
            listOf(
                Offset(size.width * 0.16f, size.height * 0.72f),
                Offset(size.width * 0.64f, size.height * 0.50f),
                Offset(size.width * 0.84f, size.height * 0.26f),
            ).forEachIndexed { index, point ->
                drawCircle(
                    color = if (index == 1) {
                        ImpulsivePsychological.copy(alpha = 0.58f)
                    } else {
                        ImpulsiveSpiritual.copy(alpha = 0.46f)
                    },
                    radius = size.minDimension * 0.06f,
                    center = point,
                )
            }
        }
        "soft_branches" -> {
            drawLine(color = ImpulsivePsychological.copy(alpha = 0.72f), start = Offset(size.width * 0.18f, size.height * 0.72f), end = Offset(size.width * 0.62f, size.height * 0.42f), strokeWidth = 10f)
            drawLine(color = ImpulsiveSpiritual.copy(alpha = 0.56f), start = Offset(size.width * 0.62f, size.height * 0.42f), end = Offset(size.width * 0.82f, size.height * 0.24f), strokeWidth = 8f)
        }
        "gentle_loop" -> {
            drawCircle(color = ImpulsivePhysical.copy(alpha = 0.42f), radius = size.minDimension * 0.26f, center = center)
            drawCircle(color = ImpulsivePsychological.copy(alpha = 0.54f), radius = size.minDimension * 0.12f, center = Offset(size.width * 0.70f, size.height * 0.34f))
        }
        "private_room" -> drawRoundRect(
            color = ImpulsiveSurface.copy(alpha = 0.70f),
            topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
            size = Size(size.width * 0.68f, size.height * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
        )
        "small_win" -> drawCircle(color = ImpulsivePsychological.copy(alpha = 0.62f), radius = size.minDimension * 0.10f, center = center)
        "calm_finish" -> drawLine(
            color = ImpulsivePsychological.copy(alpha = 0.76f),
            start = Offset(size.width * 0.16f, size.height * 0.72f),
            end = Offset(size.width * 0.84f, size.height * 0.72f),
            strokeWidth = 10f,
        )
        "visible_note" -> drawRoundRect(
            color = ImpulsiveSurface.copy(alpha = 0.76f),
            topLeft = Offset(size.width * 0.30f, size.height * 0.18f),
            size = Size(size.width * 0.40f, size.height * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
        )
        "quiet_setup" -> drawCircle(color = ImpulsivePsychological.copy(alpha = 0.42f), radius = size.minDimension * 0.18f, center = center)
        "visible_route" -> drawLine(
            color = ImpulsivePhysical.copy(alpha = 0.70f),
            start = Offset(size.width * 0.18f, size.height * 0.72f),
            end = Offset(size.width * 0.82f, size.height * 0.34f),
            strokeWidth = 10f,
        )
        else -> drawCircle(color = ImpulsivePsychological.copy(alpha = 0.38f), radius = size.minDimension * 0.18f, center = center)
    }
}

@Composable
private fun RemoteArticleView(
    articleUrl: String,
    onReadConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    loadUrl(articleUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp)),
        )
        Button(
            onClick = onReadConfirmed,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text("I've read it")
        }
    }
}

@Composable
private fun ClosingQuestion(
    article: ResetReadArticle,
    onSelectAnswer: (Int) -> Unit,
) {
    val question = article.closingQuestion
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
                    text = "Reflection",
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
            Surface(
                color = ImpulsiveSurface,
                shape = RoundedCornerShape(24.dp),
                border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clickable { onSelectAnswer(index) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(ImpulsivePsychological.copy(alpha = 0.34f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = ImpulsiveText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = option,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetReadSuccess(
    uiState: ResetReadUiState,
    taskCompletionResult: TaskCompletionResult?,
    onRateHelpfulness: (Int) -> Unit,
    onDone: () -> Unit,
) {
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
            text = "Reset Reading complete",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = selectedActionText(uiState),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resultLabel(taskCompletionResult),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        if (uiState.askHelpfulnessRating) {
            Spacer(modifier = Modifier.height(18.dp))
            ResetReadHelpfulnessRating(
                selectedRating = uiState.helpfulnessRating,
                onRateHelpfulness = onRateHelpfulness,
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onDone,
            enabled = taskCompletionResult != null,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ImpulsiveText,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (taskCompletionResult == null) "Saving" else "Done")
        }
    }
}

@Composable
private fun ResetReadHelpfulnessRating(
    selectedRating: Int?,
    onRateHelpfulness: (Int) -> Unit,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(24.dp),
        border = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Was this read helpful?",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "This only appears sometimes so Impulsive can learn better topics.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..5).forEach { rating ->
                    val selected = selectedRating == rating
                    Surface(
                        color = if (selected) {
                            ImpulsivePsychological.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                ImpulsiveText.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { onRateHelpfulness(rating) },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = rating.toString(),
                                color = if (selected) ImpulsiveText else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Text(
                text = "1 = not helpful   5 = very helpful",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    CenterPanel {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading article",
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

private fun selectedActionText(uiState: ResetReadUiState): String {
    val selected = uiState.selectedOptionIndex
    val option = selected?.let { uiState.article?.closingQuestion?.options?.getOrNull(it) }
    return option?.let { "Next 10 minutes: $it" } ?: "Choose the next helpful action."
}

private fun resultLabel(result: TaskCompletionResult?): String {
    if (result == null) return "Saving reward..."
    val wait = if (result.waitReductionMinutes > 0) {
        "Wait cut by ${result.waitReductionMinutes.formatMinutes()}"
    } else {
        "Window already protected"
    }
    val points = if (result.levelPointsAwarded > 0) {
        "+${result.levelPointsAwarded} LP"
    } else {
        "No extra LP today"
    }
    return "$wait  $points"
}

private fun resetReadDurationLabel(seconds: Int): String {
    return when {
        seconds <= 0 -> ""
        seconds < 60 -> "${seconds}s reset"
        seconds == 90 -> "90 sec reset"
        seconds % 60 == 0 -> "${seconds / 60} min reset"
        else -> "${seconds / 60} min ${seconds % 60}s reset"
    }
}

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
