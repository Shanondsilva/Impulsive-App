package com.impulsive.app.frontend.screens.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Moving
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.R
import com.impulsive.app.backend.data.repository.ResetReadRepository
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.score.ScoreDashboardState
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScorePersonalBest
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreTimelineItem
import com.impulsive.app.backend.domain.model.score.UrgeTrendState
import com.impulsive.app.backend.domain.model.score.WindowUsageState
import com.impulsive.app.backend.domain.model.release.TaperProposal
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.progress.ScoreViewModel
import com.impulsive.app.backend.session.progress.TaperViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.components.BodyModeLockedSheet
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavIndicatorState
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.MindModeStatusSheet
import com.impulsive.app.frontend.components.ModeSelectionSheet
import com.impulsive.app.frontend.components.SoulModeLockedSheet
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ScoreStrongCoral = Color(0xFFEF6F72)
private const val ScoreFlipInitialPauseMs = 15_000L
private const val ScoreFlipDurationMs = 1_050
private const val ScoreFlipPersonalBestHoldMs = 8_000L
private val ScoreFlipCardHeight = 218.dp
private val ScoreFlipControlReservedSpace = 52.dp
private val ScoreFlipHeaderMinHeight = 40.dp
private val ScoreFlipHeaderBadgeSize = 40.dp
private val ScoreFlipHeaderIconSize = 22.dp
private val ResetReadingGreenGlow = Color(0xFF93E9BE)

private data class ScoreScreenColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val text: Color,
    val muted: Color,
    val faintLine: Color,
    val mainCard: Color,
    val mainCardText: Color,
    val selectedPill: Color,
    val unselectedPill: Color,
    val safeExitCard: Color,
    val urgeCard: Color,
    val timelineDot: Color,
    val shadow: Color,
    val lavenderGlow: Color,
    val greenGlow: Color,
    val blueGlow: Color,
    val yellowGlow: Color,
    val coralGlow: Color,
)

private data class ResetReadProgressStats(
    val completedCount: Int,
    val abandonedCount: Int,
    val safeReadingMinutes: Int,
    val helpfulRatingCount: Int,
    val highlyHelpfulCount: Int,
    val averageHelpfulness: Double?,
    val lastCompletedAt: LocalDateTime?,
)

private fun buildResetReadProgressStats(
    sessions: List<ResetReadSessionRecord>,
): ResetReadProgressStats {
    val completedSessions = sessions.filter { it.validCompletion }
    val abandonedCount = sessions.count { !it.validCompletion }
    val safeSeconds = completedSessions.sumOf { session ->
        session.secondsSpent.coerceAtLeast(0)
    }
    val safeReadingMinutes = if (safeSeconds == 0) {
        0
    } else {
        (safeSeconds + 59) / 60
    }
    val helpfulRatings = completedSessions.mapNotNull { it.helpfulnessRating }
    val lastCompletedAt = completedSessions
        .maxByOrNull { it.completedAt }
        ?.completedAt

    return ResetReadProgressStats(
        completedCount = completedSessions.size,
        abandonedCount = abandonedCount,
        safeReadingMinutes = safeReadingMinutes,
        helpfulRatingCount = helpfulRatings.size,
        highlyHelpfulCount = helpfulRatings.count { it >= 4 },
        averageHelpfulness = helpfulRatings
            .takeIf { it.isNotEmpty() }
            ?.average(),
        lastCompletedAt = lastCompletedAt,
    )
}

