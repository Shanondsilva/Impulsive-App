package com.impulsive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.frontend.navigation.OnboardingNavHost
import com.impulsive.app.frontend.theme.ImpulsiveTheme

class MainActivity : ComponentActivity() {

    // Activity-scoped so we can forward onActivityResult to the Facebook SDK.
    // The same instance is passed into the onboarding graph and login screen.
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                OnboardingNavHost(authViewModel = authViewModel)
            }
        }
    }

    /**
     * Facebook Login returns its result through the legacy onActivityResult
     * path. Forward it so [com.impulsive.app.backend.data.repository.FirebaseAuthRepository]
     * can complete the suspending sign-in coroutine.
     */
    @Deprecated("onActivityResult is deprecated, but the Facebook SDK still requires it.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authViewModel.forwardActivityResult(requestCode, resultCode, data)
    }
}
