package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.safebrowse.MaximumInterruptedLeaseChargeMillis
import com.impulsive.app.backend.domain.model.safebrowse.MaximumRewardReceiptCount
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessSnapshot
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseRewardGrantResult
import com.impulsive.app.backend.domain.model.safebrowse.TwoHourGrantMillis
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private val Context.safeBrowseAccessDataStore by preferencesDataStore(name = "safe_browse_access")

/**
 * Atomic, process-death-safe Safe Browse usage ledger.
 *
 * Persists only accounting values: remaining usage milliseconds, whether a usage lease
 * is active, the lease baseline (elapsed realtime + wall clock, for reconciling an
 * abandoned lease), and a bounded set of already-redeemed reward receipt tokens. Never
 * persists a visited domain, URL, search query, page title or advertiser information.
 */
class SafeBrowseAccessDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.safeBrowseAccessDataStore)

    suspend fun currentSnapshot(): SafeBrowseAccessSnapshot = snapshotFrom(dataStore.data.first())

    /**
     * Reconciles a lease that was still marked active from a previous process -- either a
     * clean stop that failed to run, or a killed process. Charges at most
     * [MaximumInterruptedLeaseChargeMillis], never the entire dead-process interval.
     */
    suspend fun reconcileInterruptedLease(
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): SafeBrowseAccessSnapshot {
        var result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)

        dataStore.edit { preferences ->
            val remaining = (preferences[RemainingMillisKey] ?: 0L).coerceAtLeast(0L)
            val leaseActive = preferences[LeaseActiveKey] ?: false

            if (!leaseActive) {
                result = SafeBrowseAccessSnapshot(remaining, leaseActive = false)
                return@edit
            }

            val baselineElapsed = preferences[LeaseBaselineElapsedKey]
            val baselineEpoch = preferences[LeaseBaselineEpochKey]
            val charge = if (
                baselineElapsed == null || baselineEpoch == null ||
                baselineElapsed < 0L || baselineEpoch < 0L
            ) {
                minOf(remaining, MaximumInterruptedLeaseChargeMillis)
            } else {
                interruptedLeaseCharge(
                    baselineElapsedMillis = baselineElapsed,
                    baselineEpochMillis = baselineEpoch,
                    nowElapsedMillis = nowElapsedMillis,
                    nowEpochMillis = nowEpochMillis,
                )
            }
            val newRemaining = (remaining - charge).coerceAtLeast(0L)

            preferences[RemainingMillisKey] = newRemaining
            preferences[LeaseActiveKey] = false
            preferences.remove(LeaseBaselineElapsedKey)
            preferences.remove(LeaseBaselineEpochKey)
            result = SafeBrowseAccessSnapshot(newRemaining, leaseActive = false)
        }

        return result
    }

    /**
     * Redeems one rewarded-ad receipt token. The same token can never grant access twice,
     * and a fresh grant never stacks on top of an existing balance -- it raises the balance
     * to at most [grantMillis].
     */
    suspend fun grantReward(
        receiptToken: String,
        grantTimedAccess: Boolean = true,
        grantMillis: Long = TwoHourGrantMillis,
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): SafeBrowseRewardGrantResult {
        val trimmedToken = receiptToken.trim()
        require(trimmedToken.isNotEmpty()) { "Safe Browse reward receipt token must not be blank." }
        require(trimmedToken.length <= MaximumTokenLength) { "Safe Browse reward receipt token is too long." }

        var result: SafeBrowseRewardGrantResult = SafeBrowseRewardGrantResult.Duplicate(0L)

        dataStore.edit { preferences ->
            val tokens =
                decodeTokens(
                    preferences[RewardTokensKey],
                )

            val remaining =
                (
                    preferences[RemainingMillisKey]
                        ?: 0L
                ).coerceAtLeast(0L)

            if (!grantTimedAccess) {
                preferences[RemainingMillisKey] =
                    0L

                preferences[LeaseActiveKey] =
                    false

                preferences.remove(
                    LeaseBaselineElapsedKey,
                )

                preferences.remove(
                    LeaseBaselineEpochKey,
                )
            }

            if (trimmedToken in tokens) {
                result =
                    SafeBrowseRewardGrantResult
                        .Duplicate(
                            if (grantTimedAccess) {
                                remaining
                            } else {
                                0L
                            },
                        )

                return@edit
            }

            val newRemaining =
                if (grantTimedAccess) {
                    maxOf(
                        remaining,
                        grantMillis,
                    )
                } else {
                    0L
                }

            preferences[RemainingMillisKey] =
                newRemaining

            preferences[RewardTokensKey] =
                encodeTokens(
                    (
                        tokens +
                            trimmedToken
                    ).takeLast(
                        MaximumRewardReceiptCount,
                    ),
                )

            result =
                SafeBrowseRewardGrantResult
                    .Granted(
                        newRemaining,
                    )
        }

        return result
    }

    /**
     * Zeroes the timed reward balance and clears any active lease when a Safe Browse Pass
     * becomes active -- the Pass supersedes timed access, so no leftover balance should
     * silently resurface if the Pass later lapses. Never clears redeemed reward receipt
     * tokens: those must keep rejecting a replayed rewarded-ad receipt regardless of
     * whether a Pass is active.
     */
    suspend fun clearTimedAccessForPassActivation(): SafeBrowseAccessSnapshot {
        var result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)

        dataStore.edit { preferences ->
            preferences[RemainingMillisKey] = 0L
            preferences[LeaseActiveKey] = false
            preferences.remove(LeaseBaselineElapsedKey)
            preferences.remove(LeaseBaselineEpochKey)
            result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)
        }

        return result
    }

    /** Idempotent: calling this again while already active keeps the existing baseline. */
    suspend fun beginUsage(
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): SafeBrowseAccessSnapshot {
        var result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)

        dataStore.edit { preferences ->
            val remaining = (preferences[RemainingMillisKey] ?: 0L).coerceAtLeast(0L)

            if (remaining <= 0L) {
                preferences[RemainingMillisKey] = 0L
                preferences[LeaseActiveKey] = false
                preferences.remove(LeaseBaselineElapsedKey)
                preferences.remove(LeaseBaselineEpochKey)
                result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)
                return@edit
            }

            val alreadyActive = preferences[LeaseActiveKey] ?: false
            if (!alreadyActive) {
                preferences[LeaseActiveKey] = true
                preferences[LeaseBaselineElapsedKey] = nowElapsedMillis
                preferences[LeaseBaselineEpochKey] = nowEpochMillis
            }

            result = SafeBrowseAccessSnapshot(remaining, leaseActive = true)
        }

        return result
    }

    /** Deducts usage since the last baseline and resets the baseline, keeping the lease active. */
    suspend fun checkpointUsage(
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): SafeBrowseAccessSnapshot = deductActiveUsage(
        nowElapsedMillis = nowElapsedMillis,
        nowEpochMillis = nowEpochMillis,
        keepLeaseActiveIfRemaining = true,
    )

    /** Deducts the final partial interval since the last baseline and clears the lease. */
    suspend fun endUsage(
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): SafeBrowseAccessSnapshot = deductActiveUsage(
        nowElapsedMillis = nowElapsedMillis,
        nowEpochMillis = nowEpochMillis,
        keepLeaseActiveIfRemaining = false,
    )

    private suspend fun deductActiveUsage(
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
        keepLeaseActiveIfRemaining: Boolean,
    ): SafeBrowseAccessSnapshot {
        var result = SafeBrowseAccessSnapshot(remainingMillis = 0L, leaseActive = false)

        dataStore.edit { preferences ->
            val remaining = (preferences[RemainingMillisKey] ?: 0L).coerceAtLeast(0L)
            val leaseActive = preferences[LeaseActiveKey] ?: false

            if (!leaseActive) {
                result = SafeBrowseAccessSnapshot(remaining, leaseActive = false)
                return@edit
            }

            val baselineElapsed = preferences[LeaseBaselineElapsedKey] ?: nowElapsedMillis
            val elapsedSinceBaseline = (nowElapsedMillis - baselineElapsed).coerceAtLeast(0L)
            val newRemaining = (remaining - elapsedSinceBaseline).coerceAtLeast(0L)
            val stillActive = keepLeaseActiveIfRemaining && newRemaining > 0L

            preferences[RemainingMillisKey] = newRemaining
            preferences[LeaseActiveKey] = stillActive

            if (stillActive) {
                preferences[LeaseBaselineElapsedKey] = nowElapsedMillis
                preferences[LeaseBaselineEpochKey] = nowEpochMillis
            } else {
                preferences.remove(LeaseBaselineElapsedKey)
                preferences.remove(LeaseBaselineEpochKey)
            }

            result = SafeBrowseAccessSnapshot(newRemaining, stillActive)
        }

        return result
    }

    private fun interruptedLeaseCharge(
        baselineElapsedMillis: Long,
        baselineEpochMillis: Long,
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): Long {
        val elapsedDelta = nowElapsedMillis - baselineElapsedMillis
        val candidate = if (elapsedDelta >= 0L) {
            elapsedDelta
        } else {
            (nowEpochMillis - baselineEpochMillis).coerceAtLeast(0L)
        }
        return candidate.coerceIn(0L, MaximumInterruptedLeaseChargeMillis)
    }

    private fun decodeTokens(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()

        val array = try {
            JSONArray(encoded)
        } catch (error: Throwable) {
            throw IllegalStateException("Safe Browse reward receipt storage is invalid.", error)
        }

        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)

                if (value !is String) {
                    throw IllegalStateException(
                        "Safe Browse reward receipt storage contains an invalid value.",
                    )
                }

                val token = value.trim()

                if (token.isEmpty() || token.length > MaximumTokenLength) {
                    throw IllegalStateException(
                        "Safe Browse reward receipt storage contains an invalid token.",
                    )
                }

                add(token)
            }
        }
    }

    private fun encodeTokens(tokens: List<String>): String {
        val array = JSONArray()
        tokens.forEach(array::put)
        return array.toString()
    }

    private fun snapshotFrom(preferences: Preferences): SafeBrowseAccessSnapshot =
        SafeBrowseAccessSnapshot(
            remainingMillis = (preferences[RemainingMillisKey] ?: 0L).coerceAtLeast(0L),
            leaseActive = preferences[LeaseActiveKey] ?: false,
        )

    private companion object {
        const val MaximumTokenLength = 160

        val RemainingMillisKey = longPreferencesKey("safe_browse_remaining_millis")
        val LeaseActiveKey = booleanPreferencesKey("safe_browse_lease_active")
        val LeaseBaselineElapsedKey = longPreferencesKey("safe_browse_lease_baseline_elapsed")
        val LeaseBaselineEpochKey = longPreferencesKey("safe_browse_lease_baseline_epoch")
        val RewardTokensKey = stringPreferencesKey("safe_browse_reward_tokens")
    }
}