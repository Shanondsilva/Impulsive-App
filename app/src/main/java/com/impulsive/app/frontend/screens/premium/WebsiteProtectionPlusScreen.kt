package com.impulsive.app.frontend.screens.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.repository.AuthResult
import com.impulsive.app.backend.domain.model.auth.PurchaseAccountGatePhase
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.service.billing.BillingRestoreState
import com.impulsive.app.backend.service.billing.BillingUiState
import com.impulsive.app.backend.service.billing.SubscriptionCatalogState
import com.impulsive.app.backend.service.billing.allowsPurchaseAction
import com.impulsive.app.backend.service.billing.subscriptionPlanDisclosure
import com.impulsive.app.backend.service.billing.subscriptionPlanTitle
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.theme.ImpulsivePsychological

@Composable
fun WebsiteProtectionPlusScreen(
    onBack: () -> Unit,
    onOpenDnsFilterCheck: () -> Unit,
    onChooseWebsiteProtectionApps: () -> Unit,
    isPlus: Boolean,
    subscriptionCatalogState: SubscriptionCatalogState,
    billingUiState: BillingUiState,
    purchaseAccountGatePhase: PurchaseAccountGatePhase,
    pendingAccountConflict: AuthResult.AccountConflict?,
    authErrorMessage: String?,
    onLinkGoogleForPurchase: () -> Unit,
    onLinkFacebookForPurchase: () -> Unit,
    onLinkEmailForPurchase: (String, String) -> Unit,
    onConfirmAccountSwitchForPurchase: () -> Unit,
    onDismissAccountSwitch: () -> Unit,
    onDismissAuthError: () -> Unit,
    onRetryBilling: () -> Unit,
    onPurchase: (BillingPeriod) -> Unit,
    canManageSubscription: Boolean,
    onManageSubscription: () -> Unit,
    billingRestoreState: BillingRestoreState,
    onRestorePurchases: () -> Unit,
    isWebsiteProtectionEnabled: Boolean,
    isWebsiteProtectionAlwaysOn: Boolean,
    isReleaseWindowActive: Boolean,
    releaseWindowEndsAt: String?,
    onTurnWebsiteProtectionOff: () -> Unit,
    onAlwaysOnChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val accent = ImpulsivePsychological
    var showPurchaseAccountGate by rememberSaveable { mutableStateOf(false) }
    val onProtectedPurchase: (BillingPeriod) -> Unit = { period ->
        if (purchaseAccountGatePhase == PurchaseAccountGatePhase.Ready) {
            onPurchase(period)
        } else {
            showPurchaseAccountGate = true
        }
    }

    LaunchedEffect(purchaseAccountGatePhase) {
        if (purchaseAccountGatePhase == PurchaseAccountGatePhase.Ready) {
            showPurchaseAccountGate = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = text,
                    )
                }
                Text(
                    text = "Back",
                    color = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (isPlus) {
                WebsiteProtectionManagementCard(
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                    enabled = isWebsiteProtectionEnabled,
                    alwaysOn = isWebsiteProtectionAlwaysOn,
                    releaseWindowActive = isReleaseWindowActive,
                    releaseWindowEndsAt = releaseWindowEndsAt,
                    onChooseApps = onChooseWebsiteProtectionApps,
                    onTurnOn = onOpenDnsFilterCheck,
                    onTurnOff = onTurnWebsiteProtectionOff,
                    onAlwaysOnChanged = onAlwaysOnChanged,
                )

                WebsiteProtectionExplanationCard(
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                )
            } else {
                PlusHeroCard(
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                    isPlus = isPlus,
                    subscriptionCatalogState = subscriptionCatalogState,
                    billingUiState = billingUiState,
                    onRetryBilling = onRetryBilling,
                    onPurchase = onProtectedPurchase,
                )

                PlusIncludedCard(
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                )

                PlusDisclosureCard(
                    title = "Important",
                    body = "Uses Android VPN permission for local DNS-based filtering. This is not a private browsing VPN and does not hide your IP address.",
                    icon = Icons.Filled.Info,
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                )

                PlusDisclosureCard(
                    title = "Privacy first",
                    body = "Website filtering should stay on device unless a future cloud feature is clearly added and consented to.",
                    icon = Icons.Filled.PrivacyTip,
                    accent = accent,
                    surface = surface,
                    text = text,
                    muted = muted,
                )
            }

            SubscriptionActionsCard(
                canManageSubscription = canManageSubscription,
                onManageSubscription = onManageSubscription,
                restoreState = billingRestoreState,
                onRestorePurchases = onRestorePurchases,
                surface = surface,
                text = text,
                muted = muted,
            )

            Text(
                text = "Impulsive Core stays free. Plus adds stronger website protection.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showPurchaseAccountGate) {
        PurchaseAccountGateDialog(
            phase = purchaseAccountGatePhase,
            authErrorMessage = authErrorMessage,
            pendingConflict = pendingAccountConflict,
            onLinkGoogle = onLinkGoogleForPurchase,
            onLinkFacebook = onLinkFacebookForPurchase,
            onLinkEmail = onLinkEmailForPurchase,
            onConfirmAccountSwitch = onConfirmAccountSwitchForPurchase,
            onDismissAccountSwitch = onDismissAccountSwitch,
            onDismissAuthError = onDismissAuthError,
            onDismiss = { showPurchaseAccountGate = false },
        )
    }
}

@Composable
private fun SubscriptionActionsCard(
    canManageSubscription: Boolean,
    onManageSubscription: () -> Unit,
    restoreState: BillingRestoreState,
    onRestorePurchases: () -> Unit,
    surface: Color,
    text: Color,
    muted: Color,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surface.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Subscription",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (canManageSubscription) {
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Manage subscription")
                }
            }

            OutlinedButton(
                onClick = onRestorePurchases,
                enabled = restoreState != BillingRestoreState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (restoreState == BillingRestoreState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking Google Play…")
                } else {
                    Text("Restore purchases")
                }
            }

            val statusText = when (restoreState) {
                BillingRestoreState.Idle -> null
                BillingRestoreState.Loading -> "Checking Google Play…"
                BillingRestoreState.Success -> "Your Plus subscription has been restored."
                BillingRestoreState.NoPurchase -> "No active Plus subscription was found."
                BillingRestoreState.Error -> "Restore failed. Check your connection and try again."
            }

            if (statusText != null) {
                Text(
                    text = statusText,
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

}

@Composable
private fun WebsiteProtectionManagementCard(
    accent: Color,
    surface: Color,
    text: Color,
    muted: Color,
    enabled: Boolean,
    alwaysOn: Boolean,
    releaseWindowActive: Boolean,
    releaseWindowEndsAt: String?,
    onChooseApps: () -> Unit,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onAlwaysOnChanged: (Boolean) -> Unit,
) {
    val pausedByReleaseWindow = enabled && releaseWindowActive && !alwaysOn
    val statusText = when {
        !enabled -> "Off"
        alwaysOn -> "Always on"
        pausedByReleaseWindow && releaseWindowEndsAt != null -> "Paused until $releaseWindowEndsAt"
        pausedByReleaseWindow -> "Paused during release window"
        else -> "Active"
    }

    Surface(
        color = surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Website Protection",
                color = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = statusText,
                color = if (enabled) accent else muted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = if (enabled) {
                    "Impulsive will manage website blocking around your protection rhythm."
                } else {
                    "Turn this on to block adult and risky website domains."
                },
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Browser Secure DNS must remain off for website blocking and " +
                    "SafeSearch enforcement to work.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                onClick = onChooseApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Choose apps for Website Protection")
            }

            if (enabled) {
                OutlinedButton(
                    onClick = onTurnOff,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Turn off Website Protection")
                }
            } else {
                Button(
                    onClick = onTurnOn,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF2F2637),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Turn on Website Protection")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep Website Protection always on",
                        color = text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "When off, it pauses during release windows and turns back on after.",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Switch(
                    checked = alwaysOn,
                    onCheckedChange = onAlwaysOnChanged,
                )
            }
        }
    }
}

