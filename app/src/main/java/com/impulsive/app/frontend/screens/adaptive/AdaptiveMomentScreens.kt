package com.impulsive.app.frontend.screens.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.session.adaptive.AdaptiveMomentUiMode
import com.impulsive.app.backend.session.adaptive.AdaptiveMomentUiState
import com.impulsive.app.backend.session.adaptive.AdaptiveMomentViewModel
import com.impulsive.app.backend.session.adaptive.AdaptiveRouteRequest
import com.impulsive.app.backend.session.adaptive.OptionalPromptUiState
import com.impulsive.app.frontend.components.UrgeRatingRow
import kotlinx.coroutines.delay

@Composable
fun AdaptiveMomentScreen(
    onRoute: (AdaptiveRouteRequest) -> Boolean,
    onExplain: (String) -> Unit,
    onSafeExit: () -> Unit,
    viewModel: AdaptiveMomentViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAfterReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(state.decision?.decisionId, state.mode) {
        if (
            state.mode != AdaptiveMomentUiMode.Loading &&
            state.mode != AdaptiveMomentUiMode.SafeFallback &&
            state.mode != AdaptiveMomentUiMode.GenericFailure
        ) {
            viewModel.onPresented()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.routeRequest) {
        state.routeRequest?.let {
            if (onRoute(it)) viewModel.consumeRoute() else viewModel.routeFailed()
        }
    }
    BackHandler {
        if (
            state.mode == AdaptiveMomentUiMode.PauseRunning &&
            state.decision?.completedAtMillis == null
        ) {
            viewModel.dismissCurrentIntervention()
        } else {
            onSafeExit()
        }
    }
    val openExplanation = {
        state.decision?.decisionId?.let(onExplain)
        Unit
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (state.mode) {
            AdaptiveMomentUiMode.Loading -> LoadingMoment(Modifier.padding(padding))
            AdaptiveMomentUiMode.FirstAttemptPause ->
                FirstAttemptPause(
                    busy = state.savingChoice,
                    onStart = { viewModel.choose(InterventionFamily.ShortPause) },
                    onOther = viewModel::showOtherOptions,
                    onWhy = openExplanation,
                    modifier = Modifier.padding(padding),
                )
            AdaptiveMomentUiMode.RepeatedChoice ->
                RepeatedChoice(
                    state = state,
                    onCue = viewModel::selectCue,
                    onSkipCue = viewModel::skipCue,
                    onReopenCue = viewModel::reopenCue,
                    onUrge = viewModel::selectUrge,
                    onSkipUrge = viewModel::skipUrge,
                    onReopenUrge = viewModel::reopenUrge,
                    onChoose = viewModel::choose,
                    onWhy = openExplanation,
                    modifier = Modifier.padding(padding),
                )
            AdaptiveMomentUiMode.PauseRunning ->
                PauseRunning(
                    startedAtMillis = state.decision?.startedAtMillis,
                    saving = state.savingOutcome,
                    onCompleted = viewModel::completeCurrentIntervention,
                    onAbandon = viewModel::dismissCurrentIntervention,
                    modifier = Modifier.padding(padding),
                )
            AdaptiveMomentUiMode.SafeFallback,
            AdaptiveMomentUiMode.GenericFailure,
            AdaptiveMomentUiMode.UnavailablePlan ->
                SafeFallback(onSafeExit, Modifier.padding(padding))
            AdaptiveMomentUiMode.MomentPlan -> LoadingMoment(Modifier.padding(padding))
        }
    }
}

@Composable
fun MomentPlanRunScreen(
    onChooseAnother: () -> Unit,
    onRoute: (AdaptiveRouteRequest) -> Boolean,
    onSafeExit: () -> Unit,
    viewModel: AdaptiveMomentViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.reload(momentPlanDelivery = true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reload(momentPlanDelivery = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.routeRequest) {
        state.routeRequest?.let {
            if (onRoute(it)) viewModel.consumeRoute() else viewModel.routeFailed()
        }
    }
    BackHandler {
        if (
            state.decision?.startedAtMillis != null &&
            state.decision?.completedAtMillis == null &&
            state.decision?.dismissedAtMillis == null
        ) {
            viewModel.dismissCurrentIntervention()
        } else {
            onSafeExit()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val plan = state.selectedPlan
        when {
            state.mode == AdaptiveMomentUiMode.Loading ->
                LoadingMoment(Modifier.padding(padding))
            state.mode == AdaptiveMomentUiMode.UnavailablePlan || plan == null ->
                SafeFallback(
                    onSafeExit = onChooseAnother,
                    modifier = Modifier.padding(padding),
                    title = "This Moment Plan is unavailable",
                    body = "Choose another support option to keep moving in a safer direction.",
                )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    "YOUR MOMENT PLAN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                PlanSection("When:", cueLabel(plan.momentCue))
                PlanSection("Then:", plan.actionText)
                PlanSection("Why:", plan.futureCueText)
                Spacer(Modifier.height(8.dp))
                val started = state.decision?.startedAtMillis != null
                val terminal =
                    state.decision?.completedAtMillis != null ||
                        state.decision?.dismissedAtMillis != null
                if (
                    started &&
                    !terminal &&
                    (
                        plan.actionType ==
                            com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.TextOnly ||
                            plan.actionType ==
                            com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.LaunchSelectedApp
                        )
                ) {
                    Text(
                        if (
                            plan.actionType ==
                            com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.LaunchSelectedApp
                        ) {
                            "Did you complete your Moment Plan?"
                        } else {
                            "Take the small action you prepared. Return when you are ready."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = viewModel::completeCurrentIntervention,
                        enabled = !state.savingOutcome,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (
                                plan.actionType ==
                                com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.LaunchSelectedApp
                            ) {
                                "Yes, I did"
                            } else {
                                "I've done this"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (
                                plan.actionType ==
                                com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.TextOnly
                            ) {
                                viewModel.dismissCurrentIntervention()
                            } else {
                                onSafeExit()
                            }
                        },
                        enabled = !state.savingOutcome,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (
                                plan.actionType ==
                                com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType.LaunchSelectedApp
                            ) {
                                "Not yet"
                            } else {
                                "Not now"
                            },
                        )
                    }
                } else if (!terminal) {
                    Button(
                    onClick = viewModel::startMomentPlan,
                    enabled = !state.routing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.routing) "Opening…" else "Start my plan")
                }
                }
                OutlinedButton(
                    onClick = onChooseAnother,
                    enabled = !state.routing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose another option")
                }
                TextButton(onClick = onSafeExit, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave this moment")
                }
            }
        }
    }
}

