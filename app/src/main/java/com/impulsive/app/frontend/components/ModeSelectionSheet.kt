package com.impulsive.app.frontend.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ModeSelectionSheet(
    onDismissRequest: () -> Unit,
    onOpenMindMode: () -> Unit = {},
    onOpenBodyMode: () -> Unit = {},
    onOpenSoulMode: () -> Unit = {},
    bottomNavReservedSpace: Dp = 104.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mindAccent = if (isDark) Color(0xFFF2ECFF) else Color(0xFF66517F)
    val mindBackground = if (isDark) Color(0xFF332642) else Color(0xFFF2E9FB)
    val mindBorder = if (isDark) Color(0xFFD0C3F1).copy(alpha = 0.62f) else Color(0xFFD0C3F1).copy(alpha = 0.88f)
    val bodyAccent = if (isDark) Color(0xFFD9F0FF) else Color(0xFF4E7191)
    val bodyBackground = if (isDark) Color(0xFF1D2B36) else Color(0xFFE9F5FF)
    val bodyBorder = if (isDark) Color(0xFFBDE0FE).copy(alpha = 0.38f) else Color(0xFFBDE0FE).copy(alpha = 0.9f)
    val soulAccent = if (isDark) Color(0xFFFFF4C7) else Color(0xFF8B7242)
    val soulBackground = if (isDark) Color(0xFF332D20) else Color(0xFFFFF7D8)
    val soulBorder = if (isDark) Color(0xFFFEF1AB).copy(alpha = 0.42f) else Color(0xFFFEF1AB).copy(alpha = 0.92f)
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestClose() {
        scope.launch {
            visible = false
            delay(180)
            onDismissRequest()
        }
    }

    fun requestOpenMode(onOpenMode: () -> Unit) {
        visible = false
        onDismissRequest()
        onOpenMode()
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    val transition = updateTransition(targetState = visible, label = "mode_bubble_selector")
    val scrimAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160, easing = FastOutSlowInEasing) },
        label = "mode_bubble_scrim_alpha",
    ) { open ->
        if (open) 1f else 0f
    }
    val bubbleAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 190, easing = FastOutSlowInEasing) },
        label = "mode_bubble_alpha",
    ) { open ->
        if (open) 1f else 0f
    }
    val bubbleScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
        label = "mode_bubble_scale",
    ) { open ->
        if (open) 1f else 0.58f
    }
    val mindX by transition.animateDp(
        transitionSpec = { tween(durationMillis = 230, easing = FastOutSlowInEasing) },
        label = "mind_bubble_x",
    ) { open ->
        if (open) (-94).dp else 0.dp
    }
    val mindY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 230, easing = FastOutSlowInEasing) },
        label = "mind_bubble_y",
    ) { open ->
        if (open) (-24).dp else 0.dp
    }
    val bodyY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
        label = "body_bubble_y",
    ) { open ->
        if (open) (-118).dp else 0.dp
    }
    val soulX by transition.animateDp(
        transitionSpec = { tween(durationMillis = 230, easing = FastOutSlowInEasing) },
        label = "soul_bubble_x",
    ) { open ->
        if (open) 94.dp else 0.dp
    }
    val soulY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 230, easing = FastOutSlowInEasing) },
        label = "soul_bubble_y",
    ) { open ->
        if (open) (-24).dp else 0.dp
    }
    BackHandler(onBack = ::requestClose)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    (if (isDark) Color(0x660A0710) else Color(0x44FFFFFF)).copy(alpha = scrimAlpha),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { requestClose() },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ModeBubble(
                title = "Mind",
                label = "Active",
                symbol = "\u2726",
                locked = false,
                background = mindBackground,
                border = mindBorder,
                accent = mindAccent,
                reducedMotion = reducedMotion,
                phaseOffsetMillis = 0,
                modifier = Modifier
                    .offset(x = mindX, y = mindY)
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    },
                onClick = { requestOpenMode(onOpenMindMode) },
            )

            ModeBubble(
                title = "Body",
                label = "Locked",
                symbol = null,
                locked = true,
                background = bodyBackground,
                border = bodyBorder,
                accent = bodyAccent,
                reducedMotion = reducedMotion,
                phaseOffsetMillis = 1_650,
                modifier = Modifier
                    .offset(x = 0.dp, y = bodyY)
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    },
                onClick = { requestOpenMode(onOpenBodyMode) },
            )

            ModeBubble(
                title = "Soul",
                label = "Locked",
                symbol = null,
                locked = true,
                background = soulBackground,
                border = soulBorder,
                accent = soulAccent,
                reducedMotion = reducedMotion,
                phaseOffsetMillis = 3_300,
                modifier = Modifier
                    .offset(x = soulX, y = soulY)
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    },
                onClick = { requestOpenMode(onOpenSoulMode) },
            )
        }
    }
}

