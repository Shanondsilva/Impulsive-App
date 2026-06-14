package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PersonalisingToken(
    val label: String,
    val startXFraction: Float,
    val startYFraction: Float,
)

private val InputTokens = listOf(
    PersonalisingToken("Triggers", 0.04f, 0.62f),
    PersonalisingToken("Timing", 0.18f, 0.78f),
    PersonalisingToken("Goal", 0.46f, 0.84f),
    PersonalisingToken("Support", 0.10f, 0.46f),
    PersonalisingToken("Week one", 0.34f, 0.70f),
)

private val OutputTokens = listOf(
    PersonalisingToken("Mind Mode", 0.96f, 0.16f),
    PersonalisingToken("Release plan", 0.96f, 0.26f),
    PersonalisingToken("First task", 0.96f, 0.36f),
)

private const val LogoCenterXFraction = 0.5f
private const val LogoCenterYFraction = 0.20f

@Composable
fun PersonalisingSetupScreen(
    onFinished: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    var absorbTrigger by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val inputProgress = remember { InputTokens.map { Animatable(0f) } }
    val outputProgress = remember { OutputTokens.map { Animatable(0f) } }
    val contentAppear = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAppear.animateTo(1f, tween(durationMillis = 400, easing = FastOutSlowInEasing))
        if (reducedMotion) {
            delay(1100)
        } else {
            delay(150)
            InputTokens.indices.forEach { index ->
                launch {
                    delay(index * 240L)
                    inputProgress[index].animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    )
                    absorbTrigger += 1
                }
            }
            delay((InputTokens.size - 1) * 240L + 700L + 250L)
            OutputTokens.indices.forEach { index ->
                launch {
                    delay(index * 160L)
                    outputProgress[index].animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    )
                }
            }
            delay((OutputTokens.size - 1) * 160L + 650L + 150L)
        }
        exitProgress.animateTo(1f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer {
                alpha = (1f - exitProgress.value).coerceIn(0f, 1f)
                val push = 1f + (exitProgress.value * 0.04f)
                scaleX = push
                scaleY = push
            },
    ) {
        val logoX = maxWidth * LogoCenterXFraction
        val logoY = maxHeight * LogoCenterYFraction

        OnboardingLogoVisual(
            reducedMotion = reducedMotion,
            scale = OnboardingLogoScale.Large,
            absorbTrigger = absorbTrigger,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = logoY - (OnboardingLogoScale.Large.containerHeight / 2))
                .graphicsLayer { alpha = contentAppear.value },
        )

        if (!reducedMotion) {
            InputTokens.forEachIndexed { index, token ->
                val progress = inputProgress[index].value
                if (progress > 0f && progress < 1f) {
                    val startX = maxWidth * token.startXFraction
                    val startY = maxHeight * token.startYFraction
                    val x = startX + (logoX - startX) * progress
                    val y = startY + (logoY - startY) * progress
                    val alpha = if (progress < 0.82f) 1f else ((1f - progress) / 0.18f)
                    PersonalisingTokenPill(
                        label = token.label,
                        x = x,
                        y = y,
                        alpha = alpha.coerceIn(0f, 1f),
                    )
                }
            }
            OutputTokens.forEachIndexed { index, token ->
                val progress = outputProgress[index].value
                if (progress > 0f && progress < 1f) {
                    val endX = maxWidth * token.startXFraction
                    val endY = maxHeight * token.startYFraction
                    val x = logoX + (endX - logoX) * progress
                    val y = logoY + (endY - logoY) * progress
                    val alpha = when {
                        progress < 0.15f -> progress / 0.15f
                        progress > 0.80f -> (1f - progress) / 0.20f
                        else -> 1f
                    }
                    PersonalisingTokenPill(
                        label = token.label,
                        x = x,
                        y = y,
                        alpha = alpha.coerceIn(0f, 1f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = 72.dp)
                .graphicsLayer { alpha = contentAppear.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Personalising Impulsive",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Building your first Mind Mode plan from what you shared.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PersonalisingTokenPill(
    label: String,
    x: Dp,
    y: Dp,
    alpha: Float,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .offset(x = x - 36.dp, y = y - 14.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
