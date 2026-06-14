package com.impulsive.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.resolveSceneTime
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.frontend.screens.lock.AppLockGateScreen
import com.impulsive.app.frontend.navigation.AppNavHost
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val appLockDataSource by lazy {
        AppLockPreferencesDataSource(applicationContext)
    }
    private val pendingBlockRequest = mutableStateOf<BlockRequest?>(null)
    private val blockLaunchBypassActive = mutableStateOf(false)
    private val unlockedThisSession = mutableStateOf(false)
    private val showGuestPinResetDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        pendingBlockRequest.value = intent.toBlockRequestOrNull()
        blockLaunchBypassActive.value = pendingBlockRequest.value != null
        refreshEmailVerificationIfReturnIntent(intent)

        setContent {
            val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val appLockEnabled by appLockDataSource.enabled.collectAsStateWithLifecycle(initialValue = false)
            val unlocked by unlockedThisSession
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

            val blockActive = pendingBlockRequest.value != null || blockLaunchBypassActive.value
            val locked = appLockEnabled && !unlocked && !blockActive

            ImpulsiveTheme(darkTheme = useDark) {
                if (locked) {
                    AppLockGateScreen(
                        onUnlocked = { unlockedThisSession.value = true },
                        onForgotPin = { handleForgotPin() },
                    )
                } else {
                    AppNavHost(
                        authViewModel = authViewModel,
                        initialBlockRequest = pendingBlockRequest.value,
                        onBlockRequestConsumed = { pendingBlockRequest.value = null },
                    )
                }
                if (showGuestPinResetDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showGuestPinResetDialog.value = false },
                        title = { Text("Can't reset guest PIN") },
                        text = {
                            Text(
                                "Guest accounts can't reset a PIN. To regain access you'll need to clear the app's data in system settings, which erases everything.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showGuestPinResetDialog.value = false
                                    openAppDetailsSettings()
                                },
                            ) { Text("Open settings") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showGuestPinResetDialog.value = false }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBlockRequest.value = intent.toBlockRequestOrNull()
        if (pendingBlockRequest.value != null) {
            blockLaunchBypassActive.value = true
        }
        refreshEmailVerificationIfReturnIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        authViewModel.refreshEmailVerification()
    }

    override fun onStop() {
        super.onStop()
        unlockedThisSession.value = false
        blockLaunchBypassActive.value = false
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
            isFocusSession = getBooleanExtra(BlockRequest.ExtraIsFocusSession, false),
        )
    }

    private fun refreshEmailVerificationIfReturnIntent(intent: Intent?) {
        if (intent?.isEmailVerificationReturnIntent() == true) {
            authViewModel.refreshEmailVerification()
        }
    }

    private fun Intent.isEmailVerificationReturnIntent(): Boolean {
        val uri = data ?: return false
        return action == Intent.ACTION_VIEW &&
            (uri.isHttpsEmailVerificationReturn() || uri.isCustomEmailVerificationReturn())
    }

    private fun Uri.isHttpsEmailVerificationReturn(): Boolean =
        scheme == "https" &&
            host == "useimpulsive.com" &&
            path == "/auth/verified"

    private fun Uri.isCustomEmailVerificationReturn(): Boolean =
        scheme == "impulsive" &&
            host == "auth" &&
            path == "/verified"

    private fun handleForgotPin() {
        when (authViewModel.state.value.user?.provider) {
            AuthProvider.Google -> authViewModel.signInWithGoogleForAppLockReset(this) { clearAppLockAfterProviderAuth() }
            AuthProvider.Facebook -> authViewModel.signInWithFacebookForAppLockReset(this) { clearAppLockAfterProviderAuth() }
            AuthProvider.Email, AuthProvider.Guest, null -> {
                showGuestPinResetDialog.value = true
            }
        }
    }

    private fun clearAppLockAfterProviderAuth() {
        lifecycleScope.launch {
            appLockDataSource.clearPin()
            unlockedThisSession.value = true
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
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
            isFocusSession: Boolean = false,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockRequest.ExtraSourcePackage, sourcePackageName)
            putExtra(BlockRequest.ExtraSourceLabel, sourceLabel)
            putExtra(BlockRequest.ExtraDetectedAtMillis, System.currentTimeMillis())
            putExtra(BlockRequest.ExtraIsFocusSession, isFocusSession)
        }
    }
}
