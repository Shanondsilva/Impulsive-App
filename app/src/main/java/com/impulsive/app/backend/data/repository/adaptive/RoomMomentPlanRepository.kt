package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.dao.MomentPlanDao
import com.impulsive.app.backend.data.local.dao.MomentPlanMutationResult
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanSaveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMomentPlanRepository(
    private val dao: MomentPlanDao,
) : MomentPlanRepository {
    override suspend fun create(plan: MomentPlan): MomentPlanSaveResult =
        dao.create(plan.toEntity()).toDomainResult()

    override suspend fun update(plan: MomentPlan): MomentPlanSaveResult =
        dao.update(plan.toEntity()).toDomainResult()

    override suspend fun delete(planId: String): MomentPlanSaveResult =
        dao.delete(planId).toDomainResult()

    override suspend fun getById(planId: String): MomentPlan? =
        dao.getById(planId)?.toDomain()

    override fun observeAll(): Flow<List<MomentPlan>> =
        dao.observeAll().map { plans -> plans.map { it.toDomain() } }

    override fun observeEnabled(): Flow<List<MomentPlan>> =
        dao.observeEnabled().map { plans -> plans.map { it.toDomain() } }

    override suspend fun getMatchingEnabledByCue(
        cue: MomentCue,
    ): List<MomentPlan> =
        dao.getMatchingEnabledByCue(cue.name).map { it.toDomain() }

    override suspend fun setPreferred(
        planId: String,
        updatedAtMillis: Long,
    ): MomentPlanSaveResult =
        dao.setPreferred(planId, updatedAtMillis).toDomainResult()

    override suspend fun markRehearsedIfRevisionMatches(
        planId: String,
        expectedUpdatedAtMillis: Long,
        rehearsedAtMillis: Long,
    ): Boolean = dao.markRehearsedIfRevisionMatches(
        planId = planId,
        expectedUpdatedAtMillis = expectedUpdatedAtMillis,
        rehearsedAtMillis = rehearsedAtMillis,
    ) == 1

    override suspend fun markRehearsedIfContentRevisionMatches(
        planId: String,
        expectedContentRevisionId: String,
        rehearsedAtMillis: Long,
    ): Boolean = dao.markRehearsedIfContentRevisionMatches(
        planId = planId,
        expectedContentRevisionId = expectedContentRevisionId,
        rehearsedAtMillis = rehearsedAtMillis,
    ) == 1

    private fun MomentPlanMutationResult.toDomainResult(): MomentPlanSaveResult =
        when (this) {
            MomentPlanMutationResult.Applied -> MomentPlanSaveResult.Applied
            MomentPlanMutationResult.AlreadyExists -> MomentPlanSaveResult.AlreadyExists
            MomentPlanMutationResult.NotFound -> MomentPlanSaveResult.NotFound
            MomentPlanMutationResult.EnabledPlanLimitReached ->
                MomentPlanSaveResult.EnabledPlanLimitReached

            MomentPlanMutationResult.PreferredPlanMustBeEnabled ->
                MomentPlanSaveResult.PreferredPlanMustBeEnabled
        }
}
