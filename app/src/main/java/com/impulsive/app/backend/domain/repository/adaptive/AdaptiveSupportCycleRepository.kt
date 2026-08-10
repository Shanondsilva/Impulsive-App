package com.impulsive.app.backend.domain.repository.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle

data class PersistedAdaptiveSupportCycle(
    val cycle: AdaptiveSupportCycle,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val revision: Long,
)

sealed interface AdaptiveSupportCycleCreateResult {
    data class Created(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleCreateResult

    data class ExistingActive(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleCreateResult

    data object InvalidPersistedState : AdaptiveSupportCycleCreateResult
    data object Expired : AdaptiveSupportCycleCreateResult
    data object PersistenceFailure : AdaptiveSupportCycleCreateResult
}

sealed interface AdaptiveSupportCycleLoadResult {
    data class Active(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleLoadResult

    data object NotFound : AdaptiveSupportCycleLoadResult
    data object InvalidPersistedState : AdaptiveSupportCycleLoadResult
    data object Expired : AdaptiveSupportCycleLoadResult
    data object PersistenceFailure : AdaptiveSupportCycleLoadResult
}

sealed interface AdaptiveSupportCycleMutationResult {
    data class Updated(val state: PersistedAdaptiveSupportCycle) :
        AdaptiveSupportCycleMutationResult

    data object Cleared : AdaptiveSupportCycleMutationResult
    data object NotFound : AdaptiveSupportCycleMutationResult
    data object CycleMismatch : AdaptiveSupportCycleMutationResult
    data class RevisionConflict(val currentRevision: Long) :
        AdaptiveSupportCycleMutationResult

    data object InvalidPersistedState : AdaptiveSupportCycleMutationResult
    data object Expired : AdaptiveSupportCycleMutationResult
    data object PersistenceFailure : AdaptiveSupportCycleMutationResult
}

sealed interface AdaptiveSupportCycleClearAllResult {
    data object Cleared : AdaptiveSupportCycleClearAllResult
    data object PersistenceFailure : AdaptiveSupportCycleClearAllResult
}

interface AdaptiveSupportCycleRepository {
    suspend fun create(
        cycle: AdaptiveSupportCycle,
        createdAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): AdaptiveSupportCycleCreateResult

    suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult

    suspend fun update(
        cycleId: String,
        expectedRevision: Long,
        cycle: AdaptiveSupportCycle,
        updatedAtEpochMillis: Long,
    ): AdaptiveSupportCycleMutationResult

    suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult

    suspend fun clearAll(): AdaptiveSupportCycleClearAllResult
}
