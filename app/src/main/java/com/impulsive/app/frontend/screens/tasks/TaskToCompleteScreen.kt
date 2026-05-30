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
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import java.time.LocalDateTime

private data class PsychologyTask(
    val taskType: PsychologyTaskType,
    val title: String,
    val description: String,
    val chip: String,
    val icon: ImageVector,
    val iconBackground: Color,
)

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
        taskType = PsychologyTaskType.ReflexOverride,
        title = "Reflex Override",
        description = "Break autopilot with a fast reaction challenge.",
        chip = "Fast control",
        icon = Icons.Filled.SportsEsports,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.58f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.PatternBreak,
        title = "Pattern Break",
        description = "Solve quick patterns to pull attention into logic.",
        chip = "Logic",
        icon = Icons.Outlined.Pattern,
        iconBackground = ImpulsivePhysical.copy(alpha = 0.62f),
    ),
    PsychologyTask(
        taskType = PsychologyTaskType.MindLesson,
        title = "Mind Lesson",
        description = "Finish one calm lesson and answer the final check.",
        chip = "Lesson",
        icon = Icons.AutoMirrored.Outlined.Article,
        iconBackground = ImpulsivePsychological.copy(alpha = 0.46f),
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
    onOpenPatternBreakTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenMindLessonTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    onOpenFutureSelfMessageTask: () -> Unit = {},
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val state by onboardingViewModel.state.collectAsState()
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
            .background(ImpulsiveBackground)
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

            Spacer(modifier = Modifier.height(14.dp))

            TodayPlanCard(
                releasePlan = displayReleasePlan,
                taskRewardState = taskRewardState,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose one task",
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "One wait cut per window",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val orderedTasks = orderedVisibleTasks(taskRewardState.recommendedTaskType)
            orderedTasks.forEachIndexed { index, task ->
                val rewardStatus = taskRewardState.taskStatuses.first { it.taskType == task.taskType }
                TaskChoiceCard(
                    task = task,
                    rewardStatus = rewardStatus,
                    recommended = task.taskType == taskRewardState.recommendedTaskType,
                    recommendationReason = taskRewardState.recommendedTaskReason.takeIf {
                        task.taskType == taskRewardState.recommendedTaskType
                    },
                    haptics = haptics,
                    onStartTask = {
                        when (task.taskType) {
                            PsychologyTaskType.ReflexOverride -> onOpenReflexOverrideTask()
                            PsychologyTaskType.PatternBreak -> onOpenPatternBreakTask()
                            PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
                            PsychologyTaskType.MindLesson -> onOpenMindLessonTask()
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

            Spacer(modifier = Modifier.height(18.dp))
            BottomNoteCard()
        }
    }
}

private fun orderedVisibleTasks(recommendedTaskType: PsychologyTaskType): List<PsychologyTask> {
    val visibleRecommended = VisiblePsychologyTasks.firstOrNull { it.taskType == recommendedTaskType }
    return if (visibleRecommended == null) {
        VisiblePsychologyTasks
    } else {
        listOf(visibleRecommended) + VisiblePsychologyTasks.filterNot { it.taskType == recommendedTaskType }
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
                text = "Complete one real action to reduce the wait.",
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
        "LP only this time"
    }

@Composable
private fun TodayPlanCard(
    releasePlan: ReleasePlanState,
    taskRewardState: TaskRewardState,
) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.82f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.07f),
                spotColor = ImpulsiveText.copy(alpha = 0.09f),
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
                    color = Color.White.copy(alpha = 0.42f),
                    textColor = Color(0xFF5B4B7E),
                )
                Text(
                    text = "Level ${taskRewardState.currentLevel} • ${taskRewardState.currentLevelPoints}/${taskRewardState.pointsNeededForNextLevel} LP",
                    color = ImpulsiveText.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next window",
                        color = Color(0xFF5B4B7E).copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = releasePlan.formattedTimeUntilNextWindow().removePrefix("Next window in "),
                        color = Color(0xFF5B4B7E),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Planned",
                        color = Color(0xFF5B4B7E).copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = releasePlan.formattedPlannedWindows().joinToString("  "),
                        color = Color(0xFF5B4B7E),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
private fun TaskChoiceCard(
    task: PsychologyTask,
    rewardStatus: TaskRewardStatus,
    recommended: Boolean,
    recommendationReason: String?,
    haptics: com.impulsive.app.frontend.utils.ImpulsiveHaptics,
    onStartTask: () -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    val cardColor = if (recommended) Color(0xFFFFFCFF) else ImpulsiveSurface

    Surface(
        color = cardColor,
        shape = cardShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (recommended) Color(0xFF5B4B7E).copy(alpha = 0.16f) else ImpulsiveText.copy(alpha = 0.05f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (recommended) 8.dp else 5.dp,
                shape = cardShape,
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = if (recommended) 0.07f else 0.04f),
                spotColor = ImpulsiveText.copy(alpha = if (recommended) 0.09f else 0.05f),
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
                    SoftIconCircle(color = task.iconBackground, icon = task.icon)
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
                            color = ImpulsiveText.copy(alpha = 0.055f),
                            textColor = ImpulsiveMutedText,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.34f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = task.title,
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = recommendationReason ?: task.description,
                color = ImpulsiveText.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(14.dp))

            RewardPillRow(rewardStatus = rewardStatus)
        }
    }
}

@Composable
private fun RewardPillRow(rewardStatus: TaskRewardStatus) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rewardStatus.visibleWaitCutMinutes() > 0) {
            RewardPill(
                icon = Icons.Outlined.AccessTime,
                text = "Cuts ${rewardStatus.visibleWaitCutMinutes().formatMinutes()}",
                background = ImpulsivePsychological.copy(alpha = 0.34f),
                content = Color(0xFF5B4B7E),
            )
        } else {
            RewardPill(
                icon = Icons.Filled.Timer,
                text = "Wait cut used",
                background = ImpulsiveText.copy(alpha = 0.055f),
                content = ImpulsiveMutedText,
            )
        }
        RewardPill(
            icon = Icons.Filled.AutoAwesome,
            text = "+${rewardStatus.visibleLevelPoints()} LP",
            background = Color(0xFFFFF9D8),
            content = Color(0xFF5B4B7E),
        )
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
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.035f),
                spotColor = ImpulsiveText.copy(alpha = 0.05f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF5B4B7E),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Rewards only apply after the task validates completion. Opening a task or tapping through does not count.",
                color = ImpulsiveText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun TaskRewardStatus.visibleWaitCutMinutes(): Int =
    if (currentWindowRewardAlreadyUsed) 0 else displayWaitReductionMinutes

private fun TaskRewardStatus.visibleLevelPoints(): Int =
    if (currentWindowRewardAlreadyUsed) minOf(2, displayLevelPoints) else displayLevelPoints

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