@Composable
fun BodyModeLockedSheet(
    onDismissRequest: () -> Unit,
    bottomNavReservedSpace: Dp = 104.dp,
) {
    LockedModePreviewSheet(
        title = "Body Mode",
        subtitle = "Locked preview",
        badge = "LOCKED",
        symbol = "B",
        sectionTitle = "HOW BODY MODE WILL WORK",
        steps = listOf(
            "Move" to "Short physical pivots help burn off restless urge energy before it turns into action.",
            "Reset" to "Body Mode will guide simple resets that bring your nervous system back down.",
            "Return" to "The loop closes when you return to plan with your body calmer and your choice intact.",
        ),
        footer = "Body Mode is locked for now.",
        accent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFD9F0FF) else Color(0xFF4E7191),
        soft = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1D2B36) else Color(0xFFE9F5FF),
        onDismissRequest = onDismissRequest,
        bottomNavReservedSpace = bottomNavReservedSpace,
    )
}

@Composable
fun SoulModeLockedSheet(
    onDismissRequest: () -> Unit,
    bottomNavReservedSpace: Dp = 104.dp,
) {
    LockedModePreviewSheet(
        title = "Soul Mode",
        subtitle = "Locked preview",
        badge = "LOCKED",
        symbol = "S",
        sectionTitle = "HOW SOUL MODE WILL WORK",
        steps = listOf(
            "Anchor" to "Soul Mode will help you reconnect with the deeper reason you chose this path.",
            "Reflect" to "Gentle prompts will turn a difficult moment into meaning instead of momentum.",
            "Renew" to "The loop closes when you return with purpose, not pressure.",
        ),
        footer = "Soul Mode is locked for now.",
        accent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFF4C7) else Color(0xFF8B7242),
        soft = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF332D20) else Color(0xFFFFF7D8),
        onDismissRequest = onDismissRequest,
        bottomNavReservedSpace = bottomNavReservedSpace,
    )
}

@Composable
private fun LockedModePreviewSheet(
    title: String,
    subtitle: String,
    badge: String,
    symbol: String,
    sectionTitle: String,
    steps: List<Pair<String, String>>,
    footer: String,
    accent: Color,
    soft: Color,
    onDismissRequest: () -> Unit,
    bottomNavReservedSpace: Dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenBackground = if (isDark) Color(0xFF11161A) else Color(0xFFFBF8FE)
    val deepText = if (isDark) Color(0xFFFFFBFF) else Color(0xFF15121D)
    val bodyText = if (isDark) Color(0xFFEFE7FA) else Color(0xFF342D3F)
    val mutedText = if (isDark) Color(0xFFCFC4DD) else Color(0xFF7B7384)
    val cardColor = if (isDark) Color(0xFF171D22) else Color(0xFFFFFBFF)
    val screenBrush = Brush.verticalGradient(listOf(screenBackground, screenBackground))

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBrush),
    ) {
        ImpulsiveAmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 16.dp, bottom = bottomNavReservedSpace),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(soft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = deepText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = bodyText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = soft,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.46f else 0.56f)),
                    ) {
                        Text(
                            text = badge,
                            color = accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = symbol,
                            color = accent,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = sectionTitle,
                color = mutedText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = cardColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.38f else 0.22f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                    steps.forEachIndexed { index, step ->
                        LockedModeStep(
                            number = index + 1,
                            title = step.first,
                            description = step.second,
                            accent = accent,
                            soft = soft,
                            deepText = deepText,
                            bodyText = bodyText,
                        )
                        if (index != steps.lastIndex) {
                            Spacer(modifier = Modifier.height(22.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = footer,
                color = mutedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LockedModeStep(
    number: Int,
    title: String,
    description: String,
    accent: Color,
    soft: Color,
    deepText: Color,
    bodyText: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(soft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = deepText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = bodyText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ModeBubble(
    title: String,
    label: String,
    symbol: String?,
    locked: Boolean,
    background: Color,
    border: Color,
    accent: Color,
    reducedMotion: Boolean,
    phaseOffsetMillis: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current.density
    val floatTransition = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "${title}_mode_levitation")
    }
    val verticalTranslationDp = floatTransition?.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5_200,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = phaseOffsetMillis,
                offsetType = StartOffsetType.FastForward,
            ),
        ),
        label = "${title}_mode_translation_y",
    )?.value ?: 0f
    Surface(
        color = background,
        shape = CircleShape,
        border = BorderStroke(1.dp, border),
        tonalElevation = 6.dp,
        modifier = modifier
            .graphicsLayer {
                translationY = verticalTranslationDp * density
            }
            .size(96.dp)
            .impulsiveGlowShadow(
                enabled = !locked,
                shape = CircleShape,
                glowColor = Color(0xFFD0C3F1),
                elevation = 14.dp,
                ambientAlpha = 0.16f,
                spotAlpha = 0.22f,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $label"
                selected = !locked
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (locked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accent.copy(alpha = 0.82f),
                        modifier = Modifier.size(23.dp),
                    )
                } else {
                    Text(
                        text = symbol ?: "",
                        color = accent,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = title,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Text(
                text = label,
                color = accent.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
