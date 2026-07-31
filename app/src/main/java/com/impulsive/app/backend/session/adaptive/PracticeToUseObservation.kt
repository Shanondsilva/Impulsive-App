package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRehearsalRepository

data class PracticeToUseObservation(
    val completedRehearsals: Int,
    val practisedPlanIds: Set<String>,
    val laterRealUseDecisionIds: Set<String>,
    val mostRecentCompletedRehearsal: MomentPlanRehearsal?,
) {
    val laterRealUseCount: Int
        get() = laterRealUseDecisionIds.size
}

object PracticeToUsePolicy {
    const val ObservationDays = 7L
    private const val MillisPerDay = 86_400_000L

    fun observe(
        rehearsals: List<MomentPlanRehearsal>,
        realUses: List<MomentPlanUseRecord>,
    ): PracticeToUseObservation {
        val completed = rehearsals.filter { it.completedAtMillis != null }
        val matchingUseIds = buildSet {
            completed.forEach { rehearsal ->
                val completedAt = checkNotNull(rehearsal.completedAtMillis)
                val deadline = completedAt + ObservationDays * MillisPerDay
                realUses.filterTo(mutableListOf()) { use ->
                    use.planId == rehearsal.planId &&
                        use.planContentRevisionId == rehearsal.planContentRevisionId &&
                        use.startedAtMillis >= completedAt &&
                        use.startedAtMillis <= deadline
                }.forEach { add(it.decisionId) }
            }
        }
        return PracticeToUseObservation(
            completedRehearsals = completed.size,
            practisedPlanIds = completed.mapTo(linkedSetOf()) { it.planId },
            laterRealUseDecisionIds = matchingUseIds,
            mostRecentCompletedRehearsal =
                completed.maxByOrNull { checkNotNull(it.completedAtMillis) },
        )
    }
}

class PracticeToUseAggregator(
    private val rehearsals: MomentPlanRehearsalRepository,
    private val decisions: AdaptiveDecisionRepository,
) {
    suspend fun load(): PracticeToUseObservation {
        val completed = rehearsals.getRecentCompleted(MaximumLocalRecords)
        val earliest = completed.minOfOrNull { checkNotNull(it.completedAtMillis) }
        val uses = earliest?.let { decisions.getMomentPlanUsesSince(it) }.orEmpty()
        return PracticeToUsePolicy.observe(completed, uses)
    }

    private companion object {
        const val MaximumLocalRecords = 2_000
    }
}
