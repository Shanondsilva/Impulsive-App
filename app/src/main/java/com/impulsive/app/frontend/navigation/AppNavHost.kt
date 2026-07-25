package com.impulsive.app.frontend.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.provider.Settings
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryRestoreCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryRestoreDiscovery
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryRestoreResult
import com.impulsive.app.backend.data.restore.cloud.DriveAppDataAuthorization
import com.impulsive.app.backend.data.restore.cloud.DriveAuthorizationResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.data.local.device.OverlayPermissionSettingsLaunchResult
import com.impulsive.app.backend.data.local.device.OverlayPermissionSettingsNavigator
import com.impulsive.app.backend.data.local.device.UsageAccessSettingsLaunchResult
import com.impulsive.app.backend.data.local.device.BackgroundProtectionSettingsLaunchResult
import com.impulsive.app.backend.data.local.device.BackgroundProtectionSettingsNavigator
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.onboarding.AccountRestoreState
import com.impulsive.app.backend.session.onboarding.AccountLocalDataResetState
import com.impulsive.app.backend.session.onboarding.OnboardingAccountResolutionState
import com.impulsive.app.backend.session.onboarding.OnboardingCompletionState
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.screens.games.BlockCascadeScreen
import com.impulsive.app.frontend.screens.games.ReflexGameScreen
import com.impulsive.app.frontend.screens.games.RecoveryGamesScreen
import com.impulsive.app.frontend.screens.games.RhythmTilesScreen
import com.impulsive.app.frontend.screens.games.SkylineResetScreen
import com.impulsive.app.frontend.screens.intro.IntroScreen
import com.impulsive.app.frontend.screens.journal.JournalEditorScreen
import com.impulsive.app.frontend.screens.journal.JournalHubScreen
import com.impulsive.app.frontend.screens.journal.JournalListScreen
import com.impulsive.app.frontend.screens.journal.SavedNotificationsScreen
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowEvaluator
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.auth.resolvePurchaseAccountGatePhase
import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.impulsive.app.backend.service.protection.ImpulsiveVpnController
import com.impulsive.app.backend.service.protection.InterruptionNotificationStatus
import com.impulsive.app.backend.service.protection.ProtectionNotificationHelper
import com.impulsive.app.backend.service.protection.ProtectionServiceController
import com.impulsive.app.backend.service.protection.ProtectionServiceStartOrigin
import com.impulsive.app.backend.service.protection.ProtectionWatchdogScheduler
import com.impulsive.app.backend.service.protection.ProtectionLog
import com.impulsive.app.backend.service.protection.shouldRecoverProtectionService
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.protection.DnsFilterGateViewModel
import com.impulsive.app.backend.session.premium.PremiumViewModel
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.service.billing.BillingManager
import com.impulsive.app.backend.service.billing.activePlaySubscriptionProductId
import com.impulsive.app.backend.service.billing.openGooglePlaySubscriptionManagement
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.frontend.screens.onboarding.LoginSignupGuestScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingDailyRelapseCountScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingQuestionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingStartingPointScreen
import com.impulsive.app.frontend.screens.onboarding.PersonalisingSetupScreen
import com.impulsive.app.frontend.screens.focus.FocusScreen
import com.impulsive.app.frontend.screens.focus.FocusRecoveryScreen
import com.impulsive.app.frontend.screens.onboarding.ProtectionSetupOnboardingScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen
import com.impulsive.app.frontend.screens.lock.AppLockGuardHost
import com.impulsive.app.frontend.screens.lock.rememberAppLockGuardController
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.frontend.screens.protection.ImpulsiveBlockScreen
import com.impulsive.app.frontend.screens.progress.ProgressDashboardScreen
import com.impulsive.app.frontend.screens.premium.WebsiteProtectionPlusScreen
import com.impulsive.app.frontend.screens.protection.DnsFilterGateScreen
import com.impulsive.app.frontend.screens.settings.HelpFaqScreen
import com.impulsive.app.frontend.screens.settings.SettingsScreen
import com.impulsive.app.frontend.screens.settings.appVersionName
import com.impulsive.app.frontend.screens.settings.sendSupportEmail
import com.impulsive.app.frontend.screens.tasks.ResetReadScreen
import com.impulsive.app.frontend.screens.tasks.TaskToCompleteScreen
import com.impulsive.app.backend.session.tasks.ResetReadLaunchMode
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object OnboardingRoutes {
    const val Graph = "onboarding_graph"

    const val LogoIntro = "logo_intro"
    const val LoginSignupGuest = "login_signup_guest"
    const val WelcomePrivacy = "welcome_privacy"
    const val QuestionInterrupting = "question_interrupting"
    const val QuestionTiming = "question_timing"
    const val QuestionTriggers = "question_triggers"
    const val QuestionWeekOne = "question_week_one"
    const val QuestionDailyRelapseCount = "question_daily_relapse_count"
    const val ProtectionSetup = "protection_setup"
    const val ProtectionBlockedApps = "protection_blocked_apps"
    const val StartingPoint = "starting_point"
    const val PersonalisingSetup = "personalising_setup"
}

object AppRoutes {
    const val Graph = "main_graph"

    const val Home = "level_one_reveal"
    const val Settings = "settings"
    const val HelpFaq = "help_faq"
    const val Score = "score"
    const val Focus = "focus"
    const val FocusRecovery = "focus_recovery"
    const val RecoveryGames = "recovery_games"
    const val RandomRecoveryGame = "random_recovery_game/{sourcePackageName}"
    const val ReflexGame = "reflex_game"
    const val ReflexGameTask = "reflex_game_task"
    const val BlockCascadeGame = "block_cascade_game"
    const val BlockCascadeTask = "block_cascade_task"
    const val SkylineResetGame = "skyline_reset_game"
    const val SkylineResetTask = "skyline_reset_task"
    const val RhythmTilesGame = "rhythm_tiles_game"
    const val RhythmTilesTask = "rhythm_tiles_task"
    const val ResetReadTask = "reset_read_task"
    const val ResetReadFallbackTask = "reset_read_fallback_task"
    const val TaskToComplete = "task_to_complete"
    const val WebsiteProtectionPlus = "website_protection_plus"
    const val WebsiteProtectionApps = "website_protection_apps"
    const val ProtectionSetupGuide = "protection_setup_guide"
    const val ProtectionSetupGuideBlockedApps = "protection_setup_guide_blocked_apps"
    const val DnsFilterGate = "dns_filter_gate"
    const val JournalHub = "journal_hub"
    const val JournalList = "journal_list"
    const val SavedNotifications =
        "journal_saved_notifications"
    const val JournalNoteNew = "journal_note_new/{type}"
    const val JournalNoteEdit = "journal_note_edit/{noteId}"
    const val ImpulsiveBlock = "impulsive_block/{sourcePackageName}/{sourceLabel}"

    fun journalNoteNew(type: JournalNoteType): String = "journal_note_new/${type.storageValue}"
    fun journalNoteEdit(noteId: Long): String = "journal_note_edit/$noteId"
    fun impulsiveBlock(sourcePackageName: String, sourceLabel: String): String =
        "impulsive_block/${Uri.encode(sourcePackageName)}/${Uri.encode(sourceLabel)}"
    fun randomRecoveryGame(sourcePackageName: String): String =
        "random_recovery_game/${Uri.encode(sourcePackageName)}"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    protectionSetupViewModel: ProtectionSetupViewModel = viewModel(),
    authViewModel: AuthViewModel,
    billingManager: BillingManager,
    initialBlockRequest: BlockRequest? = null,
    onBlockRequestConsumed: () -> Unit = {},
    initialJournalNoteId: Long? = null,
    onJournalNoteConsumed: () -> Unit = {},
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val onboardingAccountResolutionState by
        onboardingViewModel.accountResolutionState.collectAsStateWithLifecycle()
    val accountRestoreState by onboardingViewModel.accountRestoreState.collectAsStateWithLifecycle()
    val accountLocalDataResetState by
        onboardingViewModel
            .accountLocalDataResetState
            .collectAsStateWithLifecycle()
    val onboardingCompletionState by
        onboardingViewModel.completionState.collectAsStateWithLifecycle()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val billingRestoreState by billingManager.restoreState.collectAsStateWithLifecycle()
    val billingUiState by billingManager.billingUiState.collectAsStateWithLifecycle()
    val subscriptionCatalogState by
        billingManager.subscriptionCatalogState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageAccessChecker = remember(context) { UsageAccessPermissionChecker(context) }
    val overlayPermissionSettingsNavigator =
        remember(context) { OverlayPermissionSettingsNavigator(context) }
    val backgroundProtectionSettingsNavigator =
        remember(context) { BackgroundProtectionSettingsNavigator(context) }
    val protectionNotificationHelper = remember(context) { ProtectionNotificationHelper(context) }
    var pendingDailyRelapseCount by remember { mutableStateOf<Int?>(null) }
    val bottomNavIndicatorState = rememberBottomNavIndicatorState()
    val bottomNavCurrentEntry by navController.currentBackStackEntryAsState()
    val bottomNavCurrentRoute = bottomNavCurrentEntry?.destination?.route
    val latestProtectionSetupState by rememberUpdatedState(protectionSetupState)
    val startupGraphDecision = chooseStartupGraph(
        isCompleted = state.isCompleted,
        completedAccountUid = state.completedAccountUid,
        authenticatedUid = authState.user?.uid,
        authenticatedIsGuest =
            authState.user?.provider == AuthProvider.Guest,
    )
    val mainGraphAllowed = startupGraphDecision == StartupGraphDecision.Main

