package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation

data class AdaptiveUtilityBreakdown(
    val intervention: InterventionFamily,
    val finalisedDecisionCount: Int,
    val usedCueMatchedHistory: Boolean,
    val evidencePoints: Double,
    val shrunkEvidenceScore: Double,
    val wrongTimingRate: Double,
    val receptivityPenalty: Double,
    val fatiguePenalty: Double,
    val cueMatchBonus: Double,
    val preferredPlanBonus: Double,
) {
    val finalUtility: Double =
        shrunkEvidenceScore -
            receptivityPenalty -
            fatiguePenalty +
            cueMatchBonus +
            preferredPlanBonus
}

object AdaptiveUtilityPolicy {
    const val FeedbackHelpedPoints = 2.0
    const val FeedbackHelpedALittlePoints = 1.0
    const val FeedbackDidNotHelpPoints = -2.0
    const val CompletedPoints = 0.5
    const val DismissedPoints = -0.5
    const val StartedNotCompletedPoints = -0.25
    const val NoRepeatPoints = 0.25
    const val RepeatDetectedPoints = -0.25
    const val ShrinkageDenominator = 4.0
    const val ReceptivityWeight = 0.5
    const val CueMatchBonus = 1.0
    const val PreferredPlanBonus = 0.25

    fun score(
        intervention: InterventionFamily,
        selectedCue: MomentCue?,
        history: List<AdaptiveOutcomeRecord>,
        recentActualSelections: List<InterventionFamily>,
        momentPlanMatchesCue: Boolean = false,
        momentPlanIsPreferredForCue: Boolean = false,
    ): AdaptiveUtilityBreakdown {
        val broadHistory = history
            .asSequence()
            .filter { record ->
                record.isFinalised && record.actualIntervention == intervention
            }
            .sortedByDescending { it.decisionAtMillis }
            .take(AdaptiveMomentLimits.RecentEvidenceLimit)
            .toList()
        val cueMatchedHistory = selectedCue?.let { cue ->
            broadHistory.filter { it.selectedCue == cue }
        }.orEmpty()
        val useCueMatchedHistory =
            selectedCue != null &&
                cueMatchedHistory.size >= AdaptiveMomentLimits.CueMatchedEvidenceMinimum
        val evidenceHistory = if (useCueMatchedHistory) cueMatchedHistory else broadHistory
        val evidencePoints = evidenceHistory.sumOf(::evidencePoints)
        val shrunkEvidence =
            evidencePoints / (evidenceHistory.size + ShrinkageDenominator)
        val wrongTimingCount = evidenceHistory.count {
            it.feedbackCode == FeedbackCode.WrongTiming
        }
        val wrongTimingRate =
            (wrongTimingCount + 1.0) / (evidenceHistory.size + 2.0)
        val receptivityPenalty = ReceptivityWeight * wrongTimingRate
        val cueBonus = if (
            intervention == InterventionFamily.MomentPlan &&
            selectedCue != null &&
            momentPlanMatchesCue
        ) {
            CueMatchBonus
        } else {
            0.0
        }
        val preferredBonus = if (
            cueBonus > 0.0 &&
            momentPlanIsPreferredForCue
        ) {
            PreferredPlanBonus
        } else {
            0.0
        }

        return AdaptiveUtilityBreakdown(
            intervention = intervention,
            finalisedDecisionCount = evidenceHistory.size,
            usedCueMatchedHistory = useCueMatchedHistory,
            evidencePoints = evidencePoints,
            shrunkEvidenceScore = shrunkEvidence,
            wrongTimingRate = wrongTimingRate,
            receptivityPenalty = receptivityPenalty,
            fatiguePenalty = AdaptiveFatiguePolicy.penalty(
                intervention = intervention,
                recentActualSelections = recentActualSelections,
            ),
            cueMatchBonus = cueBonus,
            preferredPlanBonus = preferredBonus,
        )
    }

    fun evidencePoints(record: AdaptiveOutcomeRecord): Double =
        feedbackPoints(record.feedbackCode) +
            engagementPoints(record.engagementOutcome) +
            repeatObservationPoints(record.repeatObservation)

    fun feedbackPoints(feedbackCode: FeedbackCode): Double = when (feedbackCode) {
        FeedbackCode.Helped -> FeedbackHelpedPoints
        FeedbackCode.HelpedALittle -> FeedbackHelpedALittlePoints
        FeedbackCode.DidNotHelp -> FeedbackDidNotHelpPoints
        FeedbackCode.WrongTiming,
        FeedbackCode.NotProvided,
        -> 0.0
    }

    fun engagementPoints(outcome: EngagementOutcome): Double = when (outcome) {
        EngagementOutcome.Completed -> CompletedPoints
        EngagementOutcome.Dismissed -> DismissedPoints
        EngagementOutcome.StartedNotCompleted -> StartedNotCompletedPoints
        EngagementOutcome.NotStarted -> 0.0
    }

    fun repeatObservationPoints(observation: RepeatObservation): Double = when (observation) {
        RepeatObservation.NoRepeatDetected -> NoRepeatPoints
        RepeatObservation.RepeatDetected -> RepeatDetectedPoints
        RepeatObservation.NotFinalised -> 0.0
    }
}
