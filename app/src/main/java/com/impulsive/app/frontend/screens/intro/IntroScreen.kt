package com.impulsive.app.frontend.screens.intro

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.impulsive.app.R

@Composable
fun IntroScreen(
    onIntroFinished: () -> Unit,
) {
    val context = LocalContext.current
    val introUri = Uri.parse("android.resource://${context.packageName}/${R.raw.impulsive_intro}")
    val currentOnIntroFinished by rememberUpdatedState(onIntroFinished)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFEFC)),
    ) {
        AndroidView(
            factory = { viewContext ->
                val textureView = TextureView(viewContext)
                textureView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var mediaPlayer: MediaPlayer? = null
                    private var surface: Surface? = null
                    private var videoW = 0f
                    private var videoH = 0f
                    private var hasFinished = false

                    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        applyTransform()
                    }

                    init {
                        textureView.addOnLayoutChangeListener(layoutChangeListener)
                    }

                    private fun finishIntro() {
                        if (hasFinished) return
                        hasFinished = true
                        currentOnIntroFinished()
                    }

                    private fun releasePlayer() {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        surface?.release()
                        surface = null
                    }

                    private fun applyTransform() {
                        val viewWidth = textureView.width.toFloat()
                        val viewHeight = textureView.height.toFloat()
                        val videoWidth = videoW
                        val videoHeight = videoH
                        if (
                            videoWidth <= 0f ||
                            videoHeight <= 0f ||
                            viewWidth <= 0f ||
                            viewHeight <= 0f
                        ) {
                            return
                        }
                        // Fit the whole video inside the view, so the
                        // composition is preserved in any orientation instead of being cropped.
                        val scale = minOf(viewWidth / videoWidth, viewHeight / videoHeight)
                        val scaledWidth = videoWidth * scale
                        val scaledHeight = videoHeight * scale
                        val matrix = Matrix().apply {
                            setScale(
                                scaledWidth / viewWidth,
                                scaledHeight / viewHeight,
                            )
                            postTranslate(
                                (viewWidth - scaledWidth) / 2f,
                                (viewHeight - scaledHeight) / 2f,
                            )
                        }
                        textureView.setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        releasePlayer()
                        val playbackSurface = Surface(surfaceTexture)
                        surface = playbackSurface
                        val player = MediaPlayer()
                        mediaPlayer = player
                        try {
                            player.setDataSource(viewContext, introUri)
                            player.setSurface(playbackSurface)
                            player.setVolume(0f, 0f)
                            player.isLooping = false
                            player.setOnPreparedListener { mp ->
                                videoW = mp.videoWidth.toFloat()
                                videoH = mp.videoHeight.toFloat()
                                applyTransform()
                                mp.start()
                            }
                            player.setOnCompletionListener {
                                finishIntro()
                            }
                            player.setOnErrorListener { _, _, _ ->
                                releasePlayer()
                                finishIntro()
                                true
                            }
                            player.prepareAsync()
                        } catch (_: Exception) {
                            releasePlayer()
                            finishIntro()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        applyTransform()
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        textureView.removeOnLayoutChangeListener(layoutChangeListener)
                        releasePlayer()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                }
                textureView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