    LaunchedEffect(authState.user?.uid, state.isCompleted) {
        if (state.isCompleted && authState.user != null) {
            onboardingViewModel.backfillAuthenticatedCompletionIfNeeded()
        }
    }
    LaunchedEffect(authState.accountSwitchCompleted) {
        if (authState.accountSwitchCompleted) {
            /*
             * Consume the one-shot flag before navigation so recomposition cannot
             * repeat this account-switch route.
             */
            authViewModel.consumeAccountSwitchNavigation()

            navController.navigate(
                OnboardingRoutes.LoginSignupGuest,
            ) {
                popUpTo(AppRoutes.Graph) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    fun syncInterruptionPermission() {
        protectionSetupViewModel.setInterruptionPermissionEnabled(
            Settings.canDrawOverlays(context),
        )
    }

    fun safelyStartSettingsActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            false
        } catch (exception: SecurityException) {
            false
        }
    }

    fun openInterruptionPermissionSettings() {
        when (overlayPermissionSettingsNavigator.open()) {
            OverlayPermissionSettingsLaunchResult.OverlaySettingsOpened -> {
                Toast.makeText(
                    context,
                    "Find Impulsive and allow Display over other apps.",
                    Toast.LENGTH_LONG,
                ).show()
            }

            OverlayPermissionSettingsLaunchResult.AppDetailsOpened -> {
                Toast.makeText(
                    context,
                    "Open Display over other apps for Impulsive.",
                    Toast.LENGTH_LONG,
                ).show()
            }

            OverlayPermissionSettingsLaunchResult.Failed -> {
                Toast.makeText(
                    context,
                    "Could not open Display over other apps settings. Open Android Settings > Apps > Special access > Display over other apps.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun syncBackgroundActivityPermission() {
        protectionSetupViewModel.setBackgroundActivityEnabled(
            backgroundProtectionSettingsNavigator.isAllowed(),
        )
    }

    fun showBackgroundProtectionLaunchResult(result: BackgroundProtectionSettingsLaunchResult) {
        val message = when (result) {
            BackgroundProtectionSettingsLaunchResult.AppDetailsOpened ->
                "Open Battery to review or change background protection for Impulsive."
            BackgroundProtectionSettingsLaunchResult.OptimizationListOpened ->
                "Find Impulsive to review its battery optimisation setting."
            BackgroundProtectionSettingsLaunchResult.Failed ->
                "Could not open Impulsive settings. Open Android Settings > Apps > Impulsive > Battery."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun openBackgroundActivityPermissionSettings() {
        val result = backgroundProtectionSettingsNavigator.open()
        syncBackgroundActivityPermission()
        showBackgroundProtectionLaunchResult(result)
    }

    fun openUsageAccessPermissionSettings() {
        when (usageAccessChecker.openUsageAccessSettings()) {
            UsageAccessSettingsLaunchResult.PackageHintOpened,
            UsageAccessSettingsLaunchResult.GeneralListOpened -> {
                Toast.makeText(
                    context,
                    "Find Impulsive and turn on Usage Access.",
                    Toast.LENGTH_LONG,
                ).show()
            }

            UsageAccessSettingsLaunchResult.Failed -> {
                Toast.makeText(
                    context,
                    "Could not open Usage Access settings. Open Android Settings and search for Usage Access.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun syncNotificationPermission() {
        val status = protectionNotificationHelper.interruptionNotificationStatus()
        protectionSetupViewModel.setNotificationPermissionEnabled(
            status == InterruptionNotificationStatus.Available,
        )
    }

    fun openAppNotificationSettings(): Boolean {
        val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

        if (safelyStartSettingsActivity(notificationSettingsIntent)) {
            return true
        }

        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

        if (safelyStartSettingsActivity(appDetailsIntent)) {
            Toast.makeText(
                context,
                "Open Notifications for Impulsive.",
                Toast.LENGTH_LONG,
            ).show()
            return true
        }

        Toast.makeText(
            context,
            "Could not open notification settings. Open Android Settings > Apps > Impulsive > Notifications.",
            Toast.LENGTH_LONG,
        ).show()
        return false
    }

    fun openInterruptionChannelSettings() {
        val channelSettingsIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, ProtectionNotificationHelper.BlockedAttemptChannelId)
        }

        if (!safelyStartSettingsActivity(channelSettingsIntent)) {
            openAppNotificationSettings()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        syncNotificationPermission()
        if (!granted) {
            // When the system suppresses the dialog after a permanent denial,
            // the result arrives instantly as not granted with no rationale.
            // Opening the notification settings page is the only remaining way
            // for the user to turn notifications on.
            val activity = context as? Activity
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            if (!canAskAgain) {
                openAppNotificationSettings()
            }
        }
    }

    fun manageProtectionNotifications() {
        when (protectionNotificationHelper.interruptionNotificationStatus()) {
            InterruptionNotificationStatus.RuntimePermissionMissing ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            InterruptionNotificationStatus.AppNotificationsDisabled -> openAppNotificationSettings()
            InterruptionNotificationStatus.ChannelDisabled,
            InterruptionNotificationStatus.ChannelNotHighPriority -> openInterruptionChannelSettings()
            InterruptionNotificationStatus.Available -> openAppNotificationSettings()
        }
    }

    fun syncProtectionSetupFromDevice(recoverService: Boolean = false) {
        val usageAccessGranted = usageAccessChecker.hasUsageAccess()
        val overlayPermissionGranted = Settings.canDrawOverlays(context)
        protectionSetupViewModel.setUsageAccessEnabled(usageAccessGranted)
        protectionSetupViewModel.setInterruptionPermissionEnabled(overlayPermissionGranted)
        syncBackgroundActivityPermission()
        syncNotificationPermission()

        if (!recoverService) return

        val setup = latestProtectionSetupState
        val shouldRecover = shouldRecoverProtectionService(
            appProtectionEnabled = setup.appProtectionMonitorEnabled,
            selectedPackages = setup.selectedBlockedAppPackageNames,
            usageAccessGranted = usageAccessGranted,
            websiteProtectionEnabled = setup.websiteProtectionEnabled,
        )
        ProtectionLog.debug(
            "Protection recovery snapshot: enabled=${setup.appProtectionMonitorEnabled}, " +
                "selected=${setup.selectedBlockedAppPackageNames.size}, " +
                "usageAccess=$usageAccessGranted, overlay=$overlayPermissionGranted, " +
                "serviceStartRequested=$shouldRecover",
        )
        if (shouldRecover) {
            ProtectionServiceController.start(
                context = context,
                origin = ProtectionServiceStartOrigin.VisibleApp,
            )
            ProtectionWatchdogScheduler.ensureScheduled(context)
        }
    }

    DisposableEffect(
        context,
        lifecycleOwner,
        usageAccessChecker,
        protectionNotificationHelper,
    ) {
        syncProtectionSetupFromDevice(recoverService = true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncProtectionSetupFromDevice(recoverService = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (state.isLoading) {
        return
    }

    // Keep the protection monitor running whenever the user has protected apps
    // configured. Without this the service only ever started on reboot, on a
    // focus session, or via a manual Settings button, so the block screen,
    // persistent notification, and one-minute timer never appeared on a normal
    // app open. Re-runs when the protected list changes. Start is idempotent.
    LaunchedEffect(
        protectionSetupState.appProtectionMonitorEnabled,
        protectionSetupState.selectedBlockedAppPackageNames,
        protectionSetupState.usageAccessEnabled,
        protectionSetupState.interruptionPermissionEnabled,
        protectionSetupState.websiteProtectionEnabled,
    ) {
        // Website protection depends on the monitor too: the DNS filter tunnel
        // is only synced from inside AppMonitorService. Gating this start on
        // blocked apps alone left website-only users with a dead filter after
        // every reboot, with no recovery on app open.
        val protectionConfigured = shouldRecoverProtectionService(
            appProtectionEnabled = protectionSetupState.appProtectionMonitorEnabled,
            selectedPackages = protectionSetupState.selectedBlockedAppPackageNames,
            usageAccessGranted = protectionSetupState.usageAccessEnabled,
            websiteProtectionEnabled = protectionSetupState.websiteProtectionEnabled,
        )
        if (protectionConfigured) {
            ProtectionServiceController.start(
                context = context,
                origin =
                    ProtectionServiceStartOrigin
                        .VisibleApp,
            )
            ProtectionWatchdogScheduler.ensureScheduled(context)
        }
    }

    LaunchedEffect(initialBlockRequest, mainGraphAllowed) {
        val request = initialBlockRequest
        if (request != null && mainGraphAllowed) {
            val targetRoute = when (request.launchTarget) {
                BlockLaunchTarget.FocusRecovery -> AppRoutes.FocusRecovery
                BlockLaunchTarget.RandomRecoveryGame ->
                    AppRoutes.randomRecoveryGame(request.sourcePackageName)
                BlockLaunchTarget.ReadingReset -> AppRoutes.ResetReadFallbackTask
                BlockLaunchTarget.BlockScreen -> AppRoutes.impulsiveBlock(
                    sourcePackageName = request.sourcePackageName,
                    sourceLabel = request.sourceLabel,
                )
            }
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
            onBlockRequestConsumed()
        }
    }

    LaunchedEffect(initialJournalNoteId, mainGraphAllowed) {
        val noteId = initialJournalNoteId
        if (noteId != null && noteId > 0L && mainGraphAllowed) {
            navController.navigate(AppRoutes.journalNoteEdit(noteId)) {
                launchSingleTop = true
            }
            onJournalNoteConsumed()
        }
    }

    LaunchedEffect(pendingDailyRelapseCount, state.answers.dailyRelapseUrgeCount) {
        val pendingCount = pendingDailyRelapseCount
        if (pendingCount != null && state.answers.dailyRelapseUrgeCount == pendingCount) {
            pendingDailyRelapseCount = null
            navController.navigateOnboarding(OnboardingRoutes.ProtectionSetup)
        }
    }

    fun navigateBackOnboardingSafely() {
        val currentEntry = navController.currentBackStackEntry ?: return
        val currentRoute = currentEntry.destination.route
        if (currentRoute == OnboardingRoutes.LoginSignupGuest) return
        val isResumed = currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (!isResumed) return
        val popped = navController.popBackStack()
        if (!popped) {
            navController.navigate(OnboardingRoutes.LoginSignupGuest) {
                launchSingleTop = true
            }
        }
    }

    // When the app was launched by a block, start the main graph directly on the
    // pause screen (or the focus recovery screen) so the dark home background does
    // not flash before the navigation lands on it. Computed once after loading.
    val mainStartDestination = remember(mainGraphAllowed) {
        val request = initialBlockRequest
        when {
            !mainGraphAllowed -> AppRoutes.Home
            request == null -> AppRoutes.Home
            request.launchTarget == BlockLaunchTarget.FocusRecovery -> AppRoutes.FocusRecovery
            request.launchTarget == BlockLaunchTarget.RandomRecoveryGame ->
                AppRoutes.randomRecoveryGame(request.sourcePackageName)
            request.launchTarget == BlockLaunchTarget.ReadingReset -> AppRoutes.ResetReadFallbackTask
            else -> AppRoutes.impulsiveBlock(
                sourcePackageName = request.sourcePackageName,
                sourceLabel = request.sourceLabel,
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (mainGraphAllowed) AppRoutes.Graph else OnboardingRoutes.Graph,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        navigation(
            route = OnboardingRoutes.Graph,
            startDestination = OnboardingRoutes.LogoIntro,
        ) {
            composable(OnboardingRoutes.LogoIntro) {
                IntroScreen(
                    onIntroFinished = {
                        navController.navigate(OnboardingRoutes.LoginSignupGuest) {
                            popUpTo(OnboardingRoutes.LogoIntro) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(OnboardingRoutes.LoginSignupGuest) {
                BackHandler { }

                var pendingAccountDecision by remember {
                    mutableStateOf<AuthenticatedOnboardingNavigationDecision?>(null)
                }

                fun navigateHomeAfterAccountRestore() {
                    onboardingViewModel.restoreAccountDataForAuthenticatedUser {
                        navController.navigateToMainClearingOnboarding()
                    }
                }

                fun resolveOnboardingForAuthenticatedAccount() {
                    pendingAccountDecision = null
                    onboardingViewModel.clearAccountRestoreState()
                    onboardingViewModel.resolveAuthenticatedOnboarding { resolution ->
                        when (
                            val decision = authenticatedOnboardingNavigationDecision(
                                resolution = resolution,
                                localOnboardingCompleted = state.isCompleted,
                            )
                        ) {
                            AuthenticatedOnboardingNavigationDecision.RestoreBeforeHome -> {
                                navigateHomeAfterAccountRestore()
                            }

                            AuthenticatedOnboardingNavigationDecision.OpenHome -> {
                                navController.navigateToMainClearingOnboarding()
                            }

                            AuthenticatedOnboardingNavigationDecision.StartSetup -> {
                                navController.navigateOnboarding(OnboardingRoutes.WelcomePrivacy)
                            }

                            AuthenticatedOnboardingNavigationDecision.ShowRemoteCompletedWithoutLocalData,
                            AuthenticatedOnboardingNavigationDecision.ShowAccountMismatch,
                            AuthenticatedOnboardingNavigationDecision.ShowLegacyUnownedLocalData,
                            AuthenticatedOnboardingNavigationDecision.ShowRestoredSameGoogleIdentityConfirmation,
                            AuthenticatedOnboardingNavigationDecision.ShowRestoredLegacyDriveVerification,
                            -> {
                                pendingAccountDecision = decision
                            }

                            AuthenticatedOnboardingNavigationDecision.AwaitRetry -> Unit
                        }
                    }
                }
                val accountResolutionFailure =
                    onboardingAccountResolutionState as? OnboardingAccountResolutionState.RetryableFailure

                val suppressUnusableLocalDataDialog =
                    accountLocalDataResetState !is
                        AccountLocalDataResetState.Idle

                LoginSignupGuestScreen(
                    onAuthenticated = ::resolveOnboardingForAuthenticatedAccount,
                    authViewModel = authViewModel,
                    accountSetupLoading =
                        onboardingAccountResolutionState is OnboardingAccountResolutionState.Loading ||
                            accountRestoreState is AccountRestoreState.Restoring,
                    accountSetupMessage = accountResolutionFailure?.message,
                    onRetryAccountSetup = ::resolveOnboardingForAuthenticatedAccount,
                    onDismissAccountSetupMessage = onboardingViewModel::clearAccountResolutionFailure,
                )

                AccountRestoreDialog(
                    state = accountRestoreState,
                    onRetry = ::navigateHomeAfterAccountRestore,
                    onUseAnotherAccount = {
                        onboardingViewModel.cancelEraseUnusableLocalData()
                        onboardingViewModel.clearAccountRestoreState()
                        authViewModel.signOut()
                        navController.navigateOnboarding(
                            OnboardingRoutes.LoginSignupGuest,
                        )
                    },
                    onEraseSavedData =
                        onboardingViewModel::requestEraseUnusableLocalData,
                    suppressUnusableLocalDataDialog =
                        suppressUnusableLocalDataDialog,
                    onDismissRetryable =
                        onboardingViewModel::clearAccountRestoreState,
                )


                AuthenticatedOnboardingDecisionDialog(
                    decision = pendingAccountDecision,
                    onTryAgain = ::resolveOnboardingForAuthenticatedAccount,
                    onSetUpAgain = {
                        pendingAccountDecision = null
                        onboardingViewModel.clearAnswers {
                            navController.navigateOnboarding(
                                OnboardingRoutes.WelcomePrivacy,
                            )
                        }
                    },
                    onUseAnotherAccount = {
                        onboardingViewModel.cancelEraseUnusableLocalData()
                        pendingAccountDecision = null
                        authViewModel.signOut()
                        navController.navigateOnboarding(
                            OnboardingRoutes.LoginSignupGuest,
                        )
                    },
                    onEraseSavedData =
                        onboardingViewModel::requestEraseUnusableLocalData,
                    suppressUnusableLocalDataDialog =
                        suppressUnusableLocalDataDialog,
                )

                AccountLocalDataResetDialog(
                    state = accountLocalDataResetState,
                    onConfirm =
                        onboardingViewModel::confirmEraseUnusableLocalData,
                    onRetry =
                        onboardingViewModel::confirmEraseUnusableLocalData,
                    onCancel =
                        onboardingViewModel::cancelEraseUnusableLocalData,
                )

            }
            composable(OnboardingRoutes.WelcomePrivacy) {
                BackHandler { navigateBackOnboardingSafely() }
                WelcomePrivacyScreen(
                    initialName = state.answers.name,
                    initialAvatarId = state.answers.avatarId,
                    onBeginSetup = { name, avatarId ->
                        onboardingViewModel.savePersonalization(
                            name = name,
                            avatarId = avatarId,
                        ) {
                            navController.navigateOnboarding(OnboardingRoutes.QuestionInterrupting)
                        }
                    },
                )
            }

            composable(OnboardingRoutes.QuestionInterrupting) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Interrupting,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionTriggers)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionTriggers) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Triggers,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionTiming)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionTiming) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Timing,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionWeekOne)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionWeekOne) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.WeekOneGoal,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionDailyRelapseCount)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionDailyRelapseCount) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingDailyRelapseCountScreen(
                    state = state,
                    initialCount = state.answers.dailyRelapseUrgeCount,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = { selectedCount ->
                        onboardingViewModel.setDailyRelapseUrgeCount(selectedCount)
                        pendingDailyRelapseCount = selectedCount
                    },
                )
            }

            composable(OnboardingRoutes.ProtectionSetup) {
                BackHandler { navigateBackOnboardingSafely() }
                ProtectionSetupOnboardingScreen(
                    state = protectionSetupState,
                    onBack = { navigateBackOnboardingSafely() },
                    onChooseApps = {
                        navController.navigateOnboarding(OnboardingRoutes.ProtectionBlockedApps)
                    },
                    onOpenUsageAccessPermission = ::openUsageAccessPermissionSettings,
                    onOpenInterruptionPermission = ::openInterruptionPermissionSettings,
                    onOpenBackgroundActivityPermission = ::openBackgroundActivityPermissionSettings,
                    onOpenNotificationPermission = ::manageProtectionNotifications,
                    onSkipItem = protectionSetupViewModel::markSkipped,
                    onContinue = {
                        protectionSetupState.incompleteCoreProtectionItems.forEach { item ->
                            protectionSetupViewModel.markSkipped(item)
                        }
                        navController.navigateOnboarding(OnboardingRoutes.StartingPoint)
                    },
                )
            }

            composable(OnboardingRoutes.ProtectionBlockedApps) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged =
                        protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { navController.safePopBackStack() },
                    allowShowMoreApps = true,
                    seedRecommendedBrowsers = true,
                )
            }

            composable(OnboardingRoutes.StartingPoint) {
                BackHandler {
                    navigateBackOnboardingSafely()
                }

                OnboardingStartingPointScreen(
                    state = state,
                    onBack = {
                        navigateBackOnboardingSafely()
                    },
                    onContinue = {
                        navController.navigateOnboarding(
                            OnboardingRoutes.PersonalisingSetup,
                        )
                    },
                )
            }

            composable(OnboardingRoutes.PersonalisingSetup) {
                BackHandler(enabled = true) { }

                PersonalisingSetupScreen(
                    onFinished = {
                        onboardingViewModel.completeOnboarding {
                            navController.navigateToMainClearingOnboarding()
                        }
                    },
                )

                val failure = onboardingCompletionState as? OnboardingCompletionState.RetryableFailure
                val saving = onboardingCompletionState is OnboardingCompletionState.Saving
                if (failure != null || saving) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = {
                            Text(if (saving) "Saving setup" else "Setup not saved")
                        },
                        text = {
                            Text(
                                if (saving) {
                                    "Saving your account setup."
                                } else {
                                    failure?.message.orEmpty()
                                },
                            )
                        },
                        confirmButton = {
                            if (failure != null) {
                                TextButton(
                                    onClick = {
                                        onboardingViewModel.completeOnboarding {
                                            navController.navigateToMainClearingOnboarding()
                                        }
                                    },
                                ) {
                                    Text("Try again")
                                }
                            }
                        },
                    )
                }
            }
        }

        navigation(
            route = AppRoutes.Graph,
            startDestination = mainStartDestination,
        ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onOpenRecoveryGames = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.RecoveryGames)
                    },
                    onOpenJournal = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.JournalList)
                    },
                    onOpenReflexOverrideTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenTasks = dropUnlessResumed {
                        navController.navigate(AppRoutes.TaskToComplete)
                    },
                    onOpenScore = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenSettings = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenWebsiteProtectionPlus = dropUnlessResumed {
                        navController.navigate(AppRoutes.WebsiteProtectionPlus) {
                            launchSingleTop = true
                        }
                    },
                    onOpenFocus = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Home,
                )
            }

