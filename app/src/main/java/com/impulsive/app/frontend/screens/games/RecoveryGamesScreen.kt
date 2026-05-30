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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText

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
            .background(ImpulsiveBackground)
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
                    tint = ImpulsiveText,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recovery Games",
                color = ImpulsiveText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Choose a short, time-boxed game. The goal is to interrupt the loop and return to control.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(22.dp))

        games.forEach { game ->
            RecoveryGameCard(game = game)
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RecoveryGameCard(
    game: RecoveryGameCardModel,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.05f),
                spotColor = ImpulsiveText.copy(alpha = 0.07f),
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
                    tint = ImpulsiveText.copy(alpha = 0.84f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftChip(game.chip)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = ImpulsiveMutedText,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = game.duration,
                            color = ImpulsiveMutedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = game.title,
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = game.description,
                    color = ImpulsiveText.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ImpulsiveText.copy(alpha = 0.36f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SoftChip(text: String) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.34f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = Color(0xFF5C4A7D),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
