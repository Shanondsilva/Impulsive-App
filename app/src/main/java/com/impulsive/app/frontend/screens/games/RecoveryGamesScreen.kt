package com.impulsive.app.frontend.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological

private data class RecoveryGamesColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val mutedText: Color,
    val accentText: Color,
    val border: Color,
    val shadow: Color,
    val chipBackground: Color,
)

@Composable
private fun rememberRecoveryGamesColors(): RecoveryGamesColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) {
        RecoveryGamesColors(
            background = scheme.background,
            surface = Color(0xFF171D22),
            text = Color(0xFFF7F2FF),
            mutedText = Color(0xFFC9C0D8),
            accentText = Color(0xFFD0C3F1),
            border = Color(0xFFD0C3F1).copy(alpha = 0.22f),
            shadow = Color(0xFFD0C3F1).copy(alpha = 0.12f),
            chipBackground = Color(0xFFD0C3F1).copy(alpha = 0.18f),
        )
    } else {
        RecoveryGamesColors(
            background = Color(0xFFFFF8FC),
            surface = Color(0xFFFFFCFF),
            text = Color(0xFF2F2637),
            mutedText = Color(0xFF706777),
            accentText = Color(0xFF5C4A7D),
            border = Color.Transparent,
            shadow = Color(0xFF2F2637).copy(alpha = 0.08f),
            chipBackground = ImpulsivePsychological.copy(alpha = 0.34f),
        )
    }
}

private data class RecoveryGameCardModel(
    val title: String,
    val description: String,
    val duration: String,
    val chip: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val onOpen: () -> Unit,
)

@Composable
fun RecoveryGamesScreen(
    onBack: () -> Unit,
    onOpenReflexOverride: () -> Unit,
    onOpenBlockCascade: () -> Unit,
    onOpenPatternBreak: () -> Unit = {},
    onOpenMindLesson: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberRecoveryGamesColors()
    val games = listOf(
        RecoveryGameCardModel(
            title = "Reflex Override",
            description = "Break autopilot with a fast control challenge.",
            duration = "60 sec",
            chip = "Fast control",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.68f),
            onOpen = onOpenReflexOverride,
        ),
        RecoveryGameCardModel(
            title = "Pattern Break",
            description = "Shift attention into quick logic and pattern recognition.",
            duration = "60 sec",
            chip = "Logic reset",
            icon = Icons.Filled.AutoAwesome,
            iconBackground = ImpulsivePhysical.copy(alpha = 0.72f),
            onOpen = onOpenPatternBreak,
        ),
        RecoveryGameCardModel(
            title = "Block Cascade",
            description = "A time-boxed block round with a clear finish state.",
            duration = "90 sec",
            chip = "Visual focus",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.58f),
            onOpen = onOpenBlockCascade,
        ),
        RecoveryGameCardModel(
            title = "Mind Lesson",
            description = "A short interactive lesson with attention checks.",
            duration = "2-3 min",
            chip = "Mind reset",
            icon = Icons.Filled.AutoAwesome,
            iconBackground = ImpulsivePhysical.copy(alpha = 0.56f),
            onOpen = onOpenMindLesson,
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recovery Games",
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Choose a short, time-boxed game. The goal is to interrupt the loop and return to control.",
            color = colors.mutedText,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(22.dp))

        games.forEach { game ->
            RecoveryGameCard(game = game, colors = colors)
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RecoveryGameCard(
    game: RecoveryGameCardModel,
    colors: RecoveryGamesColors,
) {
    val cardShape = RoundedCornerShape(28.dp)
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.surface,
        shape = cardShape,
        border = if (isDark) BorderStroke(1.dp, colors.border) else null,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = cardShape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clickable { game.onOpen() },
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = game.iconBackground,
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = game.icon,
                    contentDescription = null,
                    tint = colors.text.copy(alpha = 0.84f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftChip(text = game.chip, colors = colors)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = colors.mutedText,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = game.duration,
                            color = colors.mutedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = game.title,
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = game.description,
                    color = colors.text.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.text.copy(alpha = 0.36f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SoftChip(
    text: String,
    colors: RecoveryGamesColors,
) {
    Surface(
        color = colors.chipBackground,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = colors.accentText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
