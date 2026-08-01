package com.impulsive.app.frontend.screens.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.R
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.backend.domain.engine.adaptive.RecentSupportRecord
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksForMeBuilder
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksForMeReport
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksInterventionSummary
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.session.adaptive.WhatWorksForMeViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatWorksForMeScreen(
    onBack: () -> Unit,
    viewModel: WhatWorksForMeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    var showInformation by rememberSaveable {
        mutableStateOf(false)
    }
    if (showInformation) {
        WhatWorksForMeInfoDialog(onDismiss = { showInformation = false })
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        ImpulsiveAmbientBackground(
            modifier = Modifier.fillMaxSize(),
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.what_works_for_me_title,
                                ),
                                color = colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            IconButton(
                                onClick = {
                                    showInformation = true
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(
                                        R.string.what_works_for_me_info_description,
                                    ),
                                    tint = colorScheme.onBackground,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = colorScheme.onBackground,
                        titleContentColor = colorScheme.onBackground,
                        actionIconContentColor = colorScheme.onBackground,
                    ),
                )
            },
        ) { padding ->
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.report != null -> WhatWorksContent(
                    report = checkNotNull(state.report),
                    modifier = Modifier.padding(padding),
                )
                else -> Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        state.message ?: "Your personal patterns could not be loaded.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatWorksForMeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.what_works_for_me_info_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.what_works_for_me_info_body_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.what_works_for_me_info_body_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.what_works_for_me_info_body_more_useful),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.what_works_for_me_info_confirm),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun WhatWorksContent(
    report: WhatWorksForMeReport,
    modifier: Modifier = Modifier,
) {
    var showRecentHistory by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Your support history stays encrypted on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                report.evidenceQualityTier.plainLanguageExplanation,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (report.empty) {
            item {
                InsightCard {
                    Text(
                        "Impulsive will show cautious patterns here after you have used several support options.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            return@LazyColumn
        }
        item {
            SectionHeading("SUMMARY")
            CountRow("Protected moments recorded", report.summary.protectedMoments)
            CountRow("Support options Started", report.summary.supportOptionsStarted)
            CountRow("Support options Completed", report.summary.supportOptionsCompleted)
            CountRow("Support options Dismissed", report.summary.supportOptionsDismissed)
            CountRow("Feedback answers provided", report.summary.feedbackAnswersProvided)
            CountRow("Moment Plans practised", report.summary.momentPlansPractised)
        }
        report.interventions.forEach { summary ->
            item(key = summary.intervention.name) {
                InterventionCard(summary)
            }
        }
        if (
            report.withinOptionPatterns.isNotEmpty() ||
            report.primaryComparison != null ||
            report.differentChoiceCount > 0
        ) {
            item {
                SectionHeading("CAUTIOUS PATTERNS")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.withinOptionPatterns.forEach { Text(it) }
                    report.primaryComparison?.let { Text(it) }
                    if (report.differentChoiceCount > 0) {
                        Text(
                            "You selected a different option from the suggestion in " +
                                "${report.differentChoiceCount} moments.",
                        )
                    }
                }
            }
        }
        item {
            SectionHeading("PRACTICE AND REAL MOMENTS")
            InsightCard {
                CountRow("Completed practices", report.practice.completedRehearsals)
                CountRow("Plans practised", report.practice.plansPractised)
                CountRow(
                    "Later uses after practice",
                    report.practice.laterRealUsesWithinSevenDays,
                )
                report.practice.mostRecentlyPractisedPlanTitle?.let {
                    Text(
                        "Most recently practised: $it",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    if (report.practice.hasRecentRehearsal) {
                        "You have practised a plan recently."
                    } else {
                        "There is not enough recent practice history yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Later use is a factual observation. It does not show that practice caused the use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (report.recentHistory.isNotEmpty()) {
            item {
                TextButton(
                    onClick = { showRecentHistory = !showRecentHistory },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (showRecentHistory) "Hide recent support records" else "Show recent support records")
                }
            }
            if (showRecentHistory) {
                report.recentHistory.forEach { record ->
                    item {
                        RecentRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun InterventionCard(summary: WhatWorksInterventionSummary) {
    InsightCard {
        Text(
            WhatWorksForMeBuilder.run { summary.intervention.displayName() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CountRow("Started", summary.started)
        CountRow("Completed", summary.completed)
        CountRow("Dismissed", summary.dismissed)
        CountRow("Helped", summary.helped)
        CountRow("Helped a little", summary.helpedALittle)
        CountRow("Did not help", summary.didNotHelp)
        CountRow("Wrong timing", summary.wrongTiming)
        CountRow("Not answered", summary.notAnswered)
        CountRow("Later repeat detected", summary.laterRepeatDetected)
        CountRow("No later repeat observed", summary.noLaterRepeatObserved)
        CountRow("Still awaiting observation", summary.awaitingObservation)
    }
}

@Composable
private fun RecentRecordCard(record: RecentSupportRecord) {
    InsightCard {
        Text(
            DateTimeFormatter.ofPattern("d MMM uuuu").format(
                Instant.ofEpochMilli(record.decisionAtMillis)
                    .atZone(ZoneId.systemDefault()),
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(WhatWorksForMeBuilder.run { record.intervention.displayName() })
        Text(
            when (record.outcome) {
                EngagementOutcome.Completed -> "Completed"
                EngagementOutcome.Dismissed -> "Dismissed"
                EngagementOutcome.StartedNotCompleted -> "Started"
                EngagementOutcome.NotStarted -> "Not started"
            },
        )
        Text(
            when (record.feedback) {
                FeedbackCode.Helped -> "Helped"
                FeedbackCode.HelpedALittle -> "Helped a little"
                FeedbackCode.DidNotHelp -> "Did not help"
                FeedbackCode.WrongTiming -> "Wrong timing"
                FeedbackCode.NotProvided -> "Not answered"
            },
        )
        Text(
            when (record.repeatObservation) {
                RepeatObservation.RepeatDetected -> "Later repeat detected"
                RepeatObservation.NoRepeatDetected -> "No later repeat observed"
                RepeatObservation.NotFinalised -> "Still awaiting observation"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CountRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
