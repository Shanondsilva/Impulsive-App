package com.impulsive.app.frontend.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.impulsive.app.R
import com.impulsive.app.core.util.TimeOfDay
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CloudSpec(
    val phase: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val alpha: Float,
    val speed: Float,
)

private data class NightWindowGlowSpec(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val phase: Float,
    val speed: Float,
    val isRound: Boolean = false,
)

private data class NightCloudDriftSpec(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val phase: Float,
    val speed: Float,
    val driftPx: Float,
    val alpha: Float,
)

private data class PetalSpec(
    val startX: Float,
    val startY: Float,
    val delay: Float,
    val duration: Float,
    val drift: Float,
    val size: Float,
    val opacity: Float,
    val rotationTurns: Float,
)

private const val AmbientLoopMillis = 36_000

private val PetalSpecs = listOf(
    PetalSpec(startX = 0.12f, startY = -0.03f, delay = 0.03f, duration = 0.15f, drift = 0.07f, size = 0.80f, opacity = 0.34f, rotationTurns = 0.45f),
    PetalSpec(startX = 0.78f, startY = -0.04f, delay = 0.18f, duration = 0.13f, drift = -0.05f, size = 0.58f, opacity = 0.26f, rotationTurns = -0.35f),
    PetalSpec(startX = 0.36f, startY = -0.02f, delay = 0.34f, duration = 0.16f, drift = 0.09f, size = 0.70f, opacity = 0.30f, rotationTurns = 0.55f),
    PetalSpec(startX = 0.58f, startY = -0.06f, delay = 0.50f, duration = 0.12f, drift = -0.08f, size = 0.64f, opacity = 0.24f, rotationTurns = -0.48f),
    PetalSpec(startX = 0.22f, startY = -0.04f, delay = 0.67f, duration = 0.14f, drift = 0.06f, size = 0.52f, opacity = 0.22f, rotationTurns = 0.38f),
    PetalSpec(startX = 0.88f, startY = -0.03f, delay = 0.84f, duration = 0.13f, drift = -0.07f, size = 0.76f, opacity = 0.28f, rotationTurns = -0.42f),
)

private val NightWindowGlowSpecs = listOf(
    NightWindowGlowSpec(x = 472f, y = 331f, width = 18f, height = 30f, phase = 0.03f, speed = 8.0f),
    NightWindowGlowSpec(x = 324f, y = 417f, width = 22f, height = 40f, phase = 0.19f, speed = 9.0f),
    NightWindowGlowSpec(x = 623f, y = 285f, width = 10f, height = 16f, phase = 0.37f, speed = 7.6f),
    NightWindowGlowSpec(x = 871f, y = 293f, width = 16f, height = 24f, phase = 0.53f, speed = 8.5f),
    NightWindowGlowSpec(x = 970f, y = 340f, width = 14f, height = 28f, phase = 0.66f, speed = 8.9f),
    NightWindowGlowSpec(x = 1028f, y = 443f, width = 30f, height = 30f, phase = 0.71f, speed = 7.2f, isRound = true),
    NightWindowGlowSpec(x = 1190f, y = 553f, width = 28f, height = 52f, phase = 0.86f, speed = 9.4f),
)

private val NightCloudDriftSpecs = listOf(
    NightCloudDriftSpec(
        centerX = 478f,
        centerY = 134f,
        width = 190f,
        height = 58f,
        phase = 0.00f,
        speed = 2.0f,
        driftPx = 10f,
        alpha = 0.055f,
    ),
    NightCloudDriftSpec(
        centerX = 1066f,
        centerY = 122f,
        width = 300f,
        height = 82f,
        phase = 0.50f,
        speed = 2.3f,
        driftPx = -12f,
        alpha = 0.050f,
    ),
)

private fun calmWave(progress: Float, phase: Float = 0f): Float =
    0.5f + 0.5f * sin(((progress + phase) % 1f) * 2f * PI).toFloat()

private fun activeProgress(loopProgress: Float, delay: Float, duration: Float): Float? {
    val elapsed = if (loopProgress >= delay) {
        loopProgress - delay
    } else {
        loopProgress + 1f - delay
    }
    return (elapsed / duration).takeIf { it in 0f..1f }
}

