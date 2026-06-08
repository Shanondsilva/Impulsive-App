package com.impulsive.app.frontend.screens.dashboard

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.formattedPlannedWindows
import com.impulsive.app.backend.domain.model.release.formattedTimeUntilNextWindow
import com.impulsive.app.backend.domain.model.release.ReleasePlanDefaults
import com.impulsive.app.backend.domain.model.release.formattedTodaysWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskRewardState
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStatus
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.ThemeMode
import com.impulsive.app.core.util.greetingForHour
import com.impulsive.app.core.util.resolveSceneTime
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.core.util.timeOfDayForHour
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.MindCoreScene
import com.impulsive.app.frontend.components.impulsiveGlowBorderStroke
import com.impulsive.app.frontend.components.impulsiveGlowShadow
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsivePsychologicalDark
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import com.impulsive.app.frontend.theme.ImpulsiveText
import com.impulsive.app.frontend.theme.ImpulsiveTextDark
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.delay

private const val DAY_COUNT = 1

private val HomeLavenderGlow = Color(0xFFD0C3F1)
private val HomeGreenGlow = Color(0xFF93E9BE)
private val HomeYellowGlow = Color(0xFFFEF1AB)

private data class HomeReadablePalette(
    val cardSurface: Color,
    val innerCardSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val actionText: Color,
    val subtleBorder: Color,
    val softShadow: Color,
)

