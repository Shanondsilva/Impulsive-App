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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.formattedPlannedWindows
import com.impulsive.app.backend.domain.model.release.formattedTimeUntilNextWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskRewardState
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStatus
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import java.time.LocalDateTime

private data class PsychologyTask(
    val taskType: PsychologyTaskType,
    val title: String,
    val description: String,
    val chip: String,
    val icon: ImageVector,
    val iconBackground: Color,
)

private val PsychologyTasks = listOf(
    PsychologyTask(
        taskType = PsychologyTaskType.ReflexOverride,
        title = "Reflex Override",
        description = "Break autopilot with a fast control challenge.",
        chip = "First-time boost",
        icon = Icons.Filled.AutoAwesome,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.78f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.PatternBreak,
        title = "Pattern Break",
        description = "Solve quick patterns to shift your attention.",
        chip = "Logic",
        icon = Icons.Outlined.Pattern,
        iconBackground = ImpulsivePhysical.copy(alpha = 0.62f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.TriggerDecoder,
        title = "Trigger Decoder",
        description = "Find what is driving the urge right now.",
        chip = "Pattern",
        icon = Icons.Filled.Radar,
        iconBackground = ImpulsivePhysical.copy(alpha = 0.62f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.ThoughtCapture,
        title = "Thought Capture",
        description = "Write the thought that started the loop.",
        chip = "Journal",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.46f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.ShortReadingBurst,
        title = "Short Reading Burst",
        description = "Swipe through a short reset reading.",
        chip = "Light task",
        icon = Icons.AutoMirrored.Outlined.Article,
        iconBackground = Color(0xFFE8E2EA),
    ),
)

@Composable
fun TaskToCompleteScreen(
    onBack: () -> Unit,
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenPatternBreakTask: () -> Unit = {},
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by onboardingViewModel.state.collectAsState()
    val currentNow by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = state.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(state.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(state.answers.activeDayEndMinute),
    )
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsState()
    val completionResult by taskRewardViewModel.lastCompletionResult.collectAsState()
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp),
        ) {
            TaskHeader(onBack = onBack)

            if (completionResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                CompletionResultCard(result = completionResult!!)
            }

            Spacer(modifier = Modifier.height(18.dp))

            TodayPlanCard(
                releasePlan = displayReleasePlan,
                taskRewardState = taskRewardState,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Psychology tasks",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            val orderedTasks = PsychologyTasks.sortedBy { task ->
                if (task.taskType == taskRewardState.recommendedTaskType) 0 else 1
            }

            orderedTasks.forEachIndexed { index, task ->
                TaskChoiceCard(
                    task = task,
                    rewardStatus = taskRewardState.taskStatuses.first { it.taskType == task.taskType },
                    recommended = task.taskType == taskRewardState.recommendedTaskType,
                    onCompleteTask = {
                        if (task.taskType == PsychologyTaskType.ReflexOverride) {
                            onOpenReflexOverrideTask()
                        } else if (task.taskType == PsychologyTaskType.PatternBreak) {
                            onOpenPatternBreakTask()
                        } else {
                            taskRewardViewModel.completeTask(
                                taskType = task.taskType,
                                releasePlan = displayReleasePlan,
                                now = currentNow,
                            )
                        }
                    },
                )
                if (index != orderedTasks.lastIndex) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            BottomNoteCard()
        }
    }
}

