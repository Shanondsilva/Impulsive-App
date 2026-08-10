package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.FamiliarStepQualificationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepCandidate
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceSufficiency
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepExplanationCategory
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import kotlinx.coroutines.flow.first

enum class FamiliarStepConsideredSignal {
    ExistingRouteIdentity,
    BroadMomentCue,
    Completion,
    OptionalFeedback,
    RepeatObservation,
    Recency,
}

enum class FamiliarStepExcludedData {
    Age,
    Contacts,
    BrowsingHistory,
    Url,
    Domain,
    SearchTerms,
    PageContent,
    LocationUnlessApprovedInterventionRequiresIt,
    CloudProfiling,
}

data class FamiliarStepExplanation(
    val comparableCount: Int,
    val favourableCount: Int,
    val broadMomentCue: MomentCue?,
    val routeIdentity: FamiliarStepRouteIdentity,
    val observedPatternDisclaimer: String = ObservedPatternDisclaimer,
    val consideredSignals: Set<FamiliarStepConsideredSignal> =
        FamiliarStepConsideredSignal.entries.toSet(),
    val excludedData: Set<FamiliarStepExcludedData> =
        FamiliarStepExcludedData.entries.toSet(),
) {
    companion object {
        const val ObservedPatternDisclaimer =
            "This is an observed local pattern from a limited number of past support " +
                "moments. It is not a prediction, diagnosis or guarantee."
    }
}

object FamiliarStepExplanationService {
    fun explain(candidate: FamiliarStepCandidate): FamiliarStepExplanation =
        FamiliarStepExplanation(
            comparableCount = candidate.comparableCount,
            favourableCount = candidate.favourableCount,
            broadMomentCue = candidate.matchedCue,
            routeIdentity = candidate.routeIdentity,
        )
}

data class FamiliarStepHistoryItem(
    val routeIdentity: FamiliarStepRouteIdentity,
    val broadMomentCue: MomentCue?,
    val comparableCount: Int,
    val favourableCount: Int,
    val evidenceQuality: FamiliarStepEvidenceSufficiency =
        FamiliarStepEvidenceSufficiency.Qualified,
)

data class FamiliarStepHistorySnapshot(val items: List<FamiliarStepHistoryItem>)

/** Calm-time, bounded view derived on demand from the existing decision ledger. */
class FamiliarStepHistoryService(
    private val decisions: AdaptiveDecisionRepository,
    private val plans: MomentPlanRepository,
) {
    suspend fun snapshot(): FamiliarStepHistorySnapshot {
        val evidence = decisions.getRecentFamiliarStepEvidence(
            FamiliarStepQualificationPolicy.MaximumInspectedRecords,
        ).sortedByDescending { it.finalisedAtMillis }
            .take(FamiliarStepQualificationPolicy.MaximumInspectedRecords)
        val planRevisions = plans.observeEnabled().first().associate {
            it.planId to it.contentRevisionId
        }
        val currentProtocols = InterventionProtocolRegistry.contracts.mapTo(mutableSetOf()) {
            it.protocolId.value to it.version.value
        }
        val valid = evidence.filter { record ->
            val identity = record.routeIdentity
            (identity.protocolId to identity.protocolVersion) in currentProtocols &&
                (identity.momentPlanId == null ||
                    planRevisions[identity.momentPlanId] ==
                    identity.momentPlanContentRevisionId)
        }
        val cueGroups = valid.filter { it.momentCue != null }
            .groupBy { it.routeIdentity to it.momentCue }
            .mapNotNull { (key, records) -> qualifiedItem(key.first, key.second, records) }
        val broadGroups = valid.groupBy { it.routeIdentity }
            .mapNotNull { (identity, records) -> qualifiedItem(identity, null, records) }
        return FamiliarStepHistorySnapshot(
            (cueGroups + broadGroups)
                .distinctBy { it.routeIdentity to it.broadMomentCue }
                .sortedWith(
                    compareByDescending<FamiliarStepHistoryItem> {
                        it.favourableCount.toDouble() / it.comparableCount
                    }.thenByDescending { it.favourableCount }
                        .thenByDescending { it.comparableCount }
                        .thenBy { it.routeIdentity.protocolId }
                        .thenBy { it.routeIdentity.momentPlanId.orEmpty() }
                        .thenBy { it.broadMomentCue?.name.orEmpty() },
                )
                .take(MaximumHistoryItems),
        )
    }

    private fun qualifiedItem(
        identity: FamiliarStepRouteIdentity,
        cue: MomentCue?,
        records: List<FamiliarStepEvidenceRecord>,
    ): FamiliarStepHistoryItem? {
        val favourable = records.count(FamiliarStepQualificationPolicy::isFavourable)
        if (!FamiliarStepQualificationPolicy.qualifies(records.size, favourable)) return null
        return FamiliarStepHistoryItem(identity, cue, records.size, favourable)
    }

    companion object {
        const val MaximumHistoryItems = 12
    }
}

fun interface FamiliarStepDerivedCache {
    fun clear()
}

class FamiliarStepControls(
    private val resetCoordinator: AdaptiveResetCoordinator,
    private val preferences: AdaptivePreferenceRepository,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
    private val derivedCache: FamiliarStepDerivedCache = FamiliarStepDerivedCache {},
) {
    suspend fun clearAdaptiveHistory(): AdaptiveLifecycleResult {
        val result = resetCoordinator.resetPersonalLearning()
        if (result == AdaptiveLifecycleResult.Applied) derivedCache.clear()
        return result
    }

    suspend fun disablePersonalSuggestions() {
        setPersonalSuggestionsEnabled(false)
    }

    suspend fun setPersonalSuggestionsEnabled(enabled: Boolean) {
        val current = preferences.get()
        preferences.update(
            current.copy(personalSuggestionsEnabled = enabled),
            clock.nowMillis(),
        )
        derivedCache.clear()
    }
}
