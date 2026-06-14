package com.impulsive.app.frontend.screens.focus

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.impulsive.app.backend.domain.model.focus.elapsedFocusSeconds
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.session.focus.FocusSessionViewModel
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveFocusModeDark
import kotlinx.coroutines.delay

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
                // The session just finished (Completed or EndedEarly). Leave
                // immediately so the Focus tab can show the completion card.
                onExit()
            } else {
                // The session is written to DataStore just before navigation,
                // so the first frame can race the emission. Wait briefly
                // before concluding there is genuinely no session to show.
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
    val muted = if (isDark) Color(0xFFCFC4DD) else MaterialTheme.colorScheme.onSurfaceVariant
    val breathing = liveSession.phase == FocusSessionPhase.Running
    val transition = rememberInfiniteTransition(label = "FocusOrbBreath")
    val orbScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (breathing) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "FocusOrbScale",
    )

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    // The start animation plays once per session, only within the first few
    // seconds of focus time, so resumes and later re-entries skip straight to
    // the orb. Tap to skip. Reduced motion never shows it.
    var introFinished by rememberSaveable(liveSession.sessionId) { mutableStateOf(false) }
    val showIntro = !reducedMotion &&
        !introFinished &&
        liveSession.phase == FocusSessionPhase.Running &&
        liveSession.elapsedFocusSeconds(now) < 10L
    val introComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.focus_session_start),
    )
    val introProgress by animateLottieCompositionAsState(
        composition = introComposition,
        iterations = 1,
        isPlaying = showIntro,
    )
    LaunchedEffect(introProgress) {
        if (introProgress >= 1f) introFinished = true
    }
    val sessionContentAlpha by animateFloatAsState(
        targetValue = if (showIntro) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "FocusIntroCrossfade",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    radius = 720f,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Focus is on. Your apps are resting.",
            color = muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.34f),
                            accent.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = sessionContentAlpha },
            ) {
                Text(
                    text = liveSession.formattedRemaining(now),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "of ${liveSession.durationMinutes} min",
                    color = muted,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (showIntro && introComposition != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(300.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { introFinished = true },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val actionButtonShape = RoundedCornerShape(50)
            Surface(
                color = Color.Transparent,
                shape = actionButtonShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.72f)),
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
                    text = if (liveSession.phase == FocusSessionPhase.Running) "Pause" else "Resume",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            TextButton(onClick = { focusViewModel.endEarly() }) {
                Text("End session", color = muted)
            }
        }
    }
}
