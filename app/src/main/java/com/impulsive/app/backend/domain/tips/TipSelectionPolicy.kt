package com.impulsive.app.backend.domain.tips

class TipSelectionPolicy {
    fun select(
        catalogue: List<ImpulsiveTip>,
        context: TipSelectionContext,
    ): TipSelectionResult {
        val eligible = catalogue
            .asSequence()
            .filter(ImpulsiveTip::available)
            .filterNot(ImpulsiveTip::obsolete)
            .filterNot { it.id in context.dismissedTipIds }
            .filterNot { it.id == context.tipShownThisSession }
            .filter { tip ->
                tip.requiredFeature == null || tip.requiredFeature in context.availableFeatures
            }
            .toList()

        val onboardingTags = audienceTagsFor(context.onboardingAnswerIds)
        val stableOrder = compareByDescending<ImpulsiveTip> { it.priority }
            .thenBy { it.id.value }

        eligible
            .filterNot { it.id in context.viewedTipIds }
            .filter { it.audienceTags.any(onboardingTags::contains) }
            .sortedWith(stableOrder)
            .firstOrNull()
            ?.let { tip ->
                return TipSelectionResult(
                    tip = tip,
                    reason = TipSelectionReason.OnboardingMatch,
                    whyYouAreSeeingThis =
                        "This idea matches a choice you made during your private on-device setup.",
                )
            }

        eligible
            .filterNot { it.id in context.viewedTipIds }
            .filter { it.requiredFeature in context.configurationOpportunities }
            .sortedWith(stableOrder)
            .firstOrNull()
            ?.let { tip ->
                return TipSelectionResult(
                    tip = tip,
                    reason = TipSelectionReason.ConfigurationOpportunity,
                    whyYouAreSeeingThis =
                        "This idea relates to an Impulsive feature available on this device.",
                )
            }

        eligible
            .filterNot { it.id in context.viewedTipIds }
            .filter { TipAudienceTag.General in it.audienceTags }
            .sortedWith(stableOrder)
            .firstOrNull()
            ?.let { tip ->
                return TipSelectionResult(
                    tip = tip,
                    reason = TipSelectionReason.General,
                    whyYouAreSeeingThis = null,
                )
            }

        eligible
            .sortedWith(
                compareBy<ImpulsiveTip> {
                    context.lastShownEpochDayByTip[it.id] ?: Long.MIN_VALUE
                }.then(stableOrder),
            )
            .firstOrNull()
            ?.let { tip ->
                return TipSelectionResult(
                    tip = tip,
                    reason = TipSelectionReason.LeastRecentlyShown,
                    whyYouAreSeeingThis = null,
                )
            }

        return TipSelectionResult(
            tip = null,
            reason = TipSelectionReason.Fallback,
            whyYouAreSeeingThis = null,
        )
    }

    fun audienceTagsFor(answerIds: Set<String>): Set<TipAudienceTag> = buildSet {
        answerIds.forEach { answer ->
            when (answer) {
                "social_media" -> add(TipAudienceTag.SocialMedia)
                "browser_habit", "browser_search" -> add(TipAudienceTag.BrowserSearch)
                "late_night_phone", "late_at_night" -> add(TipAudienceTag.LateNight)
                "right_after_waking" -> add(TipAudienceTag.Morning)
                "boredom", "when_bored" -> add(TipAudienceTag.Boredom)
                "stress", "when_stressed" -> add(TipAudienceTag.Stress)
                "being_alone", "alone_on_phone" -> add(TipAudienceTag.BeingAlone)
                "trouble_sleeping" -> add(TipAudienceTag.TroubleSleeping)
                "compulsive_scrolling" -> add(TipAudienceTag.CompulsiveScrolling)
                "notice_triggers" -> add(TipAudienceTag.NoticeTriggers)
                "daily_reset_habit" -> add(TipAudienceTag.DailyResetHabit)
                "cut_down_by_half", "cut_down_a_little" -> add(TipAudienceTag.ReduceUse)
            }
        }
    }
}
