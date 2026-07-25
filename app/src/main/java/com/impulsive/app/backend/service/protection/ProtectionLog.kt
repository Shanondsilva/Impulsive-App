package com.impulsive.app.backend.service.protection

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

internal object ProtectionLog {
    const val Tag = "ImpulsiveProtection"

    private const val DefaultThrottleMillis = 20_000L
    private val lastLoggedAtByKey = ConcurrentHashMap<String, Long>()

    fun debug(message: String) {
        Log.d(Tag, message)
    }

    fun warn(message: String) {
        Log.w(Tag, message)
    }

    fun error(message: String) {
        Log.e(Tag, message)
    }

    fun debugThrottled(
        key: String,
        message: String,
        intervalMillis: Long = DefaultThrottleMillis,
    ): Boolean = logThrottled(key, intervalMillis) { Log.d(Tag, message) }

    fun warnThrottled(
        key: String,
        message: String,
        intervalMillis: Long = DefaultThrottleMillis,
    ): Boolean = logThrottled(key, intervalMillis) { Log.w(Tag, message) }

    private inline fun logThrottled(
        key: String,
        intervalMillis: Long,
        log: () -> Unit,
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        var shouldLog = false
        lastLoggedAtByKey.compute(key) { _, previous ->
            if (previous == null || now - previous >= intervalMillis) {
                shouldLog = true
                now
            } else {
                previous
            }
        }
        if (shouldLog) log()
        return shouldLog
    }
}
