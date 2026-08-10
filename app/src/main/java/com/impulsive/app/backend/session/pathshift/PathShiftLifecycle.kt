package com.impulsive.app.backend.session.pathshift

import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.engine.adaptive.MomentPlanContentRevisionIds
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftCycleStatus
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastInput
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicy
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastResult
import com.impulsive.app.backend.domain.pathshift.PathShiftProtectedMoment
import com.impulsive.app.backend.domain.pathshift.PathShiftReviewCounts
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.pathshift.PathShiftCycleRepository
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException

fun interface PathShiftIdSource {
    fun newId(): String
}

object UuidPathShiftIdSource : PathShiftIdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}

fun interface PathShiftZoneIdSource {
    fun current(): ZoneId
}

object SystemPathShiftZoneIdSource : PathShiftZoneIdSource {
    override fun current(): ZoneId = ZoneId.systemDefault()
}

interface PathShiftWorkScheduler {
    fun schedule(cycleId: String, finaliseAtMillis: Long): Boolean
    fun cancel(cycleId: String): Boolean
    fun cancelAll(): Boolean
}

sealed interface PathShiftCreateResult {
    data class Created(val cycle: PathShiftCycle) : PathShiftCreateResult
    data class Existing(val cycle: PathShiftCycle) : PathShiftCreateResult
    data class Unavailable(val forecast: PathShiftForecastResult.Unavailable) :
        PathShiftCreateResult
    data object PersistenceFailure : PathShiftCreateResult
    data class SchedulingFailure(val cycle: PathShiftCycle) : PathShiftCreateResult
}

enum class PathShiftMutationResult {
    Applied,
    Idempotent,
    NotFound,
    InvalidPlan,
    RevisionMismatch,
    InvalidTimestamp,
    PersistenceFailure,
}

enum class PathShiftFinalisationResult {
    Finalised,
    AlreadyFinalised,
    NotDue,
    Missing,
    PersistenceFailure,
}

data class PathShiftRecoveryResult(
    val finalised: Boolean,
    val rescheduled: Boolean,
    val cancelledCorrupt: Boolean,
    val failed: Boolean,
)

