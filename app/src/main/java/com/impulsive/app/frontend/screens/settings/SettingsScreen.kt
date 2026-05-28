package com.impulsive.app.frontend.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.ThemeMode
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import com.impulsive.app.frontend.theme.ImpulsivePsychological

@Composable
fun SettingsScreen(
    onBackHome: () -> Unit,
    onOpenHome: () -> Unit = onBackHome,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val onboardingState by onboardingViewModel.state.collectAsState()
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val storedMode by themeViewModel.themeMode.collectAsState()
    val selectedMode = if (storedMode == ThemeMode.System) ThemeMode.AsPerTime else storedMode
    val displayName = onboardingState.answers.name.takeIf { it.isNotBlank() } ?: "Shanon"
    val avatar = AvatarStyle.fromId(onboardingState.answers.avatarId)
    val background = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

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
                .padding(top = 18.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader(onBackHome = onBackHome)
            ProfileCard(displayName = displayName, avatar = avatar)
            PlusCard()
            AppearanceCard(
                selectedMode = selectedMode,
                onModeSelected = themeViewModel::setThemeMode,
            )
            RecoverySetupCard()
            ProtectionFocusCard()
            PrivacyAccountCard()
            SupportPlusCard()
        }

        BottomNavBar(
            selected = BottomNavItem.Settings,
            onSelect = { item ->
                if (item == BottomNavItem.Home) onOpenHome()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsHeader(onBackHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackHome,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Shape Impulsive around how you recover.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfileCard(displayName: String, avatar: AvatarStyle) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(avatar.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = avatar.drawableResId),
                    contentDescription = avatar.contentDescription,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f),
            ) {
                Text(
                    text = displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ProfileMetric(label = "Level", value = "4")
                    ProfileMetric(label = "Active", value = "Psychological Core")
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        PillLabel(text = "Private on this device")
        SettingsDivider()
        SettingsRow(title = "Edit profile", trailingIcon = Icons.Filled.KeyboardArrowRight)
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
private fun PlusCard() {
    SettingsCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Impulsive Plus",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    PlusBadge()
                }
                Text(
                    text = "Unlock stronger recovery tools",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = ImpulsivePsychological,
                modifier = Modifier.size(22.dp),
            )
        }
        SettingsDivider()
        SettingsRow(title = "Referral unlock progress", trailingIcon = Icons.Filled.KeyboardArrowRight)
    }
}

@Composable
private fun AppearanceCard(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
) {
    var hapticsEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(false) }

    SettingsCard(title = "APPEARANCE") {
        Text(
            text = "Theme",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ThemeSegmentedSelector(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
        )
        SettingsDivider()
        SettingsRow(
            title = "Haptics",
            trailing = {
                SettingsSwitch(
                    checked = hapticsEnabled,
                    onCheckedChange = { hapticsEnabled = it },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Sound effects",
            trailing = {
                SettingsSwitch(
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                )
            },
        )
    }
}

@Composable
private fun ThemeSegmentedSelector(
    selectedMode: ThemeMode,
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
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        onModeSelected(mode)
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
private fun RecoverySetupCard() {
    SettingsCard(title = "RECOVERY SETUP", leadingIcon = Icons.Filled.AutoAwesome) {
        SettingsRow(title = "Onboarding answers", trailingIcon = Icons.Filled.KeyboardArrowRight)
        SettingsDivider()
        SettingsRow(
            title = "Triggers",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillLabel(text = "3 Active")
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        SettingsDivider()
        SettingsRow(title = "Weekly target", value = "Maintain")
    }
}

@Composable
private fun ProtectionFocusCard() {
    SettingsCard(title = "PROTECTION & FOCUS") {
        SettingsRow(title = "Monitored apps", value = "12")
        SettingsDivider()
        SettingsRow(title = "Blocked websites", value = "45")
        SettingsDivider()
        SettingsRow(title = "Browser protection", value = "Active", valueColor = ImpulsiveOverallTheme)
        SettingsDivider()
        SettingsRow(title = "Default focus", value = "25 min")
        Spacer(modifier = Modifier.height(12.dp))
        LockedPlusPanel()
    }
}

@Composable
private fun LockedPlusPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = "Temperature Focus",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Adjusts to your stress level",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            PlusBadge()
        }
    }
}

@Composable
private fun PrivacyAccountCard() {
    var hideNotifications by remember { mutableStateOf(false) }

    SettingsCard(title = "PRIVACY & ACCOUNT", leadingIcon = Icons.Filled.Lock) {
        SettingsRow(
            title = "App lock",
            subtext = "PIN or biometric",
            trailingIcon = Icons.Filled.KeyboardArrowRight,
        )
        SettingsDivider()
        SettingsRow(
            title = "Hide sensitive notifications",
            trailing = {
                SettingsSwitch(
                    checked = hideNotifications,
                    onCheckedChange = { hideNotifications = it },
                )
            },
        )
        SettingsDivider()
        SettingsRow(title = "Local data", subtext = "Stored privately on this device")
        SettingsDivider()
        SettingsRow(title = "Export data", trailingIcon = Icons.Filled.KeyboardArrowRight)
        SettingsDivider()
        SettingsRow(title = "Delete data", trailingIcon = Icons.Filled.DeleteOutline)
        SettingsDivider()
        SettingsRow(title = "Link Google account", trailingIcon = Icons.Filled.KeyboardArrowRight)
        SettingsDivider()
        SettingsRow(title = "Link Apple account", trailingIcon = Icons.Filled.KeyboardArrowRight)
        SettingsDivider()
        SettingsRow(title = "Backup & sync", subtext = "Not connected")
        SettingsDivider()
        SettingsRow(title = "Restore purchases", trailingIcon = Icons.Filled.Refresh)
    }
}

@Composable
private fun SupportPlusCard() {
    SettingsCard(title = "SUPPORT & PLUS") {
        SettingsRow(title = "Help centre", trailingIcon = Icons.Filled.HelpOutline)
        SettingsDivider()
        SettingsRow(title = "Contact support", trailingIcon = Icons.Filled.MailOutline)
        SettingsDivider()
        SettingsRow(title = "Send feedback", trailingIcon = Icons.Filled.ChatBubbleOutline)
        SettingsDivider()
        SettingsRow(title = "Report a bug", trailingIcon = Icons.Filled.BugReport)
        SettingsDivider()
        SettingsRow(title = "About Impulsive", trailingIcon = Icons.Filled.Info)
    }
}

@Composable
private fun SettingsCard(
    title: String? = null,
    leadingIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            if (title != null) {
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtext: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtext == null) 42.dp else 54.dp),
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
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        if (trailing != null) {
            trailing()
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
private fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
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
