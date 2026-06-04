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
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.screens.games.BlockCascadeScreen
import com.impulsive.app.frontend.screens.games.ReflexGameScreen
import com.impulsive.app.frontend.screens.games.RecoveryGamesScreen
import com.impulsive.app.frontend.screens.intro.IntroScreen
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.frontend.screens.onboarding.LoginSignupGuestScreen
import com.impulsive.app.frontend.screens.onboarding.NotificationPermissionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingDailyRelapseCountScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingQuestionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingStartingPointScreen
import com.impulsive.app.frontend.screens.onboarding.ProtectionSetupOnboardingScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen
import com.impulsive.app.frontend.screens.lock.AppLockGuardHost
import com.impulsive.app.frontend.screens.lock.rememberAppLockGuardController
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.frontend.screens.protection.ImpulsiveBlockScreen
import com.impulsive.app.frontend.screens.protection.UninstallProtectionScreen
import com.impulsive.app.frontend.screens.progress.ProgressDashboardScreen
import com.impulsive.app.frontend.screens.settings.SettingsScreen
import com.impulsive.app.frontend.screens.tasks.FutureSelfMessageScreen
import com.impulsive.app.frontend.screens.tasks.FutureSelfRecordScreen
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
}

object AppRoutes {
    const val Graph = "main_graph"

    const val Home = "level_one_reveal"
    const val Settings = "settings"
    const val Score = "score"
    const val RecoveryGames = "recovery_games"
    const val ReflexGame = "reflex_game"
    const val ReflexGameTask = "reflex_game_task"
    const val BlockCascadeGame = "block_cascade_game"
    const val BlockCascadeTask = "block_cascade_task"
    const val ResetReadTask = "reset_read_task"
    const val TaskToComplete = "task_to_complete"
    const val FutureSelfMessageTask = "future_self_message_task"
    const val FutureSelfRecord = "future_self_record"
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

    LaunchedEffect(initialBlockRequest, state.isCompleted) {
        val request = initialBlockRequest
        if (request != null && state.isCompleted) {
            navController.navigate(
                AppRoutes.impulsiveBlock(
                    sourcePackageName = request.sourcePackageName,
                    sourceLabel = request.sourceLabel,
                ),
            ) {
                launchSingleTop = true
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
                        navController.navigateOnboarding(OnboardingRoutes.LoginSignupGuest)
                    },
                )
            }

            composable(OnboardingRoutes.LoginSignupGuest) {
                LoginSignupGuestScreen(
                    onAuthenticated = {
                        navController.navigateOnboarding(OnboardingRoutes.WelcomePrivacy)
                    },
                    authViewModel = authViewModel,
                )
            }

            composable(OnboardingRoutes.WelcomePrivacy) {
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
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Interrupting,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = navController::popBackStack,
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionTriggers)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionTriggers) {
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Triggers,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = navController::popBackStack,
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionTiming)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionTiming) {
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.Timing,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = navController::popBackStack,
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionWeekOne)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionWeekOne) {
                OnboardingQuestionScreen(
                    questionId = OnboardingQuestionId.WeekOneGoal,
                    state = state,
                    onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                    onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                    onBack = navController::popBackStack,
                    onContinue = {
                        navController.navigateOnboarding(OnboardingRoutes.QuestionDailyRelapseCount)
                    },
                    onSkip = null,
                )
            }

            composable(OnboardingRoutes.QuestionDailyRelapseCount) {
                OnboardingDailyRelapseCountScreen(
                    state = state,
                    initialCount = state.answers.dailyRelapseUrgeCount,
                    onBack = navController::popBackStack,
                    onContinue = { selectedCount ->
                        onboardingViewModel.setDailyRelapseUrgeCount(selectedCount)
                        pendingDailyRelapseCount = selectedCount
                    },
                )
            }

            composable(OnboardingRoutes.ProtectionSetup) {
                ProtectionSetupOnboardingScreen(
                    state = protectionSetupState,
                    onBack = navController::popBackStack,
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

            composable(OnboardingRoutes.ProtectionBlockedApps) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged = protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { navController.safePopBackStack() },
                    seedRecommendedBrowsers = true,
                )
            }

            composable(OnboardingRoutes.StartingPoint) {
                OnboardingStartingPointScreen(
                    state = state,
                    onBack = navController::popBackStack,
                    onContinue = {
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
                        navController.navigate(AppRoutes.FutureSelfRecord)
                    },
                    onOpenReflexOverrideTask = {
                        navController.navigate(AppRoutes.ReflexGameTask)
                    },
                    onOpenBlockCascadeTask = {
                        navController.navigate(AppRoutes.BlockCascadeTask)
                    },
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenFutureSelfMessageTask = {
                        navController.navigate(AppRoutes.FutureSelfMessageTask)
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
                )
            }

            composable(AppRoutes.Score) {
                ProgressDashboardScreen(
                    onOpenHome = { navController.navigateBackToHome() },
                    onOpenSettings = {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                )
            }

            composable(AppRoutes.Settings) {
                SettingsScreen(
                    onOpenScore = {
                        navController.navigateMainTop(AppRoutes.Score)
                    },
                    onOpenFutureSelfRecord = {
                        navController.navigate(AppRoutes.FutureSelfRecord)
                    },
                    onOpenUninstallProtection = {
                        navController.navigate(AppRoutes.UninstallProtection) {
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

            composable(AppRoutes.ResetReadTask) {
                ResetReadScreen(
                    onExit = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.FutureSelfMessageTask) {
                FutureSelfMessageScreen(
                    onExit = { navController.safePopBackStack() },
                    onRecordYours = { navController.navigate(AppRoutes.FutureSelfRecord) },
                )
            }

            composable(AppRoutes.FutureSelfRecord) {
                FutureSelfRecordScreen(
                    onExit = { navController.safePopBackStack() },
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
                                urgeEventRepository.recordEvent(source = "support_task", packageName = sourcePackageName)
                            }
                            navController.navigate(AppRoutes.TaskToComplete) { launchSingleTop = true }
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
                    onOpenResetReadTask = {
                        navController.navigate(AppRoutes.ResetReadTask)
                    },
                    onOpenFutureSelfMessageTask = {
                        navController.navigate(AppRoutes.FutureSelfMessageTask)
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
