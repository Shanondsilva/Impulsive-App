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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.formattedTimeUntilNextWindow
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PatternBreakSession
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStatus
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.PatternBreakViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.random.Random

private const val PatternBreakDurationSec = 60

private enum class PatternBreakView {
    Ready,
    Playing,
    Result,
}

private data class PatternPuzzle(
    val prompt: String,
    val pattern: String,
    val choices: List<String>,
    val correctIndex: Int,
)

@Composable
fun PatternBreakScreen(
    onExit: () -> Unit,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    patternBreakViewModel: PatternBreakViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val onboardingState by onboardingViewModel.state.collectAsState()
    val rewardStoreState by taskRewardViewModel.storeState.collectAsState()
    val completionResult by taskRewardViewModel.lastCompletionResult.collectAsState()
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
    val taskRewardState = rewardStoreState.toTaskRewardState(baseReleasePlan)
    val releasePlan = calculateRewardedReleasePlan(
        releasePlan = baseReleasePlan,
        adjustedNextReleaseWindow = taskRewardState.adjustedNextReleaseWindow,
        now = currentNow,
    )
    val rewardStatus = taskRewardState.taskStatuses.first {
        it.taskType == PsychologyTaskType.PatternBreak
    }

    var view by remember { mutableStateOf(PatternBreakView.Ready) }
    var startedAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var endedAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var timeLeft by remember { mutableIntStateOf(PatternBreakDurationSec) }
    var score by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var puzzle by remember { mutableStateOf(generatePatternPuzzle(0)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var rewardAppliedForScore by remember { mutableStateOf<Int?>(null) }
    var savedResult by remember { mutableStateOf(false) }

    val accuracy = if (attempts == 0) 0 else ((correctAnswers * 100f) / attempts).toInt()
    val validCompletion = view == PatternBreakView.Result &&
        timeLeft == 0 &&
        attempts >= 8 &&
        accuracy >= 50 &&
        startedAt != null &&
        endedAt != null

    LaunchedEffect(view) {
        if (view == PatternBreakView.Playing) {
            while (timeLeft > 0) {
                delay(1_000L)
                timeLeft -= 1
            }
            endedAt = LocalDateTime.now()
            view = PatternBreakView.Result
        }
    }

    LaunchedEffect(view, validCompletion, score) {
        if (view == PatternBreakView.Result && validCompletion && rewardAppliedForScore != score &&
            launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
        ) {
            rewardAppliedForScore = score
            taskRewardViewModel.completeTask(
                taskType = PsychologyTaskType.PatternBreak,
                releasePlan = releasePlan,
                now = LocalDateTime.now(),
                launchedFrom = "TASK_TO_COMPLETE",
                gameType = "PATTERN_BREAK",
                score = score,
                durationSec = PatternBreakDurationSec,
                validCompletion = true,
            )
        }
    }

    LaunchedEffect(view, validCompletion, completionResult) {
        if (view == PatternBreakView.Result && !savedResult) {
            val result = completionResult?.takeIf {
                it.taskType == PsychologyTaskType.PatternBreak && validCompletion
            }
            if (!validCompletion || result != null) {
                savedResult = true
                patternBreakViewModel.saveSession(
                    PatternBreakSession(
                        startedAt = startedAt ?: LocalDateTime.now(),
                        endedAt = endedAt ?: LocalDateTime.now(),
                        durationSec = PatternBreakDurationSec - timeLeft,
                        score = score,
                        accuracy = accuracy,
                        bestStreak = bestStreak,
                        attempts = attempts,
                        correctAnswers = correctAnswers,
                        validCompletion = validCompletion,
                        rewardWaitReductionMinutes = result?.waitReductionMinutes ?: 0,
                        rewardLevelPoints = result?.levelPointsAwarded ?: 0,
                        wasFirstTimeReward = validCompletion && !rewardStatus.completedEver,
                        wasSameDayRepeat = validCompletion && rewardStatus.completedTodayCount > 0,
                        appliedWaitReduction = (result?.waitReductionMinutes ?: 0) > 0,
                    ),
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ImpulsiveText,
                )
            }
            Text(
                text = "Pattern Break",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        when (view) {
            PatternBreakView.Ready -> PatternBreakReady(
                rewardStatus = rewardStatus,
                onBegin = {
                    taskRewardViewModel.clearLastCompletionResult()
                    startedAt = LocalDateTime.now()
                    endedAt = null
                    timeLeft = PatternBreakDurationSec
                    score = 0
                    attempts = 0
                    correctAnswers = 0
                    streak = 0
                    bestStreak = 0
                    feedback = null
                    rewardAppliedForScore = null
                    savedResult = false
                    puzzle = generatePatternPuzzle(0)
                    view = PatternBreakView.Playing
                },
            )

            PatternBreakView.Playing -> PatternBreakPlaying(
                timeLeft = timeLeft,
                score = score,
                streak = streak,
                puzzle = puzzle,
                feedback = feedback,
                onSelect = { selectedIndex ->
                    val correct = selectedIndex == puzzle.correctIndex
                    attempts += 1
                    if (correct) {
                        correctAnswers += 1
                        streak += 1
                        bestStreak = maxOf(bestStreak, streak)
                        score += 100 + streak * 15
                        feedback = "Keep going"
                    } else {
                        streak = 0
                        feedback = "Try the next one"
                    }
                    puzzle = generatePatternPuzzle(PatternBreakDurationSec - timeLeft)
                },
            )

            PatternBreakView.Result -> PatternBreakResult(
                score = score,
                accuracy = accuracy,
                bestStreak = bestStreak,
                correctAnswers = correctAnswers,
                attempts = attempts,
                validCompletion = validCompletion,
                completionResult = completionResult?.takeIf { it.taskType == PsychologyTaskType.PatternBreak },
                releasePlan = releasePlan,
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun PatternBreakReady(
    rewardStatus: TaskRewardStatus,
    onBegin: () -> Unit,
) {
    CenterTaskPanel {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(ImpulsivePsychological.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Pattern,
                contentDescription = null,
                tint = ImpulsiveText,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Pattern Break",
            color = ImpulsiveText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Solve quick patterns to shift your attention.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        RewardPreview(text = rewardStatus.displayRewardLabel())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onBegin,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5A8F)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Begin pattern task")
        }
    }
}

@Composable
private fun PatternBreakPlaying(
    timeLeft: Int,
    score: Int,
    streak: Int,
    puzzle: PatternPuzzle,
    feedback: String?,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CompactStat("Timer", "${timeLeft}s")
            CompactStat("Score", score.toString())
            CompactStat("Streak", streak.toString())
        }
        LinearProgressIndicator(
            progress = { timeLeft / PatternBreakDurationSec.toFloat() },
            color = Color(0xFF6C5A8F),
            trackColor = ImpulsivePsychological.copy(alpha = 0.35f),
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
        )
        Surface(
            color = ImpulsiveSurface,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = puzzle.prompt,
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = puzzle.pattern,
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (feedback != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = feedback,
                        color = Color(0xFF6C5A8F),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        puzzle.choices.forEachIndexed { index, choice ->
            Surface(
                color = if (index % 2 == 0) Color(0xFFFFFCFF) else ImpulsivePhysical.copy(alpha = 0.22f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) },
            ) {
                Text(
                    text = choice,
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PatternBreakResult(
    score: Int,
    accuracy: Int,
    bestStreak: Int,
    correctAnswers: Int,
    attempts: Int,
    validCompletion: Boolean,
    completionResult: TaskCompletionResult?,
    releasePlan: ReleasePlanState,
    onExit: () -> Unit,
) {
    CenterTaskPanel {
        Icon(
            imageVector = Icons.Filled.CheckCircleOutline,
            contentDescription = null,
            tint = Color(0xFF6C5A8F),
            modifier = Modifier.size(34.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Pattern Break complete",
            color = ImpulsiveText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!validCompletion) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Task finished, but full reward was not earned.",
                color = ImpulsiveMutedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ResultRow("Score", score.toString())
        ResultRow("Accuracy", "$accuracy%")
        ResultRow("Best streak", bestStreak.toString())
        ResultRow("Correct answers", "$correctAnswers / $attempts")
        ResultRow(
            "Wait reduction earned",
            if (validCompletion && completionResult != null && completionResult.waitReductionMinutes > 0) {
                completionResult.waitReductionMinutes.formatMinutes()
            } else {
                "-"
            },
        )
        ResultRow(
            "Level Points earned",
            if (validCompletion && completionResult != null) "+${completionResult.levelPointsAwarded} LP" else "-",
        )
        ResultRow("Next window time", releasePlan.formattedTimeUntilNextWindow().removePrefix("Next window in "))
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5A8F)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Return protected")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onExit,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("View next window")
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
            .padding(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = ImpulsiveMutedText, style = MaterialTheme.typography.labelSmall)
            Text(value, color = ImpulsiveText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RewardPreview(text: String) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = ImpulsiveText,
                modifier = Modifier.size(16.dp),
            )
            Text(text = text, color = ImpulsiveText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ImpulsiveMutedText, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = ImpulsiveText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterTaskPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ImpulsiveSurface,
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(30.dp),
                    clip = false,
                    ambientColor = ImpulsiveText.copy(alpha = 0.06f),
                    spotColor = ImpulsiveText.copy(alpha = 0.08f),
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { content() },
            )
        }
    }
}

private fun generatePatternPuzzle(elapsedSec: Int): PatternPuzzle {
    val tier = when {
        elapsedSec < 20 -> 0
        elapsedSec < 45 -> 1
        else -> 2
    }
    return when (Random.nextInt(4)) {
        0 -> oddSymbolPuzzle(tier)
        1 -> missingShapePuzzle(tier)
        2 -> sequencePuzzle(tier)
        else -> repeatedPatternPuzzle(tier)
    }
}

private fun oddSymbolPuzzle(tier: Int): PatternPuzzle {
    val symbols = listOf("●", "◆", "▲", "■", "✦", "○")
    val base = symbols.random()
    val odd = symbols.filterNot { it == base }.random()
    val oddIndex = Random.nextInt(4)
    val pattern = List(4) { index -> if (index == oddIndex) odd else base }.joinToString("  ")
    val choices = listOf("1", "2", "3", "4")
    return PatternPuzzle("Find the odd symbol", pattern, choices, oddIndex)
}

private fun missingShapePuzzle(tier: Int): PatternPuzzle {
    val shapes = if (tier == 0) listOf("○", "△", "□") else listOf("○", "△", "□", "◇")
    val sequence = List(5) { shapes[it % shapes.size] }
    val missingIndex = if (tier < 2) 3 else Random.nextInt(1, 4)
    val answer = sequence[missingIndex]
    val pattern = sequence.mapIndexed { index, value -> if (index == missingIndex) "?" else value }.joinToString("  ")
    val choices = (shapes + listOf("✦")).distinct().shuffled().take(4).let { options ->
        if (answer in options) options else options.drop(1) + answer
    }.shuffled()
    return PatternPuzzle("Choose the missing shape", pattern, choices, choices.indexOf(answer))
}

private fun sequencePuzzle(tier: Int): PatternPuzzle {
    val items = if (tier < 2) listOf("lavender", "blue", "gold") else listOf("lavender", "blue", "gold", "mint")
    val sequence = List(5) { items[it % items.size] }
    val answer = sequence.last()
    val pattern = sequence.dropLast(1).joinToString("  →  ") + "  →  ?"
    val choices = items.shuffled()
    return PatternPuzzle("Complete the sequence", pattern, choices, choices.indexOf(answer))
}

private fun repeatedPatternPuzzle(tier: Int): PatternPuzzle {
    val units = listOf("A B A", "○ △ ○", "1 2 1", "◆ ✦ ◆")
    val unit = units.random()
    val answer = unit.substringAfterLast(" ")
    val pattern = "$unit   $unit   ${unit.substringBeforeLast(" ")} ?"
    val choices = listOf(answer, "B", "△", "✦").distinct().shuffled()
    return PatternPuzzle("Match the repeated pattern", pattern, choices, choices.indexOf(answer))
}

private fun TaskRewardStatus.displayRewardLabel(): String {
    val points = if (currentWindowRewardAlreadyUsed) minOf(2, displayLevelPoints) else displayLevelPoints
    val waitReduction = if (currentWindowRewardAlreadyUsed) 0 else displayWaitReductionMinutes
    return if (waitReduction > 0) {
        "Cuts wait by ${waitReduction.formatMinutes()} • +$points LP"
    } else {
        "+$points LP"
    }
}

private fun Int.formatMinutes(): String =
    if (this >= 60 && this % 60 == 0) "${this / 60}h" else "$this min"
