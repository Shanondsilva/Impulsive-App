package com.impulsive.app.backend.session.pathshift

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptivePreferenceRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.data.repository.pathshift.RoomPathShiftCycleRepository
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicy
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import com.impulsive.app.backend.session.adaptive.SystemAdaptiveClock

object PathShiftDependencies {
    fun coordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): PathShiftCoordinator {
        val database = AppDatabase.getInstance(context.applicationContext)
        return PathShiftCoordinator(
            cycles = RoomPathShiftCycleRepository(database.pathShiftCycleDao()),
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            preferences = RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao()),
            plans = RoomMomentPlanRepository(database.momentPlanDao()),
            forecastPolicy = PathShiftForecastPolicy(),
            scheduler = WorkManagerPathShiftWorkScheduler(context, clock),
            clock = clock,
        )
    }

    fun finaliser(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): PathShiftReviewFinaliser {
        val database = AppDatabase.getInstance(context.applicationContext)
        return PathShiftReviewFinaliser(
            cycles = RoomPathShiftCycleRepository(database.pathShiftCycleDao()),
            decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            clock = clock,
        )
    }

    fun recovery(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): PathShiftRecoveryCoordinator {
        val database = AppDatabase.getInstance(context.applicationContext)
        return PathShiftRecoveryCoordinator(
            cycles = RoomPathShiftCycleRepository(database.pathShiftCycleDao()),
            finaliser = finaliser(context, clock),
            scheduler = WorkManagerPathShiftWorkScheduler(context, clock),
            clock = clock,
        )
    }

    fun scheduler(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): PathShiftWorkScheduler = WorkManagerPathShiftWorkScheduler(context, clock)
}