@Composable
fun ProgressDashboardScreen(
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFocus: () -> Unit = {},
    onOpenSnakeTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenSkylineResetTask: () -> Unit = {},
    onOpenRhythmTilesTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    modifier: Modifier = Modifier,
    indicatorState: BottomNavIndicatorState = rememberBottomNavIndicatorState(),
    isActive: Boolean = true,
    viewModel: ScoreViewModel = viewModel(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    protectionSetupViewModel: ProtectionSetupViewModel = viewModel(),
    taskRewardViewModel: TaskRewardViewModel = viewModel(),
    taperViewModel: TaperViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taperProposal by taperViewModel.proposal.collectAsStateWithLifecycle()
    val colors = rememberScoreColors()
    val context = LocalContext.current
    val resetReadRepository = remember { ResetReadRepository(context) }
    val resetReadSessions by resetReadRepository.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val resetReadProgressStats = remember(resetReadSessions) {
        buildResetReadProgressStats(resetReadSessions)
    }
    val storeManager = remember { com.impulsive.app.backend.data.repository.GameStoreManager(context) }
    val dailyEarned by storeManager.dailyEarned.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lifetimePoints by storeManager.lifetimePoints.collectAsStateWithLifecycle(initialValue = 0)
    var showScoreInfo by remember { mutableStateOf(false) }
    var mindModeSheetVisible by remember { mutableStateOf(false) }
    var modeSelectionSheetVisible by remember { mutableStateOf(false) }
    var bodyModeSheetVisible by remember { mutableStateOf(false) }
    var soulModeSheetVisible by remember { mutableStateOf(false) }
    val bottomNavReservedSpace = 104.dp
    val currentNow = LocalDateTime.now()
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = onboardingState.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(onboardingState.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(onboardingState.answers.activeDayEndMinute),
    )
    val taskRewardState = taskRewardStoreState.toTaskRewardState(releasePlan)
    val startRecommendedMindTask = {
        when (taskRewardState.recommendedTaskType) {
            PsychologyTaskType.Snake -> onOpenSnakeTask()
            // Legacy task data routes to the active game.
            PsychologyTaskType.ReflexOverride -> onOpenSnakeTask()
            PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
            PsychologyTaskType.SkylineReset -> onOpenSkylineResetTask()
            PsychologyTaskType.RhythmTiles -> onOpenRhythmTilesTask()
            PsychologyTaskType.ResetRead -> onOpenResetReadTask()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ImpulsiveAmbientBackground(
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = bottomNavReservedSpace + 40.dp),
        ) {
            ScoreHeader(
                colors = colors,
                onInfoClick = { showScoreInfo = true },
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column {
                Text(
                    text = "Total control points",
                    color = colors.muted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lifetimePoints.formatNumber(),
                    color = colors.text,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            MainControlScoreCard(
                uiState = uiState,
                colors = colors,
                dailyEarned = dailyEarned,
            )

            Spacer(modifier = Modifier.height(28.dp))

            ScoreFilter(
                selectedRange = uiState.selectedRange,
                colors = colors,
                onRangeSelected = viewModel::selectRange,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScoreFlipCardHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScoreRecordsCard(
                    personalBests = uiState.personalBests,
                    recentSessions = uiState.recentSessions,
                    colors = colors,
                    isActive = isActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                ResetReadingProgressCard(
                    stats = resetReadProgressStats,
                    colors = colors,
                    isActive = isActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            taperProposal?.let { proposal ->
                TaperProposalCard(
                    proposal = proposal,
                    onAccept = { taperViewModel.acceptProposal(proposal) },
                    onKeepCurrent = { taperViewModel.declineProposal() },
                    onNeverAsk = { taperViewModel.disableProposals() },
                    colors = colors,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            SafeExitAndUrgeCards(
                uiState = uiState,
                colors = colors,
            )
        }

        if (showScoreInfo) {
            ScoreInfoDialog(
                colors = colors,
                onDismiss = { showScoreInfo = false },
            )
        }

        if (mindModeSheetVisible) {
            MindModeStatusSheet(
                onDismissRequest = { mindModeSheetVisible = false },
                onStartMindTask = startRecommendedMindTask,
                onViewProgress = { mindModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (bodyModeSheetVisible) {
            BodyModeLockedSheet(
                onDismissRequest = { bodyModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (soulModeSheetVisible) {
            SoulModeLockedSheet(
                onDismissRequest = { soulModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (modeSelectionSheetVisible) {
            ModeSelectionSheet(
                onDismissRequest = { modeSelectionSheetVisible = false },
                onOpenMindMode = {
                    mindModeSheetVisible = true
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = false
                },
                onOpenBodyMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = true
                    soulModeSheetVisible = false
                },
                onOpenSoulMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = true
                },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        BottomNavBar(
            selected = if (
                modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible
            ) {
                BottomNavItem.Trigger
            } else {
                BottomNavItem.Progress
            },
            onSelect = { item ->
                when (item) {
                    BottomNavItem.Home -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenHome()
                    }
                    BottomNavItem.Trigger -> {
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        modeSelectionSheetVisible = !modeSelectionSheetVisible
                    }
                    BottomNavItem.Settings -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenSettings()
                    }
                    BottomNavItem.Progress -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                    }
                    BottomNavItem.Focus -> {
                        modeSelectionSheetVisible = false
                        onOpenFocus()
                    }
                }
            },
            onLongSelect = { item ->
                if (item == BottomNavItem.Trigger) {
                    modeSelectionSheetVisible = !modeSelectionSheetVisible
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            settingsBadgeVisible = protectionSetupState.profileBadgeShouldShow,
            modeSelectorOpen = modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible,
            indicatorState = indicatorState,
            isActive = isActive,
        )
    }
}

@Composable
private fun rememberScoreColors(): ScoreScreenColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        val darkBackground = Color(0xFF11161A)
        val darkSurface = Color(0xFF171D22)
        val darkElevatedSurface = Color(0xFF202832)
        val darkText = Color(0xFFF7F2FF)
        val darkMuted = Color(0xFFC9C0D8)

        ScoreScreenColors(
            background = darkBackground,
            surface = darkSurface,
            elevatedSurface = darkElevatedSurface,
            text = darkText,
            muted = darkMuted,
            faintLine = darkText.copy(alpha = 0.14f),
            mainCard = Color(0xFF171D22),
            mainCardText = darkText,
            selectedPill = Color(0xFF6E5A96),
            unselectedPill = Color(0xFF1D2526),
            safeExitCard = Color(0xFF171D22),
            urgeCard = Color(0xFF171D22),
            timelineDot = Color(0xFF202832),
            shadow = Color.Black,
            lavenderGlow = ImpulsivePsychological,
            greenGlow = ImpulsiveOverallTheme,
            blueGlow = ImpulsivePhysical,
            yellowGlow = ImpulsiveSpiritual,
            coralGlow = ScoreStrongCoral,
        )
    } else {
        ScoreScreenColors(
            background = Color(0xFFFBF8FE),
            surface = Color(0xFFFFFFFF),
            elevatedSurface = Color(0xFFF7F1F8),
            text = Color(0xFF2F2637),
            muted = Color(0xFF706777),
            faintLine = Color.Transparent,
            mainCard = Color(0xFFE9E2F7),
            mainCardText = Color(0xFF2E2540),
            selectedPill = ImpulsivePsychological,
            unselectedPill = Color(0xFFF2ECF3),
            safeExitCard = Color(0xFFE9E2F7),
            urgeCard = Color(0xFFE9E2F7),
            timelineDot = Color(0xFFF4F0F7),
            shadow = Color(0xFF2F2637),
            lavenderGlow = ImpulsivePsychological,
            greenGlow = ImpulsiveOverallTheme,
            blueGlow = ImpulsivePhysical,
            yellowGlow = ImpulsiveSpiritual,
            coralGlow = ScoreStrongCoral,
        )
    }
}

@Composable
private fun ScoreHeader(
    colors: ScoreScreenColors,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Score",
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onInfoClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "About Score",
                tint = colors.text.copy(alpha = 0.76f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ScoreInfoDialog(
    colors: ScoreScreenColors,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ImpulsivePsychological,
                ),
            ) {
                Text("Got it")
            }
        },
        title = {
            Text(
                text = "About Score",
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Your control progress, built from Notice, Pivot and Understand actions.",
                    color = colors.text.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )

                ScoreInfoItem(
                    title = "Total control points",
                    body = "Your lifetime control points from valid recovery actions, pivot games, Focus progress, and other completed supports.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Score card",
                    body = "Shows your current level progress and how your points are building across the selected time range.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Range filter",
                    body = "Switches the score view between the available time ranges so the chart and cards match the period you are checking.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Personal Best",
                    body = "Highlights your strongest pivot-game record and compares it with your recent session so progress feels self-vs-self.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Reset Reading",
                    body = "Tracks valid reading resets, safe reading time, helpful ratings, and abandoned reading attempts.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Safe Exit",
                    body = "Shows how often you chose the safer exit after a difficult moment and your best safe-exit streak.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Planned Moments",
                    body = "Compares used and skipped planned moments so the app can show whether the release plan is becoming easier to follow.",
                    colors = colors,
                )

                ScoreInfoItem(
                    title = "Difficult Moment Trend",
                    body = "Shows whether difficult moments are rising or reducing compared with your baseline pattern.",
                    colors = colors,
                )
            }
        },
    )
}

@Composable
private fun ScoreInfoItem(
    title: String,
    body: String,
    colors: ScoreScreenColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = colors.text.copy(alpha = 0.74f),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
        )
    }
}

@Composable
private fun MainControlScoreCard(
    uiState: ScoreDashboardState,
    colors: ScoreScreenColors,
    dailyEarned: Map<LocalDate, Int>,
) {
    val isDark = colors.background.luminance() < 0.5f
    val trendAccent = colors.coralGlow

    Surface(
        color = colors.mainCard,
        shape = RoundedCornerShape(34.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = colors.lavenderGlow.copy(alpha = 0.72f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 18.dp else 10.dp,
                shape = RoundedCornerShape(34.dp),
                clip = false,
                ambientColor = if (isDark) {
                    colors.lavenderGlow.copy(alpha = 0.24f)
                } else {
                    colors.greenGlow.copy(alpha = 0.12f)
                },
                spotColor = if (isDark) {
                    colors.lavenderGlow.copy(alpha = 0.16f)
                } else {
                    colors.greenGlow.copy(alpha = 0.10f)
                },
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (isDark) colors.lavenderGlow.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "LEVEL ${uiState.currentLevel}",
                        color = colors.mainCardText.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }

                Text(
                    text = "${uiState.pointsUntilNextLevel} LP left",
                    color = colors.mainCardText.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Score card",
                        color = colors.mainCardText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Points earned",
                        color = colors.mainCardText.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = trendAccent.copy(alpha = if (isDark) 0.16f else 0.28f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = if (isDark) trendAccent else colors.mainCardText.copy(alpha = 0.74f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            ScoreCardChart(
                dailyEarned = dailyEarned,
                sessions = uiState.recentSessions,
                range = uiState.selectedRange,
                accent = trendAccent,
                colors = colors,
            )
        }
    }
}

private data class ScoreChartBar(val label: String, val value: Int, val detail: String, val games: Int)

private fun buildScoreChartBars(
    dailyEarned: Map<LocalDate, Int>,
    sessions: List<ScoreTimelineItem>,
    range: ScoreRange,
): List<ScoreChartBar> {
    return when (range) {
        ScoreRange.Today,
        ScoreRange.Week -> {
            val today = LocalDate.now()
            val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
            val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
            val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            (0..6).map { index ->
                val date = sunday.plusDays(index.toLong())
                val points = dailyEarned[date] ?: 0
                val games = sessions.count { it.completedAt.toLocalDate() == date }
                val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                ScoreChartBar(
                    label = dayLetters[index],
                    value = points,
                    detail = "$dayName, ${date.format(monthDayFormatter)}",
                    games = games,
                )
            }
        }
        ScoreRange.Month -> {
            val today = LocalDate.now()
            val yearMonth = YearMonth.from(today)
            val monthEnd = yearMonth.lengthOfMonth()
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            listOf(
                Triple("W1", 1, 7),
                Triple("W2", 8, 14),
                Triple("W3", 15, 21),
                Triple("W4", 22, monthEnd),
            ).mapIndexed { index, (label, startDay, endDay) ->
                val start = yearMonth.atDay(startDay)
                val end = yearMonth.atDay(endDay)
                val points = dailyEarned.entries
                    .filter { !it.key.isBefore(start) && !it.key.isAfter(end) }
                    .sumOf { it.value }
                val games = sessions.count {
                    val completedDate = it.completedAt.toLocalDate()
                    !completedDate.isBefore(start) && !completedDate.isAfter(end)
                }
                ScoreChartBar(
                    label = label,
                    value = points,
                    detail = "Week ${index + 1}, ${start.format(formatter)} - ${end.format(formatter)}",
                    games = games,
                )
            }
        }
        ScoreRange.Year -> {
            val year = LocalDate.now().year
            val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
            (1..12).map { month ->
                val points = dailyEarned.entries
                    .filter { it.key.year == year && it.key.monthValue == month }
                    .sumOf { it.value }
                val games = sessions.count { it.completedAt.year == year && it.completedAt.monthValue == month }
                val monthName = java.time.Month.of(month)
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                ScoreChartBar(label = monthLetters[month - 1], value = points, detail = monthName, games = games)
            }
        }
    }
}

@Composable
private fun ScoreCardChart(
    dailyEarned: Map<LocalDate, Int>,
    sessions: List<ScoreTimelineItem>,
    range: ScoreRange,
    accent: Color,
    colors: ScoreScreenColors,
) {
    val bars = remember(dailyEarned, sessions, range) { buildScoreChartBars(dailyEarned, sessions, range) }
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val maxValue = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    // One height multiplier per bar: 0 -> 1.08 (rise with slight overshoot)
    // -> 0.96 (dip) -> 1.0 (settle on the exact real value). Keyed on bars so
    // the animation replays when the data or selected range changes.
    val barAnimations = remember(bars) { bars.map { Animatable(0f) } }
    LaunchedEffect(bars) {
        barAnimations.forEachIndexed { index, animatable ->
            launch {
                delay(index.coerceAtMost(10) * 45L)
                animatable.animateTo(1.08f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
                animatable.animateTo(0.96f, tween(durationMillis = 120))
                animatable.animateTo(1f, tween(durationMillis = 180))
            }
        }
    }
    val selectedBar = selected?.let { bars.getOrNull(it) }
    val fallbackPrompt = when (range) {
        ScoreRange.Today -> "Tap a bar to see details"
        ScoreRange.Week -> "Tap a bar to see that day"
        ScoreRange.Month -> "Tap a bar to see that week"
        ScoreRange.Year -> "Tap a bar to see that month"
    }

    Column {
        Text(
            text = selectedBar?.let { "${it.detail}: ${it.value} points - ${it.games} games" }
                ?: fallbackPrompt,
            color = colors.mainCardText.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, bar ->
                val isSelected = selected == index
                val fraction = bar.value.toFloat() / maxValue.toFloat()
                val animatedFraction = fraction * (barAnimations.getOrNull(index)?.value ?: 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selected = if (isSelected) null else index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(animatedFraction.coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isSelected) {
                                        accent
                                    } else {
                                        accent.copy(alpha = 0.42f)
                                    },
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bar.label,
                        color = colors.mainCardText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreFilter(
    selectedRange: ScoreRange,
    colors: ScoreScreenColors,
    onRangeSelected: (ScoreRange) -> Unit,
) {
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.unselectedPill,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isDark) colors.lavenderGlow.copy(alpha = 0.12f) else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(ScoreRange.Week, ScoreRange.Month, ScoreRange.Year).forEach { range ->
                val selected = selectedRange == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.selectedPill else Color.Transparent)
                        .clickable { onRangeSelected(range) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = range.label,
                        color = if (selected) colors.text else colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreIconBadge(
    icon: ImageVector,
    accentColor: Color,
    colors: ScoreScreenColors,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val isDark = colors.background.luminance() < 0.5f
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = accentColor.copy(alpha = if (isDark) 0.18f else 0.24f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) accentColor else colors.text.copy(alpha = 0.74f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ScoreRecordsCard(
    personalBests: List<ScorePersonalBest>,
    recentSessions: List<ScoreTimelineItem>,
    colors: ScoreScreenColors,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val topBest = personalBests
        .filter { it.hasRecord }
        .maxByOrNull { it.bestScore }
    val latestCompleted = recentSessions.firstOrNull {
        it.outcome != ScoreSessionOutcome.Abandoned
    }
    val accent = topBest?.gameType?.accentColor() ?: colors.lavenderGlow
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val reducedMotion = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val touchExplorationEnabled = remember(context) {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
    }
    var lifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleResumed =
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showingBack by rememberSaveable { mutableStateOf(false) }
    var autoCycleCompleted by rememberSaveable { mutableStateOf(false) }
    var interactionGeneration by rememberSaveable { mutableIntStateOf(0) }
    var isFlipping by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(if (showingBack) 180f else 0f) }
    val frontVisible = rotation.value < 90f
    val automaticFlipEnabled =
        isActive && lifecycleResumed && !reducedMotion && !touchExplorationEnabled

    LaunchedEffect(automaticFlipEnabled, autoCycleCompleted, interactionGeneration) {
        if (!automaticFlipEnabled || autoCycleCompleted) return@LaunchedEffect
        delay(ScoreFlipInitialPauseMs)
        try {
            isFlipping = true
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(
                    durationMillis = ScoreFlipDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            showingBack = true
            isFlipping = false
            delay(ScoreFlipPersonalBestHoldMs)
            isFlipping = true
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ScoreFlipDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            showingBack = false
            autoCycleCompleted = true
        } finally {
            val resolvedBack = rotation.value >= 90f
            rotation.snapTo(if (resolvedBack) 180f else 0f)
            showingBack = resolvedBack
            isFlipping = false
        }
    }

    val actionLabel = stringResource(
        if (frontVisible) R.string.v28_show_recent_session else R.string.v28_show_personal_best,
    )
    val requestManualFlip = {
        if (!isFlipping) {
            isFlipping = true
            interactionGeneration += 1
            autoCycleCompleted = true
            val target = if (frontVisible) 180f else 0f
            scope.launch {
                try {
                    if (reducedMotion) {
                        rotation.snapTo(target)
                    } else {
                        rotation.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = ScoreFlipDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    showingBack = target == 180f
                } finally {
                    isFlipping = false
                }
            }
        }
    }
    val cardShape = RoundedCornerShape(30.dp)
    val isDark = colors.background.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScoreFlipCardHeight)
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor = accent.copy(alpha = 0.10f),
            ),
    ) {
        ScoreFlipFaceSurface(
            eyebrow = stringResource(R.string.v28_personal_best_eyebrow),
            title = topBest?.gameType?.displayName,
            score = topBest?.bestScore,
            supporting = null,
            emptyMessage = stringResource(R.string.v28_personal_best_empty),
            icon = Icons.Outlined.EmojiEvents,
            iconContentDescription = "Personal best",
            colors = colors,
            isDark = isDark,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 24f * density
                    alpha = if (rotation.value <= 90f) 1f else 0f
                },
        )
        ScoreFlipFaceSurface(
            eyebrow = stringResource(R.string.v28_recent_session_eyebrow),
            title = latestCompleted?.gameName,
            score = latestCompleted?.score,
            supporting = latestCompleted?.completedAt?.relativeLabel(),
            emptyMessage = stringResource(R.string.v28_recent_session_empty),
            icon = Icons.Outlined.History,
            iconContentDescription = "Recent session",
            colors = colors,
            isDark = isDark,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value - 180f
                    cameraDistance = 24f * density
                    alpha = if (rotation.value >= 90f) 1f else 0f
                },
        )
        ScoreFlipActionButton(
            actionLabel = actionLabel,
            enabled = !isFlipping,
            onClick = requestManualFlip,
            tint = colors.muted,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ScoreFlipFaceSurface(
    eyebrow: String,
    title: String?,
    score: Int?,
    supporting: String?,
    emptyMessage: String,
    icon: ImageVector,
    iconContentDescription: String,
    colors: ScoreScreenColors,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colors.mainCard,
        shape = RoundedCornerShape(30.dp),
        border = if (isDark) {
            BorderStroke(1.dp, colors.lavenderGlow.copy(alpha = 0.58f))
        } else {
            null
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            ScoreFlipHeader(
                icon = icon,
                iconContentDescription = iconContentDescription,
                eyebrow = eyebrow,
                colors = colors,
                isDark = isDark,
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (score == null) {
                Text(
                    text = emptyMessage,
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                title?.let {
                    Text(
                        text = it,
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }
                Text(
                    text = stringResource(
                        R.string.v28_score_points,
                        score.formatNumber(),
                    ),
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supporting?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreFlipHeader(
    icon: ImageVector,
    iconContentDescription: String,
    eyebrow: String,
    colors: ScoreScreenColors,
    isDark: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = ScoreFlipHeaderMinHeight,
                )
                .padding(end = ScoreFlipControlReservedSpace),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(


                        ScoreFlipHeaderBadgeSize,
                    )
                    .background(
                        color = if (isDark) {
                            colors.surface.copy(alpha = 0.72f)
                        } else {
                            colors.elevatedSurface.copy(alpha = 0.84f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = colors.muted,
                    modifier =
                        Modifier.size(
                            ScoreFlipHeaderIconSize,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = eyebrow,
            color = colors.muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ScoreFlipActionButton(
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.SyncAlt,
            contentDescription = actionLabel,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PersonalBestsSection(
    personalBests: List<ScorePersonalBest>,
    colors: ScoreScreenColors,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreIconBadge(
                icon = Icons.Filled.MilitaryTech,
                accentColor = colors.lavenderGlow,
                colors = colors,
                size = 30.dp,
                iconSize = 16.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Personal Bests",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            personalBests.forEach { best ->
                PersonalBestCard(
                    best = best,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun PersonalBestCard(
    best: ScorePersonalBest,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = best.gameType.accentColor()
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(22.dp),
        border = if (isDark) BorderStroke(1.dp, colors.lavenderGlow.copy(alpha = 0.62f)) else null,
        modifier = Modifier
            .width(164.dp)
            .shadow(
                elevation = if (isDark) 12.dp else 0.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.12f) else Color.Transparent,
                spotColor = if (isDark) accent.copy(alpha = 0.08f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreIconBadge(
                    icon = best.gameType.scoreIcon(),
                    accentColor = accent,
                    colors = colors,
                    size = 30.dp,
                    iconSize = 16.dp,
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = best.gameType.displayName,
                    color = colors.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (best.hasRecord) best.bestScore.formatNumber() else "-",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Best Score",
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Prev: ${best.previousScore?.formatNumber() ?: "-"}",
                    color = colors.text.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = best.changeFromPrevious?.formatChange().orEmpty(),
                    color = colors.text.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TaperProposalCard(
    proposal: TaperProposal,
    onAccept: () -> Unit,
    onKeepCurrent: () -> Unit,
    onNeverAsk: () -> Unit,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = colors.greenGlow
    val contentColor = if (isDark) colors.text else Color(0xFF5C4A7D)

    Surface(
        color = colors.safeExitCard,
        shape = RoundedCornerShape(24.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = colors.lavenderGlow.copy(alpha = 0.72f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 16.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.24f) else Color(0xFF5C4A7D).copy(alpha = 0.08f),
                spotColor = if (isDark) accent.copy(alpha = 0.16f) else Color(0xFF5C4A7D).copy(alpha = 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isDark) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.32f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (isDark) accent else contentColor.copy(alpha = 0.78f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ready for one less?",
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "You have been using fewer moments than planned for two weeks. " +
                    "You can move from ${proposal.fromCount} to ${proposal.toCount} planned moments a day. " +
                    "Nothing changes unless you choose it.",
                color = contentColor.copy(alpha = if (isDark) 0.78f else 0.74f),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onKeepCurrent) {
                    Text(
                        text = "Not yet",
                        color = contentColor.copy(alpha = if (isDark) 0.72f else 0.66f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                TextButton(onClick = onAccept) {
                    Text(
                        text = "Move to ${proposal.toCount} a day",
                        color = if (isDark) accent else contentColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            TextButton(
                onClick = onNeverAsk,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "Don't ask again",
                    color = contentColor.copy(alpha = if (isDark) 0.55f else 0.50f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ResetReadingProgressCard(
    stats: ResetReadProgressStats,
    colors: ScoreScreenColors,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = colors.background.luminance() < 0.5f
    val lastCompletedLabel = stats.lastCompletedAt?.relativeLabel() ?: "Not yet"
    val flipContent = buildResetReadingFlipContent(
        lastCompletedValue = lastCompletedLabel,
        helpfulValue = stats.highlyHelpfulCount.formatNumber(),
        completedValue = stats.completedCount.formatNumber(),
        abandonedValue = stats.abandonedCount.formatNumber(),
    )
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val reducedMotion = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val touchExplorationEnabled = remember(context) {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
    }
    var lifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleResumed =
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showingBack by rememberSaveable { mutableStateOf(false) }
    var autoCycleCompleted by rememberSaveable { mutableStateOf(false) }
    var interactionGeneration by rememberSaveable { mutableIntStateOf(0) }
    var isFlipping by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(if (showingBack) 180f else 0f) }
    val frontVisible = rotation.value < 90f
    val automaticFlipEnabled =
        isActive && lifecycleResumed && !reducedMotion && !touchExplorationEnabled

    LaunchedEffect(automaticFlipEnabled, autoCycleCompleted, interactionGeneration) {
        if (!automaticFlipEnabled || autoCycleCompleted) return@LaunchedEffect
        delay(ScoreFlipInitialPauseMs)
        try {
            isFlipping = true
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(
                    durationMillis = ScoreFlipDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            showingBack = true
            isFlipping = false
            delay(ScoreFlipPersonalBestHoldMs)
            isFlipping = true
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ScoreFlipDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            showingBack = false
            autoCycleCompleted = true
        } finally {
            val resolvedBack = rotation.value >= 90f
            rotation.snapTo(if (resolvedBack) 180f else 0f)
            showingBack = resolvedBack
            isFlipping = false
        }
    }

    val requestManualFlip = {
        if (!isFlipping) {
            isFlipping = true
            interactionGeneration += 1
            autoCycleCompleted = true
            val target = if (frontVisible) 180f else 0f
            scope.launch {
                try {
                    if (reducedMotion) {
                        rotation.snapTo(target)
                    } else {
                        rotation.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = ScoreFlipDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    showingBack = target == 180f
                } finally {
                    isFlipping = false
                }
            }
        }
    }
    val cardShape = RoundedCornerShape(30.dp)
    val accent = ResetReadingGreenGlow
    val actionLabel = if (frontVisible) {
        "Show Reset Reading details"
    } else {
        "Show Reset Reading summary"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScoreFlipCardHeight)
            .shadow(
                elevation = if (isDark) 18.dp else 2.dp,
                shape = cardShape,
                clip = false,
                ambientColor = accent.copy(alpha = if (isDark) 0.22f else 0.08f),
                spotColor = accent.copy(alpha = if (isDark) 0.16f else 0.08f),
            ),
    ) {
        ResetReadingFlipFaceSurface(
            eyebrow = "RESET READING",
            firstMetric = flipContent.front.firstValue,
            firstLabel = flipContent.front.firstLabel,
            secondMetric = flipContent.front.secondValue,
            secondLabel = flipContent.front.secondLabel,
            colors = colors,
            isDark = isDark,
            accent = accent,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 24f * density
                    alpha = if (rotation.value <= 90f) 1f else 0f
                },
        )
        ResetReadingFlipFaceSurface(
            eyebrow = "RESET READING",
            firstMetric = flipContent.back.firstValue,
            firstLabel = flipContent.back.firstLabel,
            secondMetric = flipContent.back.secondValue,
            secondLabel = flipContent.back.secondLabel,
            colors = colors,
            isDark = isDark,
            accent = accent,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value - 180f
                    cameraDistance = 24f * density
                    alpha = if (rotation.value >= 90f) 1f else 0f
                },
        )
        ScoreFlipActionButton(
            actionLabel = actionLabel,
            enabled = !isFlipping,
            onClick = requestManualFlip,
            tint = if (isDark) accent.copy(alpha = 0.88f) else colors.muted,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ResetReadingFlipFaceSurface(
    eyebrow: String,
    firstMetric: String,
    firstLabel: String,
    secondMetric: String,
    secondLabel: String,
    colors: ScoreScreenColors,
    isDark: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colors.elevatedSurface,
        shape = RoundedCornerShape(30.dp),
        border = if (isDark) {
            BorderStroke(1.dp, accent.copy(alpha = 0.58f))
        } else {
            null
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = ScoreFlipHeaderMinHeight,
                        )
                        .padding(end = ScoreFlipControlReservedSpace),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                ScoreFlipHeaderBadgeSize,
                            )
                            .background(
                                color = if (isDark) {
                                    accent.copy(alpha = 0.30f)
                                } else {
                                    accent.copy(alpha = 0.24f)
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = if (isDark) accent else colors.text.copy(alpha = 0.74f),
                            modifier =
                                Modifier.size(
                                    ScoreFlipHeaderIconSize,
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = eyebrow,
                    color = if (isDark) accent else colors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
            }

            ResetReadingCompactMetric(
                value = firstMetric,
                label = firstLabel,
                colors = colors,
            )

            ResetReadingCompactMetric(
                value = secondMetric,
                label = secondLabel,
                colors = colors,
            )
        }
    }
}

@Composable
private fun ResetReadingCompactMetric(
    value: String,
    label: String,
    colors: ScoreScreenColors,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = value,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = colors.muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResetReadMetricPill(
    label: String,
    value: String,
    detail: String,
    colors: ScoreScreenColors,
    modifier: Modifier = Modifier,
) {
    val isDark = colors.background.luminance() < 0.5f

    Surface(
        color = if (isDark) {
            colors.surface.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = 0.46f)
        },
        shape = RoundedCornerShape(22.dp),
        border = if (isDark) {
            BorderStroke(1.dp, colors.faintLine.copy(alpha = 0.42f))
        } else {
            null
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                color = colors.muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                color = colors.muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SafeExitAndUrgeCards(
    uiState: ScoreDashboardState,
    colors: ScoreScreenColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactSafeExitCard(
            walkAwayCount = uiState.safeExitCount,
            bestStreak = uiState.bestSafeExitStreak,
            rangeSuffix = uiState.selectedRange.metricSuffix(),
            colors = colors,
        )
        WindowUsageCard(
            usage = uiState.windowUsage,
            rangeSuffix = uiState.selectedRange.metricSuffix(),
            colors = colors,
        )
        UrgeTrendCard(
            trend = uiState.urgeTrend,
            colors = colors,
        )
    }
}

@Composable
private fun CompactSafeExitCard(
    walkAwayCount: Int,
    bestStreak: Int,
    rangeSuffix: String,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = colors.greenGlow
    val contentColor = if (isDark) colors.text else Color(0xFF5C4A7D)

    Surface(
        color = colors.safeExitCard,
        shape = RoundedCornerShape(24.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = colors.lavenderGlow.copy(alpha = 0.72f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 16.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.24f) else Color(0xFF5C4A7D).copy(alpha = 0.08f),
                spotColor = if (isDark) accent.copy(alpha = 0.16f) else Color(0xFF5C4A7D).copy(alpha = 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isDark) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.32f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isDark) accent else contentColor.copy(alpha = 0.78f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Safe Exit",
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Best streak: ${bestStreak.formatNumber()}",
                    color = contentColor.copy(alpha = if (isDark) 0.76f else 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = walkAwayCount.formatNumber(),
                    color = contentColor,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Walk aways $rangeSuffix",
                    color = contentColor.copy(alpha = if (isDark) 0.76f else 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun WindowUsageCard(
    usage: WindowUsageState,
    rangeSuffix: String,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = colors.blueGlow
    val contentColor = if (isDark) colors.text else Color(0xFF1E3A52)

    Surface(
        color = colors.urgeCard,
        shape = RoundedCornerShape(24.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = colors.lavenderGlow.copy(alpha = 0.52f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 12.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.18f) else Color.Transparent,
                spotColor = if (isDark) accent.copy(alpha = 0.10f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isDark) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (isDark) accent else contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Planned Moments",
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (usage.plannedPerDay > 0) "Plan: ${usage.plannedPerDay}/day" else "No plan set",
                    color = contentColor.copy(alpha = if (isDark) 0.72f else 0.64f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!usage.hasData) {
                Text(
                    text = "No moment data yet",
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Usage appears after your next planned window passes.",
                    color = contentColor.copy(alpha = if (isDark) 0.68f else 0.60f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text = usage.rangeUsed.formatNumber(),
                            color = contentColor,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Used $rangeSuffix",
                            color = contentColor.copy(alpha = if (isDark) 0.76f else 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = usage.rangeSkipped.formatNumber(),
                            color = contentColor,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Skipped $rangeSuffix",
                            color = contentColor.copy(alpha = if (isDark) 0.76f else 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UrgeTrendCard(
    trend: UrgeTrendState,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = if (!trend.hasData || trend.difference <= 0) colors.greenGlow else colors.coralGlow
    val contentColor = if (isDark) colors.text else Color(0xFF2C1F4A)

    Surface(
        color = colors.urgeCard,
        shape = RoundedCornerShape(24.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = colors.lavenderGlow.copy(alpha = 0.52f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 12.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.18f) else Color.Transparent,
                spotColor = if (isDark) accent.copy(alpha = 0.10f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isDark) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isDark) accent else contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Difficult Moment Trend",
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (trend.baselinePerDay > 0) "Baseline ${trend.baselinePerDay}/day" else "No baseline set",
                    color = contentColor.copy(alpha = if (isDark) 0.72f else 0.64f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!trend.hasData) {
                Text(
                    text = trend.label,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your trend appears after support checks or app/browser pauses.",
                    color = contentColor.copy(alpha = if (isDark) 0.68f else 0.60f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = trend.label,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                val maxVal = (trend.bars.maxOfOrNull { maxOf(it.actual, it.baseline) } ?: 1).coerceAtLeast(1)
                val lineColor = accent
                val baselineColor = contentColor.copy(alpha = if (isDark) 0.40f else 0.34f)
                val points = trend.bars
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    val count = points.size
                    if (count == 0) return@Canvas
                    val topPad = 6f
                    val bottomPad = 6f
                    val usableHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val stepX = if (count > 1) size.width / (count - 1) else 0f

                    fun yFor(value: Int): Float =
                        topPad + usableHeight * (1f - (value.toFloat() / maxVal.toFloat()))

                    fun xFor(index: Int): Float =
                        if (count > 1) stepX * index else size.width / 2f

                    // Dashed baseline reference.
                    if (trend.baselinePerDay > 0) {
                        val baselineY = yFor(trend.baselinePerDay.coerceAtMost(maxVal))
                        drawLine(
                            color = baselineColor,
                            start = Offset(0f, baselineY),
                            end = Offset(size.width, baselineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                            ),
                        )
                    }

                    // Build the actual-values line path.
                    val linePath = Path()
                    points.forEachIndexed { index, bar ->
                        val x = xFor(index)
                        val y = yFor(bar.actual)
                        if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }

                    // Soft area fill under the line.
                    if (count > 1) {
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(xFor(count - 1), size.height - bottomPad)
                            lineTo(xFor(0), size.height - bottomPad)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            color = lineColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                        )
                    }

                    // The line itself.
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )

                    // Point dots.
                    points.forEachIndexed { index, bar ->
                        drawCircle(
                            color = lineColor,
                            radius = 2.5.dp.toPx(),
                            center = Offset(xFor(index), yFor(bar.actual)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryMetricCard(
    title: String,
    mainValue: String,
    label: String,
    footer: String,
    icon: ImageVector,
    cardColor: Color,
    accentColor: Color,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val contentColor = if (isDark) colors.text else Color(0xFF334042)
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(28.dp),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.38f),
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 14.dp else 0.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = if (isDark) accentColor.copy(alpha = 0.14f) else Color.Transparent,
                spotColor = if (isDark) accentColor.copy(alpha = 0.08f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ScoreIconBadge(
                    icon = icon,
                    accentColor = cardColor.takeIf { cardColor.luminance() >= 0.5f } ?: colors.lavenderGlow,
                    colors = colors,
                    size = 34.dp,
                    iconSize = 18.dp,
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = mainValue,
                color = contentColor,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = contentColor.copy(alpha = if (isDark) 0.76f else 0.70f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (isDark) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.18f)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = if (isDark) 0.95f else 0.78f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = footer,
                    color = contentColor.copy(alpha = if (isDark) 0.78f else 0.74f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RecentSessionsTimeline(
    items: List<ScoreTimelineItem>,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) colors.lavenderGlow.copy(alpha = 0.24f) else colors.faintLine.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 14.dp else 0.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = if (isDark) colors.lavenderGlow.copy(alpha = 0.08f) else Color.Transparent,
                spotColor = if (isDark) colors.greenGlow.copy(alpha = 0.04f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreIconBadge(
                    icon = Icons.Filled.Timeline,
                    accentColor = colors.lavenderGlow,
                    colors = colors,
                    size = 32.dp,
                    iconSize = 17.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recent sessions",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (items.isEmpty()) {
                EmptyTimelineState(colors = colors)
            } else {
                items.forEachIndexed { index, item ->
                    TimelineRow(
                        item = item,
                        showLineBelow = index != items.lastIndex,
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineState(colors: ScoreScreenColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.selectedPill.copy(alpha = 0.34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ShowChart,
                contentDescription = null,
                tint = colors.text.copy(alpha = 0.68f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No score sessions yet",
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Complete a pivot game to build this card.",
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimelineRow(
    item: ScoreTimelineItem,
    showLineBelow: Boolean,
    colors: ScoreScreenColors,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(30.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(colors.timelineDot, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.gameIcon(),
                    contentDescription = null,
                    tint = item.gameAccentColor().copy(alpha = 0.92f),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showLineBelow) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(58.dp)
                        .background(colors.faintLine),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showLineBelow) 14.dp else 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.gameName,
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.completedAt.relativeLabel(),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Score: ${item.score.formatNumber()}, Intensity: ${item.urgeLabel()}",
                color = colors.text.copy(alpha = 0.70f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(7.dp))
            OutcomePill(
                outcome = item.outcome,
                colors = colors,
            )
        }
    }
}

@Composable
private fun OutcomePill(
    outcome: ScoreSessionOutcome,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val pillColor = when (outcome) {
        ScoreSessionOutcome.WalkedAway -> if (isDark) colors.greenGlow.copy(alpha = 0.16f) else Color(0xFFF2E9FB)
        ScoreSessionOutcome.ContinuedWithIntention -> if (isDark) colors.lavenderGlow.copy(alpha = 0.14f) else Color(0xFFF0ECF0)
        ScoreSessionOutcome.Completed -> colors.lavenderGlow.copy(alpha = if (isDark) 0.18f else 0.30f)
        ScoreSessionOutcome.Replayed -> if (isDark) colors.blueGlow.copy(alpha = 0.16f) else ImpulsivePhysical.copy(alpha = 0.28f)
        ScoreSessionOutcome.Abandoned -> if (isDark) colors.coralGlow.copy(alpha = 0.12f) else Color(0xFFF0ECF0)
    }
    Surface(
        color = pillColor,
        shape = RoundedCornerShape(50),
        border = if (isDark) BorderStroke(1.dp, colors.faintLine.copy(alpha = 0.55f)) else null,
    ) {
        Text(
            text = outcome.label,
            color = colors.text.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun ScoreGameType.accentColor(): Color = when (this) {
    ScoreGameType.ReflexOverride -> ImpulsivePhysical
    ScoreGameType.BlockCascade -> ImpulsivePsychological
    ScoreGameType.SkylineReset -> ImpulsivePsychological
    ScoreGameType.UrgeSurvival -> ImpulsiveFocusMode
    ScoreGameType.FluidRegulation -> ImpulsiveOverallTheme
    ScoreGameType.PrecisionFocus -> ImpulsivePhysical
    ScoreGameType.DopamineRunner -> ImpulsiveFocusMode
    ScoreGameType.BreathControl -> ImpulsivePsychological
    ScoreGameType.RageDischarge -> ImpulsiveFocusMode
    ScoreGameType.RhythmTiles -> ImpulsivePsychological
    ScoreGameType.Snake -> ImpulsivePsychological
    ScoreGameType.FocusSession -> ImpulsiveFocusMode
    ScoreGameType.Unknown -> ImpulsivePsychological
}

private fun ScoreGameType.scoreIcon(): ImageVector = when (this) {
    ScoreGameType.ReflexOverride -> Icons.Filled.SportsEsports
    ScoreGameType.BlockCascade -> Icons.Filled.ViewModule
    ScoreGameType.SkylineReset -> Icons.Filled.SportsEsports
    ScoreGameType.UrgeSurvival -> Icons.Filled.Shield
    ScoreGameType.FluidRegulation -> Icons.Filled.TrendingDown
    ScoreGameType.PrecisionFocus -> Icons.Filled.Psychology
    ScoreGameType.DopamineRunner -> Icons.Filled.Moving
    ScoreGameType.BreathControl -> Icons.Filled.ShowChart
    ScoreGameType.RageDischarge -> Icons.Filled.AutoAwesome
    ScoreGameType.RhythmTiles -> Icons.Filled.AutoAwesome
    ScoreGameType.Snake -> Icons.Filled.SportsEsports
    ScoreGameType.FocusSession -> Icons.Filled.Psychology
    ScoreGameType.Unknown -> Icons.Filled.SportsEsports
}

private fun scoreTypeForDisplayName(gameName: String): ScoreGameType =
    ScoreGameType.entries.firstOrNull { it.displayName == gameName } ?: ScoreGameType.Unknown

private fun ScoreTimelineItem.gameIcon(): ImageVector = scoreTypeForDisplayName(gameName).scoreIcon()

private fun ScoreTimelineItem.gameAccentColor(): Color = scoreTypeForDisplayName(gameName).accentColor()

private fun ScoreTimelineItem.urgeLabel(): String = if (urgeBefore != null && urgeAfter != null) {
    "$urgeBefore -> $urgeAfter"
} else {
    "not logged"
}

private fun LocalDateTime.relativeLabel(): String {
    val now = LocalDateTime.now()
    val today = LocalDate.now()
    val duration = Duration.between(this, now)

    if (duration.isNegative || duration.toMinutes() < 1) {
        return "Just now"
    }

    return when {
        toLocalDate() == today && duration.toMinutes() < 60 -> {
            val minutes = duration.toMinutes()
            if (minutes == 1L) "1 min ago" else "${minutes} min ago"
        }

        toLocalDate() == today -> {
            val hours = duration.toHours().coerceAtLeast(1)
            if (hours == 1L) "1h ago" else "${hours}h ago"
        }

        toLocalDate() == today.minusDays(1) -> "Yesterday"

        else -> format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun Int.formatNumber(): String = NumberFormat
    .getIntegerInstance(Locale.UK)
    .format(this)

private fun Int.formatChange(): String = when {
    this > 0 -> "+${formatNumber()}"
    this < 0 -> formatNumber()
    else -> "0"
}

private fun ScoreRange.metricSuffix(): String = when (this) {
    ScoreRange.Today -> "today"
    ScoreRange.Week -> "this week"
    ScoreRange.Month -> "this month"
    ScoreRange.Year -> "this year"
}
