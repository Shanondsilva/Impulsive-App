package com.impulsive.app.frontend.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.frontend.components.BottomNavItem
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
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.protection.DnsFilterGateViewModel
import com.impulsive.app.frontend.screens.onboarding.LoginSignupGuestScreen
import com.impulsive.app.frontend.screens.onboarding.NotificationPermissionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingDailyRelapseCountScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingQuestionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingStartingPointScreen
import com.impulsive.app.frontend.screens.onboarding.PersonalisingSetupScreen
import com.impulsive.app.frontend.screens.focus.ActiveFocusSessionScreen
import com.impulsive.app.frontend.screens.focus.FocusScreen
import com.impulsive.app.frontend.screens.focus.FocusRecoveryScreen
import com.impulsive.app.frontend.screens.onboarding.ProtectionSetupOnboardingScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen
import com.impulsive.app.frontend.screens.lock.AppLockGuardHost
import com.impulsive.app.frontend.screens.lock.rememberAppLockGuardController
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.frontend.screens.protection.ImpulsiveBlockScreen
import com.impulsive.app.frontend.screens.protection.UninstallProtectionScreen
import com.impulsive.app.frontend.screens.progress.ProgressDashboardScreen
import com.impulsive.app.frontend.screens.premium.WebsiteProtectionPlusScreen
import com.impulsive.app.frontend.screens.protection.DnsFilterGateScreen
import com.impulsive.app.frontend.screens.settings.SettingsScreen
import com.impulsive.app.frontend.screens.tasks.ResetReadScreen
import com.impulsive.app.frontend.screens.tasks.TaskToCompleteScreen
import com.impulsive.app.security.antibypass.UninstallProtectionManager
import kotlinx.coroutines.launch

object OnboardingRoutes {
    const val Graph = "onboarding_graph"

    const val LogoIntro = "logo_intro"
    const val LoginSignupGuest = "login_signup_guest"
    const val WelcomePrivacy = "welcome_privacy"
    const val NotificationPermission = "notification_permission"
    const val QuestionInterrupting = "question_interrupting"
    const val QuestionTiming = "question_timing"
    const val QuestionTriggers = "question_triggers"
    const val QuestionWeekOne = "question_week_one"
    const val QuestionDailyRelapseCount = "question_daily_relapse_count"
    const val ProtectionSetup = "protection_setup"
    const val ProtectionBlockedApps = "protection_blocked_apps"
    const val UninstallProtection = "onboarding_uninstall_protection"
    const val StartingPoint = "starting_point"
    const val PersonalisingSetup = "personalising_setup"
}

object AppRoutes {
    const val Graph = "main_graph"

    const val Home = "level_one_reveal"
    const val Settings = "settings"
    const val Score = "score"
    const val Focus = "focus"
    const val FocusSession = "focus_session"
    const val FocusRecovery = "focus_recovery"
    const val RecoveryGames = "recovery_games"
    const val ReflexGame = "reflex_game"
    const val ReflexGameTask = "reflex_game_task"
    const val BlockCascadeGame = "block_cascade_game"
    const val BlockCascadeTask = "block_cascade_task"
    const val SkylineResetGame = "skyline_reset_game"
    const val SkylineResetTask = "skyline_reset_task"
    const val RhythmTilesGame = "rhythm_tiles_game"
    const val RhythmTilesTask = "rhythm_tiles_task"
    const val ResetReadTask = "reset_read_task"
    const val TaskToComplete = "task_to_complete"
    const val WebsiteProtectionPlus = "website_protection_plus"
    const val DnsFilterGate = "dns_filter_gate"
    const val JournalHub = "journal_hub"
    const val JournalList = "journal_list"
    const val JournalNoteNew = "journal_note_new/{type}"
    const val JournalNoteEdit = "journal_note_edit/{noteId}"
    const val UninstallProtection = "main_uninstall_protection"
    const val ImpulsiveBlock = "impulsive_block/{sourcePackageName}/{sourceLabel}"

    fun journalNoteNew(type: JournalNoteType): String = "journal_note_new/${type.storageValue}"
    fun journalNoteEdit(noteId: Long): String = "journal_note_edit/$noteId"
    fun impulsiveBlock(sourcePackageName: String, sourceLabel: String): String =
        "impulsive_block/${Uri.encode(sourcePackageName)}/${Uri.encode(sourceLabel)}"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    protectionSetupViewModel: ProtectionSetupViewModel = viewModel(),
    authViewModel: AuthViewModel,
    initialBlockRequest: BlockRequest? = null,
    onBlockRequestConsumed: () -> Unit = {},
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageAccessChecker = remember(context) { UsageAccessPermissionChecker(context) }
    val uninstallProtectionManager = remember(context) { UninstallProtectionManager(context) }
    var pendingDailyRelapseCount by remember { mutableStateOf<Int?>(null) }
    var bottomNavIndicatorStartFrom by remember { mutableStateOf<BottomNavItem?>(null) }

