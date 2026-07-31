package com.impulsive.app.frontend.screens.focus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.domain.model.focus.remainingSeconds
import com.impulsive.app.backend.session.focus.FocusSessionViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.frontend.components.BodyModeLockedSheet
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavIndicatorState
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.MindModeStatusSheet
import com.impulsive.app.frontend.components.ModeSelectionSheet
import com.impulsive.app.frontend.components.SoulModeLockedSheet
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveFocusModeDark
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    focusViewModel: FocusSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenHome: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    adaptiveMomentPlan: Boolean = false,
    onAdaptiveCompleted: (() -> Unit)? = null,
    indicatorState: BottomNavIndicatorState = rememberBottomNavIndicatorState(),
    isActive: Boolean = true,
) {
    val session by focusViewModel.session.collectAsStateWithLifecycle()
    val now by focusViewModel.now.collectAsStateWithLifecycle()
    val lastFocusTimeAward by focusViewModel.lastFocusTimeAward.collectAsStateWithLifecycle()
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
    var completedSummarySession by remember { mutableStateOf<FocusSessionState?>(null) }
    var adaptiveConfirmationVisible by rememberSaveable(adaptiveMomentPlan) {
        mutableStateOf(false)
    }
    val bottomNavReservedSpace = 104.dp
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isDark) ImpulsiveFocusModeDark else ImpulsiveFocusMode
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF)
    val borderColor = if (isDark) accent.copy(alpha = 0.34f) else Color(0xFFF0D8D8)

    LaunchedEffect(session?.sessionId, session?.phase) {
        val currentSession = session
        when {
            currentSession?.phase == FocusSessionPhase.Completed -> {
                completedSummarySession = currentSession
            }
            currentSession != null && !currentSession.isLive -> {
                focusViewModel.clearFinishedSession()
            }
        }
    }

    if (adaptiveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { adaptiveConfirmationVisible = false },
            title = { Text("Did you complete your Moment Plan?") },
            text = {
                Text(
                    "Only confirm when you have finished the Focus action you planned.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        adaptiveConfirmationVisible = false
                        onAdaptiveCompleted?.invoke()
                    },
                ) {
                    Text("Yes, I did")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { adaptiveConfirmationVisible = false },
                ) {
                    Text("Not yet")
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground()
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val compactHeight = maxHeight < 720.dp
            val compactWidth = maxWidth < 380.dp
            val focusSetupScrollState = rememberScrollState()
            val focusHorizontalPadding = if (compactWidth) 16.dp else 20.dp
            val focusTopPadding = if (compactHeight) 12.dp else 18.dp
            val focusBottomPadding = bottomNavReservedSpace + if (compactHeight) 20.dp else 28.dp
            val focusContentSpacing = if (compactHeight) 12.dp else 18.dp
            val focusClockSizeDp = when {
                maxHeight < 620.dp || maxWidth < 360.dp -> 188
                maxHeight < 720.dp -> 206
                else -> 230
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(focusSetupScrollState)
                    .padding(horizontal = focusHorizontalPadding)
                    .padding(top = focusTopPadding, bottom = focusBottomPadding),
                verticalArrangement = Arrangement.spacedBy(focusContentSpacing),
            ) {
                val liveSession = session?.takeIf { it.isLive }
                val primaryClockDisplayText = liveSession?.formattedRemaining(now)
                val activeRemainingSeconds = liveSession?.remainingSeconds(now)
                val displayMinutes = liveSession?.durationMinutes ?: selectedMinutes

                if (adaptiveMomentPlan) {
                    Surface(
                        color = cardColor,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Moment Plan Focus",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Use Focus for the action you prepared. Confirm only when you are done.",
                                color = muted,
                            )
                            TextButton(
                                onClick = { adaptiveConfirmationVisible = true },
                            ) {
                                Text("Confirm Moment Plan")
                            }
                        }
                    }
                }

                LaunchedEffect(liveSession?.sessionId, liveSession?.phase, activeRemainingSeconds) {
                    if (
                        liveSession?.phase == FocusSessionPhase.Running &&
                        activeRemainingSeconds == 0L
                    ) {
                        focusViewModel.completeElapsedSessionIfNeeded(now)
                    }
                }

                FocusSetupContent(
                    selectedMinutes = displayMinutes,
                    now = now,
                    primaryClockDisplayText = primaryClockDisplayText,
                    activeRemainingSeconds = activeRemainingSeconds,
                    onSelectedMinutesChanged = {
                        if (liveSession == null) {
                            selectedMinutes = it
                        }
                    },
                    onBeginFocus = {
                        focusViewModel.startSession(selectedMinutes)
                    },
                    isFocusActive = liveSession != null,
                    activeSessionPhase = liveSession?.phase,
                    onTogglePause = {
                        val currentSession = session
                        if (currentSession?.phase == FocusSessionPhase.Running) {
                            focusViewModel.pause()
                        } else if (currentSession?.phase == FocusSessionPhase.Paused) {
                            focusViewModel.resume()
                        }
                    },
                    onEndSession = focusViewModel::endEarly,
                    effectiveFocusApps = effectiveFocusApps,
                    onEditFocusApps = { showFocusAppsSheet = true },
                    accent = accent,
                    text = text,
                    muted = muted,
                    cardColor = cardColor,
                    borderColor = borderColor,
                    clockSizeDp = focusClockSizeDp,
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
                when (item) {
                    BottomNavItem.Home -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenHome()
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
                    }
                    BottomNavItem.Progress -> {
                        modeSelectionSheetVisible = false
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenScore()
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
            indicatorState = indicatorState,
            isActive = isActive,
        )
    }

    completedSummarySession?.let { summarySession ->
        FocusCompletionSummaryDialog(
            completedSession = summarySession,
            levelPointsAwarded = lastFocusTimeAward
                ?.takeIf { (sessionId, _) -> sessionId == summarySession.sessionId }
                ?.second,
            blockedAppsCount = effectiveFocusApps.size,
            accent = accent,
            text = text,
            muted = muted,
            onDismiss = {
                completedSummarySession = null
                focusViewModel.clearFinishedSession()
            },
        )
    }

    if (showFocusAppsSheet) {
        ModalBottomSheet(onDismissRequest = { showFocusAppsSheet = false }) {
            BlockedAppsSelectionContent(
                selectedPackageNames = effectiveFocusApps,
                onSelectedPackageNamesChanged = focusViewModel::setFocusBlockedPackages,
                onDone = { showFocusAppsSheet = false },
                allowShowMoreApps = true,
                useFocusCopy = true,
            )
        }
    }

}

