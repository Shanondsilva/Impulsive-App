package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import org.json.JSONObject

enum class WebsiteProtectionIncidentPhase {
    Friction,
    Cooldown,
}

data class WebsiteProtectionIncidentRecord(
    val packageName: String,
    val sourceLabel: String,
    val blockedDomain: String,
    val phase: WebsiteProtectionIncidentPhase,
    val accumulatedFrictionMillis: Long,
    val lastAdultActivityAtEpochMillis: Long?,
    val activeSegmentStartedAtEpochMillis: Long?,
    val pausedAtEpochMillis: Long?,
    val cooldownStartedAtEpochMillis: Long?,
    val cooldownUntilEpochMillis: Long?,
) {
    fun frictionElapsedMillis(
        nowEpochMillis: Long,
    ): Long {
        if (phase != WebsiteProtectionIncidentPhase.Friction) {
            return WebsiteProtectionIncidentPolicy.FrictionMillis
        }

        val activeSegmentElapsed =
            activeSegmentStartedAtEpochMillis
                ?.let { startedAt ->
                    val leaseUntil =
                        lastAdultActivityAtEpochMillis
                            ?.plus(
                                WebsiteProtectionIncidentPolicy.ResumeGraceMillis,
                            )
                            ?: startedAt

                    val effectiveNow =
                        minOf(
                            nowEpochMillis,
                            leaseUntil,
                        )

                    (
                        effectiveNow -
                            startedAt
                    ).coerceAtLeast(0L)
                }
                ?: 0L

        return (
            accumulatedFrictionMillis +
                activeSegmentElapsed
        ).coerceIn(
            0L,
            WebsiteProtectionIncidentPolicy.FrictionMillis,
        )
    }

    fun frictionRemainingMillis(
        nowEpochMillis: Long,
    ): Long =
        (
            WebsiteProtectionIncidentPolicy.FrictionMillis -
                frictionElapsedMillis(
                    nowEpochMillis,
                )
        ).coerceAtLeast(0L)

    fun isCooldownActive(
        nowEpochMillis: Long,
    ): Boolean =
        phase == WebsiteProtectionIncidentPhase.Cooldown &&
            cooldownUntilEpochMillis != null &&
            nowEpochMillis < cooldownUntilEpochMillis

    fun cooldownRemainingMillis(
        nowEpochMillis: Long,
    ): Long =
        if (phase == WebsiteProtectionIncidentPhase.Cooldown) {
            (
                (cooldownUntilEpochMillis ?: nowEpochMillis) -
                    nowEpochMillis
            ).coerceAtLeast(0L)
        } else {
            0L
        }
}

object WebsiteProtectionIncidentPolicy {
    const val FrictionMillis =
        30_000L

    const val ResumeGraceMillis =
        15_000L

    const val CooldownMillis =
        7 * 60_000L