@Composable
fun MindCoreScene(
    level: Int,
    timeOfDay: TimeOfDay,
    modifier: Modifier = Modifier,
) {
    val isNight = timeOfDay == TimeOfDay.Night

    val infiniteTransition = rememberInfiniteTransition(label = "mindCore")

    val ambientTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AmbientLoopMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientTime",
    )

    val glowAlpha = 0.10f + 0.10f * calmWave(ambientTime, phase = 0.12f)
    val breath = 1.000f + 0.010f * calmWave(ambientTime, phase = 0.40f)
    val shimmerAlpha = 0.08f + 0.18f * calmWave(ambientTime, phase = 0.72f)

    Box(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Image(
            painter = painterResource(id = sceneDrawableFor(level, timeOfDay)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleY = if (isNight) 1f else breath
                    scaleX = if (isNight) 1f else 1f + (breath - 1f) * 0.4f
                    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
                },
        )

        if (!isNight) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val sunCenter = when (timeOfDay) {
                    TimeOfDay.Morning -> Offset(w * 0.22f, h * 0.18f)
                    TimeOfDay.Afternoon -> null
                    TimeOfDay.Evening -> Offset(w * 0.74f, h * 0.24f)
                    TimeOfDay.Night -> Offset(w * 0.50f, h * 0.18f)
                }
                if (sunCenter != null) {
                    val glowPulse = 0.92f + 0.08f * calmWave(ambientTime, phase = 0.08f)
                    drawCircle(
                        color = Color(0xFFFFF3B0).copy(alpha = 0.20f),
                        radius = 36.dp.toPx() * glowPulse,
                        center = sunCenter,
                    )
                    drawCircle(
                        color = Color(0xFFFFF0A0).copy(alpha = 0.30f),
                        radius = 24.dp.toPx() * glowPulse,
                        center = sunCenter,
                    )
                    drawCircle(
                        color = Color(0xFFFFE89A).copy(alpha = 0.92f),
                        radius = 12.dp.toPx() * glowPulse,
                        center = sunCenter,
                    )
                }
            }

            Canvas(modifier = Modifier.matchParentSize()) {
                val wScene = size.width
                val hScene = size.height
                val clouds = listOf(
                    CloudSpec(phase = 0.00f, y = 0.11f, w = 0.24f, h = 0.052f, alpha = 0.42f, speed = 0.42f),
                    CloudSpec(phase = 0.24f, y = 0.18f, w = 0.18f, h = 0.040f, alpha = 0.36f, speed = 0.34f),
                    CloudSpec(phase = 0.56f, y = 0.09f, w = 0.21f, h = 0.048f, alpha = 0.32f, speed = 0.38f),
                    CloudSpec(phase = 0.78f, y = 0.23f, w = 0.17f, h = 0.038f, alpha = 0.28f, speed = 0.30f),
                )

                clouds.forEach { (phase, y, width, height, alpha, speed) ->
                    val loop = (((ambientTime * speed) + phase) % 1f)
                    val smooth = 0.5f - 0.5f * cos(loop * 2f * PI.toFloat())
                    val baseX = (-0.16f + smooth * 1.34f) * wScene
                    val baseY = (y + sin((ambientTime + phase) * 2f * PI.toFloat()).toFloat() * 0.009f) * hScene
                    val cloudColor = Color.White.copy(alpha = alpha)
                    drawCircle(
                        color = cloudColor,
                        radius = height * hScene * 0.90f,
                        center = Offset(baseX + width * wScene * 0.10f, baseY),
                    )
                    drawCircle(
                        color = cloudColor.copy(alpha = alpha * 0.95f),
                        radius = height * hScene * 1.05f,
                        center = Offset(baseX + width * wScene * 0.34f, baseY - height * hScene * 0.14f),
                    )
                    drawCircle(
                        color = cloudColor.copy(alpha = alpha * 0.92f),
                        radius = height * hScene * 0.82f,
                        center = Offset(baseX + width * wScene * 0.58f, baseY + height * hScene * 0.08f),
                    )
                    drawRoundRect(
                        color = cloudColor.copy(alpha = alpha * 0.88f),
                        topLeft = Offset(baseX + width * wScene * 0.14f, baseY - height * hScene * 0.28f),
                        size = Size(width * wScene * 0.62f, height * hScene * 0.86f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * hScene * 0.38f),
                    )
                }
            }

            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawAmbientBird(
                    loopProgress = ambientTime,
                    width = w,
                    height = h,
                )
            }

            if (level >= 2) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val windows = listOf(
                        0.30f to 0.32f,
                        0.70f to 0.40f,
                    )
                    windows.forEach { (x, y) ->
                        drawCircle(
                            color = Color(0xFFFFE08A).copy(alpha = shimmerAlpha),
                            radius = 14.dp.toPx(),
                            center = Offset(x * w, y * h),
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.matchParentSize()) {
                val h = size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ImpulsivePsychological.copy(alpha = 0f),
                            ImpulsivePsychological.copy(alpha = glowAlpha),
                        ),
                        startY = h * 0.45f,
                        endY = h,
                    ),
                    topLeft = Offset(0f, h * 0.45f),
                    size = Size(size.width, h * 0.55f),
                )
            }

            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                PetalSpecs.forEach { petal ->
                    drawAmbientPetal(
                        loopProgress = ambientTime,
                        petal = petal,
                        width = w,
                        height = h,
                    )
                }
            }

            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val seeds = listOf(
                    0.20f to 0.00f,
                    0.50f to 0.30f,
                    0.78f to 0.60f,
                    0.30f to 0.80f,
                )
                seeds.forEach { (startX, phase) ->
                    val t = (ambientTime + phase) % 1f
                    val sx = startX + cos((t * 4f * PI).toFloat()) * 0.025f
                    val sy = 1.05f - t * 1.15f
                    val alpha = when {
                        t < 0.1f -> t / 0.1f
                        t > 0.9f -> (1f - t) / 0.1f
                        else -> 1f
                    } * 0.55f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.4f),
                        radius = 6.dp.toPx(),
                        center = Offset(sx * w, sy * h),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 2.dp.toPx(),
                        center = Offset(sx * w, sy * h),
                    )
                }
            }

            if (level >= 3) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val butterflies = listOf(
                        Triple(0.70f, 0.60f, 0.0f),
                        Triple(0.80f, 0.55f, 0.5f),
                    )
                    butterflies.forEach { (baseX, baseY, phase) ->
                        val t = ((ambientTime + phase) % 1f) * 2f * PI.toFloat()
                        val driftX = baseX + sin(t).toFloat() * 0.04f
                        val driftY = baseY + cos(t * 1.3f).toFloat() * 0.025f
                        val flap = 0.5f + 0.5f * cos(t * 6f).toFloat()
                        val cx = driftX * w
                        val cy = driftY * h
                        val wingW = 4.dp.toPx() + 3.dp.toPx() * flap
                        val wingH = 6.dp.toPx()
                        drawOval(
                            color = Color(0xFFD8B4FE).copy(alpha = 0.7f),
                            topLeft = Offset(cx - wingW, cy - wingH / 2),
                            size = Size(wingW, wingH),
                        )
                        drawOval(
                            color = Color(0xFFD8B4FE).copy(alpha = 0.7f),
                            topLeft = Offset(cx, cy - wingH / 2),
                            size = Size(wingW, wingH),
                        )
                    }
                }
            }

            if (level == 5) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val positions = listOf(
                        Triple(0.55f, 0.55f, 0f),
                        Triple(0.72f, 0.40f, 0.1875f),
                        Triple(0.85f, 0.62f, 0.375f),
                        Triple(0.35f, 0.70f, 0.5625f),
                        Triple(0.15f, 0.50f, 0.75f),
                        Triple(0.92f, 0.45f, 0.9375f),
                    )
                    val twinklePhases = listOf(0f, 0.190f, 0.381f, 0.571f, 0.762f, 0.952f)
                    positions.forEachIndexed { i, (bx, by, bobPhase) ->
                        val bobY =
                            sin((ambientTime + bobPhase) * 2f * PI).toFloat() * 6.dp.toPx()
                        val twinkle = 0.45f + 0.55f * (
                            0.5f + 0.5f * sin(
                                (ambientTime + twinklePhases[i]) * 2f * PI,
                            ).toFloat()
                        )
                        val cx = bx * w
                        val cy = by * h + bobY

                        drawCircle(
                            color = Color(0xFFFFF5C2).copy(alpha = 0.4f * twinkle),
                            radius = 7.dp.toPx(),
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = Color(0xFFFFF5C2).copy(alpha = twinkle),
                            radius = 3.dp.toPx(),
                            center = Offset(cx, cy),
                        )
                    }
                }
            }
        }

        if (isNight) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val sourceWidth = 1448f
                val sourceHeight = 1086f
                val sceneScale = maxOf(w / sourceWidth, h / sourceHeight)
                val drawnWidth = sourceWidth * sceneScale
                val drawnHeight = sourceHeight * sceneScale
                val imageLeft = (w - drawnWidth) / 2f
                val imageTop = (h - drawnHeight) / 2f

                fun sceneOffset(x: Float, y: Float): Offset = Offset(
                    x = imageLeft + x * sceneScale,
                    y = imageTop + y * sceneScale,
                )

                NightCloudDriftSpecs.forEach { cloud ->
                    val driftWave = sin(((ambientTime * cloud.speed) + cloud.phase) * 2f * PI).toFloat()
                    val center = sceneOffset(
                        x = cloud.centerX + driftWave * cloud.driftPx,
                        y = cloud.centerY,
                    )
                    val cloudWidth = cloud.width * sceneScale
                    val cloudHeight = cloud.height * sceneScale
                    val cloudColor = Color(0xFFC6A0D8).copy(alpha = cloud.alpha)

                    drawCircle(
                        color = cloudColor,
                        radius = cloudHeight * 0.48f,
                        center = Offset(center.x - cloudWidth * 0.24f, center.y + cloudHeight * 0.03f),
                    )
                    drawCircle(
                        color = cloudColor.copy(alpha = cloud.alpha * 0.92f),
                        radius = cloudHeight * 0.62f,
                        center = Offset(center.x - cloudWidth * 0.02f, center.y - cloudHeight * 0.18f),
                    )
                    drawCircle(
                        color = cloudColor.copy(alpha = cloud.alpha * 0.82f),
                        radius = cloudHeight * 0.44f,
                        center = Offset(center.x + cloudWidth * 0.22f, center.y + cloudHeight * 0.02f),
                    )
                    drawRoundRect(
                        color = cloudColor.copy(alpha = cloud.alpha * 0.76f),
                        topLeft = Offset(center.x - cloudWidth * 0.38f, center.y - cloudHeight * 0.08f),
                        size = Size(cloudWidth * 0.76f, cloudHeight * 0.38f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cloudHeight * 0.19f),
                    )
                }

                val moonWave = calmWave((ambientTime * 6f) % 1f, phase = 0.19f)
                val moonCenter = sceneOffset(1345f, 115f)
                val moonRadius = 85f * sceneScale * (0.95f + 0.05f * moonWave)
                val moonAlpha = 0.15f + 0.15f * moonWave

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFE8A3).copy(alpha = moonAlpha),
                            Color(0xFFFFE8A3).copy(alpha = moonAlpha * 0.30f),
                            Color.Transparent,
                        ),
                        center = moonCenter,
                        radius = moonRadius,
                    ),
                    radius = moonRadius,
                    center = moonCenter,
                )

                NightWindowGlowSpecs.forEach { window ->
                    val windowWave = calmWave((ambientTime * window.speed) % 1f, phase = window.phase)
                    val flicker = 0.85f + 0.15f * windowWave
                    val center = sceneOffset(window.x, window.y)
                    val windowWidth = window.width * sceneScale
                    val windowHeight = window.height * sceneScale
                    val warmGlow = Color(0xFFFFD98A)
                    val glowRadius = maxOf(windowWidth, windowHeight) * if (window.isRound) 0.95f else 0.78f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                warmGlow.copy(alpha = 0.08f * flicker),
                                warmGlow.copy(alpha = 0.03f * flicker),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = glowRadius * 1.55f,
                        ),
                        radius = glowRadius * 1.55f,
                        center = center,
                    )

                    if (window.isRound) {
                        val radius = maxOf(windowWidth, windowHeight) * 0.43f
                        drawCircle(
                            color = warmGlow.copy(alpha = 0.13f * flicker),
                            radius = radius,
                            center = center,
                        )
                    } else {
                        drawRoundRect(
                            color = warmGlow.copy(alpha = 0.12f * flicker),
                            topLeft = Offset(center.x - windowWidth * 0.50f, center.y - windowHeight * 0.50f),
                            size = Size(windowWidth, windowHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(windowHeight * 0.18f),
                        )
                    }
                }

                // Tiny sky twinkles. Keep them subtle so they do not look like bubbles.
                val nightStars = listOf(
                    Triple(45f, 42f, 0.00f),
                    Triple(230f, 74f, 0.17f),
                    Triple(342f, 59f, 0.31f),
                    Triple(590f, 35f, 0.48f),
                    Triple(690f, 81f, 0.62f),
                    Triple(808f, 89f, 0.76f),
                    Triple(944f, 28f, 0.88f),
                    Triple(1415f, 82f, 0.12f),
                )
                nightStars.forEach { (sx, sy, phase) ->
                    val twinkle = calmWave(ambientTime, phase = phase)
                    val alpha = 0.40f + 0.50f * twinkle
                    val starScale = 0.90f + 0.20f * twinkle
                    drawCircle(
                        color = Color(0xFFFFF6D8).copy(alpha = alpha),
                        radius = 0.95.dp.toPx() * starScale,
                        center = sceneOffset(sx, sy),
                    )
                }
            }
        }

    }
}

