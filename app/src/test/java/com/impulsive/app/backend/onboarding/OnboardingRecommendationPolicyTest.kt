package com.impulsive.app.backend.onboarding

import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.protectioncoach.OnboardingRecommendationPolicy
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRecommendationPolicyTest {
    private val policy = OnboardingRecommendationPolicy()

    @Test
    fun socialMediaAnswerProducesReviewSuggestion() {
        val state = policy.recommendationsFor(
            OnboardingAnswers(interrupting = listOf("social_media")),
        )
        assertTrue(state.recommendations.any {
            it.suggestionType == ProtectionCoachSuggestionType.ReviewSocialApps
        })
    }

    @Test
    fun browserAnswerProducesWebsiteProtectionReview() {
        val state = policy.recommendationsFor(
            OnboardingAnswers(interrupting = listOf("browser_search")),
        )
        assertTrue(state.recommendations.any {
            it.suggestionType == ProtectionCoachSuggestionType.ReviewBrowserProtection
        })
    }

    @Test
    fun boredomProducesEligibleSupportPreference() {
        val recommendation = policy.recommendationsFor(
            OnboardingAnswers(triggers = listOf("boredom")),
        ).recommendations.single()
        assertTrue(InterventionFamily.PivotGame in recommendation.supportFamilies)
        assertTrue(InterventionFamily.PivotReading in recommendation.supportFamilies)
        assertTrue(InterventionFamily.MomentPlan in recommendation.supportFamilies)
    }

    @Test
    fun stressProducesCalmSupportPreference() {
        val recommendation = policy.recommendationsFor(
            OnboardingAnswers(triggers = listOf("stress")),
        ).recommendations.single()
        assertFalse(InterventionFamily.PivotGame in recommendation.supportFamilies)
        assertTrue(InterventionFamily.ShortPause in recommendation.supportFamilies)
    }

    @Test
    fun lateNightAndMorningAnswersProduceWindowReviews() {
        val state = policy.recommendationsFor(
            OnboardingAnswers(timing = listOf("late_at_night", "after_waking")),
        )
        assertTrue(state.recommendations.any {
            it.suggestionType == ProtectionCoachSuggestionType.CreateEveningWindow
        })
        assertTrue(state.recommendations.any {
            it.suggestionType == ProtectionCoachSuggestionType.CreateMorningWindow
        })
    }

    @Test
    fun noAnswerProducesNoFabricatedRecommendation() {
        assertEquals(emptyList<Any>(), policy.recommendationsFor(OnboardingAnswers()).recommendations)
    }

    @Test
    fun copyExplainsUserControlAndNoSilentConfigurationChange() {
        val state = policy.recommendationsFor(
            OnboardingAnswers(interrupting = listOf("social_media")),
        )
        assertEquals(
            "These are suggestions based on your answers. You can change them now or later.",
            state.explanation,
        )
        assertTrue(state.recommendations.single().body.contains("does not auto-protect"))
    }
}
