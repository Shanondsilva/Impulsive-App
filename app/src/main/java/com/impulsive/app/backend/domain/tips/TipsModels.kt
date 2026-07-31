package com.impulsive.app.backend.domain.tips

@JvmInline
value class ImpulsiveTipId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9_]+"))) {
            "Tip IDs must be stable lowercase catalogue keys."
        }
    }
}

enum class TipCategory {
    SocialMedia,
    Browser,
    LateNight,
    Morning,
    Boredom,
    Stress,
    BeingAlone,
    Focus,
    Notifications,
    Sleep,
    ImpulsiveProtection,
    MomentPlan,
    ResetReading,
    General,
}

enum class TipAudienceTag {
    SocialMedia,
    BrowserSearch,
    LateNight,
    Morning,
    Boredom,
    Stress,
    BeingAlone,
    TroubleSleeping,
    CompulsiveScrolling,
    NoticeTriggers,
    DailyResetHabit,
    ReduceUse,
    General,
}

enum class TipFeature {
    AndroidDigitalWellbeing,
    AndroidNotifications,
    AndroidModes,
    Instagram,
    AppProtection,
    MomentPlan,
    ProtectionSchedule,
    WebsiteProtection,
    ResetReading,
    Focus,
    WhatWorksForMe,
}

sealed interface TipAction {
    data object None : TipAction
    data class OpenImpulsiveFeature(val feature: TipFeature) : TipAction
    data class OpenAndroidSetting(val action: String) : TipAction
}

data class TipSource(
    val name: String,
    val reference: String,
    val lastReviewedDate: String,
)

data class ImpulsiveTip(
    val id: ImpulsiveTipId,
    val category: TipCategory,
    val title: String,
    val summary: String,
    val overviewSteps: List<String>,
    val whyThisMayHelp: String,
    val audienceTags: Set<TipAudienceTag>,
    val action: TipAction,
    val source: TipSource,
    val isExternalInstruction: Boolean,
    val priority: Int,
    val requiredFeature: TipFeature? = null,
    val available: Boolean = true,
    val obsolete: Boolean = false,
    val menuNamesMayVary: Boolean = isExternalInstruction,
)

enum class TipSelectionReason {
    OnboardingMatch,
    ConfigurationOpportunity,
    General,
    LeastRecentlyShown,
    Fallback,
}

data class TipSelectionContext(
    val onboardingAnswerIds: Set<String> = emptySet(),
    val configurationOpportunities: Set<TipFeature> = emptySet(),
    val availableFeatures: Set<TipFeature> = TipFeature.entries.toSet(),
    val viewedTipIds: Set<ImpulsiveTipId> = emptySet(),
    val dismissedTipIds: Set<ImpulsiveTipId> = emptySet(),
    val lastShownEpochDayByTip: Map<ImpulsiveTipId, Long> = emptyMap(),
    val tipShownThisSession: ImpulsiveTipId? = null,
)

data class TipSelectionResult(
    val tip: ImpulsiveTip?,
    val reason: TipSelectionReason,
    val whyYouAreSeeingThis: String?,
)

