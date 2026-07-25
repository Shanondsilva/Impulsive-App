package com.impulsive.app.backend.service.protection

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal data class ProtectionMonitorHealthSnapshot(
    val healthyGeneration: Long,
    val lastHeartbeatElapsedRealtimeMillis: Long,
    val active: Boolean,
)

internal fun isProtectionMonitorHeartbeatFresh(
    snapshot: ProtectionMonitorHealthSnapshot,
    nowElapsedRealtimeMillis: Long,
    maxAgeMillis: Long,
): Boolean {
    if (!snapshot.active) {
        return false
    }

    val heartbeat = snapshot.lastHeartbeatElapsedRealtimeMillis

    if (heartbeat <= 0L) {
        return false
    }

    if (nowElapsedRealtimeMillis < heartbeat) {
        return false
    }

    return nowElapsedRealtimeMillis - heartbeat <=
        maxAgeMillis
}

internal fun hasConfirmedProtectionMonitorHeartbeat(
    baselineGeneration: Long,
    snapshot: ProtectionMonitorHealthSnapshot,
): Boolean =
    snapshot.active &&
        snapshot.healthyGeneration > baselineGeneration

internal object ProtectionMonitorHealthRegistry {
    const val HealthyHeartbeatMaxAgeMillis = 90_000L

    const val StartConfirmationTimeoutMillis = 10_000L

    private const val ConfirmationPollMillis = 100L

    private val healthyGeneration = AtomicLong(0L)

    private val lastHeartbeatElapsedRealtimeMillis = AtomicLong(0L)

    private val active = AtomicBoolean(false)

    fun snapshot(): ProtectionMonitorHealthSnapshot =
        ProtectionMonitorHealthSnapshot(
            healthyGeneration = healthyGeneration.get(),
            lastHeartbeatElapsedRealtimeMillis =
                lastHeartbeatElapsedRealtimeMillis.get(),
            active = active.get(),
        )

    fun markHealthy(
        nowElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime(),
    ) {
        lastHeartbeatElapsedRealtimeMillis.set(
            nowElapsedRealtimeMillis,
        )
        active.set(true)
        healthyGeneration.incrementAndGet()
    }

    fun markStopped() {
        active.set(false)
    }

    suspend fun awaitHealthyAfter(
        baselineGeneration: Long,
        timeoutMillis: Long = StartConfirmationTimeoutMillis,
        pollIntervalMillis: Long = ConfirmationPollMillis,
    ): Boolean {
        require(timeoutMillis > 0L)
        require(pollIntervalMillis > 0L)

        return withTimeoutOrNull(timeoutMillis) {
            while (
                !hasConfirmedProtectionMonitorHeartbeat(
                    baselineGeneration = baselineGeneration,
                    snapshot = snapshot(),
                )
            ) {
                delay(pollIntervalMillis)
            }

            true
        } ?: false
    }
}
