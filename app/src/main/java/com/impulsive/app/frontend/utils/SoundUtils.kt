package com.impulsive.app.frontend.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.impulsive.app.R

@Composable
fun rememberImpulsiveSounds(enabled: Boolean): ImpulsiveSounds {
    val context = LocalContext.current.applicationContext
    val sounds = remember(context) { ImpulsiveSounds(context) }
    sounds.setEnabled(enabled)
    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }
    return sounds
}

@Stable
class ImpulsiveSounds internal constructor(private val context: Context) {
    private var enabled: Boolean = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val reflexCorrectId = pool.load(context, R.raw.reflex_correct, 1)
    private val reflexMissId = pool.load(context, R.raw.reflex_miss, 1)
    private val reflexSuccessId = pool.load(context, R.raw.reflex_success, 1)
    private val skySetClickId = pool.load(context, R.raw.sky_set_click, 1)
    private val skyCompleteId = pool.load(context, R.raw.sky_complete, 1)
    private val cascadePlaceId = pool.load(context, R.raw.cascade_place, 1)
    private val cascadeClearId = pool.load(context, R.raw.cascade_clear, 1)
    private val cascadeOverId = pool.load(context, R.raw.cascade_over, 1)

    private var ambient: MediaPlayer? = null
    private var music: MediaPlayer? = null

    init {
        pool.setOnLoadCompleteListener { loadedPool, sampleId, status ->
            if (status == 0) {
                loadedPool.play(sampleId, 0f, 0f, 0, 0, 1f)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            stopAmbient()
            stopCascadeMusic()
        }
    }

    fun reflexCorrect() = play(reflexCorrectId, 0.9f)

    fun reflexMiss() = play(reflexMissId, 0.85f)

    fun reflexSuccess() = play(reflexSuccessId, 1.0f)

    fun skySetClick() = play(skySetClickId, 0.7f)

    fun skyComplete() = play(skyCompleteId, 0.9f)

    fun startAmbient() {
        if (!enabled) return
        val am = audioManager
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        val existing = ambient
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        val mp = MediaPlayer.create(context, R.raw.sky_ambient) ?: return
        mp.isLooping = true
        mp.setVolume(0.45f, 0.45f)
        mp.start()
        ambient = mp
    }

    fun stopAmbient() {
        ambient?.let { if (it.isPlaying) it.pause() }
    }

    fun cascadePlace() = play(cascadePlaceId, 0.8f)

    fun cascadeClear() = play(cascadeClearId, 0.9f)

    fun cascadeOver() = play(cascadeOverId, 0.9f)

    fun startCascadeMusic() {
        if (!enabled) return
        val am = audioManager
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        val existing = music
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        val mp = MediaPlayer.create(context, R.raw.cascade_music) ?: return
        mp.isLooping = true
        mp.setVolume(0.4f, 0.4f)
        mp.start()
        music = mp
    }

    fun stopCascadeMusic() {
        music?.let { if (it.isPlaying) it.pause() }
    }

    private fun play(soundId: Int, volume: Float) {
        if (!enabled) return
        if (soundId == 0) return
        val am = audioManager
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        pool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
        ambient?.release()
        ambient = null
        music?.release()
        music = null
    }
}
