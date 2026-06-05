package com.impulsive.app.frontend.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.formattedPlannedWindows
import com.impulsive.app.backend.domain.model.release.formattedTimeUntilNextWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.TaskRewardState
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStatus
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import java.time.LocalDateTime
import java.time.LocalDate

private data class PsychologyTask(
    val taskType: PsychologyTaskType,
    val title: String,
    val description: String,
    val chip: String,
    val icon: ImageVector,
    val iconBackground: Color,
)

private data class TaskModeColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val planCard: Color,
    val planText: Color,
    val primaryText: Color,
    val mutedText: Color,
    val accentText: Color,
    val subtleBorder: Color,
    val softShadow: Color,
    val chipBackground: Color,
    val rewardBackground: Color,
    val isDark: Boolean,
)

@Composable
private fun rememberTaskModeColors(): TaskModeColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) {
        TaskModeColors(
            background = scheme.background,
            surface = Color(0xFF171D22),
            elevatedSurface = Color(0xFF202832),
            planCard = Color(0xFF241F31),
            planText = Color(0xFFF7F2FF),
            primaryText = Color(0xFFF7F2FF),
            mutedText = Color(0xFFC9C0D8),
            accentText = Color(0xFFD0C3F1),
            subtleBorder = Color(0xFFD0C3F1).copy(alpha = 0.22f),
            softShadow = Color(0xFFD0C3F1).copy(alpha = 0.12f),
            chipBackground = Color(0xFFD0C3F1).copy(alpha = 0.18f),
            rewardBackground = Color(0xFF2C2736),
            isDark = true,
        )
    } else {
        TaskModeColors(
            background = ImpulsiveBackground,
            surface = ImpulsiveSurface,
            elevatedSurface = Color(0xFFFFFCFF),
            planCard = ImpulsivePsychological.copy(alpha = 0.82f),
            planText = Color(0xFF5B4B7E),
            primaryText = ImpulsiveText,
            mutedText = ImpulsiveMutedText,
            accentText = Color(0xFF5B4B7E),
            subtleBorder = Color.Transparent,
            softShadow = ImpulsiveText.copy(alpha = 0.08f),
            chipBackground = ImpulsiveText.copy(alpha = 0.055f),
            rewardBackground = Color(0xFFFFF9D8),
            isDark = false,
        )
    }
}

private val VisiblePsychologyTasks = listOf(
    PsychologyTask(
        taskType = PsychologyTaskType.BlockCascade,
        title = "Block Cascade",
        description = "A 90-second visual focus round with a real end state.",
        chip = "Visual focus",
        icon = Icons.Filled.AutoAwesome,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.70f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.SkylineReset,
        title = "SkyStack",
        description = "Stack a calm skyscraper, floor by floor, into the night.",
        chip = "Steady focus",
        icon = Icons.Filled.SportsEsports,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.58f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.ReflexOverride,
        title = "Reflex Override",
        description = "Break autopilot with a fast reaction challenge.",
        chip = "Fast control",
        icon = Icons.Filled.SportsEsports,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.58f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.ResetRead,
        title = "Reset Read",
        description = "Read for the full timer before choosing the next move.",
        chip = "Reader",
        icon = Icons.AutoMirrored.Outlined.Article,
        iconBackground = Color(0xFFE8E2EA),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.FutureSelfMessage,
        title = "Future-Self Message",
        description = "Play your saved reason and make one clear choice.",
        chip = "Your voice",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.52f),
    ),
)

