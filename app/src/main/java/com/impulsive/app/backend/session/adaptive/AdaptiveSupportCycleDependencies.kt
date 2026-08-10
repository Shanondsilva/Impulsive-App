package com.impulsive.app.backend.session.adaptive

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.AdaptiveSupportCyclePreferencesDataSource
import com.impulsive.app.backend.data.repository.adaptive.DataStoreAdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository

object AdaptiveSupportCycleDependencies {
    fun repository(context: Context): AdaptiveSupportCycleRepository =
        DataStoreAdaptiveSupportCycleRepository(
            AdaptiveSupportCyclePreferencesDataSource.getInstance(context.applicationContext),
        )

    fun recovery(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveSupportCycleRecovery = AdaptiveSupportCycleRecovery(
        repository = repository(context),
        clock = clock,
    )

    /*
     * Every protected cycle uses one fixed duration, so no decision-history
     * repository is constructed here: a constant needs no Room query.
     */
    fun coordinator(
        context: Context,
        clock: AdaptiveClock = SystemAdaptiveClock,
    ): AdaptiveSupportCycleCoordinator = AdaptiveSupportCycleCoordinator(
        repository = repository(context),
        clock = clock,
    )
}
