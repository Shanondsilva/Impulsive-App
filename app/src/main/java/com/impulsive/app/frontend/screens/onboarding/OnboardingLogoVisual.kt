package com.impulsive.app.frontend.screens.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.impulsive.app.R
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import kotlinx.coroutines.launch

internal enum class OnboardingLogoScale(
    val containerWidth: Dp,
    val containerHeight: Dp,
    val logoSize: Dp,
) {
    Large(
        containerWidth = 156.dp,
        containerHeight = 122.dp,
        logoSize = 82.dp,
    ),
    Compact(
        containerWidth = 86.dp,
        containerHeight = 72.dp,
        logoSize = 46.dp,
    ),
}

@Composable
internal fun OnboardingLogoVisual(
    reducedMotion: Boolean,
    scale: OnboardingLogoScale,
    modifier: Modifier = Modifier,
    absorbTrigger: Int = 0,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding-logo-visual")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
        ),
        label = "onboarding-logo-rotation",
    )

    val receiveScale = remember { Animatable(1f) }
    val ringProgress = remember { Animatable(0f) }

    LaunchedEffect(absorbTrigger) {
        if (absorbTrigger == 0 || reducedMotion) return@LaunchedEffect
        launch {
            receiveScale.animateTo(0.94f, tween(100, easing = FastOutSlowInEasing))
            receiveScale.animateTo(1.06f, tween(100, easing = FastOutSlowInEasing))
            receiveScale.animateTo(1.0f, tween(120, easing = FastOutSlowInEasing))
        }
        ringProgress.snapTo(0f)
        ringProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .size(width = scale.containerWidth, height = scale.containerHeight)
            .wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        val rp = ringProgress.value
        if (rp > 0f && rp < 1f) {
            Box(
                modifier = Modifier
                    .size(scale.logoSize)
                    .drawBehind {
                        val radius = size.minDimension * 0.5f * (1f + rp * 0.8f)
                        val alpha = 0.4f * (1f - rp)
                        drawCircle(
                            color = ImpulsiveOverallTheme.copy(alpha = alpha),
                            radius = radius,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    },
            )
        }

        Image(
            painter = painterResource(id = R.drawable.impulsive_logo),
            contentDescription = "Impulsive",
            modifier = Modifier
                .size(scale.logoSize)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = receiveScale.value
                    scaleY = receiveScale.value
                },
        )
    }
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
