package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.session.adaptive.PracticeToUsePolicy

data class WhatWorksSummary(
    val protectedMoments: Int,
    val supportOptionsStarted: Int,
    val supportOptionsCompleted: Int,
    val supportOptionsDismissed: Int,
    val feedbackAnswersProvided: Int,
    val momentPlansPractised: Int,
)

data class WhatWorksInterventionSummary(
    val intervention: InterventionFamily,
    val started: Int,
    val completed: Int,
    val dismissed: Int,
    val helped: Int,
    val helpedALittle: Int,
    val didNotHelp: Int,
    val wrongTiming: Int,
    val notAnswered: Int,
    val laterRepeatDetected: Int,
    val noLaterRepeatObserved: Int,
    val awaitingObservation: Int,
)

data class RecentSupportRecord(
    val decisionAtMillis: Long,
    val intervention: InterventionFamily,
    val outcome: EngagementOutcome,
    val feedback: FeedbackCode,
    val repeatObservation: RepeatObservation,
)

data class WhatWorksPracticeSummary(
    val completedRehearsals: Int,
    val plansPractised: Int,
    val laterRealUsesWithinSevenDays: Int,
    val mostRecentlyPractisedPlanTitle: String?,
    val hasRecentRehearsal: Boolean,
)

enum class EvidenceQualityTier(val plainLanguageExplanation: String) {
    CountOnly("A few recorded moments"),
    EarlyPattern("An early personal pattern"),
    ComparisonSupported("Enough recent history for a cautious comparison"),
}

data class WhatWorksForMeReport(
    val empty: Boolean,
    val summary: WhatWorksSummary,
    val interventions: List<WhatWorksInterventionSummary>,
    val withinOptionPatterns: List<String>,
    val primaryComparison: String?,
    val differentChoiceCount: Int,
    val practice: WhatWorksPracticeSummary,
    val recentHistory: List<RecentSupportRecord>,
    val evidenceQualityTier: EvidenceQualityTier,
)

object WhatWorksForMeBuilder {
    const val MinimumWithinOptionTerminalUses = 3
    const val RecentHistoryLimit = 5
    const val RecentRehearsalDays = 14L
    private const val MillisPerDay = 86_400_000L

