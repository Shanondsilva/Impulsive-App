package com.impulsive.app.frontend.screens.protection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.local.device.InstalledAppScanner
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCandidate
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCategory
import com.impulsive.app.backend.domain.model.protection.toSuggestionGroups
import com.impulsive.app.frontend.theme.ImpulsivePsychological

@Composable
fun BlockedAppsSelectionContent(
    selectedPackageNames: Set<String>,
    onSelectedPackageNamesChanged: (Set<String>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    allowShowMoreApps: Boolean = false,
    seedRecommendedBrowsers: Boolean = false,
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryTextColor = readableTextColor(isDarkTheme)
    val secondaryTextColor = if (isDarkTheme) Color(0xFFE8DFF4) else Color(0xFF5F5868)
    val accentColor = Color(0xFF6F5A9A)
    val lavenderColor = Color(0xFFD0C3F1)
    var candidates by remember { mutableStateOf(emptyList<ProtectedAppCandidate>()) }
    var localSelection by remember(selectedPackageNames) { mutableStateOf(selectedPackageNames) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreApps by rememberSaveable { mutableStateOf(false) }
    var showAppsInfo by rememberSaveable { mutableStateOf(false) }
    val groups = remember(candidates, localSelection, searchQuery, showMoreApps) {
        candidates.toSuggestionGroups(
            selectedPackageNames = localSelection,
            searchQuery = searchQuery,
            showMoreApps = allowShowMoreApps && showMoreApps,
        )
    }

    LaunchedEffect(Unit) {
        val loaded = InstalledAppScanner(context).getLaunchableAppCandidates()
        candidates = loaded
        // First onboarding pass only: pre-select detected browsers as a default
        // suggestion. They show ticked and persist when the user saves, exactly
        // like a manual selection. The empty-selection guard means this never
        // overwrites an existing or saved set, and the flag defaults off so it
        // never runs in the Settings sheet.
        if (seedRecommendedBrowsers && localSelection.isEmpty()) {
            val browserPackages = loaded
                .filter { it.packageName.lowercase() in AutoSelectedBrowserPackageNames }
                .map { it.packageName }
                .toSet()
            if (browserPackages.isNotEmpty()) {
                localSelection = localSelection + browserPackages
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Choose apps to protect",
                color = if (isDarkTheme) Color(0xFFF4ECFF) else Color(0xFF1F1B2E),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { showAppsInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "About app protection suggestions",
                    tint = if (isDarkTheme) lavenderColor else accentColor,
                )
            }
        }
        Text(
            text = "Review the suggested browsers before continuing.",
            color = secondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (seedRecommendedBrowsers) {
            Text(
                text = "Detected browsers are pre-selected. You can untick any.",
                color = if (isDarkTheme) lavenderColor else accentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            candidateSection(
                title = "Selected",
                candidates = groups.selected,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Recommended",
                candidates = groups.recommended,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Review",
                candidates = groups.review,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = "Not usually needed",
                candidates = groups.hiddenSafe,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
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
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.White,
            ),
        ) {
            Text(if (localSelection.isEmpty()) "Save without protected apps" else "Save protected apps")
        }

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = if (isDarkTheme) lavenderColor else accentColor,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) lavenderColor else accentColor.copy(alpha = 0.72f),
            ),
        ) {
            Text("Cancel")
        }
    }

    if (showAppsInfo) {
        AlertDialog(
            onDismissRequest = { showAppsInfo = false },
            title = { Text("App suggestions") },
            text = {
                Text("Impulsive can suggest apps that often lead into the loop. You stay in control of what gets protected.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showAppsInfo = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ImpulsivePsychological,
                    ),
                ) {
                    Text("Got it")
                }
            },
        )
    }
}

private fun LazyListScope.candidateSection(
    title: String,
    candidates: List<ProtectedAppCandidate>,
    localSelection: Set<String>,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onToggle: (String) -> Unit,
) {
    if (candidates.isEmpty()) return
    item {
        Text(
            text = title,
            color = primaryTextColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(candidates, key = { it.packageName }) { candidate ->
        ProtectedAppCandidateRow(
            candidate = candidate,
            selected = candidate.packageName in localSelection,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor,
            onToggle = { onToggle(candidate.packageName) },
        )
    }
}

@Composable
private fun ProtectedAppCandidateRow(
    candidate: ProtectedAppCandidate,
    selected: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
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
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF6F5A9A),
                    uncheckedColor = if (isDarkTheme) Color(0xFFD0C3F1) else Color(0xFF8B7BA8),
                    checkmarkColor = Color.White,
                    disabledCheckedColor = Color(0xFFD0C3F1),
                    disabledUncheckedColor = Color(0xFFB8ABC9),
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.appLabel,
                    color = primaryTextColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${candidate.category.label} - ${candidate.reason}",
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun readableTextColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color.White else Color(0xFF1C1B1E)

private val AutoSelectedBrowserPackageNames = setOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.sec.android.app.sbrowser",
    "org.mozilla.firefox",
    "org.mozilla.firefox_beta",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.opera.browser",
    "com.opera.mini.native",
    "com.duckduckgo.mobile.android",
    "com.vivaldi.browser",
    "com.kiwibrowser.browser",
    "org.torproject.torbrowser",
    "com.ucmobile.intl",
    "com.yandex.browser",
)
