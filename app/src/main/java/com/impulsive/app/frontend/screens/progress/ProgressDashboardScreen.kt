package com.impulsive.app.frontend.screens.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Moving
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.domain.model.score.ScoreDashboardState
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScorePersonalBest
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreTimelineItem
import com.impulsive.app.backend.domain.model.score.UrgeTrendState
import com.impulsive.app.backend.session.progress.ScoreViewModel
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ScoreScreenColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val text: Color,
    val muted: Color,
    val faintLine: Color,
    val mainCard: Color,
    val mainCardText: Color,
    val selectedPill: Color,
    val unselectedPill: Color,
    val safeExitCard: Color,
    val urgeCard: Color,
    val timelineDot: Color,
    val shadow: Color,
    val lavenderGlow: Color,
    val greenGlow: Color,
    val blueGlow: Color,
    val yellowGlow: Color,
    val coralGlow: Color,
)

@Composable
fun ProgressDashboardScreen(
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScoreViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = rememberScoreColors()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 132.dp),
        ) {
            ScoreHeader(colors = colors)

            Spacer(modifier = Modifier.height(28.dp))

            MainControlScoreCard(
                uiState = uiState,
                colors = colors,
            )

            Spacer(modifier = Modifier.height(28.dp))

            ScoreFilter(
                selectedRange = uiState.selectedRange,
                colors = colors,
                onRangeSelected = viewModel::selectRange,
            )

            Spacer(modifier = Modifier.height(18.dp))

            GameScoreSummary(
                uiState = uiState,
                colors = colors,
            )

            Spacer(modifier = Modifier.height(22.dp))

            ScoreRecordsCard(
                personalBests = uiState.personalBests,
                recentSessions = uiState.recentSessions,
                colors = colors,
            )

            Spacer(modifier = Modifier.height(26.dp))

            SafeExitAndUrgeCards(
                uiState = uiState,
                colors = colors,
            )
        }

        BottomNavBar(
            selected = BottomNavItem.Progress,
            onSelect = { item ->
                when (item) {
                    BottomNavItem.Home -> onOpenHome()
                    BottomNavItem.Settings -> onOpenSettings()
                    else -> Unit
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun rememberScoreColors(): ScoreScreenColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        val darkBackground = Color(0xFF0E1014)
        val darkSurface = Color(0xFF1D1921)
        val darkElevatedSurface = Color(0xFF252029)
        val darkText = Color(0xFFF7F2FF)
        val darkMuted = Color(0xFFC9C0D8)

        ScoreScreenColors(
            background = darkBackground,
            surface = darkSurface,
            elevatedSurface = darkElevatedSurface,
            text = darkText,
            muted = darkMuted,
            faintLine = darkText.copy(alpha = 0.14f),
            mainCard = Color(0xFF183127),
            mainCardText = darkText,
            selectedPill = Color(0xFF6E5A96),
            unselectedPill = Color(0xFF1D2526),
            safeExitCard = darkSurface,
            urgeCard = darkSurface,
            timelineDot = Color(0xFF2A2233),
            shadow = Color.Black,
            lavenderGlow = ImpulsivePsychological,
            greenGlow = ImpulsiveOverallTheme,
            blueGlow = ImpulsivePhysical,
            yellowGlow = ImpulsiveSpiritual,
            coralGlow = ImpulsiveFocusMode,
        )
    } else {
        ScoreScreenColors(
            background = Color(0xFFFFF8FC),
            surface = Color(0xFFFFFFFF),
            elevatedSurface = Color(0xFFF7F1F8),
            text = Color(0xFF2F2637),
            muted = Color(0xFF706777),
            faintLine = Color(0xFF2F2637).copy(alpha = 0.10f),
            mainCard = Color(0xFFDFF4E8),
            mainCardText = Color(0xFF24392F),
            selectedPill = ImpulsivePsychological,
            unselectedPill = Color(0xFFF2ECF3),
            safeExitCard = ImpulsiveOverallTheme,
            urgeCard = ImpulsiveSpiritual,
            timelineDot = Color(0xFFF4F0F7),
            shadow = Color(0xFF2F2637),
            lavenderGlow = ImpulsivePsychological,
            greenGlow = ImpulsiveOverallTheme,
            blueGlow = ImpulsivePhysical,
            yellowGlow = ImpulsiveSpiritual,
            coralGlow = ImpulsiveFocusMode,
        )
    }
}

@Composable
private fun ScoreHeader(colors: ScoreScreenColors) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Score",
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Your control progress, built from real recovery actions.",
            color = colors.text.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
            modifier = Modifier.fillMaxWidth(0.88f),
        )
    }
}