    fun build(
        decisions: List<AdaptiveDecision>,
        rehearsals: List<MomentPlanRehearsal>,
        plans: List<MomentPlan>,
        nowMillis: Long,
    ): WhatWorksForMeReport {
        val actualDecisions = decisions.filter {
            it.assignment.actualIntervention != null
        }
        val outcomes = actualDecisions.map { it.toOutcome() }
        val completedRehearsals = rehearsals.filter {
            it.completedAtMillis != null
        }
        val realPlanUses = actualDecisions.mapNotNull { decision ->
            if (
                decision.assignment.actualIntervention != InterventionFamily.MomentPlan ||
                decision.assignment.momentPlanId == null ||
                decision.assignment.momentPlanUpdatedAtMillis == null ||
                decision.startedAtMillis == null
            ) {
                null
            } else {
                MomentPlanUseRecord(
                    decisionId = decision.decisionId,
                    planId = decision.assignment.momentPlanId,
                    planUpdatedAtMillis =
                        decision.assignment.momentPlanUpdatedAtMillis,
                    startedAtMillis = decision.startedAtMillis,
                    planContentRevisionId =
                        decision.assignment.actualPlanContentRevisionId
                            ?: return@mapNotNull null,
                )
            }
        }
        val practiceObservation = PracticeToUsePolicy.observe(
            completedRehearsals,
            realPlanUses,
        )
        val mostRecentPractice = practiceObservation.mostRecentCompletedRehearsal
        val mostRecentPlanTitle = mostRecentPractice?.let { rehearsal ->
            plans.firstOrNull {
                it.planId == rehearsal.planId &&
                    it.contentRevisionId == rehearsal.planContentRevisionId
            }?.title
        }

        val summaries = InterventionFamily.entries.mapNotNull { family ->
            val familyDecisions = actualDecisions.filter {
                it.assignment.actualIntervention == family
            }
            familyDecisions.takeIf { it.isNotEmpty() }?.let {
                WhatWorksInterventionSummary(
                    intervention = family,
                    started = it.count { decision -> decision.startedAtMillis != null },
                    completed = it.count { decision -> decision.completedAtMillis != null },
                    dismissed = it.count { decision -> decision.dismissedAtMillis != null },
                    helped = it.count { decision ->
                        decision.feedbackCode == FeedbackCode.Helped
                    },
                    helpedALittle = it.count { decision ->
                        decision.feedbackCode == FeedbackCode.HelpedALittle
                    },
                    didNotHelp = it.count { decision ->
                        decision.feedbackCode == FeedbackCode.DidNotHelp
                    },
                    wrongTiming = it.count { decision ->
                        decision.feedbackCode == FeedbackCode.WrongTiming
                    },
                    notAnswered = it.count { decision ->
                        decision.feedbackCode == FeedbackCode.NotProvided
                    },
                    laterRepeatDetected = it.count { decision ->
                        decision.repeatObservation == RepeatObservation.RepeatDetected &&
                            decision.observationFinalisedAtMillis != null
                    },
                    noLaterRepeatObserved = it.count { decision ->
                        decision.repeatObservation == RepeatObservation.NoRepeatDetected &&
                            decision.observationFinalisedAtMillis != null
                    },
                    awaitingObservation = it.count { decision ->
                        decision.observationFinalisedAtMillis == null
                    },
                )
            }
        }
        val insight = AdaptiveInsightBuilder.build(outcomes)
        val recentPracticeCutoff =
            nowMillis - RecentRehearsalDays * MillisPerDay

        val withinOptionPatterns = summaries.mapNotNull(::withinOptionPattern)
        val primaryComparison = insight.comparativeInsight?.copy
        return WhatWorksForMeReport(
            empty = actualDecisions.isEmpty() && completedRehearsals.isEmpty(),
            summary = WhatWorksSummary(
                protectedMoments = decisions.size,
                supportOptionsStarted = actualDecisions.count { it.startedAtMillis != null },
                supportOptionsCompleted = actualDecisions.count { it.completedAtMillis != null },
                supportOptionsDismissed = actualDecisions.count { it.dismissedAtMillis != null },
                feedbackAnswersProvided = actualDecisions.count {
                    it.feedbackCode != FeedbackCode.NotProvided
                },
                momentPlansPractised = completedRehearsals.size,
            ),
            interventions = summaries,
            withinOptionPatterns = withinOptionPatterns,
            primaryComparison = primaryComparison,
            differentChoiceCount = actualDecisions.count {
                it.assignment.userOverrodeSuggestion
            },
            practice = WhatWorksPracticeSummary(
                completedRehearsals = practiceObservation.completedRehearsals,
                plansPractised = practiceObservation.practisedPlanIds.size,
                laterRealUsesWithinSevenDays = practiceObservation.laterRealUseCount,
                mostRecentlyPractisedPlanTitle = mostRecentPlanTitle,
                hasRecentRehearsal = completedRehearsals.any {
                    val completedAt = checkNotNull(it.completedAtMillis)
                    completedAt >= recentPracticeCutoff && completedAt <= nowMillis
                },
            ),
            recentHistory = outcomes
                .filter {
                    it.engagementOutcome == EngagementOutcome.Completed ||
                        it.engagementOutcome == EngagementOutcome.Dismissed
                }
                .sortedByDescending { it.decisionAtMillis }
                .take(RecentHistoryLimit)
                .map {
                    RecentSupportRecord(
                        decisionAtMillis = it.decisionAtMillis,
                        intervention = checkNotNull(it.actualIntervention),
                        outcome = it.engagementOutcome,
                        feedback = it.feedbackCode,
                        repeatObservation = it.repeatObservation,
                    )
                },
            evidenceQualityTier = when {
                primaryComparison != null -> EvidenceQualityTier.ComparisonSupported
                withinOptionPatterns.isNotEmpty() -> EvidenceQualityTier.EarlyPattern
                else -> EvidenceQualityTier.CountOnly
            },
        )
    }

    private fun withinOptionPattern(
        summary: WhatWorksInterventionSummary,
    ): String? {
        val terminalUses = summary.completed + summary.dismissed
        if (terminalUses < MinimumWithinOptionTerminalUses) return null
        return "You completed ${summary.intervention.displayName()} " +
            "${summary.completed} of the $terminalUses times you used it."
    }

    private fun AdaptiveDecision.toOutcome(): AdaptiveOutcomeRecord =
        AdaptiveOutcomeRecord(
            decisionId = decisionId,
            actualIntervention = assignment.actualIntervention,
            selectedCue = momentCue,
            feedbackCode = feedbackCode,
            engagementOutcome = when {
                completedAtMillis != null -> EngagementOutcome.Completed
                dismissedAtMillis != null -> EngagementOutcome.Dismissed
                startedAtMillis != null -> EngagementOutcome.StartedNotCompleted
                else -> EngagementOutcome.NotStarted
            },
            repeatObservation = repeatObservation,
            decisionAtMillis = createdAtMillis,
            observationFinalisedAtMillis = observationFinalisedAtMillis,
        )

    fun InterventionFamily.displayName(): String = when (this) {
        InterventionFamily.ShortPause -> "Short Pause"
        InterventionFamily.PivotGame -> "Pivot Games"
        InterventionFamily.PivotReading -> "Reset Reading"
        InterventionFamily.MomentPlan -> "Moment Plans"
    }
}
