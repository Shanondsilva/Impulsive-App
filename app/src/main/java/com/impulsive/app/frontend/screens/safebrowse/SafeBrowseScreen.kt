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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.theme.ImpulsiveTheme

@Composable
fun SafeBrowseScreen(
    state: SafeBrowseUiState,
    onBack: () -> Unit,
    onWatchRewardedAd: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenPass: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.tertiary
    val presentation = state.toPresentation()

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
            SafeBrowseBackRow(onBack = onBack)
            SafeBrowseHeader()
            SafeBrowseAccessCard(
                state = state,
                presentation = presentation,
                accent = accent,
                secondaryAccent = secondaryAccent,
                onWatchRewardedAd = onWatchRewardedAd,
                onOpenBrowser = onOpenBrowser,
                onRetry = onRetry,
            )
            SafeBrowsePromiseCard(accent = accent)
            SafeBrowsePassCard(
                state = state,
                accent = accent,
                onOpenPass = onOpenPass,
            )
        }
    }
}

@Composable
private fun SafeBrowseBackRow(onBack: () -> Unit) {
    val text = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("safe_browse_back"),
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
private fun SafeBrowseHeader() {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Safe Browse",
            color = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("safe_browse_heading")
                .semantics { heading() },
        )
        Text(
            text = "A limited browsing space inside Impulsive for ordinary websites. " +
                "Adult content, risky navigation and unsafe browser features will remain restricted.",
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun stateIcon(accessState: SafeBrowseAccessState) = when (accessState) {
    SafeBrowseAccessState.SetupPending -> Icons.Filled.AutoAwesome
    SafeBrowseAccessState.Locked -> Icons.Filled.Lock
    is SafeBrowseAccessState.Active -> Icons.Filled.Language
    SafeBrowseAccessState.Expired -> Icons.Filled.Timer
    is SafeBrowseAccessState.Error -> Icons.Filled.Language
}

@Composable
private fun SafeBrowseAccessCard(
    state: SafeBrowseUiState,
    presentation: SafeBrowsePresentation,
    accent: Color,
    secondaryAccent: Color,
    onWatchRewardedAd: () -> Unit,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit,
) {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = presentation.stateDescription
            },
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(
                            color = secondaryAccent.copy(alpha = 0.22f),
                            shape = CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = accent.copy(alpha = 0.24f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = stateIcon(state.accessState),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Text(
                text = presentation.title,
                color = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Text(
                text = presentation.body,
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (state.accessState == SafeBrowseAccessState.SetupPending) {
                SafeBrowseSetupPendingRows(muted = muted)
            }
            if (state.accessState == SafeBrowseAccessState.Locked) {
                SafeBrowseLockedRows(muted = muted)
            }

            when (state.accessState) {
                SafeBrowseAccessState.SetupPending -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("safe_browse_setup_pending"),
                    ) {
                        Text(presentation.primaryActionLabel)
                    }
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(presentation.secondaryActionLabel ?: "Open Safe Browse")
                    }
                }

                SafeBrowseAccessState.Locked -> {
                    Button(
                        onClick = onWatchRewardedAd,
                        enabled = presentation.primaryActionEnabled,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("safe_browse_watch_ad"),
                    ) {
                        Text(presentation.primaryActionLabel)
                    }
                }

                is SafeBrowseAccessState.Active -> {
                    Button(
                        onClick = onOpenBrowser,
                        enabled = presentation.primaryActionEnabled,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("safe_browse_open_browser"),
                    ) {
                        Text(presentation.primaryActionLabel)
                    }
                }

                SafeBrowseAccessState.Expired -> {
                    Button(
                        onClick = onWatchRewardedAd,
                        enabled = presentation.primaryActionEnabled,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("safe_browse_watch_ad"),
                    ) {
                        Text(presentation.primaryActionLabel)
                    }
                }

                is SafeBrowseAccessState.Error -> {
                    Button(
                        onClick = onRetry,
                        enabled = presentation.primaryActionEnabled,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("safe_browse_retry"),
                    ) {
                        Text(presentation.primaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeBrowseSetupPendingRows(muted: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SafeBrowseInfoRow(text = "Adult websites restricted", muted = muted)
        SafeBrowseInfoRow(text = "No ads while browsing", muted = muted)
        SafeBrowseInfoRow(text = "Limited safe browsing", muted = muted)
    }
}

@Composable
private fun SafeBrowseLockedRows(muted: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SafeBrowseInfoRow(text = "Adult websites restricted", muted = muted)
        SafeBrowseInfoRow(text = "No ads while browsing", muted = muted)
        SafeBrowseInfoRow(text = "Limited safe browsing", muted = muted)
    }
}

@Composable
private fun SafeBrowseInfoRow(text: String, muted: Color) {
    Text(
        text = text,
        color = muted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SafeBrowsePromiseCard(accent: Color) {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_promise_card"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Designed for safer browsing",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SafeBrowseInfoRow(text = "Adult content restricted", muted = muted)
            SafeBrowseInfoRow(
                text = "No ads during an unlocked browsing session",
                muted = muted,
            )
            SafeBrowseInfoRow(
                text = "Downloads and unsafe browser features restricted",
                muted = muted,
            )
            Text(
                text = "These protections will become active when the secure browsing " +
                    "backend is connected.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SafeBrowsePassCard(
    state: SafeBrowseUiState,
    accent: Color,
    onOpenPass: () -> Unit,
) {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("safe_browse_pass"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Safe Browse Pass",
                color = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Use Safe Browse without watching ads.",
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            SafeBrowseInfoRow(text = "Ad-free Safe Browse access", muted = muted)
            SafeBrowseInfoRow(text = "Adult-content restrictions remain active", muted = muted)
            SafeBrowseInfoRow(text = "External-browser protection is not included", muted = muted)
            Text(
                text = state.passPriceLabel ?: "Price available when Safe Browse Pass launches",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onOpenPass,
                enabled = state.passPurchaseAvailable,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("View Safe Browse Pass")
            }
        }
    }
}

@Preview(name = "Setup pending - light", showBackground = true)
@Composable
private fun SafeBrowseSetupPendingLightPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowseScreen(
            state = SafeBrowseSetupPendingUiState,
            onBack = {},
            onWatchRewardedAd = {},
            onOpenBrowser = {},
            onOpenPass = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Locked - dark", showBackground = true)
@Composable
private fun SafeBrowseLockedDarkPreview() {
    ImpulsiveTheme(darkTheme = true) {
        SafeBrowseScreen(
            state = SafeBrowseUiState(
                accessState = SafeBrowseAccessState.Locked,
                rewardedUnlockAvailable = true,
                browserOpeningAvailable = false,
                passPurchaseAvailable = true,
                passPriceLabel = "£0.99 / 7 days",
            ),
            onBack = {},
            onWatchRewardedAd = {},
            onOpenBrowser = {},
            onOpenPass = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Active - 1h 59m", showBackground = true)
@Composable
private fun SafeBrowseActivePreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowseScreen(
            state = SafeBrowseUiState(
                accessState = SafeBrowseAccessState.Active(remainingSeconds = 7_140L),
                rewardedUnlockAvailable = false,
                browserOpeningAvailable = true,
                passPurchaseAvailable = true,
                passPriceLabel = "£0.99 / 7 days",
            ),
            onBack = {},
            onWatchRewardedAd = {},
            onOpenBrowser = {},
            onOpenPass = {},
            onRetry = {},
        )
    }
}

@Preview(name = "200% font", fontScale = 2f, showBackground = true)
@Composable
private fun SafeBrowseLargeFontPreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowseScreen(
            state = SafeBrowseSetupPendingUiState,
            onBack = {},
            onWatchRewardedAd = {},
            onOpenBrowser = {},
            onOpenPass = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Browser engine - home", showBackground = true)
@Composable
private fun SafeBrowseBrowserEnginePreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowseBrowserScreen(
            state = com.impulsive.app.backend.session.safebrowse.SafeBrowseBrowserUiState(
                policyStatus = com.impulsive.app.backend.session.safebrowse.SafeBrowsePolicyStatus.Ready,
                isHome = true,
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            evaluateNavigation = { null },
            onSearchChanged = {},
            onSearchSubmitted = {},
            onPageStarted = {},
            onPageFinished = { _, _, _, _ -> },
            onBlocked = { _, _ -> },
            onPageError = {},
            onDownloadBlocked = {},
            onPermissionBlocked = {},
            onRendererGone = {},
            onRetryPolicy = {},
            onGoBack = {},
            onGoForward = {},
            onReload = {},
            onStop = {},
            onHome = {},
            onExit = {},
        )
    }
}