class PathShiftCoordinator(
    private val cycles: PathShiftCycleRepository,
    private val decisions: AdaptiveDecisionRepository,
    private val plans: MomentPlanRepository,
    private val forecastPolicy: PathShiftForecastPolicy,
    private val scheduler: PathShiftWorkScheduler,
    private val clock: AdaptiveClock,
    private val zoneIdSource: PathShiftZoneIdSource = SystemPathShiftZoneIdSource,
    private val idSource: PathShiftIdSource = UuidPathShiftIdSource,
) {
    suspend fun createCycle(): PathShiftCreateResult {
        cycles.getActive()?.let { return PathShiftCreateResult.Existing(it) }
        val now = clock.nowMillis()
        val recent = try {
            decisions.getBetween(
                startedAtMillis = 0L,
                endedAtMillis = Long.MAX_VALUE,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return PathShiftCreateResult.PersistenceFailure
        }
        val forecast = forecastPolicy.calculate(
            PathShiftForecastInput(
                protectedMoments = recent.map {
                    PathShiftProtectedMoment(
                        incidentToken = it.protectionIncidentToken,
                        occurredAtMillis = it.createdAtMillis,
                        sourceKind = it.sourceKind,
                    )
                },
                generatedAtMillis = now,
                zoneId = zoneIdSource.current(),
            ),
        )
        if (forecast is PathShiftForecastResult.Unavailable) {
            return PathShiftCreateResult.Unavailable(forecast)
        }
        forecast as PathShiftForecastResult.Available
        val cycle = PathShiftCycle(
            cycleId = idSource.newId(),
            createdAtMillis = now,
            lookbackStartedAtMillis = forecast.lookbackStartedAtMillis,
            lookbackEndedAtMillis = forecast.lookbackEndedAtMillis,
            forecastWindowStartedAtMillis = forecast.forecastWindowStartedAtMillis,
            forecastWindowEndsAtMillis = forecast.forecastWindowEndsAtMillis,
            forecastPolicyVersion = forecast.factors.policyVersion,
            evidenceStrength = forecast.evidenceStrength,
            inputProtectedMomentCount = forecast.factors.protectedMomentCount,
            inputDistinctDayCount = forecast.factors.distinctDayCount,
            estimatedLowerCount = forecast.estimatedLowerCount,
            estimatedUpperCount = forecast.estimatedUpperCount,
            commonWindowStartMinute =
                forecast.factors.commonTimeWindow?.startMinuteInclusive,
            commonWindowEndMinute =
                forecast.factors.commonTimeWindow?.endMinuteExclusive,
        )
        val inserted = try {
            cycles.insertOnce(cycle)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!inserted) {
            return cycles.getActive()
                ?.let(PathShiftCreateResult::Existing)
                ?: PathShiftCreateResult.PersistenceFailure
        }
        return if (scheduler.schedule(cycle.cycleId, cycle.forecastWindowEndsAtMillis)) {
            PathShiftCreateResult.Created(cycle)
        } else {
            PathShiftCreateResult.SchedulingFailure(cycle)
        }
    }

    suspend fun preparePlan(
        cycleId: String,
        planId: String,
    ): PathShiftMutationResult {
        val cycle = cycles.getById(cycleId) ?: return PathShiftMutationResult.NotFound
        if (cycle.status != PathShiftCycleStatus.Active) {
            return PathShiftMutationResult.Idempotent
        }
        val plan = plans.getById(planId)
            ?.takeIf(::isEligiblePreparedPlan)
            ?: return PathShiftMutationResult.InvalidPlan
        return if (
            cycles.attachPreparedPlan(
                cycleId = cycleId,
                planId = plan.planId,
                contentRevisionId = plan.contentRevisionId,
                preparedAtMillis = clock.nowMillis(),
            )
        ) {
            PathShiftMutationResult.Applied
        } else {
            PathShiftMutationResult.PersistenceFailure
        }
    }

    suspend fun preparedPlanRevisionMatches(cycleId: String): Boolean {
        val cycle = cycles.getById(cycleId) ?: return false
        val planId = cycle.preparedPlanId ?: return true
        val revision = cycle.preparedPlanContentRevisionId ?: return false
        return plans.getById(planId)?.contentRevisionId == revision
    }

    suspend fun useNewPlanRevision(cycleId: String): PathShiftMutationResult {
        val cycle = cycles.getById(cycleId) ?: return PathShiftMutationResult.NotFound
        val planId = cycle.preparedPlanId ?: return PathShiftMutationResult.InvalidPlan
        return preparePlan(cycleId, planId)
    }

    suspend fun removePreparedPlan(cycleId: String): PathShiftMutationResult {
        val cycle = cycles.getById(cycleId) ?: return PathShiftMutationResult.NotFound
        if (cycle.status != PathShiftCycleStatus.Active) {
            return PathShiftMutationResult.Idempotent
        }
        if (cycle.preparedPlanId == null) return PathShiftMutationResult.Idempotent
        return if (cycles.clearPreparedPlan(cycleId)) {
            PathShiftMutationResult.Applied
        } else {
            PathShiftMutationResult.PersistenceFailure
        }
    }

    suspend fun cancel(cycleId: String): PathShiftMutationResult {
        val cycle = cycles.getById(cycleId) ?: return PathShiftMutationResult.NotFound
        if (cycle.status != PathShiftCycleStatus.Active) {
            return PathShiftMutationResult.Idempotent
        }
        val now = clock.nowMillis()
        if (now < 0L) return PathShiftMutationResult.InvalidTimestamp
        val cancelled = cycles.cancelOnce(cycleId, now)
        if (!cancelled) {
            return if (cycles.getById(cycleId)?.status == PathShiftCycleStatus.Cancelled) {
                PathShiftMutationResult.Idempotent
            } else {
                PathShiftMutationResult.PersistenceFailure
            }
        }
        scheduler.cancel(cycleId)
        return PathShiftMutationResult.Applied
    }

    suspend fun cancelActive(): PathShiftMutationResult =
        cycles.getActive()?.let { cancel(it.cycleId) } ?: PathShiftMutationResult.Idempotent

    private fun isEligiblePreparedPlan(plan: MomentPlan): Boolean =
        plan.enabled &&
            plan.contentRevisionId.isNotBlank() &&
            plan.contentRevisionId != MomentPlanContentRevisionIds.Unspecified &&
            InterventionProtocolRegistry.resolveForPlan(plan) != null
}

class PathShiftReviewFinaliser(
    private val cycles: PathShiftCycleRepository,
    private val decisions: AdaptiveDecisionRepository,
    private val clock: AdaptiveClock,
) {
    suspend fun finalise(cycleId: String): PathShiftFinalisationResult {
        val cycle = cycles.getById(cycleId) ?: return PathShiftFinalisationResult.Missing
        if (cycle.status == PathShiftCycleStatus.Finalised) {
            return PathShiftFinalisationResult.AlreadyFinalised
        }
        if (cycle.status != PathShiftCycleStatus.Active) {
            return PathShiftFinalisationResult.Missing
        }
        val now = clock.nowMillis()
        if (now < cycle.forecastWindowEndsAtMillis) {
            return PathShiftFinalisationResult.NotDue
        }
        val roots = try {
            decisions.getBetween(
                cycle.forecastWindowStartedAtMillis,
                cycle.forecastWindowEndsAtMillis,
            )
                .filter { it.sourceKind != AdaptiveSourceKind.ExplicitUserSupport }
                .distinctBy(AdaptiveDecision::protectionIncidentToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return PathShiftFinalisationResult.PersistenceFailure
        }
        val exactPlanUses = if (
            cycle.preparedPlanId != null &&
            cycle.preparedPlanContentRevisionId != null
        ) {
            roots.filter {
                it.assignment.actualIntervention == InterventionFamily.MomentPlan &&
                    it.assignment.momentPlanId == cycle.preparedPlanId &&
                    it.assignment.actualPlanContentRevisionId ==
                    cycle.preparedPlanContentRevisionId
            }
        } else {
            emptyList()
        }
        val counts = PathShiftReviewCounts(
            observedProtectedMomentCount = roots.size,
            preparedPlanSelectedCount = exactPlanUses.size,
            preparedPlanStartedCount = exactPlanUses.count { it.startedAtMillis != null },
            preparedPlanCompletedCount = exactPlanUses.count { it.completedAtMillis != null },
            preparedPlanDismissedCount = exactPlanUses.count { it.dismissedAtMillis != null },
            wrongTimingCount = roots.count { it.feedbackCode == FeedbackCode.WrongTiming },
            repeatDetectedCount = roots.count {
                it.repeatObservation == RepeatObservation.RepeatDetected &&
                    it.observationFinalisedAtMillis != null
            },
        )
        return if (cycles.finaliseOnce(cycleId, now, counts)) {
            PathShiftFinalisationResult.Finalised
        } else {
            when (cycles.getById(cycleId)?.status) {
                PathShiftCycleStatus.Finalised ->
                    PathShiftFinalisationResult.AlreadyFinalised
                null -> PathShiftFinalisationResult.Missing
                else -> PathShiftFinalisationResult.PersistenceFailure
            }
        }
    }
}

class PathShiftRecoveryCoordinator(
    private val cycles: PathShiftCycleRepository,
    private val finaliser: PathShiftReviewFinaliser,
    private val scheduler: PathShiftWorkScheduler,
    private val clock: AdaptiveClock,
) {
    suspend fun recover(): PathShiftRecoveryResult {
        val active = try {
            cycles.getActive()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return PathShiftRecoveryResult(false, false, false, true)
        } ?: return PathShiftRecoveryResult(false, false, false, false)

        return try {
            if (active.forecastWindowEndsAtMillis <= clock.nowMillis()) {
                when (finaliser.finalise(active.cycleId)) {
                    PathShiftFinalisationResult.Finalised,
                    PathShiftFinalisationResult.AlreadyFinalised,
                    -> PathShiftRecoveryResult(true, false, false, false)
                    PathShiftFinalisationResult.Missing ->
                        PathShiftRecoveryResult(false, false, true, false)
                    else -> PathShiftRecoveryResult(false, false, false, true)
                }
            } else {
                val scheduled = scheduler.schedule(
                    active.cycleId,
                    active.forecastWindowEndsAtMillis,
                )
                PathShiftRecoveryResult(false, scheduled, false, !scheduled)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            PathShiftRecoveryResult(false, false, false, true)
        }
    }
}
