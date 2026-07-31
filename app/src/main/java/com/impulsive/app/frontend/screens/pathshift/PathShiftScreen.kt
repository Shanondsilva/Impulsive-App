package com.impulsive.app.frontend.screens.pathshift

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftEvidenceStrength
import com.impulsive.app.backend.session.pathshift.PathShiftUiState
import com.impulsive.app.backend.session.pathshift.PathShiftViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.core.util.timeOfDayForHour
import com.impulsive.app.frontend.components.MindCoreScene
import com.impulsive.app.frontend.pathshift.PathShiftCharacterPresentation
import com.impulsive.app.frontend.pathshift.PathShiftExperienceState
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathShiftScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewPlan: (String) -> Unit,
    onPractisePlan: (String) -> Unit,
    viewModel: PathShiftViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    taskRewardViewModel: TaskRewardViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reward by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val character = PathShiftCharacterPresentation.create(
        currentLevel = reward.currentLevel,
        currentLevelPoints = reward.currentLevelPoints,
        experienceState = state.experience,
        hasPreparedPlan = state.cycle?.preparedPlanId != null,
        timeOfDay = timeOfDayForHour(LocalTime.now().hour),
        reducedMotion = reducedMotion,
    )
    var planPickerVisible by remember { mutableStateOf(false) }
    var stopConfirmationVisible by remember { mutableStateOf(false) }

    if (planPickerVisible) {
        AlertDialog(
            onDismissRequest = { planPickerVisible = false },
            title = { Text("Choose a Moment Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.enabledPlans.isEmpty()) {
                        Text("No enabled Moment Plans are available yet.")
                    }
                    state.enabledPlans.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    planPickerVisible = false
                                    viewModel.preparePlan(plan.planId)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.cycle?.preparedPlanId == plan.planId,
                                onClick = null,
                            )
                            Text(plan.title, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { planPickerVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (stopConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { stopConfirmationVisible = false },
            title = { Text("Stop this PathShift?") },
            text = {
                Text(
                    "This stops the current seven-day comparison. Your underlying " +
                        "Moment history remains unless you reset or delete it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopConfirmationVisible = false
                        viewModel.cancelActive()
                    },
                ) {
                    Text("Stop this PathShift")
                }
            },
            dismissButton = {
                TextButton(onClick = { stopConfirmationVisible = false }) {
                    Text("Keep PathShift")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Current Path") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MindCoreScene(
                    level = character.level,
                    timeOfDay = character.timeOfDay,
                    reducedMotion = character.reducedMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = character.contentDescription
                        },
                )

                state.message?.let {
                    CalmCard {
                        Text(it)
                        TextButton(onClick = viewModel::clearMessage) {
                            Text("Dismiss")
                        }
                    }
                }

                CurrentPathCard(state = state, onCreate = viewModel::createCycle)

                if (
                    state.experience == PathShiftExperienceState.Active ||
                    state.experience == PathShiftExperienceState.AwaitingReview
                ) {
                    NoticedCard(state)
                    PreparedPlanCard(
                        state = state,
                        onChoose = { planPickerVisible = true },
                        onView = onViewPlan,
                        onPractise = onPractisePlan,
                        onUseNewRevision = viewModel::useNewPlanRevision,
                        onRemove = viewModel::removePreparedPlan,
                    )
                }

                if (state.experience == PathShiftExperienceState.FinalisedReview) {
                    PathReviewCard(state.cycle)
                }

                WhyEstimateCard(state.cycle ?: state.preview?.let {
                    PathShiftCycle(
                        cycleId = "preview-only",
                        createdAtMillis = 0,
                        lookbackStartedAtMillis = it.lookbackStartedAtMillis,
                        lookbackEndedAtMillis = it.lookbackEndedAtMillis,
                        forecastWindowStartedAtMillis = it.forecastWindowStartedAtMillis,
                        forecastWindowEndsAtMillis = it.forecastWindowEndsAtMillis,
                        forecastPolicyVersion = it.factors.policyVersion,
                        evidenceStrength = it.evidenceStrength,
                        inputProtectedMomentCount = it.factors.protectedMomentCount,
                        inputDistinctDayCount = it.factors.distinctDayCount,
                        estimatedLowerCount = it.estimatedLowerCount,
                        estimatedUpperCount = it.estimatedUpperCount,
                        commonWindowStartMinute =
                            it.factors.commonTimeWindow?.startMinuteInclusive,
                        commonWindowEndMinute =
                            it.factors.commonTimeWindow?.endMinuteExclusive,
                    )
                })

                PrivacyControlsCard(
                    onReportUnhelpful = viewModel::reportEstimateUnhelpful,
                    onOpenSettings = onOpenSettings,
                )

                if (
                    state.experience == PathShiftExperienceState.Active ||
                    state.experience == PathShiftExperienceState.AwaitingReview
                ) {
                    TextButton(onClick = { stopConfirmationVisible = true }) {
                Text("Stop this PathShift")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CurrentPathCard(
    state: PathShiftUiState,
    onCreate: () -> Unit,
) {
    CalmCard {
        Text("CURRENT PATH", style = MaterialTheme.typography.labelLarge)
        when (state.experience) {
            PathShiftExperienceState.Disabled -> {
                Text("Future Path is off.")
                Text(
                    "Turn it on in Personal Support to use encrypted on-device " +
                        "Moment history for cautious estimates.",
                )
            }
            PathShiftExperienceState.InsufficientHistory -> {
                Text("Not enough history yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Impulsive needs several recorded moments across different days " +
                        "before showing a cautious estimate.",
                )
            }
            PathShiftExperienceState.ForecastReady -> {
                Text("A private seven-day estimate is ready.")
                Text(
                    "Create it deliberately to keep one fixed snapshot for the " +
                        "next seven-day comparison.",
                )
                Button(onClick = onCreate, enabled = !state.busy) {
                    Text("Create My Current Path")
                }
            }
            PathShiftExperienceState.Active,
            PathShiftExperienceState.AwaitingReview,
            -> state.cycle?.let { cycle ->
                Text(
                    "${cycle.estimatedLowerCount} to ${cycle.estimatedUpperCount}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("protected moments estimated during this seven-day period")
                Text(
                    "This is a cautious estimate based on recent patterns. " +
                        "It is not a promise about what will happen.",
                )
            }
            PathShiftExperienceState.FinalisedReview ->
                Text("This seven-day path is ready to review.")
            PathShiftExperienceState.Unavailable ->
            Text("PathShift is temporarily unavailable. Please try again.")
        }
    }
}

@Composable
private fun NoticedCard(state: PathShiftUiState) {
    val cycle = state.cycle ?: return
    CalmCard {
        Text("What Impulsive noticed", style = MaterialTheme.typography.titleMedium)
        Text("${cycle.inputProtectedMomentCount} protected moments were used.")
        Text("${cycle.inputDistinctDayCount} distinct recorded days were used.")
        Text("The lookback covered the previous 28 days.")
        cycle.commonWindowStartMinute?.let { start ->
            Text("A common broad time window was ${timeWindow(start)}.")
        }
        Text(
            when (cycle.evidenceStrength) {
                PathShiftEvidenceStrength.EarlyEstimate -> "An early estimate"
                PathShiftEvidenceStrength.CautiousEstimate ->
                    "Enough recent history for a cautious estimate"
                PathShiftEvidenceStrength.Insufficient -> "Not enough history yet"
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PreparedPlanCard(
    state: PathShiftUiState,
    onChoose: () -> Unit,
    onView: (String) -> Unit,
    onPractise: (String) -> Unit,
    onUseNewRevision: () -> Unit,
    onRemove: () -> Unit,
) {
    CalmCard {
        Text("Prepare another path", style = MaterialTheme.typography.titleMedium)
        Text("Choose a plan you would like to have ready if a suitable moment happens.")
        val plan = state.preparedPlan
        if (state.cycle?.preparedPlanId == null) {
            Button(onClick = onChoose) { Text("Choose a Moment Plan") }
        } else {
            Text(plan?.title ?: "Prepared Moment Plan", fontWeight = FontWeight.SemiBold)
                Text("Your plan is ready for this PathShift.")
            if (state.preparedPlanRevisionMismatch) {
                Text(
                    "This Moment Plan has changed since it was prepared for this PathShift.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Button(onClick = onUseNewRevision) { Text("Use the New Version") }
                OutlinedButton(onClick = onRemove) {
                Text("Keep Current PathShift Without a Prepared Plan")
                }
            } else if (plan != null) {
                Button(onClick = { onPractise(plan.planId) }) {
                    Text("Practise this Plan")
                }
                OutlinedButton(onClick = { onView(plan.planId) }) {
                    Text("View Plan")
                }
                TextButton(onClick = onChoose) { Text("Change Plan") }
            }
        }
    }
}

@Composable
private fun PathReviewCard(cycle: PathShiftCycle?) {
    cycle ?: return
    CalmCard {
        Text("Path Review", style = MaterialTheme.typography.titleLarge)
        Text(
            "Impulsive estimated ${cycle.estimatedLowerCount} to " +
                "${cycle.estimatedUpperCount} protected moments.",
        )
        Text("${cycle.observedProtectedMomentCount} protected moments were recorded.")
        Text("You selected your prepared plan ${cycle.preparedPlanSelectedCount} times.")
        Text("You started it ${cycle.preparedPlanStartedCount} times.")
        Text("You completed it ${cycle.preparedPlanCompletedCount} times.")
        Text("You dismissed it ${cycle.preparedPlanDismissedCount} times.")
        Text("Wrong Timing was recorded ${cycle.wrongTimingCount} times.")
        Text("A repeat was detected ${cycle.repeatDetectedCount} times.")
        Text(
            "This shows what was recorded. It does not prove that the plan caused the result.",
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WhyEstimateCard(cycle: PathShiftCycle?) {
    CalmCard {
        Text("Why this estimate?", style = MaterialTheme.typography.titleMedium)
        Text("Used", fontWeight = FontWeight.SemiBold)
        Text(
            if (cycle == null) {
                "Protected-moment count, distinct days, broad day and time patterns, " +
                    "and the recent history window."
            } else {
                "${cycle.inputProtectedMomentCount} protected moments, " +
                    "${cycle.inputDistinctDayCount} distinct days, broad time patterns, " +
                    "a 28-day window, and forecast policy version " +
                    "${cycle.forecastPolicyVersion}."
            },
        )
        Text("Not used", fontWeight = FontWeight.SemiBold)
        Text(
            "Protected source identity, URL, domain, package, journal content, email, " +
                "cloud behavioural profile, camera, microphone or location.",
        )
    }
}

@Composable
private fun PrivacyControlsCard(
    onReportUnhelpful: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    CalmCard {
        Text("Privacy and controls", style = MaterialTheme.typography.titleMedium)
            Text("PathShift runs on this device using encrypted Moment history.")
        OutlinedButton(onClick = onReportUnhelpful) {
            Text("Report this estimate as unhelpful")
        }
        TextButton(onClick = onOpenSettings) { Text("Turn off Future Path") }
        TextButton(onClick = onOpenSettings) { Text("Reset personal learning") }
        TextButton(onClick = onOpenSettings) { Text("Delete all Moment data") }
    }
}

@Composable
private fun CalmCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private fun timeWindow(startMinute: Int): String {
    fun label(minute: Int): String {
        if (minute == 24 * 60) return "midnight"
        val hour24 = minute / 60
        val hour12 = when (val value = hour24 % 12) {
            0 -> 12
            else -> value
        }
        val suffix = if (hour24 < 12) "AM" else "PM"
        return "$hour12:00 $suffix"
    }
    return "between ${label(startMinute)} and ${label(startMinute + 120)}"
}
