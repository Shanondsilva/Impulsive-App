package com.impulsive.app.backend.session.tasks

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessage
import com.impulsive.app.backend.data.local.preferences.FutureSelfMessageKind
import com.impulsive.app.backend.data.repository.FutureSelfMessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

const val MaxRecordingMillis: Long = 60_000L
const val MaxTextMessageChars: Int = 280
const val MinTextDwellMillis: Long = 8_000L
const val VoiceCompletionFraction: Float = 0.90f

enum class FutureSelfRecorderState {
    Idle,
    Recording,
    Recorded,
}

data class FutureSelfRecordUiState(
    val message: FutureSelfMessage? = null,
    val recorderState: FutureSelfRecorderState = FutureSelfRecorderState.Idle,
    val currentRecordingMillis: Long = 0L,
    val pendingVoiceFilePath: String? = null,
    val pendingVoiceDurationMillis: Long = 0L,
    val textDraft: String = "",
    val isPlayingPending: Boolean = false,
)

data class FutureSelfPlaybackUiState(
    val message: FutureSelfMessage? = null,
    val fallbackMessage: String? = null,
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackPositionMillis: Long = 0L,
    val playbackDurationMillis: Long = 0L,
    val dwellMillis: Long = 0L,
    val playbackCompletedFraction: Float = 0f,
    val finalChoice: FutureSelfFinalChoice? = null,
    val showSuccess: Boolean = false,
) {
    val usingFallback: Boolean get() = message == null
    val voiceComplete: Boolean
        get() = message?.kind == FutureSelfMessageKind.Voice &&
            playbackCompletedFraction >= VoiceCompletionFraction
    val textDwellComplete: Boolean
        get() = (message?.kind == FutureSelfMessageKind.Text || usingFallback) &&
            dwellMillis >= MinTextDwellMillis
    val canChoose: Boolean get() = voiceComplete || textDwellComplete
    val validCompletion: Boolean get() = showSuccess && finalChoice != null && canChoose
}

enum class FutureSelfFinalChoice {
    WillWait, AnotherTask, ImOkayNow,
}

class FutureSelfMessageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FutureSelfMessageRepository(application)

    private val _recordState = MutableStateFlow(FutureSelfRecordUiState())
    val recordState: StateFlow<FutureSelfRecordUiState> = _recordState

    private val _playbackState = MutableStateFlow(FutureSelfPlaybackUiState())
    val playbackState: StateFlow<FutureSelfPlaybackUiState> = _playbackState

    private var recorder: MediaRecorder? = null
    private var recordingStartMillis: Long = 0L
    private var currentRecordingFile: File? = null

    private var player: MediaPlayer? = null
    private var playerResumed = false
    private var lastFrameMs: Long? = null

    init {
        viewModelScope.launch {
            repository.message.collect { message ->
                _recordState.update {
                    it.copy(
                        message = message,
                        textDraft = if (it.textDraft.isBlank() && message?.kind == FutureSelfMessageKind.Text) {
                            message.text.orEmpty()
                        } else {
                            it.textDraft
                        },
                    )
                }
                _playbackState.update { it.copy(message = message) }
            }
        }
    }

    fun initPlayback(fallbackMessage: String?) {
        _playbackState.update { current ->
            if (current.isLoaded) current.copy(fallbackMessage = fallbackMessage)
            else current.copy(fallbackMessage = fallbackMessage, isLoaded = true)
        }
        val current = _playbackState.value
        if (current.message?.kind == FutureSelfMessageKind.Voice) {
            preparePlayer(current.message.voiceFilePath)
        }
    }

    fun resumePlayback() {
        playerResumed = true
        lastFrameMs = null
    }

    fun pausePlayback() {
        playerResumed = false
        lastFrameMs = null
        player?.pause()
        _playbackState.update { it.copy(isPlaying = false) }
    }

    fun tickPlayback() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFrameMs
        lastFrameMs = now
        val state = _playbackState.value
        if (!playerResumed || state.showSuccess) return
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 100L)
        if (delta <= 0L) return

        val playerNow = player
        val updatedPosition = if (state.isPlaying && playerNow != null) {
            playerNow.currentPosition.toLong().coerceAtLeast(0L)
        } else {
            state.playbackPositionMillis
        }
        val duration = state.playbackDurationMillis.coerceAtLeast(1L)
        val fraction = if (state.message?.kind == FutureSelfMessageKind.Voice) {
            (updatedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f).coerceAtLeast(state.playbackCompletedFraction)
        } else {
            state.playbackCompletedFraction
        }
        _playbackState.update {
            it.copy(
                playbackPositionMillis = updatedPosition,
                playbackCompletedFraction = fraction,
                dwellMillis = it.dwellMillis + delta,
            )
        }
    }

    fun togglePlayback() {
        val state = _playbackState.value
        val message = state.message ?: return
        if (message.kind != FutureSelfMessageKind.Voice) return
        val playerNow = player ?: preparePlayer(message.voiceFilePath) ?: return
        if (playerNow.isPlaying) {
            playerNow.pause()
            _playbackState.update { it.copy(isPlaying = false) }
        } else {
            runCatching {
                if (state.playbackCompletedFraction >= 1f) {
                    playerNow.seekTo(0)
                    _playbackState.update { it.copy(playbackCompletedFraction = 0f) }
                }
                playerNow.start()
                _playbackState.update { it.copy(isPlaying = true) }
            }
        }
    }

    fun chooseFinal(choice: FutureSelfFinalChoice) {
        val state = _playbackState.value
        if (!state.canChoose || state.showSuccess) return
        _playbackState.update { it.copy(finalChoice = choice, showSuccess = true) }
    }

    fun startRecording() {
        if (_recordState.value.recorderState == FutureSelfRecorderState.Recording) return
        val outFile = File(getApplication<Application>().filesDir, "future_self_pending.m4a")
        runCatching { outFile.delete() }
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(getApplication())
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setMaxDuration(MaxRecordingMillis.toInt())
            newRecorder.setOutputFile(outFile.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
        }.onFailure {
            runCatching { newRecorder.release() }
            return
        }
        recorder = newRecorder
        currentRecordingFile = outFile
        recordingStartMillis = SystemClock.elapsedRealtime()
        _recordState.update {
            it.copy(
                recorderState = FutureSelfRecorderState.Recording,
                currentRecordingMillis = 0L,
                pendingVoiceFilePath = null,
                pendingVoiceDurationMillis = 0L,
            )
        }
    }

    fun updateRecordingTimer() {
        if (_recordState.value.recorderState != FutureSelfRecorderState.Recording) return
        val elapsed = (SystemClock.elapsedRealtime() - recordingStartMillis).coerceAtLeast(0L)
        if (elapsed >= MaxRecordingMillis) {
            stopRecording()
        } else {
            _recordState.update { it.copy(currentRecordingMillis = elapsed) }
        }
    }

    fun stopRecording() {
        val rec = recorder ?: return
        val file = currentRecordingFile
        val durationMillis = (SystemClock.elapsedRealtime() - recordingStartMillis)
            .coerceAtLeast(0L)
            .coerceAtMost(MaxRecordingMillis)
        runCatching {
            rec.stop()
        }
        runCatching { rec.release() }
        recorder = null
        _recordState.update {
            it.copy(
                recorderState = if (file != null && file.exists()) FutureSelfRecorderState.Recorded else FutureSelfRecorderState.Idle,
                currentRecordingMillis = durationMillis,
                pendingVoiceFilePath = file?.absolutePath,
                pendingVoiceDurationMillis = durationMillis,
            )
        }
    }

    fun discardPendingRecording() {
        val path = _recordState.value.pendingVoiceFilePath
        if (path != null) runCatching { File(path).delete() }
        _recordState.update {
            it.copy(
                recorderState = FutureSelfRecorderState.Idle,
                currentRecordingMillis = 0L,
                pendingVoiceFilePath = null,
                pendingVoiceDurationMillis = 0L,
            )
        }
    }

    fun saveVoiceRecording() {
        val pending = _recordState.value.pendingVoiceFilePath ?: return
        val sourceFile = File(pending)
        if (!sourceFile.exists()) return
        val destFile = File(getApplication<Application>().filesDir, "future_self_message.m4a")
        runCatching {
            if (destFile.exists()) destFile.delete()
            sourceFile.copyTo(destFile, overwrite = true)
            sourceFile.delete()
        }
        viewModelScope.launch {
            repository.saveVoice(destFile.absolutePath, System.currentTimeMillis())
            _recordState.update {
                it.copy(
                    recorderState = FutureSelfRecorderState.Idle,
                    pendingVoiceFilePath = null,
                    pendingVoiceDurationMillis = 0L,
                    currentRecordingMillis = 0L,
                )
            }
        }
    }

    fun updateTextDraft(value: String) {
        val trimmed = if (value.length > MaxTextMessageChars) value.take(MaxTextMessageChars) else value
        _recordState.update { it.copy(textDraft = trimmed) }
    }

    fun saveTextMessage() {
        val draft = _recordState.value.textDraft.trim()
        if (draft.isBlank()) return
        viewModelScope.launch {
            repository.saveText(draft, System.currentTimeMillis())
        }
    }

    fun deleteSavedMessage() {
        viewModelScope.launch {
            repository.delete()
            _recordState.update { it.copy(textDraft = "") }
            _playbackState.update {
                it.copy(
                    playbackPositionMillis = 0L,
                    playbackDurationMillis = 0L,
                    playbackCompletedFraction = 0f,
                    isPlaying = false,
                )
            }
            releasePlayer()
        }
    }

    private fun preparePlayer(path: String?): MediaPlayer? {
        if (path == null) return null
        releasePlayer()
        val newPlayer = MediaPlayer()
        val ok = runCatching {
            newPlayer.setDataSource(path)
            newPlayer.prepare()
        }.isSuccess
        if (!ok) {
            runCatching { newPlayer.release() }
            return null
        }
        newPlayer.setOnCompletionListener {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    playbackCompletedFraction = 1f,
                    playbackPositionMillis = it.playbackDurationMillis,
                )
            }
        }
        player = newPlayer
        _playbackState.update {
            it.copy(
                playbackDurationMillis = newPlayer.duration.toLong().coerceAtLeast(1L),
                playbackPositionMillis = 0L,
                playbackCompletedFraction = 0f,
                isPlaying = false,
            )
        }
        return newPlayer
    }

    private fun releasePlayer() {
        val p = player ?: return
        runCatching { p.stop() }
        runCatching { p.release() }
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { recorder?.release() }
        recorder = null
        releasePlayer()
    }
}
