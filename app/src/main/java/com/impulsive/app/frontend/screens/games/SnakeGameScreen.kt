package com.impulsive.app.frontend.screens.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.game.SnakeCell
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGameHistory
import com.impulsive.app.backend.domain.game.SnakeGamePhase
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.game.SnakeGameState
import com.impulsive.app.backend.domain.game.SnakeGameStorePersistenceState
import com.impulsive.app.backend.domain.game.SnakeGameUiState
import com.impulsive.app.backend.domain.game.SnakeGameView
import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import com.impulsive.app.backend.domain.game.isGameStoreResultDurable
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import com.impulsive.app.backend.domain.model.tasks.calculateRewardedReleasePlan
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.backend.session.game.SnakeGameViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.frontend.components.GameSoundToggle
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDateTime

/** Whether this task's reward has reached the durable receipt ledger. */
internal enum class SnakeTaskRewardPersistenceState {
    NotRequired,
    WaitingForGameStore,
    Saving,
    Persisted,
    RetryableFailure,
}

/**
 * Snake.
 *
 * Active from SNAKE-04 for both the recovery hub and Task to Complete.
 */
@Composable
fun SnakeGameScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAnother: () -> Unit = {},
    onAdaptiveExit: ((completed: Boolean) -> Unit)? = null,
    launchSource: ReflexGameLaunchSource = ReflexGameLaunchSource.RECOVERY_GAME,
    gameLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: SnakeGameViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taskCompletionResult by taskRewardViewModel.lastCompletionResult.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val taskLaunch = launchSource == ReflexGameLaunchSource.TASK_TO_COMPLETE
    /*
     * A task-launched round that ended without a valid completion has no exit:
     * the only way on is to finish a real round. Hub rounds keep their exit.
     */
    val mustContinue = taskLaunch &&
        uiState.view == SnakeGameView.Result &&
        uiState.result?.validCompletion != true

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
    val taskCompletionToken = if (taskLaunch && uiState.result?.validCompletion == true) {
        viewModel.taskRewardCompletionToken()
    } else {
        null
    }

    /** Set only after the repository call returns, never when it starts. */
    var persistedTaskCompletionToken by remember(launchSource) {
        mutableStateOf<String?>(null)
    }
    var taskRewardRetryGeneration by remember(taskCompletionToken) { mutableIntStateOf(0) }
    var taskRewardPersistenceState by remember(taskCompletionToken, launchSource) {
        mutableStateOf(
            if (taskLaunch && taskCompletionToken != null) {
                SnakeTaskRewardPersistenceState.WaitingForGameStore
            } else {
                SnakeTaskRewardPersistenceState.NotRequired
            },
        )
    }

    LaunchedEffect(gameLaunchContext) {
        if (!viewModel.configureLaunchContext(gameLaunchContext)) {
            onExit()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /*
     * The engine owns all timing; this only asks the ViewModel to advance once
     * per rendered frame. There is no second clock in the UI.
     */
    LaunchedEffect(uiState.view) {
        while (isActive && uiState.view == SnakeGameView.Playing) {
            withFrameMillis { }
            viewModel.tick()
        }
    }

    /*
     * Rewards are keyed to the ViewModel's stable session token, never to the
     * score, so a recomposition or restored result cannot double-award.
     */
    /*
     * The reward is only attempted after the Game Store receipt is durable, and
     * the token is marked successful only once the repository call returns — so
     * a cancelled coroutine leaves a retryable state rather than a silent loss.
     */
    LaunchedEffect(
        taskCompletionToken,
        launchSource,
        uiState.gameStorePersistenceState,
        taskRewardRetryGeneration,
    ) {
        val result = uiState.result ?: return@LaunchedEffect

        if (!taskLaunch || !result.validCompletion || taskCompletionToken == null) {
            taskRewardPersistenceState = SnakeTaskRewardPersistenceState.NotRequired
            return@LaunchedEffect
        }

        if (uiState.gameStorePersistenceState != SnakeGameStorePersistenceState.Persisted) {
            taskRewardPersistenceState = SnakeTaskRewardPersistenceState.WaitingForGameStore
            return@LaunchedEffect
        }

        if (persistedTaskCompletionToken == taskCompletionToken) {
            taskRewardPersistenceState = SnakeTaskRewardPersistenceState.Persisted
            return@LaunchedEffect
        }

        taskRewardPersistenceState = SnakeTaskRewardPersistenceState.Saving
        taskRewardViewModel.clearLastCompletionResult()

        try {
            taskRewardViewModel.completeTaskAndAwait(
                taskType = PsychologyTaskType.Snake,
                releasePlan = releasePlan,
                now = LocalDateTime.now(),
                launchedFrom = "TASK_TO_COMPLETE",
                gameType = ScoreGameType.Snake.id,
                score = result.score,
                durationSec = result.durationSec,
                validCompletion = true,
                completionToken = taskCompletionToken,
            )

            // Only after the repository confirmed the write.
            persistedTaskCompletionToken = taskCompletionToken
            taskRewardPersistenceState = SnakeTaskRewardPersistenceState.Persisted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            taskRewardPersistenceState = SnakeTaskRewardPersistenceState.RetryableFailure
        }
    }

    // Support-cycle ownership must resolve before the screen is left.
    val exitSafely: () -> Unit = { viewModel.finishSupportCycleAfterChoice(onExit) }

    fun exitWithAdaptiveOutcome(completed: Boolean) {
        viewModel.finishSupportCycleAfterChoice {
            onAdaptiveExit?.invoke(completed) ?: onExit()
        }
    }

    /*
     * Every task Result — valid or not — progresses only through its explicit
     * actions, so Back is withheld there rather than bypassing them.
     */
    val taskResultLocksBack = taskLaunch && uiState.view == SnakeGameView.Result

    val resultPersistenceBlocksBack = uiState.view == SnakeGameView.Result &&
        (
            !uiState.isGameStoreResultDurable ||
                (
                    taskLaunch &&
                        uiState.result?.validCompletion == true &&
                        taskRewardPersistenceState !=
                        SnakeTaskRewardPersistenceState.Persisted
                    )
            )

    val backAllowed = !taskResultLocksBack && !resultPersistenceBlocksBack

    // An adaptive task left before completing reports completed = false.
    val exitCurrentFlow: () -> Unit = if (taskLaunch) {
        { exitWithAdaptiveOutcome(completed = false) }
    } else {
        exitSafely
    }

    BackHandler(enabled = uiState.view != SnakeGameView.Walked) {
        if (backAllowed) exitCurrentFlow()
    }

    SnakeGameScreenContent(
        uiState = uiState,
        soundEnabled = appSettingsState.soundEffectsEnabled,
        modifier = modifier,
        onSoundToggle = appSettingsViewModel::setSoundEffectsEnabled,
        onDirection = viewModel::changeDirection,
        onResume = viewModel::resume,
        onUrgeAfterSelected = viewModel::setUrgeAfter,
        onWalkAway = viewModel::walkAway,
        onPlayAgain = { viewModel.replayWithRemainingBudget {} },
        onPlayAnother = { viewModel.continueWithAnotherGame(onPlayAnother) },
        onBack = exitCurrentFlow,
        // Walk Away already finished the support action; do not finish twice.
        onDone = onExit,
        taskLaunch = taskLaunch,
        mustContinue = mustContinue,
        backAllowed = backAllowed,
        gameStorePersistenceState = uiState.gameStorePersistenceState,
        taskRewardPersistenceState = taskRewardPersistenceState,
        onRetryGameStorePersistence = viewModel::retryResultPersistence,
        onRetryTaskReward = { taskRewardRetryGeneration += 1 },
        // A stale result from an earlier task must not be shown mid-save.
        taskCompletionResult = taskCompletionResult
            ?.takeIf {
                it.taskType == PsychologyTaskType.Snake &&
                    taskRewardPersistenceState == SnakeTaskRewardPersistenceState.Persisted
            },
        nextWindowLabel = releasePlan.nextReleaseWindow.toImpulsiveCompactTime(),
        onTaskReturnProtected = { exitWithAdaptiveOutcome(completed = true) },
        onTaskViewNextWindow = { exitWithAdaptiveOutcome(completed = true) },
    )
}

@Composable
internal fun SnakeGameScreenContent(
    uiState: SnakeGameUiState,
    soundEnabled: Boolean,
    modifier: Modifier = Modifier,
    onSoundToggle: (Boolean) -> Unit = {},
    onDirection: (SnakeDirection) -> Unit = {},
    onResume: () -> Unit = {},
    onUrgeAfterSelected: (Int) -> Unit = {},
    onWalkAway: () -> Unit = {},
    onPlayAgain: () -> Unit = {},
    onPlayAnother: () -> Unit = {},
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    taskLaunch: Boolean = false,
    mustContinue: Boolean = false,
    backAllowed: Boolean = true,
    gameStorePersistenceState: SnakeGameStorePersistenceState =
        SnakeGameStorePersistenceState.NotRequired,
    taskRewardPersistenceState: SnakeTaskRewardPersistenceState =
        SnakeTaskRewardPersistenceState.NotRequired,
    onRetryGameStorePersistence: () -> Unit = {},
    onRetryTaskReward: () -> Unit = {},
    taskCompletionResult: TaskCompletionResult? = null,
    nextWindowLabel: String? = null,
    onTaskReturnProtected: () -> Unit = {},
    onTaskViewNextWindow: () -> Unit = {},
) {
    AdaptiveGameContainer(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (backAllowed) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Text(
                    text = "Snake",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                GameSoundToggle(
                    enabled = soundEnabled,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onToggle = onSoundToggle,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.view) {
                SnakeGameView.Ready,
                SnakeGameView.Playing,
                SnakeGameView.Paused,
                -> SnakePlayPanel(
                    uiState = uiState,
                    onDirection = onDirection,
                    onResume = onResume,
                )

                SnakeGameView.Result -> SnakeResultPanel(
                    uiState = uiState,
                    onUrgeAfterSelected = onUrgeAfterSelected,
                    onWalkAway = onWalkAway,
                    onPlayAgain = onPlayAgain,
                    onPlayAnother = onPlayAnother,
                    onBack = onBack,
                    taskLaunch = taskLaunch,
                    mustContinue = mustContinue,
                    backAllowed = backAllowed,
                    gameStorePersistenceState = gameStorePersistenceState,
                    taskRewardPersistenceState = taskRewardPersistenceState,
                    onRetryGameStorePersistence = onRetryGameStorePersistence,
                    onRetryTaskReward = onRetryTaskReward,
                    taskCompletionResult = taskCompletionResult,
                    nextWindowLabel = nextWindowLabel,
                    onTaskReturnProtected = onTaskReturnProtected,
                    onTaskViewNextWindow = onTaskViewNextWindow,
                )

                SnakeGameView.Walked -> SnakeWalkedPanel(
                    score = uiState.result?.score ?: 0,
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun SnakePlayPanel(
    uiState: SnakeGameUiState,
    onDirection: (SnakeDirection) -> Unit,
    onResume: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SnakeHud(
            timeLeftSeconds = uiState.timeLeftSeconds,
            score = uiState.gameState?.score ?: uiState.result?.score ?: 0,
            personalBest = uiState.history.personalBest,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // The board owns a weighted box, so gameplay never resizes the shell.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            uiState.gameState?.let { state ->
                SnakeGameBoard(
                    state = state,
                    view = uiState.view,
                    onDirection = onDirection,
                    onResume = onResume,
                )
            }
        }
    }
}

@Composable
private fun SnakeHud(
    timeLeftSeconds: Int,
    score: Int,
    personalBest: Int,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SnakeHudItem("Time", "${timeLeftSeconds}s", Modifier.weight(1f))
        SnakeHudItem("Score", score.toString(), Modifier.weight(1f))
        SnakeHudItem("Best", personalBest.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun SnakeHudItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SnakeResultPanel(
    uiState: SnakeGameUiState,
    onUrgeAfterSelected: (Int) -> Unit,
    onWalkAway: () -> Unit,
    onPlayAgain: () -> Unit,
    onPlayAnother: () -> Unit,
    onBack: () -> Unit,
    taskLaunch: Boolean,
    mustContinue: Boolean,
    backAllowed: Boolean,
    gameStorePersistenceState: SnakeGameStorePersistenceState,
    taskRewardPersistenceState: SnakeTaskRewardPersistenceState,
    onRetryGameStorePersistence: () -> Unit,
    onRetryTaskReward: () -> Unit,
    taskCompletionResult: TaskCompletionResult?,
    nextWindowLabel: String?,
    onTaskReturnProtected: () -> Unit,
    onTaskViewNextWindow: () -> Unit,
) {
    val result = uiState.result ?: return
    val isNewBest = result.validCompletion &&
        result.score > result.previousBest &&
        result.score > 0

    // Scrollable so large accessibility text never clips the actions.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = snakeResultTitle(result),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "How strong is the urge now?",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        SnakeUrgeRatingRow(
            selectedRating = uiState.urgeAfterRating,
            onSelected = onUrgeAfterSelected,
        )

        if (isNewBest) {
            Surface(
                color = ImpulsivePsychological.copy(alpha = 0.28f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "New best!",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            text = result.score.toString(),
            color = ImpulsivePsychological,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnakeStatRow("Personal best", uiState.history.personalBest.toString())
            SnakeStatRow("Fruits eaten", result.fruitsEaten.toString())
            SnakeStatRow("Time survived", "${result.durationSec}s")
            SnakeStatRow("Previous score", result.previousScore?.toString() ?: "-")
        }

        when (gameStorePersistenceState) {
            SnakeGameStorePersistenceState.Pending,
            SnakeGameStorePersistenceState.NotRequired,
            -> {
                // No action may run until the store receipt is durable.
                Text(
                    text = "Saving game progress…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                return@Column
            }

            SnakeGameStorePersistenceState.RetryableFailure -> {
                Text(
                    text = "This result hasn't been saved yet. Try again before leaving.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                OutlinedButton(
                    onClick = onRetryGameStorePersistence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Retry saving")
                }

                return@Column
            }

            SnakeGameStorePersistenceState.Persisted -> Unit
        }

        if (taskLaunch && result.validCompletion) {
            when (taskRewardPersistenceState) {
                SnakeTaskRewardPersistenceState.NotRequired,
                SnakeTaskRewardPersistenceState.WaitingForGameStore,
                -> {
                    Text(
                        text = "Saving your progress…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )

                    return@Column
                }

                SnakeTaskRewardPersistenceState.Saving -> {
                    Text(
                        text = "Saving your task reward…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )

                    return@Column
                }

                SnakeTaskRewardPersistenceState.RetryableFailure -> {
                    Text(
                        text = "Your task reward hasn't been saved yet. " +
                            "Try again before leaving.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )

                    OutlinedButton(
                        onClick = onRetryTaskReward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Retry reward")
                    }

                    return@Column
                }

                SnakeTaskRewardPersistenceState.Persisted -> Unit
            }
        }

        if (taskLaunch && taskCompletionResult != null && result.validCompletion) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SnakeStatRow(
                    "Wait reduced",
                    "${taskCompletionResult.waitReductionMinutes} min",
                )
                SnakeStatRow(
                    "Level Points earned",
                    taskCompletionResult.levelPointsAwarded.toString(),
                )
                SnakeStatRow(
                    "Next window",
                    nextWindowLabel ?: "-",
                )
            }
        }

        val invalidMessage = snakeInvalidCompletionMessage(result)

        if (taskLaunch && result.validCompletion) {
            /*
             * Task completion is not a Pivot Walk Away: it must not resolve the
             * session through walkAway().
             */
            Button(
                onClick = onTaskReturnProtected,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = Color(0xFF2F2637),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Return protected")
            }

            OutlinedButton(
                onClick = onTaskViewNextWindow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("View next window")
            }
        } else if (invalidMessage != null) {
            Text(
                text = invalidMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = Color(0xFF2F2637),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Play same game")
            }
        } else {
            Button(
                onClick = onWalkAway,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = Color(0xFF2F2637),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Walk away")
            }

            Text(
                text = "Choosing to stop is the strongest move.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            OutlinedButton(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Play again")
            }
        }

        if (!(taskLaunch && result.validCompletion)) {
            OutlinedButton(
                onClick = onPlayAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Play another")
            }
        }

        if (backAllowed && !(taskLaunch && result.validCompletion)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun SnakeStatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Snake's own urge rating: unlike the shared fixed-size row this reflows at
 * large font scales instead of clipping. Each value is a real selectable
 * target with an accessible name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnakeUrgeRatingRow(
    selectedRating: Int?,
    onSelected: (Int) -> Unit,
) {
    /*
     * The circle grows with the font scale rather than shrinking the label, so
     * a two-digit "10" stays legible and round instead of clipping.
     */
    val fontScale = LocalDensity.current.fontScale
    val visibleDiameter = when {
        fontScale >= 1.75f -> 48.dp
        fontScale >= 1.30f -> 38.dp
        else -> 30.dp
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (0..10).forEach { rating ->
            val isSelected = selectedRating == rating

            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelected(rating) },
                    )
                    .semantics { contentDescription = "Urge rating $rating" },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = if (isSelected) {
                        ImpulsivePsychological
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(visibleDiameter),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = rating.toString(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnakeWalkedPanel(
    score: Int,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "You walked away.",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = score.toString(),
            color = ImpulsivePsychological,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "That's the skill that counts: noticing the pull and stepping back.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = Color(0xFF2F2637),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Done")
        }
    }
}

// ----------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------

/** A static board for previews only; the engine still owns real gameplay. */
private fun previewSnakeState(withFood: Boolean = false): SnakeGameState = SnakeGameState(
    phase = if (withFood) SnakeGamePhase.Playing else SnakeGamePhase.Ready,
    snake = listOf(
        SnakeCell(9, 12),
        SnakeCell(8, 12),
        SnakeCell(7, 12),
        SnakeCell(6, 12),
    ),
    food = if (withFood) SnakeCell(13, 8) else null,
    direction = if (withFood) SnakeDirection.Right else null,
    queuedDirections = emptyList(),
    fruitsEaten = if (withFood) 4 else 0,
    score = if (withFood) 40 else 0,
    tickIntervalMillis = 220L,
    endReason = null,
)

private fun previewResult(
    validCompletion: Boolean,
): SnakeGameResult = SnakeGameResult(
    score = if (validCompletion) 120 else 0,
    fruitsEaten = if (validCompletion) 12 else 0,
    previousBest = 80,
    previousScore = 60,
    durationSec = if (validCompletion) 62 else 5,
    elapsedDurationMillis = if (validCompletion) 62_000L else 5_000L,
    endReason = if (validCompletion) {
        SnakeRoundEndReason.TimeLimit
    } else {
        SnakeRoundEndReason.SelfCollision
    },
    validCompletion = validCompletion,
)

@Preview(name = "Snake ready", showBackground = true)
@Composable
private fun SnakeReadyPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Ready,
                gameState = previewSnakeState(),
                timeLeftSeconds = 90,
            ),
            soundEnabled = true,
        )
    }
}

@Preview(name = "Snake playing", showBackground = true)
@Composable
private fun SnakePlayingPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Playing,
                gameState = previewSnakeState(withFood = true),
                timeLeftSeconds = 74,
                history = SnakeGameHistory(personalBest = 150, previousScore = 90),
            ),
            soundEnabled = true,
        )
    }
}

@Preview(name = "Snake playing dark", showBackground = true, uiMode = 32)
@Composable
private fun SnakePlayingDarkPreview() {
    ImpulsiveTheme(darkTheme = true) {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Playing,
                gameState = previewSnakeState(withFood = true),
                timeLeftSeconds = 41,
            ),
            soundEnabled = false,
        )
    }
}

