package com.impulsive.app.backend.session.tasks

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ResetReadRepository
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticle
import com.impulsive.app.backend.domain.model.tasks.resetReadArticleForDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetReadPhase {
    Reading,
    Question,
    Success,
}

data class ResetReadUiState(
    val article: ResetReadArticle? = null,
    val readArticleIds: Set<String> = emptySet(),
    val foregroundReadMillis: Long = 0L,
    val scrollProgress: Float = 0f,
    val reachedEnd: Boolean = false,
    val phase: ResetReadPhase = ResetReadPhase.Reading,
    val selectedOptionIndex: Int? = null,
    val markedRead: Boolean = false,
) {
    val minimumReadMillis: Long
        get() = (article?.minimumReadSeconds ?: 0) * 1_000L

    val timerProgress: Float
        get() = if (minimumReadMillis <= 0L) 0f else (foregroundReadMillis / minimumReadMillis.toFloat()).coerceIn(0f, 1f)

    val canOpenQuestion: Boolean
        get() = reachedEnd && foregroundReadMillis >= minimumReadMillis

    val validCompletion: Boolean
        get() = phase == ResetReadPhase.Success &&
            selectedOptionIndex != null &&
            canOpenQuestion

    val secondsSpent: Int
        get() = (foregroundReadMillis / 1_000L).toInt()
}

class ResetReadViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ResetReadRepository(application)

    private val _uiState = MutableStateFlow(ResetReadUiState())
    val uiState: StateFlow<ResetReadUiState> = _uiState

    private var resumed = false
    private var lastFrameMs: Long? = null

    init {
        viewModelScope.launch {
            repository.readArticleIds.collect { readIds ->
                val current = _uiState.value.article
                if (current == null) {
                    selectArticle(readIds)
                } else {
                    _uiState.update { it.copy(readArticleIds = readIds) }
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
        if (!resumed || current.phase != ResetReadPhase.Reading || current.article == null) return
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 100L)
        if (delta <= 0L) return

        _uiState.update {
            it.copy(
                foregroundReadMillis = (it.foregroundReadMillis + delta).coerceAtMost(it.minimumReadMillis),
            )
        }
    }

    fun updateScrollProgress(progress: Float) {
        _uiState.update {
            it.copy(
                scrollProgress = progress.coerceIn(0f, 1f),
                reachedEnd = progress >= 0.995f || it.reachedEnd,
            )
        }
    }

    fun markReachedEnd() {
        updateScrollProgress(1f)
    }

    fun openQuestion() {
        val state = _uiState.value
        if (state.canOpenQuestion) {
            _uiState.update { it.copy(phase = ResetReadPhase.Question) }
        }
    }

    fun selectAnswer(index: Int) {
        val state = _uiState.value
        val article = state.article ?: return
        if (state.phase != ResetReadPhase.Question || index !in article.closingQuestion.options.indices) return
        _uiState.update {
            it.copy(
                selectedOptionIndex = index,
                phase = ResetReadPhase.Success,
            )
        }
        markReadIfNeeded(article.id)
    }

    private fun selectArticle(readIds: Set<String>) {
        val epochDay = java.time.LocalDate.now().toEpochDay()
        val article = resetReadArticleForDay(epochDay, repository.articles)
        _uiState.value = ResetReadUiState(
            article = article,
            readArticleIds = readIds,
        )
    }

    private fun markReadIfNeeded(articleId: String) {
        if (_uiState.value.markedRead) return
        _uiState.update { it.copy(markedRead = true) }
        viewModelScope.launch {
            repository.markRead(articleId)
        }
    }
}