@Composable
private fun WebsiteProtectionExplanationCard(
    accent: Color,
    surface: Color,
    text: Color,
    muted: Color,
) {
    Surface(
        color = surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "How it works",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Website Protection blocks adult and risky website domains using Android VPN permission for local DNS-based filtering.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "It is not a private browsing VPN, does not hide your IP address, and does not blur adult images inside social feeds or other apps.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "By default, it follows your Impulsive release windows: it pauses during the release window and turns back on when protected time resumes.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "When always-on is on, it stays active even during release windows.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlusHeroCard(
    accent: Color,
    surface: Color,
    text: Color,
    muted: Color,
    isPlus: Boolean,
    subscriptionCatalogState: SubscriptionCatalogState,
    billingUiState: BillingUiState,
    onRetryBilling: () -> Unit,
    onPurchase: (BillingPeriod) -> Unit,
) {
    var selectedPeriod by remember { mutableStateOf(BillingPeriod.Monthly) }
    val readyCatalog = subscriptionCatalogState as? SubscriptionCatalogState.Ready
    val monthlyPlan = readyCatalog?.monthly
    val yearlyPlan = readyCatalog?.yearly

    LaunchedEffect(monthlyPlan, yearlyPlan) {
        val selectedAvailable = when (selectedPeriod) {
            BillingPeriod.Monthly -> monthlyPlan != null
            BillingPeriod.Yearly -> yearlyPlan != null
        }

        if (!selectedAvailable) {
            selectedPeriod = when {
                monthlyPlan != null -> BillingPeriod.Monthly
                yearlyPlan != null -> BillingPeriod.Yearly
                else -> BillingPeriod.Monthly
            }
        }
    }

    val selectedPlan = when (selectedPeriod) {
        BillingPeriod.Monthly -> monthlyPlan
        BillingPeriod.Yearly -> yearlyPlan
    }
    val purchaseEnabled =
        selectedPlan != null && billingUiState.allowsPurchaseAction()
    val billingStatusText = when (billingUiState) {
        BillingUiState.Connecting -> "Connecting to Google Play…"
        BillingUiState.Ready -> null
        BillingUiState.ProductUnavailable -> "This subscription is currently unavailable."
        is BillingUiState.PurchaseLaunching -> "Opening Google Play…"
        BillingUiState.Pending ->
            "Payment is pending. Plus will unlock only after Google Play confirms payment."
        BillingUiState.PurchasedAndVerifying ->
            "Purchase received. Verifying your Plus access…"
        BillingUiState.VerificationDeferred ->
            "Purchase received. Verification will resume when sign-in is ready."
        BillingUiState.UserCancelled ->
            "Purchase cancelled. No subscription change was made."
        BillingUiState.AlreadyOwned ->
            "Google Play says this subscription is already owned. Checking your access…"
        is BillingUiState.NetworkOrServiceUnavailable ->
            "Google Play billing is temporarily unavailable."
        BillingUiState.VerificationFailed ->
            "We couldn't verify this purchase. Try Restore purchases or try again later."
        BillingUiState.Restored,
        BillingUiState.NoPurchaseFound,
        -> null
        is BillingUiState.Error ->
            "Google Play couldn't complete the billing request."
    }
    val showBillingRetry =
        billingUiState == BillingUiState.ProductUnavailable ||
            billingUiState is BillingUiState.NetworkOrServiceUnavailable ||
            (billingUiState is BillingUiState.Error && billingUiState.retryable)

    Surface(
        color = surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = accent.copy(alpha = 0.10f),
                spotColor = accent.copy(alpha = 0.12f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(accent.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = text,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Surface(
                    color = accent.copy(alpha = 0.26f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "Impulsive Plus",
                        color = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Text(
                text = "Website Protection",
                color = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Block risky websites before they become a loop.",
                color = muted,
                style = MaterialTheme.typography.bodyLarge,
            )

            Surface(
                color = accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (subscriptionCatalogState) {
                        SubscriptionCatalogState.Loading -> {
                            Text(
                                text = "Loading current Google Play pricing…",
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        SubscriptionCatalogState.Unavailable -> {
                            Text(
                                text = "Google Play billing is temporarily unavailable.\n" +
                                    "Check your connection and try again.",
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(
                                onClick = onRetryBilling,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Retry")
                            }
                        }

                        is SubscriptionCatalogState.Ready -> {
                            if (!isPlus && monthlyPlan != null) {
                                PlanOptionRow(
                                    title = subscriptionPlanTitle(monthlyPlan),
                                    note = subscriptionPlanDisclosure(monthlyPlan),
                                    selected = selectedPeriod == BillingPeriod.Monthly,
                                    onSelect = { selectedPeriod = BillingPeriod.Monthly },
                                    accent = accent,
                                    text = text,
                                    muted = muted,
                                )
                            }

                            if (!isPlus && yearlyPlan != null) {
                                PlanOptionRow(
                                    title = subscriptionPlanTitle(yearlyPlan),
                                    note = subscriptionPlanDisclosure(yearlyPlan),
                                    selected = selectedPeriod == BillingPeriod.Yearly,
                                    onSelect = { selectedPeriod = BillingPeriod.Yearly },
                                    accent = accent,
                                    text = text,
                                    muted = muted,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isPlus) {
                            "Your Plus subscription is active."
                        } else {
                            "Subscription through Google Play."
                        },
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isPlus) {
                        Button(
                            onClick = {
                                if (purchaseEnabled) {
                                    selectedPlan?.let { plan ->
                                        onPurchase(plan.period)
                                    }
                                }
                            },
                            enabled = purchaseEnabled,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF2F2637),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Subscribe")
                        }

                        billingStatusText?.let { statusText ->
                            Text(
                                text = statusText,
                                color = muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        if (showBillingRetry) {
                            OutlinedButton(
                                onClick = onRetryBilling,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Retry billing")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanOptionRow(
    title: String,
    note: String,
    selected: Boolean,
    onSelect: () -> Unit,
    accent: Color,
    text: Color,
    muted: Color,
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.30f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent else muted.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = note,
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlusIncludedCard(
    accent: Color,
    surface: Color,
    text: Color,
    muted: Color,
) {
    Surface(
        color = surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Included",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PlusIncludedRow("Adult and risky website blocking", Icons.Filled.Lock, accent, text, muted)
            PlusIncludedRow("Local DNS-based filtering", Icons.Filled.Security, accent, text, muted)
            PlusIncludedRow("Safer browser protection", Icons.Filled.CheckCircle, accent, text, muted)
            PlusIncludedRow("Stronger anti-bypass support", Icons.Filled.CheckCircle, accent, text, muted)
            PlusIncludedRow("Designed for protected windows", Icons.Filled.CheckCircle, accent, text, muted)
        }
    }
}

@Composable
private fun PlusIncludedRow(
    label: String,
    icon: ImageVector,
    accent: Color,
    text: Color,
    muted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            color = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlusDisclosureCard(
    title: String,
    body: String,
    icon: ImageVector,
    accent: Color,
    surface: Color,
    text: Color,
    muted: Color,
) {
    Surface(
        color = surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
                )
            }
        }
    }
}
