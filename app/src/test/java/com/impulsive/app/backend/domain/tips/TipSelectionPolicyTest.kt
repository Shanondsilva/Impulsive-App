package com.impulsive.app.backend.domain.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TipSelectionPolicyTest {
    private val policy = TipSelectionPolicy()

    @Test fun socialMediaOnboardingSelectsSocialTip() =
        assertSelected("social", setOf("social_media"), tips(tag = TipAudienceTag.SocialMedia))

    @Test fun browserSearchOnboardingSelectsBrowserTip() =
        assertSelected("browser", setOf("browser_search"), tips(tag = TipAudienceTag.BrowserSearch))

    @Test fun browserHabitOnboardingSelectsBrowserTip() =
        assertSelected("browser", setOf("browser_habit"), tips(tag = TipAudienceTag.BrowserSearch))

    @Test fun lateNightTimingSelectsEveningTip() =
        assertSelected("late", setOf("late_at_night"), tips(tag = TipAudienceTag.LateNight))

    @Test fun troubleSleepingSelectsSleepTip() =
        assertSelected("sleep", setOf("trouble_sleeping"), tips(tag = TipAudienceTag.TroubleSleeping))

    @Test fun boredomSelectsSmallActionTip() =
        assertSelected("boredom", setOf("boredom"), tips(tag = TipAudienceTag.Boredom))

    @Test fun stressSelectsCalmNonClinicalTip() {
        val result = policy.select(tips(tag = TipAudienceTag.Stress), context("stress"))
        assertEquals("stress", result.tip?.id?.value)
        assertFalse(result.tip?.whyThisMayHelp.orEmpty().contains("diagnos", ignoreCase = true))
    }

    @Test fun beingAloneDoesNotInferDiagnosis() {
        val result = policy.select(tips(tag = TipAudienceTag.BeingAlone), context("being_alone"))
        assertEquals("alone", result.tip?.id?.value)
        assertFalse(result.tip?.title.orEmpty().contains("disorder", ignoreCase = true))
    }

    @Test fun unseenMatchingTipOutranksGeneral() {
        val selected = policy.select(
            listOf(tip("general", TipAudienceTag.General, priority = 100), tip("social", TipAudienceTag.SocialMedia)),
            context("social_media"),
        )
        assertEquals("social", selected.tip?.id?.value)
    }

    @Test fun dismissedTipsAreExcluded() {
        val selected = policy.select(
            listOf(tip("social", TipAudienceTag.SocialMedia), tip("general", TipAudienceTag.General)),
            context("social_media").copy(dismissedTipIds = setOf(ImpulsiveTipId("social"))),
        )
        assertEquals("general", selected.tip?.id?.value)
    }

    @Test fun viewedMatchingTipRotatesAfterEligibleUnseenTip() {
        val selected = policy.select(
            listOf(tip("first", TipAudienceTag.SocialMedia), tip("second", TipAudienceTag.SocialMedia)),
            context("social_media").copy(viewedTipIds = setOf(ImpulsiveTipId("first"))),
        )
        assertEquals("second", selected.tip?.id?.value)
    }

    @Test fun selectionIsDeterministic() {
        val catalogue = listOf(tip("z_tip", TipAudienceTag.General), tip("a_tip", TipAudienceTag.General))
        val results = List(20) { policy.select(catalogue, TipSelectionContext()).tip?.id }
        assertEquals(1, results.distinct().size)
        assertEquals(ImpulsiveTipId("a_tip"), results.first())
    }

    @Test fun identicalTipShownThisSessionIsExcluded() {
        val selected = policy.select(
            listOf(tip("first", TipAudienceTag.General), tip("second", TipAudienceTag.General)),
            TipSelectionContext(tipShownThisSession = ImpulsiveTipId("first")),
        )
        assertEquals("second", selected.tip?.id?.value)
    }

    @Test fun unavailableRequiredFeatureIsExcluded() {
        val selected = policy.select(
            listOf(
                tip("website", TipAudienceTag.BrowserSearch, required = TipFeature.WebsiteProtection),
                tip("general", TipAudienceTag.General),
            ),
            context("browser_search").copy(availableFeatures = emptySet()),
        )
        assertEquals("general", selected.tip?.id?.value)
    }

    @Test fun obsoleteInstructionsAreExcluded() {
        val selected = policy.select(
            listOf(tip("old", TipAudienceTag.General).copy(obsolete = true)),
            TipSelectionContext(),
        )
        assertNull(selected.tip)
    }

    @Test fun configurationOpportunityOutranksGeneral() {
        val selected = policy.select(
            listOf(
                tip("website", TipAudienceTag.BrowserSearch, required = TipFeature.WebsiteProtection),
                tip("general", TipAudienceTag.General, priority = 100),
            ),
            TipSelectionContext(configurationOpportunities = setOf(TipFeature.WebsiteProtection)),
        )
        assertEquals("website", selected.tip?.id?.value)
        assertEquals(TipSelectionReason.ConfigurationOpportunity, selected.reason)
    }

    @Test fun unseenGeneralIsThirdPriorityTier() {
        val selected = policy.select(listOf(tip("general", TipAudienceTag.General)), TipSelectionContext())
        assertEquals(TipSelectionReason.General, selected.reason)
    }

    @Test fun leastRecentlyShownTipIsUsedAfterUnseenTips() {
        val first = tip("first", TipAudienceTag.General)
        val second = tip("second", TipAudienceTag.General)
        val selected = policy.select(
            listOf(first, second),
            TipSelectionContext(
                viewedTipIds = setOf(first.id, second.id),
                lastShownEpochDayByTip = mapOf(first.id to 20L, second.id to 10L),
            ),
        )
        assertEquals(second.id, selected.tip?.id)
        assertEquals(TipSelectionReason.LeastRecentlyShown, selected.reason)
    }

    @Test fun currentWeekOneIdsMapWithoutRenaming() {
        assertTrue(TipAudienceTag.NoticeTriggers in policy.audienceTagsFor(setOf("notice_triggers")))
        assertTrue(TipAudienceTag.DailyResetHabit in policy.audienceTagsFor(setOf("daily_reset_habit")))
        assertTrue(TipAudienceTag.ReduceUse in policy.audienceTagsFor(setOf("cut_down_by_half")))
        assertTrue(TipAudienceTag.ReduceUse in policy.audienceTagsFor(setOf("cut_down_a_little")))
    }

    @Test fun stableIdsRejectRouteUnsafeValues() {
        assertThrows(IllegalArgumentException::class.java) { ImpulsiveTipId("tip/with/private/data") }
    }

    @Test fun selectionResultContainsNoRawOnboardingAnswer() {
        val result = policy.select(tips(tag = TipAudienceTag.SocialMedia), context("social_media"))
        assertFalse(result.whyYouAreSeeingThis.orEmpty().contains("social_media"))
    }

    private fun assertSelected(expected: String, answers: Set<String>, catalogue: List<ImpulsiveTip>) {
        assertEquals(expected, policy.select(catalogue, TipSelectionContext(onboardingAnswerIds = answers)).tip?.id?.value)
    }

    private fun context(answer: String) = TipSelectionContext(onboardingAnswerIds = setOf(answer))

    private fun tips(tag: TipAudienceTag): List<ImpulsiveTip> {
        val id = when (tag) {
            TipAudienceTag.BrowserSearch -> "browser"
            TipAudienceTag.LateNight -> "late"
            TipAudienceTag.TroubleSleeping -> "sleep"
            TipAudienceTag.Boredom -> "boredom"
            TipAudienceTag.Stress -> "stress"
            TipAudienceTag.BeingAlone -> "alone"
            else -> "social"
        }
        return listOf(tip(id, tag), tip("general", TipAudienceTag.General))
    }

    private fun tip(
        id: String,
        tag: TipAudienceTag,
        priority: Int = 50,
        required: TipFeature? = null,
    ) = ImpulsiveTip(
        id = ImpulsiveTipId(id),
        category = TipCategory.General,
        title = "A small practical idea",
        summary = "Try one optional adjustment.",
        overviewSteps = listOf("Review it", "Choose whether to use it", "Adjust later"),
        whyThisMayHelp = "This may add a small pause without making assumptions about you.",
        audienceTags = setOf(tag),
        action = TipAction.None,
        source = TipSource("Test", "local", "30 July 2026"),
        isExternalInstruction = false,
        priority = priority,
        requiredFeature = required,
    )
}
