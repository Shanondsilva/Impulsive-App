package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics

@Composable
internal fun QuestionOptionGroup(
    areaMinHeight: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = areaMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
internal fun StartingPointSummaryLine(item: StartingPointSummaryItem) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.emphasized) Modifier.padding(top = 6.dp) else Modifier)
            .background(
                if (item.emphasized) OnboardingSelectedOptionSurface else Color.White,
                shape,
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (item.emphasized) OnboardingPrimary else OnboardingOptionPillBorder,
                ),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = item.title,
            color = OnboardingMutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.value,
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ReduceOptionChip(
    option: ReduceOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "reduce-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "reduce-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords -> chipCenter = coords.boundsInRoot().center }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(icon = option.icon, selected = selected, plain = true, plainIconSize = 16.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = option.label, color = OnboardingText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TriggerOptionChip(
    option: TriggerOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "trigger-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "trigger-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords -> chipCenter = coords.boundsInRoot().center }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(icon = option.icon, selected = selected, plain = true, plainIconSize = 16.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = option.label, color = OnboardingText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TimingOptionChip(
    option: TimingOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "timing-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "timing-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords -> chipCenter = coords.boundsInRoot().center }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(icon = option.icon, selected = selected, plain = true, plainIconSize = 16.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = option.label, color = OnboardingText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SomethingElseInput(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF514866).copy(alpha = 0.045f),
                            topLeft = Offset(0f, 3.dp.toPx()),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                        )
                    }
                    .background(ImpulsiveSurface, RoundedCornerShape(24.dp))
                    .border(
                        1.5.dp,
                        if (focused) OnboardingPrimary else OnboardingOutlineVariant,
                        RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = "Tell us more...", color = OnboardingMutedText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun ContinueButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Continue",
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.10f else 0.045f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "continue-button-shadow",
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 7.dp)
                .background(Color(0xFF514866).copy(alpha = shadowAlpha), RoundedCornerShape(28.dp)),
        )
        Button(
            onClick = { haptics.confirm(); onClick() },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = OnboardingPrimary,
                disabledContainerColor = OnboardingDisabledButton,
                disabledContentColor = OnboardingDisabledButtonText,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(text = label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
