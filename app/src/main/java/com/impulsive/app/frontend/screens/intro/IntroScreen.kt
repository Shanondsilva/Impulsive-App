package com.impulsive.app.frontend.screens.intro

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
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
    val currentOnIntroFinished by rememberUpdatedState(onIntroFinished)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                    private var videoW = 0f
                    private var videoH = 0f

                    private fun applyTransform(viewWidth: Int, viewHeight: Int) {
                        if (videoW <= 0f || videoH <= 0f || viewWidth <= 0 || viewHeight <= 0) return
                        val vw = viewWidth.toFloat()
                        val vh = viewHeight.toFloat()
                        // Fit the whole video inside the view (letterbox on black), so the
                        // composition is preserved in any orientation instead of being cropped.
                        val scale = minOf(vw / videoW, vh / videoH)
                        val scaledWidth = videoW * scale
                        val scaledHeight = videoH * scale
                        val matrix = Matrix()
                        matrix.setScale(
                            scaledWidth / vw,
                            scaledHeight / vh,
                            vw / 2f,
                            vh / 2f,
                        )
                        textureView.setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        val surface = Surface(surfaceTexture)
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(
                                viewContext,
                                Uri.parse(
                                    "android.resource://${viewContext.packageName}/${R.raw.impulsive_intro}",
                                ),
                            )
                            setSurface(surface)
                            setVolume(0f, 0f)
                            isLooping = false
                            setOnPreparedListener { mp ->
                                videoW = mp.videoWidth.toFloat()
                                videoH = mp.videoHeight.toFloat()
                                applyTransform(textureView.width, textureView.height)
                                mp.start()
                            }
                            setOnCompletionListener {
                                currentOnIntroFinished()
                            }
                            prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        applyTransform(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        mediaPlayer?.release()
                        mediaPlayer = null
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