@Composable
private fun TaskHeader(onBack: () -> Unit) {
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
                tint = ImpulsiveText,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
            Text(
                text = "Task to Complete",
                color = Color(0xFF5B4B7E),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose one task to reduce the wait.",
                color = ImpulsiveText.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CompletionResultCard(result: TaskCompletionResult) {
    Surface(
        color = Color(0xFFFFFCFF),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                tint = Color(0xFF5B4B7E),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Task complete",
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${result.waitReductionLabel()} • +${result.levelPointsAwarded} LP",
                    color = ImpulsiveMutedText,
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
        "Points added"
    }

@Composable
private fun TodayPlanCard(
    releasePlan: ReleasePlanState,
    taskRewardState: TaskRewardState,
) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.88f),
        shape = RoundedCornerShape(34.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(34.dp),
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
                SoftChip(
                    text = "TODAY'S PLAN",
                    color = Color.White.copy(alpha = 0.45f),
                    textColor = Color(0xFF5B4B7E),
                )
                Text(
                    text = "Level ${taskRewardState.currentLevel} • ${taskRewardState.currentLevelPoints} / ${taskRewardState.pointsNeededForNextLevel} LP",
                    color = ImpulsiveText.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Next\nwindow in\n${releasePlan.formattedTimeUntilNextWindow().removePrefix("Next window in ")}",
                color = Color(0xFF5B4B7E),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Complete one task to bring it closer.",
                color = Color(0xFF5B4B7E).copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(26.dp))

            PlannedMomentsRow(releasePlan = releasePlan)

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = {
                    taskRewardState.currentLevelPoints.toFloat() /
                        taskRewardState.pointsNeededForNextLevel.toFloat()
                },
                color = Color(0xFF5B4B7E),
                trackColor = Color(0xFF5B4B7E).copy(alpha = 0.14f),
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
private fun PlannedMomentsRow(releasePlan: ReleasePlanState) {
    val plannedWindows = releasePlan.formattedPlannedWindows()
    val nextWindowIndex = releasePlan.plannedWindowsToday.indexOf(releasePlan.nextReleaseWindow)
    val activeIndex = releasePlan.currentWindowIndex ?: nextWindowIndex.takeIf { it >= 0 }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        plannedWindows.forEachIndexed { index, time ->
            PlannedMoment(
                icon = plannedMomentIcon(
                    index = index,
                    activeIndex = activeIndex,
                ),
                time = time,
                active = index == activeIndex,
            )
            if (index != plannedWindows.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(1.dp)
                        .background(Color(0xFF5B4B7E).copy(alpha = 0.20f)),
                )
            }
        }
    }
}

private fun plannedMomentIcon(
    index: Int,
    activeIndex: Int?,
): ImageVector = when {
    activeIndex != null && index < activeIndex -> Icons.Filled.CheckCircleOutline
    activeIndex != null && index == activeIndex -> Icons.Filled.Schedule
    else -> Icons.Filled.Timer
}

@Composable
private fun PlannedMoment(
    icon: ImageVector,
    time: String,
    active: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF5B4B7E).copy(alpha = if (active) 0.95f else 0.46f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = time,
            color = Color(0xFF5B4B7E).copy(alpha = if (active) 0.95f else 0.58f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TaskChoiceCard(
    task: PsychologyTask,
    rewardStatus: TaskRewardStatus,
    recommended: Boolean,
    onCompleteTask: () -> Unit,
) {
    val cardShape = RoundedCornerShape(30.dp)
    val cardColor = if (recommended) Color(0xFFFFFCFF) else ImpulsiveSurface

    Surface(
        color = cardColor,
        shape = cardShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (recommended) Color(0xFF5B4B7E).copy(alpha = 0.16f) else ImpulsiveText.copy(alpha = 0.04f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (recommended) 9.dp else 6.dp,
                shape = cardShape,
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = if (recommended) 0.08f else 0.045f),
                spotColor = ImpulsiveText.copy(alpha = if (recommended) 0.10f else 0.06f),
            )
            .clickable { onCompleteTask() },
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recommended) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SoftIconCircle(color = task.iconBackground, icon = task.icon)
                        SoftChip(
                            text = "Recommended",
                            color = Color(0xFF6C5A8F),
                            textColor = Color.White.copy(alpha = 0.96f),
                            leadingIcon = Icons.Filled.AutoAwesome,
                        )
                    }
                } else {
                    SoftIconCircle(color = task.iconBackground, icon = task.icon)
                }
                SoftChip(
                    text = rewardStatus.displayChip(task.chip),
                    color = ImpulsiveText.copy(alpha = 0.055f),
                    textColor = ImpulsiveMutedText,
                )
            }

            if (recommended) {
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            Text(
                text = task.title,
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = task.description,
                color = ImpulsiveText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.88f),
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = rewardStatus.displayRewardLabel(),
                    color = ImpulsiveText.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (!recommended) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ImpulsiveText.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftIconCircle(
    color: Color,
    icon: ImageVector,
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
            tint = ImpulsiveText.copy(alpha = 0.78f),
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun TaskRewardStatus.displayChip(defaultChip: String): String = when {
    completedTodayCount > 0 -> "Today reward"
    !isFirstTimeBoostAvailable && defaultChip == "First-time boost" -> "Repeat reward"
    else -> defaultChip
}

private fun TaskRewardStatus.displayRewardLabel(): String {
    val points = if (currentWindowRewardAlreadyUsed) {
        minOf(2, displayLevelPoints)
    } else {
        displayLevelPoints
    }
    val waitReduction = if (currentWindowRewardAlreadyUsed) 0 else displayWaitReductionMinutes
    return if (waitReduction > 0) {
        "Cuts wait by ${waitReduction.formatMinutes()} • +$points LP"
    } else {
        "+$points LP"
    }
}

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"

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
private fun BottomNoteCard() {
    Surface(
        color = Color(0xFFFFFAFF),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.04f),
                spotColor = ImpulsiveText.copy(alpha = 0.06f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF5B4B7E),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "One main task can reduce the wait for this window. Extra tasks may still give smaller points.",
                color = ImpulsiveText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
