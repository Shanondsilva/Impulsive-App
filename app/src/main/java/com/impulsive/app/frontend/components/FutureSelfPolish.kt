package com.impulsive.app.frontend.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText

@Composable
fun FutureSelfHeroPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ImpulsivePsychological.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = subtitle,
                        color = ImpulsiveMutedText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun FutureSelfPlaybackRing(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val pulse by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.68f,
        animationSpec = tween(500),
        label = "future_self_pulse",
    )
    val transition = rememberInfiniteTransition(label = "future_self_ring")
    val ambient by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "future_self_ring_ambient",
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.075f
        val radius = (size.minDimension - stroke) / 2f
        drawCircle(
            color = ImpulsivePsychological.copy(alpha = ambient),
            radius = radius,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = ImpulsivePsychological,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = stroke),
        )
        val bars = 28
        repeat(bars) { index ->
            val angle = (index / bars.toFloat()) * 360f
            val barPulse = if (isPlaying) (0.55f + (kotlin.math.sin((index * 17 + progress * 200f) / 6f) * 0.18f)) else 0.42f
            val barLength = radius * (0.18f + pulse * 0.10f + barPulse * 0.10f)
            drawLine(
                color = ImpulsivePsychological.copy(alpha = 0.70f),
                start = polarPoint(size.minDimension / 2f, angle, radius - stroke * 0.90f),
                end = polarPoint(size.minDimension / 2f, angle, radius - stroke * 0.90f + barLength),
                strokeWidth = stroke * 0.16f,
            )
        }
        drawCircle(
            color = surfaceColor,
            radius = radius * 0.54f,
        )
    }
}

@Composable
fun FutureSelfChoiceChip(
    label: String,
    letter: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = if (enabled) ImpulsivePsychological.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = if (enabled) ImpulsiveText else ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else ImpulsiveMutedText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun FutureSelfQuoteCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "\"",
                color = ImpulsivePsychological,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun FutureSelfActionRow(
    onReRecord: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReRecord,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) { Text("Re-record") }
            OutlinedButton(
                onClick = onPlay,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) { Text("Play") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = ImpulsiveText,
                ),
            ) { Text("Save") }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) { Text("Delete") }
        }
    }
}

private fun polarPoint(center: Float, angleDegrees: Float, radius: Float): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble() - 90.0)
    return Offset(
        x = center + kotlin.math.cos(radians).toFloat() * radius,
        y = center + kotlin.math.sin(radians).toFloat() * radius,
    )
}