private fun homeReadablePalette(useDarkUi: Boolean): HomeReadablePalette = if (useDarkUi) {
    val accent = Color(0xFFD0C3F1)
    HomeReadablePalette(
        cardSurface = Color(0xFF171D22),
        innerCardSurface = Color(0xFF202832),
        primaryText = Color(0xFFF7F2FF),
        secondaryText = Color(0xFFD9D2E8),
        mutedText = Color(0xFFB9B1C7),
        actionText = accent,
        subtleBorder = accent.copy(alpha = 0.22f),
        softShadow = accent.copy(alpha = 0.14f),
    )
} else {
    HomeReadablePalette(
        cardSurface = Color.Unspecified,
        innerCardSurface = Color.Unspecified,
        primaryText = ImpulsiveText,
        secondaryText = ImpulsiveText.copy(alpha = 0.80f),
        mutedText = ImpulsiveMutedText,
        actionText = Color(0xFF5C4A7D),
        subtleBorder = Color.Transparent,
        softShadow = ImpulsiveText.copy(alpha = 0.08f),
    )
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    protectionSetupViewModel: ProtectionSetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenRecoveryGames: () -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenSkylineResetTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenReading: () -> Unit = onOpenResetReadTask,
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val displayName = state.answers.name.takeIf { it.isNotBlank() } ?: "friend"
    val avatar = AvatarStyle.fromId(state.answers.avatarId)
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val currentNow by produceState(initialValue = LocalDateTime.now().withSecond(0).withNano(0)) {
        while (true) {
            value = LocalDateTime.now().withSecond(0).withNano(0)
            kotlinx.coroutines.delay(30_000L)
        }
    }
    val currentHour = currentNow.hour
    val greeting = greetingForHour(currentHour)
    val sceneTime = if (themeMode == ThemeMode.AsPerTime) {
        timeOfDayForHour(currentHour)
    } else {
        resolveSceneTime(themeMode, systemInDark)
    }
    val useDarkUi = shouldUseDarkTheme(themeMode, systemInDark)
    val palette = homeReadablePalette(useDarkUi)
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = state.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(state.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(state.answers.activeDayEndMinute),
    )
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskRewardState = taskRewardStoreState.toTaskRewardState(releasePlan)
    val displayReleasePlan = calculateRewardedReleasePlan(
        releasePlan = releasePlan,
        adjustedNextReleaseWindow = taskRewardState.adjustedNextReleaseWindow,
        now = currentNow,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
        ) {
            HeaderBlock(
                avatar = avatar,
                greeting = greeting,
                displayName = displayName,
            )

            Spacer(modifier = Modifier.height(20.dp))

            LevelCard(
                releasePlan = displayReleasePlan,
                taskRewardState = taskRewardState,
                palette = palette,
            )

            Spacer(modifier = Modifier.height(16.dp))

            MindCoreScene(
                level = taskRewardState.currentLevel,
                timeOfDay = sceneTime,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = ImpulsiveText.copy(alpha = 0.15f),
                        spotColor = ImpulsiveText.copy(alpha = 0.20f),
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (taskRewardState.waitCutAlreadyUsedToday) {
                TaskCompletedPreviewCard(
                    releasePlan = displayReleasePlan,
                    completedTaskName = taskRewardState.lastCompletedTaskType?.taskTitle ?: "Pivot task",
                    onViewAllTasks = onOpenTasks,
                    palette = palette,
                )
            } else {
                TaskToCompletePreviewCard(
                    releasePlan = displayReleasePlan,
                    taskRewardState = taskRewardState,
                    onStartTask = {
                        when (taskRewardState.recommendedTaskType) {
                            PsychologyTaskType.ReflexOverride -> onOpenReflexOverrideTask()
                            PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
                            PsychologyTaskType.SkylineReset -> onOpenSkylineResetTask()
                            PsychologyTaskType.ResetRead,
                            PsychologyTaskType.TriggerDecoder,
                            PsychologyTaskType.ThoughtCapture,
                            PsychologyTaskType.ShortReadingBurst -> onOpenResetReadTask()
                        }
                    },
                    onViewAllTasks = onOpenTasks,
                    palette = palette,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            DashboardCards(
                onOpenRecoveryGames = onOpenRecoveryGames,
                onOpenJournal = onOpenJournal,
                onOpenReading = onOpenReading,
                palette = palette,
            )
        }

        BottomNavBar(
            selected = BottomNavItem.Home,
            onSelect = { item ->
                when (item) {
                    BottomNavItem.Progress -> onOpenScore()
                    BottomNavItem.Settings -> onOpenSettings()
                    else -> Unit
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            settingsBadgeVisible = protectionSetupState.profileBadgeShouldShow,
        )
    }
}

@Composable
private fun HeaderBlock(
    avatar: AvatarStyle,
    greeting: String,
    displayName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatar.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = avatar.drawableResId),
                    contentDescription = avatar.contentDescription,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "$greeting, $displayName",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Day $DAY_COUNT",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    Surface(
                        color = ImpulsivePsychological.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "Mind mode",
                            color = ImpulsiveText.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
            }
        }
    }
}

