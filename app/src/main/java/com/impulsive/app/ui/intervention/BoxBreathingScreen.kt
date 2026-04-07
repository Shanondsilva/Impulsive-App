package com.impulsive.app.ui.intervention

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class BreathPhase(val label: String, val durationMs: Long) {
    INHALE("INHALE", 4000),
    HOLD_IN("HOLD", 4000),
    EXHALE("EXHALE", 4000),
    HOLD_OUT("HOLD", 4000)
}

private val phases = BreathPhase.entries
private const val TOTAL_CYCLES = 4

@Composable
fun BoxBreathingScreen(onComplete: () -> Unit) {
    var cycle by remember { mutableIntStateOf(1) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableStateOf(4f) }
    var expanded by remember { mutableStateOf(false) }

    val currentPhase = phases[phaseIndex]

    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.65f,
        animationSpec = tween(durationMillis = currentPhase.durationMs.toInt()),
        label = "box_scale"
    )

    LaunchedEffect(Unit) {
        while (cycle <= TOTAL_CYCLES) {
            for (i in phases.indices) {
                phaseIndex = i
                val phase = phases[i]
                expanded = phase == BreathPhase.INHALE || phase == BreathPhase.HOLD_IN
                val ticks = (phase.durationMs / 100).toInt()
                repeat(ticks) { tick ->
                    secondsLeft = ((phase.durationMs - tick * 100) / 1000f)
                    delay(100)
                }
            }
            if (cycle < TOTAL_CYCLES) cycle++ else break
        }
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "INTERVENTION MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Box Breathing",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Animated square
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentPhase.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "%.1f".format(secondsLeft.coerceAtLeast(0f)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Cycle $cycle of $TOTAL_CYCLES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Follow the square. Breathe slowly and deeply through your nose. Let the impulse pass.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onComplete) {
                Text(
                    text = "End Session Early",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
