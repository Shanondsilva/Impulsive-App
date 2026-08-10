package com.impulsive.app.backend.session.tasks

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ResetReadRepository
import com.impulsive.app.backend.domain.model.tasks.RESET_READ_MINIMUM_SECONDS
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticle
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticleExposureRecord
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.domain.model.tasks.cooldownExcludedResetReadArticleIds
import com.impulsive.app.backend.domain.model.tasks.fallbackResetReadArticleForDay
import com.impulsive.app.backend.domain.model.tasks.recommendedResetReadArticleForDay
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class ResetReadPhase {
    Reading,
    Question,
    Success,
}

enum class ResetReadLaunchMode {
    Normal,
    Fallback,
}

data class ResetReadUiState(
    val article: ResetReadArticle? = null,
    val launchMode: ResetReadLaunchMode = ResetReadLaunchMode.Normal,
    val readArticleIds: Set<String> = emptySet(),
    val foregroundReadMillis: Long = 0L,
    val scrollProgress: Float = 0f,
    val reachedEnd: Boolean = false,
    val phase: ResetReadPhase = ResetReadPhase.Reading,
    val selectedOptionIndex: Int? = null,
    val markedRead: Boolean = false,
    val recordedSession: Boolean = false,
    val recordedSessionId: Long? = null,
    val completedSessionCount: Int = 0,
    val askHelpfulnessRating: Boolean = false,
    val helpfulnessRating: Int? = null,
    val safeExitRequestStatus:
        ResetReadSafeExitRequestStatus =
        ResetReadSafeExitRequestStatus.Idle,
) {
    val requiredReadSeconds: Int
        get() = article?.minimumReadSeconds ?: RESET_READ_MINIMUM_SECONDS

    val minimumReadMillis: Long
        get() = requiredReadSeconds * 1_000L

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

    private val safeExitRecorder =
        ResetReadSafeExitRecorder(
            SafeExitRecordingCoordinator(
                application,
            ),
        )

    private val safeExitReconciliationScheduler =
        WorkManagerResetReadSafeExitReconciliationScheduler(
            application,
        )

    private val _uiState = MutableStateFlow(ResetReadUiState())
    val uiState: StateFlow<ResetReadUiState> = _uiState

    private var resumed = false
    private var lastFrameMs: Long? = null
    private var recordedShownArticleId: String? = null
    private var launchModeConfigured = false
    private var latestDataLoaded = false
    private var latestReadIds: Set<String> = emptySet()
    private var latestSessions: List<ResetReadSessionRecord> = emptyList()
    private var latestCompletedSession:
        ResetReadSessionRecord? =
        null
    private var latestExposures: List<ResetReadArticleExposureRecord> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                repository.readArticleIds,
                repository.sessions,
                repository.articleExposures,
            ) { readIds, sessions, exposures ->
                Triple(readIds, sessions, exposures)
            }.collect { (readIds, sessions, exposures) ->
                latestDataLoaded = true
                latestReadIds = readIds
                latestSessions = sessions
                latestExposures = exposures

                _uiState.update {
                    it.copy(
                        readArticleIds = readIds,
                        completedSessionCount = sessions.count { session -> session.validCompletion },
                    )
                }

                selectArticleIfReady()
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

    fun configureLaunchMode(launchMode: ResetReadLaunchMode) {
        val current = _uiState.value
        if (
            launchModeConfigured &&
            current.launchMode == launchMode &&
            current.article != null
        ) {
            return
        }

        launchModeConfigured = true
        recordedShownArticleId = null
        latestCompletedSession =
            null

        _uiState.update {
            it.copy(
                launchMode = launchMode,
                article = null,
                foregroundReadMillis = 0L,
                scrollProgress = 0f,
                reachedEnd = false,
                phase = ResetReadPhase.Reading,
                selectedOptionIndex = null,
                markedRead = false,
                recordedSession = false,
                recordedSessionId = null,
                askHelpfulnessRating = false,
                helpfulnessRating = null,
                safeExitRequestStatus =
                    ResetReadSafeExitRequestStatus.Idle,
            )
        }

        selectArticleIfReady()
    }

    private fun selectArticleIfReady() {
        val state = _uiState.value
        if (!launchModeConfigured || !latestDataLoaded || state.article != null) return

        val article = selectArticle(
            readIds = latestReadIds,
            sessions = latestSessions,
            exposures = latestExposures,
            launchMode = state.launchMode,
        )

        recordArticleShownIfNeeded(article)

        _uiState.update {
            it.copy(
                article = article,
                readArticleIds = latestReadIds,
                completedSessionCount = latestSessions.count { session -> session.validCompletion },
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

        recordSessionIfNeeded(article = article, selectedOptionIndex = index)
        markReadIfNeeded(article.id)
    }

    fun recordAbandonedSessionIfNeeded(failureReason: String) {
        val state = _uiState.value
        val article = state.article ?: return
        if (state.recordedSession || state.validCompletion) return

        _uiState.update { it.copy(recordedSession = true) }

        val completedAt = LocalDateTime.now()
        val secondsSpent = state.secondsSpent.coerceAtLeast(0)
        val selectedOptionIndex = state.selectedOptionIndex ?: -1
        val answerText = article.closingQuestion.options.getOrNull(selectedOptionIndex).orEmpty()

        val session = ResetReadSessionRecord(
            id = System.currentTimeMillis(),
            articleId = article.id,
            articleTitle = article.title,
            startedAt = completedAt.minusSeconds(secondsSpent.toLong()),
            completedAt = completedAt,
            selectedDurationSeconds = state.requiredReadSeconds,
            requiredDurationSeconds = state.requiredReadSeconds,
            secondsSpent = secondsSpent,
            selectedOptionIndex = selectedOptionIndex,
            validCompletion = false,
            answerText = answerText,
            completionQuality = "abandoned",
            failureReason = failureReason,
            rewardApplied = false,
            waitCutMinutes = 0,
            helpfulnessRating = null,
        )

        viewModelScope.launch {
            repository.recordSession(session)
        }
    }

    fun requestExplicitWalkAway() {
        val state =
            _uiState.value

        if (
            state.safeExitRequestStatus ==
                ResetReadSafeExitRequestStatus
                    .Recording ||
            state.safeExitRequestStatus ==
                ResetReadSafeExitRequestStatus
                    .Durable
        ) {
            return
        }

        val sessionId =
            state.recordedSessionId

        val session =
            sessionId?.let { id ->
                latestCompletedSession
                    ?.takeIf {
                        it.id ==
                            id
                    }
                    ?: latestSessions
                        .firstOrNull {
                            it.id ==
                                id
                        }
            }

        if (
            !state.validCompletion ||
            session == null ||
            !session.validCompletion
        ) {
            _uiState.update {
                it.copy(
                    safeExitRequestStatus =
                        ResetReadSafeExitRequestStatus
                            .Failed,
                )
            }

            return
        }

        /*
         * The complete privacy-safe request is submitted before entering
         * viewModelScope. Worker reconstruction does not depend on the
         * Reset Reading history write completing.
         */
        val enqueueReceipt =
            safeExitReconciliationScheduler
                .request(
                    session,
                )

        _uiState.update {
            it.copy(
                safeExitRequestStatus =
                    ResetReadSafeExitRequestStatus
                        .Recording,
            )
        }

        viewModelScope.launch {
            val enqueueAccepted =
                enqueueReceipt
                    ?.awaitAccepted()
                    ?: false

            /*
             * Reset Reading history is separate from Safe Exit durability.
             * Preserve it where possible, but an ordinary history failure
             * must not prevent the immediate Room attempt or invalidate an
             * accepted WorkManager request.
             */
            try {
                repository.recordSession(
                    session,
                )
            } catch (
                cancellation:
                    CancellationException,
            ) {
                throw cancellation
            } catch (
                _: Exception,
            ) {
                // The explicit Safe Exit request remains independently durable.
            }

            val immediateResult =
                try {
                    safeExitRecorder
                        .recordExplicitWalkAway(
                            session,
                        )
                } catch (
                    cancellation:
                        CancellationException,
                ) {
                    throw cancellation
                } catch (
                    _: Exception,
                ) {
                    SafeExitRecordingResult
                        .RetryableFailure
                }

            val finalStatus =
                when (
                    immediateResult
                ) {
                    is SafeExitRecordingResult.Recorded,
                    is SafeExitRecordingResult.Duplicate,
                    ->
                        ResetReadSafeExitRequestStatus
                            .Durable

                    is SafeExitRecordingResult.Rejected ->
                        ResetReadSafeExitRequestStatus
                            .Failed

                    SafeExitRecordingResult.RetryableFailure ->
                        if (
                            enqueueAccepted
                        ) {
                            ResetReadSafeExitRequestStatus
                                .Durable
                        } else {
                            ResetReadSafeExitRequestStatus
                                .Failed
                        }
                }

            _uiState.update {
                it.copy(
                    safeExitRequestStatus =
                        finalStatus,
                )
            }
        }
    }
    fun rateHelpfulness(rating: Int) {
        val state = _uiState.value
        val article = state.article ?: return
        val sessionId = state.recordedSessionId ?: return
        val selectedOptionIndex = state.selectedOptionIndex ?: return
        if (!state.validCompletion) return

        val safeRating = rating.coerceIn(1, 5)
        _uiState.update { it.copy(helpfulnessRating = safeRating) }

        val completedAt = LocalDateTime.now()
        val secondsSpent = state.secondsSpent.coerceAtLeast(state.requiredReadSeconds)
        val answerText = article.closingQuestion.options.getOrNull(selectedOptionIndex).orEmpty()
        val session = ResetReadSessionRecord(
            id = sessionId,
            articleId = article.id,
            articleTitle = article.title,
            startedAt = completedAt.minusSeconds(secondsSpent.toLong()),
            completedAt = completedAt,
            selectedDurationSeconds = state.requiredReadSeconds,
            requiredDurationSeconds = state.requiredReadSeconds,
            secondsSpent = secondsSpent,
            selectedOptionIndex = selectedOptionIndex,
            validCompletion = true,
            answerText = answerText,
            completionQuality = "valid",
            failureReason = null,
            rewardApplied = null,
            waitCutMinutes = null,
            helpfulnessRating = safeRating,
        )

        latestCompletedSession =
            session

        viewModelScope.launch {
            repository.recordSession(session)
        }
    }

    private fun selectArticle(
        readIds: Set<String>,
        sessions: List<ResetReadSessionRecord>,
        exposures: List<ResetReadArticleExposureRecord>,
        launchMode: ResetReadLaunchMode,
    ): ResetReadArticle {
        val now = LocalDateTime.now()
        val epochDay = now.toLocalDate().toEpochDay()
        val excludedArticleIds = cooldownExcludedResetReadArticleIds(
            now = now,
            sessions = sessions,
            exposures = exposures,
        )

        return when (launchMode) {
            ResetReadLaunchMode.Normal -> recommendedResetReadArticleForDay(
                epochDay = epochDay,
                articles = repository.articles,
                sessions = sessions,
                readIds = readIds,
                excludedArticleIds = excludedArticleIds,
            )
            ResetReadLaunchMode.Fallback -> fallbackResetReadArticleForDay(
                epochDay = epochDay,
                articles = repository.articles,
                excludedArticleIds = excludedArticleIds,
            )
        }
    }

    private fun recordSessionIfNeeded(
        article: ResetReadArticle,
        selectedOptionIndex: Int,
    ) {
        val state = _uiState.value
        if (state.recordedSession || !state.validCompletion) return

        val sessionId = System.currentTimeMillis()
        val shouldAskHelpfulness = (state.completedSessionCount + 1) % 3 == 0
        _uiState.update {
            it.copy(
                recordedSession = true,
                recordedSessionId = sessionId,
                askHelpfulnessRating = shouldAskHelpfulness,
            )
        }

        val completedAt = LocalDateTime.now()
        val secondsSpent = state.secondsSpent.coerceAtLeast(state.requiredReadSeconds)
        val answerText = article.closingQuestion.options.getOrNull(selectedOptionIndex).orEmpty()
        val session = ResetReadSessionRecord(
            id = sessionId,
            articleId = article.id,
            articleTitle = article.title,
            startedAt = completedAt.minusSeconds(secondsSpent.toLong()),
            completedAt = completedAt,
            selectedDurationSeconds = state.requiredReadSeconds,
            requiredDurationSeconds = state.requiredReadSeconds,
            secondsSpent = secondsSpent,
            selectedOptionIndex = selectedOptionIndex,
            validCompletion = true,
            answerText = answerText,
            completionQuality = "valid",
            failureReason = null,
            rewardApplied = null,
            waitCutMinutes = null,
            helpfulnessRating = null,
        )

        latestCompletedSession =
            session

        viewModelScope.launch {
            repository.recordSession(session)
        }
    }

    private fun recordArticleShownIfNeeded(article: ResetReadArticle) {
        if (recordedShownArticleId == article.id) return
        recordedShownArticleId = article.id

        viewModelScope.launch {
            repository.recordArticleShown(
                articleId = article.id,
                shownAt = LocalDateTime.now(),
            )
        }
    }

    private fun markReadIfNeeded(articleId: String) {
        if (_uiState.value.markedRead) return
        _uiState.update { it.copy(markedRead = true) }
        viewModelScope.launch {
            repository.markRead(articleId)
        }
    }
}