@Composable
private fun FirstAttemptPause(
    busy: Boolean,
    onStart: () -> Unit,
    onOther: () -> Unit,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Take a short pause", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Give yourself a moment before deciding what comes next.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStart,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Starting…" else "Start pause")
        }
        OutlinedButton(
            onClick = onOther,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose another support option")
        }
        TextButton(onClick = onWhy) { Text("Why this?") }
    }
}

@Composable
private fun RepeatedChoice(
    state: AdaptiveMomentUiState,
    onCue: (MomentCue) -> Unit,
    onSkipCue: () -> Unit,
    onReopenCue: () -> Unit,
    onUrge: (Int) -> Unit,
    onSkipUrge: () -> Unit,
    onReopenUrge: () -> Unit,
    onChoose: (InterventionFamily) -> Unit,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Choose a different direction", style = MaterialTheme.typography.headlineSmall)
        if (state.cuePromptState == OptionalPromptUiState.Skipped) {
            SkippedPromptRow(label = "Cue", onChange = onReopenCue)
        } else {
        Text("What seems closest to this moment? You can skip this.")
        MomentCue.entries.forEach { cue ->
            OutlinedButton(
                onClick = { onCue(cue) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(cueLabel(cue) + if (state.selectedCue == cue) " ✓" else "")
            }
        }
        TextButton(onClick = onSkipCue) { Text("Skip cue") }
        }
        if (state.urgePromptState == OptionalPromptUiState.Skipped) {
            SkippedPromptRow(label = "Rating", onChange = onReopenUrge)
        } else {
        UrgeRatingRow(
            label = "How strong is the urge right now?",
            selected = state.urgeRating,
            onSelect = { onUrge(it) },
        )
        TextButton(onClick = onSkipUrge) { Text("Skip rating") }
        }
        if (state.savingChoice) {
            Text(
                "Preparing your support option\u2026",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.decision?.assignment?.eligibleInterventions
            ?.filter { it != InterventionFamily.ShortPause }
            ?.forEach { intervention ->
                val suggested = intervention == state.assignedIntervention
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            interventionLabel(intervention),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (suggested) {
                            Text(
                                "Suggested for this moment",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(onClick = onWhy) { Text("Why this?") }
                        }
                        Button(
                            onClick = { onChoose(intervention) },
                            enabled = !state.savingChoice && !state.routing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.savingChoice) "Preparing\u2026" else "Choose")
                        }
                    }
                }
            }
    }
}

@Composable
private fun SkippedPromptRow(
    label: String,
    onChange: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "$label: Skipped",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onChange) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun PauseRunning(
    startedAtMillis: Long?,
    saving: Boolean,
    onCompleted: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingSeconds by produceState(
        initialValue = 30,
        key1 = startedAtMillis,
    ) {
        while (true) {
            val started = startedAtMillis ?: System.currentTimeMillis()
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            val remainingMillis = (30_000L - elapsed).coerceAtLeast(0L)
            value = ((remainingMillis + 999L) / 1_000L).toInt()
            if (remainingMillis == 0L) break
            delay(250L)
        }
    }
    LaunchedEffect(remainingSeconds, startedAtMillis) {
        if (startedAtMillis != null && remainingSeconds == 0) {
            onCompleted()
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pause for a moment", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            if (remainingSeconds > 0) {
                "Take three slow, natural breaths. $remainingSeconds seconds remain."
            } else {
                "The pause is finished. Choose what comes next deliberately."
            },
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = if (remainingSeconds > 0) onAbandon else onCompleted,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (remainingSeconds > 0) {
                    "End pause safely"
                } else if (saving) {
                    "Saving..."
                } else {
                    "Continue"
                },
            )
        }
    }
}

@Composable
private fun LoadingMoment(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Opening support…")
    }
}

@Composable
private fun SafeFallback(
    onSafeExit: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Support is temporarily unavailable",
    body: String = "Protection is still active. You can leave this moment safely and try again.",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSafeExit, modifier = Modifier.fillMaxWidth()) {
            Text("Leave this moment")
        }
    }
}

@Composable
private fun PlanSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun interventionLabel(intervention: InterventionFamily): String = when (intervention) {
    InterventionFamily.ShortPause -> "Short Pause"
    InterventionFamily.PivotGame -> "Pivot Game"
    InterventionFamily.PivotReading -> "Reset Reading"
    InterventionFamily.MomentPlan -> "My Moment Plan"
}

private fun cueLabel(cue: MomentCue?): String = when (cue) {
    MomentCue.Boredom -> "Boredom"
    MomentCue.Stress -> "Stress"
    MomentCue.BeingAlone -> "Being alone"
    MomentCue.Tiredness -> "Tiredness"
    MomentCue.AvoidingSomething -> "Avoiding something"
    MomentCue.AutomaticHabit -> "Automatic habit"
    null -> "Any difficult moment"
}
