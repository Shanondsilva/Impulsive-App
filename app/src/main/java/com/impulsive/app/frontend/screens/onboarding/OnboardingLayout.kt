package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class OnboardingStepUi(
    val currentStep: Int,
    val totalSteps: Int,
    val showBack: Boolean,
    val showSkip: Boolean,
    val infoText: String? = null,
)

internal enum class OnboardingFlowStep(
    val stepNumber: Int,
) {
    Personalization(stepNumber = 1),
    Reduction(stepNumber = 2),
    Triggers(stepNumber = 3),
    Timing(stepNumber = 4),
    WeekOne(stepNumber = 5),
    DailyRelapseCount(stepNumber = 6),
    ProtectionSetup(stepNumber = 7),
    StartingPoint(stepNumber = 8),
}

internal fun OnboardingFlowStep.toStepUi(
    showBack: Boolean = this != OnboardingFlowStep.Personalization,
    showSkip: Boolean = false,
    infoText: String? = null,
): OnboardingStepUi = OnboardingStepUi(
    currentStep = stepNumber,
    totalSteps = OnboardingTotalSteps,
    showBack = showBack,
    showSkip = showSkip,
    infoText = infoText,
)

internal object OnboardingLayoutDefaults {
    val HorizontalPadding: Dp = 24.dp
    val MaxContentWidth: Dp = 480.dp
    val BottomBarBottomPadding: Dp = 24.dp
    val ScrollBottomPadding: Dp = 32.dp
    val QuestionContentEndPadding: Dp = 24.dp
}

@Composable
internal fun OnboardingScreenShell(
    modifier: Modifier = Modifier,
    backgroundColors: List<Color> = listOf(
        Color(0xFFFFFEFC),
        Color(0xFFFCF8FD),
        Color(0xFFF6F2FA),
    ),
    useImePadding: Boolean = false,
    horizontalPadding: Dp = OnboardingLayoutDefaults.HorizontalPadding,
    maxContentWidth: Dp = OnboardingLayoutDefaults.MaxContentWidth,
    scrollBottomPadding: Dp = OnboardingLayoutDefaults.ScrollBottomPadding,
    bottomBarBottomPadding: Dp = OnboardingLayoutDefaults.BottomBarBottomPadding,
    stepUi: OnboardingStepUi? = null,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    content: @Composable ColumnScope.(compactHeight: Boolean) -> Unit,
) {
    val resolvedTopBar: (@Composable () -> Unit)? = topBar ?: stepUi?.let { step ->
        @Composable {
            OnboardingStepHeader(
                stepUi = step,
                onBack = onBack,
                onSkip = onSkip,
            )
        }
    }

    if (resolvedTopBar != null && !useImePadding) {
        OnboardingQuestionScaffoldShell(
            modifier = modifier,
            backgroundColors = backgroundColors,
            horizontalPadding = horizontalPadding,
            maxContentWidth = maxContentWidth,
            bottomBarBottomPadding = bottomBarBottomPadding,
            topBar = resolvedTopBar,
            bottomBar = bottomBar,
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = backgroundColors)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .then(if (useImePadding) Modifier.imePadding() else Modifier)
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compactHeight = maxHeight < 720.dp

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                resolvedTopBar?.invoke()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = scrollBottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content(compactHeight)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomBarBottomPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    bottomBar()
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepHeader(
    stepUi: OnboardingStepUi,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (stepUi.showBack && onBack != null) {
                OnboardingBackArrowButton(onClick = onBack)
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stepUi.totalSteps) { index ->
                val isActive = index + 1 == stepUi.currentStep
                val dotColor by animateColorAsState(
                    targetValue = if (isActive) {
                        OnboardingActiveDot
                    } else {
                        OnboardingInactiveDot
                    },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "onboarding-progress-dot-color",
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isActive) 10.dp else 8.dp)
                        .background(dotColor, CircleShape),
                )
            }
        }

        Row(
            modifier = Modifier
                .widthIn(min = 64.dp)
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stepUi.infoText?.let { infoText ->
                OnboardingInfoMark(text = infoText)
            }
            if (stepUi.showSkip && onSkip != null) {
                Text(
                    text = "Skip",
                    color = OnboardingHeaderText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingInfoMark(text: String) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(OnboardingInactiveDot.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { open = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            color = OnboardingHeaderText,
            fontSize = 15.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(text = "Got it")
                }
            },
            text = {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            },
        )
    }
}

@Composable
private fun OnboardingBackArrowButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                color = OnboardingHeaderText,
                start = Offset(size.width * 0.64f, size.height * 0.18f),
                end = Offset(size.width * 0.28f, size.height * 0.50f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = OnboardingHeaderText,
                start = Offset(size.width * 0.28f, size.height * 0.50f),
                end = Offset(size.width * 0.64f, size.height * 0.82f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = OnboardingHeaderText,
                start = Offset(size.width * 0.30f, size.height * 0.50f),
                end = Offset(size.width * 0.86f, size.height * 0.50f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun OnboardingQuestionScaffoldShell(
    modifier: Modifier,
    backgroundColors: List<Color>,
    horizontalPadding: Dp,
    maxContentWidth: Dp,
    bottomBarBottomPadding: Dp,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable ColumnScope.(compactHeight: Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = backgroundColors)),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compactHeight = maxHeight < 720.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = horizontalPadding)
                            .padding(bottom = bottomBarBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = maxContentWidth)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            bottomBar()
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = horizontalPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = maxContentWidth)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        topBar()
                        content(compactHeight)
                        Spacer(modifier = Modifier.height(OnboardingLayoutDefaults.QuestionContentEndPadding))
                    }
                }
            }
        }
    }
}

private const val OnboardingTotalSteps = 8
private val OnboardingActiveDot = Color(0xFF635880)
private val OnboardingInactiveDot = Color(0xFFE6E1E5)
private val OnboardingHeaderText = Color(0xFF635880)
