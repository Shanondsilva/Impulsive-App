package com.impulsive.app.frontend.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import com.impulsive.app.backend.data.UserDataManager
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.progress.LevelViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.settings.AppLockViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.backend.service.protection.ProtectionServiceController
import com.impulsive.app.frontend.screens.lock.AppLockGuardHost
import com.impulsive.app.frontend.screens.lock.SetPinScreen
import com.impulsive.app.frontend.screens.lock.rememberAppLockGuardController
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.core.util.ThemeMode
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.ImpulsiveHaptics
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackHome: () -> Unit,
    onOpenHome: () -> Unit = onBackHome,
    onOpenScore: () -> Unit = {},
    onOpenUninstallProtection: () -> Unit = {},
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appLockViewModel: AppLockViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val levelViewModel: LevelViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val appLockEnabled by appLockViewModel.enabled.collectAsStateWithLifecycle()
    val currentLevel by levelViewModel.currentLevel.collectAsStateWithLifecycle()
    val storedMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val selectedMode = if (storedMode == ThemeMode.System) ThemeMode.AsPerTime else storedMode
    val displayName = onboardingState.answers.name.takeIf { it.isNotBlank() } ?: "Shanon"
    val avatar = AvatarStyle.fromId(onboardingState.answers.avatarId)
    val context = LocalContext.current
    val protectionSetupViewModel: ProtectionSetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberImpulsiveHaptics(appSettingsState.hapticsEnabled)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showPlusSheet by remember { mutableStateOf(false) }
    var showBlockedAppsSheet by remember { mutableStateOf(false) }
    val appLockGuard = rememberAppLockGuardController()
    var notificationsAllowed by remember { mutableStateOf(isNotificationPermissionAllowed(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAllowed = isNotificationPermissionAllowed(context)
        protectionSetupViewModel.setNotificationPermissionEnabled(notificationsAllowed)
    }
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader()
            ProfileGroup(
                displayName = displayName,
                avatar = avatar,
                currentLevel = currentLevel,
                answers = onboardingState.answers,
                haptics = haptics,
                onSaveProfile = { name, avatarId, onSaved ->
                    onboardingViewModel.savePersonalization(name, avatarId) {
                        haptics.confirm()
                        onSaved()
                    }
                },
            )
            if (protectionSetupState.profileBadgeShouldShow) {
                ProtectionSetupIncompleteCard(
                    protectionSetupState = protectionSetupState,
                    onOpenProtectionSetup = { appLockGuard.run(appLockEnabled) { showBlockedAppsSheet = true } },
                )
            }
            PlusGroup(
                haptics = haptics,
                onViewPlus = { showPlusSheet = true },
            )
            AppearanceGroup(
                selectedMode = selectedMode,
                onModeSelected = themeViewModel::setThemeMode,
                haptics = haptics,
                hapticsEnabled = appSettingsState.hapticsEnabled,
                onHapticsChanged = appSettingsViewModel::setHapticsEnabled,
                soundEffectsEnabled = appSettingsState.soundEffectsEnabled,
                onSoundEffectsChanged = appSettingsViewModel::setSoundEffectsEnabled,
            )
            RecoverySetupGroup(
                answers = onboardingState.answers,
                onEditTriggers = { onboardingViewModel.setMultiSelectAnswer(OnboardingQuestionId.Triggers, it) },
                onEditTiming = { onboardingViewModel.setMultiSelectAnswer(OnboardingQuestionId.Timing, it) },
                onEditWeeklyTarget = { onboardingViewModel.setSingleSelectAnswer(OnboardingQuestionId.WeekOneGoal, it) },
            )
            ProtectionFocusGroup(
                protectionState = protectionSetupState,
                appLockEnabled = appLockEnabled,
                guard = appLockGuard::run,
                onOpenBlockedApps = { appLockGuard.run(appLockEnabled) { showBlockedAppsSheet = true } },
                onOpenUninstallProtection = onOpenUninstallProtection,
            )
            PrivacyAccountGroup(
                appLockEnabled = appLockEnabled,
                onDisableAppLock = appLockViewModel::disable,
                hideSensitiveNotifications = appSettingsState.hideSensitiveNotifications,
                onHideSensitiveNotificationsChanged = appSettingsViewModel::setHideSensitiveNotifications,
                notificationsAllowed = notificationsAllowed,
                haptics = haptics,
                onExportData = {
                    appSettingsViewModel.exportData { uri ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "My Impulsive data")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(share, "Export your data"),
                            )
                        }
                    }
                },
                onDeleteAllData = {
                    appSettingsViewModel.deleteAllData(
                        onComplete = { UserDataManager(context).restartApp() },
                    )
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            SupportGroup()
        }

        BottomNavBar(
            selected = BottomNavItem.Settings,
            onSelect = { item ->
                when (item) {
                    BottomNavItem.Home -> onOpenHome()
                    BottomNavItem.Progress -> onOpenScore()
                    else -> Unit
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            hapticsEnabled = appSettingsState.hapticsEnabled,
            settingsBadgeVisible = protectionSetupState.profileBadgeShouldShow,
        )

        if (showBlockedAppsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBlockedAppsSheet = false },
            ) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged = protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { showBlockedAppsSheet = false },
                    allowShowMoreApps = true,
                )
            }
        }

        if (showPlusSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPlusSheet = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Impulsive Plus",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Impulsive Plus will unlock stronger pivot tools when payments are connected.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { showPlusSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Close")
                    }
                }
            }
        }

        AppLockGuardHost(controller = appLockGuard)
    }
}

