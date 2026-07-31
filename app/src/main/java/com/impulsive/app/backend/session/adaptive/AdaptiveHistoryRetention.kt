package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class AdaptiveRetentionDeletionBatch(
    val decisionIds: List<String> = emptyList(),
    val rehearsalIds: List<String> = emptyList(),
    val pathShiftCycleIds: List<String> = emptyList(),
) {
    val changed: Boolean
        get() = decisionIds.isNotEmpty() ||
            rehearsalIds.isNotEmpty() ||
            pathShiftCycleIds.isNotEmpty()
}

data class AdaptiveRetentionResult(
    val policy: AdaptiveHistoryRetentionPolicy,
    val cutoffMillis: Long? = null,
    val deletedDecisionIds: List<String> = emptyList(),
    val deletedRehearsalIds: List<String> = emptyList(),
    val deletedPathShiftCycleIds: List<String> = emptyList(),
    val skippedBecauseRestoreActive: Boolean = false,
    val failedSafely: Boolean = false,
)

fun interface AdaptiveRetentionStore {
    suspend fun prune(
        cutoffMillis: Long,
        protectedDecisionIds: Set<String>,
        limit: Int,
    ): AdaptiveRetentionDeletionBatch
}

fun interface AdaptiveRetentionObservationRecovery {
    suspend fun recover(limit: Int)
}

fun interface AdaptiveRetentionWorkCanceller {
    fun cancelDecisionWork(decisionId: String)
}

fun interface AdaptiveRetentionBackupRequester {
    fun requestCoalescedRefresh()
}

interface AdaptiveRetentionSafetyState {
    fun protectedDecisionIds(): Set<String>
    fun restoreInProgress(): Boolean
    fun clearDeletedReferences(decisionIds: Set<String>)
}

