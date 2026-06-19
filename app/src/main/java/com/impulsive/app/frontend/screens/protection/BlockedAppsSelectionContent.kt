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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlockedAppsSelectionContent(
    selectedPackageNames: Set<String>,
    onSelectedPackageNamesChanged: (Set<String>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    allowShowMoreApps: Boolean = false,
    seedRecommendedBrowsers: Boolean = false,
    useFocusCopy: Boolean = false,
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryTextColor = readableTextColor(isDarkTheme)
    val secondaryTextColor = if (isDarkTheme) Color(0xFFE8DFF4) else Color(0xFF5F5868)
    val accentColor = Color(0xFF6F5A9A)
    val lavenderColor = Color(0xFFD0C3F1)
    var candidates by remember { mutableStateOf(emptyList<ProtectedAppCandidate>()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var localSelection by remember(selectedPackageNames) { mutableStateOf(selectedPackageNames) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreApps by rememberSaveable { mutableStateOf(false) }
    var showAppsInfo by rememberSaveable { mutableStateOf(false) }
    val titleText = if (useFocusCopy) "Choose apps to block during focus" else "Choose apps to protect"
    val subtitleText = if (useFocusCopy) {
        "Pick the apps that usually pull you away while working or studying. This only affects Focus Mode."
    } else {
        "Review the suggested browsers before continuing."
    }
    val infoContentDescription = if (useFocusCopy) {
        "About Focus app blocking"
    } else {
        "About app protection suggestions"
    }
    val infoDialogTitle = if (useFocusCopy) "Focus app blocking" else "App suggestions"
    val infoDialogBody = if (useFocusCopy) {
        "Choose the apps that usually break your focus. Select all only includes safe launchable apps, while system and utility apps stay out of that action."
    } else {
        "Impulsive can suggest apps that often lead into the loop. You stay in control of what gets protected."
    }
    val selectedSectionTitle = if (useFocusCopy) "Blocked in Focus" else "Selected"
    val recommendedSectionTitle = if (useFocusCopy) "Common distractions" else "Recommended"
    val reviewSectionTitle = if (useFocusCopy) "Other apps" else "Review"
    val hiddenSafeSectionTitle = if (useFocusCopy) "System and utility apps" else "Not usually needed"
    val saveButtonText = if (useFocusCopy) {
        if (localSelection.isEmpty()) "Continue with no blocked apps" else "Save focus apps"
    } else {
        if (localSelection.isEmpty()) "Save without protected apps" else "Save protected apps"
    }
    val selectablePackageNames = remember(candidates) {
        candidates
            .filterNot { it.isHiddenSafe }
            .map { it.packageName }
            .toSet()
    }
    val allSelectableAppsSelected = selectablePackageNames.isNotEmpty() &&
        localSelection.containsAll(selectablePackageNames)
    val groups = remember(candidates, localSelection, searchQuery, showMoreApps) {
        candidates.toSuggestionGroups(
            selectedPackageNames = localSelection,
            searchQuery = searchQuery,
            showMoreApps = allowShowMoreApps && showMoreApps,
        )
    }

    LaunchedEffect(seedRecommendedBrowsers) {
        isLoadingApps = true

        val appContext = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            InstalledAppScanner(appContext).getLaunchableAppCandidates()
        }

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

        isLoadingApps = false
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
                text = titleText,
                color = if (isDarkTheme) Color(0xFFF4ECFF) else Color(0xFF1F1B2E),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { showAppsInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = infoContentDescription,
                    tint = if (isDarkTheme) lavenderColor else accentColor,
                )
            }
        }

        Text(
            text = subtitleText,
            color = secondaryTextColor,
            style = MaterialTheme.typography.bodySmall,
        )

        if (seedRecommendedBrowsers) {
            Text(
                text = "Detected browsers are pre-selected. You can untick any.",
                color = if (isDarkTheme) lavenderColor else accentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search installed apps") },
                singleLine = true,
            )

            if (useFocusCopy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            localSelection = if (allSelectableAppsSelected) {
                                localSelection - selectablePackageNames
                            } else {
                                localSelection + selectablePackageNames
                            }
                        },
                        enabled = selectablePackageNames.isNotEmpty(),
                    ) {
                        Text(if (allSelectableAppsSelected) "Deselect all" else "Select all apps")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLoadingApps) {
                item {
                    Text(
                        text = "Loading installed apps...",
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (candidates.isEmpty()) {
                item {
                    Text(
                        text = "No installed apps found.",
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            candidateSection(
                title = selectedSectionTitle,
                candidates = groups.selected,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = recommendedSectionTitle,
                candidates = groups.recommended,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = reviewSectionTitle,
                candidates = groups.review,
                localSelection = localSelection,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            ) { localSelection = localSelection.toggle(it) }

            candidateSection(
                title = hiddenSafeSectionTitle,
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

        if (useFocusCopy && localSelection.isEmpty()) {
            Text(
                text = "No apps selected. Focus will still run as a timer, but no apps will be blocked.",
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
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
            Text(saveButtonText)
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
            title = { Text(infoDialogTitle) },
            text = {
                Text(infoDialogBody)
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
