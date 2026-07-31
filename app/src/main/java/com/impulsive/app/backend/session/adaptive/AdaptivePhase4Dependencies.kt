package com.impulsive.app.backend.session.adaptive

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDataRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptivePreferenceRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRehearsalRepository
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.SecureRandomisationSource
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.session.pathshift.PathShiftDependencies

/**
 * Small project-style factory for Phase 4 infrastructure. It reuses the one
 * SQLCipher AppDatabase and does not introduce a service locator or DI graph.
 */
object AdaptivePhase4Dependencies {
    fun decisions(context: Context): AdaptiveDecisionRepository {
        val database = AppDatabase.getInstance(context.applicationContext)
        return RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
    }

    fun momentPlans(context: Context): MomentPlanRepository {
        val database = AppDatabase.getInstance(context.applicationContext)
        return RoomMomentPlanRepository(database.momentPlanDao())
    }

    fun rehearsalCoordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): MomentPlanRehearsalCoordinator {
        val database = AppDatabase.getInstance(context.applicationContext)
        return MomentPlanRehearsalCoordinator(
            rehearsals = RoomMomentPlanRehearsalRepository(
                database.momentPlanRehearsalDao(),
            ),
            plans = RoomMomentPlanRepository(database.momentPlanDao()),
            clock = clock,
        )
    }

    fun coordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveMomentCoordinator {
        val database = AppDatabase.getInstance(context.applicationContext)
        return AdaptiveMomentCoordinator(
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            preferences = RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao()),
            momentPlans = RoomMomentPlanRepository(database.momentPlanDao()),
            recommendationPolicy = AdaptiveRecommendationPolicy(
                SecureRandomisationSource(),
            ),
            clock = clock,
            rehearsals = RoomMomentPlanRehearsalRepository(
                database.momentPlanRehearsalDao(),
            ),
        )
    }

    fun lifecycle(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveDecisionLifecycle {
        val database = AppDatabase.getInstance(context.applicationContext)
        return AdaptiveDecisionLifecycle(
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            momentPlans = RoomMomentPlanRepository(database.momentPlanDao()),
            scheduler = WorkManagerAdaptiveObservationScheduler(context, clock),
            clock = clock,
        )
    }

    fun outcomeCoordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveOutcomeCoordinator {
        val decisions = decisions(context)
        return AdaptiveOutcomeCoordinator(
            decisions = decisions,
            lifecycle = lifecycle(context, clock),
            clock = clock,
        )
    }

    fun pendingFeedbackCoordinator(
        context: Context,
    ): AdaptivePendingFeedbackCoordinator =
        AdaptivePendingFeedbackCoordinator(decisions(context))

    fun followUpSupport(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveFollowUpSupport {
        val database = AppDatabase.getInstance(context.applicationContext)
        val decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        val plans = RoomMomentPlanRepository(database.momentPlanDao())
        return AdaptiveFollowUpSupport(
            coordinator = coordinator(context, clock),
            decisions = decisions,
            momentPlans = plans,
            lifecycle = AdaptiveDecisionLifecycle(
                decisions = decisions,
                momentPlans = plans,
                scheduler = WorkManagerAdaptiveObservationScheduler(context, clock),
                clock = clock,
            ),
            clock = clock,
        )
    }

    fun observationFinalizer(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveObservationFinalizer {
        val database = AppDatabase.getInstance(context.applicationContext)
        return AdaptiveObservationFinalizer(
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            clock = clock,
        )
    }

    fun recovery(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveObservationRecovery {
        val database = AppDatabase.getInstance(context.applicationContext)
        val decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        val scheduler = WorkManagerAdaptiveObservationScheduler(context, clock)
        return AdaptiveObservationRecovery(
            decisions = decisions,
            finalizer = AdaptiveObservationFinalizer(decisions, clock),
            scheduler = scheduler,
            clock = clock,
        )
    }

    fun resetCoordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveResetCoordinator {
        val database = AppDatabase.getInstance(context.applicationContext)
        return AdaptiveResetCoordinator(
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            allAdaptiveData = RoomAdaptiveDataRepository(database),
            scheduler = WorkManagerAdaptiveObservationScheduler(context, clock),
            retentionWork = AdaptiveRetentionResetWork { deleteAllMomentData ->
                check(PathShiftDependencies.scheduler(context, clock).cancelAll())
                if (deleteAllMomentData) {
                    AdaptiveHistoryRetentionScheduler.cancelAllAndAwait(context)
                } else {
                    AdaptiveHistoryRetentionScheduler.cancelCleanupAndAwait(context)
                }
                true
            },
        )
    }
}
