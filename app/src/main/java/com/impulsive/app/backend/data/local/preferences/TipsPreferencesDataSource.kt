package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAX_TIP_HISTORY = 64
private const val ITEM_SEPARATOR = "\u001F"
private const val VALUE_SEPARATOR = "\u001E"

private val Context.tipsDataStore by preferencesDataStore(name = "tips_presentation_state")

/**
 * Non-critical, on-device presentation state. It contains only bounded catalogue IDs and
 * timestamps, is never uploaded or added to analytics/recovery, and may reset after reinstall.
 */
data class TipsPreferencesState(
    val lastShownTipId: ImpulsiveTipId? = null,
    val lastShownEpochDay: Long? = null,
    val currentHomeTipId: ImpulsiveTipId? = null,
    val viewedTipIds: Set<ImpulsiveTipId> = emptySet(),
    val dismissedTipIds: Set<ImpulsiveTipId> = emptySet(),
    val lastShownEpochDayByTip: Map<ImpulsiveTipId, Long> = emptyMap(),
    val lastRotationTimestampMillis: Long? = null,
    val introductionSeen: Boolean = false,
)

class TipsPreferencesDataSource(context: Context) {
    private val dataStore = context.applicationContext.tipsDataStore

    val state: Flow<TipsPreferencesState> = dataStore.data.map(::decode)

    suspend fun recordShown(tipId: ImpulsiveTipId, epochDay: Long, nowMillis: Long) {
        dataStore.edit { preferences ->
            val viewed = decodeIds(preferences[ViewedIdsKey]).toMutableList()
            viewed.remove(tipId)
            viewed.add(tipId)
            val shown = decodeShown(preferences[ShownEpochDaysKey]).toMutableMap()
            shown.remove(tipId)
            shown[tipId] = epochDay
            preferences[LastShownIdKey] = tipId.value
            preferences[LastShownEpochDayKey] = epochDay
            preferences[CurrentHomeIdKey] = tipId.value
            preferences[ViewedIdsKey] = encodeIds(viewed.takeLast(MAX_TIP_HISTORY))
            preferences[ShownEpochDaysKey] = encodeShown(
                shown.entries.toList().takeLast(MAX_TIP_HISTORY).associate { it.toPair() },
            )
            preferences[LastRotationMillisKey] = nowMillis
        }
    }

    suspend fun markViewed(tipId: ImpulsiveTipId) {
        dataStore.edit { preferences ->
            val ids = decodeIds(preferences[ViewedIdsKey]).toMutableList()
            ids.remove(tipId)
            ids.add(tipId)
            preferences[ViewedIdsKey] = encodeIds(ids.takeLast(MAX_TIP_HISTORY))
        }
    }

    suspend fun dismiss(tipId: ImpulsiveTipId) {
        dataStore.edit { preferences ->
            val ids = decodeIds(preferences[DismissedIdsKey]).toMutableList()
            ids.remove(tipId)
            ids.add(tipId)
            preferences[DismissedIdsKey] = encodeIds(ids.takeLast(MAX_TIP_HISTORY))
            if (preferences[CurrentHomeIdKey] == tipId.value) {
                preferences.remove(CurrentHomeIdKey)
            }
        }
    }

    suspend fun markIntroductionSeen() {
        dataStore.edit { it[IntroductionSeenKey] = true }
    }

    suspend fun resetHiddenTips() {
        dataStore.edit { it.remove(DismissedIdsKey) }
    }

    private fun decode(preferences: Preferences): TipsPreferencesState =
        TipsPreferencesState(
            lastShownTipId = preferences[LastShownIdKey]?.toTipIdOrNull(),
            lastShownEpochDay = preferences[LastShownEpochDayKey],
            currentHomeTipId = preferences[CurrentHomeIdKey]?.toTipIdOrNull(),
            viewedTipIds = decodeIds(preferences[ViewedIdsKey]).toSet(),
            dismissedTipIds = decodeIds(preferences[DismissedIdsKey]).toSet(),
            lastShownEpochDayByTip = decodeShown(preferences[ShownEpochDaysKey]),
            lastRotationTimestampMillis = preferences[LastRotationMillisKey],
            introductionSeen = preferences[IntroductionSeenKey] ?: false,
        )

    internal companion object {
        val LastShownIdKey = stringPreferencesKey("last_shown_tip_id")
        val LastShownEpochDayKey = longPreferencesKey("last_shown_epoch_day")
        val CurrentHomeIdKey = stringPreferencesKey("current_home_tip_id")
        val ViewedIdsKey = stringPreferencesKey("viewed_tip_ids")
        val DismissedIdsKey = stringPreferencesKey("dismissed_tip_ids")
        val ShownEpochDaysKey = stringPreferencesKey("shown_epoch_days_by_tip")
        val LastRotationMillisKey = longPreferencesKey("last_rotation_timestamp_millis")
        val IntroductionSeenKey = booleanPreferencesKey("introduction_seen")

        internal fun encodeIds(ids: Collection<ImpulsiveTipId>): String =
            ids.joinToString(ITEM_SEPARATOR) { it.value }

        internal fun decodeIds(raw: String?): List<ImpulsiveTipId> =
            raw.orEmpty()
                .split(ITEM_SEPARATOR)
                .mapNotNull { it.toTipIdOrNull() }
                .distinct()
                .takeLast(MAX_TIP_HISTORY)

        internal fun encodeShown(values: Map<ImpulsiveTipId, Long>): String =
            values.entries.joinToString(ITEM_SEPARATOR) {
                "${it.key.value}$VALUE_SEPARATOR${it.value}"
            }

        internal fun decodeShown(raw: String?): Map<ImpulsiveTipId, Long> =
            raw.orEmpty()
                .split(ITEM_SEPARATOR)
                .mapNotNull { entry ->
                    val parts = entry.split(VALUE_SEPARATOR)
                    if (parts.size != 2) return@mapNotNull null
                    val id = parts[0].toTipIdOrNull() ?: return@mapNotNull null
                    val epochDay = parts[1].toLongOrNull() ?: return@mapNotNull null
                    id to epochDay
                }
                .takeLast(MAX_TIP_HISTORY)
                .toMap()

        private fun String.toTipIdOrNull(): ImpulsiveTipId? =
            takeIf { it.matches(Regex("[a-z0-9_]+")) }
                ?.let(::ImpulsiveTipId)
    }
}
