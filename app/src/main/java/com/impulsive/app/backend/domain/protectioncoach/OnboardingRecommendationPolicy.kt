package com.impulsive.app.backend.domain.protectioncoach

import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers

data class OnboardingRecommendationUiState(
    val recommendations: List<OnboardingRecommendation>,
    val explanation: String =
        "These are suggestions based on your answers. You can change them now or later.",
) {
    val hasSuggestions: Boolean get() = recommendations.isNotEmpty()
}

data class OnboardingRecommendation(
    val suggestionType: ProtectionCoachSuggestionType,
    val reason: ProtectionCoachOnboardingReason,
    val title: String,
    val body: String,
    val action: OnboardingRecommendationAction,
    val supportFamilies: Set<InterventionFamily> = emptySet(),
)

enum class OnboardingRecommendationAction {
    Review,
    Accept,
    Edit,
    Skip,
    UseThisSetup,
}

class OnboardingRecommendationPolicy {
    fun recommendationsFor(answers: OnboardingAnswers): OnboardingRecommendationUiState {
        val recommendations = buildList {
            if (answers.containsAny(SocialMediaTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.ReviewSocialApps,
                        reason = ProtectionCoachOnboardingReason.SocialMedia,
                        title = "Review the social apps installed on this phone.",
                        body = "You choose each protected app explicitly. Impulsive does not auto-protect apps from setup answers.",
                        action = OnboardingRecommendationAction.Review,
                    ),
                )
            }
            if (answers.containsAny(BrowserTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.ReviewBrowserProtection,
                        reason = ProtectionCoachOnboardingReason.BrowserBrowsing,
                        title = "Review Website Protection and browser guidance.",
                        body = "Browsing history, URLs and search terms stay out of the suggestion record.",
                        action = OnboardingRecommendationAction.Review,
                    ),
                )
            }
            if (answers.containsAny(BoredomTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.EnableSupportFamily,
                        reason = ProtectionCoachOnboardingReason.Boredom,
                        title = "Keep active reset options easy to reach.",
                        body = "Pivot Game, Reset Reading and Moment Plans can stay available without being forced.",
                        action = OnboardingRecommendationAction.Accept,
                        supportFamilies = setOf(
                            InterventionFamily.PivotGame,
                            InterventionFamily.PivotReading,
                            InterventionFamily.MomentPlan,
                        ),
                    ),
                )
            }
            if (answers.containsAny(CalmSupportTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.EnableSupportFamily,
                        reason = ProtectionCoachOnboardingReason.StressOrAlone,
                        title = "Emphasise calmer support options.",
                        body = "Short Pause, Moment Plan and Reset Reading can be suggested without inferring a diagnosis.",
                        action = OnboardingRecommendationAction.Accept,
                        supportFamilies = setOf(
                            InterventionFamily.ShortPause,
                            InterventionFamily.PivotReading,
                            InterventionFamily.MomentPlan,
                        ),
                    ),
                )
            }
            if (answers.containsAny(LateNightTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.CreateEveningWindow,
                        reason = ProtectionCoachOnboardingReason.LateNight,
                        title = "Review an evening protection window.",
                        body = "Nothing changes until you approve a time.",
                        action = OnboardingRecommendationAction.Edit,
                    ),
                )
            }
            if (answers.containsAny(MorningTokens)) {
                add(
                    OnboardingRecommendation(
                        suggestionType = ProtectionCoachSuggestionType.CreateMorningWindow,
                        reason = ProtectionCoachOnboardingReason.Morning,
                        title = "Review a morning protection window.",
                        body = "A morning plan can be practised without changing protection automatically.",
                        action = OnboardingRecommendationAction.Edit,
                    ),
                )
            }
            when (answers.weekOneGoal?.let(::normaliseToken)) {
                "notice_cues", "notice", "cues" -> add(weekOne(ProtectionCoachOnboardingReason.WeekOneCueAwareness))
                "practise_plan", "practice_plan", "plan" -> add(weekOne(ProtectionCoachOnboardingReason.WeekOnePracticePlan))
                "understand_patterns", "patterns" -> add(weekOne(ProtectionCoachOnboardingReason.WeekOnePatterns))
            }
        }.distinctBy { it.suggestionType to it.reason }
        return OnboardingRecommendationUiState(recommendations)
    }

    private fun weekOne(reason: ProtectionCoachOnboardingReason): OnboardingRecommendation =
        OnboardingRecommendation(
            suggestionType = ProtectionCoachSuggestionType.PractiseMomentPlan,
            reason = reason,
            title = "Keep your week-one goal visible on Home.",
            body = "A stated goal is treated as setup emphasis, not outcome evidence.",
            action = OnboardingRecommendationAction.Review,
        )

    private fun OnboardingAnswers.containsAny(tokens: Set<String>): Boolean =
        (interrupting + timing + triggers + listOfNotNull(weekOneGoal))
            .map(::normaliseToken)
            .any { answer -> tokens.any { token -> answer.contains(token) } }

    private fun normaliseToken(value: String): String =
        value.lowercase().replace("-", "_").replace(" ", "_")

    private companion object {
        val SocialMediaTokens = setOf("social", "instagram", "tiktok", "facebook", "twitter", "x_", "snap")
        val BrowserTokens = setOf("browser", "browsing", "search", "website", "internet", "safari", "chrome")
        val BoredomTokens = setOf("bored", "boredom")
        val CalmSupportTokens = setOf("stress", "thought", "alone", "lonely", "anxious", "difficult")
        val LateNightTokens = setOf("late", "night", "sleep", "bed")
        val MorningTokens = setOf("morning", "wake", "waking", "after_waking")
    }
}
