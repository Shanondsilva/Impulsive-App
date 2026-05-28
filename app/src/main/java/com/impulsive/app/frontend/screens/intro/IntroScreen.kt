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
                TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var mediaPlayer: MediaPlayer? = null

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
                                    val videoWidth = mp.videoWidth.toFloat()
                                    val videoHeight = mp.videoHeight.toFloat()
                                    val viewWidth = width.toFloat()
                                    val viewHeight = height.toFloat()

                                    val scaleX = viewWidth / videoWidth
                                    val scaleY = viewHeight / videoHeight
                                    val scale = maxOf(scaleX, scaleY)

                                    val scaledWidth = videoWidth * scale
                                    val scaledHeight = videoHeight * scale

                                    val matrix = Matrix()
                                    matrix.setScale(
                                        scaledWidth / viewWidth,
                                        scaledHeight / viewHeight,
                                        viewWidth / 2f,
                                        viewHeight / 2f,
                                    )
                                    setTransform(matrix)
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
                        ) {}

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