@Composable
private fun MainControlScoreCard(
    uiState: ScoreDashboardState,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val trendScores = uiState.recentSessions
        .asReversed()
        .map { it.score.coerceAtLeast(0) }
        .takeLast(7)
    val hasTrendData = trendScores.isNotEmpty()
    val trendIsImproving = trendScores.size >= 2 && trendScores.last() >= trendScores.first()
    val trendAccent = colors.coralGlow
    val trendLabel = when {
        !hasTrendData -> "No trend yet"
        trendScores.size == 1 -> "First session logged"
        trendIsImproving -> "Trending stronger"
        else -> "Recovery still building"
    }

    Surface(
        color = colors.mainCard,
        shape = RoundedCornerShape(34.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) colors.greenGlow.copy(alpha = 0.42f) else colors.greenGlow.copy(alpha = 0.30f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 18.dp else 10.dp,
                shape = RoundedCornerShape(34.dp),
                clip = false,
                ambientColor = colors.greenGlow.copy(alpha = if (isDark) 0.18f else 0.12f),
                spotColor = colors.greenGlow.copy(alpha = if (isDark) 0.10f else 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (isDark) colors.lavenderGlow.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "LEVEL ${uiState.currentLevel}",
                        color = colors.mainCardText.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }

                Text(
                    text = "${uiState.pointsUntilNextLevel} LP left",
                    color = colors.mainCardText.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Recent Trend",
                        color = colors.mainCardText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trendLabel,
                        color = colors.mainCardText.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = trendAccent.copy(alpha = if (isDark) 0.16f else 0.28f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = if (isDark) trendAccent else colors.mainCardText.copy(alpha = 0.74f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            RecoveryTrendBars(
                scores = trendScores,
                accent = trendAccent,
                colors = colors,
            )
        }
    }
}

@Composable
private fun RecoveryTrendBars(
    scores: List<Int>,
    accent: Color,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val maxScore = scores.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (scores.isEmpty()) {
            repeat(7) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((10 + index % 3 * 6).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(colors.mainCardText.copy(alpha = if (isDark) 0.10f else 0.18f)),
                )
            }
        } else {
            scores.forEach { score ->
                val normalizedHeight = (18 + (score / maxScore.toFloat()) * 48).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(normalizedHeight)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(accent.copy(alpha = if (isDark) 0.78f else 0.58f)),
                )
            }
            repeat((7 - scores.size).coerceAtLeast(0)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(colors.mainCardText.copy(alpha = if (isDark) 0.10f else 0.14f)),
                )
            }
        }
    }
}

