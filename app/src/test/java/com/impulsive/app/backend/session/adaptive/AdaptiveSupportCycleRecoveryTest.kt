package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveSupportCycleRecoveryTest {
    @Test
    fun validActiveCycleIsMarkedRestoredWithoutAddingDowntimeConsumption() = runBlocking {
        val original = AdaptiveSupportCycle(
            cycleId = "cycle-1",
            decisionId = "decision-1",
            protectionIncidentToken = "incident-1",
            initialDurationMillis = 90_000L,
            consumedDurationMillis = 40_000L,
        )
        val repository = FakeRepository(
            PersistedAdaptiveSupportCycle(original, 100L, 100L, 1_000L, 4L),
        )

        val result = AdaptiveSupportCycleRecovery(repository, AdaptiveClock { 200L }).recover()
            as AdaptiveSupportCycleRecoveryResult.Restored

        assertEquals(40_000L, result.state.cycle.consumedDurationMillis)
        assertEquals(
            AdaptiveSupportCycleTransitionReason.Restored,
            result.state.cycle.transitionReason,
        )
        assertEquals(5L, result.state.revision)
    }

    /**
     * Recovery rewrites the transition reason to Restored, which is exactly why
     * the first explicit alternative request cannot be inferred from
     * transitionReason. The durable count must survive process recreation.
     */
    @Test
    fun recoveryPreservesTheDurableAlternativeRequestCount() = runBlocking {
        val original = AdaptiveSupportCycle(
            cycleId = "cycle-1",
            decisionId = "decision-1",
            protectionIncidentToken = "incident-1",
            initialDurationMillis = 90_000L,
            consumedDurationMillis = 40_000L,
            alternativeRequestCount = 1,
        )
        val repository = FakeRepository(
            PersistedAdaptiveSupportCycle(original, 100L, 100L, 1_000L, 4L),
        )

        val result = AdaptiveSupportCycleRecovery(repository, AdaptiveClock { 200L }).recover()
            as AdaptiveSupportCycleRecoveryResult.Restored

        assertEquals(
            AdaptiveSupportCycleTransitionReason.Restored,
            result.state.cycle.transitionReason,
        )
        assertEquals(1, result.state.cycle.alternativeRequestCount)
    }

    private class FakeRepository(
        private var state: PersistedAdaptiveSupportCycle?,
    ) : AdaptiveSupportCycleRepository {
        override suspend fun create(
            cycle: AdaptiveSupportCycle,
            createdAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ) = AdaptiveSupportCycleCreateResult.PersistenceFailure

        override suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult =
            state?.let(AdaptiveSupportCycleLoadResult::Active)
                ?: AdaptiveSupportCycleLoadResult.NotFound

        override suspend fun update(
            cycleId: String,
            expectedRevision: Long,
            cycle: AdaptiveSupportCycle,
            updatedAtEpochMillis: Long,
        ): AdaptiveSupportCycleMutationResult {
            val current = state ?: return AdaptiveSupportCycleMutationResult.NotFound
            if (current.revision != expectedRevision) {
                return AdaptiveSupportCycleMutationResult.RevisionConflict(current.revision)
            }
            return AdaptiveSupportCycleMutationResult.Updated(
                current.copy(
                    cycle = cycle,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                    revision = current.revision + 1L,
                ).also { state = it },
            )
        }

        override suspend fun clear(cycleId: String) =
            AdaptiveSupportCycleMutationResult.Cleared.also { state = null }

        override suspend fun clearAll() =
            AdaptiveSupportCycleClearAllResult.Cleared.also { state = null }
    }
}
