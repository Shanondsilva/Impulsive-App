package com.impulsive.app.backend.data.repository

import android.content.Context
import android.os.SystemClock
import com.impulsive.app.backend.data.local.preferences.SafeBrowseAccessDataSource
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessSnapshot
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseRewardGrantResult
import com.impulsive.app.backend.domain.model.safebrowse.TwoHourGrantMillis

/**
 * The only production layer the Safe Browse access ViewModel uses to mutate the usage
 * ledger. Time is injected so every caller — including tests — controls elapsed realtime
 * and wall-clock time explicitly instead of reading the system clock implicitly.
 */
class SafeBrowseAccessRepository internal constructor(
    private val dataSource: SafeBrowseAccessDataSource,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val epochMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
        epochMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        SafeBrowseAccessDataSource(context.applicationContext),
        elapsedRealtimeMillis,
        epochMillis,
    )

    suspend fun currentSnapshot(): SafeBrowseAccessSnapshot = dataSource.currentSnapshot()

    suspend fun reconcileInterruptedLease(): SafeBrowseAccessSnapshot =
        dataSource.reconcileInterruptedLease(
            nowElapsedMillis = elapsedRealtimeMillis(),
            nowEpochMillis = epochMillis(),
        )

    suspend fun grantReward(
        receiptToken: String,
        grantTimedAccess: Boolean = true,
        grantMillis: Long = TwoHourGrantMillis,
    ): SafeBrowseRewardGrantResult = dataSource.grantReward(
        receiptToken = receiptToken,
        grantTimedAccess = grantTimedAccess,
        grantMillis = grantMillis,
        nowElapsedMillis = elapsedRealtimeMillis(),
        nowEpochMillis = epochMillis(),
    )

    suspend fun beginUsage(): SafeBrowseAccessSnapshot = dataSource.beginUsage(
        nowElapsedMillis = elapsedRealtimeMillis(),
        nowEpochMillis = epochMillis(),
    )

    suspend fun checkpointUsage(): SafeBrowseAccessSnapshot = dataSource.checkpointUsage(
        nowElapsedMillis = elapsedRealtimeMillis(),
        nowEpochMillis = epochMillis(),
    )

    suspend fun endUsage(): SafeBrowseAccessSnapshot = dataSource.endUsage(
        nowElapsedMillis = elapsedRealtimeMillis(),
        nowEpochMillis = epochMillis(),
    )

    suspend fun clearTimedAccessForPassActivation(): SafeBrowseAccessSnapshot =
        dataSource.clearTimedAccessForPassActivation()
}