@Composable
private fun FocusCompletionSummaryDialog(
    completedSession: FocusSessionState,
    levelPointsAwarded: Int?,
    blockedAppsCount: Int,
    accent: Color,
    text: Color,
    muted: Color,
    onDismiss: () -> Unit,
) {
    val levelPointsText = when (val points = levelPointsAwarded) {
        null -> "Calculating..."
        0 -> "Focus LP reached for today"
        else -> "+$points LP"
    }
    val blockedAppsText = when (blockedAppsCount) {
        0 -> "No apps blocked"
        1 -> "1 app guarded"
        else -> "$blockedAppsCount apps guarded"
    }
    val interruptionsText = when (completedSession.interruptionCount) {
        0 -> "No interruptions opened"
        1 -> "1 interruption recovered"
        else -> "${completedSession.interruptionCount} interruptions recovered"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Focus complete",
                color = text,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "You protected your focus window.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )

                FocusSummaryRow(
                    label = "Time completed",
                    value = formatCleanFocusDuration(completedSession.durationMinutes),
                    text = text,
                    muted = muted,
                )

                FocusSummaryRow(
                    label = "Distractions blocked",
                    value = blockedAppsText,
                    text = text,
                    muted = muted,
                )

                FocusSummaryRow(
                    label = "Interruptions recovered",
                    value = interruptionsText,
                    text = text,
                    muted = muted,
                )

                FocusSummaryRow(
                    label = "Level Points earned",
                    value = levelPointsText,
                    text = text,
                    muted = muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun FocusSummaryRow(
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
        Text(
            text = label,
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = value,
            color = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FocusSetupContent(
    selectedMinutes: Int,
    now: LocalDateTime,
    primaryClockDisplayText: String?,
    activeRemainingSeconds: Long?,
    onSelectedMinutesChanged: (Int) -> Unit,
    onBeginFocus: () -> Unit,
    isFocusActive: Boolean,
    activeSessionPhase: FocusSessionPhase?,
    onTogglePause: () -> Unit,
    onEndSession: () -> Unit,
    effectiveFocusApps: Set<String>,
    onEditFocusApps: () -> Unit,
    accent: Color,
    text: Color,
    muted: Color,
    cardColor: Color,
    borderColor: Color,
    clockSizeDp: Int,
) {
    FocusHeader(
        text = text,
        muted = muted,
    )

    

    CleanFocusDurationClock(
        durationMinutes = selectedMinutes,
        currentTime = now,
        accent = accent,
        text = text,
        muted = muted,
        primaryDisplayText = primaryClockDisplayText,
        activeRemainingSeconds = activeRemainingSeconds,
        isInteractionEnabled = !isFocusActive,
        recommendedMinutes = listOf(15, 30, 60),
        clockSizeDp = clockSizeDp,
        onDurationMinutesChanged = onSelectedMinutesChanged,
    )

    val blockedAppsCardShape = RoundedCornerShape(28.dp)

    Surface(
        color = cardColor,
        shape = blockedAppsCardShape,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(blockedAppsCardShape)
            .impulsiveNoSquareRippleClickable { onEditFocusApps() },
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val appCount = effectiveFocusApps.size

            Text(
                text = "Apps blocked during focus",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = if (appCount == 1) "1 app" else "$appCount apps",
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    val pauseButtonText = if (activeSessionPhase == FocusSessionPhase.Running) {
        "Pause"
    } else {
        "Resume"
    }

    FocusStableSessionControls(
        isFocusActive = isFocusActive,
        pauseText = pauseButtonText,
        accent = accent,
        muted = muted,
        onStart = onBeginFocus,
        onPauseResume = onTogglePause,
        onEndSession = onEndSession,
    )
}

@Composable
private fun FocusHeader(
    text: Color,
    muted: Color,
) {
    var showFocusInfo by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Focus",
            color = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = { showFocusInfo = true },
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "About Focus",
                tint = text.copy(alpha = 0.76f),
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (showFocusInfo) {
        FocusInfoDialog(
            text = text,
            muted = muted,
            onDismiss = { showFocusInfo = false },
        )
    }
}

@Composable
private fun FocusInfoDialog(
    text: Color,
    muted: Color,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ImpulsivePsychological,
                ),
            ) {
                Text("Got it")
            }
        },
        title = {
            Text(
                text = "About Focus",
                color = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Take a break.",
                    color = text.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                FocusInfoItem(
                    title = "Focus session",
                    body = "Choose a focused time block and let Impulsive guard the session from distracting apps.",
                    text = text,
                    muted = muted,
                )

                FocusInfoItem(
                    title = "Time dial",
                    body = "Drag the clock to set your focus length before starting. Once a session starts, the timer becomes locked.",
                    text = text,
                    muted = muted,
                )

                FocusInfoItem(
                    title = "Blocked apps",
                    body = "Shows how many apps are protected during Focus. Tap the card to adjust the Focus blocking list.",
                    text = text,
                    muted = muted,
                )

                FocusInfoItem(
                    title = "Start, Pause, End",
                    body = "Start begins protection, Pause temporarily holds the session, and End stops the current Focus block.",
                    text = text,
                    muted = muted,
                )

                FocusInfoItem(
                    title = "Focus points",
                    body = "Completed Focus time can build Level Points, but Focus LP is capped so it supports progress without becoming farmable.",
                    text = text,
                    muted = muted,
                )
            }
        },
    )
}

@Composable
private fun FocusInfoItem(
    title: String,
    body: String,
    text: Color,
    muted: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = body,
            color = muted,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
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
private fun FocusStableSessionControls(
    isFocusActive: Boolean,
    pauseText: String,
    accent: Color,
    muted: Color,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onEndSession: () -> Unit,
) {
    val splitProgress by animateFloatAsState(
        targetValue = if (isFocusActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing,
        ),
        label = "focusStableSplitProgress",
    )

    val progress = splitProgress.coerceIn(0f, 1f)
    val easedProgress = smoothStableFocusProgress(progress)
    val endAlpha = smoothStableFocusProgress(((progress - 0.08f) / 0.92f).coerceIn(0f, 1f))
    val primaryStartAlpha = (1f - smoothStableFocusProgress((progress / 0.55f).coerceIn(0f, 1f)))
        .coerceIn(0f, 1f)
    val primaryActiveAlpha = smoothStableFocusProgress(((progress - 0.25f) / 0.75f).coerceIn(0f, 1f))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        val finalGap = 12.dp
        val finalButtonWidth = (maxWidth - finalGap) / 2
        val primaryWidth = lerp(maxWidth, finalButtonWidth, easedProgress)

        FocusActionPill(
            text = "End session",
            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = muted,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(finalButtonWidth)
                .height(54.dp)
                .alpha(endAlpha),
            onClick = {
                if (isFocusActive) {
                    onEndSession()
                }
            },
        )

        FocusStablePrimaryPill(
            startAlpha = primaryStartAlpha,
            activeAlpha = primaryActiveAlpha,
            activeText = pauseText,
            accent = accent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(primaryWidth)
                .height(54.dp),
            onClick = {
                if (isFocusActive) {
                    onPauseResume()
                } else {
                    onStart()
                }
            },
        )
    }
}

@Composable
private fun FocusStablePrimaryPill(
    startAlpha: Float,
    activeAlpha: Float,
    activeText: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(50)

    Surface(
        color = accent,
        shape = buttonShape,
        modifier = modifier
            .clip(buttonShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Start",
                color = Color(0xFF281D38),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(startAlpha),
            )

            Text(
                text = activeText,
                color = Color(0xFF281D38),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(activeAlpha),
            )
        }
    }
}

@Composable
private fun FocusActionPill(
    text: String,
    background: Color,
    contentColor: Color,
    border: BorderStroke?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(50)

    Surface(
        color = background,
        shape = buttonShape,
        border = border,
        modifier = modifier
            .height(54.dp)
            .clip(buttonShape)
            .impulsiveNoSquareRippleClickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun smoothStableFocusProgress(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

@Composable
private fun Modifier.impulsiveNoSquareRippleClickable(
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