@Composable
private fun ScoreFilter(
    selectedRange: ScoreRange,
    colors: ScoreScreenColors,
    onRangeSelected: (ScoreRange) -> Unit,
) {
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.unselectedPill,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isDark) colors.lavenderGlow.copy(alpha = 0.12f) else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(ScoreRange.Week, ScoreRange.AllTime).forEach { range ->
                val selected = selectedRange == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.selectedPill else Color.Transparent)
                        .clickable { onRangeSelected(range) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = range.label,
                        color = if (selected) colors.text else colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreIconBadge(
    icon: ImageVector,
    accentColor: Color,
    colors: ScoreScreenColors,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val isDark = colors.background.luminance() < 0.5f
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = accentColor.copy(alpha = if (isDark) 0.18f else 0.24f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) accentColor else colors.text.copy(alpha = 0.74f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun GameScoreSummary(
    uiState: ScoreDashboardState,
    colors: ScoreScreenColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryMetricCard(
            icon = Icons.Filled.SportsEsports,
            title = "GAMES COMPLETED",
            value = uiState.gamesCompleted.formatNumber(),
            accentColor = colors.blueGlow,
            colors = colors,
        )
        SummaryMetricCard(
            icon = Icons.Filled.MilitaryTech,
            title = "BEST GAME",
            value = uiState.bestGameName,
            accentColor = colors.yellowGlow,
            colors = colors,
        )
        SummaryMetricCard(
            icon = Icons.Filled.AutoAwesome,
            title = "TOTAL CONTROL POINTS",
            value = uiState.totalControlPoints.formatNumber(),
            accentColor = colors.greenGlow,
            colors = colors,
        )
    }
}

@Composable
private fun SummaryMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) colors.greenGlow.copy(alpha = 0.16f) else colors.faintLine.copy(alpha = 0.6f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 10.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) colors.greenGlow.copy(alpha = 0.06f) else Color.Transparent,
                spotColor = if (isDark) colors.lavenderGlow.copy(alpha = 0.05f) else Color.Transparent,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreIconBadge(
                icon = icon,
                accentColor = accentColor,
                colors = colors,
                size = 34.dp,
                iconSize = 18.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = colors.text.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScoreRecordsCard(
    personalBests: List<ScorePersonalBest>,
    recentSessions: List<ScoreTimelineItem>,
    colors: ScoreScreenColors,
) {
    val topBest = personalBests
        .filter { it.hasRecord }
        .maxByOrNull { it.bestScore }
        ?: personalBests.firstOrNull()
    val accent = topBest?.gameType?.accentColor() ?: colors.lavenderGlow

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.58f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor = accent.copy(alpha = 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreIconBadge(
                        icon = Icons.Filled.MilitaryTech,
                        accentColor = accent,
                        colors = colors,
                        size = 32.dp,
                        iconSize = 17.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Personal Best",
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Recent session",
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MainPersonalBestPanel(
                    best = topBest,
                    colors = colors,
                    modifier = Modifier.weight(1.08f),
                )
                RecentPlayedPanel(
                    item = recentSessions.firstOrNull(),
                    colors = colors,
                    modifier = Modifier.weight(0.92f),
                )
            }
        }
    }
}

@Composable
private fun MainPersonalBestPanel(
    best: ScorePersonalBest?,
    colors: ScoreScreenColors,
    modifier: Modifier = Modifier,
) {
    val accent = best?.gameType?.accentColor() ?: colors.lavenderGlow
    Surface(
        color = colors.elevatedSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreIconBadge(
                    icon = best?.gameType?.scoreIcon() ?: Icons.Filled.SportsEsports,
                    accentColor = accent,
                    colors = colors,
                    size = 34.dp,
                    iconSize = 18.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = best?.gameType?.displayName ?: "Recovery Game",
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (best?.hasRecord == true) best.bestScore.formatNumber() else "-",
                    color = colors.text,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Best",
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (best?.hasRecord == true) {
                    "Prev: ${best.previousScore?.formatNumber() ?: "-"} ${best.changeFromPrevious?.formatChange().orEmpty()}"
                } else {
                    "Complete a recovery game to set your first record."
                },
                color = colors.text.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentPlayedPanel(
    item: ScoreTimelineItem?,
    colors: ScoreScreenColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colors.elevatedSurface.copy(alpha = 0.48f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colors.faintLine.copy(alpha = 0.45f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (item == null) {
                EmptyRecentPlayedState(colors = colors)
            } else {
                RecentPlayedMiniRow(
                    item = item,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun RecentPlayedMiniRow(
    item: ScoreTimelineItem,
    colors: ScoreScreenColors,
) {
    Row(verticalAlignment = Alignment.Top) {
        ScoreIconBadge(
            icon = item.gameIcon(),
            accentColor = item.gameAccentColor(),
            colors = colors,
            size = 28.dp,
            iconSize = 14.dp,
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.gameName,
                    color = colors.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.completedAt.relativeLabel(),
                    color = colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.score.formatNumber()} pts",
                color = colors.text.copy(alpha = 0.70f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutcomePill(
                outcome = item.outcome,
                colors = colors,
            )
        }
    }
}

@Composable
private fun EmptyRecentPlayedState(colors: ScoreScreenColors) {
    Column(horizontalAlignment = Alignment.Start) {
        ScoreIconBadge(
            icon = Icons.Filled.Timeline,
            accentColor = colors.lavenderGlow,
            colors = colors,
            size = 32.dp,
            iconSize = 17.dp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "No plays yet",
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your latest session appears here.",
            color = colors.muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PersonalBestsSection(
    personalBests: List<ScorePersonalBest>,
    colors: ScoreScreenColors,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreIconBadge(
                icon = Icons.Filled.MilitaryTech,
                accentColor = colors.lavenderGlow,
                colors = colors,
                size = 30.dp,
                iconSize = 16.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Personal Bests",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            personalBests.forEach { best ->
                PersonalBestCard(
                    best = best,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun PersonalBestCard(
    best: ScorePersonalBest,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = best.gameType.accentColor()
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.62f else 0.55f)),
        modifier = Modifier
            .width(164.dp)
            .shadow(
                elevation = if (isDark) 12.dp else 0.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.12f) else Color.Transparent,
                spotColor = if (isDark) accent.copy(alpha = 0.08f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreIconBadge(
                    icon = best.gameType.scoreIcon(),
                    accentColor = accent,
                    colors = colors,
                    size = 30.dp,
                    iconSize = 16.dp,
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = best.gameType.displayName,
                    color = colors.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (best.hasRecord) best.bestScore.formatNumber() else "-",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Best Score",
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Prev: ${best.previousScore?.formatNumber() ?: "-"}",
                    color = colors.text.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = best.changeFromPrevious?.formatChange().orEmpty(),
                    color = colors.text.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SafeExitAndUrgeCards(
    uiState: ScoreDashboardState,
    colors: ScoreScreenColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactSafeExitCard(
            walkAwayCount = uiState.safeExitCount,
            bestStreak = uiState.bestSafeExitStreak,
            rangeSuffix = uiState.selectedRange.metricSuffix(),
            colors = colors,
        )
        UrgeTrendCard(
            trend = uiState.urgeTrend,
            colors = colors,
        )
    }
}

@Composable
private fun CompactSafeExitCard(
    walkAwayCount: Int,
    bestStreak: Int,
    rangeSuffix: String,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = colors.greenGlow
    val contentColor = if (isDark) colors.text else Color(0xFF173B34)

    Surface(
        color = colors.safeExitCard,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) accent.copy(alpha = 0.72f) else accent.copy(alpha = 0.42f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 16.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.24f) else Color(0xFF173B34).copy(alpha = 0.08f),
                spotColor = if (isDark) accent.copy(alpha = 0.16f) else Color(0xFF173B34).copy(alpha = 0.10f),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isDark) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.32f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isDark) accent else contentColor.copy(alpha = 0.78f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Safe Exit",
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Best streak: ${bestStreak.formatNumber()}",
                    color = contentColor.copy(alpha = if (isDark) 0.76f else 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = walkAwayCount.formatNumber(),
                    color = contentColor,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Walk aways $rangeSuffix",
                    color = contentColor.copy(alpha = if (isDark) 0.76f else 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun UrgeTrendCard(
    trend: UrgeTrendState,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val accent = if (!trend.hasData || trend.difference <= 0) colors.greenGlow else colors.coralGlow
    val contentColor = if (isDark) colors.text else Color(0xFF2C1F4A)

    Surface(
        color = colors.urgeCard,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) accent.copy(alpha = 0.52f) else accent.copy(alpha = 0.30f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 12.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (isDark) accent.copy(alpha = 0.18f) else Color.Transparent,
                spotColor = if (isDark) accent.copy(alpha = 0.10f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isDark) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isDark) accent else contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Urge Trend",
                        color = contentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (trend.baselinePerDay > 0) "Baseline ${trend.baselinePerDay}/day" else "No baseline set",
                    color = contentColor.copy(alpha = if (isDark) 0.72f else 0.64f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!trend.hasData) {
                Text(
                    text = trend.label,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your trend appears after recovery checks or app/browser intercepts.",
                    color = contentColor.copy(alpha = if (isDark) 0.68f else 0.60f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = trend.label,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                val maxVal = (trend.bars.maxOfOrNull { maxOf(it.actual, it.baseline) } ?: 1).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    trend.bars.forEach { bar ->
                        val barAccent = if (bar.aboveBaseline) colors.coralGlow else colors.greenGlow
                        val height = (8 + (bar.actual.toFloat() / maxVal) * 36).dp
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(height)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barAccent.copy(alpha = if (isDark) 0.74f else 0.56f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryMetricCard(
    title: String,
    mainValue: String,
    label: String,
    footer: String,
    icon: ImageVector,
    cardColor: Color,
    accentColor: Color,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val contentColor = if (isDark) colors.text else Color(0xFF334042)
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) accentColor.copy(alpha = 0.38f) else Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 14.dp else 0.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = if (isDark) accentColor.copy(alpha = 0.14f) else Color.Transparent,
                spotColor = if (isDark) accentColor.copy(alpha = 0.08f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ScoreIconBadge(
                    icon = icon,
                    accentColor = cardColor.takeIf { cardColor.luminance() >= 0.5f } ?: colors.lavenderGlow,
                    colors = colors,
                    size = 34.dp,
                    iconSize = 18.dp,
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = mainValue,
                color = contentColor,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = contentColor.copy(alpha = if (isDark) 0.76f else 0.70f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (isDark) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.18f)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = if (isDark) 0.95f else 0.78f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = footer,
                    color = contentColor.copy(alpha = if (isDark) 0.78f else 0.74f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RecentSessionsTimeline(
    items: List<ScoreTimelineItem>,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) colors.lavenderGlow.copy(alpha = 0.24f) else colors.faintLine.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 14.dp else 0.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = if (isDark) colors.lavenderGlow.copy(alpha = 0.08f) else Color.Transparent,
                spotColor = if (isDark) colors.greenGlow.copy(alpha = 0.04f) else Color.Transparent,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreIconBadge(
                    icon = Icons.Filled.Timeline,
                    accentColor = colors.lavenderGlow,
                    colors = colors,
                    size = 32.dp,
                    iconSize = 17.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recent sessions",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (items.isEmpty()) {
                EmptyTimelineState(colors = colors)
            } else {
                items.forEachIndexed { index, item ->
                    TimelineRow(
                        item = item,
                        showLineBelow = index != items.lastIndex,
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineState(colors: ScoreScreenColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.selectedPill.copy(alpha = 0.34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ShowChart,
                contentDescription = null,
                tint = colors.text.copy(alpha = 0.68f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No score sessions yet",
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Complete a recovery game to build this card.",
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimelineRow(
    item: ScoreTimelineItem,
    showLineBelow: Boolean,
    colors: ScoreScreenColors,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(30.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(colors.timelineDot, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.gameIcon(),
                    contentDescription = null,
                    tint = item.gameAccentColor().copy(alpha = 0.92f),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showLineBelow) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(58.dp)
                        .background(colors.faintLine),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showLineBelow) 14.dp else 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.gameName,
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.completedAt.relativeLabel(),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Score: ${item.score.formatNumber()} • Urge: ${item.urgeLabel()}",
                color = colors.text.copy(alpha = 0.70f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(7.dp))
            OutcomePill(
                outcome = item.outcome,
                colors = colors,
            )
        }
    }
}

@Composable
private fun OutcomePill(
    outcome: ScoreSessionOutcome,
    colors: ScoreScreenColors,
) {
    val isDark = colors.background.luminance() < 0.5f
    val pillColor = when (outcome) {
        ScoreSessionOutcome.WalkedAway -> if (isDark) colors.greenGlow.copy(alpha = 0.16f) else Color(0xFFE0F7EA)
        ScoreSessionOutcome.ContinuedWithIntention -> if (isDark) colors.lavenderGlow.copy(alpha = 0.14f) else Color(0xFFF0ECF0)
        ScoreSessionOutcome.Completed -> colors.lavenderGlow.copy(alpha = if (isDark) 0.18f else 0.30f)
        ScoreSessionOutcome.Replayed -> if (isDark) colors.blueGlow.copy(alpha = 0.16f) else ImpulsivePhysical.copy(alpha = 0.28f)
        ScoreSessionOutcome.Abandoned -> if (isDark) colors.coralGlow.copy(alpha = 0.12f) else Color(0xFFF0ECF0)
    }
    Surface(
        color = pillColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, colors.faintLine.copy(alpha = 0.55f)),
    ) {
        Text(
            text = outcome.label,
            color = colors.text.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun ScoreGameType.accentColor(): Color = when (this) {
    ScoreGameType.ReflexOverride -> ImpulsivePhysical
    ScoreGameType.PatternBreak -> ImpulsiveSpiritual
    ScoreGameType.BlockCascade -> ImpulsivePsychological
    ScoreGameType.MindLesson -> ImpulsiveOverallTheme
    ScoreGameType.UrgeSurvival -> ImpulsiveFocusMode
    ScoreGameType.FluidRegulation -> ImpulsiveOverallTheme
    ScoreGameType.PrecisionFocus -> ImpulsivePhysical
    ScoreGameType.DopamineRunner -> ImpulsiveFocusMode
    ScoreGameType.BreathControl -> ImpulsivePsychological
    ScoreGameType.RageDischarge -> ImpulsiveFocusMode
    ScoreGameType.Unknown -> ImpulsivePsychological
}

private fun ScoreGameType.scoreIcon(): ImageVector = when (this) {
    ScoreGameType.ReflexOverride -> Icons.Filled.SportsEsports
    ScoreGameType.PatternBreak -> Icons.Filled.AutoAwesome
    ScoreGameType.BlockCascade -> Icons.Filled.ViewModule
    ScoreGameType.MindLesson -> Icons.Filled.MenuBook
    ScoreGameType.UrgeSurvival -> Icons.Filled.Shield
    ScoreGameType.FluidRegulation -> Icons.Filled.TrendingDown
    ScoreGameType.PrecisionFocus -> Icons.Filled.Psychology
    ScoreGameType.DopamineRunner -> Icons.Filled.Moving
    ScoreGameType.BreathControl -> Icons.Filled.ShowChart
    ScoreGameType.RageDischarge -> Icons.Filled.AutoAwesome
    ScoreGameType.Unknown -> Icons.Filled.SportsEsports
}

private fun scoreTypeForDisplayName(gameName: String): ScoreGameType =
    ScoreGameType.entries.firstOrNull { it.displayName == gameName } ?: ScoreGameType.Unknown

private fun ScoreTimelineItem.gameIcon(): ImageVector = scoreTypeForDisplayName(gameName).scoreIcon()

private fun ScoreTimelineItem.gameAccentColor(): Color = scoreTypeForDisplayName(gameName).accentColor()

private fun ScoreTimelineItem.urgeLabel(): String = if (urgeBefore != null && urgeAfter != null) {
    "$urgeBefore -> $urgeAfter"
} else {
    "not logged"
}

private fun LocalDateTime.relativeLabel(): String {
    val now = LocalDateTime.now()
    val today = LocalDate.now()
    val duration = Duration.between(this, now)

    if (duration.isNegative || duration.toMinutes() < 1) {
        return "Just now"
    }

    return when {
        toLocalDate() == today && duration.toMinutes() < 60 -> {
            val minutes = duration.toMinutes()
            if (minutes == 1L) "1 min ago" else "${minutes} min ago"
        }

        toLocalDate() == today -> {
            val hours = duration.toHours().coerceAtLeast(1)
            if (hours == 1L) "1h ago" else "${hours}h ago"
        }

        toLocalDate() == today.minusDays(1) -> "Yesterday"

        else -> format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun Int.formatNumber(): String = NumberFormat
    .getIntegerInstance(Locale.UK)
    .format(this)

private fun Int.formatChange(): String = when {
    this > 0 -> "+${formatNumber()}"
    this < 0 -> formatNumber()
    else -> "0"
}

private fun ScoreRange.metricSuffix(): String = when (this) {
    ScoreRange.Today -> "today"
    ScoreRange.Week -> "this week"
    ScoreRange.AllTime -> "all time"
}
