package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playStoreRatingPromptDataStore
    by preferencesDataStore(
        name = "play_store_rating_prompt",
    )

data class PlayStoreRatingPromptState(
    val lastUseEpochDay: Long? = null,
    val consecutiveUseDays: Int = 0,
    val lastEligibilityCheckEpochDay:
        Long? = null,
    val eligiblePromptEpochDay:
        Long? = null,
    val snoozedUntilEpochDay:
        Long? = null,
    val neverShowAgain: Boolean = false,
    val ratedOnPlayStore: Boolean = false,
) {
    val isPermanentlySuppressed: Boolean
        get() =
            neverShowAgain ||
                ratedOnPlayStore

    fun isEligibleOn(
        epochDay: Long,
    ): Boolean {
        return !isPermanentlySuppressed &&
            eligiblePromptEpochDay ==
                epochDay
    }
}

object PlayStoreRatingPromptPolicy {
    const val RequiredConsecutiveUseDays =
        3

    const val EligibilityChancePercent =
        20

    const val SnoozeDays =
        7L

    fun recordUse(
        previous:
            PlayStoreRatingPromptState,
        currentEpochDay: Long,
        chanceRoll: Int,
    ): PlayStoreRatingPromptState {
        if (
            previous.lastUseEpochDay ==
            currentEpochDay
        ) {
            return previous
        }

        val consecutiveUseDays =
            when (
                previous.lastUseEpochDay
            ) {
                currentEpochDay - 1L ->
                    previous
                        .consecutiveUseDays
                        .plus(1)

                else ->
                    1
            }

        val base =
            previous.copy(
                lastUseEpochDay =
                    currentEpochDay,
                consecutiveUseDays =
                    consecutiveUseDays,
                eligiblePromptEpochDay =
                    null,
            )

        if (
            base.isPermanentlySuppressed
        ) {
            return base
        }

        val snoozedUntil =
            base.snoozedUntilEpochDay

        if (
            snoozedUntil != null &&
            currentEpochDay <
                snoozedUntil
        ) {
            return base
        }

        if (
            consecutiveUseDays <
            RequiredConsecutiveUseDays
        ) {
            return base
        }

        if (
            base.lastEligibilityCheckEpochDay ==
            currentEpochDay
        ) {
            return base
        }

        val normalizedRoll =
            chanceRoll.coerceIn(
                minimumValue = 0,
                maximumValue = 99,
            )

        val eligible =
            normalizedRoll <
                EligibilityChancePercent

        return base.copy(
            lastEligibilityCheckEpochDay =
                currentEpochDay,
            eligiblePromptEpochDay =
                if (eligible) {
                    currentEpochDay
                } else {
                    null
                },
        )
    }

    fun showLater(
        previous:
            PlayStoreRatingPromptState,
        currentEpochDay: Long,
    ): PlayStoreRatingPromptState {
        return previous.copy(
            eligiblePromptEpochDay =
                null,
            snoozedUntilEpochDay =
                currentEpochDay +
                    SnoozeDays,
        )
    }

    fun neverShowAgain(
        previous:
            PlayStoreRatingPromptState,
    ): PlayStoreRatingPromptState {
        return previous.copy(
            eligiblePromptEpochDay =
                null,
            neverShowAgain = true,
        )
    }

    fun ratedOnPlayStore(
        previous:
            PlayStoreRatingPromptState,
    ): PlayStoreRatingPromptState {
        return previous.copy(
            eligiblePromptEpochDay =
                null,
            ratedOnPlayStore = true,
        )
    }
}

