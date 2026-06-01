package com.impulsive.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.resolveSceneTime
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.frontend.navigation.AppNavHost
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import java.time.LocalTime
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val pendingBlockRequest = mutableStateOf<BlockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        pendingBlockRequest.value = intent.toBlockRequestOrNull()

        setContent {
            val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()

            // Single ticking time source — re-emits every minute so both shouldUseDarkTheme
            // and resolveSceneTime always see the same hour and update at boundary crossings.
            val currentHour by produceState(initialValue = LocalTime.now().hour) {
                while (true) {
                    val now = LocalTime.now()
                    value = now.hour
                    // Sleep until the start of the next minute.
                    delay((60 - now.second) * 1_000L - now.nano / 1_000_000L)
                }
            }

            val useDark = shouldUseDarkTheme(themeMode, systemInDark, currentHour)
            val view = LocalView.current
            SideEffect {
                val window = (view.context as android.app.Activity).window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }

            ImpulsiveTheme(darkTheme = useDark) {
                AppNavHost(
                    authViewModel = authViewModel,
                    initialBlockRequest = pendingBlockRequest.value,
                    onBlockRequestConsumed = { pendingBlockRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBlockRequest.value = intent.toBlockRequestOrNull()
    }

    @Deprecated("onActivityResult is deprecated, but the Facebook SDK still requires it.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authViewModel.forwardActivityResult(requestCode, resultCode, data)
    }

    private fun Intent?.toBlockRequestOrNull(): BlockRequest? {
        if (this == null) return null
        val sourcePackage = getStringExtra(BlockRequest.ExtraSourcePackage).orEmpty()
        if (sourcePackage.isBlank()) return null
        return BlockRequest(
            sourcePackageName = sourcePackage,
            sourceLabel = getStringExtra(BlockRequest.ExtraSourceLabel).orEmpty().ifBlank { sourcePackage },
            detectedAtMillis = getLongExtra(BlockRequest.ExtraDetectedAtMillis, System.currentTimeMillis()),
        )
    }

    companion object {
        fun createHomeIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        fun createBlockIntent(
            context: Context,
            sourcePackageName: String,
            sourceLabel: String,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockRequest.ExtraSourcePackage, sourcePackageName)
            putExtra(BlockRequest.ExtraSourceLabel, sourceLabel)
            putExtra(BlockRequest.ExtraDetectedAtMillis, System.currentTimeMillis())
        }
    }
}
