package com.impulsive.app.frontend.screens.games

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.local.preferences.GameAccessState
import com.impulsive.app.backend.data.repository.StoreResult
import com.impulsive.app.backend.domain.model.store.GameAccess
import com.impulsive.app.backend.domain.model.store.GameStoreCatalog
import com.impulsive.app.backend.domain.model.store.StoreGame
import com.impulsive.app.backend.session.game.GameStoreViewModel
import com.impulsive.app.frontend.components.impulsiveGlowBorderStroke
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
    val infoDescription: String,
    val duration: String,
    val chip: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val gameTypeId: String? = null,
    val winPoints: Int? = null,
    val onOpen: () -> Unit,
)

@Composable
fun RecoveryGamesScreen(
    onBack: () -> Unit,
    onOpenReflexOverride: () -> Unit,
    onOpenBlockCascade: () -> Unit,
    onOpenSkylineReset: () -> Unit = {},
    onOpenRhythmTiles: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberRecoveryGamesColors()
    val storeViewModel: GameStoreViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val points by storeViewModel.spendablePoints.collectAsStateWithLifecycle()
    val access by storeViewModel.accessByGame.collectAsStateWithLifecycle()
    val playedGameTypeIds by storeViewModel.playedGameTypeIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedInfoGame by remember { mutableStateOf<RecoveryGameCardModel?>(null) }
    var showPivotGamesInfo by remember { mutableStateOf(false) }

    fun showStoreResult(result: StoreResult, successText: String) {
        val msg = when (result) {
            StoreResult.Success -> successText
            StoreResult.NotEnoughPoints -> "Not enough control points yet."
            StoreResult.Unavailable -> "That game isn't available."
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    selectedInfoGame?.let { game ->
        AlertDialog(
            onDismissRequest = { selectedInfoGame = null },
            title = {
                Text(
                    text = game.title,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = game.infoDescription,
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedInfoGame = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.accentText,
                    ),
                ) {
                    Text(text = "Got it")
                }
            },
        )
    }

    if (showPivotGamesInfo) {
        AlertDialog(
            onDismissRequest = { showPivotGamesInfo = false },
            title = {
                Text(
                    text = "Pivot Games",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Choose a short, time-boxed game. Notice the moment, pivot your attention, then return to plan.",
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showPivotGamesInfo = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.accentText,
                    ),
                ) {
                    Text(text = "Got it")
                }
            },
        )
    }

    val games = listOf(
        RecoveryGameCardModel(
            title = "Reflex Override",
            description = "Break autopilot with a fast control challenge.",
            infoDescription = "A fast reaction game that helps you snap out of autopilot. Tap the right targets, avoid mistakes, and bring your attention back under control.",
            duration = "90 sec",
            chip = "Fast control",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.68f),
            gameTypeId = "REFLEX_OVERRIDE",
            winPoints = GameStoreCatalog.TwoWinStreakControlPoints,
            onOpen = onOpenReflexOverride,
        ),
        RecoveryGameCardModel(
            title = "Block Cascade",
            description = "A time-boxed block round with a clear finish state.",
            infoDescription = "A steady block-clearing game that helps slow your thoughts and gives your mind something structured to solve. Clear lines, stay calm, and finish the round.",
            duration = "90 sec",
            chip = "Visual focus",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.58f),
            gameTypeId = "BLOCK_CASCADE",
            winPoints = GameStoreCatalog.TwoWinStreakControlPoints,
            onOpen = onOpenBlockCascade,
        ),
        RecoveryGameCardModel(
            title = "SkyStack",
            description = "Place sliding blocks and build a steady tower.",
            infoDescription = "A calm stacking game where each tap trims the block to the overlap. Keep the tower continuous, aim for perfect drops, and stay with the rhythm.",
            duration = "90 sec",
            chip = "Calm stack",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.50f),
            gameTypeId = "SKYLINE_RESET",
            winPoints = GameStoreCatalog.TwoWinStreakControlPoints,
            onOpen = onOpenSkylineReset,
        ),
        RecoveryGameCardModel(
            title = "Rhythm Tiles",
            description = "Tap falling tiles and play the melody yourself.",
            infoDescription = "A melody game where every tap plays the next note of a song. Catch the tiles before they slip away, keep the tune going, and let your focus settle into the rhythm.",
            duration = "90 sec",
            chip = "Melody focus",
            icon = Icons.Filled.SportsEsports,
            iconBackground = ImpulsivePsychological.copy(alpha = 0.42f),
            gameTypeId = "RHYTHM_TILES",
            winPoints = GameStoreCatalog.TwoWinStreakControlPoints,
            onOpen = onOpenRhythmTiles,
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
                text = "Pivot Games",
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { showPivotGamesInfo = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "About Pivot Games",
                    tint = colors.mutedText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Control points",
                    color = colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = points.toString(),
                    color = colors.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Spend below to unlock games",
                    color = colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        games.forEach { game ->
            RecoveryGameCard(
                game = game,
                colors = colors,
                isUnplayed = game.gameTypeId != null && game.gameTypeId !in playedGameTypeIds,
                onInfo = { selectedInfoGame = game },
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        val lockedGames = GameStoreCatalog.games.filter { catalogGame ->
            (access[catalogGame.id]?.access ?: GameAccess.LOCKED) != GameAccess.OWNED
        }
        if (lockedGames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Unlock more games",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            lockedGames.forEach { catalogGame ->
                val state = access[catalogGame.id]
                LockedGameStoreCard(
                    game = catalogGame,
                    state = state,
                    points = points,
                    colors = colors,
                    onBuy = {
                        storeViewModel.buy(catalogGame.id) { result ->
                            showStoreResult(result, "Unlocked ${catalogGame.displayName}.")
                        }
                    },
                    onRent = {
                        storeViewModel.rent(catalogGame.id) { result ->
                            showStoreResult(result, "Rented for ${GameStoreCatalog.RentPlays} plays.")
                        }
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecoveryGameCard(
    game: RecoveryGameCardModel,
    colors: RecoveryGamesColors,
    isUnplayed: Boolean = false,
    onInfo: () -> Unit,
) {
    val cardShape = RoundedCornerShape(28.dp)
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.surface,
        shape = cardShape,
        border = if (isDark) {
            impulsiveGlowBorderStroke(
                enabled = true,
                glowColor = ImpulsivePsychological,
                fallbackColor = Color.Transparent,
                width = 1.25.dp,
                darkAlpha = 0.72f,
            )
        } else null,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    if (isUnplayed && game.winPoints != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = ImpulsivePsychological.copy(alpha = if (isDark) 0.32f else 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "2 wins +${game.winPoints}",
                                color = colors.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.title,
                        color = colors.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onInfo,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "About ${game.title}",
                            tint = colors.mutedText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = game.description,
                    color = colors.text.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LockedGameStoreCard(
    game: StoreGame,
    state: GameAccessState?,
    points: Int,
    colors: RecoveryGamesColors,
    onBuy: () -> Unit,
    onRent: () -> Unit,
) {
    val isRented = state?.access == GameAccess.RENTED
    val cardShape = RoundedCornerShape(28.dp)
    Surface(
        color = colors.surface,
        shape = cardShape,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = colors.chipBackground,
                            shape = RoundedCornerShape(20.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = colors.text.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName,
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isRented) "${state?.playsLeft ?: 0} plays left" else "Locked",
                        color = colors.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!isRented) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onRent,
                        enabled = points >= game.rentPrice,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.accentText,
                            disabledContentColor = colors.mutedText.copy(alpha = 0.58f),
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (points >= game.rentPrice) {
                                ImpulsivePsychological.copy(alpha = 0.72f)
                            } else {
                                colors.border
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Rent - ${game.rentPrice}")
                    }
                    Button(
                        onClick = onBuy,
                        enabled = points >= game.buyPrice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImpulsivePsychological,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = ImpulsivePsychological.copy(alpha = 0.24f),
                            disabledContentColor = colors.mutedText.copy(alpha = 0.68f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Buy - ${game.buyPrice}")
                    }
                }
            }
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
