package com.impulsive.app.frontend.screens.protection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowEvaluator
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import com.impulsive.app.backend.domain.model.release.ReleasePlanDefaults
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun ImpulsiveBlockScreen(
    sourcePackageName: String,
    sourceLabel: String,
    onStartControlTask: () -> Unit,
    onStartReadingTask: () -> Unit,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = viewModel(),
    taskRewardViewModel: TaskRewardViewModel = viewModel(),
) {
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val taskStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }

    val answers = onboardingState.answers
    val baseReleasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = answers.dailyRelapseUrgeCount,
        now = now,
        activeDayStart = minuteOfDayToLocalTime(answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(answers.activeDayEndMinute),
    )
    val windowSnapshot = ProtectionWindowEvaluator.evaluate(
        now = now,
        releasePlan = baseReleasePlan,
        adjustedNextReleaseWindow = taskStoreState.adjustedNextReleaseWindow,
    )
    val timeLeft = windowSnapshot.timeUntilNextWindow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (windowSnapshot.isProtectionPaused) {
                        stringResource(R.string.block_screen_headline_paused)
                    } else {
                        stringResource(R.string.block_screen_headline_protected)
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.block_screen_app_in_list, sourceLabel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (windowSnapshot.isProtectionPaused) {
                        stringResource(R.string.block_screen_time_label_paused)
                    } else {
                        stringResource(R.string.block_screen_time_label_protected)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = windowSnapshot.pausedWindowEnd?.toImpulsiveCompactTime()
                        ?: timeLeft.formatBlockDuration(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (windowSnapshot.isProtectionPaused) {
                        stringResource(R.string.block_screen_detail_paused, ReleasePlanDefaults.ReleaseWindowMinutes)
                    } else {
                        stringResource(R.string.block_screen_detail_protected, ReleasePlanDefaults.ReleaseWindowMinutes)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (windowSnapshot.isProtectionPaused) {
                    Button(
                        onClick = onStartControlTask,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    ) {
                        Text(stringResource(R.string.block_screen_btn_task_not_needed))
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onStartControlTask,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.block_screen_btn_start_task))
                            }

                            Button(
                                onClick = onStartReadingTask,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.block_screen_btn_start_reading_task))
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onReturnHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.block_screen_btn_return_home))
                }
                Text(
                    text = stringResource(R.string.block_screen_source_label, sourcePackageName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun Duration.formatBlockDuration(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
