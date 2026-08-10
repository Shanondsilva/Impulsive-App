package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle

sealed interface AdaptiveSupportCycleRecoveryResult {
    data class Restored(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleRecoveryResult

    data object NotFound : AdaptiveSupportCycleRecoveryResult
    data object InvalidPersistedStateCleared : AdaptiveSupportCycleRecoveryResult
    data object ExpiredCleared : AdaptiveSupportCycleRecoveryResult
    data object RevisionConflict : AdaptiveSupportCycleRecoveryResult
    data object PersistenceFailure : AdaptiveSupportCycleRecoveryResult
}

class AdaptiveSupportCycleRecovery(
    private val repository: AdaptiveSupportCycleRepository,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
) {
    suspend fun recover(): AdaptiveSupportCycleRecoveryResult {
        return when (val loaded = repository.load(clock.nowMillis())) {
            is AdaptiveSupportCycleLoadResult.Active -> restore(loaded.state)
            AdaptiveSupportCycleLoadResult.NotFound -> AdaptiveSupportCycleRecoveryResult.NotFound
            AdaptiveSupportCycleLoadResult.InvalidPersistedState ->
                AdaptiveSupportCycleRecoveryResult.InvalidPersistedStateCleared
            AdaptiveSupportCycleLoadResult.Expired ->
                AdaptiveSupportCycleRecoveryResult.ExpiredCleared
            AdaptiveSupportCycleLoadResult.PersistenceFailure ->
                AdaptiveSupportCycleRecoveryResult.PersistenceFailure
        }
    }

    private suspend fun restore(
        state: PersistedAdaptiveSupportCycle,
    ): AdaptiveSupportCycleRecoveryResult {
        if (state.cycle.transitionReason == AdaptiveSupportCycleTransitionReason.Restored) {
            return AdaptiveSupportCycleRecoveryResult.Restored(state)
        }
        return when (
            val updated = repository.update(
                cycleId = state.cycle.cycleId,
                expectedRevision = state.revision,
                cycle = state.cycle.copy(
                    transitionReason = AdaptiveSupportCycleTransitionReason.Restored,
                ),
                updatedAtEpochMillis = clock.nowMillis(),
            )
        ) {
            is AdaptiveSupportCycleMutationResult.Updated ->
                AdaptiveSupportCycleRecoveryResult.Restored(updated.state)
            is AdaptiveSupportCycleMutationResult.RevisionConflict ->
                AdaptiveSupportCycleRecoveryResult.RevisionConflict
            AdaptiveSupportCycleMutationResult.Expired ->
                AdaptiveSupportCycleRecoveryResult.ExpiredCleared
            AdaptiveSupportCycleMutationResult.InvalidPersistedState ->
                AdaptiveSupportCycleRecoveryResult.InvalidPersistedStateCleared
            else -> AdaptiveSupportCycleRecoveryResult.PersistenceFailure
        }
    }
}
