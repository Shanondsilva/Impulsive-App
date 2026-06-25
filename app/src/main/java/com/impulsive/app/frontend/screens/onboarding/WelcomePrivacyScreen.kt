package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import kotlinx.coroutines.delay

@Composable
fun WelcomePrivacyScreen(
    initialName: String = "",
    initialAvatarId: String = "avatar_01",
    onBeginSetup: (name: String, avatarId: String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedAvatarId by remember(initialAvatarId) { mutableStateOf(AvatarStyle.fromId(initialAvatarId).id) }
    val reducedMotion = rememberReducedMotion()
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val canBegin = name.trim().isNotEmpty()

    OnboardingScreenShell(
        backgroundColors = listOf(
            Color(0xFFFFFEFC),
            Color(0xFFFDF8FC),
            Color(0xFFF7F2F6),
        ),
        useImePadding = true,
        scrollBottomPadding = 34.dp,
        stepUi = OnboardingFlowStep.Personalization.toStepUi(showBack = false),
        bottomBar = {
            BeginSetupButton(
                enabled = canBegin,
                onClick = {
                    haptics.confirm()
                    onBeginSetup(
                        name.trim(),
                        selectedAvatarId,
                    )
                },
            )
        },
    ) { compactHeight ->
        val metrics = rememberWelcomeResponsiveMetrics(compactHeight = compactHeight)

        Spacer(modifier = Modifier.height(metrics.topSpacing))

        OnboardingLogoVisual(
            reducedMotion = reducedMotion,
            scale = OnboardingLogoScale.Large,
        )

        Spacer(modifier = Modifier.height(metrics.logoToPrivacySpacing))

        Text(
            text = "Private by design.",
            color = SereneText,
            fontSize = metrics.privacyTitleFontSize,
            lineHeight = metrics.privacyTitleLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(metrics.privacyTextSpacing))

        Text(
            text = "Your setup stays on your device.",
            color = SereneMutedText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(metrics.privacyToTitleSpacing))

        Text(
            text = "Let's personalize your space.",
            color = SereneText,
            fontSize = metrics.titleFontSize,
            lineHeight = metrics.titleLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

        Text(
            text = "What should Impulsive call you?",
            color = SereneMutedText,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f),
        )

        Spacer(modifier = Modifier.height(metrics.subtitleToInputSpacing))

        SoftNameInput(
            value = name,
            onValueChange = { name = it },
        )

        Spacer(modifier = Modifier.height(metrics.inputToAvatarTitleSpacing))



        Spacer(modifier = Modifier.height(metrics.avatarTitleToPickerSpacing))

        AvatarPicker(
            selectedAvatarId = selectedAvatarId,
            onAvatarSelected = { selectedAvatarId = it.id },
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
private fun rememberWelcomeResponsiveMetrics(
    compactHeight: Boolean,
): WelcomeResponsiveMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 380 -> WelcomeResponsiveMetrics(
            topSpacing = if (compactHeight) 12.dp else 22.dp,
            logoToPrivacySpacing = 14.dp,
            privacyTitleFontSize = 19.sp,
            privacyTitleLineHeight = 25.sp,
            privacyTextSpacing = 4.dp,
            privacyToTitleSpacing = if (compactHeight) 18.dp else 24.dp,
            titleFontSize = 25.sp,
            titleLineHeight = 31.sp,
            titleToSubtitleSpacing = 8.dp,
            subtitleToInputSpacing = 14.dp,
            inputToAvatarTitleSpacing = if (compactHeight) 18.dp else 24.dp,
            avatarTitleToPickerSpacing = 12.dp,
        )
        widthDp < 430 -> WelcomeResponsiveMetrics(
            topSpacing = if (compactHeight) 14.dp else 26.dp,
            logoToPrivacySpacing = 16.dp,
            privacyTitleFontSize = 20.sp,
            privacyTitleLineHeight = 26.sp,
            privacyTextSpacing = 5.dp,
            privacyToTitleSpacing = if (compactHeight) 20.dp else 26.dp,
            titleFontSize = 27.sp,
            titleLineHeight = 33.sp,
            titleToSubtitleSpacing = 9.dp,
            subtitleToInputSpacing = 16.dp,
            inputToAvatarTitleSpacing = if (compactHeight) 20.dp else 26.dp,
            avatarTitleToPickerSpacing = 14.dp,
        )
        else -> WelcomeResponsiveMetrics(
            topSpacing = if (compactHeight) 18.dp else 34.dp,
            logoToPrivacySpacing = 18.dp,
            privacyTitleFontSize = 21.sp,
            privacyTitleLineHeight = 27.sp,
            privacyTextSpacing = 5.dp,
            privacyToTitleSpacing = if (compactHeight) 24.dp else 30.dp,
            titleFontSize = 28.sp,
            titleLineHeight = 34.sp,
            titleToSubtitleSpacing = 10.dp,
            subtitleToInputSpacing = 18.dp,
            inputToAvatarTitleSpacing = if (compactHeight) 24.dp else 30.dp,
            avatarTitleToPickerSpacing = 16.dp,
        )
    }
}

private data class WelcomeResponsiveMetrics(
    val topSpacing: Dp,
    val logoToPrivacySpacing: Dp,
    val privacyTitleFontSize: TextUnit,
    val privacyTitleLineHeight: TextUnit,
    val privacyTextSpacing: Dp,
    val privacyToTitleSpacing: Dp,
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val titleToSubtitleSpacing: Dp,
    val subtitleToInputSpacing: Dp,
    val inputToAvatarTitleSpacing: Dp,
    val avatarTitleToPickerSpacing: Dp,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoftNameInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val borderColor = if (focused) ImpulsivePsychological else Color(0xFFE6DFEF)

    LaunchedEffect(focused) {
        if (focused) {
            delay(260)
            bringIntoViewRequester.bringIntoView()
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color(0xFF211C33),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .height(62.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF3C3843).copy(alpha = 0.055f),
                            topLeft = Offset(0f, 4.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                        )
                    }
                    .background(Color(0xFFFFFBFE), RoundedCornerShape(24.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Your name",
                        color = Color(0xFF79757E),
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun AvatarPicker(
    selectedAvatarId: String,
    onAvatarSelected: (AvatarStyle) -> Unit,
    reducedMotion: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAvatar = AvatarStyle.fromId(selectedAvatarId)
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 240, easing = FastOutSlowInEasing),
        label = "avatar-picker-chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF3C3843).copy(alpha = 0.045f),
                    topLeft = Offset(0f, 5.dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                )
            }
            .background(Color(0xFFFFFBFE).copy(alpha = 0.96f), RoundedCornerShape(28.dp))
            .border(1.dp, Color(0xFFE8DFEF), RoundedCornerShape(28.dp))
            .animateContentSize(
                animationSpec = tween(durationMillis = if (reducedMotion) 0 else 260, easing = FastOutSlowInEasing),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        haptics.light()
                        expanded = !expanded
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImageCircle(
                avatar = selectedAvatar,
                selected = true,
                size = 58.dp,
                imageSize = 48.dp,
                contentDescription = "Selected ${selectedAvatar.contentDescription}",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Choose an avatar for you",
                    color = SereneText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )

            }

            AvatarPickerChevron(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 18.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarStyle.entries.chunked(AvatarGridColumns).forEach { rowAvatars ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowAvatars.forEach { avatar ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                AvatarOption(
                                    avatar = avatar,
                                    selected = selectedAvatar.id == avatar.id,
                                    onClick = {
                                        if (selectedAvatar.id != avatar.id) {
                                            haptics.light()
                                            onAvatarSelected(avatar)
                                        }
                                    },
                                )
                            }
                        }
                        repeat(AvatarGridColumns - rowAvatars.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    avatar: AvatarStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AvatarImageCircle(
            avatar = avatar,
            selected = selected,
            size = 78.dp,
            imageSize = 66.dp,
            contentDescription = avatar.contentDescription,
        )
    }
}

@Composable
private fun AvatarImageCircle(
    avatar: AvatarStyle,
    selected: Boolean,
    size: Dp,
    imageSize: Dp,
    contentDescription: String,
) {
    val backgroundColor = if (selected) {
        AvatarSelectionAccent.copy(alpha = 0.34f)
    } else {
        avatar.backgroundColor
    }
    val borderColor = if (selected) AvatarSelectionAccent else Color.Transparent

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(if (selected) 2.dp else 0.dp, borderColor, CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = avatar.drawableResId),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(imageSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        if (selected) {
            AvatarCheckBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp),
            )
        }
    }
}

@Composable
private fun AvatarPickerChevron(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawLine(
            color = Color(0xFF635880),
            start = Offset(size.width * 0.28f, size.height * 0.42f),
            end = Offset(size.width * 0.50f, size.height * 0.62f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF635880),
            start = Offset(size.width * 0.50f, size.height * 0.62f),
            end = Offset(size.width * 0.72f, size.height * 0.42f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun AvatarCheckBadge(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawCircle(color = Color(0xFFFFFEFC))
        drawCircle(color = AvatarSelectionAccent, radius = size.minDimension * 0.42f)
        drawLine(
            color = Color(0xFFFFFFFF),
            start = Offset(size.width * 0.30f, size.height * 0.52f),
            end = Offset(size.width * 0.44f, size.height * 0.66f),
            strokeWidth = 1.9.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFFFFFFF),
            start = Offset(size.width * 0.44f, size.height * 0.66f),
            end = Offset(size.width * 0.72f, size.height * 0.36f),
            strokeWidth = 1.9.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BeginSetupButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val glowAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.10f else 0.045f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "begin-glow",
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 7.dp)
                .background(
                    color = Color(0xFF514866).copy(alpha = glowAlpha),
                    shape = RoundedCornerShape(28.dp),
                ),
        )

        Button(
            onClick = {
                haptics.confirm()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = Color(0xFF635880),
                disabledContainerColor = Color(0xFFE8DDFF),
                disabledContentColor = Color(0xFF9C93A8),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = "Begin setup",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val SereneText = Color(0xFF1C1B1E)
private val SereneMutedText = Color(0xFF48454E)
private val AvatarSelectionAccent = Color(0xFFD0C3F1)
private const val AvatarGridColumns = 3
