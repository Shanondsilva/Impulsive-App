package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRehearsalRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface RehearsalIdSource {
    fun newId(): String
}

object UuidRehearsalIdSource : RehearsalIdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}

data class MomentPlanRehearsalSession(
    val rehearsal: MomentPlanRehearsal,
    val plan: MomentPlan,
)

enum class RehearsalStartFailure {
    PlanUnavailable,
    PlanDisabled,
    PlanInvalid,
    AnotherPracticeIsOpen,
    PersistenceRejected,
}

data class RehearsalStartResult(
    val session: MomentPlanRehearsalSession? = null,
    val created: Boolean = false,
    val failure: RehearsalStartFailure? = null,
)

enum class RehearsalTerminalResult {
    Applied,
    AlreadyCompleted,
    AlreadyDismissed,
    NotFound,
}

class MomentPlanRehearsalCoordinator(
    private val rehearsals: MomentPlanRehearsalRepository,
    private val plans: MomentPlanRepository,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
    private val ids: RehearsalIdSource = UuidRehearsalIdSource,
) {
    private val startMutex = Mutex()

    suspend fun startGuided(planId: String): RehearsalStartResult =
        start(planId, MomentPlanRehearsalMode.Guided)

    suspend fun startQuick(planId: String): RehearsalStartResult =
        start(planId, MomentPlanRehearsalMode.Quick)

    suspend fun complete(rehearsalId: String): RehearsalTerminalResult {
        val existing = rehearsals.getById(rehearsalId)
            ?: return RehearsalTerminalResult.NotFound
        if (existing.completedAtMillis != null) {
            updateLatestCompletedPractice(existing)
            return RehearsalTerminalResult.AlreadyCompleted
        }
        if (existing.dismissedAtMillis != null) {
            return RehearsalTerminalResult.AlreadyDismissed
        }
        val completedAt = clock.nowMillis().coerceAtLeast(existing.startedAtMillis)
        if (!rehearsals.markCompletedOnce(rehearsalId, completedAt)) {
            return terminalState(rehearsalId)
        }
        updateLatestCompletedPractice(existing.copy(completedAtMillis = completedAt))
        return RehearsalTerminalResult.Applied
    }

    suspend fun dismiss(rehearsalId: String): RehearsalTerminalResult {
        val existing = rehearsals.getById(rehearsalId)
            ?: return RehearsalTerminalResult.NotFound
        if (existing.completedAtMillis != null) {
            return RehearsalTerminalResult.AlreadyCompleted
        }
        if (existing.dismissedAtMillis != null) {
            return RehearsalTerminalResult.AlreadyDismissed
        }
        val dismissedAt = clock.nowMillis().coerceAtLeast(existing.startedAtMillis)
        if (!rehearsals.markDismissedOnce(rehearsalId, dismissedAt)) {
            return terminalState(rehearsalId)
        }
        return RehearsalTerminalResult.Applied
    }

    suspend fun recoverOpen(): MomentPlanRehearsalSession? {
        val rehearsal = rehearsals.getOpenRehearsal() ?: return null
        val plan = plans.getById(rehearsal.planId)
        if (!plan.isValidFor(rehearsal)) {
            rehearsals.markDismissedOnce(
                rehearsal.rehearsalId,
                clock.nowMillis().coerceAtLeast(rehearsal.startedAtMillis),
            )
            return null
        }
        return MomentPlanRehearsalSession(rehearsal, checkNotNull(plan))
    }

    suspend fun reload(rehearsalId: String): MomentPlanRehearsalSession? {
        val rehearsal = rehearsals.getById(rehearsalId) ?: return null
        val plan = plans.getById(rehearsal.planId)
        return if (plan.isValidFor(rehearsal)) {
            MomentPlanRehearsalSession(rehearsal, checkNotNull(plan))
        } else {
            null
        }
    }

    private suspend fun start(
        planId: String,
        mode: MomentPlanRehearsalMode,
    ): RehearsalStartResult = startMutex.withLock {
        val plan = plans.getById(planId)
            ?: return@withLock RehearsalStartResult(
                failure = RehearsalStartFailure.PlanUnavailable,
            )
        if (!plan.enabled) {
            return@withLock RehearsalStartResult(
                failure = RehearsalStartFailure.PlanDisabled,
            )
        }
        if (!AdaptiveModelValidator.isSafeAndValid(plan)) {
            return@withLock RehearsalStartResult(
                failure = RehearsalStartFailure.PlanInvalid,
            )
        }

        val open = rehearsals.getOpenRehearsal()
        if (open != null) {
            if (
                open.planId == plan.planId &&
                open.planContentRevisionId == plan.contentRevisionId &&
                open.mode == mode
            ) {
                return@withLock RehearsalStartResult(
                    session = MomentPlanRehearsalSession(open, plan),
                    created = false,
                )
            }
            return@withLock RehearsalStartResult(
                failure = RehearsalStartFailure.AnotherPracticeIsOpen,
            )
        }

        val rehearsal = MomentPlanRehearsal(
            rehearsalId = ids.newId(),
            planId = plan.planId,
            planUpdatedAtMillisAtStart = plan.updatedAtMillis,
            mode = mode,
            startedAtMillis = clock.nowMillis(),
            planContentRevisionId = plan.contentRevisionId,
        )
        if (!rehearsals.insertOnce(rehearsal)) {
            return@withLock RehearsalStartResult(
                failure = RehearsalStartFailure.PersistenceRejected,
            )
        }
        RehearsalStartResult(
            session = MomentPlanRehearsalSession(rehearsal, plan),
            created = true,
        )
    }

    private suspend fun updateLatestCompletedPractice(rehearsal: MomentPlanRehearsal) {
        val completedAt = rehearsal.completedAtMillis ?: return
        plans.markRehearsedIfContentRevisionMatches(
            planId = rehearsal.planId,
            expectedContentRevisionId = rehearsal.planContentRevisionId,
            rehearsedAtMillis = completedAt,
        )
    }

    private suspend fun terminalState(rehearsalId: String): RehearsalTerminalResult {
        val current = rehearsals.getById(rehearsalId)
            ?: return RehearsalTerminalResult.NotFound
        return when {
            current.completedAtMillis != null -> RehearsalTerminalResult.AlreadyCompleted
            current.dismissedAtMillis != null -> RehearsalTerminalResult.AlreadyDismissed
            else -> RehearsalTerminalResult.NotFound
        }
    }

    private fun MomentPlan?.isValidFor(rehearsal: MomentPlanRehearsal): Boolean =
        this != null &&
            enabled &&
            contentRevisionId == rehearsal.planContentRevisionId &&
            AdaptiveModelValidator.isSafeAndValid(this)
}
