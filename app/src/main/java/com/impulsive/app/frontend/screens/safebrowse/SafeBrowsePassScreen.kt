package com.impulsive.app.frontend.screens.safebrowse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.service.billing.SafeBrowsePassPeriod
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.theme.ImpulsiveTheme

@Composable
fun SafeBrowsePassScreen(
    state: SafeBrowsePassUiState,
    onBack: () -> Unit,
    onSelectPeriod: (SafeBrowsePassPeriod) -> Unit,
    onPurchase: () -> Unit,
    onPrepaidTopUp: () -> Unit,
    onManageSubscription: () -> Unit,
    onRetry: () -> Unit,
    onRestorePurchases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SafeBrowsePassBackRow(onBack = onBack)
            SafeBrowsePassHeader()

            when (val access = state.accessState) {
                SafeBrowsePassScreenAccessState.Loading ->
                    SafeBrowsePassLoadingCard(muted = muted)

                is SafeBrowsePassScreenAccessState.Active ->
                    SafeBrowsePassActiveCard(
                        access = access,
                        state = state,
                        accent = accent,
                        text = text,
                        muted = muted,
                        onManageSubscription = onManageSubscription,
                        onPrepaidTopUp = onPrepaidTopUp,
                        onRetry = onRetry,
                    )

                SafeBrowsePassScreenAccessState.NotActive -> {
                    SafeBrowsePassOffersCard(
                        state = state,
                        accent = accent,
                        text = text,
                        muted = muted,
                        onSelectPeriod = onSelectPeriod,
                        onPurchase = onPurchase,
                        onRetry = onRetry,
                    )

                    SafeBrowsePassRestoreButton(
                        enabled = state.restoreEnabled,
                        onRestorePurchases = onRestorePurchases,
                    )
                }

                is SafeBrowsePassScreenAccessState.Expired -> {
                    SafeBrowsePassExpiredCard(
                        access = access,
                        accent = accent,
                        text = text,
                        muted = muted,
                    )

                    SafeBrowsePassOffersCard(
                        state = state,
                        accent = accent,
                        text = text,
                        muted = muted,
                        onSelectPeriod = onSelectPeriod,
                        onPurchase = onPurchase,
                        onRetry = onRetry,
                    )

                    SafeBrowsePassRestoreButton(
                        enabled = state.restoreEnabled,
                        onRestorePurchases = onRestorePurchases,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeBrowsePassBackRow(onBack: () -> Unit) {
    val text = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("safe_browse_pass_back"),
        ) {
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
}

@Composable
private fun SafeBrowsePassHeader() {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Safe Browse Pass",
            color = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("safe_browse_pass_heading")
                .semantics { heading() },
        )
        Text(
            text = "Use Safe Browse without watching a rewarded ad first. Adult-content " +
                "and unsafe-navigation restrictions stay exactly the same.",
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SafeBrowsePassLoadingCard(muted: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_pass_loading"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Checking your Safe Browse Pass...",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SafeBrowsePassActiveCard(
    access: SafeBrowsePassScreenAccessState.Active,
    state: SafeBrowsePassUiState,
    accent: Color,
    text: Color,
    muted: Color,
    onManageSubscription: () -> Unit,
    onPrepaidTopUp: () -> Unit,
    onRetry: () -> Unit,
) {
    val formattedExpiry = formatSafeBrowsePassDateTime(access.expiryTimeMillis)
    val display = safeBrowsePassActiveDisplayText(
        access = access,
        formattedExpiry = formattedExpiry,
    )

    val showTopUpAction = access.planStatus == SafeBrowsePassActivePlanStatus.Prepaid &&
        (state.prepaidTopUpAvailable || state.prepaidTopUpInProgress || access.topUpPending)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_pass_active")
            .semantics {
                stateDescription = display.stateDescription
            },
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(color = accent.copy(alpha = 0.24f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = display.title,
                color = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = display.planLabel,
                color = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            display.timingLabel?.let { timing ->
                Text(
                    text = timing,
                    color = text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("safe_browse_pass_expiry"),
                )
            }

            Text(
                text = display.supportingText,
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            if (
                !access.topUpPending &&
                state.statusMessage != null &&
                (state.purchaseInProgress || state.showRetry)
            ) {
                SafeBrowsePassStatusMessage(
                    message = state.statusMessage,
                    muted = muted,
                )
            }

            if (state.manageSubscriptionAvailable) {
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_pass_manage"),
                ) {
                    Text("Manage subscription")
                }
            }

            if (showTopUpAction) {
                Button(
                    onClick = onPrepaidTopUp,
                    enabled = state.prepaidTopUpAvailable,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_pass_top_up"),
                ) {
                    when {
                        access.topUpPending ->
                            Text("Top-up pending")

                        state.prepaidTopUpInProgress -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )

                            Text(
                                text = "Processing top-up",
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }

                        else -> {
                            val price = state.prepaidPlan?.formattedPrice

                            Text(
                                text = if (price != null) {
                                    "Top up for $price"
                                } else {
                                    "Top up prepaid Pass"
                                },
                            )
                        }
                    }
                }
            }

            if (state.showRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_pass_retry"),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SafeBrowsePassExpiredCard(
    access: SafeBrowsePassScreenAccessState.Expired,
    accent: Color,
    text: Color,
    muted: Color,
) {
    val formattedExpiry = formatSafeBrowsePassDateTime(access.expiryTimeMillis)
    val display = safeBrowsePassExpiredDisplayText(
        access = access,
        formattedExpiry = formattedExpiry,
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_pass_expired")
            .semantics {
                stateDescription = display.stateDescription
            },
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color = accent.copy(alpha = 0.16f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp),
                )
            }

            Text(
                text = display.title,
                color = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            display.timingLabel?.let { timing ->
                Text(
                    text = timing,
                    color = text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = display.supportingText,
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SafeBrowsePassRestoreButton(
    enabled: Boolean,
    onRestorePurchases: () -> Unit,
) {
    OutlinedButton(
        onClick = onRestorePurchases,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("safe_browse_pass_restore"),
    ) {
        Text("Restore purchases")
    }
}

@Composable
private fun SafeBrowsePassStatusMessage(
    message: String,
    muted: Color,
) {
    Text(
        text = message,
        color = muted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .testTag("safe_browse_pass_status_message")
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
private fun SafeBrowsePassOffersCard(
    state: SafeBrowsePassUiState,
    accent: Color,
    text: Color,
    muted: Color,
    onSelectPeriod: (SafeBrowsePassPeriod) -> Unit,
    onPurchase: () -> Unit,
    onRetry: () -> Unit,
) {
    val selectedPlan = when (state.selectedPeriod) {
        SafeBrowsePassPeriod.Monthly -> state.monthlyPlan
        SafeBrowsePassPeriod.Prepaid -> state.prepaidPlan
        null -> null
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_pass_offers"),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(36.dp),
            )

            if (state.catalogUnavailable) {
                Text(
                    text = "Safe Browse Pass pricing could not be loaded.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_pass_retry"),
                ) {
                    Text("Try again")
                }
                return@Column
            }

            if (state.monthlyPlan == null && state.prepaidPlan == null) {
                Text(
                    text = "Loading Safe Browse Pass options...",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.monthlyPlan?.let { plan ->
                    SafeBrowsePassPlanOptionRow(
                        plan = plan,
                        selected = state.selectedPeriod == SafeBrowsePassPeriod.Monthly,
                        onSelect = { onSelectPeriod(plan.period) },
                        text = text,
                        muted = muted,
                        accent = accent,
                    )
                }
                state.prepaidPlan?.let { plan ->
                    SafeBrowsePassPlanOptionRow(
                        plan = plan,
                        selected = state.selectedPeriod == SafeBrowsePassPeriod.Prepaid,
                        onSelect = { onSelectPeriod(plan.period) },
                        text = text,
                        muted = muted,
                        accent = accent,
                    )
                }
            }

            state.statusMessage?.let { message ->
                SafeBrowsePassStatusMessage(
                    message = message,
                    muted = muted,
                )
            }

            Button(
                onClick = onPurchase,
                enabled = state.purchaseEnabled && selectedPlan != null,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("safe_browse_pass_purchase"),
            ) {
                if (state.purchaseInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(selectedPlan?.let { "Get Safe Browse Pass" } ?: "Select a plan")
                }
            }

            if (state.showRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_pass_retry"),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SafeBrowsePassPlanOptionRow(
    plan: SafeBrowsePassPlanUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    text: Color,
    muted: Color,
    accent: Color,
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .testTag("safe_browse_pass_plan_${plan.period.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = plan.period.label(),
                    color = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = plan.formattedPrice,
                    color = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = plan.disclosure,
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = plan.periodLabel,
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val PreviewFixedExpiryMillis = 1_893_456_000_000L

@Preview(name = "Loading", showBackground = true)
@Composable
private fun SafeBrowsePassLoadingPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState(),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Active", showBackground = true)
@Composable
private fun SafeBrowsePassActivePreview() {
    ImpulsiveTheme(darkTheme = true) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Active(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        planStatus =
                            SafeBrowsePassActivePlanStatus
                                .AutoRenewing,
                        topUpPending = false,
                    ),
                manageSubscriptionAvailable = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Active prepaid, top-up available", showBackground = true)
@Composable
private fun SafeBrowsePassActivePrepaidTopUpAvailablePreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Active(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        planStatus =
                            SafeBrowsePassActivePlanStatus.Prepaid,
                        topUpPending = false,
                    ),
                prepaidPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Prepaid,
                    formattedPrice = "£3.49",
                    periodLabel = "30 days",
                    disclosure = "Prepaid access. Top up again when you choose.",
                ),
                prepaidTopUpAvailable = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Active prepaid, top-up pending", showBackground = true)
@Composable
private fun SafeBrowsePassActivePrepaidTopUpPendingPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Active(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        planStatus =
                            SafeBrowsePassActivePlanStatus.Prepaid,
                        topUpPending = true,
                    ),
                prepaidPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Prepaid,
                    formattedPrice = "£3.49",
                    periodLabel = "30 days",
                    disclosure = "Prepaid access. Top up again when you choose.",
                ),
                prepaidTopUpAvailable = false,
                prepaidTopUpInProgress = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Active cancelled until expiry (dark)", showBackground = true)
@Composable
private fun SafeBrowsePassActiveCancelledUntilExpiryDarkPreview() {
    ImpulsiveTheme(darkTheme = true) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Active(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        planStatus =
                            SafeBrowsePassActivePlanStatus
                                .CancelledUntilExpiry,
                        topUpPending = false,
                    ),
                manageSubscriptionAvailable = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Offers", showBackground = true)
@Composable
private fun SafeBrowsePassOffersPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState = SafeBrowsePassScreenAccessState.NotActive,
                monthlyPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Monthly,
                    formattedPrice = "£1.99",
                    periodLabel = "1 month",
                    disclosure = "Auto-renews until cancelled.",
                ),
                prepaidPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Prepaid,
                    formattedPrice = "£3.49",
                    periodLabel = "30 days",
                    disclosure = "Prepaid access. Top up again when you choose.",
                ),
                selectedPeriod = SafeBrowsePassPeriod.Monthly,
                purchaseEnabled = true,
                restoreEnabled = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(name = "Expired with offers", showBackground = true)
@Composable
private fun SafeBrowsePassExpiredPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Expired(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        wasPrepaid = false,
                    ),
                monthlyPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Monthly,
                    formattedPrice = "£1.99",
                    periodLabel = "1 month",
                    disclosure = "Auto-renews until cancelled.",
                ),
                prepaidPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Prepaid,
                    formattedPrice = "£3.49",
                    periodLabel = "30 days",
                    disclosure = "Prepaid access. Top up again when you choose.",
                ),
                selectedPeriod = SafeBrowsePassPeriod.Monthly,
                purchaseEnabled = true,
                restoreEnabled = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}

@Preview(
    name = "Expired 200 percent text",
    showBackground = true,
    fontScale = 2f,
    heightDp = 1200,
)
@Composable
private fun SafeBrowsePassExpiredLargeTextPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowsePassScreen(
            state = defaultSafeBrowsePassUiState().copy(
                accessState =
                    SafeBrowsePassScreenAccessState.Expired(
                        expiryTimeMillis = PreviewFixedExpiryMillis,
                        wasPrepaid = true,
                    ),
                monthlyPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Monthly,
                    formattedPrice = "£1.99",
                    periodLabel = "1 month",
                    disclosure = "Auto-renews until cancelled.",
                ),
                prepaidPlan = SafeBrowsePassPlanUiModel(
                    period = SafeBrowsePassPeriod.Prepaid,
                    formattedPrice = "£3.49",
                    periodLabel = "30 days",
                    disclosure = "Prepaid access. Top up again when you choose.",
                ),
                selectedPeriod = SafeBrowsePassPeriod.Prepaid,
                purchaseEnabled = true,
                restoreEnabled = true,
            ),
            onBack = {},
            onSelectPeriod = {},
            onPurchase = {},
            onPrepaidTopUp = {},
            onManageSubscription = {},
            onRetry = {},
            onRestorePurchases = {},
        )
    }
}
