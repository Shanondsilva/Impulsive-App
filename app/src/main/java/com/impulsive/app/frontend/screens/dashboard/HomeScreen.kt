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
    onOpenReflexGame: () -> Unit = {},
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenPatternBreakTask: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by onboardingViewModel.state.collectAsState()
    val displayName = state.answers.name.takeIf { it.isNotBlank() } ?: "friend"
    val avatar = AvatarStyle.fromId(state.answers.avatarId)
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val currentNow by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000L)
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    if (taskRewardState.recommendedTaskType == PsychologyTaskType.ReflexOverride) {
                        onOpenReflexOverrideTask()
                    } else if (taskRewardState.recommendedTaskType == PsychologyTaskType.PatternBreak) {
                        onOpenPatternBreakTask()
                    } else {
                        taskRewardViewModel.completeTask(
                            taskType = taskRewardState.recommendedTaskType,
                            releasePlan = displayReleasePlan,
                            now = currentNow,
                        )
                    }
                },
                onViewAllTasks = onOpenTasks,
            )

            Spacer(modifier = Modifier.height(18.dp))

            DashboardCards(onOpenReflexGame = onOpenReflexGame)
        }

        BottomNavBar(
            selected = BottomNavItem.Home,
            onSelect = { item ->
                if (item == BottomNavItem.Settings) onOpenSettings()
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

    Surface(
        color = Color(0xFFFFFAFF),
        shape = RoundedCornerShape(34.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(34.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.08f),
                spotColor = ImpulsiveText.copy(alpha = 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "Task to Complete",
                color = ImpulsiveText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("Psychology task", ImpulsivePsychological.copy(alpha = 0.72f))
                SoftChip("Recommended", ImpulsivePhysical.copy(alpha = 0.74f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Color(0xFFFFFCFF),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.07f)),
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = recommendedTask.title,
                        color = ImpulsiveText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recommendedTask.description,
                        color = ImpulsiveText.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = ImpulsiveText,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recommendedReward.displayRewardLabel(),
                            color = ImpulsiveText.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ImpulsiveText.copy(alpha = 0.06f)),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = releasePlan.formattedTimeUntilNextWindow(),
                        color = ImpulsiveText.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        releasePlan.formattedPlannedWindows().forEach { plannedWindow ->
                            SoftTimeChip(plannedWindow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xFF6C5A8F),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { onStartTask() },
                ) {
                    Text(
                        text = "Start task",
                        color = Color.White.copy(alpha = 0.96f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
                    )
                }

                Text(
                    text = "View all tasks ›",
                    color = Color(0xFF5C4A7D),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onViewAllTasks() },
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
    val points = if (currentWindowRewardAlreadyUsed) {
        minOf(2, displayLevelPoints)
    } else {
        displayLevelPoints
    }
    val waitReduction = if (currentWindowRewardAlreadyUsed) 0 else displayWaitReductionMinutes
    return if (waitReduction > 0) {
        "Cuts wait by ${waitReduction.formatMinutes()}  •  +$points LP"
    } else {
        "+$points LP"
    }
}

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
    PsychologyTaskType.TriggerDecoder -> HomeRecommendedTask(
        title = "Trigger Decoder",
        description = "Find what is driving the urge right now.",
    )
    PsychologyTaskType.ThoughtCapture -> HomeRecommendedTask(
        title = "Thought Capture",
        description = "Write the thought that started the loop.",
    )
    PsychologyTaskType.ShortReadingBurst -> HomeRecommendedTask(
        title = "Short Reading Burst",
        description = "Swipe through a short reset reading.",
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
private fun DashboardCards(onOpenReflexGame: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SmallActionCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenReflexGame() },
            label = "RECOVERY GAME",
            title = "Reflex\nOverride",
            subtext = "60-second reset",
            cta = "Play now ›",
            iconColor = ImpulsivePsychological.copy(alpha = 0.58f),
            icon = {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.82f),
                    modifier = Modifier.size(22.dp),
                )
            },
        )

        SmallActionCard(
            modifier = Modifier.weight(1f),
            label = "JOURNAL",
            title = "Thought\nCapture",
            subtext = "Save what triggered the loop",
            cta = "Write now ›",
            iconColor = ImpulsiveSpiritual.copy(alpha = 0.78f),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.82f),
                    modifier = Modifier.size(23.dp),
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
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = modifier
            .heightIn(min = 220.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.06f),
                spotColor = ImpulsiveText.copy(alpha = 0.08f),
            ),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = label,
                color = ImpulsiveText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = iconColor,
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                color = ImpulsiveText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = cta,
                color = Color(0xFF5C4A7D),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