    fun createFriction(
        packageName: String,
        sourceLabel: String,
        blockedDomain: String,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentRecord(
            packageName =
                canonicalAccessKey(
                    packageName,
                ),
            sourceLabel =
                sourceLabel,
            blockedDomain =
                blockedDomain,
            phase =
                WebsiteProtectionIncidentPhase.Friction,
            accumulatedFrictionMillis =
                0L,
            lastAdultActivityAtEpochMillis =
                nowEpochMillis,
            activeSegmentStartedAtEpochMillis =
                nowEpochMillis,
            pausedAtEpochMillis =
                null,
            cooldownStartedAtEpochMillis =
                null,
            cooldownUntilEpochMillis =
                null,
        )

    fun onAdultActivity(
        record: WebsiteProtectionIncidentRecord,
        sourceLabel: String,
        blockedDomain: String,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord {
        val current =
            reconcile(
                record =
                    record,
                foregroundPackage =
                    record.packageName,
                nowEpochMillis =
                    nowEpochMillis,
            ) ?: return createFriction(
                packageName =
                    record.packageName,
                sourceLabel =
                    sourceLabel,
                blockedDomain =
                    blockedDomain,
                nowEpochMillis =
                    nowEpochMillis,
            )

        return when (current.phase) {
            WebsiteProtectionIncidentPhase.Friction ->
                current.copy(
                    sourceLabel =
                        sourceLabel,
                    blockedDomain =
                        blockedDomain,
                    lastAdultActivityAtEpochMillis =
                        nowEpochMillis,
                )

            WebsiteProtectionIncidentPhase.Cooldown ->
                current.copy(
                    sourceLabel =
                        sourceLabel,
                    blockedDomain =
                        blockedDomain,
                )
        }
    }
    fun reconcile(
        record: WebsiteProtectionIncidentRecord,
        foregroundPackage: String?,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? =
        when (record.phase) {
            WebsiteProtectionIncidentPhase.Friction ->
                reconcileFriction(
                    record =
                        record,
                    foregroundPackage =
                        foregroundPackage,
                    nowEpochMillis =
                        nowEpochMillis,
                )

            WebsiteProtectionIncidentPhase.Cooldown ->
                reconcileCooldown(
                    record =
                        record,
                    nowEpochMillis =
                        nowEpochMillis,
                )
        }

    fun validate(
        record: WebsiteProtectionIncidentRecord,
    ): WebsiteProtectionIncidentRecord? {
        if (record.packageName.isBlank()) {
            return null
        }

        if (
            record.accumulatedFrictionMillis < 0L ||
            record.accumulatedFrictionMillis > FrictionMillis
        ) {
            return null
        }

        return when (record.phase) {
            WebsiteProtectionIncidentPhase.Friction -> {
                if (
                    record.cooldownStartedAtEpochMillis != null ||
                    record.cooldownUntilEpochMillis != null
                ) {
                    return null
                }

                if (record.lastAdultActivityAtEpochMillis == null) {
                    return null
                }

                val hasActiveSegment =
                    record.activeSegmentStartedAtEpochMillis != null
                val hasPausedMarker =
                    record.pausedAtEpochMillis != null

                if (hasActiveSegment == hasPausedMarker) {
                    null
                } else {
                    record
                }
            }
            WebsiteProtectionIncidentPhase.Cooldown -> {
                val cooldownStartedAt =
                    record.cooldownStartedAtEpochMillis
                        ?: return null
                val cooldownUntil =
                    record.cooldownUntilEpochMillis
                        ?: return null

                if (
                    record.activeSegmentStartedAtEpochMillis != null ||
                    record.pausedAtEpochMillis != null ||
                    record.lastAdultActivityAtEpochMillis != null ||
                    cooldownUntil <= cooldownStartedAt
                ) {
                    null
                } else {
                    record.copy(
                        accumulatedFrictionMillis =
                            FrictionMillis,
                    )
                }
            }
        }
    }

    private fun reconcileFriction(
        record: WebsiteProtectionIncidentRecord,
        foregroundPackage: String?,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? {
        val lastAdultActivityAt =
            record.lastAdultActivityAtEpochMillis
                ?: return null

        val activityLeaseUntil =
            lastAdultActivityAt +
                ResumeGraceMillis

        val activeSegmentStartedAt =
            record.activeSegmentStartedAtEpochMillis

        val pausedAt =
            record.pausedAtEpochMillis

        if (activeSegmentStartedAt != null) {
            val effectiveSegmentEnd =
                minOf(
                    nowEpochMillis,
                    activityLeaseUntil,
                )

            val segmentElapsed =
                (
                    effectiveSegmentEnd -
                        activeSegmentStartedAt
                ).coerceAtLeast(0L)

            val elapsed =
                (
                    record.accumulatedFrictionMillis +
                        segmentElapsed
                ).coerceAtMost(
                    FrictionMillis,
                )

            if (elapsed >= FrictionMillis) {
                val remainingBeforeSegment =
                    (
                        FrictionMillis -
                            record.accumulatedFrictionMillis
                    ).coerceAtLeast(0L)

                val cooldownStartedAt =
                    activeSegmentStartedAt +
                        remainingBeforeSegment

                return beginCooldown(
                    record =
                        record,
                    cooldownStartedAtEpochMillis =
                        cooldownStartedAt,
                )
            }

            // No adult-domain activity for more than 15 seconds:
            // clear the partial friction session.
            if (nowEpochMillis > activityLeaseUntil) {
                return null
            }

            return if (foregroundPackage == record.packageName) {
                record
            } else {
                record.copy(
                    accumulatedFrictionMillis =
                        elapsed,
                    activeSegmentStartedAtEpochMillis =
                        null,
                    pausedAtEpochMillis =
                        nowEpochMillis,
                )
            }
        }

        if (pausedAt != null) {
            if (nowEpochMillis > activityLeaseUntil) {
                return null
            }

            return if (foregroundPackage == record.packageName) {
                record.copy(
                    activeSegmentStartedAtEpochMillis =
                        nowEpochMillis,
                    pausedAtEpochMillis =
                        null,
                )
            } else {
                record
            }
        }

        return null
    }
    private fun reconcileCooldown(
        record: WebsiteProtectionIncidentRecord,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? {
        val cooldownUntil =
            record.cooldownUntilEpochMillis
                ?: return null

        return if (nowEpochMillis < cooldownUntil) {
            record
        } else {
            null
        }
    }

    private fun beginCooldown(
        record: WebsiteProtectionIncidentRecord,
        cooldownStartedAtEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        record.copy(
            phase =
                WebsiteProtectionIncidentPhase.Cooldown,
            accumulatedFrictionMillis =
                FrictionMillis,
            activeSegmentStartedAtEpochMillis =
                null,
            pausedAtEpochMillis =
                null,
            lastAdultActivityAtEpochMillis =
                null,
            cooldownStartedAtEpochMillis =
                cooldownStartedAtEpochMillis,
            cooldownUntilEpochMillis =
                cooldownStartedAtEpochMillis +
                    CooldownMillis,
        )
}

class WebsiteProtectionIncidentDataSource(
    context: Context,
) {
    private val preferences =
        context
            .applicationContext
            .getSharedPreferences(
                PreferencesName,
                Context.MODE_PRIVATE,
            )

    fun recordAdultActivity(
        packageName: String,
        sourceLabel: String,
        blockedDomain: String,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        synchronized(ProcessLock) {
            val key =
                canonicalAccessKey(
                    packageName,
                )

            val current =
                readAndEvaluateLocked(
                    packageName =
                        key,
                    foregroundPackage =
                        key,
                    nowEpochMillis =
                        nowEpochMillis,
                )

            val updated =
                if (current == null) {
                    WebsiteProtectionIncidentPolicy
                        .createFriction(
                            packageName =
                                key,
                            sourceLabel =
                                sourceLabel,
                            blockedDomain =
                                blockedDomain,
                            nowEpochMillis =
                                nowEpochMillis,
                        )
                } else {
                    WebsiteProtectionIncidentPolicy
                        .onAdultActivity(
                            record =
                                current,
                            sourceLabel =
                                sourceLabel,
                            blockedDomain =
                                blockedDomain,
                            nowEpochMillis =
                                nowEpochMillis,
                        )
                }

            writeLocked(
                updated,
            )

            updated
        }

    fun reconcileForegroundPackage(
        foregroundPackage: String?,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? =
        synchronized(ProcessLock) {
            val foregroundKey =
                foregroundPackage
                    ?.let(::canonicalAccessKey)
                    ?.ifBlank { null }
            var foregroundIncident: WebsiteProtectionIncidentRecord? = null

            activeKeysLocked().forEach { packageName ->
                val evaluated =
                    readAndEvaluateLocked(
                        packageName =
                            packageName,
                        foregroundPackage =
                            foregroundKey,
                        nowEpochMillis =
                            nowEpochMillis,
                    )

                if (
                    evaluated != null &&
                    evaluated.packageName == foregroundKey
                ) {
                    foregroundIncident =
                        evaluated
                }
            }

            foregroundIncident
        }

    fun getCurrent(
        packageName: String,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? =
        synchronized(ProcessLock) {
            readAndEvaluateLocked(
                packageName =
                    canonicalAccessKey(
                        packageName,
                    ),
                foregroundPackage =
                    null,
                nowEpochMillis =
                    nowEpochMillis,
            )
        }

    fun clear(
        packageName: String,
    ) {
        synchronized(ProcessLock) {
            removeLocked(
                canonicalAccessKey(
                    packageName,
                ),
            )
        }
    }

    private fun readAndEvaluateLocked(
        packageName: String,
        foregroundPackage: String?,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? {
        val raw =
            preferences.getString(
                preferenceKey(
                    packageName,
                ),
                null,
            ) ?: return null

        val decoded =
            decode(
                raw,
            )

        if (decoded == null) {
            removeLocked(
                packageName,
            )
            return null
        }

        val evaluated =
            WebsiteProtectionIncidentPolicy
                .reconcile(
                    record =
                        decoded,
                    foregroundPackage =
                        foregroundPackage,
                    nowEpochMillis =
                        nowEpochMillis,
                )

        when {
            evaluated == null ->
                removeLocked(
                    packageName,
                )

            evaluated != decoded ->
                writeLocked(
                    evaluated,
                )
        }

        return evaluated
    }

    private fun activeKeysLocked(): List<String> =
        preferences
            .all
            .keys
            .asSequence()
            .filter {
                it.startsWith(
                    IncidentKeyPrefix,
                )
            }
            .map {
                it.removePrefix(
                    IncidentKeyPrefix,
                )
            }
            .toList()

    private fun writeLocked(
        record: WebsiteProtectionIncidentRecord,
    ) {
        preferences
            .edit()
            .putString(
                preferenceKey(
                    record.packageName,
                ),
                encode(
                    record,
                ),
            )
            .apply()
    }

    private fun removeLocked(
        packageName: String,
    ) {
        preferences
            .edit()
            .remove(
                preferenceKey(
                    packageName,
                ),
            )
            .apply()
    }

    private fun preferenceKey(
        packageName: String,
    ): String =
        IncidentKeyPrefix +
            canonicalAccessKey(
                packageName,
            )

    private fun encode(
        record: WebsiteProtectionIncidentRecord,
    ): String {
        val json =
            JSONObject()
                .put(
                    PackageNameKey,
                    record.packageName,
                )
                .put(
                    SourceLabelKey,
                    record.sourceLabel,
                )
                .put(
                    BlockedDomainKey,
                    record.blockedDomain,
                )
                .put(
                    PhaseKey,
                    record.phase.name,
                )
                .put(
                    AccumulatedFrictionKey,
                    record.accumulatedFrictionMillis,
                )

        record
            .lastAdultActivityAtEpochMillis
            ?.let {
                json.put(
                    LastAdultActivityAtKey,
                    it,
                )
            }

        record
            .activeSegmentStartedAtEpochMillis
            ?.let {
                json.put(
                    ActiveSegmentStartedAtKey,
                    it,
                )
            }

        record
            .pausedAtEpochMillis
            ?.let {
                json.put(
                    PausedAtKey,
                    it,
                )
            }

        record
            .cooldownStartedAtEpochMillis
            ?.let {
                json.put(
                    CooldownStartedAtKey,
                    it,
                )
            }

        record
            .cooldownUntilEpochMillis
            ?.let {
                json.put(
                    CooldownUntilKey,
                    it,
                )
            }

        return json.toString()
    }

    private fun decode(
        raw: String,
    ): WebsiteProtectionIncidentRecord? =
        runCatching {
            val json =
                JSONObject(
                    raw,
                )

            WebsiteProtectionIncidentPolicy
                .validate(
                    WebsiteProtectionIncidentRecord(
                        packageName =
                            canonicalAccessKey(
                                json.getString(
                                    PackageNameKey,
                                ),
                            ),
                        sourceLabel =
                            json.optString(
                                SourceLabelKey,
                            ),
                        blockedDomain =
                            json.optString(
                                BlockedDomainKey,
                            ),
                        phase =
                            WebsiteProtectionIncidentPhase
                                .valueOf(
                                    json.getString(
                                        PhaseKey,
                                    ),
                                ),
                        accumulatedFrictionMillis =
                            json.getLong(
                                AccumulatedFrictionKey,
                            ),
                        lastAdultActivityAtEpochMillis =
                            optionalLong(
                                json =
                                    json,
                                key =
                                    LastAdultActivityAtKey,
                            ),
                        activeSegmentStartedAtEpochMillis =
                            optionalLong(
                                json =
                                    json,
                                key =
                                    ActiveSegmentStartedAtKey,
                            ),
                        pausedAtEpochMillis =
                            optionalLong(
                                json =
                                    json,
                                key =
                                    PausedAtKey,
                            ),
                        cooldownStartedAtEpochMillis =
                            optionalLong(
                                json =
                                    json,
                                key =
                                    CooldownStartedAtKey,
                            ),
                        cooldownUntilEpochMillis =
                            optionalLong(
                                json =
                                    json,
                                key =
                                    CooldownUntilKey,
                            ),
                    ),
                )
        }.getOrNull()

    private fun optionalLong(
        json: JSONObject,
        key: String,
    ): Long? =
        if (
            json.has(
                key,
            ) &&
            !json.isNull(
                key,
            )
        ) {
            json.getLong(
                key,
            )
        } else {
            null
        }

    private companion object {
        private val ProcessLock =
            Any()

        const val PreferencesName =
            "website_protection_incidents_v3"

        const val IncidentKeyPrefix =
            "incident:"

        const val PackageNameKey =
            "packageName"

        const val SourceLabelKey =
            "sourceLabel"

        const val BlockedDomainKey =
            "blockedDomain"

        const val PhaseKey =
            "phase"

        const val AccumulatedFrictionKey =
            "accumulatedFrictionMillis"

        const val LastAdultActivityAtKey =
            "lastAdultActivityAtEpochMillis"

        const val ActiveSegmentStartedAtKey =
            "activeSegmentStartedAtEpochMillis"

        const val PausedAtKey =
            "pausedAtEpochMillis"

        const val CooldownStartedAtKey =
            "cooldownStartedAtEpochMillis"

        const val CooldownUntilKey =
            "cooldownUntilEpochMillis"
    }
}