@Composable
private fun LevelCard(
    releasePlan: ReleasePlanState,
    taskRewardState: TaskRewardState,
    palette: HomeReadablePalette,
) {
    val isDark = palette.cardSurface != Color.Unspecified
    val cardShape = RoundedCornerShape(28.dp)
    val levelCardColor = if (isDark) {
        ImpulsivePsychologicalDark
    } else {
        ImpulsivePsychological.copy(alpha = 0.66f)
    }
    val levelCardContent = if (isDark) ImpulsiveTextDark else ImpulsiveText
    Surface(
        color = levelCardColor,
        shape = cardShape,
        border = if (isDark) {
            impulsiveGlowBorderStroke(
                enabled = true,
                glowColor = HomeLavenderGlow,
                fallbackColor = Color.Transparent,
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.08f),
                spotColor = ImpulsiveText.copy(alpha = 0.10f),
            )
            .impulsiveGlowShadow(
                enabled = isDark,
                shape = cardShape,
                glowColor = HomeLavenderGlow,
                elevation = 18.dp,
            ),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TODAY",
                    color = levelCardContent.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Outlined.NightsStay,
                    contentDescription = null,
                    tint = levelCardContent.copy(alpha = 0.86f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Level ${taskRewardState.currentLevel}",
                color = levelCardContent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = {
                    taskRewardState.currentLevelPoints.toFloat() /
                        taskRewardState.pointsNeededForNextLevel.toFloat()
                },
                color = Color.White.copy(alpha = 0.90f),
                trackColor = Color.White.copy(alpha = 0.45f),
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val liveNow by produceState(initialValue = LocalDateTime.now()) {
                    while (true) {
                        value = LocalDateTime.now()
                        delay(1000)
                    }
                }
                val activeWindowEnd = releasePlan.plannedWindowsToday
                    .firstOrNull { start ->
                        !liveNow.isBefore(start) &&
                            liveNow.isBefore(start.plusMinutes(ReleasePlanDefaults.ReleaseWindowMinutes))
                    }
                    ?.plusMinutes(ReleasePlanDefaults.ReleaseWindowMinutes)
                val remainingInWindow = activeWindowEnd
                    ?.let { Duration.between(liveNow, it) }
                    ?.takeIf { !it.isNegative && !it.isZero }
                Text(
                    text = if (remainingInWindow != null) {
                        val totalSeconds = remainingInWindow.seconds
                        "Time left: %d:%02d".format(totalSeconds / 60, totalSeconds % 60)
                    } else {
                        releasePlan.formattedTodaysWindow()
                    },
                    color = levelCardContent.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${taskRewardState.currentLevelPoints} / ${taskRewardState.pointsNeededForNextLevel} LP",
                    color = levelCardContent.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TaskToCompletePreviewCard(
    releasePlan: ReleasePlanState,
    taskRewardState: TaskRewardState,
    onStartTask: () -> Unit,
    onViewAllTasks: () -> Unit,
    palette: HomeReadablePalette,
) {
    val recommendedTask = taskRewardState.recommendedTaskType.homePreview()
    val recommendedReward = taskRewardState.taskStatuses.first {
        it.taskType == taskRewardState.recommendedTaskType
    }
    val hasWaitCut = recommendedReward.hasVisibleWaitCut()

    val surfaceColor = if (palette.cardSurface == Color.Unspecified)
        MaterialTheme.colorScheme.surface else palette.cardSurface
    val innerSurfaceColor = if (palette.innerCardSurface == Color.Unspecified)
        MaterialTheme.colorScheme.surfaceVariant else palette.innerCardSurface
    val isDark = palette.cardSurface != Color.Unspecified
    val cardShape = RoundedCornerShape(30.dp)

    Surface(
        color = surfaceColor,
        shape = cardShape,
        border = impulsiveGlowBorderStroke(
            enabled = isDark,
            glowColor = HomeLavenderGlow,
            fallbackColor = palette.subtleBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = palette.softShadow,
                spotColor = palette.softShadow,
            )
            .impulsiveGlowShadow(
                enabled = isDark,
                shape = cardShape,
                glowColor = HomeLavenderGlow,
                elevation = 18.dp,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tasks",
                        color = palette.primaryText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = releasePlan.formattedTimeUntilNextWindow(),
                        color = palette.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                SoftChip(
                    text = if (recommendedReward.isFirstTimeBoostAvailable) "First-time boost" else "Reward ready",
                    color = ImpulsivePsychological.copy(alpha = 0.72f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = innerSurfaceColor,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, palette.subtleBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = ImpulsivePsychological.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = palette.primaryText.copy(alpha = 0.84f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recommendedTask.title,
                            color = palette.primaryText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recommendedTask.description,
                            color = palette.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasWaitCut) Icons.Outlined.AccessTime else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = palette.actionText,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recommendedReward.displayRewardLabel(),
                                color = palette.actionText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xFF6C5A8F),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onStartTask() },
                ) {
                    Text(
                        text = "Start task",
                        color = Color.White.copy(alpha = 0.96f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                    )
                }

                Surface(
                    color = ImpulsivePsychological.copy(alpha = 0.36f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onViewAllTasks() },
                ) {
                    Text(
                        text = "All tasks",
                        color = palette.actionText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCompletedPreviewCard(
    releasePlan: ReleasePlanState,
    completedTaskName: String,
    onViewAllTasks: () -> Unit,
    palette: HomeReadablePalette,
) {
    val surfaceColor = if (palette.cardSurface == Color.Unspecified)
        Color(0xFFFFFCFF) else palette.cardSurface
    val isDark = palette.cardSurface != Color.Unspecified
    val cardShape = RoundedCornerShape(30.dp)

    Surface(
        color = surfaceColor,
        shape = cardShape,
        border = impulsiveGlowBorderStroke(
            enabled = isDark,
            glowColor = HomeGreenGlow,
            fallbackColor = ImpulsivePsychological.copy(alpha = 0.28f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = palette.softShadow,
                spotColor = palette.softShadow,
            )
            .impulsiveGlowShadow(
                enabled = isDark,
                shape = cardShape,
                glowColor = HomeGreenGlow,
                elevation = 18.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = ImpulsivePsychological.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = palette.primaryText.copy(alpha = 0.86f),
                    modifier = Modifier.size(21.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tasks",
                    color = palette.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$completedTaskName is logged for today.",
                    color = palette.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = releasePlan.formattedTimeUntilNextWindow(),
                    color = palette.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                color = ImpulsivePsychological.copy(alpha = 0.30f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onViewAllTasks() },
            ) {
                Text(
                    text = "All tasks",
                    color = palette.actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SoftChip(
    text: String,
    color: Color,
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = ImpulsiveText.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun TaskRewardStatus.displayRewardLabel(): String {
    val points = displayLevelPoints
    val waitReduction = if (waitCutAlreadyUsedToday) 0 else displayWaitReductionMinutes
    return if (waitReduction > 0) {
        "Cuts wait by ${waitReduction.formatMinutes()} • +$points LP"
    } else {
        "Bonus only: +$points LP"
    }
}

private fun TaskRewardStatus.hasVisibleWaitCut(): Boolean =
    !waitCutAlreadyUsedToday && displayWaitReductionMinutes > 0

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"

private data class HomeRecommendedTask(
    val title: String,
    val description: String,
)

private fun PsychologyTaskType.homePreview(): HomeRecommendedTask = when (this) {
    PsychologyTaskType.ReflexOverride -> HomeRecommendedTask(
        title = "Reflex Override",
        description = "Break autopilot with a fast control challenge.",
    )
    PsychologyTaskType.BlockCascade -> HomeRecommendedTask(
        title = "Block Cascade",
        description = "Load visual focus with a calm falling-block challenge.",
    )
    PsychologyTaskType.SkylineReset -> HomeRecommendedTask(
        title = "SkyStack",
        description = "Stack a calm skyscraper, floor by floor, into the night.",
    )
    PsychologyTaskType.ResetRead -> HomeRecommendedTask(
        title = "Reset Read",
        description = "Read one focused reset and let the timer finish.",
    )
    PsychologyTaskType.TriggerDecoder,
    PsychologyTaskType.ThoughtCapture,
    PsychologyTaskType.ShortReadingBurst -> HomeRecommendedTask(
        title = "Reset Read",
        description = "Read one focused reset and let the timer finish.",
    )
}

@Composable
private fun SoftTimeChip(text: String) {
    Surface(
        color = ImpulsiveText.copy(alpha = 0.055f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = ImpulsiveText.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DashboardCards(
    onOpenRecoveryGames: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenReading: () -> Unit,
    palette: HomeReadablePalette,
) {
    val isDark = palette.cardSurface != Color.Unspecified

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SmallActionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenRecoveryGames() },
                label = "PIVOT GAME",
                title = "Pivot\nGames",
                subtext = "Reflex, block and mind games",
                animatedTitles = listOf(
                    "Reflex Override",
                    "Block Cascade",
                    "SkyStack",
                ),
                animatedSubtitles = listOf(
                    "2 wins +50",
                    "2 wins +50",
                    "2 wins +50",
                ),
                cta = "Open list ›",
                iconColor = ImpulsivePsychological.copy(alpha = 0.58f),
                glowColor = HomeLavenderGlow,
                palette = palette,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = palette.primaryText.copy(alpha = 0.82f),
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            SmallActionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenJournal() },
                label = "NOTES",
                title = "Private\nNotes",
                subtext = "Notes, lists and reminders",
                cta = "Open notes ›",
                iconColor = ImpulsiveSpiritual.copy(alpha = 0.78f),
                glowColor = HomeYellowGlow,
                palette = palette,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = palette.primaryText.copy(alpha = 0.82f),
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
        }

        SmallActionCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenReading() },
            label = "READING",
            title = "Reset Reading",
            subtext = "Short calm cards for low-energy reset moments",
            cta = "Open reading ›",
            iconColor = Color(0xFFFEF1AB).copy(alpha = if (isDark) 0.34f else 0.78f),
            glowColor = HomeGreenGlow,
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    tint = palette.primaryText.copy(alpha = 0.82f),
                    modifier = Modifier.size(21.dp),
                )
            },
        )
    }
}

@Composable
private fun SmallActionCard(
    modifier: Modifier,
    label: String,
    title: String,
    animatedTitles: List<String>? = null,
    subtext: String,
    animatedSubtitles: List<String>? = null,
    cta: String,
    iconColor: Color,
    glowColor: Color,
    palette: HomeReadablePalette,
    icon: @Composable () -> Unit,
) {
    val surfaceColor = if (palette.cardSurface == Color.Unspecified)
        MaterialTheme.colorScheme.surface else palette.cardSurface
    val isDark = palette.cardSurface != Color.Unspecified
    val cardShape = RoundedCornerShape(24.dp)

    Surface(
        color = surfaceColor,
        shape = cardShape,
        border = impulsiveGlowBorderStroke(
            enabled = isDark,
            glowColor = glowColor,
            fallbackColor = palette.subtleBorder,
        ),
        modifier = modifier
            .heightIn(min = 138.dp)
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false,
                ambientColor = palette.softShadow,
                spotColor = palette.softShadow,
            )
            .impulsiveGlowShadow(
                enabled = isDark,
                shape = cardShape,
                glowColor = glowColor,
                elevation = 16.dp,
                ambientAlpha = 0.14f,
                spotAlpha = 0.18f,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = iconColor,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = palette.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val hasShowcase = !animatedTitles.isNullOrEmpty() || !animatedSubtitles.isNullOrEmpty()
            var showcaseIndex by remember { mutableIntStateOf(0) }
            if (hasShowcase) {
                LaunchedEffect(animatedTitles, animatedSubtitles) {
                    while (true) {
                        delay(2800)
                        val count = maxOf(animatedTitles?.size ?: 1, animatedSubtitles?.size ?: 1)
                        showcaseIndex = (showcaseIndex + 1) % count
                    }
                }
            }

            if (animatedTitles.isNullOrEmpty()) {
                Text(
                    text = title,
                    color = palette.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                androidx.compose.animation.AnimatedContent(
                    targetState = animatedTitles[showcaseIndex % animatedTitles.size],
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(animationSpec = tween(600)) togetherWith
                            androidx.compose.animation.fadeOut(animationSpec = tween(600))
                    },
                    label = "gameTitle",
                ) { line ->
                    Text(
                        text = line,
                        color = palette.primaryText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (animatedSubtitles.isNullOrEmpty()) {
                Text(
                    text = subtext,
                    color = palette.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                androidx.compose.animation.AnimatedContent(
                    targetState = animatedSubtitles[showcaseIndex % animatedSubtitles.size],
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(animationSpec = tween(600)) togetherWith
                            androidx.compose.animation.fadeOut(animationSpec = tween(600))
                    },
                    label = "gameShowcase",
                ) { line ->
                    Text(
                        text = line,
                        color = palette.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cta,
                color = palette.actionText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
