package com.impulsive.app.frontend.screens.protection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.security.antibypass.UninstallProtectionManager

@Composable
fun UninstallProtectionScreen(
    state: ProtectionSetupState,
    onBack: () -> Unit,
    onEnabledSynced: (Boolean) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { UninstallProtectionManager(context) }
    val enableAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        onEnabledSynced(manager.isActive())
    }

    LaunchedEffect(manager) {
        onEnabledSynced(manager.isActive())
    }

    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEnabledSynced(manager.isActive())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isActive = state.uninstallProtectionEnabled || manager.isActive()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ProtectionStatusCard(isActive = isActive)
                Text(
                    text = "Make Impulsive harder to remove during weak moments.",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "This adds an extra Android system step before uninstalling. " +
                        "It does not trap you. You stay in control and can turn this off later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CalmInfoRow("You stay in control.")
                    CalmInfoRow("The app does not hide itself.")
                    CalmInfoRow("No device wipe, password reset, lock, or camera control is used.")
                    CalmInfoRow("If you disable this, protection can still work, but removal becomes easier.")
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isActive) {
                    Button(
                        onClick = {
                            manager.disableOwnAdmin()
                            onEnabledSynced(manager.isActive())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpulsivePsychological,
                            contentColor = Color(0xFF2F2637),
                        ),
                    ) {
                        Text("Turn off uninstall protection")
                    }
                } else {
                    Button(
                        onClick = {
                            enableAdminLauncher.launch(manager.buildEnableIntent())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpulsivePsychological,
                            contentColor = Color(0xFF2F2637),
                        ),
                    ) {
                        Text("Enable uninstall protection")
                    }
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Do this later")
                    }
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusCard(isActive: Boolean) {
    val borderColor = if (isActive) {
        Color(0xFFD0C3F1)
    } else {
        ImpulsivePsychological.copy(alpha = 0.65f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(28.dp),
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = borderColor,
        )
        Column {
            Text(
                text = if (isActive) {
                    "Uninstall protection is active"
                } else {
                    "Uninstall protection is off"
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isActive) {
                    "Impulsive now has an extra removal step."
                } else {
                    "Enable it to add friction before removing the app."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CalmInfoRow(text: String) {
    Text(
        text = "- $text",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}
