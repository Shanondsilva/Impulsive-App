package com.impulsive.app.frontend.components

import androidx.activity.compose.BackHandler
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.impulsive.app.R
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import kotlinx.coroutines.delay

@Composable
fun MindModeStatusSheet(
    onDismissRequest: () -> Unit,
    onStartMindTask: () -> Unit,
    onViewProgress: () -> Unit,
    bottomNavReservedSpace: Dp = 104.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenBackground = if (isDark) Color(0xFF11161A) else Color(0xFFFBF8FE)
    val deepText = if (isDark) Color(0xFFFFFBFF) else Color(0xFF15121D)
    val bodyText = if (isDark) Color(0xFFEFE7FA) else Color(0xFF342D3F)
    val mutedText = if (isDark) Color(0xFFCFC4DD) else Color(0xFF7B7384)
    val lavender = if (isDark) ImpulsivePsychological else ImpulsivePsychological
    val lavenderSoft = if (isDark) Color(0xFF332642) else Color(0xFFEFE6FA)
    val lavenderDeep = if (isDark) Color(0xFFF2ECFF) else Color(0xFF685985)
    val onLavender = if (isDark) Color(0xFF281D38) else Color(0xFF3A2E50)
    val screenBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF11161A),
                Color(0xFF11161A),
                Color(0xFF11161A),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                screenBackground,
                screenBackground,
            ),
        )
    }

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBrush),
    ) {
        ImpulsiveAmbientBackground(lightweight = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 8.dp, bottom = bottomNavReservedSpace),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mind Mode",
                        color = deepText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ActiveMindModeBadge(
                        isDark = isDark,
                        lavender = lavender,
                        lavenderSoft = lavenderSoft,
                        lavenderDeep = lavenderDeep,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your active root mode",
                        color = bodyText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(lavender),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u2726",
                        color = onLavender,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "HOW MIND MODE WORKS",
                color = mutedText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF),
                shape = RoundedCornerShape(34.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) ImpulsivePsychological.copy(alpha = 0.38f) else Color(0xFFE9DFF2),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MindModeExplainerCarousel(
                    deepText = deepText,
                    bodyText = bodyText,
                    mutedText = mutedText,
                    lavender = lavender,
                    lavenderSoft = lavenderSoft,
                    lavenderDeep = lavenderDeep,
                    isDark = isDark,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF),
                shape = RoundedCornerShape(34.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) ImpulsivePsychological.copy(alpha = 0.38f) else Color(0xFFE9DFF2),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MindModeDecisionTreeVisual(
                    deepText = deepText,
                    mutedText = mutedText,
                    lavender = lavender,
                    lavenderSoft = lavenderSoft,
                    lavenderDeep = lavenderDeep,
                    isDark = isDark,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = lavender,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onStartMindTask() },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = onLavender,
                        modifier = Modifier.size(27.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Start 90-second Mind Pivot",
                        color = onLavender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "View today's progress",
                color = lavenderDeep,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onViewProgress() },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Mind Mode stays active as the root mode for this loop.",
                color = mutedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActiveMindModeBadge(
    isDark: Boolean,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
) {
    val badgePulse = rememberInfiniteTransition(label = "active_mind_badge_pulse")
    val borderAlpha by badgePulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "active_mind_badge_border_alpha",
    )
    Surface(
        color = if (isDark) lavender.copy(alpha = 0.14f) else lavenderSoft.copy(alpha = 0.86f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.dp,
            if (isDark) lavender.copy(alpha = borderAlpha) else ImpulsivePsychological.copy(alpha = borderAlpha),
        ),
    ) {
        Text(
            text = "ACTIVE",
            color = if (isDark) Color(0xFFF2ECFF) else lavenderDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private data class MindModeExplainerStep(
    val title: String,
    val description: String,
)

private fun Int.asMindModeStepIndex(stepCount: Int): Int {
    return ((this % stepCount) + stepCount) % stepCount
}

@Composable
private fun MindModeDecisionTreeVisual(
    deepText: Color,
    mutedText: Color,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    // Seven sequential stages: trigger, pause, state chips, decision node,
    // task branches, complete and reward, learn loop.
    val stages = remember { List(7) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            stages.forEach { it.snapTo(1f) }
            return@LaunchedEffect
        }
        stages.forEach { stage ->
            stage.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
            delay(90)
        }
    }
    val pulseTransition = rememberInfiniteTransition(label = "MindTreePulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reducedMotion) 1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "MindTreePulseScale",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(388.dp)
            .padding(horizontal = 12.dp),
    ) {
        val w = maxWidth
        val centerX = w / 2
        val triggerY = 26.dp
        val pauseY = 72.dp
        val chipsY = 118.dp
        val decisionY = 170.dp
        val branchTopY = 228.dp
        val branchBottomY = 268.dp
        val branchThirdY = 308.dp
        val rewardY = 354.dp
        val branchLeftX = w * 0.26f
        val branchRightX = w * 0.74f
        val lineColor = lavender.copy(alpha = if (isDark) 0.55f else 0.45f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = centerX.toPx()
            fun seg(from: Offset, to: Offset, progress: Float) {
                if (progress <= 0f) return
                val end = Offset(
                    from.x + (to.x - from.x) * progress,
                    from.y + (to.y - from.y) * progress,
                )
                drawLine(
                    color = lineColor,
                    start = from,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            seg(Offset(cx, triggerY.toPx() + 14.dp.toPx()), Offset(cx, pauseY.toPx() - 14.dp.toPx()), stages[1].value)
            seg(Offset(cx, pauseY.toPx() + 14.dp.toPx()), Offset(cx, chipsY.toPx() - 12.dp.toPx()), stages[2].value)
            seg(Offset(cx, chipsY.toPx() + 12.dp.toPx()), Offset(cx, decisionY.toPx() - 16.dp.toPx()), stages[3].value)
            val branchTargets = listOf(
                Offset(branchLeftX.toPx(), branchTopY.toPx() - 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchTopY.toPx() - 12.dp.toPx()),
                Offset(branchLeftX.toPx(), branchBottomY.toPx() - 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchBottomY.toPx() - 12.dp.toPx()),
                Offset(cx, branchThirdY.toPx() - 12.dp.toPx()),
            )
            branchTargets.forEachIndexed { index, target ->
                val p = ((stages[4].value * 5f) - index).coerceIn(0f, 1f)
                seg(Offset(cx, decisionY.toPx() + 16.dp.toPx()), target, p)
            }
            seg(
                Offset(branchLeftX.toPx(), branchBottomY.toPx() + 12.dp.toPx()),
                Offset((w * 0.34f).toPx(), rewardY.toPx() - 12.dp.toPx()),
                stages[5].value,
            )
            seg(
                Offset(branchRightX.toPx(), branchBottomY.toPx() + 12.dp.toPx()),
                Offset((w * 0.66f).toPx(), rewardY.toPx() - 12.dp.toPx()),
                stages[5].value,
            )
            seg(
                Offset(cx, branchThirdY.toPx() + 12.dp.toPx()),
                Offset(cx, rewardY.toPx() - 12.dp.toPx()),
                stages[5].value,
            )
            if (stages[6].value > 0f) {
                drawArc(
                    color = lineColor,
                    startAngle = 80f,
                    sweepAngle = -160f * stages[6].value,
                    useCenter = false,
                    topLeft = Offset((w * 0.62f).toPx(), decisionY.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        (w * 0.30f).toPx(),
                        (rewardY - decisionY).toPx(),
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }

        @Composable
        fun nodePill(
            label: String,
            xCenter: Dp,
            yCenter: Dp,
            progress: Float,
            emphasised: Boolean = false,
            scaleOverride: Float = 1f,
        ) {
            if (progress <= 0f) return
            Box(
                modifier = Modifier
                    .offset(x = xCenter - 80.dp, y = yCenter - 16.dp)
                    .width(160.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = if (emphasised) lavender else lavenderSoft,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.graphicsLayer {
                        val appear = 0.85f + (0.15f * progress)
                        scaleX = appear * scaleOverride
                        scaleY = appear * scaleOverride
                        alpha = progress
                    },
                ) {
                    Text(
                        text = label,
                        color = if (emphasised) {
                            if (isDark) Color(0xFF1C1430) else Color.White
                        } else {
                            deepText
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        nodePill("Trigger", centerX, triggerY, stages[0].value)
        nodePill("Pause", centerX, pauseY, stages[1].value)
        nodePill("urge", w * 0.26f, chipsY, stages[2].value)
        nodePill("time", centerX, chipsY, stages[2].value)
        nodePill("pattern", w * 0.74f, chipsY, stages[2].value)
        nodePill(
            label = "Mind picks a task",
            xCenter = centerX,
            yCenter = decisionY,
            progress = stages[3].value,
            emphasised = true,
            scaleOverride = pulse,
        )
        nodePill("Reflex Override", branchLeftX, branchTopY, ((stages[4].value * 5f) - 0f).coerceIn(0f, 1f))
        nodePill("Block Cascade", branchRightX, branchTopY, ((stages[4].value * 5f) - 1f).coerceIn(0f, 1f))
        nodePill("SkyStack", branchLeftX, branchBottomY, ((stages[4].value * 5f) - 2f).coerceIn(0f, 1f))
        nodePill("Reset Read", branchRightX, branchBottomY, ((stages[4].value * 5f) - 3f).coerceIn(0f, 1f))
        nodePill("Piano steps", centerX, branchThirdY, ((stages[4].value * 5f) - 4f).coerceIn(0f, 1f))
        nodePill("Complete", w * 0.30f, rewardY, stages[5].value)
        nodePill("Wait cut + LP", w * 0.70f, rewardY, stages[5].value)
        nodePill("Learn", w * 0.94f, (decisionY + rewardY) / 2, stages[6].value)
    }
}

private fun mindModeStepLottieRawRes(stepTitle: String): Int? {
    return when (stepTitle) {
        "Trigger" -> R.raw.mind_trigger_lottie
        "Pause" -> R.raw.mind_pause_lottie
        "Pivot" -> R.raw.mind_pivot_lottie
        "Control" -> R.raw.mind_control_lottie
        else -> null
    }
}

@Composable
private fun MindModeExplainerCarousel(
    deepText: Color,
    bodyText: Color,
    mutedText: Color,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    val steps = remember {
        listOf(
            MindModeExplainerStep(
                title = "Trigger",
                description = "The moment the urge starts. Impulsive catches the spark before it becomes autopilot.",
            ),
            MindModeExplainerStep(
                title = "Pause",
                description = "Slow the moment down. Create space before the next action.",
            ),
            MindModeExplainerStep(
                title = "Pivot",
                description = "Redirect the urge into a safer action that keeps you in control.",
            ),
            MindModeExplainerStep(
                title = "Control",
                description = "The loop closes. You return to yourself with control restored.",
            ),
        )
    }

    val initialPage = Int.MAX_VALUE / 2 - ((Int.MAX_VALUE / 2) % steps.size)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { Int.MAX_VALUE },
    )

    LaunchedEffect(pagerState) {
        while (true) {
            delay(4200)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) { page ->
            val step = steps[page.asMindModeStepIndex(steps.size)]

            MindModeExplainerPage(
                step = step,
                deepText = deepText,
                bodyText = bodyText,
                mutedText = mutedText,
                lavender = lavender,
                lavenderSoft = lavenderSoft,
                lavenderDeep = lavenderDeep,
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedStepIndex = pagerState.currentPage.asMindModeStepIndex(steps.size)

            steps.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == selectedStepIndex) 11.dp else 9.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == selectedStepIndex) {
                                lavenderDeep
                            } else {
                                mutedText.copy(alpha = 0.28f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun MindModeExplainerPage(
    step: MindModeExplainerStep,
    deepText: Color,
    bodyText: Color,
    mutedText: Color,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            MindModeSafeStepVisual(
                stepTitle = step.title,
                lavender = lavender,
                lavenderSoft = lavenderSoft,
                lavenderDeep = lavenderDeep,
                isDark = isDark,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = step.title,
            color = deepText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = step.description,
            color = bodyText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun MindModeSafeStepVisual(
    stepTitle: String,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mind_step_visual")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mind_step_pulse",
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mind_step_float",
    )

    val visualBackground = if (isDark) {
        Color(0xFF202832)
    } else {
        Color(0xFFF8F2FF)
    }
    val symbolVerticalOffset = if (stepTitle == "Trigger") 8.dp else 0.dp
    val lottieRawRes = mindModeStepLottieRawRes(stepTitle)
    val lottieSize = if (stepTitle == "Trigger") 64.dp else 78.dp
    val lottieYOffset = if (stepTitle == "Trigger") 8.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(visualBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (stepTitle == "Control") {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .background(lavender.copy(alpha = if (isDark) 0.10f else 0.16f)),
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    translationY = floatOffset
                }
                .size(96.dp)
                .clip(CircleShape)
                .background(lavenderSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (lottieRawRes != null) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRawRes))
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier
                        .size(lottieSize)
                        .offset(y = lottieYOffset),
                )
            } else {
                Text(
                    text = when (stepTitle) {
                        "Trigger" -> "!"
                        "Pause" -> "\u2161"
                        "Pivot" -> "\u21B7"
                        else -> "\u2726"
                    },
                    color = lavenderDeep,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = symbolVerticalOffset),
                )
            }
        }
    }
}
