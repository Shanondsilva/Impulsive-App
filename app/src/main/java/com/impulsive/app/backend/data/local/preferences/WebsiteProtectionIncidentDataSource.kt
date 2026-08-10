package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import org.json.JSONObject

data class WebsiteProtectionIncidentRecord(
    val packageName: String,
    val sourceLabel: String,
    val blockedDomain: String,
    val lastAdultActivityAtEpochMillis: Long,
    val incidentStartedAtEpochMillis: Long,
)

object WebsiteProtectionIncidentPolicy {
    /**
     * Internal pending-event freshness only.
     *
     * This is not a user-facing wait period and not a cooldown. A qualifying
     * incident is eligible immediately. The freshness limit only prevents an
     * old persisted DNS event from unexpectedly opening an interruption later.
     */
    const val IncidentFreshnessMillis =
        15_000L

    fun createImmediateIncident(
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
            lastAdultActivityAtEpochMillis =
                nowEpochMillis,
            incidentStartedAtEpochMillis =
                nowEpochMillis,
        )

    fun onAdultActivity(
        record: WebsiteProtectionIncidentRecord,
        sourceLabel: String,
        blockedDomain: String,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord {
        val current =
            validate(
                record,
            )

        if (
            current == null ||
            !isFresh(
                record = current,
                nowEpochMillis = nowEpochMillis,
            )
        ) {
            return createImmediateIncident(
                packageName = record.packageName,
                sourceLabel = sourceLabel,
                blockedDomain = blockedDomain,
                nowEpochMillis = nowEpochMillis,
            )
        }

        return current.copy(
            sourceLabel =
                sourceLabel,
            blockedDomain =
                blockedDomain,
            lastAdultActivityAtEpochMillis =
                nowEpochMillis,
        )
    }

    fun reconcile(
        record: WebsiteProtectionIncidentRecord,
        foregroundPackage: String?,
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord? {
        val current =
            validate(
                record,
            ) ?: return null

        val foregroundKey =
            foregroundPackage
                ?.let(::canonicalAccessKey)
                ?.ifBlank {
                    null
                }
                ?: return null

        if (foregroundKey != current.packageName) {
            return null
        }

        if (
            !isFresh(
                record = current,
                nowEpochMillis = nowEpochMillis,
            )
        ) {
            return null
        }

        return current
    }

    fun validate(
        record: WebsiteProtectionIncidentRecord,
    ): WebsiteProtectionIncidentRecord? {
        val canonicalPackageName =
            canonicalAccessKey(
                record.packageName,
            )

        if (canonicalPackageName.isBlank()) {
            return null
        }

        if (
            record.lastAdultActivityAtEpochMillis < 0L ||
            record.incidentStartedAtEpochMillis < 0L ||
            record.incidentStartedAtEpochMillis >
            record.lastAdultActivityAtEpochMillis
        ) {
            return null
        }

        return if (
            canonicalPackageName ==
            record.packageName
        ) {
            record
        } else {
            record.copy(
                packageName =
                    canonicalPackageName,
            )
        }
    }

    fun isFresh(
        record: WebsiteProtectionIncidentRecord,
        nowEpochMillis: Long,
    ): Boolean {
        val ageMillis =
            nowEpochMillis -
                record.lastAdultActivityAtEpochMillis

        return ageMillis in
            0L..IncidentFreshnessMillis
    }
}

class WebsiteProtectionIncidentDataSource(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        )

    init {
        /*
         * v3 contained the removed friction/cooldown state machine.
         * The v4 store deliberately does not decode or migrate those records.
         */
        appContext
            .getSharedPreferences(
                LegacyPreferencesName,
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .apply()
    }

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

            require(
                key.isNotBlank(),
            ) {
                "Website Protection incident package must not be blank"
            }

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
                        .createImmediateIncident(
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
                    ?.ifBlank {
                        null
                    }

            var foregroundIncident:
                WebsiteProtectionIncidentRecord? =
                null

            activeKeysLocked()
                .forEach { packageName ->
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
                        evaluated.packageName ==
                        foregroundKey
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
            val key =
                canonicalAccessKey(
                    packageName,
                )

            if (key.isBlank()) {
                return@synchronized null
            }

            readAndEvaluateLocked(
                packageName =
                    key,
                foregroundPackage =
                    key,
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
        if (packageName.isBlank()) {
            return
        }

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
    ): String =
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
                LastAdultActivityAtKey,
                record.lastAdultActivityAtEpochMillis,
            )
            .put(
                IncidentStartedAtKey,
                record.incidentStartedAtEpochMillis,
            )
            .toString()

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
                        lastAdultActivityAtEpochMillis =
                            json.getLong(
                                LastAdultActivityAtKey,
                            ),
                        incidentStartedAtEpochMillis =
                            json.getLong(
                                IncidentStartedAtKey,
                            ),
                    ),
                )
        }.getOrNull()

    private companion object {
        private val ProcessLock =
            Any()

        const val PreferencesName =
            "website_protection_incidents_v4"

        const val LegacyPreferencesName =
            "website_protection_incidents_v3"

        const val IncidentKeyPrefix =
            "incident:"

        const val PackageNameKey =
            "packageName"

        const val SourceLabelKey =
            "sourceLabel"

        const val BlockedDomainKey =
            "blockedDomain"

        const val LastAdultActivityAtKey =
            "lastAdultActivityAtEpochMillis"

        const val IncidentStartedAtKey =
            "incidentStartedAtEpochMillis"
    }
}
