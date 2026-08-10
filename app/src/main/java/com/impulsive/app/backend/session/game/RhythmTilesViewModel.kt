package com.impulsive.app.backend.session.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.RhythmTilesHistoryDataSource
import com.impulsive.app.backend.data.repository.GameStoreManager
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.game.GameHistory
import com.impulsive.app.backend.domain.game.GameView
import com.impulsive.app.backend.domain.game.RhythmTile
import com.impulsive.app.backend.domain.game.RhythmTilesConfig
import com.impulsive.app.backend.domain.game.RhythmTilesResult
import com.impulsive.app.backend.domain.game.RhythmSong
import com.impulsive.app.backend.domain.game.RhythmTilesCatalog
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.newScoreSessionId
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class RhythmTilesUiState(
    val view: GameView = GameView.Ready,
    val countdown: Int = 3,
    val timeLeft: Int = RhythmTilesConfig.ROUND_SECONDS,
    val score: Int = 0,
    val combo: Int = 0,
    val lives: Int = RhythmTilesConfig.MAX_MISSES,
    val tiles: List<RhythmTile> = emptyList(),
    val selectedSong: RhythmSong = RhythmTilesCatalog.songs.first(),
    val result: RhythmTilesResult? = null,
    val walkScore: Int = 0,
    val shake: Boolean = false,
    val history: GameHistory = GameHistory(),
)

class RhythmTilesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val supportCycleRuntime = RecoveryGameSupportCycleRuntime(application)
    private val resultStateStore = RecoveryGameResultStateStore(savedStateHandle)
    private val resultActionCoordinator = RecoveryGameResultActionCoordinator(
        runtime = supportCycleRuntime,
        clearResultState = { resultStateStore.clear() },
    )
    private val dataSource = RhythmTilesHistoryDataSource(application)
    private val scoreRepository = ScoreRepository(application)
    private val pivotGameSessionCommitCoordinator =
        PivotGameSessionCommitCoordinator(
            scoreRepository =
                scoreRepository,
            immediateSafeExitRecorder =
                PivotGameSafeExitRecorder(
                    SafeExitRecordingCoordinator(
                        application,
                    ),
                ),
            reconciliationScheduler =
                WorkManagerPivotGameSafeExitReconciliationScheduler(
                    application,
                ),
        )
    private val gameStoreManager = GameStoreManager(application)
    private val _uiState = MutableStateFlow(RhythmTilesUiState())
    val uiState: StateFlow<RhythmTilesUiState> = _uiState

    private var score = 0
    private var combo = 0
    private var maxCombo = 0
    private var hits = 0
    private var misses = 0
    private var noMiss = true
    private var loopsCompleted = 0
    private var gameOver = false
    private var startMs = 0L
    private var nextSpawnMs = 0L
    private var noteIndex = 0
    private var lastLane = -1
    private var speedMultiplier = 1f
    private var urgeSpeedFactor = 1f
    private var tileIdCounter = 0L
    private var tiles = emptyList<RhythmTile>()
    private var activeSessionId: Long = newScoreSessionId()
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()
    private var urgeBeforeRating: Int? = null
    private var urgeAfterRating: Int? = null
    private var lastRecordedSession: ScoreSessionRecord? = null
    private var roundDurationMillis = RhythmTilesConfig.ROUND_SECONDS * 1_000L
    private var lastSupportOutcome: SupportCycleGameTerminalOutcome? = null
    private var lastSupportElapsedMillis: Long = 0L
    private var activeLaunchContext: RecoveryGameLaunchContext = RecoveryGameLaunchContext.Standalone

    suspend fun configureLaunchContext(launchContext: RecoveryGameLaunchContext): Boolean {
        val binding = supportCycleRuntime.bindWithRecovery(
            requested = launchContext,
            standaloneDurationMillis = RhythmTilesConfig.ROUND_SECONDS * 1_000L,
        ) ?: return false

        roundDurationMillis = binding.durationMillis
        activeLaunchContext = launchContext

        val snapshot = resultStateStore.restore(
            launchContext = launchContext,
            expectedGameType = ScoreGameType.RhythmTiles,
        )

        val authoritativeResult = binding.resolvedStep

        /*
         * The repository says the support-cycle game step is already terminal.
         * Restore the matching result presentation from SavedStateHandle.
         */
        if (authoritativeResult != null) {
            val snapshotOutcome = snapshot?.supportOutcomeOrNull()

            if (
                snapshot == null ||
                snapshotOutcome != authoritativeResult.outcome ||
                !restoreResultSnapshot(snapshot)
            ) {
                /*
                 * The authoritative step cannot remain stranded without a trusted
                 * result presentation. End the existing cycle through its recorded
                 * outcome, clear the invalid presentation state, and use the
                 * existing screen-exit path.
                 */
                resultStateStore.clear()

                supportCycleRuntime.resolveAndEnd(
                    outcome = authoritativeResult.outcome,
                    elapsedDurationMillis = authoritativeResult.elapsedDurationMillis,
                )

                return false
            }

            /*
             * Repository state remains authoritative if the snapshot contains an
             * older elapsed value.
             */
            lastSupportOutcome = authoritativeResult.outcome
            lastSupportElapsedMillis = authoritativeResult.elapsedDurationMillis

            return true
        }

        /*
         * This covers process death after the result snapshot was written but
         * before the asynchronous terminal-step persistence completed.
         */
        if (snapshot != null) {
            val snapshotOutcome = snapshot.supportOutcomeOrNull()

            if (snapshotOutcome == null || !restoreResultSnapshot(snapshot)) {
                /*
                 * The authoritative step is still InProgress. A corrupt
                 * presentation snapshot must not terminalise it.
                 */
                resultStateStore.clear()

                if (_uiState.value.view == GameView.Ready) {
                    _uiState.update { it.copy(timeLeft = roundDurationSeconds()) }
                }

                return true
            }

            lastSupportOutcome = snapshotOutcome
            lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis.coerceAtLeast(0L)

            val resolution = supportCycleRuntime.resolveForContinuation(
                outcome = snapshotOutcome,
                elapsedDurationMillis = lastSupportElapsedMillis,
            )

            /*
             * A restored result must remain visible whenever the retry keeps
             * it valid: continuation succeeded, the outcome was already
             * persisted as terminal/NotFound, or the failure is explicitly
             * retryable. Only a non-retryable failure (e.g. OutcomeConflict)
             * discards the snapshot.
             */
            if (!resolution.keepsRestoredResultVisible) {
                resultStateStore.clear()

                return false
            }

            return true
        }

        if (_uiState.value.view == GameView.Ready) {
            _uiState.update { it.copy(timeLeft = roundDurationSeconds()) }
        }

        return true
    }

    fun abandonSupportCycle() {
        val elapsed = if (startMs > 0L) SystemClock.uptimeMillis() - startMs else 0L
        viewModelScope.launch {
            resultActionCoordinator.abandon(elapsedDurationMillis = elapsed)
        }
    }

    fun continueWithAnotherGame(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        viewModelScope.launch {
            val allowed = resultActionCoordinator.continueWithAnotherGame(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (allowed) {
                onReady()
            }
        }
    }

    fun replayWithRemainingBudget(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        viewModelScope.launch {
            val duration = resultActionCoordinator.prepareReplay(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
                requestedDurationMillis = RhythmTilesConfig.ROUND_SECONDS * 1_000L,
            ) ?: return@launch

            roundDurationMillis = duration
            lastSupportOutcome = null
            lastSupportElapsedMillis = 0L
            onReady()
        }
    }

    fun finishSupportCycleAfterChoice(onReady: () -> Unit) {
        val outcome = lastSupportOutcome ?: SupportCycleGameTerminalOutcome.Abandoned
        val elapsed = if (lastSupportOutcome == null && startMs > 0L) {
            SystemClock.uptimeMillis() - startMs
        } else {
            lastSupportElapsedMillis
        }
        viewModelScope.launch {
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (allowed) {
                onReady()
            }
        }
    }

    private fun roundDurationSeconds(): Int =
        ((roundDurationMillis + 999L) / 1_000L).toInt().coerceAtLeast(1)

    init {
        viewModelScope.launch {
            dataSource.history.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun selectSong(id: String) {
        if (_uiState.value.view != GameView.Ready) return
        val song = RhythmTilesCatalog.byId(id) ?: return
        _uiState.update { it.copy(selectedSong = song) }
    }

    fun startTaskCountdown() {
        if (_uiState.value.view != GameView.Ready) return

        val suitableSongs = RhythmTilesCatalog.songs
            .filter { it.bpm >= 118 }
            .ifEmpty { RhythmTilesCatalog.songs }

        val selectedSong = suitableSongs[Random.nextInt(suitableSongs.size)]

        _uiState.update {
            it.copy(
                selectedSong = selectedSong,
            )
        }

        startCountdown()
    }

    fun startCountdown() {
        resultStateStore.clear()
        _uiState.update {
            it.copy(
                view = GameView.Countdown,
                countdown = 3,
                result = null,
                walkScore = 0,
                tiles = emptyList(),
            )
        }
        viewModelScope.launch {
            for (n in 3 downTo 1) {
                _uiState.update { it.copy(countdown = n) }
                delay(600)
            }
            _uiState.update { it.copy(countdown = 0) }
            delay(320)
            startGame()
        }
    }

    fun startGame() {
        score = 0
        combo = 0
        maxCombo = 0
        hits = 0
        misses = 0
        noMiss = true
        loopsCompleted = 0
        gameOver = false
        noteIndex = 0
        lastLane = -1
        speedMultiplier = RhythmTilesConfig.START_SPEED_MULTIPLIER
        urgeSpeedFactor = rhythmUrgeSpeedFactor(urgeBeforeRating)
        tiles = emptyList()
        activeSessionId = newScoreSessionId()
        sessionStartedAt = LocalDateTime.now()
        urgeAfterRating = null
        startMs = SystemClock.uptimeMillis()
        nextSpawnMs = startMs + 600
        _uiState.update {
            it.copy(
                view = GameView.Playing,
                countdown = 3,
                timeLeft = roundDurationSeconds(),
                score = 0,
                combo = 0,
                lives = RhythmTilesConfig.MAX_MISSES,
                tiles = emptyList(),
                result = null,
                walkScore = 0,
                shake = false,
            )
        }
    }

    fun tick() {
        if (_uiState.value.view != GameView.Playing || gameOver) return

        val now = SystemClock.uptimeMillis()
        val elapsedSec = (now - startMs) / 1000.0
        val roundSeconds = roundDurationMillis / 1_000.0
        if (elapsedSec >= roundSeconds) {
            finishRound(earlyExit = false)
            return
        }

        speedMultiplier = currentRhythmTilesSpeedMultiplier(elapsedSec)

        val timeLeft = max(0, (roundSeconds - elapsedSec).toInt() + 1)

        val song = _uiState.value.selectedSong
        if (now >= nextSpawnMs) {
            spawnTile(now, song)
        }

        val escaped = tiles.filter { now - it.spawnAtMs > it.fallDurationMs }
        if (escaped.isNotEmpty()) {
            combo = 0
            noMiss = false
            misses += escaped.size
            tiles = tiles.filter { tile -> escaped.none { it.id == tile.id } }
            pulseShake()
            if (misses >= RhythmTilesConfig.MAX_MISSES) {
                _uiState.update {
                    it.copy(
                        timeLeft = timeLeft,
                        combo = combo,
                        lives = max(0, RhythmTilesConfig.MAX_MISSES - misses),
                        tiles = tiles,
                    )
                }
                endWithGameOver()
                return
            }
        }

        _uiState.update {
            it.copy(
                timeLeft = timeLeft,
                score = score,
                combo = combo,
                lives = max(0, RhythmTilesConfig.MAX_MISSES - misses),
                tiles = tiles,
            )
        }
    }

    private fun currentRhythmTilesSpeedMultiplier(elapsedSec: Double): Float {
        val progress = (elapsedSec / RhythmTilesConfig.ROUND_SECONDS.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()

        return RhythmTilesConfig.START_SPEED_MULTIPLIER +
            (RhythmTilesConfig.END_SPEED_MULTIPLIER - RhythmTilesConfig.START_SPEED_MULTIPLIER) * progress
    }

    private fun spawnTile(now: Long, song: RhythmSong) {
        if (song.notes.isEmpty()) {
            nextSpawnMs = now + 1_000L
            return
        }

        if (noteIndex >= song.notes.size) {
            nextSpawnMs = now + 1_000L
            return
        }

        val note = song.notes[noteIndex]
        val beatMs = 60_000f / song.bpm
        nextSpawnMs = now + (note.beats * beatMs).toLong().coerceAtLeast(120L)
        noteIndex++

        if (note.isRest) {
            return
        }

        var lane = Random.nextInt(RhythmTilesConfig.LANES)
        if (lane == lastLane) {
            lane = (lane + 1 + Random.nextInt(RhythmTilesConfig.LANES - 1)) % RhythmTilesConfig.LANES
        }

        lastLane = lane

        val fallMs = (RhythmTilesConfig.BASE_FALL_MS / (speedMultiplier * urgeSpeedFactor)).toLong()
        tiles = tiles + RhythmTile(
            id = ++tileIdCounter,
            lane = lane,
            semitone = note.semitone,
            spawnAtMs = now,
            fallDurationMs = fallMs,
        )
    }

    /**
     * Tap on a tile by id. Returns the tile's semitone when the tap counts so
     * the screen can play the note, or null when the tile is already gone.
     */
    fun tapTile(id: Long): Int? {
        if (_uiState.value.view != GameView.Playing || gameOver) return null
        val tile = tiles.firstOrNull { it.id == id } ?: return null
        return registerHit(tile)
    }

    /**
     * Tap anywhere in a lane. Resolves to the lowest (oldest) live tile in
     * that lane so a tap counts even when the finger lands slightly above or
     * below the moving tile. Returns the semitone to play, or null when the
     * lane has no live tile, in which case the screen treats it as an empty tap.
     */
    fun tapLane(lane: Int): Int? {
        if (_uiState.value.view != GameView.Playing || gameOver) return null
        val tile = tiles.filter { it.lane == lane }.minByOrNull { it.spawnAtMs } ?: return null
        return registerHit(tile)
    }

    private fun registerHit(tile: RhythmTile): Int {
        tiles = tiles.filterNot { it.id == tile.id }
        hits++
        combo++
        maxCombo = max(maxCombo, combo)
        score += RhythmTilesConfig.HIT_POINTS + min(combo, 20)
        _uiState.update {
            it.copy(score = score, combo = combo, tiles = tiles)
        }
        return tile.semitone
    }

    fun tapEmpty() {
        if (_uiState.value.view != GameView.Playing || gameOver) return
        combo = 0
        score = max(0, score - RhythmTilesConfig.EMPTY_TAP_PENALTY)
        pulseShake()
        _uiState.update { it.copy(score = score, combo = combo) }
    }

    private fun pulseShake() {
        _uiState.update { it.copy(shake = true) }
        viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(shake = false) }
        }
    }

    private fun endWithGameOver() {
        if (gameOver) return
        gameOver = true
        viewModelScope.launch {
            delay(900)
            finishRound(earlyExit = true)
        }
    }

    private fun finishRound(earlyExit: Boolean) {
        if (_uiState.value.view == GameView.Result || _uiState.value.view == GameView.Walked) return
        if (!earlyExit) {
            score += RhythmTilesConfig.FINISH_BONUS
            if (noMiss && hits > 0) score += RhythmTilesConfig.NO_MISS_BONUS
        }
        val old = _uiState.value.history
        val nextHistory = GameHistory(
            pb = max(old.pb, score),
            prev = score,
            bestReactionMs = old.bestReactionMs,
            bestCombo = max(old.bestCombo, maxCombo),
        )
        val durationSec = min(
            roundDurationSeconds(),
            ((SystemClock.uptimeMillis() - startMs) / 1000L).toInt(),
        )
        val result = RhythmTilesResult(
            score = score,
            previousBest = old.pb,
            previousScore = old.prev,
            maxCombo = maxCombo,
            hits = hits,
            misses = misses,
            loopsCompleted = loopsCompleted,
            gameOver = earlyExit,
            durationSec = durationSec,
            validCompletion = !earlyExit && score > 0 && hits > 0 && durationSec >= 5,
        )
        tiles = emptyList()
        viewModelScope.launch { dataSource.save(nextHistory) }
        _uiState.update {
            it.copy(
                view = GameView.Result,
                score = score,
                combo = combo,
                lives = max(0, RhythmTilesConfig.MAX_MISSES - misses),
                tiles = emptyList(),
                result = result,
                history = nextHistory,
            )
        }
        val outcome = if (result.validCompletion) ScoreSessionOutcome.Completed else ScoreSessionOutcome.Abandoned
        recordScoreSession(outcome = outcome, scoreValue = score, result = result)
        val elapsedMillis = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(0L)
        val supportOutcome = when {
            roundDurationMillis < RhythmTilesConfig.ROUND_SECONDS * 1_000L &&
                elapsedMillis >= roundDurationMillis -> SupportCycleGameTerminalOutcome.TimedOut
            !result.validCompletion -> SupportCycleGameTerminalOutcome.Abandoned
            else -> SupportCycleGameTerminalOutcome.Completed
        }
        lastSupportOutcome = supportOutcome
        lastSupportElapsedMillis = elapsedMillis

        /*
         * Save the stable result presentation synchronously before the asynchronous
         * support-cycle mutation. This closes the process-death race between result
         * rendering and terminal-step persistence.
         */
        saveCurrentResultSnapshot()

        viewModelScope.launch {
            supportCycleRuntime.resolveForContinuation(supportOutcome, elapsedMillis)
        }
    }

    fun walkAway() {
        val result = _uiState.value.result ?: return
        val outcome = lastSupportOutcome ?: return
        val elapsed = lastSupportElapsedMillis
        val oldHistory = _uiState.value.history
        val total = result.score + RhythmTilesConfig.WALK_AWAY_BONUS
        val nextHistory = oldHistory.copy(
            pb = max(oldHistory.pb, total),
            prev = total,
        )

        viewModelScope.launch {
            /*
             * Finish using the authoritative result outcome. Failed,
             * Abandoned, and TimedOut results must not be rewritten as
             * Completed merely because the user chose Walk Away.
             */
            val allowed = resultActionCoordinator.finish(
                outcome = outcome,
                elapsedDurationMillis = elapsed,
            )

            if (!allowed) {
                return@launch
            }

            recordCurrentResult(
                outcome = ScoreSessionOutcome.WalkedAway,
                scoreOverride = total,
            )

            _uiState.update {
                it.copy(
                    view = GameView.Walked,
                    walkScore = total,
                    history = nextHistory,
                    tiles = emptyList(),
                )
            }

            dataSource.save(nextHistory)
        }
    }

    private fun rhythmUrgeSpeedFactor(urge: Int?): Float {
        val rating = (urge ?: 5).coerceIn(0, 10)
        return 1f + (rating / 10f) * 0.35f
    }

    fun setUrgeBefore(rating: Int) {
        urgeBeforeRating = rating.coerceIn(0, 10)
    }

    fun taskRewardCompletionToken(): String =
        "${ScoreGameType.RhythmTiles.id}:$activeSessionId"

    fun setUrgeAfter(rating: Int) {
        val coerced = rating.coerceIn(0, 10)
        urgeAfterRating = coerced
        val recorded = lastRecordedSession ?: return
        val updated = recorded.copy(urgeAfter = coerced)
        lastRecordedSession = updated
        saveCurrentResultSnapshot()
        viewModelScope.launch { scoreRepository.recordSession(updated) }
    }

    fun recordCurrentResult(
        outcome: ScoreSessionOutcome,
        scoreOverride: Int? = null,
    ) {
        val result = _uiState.value.result ?: return
        recordScoreSession(outcome = outcome, scoreValue = scoreOverride ?: result.score, result = result)
    }

    private fun recordScoreSession(
        outcome: ScoreSessionOutcome,
        scoreValue: Int,
        result: RhythmTilesResult,
    ) {
        val record = ScoreSessionRecord(
            id = activeSessionId,
            gameType = ScoreGameType.RhythmTiles,
            score = scoreValue.coerceAtLeast(0),
            startedAt = sessionStartedAt,
            completedAt = LocalDateTime.now(),
            durationSec = result.durationSec.coerceAtLeast(0),
            urgeBefore = urgeBeforeRating,
            urgeAfter = urgeAfterRating,
            outcome = outcome,
            validCompletion =
                result.validCompletion &&
                outcome !=
                ScoreSessionOutcome.Abandoned,
        )
        lastRecordedSession = record
        viewModelScope.launch {
            pivotGameSessionCommitCoordinator
                .commit(
                    record,
                )

            if (
                outcome !=
                ScoreSessionOutcome.WalkedAway
            ) {
                gameStoreManager
                    .recordPlay(
                        gameId =
                            "RHYTHM_TILES",
                        won =
                            !result.gameOver,
                    )
            }
        }
    }

    private fun saveCurrentResultSnapshot() {
        val launch = activeLaunchContext as? RecoveryGameLaunchContext.SupportCycle ?: return
        val outcome = lastSupportOutcome ?: return
        val state = _uiState.value
        val result = state.result ?: return

        resultStateStore.save(
            RecoveryGameResultSnapshot(
                cycleId = launch.cycleId,
                decisionId = launch.decisionId,
                gameTypeId = ScoreGameType.RhythmTiles.id,
                supportOutcomeName = outcome.name,
                supportElapsedDurationMillis = lastSupportElapsedMillis.coerceAtLeast(0L),
                activeSessionId = activeSessionId,
                sessionStartedAtIso = sessionStartedAt.toString(),
                urgeBeforeRating = urgeBeforeRating,
                urgeAfterRating = urgeAfterRating,
                lastRecordedSession = lastRecordedSession?.let { ScoreSessionSnapshot.from(it) },
                payload = RecoveryGameResultPayload.RhythmTiles(
                    selectedSongId = state.selectedSong.id,
                    score = state.score,
                    combo = state.combo,
                    lives = state.lives,
                    historyPersonalBest = state.history.pb,
                    historyPrevious = state.history.prev,
                    historyBestReactionMs = state.history.bestReactionMs,
                    historyBestCombo = state.history.bestCombo,
                    resultScore = result.score,
                    resultPreviousBest = result.previousBest,
                    resultPreviousScore = result.previousScore,
                    resultMaxCombo = result.maxCombo,
                    resultHits = result.hits,
                    resultMisses = result.misses,
                    resultLoopsCompleted = result.loopsCompleted,
                    resultGameOver = result.gameOver,
                    resultDurationSec = result.durationSec,
                    resultValidCompletion = result.validCompletion,
                ),
            ),
        )
    }

    private fun restoreResultSnapshot(snapshot: RecoveryGameResultSnapshot): Boolean {
        val payload = snapshot.payload as? RecoveryGameResultPayload.RhythmTiles ?: return false
        val restoredSong = RhythmTilesCatalog.byId(payload.selectedSongId) ?: return false
        val restoredSessionStart = snapshot.sessionStartedAtOrNull() ?: return false
        val restoredOutcome = snapshot.supportOutcomeOrNull() ?: return false
        val restoredRecordedSession = snapshot.lastRecordedSession?.toRecordOrNull()

        if (snapshot.lastRecordedSession != null && restoredRecordedSession == null) {
            return false
        }

        if (
            snapshot.activeSessionId <= 0L ||
            snapshot.supportElapsedDurationMillis < 0L ||
            payload.resultDurationSec < 0 ||
            payload.lives !in 0..RhythmTilesConfig.MAX_MISSES
        ) {
            return false
        }

        activeSessionId = snapshot.activeSessionId
        sessionStartedAt = restoredSessionStart
        urgeBeforeRating = snapshot.urgeBeforeRating?.coerceIn(0, 10)
        urgeAfterRating = snapshot.urgeAfterRating?.coerceIn(0, 10)
        lastRecordedSession = restoredRecordedSession
        lastSupportOutcome = restoredOutcome
        lastSupportElapsedMillis = snapshot.supportElapsedDurationMillis

        score = payload.score
        combo = payload.combo
        maxCombo = payload.resultMaxCombo
        hits = payload.resultHits
        misses = payload.resultMisses
        loopsCompleted = payload.resultLoopsCompleted
        gameOver = payload.resultGameOver
        noMiss = payload.resultMisses == 0
        tiles = emptyList()

        /*
         * A restored result must never restart the old game clock, spawning, or
         * tile loop.
         */
        startMs = 0L
        nextSpawnMs = 0L
        noteIndex = 0
        lastLane = -1

        _uiState.value = RhythmTilesUiState(
            view = GameView.Result,
            countdown = 0,
            timeLeft = 0,
            score = payload.score,
            combo = payload.combo,
            lives = payload.lives.coerceIn(0, RhythmTilesConfig.MAX_MISSES),
            tiles = emptyList(),
            selectedSong = restoredSong,
            result = RhythmTilesResult(
                score = payload.resultScore,
                previousBest = payload.resultPreviousBest,
                previousScore = payload.resultPreviousScore,
                maxCombo = payload.resultMaxCombo,
                hits = payload.resultHits,
                misses = payload.resultMisses,
                loopsCompleted = payload.resultLoopsCompleted,
                gameOver = payload.resultGameOver,
                durationSec = payload.resultDurationSec,
                validCompletion = payload.resultValidCompletion,
            ),
            walkScore = 0,
            shake = false,
            history = GameHistory(
                pb = payload.historyPersonalBest,
                prev = payload.historyPrevious,
                bestReactionMs = payload.historyBestReactionMs,
                bestCombo = payload.historyBestCombo,
            ),
        )

        return true
    }

    fun playAgain() {
        startCountdown()
    }

    fun reset() {
        resultStateStore.clear()
        tiles = emptyList()
        _uiState.update {
            it.copy(
                view = GameView.Ready,
                countdown = 3,
                timeLeft = roundDurationSeconds(),
                score = 0,
                combo = 0,
                lives = RhythmTilesConfig.MAX_MISSES,
                tiles = emptyList(),
                result = null,
                walkScore = 0,
                shake = false,
            )
        }
    }
}
