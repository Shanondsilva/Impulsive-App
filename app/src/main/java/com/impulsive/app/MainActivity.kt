package com.impulsive.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.impulsive.app.backend.data.UserDataManager
import com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.PlayStoreRatingPromptDataSource
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.data.repository.SessionValidationResult
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.service.protection.ProtectionInterruptionOverlay
import com.impulsive.app.backend.service.protection.InterruptionNotificationLimiter
import com.impulsive.app.backend.service.protection.AppMonitorService
import com.impulsive.app.backend.service.protection.ProtectionNotificationHelper
import com.impulsive.app.backend.service.billing.BillingManager
import com.impulsive.app.backend.service.billing.shouldReconcileBillingAfterAuthChange
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.core.util.resolveSceneTime
import com.impulsive.app.core.util.shouldUseDarkTheme
import com.impulsive.app.frontend.screens.lock.AppLockGateScreen
import com.impulsive.app.frontend.navigation.AppNavHost
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val appLockDataSource by lazy {
        AppLockPreferencesDataSource(applicationContext)
    }
    private val playStoreRatingPromptDataSource by lazy {
        PlayStoreRatingPromptDataSource(applicationContext)
    }
    private val billingManager by lazy { BillingManager(applicationContext) }
    private var lastBillingAuthenticatedUserId: String? = null
    private val billingAuthStateListener = FirebaseAuth.AuthStateListener { auth ->
        val currentUserId = auth.currentUser?.uid
        if (
            shouldReconcileBillingAfterAuthChange(
                previousUserId = lastBillingAuthenticatedUserId,
                currentUserId = currentUserId,
            )
        ) {
            billingManager.onAuthenticatedUserAvailable()
        }
        lastBillingAuthenticatedUserId = currentUserId
    }
    private val pendingBlockRequest = mutableStateOf<BlockRequest?>(null)
    private val pendingJournalNoteId = mutableStateOf<Long?>(null)
    private val unlockedThisSession = mutableStateOf(false)
    private val showGuestPinResetDialog = mutableStateOf(false)
    private var foregroundSessionValidationInFlight = false
    private var remoteAccountDeletionHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        endFallbackNotificationIfRequested(intent)
        pendingBlockRequest.value = intent.toBlockRequestOrNull()
        pendingJournalNoteId.value = intent.openJournalNoteIdOrNull()
        if (pendingBlockRequest.value != null) {
            cancelBlockedAttemptNotification(
                pendingBlockRequest.value?.sourcePackageName,
            )
        }
        refreshEmailVerificationIfReturnIntent(intent)
        billingManager.connect()
        FirebaseAuth.getInstance().addAuthStateListener(billingAuthStateListener)
        com.impulsive.app.backend.service.journal.FeedbackPromptScheduler(this)
            .scheduleDailyNudge()

        WorkManager
            .getInstance(applicationContext)
            .cancelUniqueWork(
                "feedback_reading_daily",
            )

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                JournalRepository(applicationContext)
                    .purgeObsoleteFeedbackNotes()
            }

            runCatching {
                applicationContext
                    .preferencesDataStoreFile(
                        "feedback_insights",
                    )
                    .delete()
            }
        }

        setContent {
            val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val appLockEnabled by appLockDataSource.enabled.collectAsStateWithLifecycle(initialValue = false)
            val unlocked by unlockedThisSession
            val systemInDark = isSystemInDarkTheme()

            // Single ticking time source, re-emits every minute so both shouldUseDarkTheme
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
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }

            val locked = appLockEnabled && !unlocked

            LaunchedEffect(locked, pendingBlockRequest.value) {
                if (locked && pendingBlockRequest.value != null) {
                    ProtectionInterruptionOverlay.dismissAny()
                }
            }

            ImpulsiveTheme(darkTheme = useDark) {
                if (locked) {
                    AppLockGateScreen(
                        onUnlocked = { unlockedThisSession.value = true },
                        onForgotPin = { handleForgotPin() },
                    )
                } else {
                    AppNavHost(
                        authViewModel = authViewModel,
                        billingManager = billingManager,
                        initialBlockRequest = if (!locked) pendingBlockRequest.value else null,
                        onBlockRequestConsumed = {
                            if (pendingBlockRequest.value != null) {
                                ProtectionInterruptionOverlay.dismissAny()
                                pendingBlockRequest.value = null
                            }
                        },
                        initialJournalNoteId = pendingJournalNoteId.value,
                        onJournalNoteConsumed = { pendingJournalNoteId.value = null },
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
        endFallbackNotificationIfRequested(intent)
        pendingBlockRequest.value = intent.toBlockRequestOrNull()
        pendingJournalNoteId.value = intent.openJournalNoteIdOrNull()
        if (pendingBlockRequest.value != null) {
            cancelBlockedAttemptNotification(
                pendingBlockRequest.value?.sourcePackageName,
            )
        }
        refreshEmailVerificationIfReturnIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        billingManager.onAppForegrounded()

        if (pendingBlockRequest.value == null) {
            ProtectionInterruptionOverlay.dismissAny()
        }

        checkForRemoteAccountDeletion()

        authViewModel.refreshEmailVerification()

        lifecycleScope.launch(Dispatchers.IO) {
            playStoreRatingPromptDataSource.recordAppUse()
        }
    }

    /**
     * Checks whether the Firebase account behind the currently running app session
     * was deleted remotely, for example through useimpulsive.com/delete-account.
     *
     * A permanent local wipe is performed ONLY when Firebase definitively reports
     * ERROR_USER_NOT_FOUND.
     */
    private fun checkForRemoteAccountDeletion() {
        if (
            foregroundSessionValidationInFlight ||
            remoteAccountDeletionHandled
        ) {
            return
        }

        val currentUser = authViewModel.state.value.user ?: return

        /*
         * Anonymous/guest users are deliberately excluded from the website-driven
         * Firebase account deletion detection flow.
         */
        if (currentUser.provider == AuthProvider.Guest) {
            return
        }

        foregroundSessionValidationInFlight = true

        lifecycleScope.launch {
            try {
                when (authViewModel.validateForegroundSession()) {
                    SessionValidationResult.RemotelyDeleted -> {
                        /*
                         * The Firebase account has definitively been deleted.
                         *
                         * Order matters:
                         *
                         * 1. Permanently erase local Room/DataStore/restore data.
                         * 2. Clear the cached Firebase/Facebook authentication.
                         * 3. Cold restart into the app's normal fresh-start flow.
                         *
                         * If local deletion throws, do not sign out and do not
                         * restart. This allows the cleanup to be retried rather
                         * than silently leaving supposedly deleted data behind.
                         */
                        val userDataManager =
                            UserDataManager(applicationContext)

                        userDataManager.deleteAllData()

                        authViewModel.clearValidatedSession()

                        remoteAccountDeletionHandled = true

                        userDataManager.restartApp()
                    }

                    SessionValidationResult.Invalid -> {
                        /*
                         * The Firebase session is unusable for a reason other than
                         * confirmed account deletion.
                         *
                         * Clear authentication, but preserve ALL local Impulsive
                         * data. Never treat disabled/expired/revoked credentials
                         * as a request to erase user data.
                         */
                        authViewModel.clearValidatedSession()
                    }

                    SessionValidationResult.Valid,
                    SessionValidationResult.NoSession,
                    SessionValidationResult.TransientFailure,
                    -> Unit
                }
            } catch (error: Exception) {
                /*
                 * Most importantly, never wipe/restart on an unknown failure.
                 *
                 * If deleteAllData() itself failed after a confirmed remote
                 * deletion, the authenticated session is intentionally retained
                 * so this cleanup can be attempted again later.
                 */
                android.util.Log.e(
                    "MainActivity",
                    "Foreground authentication validation or remote deletion cleanup failed.",
                    error,
                )
            } finally {
                foregroundSessionValidationInFlight = false
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unlockedThisSession.value = false
        RestoreSnapshotRefreshScheduler.request(applicationContext)
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(billingAuthStateListener)
        billingManager.release()
        super.onDestroy()
    }

    @Deprecated("onActivityResult is deprecated, but the Facebook SDK still requires it.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authViewModel.forwardActivityResult(requestCode, resultCode, data)
    }

    private fun Intent?.openJournalNoteIdOrNull(): Long? {
        val id = this?.getLongExtra(
            com.impulsive.app.backend.service.journal.JournalReminderWorker.ExtraOpenJournalNoteId,
            0L,
        ) ?: 0L
        return if (id > 0L) id else null
    }

    private fun Intent?.toBlockRequestOrNull(): BlockRequest? {
        if (this == null) return null
        val adaptiveDecisionId = getStringExtra(BlockRequest.ExtraAdaptiveDecisionId)
            ?.takeIf { it.isNotBlank() }
        val sourcePackage = getStringExtra(BlockRequest.ExtraSourcePackage).orEmpty()
        if (sourcePackage.isBlank() && adaptiveDecisionId == null) return null
        return BlockRequest(
            sourcePackageName = sourcePackage.ifBlank { "adaptive" },
            sourceLabel = getStringExtra(BlockRequest.ExtraSourceLabel).orEmpty()
                .ifBlank { if (sourcePackage.isBlank()) "Impulsive" else sourcePackage },
            detectedAtMillis = getLongExtra(BlockRequest.ExtraDetectedAtMillis, System.currentTimeMillis()),
            launchTarget = getStringExtra(BlockRequest.ExtraLaunchTarget)
                ?.let { stored ->
                    BlockLaunchTarget.entries.firstOrNull { target -> target.name == stored }
                }
                ?: BlockLaunchTarget.BlockScreen,
            adaptiveDecisionId = adaptiveDecisionId,
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
            uri.isHttpsEmailVerificationReturn()
    }

    private fun Uri.isHttpsEmailVerificationReturn(): Boolean =
        scheme == "https" &&
            host == "useimpulsive.com" &&
            path == "/auth/verified"

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

    private fun endFallbackNotificationIfRequested(intent: Intent?) {
        if (intent?.action != ProtectionNotificationHelper.ActionOpenInterruptionHome) {
            return
        }

        cancelBlockedAttemptNotification(
            intent.getStringExtra(AppMonitorService.ExtraFallbackIncidentPackageName),
        )
    }

    private fun cancelBlockedAttemptNotification(sourcePackageName: String?) {
        runCatching {
            sourcePackageName?.let { packageName ->
                InterruptionNotificationLimiter.endAppEncounter(packageName)
                startService(
                    Intent(applicationContext, AppMonitorService::class.java).apply {
                        action = AppMonitorService.ActionEndFallbackNotificationIncident
                        putExtra(
                            AppMonitorService.ExtraFallbackIncidentPackageName,
                            packageName,
                        )
                    },
                )
            }
            ProtectionNotificationHelper(applicationContext)
                .cancelBlockedAttemptNotification()
        }
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
            launchTarget: BlockLaunchTarget = BlockLaunchTarget.BlockScreen,
            detectedAtMillis: Long = System.currentTimeMillis(),
            adaptiveDecisionId: String? = null,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockRequest.ExtraSourcePackage, sourcePackageName)
            putExtra(BlockRequest.ExtraSourceLabel, sourceLabel)
            putExtra(BlockRequest.ExtraDetectedAtMillis, detectedAtMillis)
            putExtra(BlockRequest.ExtraLaunchTarget, launchTarget.name)
            adaptiveDecisionId?.let {
                putExtra(BlockRequest.ExtraAdaptiveDecisionId, it)
            }
        }

        fun createAdaptiveMomentIntent(
            context: Context,
            decisionId: String,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockRequest.ExtraLaunchTarget, BlockLaunchTarget.AdaptiveMoment.name)
            putExtra(BlockRequest.ExtraAdaptiveDecisionId, decisionId)
        }
    }
}
