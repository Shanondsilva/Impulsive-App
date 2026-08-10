package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDataRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import kotlinx.coroutines.CancellationException

fun interface AdaptiveRetentionResetWork {
    suspend fun cancel(deleteAllMomentData: Boolean): Boolean
}

class AdaptiveResetCoordinator(
    private val decisions: AdaptiveDecisionRepository,
    private val allAdaptiveData: AdaptiveDataRepository,
    private val scheduler: AdaptiveObservationScheduler,
    private val logger: AdaptiveSafeLogger = AndroidAdaptiveSafeLogger,
    private val retentionWork: AdaptiveRetentionResetWork =
        AdaptiveRetentionResetWork { true },
    private val clearActiveSupportCycleState: suspend () -> Boolean = { true },
    private val clearPendingRuntimeState: () -> Unit =
        AdaptiveRetentionRuntimeState::clearAllAdaptiveReferences,
) {
    suspend fun resetPersonalLearning(): AdaptiveLifecycleResult =
        reset("reset personal learning", deleteAllMomentData = false) {
            allAdaptiveData.clearPersonalLearning()
        }

    suspend fun clearAllAdaptiveData(): AdaptiveLifecycleResult =
        reset("clear all adaptive data", deleteAllMomentData = true) {
            allAdaptiveData.clearAllAdaptiveData()
        }

    private suspend fun reset(
        operation: String,
        deleteAllMomentData: Boolean,
        clear: suspend () -> Unit,
    ): AdaptiveLifecycleResult = try {
        if (
            !scheduler.cancelAll() ||
            !retentionWork.cancel(deleteAllMomentData) ||
            !clearActiveSupportCycleState()
        ) {
            return AdaptiveLifecycleResult.SchedulingFailure
        }
        clear()
        clearPendingRuntimeState()
        AdaptiveLifecycleResult.Applied
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        logger.failure(operation, error)
        AdaptiveLifecycleResult.PersistenceFailure
    }
}
