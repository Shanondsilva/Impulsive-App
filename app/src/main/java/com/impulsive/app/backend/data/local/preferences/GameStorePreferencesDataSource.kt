package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.store.GameAccess
import com.impulsive.app.backend.domain.model.store.GameStoreCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.gameStoreDataStore by preferencesDataStore(name = "game_store_prefs")

data class GameAccessState(val access: GameAccess, val playsLeft: Int)

class GameStorePreferencesDataSource internal constructor(
    private val store: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.gameStoreDataStore)

    val spendablePoints: Flow<Int> = store.data.map { it[SpendableKey] ?: 0 }
    val lifetimePoints: Flow<Int> = store.data.map { it[LifetimeKey] ?: 0 }

    val dailyEarned: Flow<Map<LocalDate, Int>> = store.data.map { prefs ->
        decodeLedger(prefs[LedgerKey].orEmpty())
    }

    val accessByGame: Flow<Map<String, GameAccessState>> = store.data.map { prefs ->
        val stored = decodeAccess(prefs[AccessKey].orEmpty())
        GameStoreCatalog.games.associate { game ->
            game.id to (
                stored[game.id]
                    ?: GameAccessState(if (game.defaultOwned) GameAccess.OWNED else GameAccess.LOCKED, 0)
                )
        }
    }

    suspend fun addEarned(points: Int) {
        val today = LocalDate.now().toString()
        store.edit { prefs ->
            prefs[SpendableKey] = (prefs[SpendableKey] ?: 0) + points
            prefs[LifetimeKey] = (prefs[LifetimeKey] ?: 0) + points
            val ledger = decodeLedger(prefs[LedgerKey].orEmpty()).toMutableMap()
            val key = LocalDate.parse(today)
            ledger[key] = (ledger[key] ?: 0) + points
            prefs[LedgerKey] = encodeLedger(ledger)
        }
    }

    suspend fun recordGlobalWinStreak(won: Boolean, pointsPerTwoWinStreak: Int): Int {
        var awarded = 0
        store.edit { prefs ->
            if (!won) {
                prefs[GlobalWinStreakKey] = 0
                return@edit
            }

            val nextStreak = (prefs[GlobalWinStreakKey] ?: 0) + 1
            if (nextStreak >= 2) {
                prefs[GlobalWinStreakKey] = 0
                prefs[SpendableKey] = (prefs[SpendableKey] ?: 0) + pointsPerTwoWinStreak
                prefs[LifetimeKey] = (prefs[LifetimeKey] ?: 0) + pointsPerTwoWinStreak

                val ledger = decodeLedger(prefs[LedgerKey].orEmpty()).toMutableMap()
                val today = LocalDate.now()
                ledger[today] = (ledger[today] ?: 0) + pointsPerTwoWinStreak
                prefs[LedgerKey] = encodeLedger(ledger)

                awarded = pointsPerTwoWinStreak
            } else {
                prefs[GlobalWinStreakKey] = nextStreak
            }
        }
        return awarded
    }

    suspend fun tryAwardWeekly(key: String, points: Int): Boolean {
        if (points <= 0) return false
        var awarded = false
        store.edit { prefs ->
            val map = decodeWeekly(prefs[WeeklyKey].orEmpty()).toMutableMap()
            val today = LocalDate.now().toEpochDay()
            val last = map[key]
            if (last == null || today - last >= 7L) {
                prefs[SpendableKey] = (prefs[SpendableKey] ?: 0) + points
                prefs[LifetimeKey] = (prefs[LifetimeKey] ?: 0) + points
                val ledger = decodeLedger(prefs[LedgerKey].orEmpty()).toMutableMap()
                val lkey = LocalDate.now()
                ledger[lkey] = (ledger[lkey] ?: 0) + points
                prefs[LedgerKey] = encodeLedger(ledger)
                map[key] = today
                prefs[WeeklyKey] = encodeWeekly(map)
                awarded = true
            }
        }
        return awarded
    }

    /**
     * Records one play exactly once for a stable score session.
     *
     * A game result can survive process death and be replayed into this store,
     * so the receipt, the win-streak award and any rental consumption all happen
     * inside a single edit. That makes it impossible to award points without
     * persisting the receipt, or vice versa.
     *
     * @return true when this call applied the play, false when the session was
     * already recorded or the game is unknown.
     */
    suspend fun recordPlayOnce(
        gameId: String,
        sessionId: Long,
        won: Boolean,
        pointsPerTwoWinStreak: Int,
    ): Boolean {
        require(sessionId > 0L) { "sessionId must be positive" }
        require(pointsPerTwoWinStreak >= 0) { "pointsPerTwoWinStreak must not be negative" }

        val game = GameStoreCatalog.byId(gameId) ?: return false
        val token = normalizePlayReceiptToken("$gameId:$sessionId") ?: return false

        var applied = false

        store.edit { prefs ->
            val receipts = decodePlayReceipts(prefs[PlayReceiptKey].orEmpty())

            if (token in receipts) return@edit

            if (!won) {
                prefs[GlobalWinStreakKey] = 0
            } else {
                val nextStreak = (prefs[GlobalWinStreakKey] ?: 0) + 1

                if (nextStreak >= 2) {
                    prefs[GlobalWinStreakKey] = 0
                    prefs[SpendableKey] = (prefs[SpendableKey] ?: 0) + pointsPerTwoWinStreak
                    prefs[LifetimeKey] = (prefs[LifetimeKey] ?: 0) + pointsPerTwoWinStreak

                    val ledger = decodeLedger(prefs[LedgerKey].orEmpty()).toMutableMap()
                    val today = LocalDate.now()
                    ledger[today] = (ledger[today] ?: 0) + pointsPerTwoWinStreak
                    prefs[LedgerKey] = encodeLedger(ledger)
                } else {
                    prefs[GlobalWinStreakKey] = nextStreak
                }
            }

            val accessMap = decodeAccess(prefs[AccessKey].orEmpty()).toMutableMap()
            val current = accessMap[gameId]
                ?: GameAccessState(
                    if (game.defaultOwned) GameAccess.OWNED else GameAccess.LOCKED,
                    0,
                )

            if (current.access == GameAccess.RENTED) {
                val remaining = (current.playsLeft - 1).coerceAtLeast(0)
                accessMap[gameId] = if (remaining <= 0) {
                    GameAccessState(GameAccess.LOCKED, 0)
                } else {
                    GameAccessState(GameAccess.RENTED, remaining)
                }
                prefs[AccessKey] = encodeAccess(accessMap)
            }

            prefs[PlayReceiptKey] = encodePlayReceipts(
                (receipts + token).takeLast(MaximumPlayReceiptCount),
            )

            applied = true
        }

        return applied
    }

    /**
     * Whether this session's play receipt already exists.
     *
     * Read-only: it awards nothing, consumes nothing and creates no receipt. It
     * exists so a `false` from [recordPlayOnce] can be disambiguated between
     * "already recorded" and "not recorded".
     */
    suspend fun isPlayRecorded(
        gameId: String,
        sessionId: Long,
    ): Boolean {
        require(sessionId > 0L) { "sessionId must be positive" }

        if (GameStoreCatalog.byId(gameId) == null) return false

        val token = normalizePlayReceiptToken("$gameId:$sessionId") ?: return false
        val preferences = store.data.first()

        return token in decodePlayReceipts(preferences[PlayReceiptKey].orEmpty())
    }

    suspend fun trySpend(points: Int): Boolean {
        var ok = false
        store.edit { prefs ->
            val balance = prefs[SpendableKey] ?: 0
            if (balance >= points) {
                prefs[SpendableKey] = balance - points
                ok = true
            }
        }
        return ok
    }

    suspend fun setAccess(gameId: String, state: GameAccessState) {
        store.edit { prefs ->
            val map = decodeAccess(prefs[AccessKey].orEmpty()).toMutableMap()
            map[gameId] = state
            prefs[AccessKey] = encodeAccess(map)
        }
    }

    suspend fun accessFor(gameId: String): GameAccessState =
        accessByGame.first()[gameId] ?: GameAccessState(GameAccess.LOCKED, 0)

    private fun encodeLedger(m: Map<LocalDate, Int>): String =
        m.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeLedger(s: String): Map<LocalDate, Int> =
        if (s.isBlank()) {
            emptyMap()
        } else {
            s.split(";").mapNotNull {
                val p = it.split("=")
                if (p.size == 2) runCatching { LocalDate.parse(p[0]) to p[1].toInt() }.getOrNull() else null
            }.toMap()
        }

    private fun encodeWeekly(m: Map<String, Long>): String =
        m.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeWeekly(s: String): Map<String, Long> =
        if (s.isBlank()) {
            emptyMap()
        } else {
            s.split(";").mapNotNull {
                val p = it.split("=")
                if (p.size == 2) runCatching { p[0] to p[1].toLong() }.getOrNull() else null
            }.toMap()
        }

    private fun encodeAccess(m: Map<String, GameAccessState>): String =
        m.entries.joinToString(";") { "${it.key}=${it.value.access.name}:${it.value.playsLeft}" }

    private fun decodeAccess(s: String): Map<String, GameAccessState> =
        if (s.isBlank()) {
            emptyMap()
        } else {
            s.split(";").mapNotNull {
                val p = it.split("=")
                if (p.size != 2) return@mapNotNull null
                val parts = p[1].split(":")
                if (parts.size != 2) return@mapNotNull null
                runCatching {
                    p[0] to GameAccessState(GameAccess.valueOf(parts[0]), parts[1].toInt())
                }.getOrNull()
            }.toMap()
        }

    /** Rejects blank or oversized tokens rather than persisting junk. */
    private fun normalizePlayReceiptToken(token: String): String? {
        val trimmed = token.trim()

        if (trimmed.isBlank()) return null
        if (trimmed.length > MaximumPlayReceiptTokenLength) return null
        if (trimmed.contains(ReceiptSeparator)) return null

        return trimmed
    }

    private fun decodePlayReceipts(stored: String): List<String> =
        if (stored.isBlank()) {
            emptyList()
        } else {
            stored.split(ReceiptSeparator)
                .mapNotNull { normalizePlayReceiptToken(it) }
                .distinct()
        }

    private fun encodePlayReceipts(receipts: List<String>): String =
        receipts.distinct().joinToString(ReceiptSeparator)

    private companion object {
        /** Not present in any game or session identifier. */
        const val ReceiptSeparator = ""
        const val MaximumPlayReceiptCount = 200
        const val MaximumPlayReceiptTokenLength = 160

        val SpendableKey = intPreferencesKey("spendable_points")
        val LifetimeKey = intPreferencesKey("lifetime_points")
        val LedgerKey = stringPreferencesKey("daily_earned_ledger")
        val AccessKey = stringPreferencesKey("game_access")
        val WeeklyKey = stringPreferencesKey("weekly_award_days")
        val GlobalWinStreakKey = intPreferencesKey("global_game_win_streak")
        val PlayReceiptKey = stringPreferencesKey("game_play_receipts")
    }
}
