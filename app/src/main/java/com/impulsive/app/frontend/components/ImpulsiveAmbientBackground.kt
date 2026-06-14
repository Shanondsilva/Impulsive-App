package com.impulsive.app.frontend.components

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ImpulsiveAmbientBackground(
    modifier: Modifier = Modifier,
    lightweight: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (isDark) {
        AmbientParticlesLayer(modifier, lightweight = lightweight)
    } else {
        AmbientBlobsLayer(modifier)
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

private class AmbientBlobSpec(
    val color: Color,
    val relX: Float,
    val relY: Float,
    val relRadius: Float,
    val durationSeconds: Float,
    val phase: Float,
)

@Composable
private fun AmbientBlobsLayer(modifier: Modifier) {
    val reduceMotion = rememberReduceMotion()
    val blobs = remember {
        listOf(
            AmbientBlobSpec(Color(0xFFD0C3F1), -0.15f, -0.05f, 0.85f, 28f, 0f),
            AmbientBlobSpec(Color(0xFFBDE0FE), 1.05f, 0.10f, 0.95f, 34f, 1.2f),
            AmbientBlobSpec(Color(0xFFFEF1AB), -0.05f, 0.45f, 0.70f, 22f, 2.1f),
            AmbientBlobSpec(Color(0xFFF5A7A6), 1.10f, 0.55f, 0.80f, 30f, 3.0f),
            AmbientBlobSpec(Color(0xFF93E9BE), -0.05f, 0.85f, 0.72f, 26f, 4.0f),
            AmbientBlobSpec(Color(0xFFD0C3F1), 1.10f, 1.05f, 0.90f, 32f, 5.0f),
        )
    }
    var frameTime by remember { mutableStateOf(0L) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            while (true) {
                withInfiniteAnimationFrameMillis { frameTime = it }
            }
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minDim = if (w < h) w else h
        val seconds = frameTime / 1000f
        blobs.forEach { blob ->
            val angle = seconds / blob.durationSeconds * 2f * PI.toFloat() + blob.phase
            val dx = sin(angle) * minDim * 0.06f
            val dy = sin(angle * 0.8f + blob.phase) * minDim * 0.05f
            val cx = blob.relX * w + dx
            val cy = blob.relY * h + dy
            val radius = blob.relRadius * minDim
            if (radius > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blob.color.copy(alpha = 0.40f),
                            blob.color.copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

private class AmbientParticle(
    var x: Float,
    var y: Float,
    var baseX: Float,
    val size: Float,
    val speed: Float,
    val period: Float,
    val phase: Float,
    val pulseDuration: Float,
    val color: Color,
    var sparkStart: Float,
    var sparkUntil: Float,
)

private val particlePalette = listOf(
    Color(0xFF8B78DD),
    Color(0xFF8B78DD),
    Color(0xFF8B78DD),
    Color(0xFF8B78DD),
    Color(0xFF638CDD),
    Color(0xFF638CDD),
    Color(0xFF638CDD),
    Color(0xFFDDC878),
    Color(0xFFDDC878),
    Color(0xFFFFFFFF),
)

private fun spawnParticle(width: Float, height: Float, y: Float, lightweight: Boolean = false): AmbientParticle {
    val x = Random.nextFloat() * width
    return AmbientParticle(
        x = x,
        y = y,
        baseX = x,
        size = if (lightweight) 0.8f + Random.nextFloat() * 1.0f else 1f + Random.nextFloat() * 1.5f,
        speed = if (lightweight) 4f + Random.nextFloat() * 6f else 8f + Random.nextFloat() * 12f,
        period = if (lightweight) 9000f + Random.nextFloat() * 5000f else 6000f + Random.nextFloat() * 4000f,
        phase = Random.nextFloat() * PI.toFloat() * 2f,
        pulseDuration = if (lightweight) 7000f + Random.nextFloat() * 5000f else 4000f + Random.nextFloat() * 4000f,
        color = particlePalette[Random.nextInt(particlePalette.size)],
        sparkStart = 0f,
        sparkUntil = 0f,
    )
}

@Composable
private fun AmbientParticlesLayer(modifier: Modifier, lightweight: Boolean = false) {
    val reduceMotion = rememberReduceMotion()
    val dp = LocalDensity.current.density
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        val particles = remember(wPx, hPx, lightweight) {
            if (wPx <= 0f || hPx <= 0f) {
                emptyList()
            } else {
                List(if (lightweight) 14 else 26) {
                    spawnParticle(wPx, hPx, Random.nextFloat() * hPx, lightweight = lightweight)
                }
            }
        }
        var frameTime by remember { mutableStateOf(0L) }
        LaunchedEffect(particles, reduceMotion) {
            if (particles.isEmpty()) return@LaunchedEffect
            if (reduceMotion) {
                frameTime = 1L
                return@LaunchedEffect
            }
            var lastTime = 0f
            var nextSparkAt = 0f
            while (true) {
                withInfiniteAnimationFrameMillis { ms ->
                    val now = ms.toFloat()
                    if (lastTime == 0f) lastTime = now
                    val delta = ((now - lastTime) / 1000f).coerceAtMost(0.05f)
                    lastTime = now
                    if (nextSparkAt == 0f) {
                        nextSparkAt = if (lightweight) {
                            now + 9000f + Random.nextFloat() * 6000f
                        } else {
                            now + 4000f + Random.nextFloat() * 4000f
                        }
                    }
                    particles.forEach { p ->
                        p.y -= p.speed * dp * delta
                        p.x = p.baseX + sin(now / p.period + p.phase) * (if (lightweight) 8f else 15f) * dp
                        if (p.y < -12f * dp) {
                            p.y = hPx + 12f * dp
                            p.x = Random.nextFloat() * wPx
                            p.baseX = p.x
                        }
                    }
                    if (now > nextSparkAt) {
                        val visible = particles.filter { it.y > 0f && it.y < hPx }
                        if (visible.isNotEmpty()) {
                            val spark = visible[Random.nextInt(visible.size)]
                            spark.sparkStart = now
                            spark.sparkUntil = now + if (lightweight) 420f else 600f
                        }
                        nextSparkAt = if (lightweight) {
                            now + 9000f + Random.nextFloat() * 6000f
                        } else {
                            now + 4000f + Random.nextFloat() * 4000f
                        }
                    }
                    frameTime = ms
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = frameTime.toFloat()
            particles.forEach { p ->
                val pulse = (sin(now / p.pulseDuration * PI.toFloat() * 2f + p.phase) + 1f) / 2f
                var alpha = if (lightweight) 0.18f + pulse * 0.26f else 0.3f + pulse * 0.5f
                var radius = p.size * dp
                if (p.sparkUntil > now) {
                    val elapsed = now - p.sparkStart
                    val progress = (elapsed / if (lightweight) 420f else 600f).coerceAtMost(1f)
                    val ease = 1f - (1f - progress).pow(3)
                    val strength = 1f - ease
                    alpha = maxOf(alpha, if (lightweight) 0.34f + strength * 0.28f else 0.55f + strength * 0.45f)
                    radius = p.size * dp * (1f + strength * if (lightweight) 1.0f else 2f)
                    val glowRadius = if (lightweight) 5f * dp else 8f * dp
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                p.color.copy(alpha = (if (lightweight) 0.14f else 0.3f) * strength),
                                p.color.copy(alpha = 0f),
                            ),
                            center = Offset(p.x, p.y),
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = Offset(p.x, p.y),
                    )
                }
                drawCircle(
                    color = p.color.copy(alpha = alpha * if (lightweight) 0.28f else 0.4f),
                    radius = radius,
                    center = Offset(p.x, p.y),
                )
            }
        }
    }
}
