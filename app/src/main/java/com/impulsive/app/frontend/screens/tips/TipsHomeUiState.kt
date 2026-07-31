package com.impulsive.app.frontend.screens.tips

import com.impulsive.app.backend.domain.tips.ImpulsiveTip
import com.impulsive.app.backend.domain.tips.TipSelectionReason

data class TipsHomeUiState(
    val loading: Boolean = true,
    val catalogue: List<ImpulsiveTip> = emptyList(),
    val currentTip: ImpulsiveTip? = null,
    val currentReason: TipSelectionReason = TipSelectionReason.Fallback,
    val whyYouAreSeeingThis: String? = null,
    val dismissedCount: Int = 0,
)

