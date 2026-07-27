package com.impulsive.app.backend.service.protection

import java.util.Locale

sealed interface InterruptionNotificationDecision {
    data class Post(
        val message: String,
        val stage: InterruptionNotificationStage,
    ) : InterruptionNotificationDecision

    data object Suppress : InterruptionNotificationDecision
}

enum class InterruptionNotificationStage(
    internal val elapsedThresholdMillis: Long,
) {
    Initial(0L),
    TwentySeconds(20_000L),
    FortySeconds(40_000L),
}

object InterruptionNotificationLimiter {
    private data class Encounter(
        val message: String,
        val startedAtMillis: Long,
        var lastSubmittedStage: InterruptionNotificationStage?,
        var lastSeenAtMillis: Long,
    )

    private val lock = Any()
    private val encounters = mutableMapOf<String, Encounter>()

    fun messageForApp(
        packageName: String,
        nowMillis: Long,
        incidentStartedAtMillis: Long? = null,
        selectMessage: () -> String,
    ): String = synchronized(lock) {
        messageFor(
            key = appKey(packageName),
            nowMillis = nowMillis,
            incidentStartedAtMillis = incidentStartedAtMillis,
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
            incidentStartedAtMillis = null,
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
            stages = AppNotificationStages,
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
            stages = DomainNotificationStages,
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
        incidentStartedAtMillis: Long?,
        resetAfterMillis: Long?,
        selectMessage: () -> String,
    ): String {
        val current = encounters[key]
        val expired =
            current != null &&
                resetAfterMillis != null &&
                nowMillis - current.lastSeenAtMillis >= resetAfterMillis
        val replaced =
            current != null &&
                incidentStartedAtMillis != null &&
                current.startedAtMillis != incidentStartedAtMillis

        if (current == null || expired || replaced) {
            return Encounter(
                message = selectMessage(),
                startedAtMillis = incidentStartedAtMillis ?: nowMillis,
                lastSubmittedStage = null,
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
        stages: List<InterruptionNotificationStage>,
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

        val elapsedMillis =
            (nowMillis - encounter.startedAtMillis).coerceAtLeast(0L)
        val stage =
            stages.lastOrNull { candidate ->
                elapsedMillis >= candidate.elapsedThresholdMillis
            } ?: return InterruptionNotificationDecision.Suppress

        if (
            encounter.lastSubmittedStage != null &&
            encounter.lastSubmittedStage!!.ordinal >= stage.ordinal
        ) {
            return InterruptionNotificationDecision.Suppress
        }

        encounter.lastSubmittedStage = stage

        return InterruptionNotificationDecision.Post(
            encounter.message,
            stage,
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
    private val AppNotificationStages =
        listOf(
            InterruptionNotificationStage.Initial,
            InterruptionNotificationStage.TwentySeconds,
            InterruptionNotificationStage.FortySeconds,
        )
    private val DomainNotificationStages =
        listOf(
            InterruptionNotificationStage.Initial,
        )
}
