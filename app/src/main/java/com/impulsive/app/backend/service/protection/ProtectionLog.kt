package com.impulsive.app.backend.service.protection

import android.os.SystemClock
import android.util.Log
import com.impulsive.app.BuildConfig
import java.util.concurrent.ConcurrentHashMap

internal object ProtectionLog {
    const val Tag = "ImpulsiveProtection"

    private const val DefaultThrottleMillis = 20_000L
    private val lastLoggedAtByKey = ConcurrentHashMap<String, Long>()

    fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(Tag, message)
        }
    }

    fun warn(
        message: String,
        debugDetails: String? = null,
    ) {
        Log.w(
            Tag,
            messageWithDebugDetails(
                message = message,
                debugDetails = debugDetails,
            ),
        )
    }

    fun error(
        message: String,
        debugDetails: String? = null,
    ) {
        Log.e(
            Tag,
            messageWithDebugDetails(
                message = message,
                debugDetails = debugDetails,
            ),
        )
    }

    fun debugThrottled(
        key: String,
        message: String,
        intervalMillis: Long = DefaultThrottleMillis,
    ): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }

        return logThrottled(
            key = key,
            intervalMillis = intervalMillis,
        ) {
            Log.d(Tag, message)
        }
    }

    fun warnThrottled(
        key: String,
        message: String,
        intervalMillis: Long = DefaultThrottleMillis,
        debugDetails: String? = null,
    ): Boolean =
        logThrottled(
            key = key,
            intervalMillis = intervalMillis,
        ) {
            Log.w(
                Tag,
                messageWithDebugDetails(
                    message = message,
                    debugDetails = debugDetails,
                ),
            )
        }

    private fun messageWithDebugDetails(
        message: String,
        debugDetails: String?,
    ): String =
        if (
            BuildConfig.DEBUG &&
            !debugDetails.isNullOrBlank()
        ) {
            "$message [$debugDetails]"
        } else {
            message
        }

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
