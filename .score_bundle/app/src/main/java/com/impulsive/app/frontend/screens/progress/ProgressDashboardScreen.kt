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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.domain.model.score.ScoreDashboardState
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScorePersonalBest
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreTimelineItem
import com.impulsive.app.backend.session.progress.ScoreViewModel
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.theme.ImpulsiveBackground
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsiveMutedText
import com.impulsive.app.frontend.theme.ImpulsivePhysical
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSpiritual
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.theme.ImpulsiveText
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressDashboardScreen(
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScoreViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImpulsiveBackground)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 132.dp),
        ) {
            ScoreHeader()

            Spacer(modifier = Modifier.height(28.dp))

            MainControlScoreCard(uiState = uiState)

            Spacer(modifier = Modifier.height(28.dp))

            ScoreFilter(
                selectedRange = uiState.selectedRange,
                onRangeSelected = viewModel::selectRange,
            )

            Spacer(modifier = Modifier.height(18.dp))

            GameScoreSummary(uiState = uiState)

            Spacer(modifier = Modifier.height(22.dp))

            PersonalBestsSection(personalBests = uiState.personalBests)

            Spacer(modifier = Modifier.height(26.dp))

            SafeExitAndUrgeCards(uiState = uiState)

            Spacer(modifier = Modifier.height(26.dp))

            RecentSessionsTimeline(items = uiState.recentSessions)
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
private fun ScoreHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Score",
            color = ImpulsiveText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ImpulsiveSurface)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = ImpulsiveText.copy(alpha = 0.08f),
                    spotColor = ImpulsiveText.copy(alpha = 0.08f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = ImpulsiveText.copy(alpha = 0.76f),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Your control progress, built from real recovery actions.",
        color = ImpulsiveText.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(0.82f),
    )
}

@Composable
private fun MainControlScoreCard(uiState: ScoreDashboardState) {
    Surface(
        color = ImpulsivePsychological,
        shape = RoundedCornerShape(34.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(34.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.08f),
                spotColor = ImpulsivePsychological.copy(alpha = 0.32f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                color = Color.White.copy(alpha = 0.34f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "LEVEL ${uiState.currentLevel}",
                    color = ImpulsiveText.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = uiState.controlScore.formatNumber(),
                    color = ImpulsiveText.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Control Score",
                    color = ImpulsiveText.copy(alpha = 0.54f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Based on your recovery actions.",
                color = ImpulsiveText.copy(alpha = 0.48f),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Trend",
                    color = ImpulsiveText.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${uiState.pointsUntilNextLevel} LP left",
                    color = ImpulsiveText.copy(alpha = 0.54f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MiniTrendBars(progress = uiState.levelProgress)
        }
    }
}

@Composable
private fun MiniTrendBars(progress: Float) {
    val activeIndex = ((progress * 6).toInt()).coerceIn(0, 5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val heights = listOf(12.dp, 18.dp, 14.dp, 24.dp, 18.dp, 28.dp)
        heights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        if (index == activeIndex) {
                            ImpulsiveText.copy(alpha = 0.62f)
                        } else {
                            Color.White.copy(alpha = 0.42f)
                        },
                    ),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.26f)),
        )
    }
}