            composable(AppRoutes.Focus) {
                FocusScreen(
                    onOpenHome = {
                        navController.navigateMainTop(AppRoutes.Home)
                    },
                    onOpenScore = {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenSettings = {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenTasks = {
                        navController.navigate(AppRoutes.TaskToComplete)
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Focus,
                )
            }

            composable(AppRoutes.FocusRecovery) {
                FocusRecoveryScreen(
                    onReturnToFocus = {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    onEndedCalmly = {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                )
            }

            composable(
                route = AppRoutes.RandomRecoveryGame,
                arguments = listOf(
                    navArgument("sourcePackageName") {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val sourcePackageName =
                    Uri.decode(
                        backStackEntry.arguments
                            ?.getString("sourcePackageName")
                            .orEmpty(),
                    )

                LaunchedEffect(sourcePackageName) {
                    if (sourcePackageName.isBlank()) {
                        navController.navigateBackToHome()
                        return@LaunchedEffect
                    }

                    try {
                        val chosenGame = selectAndRecordGuidedGame(
                            context = context,
                            sourcePackageName = sourcePackageName,
                        )
                        navController.navigate(recoveryGameRoute(chosenGame, asTask = true)) {
                            launchSingleTop = true
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        ProtectionLog.error(
                            "Protection recovery game selection failed " +
                                "(exception=${error.javaClass.simpleName})",
                        )
                        navController.navigateBackToHome()
                    }
                }
            }

            composable(AppRoutes.Score) {
                ProgressDashboardScreen(
                    onOpenHome = {
                        navController.navigateBackToHome()
                    },
                    onOpenSettings = {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenFocus = {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    onOpenReflexOverrideTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Score,
                )
            }

            composable(AppRoutes.Settings) {
                SettingsScreen(
                    authViewModel = authViewModel,
                    protectionSetupViewModel = protectionSetupViewModel,
                    billingRestoreState = billingRestoreState,
                    onRestorePurchases = {
                        billingManager.restorePurchases()
                    },
                    subscriptionCatalogState = subscriptionCatalogState,
                    onRetryBilling = {
                        billingManager.refreshProductDetails()
                    },
                    onOpenScore = {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenFocus = {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    onOpenReflexOverrideTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenHelp = {
                        navController.navigate(AppRoutes.HelpFaq) {
                            launchSingleTop = true
                        }
                    },
                    onOpenWebsiteProtectionPlus = {
                        navController.navigate(AppRoutes.WebsiteProtectionPlus) {
                            launchSingleTop = true
                        }
                    },
                    onOpenProtectionSetupGuide = {
                        navController.navigate(AppRoutes.ProtectionSetupGuide) {
                            launchSingleTop = true
                        }
                    },
                    onOpenUsageAccessPermission = ::openUsageAccessPermissionSettings,
                    onOpenInterruptionPermission = ::openInterruptionPermissionSettings,
                    onOpenBackgroundActivityPermission = ::openBackgroundActivityPermissionSettings,
                    onManageProtectionNotifications = ::manageProtectionNotifications,
                    onBackHome = {
                        val isResumed = navController.currentBackStackEntry
                            ?.lifecycle
                            ?.currentState
                            ?.isAtLeast(Lifecycle.State.RESUMED) == true
                        if (isResumed) {
                            navController.navigateBackToHome()
                        }
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Settings,
                )
            }

            composable(AppRoutes.ProtectionSetupGuide) {
                ProtectionSetupOnboardingScreen(
                    state = protectionSetupState,
                    onBack = { navController.safePopBackStack() },
                    onChooseApps = {
                        navController.navigate(AppRoutes.ProtectionSetupGuideBlockedApps) {
                            launchSingleTop = true
                        }
                    },
                    onOpenUsageAccessPermission = ::openUsageAccessPermissionSettings,
                    onOpenInterruptionPermission = ::openInterruptionPermissionSettings,
                    onOpenBackgroundActivityPermission = ::openBackgroundActivityPermissionSettings,
                    onOpenNotificationPermission = ::manageProtectionNotifications,
                    onSkipItem = protectionSetupViewModel::markSkipped,
                    onContinue = { navController.safePopBackStack() },
                    showMonitorToggle = true,
                    onMonitorEnabledChange = protectionSetupViewModel::setAppProtectionMonitorEnabled,
                )
            }

            composable(AppRoutes.ProtectionSetupGuideBlockedApps) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged = protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { navController.safePopBackStack() },
                    allowShowMoreApps = true,
                    seedRecommendedBrowsers = true,
                )
            }

            composable(AppRoutes.HelpFaq) {
                val context = LocalContext.current

                HelpFaqScreen(
                    onBack = {
                        navController.safePopBackStack()
                    },
                    onContactSupport = {
                        sendSupportEmail(
                            context = context,
                            subject = "Impulsive support",
                        )
                    },
                    onReportBug = {
                        sendSupportEmail(
                            context = context,
                            subject = "Impulsive bug report",
                            body = "\n\n---\n" +
                                "App version: ${appVersionName(context)}\n" +
                                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                                "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
                        )
                    },
                )
            }

            composable(AppRoutes.WebsiteProtectionPlus) {
                val premiumViewModel: PremiumViewModel = viewModel()
                val taskRewardViewModel: TaskRewardViewModel = viewModel()
                val isPlus by premiumViewModel
                    .hasFeature(PremiumFeature.VpnWebsiteBlocker)
                    .collectAsStateWithLifecycle()
                val premiumEntitlement by premiumViewModel.entitlement
                    .collectAsStateWithLifecycle()
                val taskStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
                val now by produceState(initialValue = LocalDateTime.now().withSecond(0).withNano(0)) {
                    while (true) {
                        value = LocalDateTime.now().withSecond(0).withNano(0)
                        delay(30_000L)
                    }
                }
                val releasePlan = calculateReleasePlan(
                    selectedDailyUrgeCount = state.answers.dailyRelapseUrgeCount,
                    now = now,
                    activeDayStart = minuteOfDayToLocalTime(state.answers.activeDayStartMinute),
                    activeDayEnd = minuteOfDayToLocalTime(state.answers.activeDayEndMinute),
                )
                val windowSnapshot = ProtectionWindowEvaluator.evaluate(
                    now = now,
                    releasePlan = releasePlan,
                    adjustedNextReleaseWindow = taskStoreState.adjustedNextReleaseWindow,
                )
                val context = LocalContext.current
                val activeSubscriptionProductId = activePlaySubscriptionProductId(
                    entitlement = premiumEntitlement,
                    nowMillis = System.currentTimeMillis(),
                )
                val purchaseAccountGatePhase = resolvePurchaseAccountGatePhase(
                    user = authState.user,
                    inFlightProvider = authState.inFlightProvider,
                    pendingEmailVerificationAddress =
                        authState.pendingEmailVerificationAddress,
                    hasAccountConflict = authState.pendingAccountConflict != null,
                )
                WebsiteProtectionPlusScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenDnsFilterCheck = {
                        navController.navigate(AppRoutes.DnsFilterGate) {
                            launchSingleTop = true
                        }
                    },
                    onChooseWebsiteProtectionApps = {
                        navController.navigate(AppRoutes.WebsiteProtectionApps) {
                            launchSingleTop = true
                        }
                    },
                    isPlus = isPlus,
                    subscriptionCatalogState = subscriptionCatalogState,
                    billingUiState = billingUiState,
                    purchaseAccountGatePhase = purchaseAccountGatePhase,
                    pendingAccountConflict = authState.pendingAccountConflict,
                    authErrorMessage = authState.errorMessage,
                    onLinkGoogleForPurchase = {
                        (context as? Activity)?.let { authViewModel.linkGoogleAccount(it) }
                    },
                    onLinkFacebookForPurchase = {
                        (context as? Activity)?.let { authViewModel.linkFacebookAccount(it) }
                    },
                    onLinkEmailForPurchase = { email, password ->
                        authViewModel.linkEmailAccount(
                            email = email,
                            password = password,
                        )
                    },
                    onConfirmAccountSwitchForPurchase = {
                        authViewModel.confirmAccountSwitchForPurchase()
                    },
                    onDismissAccountSwitch = {
                        authViewModel.dismissAccountSwitch()
                    },
                    onDismissAuthError = {
                        authViewModel.consumeError()
                    },
                    onRetryBilling = {
                        billingManager.refreshProductDetails()
                    },
                    onPurchase = { period ->
                        (context as? Activity)?.let { billingManager.launchPurchase(it, period) }
                    },
                    canManageSubscription = activeSubscriptionProductId != null,
                    onManageSubscription = {
                        if (activeSubscriptionProductId != null) {
                            val opened = openGooglePlaySubscriptionManagement(
                                context = context,
                                productId = activeSubscriptionProductId,
                            )

                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    "Google Play subscriptions could not be opened.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    billingRestoreState = billingRestoreState,
                    onRestorePurchases = {
                        billingManager.restorePurchases()
                    },
                    isWebsiteProtectionEnabled = protectionSetupState.websiteProtectionEnabled,
                    isWebsiteProtectionAlwaysOn = protectionSetupState.websiteProtectionAlwaysOn,
                    isReleaseWindowActive = windowSnapshot.isProtectionPaused,
                    releaseWindowEndsAt = windowSnapshot.pausedWindowEnd?.toImpulsiveCompactTime(),
                    onTurnWebsiteProtectionOff = {
                        protectionSetupViewModel.setWebsiteProtectionEnabled(false)
                    },
                    onAlwaysOnChanged = protectionSetupViewModel::setWebsiteProtectionAlwaysOn,
                )
            }

            composable(AppRoutes.WebsiteProtectionApps) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.websiteProtectedAppPackageNames,
                    onSelectedPackageNamesChanged =
                        protectionSetupViewModel::setWebsiteProtectedAppPackageNames,
                    onDone = { navController.safePopBackStack() },
                    allowShowMoreApps = true,
                    seedRecommendedBrowsers = true,
                    titleOverride = "Choose apps for Website Protection",
                    subtitleOverride =
                        "Only selected apps use Website Protection. Other apps use your normal internet connection.",
                )
            }

            composable(AppRoutes.DnsFilterGate) {
                val dnsFilterGateViewModel: DnsFilterGateViewModel = viewModel()
                val dnsFilterGateState by dnsFilterGateViewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val vpnConsentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        ImpulsiveVpnController.start(context)
                        protectionSetupViewModel.setWebsiteProtectionEnabled(true)
                        navController.safePopBackStack()
                    }
                }
                DnsFilterGateScreen(
                    state = dnsFilterGateState,
                    onOpenPrivateDnsSettings = {
                        context.startActivity(dnsFilterGateViewModel.privateDnsSettingsIntent())
                    },
                    onRefresh = { dnsFilterGateViewModel.refresh() },
                    onContinue = {
                        val consent = ImpulsiveVpnController.consentIntent(context)
                        if (consent != null) {
                            vpnConsentLauncher.launch(consent)
                        } else {
                            ImpulsiveVpnController.start(context)
                            protectionSetupViewModel.setWebsiteProtectionEnabled(true)
                            navController.safePopBackStack()
                        }
                    },
                    onTurnOff = {
                        protectionSetupViewModel.setWebsiteProtectionEnabled(false)
                        navController.safePopBackStack()
                    },
                    onBack = { navController.safePopBackStack() },
                )
            }
            }

            composable(AppRoutes.RecoveryGames) {
                RecoveryGamesScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenReflexOverride = dropUnlessResumed { navController.navigate(AppRoutes.ReflexGame) },
                    onOpenBlockCascade = dropUnlessResumed { navController.navigate(AppRoutes.BlockCascadeGame) },
                    onOpenSkylineReset = dropUnlessResumed { navController.navigate(AppRoutes.SkylineResetGame) },
                    onOpenRhythmTiles = dropUnlessResumed { navController.navigate(AppRoutes.RhythmTilesGame) },
                )
            }

            composable(AppRoutes.ReflexGame) {
                ReflexGameScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
                        asTask = false,
                    ),
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.ReflexGameTask) {
                ReflexGameScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.BlockCascadeGame) {
                BlockCascadeScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade,
                        asTask = false,
                    ),
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.BlockCascadeTask) {
                BlockCascadeScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.SkylineResetGame) {
                SkylineResetScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset,
                        asTask = false,
                    ),
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.SkylineResetTask) {
                SkylineResetScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.RhythmTilesGame) {
                RhythmTilesScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles,
                        asTask = false,
                    ),
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.RhythmTilesTask) {
                RhythmTilesScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.ResetReadTask) {
                ResetReadScreen(
                    launchMode = ResetReadLaunchMode.Normal,
                    onExit = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.ResetReadFallbackTask) {
                ResetReadScreen(
                    launchMode = ResetReadLaunchMode.Fallback,
                    onExit = { navController.exitRecoveryFlowSafely() },
                )
            }

            composable(AppRoutes.JournalHub) {
                JournalHubScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenNormalJournal = { navController.navigate(AppRoutes.JournalList) },
                    onCreateNote = { type -> navController.navigate(AppRoutes.journalNoteNew(type)) },
                    onOpenNote = { noteId -> navController.navigate(AppRoutes.journalNoteEdit(noteId)) },
                )
            }

            composable(AppRoutes.JournalList) {
                JournalListScreen(
                    onBack = { navController.safePopBackStack() },
                    onCreateNote = { type -> navController.navigate(AppRoutes.journalNoteNew(type)) },
                    onOpenNote = { noteId -> navController.navigate(AppRoutes.journalNoteEdit(noteId)) },
                    onOpenSavedNotifications = {
                        navController.navigate(
                            AppRoutes.SavedNotifications,
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                AppRoutes.SavedNotifications,
            ) {
                SavedNotificationsScreen(
                    onBack = {
                        navController
                            .safePopBackStack()
                    },
                )
            }

            composable(
                route = AppRoutes.JournalNoteNew,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { backStackEntry ->
                val noteType = JournalNoteType.fromStorage(backStackEntry.arguments?.getString("type").orEmpty())
                JournalEditorScreen(
                    noteId = 0L,
                    initialType = noteType,
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(
                route = AppRoutes.JournalNoteEdit,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { backStackEntry ->
                JournalEditorScreen(
                    noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L,
                    initialType = JournalNoteType.Text,
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(
                route = AppRoutes.ImpulsiveBlock,
                arguments = listOf(
                    navArgument("sourcePackageName") { type = NavType.StringType },
                    navArgument("sourceLabel") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val sourcePackageName =
                    Uri.decode(backStackEntry.arguments?.getString("sourcePackageName").orEmpty())
                val sourceLabel =
                    Uri.decode(backStackEntry.arguments?.getString("sourceLabel").orEmpty())
                val appLockDataSource = remember(context) {
                    com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource(context)
                }
                val appLockEnabled by appLockDataSource.enabled.collectAsStateWithLifecycle(initialValue = false)
                val appSettingsDataSource = remember(context) {
                    com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource(context)
                }
                val hideSensitive by appSettingsDataSource.hideSensitiveNotifications
                    .collectAsStateWithLifecycle(initialValue = false)
                val urgeEventScope = rememberCoroutineScope()
                val urgeEventRepository = remember(context) {
                    com.impulsive.app.backend.data.repository.UrgeEventRepository(context)
                }
                val blockGuard = rememberAppLockGuardController()
                val oneMinuteAccessDataSource = remember {
                    com.impulsive.app.backend.data.local.preferences.OneMinuteAccessDataSource(context)
                }
                val oneMinuteAccessState by oneMinuteAccessDataSource.state
                    .collectAsStateWithLifecycle(
                        initialValue = com.impulsive.app.backend.data.local.preferences.OneMinuteAccessState(),
                    )
                val oneMinuteAccessAvailable = oneMinuteAccessState.enabled &&
                    !oneMinuteAccessState.isOnCooldown(
                        sourcePackageName,
                        System.currentTimeMillis(),
                        com.impulsive.app.backend.data.local.preferences.OneMinuteAccessDataSource.OneMinuteAccessCooldownMillis,
                    )
                BackHandler {
                    val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        addCategory(android.content.Intent.CATEGORY_HOME)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                }
                ImpulsiveBlockScreen(
                    sourcePackageName = if (hideSensitive) "a protected app" else sourcePackageName,
                    sourceLabel = if (hideSensitive) "a protected app" else sourceLabel.ifBlank { sourcePackageName },
                    onStartControlTask = {
                        blockGuard.run(appLockEnabled) {
                            urgeEventScope.launch {
                                val chosenGame = selectAndRecordGuidedGame(
                                    context = context,
                                    sourcePackageName = sourcePackageName,
                                )
                                navController.navigate(recoveryGameRoute(chosenGame, asTask = true)) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onStartReadingTask = {
                        blockGuard.run(appLockEnabled) {
                            urgeEventScope.launch {
                                urgeEventRepository.recordEvent(source = "support_reading_task", packageName = sourcePackageName)
                            }
                            navController.navigate(AppRoutes.ResetReadFallbackTask) { launchSingleTop = true }
                        }
                    },
                    onReturnHome = {
                        blockGuard.run(appLockEnabled) {
                            navController.navigateBackToHome()
                        }
                    },
                    oneMinuteAccessAvailable = oneMinuteAccessAvailable,
                    onOpenForOneMinute = {
                        blockGuard.run(appLockEnabled) {
                            urgeEventScope.launch {
                                oneMinuteAccessDataSource.grant(
                                    sourcePackageName,
                                    System.currentTimeMillis(),
                                )
                                val launchIntent = context.packageManager
                                    .getLaunchIntentForPackage(sourcePackageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                        }
                    },
                )
                AppLockGuardHost(
                    controller = blockGuard,
                    title = "Confirm it's you",
                    subtitle = "Authenticate to continue.",
                )
            }

            composable(AppRoutes.TaskToComplete) {
                TaskToCompleteScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenReflexOverrideTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                )
            }
        }
    }

@Composable
private fun CloudRestoreDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val coordinator =
        remember(
            context,
        ) {
            CloudRecoveryRestoreCoordinator(
                context,
            )
        }

    val authorization =
        remember(
            context,
        ) {
            DriveAppDataAuthorization(
                context,
            )
        }

    var envelope by
        remember {
            mutableStateOf<ByteArray?>(
                null,
            )
        }

    var password by
        remember {
            mutableStateOf(
                "",
            )
        }

    var requiresReplacement by
        remember {
            mutableStateOf(
                false,
            )
        }

    var showPassword by
        remember {
            mutableStateOf(
                false,
            )
        }

    var showOwnerMigrationConfirmation by
        remember {
            mutableStateOf(false)
        }

    var ownerMigrationConfirmed by
        remember {
            mutableStateOf(false)
        }

    var showReplace by
        remember {
            mutableStateOf(
                false,
            )
        }

    var message by
        remember {
            mutableStateOf<String?>(
                null,
            )
        }

    fun clearEnvelope() {
        envelope?.fill(
            0,
        )

        envelope =
            null
    }

    fun restore(
        replace:
            Boolean,
        ownerMigrationConfirmed:
            Boolean = false,
    ) {
        val bytes =
            envelope
                ?: return

        val passwordChars =
            password.toCharArray()

        password =
            ""

        scope.launch {
            when (
                coordinator.restore(
                    downloadedEnvelope =
                        bytes,

                    password =
                        passwordChars,

                    replaceExistingData =
                        replace,

                    ownerMigrationConfirmed =
                        ownerMigrationConfirmed,
                )
            ) {
                CloudRecoveryRestoreResult.Success -> {
                    clearEnvelope()
                    onSuccess()
                }

                CloudRecoveryRestoreResult.SuccessBackupRefreshPending -> {
                    clearEnvelope()

                    Toast.makeText(
                        context,
                        "Your data was restored and Google Drive recovery backup is on. " +
                            "A backup refresh will be requested again when your data changes.",
                        Toast.LENGTH_LONG,
                    ).show()

                    onSuccess()
                }
                CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed -> {
                    clearEnvelope()

                    Toast.makeText(
                        context,
                        "Your recovery data was restored, but automatic " +
                            "Google Drive backup could not be re-enabled. " +
                            "Turn it on again in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()

                    onSuccess()
                }

                CloudRecoveryRestoreResult.IncorrectPassword -> {
                    message =
                        "Incorrect recovery password."
                }

                CloudRecoveryRestoreResult.AccountMismatch -> {
                    message =
                        "This recovery backup belongs to a different " +
                            "Impulsive account."
                }

                is CloudRecoveryRestoreResult.OwnerMigrationConfirmationRequired -> {
                    showOwnerMigrationConfirmation = true
                }

                CloudRecoveryRestoreResult.ReplacementConfirmationRequired -> {
                    showReplace =
                        true
                }

                CloudRecoveryRestoreResult.InvalidBackup -> {
                    message =
                        "This Google Drive recovery backup is not valid."
                }

                CloudRecoveryRestoreResult.ImportFailed -> {
                    message =
                        "Could not import your recovery data. Your existing " +
                            "local data was left unchanged."
                }

                CloudRecoveryRestoreResult.NotSignedIn,
                CloudRecoveryRestoreResult.GuestNotSupported -> {
                    message =
                        "Sign in with the account that created this recovery backup."
                }
            }
        }
    }

    fun discover(
        accessToken:
            String,
    ) {
        scope.launch {
            when (
                val result =
                    coordinator.discover(
                        accessToken,
                    )
            ) {
                is CloudRecoveryRestoreDiscovery.Downloaded -> {
                    clearEnvelope()

                    envelope =
                        result.bytes

                    requiresReplacement =
                        result
                            .requiresReplacementConfirmation

                    showPassword =
                        true
                }

                CloudRecoveryRestoreDiscovery.NoBackupFound -> {
                    message =
                        "No Google Drive recovery backup was found for Impulsive."
                }

                CloudRecoveryRestoreDiscovery.AuthorizationRequired -> {
                    message =
                        "Google Drive authorization is required to access your recovery backup."
                }

                CloudRecoveryRestoreDiscovery.TemporarilyUnavailable -> {
                    message =
                        "Google Drive recovery is temporarily unavailable. " +
                            "Check your connection and try again."
                }

                CloudRecoveryRestoreDiscovery.InvalidBackup -> {
                    message =
                        "The Google Drive recovery backup is not valid."
                }

                CloudRecoveryRestoreDiscovery.NotSignedIn,
                CloudRecoveryRestoreDiscovery.GuestNotSupported,
                CloudRecoveryRestoreDiscovery.Failed -> {
                    message =
                        "Could not access Google Drive recovery."
                }
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartIntentSenderForResult(),
        ) { result ->
            val data =
                result.data

            if (
                data == null
            ) {
                message =
                    "Google Drive recovery backup was not authorized."
            } else {
                when (
                    val authorizationResult =
                        authorization.resultFromIntent(
                            data,
                        )
                ) {
                    is DriveAuthorizationResult.Authorized -> {
                        discover(
                            authorizationResult.accessToken,
                        )
                    }

                    else -> {
                        message =
                            "Google Drive recovery backup was not authorized."
                    }
                }
            }
        }

    AlertDialog(
        onDismissRequest = {
            password =
                ""

            clearEnvelope()

            onDismiss()
        },

        title = {
            Text(
                "Restore from Google Drive",
            )
        },

        text = {
            Text(
                "Restore an encrypted recovery backup from your private " +
                    "Google Drive app data.",
            )
        },

        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        when (
                            val auth =
                                authorization
                                    .requestAuthorization()
                        ) {
                            is DriveAuthorizationResult.Authorized -> {
                                discover(
                                    auth.accessToken,
                                )
                            }

                            is DriveAuthorizationResult.NeedsUserResolution -> {
                                launcher.launch(
                                    IntentSenderRequest
                                        .Builder(
                                            auth
                                                .pendingIntent
                                                .intentSender,
                                        )
                                        .build(),
                                )
                            }

                            else -> {
                                message =
                                    "Google Drive recovery backup was not authorized."
                            }
                        }
                    }
                },
            ) {
                Text(
                    "Continue",
                )
            }
        },

        dismissButton = {
            TextButton(
                onClick = {
                    password =
                        ""

                    clearEnvelope()

                    onDismiss()
                },
            ) {
                Text(
                    "Cancel",
                )
            }
        },
    )

    if (
        showPassword
    ) {
        AlertDialog(
            onDismissRequest = {
                showPassword =
                    false

                password =
                    ""

                clearEnvelope()
            },

            title = {
                Text(
                    "Enter recovery password",
                )
            },

            text = {
                Column {
                    Text(
                        "Enter the password you created when you turned on " +
                            "Google Drive recovery. Impulsive does not store " +
                            "this password.",
                    )

                    OutlinedTextField(
                        value =
                            password,

                        onValueChange = {
                            password =
                                it
                        },

                        label = {
                            Text(
                                "Recovery password",
                            )
                        },

                        visualTransformation =
                            PasswordVisualTransformation(),
                    )
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showPassword =
                            false

                        if (
                            requiresReplacement
                        ) {
                            showReplace =
                                true
                        } else {
                            restore(
                                replace =
                                    false,
                                ownerMigrationConfirmed = ownerMigrationConfirmed,
                            )
                        }
                    },
                ) {
                    Text(
                        "Restore",
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showPassword =
                            false

                        password =
                            ""

                        clearEnvelope()
                    },
                ) {
                    Text(
                        "Cancel",
                    )
                }
            },
        )
    }

    if (showOwnerMigrationConfirmation) {
        AlertDialog(
            onDismissRequest = { showOwnerMigrationConfirmation = false },
            title = { Text("Restore saved data?") },
            text = { Text("The Firebase account identifier changed, but the encrypted recovery copy matches this linked Google identity. Confirm to restore it.") },
            confirmButton = {
                TextButton(onClick = {
                    showOwnerMigrationConfirmation = false
                    ownerMigrationConfirmed = true
                    showPassword = true
                }) { Text("Restore data") }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerMigrationConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    if (
        showReplace
    ) {
        AlertDialog(
            onDismissRequest = {
                showReplace =
                    false

                password =
                    ""

                clearEnvelope()
            },

            title = {
                Text(
                    "Replace local recovery data?",
                )
            },

            text = {
                Text(
                    "Restoring will replace the recovery data currently " +
                        "stored on this device with your encrypted Google " +
                        "Drive recovery copy.",
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showReplace =
                            false

                        restore(
                            replace =
                                true,
                            ownerMigrationConfirmed = ownerMigrationConfirmed,
                        )
                    },
                ) {
                    Text(
                        "Restore",
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showReplace =
                            false

                        password =
                            ""

                        clearEnvelope()
                    },
                ) {
                    Text(
                        "Cancel",
                    )
                }
            },
        )
    }

    message?.let { value ->
        AlertDialog(
            onDismissRequest = {
                message =
                    null
            },

            title = {
                Text(
                    "Google Drive recovery",
                )
            },

            text = {
                Text(
                    value,
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        message =
                            null
                    },
                ) {
                    Text(
                        "OK",
                    )
                }
            },
        )
    }
}
@Composable
private fun AccountRestoreDialog(
    state: AccountRestoreState,
    onRetry: () -> Unit,
    onUseAnotherAccount: () -> Unit,
    onEraseSavedData: () -> Unit,
    suppressUnusableLocalDataDialog: Boolean,
    onDismissRetryable: () -> Unit,
) {
    when (state) {
        AccountRestoreState.Idle -> Unit

        AccountRestoreState.Restoring -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restoring data") },
                text = {
                    Text(
                        "Restoring your saved Impulsive data.",
                    )
                },
                confirmButton = { },
            )
        }

        is AccountRestoreState.RetryableFailure -> {
            AlertDialog(
                onDismissRequest = onDismissRetryable,
                title = { Text("Restore unavailable") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text("Try again")
                    }
                },
            )
        }

        AccountRestoreState.AccountMismatch -> {
            if (!suppressUnusableLocalDataDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(
                            "Saved data belongs to another account",
                        )
                    },
                    text = {
                        Text(
                            "This saved Impulsive data belongs to a different account. You can sign in with the account that created it, or permanently erase the saved data from this device and continue with the account currently signed in.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onUseAnotherAccount,
                        ) {
                            Text("Use another account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onEraseSavedData,
                        ) {
                            Text("Erase saved data")
                        }
                    },
                )
            }
        }

        AccountRestoreState.LocalBackupUnavailable -> {
            if (!suppressUnusableLocalDataDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text("Saved data needs review")
                    },
                    text = {
                        Text(
                            "Impulsive found saved data from an older version, but it cannot safely confirm which account owns it. You can use another account or permanently erase the saved data from this device.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onUseAnotherAccount,
                        ) {
                            Text("Use another account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onEraseSavedData,
                        ) {
                            Text("Erase saved data")
                        }
                    },
                )
            }
        }
    }
}


@Composable
private fun AuthenticatedOnboardingDecisionDialog(
    decision: AuthenticatedOnboardingNavigationDecision?,
    onTryAgain: () -> Unit,
    onSetUpAgain: () -> Unit,
    onUseAnotherAccount: () -> Unit,
    onEraseSavedData: () -> Unit,
    suppressUnusableLocalDataDialog: Boolean,
) {
    when (decision) {
        null,
        AuthenticatedOnboardingNavigationDecision.OpenHome,
        AuthenticatedOnboardingNavigationDecision.RestoreBeforeHome,
        AuthenticatedOnboardingNavigationDecision.StartSetup,
        AuthenticatedOnboardingNavigationDecision.AwaitRetry,
        -> Unit

        AuthenticatedOnboardingNavigationDecision.ShowRemoteCompletedWithoutLocalData -> {
            var showCloudRestore by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Setup not on this device") },
                text = {
                    Column {
                        Text("Your account was found, but this device doesn't currently have your saved Impulsive setup. You can restore an encrypted Google Drive recovery backup, try Android backup again, or set up this device again.")
                        TextButton(onClick = onTryAgain) { Text("Try Android backup again") }
                    }
                },
                confirmButton = { TextButton(onClick = { showCloudRestore = true }) { Text("Restore from Google Drive") } },
                dismissButton = { TextButton(onClick = onSetUpAgain) { Text("Set up again") } },
            )
            if (showCloudRestore) {
                CloudRestoreDialog(onDismiss = { showCloudRestore = false }, onSuccess = { showCloudRestore = false; onTryAgain() })
            }
        }
        AuthenticatedOnboardingNavigationDecision.ShowRestoredSameGoogleIdentityConfirmation -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restore saved data?") },
                text = { Text("Android restored Impulsive data from the same linked Google identity, but the Firebase account identifier changed.") },
                confirmButton = { TextButton(onClick = onTryAgain) { Text("Restore data") } },
                dismissButton = { TextButton(onClick = onUseAnotherAccount) { Text("Use another account") } },
            )
        }
        AuthenticatedOnboardingNavigationDecision.ShowRestoredLegacyDriveVerification -> {
            var showCloudRestore by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restore saved data?") },
                text = { Text("This restored local data predates the identity claim. Restore and confirm the encrypted Google Drive recovery copy before claiming it.") },
                confirmButton = { TextButton(onClick = { showCloudRestore = true }) { Text("Restore from Google Drive") } },
                dismissButton = { TextButton(onClick = onUseAnotherAccount) { Text("Use another account") } },
            )
            if (showCloudRestore) CloudRestoreDialog(onDismiss = { showCloudRestore = false }, onSuccess = { showCloudRestore = false; onTryAgain() })
        }
        AuthenticatedOnboardingNavigationDecision.ShowAccountMismatch -> {
            if (!suppressUnusableLocalDataDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(
                            "Saved data belongs to another account",
                        )
                    },
                    text = {
                        Text(
                            "This saved Impulsive data belongs to a different account. You can sign in with the account that created it, or permanently erase the saved data from this device and continue with the account currently signed in.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onUseAnotherAccount,
                        ) {
                            Text("Use another account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onEraseSavedData,
                        ) {
                            Text("Erase saved data")
                        }
                    },
                )
            }
        }

        AuthenticatedOnboardingNavigationDecision
            .ShowLegacyUnownedLocalData -> {
            if (!suppressUnusableLocalDataDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text("Saved data needs review")
                    },
                    text = {
                        Text(
                            "Impulsive found saved data from an older version, but it cannot safely confirm which account owns it. You can use another account or permanently erase the saved data from this device.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onUseAnotherAccount,
                        ) {
                            Text("Use another account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onEraseSavedData,
                        ) {
                            Text("Erase saved data")
                        }
                    },
                )
            }
        }

    }
}

@Composable
private fun AccountLocalDataResetDialog(
    state: AccountLocalDataResetState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        AccountLocalDataResetState.Idle -> Unit

        is AccountLocalDataResetState.Confirming -> {
            AlertDialog(
                onDismissRequest = onCancel,
                title = {
                    Text(
                        "Erase saved data from this device?",
                    )
                },
                text = {
                    Text(
                        "This permanently removes the previous account's Impulsive setup and all locally saved Impulsive data from this device. It will not transfer that data to the account currently signed in. This cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onConfirm,
                    ) {
                        Text("Erase and continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCancel,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        is AccountLocalDataResetState.Deleting -> {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text("Erasing saved data")
                },
                text = {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Removing the previous account's saved Impulsive data from this device.",
                        )
                    }
                },
                confirmButton = { },
            )
        }

        AccountLocalDataResetState.SessionChanged -> {
            AlertDialog(
                onDismissRequest = onCancel,
                title = {
                    Text("Signed-in account changed")
                },
                text = {
                    Text(
                        "The signed-in account changed before the erase could begin. No saved data was erased. Continue, then start the erase again from the saved-data warning.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onCancel,
                    ) {
                        Text("Continue")
                    }
                },
            )
        }

        is AccountLocalDataResetState.Failed -> {
            AlertDialog(
                onDismissRequest = onCancel,
                title = {
                    Text("Could not finish")
                },
                text = {
                    Text(state.message)
                },
                confirmButton = {
                    TextButton(
                        onClick = onRetry,
                    ) {
                        Text("Try again")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCancel,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}


private fun NavHostController.navigateOnboarding(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.safePopBackStack() {
    val hasPrevious = previousBackStackEntry != null
    val isResumed = currentBackStackEntry
        ?.lifecycle
        ?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) == true
    if (hasPrevious && isResumed) {
        popBackStack()
    }
}

private fun NavHostController.exitRecoveryFlowSafely() {
    val currentEntry =
        currentBackStackEntry
            ?: return

    val isResumed =
        currentEntry
            .lifecycle
            .currentState
            .isAtLeast(
                Lifecycle.State.RESUMED,
            )

    if (!isResumed) {
        return
    }

    val currentRoute =
        currentEntry
            .destination
            .route

    val previousEntry =
        previousBackStackEntry

    val previousRoute =
        previousEntry
            ?.destination
            ?.route

    /*
     * Protection-origin recovery content must never return to an obsolete
     * protection entry point.
     */
    val previousIsProtectionEntryPoint =
        previousRoute ==
            AppRoutes.RandomRecoveryGame ||
            previousRoute ==
            AppRoutes.ImpulsiveBlock

    /*
     * ResetReadFallbackTask is itself protection-only.
     *
     * On a warm MainActivity, a normal Impulsive destination may still exist
     * underneath it.
     *
     * The presence of previousBackStackEntry must therefore not cause this
     * fallback flow to pop back to that stale screen.
     */
    val currentIsProtectionOnly =
        currentRoute ==
            AppRoutes.ResetReadFallbackTask

    /*
     * Normal recovery content preserves normal back navigation.
     *
     * Examples:
     *
     * Recovery Games -> Reflex Game -> Done
     * Task to Complete -> Rhythm Tiles -> Done
     *
     * Protection-only reading and protection entry points instead exit Home.
     */
    if (
        previousEntry != null &&
        !previousIsProtectionEntryPoint &&
        !currentIsProtectionOnly
    ) {
        popBackStack()
        return
    }

    /*
     * No usable previous app destination exists, or this destination belongs
     * to the protection flow.
     *
     * Clear all current destinations inside main_graph and establish Home as
     * the only visible child destination.
     */
    navigate(
        AppRoutes.Home,
    ) {
        popUpTo(
            AppRoutes.Graph,
        ) {
            inclusive = false
        }

        launchSingleTop = true
    }
}

// Pops the entire onboarding graph so back from home exits the app.
private fun NavHostController.navigateToMainClearingOnboarding() {
    navigate(AppRoutes.Graph) {
        popUpTo(OnboardingRoutes.Graph) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

// Returns to home within the main graph, collapsing any detail screens above it.
private fun NavHostController.navigateBackToHome() {
    navigate(AppRoutes.Home) {
        popUpTo(AppRoutes.Home) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

// Navigates to a top-level main destination (Score, Settings, RecoveryGames, JournalHub)
// without growing the back stack past the home screen.
private fun NavHostController.navigateMainTop(route: String) {
    navigate(route) {
        popUpTo(AppRoutes.Home) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

// Maps a recovery game to its route in either the hub context or the block-task
// context, so play-another can stay in the same launch context.
private fun recoveryGameRoute(
    game: com.impulsive.app.backend.domain.model.score.ScoreGameType,
    asTask: Boolean,
): String = when (game) {
    com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade ->
        if (asTask) AppRoutes.BlockCascadeTask else AppRoutes.BlockCascadeGame
    com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset ->
        if (asTask) AppRoutes.SkylineResetTask else AppRoutes.SkylineResetGame
    com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles ->
        if (asTask) AppRoutes.RhythmTilesTask else AppRoutes.RhythmTilesGame
    else ->
        if (asTask) AppRoutes.ReflexGameTask else AppRoutes.ReflexGame
}

private suspend fun selectAndRecordGuidedGame(
    context: Context,
    sourcePackageName: String,
): com.impulsive.app.backend.domain.model.score.ScoreGameType {
    val scoreRepository = com.impulsive.app.backend.data.repository.ScoreRepository(context)
    val urgeEventRepository = com.impulsive.app.backend.data.repository.UrgeEventRepository(context)
    val servedGamesRepository = com.impulsive.app.backend.data.repository.ServedGamesRepository(context)
    val chosenGame = com.impulsive.app.backend.domain.usecase.GameSelectionEngine.selectNextGame(
        sessions = scoreRepository.sessions.first(),
        urgeEvents = urgeEventRepository.events.first(),
        recentlyServed = servedGamesRepository.served.first(),
    )
    urgeEventRepository.recordEvent(
        source = "support_task",
        packageName = sourcePackageName,
    )
    servedGamesRepository.recordServed(chosenGame)
    return chosenGame
}

// Builds a play-another callback that picks a different recovery game, keeps the
// same launch context, records it served in the block-task context, and replaces
// the current game in the back stack so back does not return to it.
@Composable
private fun rememberPlayAnotherGame(
    navController: NavHostController,
    current: com.impulsive.app.backend.domain.model.score.ScoreGameType,
    asTask: Boolean,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scoreRepository = remember(context) {
        com.impulsive.app.backend.data.repository.ScoreRepository(context)
    }
    val urgeEventRepository = remember(context) {
        com.impulsive.app.backend.data.repository.UrgeEventRepository(context)
    }
    val servedGamesRepository = remember(context) {
        com.impulsive.app.backend.data.repository.ServedGamesRepository(context)
    }
    val sessions by scoreRepository.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val urgeEvents by urgeEventRepository.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyServed by servedGamesRepository.served.collectAsStateWithLifecycle(initialValue = emptyList())
    return {
        val pool = com.impulsive.app.backend.domain.usecase.GameSelectionEngine.candidates
            .filter { it != current }
        var chosen = com.impulsive.app.backend.domain.usecase.GameSelectionEngine.selectNextGame(
            sessions = sessions,
            urgeEvents = urgeEvents,
            recentlyServed = recentlyServed,
        )
        if (chosen == current) {
            chosen = pool.random()
        }
        if (asTask) {
            scope.launch { servedGamesRepository.recordServed(chosen) }
        }
        navController.navigate(recoveryGameRoute(chosen, asTask)) {
            launchSingleTop = true
            popUpTo(recoveryGameRoute(current, asTask)) { inclusive = true }
        }
    }
}
