package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import kotlin.math.abs

data class AdaptiveInterventionSummary(
    val intervention: InterventionFamily,
    val actualUses: Int,
    val completions: Int,
    val dismissals: Int,
    val helped: Int,
    val helpedALittle: Int,
    val didNotHelp: Int,
    val wrongTiming: Int,
)

data class AdaptiveComparativeInsight(
    val firstIntervention: InterventionFamily,
    val secondIntervention: InterventionFamily,
    val firstHelpfulCount: Int,
    val firstFeedbackCount: Int,
    val secondHelpfulCount: Int,
    val secondFeedbackCount: Int,
    val copy: String,
)

data class AdaptiveDashboardInsight(
    val headline: String,
    val completedInterventionCount: Int,
    val finalisedRecordCount: Int,
    val summaries: List<AdaptiveInterventionSummary>,
    val comparativeInsight: AdaptiveComparativeInsight?,
)

object AdaptiveInsightBuilder {
    const val MinimumCompletedInterventions = 5
    const val MinimumComparisonActualUses = 8
    const val MinimumComparisonFeedbackResponses = 4
    const val MinimumHelpfulRateDifference = 0.25

    private val ComparisonOrder = listOf(
        InterventionFamily.PivotGame,
        InterventionFamily.PivotReading,
        InterventionFamily.MomentPlan,
    )

    fun build(history: List<AdaptiveOutcomeRecord>): AdaptiveDashboardInsight {
        val finalisedActualUses = history.filter {
            it.isFinalised && it.actualIntervention != null
        }
        val completedCount = finalisedActualUses.count {
            it.engagementOutcome == EngagementOutcome.Completed
        }
        val summaries = InterventionFamily.entries.mapNotNull { intervention ->
            val records = finalisedActualUses.filter {
                it.actualIntervention == intervention
            }
            records.takeIf { it.isNotEmpty() }?.toSummary(intervention)
        }
        if (completedCount < MinimumCompletedInterventions) {
            return AdaptiveDashboardInsight(
                headline = "Impulsive is learning what helps you.",
                completedInterventionCount = completedCount,
                finalisedRecordCount = finalisedActualUses.size,
                summaries = summaries,
                comparativeInsight = null,
            )
        }

        return AdaptiveDashboardInsight(
            headline = "Your recent support moments",
            completedInterventionCount = completedCount,
            finalisedRecordCount = finalisedActualUses.size,
            summaries = summaries,
            comparativeInsight = buildComparison(finalisedActualUses),
        )
    }

    private fun buildComparison(
        records: List<AdaptiveOutcomeRecord>,
    ): AdaptiveComparativeInsight? {
        val candidates = buildList {
            ComparisonOrder.forEachIndexed { firstIndex, first ->
                ComparisonOrder.drop(firstIndex + 1).forEach { second ->
                    comparisonCandidate(
                        first = first,
                        second = second,
                        records = records,
                    )?.let(::add)
                }
            }
        }
        return candidates.sortedWith(
            compareByDescending<ComparisonCandidate> {
                it.combinedActualUses
            }.thenByDescending {
                it.helpfulRateDifference
            }.thenBy {
                ComparisonOrder.indexOf(it.first)
            }.thenBy {
                ComparisonOrder.indexOf(it.second)
            },
        ).firstOrNull()?.toInsight()
    }

    private fun comparisonCandidate(
        first: InterventionFamily,
        second: InterventionFamily,
        records: List<AdaptiveOutcomeRecord>,
    ): ComparisonCandidate? {
        val firstRecords = records.filter { it.actualIntervention == first }
        val secondRecords = records.filter { it.actualIntervention == second }
        if (
            firstRecords.size < MinimumComparisonActualUses ||
            secondRecords.size < MinimumComparisonActualUses
        ) {
            return null
        }
        val firstFeedback = firstRecords.explicitFeedback()
        val secondFeedback = secondRecords.explicitFeedback()
        if (
            firstFeedback.size < MinimumComparisonFeedbackResponses ||
            secondFeedback.size < MinimumComparisonFeedbackResponses
        ) {
            return null
        }
        val firstHelpful = firstFeedback.count { it.wasMarkedHelpful() }
        val secondHelpful = secondFeedback.count { it.wasMarkedHelpful() }
        val firstRate = firstHelpful.toDouble() / firstFeedback.size
        val secondRate = secondHelpful.toDouble() / secondFeedback.size
        val difference = abs(firstRate - secondRate)
        if (difference < MinimumHelpfulRateDifference) {
            return null
        }
        return ComparisonCandidate(
            first = first,
            second = second,
            firstActualUses = firstRecords.size,
            secondActualUses = secondRecords.size,
            firstHelpful = firstHelpful,
            firstFeedback = firstFeedback.size,
            secondHelpful = secondHelpful,
            secondFeedback = secondFeedback.size,
            helpfulRateDifference = difference,
        )
    }

    private fun List<AdaptiveOutcomeRecord>.toSummary(
        intervention: InterventionFamily,
    ): AdaptiveInterventionSummary = AdaptiveInterventionSummary(
        intervention = intervention,
        actualUses = size,
        completions = count { it.engagementOutcome == EngagementOutcome.Completed },
        dismissals = count { it.engagementOutcome == EngagementOutcome.Dismissed },
        helped = count { it.feedbackCode == FeedbackCode.Helped },
        helpedALittle = count { it.feedbackCode == FeedbackCode.HelpedALittle },
        didNotHelp = count { it.feedbackCode == FeedbackCode.DidNotHelp },
        wrongTiming = count { it.feedbackCode == FeedbackCode.WrongTiming },
    )

    private fun List<AdaptiveOutcomeRecord>.explicitFeedback(): List<AdaptiveOutcomeRecord> =
        filter {
            it.feedbackCode != FeedbackCode.NotProvided &&
                it.feedbackCode != FeedbackCode.WrongTiming
        }

    private fun AdaptiveOutcomeRecord.wasMarkedHelpful(): Boolean =
        feedbackCode == FeedbackCode.Helped ||
            feedbackCode == FeedbackCode.HelpedALittle

    private fun InterventionFamily.displayName(): String = when (this) {
        InterventionFamily.ShortPause -> "Short Pause"
        InterventionFamily.PivotGame -> "Game"
        InterventionFamily.PivotReading -> "Reading"
        InterventionFamily.MomentPlan -> "My Moment Plan"
    }

    private data class ComparisonCandidate(
        val first: InterventionFamily,
        val second: InterventionFamily,
        val firstActualUses: Int,
        val secondActualUses: Int,
        val firstHelpful: Int,
        val firstFeedback: Int,
        val secondHelpful: Int,
        val secondFeedback: Int,
        val helpfulRateDifference: Double,
    ) {
        val combinedActualUses: Int
            get() = firstActualUses + secondActualUses

        fun toInsight(): AdaptiveComparativeInsight {
            val firstName = first.displayName()
            val secondName = second.displayName()
            return AdaptiveComparativeInsight(
                firstIntervention = first,
                secondIntervention = second,
                firstHelpfulCount = firstHelpful,
                firstFeedbackCount = firstFeedback,
                secondHelpfulCount = secondHelpful,
                secondFeedbackCount = secondFeedback,
                copy =
                    "An early pattern in recent records: $firstName was marked helpful or " +
                        "somewhat helpful $firstHelpful of $firstFeedback times, and " +
                        "$secondName $secondHelpful of $secondFeedback times. " +
                        "This is not a conclusion.",
            )
        }
    }
}