@Preview(name = "Snake result valid", showBackground = true)
@Composable
private fun SnakeResultValidPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Result,
                result = previewResult(validCompletion = true),
                history = SnakeGameHistory(personalBest = 120, previousScore = 120),
            ),
            soundEnabled = true,
        )
    }
}

@Preview(name = "Snake result invalid", showBackground = true)
@Composable
private fun SnakeResultInvalidPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Result,
                result = previewResult(validCompletion = false),
                history = SnakeGameHistory(personalBest = 80, previousScore = 60),
            ),
            soundEnabled = true,
        )
    }
}

@Preview(name = "Snake result 200% font", showBackground = true, fontScale = 2f)
@Composable
private fun SnakeResultLargeFontPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Result,
                result = previewResult(validCompletion = true),
                history = SnakeGameHistory(personalBest = 120, previousScore = 120),
                // The hardest label: two digits, selected, at double font size.
                urgeAfterRating = 10,
            ),
            soundEnabled = true,
        )
    }
}

/**
 * Proves the wrap seam draws as two edge stubs rather than one line across the
 * board, and that a row-0 fruit renders completely inside it.
 */
@Preview(name = "Snake edge wrap + top fruit", showBackground = true)
@Composable
private fun SnakeEdgeWrapPreview() {
    ImpulsiveTheme {
        SnakeGameScreenContent(
            uiState = SnakeGameUiState(
                view = SnakeGameView.Playing,
                gameState = SnakeGameState(
                    phase = SnakeGamePhase.Playing,
                    snake = listOf(
                        SnakeCell(0, 12),
                        SnakeCell(17, 12),
                        SnakeCell(16, 12),
                        SnakeCell(15, 12),
                        SnakeCell(15, 13),
                        SnakeCell(15, 14),
                    ),
                    food = SnakeCell(5, 0),
                    direction = SnakeDirection.Right,
                    queuedDirections = emptyList(),
                    fruitsEaten = 2,
                    score = 20,
                    tickIntervalMillis = 210L,
                    endReason = null,
                ),
                timeLeftSeconds = 58,
            ),
            soundEnabled = true,
        )
    }
}
