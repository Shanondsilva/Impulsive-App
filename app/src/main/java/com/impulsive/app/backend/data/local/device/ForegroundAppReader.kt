package com.impulsive.app.backend.data.local.device

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class ForegroundAppReader(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    @Volatile
    private var lastKnownForegroundPackage: String? = null

    @Volatile
    private var hasAttemptedColdStartSeed = false

    fun getCurrentForegroundPackage(lookbackMillis: Long = 10_000L): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as?
            UsageStatsManager ?: return lastKnownForegroundPackage
        val endTime = System.currentTimeMillis()
        val recentPackage = scanLatestForegroundPackage(
            usageStatsManager = usageStatsManager,
            startTime = (endTime - lookbackMillis).coerceAtLeast(0L),
            endTime = endTime,
        )
        // When the user stays inside one app, the system stops emitting new foreground
        // events, so a short lookback window finds nothing and this query returns null.
        // Treating that null as "no app in front" is what let a protected app stay open
        // after its access window ended: both the re-block check and the monitor loop
        // bail out on a null package. The most recent foreground app is still the one in
        // front until a newer foreground event appears, so remember it and fall back to
        // it whenever the current query is empty.
        if (recentPackage != null) {
            lastKnownForegroundPackage = recentPackage
            return recentPackage
        }
        // Cold-start blind spot: after the service process is killed and restarted
        // while the user is already sitting inside an app, this cache starts empty
        // and the short lookback keeps finding nothing, so every poll tick returns
        // null and a protected app is never re-blocked for that whole session. Seed
        // the cache exactly once per process with a wide lookback scan. The scan uses
        // the same foreground event semantics, and launcher resume events inside the
        // wide window keep the result accurate when the user has since gone home.
        if (lastKnownForegroundPackage == null && !hasAttemptedColdStartSeed) {
            hasAttemptedColdStartSeed = true
            val seededPackage = scanLatestForegroundPackage(
                usageStatsManager = usageStatsManager,
                startTime = (endTime - ColdStartSeedLookbackMillis).coerceAtLeast(0L),
                endTime = endTime,
            )
            if (seededPackage != null) {
                lastKnownForegroundPackage = seededPackage
                return seededPackage
            }
        }
        return lastKnownForegroundPackage
    }

    private fun scanLatestForegroundPackage(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
    ): String? {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestPackageName: String? = null
        var latestTimestamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForegroundEvent && event.timeStamp >= latestTimestamp &&
                !event.packageName.isNullOrBlank()) {
                latestTimestamp = event.timeStamp
                latestPackageName = event.packageName
            }
        }
        return latestPackageName
    }

    companion object {
        // Wide enough to reach back past a mid-session service kill, short enough
        // that a single seed scan stays cheap. Runs at most once per process.
        private const val ColdStartSeedLookbackMillis = 6L * 60L * 60L * 1000L
    }

    fun getApplicationLabel(packageName: String): String {
        return runCatching {
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(applicationInfo).toString().trim()
        }.getOrNull().orEmpty().ifBlank { packageName }
    }
}
