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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryRestoreCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmation
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmationKind
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
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
import com.impulsive.app.backend.session.onboarding.RestoredAccountMigrationUiState
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.screens.adaptive.MomentPlanDetailScreen
import com.impulsive.app.frontend.screens.adaptive.MomentPlanEditorScreen
import com.impulsive.app.frontend.screens.adaptive.MomentPlanListScreen
import com.impulsive.app.frontend.screens.adaptive.AdaptiveMomentScreen
import com.impulsive.app.frontend.screens.adaptive.AdaptiveDecisionExplanationScreen
import com.impulsive.app.frontend.screens.adaptive.AdaptiveFeedbackScreen
import com.impulsive.app.frontend.screens.adaptive.MomentPlanRunScreen
import com.impulsive.app.frontend.screens.adaptive.MomentPlanRehearsalScreen
import com.impulsive.app.frontend.screens.adaptive.WhatWorksForMeScreen
import com.impulsive.app.frontend.screens.pathshift.PathShiftScreen
import com.impulsive.app.frontend.screens.adaptive.HowSuggestionsWorkScreen
import com.impulsive.app.backend.session.adaptive.MomentPlanRehearsalLauncherViewModel
import com.impulsive.app.backend.session.adaptive.AdaptivePreferencesViewModel
import com.impulsive.app.backend.session.adaptive.AdaptiveRetentionRuntimeState
import com.impulsive.app.frontend.privacy.rememberRouteSensitiveScreenPrivacyReady
import com.impulsive.app.frontend.screens.games.BlockCascadeScreen
import com.impulsive.app.frontend.screens.games.ReflexGameScreen
import com.impulsive.app.frontend.screens.games.RecoveryGamesScreen
import com.impulsive.app.frontend.screens.games.SnakeGameScreen
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
import com.impulsive.app.backend.session.protection.WebsiteProtectionNextAction
import com.impulsive.app.backend.session.adaptive.FamiliarStepHistoryViewModel
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
import com.impulsive.app.frontend.screens.protectioncoach.ProtectionCoachScreen
import com.impulsive.app.frontend.screens.protectioncoach.ProtectionCoachSuggestionScreen
import com.impulsive.app.frontend.screens.protectioncoach.ProtectionTransitionScreen
import com.impulsive.app.frontend.screens.tips.TipDetailScreen
import com.impulsive.app.frontend.screens.tips.TipsScreen
import com.impulsive.app.frontend.screens.tips.TipsViewModel
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipAction
import com.impulsive.app.backend.domain.tips.TipFeature
import com.impulsive.app.frontend.screens.protection.DnsFilterGateScreen
import com.impulsive.app.backend.session.safebrowse.SafeBrowseAccessViewModel
import com.impulsive.app.backend.session.safebrowse.SafeBrowseAccessViewModelFactory
import com.impulsive.app.backend.session.safebrowse.SafeBrowsePassViewModel
import com.impulsive.app.backend.session.safebrowse.SafeBrowsePassViewModelFactory
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowseBrowserRoute
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowsePassRoute
import com.impulsive.app.frontend.screens.safebrowse.SafeBrowseRoute
import com.impulsive.app.frontend.screens.settings.HelpFaqScreen
import com.impulsive.app.frontend.screens.settings.PersonalSupportPrivacyAndDataScreen
import com.impulsive.app.frontend.screens.settings.PersonalSupportSuggestionPreferencesScreen
import com.impulsive.app.frontend.screens.settings.SettingsScreen
import com.impulsive.app.frontend.screens.settings.appVersionName
import com.impulsive.app.frontend.screens.settings.sendSupportEmail
import com.impulsive.app.frontend.screens.tasks.ResetReadScreen
import com.impulsive.app.frontend.screens.tasks.TaskToCompleteScreen
import com.impulsive.app.backend.session.tasks.ResetReadLaunchMode
import com.impulsive.app.backend.session.adaptive.AdaptiveLifecycleResult
import com.impulsive.app.backend.session.adaptive.AdaptivePhase4Dependencies
import com.impulsive.app.backend.session.adaptive.AdaptiveRouteKind
import com.impulsive.app.backend.session.adaptive.AdaptiveRouteRequest
import com.impulsive.app.backend.session.adaptive.toRouteRequest
import com.impulsive.app.backend.session.protectioncoach.ProtectionCoachViewModel
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
    const val SnakeGame = "snake_game"
    const val SnakeGameTask = "snake_game_task"

    /*
     * Legacy upgrade compatibility only. These strings must keep their original
     * values so a restored pre-cutover back stack or an already-running Reflex
     * support step can still finish truthfully. Nothing active navigates here.
     */
    const val LegacyReflexGame = "reflex_game"
    const val LegacyReflexGameTask = "reflex_game_task"
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
    const val SafeBrowse = "safe_browse"
    const val SafeBrowseBrowser = "safe_browse/browser"
    const val SafeBrowsePass = "safe_browse/pass"
    const val ProtectionSetupGuide = "protection_setup_guide"
    const val ProtectionSetupGuideBlockedApps = "protection_setup_guide_blocked_apps"
    const val DnsFilterGate = "dns_filter_gate"
    const val JournalHub = "journal_hub"
    const val JournalList = "journal_list"
    const val SavedNotifications =
        "journal_saved_notifications"
    const val JournalNoteNew = "journal_note_new/{type}"
    const val JournalNoteEdit = "journal_note_edit/{noteId}"
    const val MomentPlanList = "moment_plan_list"
    const val MomentPlanEditor = "moment_plan_editor?planId={planId}"
    const val MomentPlanDetail = "moment_plan_detail/{planId}"
    const val MomentPlanRehearsal = "moment_plan_rehearsal/{rehearsalId}"
    const val WhatWorksForMe = "what_works_for_me"
    const val HowSuggestionsWork = "how_suggestions_work"
    const val PersonalSupportSuggestions = "personal_support_suggestions"
    const val PersonalSupportPrivacy = "personal_support_privacy"
    const val PathShift = "path_shift"
    const val SuggestedSetup = "suggested_setup"
    const val ProtectionCoach = "protection_coach"
    const val ProtectionCoachSuggestion = "protection_coach_suggestion/{suggestionId}"
    const val ProtectionTransition = "protection_transition"
    const val Tips = "tips"
    const val TipDetail = "tip/{tipId}"
    const val ImpulsiveBlock = "impulsive_block/{sourcePackageName}/{sourceLabel}"
    const val AdaptiveMoment =
        "adaptive_moment/{decisionId}?triggeringPackageName={triggeringPackageName}"

    /**
     * Automatic game-only entry for a protected app/site interruption.
     *
     * Deliberately separate from [AdaptiveMoment] so protected routing is
     * auditable and process recovery can never land back on the questionnaire.
     */
    const val ProtectedMoment =
        "protected_moment/{decisionId}?triggeringPackageName={triggeringPackageName}"
    const val AdaptiveExplanation = "adaptive_explanation/{decisionId}"
    const val AdaptiveGame = "adaptive_game/{decisionId}"
    const val AdaptiveReading = "adaptive_reading/{decisionId}"
    const val MomentPlanRun = "moment_plan_run/{decisionId}"
    const val AdaptiveFeedback = "adaptive_feedback/{decisionId}"

    fun journalNoteNew(type: JournalNoteType): String = "journal_note_new/${type.storageValue}"
    fun journalNoteEdit(noteId: Long): String = "journal_note_edit/$noteId"
    fun momentPlanEditor(planId: String? = null): String =
        planId?.let { "moment_plan_editor?planId=${Uri.encode(it)}" } ?: "moment_plan_editor"
    fun momentPlanDetail(planId: String): String =
        "moment_plan_detail/${Uri.encode(planId)}"
    fun momentPlanRehearsal(rehearsalId: String): String =
        "moment_plan_rehearsal/${Uri.encode(rehearsalId)}"
    fun impulsiveBlock(sourcePackageName: String, sourceLabel: String): String =
        "impulsive_block/${Uri.encode(sourcePackageName)}/${Uri.encode(sourceLabel)}"
    fun randomRecoveryGame(sourcePackageName: String): String =
        "random_recovery_game/${Uri.encode(sourcePackageName)}"
    fun adaptiveMoment(
        decisionId: String,
        triggeringPackageName: String? = null,
    ): String = buildString {
        append("adaptive_moment/")
        append(Uri.encode(decisionId))
        triggeringPackageName?.takeIf(String::isNotBlank)?.let {
            append("?triggeringPackageName=")
            append(Uri.encode(it))
        }
    }
    fun protectedMoment(
        decisionId: String,
        triggeringPackageName: String? = null,
    ): String = buildString {
        append("protected_moment/")
        append(Uri.encode(decisionId))
        triggeringPackageName?.takeIf(String::isNotBlank)?.let {
            append("?triggeringPackageName=")
            append(Uri.encode(it))
        }
    }
    fun adaptiveExplanation(decisionId: String): String =
        "adaptive_explanation/${Uri.encode(decisionId)}"
    fun adaptiveGame(decisionId: String): String =
        "adaptive_game/${Uri.encode(decisionId)}"
    fun adaptiveReading(decisionId: String): String =
        "adaptive_reading/${Uri.encode(decisionId)}"
    fun momentPlanRun(decisionId: String): String =
        "moment_plan_run/${Uri.encode(decisionId)}"
    fun adaptiveFeedback(decisionId: String): String =
        "adaptive_feedback/${Uri.encode(decisionId)}"
    fun protectionCoachSuggestion(suggestionId: String): String =
        "protection_coach_suggestion/${Uri.encode(suggestionId)}"
    fun tipDetail(tipId: ImpulsiveTipId): String =
        "tip/${Uri.encode(tipId.value)}"
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
    val restoredAccountMigrationState by
        onboardingViewModel.restoredAccountMigrationState.collectAsStateWithLifecycle()
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
    val adaptiveScope = rememberCoroutineScope()
    val rehearsalLauncher: MomentPlanRehearsalLauncherViewModel = viewModel()
    // Shared between the Safe Browse unlock screen and the secured browser destination --
    // never a second, independent access ledger.
    val safeBrowseAccessViewModel: SafeBrowseAccessViewModel = viewModel(
        factory = SafeBrowseAccessViewModelFactory(context.applicationContext),
    )
    // One app-wide UMP consent manager, provided below to every destination in this NavHost
    // -- never a second, independent instance racing the same ConsentInformation singleton.
    val applicationContext = context.applicationContext
    val safeBrowseConsentManager = remember(applicationContext) {
        com.impulsive.app.frontend.ads.SafeBrowseConsentManagerProvider.get(applicationContext)
    }
    val safeBrowseConsentActivity = context as? Activity
    LaunchedEffect(safeBrowseConsentManager, safeBrowseConsentActivity) {
        safeBrowseConsentActivity?.let(safeBrowseConsentManager::requestConsentInfoUpdate)
    }
    val adaptiveOutcomeCoordinator = remember(context) {
        AdaptivePhase4Dependencies.outcomeCoordinator(context)
    }
    val adaptivePendingFeedbackCoordinator = remember(context) {
        AdaptivePhase4Dependencies.pendingFeedbackCoordinator(context)
    }
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
    val retentionDecisionId =
        if (
            bottomNavCurrentRoute in setOf(
                AppRoutes.AdaptiveMoment,
                AppRoutes.AdaptiveExplanation,
                AppRoutes.AdaptiveGame,
                AppRoutes.AdaptiveReading,
                AppRoutes.MomentPlanRun,
                AppRoutes.AdaptiveFeedback,
            )
        ) {
            Uri.decode(
                bottomNavCurrentEntry?.arguments?.getString("decisionId").orEmpty(),
            ).takeIf(String::isNotBlank)
        } else {
            null
        }
    DisposableEffect(retentionDecisionId, bottomNavCurrentRoute) {
        retentionDecisionId?.let(AdaptiveRetentionRuntimeState::enterDecisionRoute)
        if (
            retentionDecisionId != null &&
            bottomNavCurrentRoute == AppRoutes.AdaptiveFeedback
        ) {
            AdaptiveRetentionRuntimeState.markFeedbackPresented(retentionDecisionId)
        }
        onDispose {
            retentionDecisionId?.let(AdaptiveRetentionRuntimeState::leaveDecisionRoute)
        }
    }
    val screenPrivacyViewModel: AdaptivePreferencesViewModel = viewModel()
    val screenPrivacyState by
        screenPrivacyViewModel.state.collectAsStateWithLifecycle()
    val privateContentReady = rememberRouteSensitiveScreenPrivacyReady(
        routePattern = bottomNavCurrentRoute,
        enabled = screenPrivacyState.preferences.privateScreenProtectionEnabled,
    )
    val latestProtectionSetupState by rememberUpdatedState(protectionSetupState)
    val startupGraphDecision = chooseStartupGraph(
        isCompleted = state.isCompleted,
        completedAccountUid = state.completedAccountUid,
        authenticatedUid = authState.user?.uid,
        authenticatedIsGuest =
            authState.user?.provider == AuthProvider.Guest,
    )
    val mainGraphAllowed = startupGraphDecision == StartupGraphDecision.Main

    LaunchedEffect(
        bottomNavCurrentRoute,
        initialBlockRequest,
        mainGraphAllowed,
    ) {
        if (
            mainGraphAllowed &&
            bottomNavCurrentRoute == AppRoutes.Home &&
            initialBlockRequest == null
        ) {
            val pending = adaptivePendingFeedbackCoordinator
                .claimMostRecentEligible(
                    com.impulsive.app.backend.session.adaptive.AdaptivePendingFeedbackSafety(
                        protectionOverlayVisible = false,
                        activeInterventionRunning = false,
                        appLockPending = false,
                    ),
                )
            if (pending != null) {
                navController.navigate(
                    AppRoutes.adaptiveFeedback(pending.decisionId),
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

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
            websiteProtectionEnabled = setup.websiteProtectionRuntimeEnabled,
            transitionCompleted = setup.protectionMonitorTransitionCompleted,
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
        ImpulsiveLoadingSurface(
            blockRequestPending = initialBlockRequest != null,
        )
        return
    }

    // Keep the protection monitor running whenever the user has protected apps
    // configured. Without this the service only ever started on reboot, on a
    // focus session, or via a manual Settings button, so the block screen,
    // persistent notification, and one-minute timer never appeared on a normal
    // app open. Re-runs when the protected list changes. Start is idempotent.
    LaunchedEffect(
        protectionSetupState.appProtectionMonitorEnabled,
        protectionSetupState.protectionMonitorTransitionCompleted,
        protectionSetupState.selectedBlockedAppPackageNames,
        protectionSetupState.usageAccessEnabled,
        protectionSetupState.interruptionPermissionEnabled,
        protectionSetupState.websiteProtectionEnabled,
        protectionSetupState
            .websiteProtectionDisclosureConsentVersion,
    ) {
        // Website protection depends on the monitor too: the DNS filter tunnel
        // is only synced from inside AppMonitorService. Gating this start on
        // blocked apps alone left website-only users with a dead filter after
        // every reboot, with no recovery on app open.
        val protectionConfigured = shouldRecoverProtectionService(
            appProtectionEnabled = protectionSetupState.appProtectionMonitorEnabled,
            selectedPackages = protectionSetupState.selectedBlockedAppPackageNames,
            usageAccessGranted = protectionSetupState.usageAccessEnabled,
            websiteProtectionEnabled = protectionSetupState.websiteProtectionRuntimeEnabled,
            transitionCompleted = protectionSetupState.protectionMonitorTransitionCompleted,
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

    LaunchedEffect(
        initialBlockRequest,
        mainGraphAllowed,
        bottomNavCurrentEntry,
    ) {
        val request = initialBlockRequest
        val currentEntry = bottomNavCurrentEntry
        if (
            request != null &&
            mainGraphAllowed &&
            currentEntry != null
        ) {
            val currentRoutePattern = currentEntry.destination.route
            val currentSourcePackageName =
                if (
                    currentRoutePattern == AppRoutes.RandomRecoveryGame ||
                    currentRoutePattern == AppRoutes.ImpulsiveBlock
                ) {
                    Uri.decode(
                        currentEntry.arguments
                            ?.getString("sourcePackageName")
                            .orEmpty(),
                    )
                } else {
                    null
                }
            val currentSourceLabel =
                if (currentRoutePattern == AppRoutes.ImpulsiveBlock) {
                    Uri.decode(
                        currentEntry.arguments
                            ?.getString("sourceLabel")
                            .orEmpty(),
                    )
                } else {
                    null
                }
            val currentAdaptiveDecisionId =
                if (currentRoutePattern == AppRoutes.AdaptiveMoment) {
                    Uri.decode(
                        currentEntry.arguments
                            ?.getString("decisionId")
                            .orEmpty(),
                    )
                } else {
                    null
                }

            if (
                !blockRequestDestinationMatches(
                    currentRoutePattern = currentRoutePattern,
                    currentSourcePackageName = currentSourcePackageName,
                    currentSourceLabel = currentSourceLabel,
                    currentAdaptiveDecisionId = currentAdaptiveDecisionId,
                    request = request,
                )
            ) {
                navController.navigate(blockRequestDestinationRoute(request)) {
                    launchSingleTop = true
                }
            }
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

    fun persistAdaptiveOutcome(
        decisionId: String,
        completed: Boolean,
        openFeedback: Boolean,
    ) {
        if (decisionId.isBlank()) return
        adaptiveScope.launch {
            val result = if (completed) {
                adaptiveOutcomeCoordinator.complete(decisionId)
            } else {
                adaptiveOutcomeCoordinator.dismiss(decisionId)
            }
            AdaptiveRetentionRuntimeState.clearPendingNavigation(decisionId)
            if (
                openFeedback &&
                (
                    result ==
                        com.impulsive.app.backend.session.adaptive.AdaptiveOutcomeResult.Applied ||
                        result ==
                        com.impulsive.app.backend.session.adaptive.AdaptiveOutcomeResult.Idempotent
                    )
            ) {
                navController.navigate(AppRoutes.adaptiveFeedback(decisionId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    /**
     * Resumes an existing protected support step.
     *
     * Only a game may be resumed inside a protected Moment. A legacy cycle whose
     * step is a Short Pause, Reading or Moment Plan is deliberately refused here
     * so the obsolete intervention is never restarted; the caller then exits
     * safely instead.
     */
    fun routeProtectedMomentInternal(request: AdaptiveRouteRequest): Boolean {
        val supportLaunch = request.gameLaunchContext as?
            RecoveryGameLaunchContext.SupportCycle
        if (request.kind != AdaptiveRouteKind.Game || supportLaunch == null) return false
        if (
            request.decisionId != supportLaunch.decisionId ||
            supportLaunch.cycleId.isBlank() ||
            supportLaunch.decisionId.isBlank() ||
            supportLaunch.maxDurationMillis <= 0L
        ) {
            return false
        }

        AdaptiveRetentionRuntimeState.markPendingNavigation(supportLaunch.decisionId)
        navController.navigate(recoveryGameRoute(supportLaunch.gameType, asTask = true)) {
            launchSingleTop = true
            popUpTo(AppRoutes.ProtectedMoment) { inclusive = true }
        }
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.apply {
                set(AdaptiveDecisionIdStateKey, supportLaunch.decisionId)
                set(AdaptiveSupportCycleIdStateKey, supportLaunch.cycleId)
                set(AdaptiveSupportCycleMaxDurationStateKey, supportLaunch.maxDurationMillis)
            }
        return true
    }

    fun routeAdaptiveInternal(
        request: AdaptiveRouteRequest,
        replaceAdaptiveGame: Boolean,
    ): Boolean = when (request.kind) {
        AdaptiveRouteKind.AdaptiveMoment -> {
            if (request.decisionId.isBlank()) {
                false
            } else {
                AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
                navController.navigate(AppRoutes.adaptiveMoment(request.decisionId)) {
                    launchSingleTop = true
                    if (replaceAdaptiveGame) {
                        popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                    }
                }
                true
            }
        }
        AdaptiveRouteKind.Game -> {
            val supportLaunch = request.gameLaunchContext as?
                RecoveryGameLaunchContext.SupportCycle

            if (supportLaunch != null) {
                if (
                    request.decisionId != supportLaunch.decisionId ||
                    supportLaunch.cycleId.isBlank() ||
                    supportLaunch.decisionId.isBlank() ||
                    supportLaunch.maxDurationMillis <= 0L
                ) {
                    false
                } else {
                    AdaptiveRetentionRuntimeState.markPendingNavigation(supportLaunch.decisionId)

                    navController.navigate(recoveryGameRoute(supportLaunch.gameType, asTask = true)) {
                        launchSingleTop = true
                        if (replaceAdaptiveGame) {
                            popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                        }
                    }

                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.apply {
                            set(AdaptiveDecisionIdStateKey, supportLaunch.decisionId)
                            set(AdaptiveSupportCycleIdStateKey, supportLaunch.cycleId)
                            set(
                                AdaptiveSupportCycleMaxDurationStateKey,
                                supportLaunch.maxDurationMillis,
                            )
                        }

                    true
                }
            } else {
                AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
                navController.navigate(AppRoutes.adaptiveGame(request.decisionId)) {
                    launchSingleTop = true
                    if (replaceAdaptiveGame) {
                        popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                    }
                }
                true
            }
        }
        AdaptiveRouteKind.Reading -> {
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            navController.navigate(AppRoutes.adaptiveReading(request.decisionId)) {
                launchSingleTop = true
                if (replaceAdaptiveGame) {
                    popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                }
            }
            true
        }
        AdaptiveRouteKind.MomentPlan -> {
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            navController.navigate(AppRoutes.momentPlanRun(request.decisionId)) {
                launchSingleTop = true
                if (replaceAdaptiveGame) {
                    popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                }
            }
            true
        }
        AdaptiveRouteKind.Focus -> {
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            navController.navigate(AppRoutes.Focus) { launchSingleTop = true }
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(AdaptiveDecisionIdStateKey, request.decisionId)
            true
        }
        AdaptiveRouteKind.Journal -> {
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            navController.navigate(AppRoutes.JournalHub) { launchSingleTop = true }
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(AdaptiveDecisionIdStateKey, request.decisionId)
            true
        }
        AdaptiveRouteKind.ExternalApplication -> {
            val packageName = request.opaqueTarget ?: return false
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            runCatching {
                launchIntent.replaceExtras(android.os.Bundle())
                context.startActivity(launchIntent)
            }.onSuccess {
                adaptiveScope.launch { markAdaptiveStarted(context, request.decisionId) }
            }.isSuccess
        }
        AdaptiveRouteKind.Feedback -> {
            AdaptiveRetentionRuntimeState.markPendingNavigation(request.decisionId)
            navController.navigate(AppRoutes.adaptiveFeedback(request.decisionId)) {
                launchSingleTop = true
            }
            true
        }
    }

    fun routeAdaptive(request: AdaptiveRouteRequest): Boolean = routeAdaptiveInternal(
        request = request,
        replaceAdaptiveGame = false,
    )

    androidx.compose.runtime.CompositionLocalProvider(
        com.impulsive.app.frontend.ads.LocalSafeBrowseConsentManager provides safeBrowseConsentManager,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                    onConfirmSameGoogleRestore = {
                        onboardingViewModel.confirmRestoredSameGoogleIdentity(
                            onReady = {
                                pendingAccountDecision = null
                                resolveOnboardingForAuthenticatedAccount()
                            },
                            onLegacyCloudVerificationRequired = {
                                pendingAccountDecision =
                                    AuthenticatedOnboardingNavigationDecision
                                        .ShowRestoredLegacyDriveVerification
                            },
                        )
                    },
                    sameGoogleRestoreInProgress =
                        restoredAccountMigrationState is
                            RestoredAccountMigrationUiState.Restoring,
                    onCloudRestoreRequiresOnboardingSetup = {
                        pendingAccountDecision = null
                        navController.navigateOnboarding(
                            OnboardingRoutes.WelcomePrivacy,
                        )
                    },
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

                RestoredAccountMigrationMessage(
                    state = restoredAccountMigrationState,
                    onDismiss =
                        onboardingViewModel::
                            dismissRestoredAccountMigrationMessage,
                    onUseAnotherAccount = {
                        pendingAccountDecision = null
                        authViewModel.signOut()
                        navController.navigateOnboarding(
                            OnboardingRoutes.LoginSignupGuest,
                        )
                    },
                )

                LaunchedEffect(restoredAccountMigrationState) {
                    if (
                        restoredAccountMigrationState is
                        RestoredAccountMigrationUiState.RefreshPending
                    ) {
                        Toast.makeText(
                            context,
                            "Your data is ready. Encrypted backup refresh will retry automatically.",
                            Toast.LENGTH_LONG,
                        ).show()
                        onboardingViewModel
                            .dismissRestoredAccountMigrationMessage()
                    }
                }

                LaunchedEffect(onboardingAccountResolutionState) {
                    val message =
                        when (onboardingAccountResolutionState) {
                            OnboardingAccountResolutionState
                                .CloudRestoreRefreshPending ->
                                "Your restored data is ready. Backup refresh will retry automatically."
                            OnboardingAccountResolutionState
                                .CloudRecoverySetupRequired ->
                                "Your restored data is ready. Re-enable encrypted cloud recovery in Settings."
                            OnboardingAccountResolutionState.Idle,
                            OnboardingAccountResolutionState.Loading,
                            is OnboardingAccountResolutionState
                                .RetryableFailure,
                            -> null
                        }
                    if (message != null) {
                        Toast.makeText(
                            context,
                            message,
                            Toast.LENGTH_LONG,
                        ).show()
                        onboardingViewModel
                            .clearAccountResolutionFailure()
                    }
                }

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
            startDestination = AppRoutes.Home,
        ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onOpenRecoveryGames = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.RecoveryGames)
                    },
                    onOpenJournal = dropUnlessResumed {
                        navController.navigateMainTop(AppRoutes.JournalList)
                    },
                    onOpenSnakeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SnakeGameTask)
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
                    onOpenSafeBrowse = dropUnlessResumed {
                        navController.navigate(AppRoutes.SafeBrowse) {
                            launchSingleTop = true
                        }
                    },
                    onOpenMomentPlans = dropUnlessResumed {
                        navController.navigate(AppRoutes.MomentPlanList) {
                            launchSingleTop = true
                        }
                    },
                    onOpenTips = dropUnlessResumed {
                        navController.navigate(AppRoutes.Tips) {
                            launchSingleTop = true
                        }
                    },
                    onOpenTip = { tipId ->
                        navController.navigate(AppRoutes.tipDetail(tipId)) {
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

            composable(AppRoutes.Focus) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                BackHandler(enabled = adaptiveDecisionId != null) {
                    persistAdaptiveOutcome(
                        adaptiveDecisionId.orEmpty(),
                        completed = false,
                        openFeedback = true,
                    )
                }
                FocusScreen(
                    onOpenHome = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.navigateMainTop(AppRoutes.Home)
                        }
                    },
                    onOpenScore = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.navigateMainTop(AppRoutes.Score)
                        }
                    },
                    onOpenSettings = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.navigateMainTop(AppRoutes.Settings)
                        }
                    },
                    onOpenTasks = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.navigate(AppRoutes.TaskToComplete)
                        }
                    },
                    adaptiveMomentPlan = adaptiveDecisionId != null,
                    onAdaptiveCompleted = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(
                                id,
                                completed = true,
                                openFeedback = true,
                            )
                        }
                    },
                    indicatorState = bottomNavIndicatorState,
                    isActive = bottomNavCurrentRoute == AppRoutes.Focus,
                )
            }

            composable(AppRoutes.FocusRecovery) { backStackEntry ->
                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.FocusRecovery,
                    expectedRoutePattern = AppRoutes.FocusRecovery,
                    currentRoutePattern = backStackEntry.destination.route,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )
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

                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.RandomRecoveryGame,
                    expectedRoutePattern = AppRoutes.RandomRecoveryGame,
                    currentRoutePattern = backStackEntry.destination.route,
                    sourcePackageName = sourcePackageName,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )

                LaunchedEffect(sourcePackageName, initialBlockRequest) {
                    if (
                        initialBlockRequest?.launchTarget ==
                        BlockLaunchTarget.RandomRecoveryGame &&
                        initialBlockRequest.sourcePackageName == sourcePackageName
                    ) {
                        return@LaunchedEffect
                    }
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
                    onOpenSnakeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SnakeGameTask)
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
                    onOpenSnakeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SnakeGameTask)
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
                    onOpenWhatWorksForMe = {
                        navController.navigate(AppRoutes.WhatWorksForMe) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPrivacyAndData = {
                        navController.navigate(AppRoutes.PersonalSupportPrivacy) {
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

            composable(AppRoutes.MomentPlanList) {
                MomentPlanListScreen(
                    onBack = { navController.popBackStack() },
                    onCreate = {
                        navController.navigate(AppRoutes.momentPlanEditor())
                    },
                    onOpen = { planId ->
                        navController.navigate(AppRoutes.momentPlanDetail(planId))
                    },
                    onEdit = { planId ->
                        navController.navigate(AppRoutes.momentPlanEditor(planId))
                    },
                    onPractise = { planId ->
                        rehearsalLauncher.startGuided(planId) { rehearsalId ->
                            navController.navigate(
                                AppRoutes.momentPlanRehearsal(rehearsalId),
                            ) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(
                route = AppRoutes.MomentPlanEditor,
                arguments = listOf(
                    navArgument("planId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                MomentPlanEditorScreen(
                    onBack = { navController.popBackStack() },
                    onPractise = { planId ->
                        rehearsalLauncher.startGuided(planId) { rehearsalId ->
                            navController.navigate(
                                AppRoutes.momentPlanRehearsal(rehearsalId),
                            ) {
                                popUpTo(AppRoutes.MomentPlanEditor) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onSaved = { planId ->
                        navController.navigate(AppRoutes.momentPlanDetail(planId)) {
                            popUpTo(AppRoutes.MomentPlanEditor) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = AppRoutes.MomentPlanDetail,
                arguments = listOf(
                    navArgument("planId") {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val planId = Uri.decode(
                    backStackEntry.arguments?.getString("planId").orEmpty(),
                )
                MomentPlanDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = {
                        navController.navigate(AppRoutes.momentPlanEditor(planId))
                    },
                    onPractise = {
                        rehearsalLauncher.startQuick(planId) { rehearsalId ->
                            navController.navigate(
                                AppRoutes.momentPlanRehearsal(rehearsalId),
                            ) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onDeleted = {
                        navController.navigate(AppRoutes.MomentPlanList) {
                            popUpTo(AppRoutes.MomentPlanList) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = AppRoutes.MomentPlanRehearsal,
                arguments = listOf(
                    navArgument("rehearsalId") {
                        type = NavType.StringType
                    },
                ),
            ) {
                MomentPlanRehearsalScreen(
                    onFinished = { navController.popBackStack() },
                )
            }

            composable(AppRoutes.WhatWorksForMe) {
                WhatWorksForMeScreen(
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.HowSuggestionsWork) {
                HowSuggestionsWorkScreen(
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.PersonalSupportSuggestions) {
                PersonalSupportSuggestionPreferencesScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenHowSuggestionsWork = {
                        navController.navigate(AppRoutes.HowSuggestionsWork) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(AppRoutes.PersonalSupportPrivacy) {
                val familiarStepHistoryViewModel: FamiliarStepHistoryViewModel = viewModel()

                PersonalSupportPrivacyAndDataScreen(
                    onBack = { navController.safePopBackStack() },
                    familiarStepHistoryViewModel = familiarStepHistoryViewModel,
                )
            }

            composable(AppRoutes.Tips) {
                val tipsViewModel: TipsViewModel = viewModel()
                val tipsState by tipsViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.answers) {
                    tipsViewModel.updateContext(state.answers)
                    tipsViewModel.ensureHomeTip()
                }
                TipsScreen(
                    state = tipsState,
                    onOpenTip = { tipId ->
                        navController.navigate(AppRoutes.tipDetail(tipId)) {
                            launchSingleTop = true
                        }
                    },
                    onResetHiddenTips = tipsViewModel::resetHiddenTips,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = AppRoutes.TipDetail,
                arguments = listOf(navArgument("tipId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val tipsViewModel: TipsViewModel = viewModel()
                val tipsState by tipsViewModel.state.collectAsStateWithLifecycle()
                val rawTipId = Uri.decode(backStackEntry.arguments?.getString("tipId").orEmpty())
                val tipId = runCatching { ImpulsiveTipId(rawTipId) }.getOrNull()
                val tip = tipId?.let(tipsViewModel::findTip)
                LaunchedEffect(state.answers, tipId) {
                    tipsViewModel.updateContext(state.answers)
                    if (tipId != null) tipsViewModel.markViewed(tipId)
                    if (tip == null) {
                        navController.navigate(AppRoutes.Tips) {
                            popUpTo(AppRoutes.TipDetail) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                tip?.let { selectedTip ->
                    TipDetailScreen(
                        tip = selectedTip,
                        whyYouAreSeeingThis = tipsState.whyYouAreSeeingThis
                            .takeIf { tipsState.currentTip?.id == selectedTip.id },
                        onAction = { action ->
                            when (action) {
                                TipAction.None -> Unit
                                is TipAction.OpenAndroidSetting -> runCatching {
                                    context.startActivity(
                                        Intent(action.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                                is TipAction.OpenImpulsiveFeature -> {
                                    val route = when (action.feature) {
                                        TipFeature.AppProtection -> AppRoutes.ProtectionSetupGuideBlockedApps
                                        TipFeature.MomentPlan -> AppRoutes.MomentPlanList
                                        TipFeature.ProtectionSchedule -> AppRoutes.ProtectionSetupGuide
                                        TipFeature.WebsiteProtection -> AppRoutes.WebsiteProtectionPlus
                                        TipFeature.ResetReading -> AppRoutes.ResetReadTask
                                        TipFeature.Focus -> AppRoutes.Focus
                                        TipFeature.WhatWorksForMe -> AppRoutes.WhatWorksForMe
                                        else -> null
                                    }
                                    route?.let { navController.navigate(it) { launchSingleTop = true } }
                                }
                            }
                        },
                        onShowAnother = {
                            tipsViewModel.rotate()
                            navController.navigate(AppRoutes.Tips) {
                                popUpTo(AppRoutes.TipDetail) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onDismiss = {
                            tipsViewModel.dismiss(selectedTip.id)
                            navController.navigate(AppRoutes.Tips) {
                                popUpTo(AppRoutes.TipDetail) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(AppRoutes.SuggestedSetup) {
                val coachViewModel: ProtectionCoachViewModel = viewModel()
                val coachState by coachViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(coachState.loading, coachState.activeTimingSuggestion) {
                    if (!coachState.loading) {
                        val destination = if (coachState.activeTimingSuggestion != null) {
                            AppRoutes.ProtectionCoach
                        } else {
                            AppRoutes.ProtectionSetupGuide
                        }
                        navController.navigate(destination) {
                            popUpTo(AppRoutes.SuggestedSetup) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }

            composable(AppRoutes.ProtectionCoach) {
                val coachViewModel: ProtectionCoachViewModel = viewModel()
                val coachState by coachViewModel.state.collectAsStateWithLifecycle()
                ProtectionCoachScreen(
                    state = coachState,
                    onReviewTime = {
                        navController.navigate(AppRoutes.ProtectionSetupGuide) {
                            launchSingleTop = true
                        }
                    },
                    onDismiss = coachViewModel::dismiss,
                    onSuppress = coachViewModel::suppress,
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(
                route = AppRoutes.ProtectionCoachSuggestion,
                arguments = listOf(navArgument("suggestionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val suggestionId = Uri.decode(
                    backStackEntry.arguments?.getString("suggestionId").orEmpty(),
                )
                val coachViewModel: ProtectionCoachViewModel = viewModel()
                val coachState by coachViewModel.state.collectAsStateWithLifecycle()
                val activeSuggestion = coachState.activeTimingSuggestion
                    ?.takeIf { it.suggestionId.value == suggestionId }
                LaunchedEffect(coachState.loading, activeSuggestion, suggestionId) {
                    if (!coachState.loading && activeSuggestion == null) {
                        navController.navigate(AppRoutes.ProtectionCoach) {
                            popUpTo(AppRoutes.ProtectionCoachSuggestion) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                activeSuggestion?.let {
                    ProtectionCoachSuggestionScreen(
                        suggestionId = suggestionId,
                        onReviewTime = {
                            navController.navigate(AppRoutes.ProtectionSetupGuide) {
                                launchSingleTop = true
                            }
                        },
                        onDismiss = { id ->
                            coachViewModel.dismiss(id)
                            navController.safePopBackStack()
                        },
                        onSuppress = { id ->
                            coachViewModel.suppress(id)
                            navController.safePopBackStack()
                        },
                        onBack = { navController.safePopBackStack() },
                    )
                }
            }

            composable(AppRoutes.ProtectionTransition) {
                ProtectionTransitionScreen(
                    onKeepProtection = {
                        adaptiveScope.launch {
                            protectionSetupViewModel
                                .setProtectionMonitorTransitionCompleted(true)
                            navController.safePopBackStack()
                        }
                    },
                    onReviewProtectedApps = {
                        navController.navigate(AppRoutes.ProtectionSetupGuideBlockedApps) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.PathShift) {
                PathShiftScreen(
                    onBack = { navController.safePopBackStack() },
                    onOpenSettings = {
                        navController.navigateMainTop(AppRoutes.Settings)
                    },
                    onViewPlan = { planId ->
                        navController.navigate(AppRoutes.momentPlanDetail(planId)) {
                            launchSingleTop = true
                        }
                    },
                    onPractisePlan = { planId ->
                        rehearsalLauncher.startGuided(planId) { rehearsalId ->
                            navController.navigate(
                                AppRoutes.momentPlanRehearsal(rehearsalId),
                            ) {
                                launchSingleTop = true
                            }
                        }
                    },
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
                val dnsFilterGateViewModel: DnsFilterGateViewModel = viewModel()
                val websiteSetupState by
                    protectionSetupViewModel
                        .websiteSetupState
                        .collectAsStateWithLifecycle()
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
                    websiteSetupState = websiteSetupState,
                    onRefreshWebsiteSetup =
                        protectionSetupViewModel::refreshWebsiteProtectionSetupState,
                    onWebsiteSetupAction = { action ->
                        when (action) {
                            WebsiteProtectionNextAction.SelectBrowser,
                            WebsiteProtectionNextAction.ChooseSupportedBrowser,
                            -> {
                                navController.navigate(AppRoutes.WebsiteProtectionApps) {
                                    launchSingleTop = true
                                }
                            }

                            /*
                             * DnsFilterGate remains the sole user-facing
                             * affirmative consent flow: the card never sets
                             * disclosure acceptance directly.
                             */
                            WebsiteProtectionNextAction.ReviewDisclosure,
                            WebsiteProtectionNextAction.RequestVpnPermission,
                            -> {
                                navController.navigate(AppRoutes.DnsFilterGate) {
                                    launchSingleTop = true
                                }
                            }

                            WebsiteProtectionNextAction.OpenVpnSettings -> {
                                runCatching {
                                    context.startActivity(
                                        dnsFilterGateViewModel.vpnSettingsIntent(),
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "VPN settings could not be opened.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }

                            WebsiteProtectionNextAction.OpenPrivateDnsSettings -> {
                                runCatching {
                                    context.startActivity(
                                        dnsFilterGateViewModel.privateDnsSettingsIntent(),
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Private DNS settings could not be opened.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }

                            WebsiteProtectionNextAction.RetryCapabilityCheck -> {
                                protectionSetupViewModel.refreshWebsiteProtectionSetupState()
                            }

                            WebsiteProtectionNextAction.None -> Unit
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
                    websiteProtectionEnableIntent =
                        protectionSetupState.websiteProtectionEnabled,
                    websiteProtectionRuntimeEnabled =
                        protectionSetupState.websiteProtectionRuntimeEnabled,
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

            composable(AppRoutes.SafeBrowse) {
                SafeBrowseRoute(
                    accessViewModel = safeBrowseAccessViewModel,
                    billingManager = billingManager,
                    onBack = { navController.safePopBackStack() },
                    onOpenBrowser = {
                        navController.navigate(AppRoutes.SafeBrowseBrowser) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSafeBrowsePass = {
                        navController.navigate(AppRoutes.SafeBrowsePass) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(AppRoutes.SafeBrowseBrowser) {
                SafeBrowseBrowserRoute(
                    accessViewModel = safeBrowseAccessViewModel,
                    onExit = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.SafeBrowsePass) {
                val context = LocalContext.current
                val safeBrowsePassViewModel: SafeBrowsePassViewModel = viewModel(
                    factory = SafeBrowsePassViewModelFactory(
                        billingManager,
                    ),
                )
                val purchaseAccountGatePhase = resolvePurchaseAccountGatePhase(
                    user = authState.user,
                    inFlightProvider = authState.inFlightProvider,
                    pendingEmailVerificationAddress =
                        authState.pendingEmailVerificationAddress,
                    hasAccountConflict = authState.pendingAccountConflict != null,
                )

                SafeBrowsePassRoute(
                    passViewModel = safeBrowsePassViewModel,
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
                    onBack = { navController.safePopBackStack() },
                )
            }
            composable(AppRoutes.DnsFilterGate) {
                val dnsFilterGateViewModel: DnsFilterGateViewModel = viewModel()
                val dnsFilterGateState by dnsFilterGateViewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val dnsGateScope =
                    rememberCoroutineScope()
                var dnsGateContinueInProgress by
                    remember {
                        mutableStateOf(
                            false,
                        )
                    }
                var dnsGateDisclosureSaveFailed by
                    remember {
                        mutableStateOf(
                            false,
                        )
                    }

                suspend fun enableAndStartWebsiteProtection() {
                    val enabled =
                        protectionSetupViewModel
                            .enableWebsiteProtectionAfterDisclosure()

                    if (!enabled) {
                        dnsGateDisclosureSaveFailed =
                            true

                        dnsGateContinueInProgress =
                            false

                        return
                    }

                    ImpulsiveVpnController
                        .start(
                            context,
                        )

                    dnsGateContinueInProgress =
                        false

                    navController
                        .safePopBackStack()
                }

                val vpnConsentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        dnsGateScope.launch {
                            enableAndStartWebsiteProtection()
                        }
                    } else {
                        dnsGateContinueInProgress =
                            false
                    }
                }
                DnsFilterGateScreen(
                    state =
                        dnsFilterGateState,
                    protectedBrowserPackageNames =
                        protectionSetupState
                            .websiteProtectedAppPackageNames,
                    websiteProtectionDisclosureAccepted =
                        protectionSetupState
                            .websiteProtectionDisclosureAccepted,
                    continueInProgress =
                        dnsGateContinueInProgress,
                    disclosureSaveFailed =
                        dnsGateDisclosureSaveFailed,
                    onOpenPrivateDnsSettings = {
                        context.startActivity(dnsFilterGateViewModel.privateDnsSettingsIntent())
                    },
                    onRefresh = {
                        dnsFilterGateViewModel.refresh()
                        protectionSetupViewModel.refreshWebsiteProtectionSetupState()
                    },
                    onContinue = { needsDisclosurePersistence ->
                        if (
                            dnsGateContinueInProgress
                        ) {
                            return@DnsFilterGateScreen
                        }

                        dnsGateContinueInProgress =
                            true

                        dnsGateDisclosureSaveFailed =
                            false

                        dnsGateScope.launch {
                            if (
                                needsDisclosurePersistence
                            ) {
                                val accepted =
                                    protectionSetupViewModel
                                        .acceptCurrentWebsiteProtectionDisclosure()

                                if (!accepted) {
                                    dnsGateDisclosureSaveFailed =
                                        true

                                    dnsGateContinueInProgress =
                                        false

                                    return@launch
                                }
                            }

                            val systemConsent =
                                ImpulsiveVpnController
                                    .consentIntent(
                                        context,
                                    )

                            if (
                                systemConsent != null
                            ) {
                                vpnConsentLauncher
                                    .launch(
                                        systemConsent,
                                    )
                            } else {
                                enableAndStartWebsiteProtection()
                            }
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
                    onOpenSnake = dropUnlessResumed { navController.navigate(AppRoutes.SnakeGame) },
                    onOpenBlockCascade = dropUnlessResumed { navController.navigate(AppRoutes.BlockCascadeGame) },
                    onOpenSkylineReset = dropUnlessResumed { navController.navigate(AppRoutes.SkylineResetGame) },
                    onOpenRhythmTiles = dropUnlessResumed { navController.navigate(AppRoutes.RhythmTilesGame) },
                )
            }

            composable(AppRoutes.SnakeGame) {
                SnakeGameScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.Snake,
                        asTask = false,
                    ),
                    launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
                )
            }

            composable(AppRoutes.SnakeGameTask) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                SnakeGameScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveExit = adaptiveDecisionId?.let { id ->
                        { completed ->
                            persistAdaptiveOutcome(id, completed, openFeedback = true)
                        }
                    },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.Snake,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                    gameLaunchContext = supportCycleGameLaunchContext(
                        backStackEntry,
                        com.impulsive.app.backend.domain.model.score.ScoreGameType.Snake,
                    ),
                )
            }

            // LEGACY UPGRADE COMPATIBILITY ONLY - not reachable from active UI.
            composable(AppRoutes.LegacyReflexGame) {
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

            // LEGACY UPGRADE COMPATIBILITY ONLY - not reachable from active UI.
            composable(AppRoutes.LegacyReflexGameTask) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                ReflexGameScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveCompleted = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(id, completed = true, openFeedback = false)
                        }
                    },
                    onAdaptiveExit = adaptiveDecisionId?.let { id ->
                        { completed ->
                            persistAdaptiveOutcome(id, completed, openFeedback = true)
                        }
                    },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                    gameLaunchContext = supportCycleGameLaunchContext(
                        backStackEntry,
                        com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
                    ),
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

            composable(AppRoutes.BlockCascadeTask) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                BlockCascadeScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveCompleted = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(id, completed = true, openFeedback = false)
                        }
                    },
                    onAdaptiveExit = adaptiveDecisionId?.let { id ->
                        { completed ->
                            persistAdaptiveOutcome(id, completed, openFeedback = true)
                        }
                    },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                    gameLaunchContext = supportCycleGameLaunchContext(
                        backStackEntry,
                        com.impulsive.app.backend.domain.model.score.ScoreGameType.BlockCascade,
                    ),
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

            composable(AppRoutes.SkylineResetTask) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                SkylineResetScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveCompleted = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(id, completed = true, openFeedback = false)
                        }
                    },
                    onAdaptiveExit = adaptiveDecisionId?.let { id ->
                        { completed ->
                            persistAdaptiveOutcome(id, completed, openFeedback = true)
                        }
                    },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                    gameLaunchContext = supportCycleGameLaunchContext(
                        backStackEntry,
                        com.impulsive.app.backend.domain.model.score.ScoreGameType.SkylineReset,
                    ),
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

            composable(AppRoutes.RhythmTilesTask) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                RhythmTilesScreen(
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveCompleted = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(id, completed = true, openFeedback = false)
                        }
                    },
                    onAdaptiveExit = adaptiveDecisionId?.let { id ->
                        { completed ->
                            persistAdaptiveOutcome(id, completed, openFeedback = true)
                        }
                    },
                    onPlayAnother = rememberPlayAnotherGame(
                        navController = navController,
                        current = com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles,
                        asTask = true,
                    ),
                    launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
                    gameLaunchContext = supportCycleGameLaunchContext(
                        backStackEntry,
                        com.impulsive.app.backend.domain.model.score.ScoreGameType.RhythmTiles,
                    ),
                )
            }

            composable(AppRoutes.ResetReadTask) {
                ResetReadScreen(
                    launchMode = ResetReadLaunchMode.Normal,
                    onExit = { navController.safePopBackStack() },
                )
            }

            composable(AppRoutes.ResetReadFallbackTask) { backStackEntry ->
                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.ReadingReset,
                    expectedRoutePattern = AppRoutes.ResetReadFallbackTask,
                    currentRoutePattern = backStackEntry.destination.route,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )
                ResetReadScreen(
                    launchMode = ResetReadLaunchMode.Fallback,
                    onExit = { navController.exitRecoveryFlowSafely() },
                )
            }

            composable(AppRoutes.JournalHub) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                AdaptiveStartedEffect(
                    adaptiveDecisionId,
                )
                JournalHubScreen(
                    onBack = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.safePopBackStack()
                        }
                    },
                    onOpenNormalJournal = {
                        navController.navigate(AppRoutes.JournalList)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, adaptiveDecisionId)
                    },
                    onCreateNote = { type ->
                        navController.navigate(AppRoutes.journalNoteNew(type))
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, adaptiveDecisionId)
                    },
                    onOpenNote = { noteId ->
                        navController.navigate(AppRoutes.journalNoteEdit(noteId))
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, adaptiveDecisionId)
                    },
                )
            }

            composable(AppRoutes.JournalList) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                JournalListScreen(
                    onBack = {
                        if (adaptiveDecisionId != null) {
                            persistAdaptiveOutcome(
                                adaptiveDecisionId.orEmpty(),
                                completed = false,
                                openFeedback = true,
                            )
                        } else {
                            navController.safePopBackStack()
                        }
                    },
                    onCreateNote = { type ->
                        navController.navigate(AppRoutes.journalNoteNew(type))
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, adaptiveDecisionId)
                    },
                    onOpenNote = { noteId ->
                        navController.navigate(AppRoutes.journalNoteEdit(noteId))
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, adaptiveDecisionId)
                    },
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
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                JournalEditorScreen(
                    noteId = 0L,
                    initialType = noteType,
                    onBack = { navController.safePopBackStack() },
                    onAdaptiveSaved = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(
                                id,
                                completed = true,
                                openFeedback = true,
                            )
                        }
                    },
                )
            }

            composable(
                route = AppRoutes.JournalNoteEdit,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val adaptiveDecisionId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(AdaptiveDecisionIdStateKey, null)
                    .collectAsStateWithLifecycle()
                JournalEditorScreen(
                    noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L,
                    initialType = JournalNoteType.Text,
                    onBack = { navController.safePopBackStack() },
                    onAdaptiveSaved = adaptiveDecisionId?.let { id ->
                        {
                            persistAdaptiveOutcome(
                                id,
                                completed = true,
                                openFeedback = true,
                            )
                        }
                    },
                )
            }

            composable(
                route = AppRoutes.AdaptiveMoment,
                arguments = listOf(
                    navArgument("decisionId") { type = NavType.StringType },
                    navArgument("triggeringPackageName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val decisionId = Uri.decode(
                    backStackEntry.arguments?.getString("decisionId").orEmpty(),
                )
                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.AdaptiveMoment,
                    expectedRoutePattern = AppRoutes.AdaptiveMoment,
                    currentRoutePattern = backStackEntry.destination.route,
                    adaptiveDecisionId = decisionId,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )
                AdaptiveMomentScreen(
                    onRoute = ::routeAdaptive,
                    onExplain = { decisionId ->
                        navController.navigate(AppRoutes.adaptiveExplanation(decisionId))
                    },
                    onSafeExit = { navController.navigateBackToHome() },
                )
            }

            /*
             * Automatic game-only entry for a protected app/site.
             *
             * This is a bootstrap, not a screen: it resolves the authoritative
             * decision and Support Cycle, resumes an existing game step or starts
             * one, and navigates on. It renders only a matching dark background so
             * the hand-off from the protection bridge does not flash, and it never
             * presents a questionnaire, a picker or a choice. Any failure exits
             * safely rather than falling back to the old menus.
             */
            composable(
                route = AppRoutes.ProtectedMoment,
                arguments = listOf(
                    navArgument("decisionId") { type = NavType.StringType },
                    navArgument("triggeringPackageName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val decisionId = Uri.decode(
                    backStackEntry.arguments?.getString("decisionId").orEmpty(),
                )
                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.ProtectedMoment,
                    expectedRoutePattern = AppRoutes.ProtectedMoment,
                    currentRoutePattern = backStackEntry.destination.route,
                    adaptiveDecisionId = decisionId,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ProtectedMomentBootstrapBackground),
                )
                LaunchedEffect(decisionId) {
                    try {
                        val decision = AdaptivePhase4Dependencies.decisions(context)
                            .getById(decisionId)
                            ?: error("Adaptive decision is unavailable")
                        val coordinator =
                            com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleDependencies
                                .coordinator(context)
                        val activeResult = coordinator.createOrRecover(decision)

                        val activeState = when (activeResult) {
                            is com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult.Active ->
                                activeResult.state
                            is com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult.ExistingActive ->
                                activeResult.state
                            /*
                             * Another decision already owns the single active cycle.
                             * Resume that authoritative lifecycle instead of opening a
                             * second one.
                             */
                            is com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult.ActiveDecisionConflict ->
                                activeResult.state
                            else -> error("Support cycle is unavailable")
                        }

                        /*
                         * An existing step -- in progress, or a game result awaiting
                         * its presentation -- is authoritative. Resume it rather than
                         * selecting or starting anything new.
                         */
                        if (
                            com.impulsive.app.backend.session.adaptive
                                .AdaptiveSupportCycleResumePolicy
                                .requiresResumeBeforeStartingGame(activeState)
                        ) {
                            val resumeRequest =
                                com.impulsive.app.backend.session.adaptive
                                    .AdaptiveSupportCycleResumePolicy
                                    .target(activeState)
                                    .toRouteRequest()

                            check(
                                routeProtectedMomentInternal(resumeRequest),
                            ) {
                                "Existing support-cycle step could not be resumed"
                            }

                            return@LaunchedEffect
                        }

                        val chosenGame = selectAndRecordGuidedGame(
                            context = context,
                            sourcePackageName = "protected_moment",
                        )
                        val started = coordinator.startGame(
                            cycleId = activeState.cycle.cycleId,
                            gameType = chosenGame,
                            requestedDurationMillis =
                                com.impulsive.app.backend.domain.model.adaptive
                                    .AdaptiveSupportCycleTiming.TotalDurationMillis,
                            minimumUsefulDurationMillis = 10_000L,
                        ) as? com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
                            ?: error("Support game could not start")
                        val launch = started.launch
                        navController.navigate(recoveryGameRoute(chosenGame, asTask = true)) {
                            launchSingleTop = true
                            popUpTo(AppRoutes.ProtectedMoment) { inclusive = true }
                        }
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.apply {
                                set(AdaptiveDecisionIdStateKey, launch.decisionId)
                                set(AdaptiveSupportCycleIdStateKey, launch.cycleId)
                                set(
                                    AdaptiveSupportCycleMaxDurationStateKey,
                                    launch.maxDurationMillis,
                                )
                            }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Never fall back to the questionnaire, Reading or a picker.
                        navController.navigateBackToHome()
                    }
                }
            }

            composable(
                route = AppRoutes.AdaptiveExplanation,
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
            ) {
                AdaptiveDecisionExplanationScreen(
                    onBack = { navController.safePopBackStack() },
                )
            }

            composable(
                route = AppRoutes.AdaptiveGame,
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val decisionId = Uri.decode(
                    backStackEntry.arguments?.getString("decisionId").orEmpty(),
                )
                LaunchedEffect(decisionId) {
                    try {
                        val decision = AdaptivePhase4Dependencies.decisions(context)
                            .getById(decisionId)
                            ?: error("Adaptive decision is unavailable")
                        val coordinator =
                            com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleDependencies
                                .coordinator(context)
                        val activeResult = coordinator.createOrRecover(decision)

                        if (
                            activeResult is
                            com.impulsive.app.backend.session.adaptive
                                .AdaptiveSupportCycleCommandResult
                                .ActiveDecisionConflict
                        ) {
                            val resumeRequest =
                                com.impulsive.app.backend.session.adaptive
                                    .AdaptiveSupportCycleResumePolicy
                                    .target(activeResult.state)
                                    .toRouteRequest()

                            check(
                                routeAdaptiveInternal(
                                    request = resumeRequest,
                                    replaceAdaptiveGame = true,
                                ),
                            ) {
                                "Existing support cycle could not be resumed"
                            }

                            return@LaunchedEffect
                        }

                        val activeState = when (activeResult) {
                            is com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult.Active ->
                                activeResult.state
                            is com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult.ExistingActive ->
                                activeResult.state
                            else -> error("Support cycle is unavailable")
                        }
                        check(activeState.cycle.decisionId == decisionId) {
                            "Support cycle belongs to another adaptive decision"
                        }

                        /*
                         * Any existing current step must resume through the authoritative shared
                         * policy. This includes terminal game steps whose result presentation
                         * needs restoration.
                         */
                        if (
                            com.impulsive.app.backend.session.adaptive
                                .AdaptiveSupportCycleResumePolicy
                                .requiresResumeBeforeStartingGame(activeState)
                        ) {
                            val resumeRequest =
                                com.impulsive.app.backend.session.adaptive
                                    .AdaptiveSupportCycleResumePolicy
                                    .target(activeState)
                                    .toRouteRequest()

                            check(
                                routeAdaptiveInternal(
                                    request = resumeRequest,
                                    replaceAdaptiveGame = true,
                                ),
                            ) {
                                "Existing support-cycle step could not be resumed"
                            }

                            return@LaunchedEffect
                        }

                        val chosenGame = selectAndRecordGuidedGame(
                            context = context,
                            sourcePackageName = "adaptive",
                        )
                        val started = coordinator.startGame(
                            cycleId = activeState.cycle.cycleId,
                            gameType = chosenGame,
                            requestedDurationMillis = 90_000L,
                            minimumUsefulDurationMillis = 10_000L,
                        ) as? com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
                            ?: error("Support game could not start")
                        val launch = started.launch
                        navController.navigate(recoveryGameRoute(chosenGame, asTask = true)) {
                            launchSingleTop = true
                            popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                        }
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveDecisionIdStateKey, launch.decisionId)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveSupportCycleIdStateKey, launch.cycleId)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(AdaptiveSupportCycleMaxDurationStateKey, launch.maxDurationMillis)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        navController.navigate(AppRoutes.adaptiveMoment(decisionId)) {
                            launchSingleTop = true
                            popUpTo(AppRoutes.AdaptiveGame) { inclusive = true }
                        }
                    }
                }
            }

            composable(
                route = AppRoutes.AdaptiveReading,
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val decisionId = Uri.decode(
                    backStackEntry.arguments?.getString("decisionId").orEmpty(),
                )
                AdaptiveStartedEffect(decisionId)
                ResetReadScreen(
                    launchMode = ResetReadLaunchMode.Fallback,
                    onExit = { navController.exitRecoveryFlowSafely() },
                    onAdaptiveCompleted = {
                        persistAdaptiveOutcome(
                            decisionId,
                            completed = true,
                            openFeedback = false,
                        )
                    },
                    onAdaptiveExit = { completed ->
                        persistAdaptiveOutcome(
                            decisionId,
                            completed = completed,
                            openFeedback = true,
                        )
                    },
                )
            }

            composable(
                route = AppRoutes.MomentPlanRun,
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val decisionId = Uri.decode(
                    backStackEntry.arguments?.getString("decisionId").orEmpty(),
                )
                MomentPlanRunScreen(
                    onChooseAnother = {
                        adaptiveScope.launch {
                            val current = adaptiveOutcomeCoordinator.load(decisionId)
                            if (
                                current?.startedAtMillis != null &&
                                current.completedAtMillis == null &&
                                current.dismissedAtMillis == null
                            ) {
                                adaptiveOutcomeCoordinator.dismiss(decisionId)
                            }
                            navController.navigate(AppRoutes.adaptiveMoment(decisionId)) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onRoute = ::routeAdaptive,
                    onSafeExit = { navController.navigateBackToHome() },
                )
            }

            composable(
                route = AppRoutes.AdaptiveFeedback,
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
            ) {
                AdaptiveFeedbackScreen(
                    onDone = { navController.navigateBackToHome() },
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
                BlockRequestDestinationReadyEffect(
                    pendingRequest = initialBlockRequest,
                    expectedTarget = BlockLaunchTarget.BlockScreen,
                    expectedRoutePattern = AppRoutes.ImpulsiveBlock,
                    currentRoutePattern = backStackEntry.destination.route,
                    sourcePackageName = sourcePackageName,
                    sourceLabel = sourceLabel,
                    onBlockRequestConsumed = onBlockRequestConsumed,
                )
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
                    onOpenSnakeTask = dropUnlessResumed {
                        navController.navigate(AppRoutes.SnakeGameTask)
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
        if (!privateContentReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
    }
}

private const val AdaptiveDecisionIdStateKey = "adaptiveDecisionId"
private const val AdaptiveSupportCycleIdStateKey = "adaptiveSupportCycleId"
private const val AdaptiveSupportCycleMaxDurationStateKey = "adaptiveSupportCycleMaxDurationMillis"

private fun supportCycleGameLaunchContext(
    backStackEntry: androidx.navigation.NavBackStackEntry,
    gameType: com.impulsive.app.backend.domain.model.score.ScoreGameType,
): RecoveryGameLaunchContext = supportCycleGameLaunchContext(
    cycleId = backStackEntry.savedStateHandle.get(AdaptiveSupportCycleIdStateKey),
    decisionId = backStackEntry.savedStateHandle.get(AdaptiveDecisionIdStateKey),
    maxDurationMillis = backStackEntry.savedStateHandle.get(AdaptiveSupportCycleMaxDurationStateKey),
    gameType = gameType,
)

internal fun supportCycleGameLaunchContext(
    cycleId: String?,
    decisionId: String?,
    maxDurationMillis: Long?,
    gameType: com.impulsive.app.backend.domain.model.score.ScoreGameType,
): RecoveryGameLaunchContext {
    val safeCycleId = cycleId.orEmpty()
    val safeDecisionId = decisionId.orEmpty()
    val safeMaxDurationMillis = maxDurationMillis ?: 0L
    val hasNoSupportCycleState =
        safeCycleId.isBlank() && safeDecisionId.isBlank() && safeMaxDurationMillis == 0L
    return if (!hasNoSupportCycleState) {
        require(
            safeCycleId.isNotBlank() &&
                safeDecisionId.isNotBlank() &&
                safeMaxDurationMillis > 0L,
        ) {
            "Incomplete support-cycle launch context"
        }
        RecoveryGameLaunchContext.SupportCycle(
            cycleId = safeCycleId,
            decisionId = safeDecisionId,
            gameType = gameType,
            maxDurationMillis = safeMaxDurationMillis,
        )
    } else {
        RecoveryGameLaunchContext.Standalone
    }
}

@Composable
private fun ImpulsiveLoadingSurface(
    blockRequestPending: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                if (blockRequestPending) {
                    "Opening your reset…"
                } else {
                    "Loading Impulsive…"
                },
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun BlockRequestDestinationReadyEffect(
    pendingRequest: BlockRequest?,
    expectedTarget: BlockLaunchTarget,
    expectedRoutePattern: String,
    currentRoutePattern: String?,
    sourcePackageName: String? = null,
    sourceLabel: String? = null,
    adaptiveDecisionId: String? = null,
    onBlockRequestConsumed: () -> Unit,
) {
    var lastReadyRequest by remember { mutableStateOf<BlockRequest?>(null) }
    val latestRequest by rememberUpdatedState(pendingRequest)
    val latestOnBlockRequestConsumed by rememberUpdatedState(onBlockRequestConsumed)

    LaunchedEffect(
        pendingRequest,
        currentRoutePattern,
        sourcePackageName,
        sourceLabel,
        adaptiveDecisionId,
    ) {
        val request = pendingRequest ?: return@LaunchedEffect
        val matchesDestination =
            request.launchTarget == expectedTarget &&
                currentRoutePattern == expectedRoutePattern &&
                (sourcePackageName == null || request.sourcePackageName == sourcePackageName) &&
                (sourceLabel == null || request.sourceLabel == sourceLabel) &&
                (
                    adaptiveDecisionId == null ||
                        request.adaptiveDecisionId == adaptiveDecisionId
                    )

        if (!matchesDestination || lastReadyRequest == request) {
            return@LaunchedEffect
        }

        withFrameNanos { }

        if (latestRequest == request && lastReadyRequest != request) {
            lastReadyRequest = request
            latestOnBlockRequestConsumed()
        }
    }
}

internal fun blockRequestDestinationRoute(request: BlockRequest): String =
    when (request.launchTarget) {
        BlockLaunchTarget.FocusRecovery -> AppRoutes.FocusRecovery
        BlockLaunchTarget.AdaptiveMoment ->
            request.adaptiveDecisionId
                ?.let { decisionId ->
                    AppRoutes.adaptiveMoment(
                        decisionId = decisionId,
                        triggeringPackageName = request.sourcePackageName,
                    )
                }
                ?: AppRoutes.impulsiveBlock(
                    request.sourcePackageName,
                    request.sourceLabel,
                )
        /*
         * A protected interruption without a usable decision must not fall back
         * to the questionnaire; the block screen is the safe destination.
         */
        BlockLaunchTarget.ProtectedMoment ->
            request.adaptiveDecisionId
                ?.let { decisionId ->
                    AppRoutes.protectedMoment(
                        decisionId = decisionId,
                        triggeringPackageName = request.sourcePackageName,
                    )
                }
                ?: AppRoutes.impulsiveBlock(
                    request.sourcePackageName,
                    request.sourceLabel,
                )
        BlockLaunchTarget.RandomRecoveryGame ->
            AppRoutes.randomRecoveryGame(request.sourcePackageName)
        BlockLaunchTarget.ReadingReset -> AppRoutes.ResetReadFallbackTask
        BlockLaunchTarget.BlockScreen -> AppRoutes.impulsiveBlock(
            sourcePackageName = request.sourcePackageName,
            sourceLabel = request.sourceLabel,
        )
    }

internal fun blockRequestDestinationRoutePattern(request: BlockRequest): String =
    when (request.launchTarget) {
        BlockLaunchTarget.FocusRecovery -> AppRoutes.FocusRecovery
        BlockLaunchTarget.AdaptiveMoment -> AppRoutes.AdaptiveMoment
        BlockLaunchTarget.ProtectedMoment -> AppRoutes.ProtectedMoment
        BlockLaunchTarget.RandomRecoveryGame -> AppRoutes.RandomRecoveryGame
        BlockLaunchTarget.ReadingReset -> AppRoutes.ResetReadFallbackTask
        BlockLaunchTarget.BlockScreen -> AppRoutes.ImpulsiveBlock
    }

/**
 * Task-mode game routes a protected Support Cycle can already be running in.
 */
/** Matches the protection bridge so the hand-off does not flash. */
private val ProtectedMomentBootstrapBackground = Color(0xFF120E18)

private val ProtectedMomentGameRoutePatterns = setOf(
    AppRoutes.BlockCascadeTask,
    AppRoutes.SkylineResetTask,
    AppRoutes.RhythmTilesTask,
    AppRoutes.SnakeGameTask,
    AppRoutes.LegacyReflexGameTask,
)

internal fun blockRequestDestinationMatches(
    currentRoutePattern: String?,
    currentSourcePackageName: String?,
    currentSourceLabel: String?,
    request: BlockRequest,
    currentAdaptiveDecisionId: String? = null,
): Boolean =
    when (request.launchTarget) {
        BlockLaunchTarget.AdaptiveMoment ->
            currentRoutePattern == AppRoutes.AdaptiveMoment &&
                currentAdaptiveDecisionId == request.adaptiveDecisionId

        /*
         * A duplicate protected intent must not interrupt a Support Cycle that
         * is already running: the bootstrap route and the game it launched both
         * count as already satisfying this request.
         */
        BlockLaunchTarget.ProtectedMoment ->
            (
                currentRoutePattern == AppRoutes.ProtectedMoment &&
                    currentAdaptiveDecisionId == request.adaptiveDecisionId
                ) ||
                currentRoutePattern in ProtectedMomentGameRoutePatterns

        BlockLaunchTarget.RandomRecoveryGame ->
            currentRoutePattern == AppRoutes.RandomRecoveryGame &&
                currentSourcePackageName == request.sourcePackageName

        BlockLaunchTarget.BlockScreen ->
            currentRoutePattern == AppRoutes.ImpulsiveBlock &&
                currentSourcePackageName == request.sourcePackageName &&
                currentSourceLabel == request.sourceLabel

        BlockLaunchTarget.ReadingReset ->
            currentRoutePattern == AppRoutes.ResetReadFallbackTask

        BlockLaunchTarget.FocusRecovery ->
            currentRoutePattern == AppRoutes.FocusRecovery
    }

@Composable
private fun AdaptiveStartedEffect(decisionId: String?) {
    val context = LocalContext.current
    LaunchedEffect(decisionId) {
        if (decisionId.isNullOrBlank()) return@LaunchedEffect
        withFrameNanos { }
        markAdaptiveStarted(context, decisionId)
    }
}

private suspend fun markAdaptiveStarted(context: Context, decisionId: String) {
    if (decisionId.isBlank()) return
    val decisions = AdaptivePhase4Dependencies.decisions(context)
    val current = decisions.getById(decisionId) ?: return
    if (current.startedAtMillis != null) return
    AdaptivePhase4Dependencies.lifecycle(context).markStarted(
        decisionId,
        System.currentTimeMillis(),
    )
}

@Composable
private fun CloudRestoreDialog(
    onDismiss: () -> Unit,
    onReadyForHome: () -> Unit,
    onRequiresOnboardingSetup: () -> Unit,
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

    var ownerConfirmationKind by
        remember {
            mutableStateOf<CloudRecoveryOwnerConfirmationKind?>(null)
        }

    var ownerConfirmation by
        remember {
            mutableStateOf<CloudRecoveryOwnerConfirmation>(CloudRecoveryOwnerConfirmation.None)
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

    fun resetOwnerConfirmation() {
        ownerConfirmationKind = null
        ownerConfirmation = CloudRecoveryOwnerConfirmation.None
    }

    fun restore(
        replace:
            Boolean,
        ownerConfirmation: CloudRecoveryOwnerConfirmation = CloudRecoveryOwnerConfirmation.None,
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
                val result = coordinator.restore(
                    downloadedEnvelope =
                        bytes,

                    password =
                        passwordChars,

                    replaceExistingData =
                        replace,

                    ownerConfirmation = ownerConfirmation,
                )
            ) {
                CloudRecoveryRestoreResult.Success -> {
                    clearEnvelope()
                    resetOwnerConfirmation()
                    dispatchCloudRestoreSuccess(
                        requiresOnboardingSetup = false,
                        onReadyForHome = onReadyForHome,
                        onRequiresOnboardingSetup =
                            onRequiresOnboardingSetup,
                    )
                }

                CloudRecoveryRestoreResult.SuccessBackupRefreshPending -> {
                    clearEnvelope()

                    Toast.makeText(
                        context,
                        "Your data was restored and cloud recovery backup is on. " +
                            "A backup refresh will be requested again when your data changes.",
                        Toast.LENGTH_LONG,
                    ).show()

                    resetOwnerConfirmation()
                    dispatchCloudRestoreSuccess(
                        requiresOnboardingSetup = false,
                        onReadyForHome = onReadyForHome,
                        onRequiresOnboardingSetup =
                            onRequiresOnboardingSetup,
                    )
                }
                CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed -> {
                    clearEnvelope()

                    Toast.makeText(
                        context,
                        "Your recovery data was restored, but automatic " +
                            "cloud recovery backup could not be re-enabled. " +
                            "Turn it on again in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()

                    resetOwnerConfirmation()
                    dispatchCloudRestoreSuccess(
                        requiresOnboardingSetup = false,
                        onReadyForHome = onReadyForHome,
                        onRequiresOnboardingSetup =
                            onRequiresOnboardingSetup,
                    )
                }

                CloudRecoveryRestoreResult.SuccessRequiresOnboardingSetup -> {
                    clearEnvelope()
                    resetOwnerConfirmation()
                    dispatchCloudRestoreSuccess(
                        requiresOnboardingSetup = true,
                        onReadyForHome = onReadyForHome,
                        onRequiresOnboardingSetup =
                            onRequiresOnboardingSetup,
                    )
                }

                CloudRecoveryRestoreResult
                    .SuccessRequiresOnboardingSetupCloudRecoverySetupFailed -> {
                    clearEnvelope()
                    resetOwnerConfirmation()
                    Toast.makeText(
                        context,
                        "Your recovery data was restored, but automatic cloud " +
                            "recovery could not be re-enabled. Finish setting up " +
                            "this device, then turn cloud recovery on in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                    dispatchCloudRestoreSuccess(
                        requiresOnboardingSetup = true,
                        onReadyForHome = onReadyForHome,
                        onRequiresOnboardingSetup =
                            onRequiresOnboardingSetup,
                    )
                }

                CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending -> {
                    clearEnvelope()
                    resetOwnerConfirmation()
                    message =
                        "Your recovery data was restored, but Impulsive could not " +
                            "finish binding it to the signed-in account. Keep this " +
                            "account signed in and try recovery again."
                }

                CloudRecoveryRestoreResult.IncorrectPassword -> {
                    message =
                        "Incorrect recovery password."
                }

                CloudRecoveryRestoreResult.AccountMismatch -> {
                    resetOwnerConfirmation()
                    message =
                        "This recovery backup belongs to a different " +
                            "Impulsive account."
                }

                is CloudRecoveryRestoreResult.OwnerMigrationConfirmationRequired -> {
                    ownerConfirmationKind = result.kind
                }

                CloudRecoveryRestoreResult.ReplacementConfirmationRequired -> {
                    showReplace =
                        true
                }

                CloudRecoveryRestoreResult.InvalidBackup -> {
                    message =
                        "This cloud recovery backup is not valid."
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
            String?,
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
                    resetOwnerConfirmation()

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
                        "No cloud recovery backup was found for Impulsive."
                }

                CloudRecoveryRestoreDiscovery.AuthorizationRequired -> {
                    message =
                        "Google Drive authorization is required to access your recovery backup."
                }

                CloudRecoveryRestoreDiscovery.TemporarilyUnavailable -> {
                    message =
                        "Cloud recovery is temporarily unavailable. " +
                            "Check your connection and try again."
                }

                CloudRecoveryRestoreDiscovery.InvalidBackup -> {
                    message =
                        "The cloud recovery backup is not valid."
                }

                CloudRecoveryRestoreDiscovery.NotSignedIn,
                CloudRecoveryRestoreDiscovery.GuestNotSupported,
                CloudRecoveryRestoreDiscovery.Failed -> {
                    message =
                        "Could not access cloud recovery."
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
            resetOwnerConfirmation()

            onDismiss()
        },

        title = {
            Text(
                "Restore from cloud backup",
            )
        },

        text = {
            Text(
                "Restore your encrypted cloud recovery copy.",
            )
        },

        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        if (!coordinator.requiresDriveAuthorization()) {
                            discover(null)
                            return@launch
                        }

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
                    resetOwnerConfirmation()

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
                resetOwnerConfirmation()
            },

            title = {
                Text(
                    "Enter recovery password",
                )
            },

            text = {
                Column {
                    Text(
                        "Enter the password you created when you turned on cloud recovery backup. " +
                            "Impulsive does not store " +
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
                                ownerConfirmation = ownerConfirmation,
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
                        resetOwnerConfirmation()
                    },
                ) {
                    Text(
                        "Cancel",
                    )
                }
            },
        )
    }

    val pendingOwnerConfirmationKind = ownerConfirmationKind
    if (pendingOwnerConfirmationKind != null) {
        AlertDialog(
            onDismissRequest = { resetOwnerConfirmation() },
            title = { Text("Restore saved data?") },
            text = {
                Text(
                    cloudRecoveryOwnerConfirmationCopy(
                        pendingOwnerConfirmationKind,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ownerConfirmationKind = null
                    ownerConfirmation =
                        cloudRecoveryOwnerConfirmationFor(
                            pendingOwnerConfirmationKind,
                        )
                    showPassword = true
                }) { Text("Restore data") }
            },
            dismissButton = {
                TextButton(onClick = { resetOwnerConfirmation() }) {
                    Text("Cancel")
                }
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
                resetOwnerConfirmation()
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
                            ownerConfirmation = ownerConfirmation,
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
                    "Cloud recovery",
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
private fun RestoredAccountMigrationMessage(
    state: RestoredAccountMigrationUiState,
    onDismiss: () -> Unit,
    onUseAnotherAccount: () -> Unit,
) {
    when (state) {
        RestoredAccountMigrationUiState.Idle,
        RestoredAccountMigrationUiState.Restoring,
        RestoredAccountMigrationUiState.RefreshPending,
        RestoredAccountMigrationUiState.LegacyCloudVerificationRequired,
        -> Unit

        RestoredAccountMigrationUiState.OwnershipChanged -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Account ownership changed") },
                text = {
                    Text(
                        "Impulsive could not verify that the restored data still " +
                            "belongs to the currently signed-in account.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = onUseAnotherAccount) {
                        Text("Use another account")
                    }
                },
            )
        }

        RestoredAccountMigrationUiState.ExistingLocalData -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Local data already exists") },
                text = {
                    Text(
                        "Impulsive did not replace the data already on this device " +
                            "or change its account ownership.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = onUseAnotherAccount) {
                        Text("Use another account")
                    }
                },
            )
        }

        RestoredAccountMigrationUiState.InvalidBackup -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restore copy is invalid") },
                text = {
                    Text(
                        "The restored Android backup could not be verified, so " +
                            "Impulsive did not change local account ownership.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = onUseAnotherAccount) {
                        Text("Use another account")
                    }
                },
            )
        }

        is RestoredAccountMigrationUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Restore not finished") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Try again")
                    }
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedOnboardingDecisionDialog(
    decision: AuthenticatedOnboardingNavigationDecision?,
    onTryAgain: () -> Unit,
    onConfirmSameGoogleRestore: () -> Unit,
    sameGoogleRestoreInProgress: Boolean,
    onCloudRestoreRequiresOnboardingSetup: () -> Unit,
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
                        Text("Your account was found, but this device doesn't currently have your saved Impulsive setup. You can restore an encrypted cloud recovery backup, try Android backup again, or set up this device again.")
                        TextButton(onClick = { onTryAgain() }) {
                            Text("Try Android backup again")
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCloudRestore = true }) { Text("Restore from cloud backup") } },
                dismissButton = { TextButton(onClick = onSetUpAgain) { Text("Set up again") } },
            )
            if (showCloudRestore) {
                CloudRestoreDialog(
                    onDismiss = { showCloudRestore = false },
                    onReadyForHome = {
                        showCloudRestore = false
                        onTryAgain()
                    },
                    onRequiresOnboardingSetup = {
                        showCloudRestore = false
                        onCloudRestoreRequiresOnboardingSetup()
                    },
                )
            }
        }
        AuthenticatedOnboardingNavigationDecision.ShowRestoredSameGoogleIdentityConfirmation -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restore saved data?") },
                text = { Text("Android restored Impulsive data from the same linked Google identity, but the Firebase account identifier changed.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            performSameGoogleRestore(
                                onConfirmSameGoogleRestore,
                            )
                        },
                        enabled = sameGoogleRestoreButtonEnabled(
                            sameGoogleRestoreInProgress,
                        ),
                    ) {
                        Text(
                            if (sameGoogleRestoreInProgress) {
                                "Restoring…"
                            } else {
                                "Restore data"
                            },
                        )
                    }
                },
                dismissButton = { TextButton(onClick = onUseAnotherAccount) { Text("Use another account") } },
            )
        }
        AuthenticatedOnboardingNavigationDecision.ShowRestoredLegacyDriveVerification -> {
            var showCloudRestore by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Restore saved data?") },
                text = { Text("This restored local data predates the identity claim. Restore and confirm your encrypted cloud recovery copy before claiming it.") },
                confirmButton = { TextButton(onClick = { showCloudRestore = true }) { Text("Restore from cloud backup") } },
                dismissButton = { TextButton(onClick = onUseAnotherAccount) { Text("Use another account") } },
            )
            if (showCloudRestore) {
                CloudRestoreDialog(
                    onDismiss = { showCloudRestore = false },
                    onReadyForHome = {
                        showCloudRestore = false
                        onTryAgain()
                    },
                    onRequiresOnboardingSetup = {
                        showCloudRestore = false
                        onCloudRestoreRequiresOnboardingSetup()
                    },
                )
            }
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
    com.impulsive.app.backend.domain.model.score.ScoreGameType.Snake ->
        if (asTask) AppRoutes.SnakeGameTask else AppRoutes.SnakeGame
    // Legacy: only for a restored pre-cutover Reflex step.
    com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride ->
        if (asTask) AppRoutes.LegacyReflexGameTask else AppRoutes.LegacyReflexGame
    else ->
        error("Unsupported recovery game route: ${game.id}")
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
    val supportCycleHandOff = remember(context) {
        com.impulsive.app.backend.session.game.RecoveryGameSupportCycleHandOff(
            com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleDependencies
                .coordinator(context),
        )
    }
    val handOffInFlight = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val sessions by scoreRepository.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val urgeEvents by urgeEventRepository.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyServed by servedGamesRepository.served.collectAsStateWithLifecycle(initialValue = emptyList())
    return playAnother@{
        val currentEntry = navController.currentBackStackEntry ?: return@playAnother
        val currentLaunch = runCatching {
            supportCycleGameLaunchContext(currentEntry, current)
        }.getOrElse {
            navController.exitRecoveryFlowSafely()
            return@playAnother
        }
        val chosen = com.impulsive.app.backend.domain.usecase.GameSelectionEngine.selectNextGame(
            sessions = sessions,
            urgeEvents = urgeEvents,
            recentlyServed = recentlyServed,
            excludedGames = setOf(current),
        )
        if (currentLaunch === RecoveryGameLaunchContext.Standalone) {
            if (asTask) {
                scope.launch { servedGamesRepository.recordServed(chosen) }
            }
            navController.navigate(recoveryGameRoute(chosen, asTask)) {
                launchSingleTop = true
                popUpTo(recoveryGameRoute(current, asTask)) { inclusive = true }
            }
            return@playAnother
        }
        if (!handOffInFlight.compareAndSet(false, true)) return@playAnother
        scope.launch {
            try {
                when (val result = supportCycleHandOff.prepareNext(currentLaunch, chosen)) {
                    is com.impulsive.app.backend.session.game.RecoveryGameHandOffResult.Ready -> {
                        val launch = result.launch
                        check(launch.gameType == chosen)
                        servedGamesRepository.recordServed(chosen)
                        navController.navigate(recoveryGameRoute(launch.gameType, asTask = true)) {
                            launchSingleTop = true
                            popUpTo(recoveryGameRoute(current, asTask = true)) { inclusive = true }
                        }
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(AdaptiveDecisionIdStateKey, launch.decisionId)
                            set(AdaptiveSupportCycleIdStateKey, launch.cycleId)
                            set(
                                AdaptiveSupportCycleMaxDurationStateKey,
                                launch.maxDurationMillis,
                            )
                        }
                    }

                    is com.impulsive.app.backend.session.game.RecoveryGameHandOffResult.Standalone ->
                        error("Support-cycle hand-off unexpectedly became standalone")

                    com.impulsive.app.backend.session.game.RecoveryGameHandOffResult.Unavailable ->
                        navController.exitRecoveryFlowSafely()
                }
            } finally {
                handOffInFlight.set(false)
            }
        }
    }
}