class PlayStoreRatingPromptDataSource(
    context: Context,
    private val chanceRollProvider:
        () -> Int = {
            Random.nextInt(
                from = 0,
                until = 100,
            )
        },
) {
    private val dataStore =
        context.applicationContext
            .playStoreRatingPromptDataStore

    val state:
        Flow<PlayStoreRatingPromptState> =
        dataStore.data.map {
                preferences ->

            preferences.toState()
        }

    suspend fun recordAppUse(
        nowMillis: Long =
            System.currentTimeMillis(),
        zone: ZoneId =
            ZoneId.systemDefault(),
    ) {
        val currentEpochDay =
            nowMillis.toEpochDay(zone)

        dataStore.edit {
                preferences ->

            val previous =
                preferences.toState()

            val updated =
                PlayStoreRatingPromptPolicy
                    .recordUse(
                        previous = previous,
                        currentEpochDay =
                            currentEpochDay,
                        chanceRoll =
                            chanceRollProvider(),
                    )

            preferences.writeState(
                updated,
            )
        }
    }

    suspend fun showLater(
        nowMillis: Long =
            System.currentTimeMillis(),
        zone: ZoneId =
            ZoneId.systemDefault(),
    ) {
        val currentEpochDay =
            nowMillis.toEpochDay(zone)

        dataStore.edit {
                preferences ->

            val updated =
                PlayStoreRatingPromptPolicy
                    .showLater(
                        previous =
                            preferences
                                .toState(),
                        currentEpochDay =
                            currentEpochDay,
                    )

            preferences.writeState(
                updated,
            )
        }
    }

    suspend fun neverShowAgain() {
        dataStore.edit {
                preferences ->

            val updated =
                PlayStoreRatingPromptPolicy
                    .neverShowAgain(
                        previous =
                            preferences
                                .toState(),
                    )

            preferences.writeState(
                updated,
            )
        }
    }

    suspend fun markRatedOnPlayStore() {
        dataStore.edit {
                preferences ->

            val updated =
                PlayStoreRatingPromptPolicy
                    .ratedOnPlayStore(
                        previous =
                            preferences
                                .toState(),
                    )

            preferences.writeState(
                updated,
            )
        }
    }

    private fun Preferences.toState():
        PlayStoreRatingPromptState {
        return PlayStoreRatingPromptState(
            lastUseEpochDay =
                this[LastUseEpochDayKey],
            consecutiveUseDays =
                this[
                    ConsecutiveUseDaysKey
                ] ?: 0,
            lastEligibilityCheckEpochDay =
                this[
                    LastEligibilityCheckEpochDayKey
                ],
            eligiblePromptEpochDay =
                this[
                    EligiblePromptEpochDayKey
                ],
            snoozedUntilEpochDay =
                this[
                    SnoozedUntilEpochDayKey
                ],
            neverShowAgain =
                this[
                    NeverShowAgainKey
                ] ?: false,
            ratedOnPlayStore =
                this[
                    RatedOnPlayStoreKey
                ] ?: false,
        )
    }

    private fun MutablePreferences
        .writeState(
            state:
                PlayStoreRatingPromptState,
        ) {
        setOrRemove(
            key = LastUseEpochDayKey,
            value =
                state.lastUseEpochDay,
        )

        this[ConsecutiveUseDaysKey] =
            state.consecutiveUseDays

        setOrRemove(
            key =
                LastEligibilityCheckEpochDayKey,
            value =
                state
                    .lastEligibilityCheckEpochDay,
        )

        setOrRemove(
            key =
                EligiblePromptEpochDayKey,
            value =
                state
                    .eligiblePromptEpochDay,
        )

        setOrRemove(
            key =
                SnoozedUntilEpochDayKey,
            value =
                state
                    .snoozedUntilEpochDay,
        )

        this[NeverShowAgainKey] =
            state.neverShowAgain

        this[RatedOnPlayStoreKey] =
            state.ratedOnPlayStore
    }

    private fun MutablePreferences
        .setOrRemove(
            key: Preferences.Key<Long>,
            value: Long?,
        ) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private fun Long.toEpochDay(
        zone: ZoneId,
    ): Long {
        return Instant
            .ofEpochMilli(this)
            .atZone(zone)
            .toLocalDate()
            .toEpochDay()
    }

    private companion object {
        val LastUseEpochDayKey =
            longPreferencesKey(
                "last_use_epoch_day",
            )

        val ConsecutiveUseDaysKey =
            intPreferencesKey(
                "consecutive_use_days",
            )

        val LastEligibilityCheckEpochDayKey =
            longPreferencesKey(
                "last_eligibility_check_epoch_day",
            )

        val EligiblePromptEpochDayKey =
            longPreferencesKey(
                "eligible_prompt_epoch_day",
            )

        val SnoozedUntilEpochDayKey =
            longPreferencesKey(
                "snoozed_until_epoch_day",
            )

        val NeverShowAgainKey =
            booleanPreferencesKey(
                "never_show_again",
            )

        val RatedOnPlayStoreKey =
            booleanPreferencesKey(
                "rated_on_play_store",
            )
    }
}
