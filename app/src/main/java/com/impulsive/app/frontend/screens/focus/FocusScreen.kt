package com.impulsive.app.frontend.screens.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.model.focus.DefaultFocusDurationOptions
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.elapsedFocusSeconds
import com.impulsive.app.backend.domain.model.focus.focusCompletionLevelPoints
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.domain.model.focus.progressFraction
import com.impulsive.app.backend.session.focus.FocusSessionViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.frontend.components.BodyModeLockedSheet
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.MindModeStatusSheet
import com.impulsive.app.frontend.components.ModeSelectionSheet
import com.impulsive.app.frontend.components.SoulModeLockedSheet
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveFocusModeDark
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    focusViewModel: FocusSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenHome: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSession: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onNavigateFromModeContext: () -> Unit = {},
    bottomNavIndicatorStartFrom: BottomNavItem? = null,
    onBottomNavIndicatorStartConsumed: () -> Unit = {},
) {
    val session by focusViewModel.session.collectAsStateWithLifecycle()
    val now by focusViewModel.now.collectAsStateWithLifecycle()
    val protectionSetupViewModel: ProtectionSetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val configuredFocusBlockedPackages by focusViewModel.configuredFocusBlockedPackages.collectAsStateWithLifecycle()
    val effectiveFocusApps = configuredFocusBlockedPackages ?: protectionSetupState.selectedBlockedAppPackageNames
    var selectedMinutes by rememberSaveable { mutableIntStateOf(25) }
    var modeSelectionSheetVisible by remember { mutableStateOf(false) }
    var mindModeSheetVisible by remember { mutableStateOf(false) }
    var bodyModeSheetVisible by remember { mutableStateOf(false) }
    var soulModeSheetVisible by remember { mutableStateOf(false) }
    var showFocusAppsSheet by remember { mutableStateOf(false) }
    val bottomNavReservedSpace = 104.dp
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isDark) ImpulsiveFocusModeDark else ImpulsiveFocusMode
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF)
    val borderColor = if (isDark) accent.copy(alpha = 0.34f) else Color(0xFFF0D8D8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground(lightweight = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                session == null -> FocusSetupContent(
                    selectedMinutes = selectedMinutes,
                    onSelectedMinutesChanged = { selectedMinutes = it },
                    onBeginFocus = {
                        focusViewModel.startSession(selectedMinutes)
                        onOpenSession()
                    },
                    effectiveFocusApps = effectiveFocusApps,
                    onEditFocusApps = { showFocusAppsSheet = true },
                    accent = accent,
                    text = text,
                    muted = muted,
                    cardColor = cardColor,
                    borderColor = borderColor,
                )
                session?.isLive == true -> FocusResumeCard(
                    session = session,
                    now = now,
                    onResumeFocus = onOpenSession,
                    onEndSession = focusViewModel::endEarly,
                    accent = accent,
                    text = text,
                    muted = muted,
                    cardColor = cardColor,
                    borderColor = borderColor,
                )
                else -> FocusCompletionCard(
                    session = session,
                    now = now,
                    onDone = focusViewModel::clearFinishedSession,
                    onStartAnother = focusViewModel::clearFinishedSession,
                    accent = accent,
                    text = text,
                    muted = muted,
                    cardColor = cardColor,
                    borderColor = borderColor,
                )
            }
        }

        if (mindModeSheetVisible) {
            MindModeStatusSheet(
                onDismissRequest = { mindModeSheetVisible = false },
                onStartMindTask = {
                    mindModeSheetVisible = false
                    onOpenTasks()
                },
                onViewProgress = { mindModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (bodyModeSheetVisible) {
            BodyModeLockedSheet(
                onDismissRequest = { bodyModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (soulModeSheetVisible) {
            SoulModeLockedSheet(
                onDismissRequest = { soulModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (modeSelectionSheetVisible) {
            ModeSelectionSheet(
                onDismissRequest = { modeSelectionSheetVisible = false },
                onOpenMindMode = {
                    mindModeSheetVisible = true
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = false
                },
                onOpenBodyMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = true
                    soulModeSheetVisible = false
                },
                onOpenSoulMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = true
                },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        BottomNavBar(
            selected = if (
                modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible
            ) {
                BottomNavItem.Trigger
            } else {
                BottomNavItem.Focus
            },
            onSelect = { item ->
                val fromModeContext = modeSelectionSheetVisible ||
                    mindModeSheetVisible ||
                    bodyModeSheetVisible ||
                    soulModeSheetVisible
                when (item) {
                    BottomNavItem.Home -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenHome()
                        if (fromModeContext) onNavigateFromModeContext()
                    }
                    BottomNavItem.Trigger -> {
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        modeSelectionSheetVisible = !modeSelectionSheetVisible
                    }
                    BottomNavItem.Settings -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenSettings()
                        if (fromModeContext) onNavigateFromModeContext()
                    }
                    BottomNavItem.Progress -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenScore()
                        if (fromModeContext) onNavigateFromModeContext()
                    }
                    BottomNavItem.Focus -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                    }
                }
            },
            onLongSelect = { item ->
                if (item == BottomNavItem.Trigger) {
                    modeSelectionSheetVisible = !modeSelectionSheetVisible
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            settingsBadgeVisible = protectionSetupState.profileBadgeShouldShow,
            modeSelectorOpen = modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible,
            indicatorStartFrom = bottomNavIndicatorStartFrom,
            onIndicatorStartConsumed = onBottomNavIndicatorStartConsumed,
        )
    }

    if (showFocusAppsSheet) {
        ModalBottomSheet(onDismissRequest = { showFocusAppsSheet = false }) {
            BlockedAppsSelectionContent(
                selectedPackageNames = effectiveFocusApps,
                onSelectedPackageNamesChanged = focusViewModel::setFocusBlockedPackages,
                onDone = { showFocusAppsSheet = false },
                allowShowMoreApps = true,
            )
        }
    }
}

@Composable
private fun FocusSetupContent(
    selectedMinutes: Int,
    onSelectedMinutesChanged: (Int) -> Unit,
    onBeginFocus: () -> Unit,
    effectiveFocusApps: Set<String>,
    onEditFocusApps: () -> Unit,
    accent: Color,
    text: Color,
    muted: Color,
    cardColor: Color,
    borderColor: Color,
) {
    Text(
        text = "FOCUS",
        color = muted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Start a focus session",
        color = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Duration",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultFocusDurationOptions.forEach { minutes ->
                    FocusChip(
                        label = "$minutes min",
                        selected = selectedMinutes == minutes,
                        accent = accent,
                        onClick = { onSelectedMinutesChanged(minutes) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Blocked during focus",
                        color = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val appCount = effectiveFocusApps.size
                    Text(
                        text = if (appCount == 1) "1 app" else "$appCount apps",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onEditFocusApps) {
                    Text("Edit", color = accent)
                }
            }
        }
    }
    Surface(
        color = cardColor.copy(alpha = 0.72f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cold", "Warm", "Hot").forEach { label ->
                        DisabledTemperatureChip(label = label)
                    }
                }
                Text(
                    text = "Temperature focus unlocks with premium",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    FocusPrimaryButton(
        text = "Begin focus",
        accent = accent,
        onClick = onBeginFocus,
    )
    Text(
        text = "Distracting apps stay quiet until the timer ends",
        color = muted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FocusResumeCard(
    session: FocusSessionState?,
    now: LocalDateTime,
    onResumeFocus: () -> Unit,
    onEndSession: () -> Unit,
    accent: Color,
    text: Color,
    muted: Color,
    cardColor: Color,
    borderColor: Color,
) {
    if (session == null) return
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Session in progress", color = muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("${session.formattedRemaining(now)} left", color = text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Picking up where you left off", color = muted, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { session.progressFraction(now) },
                color = accent,
                trackColor = accent.copy(alpha = 0.16f),
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
            )
            FocusPrimaryButton("Resume focus", accent, onResumeFocus)
            TextButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) {
                Text("End session", color = muted)
            }
        }
    }
}

@Composable
private fun FocusCompletionCard(
    session: FocusSessionState?,
    now: LocalDateTime,
    onDone: () -> Unit,
    onStartAnother: () -> Unit,
    accent: Color,
    text: Color,
    muted: Color,
    cardColor: Color,
    borderColor: Color,
) {
    if (session == null) return
    val focusedMinutes = if (session.phase == FocusSessionPhase.Completed) {
        session.durationMinutes
    } else {
        (session.elapsedFocusSeconds(now) / 60).toInt().coerceAtLeast(0)
    }
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Focus complete", color = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$focusedMinutes minutes protected", color = muted, style = MaterialTheme.typography.bodyLarge)
            FocusStatRow("Time focused", "$focusedMinutes min", text, muted)
            FocusStatRow("Interruptions handled", session.interruptionCount.toString(), text, muted)
            if (session.phase == FocusSessionPhase.Completed) {
                FocusStatRow(
                    "Level Points earned",
                    "+${focusCompletionLevelPoints(session.durationMinutes)}",
                    accent,
                    muted,
                )
            }
            FocusPrimaryButton("Done", accent, onDone)
            TextButton(onClick = onStartAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Start another", color = muted)
            }
        }
    }
}

@Composable
private fun FocusChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val chipShape = RoundedCornerShape(50)
    Surface(
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
        shape = chipShape,
        modifier = Modifier
            .clip(chipShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF281D38) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DisabledTemperatureChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FocusPrimaryButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(50)
    Surface(
        color = accent,
        shape = buttonShape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(buttonShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Text(
            text = text,
            color = Color(0xFF281D38),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun Modifier.impulsiveNoSquareRippleClickable(
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
private fun FocusStatRow(
    label: String,
    value: String,
    text: Color,
    muted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
