package com.impulsive.app.frontend.screens.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.focus.MaxFocusMinutes
import com.impulsive.app.backend.domain.model.focus.MinFocusMinutes
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val EndTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun CleanFocusDurationClock(
    durationMinutes: Int,
    currentTime: LocalDateTime,
    accent: Color,
    text: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    primaryDisplayText: String? = null,
    activeRemainingSeconds: Long? = null,
    isInteractionEnabled: Boolean = true,
    recommendedMinutes: List<Int> = listOf(15, 30, 60),
    clockSizeDp: Int = 230,
    onDurationMinutesChanged: (Int) -> Unit,
) {
    val safeDuration = durationMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = accent.copy(alpha = 0.08f),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (activeRemainingSeconds != null) {
                    FocusActiveAnimationTimer(
                        remainingText = primaryDisplayText ?: formatFocusRemainingSeconds(activeRemainingSeconds),
                        text = text,
                        muted = muted,
                        animationSizeDp = clockSizeDp,
                    )
                } else {
                    CleanFocusLiveClockFace(
                        currentTime = currentTime,
                        selectedDurationMinutes = safeDuration,
                        activeRemainingSeconds = null,
                        accent = accent,
                        muted = muted,
                        sizeDp = clockSizeDp,
                        handColor = text,
                        onDurationDragged = if (isInteractionEnabled) onDurationMinutesChanged else null,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Focus for ${formatCleanFocusDuration(safeDuration)}",
                        color = text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "Ends at ${formatFocusEndTime(currentTime, safeDuration)}",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )

                    if (isInteractionEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            recommendedMinutes.forEach { minutes ->
                                CleanFocusClockChip(
                                    label = "$minutes min",
                                    selected = safeDuration == minutes,
                                    accent = accent,
                                    onClick = {
                                        if (isInteractionEnabled) {
                                            onDurationMinutesChanged(minutes)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusActiveAnimationTimer(
    remainingText: String,
    text: Color,
    muted: Color,
    animationSizeDp: Int,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.focus_session_start),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(animationSizeDp.dp),
        )

        Text(
            text = remainingText,
            color = text,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFeatureSettings = "tnum",
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CleanFocusClockChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val chipShape = RoundedCornerShape(50)
    Surface(
        color = if (selected) {
            accent.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        shape = chipShape,
        modifier = Modifier
            .clip(chipShape)
            .impulsiveClockChipClickable { onClick() },
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF281D38) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun CleanFocusLiveClockFace(
    currentTime: LocalDateTime,
    selectedDurationMinutes: Int,
    activeRemainingSeconds: Long? = null,
    accent: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    sizeDp: Int = 230,
    handColor: Color = Color(0xFF1F1B2E),
    onDurationDragged: ((Int) -> Unit)? = null,
) {
    val safeDuration = selectedDurationMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
    val durationDragged = onDurationDragged
    val latestDurationDragged by rememberUpdatedState(durationDragged)
    val haptics = rememberImpulsiveHaptics()
    val latestHaptics by rememberUpdatedState(haptics)
    var isDragging by remember { mutableStateOf(false) }
    var visualTopDegrees by remember { mutableStateOf(durationToTopDegrees(safeDuration)) }
    val countdownTopDegrees = activeRemainingSeconds?.let { remainingSecondsToTopDegrees(it) }
    val drawnTopDegrees = countdownTopDegrees ?: visualTopDegrees

    LaunchedEffect(safeDuration, activeRemainingSeconds) {
        if (!isDragging && activeRemainingSeconds == null) {
            visualTopDegrees = durationToTopDegrees(safeDuration)
        }
    }

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (durationDragged != null) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val dragCallback = latestDurationDragged ?: return@awaitEachGesture
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                var currentRingMinute = ringMinuteFromClockOffset(down.position, size)
                                var currentDuration = ringMinuteToDurationMinutes(currentRingMinute)
                                var lastReported = currentDuration

                                isDragging = true
                                visualTopDegrees = durationToTopDegrees(currentDuration)
                                dragCallback(currentDuration)

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    currentRingMinute = ringMinuteFromClockOffset(change.position, size)
                                    currentDuration = ringMinuteToDurationMinutes(currentRingMinute)
                                    visualTopDegrees = ringMinuteToTopDegrees(currentRingMinute)

                                    if (currentDuration != lastReported) {
                                        latestHaptics.light()
                                        dragCallback(currentDuration)
                                        lastReported = currentDuration
                                    }

                                    change.consume()
                                }

                                val finalSnapped = ringMinuteToDurationMinutes(currentRingMinute)
                                visualTopDegrees = durationToTopDegrees(finalSnapped)
                                isDragging = false
                                if (finalSnapped != lastReported) {
                                    latestHaptics.light()
                                    dragCallback(finalSnapped)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val strokeWidth = 1.5.dp.toPx()
            val clockRadius = min(size.width, size.height) / 2f - 6.dp.toPx()
            val centerPoint = center
            val labelRadius = clockRadius - 24.dp.toPx()
            val handAngle = (drawnTopDegrees - 90f) * (PI / 180.0)
            val selectorRadius = clockRadius * 0.60f
            val selectorCenter = Offset(
                x = centerPoint.x + cos(handAngle).toFloat() * selectorRadius,
                y = centerPoint.y + sin(handAngle).toFloat() * selectorRadius,
            )
            val lineEndRadius = selectorRadius - 18.dp.toPx()
            val lineEnd = Offset(
                x = centerPoint.x + cos(handAngle).toFloat() * lineEndRadius,
                y = centerPoint.y + sin(handAngle).toFloat() * lineEndRadius,
            )

            drawCircle(
                color = accent.copy(alpha = 0.05f),
                radius = clockRadius,
                center = centerPoint,
            )

            drawCircle(
                color = handColor.copy(alpha = 0.10f),
                radius = clockRadius,
                center = centerPoint,
                style = Stroke(width = strokeWidth),
            )

            val numberColor = handColor.copy(alpha = 0.58f)
            val activeLabel = (((drawnTopDegrees % 360f) + 360f) % 360f / 30f).roundToInt() % 12
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 12.dp.toPx()
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.NORMAL,
                    )
                }
                repeat(12) { index ->
                    val angle = ((index / 12f) * 360f - 90f) * (PI / 180.0)
                    val labelCenter = Offset(
                        x = centerPoint.x + cos(angle).toFloat() * labelRadius,
                        y = centerPoint.y + sin(angle).toFloat() * labelRadius,
                    )
                    val isActive = index == activeLabel
                    paint.color = if (isActive) {
                        accent.copy(alpha = 0.95f).toArgb()
                    } else {
                        numberColor.toArgb()
                    }
                    paint.typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
                    )
                    val label = ((index * 5) % 60).toString().padStart(2, '0')
                    val textY = labelCenter.y - (paint.descent() + paint.ascent()) / 2f
                    canvas.nativeCanvas.drawText(label, labelCenter.x, textY, paint)
                }
            }

            drawLine(
                color = numberColor,
                start = centerPoint,
                end = lineEnd,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            drawCircle(
                color = handColor.copy(alpha = 0.75f),
                radius = 3.dp.toPx(),
                center = centerPoint,
            )

            drawCircle(
                color = accent.copy(alpha = 0.08f),
                radius = 12.dp.toPx(),
                center = selectorCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.96f),
                radius = 10.dp.toPx(),
                center = selectorCenter,
            )
            drawCircle(
                color = accent.copy(alpha = 0.90f),
                radius = 10.dp.toPx(),
                center = selectorCenter,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = accent.copy(alpha = 0.95f),
                radius = 3.5.dp.toPx(),
                center = selectorCenter,
            )
        }
    }
}

@Composable
private fun Modifier.impulsiveClockChipClickable(
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

fun formatCleanFocusDuration(totalMinutes: Int): String {
    val safeTotal = totalMinutes.coerceAtLeast(0)
    val hours = safeTotal / 60
    val minutes = safeTotal % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "$minutes min"
    }
}

fun formatFocusEndTime(
    currentTime: LocalDateTime,
    totalMinutes: Int,
    activeRemainingSeconds: Long? = null,
): String {
    val endTime = activeRemainingSeconds
        ?.coerceAtLeast(0L)
        ?.let { currentTime.plusSeconds(it) }
        ?: currentTime.plusMinutes(totalMinutes.toLong())

    return endTime.format(EndTimeFormatter)
}

private fun formatFocusRemainingSeconds(remainingSeconds: Long): String {
    val safeRemaining = remainingSeconds.coerceAtLeast(0L)
    val minutes = safeRemaining / 60L
    val seconds = safeRemaining % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Int.roundToFiveMinuteStep(): Int {
    val rounded = (this / 5f).roundToInt() * 5
    return rounded.coerceIn(MinFocusMinutes, MaxFocusMinutes)
}

private fun durationToTopDegrees(totalMinutes: Int): Float =
    durationFloatToTopDegrees(totalMinutes.toFloat())

private fun remainingSecondsToTopDegrees(remainingSeconds: Long): Float {
    if (remainingSeconds <= 0L) return 0f

    val remainingMinutes = remainingSeconds / 60f
    val minuteInCurrentHour = remainingMinutes % 60f

    return if (minuteInCurrentHour == 0f) {
        0f
    } else {
        minuteInCurrentHour * 6f
    }
}

private fun durationFloatToTopDegrees(totalMinutes: Float): Float {
    val minute = totalMinutes % 60f
    val displayMinute = if (minute == 0f) 60f else minute
    return displayMinute * 6f
}

private fun ringMinuteToTopDegrees(minute: Float): Float = minute * 6f

private fun ringMinuteToDurationMinutes(minute: Float): Int {
    val roundedMinute = (minute / 5f).roundToInt() * 5
    val duration = if (roundedMinute == 0 || roundedMinute == 60) 60 else roundedMinute
    return duration.coerceIn(MinFocusMinutes, MaxFocusMinutes)
}

private fun minuteFromClockOffset(offset: Offset, size: IntSize): Int {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radians = atan2(offset.y - centerY, offset.x - centerX)
    val degreesFromTop = ((radians * 180f / PI.toFloat()) + 90f + 360f) % 360f
    val minute = ((degreesFromTop / 360f) * 60f).roundToInt()
    return if (minute >= 60) 0 else minute.coerceIn(0, 59)
}

private fun minuteFloatFromClockOffset(offset: Offset, size: IntSize): Float {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radians = atan2(offset.y - centerY, offset.x - centerX)
    val degreesFromTop = ((radians * 180f / PI.toFloat()) + 90f + 360f) % 360f
    return (degreesFromTop / 360f) * 60f
}

private fun ringMinuteFromClockOffset(offset: Offset, size: IntSize): Float {
    val minute = minuteFloatFromClockOffset(offset, size)
    return if (minute >= 60f) 0f else minute.coerceIn(0f, 59.999f)
}
