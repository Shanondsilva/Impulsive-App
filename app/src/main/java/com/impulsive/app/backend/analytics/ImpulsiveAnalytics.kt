package com.impulsive.app.backend.analytics

enum class ImpulsiveAnalyticsEvent(val eventName: String) {
    OnboardingRecommendationShown("onboarding_recommendation_shown"),
    OnboardingRecommendationAccepted("onboarding_recommendation_accepted"),
    OnboardingRecommendationEdited("onboarding_recommendation_edited"),
    OnboardingRecommendationDismissed("onboarding_recommendation_dismissed"),
    TimingSuggestionShown("timing_suggestion_shown"),
    TimingSuggestionAccepted("timing_suggestion_accepted"),
    TimingSuggestionEdited("timing_suggestion_edited"),
    TimingSuggestionDismissed("timing_suggestion_dismissed"),
    TimingSuggestionSuppressed("timing_suggestion_suppressed"),
    ProtectionTransitionShown("protection_transition_shown"),
    ProtectionTransitionCompleted("protection_transition_completed"),
    PlusPromotionViewed("plus_promotion_viewed"),
    PlusPromotionDismissed("plus_promotion_dismissed"),
    PaywallViewed("paywall_viewed"),
    PurchaseStarted("purchase_started"),
    PurchaseCompleted("purchase_completed"),
    PurchaseRestored("purchase_restored"),
}

enum class ImpulsiveAnalyticsParam(val key: String) {
    EntryPoint("entry_point"),
    SuggestionType("suggestion_type"),
    Result("result"),
    PolicyVersion("policy_version"),
    SubscriptionSurface("subscription_surface"),
}

interface ImpulsiveAnalytics {
    fun log(
        event: ImpulsiveAnalyticsEvent,
        parameters: Map<ImpulsiveAnalyticsParam, String> = emptyMap(),
    )
}

object NoOpImpulsiveAnalytics : ImpulsiveAnalytics {
    override fun log(
        event: ImpulsiveAnalyticsEvent,
        parameters: Map<ImpulsiveAnalyticsParam, String>,
    ) = Unit
}

object ImpulsiveAnalyticsPrivacyPolicy {
    private val AllowedEvents = ImpulsiveAnalyticsEvent.entries.map { it.eventName }.toSet()
    private val AllowedParams = ImpulsiveAnalyticsParam.entries.map { it.key }.toSet()
    private val DisallowedFragments = listOf(
        "answer",
        "trigger",
        "cue",
        "window",
        "time",
        "plan_id",
        "revision",
        "package",
        "url",
        "domain",
        "website",
        "journal",
        "feedback",
        "urge",
        "decision_id",
        "suggestion_id",
        "uid",
        "email",
        "pathshift_estimate",
        "lp",
        "level",
    )

    fun isAllowed(eventName: String, parameterKeys: Set<String>): Boolean =
        eventName in AllowedEvents &&
            parameterKeys.all { it in AllowedParams } &&
            parameterKeys.none { key -> DisallowedFragments.any(key.lowercase()::contains) }
}
