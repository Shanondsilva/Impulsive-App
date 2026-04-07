package com.impulsive.app.ui.checkin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.ui.insights.InsightsChart
import com.impulsive.app.viewmodel.WeeklyCheckInViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun WeeklyCheckInScreen(
    onComplete: () -> Unit,
    viewModel: WeeklyCheckInViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showStallDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            viewModel.resetComplete()
            onComplete()
        }
    }

    if (showStallDialog) {
        StallReasonDialog(
            onSelect = { reason ->
                showStallDialog = false
                viewModel.keepCurrent(reason)
            },
            onDismiss = { showStallDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "WEEKLY REFLECTION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 4.sp
        )
        Text(
            text = "Reflecting on the last seven days.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Here is your week.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Chart
        InsightsChart(
            interceptionsByDay = state.interceptionsByDay,
            sessionsByDay = state.sessionsByDay
        )

        // Stats row
        val week = state.lastWeek
        if (week != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "URGES",
                    value = "${state.lastWeekLogs.size}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "SESSIONS",
                    value = "${week.usedSessions}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "LIMIT WAS",
                    value = "${week.allowedSessions}"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Taper decision card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.shouldTaper) {
                Text(
                    text = "You stayed within your limit.",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B9F78)
                )
                Text(
                    text = "Your limit was ${state.lastWeek?.allowedSessions ?: "?"}. This week, we drop to ${state.suggestedNextAllowed}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "You exceeded your limit.",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFC4873B)
                )
                Text(
                    text = "Your limit was ${state.lastWeek?.allowedSessions ?: "?"}. Let's hold steady and try again next week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Primary CTA
        Button(
            onClick = { viewModel.acceptTaper() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "Confirm Adjustment",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Secondary CTA
        OutlinedButton(
            onClick = { showStallDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            val currentAllowed = state.lastWeek?.allowedSessions ?: "?"
            Text(
                text = "Keep Current $currentAllowed Limit",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
