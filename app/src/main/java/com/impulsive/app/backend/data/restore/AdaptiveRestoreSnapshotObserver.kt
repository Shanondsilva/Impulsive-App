package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.room.InvalidationTracker
import com.impulsive.app.backend.data.local.database.AppDatabase

/**
 * Routes every committed adaptive-table change through the existing unique
 * snapshot work. The worker performs account/ownership checks before writing,
 * and cloud upload is requested only after that snapshot write succeeds.
 */
class AdaptiveRestoreSnapshotObserver(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val observer = object : InvalidationTracker.Observer(
        "moment_plans",
        "adaptive_preferences",
        "adaptive_decisions",
        "moment_plan_rehearsals",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            RestoreSnapshotRefreshScheduler.request(appContext)
        }
    }

    fun start() {
        database.invalidationTracker.addObserver(observer)
    }
}
