package com.impulsive.app.frontend.screens.protection

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.backend.session.protection.DnsFilterGateUiState
import com.impulsive.app.frontend.theme.ImpulsivePsychological

@Composable
fun DnsFilterGateScreen(
    state: DnsFilterGateUiState,
    onOpenPrivateDnsSettings: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onTurnOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        onRefresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                Text(
                    text = "One quick check before protection turns on",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "For Impulsive to step in, it needs to see which sites are opened. " +
                        "A couple of phone settings can hide that view. Let us clear them first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (state.privateDnsActive) {
                    GateCard(
                        icon = Icons.Filled.Dns,
                        title = "Private DNS is on",
                        body = if (state.privateDnsHostname != null) {
                            "It is set to " + state.privateDnsHostname + ". While Private DNS is on, " +
                                "site checks are encrypted and Impulsive cannot read them. " +
                                "Set it to Off or Automatic, then come back."
                        } else {
                            "While Private DNS is on, site checks are encrypted and Impulsive cannot " +
                                "read them. Set it to Off or Automatic, then come back."
                        },
                    )
                }

                if (state.anotherVpnActive) {
                    GateCard(
                        icon = Icons.Filled.VpnKey,
                        title = "Another VPN is running",
                        body = "Android allows one VPN at a time, so Impulsive cannot start while " +
                            "another is active. Turn the other one off, then come back.",
                    )
                }

                if (state.protectionOn) {
                    GateCard(
                        icon = Icons.Filled.CheckCircle,
                        title = "Website protection is on",
                        body = "Impulsive is checking sites during your protected time.",
                    )
                }

                if (state.hasChecked && state.canEnable && !state.protectionOn) {
                    GateCard(
                        icon = Icons.Filled.CheckCircle,
                        title = "Nothing is in the way",
                        body = "Your settings are ready. You can turn on protection now.",
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    state.protectionOn -> {
                        PrimaryGateButton(
                            text = "Turn off protection",
                            onClick = onTurnOff,
                        )
                    }
                    state.canEnable -> {
                        PrimaryGateButton(
                            text = "Continue",
                            onClick = onContinue,
                        )
                    }
                    state.privateDnsActive -> {
                        PrimaryGateButton(
                            text = "Open network settings",
                            onClick = onOpenPrivateDnsSettings,
                        )
                    }
                    else -> {
                        PrimaryGateButton(
                            text = "Check again",
                            onClick = onRefresh,
                        )
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
private fun PrimaryGateButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = ImpulsivePsychological,
            contentColor = Color(0xFF2F2637),
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun GateCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ImpulsivePsychological.copy(alpha = 0.65f),
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
            imageVector = icon,
            contentDescription = null,
            tint = ImpulsivePsychological,
        )
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
