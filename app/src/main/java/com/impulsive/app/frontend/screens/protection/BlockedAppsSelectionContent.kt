package com.impulsive.app.frontend.screens.protection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.local.device.InstalledAppScanner
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCandidate
import com.impulsive.app.backend.domain.model.protection.toSuggestionGroups

@Composable
fun BlockedAppsSelectionContent(
    selectedPackageNames: Set<String>,
    onSelectedPackageNamesChanged: (Set<String>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    allowShowMoreApps: Boolean = false,
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var candidates by remember { mutableStateOf(emptyList<ProtectedAppCandidate>()) }
    var localSelection by remember(selectedPackageNames) { mutableStateOf(selectedPackageNames) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreApps by rememberSaveable { mutableStateOf(false) }
    val groups = remember(candidates, localSelection, searchQuery, showMoreApps) {
        candidates.toSuggestionGroups(
            selectedPackageNames = localSelection,
            searchQuery = searchQuery,
            showMoreApps = allowShowMoreApps && showMoreApps,
        )
    }

    LaunchedEffect(Unit) {
        candidates = InstalledAppScanner(context).getLaunchableAppCandidates()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Choose apps to protect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Impulsive can suggest apps that often lead into the loop. You stay in control of what gets protected.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search installed apps") },
            singleLine = true,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            candidateSection(
                title = "Selected",
                candidates = groups.selected,
                localSelection = localSelection,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Recommended",
                candidates = groups.recommended,
                localSelection = localSelection,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Review",
                candidates = groups.review,
                localSelection = localSelection,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Not usually needed",
                candidates = groups.hiddenSafe,
                localSelection = localSelection,
            ) { localSelection = localSelection.toggle(it) }

            if (allowShowMoreApps && !showMoreApps) {
                item {
                    TextButton(onClick = { showMoreApps = true }) {
                        Text("Show more apps")
                    }
                }
            }
        }

        Button(
            onClick = {
                onSelectedPackageNamesChanged(localSelection)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (localSelection.isEmpty()) "Save without protected apps" else "Save protected apps")
        }

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
    }
}

private fun LazyListScope.candidateSection(
    title: String,
    candidates: List<ProtectedAppCandidate>,
    localSelection: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (candidates.isEmpty()) return
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(candidates, key = { it.packageName }) { candidate ->
        ProtectedAppCandidateRow(
            candidate = candidate,
            selected = candidate.packageName in localSelection,
            onToggle = { onToggle(candidate.packageName) },
        )
    }
}

@Composable
private fun ProtectedAppCandidateRow(
    candidate: ProtectedAppCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        border = if (isDarkTheme) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${candidate.category.label} - ${candidate.reason}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