@Composable
fun TaskToCompleteScreen(
    onBack: () -> Unit,
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenSkylineResetTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    onOpenFutureSelfMessageTask: () -> Unit = {},
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val colors = rememberTaskModeColors()
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val currentNow by produceState(initialValue = LocalDateTime.now().withSecond(0).withNano(0)) {
        while (true) {
            value = LocalDateTime.now().withSecond(0).withNano(0)
            kotlinx.coroutines.delay(30_000L)
        }
    }
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = state.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(state.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(state.answers.activeDayEndMinute),
    )
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val completionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val taskRewardState = taskRewardStoreState.toTaskRewardState(releasePlan)
    val displayReleasePlan = calculateRewardedReleasePlan(
        releasePlan = releasePlan,
        adjustedNextReleaseWindow = taskRewardState.adjustedNextReleaseWindow,
        now = currentNow,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp),
        ) {
            TaskHeader(onBack = onBack, colors = colors)

            if (completionResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                CompletionResultCard(result = completionResult!!, colors = colors)
            }

            Spacer(modifier = Modifier.height(14.dp))

            TodayPlanCard(
                releasePlan = displayReleasePlan,
                taskRewardState = taskRewardState,
                colors = colors,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose one task",
                    color = colors.primaryText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "One wait cut per day",
                    color = colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val orderedTasks = orderedVisibleTasks(taskRewardState, currentNow.toLocalDate())
            val highlightedTaskType = orderedTasks.firstOrNull()?.taskType
            orderedTasks.forEachIndexed { index, task ->
                val rewardStatus = taskRewardState.taskStatuses.first { it.taskType == task.taskType }
                TaskChoiceCard(
                    task = task,
                    rewardStatus = rewardStatus,
                    recommended = task.taskType == highlightedTaskType,
                    recommendationReason = taskRewardState.recommendedTaskReason.takeIf {
                        task.taskType == taskRewardState.recommendedTaskType
                    },
                    haptics = haptics,
                    colors = colors,
                    onStartTask = {
                        when (task.taskType) {
                            PsychologyTaskType.ReflexOverride -> onOpenReflexOverrideTask()
                            PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
                            PsychologyTaskType.SkylineReset -> onOpenSkylineResetTask()
                            PsychologyTaskType.ResetRead -> onOpenResetReadTask()
                            PsychologyTaskType.FutureSelfMessage -> onOpenFutureSelfMessageTask()
                            PsychologyTaskType.TriggerDecoder,
                            PsychologyTaskType.ThoughtCapture,
                            PsychologyTaskType.ShortReadingBurst -> onOpenFutureSelfMessageTask()
                        }
                    },
                )
                if (index != orderedTasks.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (orderedTasks.isEmpty()) {
                AllTasksCompleteCard(colors = colors)
            }

            Spacer(modifier = Modifier.height(18.dp))
            BottomNoteCard(colors = colors)
        }
    }
}

private fun orderedVisibleTasks(
    taskRewardState: TaskRewardState,
    today: LocalDate,
): List<PsychologyTask> {
    val incompleteTaskTypes = taskRewardState.taskStatuses
        .filterNot { it.lastCompletedAt?.toLocalDate() == today }
        .map { it.taskType }
        .toSet()
    val visibleIncompleteTasks = VisiblePsychologyTasks.filter { it.taskType in incompleteTaskTypes }
    val recommendedTaskType = taskRewardState.recommendedTaskType
    val visibleRecommended = VisiblePsychologyTasks.firstOrNull { it.taskType == recommendedTaskType }
        ?.takeIf { it.taskType in incompleteTaskTypes }
    return if (visibleRecommended == null) {
        visibleIncompleteTasks
    } else {
        listOf(visibleRecommended) + visibleIncompleteTasks.filterNot { it.taskType == recommendedTaskType }
    }
}

@Composable
private fun TaskHeader(
    onBack: () -> Unit,
    colors: TaskModeColors,
) {
    var showTaskInfo by remember { mutableStateOf(false) }

    if (showTaskInfo) {
        AlertDialog(
            onDismissRequest = { showTaskInfo = false },
            title = {
                Text(
                    text = "What are Tasks?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Tasks are short control actions that help you pause, redirect your attention, and earn progress before the next window. Complete one real action to reduce waiting time and build Level Points.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showTaskInfo = false }) {
                    Text(text = "Got it")
                }
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tasks",
                    color = colors.accentText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { showTaskInfo = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About Tasks",
                        tint = colors.accentText.copy(alpha = 0.88f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Complete one real action to reduce the wait.",
                color = colors.primaryText.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CompletionResultCard(
    result: TaskCompletionResult,
    colors: TaskModeColors,
) {
    Surface(
        color = colors.elevatedSurface,
        shape = RoundedCornerShape(22.dp),
        border = if (colors.isDark) BorderStroke(1.dp, colors.subtleBorder) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Tasks",
                    color = colors.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${result.waitReductionLabel()} • +${result.levelPointsAwarded} LP",
                    color = colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun TaskCompletionResult.waitReductionLabel(): String =
    if (waitReductionMinutes > 0) {
        "Wait reduced by ${waitReductionMinutes.formatMinutes()}"
    } else {
        "LP only this time"
    }

@Composable
private fun TodayPlanCard(
    releasePlan: ReleasePlanState,
    taskRewardState: TaskRewardState,
    colors: TaskModeColors,
) {
    val plannedWindows = releasePlan.formattedPlannedWindows()
    Surface(
        color = colors.planCard,
        shape = RoundedCornerShape(28.dp),
        border = if (colors.isDark) BorderStroke(1.dp, colors.subtleBorder) else null,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = colors.softShadow,
                spotColor = colors.softShadow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SoftChip(
                    text = "TODAY'S PLAN",
                    color = if (colors.isDark) colors.chipBackground else Color.White.copy(alpha = 0.42f),
                    textColor = colors.accentText,
                )
                Text(
                    text = "Level ${taskRewardState.currentLevel} • ${taskRewardState.currentLevelPoints}/${taskRewardState.pointsNeededForNextLevel} LP",
                    color = colors.planText.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next window",
                        color = colors.planText.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = releasePlan.formattedTimeUntilNextWindow().removePrefix("Next window in "),
                        color = colors.planText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "Planned",
                        color = colors.planText.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = plannedWindows.joinToString("\n"),
                        color = colors.planText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = plannedWindows.size.coerceAtLeast(1),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = {
                    taskRewardState.currentLevelPoints.toFloat() /
                        taskRewardState.pointsNeededForNextLevel.toFloat()
                },
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.14f),
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun TaskChoiceCard(
    task: PsychologyTask,
    rewardStatus: TaskRewardStatus,
    recommended: Boolean,
    recommendationReason: String?,
    haptics: com.impulsive.app.frontend.utils.ImpulsiveHaptics,
    colors: TaskModeColors,
    onStartTask: () -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    val cardColor = if (recommended) colors.elevatedSurface else colors.surface

    Surface(
        color = cardColor,
        shape = cardShape,
        border = if (colors.isDark) {
            BorderStroke(
                width = 1.dp,
                color = if (recommended) colors.accentText.copy(alpha = 0.22f) else colors.subtleBorder,
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (recommended) 8.dp else 5.dp,
                shape = cardShape,
                clip = false,
                ambientColor = colors.softShadow.copy(alpha = if (recommended) 1f else 0.72f),
                spotColor = colors.softShadow.copy(alpha = if (recommended) 1f else 0.72f),
            )
            .clickable {
                haptics.start()
                onStartTask()
            },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    SoftIconCircle(
                        color = task.iconBackground,
                        icon = task.icon,
                        contentColor = colors.primaryText.copy(alpha = 0.78f),
                    )
                    if (recommended) {
                        SoftChip(
                            text = "Recommended",
                            color = Color(0xFF6C5A8F),
                            textColor = Color.White.copy(alpha = 0.96f),
                            leadingIcon = Icons.Filled.AutoAwesome,
                        )
                    } else {
                        SoftChip(
                            text = task.chip,
                            color = colors.chipBackground,
                            textColor = colors.mutedText,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.primaryText.copy(alpha = 0.34f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = task.title,
                color = colors.primaryText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = recommendationReason ?: task.description,
                color = colors.primaryText.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(14.dp))

            RewardPillRow(rewardStatus = rewardStatus, colors = colors)
        }
    }
}

@Composable
private fun RewardPillRow(
    rewardStatus: TaskRewardStatus,
    colors: TaskModeColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rewardStatus.visibleWaitCutMinutes() > 0) {
            RewardPill(
                icon = Icons.Outlined.AccessTime,
                text = "Cuts ${rewardStatus.visibleWaitCutMinutes().formatMinutes()}",
                background = if (colors.isDark) colors.chipBackground else ImpulsivePsychological.copy(alpha = 0.34f),
                content = colors.accentText,
            )
        } else {
            RewardPill(
                icon = Icons.Filled.Timer,
                text = "Wait cut used today",
                background = colors.chipBackground,
                content = colors.mutedText,
            )
        }
        RewardPill(
            icon = Icons.Filled.AutoAwesome,
            text = "+${rewardStatus.visibleLevelPoints()} LP",
            background = colors.rewardBackground,
            content = colors.accentText,
        )
        val firstTimeBonus = rewardStatus.firstTimeBonusLevelPoints()
        if (firstTimeBonus > 0) {
            RewardPill(
                icon = Icons.Filled.AutoAwesome,
                text = "First-time bonus +$firstTimeBonus",
                background = if (colors.isDark) colors.chipBackground else Color(0xFFFFF9D8),
                content = colors.accentText,
            )
        }
    }
}

@Composable
private fun RewardPill(
    icon: ImageVector,
    text: String,
    background: Color,
    content: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SoftIconCircle(
    color: Color,
    icon: ImageVector,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun SoftChip(
    text: String,
    color: Color,
    textColor: Color,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BottomNoteCard(colors: TaskModeColors) {
    Surface(
        color = colors.elevatedSurface,
        shape = RoundedCornerShape(24.dp),
        border = if (colors.isDark) BorderStroke(1.dp, colors.subtleBorder) else null,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = colors.softShadow.copy(alpha = 0.55f),
                spotColor = colors.softShadow.copy(alpha = 0.55f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Rewards only apply after the task validates completion. Opening a task or tapping through does not count.",
                color = colors.primaryText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AllTasksCompleteCard(colors: TaskModeColors) {
    Surface(
        color = colors.elevatedSurface,
        shape = RoundedCornerShape(24.dp),
        border = if (colors.isDark) BorderStroke(1.dp, colors.subtleBorder) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "All first-time tasks complete",
                color = colors.primaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You have claimed each first-time task bonus. New task choices will appear when more task types are added.",
                color = colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun TaskRewardStatus.visibleWaitCutMinutes(): Int =
    if (waitCutAlreadyUsedToday) 0 else displayWaitReductionMinutes

private fun TaskRewardStatus.visibleLevelPoints(): Int =
    if (currentWindowRewardAlreadyUsed) minOf(2, displayLevelPoints) else displayLevelPoints

private fun TaskRewardStatus.firstTimeBonusLevelPoints(): Int =
    if (completedEver) 0 else (firstTimeLevelPoints - repeatLevelPoints).coerceAtLeast(0)

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
