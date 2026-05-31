package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics

@Composable
fun ProtectionSetupOnboardingScreen(
    state: ProtectionSetupState,
    onBack: () -> Unit,
    onChooseApps: () -> Unit,
    onOpenUninstallProtection: () -> Unit,
    onSkipItem: (ProtectionSetupItem) -> Unit,
    onContinue: () -> Unit,
) {
    val missingCoreCount = state.incompleteCoreProtectionItems.size
    val continueLabel = if (missingCoreCount == 0) "Continue" else "Continue and finish later"

    OnboardingScreenShell(
        backgroundColors = listOf(
            Color(0xFFFFFEFC),
            Color(0xFFFCF8FD),
            Color(0xFFF6F2FA),
        ),
        stepUi = OnboardingFlowStep.ProtectionSetup.toStepUi(),
        onBack = onBack,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProtectionPrimaryButton(
                    label = continueLabel,
                    enabled = true,
                    onClick = onContinue,
                )
                if (missingCoreCount > 0) {
                    Text(
                        text = "You can finish protection setup from your profile later.",
                        color = ProtectionMutedText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { compactHeight ->
        Spacer(modifier = Modifier.height(if (compactHeight) 20.dp else 34.dp))

        ProtectionIntroMark()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set up your protection loop",
            color = ProtectionPrimaryText,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Impulsive works best when it can step in before the loop continues. Start with the basics now. Anything skipped stays visible later.",
            color = ProtectionMutedText,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.94f),
        )

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProtectionSetupCard(
                badge = "1",
                title = "Choose apps to protect",
                body = "Pick apps that usually lead into the trigger loop. You can change the list later.",
                completed = state.isComplete(ProtectionSetupItem.BlockedApps),
                skipped = ProtectionSetupItem.BlockedApps in state.skippedSetupItems,
                actionLabel = if (state.blockedAppsSelected) "Edit apps" else "Choose apps",
                onAction = onChooseApps,
                onSkip = { onSkipItem(ProtectionSetupItem.BlockedApps) },
            )
            ProtectionSetupCard(
                badge = "2",
                title = "Enable app detection",
                body = "This phone setting lets Impulsive notice protected apps. You can finish it from your profile after onboarding.",
                completed = state.isComplete(ProtectionSetupItem.UsageAccess),
                skipped = ProtectionSetupItem.UsageAccess in state.skippedSetupItems,
                actionLabel = null,
                onAction = null,
                onSkip = { onSkipItem(ProtectionSetupItem.UsageAccess) },
            )
            ProtectionSetupCard(
                badge = "3",
                title = "Keep notifications active",
                body = "Notifications tell you when a planned window opens and when protection turns back on.",
                completed = state.isComplete(ProtectionSetupItem.Notifications),
                skipped = ProtectionSetupItem.Notifications in state.skippedSetupItems,
                actionLabel = null,
                onAction = null,
                onSkip = { onSkipItem(ProtectionSetupItem.Notifications) },
            )
            ProtectionSetupCard(
                badge = "4",
                title = "Add uninstall friction",
                body = "This adds friction before removing Impulsive during weak moments, while keeping you in control.",
                completed = state.isComplete(ProtectionSetupItem.UninstallProtection),
                skipped = ProtectionSetupItem.UninstallProtection in state.skippedSetupItems,
                actionLabel = "Set up",
                onAction = onOpenUninstallProtection,
                onSkip = { onSkipItem(ProtectionSetupItem.UninstallProtection) },
            )
        }
    }
}

@Composable
private fun ProtectionIntroMark() {
    Box(
        modifier = Modifier
            .size(82.dp)
            .background(ImpulsivePsychological.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "I",
            color = ProtectionAccentText,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ProtectionSetupCard(
    badge: String,
    title: String,
    body: String,
    completed: Boolean,
    skipped: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onSkip: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            completed -> ProtectionComplete.copy(alpha = 0.62f)
            skipped -> ProtectionSkipped.copy(alpha = 0.50f)
            else -> ProtectionOutline
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "protection-card-border",
    )
    val statusText = when {
        completed -> "Active"
        skipped -> "Skipped"
        else -> "Needed"
    }
    val statusColor = when {
        completed -> ProtectionComplete
        skipped -> ProtectionSkipped
        else -> ProtectionMutedText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImpulsiveSurface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.2.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(ImpulsivePsychological.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    color = ProtectionAccentText,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        color = ProtectionPrimaryText,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    color = ProtectionMutedText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (!completed) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionLabel != null && onAction != null) {
                    ProtectionSmallButton(
                        label = actionLabel,
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Do this later",
                        color = ProtectionAccentText,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.10f else 0.045f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "protection-primary-button-shadow",
    )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 7.dp)
                .background(Color(0xFF514866).copy(alpha = shadowAlpha), RoundedCornerShape(28.dp)),
        )
        Button(
            onClick = {
                haptics.confirm()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = ProtectionAccentText,
                disabledContainerColor = Color(0xFFE8DDFF),
                disabledContentColor = Color(0xFF9C93A8),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProtectionSmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    androidx.compose.material3.OutlinedButton(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, ImpulsivePsychological),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ProtectionAccentText,
        ),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal val ProtectionPrimaryText = Color(0xFF1C1B1E)
internal val ProtectionMutedText = Color(0xFF5F5868)
internal val ProtectionAccentText = Color(0xFF635880)
internal val ProtectionOutline = Color(0xFFE1D9EC)
internal val ProtectionComplete = Color(0xFF47A36F)
internal val ProtectionSkipped = Color(0xFFE6A15A)
