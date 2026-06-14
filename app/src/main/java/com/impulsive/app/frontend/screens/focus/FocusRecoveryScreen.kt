package com.impulsive.app.frontend.screens.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.session.focus.FocusSessionViewModel
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveFocusModeDark
import kotlinx.coroutines.delay

@Composable
fun FocusRecoveryScreen(
    focusViewModel: FocusSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onReturnToFocus: () -> Unit = {},
    onEndedCalmly: () -> Unit = {},
) {
    val session by focusViewModel.session.collectAsStateWithLifecycle()
    val now by focusViewModel.now.collectAsStateWithLifecycle()
    val liveSession = session?.takeIf { it.isLive }

    LaunchedEffect(liveSession == null) {
        if (liveSession == null) onEndedCalmly()
    }
    if (liveSession == null) return

    BackHandler { onReturnToFocus() }

    var isBreathing by remember { mutableStateOf(false) }
    var breathSeconds by remember { mutableIntStateOf(20) }
    LaunchedEffect(isBreathing) {
        if (!isBreathing) return@LaunchedEffect
        breathSeconds = 20
        while (breathSeconds > 0) {
            delay(1_000)
            breathSeconds -= 1
        }
        onReturnToFocus()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isDark) ImpulsiveFocusModeDark else ImpulsiveFocusMode
    val background = if (isDark) Color(0xFF090D13) else Color(0xFFFFFAF7)
    val text = MaterialTheme.colorScheme.onBackground
    val muted = if (isDark) Color(0xFFCFC4DD) else MaterialTheme.colorScheme.onSurfaceVariant
    val neutralCard = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF)
    val borderColor = if (isDark) accent.copy(alpha = 0.28f) else Color(0xFFF0D8D8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    radius = 760f,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 22.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "You got pulled away",
                color = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "That happens. Your session is still here.",
                color = muted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(34.dp))

            if (isBreathing) {
                BreathResetContent(
                    remainingSeconds = breathSeconds,
                    accent = accent,
                    text = text,
                    muted = muted,
                )
            } else {
                RecoveryChoices(
                    nowText = liveSession.formattedRemaining(now),
                    accent = accent,
                    text = text,
                    muted = muted,
                    neutralCard = neutralCard,
                    borderColor = borderColor,
                    onReturnToFocus = onReturnToFocus,
                    onStartBreath = { isBreathing = true },
                    onEndCalmly = {
                        focusViewModel.endEarly()
                        onEndedCalmly()
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "No points lost for being human",
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecoveryChoices(
    nowText: String,
    accent: Color,
    text: Color,
    muted: Color,
    neutralCard: Color,
    borderColor: Color,
    onReturnToFocus: () -> Unit,
    onStartBreath: () -> Unit,
    onEndCalmly: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FocusRecoveryChoice(
            title = "Return to focus",
            subtitle = "$nowText left",
            containerColor = accent,
            contentColor = Color(0xFF281D38),
            borderColor = Color.Transparent,
            onClick = onReturnToFocus,
        )
        FocusRecoveryChoice(
            title = "Take one breath first",
            subtitle = "A 20 second reset, then back in",
            containerColor = neutralCard,
            contentColor = text,
            subtitleColor = muted,
            borderColor = borderColor,
            onClick = onStartBreath,
        )
        FocusRecoveryChoice(
            title = "End session calmly",
            subtitle = "Keep the progress you made",
            containerColor = Color.Transparent,
            contentColor = muted,
            subtitleColor = muted.copy(alpha = 0.82f),
            borderColor = Color.Transparent,
            onClick = onEndCalmly,
        )
    }
}

@Composable
private fun BreathResetContent(
    remainingSeconds: Int,
    accent: Color,
    text: Color,
    muted: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = remainingSeconds.toString(),
            color = accent,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Breathe in. Breathe out.",
            color = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your focus timer keeps going.",
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FocusRecoveryChoice(
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    subtitleColor: Color = contentColor.copy(alpha = 0.74f),
) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        color = containerColor,
        shape = shape,
        border = if (borderColor == Color.Transparent) null else BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
