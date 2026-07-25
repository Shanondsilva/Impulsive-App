package com.impulsive.app.backend.service.protection

/**
 * Endpoint selection for the DoH resolver. Primary is Cloudflare Family;
 * Fallback is AdGuard Family Protection. Rules: start on Primary; after
 * [failuresBeforeFallback] consecutive Primary failures switch to Fallback;
 * while on Fallback, probe Primary again after [primaryRetryIntervalMillis]
 * (a successful probe switches back); a Fallback failure allows an immediate
 * Primary probe so the resolver never pins to a dead endpoint.
 */
internal class DoHFailoverPolicy(
    private val failuresBeforeFallback: Int = 3,
    private val primaryRetryIntervalMillis: Long = 60_000L,
) {

    enum class Endpoint { Primary, Fallback }

    private val lock = Any()
    private var active: Endpoint = Endpoint.Primary
    private var consecutivePrimaryFailures: Int = 0
    private var fallbackSinceMillis: Long = 0L

    fun endpointForNextQuery(nowMillis: Long): Endpoint = synchronized(lock) {
        if (
            active == Endpoint.Fallback &&
            nowMillis - fallbackSinceMillis >= primaryRetryIntervalMillis
        ) {
            Endpoint.Primary
        } else {
            active
        }
    }

    fun recordResult(endpoint: Endpoint, success: Boolean, nowMillis: Long) {
        synchronized(lock) {
            if (success) {
                if (endpoint == Endpoint.Primary) {
                    active = Endpoint.Primary
                    consecutivePrimaryFailures = 0
                }
                return
            }
            if (endpoint == Endpoint.Primary) {
                consecutivePrimaryFailures += 1
                if (
                    consecutivePrimaryFailures >= failuresBeforeFallback ||
                    active == Endpoint.Fallback
                ) {
                    active = Endpoint.Fallback
                    fallbackSinceMillis = nowMillis
                }
            } else {
                fallbackSinceMillis = 0L
            }
        }
    }
}
