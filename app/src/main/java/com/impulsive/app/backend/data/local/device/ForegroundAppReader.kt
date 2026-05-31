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

    fun getCurrentForegroundPackage(lookbackMillis: Long = 10_000L): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as?
            UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = (endTime - lookbackMillis).coerceAtLeast(0L)
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
