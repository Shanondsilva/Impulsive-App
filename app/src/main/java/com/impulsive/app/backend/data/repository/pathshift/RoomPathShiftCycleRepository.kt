package com.impulsive.app.backend.data.repository.pathshift

import com.impulsive.app.backend.data.local.dao.PathShiftCycleDao
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftCycleStatus
import com.impulsive.app.backend.domain.pathshift.PathShiftEvidenceStrength
import com.impulsive.app.backend.domain.pathshift.PathShiftReviewCounts
import com.impulsive.app.backend.domain.repository.pathshift.PathShiftCycleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPathShiftCycleRepository(
    private val dao: PathShiftCycleDao,
) : PathShiftCycleRepository {
    override suspend fun insertOnce(cycle: PathShiftCycle): Boolean =
        dao.insertOnce(cycle.toEntity()) != -1L

    override suspend fun getById(cycleId: String): PathShiftCycle? =
        dao.getById(cycleId)?.toDomain()

    override fun observeActive(): Flow<PathShiftCycle?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getActive(): PathShiftCycle? = dao.getActive()?.toDomain()

    override fun observeLatestFinalised(limit: Int): Flow<List<PathShiftCycle>> {
        require(limit > 0)
        return dao.observeLatestFinalised(limit).map { cycles ->
            cycles.map(PathShiftCycleEntity::toDomain)
        }
    }

    override suspend fun attachPreparedPlan(
        cycleId: String,
        planId: String,
        contentRevisionId: String,
        preparedAtMillis: Long,
    ): Boolean {
        require(cycleId.isNotBlank())
        require(planId.isNotBlank())
        require(contentRevisionId.isNotBlank())
        require(preparedAtMillis >= 0L)
        return dao.attachPreparedPlan(
            cycleId,
            planId,
            contentRevisionId,
            preparedAtMillis,
        ) == 1
    }

    override suspend fun clearPreparedPlan(cycleId: String): Boolean =
        dao.clearPreparedPlan(cycleId) == 1

    override suspend fun finaliseOnce(
        cycleId: String,
        finalisedAtMillis: Long,
        counts: PathShiftReviewCounts,
    ): Boolean = dao.finaliseOnce(
        cycleId = cycleId,
        finalisedAtMillis = finalisedAtMillis,
        observedCount = counts.observedProtectedMomentCount,
        selectedCount = counts.preparedPlanSelectedCount,
        startedCount = counts.preparedPlanStartedCount,
        completedCount = counts.preparedPlanCompletedCount,
        dismissedCount = counts.preparedPlanDismissedCount,
        wrongTimingCount = counts.wrongTimingCount,
        repeatDetectedCount = counts.repeatDetectedCount,
    ) == 1

    override suspend fun cancelOnce(
        cycleId: String,
        cancelledAtMillis: Long,
    ): Boolean = dao.cancelOnce(cycleId, cancelledAtMillis) == 1

    override suspend fun deleteExpiredFinalised(
        cutoffMillis: Long,
        limit: Int,
    ): Int {
        require(limit > 0)
        val ids = dao.getExpiredFinalisedIds(cutoffMillis, limit)
        return if (ids.isEmpty()) 0 else dao.deleteByIds(ids)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}

internal fun PathShiftCycle.toEntity(): PathShiftCycleEntity = PathShiftCycleEntity(
    cycleId = cycleId,
    createdAtMillis = createdAtMillis,
    lookbackStartedAtMillis = lookbackStartedAtMillis,
    lookbackEndedAtMillis = lookbackEndedAtMillis,
    forecastWindowStartedAtMillis = forecastWindowStartedAtMillis,
    forecastWindowEndsAtMillis = forecastWindowEndsAtMillis,
    forecastPolicyVersion = forecastPolicyVersion,
    evidenceStrength = evidenceStrength.name,
    inputProtectedMomentCount = inputProtectedMomentCount,
    inputDistinctDayCount = inputDistinctDayCount,
    estimatedLowerCount = estimatedLowerCount,
    estimatedUpperCount = estimatedUpperCount,
    commonWindowStartMinute = commonWindowStartMinute,
    commonWindowEndMinute = commonWindowEndMinute,
    preparedPlanId = preparedPlanId,
    preparedPlanContentRevisionId = preparedPlanContentRevisionId,
    preparedAtMillis = preparedAtMillis,
    reviewFinalisedAtMillis = reviewFinalisedAtMillis,
    observedProtectedMomentCount = observedProtectedMomentCount,
    preparedPlanSelectedCount = preparedPlanSelectedCount,
    preparedPlanStartedCount = preparedPlanStartedCount,
    preparedPlanCompletedCount = preparedPlanCompletedCount,
    preparedPlanDismissedCount = preparedPlanDismissedCount,
    wrongTimingCount = wrongTimingCount,
    repeatDetectedCount = repeatDetectedCount,
    status = status.name,
    cancelledAtMillis = cancelledAtMillis,
)

internal fun PathShiftCycleEntity.toDomain(): PathShiftCycle = PathShiftCycle(
    cycleId = cycleId,
    createdAtMillis = createdAtMillis,
    lookbackStartedAtMillis = lookbackStartedAtMillis,
    lookbackEndedAtMillis = lookbackEndedAtMillis,
    forecastWindowStartedAtMillis = forecastWindowStartedAtMillis,
    forecastWindowEndsAtMillis = forecastWindowEndsAtMillis,
    forecastPolicyVersion = forecastPolicyVersion,
    evidenceStrength = enumValueOf(evidenceStrength),
    inputProtectedMomentCount = inputProtectedMomentCount,
    inputDistinctDayCount = inputDistinctDayCount,
    estimatedLowerCount = estimatedLowerCount,
    estimatedUpperCount = estimatedUpperCount,
    commonWindowStartMinute = commonWindowStartMinute,
    commonWindowEndMinute = commonWindowEndMinute,
    preparedPlanId = preparedPlanId,
    preparedPlanContentRevisionId = preparedPlanContentRevisionId,
    preparedAtMillis = preparedAtMillis,
    reviewFinalisedAtMillis = reviewFinalisedAtMillis,
    observedProtectedMomentCount = observedProtectedMomentCount,
    preparedPlanSelectedCount = preparedPlanSelectedCount,
    preparedPlanStartedCount = preparedPlanStartedCount,
    preparedPlanCompletedCount = preparedPlanCompletedCount,
    preparedPlanDismissedCount = preparedPlanDismissedCount,
    wrongTimingCount = wrongTimingCount,
    repeatDetectedCount = repeatDetectedCount,
    status = enumValueOf<PathShiftCycleStatus>(status),
    cancelledAtMillis = cancelledAtMillis,
)
