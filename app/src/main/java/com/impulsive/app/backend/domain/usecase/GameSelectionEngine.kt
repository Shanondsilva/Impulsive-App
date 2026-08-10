package com.impulsive.app.backend.domain.usecase

import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import java.time.Duration
import kotlin.random.Random

/**
 * Picks which pivot game to launch when the user starts a control task from the
 * block screen. Pure and side effect free: the caller passes in the data and the
 * chosen game comes back. Persistence of the served history and the navigation
 * itself live in the caller, not here.
 *
 * Selection rules, applied in order:
 *  1. Unplayed first. Any candidate the user has never played is served before
 *     anything repeats. If several are unplayed, one is chosen at random.
 *  2. Once every candidate has been played, weighted random favouring the games
 *     that work for this user. A play counts as a success when it ended in
 *     WalkedAway or Completed and no protected app was reopened within
 *     [successWindow] afterwards.
 *  3. Never serve the same game more than [maxRunLength] times in a row.
 */
object GameSelectionEngine {

    /** The only games eligible for the block-flow draw. */
    val candidates: List<ScoreGameType> = listOf(
        ScoreGameType.Snake,
        ScoreGameType.BlockCascade,
        ScoreGameType.SkylineReset,
        ScoreGameType.RhythmTiles,
    )

    private const val DefaultMaxRunLength = 3

    /**
     * @param sessions all recorded game sessions, newest or oldest order does not matter.
     * @param urgeEvents all recorded urge events, used to detect a return to the loop.
     * @param recentlyServed the games served previously, oldest first, used for the streak guard.
     * @param successWindow how long after a session a protected-app reopen still counts as a return.
     * @param random injectable for tests.
     */
    fun selectNextGame(
        sessions: List<ScoreSessionRecord>,
        urgeEvents: List<UrgeEventRecord>,
        recentlyServed: List<ScoreGameType>,
        eligibleGames: List<ScoreGameType> = candidates,
        excludedGames: Set<ScoreGameType> = emptySet(),
        successWindow: Duration = Duration.ofMinutes(10),
        maxRunLength: Int = DefaultMaxRunLength,
        random: Random = Random.Default,
    ): ScoreGameType {
        val pool = eligibleGames
            .distinct()
            .filter { it in candidates && it !in excludedGames }
            .ifEmpty {
                eligibleGames.distinct().filter { it in candidates }
            }
        require(pool.isNotEmpty()) { "At least one recovery game must be eligible." }

        if (pool.size == 1) return pool.single()

        val boundedSessions = sessions
            .asSequence()
            .filter { it.gameType in pool }
            .sortedByDescending { it.completedAt }
            .take(MaxEvidenceSessions)
            .toList()

        // Streak guard: if the tail of recentlyServed is the same game repeated
        // maxRunLength times, drop it from this draw so the streak breaks.
        val repeatedSuccessfulGame: ScoreGameType? = run {
            if (maxRunLength <= 0 || recentlyServed.size < maxRunLength) return@run null
            val tail = recentlyServed.takeLast(maxRunLength)
            val first = tail.first()
            val latestOutcome = boundedSessions.firstOrNull { it.gameType == first }
            if (
                tail.all { it == first } &&
                latestOutcome?.isFavourable(urgeEvents, successWindow) == true
            ) first else null
        }

        val latestFinalised = boundedSessions.firstOrNull()
        val rotateAfterPoorOutcome = latestFinalised
            ?.takeIf { !it.isFavourable(urgeEvents, successWindow) }
            ?.gameType
        val blocked = setOfNotNull(repeatedSuccessfulGame, rotateAfterPoorOutcome)
        val allowed = pool.filter { it !in blocked }.ifEmpty { pool }

        // Unplayed first.
        val playedTypes = boundedSessions.map { it.gameType }.toSet()
        val unplayed = allowed.filter { it !in playedTypes }
        if (unplayed.isNotEmpty()) {
            return unplayed[random.nextInt(unplayed.size)]
        }

        // Weighted random by smoothed success rate.
        val weights = allowed.map { game ->
            val plays = boundedSessions.filter { it.gameType == game }
            val successes = plays.count { it.isFavourable(urgeEvents, successWindow) }
            // Laplace smoothing keeps early picks near even and lets confidence
            // grow with evidence. The floor of 1.0 means even a game that never
            // works still appears sometimes, both for variety and to retest it.
            val smoothedRate = (successes + 1.0) / (plays.size + 2.0)
            game to (1.0 + smoothedRate * 3.0)
        }
        return weightedPick(weights, random)
    }

    private fun ScoreSessionRecord.isFavourable(
        urgeEvents: List<UrgeEventRecord>,
        window: Duration,
    ): Boolean {
        if (!validCompletion) return false
        val workedInGame = outcome == ScoreSessionOutcome.WalkedAway ||
            outcome == ScoreSessionOutcome.Completed
        if (!workedInGame) return false
        if (urgeBefore != null && urgeAfter != null && urgeAfter >= urgeBefore) return false
        val windowEnd = completedAt.plus(window)
        val returnedToLoop = urgeEvents.any { event ->
            event.source == "app" &&
                event.at != null &&
                event.at.isAfter(completedAt) &&
                event.at.isBefore(windowEnd)
        }
        return !returnedToLoop
    }

    private const val MaxEvidenceSessions = 100

    private fun weightedPick(
        weights: List<Pair<ScoreGameType, Double>>,
        random: Random,
    ): ScoreGameType {
        val total = weights.sumOf { it.second }
        if (total <= 0.0) return weights[random.nextInt(weights.size)].first
        var roll = random.nextDouble(total)
        for ((game, weight) in weights) {
            roll -= weight
            if (roll < 0.0) return game
        }
        return weights.last().first
    }
}
