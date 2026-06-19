package com.impulsive.app.frontend.screens.focus

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.domain.model.focus.remainingSeconds
import com.impulsive.app.backend.session.focus.FocusSessionViewModel
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveFocusModeDark
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun ActiveFocusSessionScreen(
    focusViewModel: FocusSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onExit: () -> Unit = {},
) {
    val session by focusViewModel.session.collectAsStateWithLifecycle()
    val now by focusViewModel.now.collectAsStateWithLifecycle()
    val liveSession = session?.takeIf { it.isLive }

    LaunchedEffect(liveSession == null) {
        if (liveSession == null) {
            if (session != null) {
                onExit()
            } else {
                delay(600)
                if (focusViewModel.session.value?.isLive != true) onExit()
            }
        }
    }

    if (liveSession == null) return

    BackHandler { onExit() }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isDark) ImpulsiveFocusModeDark else ImpulsiveFocusMode
    val background = if (isDark) Color(0xFF090D13) else Color(0xFFFFFAF7)
    val text = MaterialTheme.colorScheme.onBackground
    val muted = if (isDark) Color(0xFFCFC4DD) else MaterialTheme.colorScheme.onSurfaceVariant
    val remainingSeconds = liveSession.remainingSeconds(now)

    LaunchedEffect(liveSession.sessionId, liveSession.phase, remainingSeconds) {
        if (
            liveSession.phase == FocusSessionPhase.Running &&
            remainingSeconds == 0L
        ) {
            focusViewModel.completeElapsedSessionIfNeeded(now)
        }
    }

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    var introFinished by rememberSaveable(liveSession.sessionId) { mutableStateOf(false) }
    val showIntro = !reducedMotion &&
        !introFinished &&
        liveSession.phase == FocusSessionPhase.Running

    val introComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.focus_session_start),
    )
    val introProgress by animateLottieCompositionAsState(
        composition = introComposition,
        iterations = 1,
        isPlaying = showIntro,
    )

    LaunchedEffect(showIntro, introProgress) {
        if (showIntro && introProgress >= 0.92f) {
            introFinished = true
        }
    }

    LaunchedEffect(showIntro) {
        if (showIntro) {
            delay(2600)
            introFinished = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    radius = 720f,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val compactHeight = maxHeight < 720.dp
        val veryCompactHeight = maxHeight < 620.dp
        val compactWidth = maxWidth < 380.dp
        val activeHorizontalPadding = if (compactWidth) 20.dp else 28.dp
        val activeVerticalPadding = if (compactHeight) 16.dp else 24.dp
        val activeClockSizeDp = when {
            maxHeight < 620.dp || maxWidth < 360.dp -> 184
            maxHeight < 720.dp -> 204
            else -> 232
        }
        val activeIntroSizeDp = when {
            maxHeight < 620.dp || maxWidth < 360.dp -> 214
            maxHeight < 720.dp -> 238
            else -> 270
        }
        val topTextSpacing = if (compactHeight) 6.dp else 8.dp
        val bottomControlSpacing = if (compactHeight) 8.dp else 10.dp
        val actionButtonVerticalPadding = if (veryCompactHeight) 13.dp else 16.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = activeHorizontalPadding,
                    vertical = activeVerticalPadding,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(topTextSpacing),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "Let the phone rest while you focus.",
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Impulsive is guarding this session quietly.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            ActiveCleanFocusClock(
                currentTime = now,
                remainingText = liveSession.formattedRemaining(now),
                remainingSeconds = remainingSeconds,
                durationMinutes = liveSession.durationMinutes,
                isPaused = liveSession.phase == FocusSessionPhase.Paused,
                accent = accent,
                text = text,
                muted = muted,
                clockSizeDp = activeClockSizeDp,
                modifier = Modifier.align(Alignment.Center),
            )

            AnimatedVisibility(
                visible = showIntro && introComposition != null,
                enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 420)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    modifier = Modifier
                        .size(activeIntroSizeDp.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            introFinished = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LottieAnimation(
                        composition = introComposition,
                        progress = { introProgress },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(bottomControlSpacing),
            ) {
                val actionButtonShape = RoundedCornerShape(50)

                Surface(
                    color = Color.Transparent,
                    shape = actionButtonShape,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.72f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(actionButtonShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (liveSession.phase == FocusSessionPhase.Running) {
                                focusViewModel.pause()
                            } else {
                                focusViewModel.resume()
                            }
                        },
                ) {
                    Text(
                        text = if (liveSession.phase == FocusSessionPhase.Running) {
                            "Pause focus"
                        } else {
                            "Resume focus"
                        },
                        color = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = actionButtonVerticalPadding),
                    )
                }

                TextButton(onClick = { focusViewModel.endEarly() }) {
                    Text("End session", color = muted)
                }
            }
        }
    }
}

@Composable
private fun ActiveCleanFocusClock(
    currentTime: LocalDateTime,
    remainingText: String,
    remainingSeconds: Long,
    durationMinutes: Int,
    isPaused: Boolean,
    accent: Color,
    text: Color,
    muted: Color,
    clockSizeDp: Int = 232,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CleanFocusLiveClockFace(
            currentTime = currentTime,
            selectedDurationMinutes = durationMinutes,
            activeRemainingSeconds = remainingSeconds,
            accent = accent,
            muted = muted,
            sizeDp = clockSizeDp,
            handColor = text,
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = remainingText,
            color = text,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "of ${formatCleanFocusDuration(durationMinutes)}",
            color = muted,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        
    }
}
