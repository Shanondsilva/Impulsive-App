package com.impulsive.app.frontend.screens.protectioncoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.R
import com.impulsive.app.backend.session.protectioncoach.ProtectionCoachUiState

@Composable
fun ProtectionCoachScreen(
    state: ProtectionCoachUiState,
    onReviewTime: () -> Unit,
    onDismiss: (String) -> Unit,
    onSuppress: (String) -> Unit,
    onBack: () -> Unit,
) {
    CoachScaffold(title = "Protection Coach", onBack = onBack) {
        Text(
            text = stringResource(R.string.protection_coach_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.activeTimingSuggestion?.let { suggestion ->
            TimingSuggestionCard(
                onReviewTime = onReviewTime,
                onDismiss = { onDismiss(suggestion.suggestionId.value) },
                onSuppress = { onSuppress(suggestion.suggestionId.value) },
            )
        } ?: if (!state.loading) {
            Text(
                text = stringResource(R.string.protection_coach_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Unit
    }
}

@Composable
fun ProtectionCoachSuggestionScreen(
    suggestionId: String,
    onReviewTime: () -> Unit,
    onDismiss: (String) -> Unit,
    onSuppress: (String) -> Unit,
    onBack: () -> Unit,
) {
    CoachScaffold(title = "Protection Coach", onBack = onBack) {
        TimingSuggestionCard(
            onReviewTime = onReviewTime,
            onDismiss = { onDismiss(suggestionId) },
            onSuppress = { onSuppress(suggestionId) },
        )
    }
}

@Composable
fun ProtectionTransitionScreen(
    onKeepProtection: () -> Unit,
    onReviewProtectedApps: () -> Unit,
    onBack: () -> Unit,
) {
    CoachScaffold(title = "App protection has changed", onBack = onBack) {
        Text(
            text = "Impulsive now monitors automatically whenever you keep protected apps selected.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "You can keep those apps protected or review your selection. Dismissing this screen does not silently enable monitoring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onKeepProtection,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Keep protection")
        }
        OutlinedButton(
            onClick = onReviewProtectedApps,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Review protected apps")
        }
    }
}

@Composable
private fun TimingSuggestionCard(
    onReviewTime: () -> Unit,
    onDismiss: () -> Unit,
    onSuppress: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.protection_coach_timing_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.protection_coach_timing_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onReviewTime,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.protection_coach_review_time))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.protection_coach_not_now))
            }
            TextButton(
                onClick = onSuppress,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.protection_coach_never))
            }
        }
    }
}

@Composable
private fun CoachScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.tips_back),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}
