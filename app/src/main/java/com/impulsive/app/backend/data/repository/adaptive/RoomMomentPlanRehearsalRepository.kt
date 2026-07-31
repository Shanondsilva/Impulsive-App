package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.dao.MomentPlanRehearsalDao
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRehearsalRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMomentPlanRehearsalRepository(
    private val dao: MomentPlanRehearsalDao,
) : MomentPlanRehearsalRepository {
    override suspend fun insertOnce(rehearsal: MomentPlanRehearsal): Boolean =
        dao.insertOnce(rehearsal.toEntity()) != -1L

    override suspend fun getById(rehearsalId: String): MomentPlanRehearsal? =
        dao.getById(rehearsalId)?.toDomain()

    override suspend fun markCompletedOnce(
        rehearsalId: String,
        completedAtMillis: Long,
    ): Boolean = dao.markCompletedOnce(rehearsalId, completedAtMillis) == 1

    override suspend fun markDismissedOnce(
        rehearsalId: String,
        dismissedAtMillis: Long,
    ): Boolean = dao.markDismissedOnce(rehearsalId, dismissedAtMillis) == 1

    override suspend fun getOpenRehearsal(): MomentPlanRehearsal? =
        dao.getOpenRehearsal()?.toDomain()

    override suspend fun getRecentCompleted(limit: Int): List<MomentPlanRehearsal> =
        dao.getRecentCompleted(limit.coerceAtLeast(0)).map { it.toDomain() }

    override fun observeRecentCompleted(
        limit: Int,
    ): Flow<List<MomentPlanRehearsal>> =
        dao.observeRecentCompleted(limit.coerceAtLeast(0)).map { rehearsals ->
            rehearsals.map { it.toDomain() }
        }

    override suspend fun getCompletedByPlan(planId: String): List<MomentPlanRehearsal> =
        dao.getCompletedByPlan(planId).map { it.toDomain() }

    override suspend fun clearHistory() {
        dao.clearHistory()
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}

internal fun MomentPlanRehearsal.toEntity(): MomentPlanRehearsalEntity {
    require(runCatching { UUID.fromString(rehearsalId) }.isSuccess) {
        "Rehearsal ID must be a UUID."
    }
    require(runCatching { UUID.fromString(planId) }.isSuccess) {
        "Plan ID must be a UUID."
    }
    require(startedAtMillis >= 0L) {
        "Rehearsal start must not be negative."
    }
    require(planUpdatedAtMillisAtStart >= 0L) {
        "Plan revision must not be negative."
    }
    require(runCatching { UUID.fromString(planContentRevisionId) }.isSuccess) {
        "Plan content revision ID must be a UUID."
    }
    require(completedAtMillis == null || dismissedAtMillis == null) {
        "A rehearsal cannot be both completed and dismissed."
    }
    require(completedAtMillis == null || completedAtMillis >= startedAtMillis) {
        "Rehearsal completion must not precede its start."
    }
    require(dismissedAtMillis == null || dismissedAtMillis >= startedAtMillis) {
        "Rehearsal dismissal must not precede its start."
    }
    return MomentPlanRehearsalEntity(
        rehearsalId = rehearsalId,
        planId = planId,
        planUpdatedAtMillisAtStart = planUpdatedAtMillisAtStart,
        mode = mode.name,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        planContentRevisionId = planContentRevisionId,
    )
}

internal fun MomentPlanRehearsalEntity.toDomain(): MomentPlanRehearsal {
    val domain = MomentPlanRehearsal(
        rehearsalId = rehearsalId,
        planId = planId,
        planUpdatedAtMillisAtStart = planUpdatedAtMillisAtStart,
        mode = MomentPlanRehearsalMode.entries.firstOrNull { it.name == mode }
            ?: throw IllegalArgumentException("Unknown rehearsal mode."),
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        planContentRevisionId = planContentRevisionId,
    )
    domain.toEntity()
    return domain
}
