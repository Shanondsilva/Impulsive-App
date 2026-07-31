package com.impulsive.app.frontend.screens.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.impulsive.app.R
import com.impulsive.app.backend.domain.tips.ImpulsiveTip
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipAction
import com.impulsive.app.backend.domain.tips.TipCategory
import com.impulsive.app.frontend.components.ImpulsiveTopAppBar

@Composable
fun TipsScreen(
    state: TipsHomeUiState,
    onOpenTip: (ImpulsiveTipId) -> Unit,
    onResetHiddenTips: () -> Unit,
    onBack: () -> Unit,
) {
    val suggested = state.currentTip
    val remaining = state.catalogue.filterNot { it.id == suggested?.id }
    val sections = listOf(
        stringResource(R.string.tips_suggested_section) to listOfNotNull(suggested),
        stringResource(R.string.tips_impulsive_section) to remaining.filter {
            it.category in setOf(
                TipCategory.ImpulsiveProtection,
                TipCategory.MomentPlan,
                TipCategory.ResetReading,
            ) || (it.category == TipCategory.General && !it.isExternalInstruction)
        },
        stringResource(R.string.tips_phone_section) to remaining.filter {
            it.category in setOf(
                TipCategory.Browser,
                TipCategory.Notifications,
                TipCategory.General,
            ) && it.isExternalInstruction
        },
        stringResource(R.string.tips_social_section) to remaining.filter {
            it.category == TipCategory.SocialMedia
        },
        stringResource(R.string.tips_sleep_focus_section) to remaining.filter {
            it.category in setOf(
                TipCategory.Sleep,
                TipCategory.Focus,
                TipCategory.LateNight,
                TipCategory.Morning,
            )
        },
    ).filter { it.second.isNotEmpty() }

    TipsScaffold(title = stringResource(R.string.tips_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.tips_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sections.forEach { (title, tips) ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            tips.forEach { tip ->
                TipListCard(tip = tip, onClick = { onOpenTip(tip.id) })
            }
        }
        if (state.dismissedCount > 0) {
            TextButton(
                onClick = onResetHiddenTips,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.tips_reset_hidden))
            }
        }
    }
}

@Composable
fun TipDetailScreen(
    tip: ImpulsiveTip,
    whyYouAreSeeingThis: String?,
    onAction: (TipAction) -> Unit,
    onShowAnother: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    TipsScaffold(title = "Tip", onBack = onBack) {
        Text(
            text = tip.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = tip.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tip.overviewSteps.forEachIndexed { index, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${index + 1}.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = step, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider()
        DetailSection(
            title = stringResource(R.string.tips_why_heading),
            body = tip.whyThisMayHelp,
        )
        whyYouAreSeeingThis?.let {
            DetailSection(
                title = stringResource(R.string.tips_why_seen_heading),
                body = it,
            )
        }
        DetailSection(
            title = stringResource(R.string.tips_source_heading),
            body = "${tip.source.name}\n${stringResource(R.string.tips_reviewed, tip.source.lastReviewedDate)}",
        )
        if (tip.menuNamesMayVary) {
            Text(
                text = stringResource(R.string.tips_menu_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tip.action !is TipAction.None) {
            Button(
                onClick = { onAction(tip.action) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.tips_open_setting))
            }
        }
        OutlinedButton(
            onClick = onShowAnother,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.tips_show_another))
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.tips_not_for_me))
        }
    }
}

@Composable
private fun TipsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            ImpulsiveTopAppBar(
                title = title,
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TipListCard(tip: ImpulsiveTip, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = tip.title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = tip.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold)
        Text(text = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@get:StringRes
private val TipCategory.displayNameRes: Int
    get() = when (this) {
        TipCategory.SocialMedia -> R.string.tips_category_social_media
        TipCategory.Browser -> R.string.tips_category_browser
        TipCategory.LateNight -> R.string.tips_category_late_night
        TipCategory.Morning -> R.string.tips_category_morning
        TipCategory.Boredom -> R.string.tips_category_boredom
        TipCategory.Stress -> R.string.tips_category_stress
        TipCategory.BeingAlone -> R.string.tips_category_being_alone
        TipCategory.Focus -> R.string.tips_category_focus
        TipCategory.Notifications -> R.string.tips_category_notifications
        TipCategory.Sleep -> R.string.tips_category_sleep
        TipCategory.ImpulsiveProtection -> R.string.tips_category_impulsive_protection
        TipCategory.MomentPlan -> R.string.tips_category_moment_plan
        TipCategory.ResetReading -> R.string.tips_category_reset_reading
        TipCategory.General -> R.string.tips_category_general
    }