@Composable
private fun ProtectionSetupIncompleteCard(
    protectionSetupState: ProtectionSetupState,
    onOpenProtectionSetup: () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val items = (
        protectionSetupState.incompleteCoreProtectionItems +
            protectionSetupState.skippedCoreProtectionItems
        )
        .distinct()
        .sortedBy { it.ordinal }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isDarkTheme) BorderStroke(1.dp, SettingsBoxBorder.copy(alpha = 0.55f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ImpulsiveFocusMode),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Protection setup incomplete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "These settings help Impulsive step in during difficult habit moments. You can enable them now or later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { item ->
                MissingProtectionItemRow(item = item)
            }
            Button(
                onClick = onOpenProtectionSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Finish protection setup")
            }
        }
    }
}

@Composable
private fun MissingProtectionItemRow(item: ProtectionSetupItem) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = item.protectionReasonText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ProtectionSetupItem.protectionReasonText(): String = when (this) {
    ProtectionSetupItem.BlockedApps ->
        "Choose the apps where Impulsive should create a pause before the loop continues."
    ProtectionSetupItem.UsageAccess ->
        "Allows Impulsive to detect protected apps without reading private content."
    ProtectionSetupItem.Notifications ->
        "Lets Impulsive tell you when a planned window opens or protection turns back on."
    ProtectionSetupItem.UninstallProtection ->
        "Adds friction before uninstalling during weak moments. You stay in control."
    ProtectionSetupItem.InterruptionPermission ->
        "Allows stronger pivot tools later when you explicitly enable them."
    ProtectionSetupItem.BackgroundActivity ->
        "Helps Impulsive restart after reboot and avoid being stopped by battery optimization."
    ProtectionSetupItem.WebsiteProtection ->
        "Lets Impulsive protect selected domains when website blocking is added."
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Shape Impulsive around how you Notice, Pivot and Understand.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfileGroup(
    displayName: String,
    avatar: AvatarStyle,
    currentLevel: Int,
    answers: OnboardingAnswers,
    haptics: ImpulsiveHaptics,
    onSaveProfile: (String, String, () -> Unit) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable(displayName) { mutableStateOf(displayName) }
    var draftAvatarId by rememberSaveable(avatar.id) { mutableStateOf(avatar.id) }

    AccordionGroup(
        title = "Profile",
        summary = "$displayName • Psychological Core",
        icon = Icons.Filled.Person,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(ProfileGlow),
        leadingContent = {
            AvatarCircle(avatar = avatar, size = 38.dp, imageSize = 32.dp)
        },
    ) {
        Text(
            text = displayName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProfileMetric(label = "Level", value = "$currentLevel")
            ProfileMetric(label = "Path", value = "Psychological Core")
        }
        Spacer(modifier = Modifier.height(12.dp))
        PillLabel(text = "Private on this device")
        SettingsDivider()
        SettingsRow(
            title = "Edit profile",
            subtext = "Name and avatar",
            onClick = {
                draftName = displayName
                draftAvatarId = avatar.id
                editing = true
            },
        )
        AnimatedVisibility(
            visible = editing,
            enter = settingsExpandEnter(),
            exit = settingsCollapseExit(),
        ) {
            ProfileEditPanel(
                draftName = draftName,
                onDraftNameChanged = { draftName = it },
                draftAvatarId = draftAvatarId,
                onAvatarSelected = {
                    if (it.id != draftAvatarId) {
                        haptics.light()
                        draftAvatarId = it.id
                    }
                },
                onSave = {
                    onSaveProfile(draftName, draftAvatarId) {
                        editing = false
                    }
                },
                onCancel = {
                    draftName = displayName
                    draftAvatarId = AvatarStyle.fromId(answers.avatarId).id
                    editing = false
                },
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileEditPanel(
    draftName: String,
    onDraftNameChanged: (String) -> Unit,
    draftAvatarId: String,
    onAvatarSelected: (AvatarStyle) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = draftName,
            onValueChange = onDraftNameChanged,
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Text(
            text = "Avatar",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        AvatarStyle.entries.chunked(3).forEach { rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowAvatars.forEach { avatar ->
                    AvatarPickerItem(
                        avatar = avatar,
                        selected = avatar.id == draftAvatarId,
                        onClick = { onAvatarSelected(avatar) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) {
                Text(text = "Cancel")
            }
            Button(
                onClick = onSave,
                enabled = draftName.trim().isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
private fun AvatarPickerItem(
    avatar: AvatarStyle,
    selected: Boolean,
    haptics: ImpulsiveHaptics? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    if (!selected) {
                        haptics?.light()
                    }
                    onClick()
                },
            )
            .background(if (selected) ImpulsivePsychological.copy(alpha = 0.32f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) ImpulsivePsychological else Color.Transparent,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AvatarCircle(avatar = avatar, size = 58.dp, imageSize = 50.dp)
    }
}

@Composable
private fun AppearanceGroup(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    haptics: ImpulsiveHaptics,
    hapticsEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    soundEffectsEnabled: Boolean,
    onSoundEffectsChanged: (Boolean) -> Unit,
) {
    AccordionGroup(
        title = "Appearance",
        summary = "Theme, haptics, and sound",
        icon = Icons.Filled.Palette,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(AppearanceGlow),
    ) {
        Text(
            text = "Theme",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ThemeSegmentedSelector(
            selectedMode = selectedMode,
            haptics = haptics,
            onModeSelected = onModeSelected,
        )
        SettingsDivider()
        SettingsRow(
            title = "Haptics",
            trailing = {
                SettingsSwitch(
                    checked = hapticsEnabled,
                    haptics = haptics,
                    onCheckedChange = onHapticsChanged,
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Sound effects",
            trailing = {
                SettingsSwitch(
                    checked = soundEffectsEnabled,
                    haptics = haptics,
                    onCheckedChange = onSoundEffectsChanged,
                )
            },
        )
    }
}

@Composable
private fun ThemeSegmentedSelector(
    selectedMode: ThemeMode,
    haptics: ImpulsiveHaptics,
    onModeSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.Light to "Light",
        ThemeMode.Dark to "Dark",
        ThemeMode.AsPerTime to "Auto by time",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (selectedMode != mode) {
                            haptics.light()
                            onModeSelected(mode)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RecoverySetupGroup(
    answers: OnboardingAnswers,
    onEditTriggers: (List<String>) -> Unit,
    onEditTiming: (List<String>) -> Unit,
    onEditWeeklyTarget: (String?) -> Unit,
) {
    var editing by remember { mutableStateOf<RecoveryEditTarget?>(null) }

    AccordionGroup(
        title = "Pivot setup",
        summary = recoverySummary(answers),
        icon = Icons.Filled.Spa,
        haptics = null,
        glowSpec = SettingsGlowSpec.single(RecoverySetupGlow),
    ) {
        SettingsRow(title = "Onboarding answers", subtext = "Tap an item below to update it")
        SettingsDivider()
        SettingsRow(
            title = "Cues",
            value = answerListSummary(answers.triggers, TriggerLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.Triggers },
        )
        SettingsDivider()
        SettingsRow(
            title = "Timing pattern",
            value = answerListSummary(answers.timing, TimingLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.Timing },
        )
        SettingsDivider()
        SettingsRow(
            title = "Weekly target",
            value = answerLabel(answers.weekOneGoal, WeekOneLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.WeeklyTarget },
        )
        SettingsDivider()
        SettingsRow(title = "Daily support estimate", value = "${answers.dailyRelapseUrgeCount} moments per day")

        when (editing) {
            RecoveryEditTarget.Triggers -> MultiSelectEditDialog(
                title = "Cues",
                options = TriggerLabels,
                selected = answers.triggers,
                onConfirm = { onEditTriggers(it); editing = null },
                onDismiss = { editing = null },
            )
            RecoveryEditTarget.Timing -> MultiSelectEditDialog(
                title = "Timing pattern",
                options = TimingLabels,
                selected = answers.timing,
                onConfirm = { onEditTiming(it); editing = null },
                onDismiss = { editing = null },
            )
            RecoveryEditTarget.WeeklyTarget -> SingleSelectEditDialog(
                title = "Weekly target",
                options = WeekOneLabels,
                selected = answers.weekOneGoal,
                onConfirm = { onEditWeeklyTarget(it); editing = null },
                onDismiss = { editing = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun MultiSelectEditDialog(
    title: String,
    options: Map<String, String>,
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = if (id in current) current - id else current + id
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = id in current,
                            onCheckedChange = { checked ->
                                current = if (checked) current + id else current - id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toList()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SingleSelectEditDialog(
    title: String,
    options: Map<String, String>,
    selected: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { current = id }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = current == id, onClick = { current = id })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProtectionFocusGroup(
    protectionState: ProtectionSetupState,
    appLockEnabled: Boolean,
    guard: (enabled: Boolean, action: () -> Unit) -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenUninstallProtection: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectedCount = protectionState.selectedBlockedAppPackageNames.size
    val monitoredAppsValue = if (selectedCount == 0) "Not configured" else "$selectedCount selected"
    val monitoredAppsSubtext = if (selectedCount == 0) {
        "Let Impulsive suggest apps that often lead into the loop."
    } else {
        "Tap to review or change protected apps."
    }

    AccordionGroup(
        title = "Protection & Focus",
        summary = "Protected apps, website protection, and focus defaults",
        icon = Icons.Filled.Security,
        haptics = null,
        glowSpec = SettingsGlowSpec.split(ProtectionGlow, FocusGlow),
    ) {
        SettingsRow(
            title = "Protected apps",
            value = monitoredAppsValue,
            subtext = monitoredAppsSubtext,
            onClick = onOpenBlockedApps,
        )
        SettingsDivider()
        SettingsRow(
            title = "Usage Access",
            value = if (protectionState.usageAccessEnabled) "Enabled" else "Not enabled",
            subtext = "Lets Impulsive detect when a protected app opens. Required for protection to work.",
            onClick = {
                val intent = UsageAccessPermissionChecker(context).createUsageAccessSettingsIntent()
                runCatching { context.startActivity(intent) }
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Uninstall protection",
            value = if (protectionState.uninstallProtectionEnabled) "Active" else "Off",
            subtext = if (protectionState.uninstallProtectionEnabled) {
                "Extra removal step is active."
            } else {
                "Add friction before removing Impulsive during weak moments."
            },
            onClick = { guard(appLockEnabled) { onOpenUninstallProtection() } },
        )
        SettingsDivider()
        SettingsRow(
            title = "Battery optimization",
            value = if (protectionState.backgroundActivityEnabled) "Allowed" else "Needs review",
            subtext = "Helps protection survive reboot and battery optimization.",
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .apply {
                        data = Uri.parse("package:" + context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                runCatching { context.startActivity(intent) }
            },
        )
        if (protectionState.usageAccessEnabled && protectionState.selectedBlockedAppPackageNames.isNotEmpty()) {
            SettingsDivider()
            SettingsRow(
                title = "Protection monitor",
                subtext = "Checks protected apps during protected time.",
                onClick = { ProtectionServiceController.start(context) },
            )
            SettingsDivider()
            SettingsRow(
                title = "Pause protection monitor",
                subtext = "Stops the monitor until you start it again.",
                onClick = { guard(appLockEnabled) { ProtectionServiceController.stop(context) } },
            )
        }
    }
}

@Composable
private fun PrivacyAccountGroup(
    appLockEnabled: Boolean,
    onDisableAppLock: () -> Unit,
    hideSensitiveNotifications: Boolean,
    onHideSensitiveNotificationsChanged: (Boolean) -> Unit,
    notificationsAllowed: Boolean,
    haptics: ImpulsiveHaptics,
    onExportData: () -> Unit,
    onDeleteAllData: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLocalDataInfo by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSetPin by remember { mutableStateOf(false) }
    val notificationValue = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Allowed by system"
        notificationsAllowed -> "Allowed"
        else -> "Not allowed"
    }

    AccordionGroup(
        title = "Privacy & account",
        summary = "Permissions, local data, and account links",
        icon = Icons.Filled.PrivacyTip,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(PrivacyGlow),
    ) {
        SettingsRow(
            title = "App lock",
            subtext = if (appLockEnabled) {
                "Fingerprint or PIN required to open Impulsive"
            } else {
                "Add a fingerprint or PIN to keep Impulsive private"
            },
            trailing = {
                SettingsSwitch(
                    checked = appLockEnabled,
                    haptics = haptics,
                    onCheckedChange = { wantOn ->
                        if (wantOn) showSetPin = true else onDisableAppLock()
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Hide sensitive notifications",
            trailing = {
                SettingsSwitch(
                    checked = hideSensitiveNotifications,
                    haptics = haptics,
                    onCheckedChange = onHideSensitiveNotificationsChanged,
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Notifications",
            value = notificationValue,
            trailing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
                {
                    TextButtonPill(
                        text = "Allow",
                        haptics = haptics,
                        onClick = onRequestNotifications,
                    )
                }
            } else {
                null
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Local data",
            subtext = "Stored privately on this device",
            onClick = { showLocalDataInfo = true },
        )
        SettingsDivider()
        SettingsRow(
            title = "Export data",
            subtext = "Save a readable copy and share it anywhere",
            onClick = onExportData,
        )
        SettingsDivider()
        SettingsRow(
            title = "Delete data",
            subtext = "Permanently erase all data on this device",
            trailingIcon = Icons.Filled.DeleteOutline,
            onClick = { showDeleteConfirm = true },
        )
        SettingsDivider()
        SettingsRow(title = "Link Google account", subtext = "Not connected")
        SettingsDivider()
        SettingsRow(title = "Link Facebook account", subtext = "Not connected")
        SettingsDivider()
        SettingsRow(title = "Backup & sync", subtext = "Not connected")
        if (showLocalDataInfo) {
            AlertDialog(
                onDismissRequest = { showLocalDataInfo = false },
                confirmButton = { TextButton(onClick = { showLocalDataInfo = false }) { Text("Got it") } },
                title = { Text("Where your data is stored") },
                text = {
                    Text(
                        "All your notes, sessions, and settings stay in Impulsive's private storage on " +
                            "this device only:\n\n${context.applicationInfo.dataDir}\n\nNothing is uploaded. " +
                            "Other apps cannot read this folder. Use Delete data to remove everything."
                    )
                },
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete all data?") },
                text = {
                    Text(
                        "This permanently erases your notes, sessions, scores, settings, and " +
                            "everything else stored on this device. This cannot be undone, and the app " +
                            "will restart."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDeleteAllData()
                    }) { Text("Delete everything") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }
        if (showSetPin) {
            Dialog(
                onDismissRequest = { showSetPin = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                SetPinScreen(
                    onPinSet = { showSetPin = false },
                    onCancel = { showSetPin = false },
                )
            }
        }
    }
}

@Composable
private fun SupportGroup() {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    AccordionGroup(
        title = "Support",
        summary = "Help, feedback, and about",
        icon = Icons.Filled.AutoAwesome,
        haptics = null,
        glowSpec = SettingsGlowSpec.single(SupportGlow),
    ) {
        SettingsRow(
            title = "Help centre",
            trailingIcon = Icons.AutoMirrored.Filled.HelpOutline,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://useimpulsive.com/help.html"))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { context.startActivity(intent) }
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Contact support",
            trailingIcon = Icons.Filled.MailOutline,
            onClick = { sendSupportEmail(context, "Impulsive support") },
        )
        SettingsDivider()
        SettingsRow(
            title = "Send feedback",
            trailingIcon = Icons.Filled.ChatBubbleOutline,
            onClick = { sendSupportEmail(context, "Impulsive feedback") },
        )
        SettingsDivider()
        SettingsRow(
            title = "Report a bug",
            trailingIcon = Icons.Filled.BugReport,
            onClick = {
                sendSupportEmail(
                    context,
                    "Impulsive bug report",
                    "\n\n---\nApp version: ${appVersionName(context)}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}",
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "About Impulsive",
            trailingIcon = Icons.Filled.Info,
            onClick = { showAbout = true },
        )
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) { Text("Close") }
                },
                title = { Text("About Impulsive") },
                text = {
                    Column {
                        Text("Impulsive helps you notice a difficult moment, create a pause, choose a next step, and understand your patterns.")
                        Spacer(Modifier.height(8.dp))
                        Text("If your patterns are causing serious distress, harm, or feel difficult to stop despite unwanted consequences, consider speaking with a qualified professional or a trusted support service.")
                        Spacer(Modifier.height(8.dp))
                        Text("Impulsive is a behaviour-change support tool for adults. It is not a medical device, therapy service, diagnosis tool, crisis-support service, or clinically validated treatment. It does not diagnose, treat, cure, or prevent addiction, compulsions, mental health conditions, or any medical condition. It helps you create a pause, choose a next step, and understand your patterns.")
                        Spacer(Modifier.height(8.dp))
                        Text("Version ${appVersionName(context)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("useimpulsive.com", style = MaterialTheme.typography.bodySmall)
                        Text("Hello@useimpulsive.com", style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        }
    }
}

@Composable
private fun PlusGroup(
    haptics: ImpulsiveHaptics,
    onViewPlus: () -> Unit,
) {
    AccordionGroup(
        title = "Impulsive Plus",
        summary = "Unlock stronger pivot tools",
        icon = Icons.Filled.AutoAwesome,
        haptics = haptics,
        headerExtra = { PlusBadge() },
        glowSpec = SettingsGlowSpec.rainbow(PlusRainbowGlow),
    ) {
        Text(
            text = "Includes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PlusFeatureRow(title = "Advanced Nexus routing")
        SettingsDivider()
        PlusFeatureRow(title = "Physical and Spiritual paths")
        SettingsDivider()
        PlusFeatureRow(title = "Temperature Focus")
        SettingsDivider()
        PlusFeatureRow(title = "Premium pivot games")
        SettingsDivider()
        PlusFeatureRow(title = "Deeper weekly insights")
        SettingsDivider()
        PlusFeatureRow(
            title = "Website protection",
            note = "DNS-based filtering, stronger anti-bypass tools, and future cloud protection",
        )
        SettingsDivider()
        PlusFeatureRow(title = "Restore purchases", note = "Available when billing is connected")

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                haptics.confirm()
                onViewPlus()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(text = "View Plus")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Calm upgrade only. Never during a difficult habit moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AccordionGroup(
    title: String,
    summary: String,
    icon: ImageVector,
    haptics: ImpulsiveHaptics? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    glowSpec: SettingsGlowSpec? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val borderFlowRotation = if (isDarkTheme && glowSpec?.animated == true) {
        val infiniteTransition = rememberInfiniteTransition(label = "$title-border-flow")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "$title-border-rotation",
        )
        rotation
    } else {
        0f
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) SettingsArrowExpandMillis else SettingsArrowCollapseMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "$title-arrow-rotation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .settingsDarkGlowBorder(
                isDarkTheme = isDarkTheme,
                glowSpec = glowSpec,
                borderFlowRotation = borderFlowRotation,
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        border = if (isDarkTheme && glowSpec != null && !glowSpec.animated) {
            BorderStroke(1.2.dp, glowSpec.colors.first().copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        haptics?.light()
                        expanded = !expanded
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ImpulsivePsychological.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
                headerExtra?.invoke()
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = settingsExpandEnter(),
                exit = settingsCollapseExit(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    content = content,
                )
            }
        }
    }
}

private fun settingsExpandEnter() =
    expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = tween(durationMillis = SettingsExpandMillis, easing = FastOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SettingsFadeInMillis,
            delayMillis = SettingsFadeInDelayMillis,
            easing = FastOutSlowInEasing,
        ),
    )

private fun settingsCollapseExit() =
    fadeOut(
        animationSpec = tween(durationMillis = SettingsFadeOutMillis, easing = FastOutSlowInEasing),
    ) + shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = tween(durationMillis = SettingsCollapseMillis, easing = FastOutSlowInEasing),
    )

private data class SettingsGlowSpec(
    val colors: List<Color>,
    val animated: Boolean = false,
    val split: Boolean = false,
) {
    companion object {
        fun single(color: Color): SettingsGlowSpec = SettingsGlowSpec(colors = listOf(color))
        fun split(left: Color, right: Color): SettingsGlowSpec = SettingsGlowSpec(
            colors = listOf(left, right),
            split = true,
        )
        fun rainbow(colors: List<Color>): SettingsGlowSpec = SettingsGlowSpec(
            colors = colors,
            animated = true,
        )
    }
}

private fun Modifier.settingsDarkGlowBorder(
    isDarkTheme: Boolean,
    glowSpec: SettingsGlowSpec?,
    borderFlowRotation: Float,
): Modifier {
    if (!isDarkTheme || glowSpec == null) return this

    return drawWithContent {
        drawContent()

        val cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
        val glowWidth = 7.dp.toPx()
        val borderWidth = 1.35.dp.toPx()
        val borderInset = borderWidth / 2f
        val borderSize = Size(
            width = size.width - borderWidth,
            height = size.height - borderWidth,
        )
        val glowInset = glowWidth / 2f
        val glowSize = Size(
            width = size.width - glowWidth,
            height = size.height - glowWidth,
        )

        val glowBrush = settingsGlowBrush(
            spec = glowSpec,
            width = size.width,
            height = size.height,
            rotationDegrees = borderFlowRotation,
            alpha = 0.20f,
        )
        val borderBrush = settingsGlowBrush(
            spec = glowSpec,
            width = size.width,
            height = size.height,
            rotationDegrees = borderFlowRotation,
            alpha = if (glowSpec.animated) 0.95f else 0.78f,
        )

        drawRoundRect(
            brush = glowBrush,
            topLeft = Offset(glowInset, glowInset),
            size = glowSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = glowWidth),
        )
        drawRoundRect(
            brush = borderBrush,
            topLeft = Offset(borderInset, borderInset),
            size = borderSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = borderWidth),
        )
    }
}

private fun settingsGlowBrush(
    spec: SettingsGlowSpec,
    width: Float,
    height: Float,
    rotationDegrees: Float,
    alpha: Float,
): Brush {
    val colours = spec.colors.map { it.copy(alpha = alpha) }

    if (spec.animated) {
        val radians = rotationDegrees / 180f * PI.toFloat()
        val radius = max(width, height)
        val center = Offset(width / 2f, height / 2f)
        val direction = Offset(
            x = cos(radians) * radius,
            y = sin(radians) * radius,
        )
        return Brush.linearGradient(
            colors = colours,
            start = center - direction,
            end = center + direction,
        )
    }

    if (spec.split && colours.size >= 2) {
        return Brush.horizontalGradient(
            colors = listOf(colours[0], colours[0], colours[1], colours[1]),
            startX = 0f,
            endX = width,
        )
    }

    val colour = colours.first()
    return Brush.linearGradient(
        colors = listOf(
            colour.copy(alpha = alpha),
            colour.copy(alpha = alpha * 0.52f),
            colour.copy(alpha = alpha),
        ),
        start = Offset.Zero,
        end = Offset(width, height),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtext: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .height(if (subtext == null) 42.dp else 58.dp)
        .then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
            } else {
                Modifier
            },
        )

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 14.sp,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 10.dp)) {
                trailing()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun PillLabel(text: String) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.28f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PlusBadge() {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.34f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "PLUS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PlusFeatureRow(
    title: String,
    note: String = "Included in Plus",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TextButtonPill(
    text: String,
    haptics: ImpulsiveHaptics? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.28f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable {
            haptics?.light()
            onClick()
        },
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    haptics: ImpulsiveHaptics,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = { next ->
            if (next != checked) {
                haptics.light()
                onCheckedChange(next)
            }
        },
        modifier = Modifier.size(width = 48.dp, height = 28.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            checkedTrackColor = ImpulsivePsychological,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AvatarCircle(
    avatar: AvatarStyle,
    size: androidx.compose.ui.unit.Dp,
    imageSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatar.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = avatar.drawableResId),
            contentDescription = avatar.contentDescription,
            modifier = Modifier
                .size(imageSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun isNotificationPermissionAllowed(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun sendSupportEmail(context: Context, subject: String, body: String = "") {
    val uri = Uri.parse(
        "mailto:Hello@useimpulsive.com" +
            "?subject=" + Uri.encode(subject) +
            (if (body.isNotBlank()) "&body=" + Uri.encode(body) else "")
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
}

private fun appVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

private fun recoverySummary(answers: OnboardingAnswers): String {
    val triggerCount = answers.triggers.size
    val timingCount = answers.timing.size
    return when {
        triggerCount > 0 && timingCount > 0 -> "$triggerCount cues, $timingCount timing cues"
        triggerCount > 0 -> "$triggerCount cues saved"
        timingCount > 0 -> "$timingCount timing cues saved"
        else -> "Setup answers and targets"
    }
}

private fun answerListSummary(
    selectedIds: List<String>,
    labels: Map<String, String>,
    emptyText: String,
): String {
    val selected = selectedIds.mapNotNull { labels[it] }
    return when {
        selected.isEmpty() -> emptyText
        selected.size == 1 -> selected.first()
        else -> "${selected.size} saved"
    }
}

private fun answerLabel(
    selectedId: String?,
    labels: Map<String, String>,
    emptyText: String,
): String = selectedId?.let { labels[it] } ?: emptyText

private enum class RecoveryEditTarget { Triggers, Timing, WeeklyTarget }

private const val SettingsExpandMillis = 220
private const val SettingsCollapseMillis = 170
private const val SettingsFadeInMillis = 120
private const val SettingsFadeInDelayMillis = 35
private const val SettingsFadeOutMillis = 85
private const val SettingsArrowExpandMillis = 180
private const val SettingsArrowCollapseMillis = 140

private val SettingsBoxBorder = Color(0xFFD0C3F1)
private val ProfileGlow = SettingsBoxBorder
private val AppearanceGlow = SettingsBoxBorder
private val RecoverySetupGlow = SettingsBoxBorder
private val ProtectionGlow = SettingsBoxBorder
private val FocusGlow = SettingsBoxBorder
private val PrivacyGlow = SettingsBoxBorder
private val SupportGlow = SettingsBoxBorder
private val PlusRainbowGlow = listOf(
    Color(0xFFD0C3F1),
    Color(0xFFBDE0FE),
    Color(0xFFBAFFC9),
    Color(0xFFFFFFBA),
    Color(0xFFF5A7A6),
    Color(0xFFD8B0EB),
    Color(0xFFD0C3F1),
)

private val TriggerLabels = mapOf(
    "social_media" to "Social media",
    "browser_search" to "A browser search",
    "memory_or_thought" to "A memory or thought",
    "boredom" to "Boredom",
    "being_alone" to "Being alone",
    "stress" to "Stress",
)

private val TimingLabels = mapOf(
    "late_at_night" to "Late at night",
    "right_after_waking" to "Right after waking",
    "alone_on_phone" to "Alone on my phone",
    "when_bored" to "When bored",
    "when_stressed" to "When stressed",
    "trouble_sleeping" to "Trouble sleeping",
)

private val WeekOneLabels = mapOf(
    "notice_triggers" to "Notice my cues",
    "cut_down_a_little" to "Cut down a little",
    "daily_reset_habit" to "Build one daily reset habit",
    "cut_down_by_half" to "Cut down by half",
)
