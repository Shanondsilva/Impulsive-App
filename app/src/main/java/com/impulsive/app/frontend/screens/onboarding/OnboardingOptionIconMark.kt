package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun OptionIconMark(
    icon: OnboardingOptionIcon,
    selected: Boolean,
    plain: Boolean = false,
    plainIconSize: Dp = 18.dp,
) {
    val fillColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFFFFEFC).copy(alpha = 0.74f) else OnboardingIconSurface,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "option-icon-fill",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingIconMuted,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "option-icon-content",
    )
    val drawableResId = icon.drawableResId

    if (drawableResId != null) {
        Image(
            painter = painterResource(id = drawableResId),
            contentDescription = null,
            modifier = Modifier.size(if (plain) plainIconSize else 18.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
    } else {
        Box(
            modifier = if (plain) {
                Modifier.size(plainIconSize)
            } else {
                Modifier
                    .size(40.dp)
                    .background(fillColor, CircleShape)
                    .border(1.dp, OnboardingIconBorder, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(if (plain) plainIconSize else 22.dp)) {
                val strokeWidth = 2.1.dp.toPx()
                val thinStroke = 1.7.dp.toPx()

                when (icon) {
                    OnboardingOptionIcon.PrivateHabit,
                    OnboardingOptionIcon.CompulsiveScrolling,
                    OnboardingOptionIcon.LateNightPhone,
                    OnboardingOptionIcon.BrowserHabit,
                    OnboardingOptionIcon.SomethingElse,
                    OnboardingOptionIcon.LateAtNight,
                    OnboardingOptionIcon.RightAfterWaking,
                    OnboardingOptionIcon.AloneOnPhone,
                    OnboardingOptionIcon.WhenBored,
                    OnboardingOptionIcon.WhenStressed,
                    OnboardingOptionIcon.TroubleSleeping,
                    OnboardingOptionIcon.SocialMedia,
                    OnboardingOptionIcon.BrowserSearch,
                    OnboardingOptionIcon.MemoryOrThought,
                    OnboardingOptionIcon.BoredomTrigger,
                    OnboardingOptionIcon.BeingAlone,
                    OnboardingOptionIcon.StressTrigger,
                    OnboardingOptionIcon.NoticeTriggers,
                    OnboardingOptionIcon.CutDownLittle,
                    OnboardingOptionIcon.DailyResetHabit,
                    OnboardingOptionIcon.CutDownHalf -> Unit
                    OnboardingOptionIcon.Shield -> {
                        val path = Path().apply {
                            moveTo(size.width * 0.50f, size.height * 0.10f)
                            lineTo(size.width * 0.82f, size.height * 0.24f)
                            lineTo(size.width * 0.76f, size.height * 0.64f)
                            quadraticBezierTo(size.width * 0.50f, size.height * 0.90f, size.width * 0.24f, size.height * 0.64f)
                            lineTo(size.width * 0.18f, size.height * 0.24f)
                            close()
                        }
                        drawPath(path, contentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.34f, size.height * 0.52f), Offset(size.width * 0.45f, size.height * 0.64f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.45f, size.height * 0.64f), Offset(size.width * 0.66f, size.height * 0.40f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Incognito -> {
                        drawArc(contentColor, 205f, 130f, false, Offset(size.width * 0.18f, size.height * 0.18f), Size(size.width * 0.64f, size.height * 0.52f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.56f), Offset(size.width * 0.84f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                        drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.34f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.66f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.48f, size.height * 0.68f), Offset(size.width * 0.52f, size.height * 0.68f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Social -> {
                        val left = Offset(size.width * 0.24f, size.height * 0.58f)
                        val top = Offset(size.width * 0.54f, size.height * 0.28f)
                        val right = Offset(size.width * 0.78f, size.height * 0.70f)
                        drawLine(contentColor, left, top, thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, top, right, thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, left, right, thinStroke, cap = StrokeCap.Round)
                        drawCircle(contentColor, radius = 2.5.dp.toPx(), center = left)
                        drawCircle(contentColor, radius = 2.5.dp.toPx(), center = top)
                        drawCircle(contentColor, radius = 2.5.dp.toPx(), center = right)
                    }
                    OnboardingOptionIcon.Loop -> {
                        drawArc(contentColor, 35f, 250f, false, Offset(size.width * 0.14f, size.height * 0.18f), Size(size.width * 0.72f, size.height * 0.62f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.74f, size.height * 0.18f), Offset(size.width * 0.84f, size.height * 0.38f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.74f, size.height * 0.18f), Offset(size.width * 0.54f, size.height * 0.22f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Search -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.25f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.62f, size.height * 0.62f), Offset(size.width * 0.82f, size.height * 0.82f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Stress -> {
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                        drawArc(contentColor, 180f, 145f, false, Offset(size.width * 0.14f, size.height * 0.30f), Size(size.width * 0.44f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 215f, 145f, false, Offset(size.width * 0.42f, size.height * 0.30f), Size(size.width * 0.44f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                    OnboardingOptionIcon.Boredom -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = 1.5.dp.toPx(), center = Offset(size.width * 0.40f, size.height * 0.43f))
                        drawCircle(contentColor, radius = 1.5.dp.toPx(), center = Offset(size.width * 0.60f, size.height * 0.43f))
                        drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.65f), Offset(size.width * 0.62f, size.height * 0.65f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Lonely,
                    OnboardingOptionIcon.Person -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.15f, center = Offset(size.width * 0.50f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 205f, 130f, false, Offset(size.width * 0.24f, size.height * 0.50f), Size(size.width * 0.52f, size.height * 0.36f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        if (icon == OnboardingOptionIcon.Lonely) {
                            drawCircle(contentColor.copy(alpha = 0.5f), radius = size.minDimension * 0.10f, center = Offset(size.width * 0.76f, size.height * 0.34f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        }
                    }
                    OnboardingOptionIcon.Heart -> {
                        val path = Path().apply {
                            moveTo(size.width * 0.50f, size.height * 0.80f)
                            cubicTo(size.width * 0.16f, size.height * 0.58f, size.width * 0.18f, size.height * 0.26f, size.width * 0.38f, size.height * 0.26f)
                            cubicTo(size.width * 0.48f, size.height * 0.26f, size.width * 0.50f, size.height * 0.36f, size.width * 0.50f, size.height * 0.36f)
                            cubicTo(size.width * 0.50f, size.height * 0.36f, size.width * 0.52f, size.height * 0.26f, size.width * 0.62f, size.height * 0.26f)
                            cubicTo(size.width * 0.82f, size.height * 0.26f, size.width * 0.84f, size.height * 0.58f, size.width * 0.50f, size.height * 0.80f)
                        }
                        drawPath(path, contentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                    OnboardingOptionIcon.Moon -> {
                        drawArc(contentColor, 82f, 230f, false, Offset(size.width * 0.18f, size.height * 0.12f), Size(size.width * 0.62f, size.height * 0.76f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 95f, 200f, false, Offset(size.width * 0.34f, size.height * 0.10f), Size(size.width * 0.58f, size.height * 0.78f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                    OnboardingOptionIcon.Morning,
                    OnboardingOptionIcon.Afternoon,
                    OnboardingOptionIcon.Evening -> {
                        val radius = if (icon == OnboardingOptionIcon.Afternoon) size.minDimension * 0.20f else size.minDimension * 0.16f
                        val y = if (icon == OnboardingOptionIcon.Evening) size.height * 0.62f else size.height * 0.44f
                        drawCircle(contentColor, radius = radius, center = Offset(size.width * 0.50f, y), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        repeat(6) { index ->
                            val angle = Math.toRadians((index * 60).toDouble())
                            val start = Offset(x = size.width * 0.50f + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.31f, y = y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.31f)
                            val end = Offset(x = size.width * 0.50f + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.42f, y = y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.42f)
                            drawLine(contentColor, start, end, thinStroke, cap = StrokeCap.Round)
                        }
                        if (icon == OnboardingOptionIcon.Evening) {
                            drawLine(contentColor, Offset(size.width * 0.18f, size.height * 0.78f), Offset(size.width * 0.82f, size.height * 0.78f), strokeWidth, cap = StrokeCap.Round)
                        }
                    }
                    OnboardingOptionIcon.Work -> {
                        drawRoundRect(contentColor, Offset(size.width * 0.18f, size.height * 0.36f), Size(size.width * 0.64f, size.height * 0.42f), CornerRadius(5.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.36f), Offset(size.width * 0.38f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.62f, size.height * 0.36f), Offset(size.width * 0.62f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.26f), Offset(size.width * 0.62f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Notice -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.30f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = size.minDimension * 0.10f, center = center)
                    }
                    OnboardingOptionIcon.Pause -> {
                        drawRoundRect(contentColor, Offset(size.width * 0.30f, size.height * 0.22f), Size(size.width * 0.12f, size.height * 0.56f), CornerRadius(3.dp.toPx()))
                        drawRoundRect(contentColor, Offset(size.width * 0.58f, size.height * 0.22f), Size(size.width * 0.12f, size.height * 0.56f), CornerRadius(3.dp.toPx()))
                    }
                    OnboardingOptionIcon.Target -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = size.minDimension * 0.18f, center = center, style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = 2.dp.toPx(), center = center)
                    }
                    OnboardingOptionIcon.Boundary -> {
                        drawRoundRect(contentColor, Offset(size.width * 0.22f, size.height * 0.18f), Size(size.width * 0.56f, size.height * 0.64f), CornerRadius(6.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.36f, size.height * 0.50f), Offset(size.width * 0.64f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.CheckIn -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.32f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.64f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.64f), Offset(size.width * 0.70f, size.height * 0.38f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Lock -> {
                        drawArc(contentColor, 180f, 180f, false, Offset(size.width * 0.32f, size.height * 0.16f), Size(size.width * 0.36f, size.height * 0.36f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawRoundRect(contentColor, Offset(size.width * 0.22f, size.height * 0.48f), Size(size.width * 0.56f, size.height * 0.36f), CornerRadius(5.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = 2.6.dp.toPx(), center = Offset(size.width * 0.50f, size.height * 0.64f))
                    }
                    OnboardingOptionIcon.Swipe -> {
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.34f, size.height * 0.30f), Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.30f), Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.50f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.34f, size.height * 0.70f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.70f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Globe -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.50f), Offset(size.width * 0.84f, size.height * 0.50f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.84f), thinStroke, cap = StrokeCap.Round)
                        drawArc(contentColor, 0f, 360f, false, Offset(size.width * 0.32f, size.height * 0.16f), Size(size.width * 0.36f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    }
                    OnboardingOptionIcon.Add -> {
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.18f), Offset(size.width * 0.50f, size.height * 0.82f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.18f, size.height * 0.50f), Offset(size.width * 0.82f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.Smartphone -> {
                        drawRoundRect(contentColor, Offset(size.width * 0.28f, size.height * 0.10f), Size(size.width * 0.44f, size.height * 0.80f), CornerRadius(6.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.50f, size.height * 0.82f))
                        drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.18f), Offset(size.width * 0.62f, size.height * 0.18f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.SleepTrouble -> {
                        drawArc(contentColor, 82f, 230f, false, Offset(size.width * 0.18f, size.height * 0.12f), Size(size.width * 0.62f, size.height * 0.76f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 95f, 200f, false, Offset(size.width * 0.34f, size.height * 0.10f), Size(size.width * 0.58f, size.height * 0.78f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.80f, size.height * 0.24f))
                        drawCircle(contentColor, radius = 1.2.dp.toPx(), center = Offset(size.width * 0.74f, size.height * 0.12f))
                        drawCircle(contentColor, radius = 1.0.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.40f))
                    }
                    OnboardingOptionIcon.Thought -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.30f, center = Offset(size.width * 0.50f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 0f, 180f, false, Offset(size.width * 0.32f, size.height * 0.33f), Size(size.width * 0.14f, size.height * 0.14f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawArc(contentColor, 0f, 180f, false, Offset(size.width * 0.46f, size.height * 0.33f), Size(size.width * 0.14f, size.height * 0.14f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.32f, size.height * 0.40f), Offset(size.width * 0.60f, size.height * 0.40f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.43f, size.height * 0.74f), Offset(size.width * 0.57f, size.height * 0.74f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.SelfImprovement -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.50f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.37f), Offset(size.width * 0.50f, size.height * 0.55f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.22f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.78f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                        drawArc(contentColor, 180f, 90f, false, Offset(size.width * 0.18f, size.height * 0.54f), Size(size.width * 0.32f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 270f, 90f, false, Offset(size.width * 0.50f, size.height * 0.54f), Size(size.width * 0.32f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                    OnboardingOptionIcon.Eye -> {
                        drawArc(contentColor, 200f, 140f, false, Offset(size.width * 0.14f, size.height * 0.28f), Size(size.width * 0.72f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(contentColor, 20f, 140f, false, Offset(size.width * 0.14f, size.height * 0.28f), Size(size.width * 0.72f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.50f, size.height * 0.50f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawCircle(contentColor, radius = size.minDimension * 0.05f, center = Offset(size.width * 0.50f, size.height * 0.50f))
                    }
                    OnboardingOptionIcon.TrendingDown -> {
                        drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.32f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.58f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.58f), Offset(size.width * 0.76f, size.height * 0.58f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.76f, size.height * 0.58f), Offset(size.width * 0.64f, size.height * 0.46f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.76f, size.height * 0.58f), Offset(size.width * 0.64f, size.height * 0.70f), strokeWidth, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.EventRepeat -> {
                        drawRoundRect(contentColor, Offset(size.width * 0.16f, size.height * 0.22f), Size(size.width * 0.68f, size.height * 0.62f), CornerRadius(4.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.40f), Offset(size.width * 0.84f, size.height * 0.40f), thinStroke, cap = StrokeCap.Round)
                        drawArc(contentColor, 30f, 280f, false, Offset(size.width * 0.34f, size.height * 0.46f), Size(size.width * 0.32f, size.height * 0.28f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                        drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.52f), Offset(size.width * 0.72f, size.height * 0.46f), thinStroke, cap = StrokeCap.Round)
                        drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.52f), Offset(size.width * 0.60f, size.height * 0.46f), thinStroke, cap = StrokeCap.Round)
                    }
                    OnboardingOptionIcon.PieChart -> {
                        drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawLine(contentColor, center, Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                        drawLine(contentColor, center, Offset(size.width * 0.79f, size.height * 0.66f), strokeWidth, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
