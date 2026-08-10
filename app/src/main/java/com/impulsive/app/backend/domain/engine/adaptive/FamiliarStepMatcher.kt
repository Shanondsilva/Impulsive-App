package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepCandidate
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepMatchResult
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepNoMatchReason
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation

object FamiliarStepQualificationPolicy {
    const val MinimumComparableRecords = 4
    const val MinimumFavourableRecords = 3
    const val MaximumInspectedRecords = AdaptiveMomentLimits.RecentEvidenceLimit

    fun isFavourable(record: FamiliarStepEvidenceRecord): Boolean =
        record.engagementOutcome == EngagementOutcome.Completed &&
            record.repeatObservation == RepeatObservation.NoRepeatDetected &&
            record.feedbackCode in setOf(FeedbackCode.Helped, FeedbackCode.HelpedALittle)

    fun qualifies(comparable: Int, favourable: Int): Boolean =
        comparable >= MinimumComparableRecords &&
            favourable >= MinimumFavourableRecords &&
            favourable * 2 > comparable
}

data class FamiliarStepMatchInput(
    val momentIntensity: MomentIntensity,
    val personalSuggestionsEnabled: Boolean,
    val eligibleInterventions: Set<InterventionFamily>,
    val currentMomentCue: MomentCue?,
    val evidence: List<FamiliarStepEvidenceRecord>,
    val currentProtocolIdentities: Set<Pair<String, Int>>,
    val currentMomentPlanRevisions: Map<String, String>,
    val privacySafeEvidence: Boolean = true,
)

object FamiliarStepMatcher {
    fun match(input: FamiliarStepMatchInput): FamiliarStepMatchResult {
        if (input.momentIntensity == MomentIntensity.FirstAttempt) {
            return noMatch(FamiliarStepNoMatchReason.FirstAttempt)
        }
        if (!input.personalSuggestionsEnabled) {
            return noMatch(FamiliarStepNoMatchReason.PersonalSuggestionsDisabled)
        }
        if (!input.privacySafeEvidence) {
            return noMatch(FamiliarStepNoMatchReason.PrivacyUnsafeEvidence)
        }
        if (input.eligibleInterventions.isEmpty()) {
            return noMatch(FamiliarStepNoMatchReason.NoEligibleRoute)
        }

        val bounded = input.evidence
            .sortedByDescending { it.finalisedAtMillis }
            .take(FamiliarStepQualificationPolicy.MaximumInspectedRecords)

        /*
         * An empty history means no route has accumulated enough evidence.
         *
         * It does not mean the current decision lacks eligible interventions. This
         * check must occur before evidence is filtered by supported or eligible route
         * identity, otherwise an empty history is incorrectly classified as
         * NoEligibleRoute.
         */
        if (bounded.isEmpty()) {
            return noMatch(FamiliarStepNoMatchReason.InsufficientEvidence)
        }

        val supported = bounded.filter { it.routeIdentity.intervention in SupportedInterventions }
        if (supported.size != bounded.size) {
            return noMatch(FamiliarStepNoMatchReason.UnsupportedIntervention)
        }
        val eligible = supported.filter { it.routeIdentity.intervention in input.eligibleInterventions }
        if (eligible.isEmpty()) return noMatch(FamiliarStepNoMatchReason.NoEligibleRoute)

        val protocolCurrent = eligible.filter {
            (it.routeIdentity.protocolId to it.routeIdentity.protocolVersion) in
                input.currentProtocolIdentities
        }
        if (protocolCurrent.isEmpty()) return noMatch(FamiliarStepNoMatchReason.StaleProtocol)
        val planCurrent = protocolCurrent.filter { record ->
            val identity = record.routeIdentity
            identity.momentPlanId == null ||
                input.currentMomentPlanRevisions[identity.momentPlanId] ==
                identity.momentPlanContentRevisionId
        }
        if (planCurrent.isEmpty()) return noMatch(FamiliarStepNoMatchReason.StalePlanRevision)

        val broadGroups = planCurrent.groupBy { it.routeIdentity }
        val cueGroups = input.currentMomentCue?.let { cue ->
            planCurrent.filter { it.momentCue == cue }.groupBy { it.routeIdentity }
        }.orEmpty()
        val groups = cueGroups.takeIf {
            it.values.any { records ->
                records.size >= FamiliarStepQualificationPolicy.MinimumComparableRecords
            }
        } ?: broadGroups

        val candidates = groups.map { (identity, records) ->
            val favourable = records.filter(FamiliarStepQualificationPolicy::isFavourable)
            FamiliarStepCandidate(
                routeIdentity = identity,
                comparableCount = records.size,
                favourableCount = favourable.size,
                matchedCue = input.currentMomentCue.takeIf { groups === cueGroups },
                mostRecentFavourableAtMillis = favourable.maxOfOrNull { it.finalisedAtMillis } ?: 0L,
            )
        }
        if (candidates.none { it.comparableCount >= FamiliarStepQualificationPolicy.MinimumComparableRecords }) {
            return noMatch(FamiliarStepNoMatchReason.InsufficientEvidence)
        }
        val qualified = candidates.filter {
            FamiliarStepQualificationPolicy.qualifies(it.comparableCount, it.favourableCount)
        }
        if (qualified.isEmpty()) return noMatch(FamiliarStepNoMatchReason.NoFavourableMajority)

        val selected = qualified.sortedWith(
            compareByDescending<FamiliarStepCandidate> {
                it.favourableCount.toDouble() / it.comparableCount
            }.thenByDescending { it.favourableCount }
                .thenByDescending { it.comparableCount }
                .thenByDescending { it.mostRecentFavourableAtMillis }
                .thenBy { it.routeIdentity.protocolId }
                .thenBy { it.routeIdentity.momentPlanId.orEmpty() },
        ).first()
        return FamiliarStepMatchResult.Match(selected)
    }

    private val SupportedInterventions = setOf(
        InterventionFamily.ShortPause,
        InterventionFamily.PivotGame,
        InterventionFamily.PivotReading,
        InterventionFamily.MomentPlan,
    )

    private fun noMatch(reason: FamiliarStepNoMatchReason) =
        FamiliarStepMatchResult.NoMatch(reason)
}