private fun sceneDrawableFor(level: Int, time: TimeOfDay): Int {
    val t = time
    return when (level.coerceIn(1, 5)) {
        1 -> when (t) {
            TimeOfDay.Morning -> R.drawable.level1_morning
            TimeOfDay.Afternoon -> R.drawable.level1_afternoon
            TimeOfDay.Evening -> R.drawable.level1_evening
            TimeOfDay.Night -> R.drawable.level1_night
        }
        2 -> when (t) {
            TimeOfDay.Morning -> R.drawable.level2_morning
            TimeOfDay.Afternoon -> R.drawable.level2_afternoon
            TimeOfDay.Evening -> R.drawable.level2_evening
            TimeOfDay.Night -> R.drawable.level2_night
        }
        3 -> when (t) {
            TimeOfDay.Morning -> R.drawable.level3_morning
            TimeOfDay.Afternoon -> R.drawable.level3_afternoon
            TimeOfDay.Evening -> R.drawable.level3_evening
            TimeOfDay.Night -> R.drawable.level3_night
        }
        4 -> R.drawable.level_world_4
        5 -> R.drawable.level_world_5
        else -> R.drawable.level1_afternoon
    }
}

private fun DrawScope.drawAmbientBird(
    loopProgress: Float,
    width: Float,
    height: Float,
) {
    val birdProgress = activeProgress(
        loopProgress = loopProgress,
        delay = 6f / 36f,
        duration = 14f / 36f,
    ) ?: return
    val eased = birdProgress * birdProgress * (3f - 2f * birdProgress)
    val fade = when {
        birdProgress < 0.16f -> birdProgress / 0.16f
        birdProgress > 0.84f -> (1f - birdProgress) / 0.16f
        else -> 1f
    }
    val x = (-0.08f + eased * 1.16f) * width
    val y = (
        0.115f +
            sin(eased * PI).toFloat() * 0.035f +
            sin(eased * 2f * PI + 0.7f).toFloat() * 0.010f
        ) * height
    val wingSpan = 8.dp.toPx()
    val wingLift = (2.2f + sin(birdProgress * 18f * PI).toFloat() * 1.2f).dp.toPx()
    val bodyLift = 0.7.dp.toPx() * sin(birdProgress * 4f * PI).toFloat()
    val color = Color(0xFF74677F).copy(alpha = 0.38f * fade)
    val strokeWidth = 1.25.dp.toPx()

    val leftWing = Path().apply {
        moveTo(x, y + bodyLift)
        cubicTo(
            x - wingSpan * 0.32f,
            y - wingLift * 0.80f + bodyLift,
            x - wingSpan * 0.78f,
            y - wingLift * 0.80f + bodyLift,
            x - wingSpan,
            y + wingLift * 0.35f + bodyLift,
        )
    }
    val rightWing = Path().apply {
        moveTo(x, y + bodyLift)
        cubicTo(
            x + wingSpan * 0.32f,
            y - wingLift * 0.85f + bodyLift,
            x + wingSpan * 0.78f,
            y - wingLift * 0.78f + bodyLift,
            x + wingSpan,
            y + wingLift * 0.28f + bodyLift,
        )
    }

    drawPath(
        path = leftWing,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawPath(
        path = rightWing,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawAmbientPetal(
    loopProgress: Float,
    petal: PetalSpec,
    width: Float,
    height: Float,
) {
    val petalProgress = activeProgress(
        loopProgress = loopProgress,
        delay = petal.delay,
        duration = petal.duration,
    ) ?: return
    val fade = when {
        petalProgress < 0.18f -> petalProgress / 0.18f
        petalProgress > 0.82f -> (1f - petalProgress) / 0.18f
        else -> 1f
    }
    val driftWave = sin((petalProgress * 2.5f * PI) + petal.delay * 9f).toFloat()
    val x = (petal.startX + petal.drift * petalProgress + driftWave * 0.018f) * width
    val y = (petal.startY + petalProgress * 0.92f) * height
    val petalWidth = 6.2.dp.toPx() * petal.size
    val petalHeight = 3.0.dp.toPx() * petal.size
    val rotation = (petalProgress * petal.rotationTurns * 360f) + petal.delay * 180f

    rotate(degrees = rotation, pivot = Offset(x, y)) {
        drawOval(
            color = Color(0xFFC9AFA8).copy(alpha = petal.opacity * fade),
            topLeft = Offset(x - petalWidth / 2f, y - petalHeight / 2f),
            size = Size(petalWidth, petalHeight),
        )
    }
}
