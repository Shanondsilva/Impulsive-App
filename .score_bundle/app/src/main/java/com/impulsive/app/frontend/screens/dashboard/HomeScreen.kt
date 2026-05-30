package com.impulsive.app.frontend.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.impulsive.app.backend.domain.model.release.formattedTodaysWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskRewardState
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStatus
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.ThemeMode
import com.impulsive.app.core.util.greetingForHour
import com.impulsive.app.core.util.resolveSceneTime
import com.impulsive.app.core.util.timeOfDayForHour
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.MindCoreScene
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import java.time.LocalDateTime

private const val DAY_COUNT = 1
private const val CURRENT_LEVEL = 1
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenRecoveryGames: () -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenPatternBreakTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenMindLessonTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    onOpenFutureSelfMessageTask: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by onboardingViewModel.state.collectAsState()
    val displayName = state.answers.name.takeIf { it.isNotBlank() } ?: "friend"
    val avatar = AvatarStyle.fromId(state.answers.avatarId)
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
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
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = state.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(state.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(state.answers.activeDayEndMinute),
    )
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsState()
    val taskRewardState = taskRewardStoreState.toTaskRewardState(releasePlan)
    val displayReleasePlan = calculateRewardedReleasePlan(
        releasePlan = releasePlan,
        adjustedNextReleaseWindow = taskRewardState.adjustedNextReleaseWindow,
        now = currentNow,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImpulsiveBackground)
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
            )

            Spacer(modifier = Modifier.height(16.dp))

            MindCoreScene(
                level = CURRENT_LEVEL,
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

            TaskToCompletePreviewCard(
                releasePlan = displayReleasePlan,
                taskRewardState = taskRewardState,
                onStartTask = {
                    when (taskRewardState.recommendedTaskType) {
                        PsychologyTaskType.ReflexOverride -> onOpenReflexOverrideTask()
                        PsychologyTaskType.PatternBreak -> onOpenPatternBreakTask()
                        PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
                        PsychologyTaskType.MindLesson -> onOpenMindLessonTask()
                        PsychologyTaskType.ResetRead -> onOpenResetReadTask()
                        PsychologyTaskType.FutureSelfMessage,
                        PsychologyTaskType.TriggerDecoder,
                        PsychologyTaskType.ThoughtCapture,
                        PsychologyTaskType.ShortReadingBurst -> onOpenFutureSelfMessageTask()
                    }
                },
                onViewAllTasks = onOpenTasks,
            )

            Spacer(modifier = Modifier.height(18.dp))

            DashboardCards(
                onOpenRecoveryGames = onOpenRecoveryGames,
                onOpenJournal = onOpenJournal,
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
) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.66f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.08f),
                spotColor = ImpulsiveText.copy(alpha = 0.10f),
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
                    color = ImpulsiveText.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Outlined.NightsStay,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.86f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Level ${taskRewardState.currentLevel}",
                color = ImpulsiveText,
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
                Text(
                    text = releasePlan.formattedTodaysWindow(),
                    color = ImpulsiveText.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${taskRewardState.currentLevelPoints} / ${taskRewardState.pointsNeededForNextLevel} LP",
                    color = ImpulsiveText.copy(alpha = 0.84f),
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
) {
    val recommendedTask = taskRewardState.recommendedTaskType.homePreview()
    val recommendedReward = taskRewardState.taskStatuses.first {
        it.taskType == taskRewardState.recommendedTaskType
    }
    val hasWaitCut = recommendedReward.hasVisibleWaitCut()

    Surface(
        color = Color(0xFFFFFCFF),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.06f),
                spotColor = ImpulsiveText.copy(alpha = 0.08f),
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
                        text = "Task to Complete",
                        color = ImpulsiveText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = releasePlan.formattedTimeUntilNextWindow(),
                        color = ImpulsiveMutedText,
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
                color = Color(0xFFF9F6FE),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.055f)),
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
                            tint = ImpulsiveText.copy(alpha = 0.84f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recommendedTask.title,
                            color = ImpulsiveText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recommendedTask.description,
                            color = ImpulsiveText.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasWaitCut) Icons.Outlined.AccessTime else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF5C4A7D),
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recommendedReward.displayRewardLabel(),
                                color = Color(0xFF5C4A7D),
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
                        .clickable { onStartTask() },
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
                    modifier = Modifier.clickable { onViewAllTasks() },
                ) {
                    Text(
                        text = "All tasks",
                        color = Color(0xFF5C4A7D),
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
    val points = if (currentWindowRewardAlreadyUsed) minOf(2, displayLevelPoints) else displayLevelPoints
    val waitReduction = if (currentWindowRewardAlreadyUsed) 0 else displayWaitReductionMinutes
    return if (waitReduction > 0) {
        "Cuts wait by ${waitReduction.formatMinutes()} • +$points LP"
    } else {
        "Bonus only: +$points LP"
    }
}

private fun TaskRewardStatus.hasVisibleWaitCut(): Boolean =
    !currentWindowRewardAlreadyUsed && displayWaitReductionMinutes > 0

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
    PsychologyTaskType.PatternBreak -> HomeRecommendedTask(
        title = "Pattern Break",
        description = "Solve quick patterns to shift your attention.",
    )
    PsychologyTaskType.BlockCascade -> HomeRecommendedTask(
        title = "Block Cascade",
        description = "Load visual focus with a calm falling-block challenge.",
    )
    PsychologyTaskType.MindLesson -> HomeRecommendedTask(
        title = "Mind Lesson",
        description = "Learn one useful idea, then choose the next action.",
    )
    PsychologyTaskType.ResetRead -> HomeRecommendedTask(
        title = "Reset Read",
        description = "Read one focused reset and let the timer finish.",
    )
    PsychologyTaskType.FutureSelfMessage -> HomeRecommendedTask(
        title = "Future-Self Message",
        description = "Hear your own reason before the moment gets harder.",
    )
    PsychologyTaskType.TriggerDecoder,
    PsychologyTaskType.ThoughtCapture,
    PsychologyTaskType.ShortReadingBurst -> HomeRecommendedTask(
        title = "Future-Self Message",
        description = "Use your saved reason as the next recovery action.",
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SmallActionCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenRecoveryGames() },
            label = "RECOVERY GAME",
            title = "Recovery\nGames",
            subtext = "Reflex and Block Cascade",
            cta = "Open list ›",
            iconColor = ImpulsivePsychological.copy(alpha = 0.58f),
            icon = {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.82f),
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        SmallActionCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenJournal() },
            label = "JOURNAL",
            title = "Private\nJournal",
            subtext = "Notes, lists and future-self cues",
            cta = "Open journal ›",
            iconColor = ImpulsiveSpiritual.copy(alpha = 0.78f),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.82f),
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
    subtext: String,
    cta: String,
    iconColor: Color,
    icon: @Composable () -> Unit,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = modifier
            .heightIn(min = 138.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.045f),
                spotColor = ImpulsiveText.copy(alpha = 0.06f),
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
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtext,
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cta,
                color = Color(0xFF5C4A7D),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