    fun syncUsageAccessPermission() {
        protectionSetupViewModel.setUsageAccessEnabled(usageAccessChecker.hasUsageAccess())
    }

    fun syncInterruptionPermission() {
        protectionSetupViewModel.setInterruptionPermissionEnabled(Settings.canDrawOverlays(context))
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun syncBackgroundActivityPermission() {
        protectionSetupViewModel.setBackgroundActivityEnabled(isIgnoringBatteryOptimizations())
    }

    fun syncNotificationPermission() {
        val isAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        protectionSetupViewModel.setNotificationPermissionEnabled(isAllowed)
    }

    fun syncUninstallProtection() {
        protectionSetupViewModel.setUninstallProtectionEnabled(uninstallProtectionManager.isActive())
    }

    fun syncProtectionSetupFromDevice() {
        syncUsageAccessPermission()
        syncInterruptionPermission()
        syncBackgroundActivityPermission()
        syncNotificationPermission()
        syncUninstallProtection()
    }

    fun bottomNavItemForCurrentRoute(): BottomNavItem {
        return when (navController.currentBackStackEntry?.destination?.route) {
            AppRoutes.Home -> BottomNavItem.Home
            AppRoutes.Score -> BottomNavItem.Progress
            AppRoutes.Settings -> BottomNavItem.Settings
            AppRoutes.Focus -> BottomNavItem.Focus
            else -> BottomNavItem.Home
        }
    }

    fun prepareBottomNavTopLevelTransition(target: BottomNavItem) {
        val current = bottomNavItemForCurrentRoute()
        bottomNavIndicatorStartFrom = if (current != target) current else null
    }

    DisposableEffect(context, lifecycleOwner, usageAccessChecker, uninstallProtectionManager) {
        syncProtectionSetupFromDevice()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncProtectionSetupFromDevice()
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

    LaunchedEffect(initialBlockRequest, state.isCompleted) {
        val request = initialBlockRequest
        if (request != null && state.isCompleted) {
            if (request.isFocusSession) {
                navController.navigate(AppRoutes.FocusRecovery) {
                    launchSingleTop = true
                }
            } else {
                navController.navigate(
                    AppRoutes.impulsiveBlock(
                        sourcePackageName = request.sourcePackageName,
                        sourceLabel = request.sourceLabel,
                    ),
                ) {
                    launchSingleTop = true
                }
            }
            onBlockRequestConsumed()
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

    NavHost(
        navController = navController,
        startDestination = if (state.isCompleted) AppRoutes.Graph else OnboardingRoutes.Graph,
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
                LoginSignupGuestScreen(
                    onAuthenticated = {
                        navController.navigateOnboarding(OnboardingRoutes.WelcomePrivacy)
                    },
                    authViewModel = authViewModel,
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
                            navController.navigateOnboarding(OnboardingRoutes.NotificationPermission)
                        }
                    },
                )
            }

            composable(OnboardingRoutes.NotificationPermission) {
                BackHandler { navigateBackOnboardingSafely() }
                NotificationPermissionScreen(
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionInterrupting)
                    },
                    onPermissionResult = { granted ->
                        protectionSetupViewModel.setNotificationPermissionEnabled(granted)
                        if (!granted) {
                            protectionSetupViewModel.markSkipped(ProtectionSetupItem.Notifications)
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
                    onOpenUsageAccessPermission = {
                        context.startActivity(usageAccessChecker.createUsageAccessSettingsIntent())
                    },
                    onOpenInterruptionPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                    onOpenBackgroundActivityPermission = {
                        val intent = if (isIgnoringBatteryOptimizations()) {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        } else {
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                    },
                    onOpenUninstallProtection = {
                        navController.navigate(OnboardingRoutes.UninstallProtection) {
                            launchSingleTop = true
                        }
                    },
                    onSkipItem = protectionSetupViewModel::markSkipped,
                    onContinue = {
                        protectionSetupState.incompleteCoreProtectionItems.forEach { item ->
                            protectionSetupViewModel.markSkipped(item)
                        }
                        navController.navigateOnboarding(OnboardingRoutes.StartingPoint)
                    },
                )
            }

            composable(OnboardingRoutes.UninstallProtection) {
                BackHandler { navigateBackOnboardingSafely() }
                UninstallProtectionScreen(
                    state = protectionSetupState,
                    onBack = { navigateBackOnboardingSafely() },
                    onEnabledSynced = protectionSetupViewModel::setUninstallProtectionEnabled,
                    onSkip = {
                        protectionSetupViewModel.markSkipped(ProtectionSetupItem.UninstallProtection)
                        navigateBackOnboardingSafely()
                    },
                )
            }

            composable(OnboardingRoutes.ProtectionBlockedApps) {
                BackHandler { navigateBackOnboardingSafely() }
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged = protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { navigateBackOnboardingSafely() },
                    allowShowMoreApps = true,
                    seedRecommendedBrowsers = true,
                )
            }

            composable(OnboardingRoutes.StartingPoint) {
                BackHandler { navigateBackOnboardingSafely() }
                OnboardingStartingPointScreen(
                    state = state,
                    onBack = { navigateBackOnboardingSafely() },
                    onContinue = { navController.navigate(OnboardingRoutes.PersonalisingSetup) },
                )
            }
            composable(OnboardingRoutes.PersonalisingSetup) {
                // Back is intentionally swallowed: the user has committed to
                // starting week one and the screen completes on its own.
                BackHandler { }
                PersonalisingSetupScreen(
                    onFinished = {
                        onboardingViewModel.completeOnboarding {
                            navController.navigateToMainClearingOnboarding()
                        }
                    },
                )
            }
        }

        navigation(
            route = AppRoutes.Graph,
            startDestination = AppRoutes.Home,
        ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onOpenRecoveryGames = {
                        navController.navigateMainTop(AppRoutes.RecoveryGames)
                    },
                    onOpenJournal = {
                        navController.navigateMainTop(AppRoutes.JournalList)
                    },
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenTasks = {
                        navController.navigate(AppRoutes.TaskToComplete)
                    },
                    onOpenScore = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Progress)
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenSettings = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Settings)
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenWebsiteProtectionPlus = {
                        navController.navigate(AppRoutes.WebsiteProtectionPlus) {
                            launchSingleTop = true
                        }
                    },
                    onOpenFocus = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Focus)
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    bottomNavIndicatorStartFrom = bottomNavIndicatorStartFrom,
                    onBottomNavIndicatorStartConsumed = {
                        bottomNavIndicatorStartFrom = null
                    },
                    onNavigateFromModeContext = {
                        bottomNavIndicatorStartFrom = BottomNavItem.Trigger
                    },
                )
            }

            composable(AppRoutes.Focus) {
                FocusScreen(
                    onOpenHome = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Home)
                        navController.navigateMainTop(AppRoutes.Home)
                    },
                    onOpenScore = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Progress)
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenSettings = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Settings)
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenSession = {
                        navController.navigate(AppRoutes.FocusSession)
                    },
                    onOpenTasks = {
                        navController.navigate(AppRoutes.TaskToComplete)
                    },
                    bottomNavIndicatorStartFrom = bottomNavIndicatorStartFrom,
                    onBottomNavIndicatorStartConsumed = {
                        bottomNavIndicatorStartFrom = null
                    },
                    onNavigateFromModeContext = {
                        bottomNavIndicatorStartFrom = BottomNavItem.Trigger
                    },
                )
            }

            composable(AppRoutes.FocusRecovery) {
                FocusRecoveryScreen(
                    onReturnToFocus = {
                        navController.navigate(AppRoutes.FocusSession) {
                            launchSingleTop = true
                        }
                    },
                    onEndedCalmly = {
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                )
            }

            composable(AppRoutes.FocusSession) {
                ActiveFocusSessionScreen(
                    onExit = { navController.popBackStack() },
                )
            }

            composable(AppRoutes.Score) {
                ProgressDashboardScreen(
                    onOpenHome = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Home)
                        navController.navigateBackToHome()
                    },
                    onOpenSettings = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Settings)
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenFocus = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Focus)
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    bottomNavIndicatorStartFrom = bottomNavIndicatorStartFrom,
                    onBottomNavIndicatorStartConsumed = {
                        bottomNavIndicatorStartFrom = null
                    },
                    onNavigateFromModeContext = {
                        bottomNavIndicatorStartFrom = BottomNavItem.Trigger
                    },
                )
            }

            composable(AppRoutes.Settings) {
                SettingsScreen(
                    onOpenScore = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Progress)
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenFocus = {
                        prepareBottomNavTopLevelTransition(BottomNavItem.Focus)
                        navController.navigateMainTop(AppRoutes.Focus)
                    },
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenUninstallProtection = {
                        navController.navigate(AppRoutes.UninstallProtection) {
                            launchSingleTop = true
                        }
                    },
                    onOpenWebsiteProtectionPlus = {
                        navController.navigate(AppRoutes.WebsiteProtectionPlus) {
                            launchSingleTop = true
                        }
                    },
                    onBackHome = {
                        val isResumed = navController.currentBackStackEntry
                            ?.lifecycle
                            ?.currentState
                            ?.isAtLeast(Lifecycle.State.RESUMED) == true
                        if (isResumed) {
                            prepareBottomNavTopLevelTransition(BottomNavItem.Home)
                            navController.navigateBackToHome()
                        }
                    },
                    bottomNavIndicatorStartFrom = bottomNavIndicatorStartFrom,
                    onBottomNavIndicatorStartConsumed = {
                        bottomNavIndicatorStartFrom = null
                    },
                    onNavigateFromModeContext = {
                        bottomNavIndicatorStartFrom = BottomNavItem.Trigger
                    },
                )
            }

            composable(AppRoutes.WebsiteProtectionPlus) {
                WebsiteProtectionPlusScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenDnsFilterCheck = {
                        navController.navigate(AppRoutes.DnsFilterGate) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(AppRoutes.DnsFilterGate) {
                val dnsFilterGateViewModel: DnsFilterGateViewModel = viewModel()
                val dnsFilterGateState by dnsFilterGateViewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                DnsFilterGateScreen(
                    state = dnsFilterGateState,
                    onOpenPrivateDnsSettings = {
                        context.startActivity(dnsFilterGateViewModel.privateDnsSettingsIntent())
                    },
                    onRefresh = { dnsFilterGateViewModel.refresh() },
                    // Pops back for now. Repointed to the enable flow once the VpnService exists.
                    onContinue = { navController.safePopBackStack() },
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.UninstallProtection) {
                UninstallProtectionScreen(
                    state = protectionSetupState,
                    onBack = { navController.safePopBackStack() },
                    onEnabledSynced = protectionSetupViewModel::setUninstallProtectionEnabled,
                    onSkip = {
                        protectionSetupViewModel.markSkipped(ProtectionSetupItem.UninstallProtection)
                        navController.safePopBackStack()
                    },
                )
            }

            composable(AppRoutes.RecoveryGames) {
                RecoveryGamesScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenReflexOverride = { navController.navigate(AppRoutes.ReflexGame) },
                    onOpenBlockCascade = { navController.navigate(AppRoutes.BlockCascadeGame) },
                    onOpenSkylineReset = { navController.navigate(AppRoutes.SkylineResetGame) },
                    onOpenRhythmTiles = { navController.navigate(AppRoutes.RhythmTilesGame) },
                )
            }

            composable(AppRoutes.ReflexGame) {
                ReflexGameScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.ReflexGameTask) {
                ReflexGameScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.BlockCascadeGame) {
                BlockCascadeScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.BlockCascadeTask) {
                BlockCascadeScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.SkylineResetGame) {
                SkylineResetScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.SkylineResetTask) {
                SkylineResetScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.RhythmTilesGame) {
                RhythmTilesScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.RhythmTilesTask) {
                RhythmTilesScreen(
                    onExit = { navController.safePopBackStack() },
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                )
            }

            composable(AppRoutes.ResetReadTask) {
                ResetReadScreen(
                    onExit = { navController.safePopBackStack() },
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
                val scoreRepository = remember(context) {
                    com.impulsive.app.backend.data.repository.ScoreRepository(context)
                }
                val servedGamesRepository = remember(context) {
                    com.impulsive.app.backend.data.repository.ServedGamesRepository(context)
                }
                val gameSessions by scoreRepository.sessions
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val urgeEvents by urgeEventRepository.events
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val recentlyServedGames by servedGamesRepository.served
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val blockGuard = rememberAppLockGuardController()
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
                            val chosenGame = com.impulsive.app.backend.domain.usecase.GameSelectionEngine.selectNextGame(
                                sessions = gameSessions,
                                urgeEvents = urgeEvents,
                                recentlyServed = recentlyServedGames,
                            )
                            urgeEventScope.launch {
                                urgeEventRepository.recordEvent(source = "support_task", packageName = sourcePackageName)
                                servedGamesRepository.recordServed(chosenGame)
                            }
                            val gameRoute = when (chosenGame) {
                                com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade -> AppRoutes.BlockCascadeTask
                                com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset -> AppRoutes.SkylineResetTask
                                com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles -> AppRoutes.RhythmTilesTask
                                else -> AppRoutes.ReflexGameTask
                            }
                            navController.navigate(gameRoute) { launchSingleTop = true }
                        }
                    },
                    onReturnHome = {
                        blockGuard.run(appLockEnabled) {
                            navController.navigateBackToHome()
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
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                )
            }
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
