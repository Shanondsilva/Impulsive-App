package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.theme.ImpulsivePsychological

/**
 * Holds the per-screen state for the token absorb animation.
 *  - Set [logoCenter] from the logo's Modifier.onGloballyPositioned (root-space center).
 *  - Call [launchToken] with a chip's root-space center to start a flying token.
 *  - Each completed token increments [absorbTrigger]; pass it to OnboardingLogoVisual so
 *    the logo plays one receive (scale dip + pulse ring) per arrival.
 */
class AbsorbAnimationState {
    var logoCenter by mutableStateOf<Offset?>(null)
    val inFlight = mutableStateListOf<TokenFlight>()
    var absorbTrigger by mutableStateOf(0)
        private set

    fun launchToken(start: Offset) {
        val target = logoCenter ?: return
        if (inFlight.size >= 3) return
        inFlight.add(TokenFlight(start = start, target = target))
    }

    fun onTokenArrived(flight: TokenFlight) {
        inFlight.remove(flight)
        absorbTrigger++
    }
}

data class TokenFlight(
    val id: Long = System.nanoTime(),
    val start: Offset,
    val target: Offset,
)

@Composable
fun rememberAbsorbAnimationState(): AbsorbAnimationState = remember { AbsorbAnimationState() }

/**
 * Full-screen overlay that renders any in-flight tokens above the screen content.
 * Must be placed as a sibling of (not inside) the screen's OnboardingScreenShell so it shares
 * root coordinates with the captured chip and logo positions.
 */
@Composable
fun OnboardingAbsorbOverlay(
    state: AbsorbAnimationState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        state.inFlight.toList().forEach { flight ->
            key(flight.id) {
                FlyingToken(flight = flight, onArrived = { state.onTokenArrived(flight) })
            }
        }
    }
}

@Composable
private fun FlyingToken(
    flight: TokenFlight,
    onArrived: () -> Unit,
) {
    val density = LocalDensity.current
    val tokenSizePx = with(density) { 28.dp.toPx() }

    val progress = remember(flight.id) { Animatable(0f) }

    LaunchedEffect(flight.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        )
        onArrived()
    }

    val p = progress.value
    val x = flight.start.x + (flight.target.x - flight.start.x) * p
    val y = flight.start.y + (flight.target.y - flight.start.y) * p
    val scale = 1f - (p * 0.6f)
    val alpha = if (p < 0.75f) 1f else 1f - ((p - 0.75f) / 0.25f)

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                translationX = x - tokenSizePx / 2f
                translationY = y - tokenSizePx / 2f
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .drawBehind {
                drawCircle(
                    color = ImpulsivePsychological.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.85f,
                )
            }
            .background(ImpulsivePsychological, CircleShape),
    )
}