@Composable
private fun ScoreFilter(
    selectedRange: ScoreRange,
    onRangeSelected: (ScoreRange) -> Unit,
) {
    Surface(
        color = ImpulsiveSurface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScoreRange.entries.forEach { range ->
                val selected = selectedRange == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) ImpulsivePsychological else Color.Transparent)
                        .clickable { onRangeSelected(range) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = range.label,
                        color = if (selected) ImpulsiveText else ImpulsiveText.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameScoreSummary(uiState: ScoreDashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCard(
            icon = Icons.Filled.SportsEsports,
            label = "GAMES COMPLETED",
            value = uiState.gamesCompleted.toString(),
            iconTint = Color(0xFF38556D),
        )
        SummaryCard(
            icon = Icons.Filled.EmojiEvents,
            label = "BEST GAME",
            value = uiState.bestGameName,
            iconTint = Color(0xFF38556D),
        )
        SummaryCard(
            icon = Icons.Filled.AutoAwesome,
            label = "TOTAL CONTROL POINTS",
            value = uiState.totalControlPoints.formatNumber(),
            iconTint = Color(0xFF6D6827),
        )
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.04f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(26.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.04f),
                spotColor = ImpulsiveText.copy(alpha = 0.05f),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = ImpulsiveText.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = ImpulsiveText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PersonalBestsSection(personalBests: List<ScorePersonalBest>) {
    Text(
        text = "Personal Bests",
        color = ImpulsiveText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(14.dp))

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        personalBests.forEach { personalBest ->
            PersonalBestCard(personalBest = personalBest)
        }
    }
}

@Composable
private fun PersonalBestCard(personalBest: ScorePersonalBest) {
    val accent = personalBest.gameType.accentColor()
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, accent.copy(alpha = 0.82f)),
        modifier = Modifier
            .width(154.dp)
            .heightIn(min = 112.dp)
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = accent.copy(alpha = 0.10f),
                spotColor = accent.copy(alpha = 0.16f),
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = personalBest.gameType.displayName,
                color = ImpulsiveText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (personalBest.hasRecord) personalBest.bestScore.formatNumber() else "0",
                    color = if (personalBest.hasRecord) ImpulsiveText else ImpulsiveMutedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Best Score",
                    color = ImpulsiveMutedText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Prev: ${personalBest.previousScore?.formatNumber() ?: "-"}",
                    color = ImpulsiveText.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = personalBest.changeFromPrevious?.formatChange() ?: "",
                    color = Color(0xFF386E7D),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SafeExitAndUrgeCards(uiState: ScoreDashboardState) {
    RecoveryMetricCard(
        title = "Safe Exit",
        mainValue = uiState.safeExitCount.toString(),
        label = "Walk aways ${uiState.selectedRange.metricSuffix()}",
        footer = "Best streak: ${uiState.bestSafeExitStreak}",
        icon = Icons.Filled.Shield,
        color = ImpulsivePhysical,
        largeTextColor = Color(0xFF2D5B70),
    )

    Spacer(modifier = Modifier.height(14.dp))

    RecoveryMetricCard(
        title = "Urge Drop",
        mainValue = uiState.averageUrgeDrop?.let { "-${String.format(Locale.US, "%.1f", it)}" } ?: "-",
        label = "Average urge reduction",
        footer = "Best drop: ${uiState.bestUrgeDrop?.let { "-$it" } ?: "-"}",
        icon = Icons.Filled.TrendingDown,
        color = ImpulsiveSpiritual,
        largeTextColor = Color(0xFF6B6725),
    )
}

@Composable
private fun RecoveryMetricCard(
    title: String,
    mainValue: String,
    label: String,
    footer: String,
    icon: ImageVector,
    color: Color,
    largeTextColor: Color,
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.34f), CircleShape),
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = mainValue,
                color = largeTextColor,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = ImpulsiveText.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ImpulsiveText.copy(alpha = 0.08f)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = largeTextColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = footer,
                    color = ImpulsiveText.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RecentSessionsTimeline(items: List<ScoreTimelineItem>) {
    Surface(
        color = ImpulsiveSurface,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.04f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.05f),
                spotColor = ImpulsiveText.copy(alpha = 0.07f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Recent sessions",
                color = ImpulsiveText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (items.isEmpty()) {
                EmptyTimelineState()
            } else {
                items.forEachIndexed { index, item ->
                    TimelineRow(
                        item = item,
                        showLineBelow = index != items.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(ImpulsivePsychological.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ShowChart,
                contentDescription = null,
                tint = ImpulsiveText.copy(alpha = 0.68f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No score sessions yet",
            color = ImpulsiveText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Complete a recovery game to build this card.",
            color = ImpulsiveMutedText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimelineRow(
    item: ScoreTimelineItem,
    showLineBelow: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(30.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFFF4F0F7), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.gameIcon(),
                    contentDescription = null,
                    tint = ImpulsiveText.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showLineBelow) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(58.dp)
                        .background(ImpulsiveText.copy(alpha = 0.10f)),
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
                    color = ImpulsiveText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.completedAt.relativeLabel(),
                    color = ImpulsiveText.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Score: ${item.score.formatNumber()}  •  Urge: ${item.urgeLabel()}",
                color = ImpulsiveText.copy(alpha = 0.70f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(7.dp))
            OutcomePill(outcome = item.outcome)
        }
    }
}

@Composable
private fun OutcomePill(outcome: ScoreSessionOutcome) {
    val color = when (outcome) {
        ScoreSessionOutcome.WalkedAway -> Color(0xFFE0F7EA)
        ScoreSessionOutcome.ContinuedWithIntention -> Color(0xFFF0ECF0)
        ScoreSessionOutcome.Completed -> ImpulsivePsychological.copy(alpha = 0.30f)
        ScoreSessionOutcome.Replayed -> ImpulsivePhysical.copy(alpha = 0.28f)
        ScoreSessionOutcome.Abandoned -> Color(0xFFF0ECF0)
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, ImpulsiveText.copy(alpha = 0.05f)),
    ) {
        Text(
            text = outcome.label,
            color = ImpulsiveText.copy(alpha = 0.74f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun ScoreGameType.accentColor(): Color = when (this) {
    ScoreGameType.ReflexOverride -> ImpulsivePhysical
    ScoreGameType.PatternBreak -> ImpulsiveSpiritual
    ScoreGameType.BlockCascade -> ImpulsivePsychological
    ScoreGameType.UrgeSurvival -> ImpulsiveFocusMode
    ScoreGameType.FluidRegulation -> Color(0xFFD8F4E3)
    ScoreGameType.PrecisionFocus -> ImpulsivePhysical
    ScoreGameType.DopamineRunner -> ImpulsiveFocusMode
    ScoreGameType.BreathControl -> ImpulsivePsychological
    ScoreGameType.RageDischarge -> ImpulsiveFocusMode
    ScoreGameType.Unknown -> ImpulsivePsychological
}

private fun ScoreTimelineItem.gameIcon(): ImageVector = when (gameName) {
    ScoreGameType.PatternBreak.displayName -> Icons.Filled.AutoAwesome
    ScoreGameType.UrgeSurvival.displayName -> Icons.Filled.Shield
    ScoreGameType.FluidRegulation.displayName -> Icons.Filled.TrendingDown
    else -> Icons.Filled.SportsEsports
}

private fun ScoreTimelineItem.urgeLabel(): String = if (urgeBefore != null && urgeAfter != null) {
    "$urgeBefore → $urgeAfter"
} else {
    "not logged"
}

private fun LocalDateTime.relativeLabel(): String {
    val now = LocalDateTime.now()
    val duration = Duration.between(this, now)
    return when {
        toLocalDate() == LocalDate.now() && duration.toHours() < 1 -> "Now"
        toLocalDate() == LocalDate.now() -> "${duration.toHours()}h ago"
        toLocalDate() == LocalDate.now().minusDays(1) -> "Yesterday"
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
