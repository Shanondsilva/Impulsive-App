package com.impulsive.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.frontend.navigation.OnboardingNavHost
import com.impulsive.app.frontend.theme.ImpulsiveTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val pendingBlockRequest = mutableStateOf<BlockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingBlockRequest.value = intent.toBlockRequestOrNull()

        setContent {
            val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val useDark = shouldUseDarkTheme(themeMode, systemInDark)
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
                OnboardingNavHost(
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
