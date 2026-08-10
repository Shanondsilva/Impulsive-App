package com.impulsive.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.impulsive.app.backend.data.restore.AdaptiveRestoreSnapshotObserver
import com.impulsive.app.backend.session.adaptive.AdaptivePhase4Dependencies
import com.impulsive.app.backend.session.adaptive.AdaptiveHistoryRetentionScheduler
import com.impulsive.app.backend.session.adaptive.AdaptiveRetentionDependencies
import com.impulsive.app.backend.session.game.WorkManagerPivotGameSafeExitReconciliationScheduler
import com.impulsive.app.backend.session.pathshift.PathShiftDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ImpulsiveApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var adaptiveRestoreSnapshotObserver:
        AdaptiveRestoreSnapshotObserver

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        AppCheckInitializer.install()

        WorkManagerPivotGameSafeExitReconciliationScheduler(
            applicationContext,
        )
            .request()

        startupScope.launch {
            adaptiveRestoreSnapshotObserver =
                AdaptiveRestoreSnapshotObserver(applicationContext)
            adaptiveRestoreSnapshotObserver.start()
            AdaptivePhase4Dependencies.recovery(applicationContext).recover()
            PathShiftDependencies.recovery(applicationContext).recover()
            AdaptiveRetentionDependencies
                .coordinator(applicationContext)
                .runBounded()
            AdaptiveHistoryRetentionScheduler.ensureScheduled(applicationContext)
        }
    }
}
