package com.impulsive.app.frontend.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
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
import com.impulsive.app.backend.service.protection.ImpulsiveVpnController
import com.impulsive.app.backend.service.protection.ProtectionServiceController
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.protection.DnsFilterGateViewModel
import com.impulsive.app.backend.session.premium.PremiumViewModel
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.service.billing.BillingManager
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
import com.impulsive.app.frontend.screens.protection.UninstallProtectionScreen
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
import com.impulsive.app.security.antibypass.UninstallProtectionManager
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
    const val UninstallProtection = "onboarding_uninstall_protection"
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
    const val ProtectionSetupGuide = "protection_setup_guide"
    const val ProtectionSetupGuideBlockedApps = "protection_setup_guide_blocked_apps"
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
    initialJournalNoteId: Long? = null,
    onJournalNoteConsumed: () -> Unit = {},
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageAccessChecker = remember(context) { UsageAccessPermissionChecker(context) }
    val uninstallProtectionManager = remember(context) { UninstallProtectionManager(context) }
    var pendingDailyRelapseCount by remember { mutableStateOf<Int?>(null) }
    val bottomNavIndicatorState = rememberBottomNavIndicatorState()
    val bottomNavCurrentEntry by navController.currentBackStackEntryAsState()
    val bottomNavCurrentRoute = bottomNavCurrentEntry?.destination?.route

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

    // Keep the protection monitor running whenever the user has protected apps
    // configured. Without this the service only ever started on reboot, on a
    // focus session, or via a manual Settings button, so the block screen,
    // persistent notification, and one-minute timer never appeared on a normal
    // app open. Re-runs when the protected list changes. Start is idempotent.
    LaunchedEffect(protectionSetupState.selectedBlockedAppPackageNames) {
        if (protectionSetupState.selectedBlockedAppPackageNames.isNotEmpty()) {
            ProtectionServiceController.start(context)
        }
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

    LaunchedEffect(initialJournalNoteId, state.isCompleted) {
        val noteId = initialJournalNoteId
        if (noteId != null && noteId > 0L && state.isCompleted) {
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
    val mainStartDestination = remember(state.isCompleted) {
        val request = initialBlockRequest
        when {
            !state.isCompleted -> AppRoutes.Home
            request == null -> AppRoutes.Home
            request.isFocusSession -> AppRoutes.FocusRecovery
            else -> AppRoutes.impulsiveBlock(
                sourcePackageName = request.sourcePackageName,
                sourceLabel = request.sourceLabel,
            )
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
            startDestination = mainStartDestination,
        ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onOpenRecoveryGames = {
                        navController.navigateMainTop(AppRoutes.RecoveryGames)
                    },
                    onOpenJournal = {
                        navController.navigateMainTop(AppRoutes.JournalList)
                    },
                    onCreateJournalNote = { type ->
                        navController.navigate(AppRoutes.journalNoteNew(type))
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
                    onOpenRhythmTilesTask = {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenTasks = {
                        navController.navigate(AppRoutes.TaskToComplete)
                    },
                    onOpenScore = {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenSettings = {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onOpenWebsiteProtectionPlus = {
                        navController.navigate(AppRoutes.WebsiteProtectionPlus) {
                            launchSingleTop = true
                        }
                    },
                    onOpenFocus = {
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
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Score,
                )
            }

            composable(AppRoutes.Settings) {
                SettingsScreen(
                    onOpenScore = {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenFocus = {
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
                    onOpenRhythmTilesTask = {
                        navController.navigate(AppRoutes.RhythmTilesTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenHelp = {
                        navController.navigate(AppRoutes.HelpFaq) {
                            launchSingleTop = true
                        }
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
                    onOpenProtectionSetupGuide = {
                        navController.navigate(AppRoutes.ProtectionSetupGuide) {
                            launchSingleTop = true
                        }
                    },
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
                        navController.navigate(AppRoutes.UninstallProtection) {
                            launchSingleTop = true
                        }
                    },
                    onSkipItem = protectionSetupViewModel::markSkipped,
                    onContinue = { navController.safePopBackStack() },
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
                val isPlus by remember(premiumViewModel) {
                    premiumViewModel.hasFeature(PremiumFeature.VpnWebsiteBlocker)
                }.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val billingManager = remember { BillingManager(context) }
                val priceLabel by billingManager.formattedPrice.collectAsStateWithLifecycle()
                DisposableEffect(billingManager) {
                    billingManager.connect()
                    onDispose { billingManager.release() }
                }
                WebsiteProtectionPlusScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenDnsFilterCheck = {
                        navController.navigate(AppRoutes.DnsFilterGate) {
                            launchSingleTop = true
                        }
                    },
                    isPlus = isPlus,
                    priceLabel = priceLabel,
                    onPurchase = {
                        (context as? Activity)?.let { billingManager.launchPurchase(it) }
                    },
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
                            navController.safePopBackStack()
                        }
                    },
                    onTurnOff = {
                        ImpulsiveVpnController.stop(context)
                        navController.safePopBackStack()
                    },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onExit = { navController.safePopBackStack() },
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
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenSkylineResetTask = {
                        navController.navigate(AppRoutes.SkylineResetTask)
                    },
                    onOpenRhythmTilesTask = {
                        navController.navigate(AppRoutes.RhythmTilesTask)
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
