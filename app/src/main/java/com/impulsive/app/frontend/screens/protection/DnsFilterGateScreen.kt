package com.impulsive.app.frontend.screens.protection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    protectedBrowserPackageNames: Set<String>,
    websiteProtectionDisclosureAccepted:
        Boolean,
    continueInProgress:
        Boolean,
    disclosureSaveFailed:
        Boolean,
    onOpenPrivateDnsSettings: () -> Unit,
    onRefresh: () -> Unit,
    onContinue:
        (
            needsDisclosurePersistence:
                Boolean,
        ) -> Unit,
    onTurnOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner =
        LocalLifecycleOwner.current

    val browserSecureDnsGuides =
        remember(
            protectedBrowserPackageNames,
        ) {
            BrowserSecureDnsGuidancePolicy
                .requiredGuides(
                    protectedBrowserPackageNames,
                )
        }

    var confirmedBrowserSecureDnsGuides by
        remember(
            browserSecureDnsGuides,
        ) {
            mutableStateOf(
                emptySet<BrowserSecureDnsGuide>(),
            )
        }

    var disclosureAcknowledged by
        remember(
            websiteProtectionDisclosureAccepted,
        ) {
            mutableStateOf(
                false,
            )
        }

    val disclosureSatisfied =
        websiteProtectionDisclosureAccepted ||
            disclosureAcknowledged

    val continueEnabled =
        canContinueDnsFilterGate(
            state =
                state,
            requiredBrowserSecureDnsGuides =
                browserSecureDnsGuides,
            confirmedBrowserSecureDnsGuides =
                confirmedBrowserSecureDnsGuides,
        ) &&
            disclosureSatisfied &&
            !continueInProgress

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
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
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

                GateCard(
                    icon =
                        Icons.Filled.Dns,
                    title =
                        "How Website Protection handles DNS",
                    body =
                        "Website Protection uses Android's VPN permission to inspect DNS " +
                            "domain requests from the browsers you select. Impulsive checks " +
                            "blocked domains on this device. DNS requests that are not blocked " +
                            "locally are resolved over encrypted DNS-over-HTTPS using Cloudflare " +
                            "1.1.1.1 for Families. If Cloudflare is unavailable, AdGuard Family " +
                            "Protection may be used as a fallback. These DNS providers receive " +
                            "the domain names needed to answer those DNS requests. Impulsive " +
                            "does not route your normal web traffic through an Impulsive remote " +
                            "VPN server, and these DNS requests are not sent to Impulsive " +
                            "servers. Blocked-site incidents may be kept locally on this device " +
                            "to support protection.",
                )

                if (!websiteProtectionDisclosureAccepted) {
                    GateConfirmationCard(
                        text =
                            "I understand and agree to this Website Protection DNS handling",
                        checked =
                            disclosureAcknowledged,
                        onCheckedChange = { checked ->
                            disclosureAcknowledged =
                                checked
                        },
                    )
                }

                if (disclosureSaveFailed) {
                    GateCard(
                        icon =
                            Icons.Filled.Dns,
                        title =
                            "Your choice was not saved",
                        body =
                            "Website Protection was not started. Please try again.",
                    )
                }

                if (state.privateDnsActive) {
                    GateCard(
                        icon = Icons.Filled.Dns,
                        title = "Private DNS is on",
                        body = if (state.privateDnsHostname != null) {
                            "It is set to " + state.privateDnsHostname + ". While Private DNS is on, " +
                                "site checks are encrypted and Impulsive cannot read them. " +
                                "Set Private DNS to Off, then come back."
                        } else {
                            "While Private DNS is on, site checks are encrypted and Impulsive cannot " +
                                "read them. Set Private DNS to Off, then come back."
                        },
                    )
                }

                browserSecureDnsGuides
                    .forEach { guide ->
                        val copy =
                            guide.displayCopy()

                        GateCard(
                            icon =
                                Icons.Filled.Dns,
                            title =
                                copy.title,
                            body =
                                copy.body,
                        )

                        GateConfirmationCard(
                            text =
                                copy.confirmationText,
                            checked =
                                guide in
                                    confirmedBrowserSecureDnsGuides,
                            onCheckedChange = { checked ->
                                confirmedBrowserSecureDnsGuides =
                                    if (checked) {
                                        confirmedBrowserSecureDnsGuides +
                                            guide
                                    } else {
                                        confirmedBrowserSecureDnsGuides -
                                            guide
                                    }
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

                if (continueEnabled && !state.protectionOn) {
                    GateCard(
                        icon = Icons.Filled.CheckCircle,
                        title = "Nothing is in the way",
                        body = "Your settings are ready. You can turn on protection now.",
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 18.dp),
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
                            onClick = {
                                onContinue(
                                    !websiteProtectionDisclosureAccepted,
                                )
                            },
                            enabled = continueEnabled,
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
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = ImpulsivePsychological,
            contentColor = Color(0xFF2F2637),
        ),
    ) {
        Text(text)
    }
}

internal fun websiteProtectionDisclosureSatisfied(
    alreadyAccepted:
        Boolean,
    acknowledgedNow:
        Boolean,
): Boolean =
    alreadyAccepted ||
        acknowledgedNow

internal fun canContinueDnsFilterGate(
    state: DnsFilterGateUiState,
    requiredBrowserSecureDnsGuides:
        Collection<BrowserSecureDnsGuide>,
    confirmedBrowserSecureDnsGuides:
        Set<BrowserSecureDnsGuide>,
): Boolean =
    state.hasChecked &&
        state.canEnable &&
        confirmedBrowserSecureDnsGuides
            .containsAll(
                requiredBrowserSecureDnsGuides,
            )

private data class BrowserSecureDnsDisplayCopy(
    val title: String,
    val body: String,
    val confirmationText: String,
)

private fun BrowserSecureDnsGuide
    .displayCopy():
    BrowserSecureDnsDisplayCopy =
    when (this) {
        BrowserSecureDnsGuide.Chrome ->
            BrowserSecureDnsDisplayCopy(
                title =
                    "Chrome Secure DNS",
                body =
                    "Chrome can use its own encrypted DNS setting, which can bypass " +
                        "Impulsive. Impulsive cannot read this Chrome setting directly.\n\n" +
                        "In Chrome, open:\n" +
                        "Settings → Privacy and security → Use Secure DNS → Off",
                confirmationText =
                    "I turned off Secure DNS in Chrome",
            )

        BrowserSecureDnsGuide.Brave ->
            BrowserSecureDnsDisplayCopy(
                title =
                    "Brave Secure DNS",
                body =
                    "Brave can use its own encrypted DNS setting, which can bypass " +
                        "Impulsive. Impulsive cannot read this Brave setting directly.\n\n" +
                        "In Brave, open:\n" +
                        "Settings → Brave Shields & privacy → Use Secure DNS → Off",
                confirmationText =
                    "I turned off Secure DNS in Brave",
            )

        BrowserSecureDnsGuide.OtherBrowsers ->
            BrowserSecureDnsDisplayCopy(
                title =
                    "Secure DNS in other browsers",
                body =
                    "Some other protected browsers can use encrypted DNS outside Android's " +
                        "Private DNS setting. Impulsive cannot read those browser settings " +
                        "directly.\n\n" +
                        "Open each other protected browser's privacy or security settings and " +
                        "turn off Secure DNS, DNS over HTTPS or encrypted DNS before continuing.",
                confirmationText =
                    "I checked Secure DNS in my other protected browsers",
            )
    }

@Composable
private fun GateConfirmationCard(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
