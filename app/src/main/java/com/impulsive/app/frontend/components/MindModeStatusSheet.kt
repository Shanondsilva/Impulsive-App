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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay

@Composable
fun MindModeStatusSheet(
    onDismissRequest: () -> Unit,
    onStartMindTask: () -> Unit,
    onViewProgress: () -> Unit,
    bottomNavReservedSpace: Dp = 104.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val deepText = colorScheme.onBackground
    val bodyText = colorScheme.onSurface
    val mutedText = colorScheme.onSurfaceVariant

    val lavender = colorScheme.primary
    val lavenderSoft = if (isDark) {
        colorScheme.primary.copy(alpha = 0.16f)
    } else {
        colorScheme.primary.copy(alpha = 0.28f)
    }
    val lavenderDeep = if (isDark) {
        colorScheme.primary
    } else {
        Color(0xFF685985)
    }
    val onLavender = if (isDark) Color(0xFF281D38) else Color(0xFF3A2E50)
    val cardSurface = colorScheme.surface.copy(
        alpha = if (isDark) 0.92f else 0.96f,
    )

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        ImpulsiveAmbientBackground(
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(
                    top = 8.dp,
                    bottom = bottomNavReservedSpace,
                ),
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
                        style = MaterialTheme.typography.headlineMedium,
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
                color = cardSurface,
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(34.dp),
                tonalElevation = 0.dp,
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
                color = cardSurface,
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(34.dp),
                tonalElevation = 0.dp,
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
    Surface(
        color = if (isDark) lavender.copy(alpha = 0.14f) else lavenderSoft.copy(alpha = 0.86f),
        shape = RoundedCornerShape(50),
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

private enum class MindModeExplainerVisual {
    Notice,
    Pause,
    Pivot,
    Understand,
}

private data class MindModeExplainerStep(
    val visual: MindModeExplainerVisual,
    val title: String,
    val description: String,
)

private fun Int.asMindModeStepIndex(stepCount: Int): Int {
    return ((this % stepCount) + stepCount) % stepCount
}

private data class MindModePathwayStage(
    val title: String,
    val description: String,
)

private data class MindModeSupportFamily(
    val title: String,
    val detail: String? = null,
)

private data class MindModePathwayModel(
    val notice: MindModePathwayStage,
    val privateCues: List<String>,
    val privateCuesDescription: String,
    val decision: MindModePathwayStage,
    val supportFamilies: List<MindModeSupportFamily>,
    val outcome: MindModePathwayStage,
    val learning: MindModePathwayStage,
)

private val MindModePathway = MindModePathwayModel(
    notice = MindModePathwayStage(
        title = "Notice",
        description = "Impulsive recognises the difficult moment.",
    ),
    privateCues = listOf("urge", "time", "pattern"),
    privateCuesDescription = "These cues stay on this device.",
    decision = MindModePathwayStage(
        title = "Mind suggests support",
        description = "One eligible option is selected.",
    ),
    supportFamilies = listOf(
        MindModeSupportFamily(title = "Short Pause"),
        MindModeSupportFamily(
            title = "Pivot Game",
            detail = "Reflex • Block • SkyStack • Rhythm",
        ),
        MindModeSupportFamily(title = "Reset Reading"),
        MindModeSupportFamily(title = "Moment Plan"),
    ),
    outcome = MindModePathwayStage(
        title = "Outcome recorded",
        description = "What happened is stored privately.",
    ),
    learning = MindModePathwayStage(
        title = "Private learning",
        description = "Later suggestions can adjust without changing the core rules.",
    ),
)

@Composable
private fun MindModeDecisionTreeVisual(
    deepText: Color,
    mutedText: Color,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    if (LocalDensity.current.fontScale >= 1.6f) {
        MindModeDecisionTreeList(
            deepText = deepText,
            mutedText = mutedText,
            lavender = lavender,
            lavenderSoft = lavenderSoft,
            lavenderDeep = lavenderDeep,
            isDark = isDark,
        )
        return
    }

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    // Seven sequential stages: notice, private cues, decision, the four
    // support-family nodes, outcome, private learning, return loop.
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
        val pathway = MindModePathway
        val w = maxWidth
        val centerX = w / 2
        val noticeY = 26.dp
        val cuesY = 76.dp
        val decisionY = 132.dp
        val branchTopY = 194.dp
        val branchBottomY = 238.dp
        val outcomeY = 300.dp
        val learningY = 348.dp
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
            // Notice -> private cues -> decision.
            seg(Offset(cx, noticeY.toPx() + 14.dp.toPx()), Offset(cx, cuesY.toPx() - 12.dp.toPx()), stages[1].value)
            seg(Offset(cx, cuesY.toPx() + 12.dp.toPx()), Offset(cx, decisionY.toPx() - 16.dp.toPx()), stages[2].value)

            // Decision fans out to every one of the four support families.
            val branchTargets = listOf(
                Offset(branchLeftX.toPx(), branchTopY.toPx() - 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchTopY.toPx() - 12.dp.toPx()),
                Offset(branchLeftX.toPx(), branchBottomY.toPx() - 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchBottomY.toPx() - 12.dp.toPx()),
            )
            branchTargets.forEachIndexed { index, target ->
                val p = ((stages[3].value * 4f) - index).coerceIn(0f, 1f)
                seg(Offset(cx, decisionY.toPx() + 16.dp.toPx()), target, p)
            }

            // Every support family converges into the same outcome node.
            val branchSources = listOf(
                Offset(branchLeftX.toPx(), branchTopY.toPx() + 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchTopY.toPx() + 12.dp.toPx()),
                Offset(branchLeftX.toPx(), branchBottomY.toPx() + 12.dp.toPx()),
                Offset(branchRightX.toPx(), branchBottomY.toPx() + 12.dp.toPx()),
            )
            val outcomeTarget = Offset(cx, outcomeY.toPx() - 12.dp.toPx())
            branchSources.forEach { source ->
                seg(source, outcomeTarget, stages[4].value)
            }

            // Outcome -> private learning.
            seg(Offset(cx, outcomeY.toPx() + 12.dp.toPx()), Offset(cx, learningY.toPx() - 12.dp.toPx()), stages[5].value)

            // Return loop: private learning visually points back toward the
            // decision area. It never rewrites the fixed rules directly.
            if (stages[6].value > 0f) {
                drawArc(
                    color = lineColor,
                    startAngle = 80f,
                    sweepAngle = -160f * stages[6].value,
                    useCenter = false,
                    topLeft = Offset((w * 0.62f).toPx(), decisionY.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        (w * 0.30f).toPx(),
                        (learningY - decisionY).toPx(),
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
            pillWidth: Dp = 160.dp,
        ) {
            if (progress <= 0f) return
            Box(
                modifier = Modifier
                    .offset(x = xCenter - pillWidth / 2, y = yCenter - 16.dp)
                    .width(pillWidth)
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
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        nodePill(pathway.notice.title, centerX, noticeY, stages[0].value)
        nodePill(pathway.privateCues[0], branchLeftX, cuesY, stages[1].value)
        nodePill(pathway.privateCues[1], centerX, cuesY, stages[1].value)
        nodePill(pathway.privateCues[2], branchRightX, cuesY, stages[1].value)
        nodePill(
            label = pathway.decision.title,
            xCenter = centerX,
            yCenter = decisionY,
            progress = stages[2].value,
            emphasised = true,
            scaleOverride = pulse,
            pillWidth = 200.dp,
        )
        nodePill(
            pathway.supportFamilies[0].title,
            branchLeftX,
            branchTopY,
            ((stages[3].value * 4f) - 0f).coerceIn(0f, 1f),
        )
        nodePill(
            pathway.supportFamilies[1].title,
            branchRightX,
            branchTopY,
            ((stages[3].value * 4f) - 1f).coerceIn(0f, 1f),
        )
        nodePill(
            pathway.supportFamilies[2].title,
            branchLeftX,
            branchBottomY,
            ((stages[3].value * 4f) - 2f).coerceIn(0f, 1f),
        )
        nodePill(
            pathway.supportFamilies[3].title,
            branchRightX,
            branchBottomY,
            ((stages[3].value * 4f) - 3f).coerceIn(0f, 1f),
        )
        nodePill(pathway.outcome.title, centerX, outcomeY, stages[4].value)
        nodePill(pathway.learning.title, centerX, learningY, stages[5].value)
    }
}

@Composable
private fun MindModeDecisionTreeList(
    deepText: Color,
    mutedText: Color,
    lavender: Color,
    lavenderSoft: Color,
    lavenderDeep: Color,
    isDark: Boolean,
) {
    val pathway = MindModePathway
    val decisionIndex = 2
    val steps = buildList {
        add(pathway.notice.title to pathway.notice.description)
        add(
            "Private cues" to
                "${pathway.privateCues.joinToString(" • ")}. ${pathway.privateCuesDescription}",
        )
        add(pathway.decision.title to pathway.decision.description)
        pathway.supportFamilies.forEach { family ->
            add(family.title to (family.detail ?: "One eligible support family."))
        }
        add(pathway.outcome.title to pathway.outcome.description)
        add(pathway.learning.title to pathway.learning.description)
    }
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        steps.forEachIndexed { index, (title, body) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    color = if (index == decisionIndex) lavender else lavenderSoft,
                    shape = CircleShape,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = if (index == decisionIndex) {
                            if (isDark) Color(0xFF1C1430) else MaterialTheme.colorScheme.onPrimary
                        } else {
                            lavenderDeep
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = deepText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = body,
                        color = mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun mindModeStepLottieRawRes(
    visual: MindModeExplainerVisual,
): Int = when (visual) {
    MindModeExplainerVisual.Notice -> R.raw.mind_trigger_lottie
    MindModeExplainerVisual.Pause -> R.raw.mind_pause_lottie
    MindModeExplainerVisual.Pivot -> R.raw.mind_pivot_lottie
    MindModeExplainerVisual.Understand -> R.raw.mind_control_lottie
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
                visual = MindModeExplainerVisual.Notice,
                title = "Notice",
                description = "A difficult moment is noticed before it becomes autopilot.",
            ),
            MindModeExplainerStep(
                visual = MindModeExplainerVisual.Pause,
                title = "Pause",
                description = "Sometimes the lightest support is a short pause before the next action.",
            ),
            MindModeExplainerStep(
                visual = MindModeExplainerVisual.Pivot,
                title = "Pivot",
                description = "Mind can suggest a Pivot Game, Reset Reading, or a prepared Moment Plan.",
            ),
            MindModeExplainerStep(
                visual = MindModeExplainerVisual.Understand,
                title = "Understand",
                description = "Your private outcome can help shape future suggestions.",
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
                visual = step.visual,
                lavender = lavender,
                lavenderSoft = lavenderSoft,
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
    visual: MindModeExplainerVisual,
    lavender: Color,
    lavenderSoft: Color,
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

    val lottieRawRes = mindModeStepLottieRawRes(visual)
    val lottieSize = if (visual == MindModeExplainerVisual.Notice) 64.dp else 78.dp
    val lottieYOffset = if (visual == MindModeExplainerVisual.Notice) 8.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (visual == MindModeExplainerVisual.Understand) {
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
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRawRes))
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .size(lottieSize)
                    .offset(y = lottieYOffset),
            )
        }
    }
}