class AdaptiveHistoryRetentionCoordinator(
    private val preferences: AdaptivePreferenceRepository,
    private val store: AdaptiveRetentionStore,
    private val observationRecovery: AdaptiveRetentionObservationRecovery,
    private val workCanceller: AdaptiveRetentionWorkCanceller,
    private val backupRequester: AdaptiveRetentionBackupRequester,
    private val safetyState: AdaptiveRetentionSafetyState,
    private val clock: AdaptiveClock,
) {
    suspend fun runBounded(
        limit: Int = DefaultBatchLimit,
    ): AdaptiveRetentionResult {
        require(limit in 1..MaximumBatchLimit)
        val policy = try {
            preferences.get().historyRetentionPolicy
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AdaptiveRetentionResult(
                policy = AdaptiveHistoryRetentionPolicy.SixMonths,
                failedSafely = true,
            )
        }
        if (safetyState.restoreInProgress()) {
            return AdaptiveRetentionResult(
                policy = policy,
                skippedBecauseRestoreActive = true,
            )
        }
        val now = clock.nowMillis()
        val cutoff = policy.cutoffMillis(now)
            ?: return AdaptiveRetentionResult(
                policy = policy,
                failedSafely = now < 0L,
            )
        return try {
            observationRecovery.recover(limit)
            val deletion = store.prune(
                cutoffMillis = cutoff,
                protectedDecisionIds = safetyState.protectedDecisionIds(),
                limit = limit,
            )
            deletion.decisionIds.forEach(workCanceller::cancelDecisionWork)
            safetyState.clearDeletedReferences(deletion.decisionIds.toSet())
            if (deletion.changed) {
                backupRequester.requestCoalescedRefresh()
            }
            AdaptiveRetentionResult(
                policy = policy,
                cutoffMillis = cutoff,
                deletedDecisionIds = deletion.decisionIds,
                deletedRehearsalIds = deletion.rehearsalIds,
                deletedPathShiftCycleIds = deletion.pathShiftCycleIds,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            AdaptiveRetentionResult(
                policy = policy,
                cutoffMillis = cutoff,
                failedSafely = true,
            )
        }
    }

    companion object {
        const val DefaultBatchLimit = 500
        const val MaximumBatchLimit = 1_000
    }
}

class RoomAdaptiveRetentionStore(
    private val database: AppDatabase,
) : AdaptiveRetentionStore {
    override suspend fun prune(
        cutoffMillis: Long,
        protectedDecisionIds: Set<String>,
        limit: Int,
    ): AdaptiveRetentionDeletionBatch = database.withTransaction {
        val decisionIds = database.adaptiveDecisionDao()
            .getSafeRetentionCandidateIds(cutoffMillis, limit)
            .filterNot(protectedDecisionIds::contains)
            .take(limit)
        val rehearsalIds = database.momentPlanRehearsalDao()
            .getSafeRetentionCandidateIds(cutoffMillis, limit)
            .take(limit)
        val pathShiftCycleIds = database.pathShiftCycleDao()
            .getExpiredFinalisedIds(cutoffMillis, limit)
            .take(limit)
        if (decisionIds.isNotEmpty()) {
            check(database.adaptiveDecisionDao().deleteByIds(decisionIds) == decisionIds.size)
        }
        if (rehearsalIds.isNotEmpty()) {
            check(
                database.momentPlanRehearsalDao().deleteByIds(rehearsalIds) ==
                    rehearsalIds.size,
            )
        }
        if (pathShiftCycleIds.isNotEmpty()) {
            check(
                database.pathShiftCycleDao().deleteByIds(pathShiftCycleIds) ==
                    pathShiftCycleIds.size,
            )
        }
        AdaptiveRetentionDeletionBatch(
            decisionIds = decisionIds,
            rehearsalIds = rehearsalIds,
            pathShiftCycleIds = pathShiftCycleIds,
        )
    }
}

object AdaptiveRetentionRuntimeState : AdaptiveRetentionSafetyState {
    private val activeRouteDecisionIds = linkedSetOf<String>()
    private val activeFeedbackDecisionIds = linkedSetOf<String>()
    private val pendingNavigationDecisionIds = linkedSetOf<String>()
    private var restoreDepth = 0

    @Synchronized
    fun enterDecisionRoute(decisionId: String) {
        if (decisionId.isNotBlank()) {
            activeRouteDecisionIds += decisionId
            pendingNavigationDecisionIds -= decisionId
        }
    }

    @Synchronized
    fun leaveDecisionRoute(decisionId: String) {
        activeRouteDecisionIds -= decisionId
        activeFeedbackDecisionIds -= decisionId
    }

    @Synchronized
    fun markFeedbackPresented(decisionId: String) {
        if (decisionId.isNotBlank()) activeFeedbackDecisionIds += decisionId
    }

    @Synchronized
    fun markPendingNavigation(decisionId: String) {
        if (decisionId.isNotBlank()) pendingNavigationDecisionIds += decisionId
    }

    @Synchronized
    fun clearPendingNavigation(decisionId: String) {
        pendingNavigationDecisionIds -= decisionId
    }

    @Synchronized
    fun beginRestore() {
        restoreDepth++
    }

    @Synchronized
    fun endRestore() {
        restoreDepth = (restoreDepth - 1).coerceAtLeast(0)
    }

    @Synchronized
    override fun protectedDecisionIds(): Set<String> =
        activeRouteDecisionIds +
            activeFeedbackDecisionIds +
            pendingNavigationDecisionIds

    @Synchronized
    override fun restoreInProgress(): Boolean = restoreDepth > 0

    @Synchronized
    override fun clearDeletedReferences(decisionIds: Set<String>) {
        activeFeedbackDecisionIds.removeAll(decisionIds)
        pendingNavigationDecisionIds.removeAll(decisionIds)
    }

    @Synchronized
    fun clearAllAdaptiveReferences() {
        activeRouteDecisionIds.clear()
        activeFeedbackDecisionIds.clear()
        pendingNavigationDecisionIds.clear()
    }
}

object AdaptiveHistoryRetentionScheduler {
    const val UniqueWorkName = "adaptive-history-retention-weekly"
    const val CleanupWorkName = "adaptive-history-retention-bounded-cleanup"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AdaptiveHistoryRetentionWorker>(
            7,
            TimeUnit.DAYS,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun requestCleanup(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            CleanupWorkName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AdaptiveHistoryRetentionWorker>().build(),
        )
    }

    suspend fun cancelCleanupAndAwait(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(CleanupWorkName)
            .await()
    }

    suspend fun cancelAllAndAwait(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(CleanupWorkName).await()
        workManager.cancelUniqueWork(UniqueWorkName).await()
    }
}

class AdaptiveHistoryRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = AdaptiveRetentionDependencies
            .coordinator(applicationContext)
            .runBounded()
        return if (result.failedSafely) Result.retry() else Result.success()
    }
}

object AdaptiveRetentionDependencies {
    fun coordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveHistoryRetentionCoordinator {
        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)
        return AdaptiveHistoryRetentionCoordinator(
            preferences =
                com.impulsive.app.backend.data.repository.adaptive
                    .RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao()),
            store = RoomAdaptiveRetentionStore(database),
            observationRecovery = AdaptiveRetentionObservationRecovery { limit ->
                AdaptivePhase4Dependencies.recovery(appContext, clock).recover(limit)
            },
            workCanceller = AdaptiveRetentionWorkCanceller { decisionId ->
                WorkManager.getInstance(appContext).cancelUniqueWork(
                    AdaptiveObservationWork.uniqueName(decisionId),
                )
            },
            backupRequester = AdaptiveRetentionBackupRequester {
                RestoreSnapshotRefreshScheduler.request(appContext)
            },
            safetyState = AdaptiveRetentionRuntimeState,
            clock = clock,
        )
    }
}
