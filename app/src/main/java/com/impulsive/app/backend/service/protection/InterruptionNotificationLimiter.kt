package com.impulsive.app.backend.service.protection

import java.util.Locale

sealed interface InterruptionNotificationDecision {
    data class Post(
        val message: String,
    ) : InterruptionNotificationDecision

    data object Suppress : InterruptionNotificationDecision
}

object InterruptionNotificationLimiter {
    private data class Encounter(
        val message: String,
        var fallbackNotificationClaimed: Boolean,
        var lastSeenAtMillis: Long,
    )

    private val lock = Any()
    private val encounters = mutableMapOf<String, Encounter>()

    fun messageForApp(
        packageName: String,
        nowMillis: Long,
        selectMessage: () -> String,
    ): String = synchronized(lock) {
        messageFor(
            key = appKey(packageName),
            nowMillis = nowMillis,
            resetAfterMillis = null,
            selectMessage = selectMessage,
        )
    }

    fun messageForDomain(
        matchedDomain: String,
        nowMillis: Long,
        selectMessage: () -> String,
    ): String = synchronized(lock) {
        messageFor(
            key = domainKey(matchedDomain),
            nowMillis = nowMillis,
            resetAfterMillis = DomainEncounterResetMillis,
            selectMessage = selectMessage,
        )
    }

    fun decideNotificationForApp(
        packageName: String,
        nowMillis: Long,
    ): InterruptionNotificationDecision = synchronized(lock) {
        decideNotification(
            key = appKey(packageName),
            nowMillis = nowMillis,
            resetAfterMillis = null,
        )
    }

    fun decideNotificationForDomain(
        matchedDomain: String,
        nowMillis: Long,
    ): InterruptionNotificationDecision = synchronized(lock) {
        decideNotification(
            key = domainKey(matchedDomain),
            nowMillis = nowMillis,
            resetAfterMillis = DomainEncounterResetMillis,
        )
    }

    fun endAppEncounter(packageName: String) {
        synchronized(lock) {
            encounters.remove(appKey(packageName))
        }
    }

    fun observeDomain(
        matchedDomain: String,
        nowMillis: Long,
    ) {
        synchronized(lock) {
            encounters[domainKey(matchedDomain)]?.lastSeenAtMillis = nowMillis
        }
    }

    fun clearAppEncounters() {
        synchronized(lock) {
            encounters.keys.removeAll { key ->
                key.startsWith(AppPrefix)
            }
        }
    }

    private fun messageFor(
        key: String,
        nowMillis: Long,
        resetAfterMillis: Long?,
        selectMessage: () -> String,
    ): String {
        val current = encounters[key]
        val expired =
            current != null &&
                resetAfterMillis != null &&
                nowMillis - current.lastSeenAtMillis >= resetAfterMillis

        if (current == null || expired) {
            return Encounter(
                message = selectMessage(),
                fallbackNotificationClaimed = false,
                lastSeenAtMillis = nowMillis,
            ).also { encounter ->
                encounters[key] = encounter
            }.message
        }

        current.lastSeenAtMillis = nowMillis
        return current.message
    }

    private fun decideNotification(
        key: String,
        nowMillis: Long,
        resetAfterMillis: Long?,
    ): InterruptionNotificationDecision {
        val encounter = encounters[key]
            ?: return InterruptionNotificationDecision.Suppress

        if (
            resetAfterMillis != null &&
            nowMillis - encounter.lastSeenAtMillis >= resetAfterMillis
        ) {
            encounters.remove(key)
            return InterruptionNotificationDecision.Suppress
        }

        encounter.lastSeenAtMillis = nowMillis

        if (encounter.fallbackNotificationClaimed) {
            return InterruptionNotificationDecision.Suppress
        }

        encounter.fallbackNotificationClaimed = true

        return InterruptionNotificationDecision.Post(
            encounter.message,
        )
    }

    private fun appKey(raw: String): String =
        AppPrefix + canonicalPart(raw)

    private fun domainKey(raw: String): String =
        DomainPrefix + canonicalPart(raw).trimEnd('.')

    private fun canonicalPart(raw: String): String =
        raw.trim().lowercase(Locale.ROOT)

    private const val AppPrefix = "app:"
    private const val DomainPrefix = "domain:"
    private const val DomainEncounterResetMillis = 60_000L
}
