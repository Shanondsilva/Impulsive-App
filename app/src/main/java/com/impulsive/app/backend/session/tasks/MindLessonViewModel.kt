package com.impulsive.app.backend.session.tasks

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.MindLessonRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.tasks.LessonCard
import com.impulsive.app.backend.domain.model.tasks.MindLesson
import com.impulsive.app.backend.domain.model.tasks.nextLessonFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val CardDwellMillis = 5_000L
private const val MaxPuzzleWrongTaps = 3

enum class MindLessonPhase {
    Cards,
    Question,
    Success,
}

data class MindLessonUiState(
    val lesson: MindLesson? = null,
    val completedLessonIds: Set<String> = emptySet(),
    val cardIndex: Int = 0,
    val cardDwellMillis: List<Long> = emptyList(),
    val cardSolved: List<Boolean> = emptyList(),
    val puzzleFoundIndices: List<Set<Int>> = emptyList(),
    val puzzleWrongTaps: List<Int> = emptyList(),
    val totalForegroundMillis: Long = 0L,
    val phase: MindLessonPhase = MindLessonPhase.Cards,
    val selectedOptionIndex: Int? = null,
    val answeredCorrectly: Boolean = false,
    val wrongAnswerAttempts: Int = 0,
    val hintedWrongOptionIndex: Int? = null,
    val feedbackMessage: String? = null,
    val markedCompleted: Boolean = false,
) {
    val currentCard: LessonCard?
        get() = lesson?.cards?.getOrNull(cardIndex)

    val currentCardDwellMillis: Long
        get() = cardDwellMillis.getOrNull(cardIndex) ?: 0L

    val currentCardSolved: Boolean
        get() = cardSolved.getOrNull(cardIndex) == true

    val currentPuzzleFoundIndices: Set<Int>
        get() = puzzleFoundIndices.getOrNull(cardIndex).orEmpty()

    val currentPuzzleWrongTaps: Int
        get() = puzzleWrongTaps.getOrNull(cardIndex) ?: 0

    val currentPuzzleRemainingTaps: Int
        get() = (MaxPuzzleWrongTaps - currentPuzzleWrongTaps).coerceAtLeast(0)

    val currentPuzzleLocked: Boolean
        get() = (currentCard is LessonCard.SpotTheDifference || currentCard is LessonCard.FindTarget)
            && currentPuzzleWrongTaps >= MaxPuzzleWrongTaps && !currentCardSolved

    val currentCardCanAdvance: Boolean
        get() = when (currentCard) {
            is LessonCard.Text -> currentCardDwellMillis >= CardDwellMillis
            is LessonCard.SpotTheDifference, is LessonCard.FindTarget -> currentCardSolved
            null -> false
        }

    val currentCardProgress: Float
        get() = when (val card = currentCard) {
            is LessonCard.Text -> (currentCardDwellMillis / CardDwellMillis.toFloat()).coerceIn(0f, 1f)
            is LessonCard.SpotTheDifference -> {
                val total = card.diffHotspots.size.coerceAtLeast(1)
                (currentPuzzleFoundIndices.size / total.toFloat()).coerceIn(0f, 1f)
            }
            is LessonCard.FindTarget -> if (currentCardSolved) 1f else 0f
            null -> 0f
        }

    val allCardsCompleted: Boolean
        get() = lesson != null && cardSolved.size == lesson.cards.size && cardSolved.all { it }

    val canShowQuestion: Boolean
        get() = phase == MindLessonPhase.Question && allCardsCompleted

    val validCompletion: Boolean
        get() = phase == MindLessonPhase.Success && answeredCorrectly && allCardsCompleted

    val secondsSpent: Int
        get() = (totalForegroundMillis / 1_000L).toInt()
}

class MindLessonViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MindLessonRepository(application)
    private val scoreRepository = ScoreRepository(application)

    private val _uiState = MutableStateFlow(MindLessonUiState())
    val uiState: StateFlow<MindLessonUiState> = _uiState

    private var resumed = false
    private var lastFrameMs: Long? = null
    private var sessionStartedAt: LocalDateTime = LocalDateTime.now()

    init {
        viewModelScope.launch {
            repository.completedLessonIds.collect { completedIds ->
                val current = _uiState.value.lesson
                if (current == null) {
                    selectLesson(completedIds)
                } else {
                    _uiState.update { it.copy(completedLessonIds = completedIds) }
                }
            }
        }
    }

    fun resume() {
        resumed = true
        lastFrameMs = null
    }

    fun pause() {
        resumed = false
        lastFrameMs = null
    }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFrameMs
        lastFrameMs = now

        val current = _uiState.value
        if (!resumed || current.phase == MindLessonPhase.Success || current.lesson == null) return
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 100L)
        if (delta <= 0L) return

        _uiState.update { state ->
            val updatedDwell = if (state.phase == MindLessonPhase.Cards && state.currentCard is LessonCard.Text) {
                state.cardDwellMillis.mapIndexed { index, value ->
                    if (index == state.cardIndex) {
                        (value + delta).coerceAtMost(CardDwellMillis)
                    } else {
                        value
                    }
                }
            } else {
                state.cardDwellMillis
            }
            state.copy(
                cardDwellMillis = updatedDwell,
                totalForegroundMillis = state.totalForegroundMillis + delta,
            )
        }
    }

    fun next() {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        if (state.phase != MindLessonPhase.Cards || !state.currentCardCanAdvance) return
        if (state.cardIndex < lesson.cards.lastIndex) {
            _uiState.update { it.copy(cardIndex = it.cardIndex + 1) }
        } else {
            _uiState.update { it.copy(phase = MindLessonPhase.Question) }
        }
    }

    fun previous() {
        val state = _uiState.value
        if (state.phase == MindLessonPhase.Cards && state.cardIndex > 0) {
            _uiState.update { it.copy(cardIndex = it.cardIndex - 1) }
        }
    }

    fun onPuzzleTap(hotspotIndex: Int) {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        val card = state.currentCard ?: return
        if (state.phase != MindLessonPhase.Cards || state.cardIndex !in lesson.cards.indices || state.currentPuzzleLocked) return
        when (card) {
            is LessonCard.Text -> Unit
            is LessonCard.SpotTheDifference -> {
                val alreadyFound = state.currentPuzzleFoundIndices
                if (hotspotIndex !in card.diffHotspots.indices || hotspotIndex in alreadyFound) return
                val newFound = alreadyFound + hotspotIndex
                val solved = newFound.size >= card.diffHotspots.size
                _uiState.update { it.updatePuzzleProgress(newFound, solved) }
            }
            is LessonCard.FindTarget -> {
                if (hotspotIndex != 0) return
                _uiState.update { it.updatePuzzleProgress(setOf(0), true) }
            }
        }
    }


    fun onPuzzleMiss() {
        val state = _uiState.value
        if (state.phase != MindLessonPhase.Cards || state.currentPuzzleLocked) return
        val card = state.currentCard
        if (card !is LessonCard.SpotTheDifference && card !is LessonCard.FindTarget) return

        _uiState.update { current ->
            val updatedWrongTaps = current.puzzleWrongTaps.toMutableList()
            if (current.cardIndex in updatedWrongTaps.indices) {
                val newCount = (updatedWrongTaps[current.cardIndex] + 1).coerceAtMost(MaxPuzzleWrongTaps)
                updatedWrongTaps[current.cardIndex] = newCount
                current.copy(
                    puzzleWrongTaps = updatedWrongTaps,
                    feedbackMessage = if (newCount >= MaxPuzzleWrongTaps) {
                        "Too many guesses. Reset this card and try slowly."
                    } else {
                        "Not there. Slow down and look again."
                    },
                )
            } else {
                current
            }
        }
    }

    fun resetCurrentCard() {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        if (state.phase != MindLessonPhase.Cards || state.cardIndex !in lesson.cards.indices) return
        _uiState.update { current ->
            val updatedDwell = current.cardDwellMillis.toMutableList()
            val updatedSolved = current.cardSolved.toMutableList()
            val updatedFound = current.puzzleFoundIndices.toMutableList()
            val updatedWrongTaps = current.puzzleWrongTaps.toMutableList()
            updatedDwell[current.cardIndex] = 0L
            updatedSolved[current.cardIndex] = false
            updatedFound[current.cardIndex] = emptySet()
            updatedWrongTaps[current.cardIndex] = 0
            current.copy(
                cardDwellMillis = updatedDwell,
                cardSolved = updatedSolved,
                puzzleFoundIndices = updatedFound,
                puzzleWrongTaps = updatedWrongTaps,
                feedbackMessage = null,
            )
        }
        resume()
    }

    fun selectAnswer(index: Int) {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        val question = lesson.checkQuestion
        if (state.phase != MindLessonPhase.Question || index !in question.options.indices) return
        if (index == question.correctAnswerIndex) {
            val now = LocalDateTime.now()
            _uiState.update {
                it.copy(
                    selectedOptionIndex = index,
                    answeredCorrectly = true,
                    phase = MindLessonPhase.Success,
                    feedbackMessage = "Correct.",
                    hintedWrongOptionIndex = null,
                )
            }
            markCompletedIfNeeded(lesson.id)
            recordScoreSession(
                outcome = ScoreSessionOutcome.Completed,
                validCompletion = true,
                completedAt = now,
                durationSec = state.secondsSpent,
            )
        } else {
            val attempts = state.wrongAnswerAttempts + 1
            val hintIndex = if (attempts >= 2) {
                question.options.indices.firstOrNull { it != question.correctAnswerIndex }
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    selectedOptionIndex = index,
                    wrongAnswerAttempts = attempts,
                    hintedWrongOptionIndex = hintIndex,
                    feedbackMessage = "Not quite - let's look again.",
                )
            }
            restartLessonAttempt()
        }
    }

    private fun selectLesson(completedIds: Set<String>) {
        val lesson = nextLessonFor(completedIds, repository.lessons)
        sessionStartedAt = LocalDateTime.now()
        _uiState.value = MindLessonUiState(
            lesson = lesson,
            completedLessonIds = completedIds,
            cardDwellMillis = List(lesson.cards.size) { 0L },
            cardSolved = List(lesson.cards.size) { false },
            puzzleFoundIndices = List(lesson.cards.size) { emptySet() },
            puzzleWrongTaps = List(lesson.cards.size) { 0 },
        )
    }

    private fun restartLessonAttempt() {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        _uiState.update {
            it.copy(
                phase = MindLessonPhase.Cards,
                cardIndex = 0,
                cardDwellMillis = List(lesson.cards.size) { 0L },
                cardSolved = List(lesson.cards.size) { false },
                puzzleFoundIndices = List(lesson.cards.size) { emptySet() },
                puzzleWrongTaps = List(lesson.cards.size) { 0 },
            )
        }
        resume()
    }

    private fun MindLessonUiState.updatePuzzleProgress(
        foundIndices: Set<Int>,
        solved: Boolean,
    ): MindLessonUiState {
        val updatedSolved = cardSolved.toMutableList()
        val updatedFound = puzzleFoundIndices.toMutableList()
        val updatedWrongTaps = puzzleWrongTaps.toMutableList()
        if (cardIndex in updatedSolved.indices) {
            updatedSolved[cardIndex] = solved
            updatedFound[cardIndex] = foundIndices
            if (solved && cardIndex in updatedWrongTaps.indices) {
                updatedWrongTaps[cardIndex] = 0
            }
        }
        return copy(
            cardSolved = updatedSolved,
            puzzleFoundIndices = updatedFound,
            puzzleWrongTaps = updatedWrongTaps,
            feedbackMessage = if (solved) "Good. You found it." else feedbackMessage,
        )
    }

    private fun markCompletedIfNeeded(lessonId: String) {
        if (_uiState.value.markedCompleted) return
        _uiState.update { it.copy(markedCompleted = true) }
        viewModelScope.launch {
            repository.markCompleted(lessonId)
        }
    }

    private fun recordScoreSession(
        outcome: ScoreSessionOutcome,
        validCompletion: Boolean,
        completedAt: LocalDateTime,
        durationSec: Int,
    ) {
        val score = if (validCompletion) (durationSec.coerceAtLeast(30) * 4) else 0
        viewModelScope.launch {
            scoreRepository.recordSession(
                ScoreSessionRecord(
                    gameType = ScoreGameType.MindLesson,
                    score = score,
                    startedAt = sessionStartedAt,
                    completedAt = completedAt,
                    durationSec = durationSec.coerceAtLeast(0),
                    outcome = outcome,
                    validCompletion = validCompletion,
                ),
            )
        }
    }
}
