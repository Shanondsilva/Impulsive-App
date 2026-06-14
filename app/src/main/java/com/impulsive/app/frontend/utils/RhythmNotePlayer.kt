package com.impulsive.app.frontend.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.impulsive.app.R
import kotlin.math.pow

@Composable
fun rememberRhythmNotePlayer(enabled: Boolean): RhythmNotePlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { RhythmNotePlayer(context) }
    player.setEnabled(enabled)
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/**
 * Plays melody notes by pitch shifting two piano samples (C4 and C5)
 * through SoundPool's playback rate. SoundPool supports rates between
 * 0.5 and 2.0, which is one octave down or up from a sample. Using two
 * samples an octave apart gives a clean combined range from C3 (-12)
 * to C6 (+24) relative to middle C.
 */
@Stable
class RhythmNotePlayer internal constructor(private val context: Context) {
    private var enabled: Boolean = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()

    private val sampleC4Id = pool.load(context, R.raw.piano_c4, 1)
    private val sampleC5Id = pool.load(context, R.raw.piano_c5, 1)

    init {
        pool.setOnLoadCompleteListener { loadedPool, sampleId, status ->
            if (status == 0) {
                loadedPool.play(sampleId, 0f, 0f, 0, 0, 1f)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Plays a note at the given pitch in semitones relative to C4.
     * Values outside -12..24 are clamped to the playable range.
     */
    fun playNote(semitone: Int, volume: Float = 0.9f) {
        if (!enabled) return
        val am = audioManager
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return

        val clamped = semitone.coerceIn(-12, 24)
        val useC5 = clamped >= 6
        val sampleId = if (useC5) sampleC5Id else sampleC4Id
        if (sampleId == 0) return
        val samplePitch = if (useC5) 12 else 0
        val rate = 2f.pow((clamped - samplePitch) / 12f).coerceIn(0.5f, 2.0f)
        pool.play(sampleId, volume, volume, 1, 0, rate)
    }

    fun release() {
        pool.release()
    }
}
