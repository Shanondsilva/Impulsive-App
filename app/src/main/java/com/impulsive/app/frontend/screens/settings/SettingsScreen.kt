package com.impulsive.app.frontend.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import com.impulsive.app.backend.data.UserDataManager
import com.impulsive.app.R
import com.impulsive.app.backend.data.UserDataExporter
import com.impulsive.app.backend.data.restore.ManualBackupManager
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryPreferencesDataSource
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryAccountEligibility
import com.impulsive.app.backend.data.restore.cloud.CloudRecoverySetupCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRecoverySetupResult
import com.impulsive.app.backend.data.restore.cloud.hasValidCloudRecoveryPassword
import com.impulsive.app.backend.data.restore.cloud.DriveAppDataAuthorization
import com.impulsive.app.backend.data.restore.cloud.DriveAuthorizationResult
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryDeletionCoordinator
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryDeletionResult
import com.impulsive.app.backend.data.restore.cloud.currentCloudRecoveryTransportRequiresDriveAuthorization
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryUploadScheduler
import com.impulsive.app.backend.data.restore.cloud.cloudRecoveryTransportKind
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.session.settings.CloudRecoveryBackupUiModel
import com.impulsive.app.backend.session.settings.cloudRecoveryBackupUiModel
import com.impulsive.app.backend.domain.model.legal.ImpulsiveLegalDestination
import com.impulsive.app.backend.domain.model.legal.impulsiveLegalUrl
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.service.billing.BillingRestoreState
import com.impulsive.app.backend.service.billing.SubscriptionCatalogState
import com.impulsive.app.backend.service.billing.activePlaySubscriptionProductId
import com.impulsive.app.backend.service.billing.openGooglePlaySubscriptionManagement
import com.impulsive.app.backend.service.billing.subscriptionPlanDisclosure
import com.impulsive.app.backend.service.billing.subscriptionPlanTitle
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.toTaskRewardState
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.session.auth.AccountDeletionUiState
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.adaptive.AdaptivePreferencesViewModel
import com.impulsive.app.backend.session.adaptive.PersonalSupportControlsViewModel
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.backend.session.premium.PremiumViewModel
import com.impulsive.app.backend.session.protection.ProtectionSetupViewModel
import com.impulsive.app.backend.session.progress.TaperViewModel
import com.impulsive.app.backend.session.settings.AppLockViewModel
import com.impulsive.app.backend.session.settings.AppSettingsViewModel
import com.impulsive.app.backend.session.tasks.TaskRewardViewModel
import com.impulsive.app.backend.session.theme.ThemeViewModel
import com.impulsive.app.backend.service.review.openImpulsivePlayStoreListing
import com.impulsive.app.frontend.screens.lock.AppLockGuardHost
import com.impulsive.app.frontend.screens.lock.SetPinScreen
import com.impulsive.app.frontend.screens.lock.rememberAppLockGuardController
import com.impulsive.app.frontend.screens.protection.BlockedAppsSelectionContent
import com.impulsive.app.core.util.ThemeMode
import com.impulsive.app.frontend.components.AvatarStyle
import com.impulsive.app.frontend.components.BodyModeLockedSheet
import com.impulsive.app.frontend.components.BottomNavBar
import com.impulsive.app.frontend.components.BottomNavIndicatorState
import com.impulsive.app.frontend.components.BottomNavItem
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.MindModeStatusSheet
import com.impulsive.app.frontend.components.ModeSelectionSheet
import com.impulsive.app.frontend.components.SoulModeLockedSheet
import com.impulsive.app.frontend.components.rememberBottomNavIndicatorState
import com.impulsive.app.frontend.theme.ImpulsiveFocusMode
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.ImpulsiveHaptics
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackHome: () -> Unit,
    // Activity-scoped in the real app so Facebook onActivityResult delivery,
    // auth state, and dialogs are shared with MainActivity. The default keeps
    // DemoNavHost compiling; the shared CallbackManager makes even a defaulted
    // instance safe for Facebook login.
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenHome: () -> Unit = onBackHome,
    onOpenScore: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenReflexOverrideTask: () -> Unit = {},
    onOpenBlockCascadeTask: () -> Unit = {},
    onOpenSkylineResetTask: () -> Unit = {},
    onOpenRhythmTilesTask: () -> Unit = {},
    onOpenResetReadTask: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenWebsiteProtectionPlus: () -> Unit = {},
    onOpenMomentPlans: () -> Unit = {},
    onOpenTips: () -> Unit = {},
    onOpenWhatWorksForMe: () -> Unit = {},
    onOpenSuggestionPreferences: () -> Unit = {},
    onOpenPrivacyAndData: () -> Unit = {},
    onOpenProtectionCoach: () -> Unit = {},
    onOpenProtectionSetupGuide: () -> Unit = {},
    onOpenUsageAccessPermission: () -> Unit = {},
    onOpenInterruptionPermission: () -> Unit = {},
    onOpenBackgroundActivityPermission: () -> Unit = {},
    onManageProtectionNotifications: () -> Unit = {},
    billingRestoreState: BillingRestoreState = BillingRestoreState.Idle,
    onRestorePurchases: () -> Unit = {},
    subscriptionCatalogState: SubscriptionCatalogState = SubscriptionCatalogState.Loading,
    onRetryBilling: () -> Unit = {},
    indicatorState: BottomNavIndicatorState = rememberBottomNavIndicatorState(),
    isActive: Boolean = true,
    onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    protectionSetupViewModel: ProtectionSetupViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val homeGuideContext = androidx.compose.ui.platform.LocalContext.current
    val homeGuideStore = remember {
        com.impulsive.app.backend.data.local.preferences.HomeGuideStore(homeGuideContext)
    }
    var homeGuideReplayRequested by remember { mutableStateOf(false) }
    LaunchedEffect(homeGuideReplayRequested) {
        if (homeGuideReplayRequested) {
            homeGuideStore.reset()
            homeGuideReplayRequested = false
            onOpenHome()
        }
    }
    val appSettingsViewModel: AppSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appLockViewModel: AppLockViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val taskRewardViewModel: TaskRewardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val taperViewModel: TaperViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val appLockEnabled by appLockViewModel.enabled.collectAsStateWithLifecycle()
    val taskRewardStoreState by taskRewardViewModel.storeState.collectAsStateWithLifecycle()
    val taperSuggestionsEnabled by taperViewModel.taperSuggestionsEnabled.collectAsStateWithLifecycle()
    val currentLevel = taskRewardStoreState.currentLevel
    val storedMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val selectedMode = if (storedMode == ThemeMode.System) ThemeMode.AsPerTime else storedMode
    val savedProfileName = onboardingState.answers.name.trim()
    val displayName = savedProfileName.ifBlank { "Your profile" }
    val avatar = AvatarStyle.fromId(onboardingState.answers.avatarId)
    val context = LocalContext.current
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val deletionState by authViewModel.deletionState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val protectionSetupState by protectionSetupViewModel.state.collectAsStateWithLifecycle()
    val premiumViewModel: PremiumViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val premiumEntitlement by premiumViewModel.entitlement.collectAsStateWithLifecycle()
    val websiteProtectionPlusUnlocked by remember(premiumViewModel) {
        premiumViewModel.hasFeature(PremiumFeature.VpnWebsiteBlocker)
    }.collectAsStateWithLifecycle()
    val haptics = rememberImpulsiveHaptics(appSettingsState.hapticsEnabled)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showBlockedAppsSheet by remember { mutableStateOf(false) }
    var mindModeSheetVisible by remember { mutableStateOf(false) }
    var modeSelectionSheetVisible by remember { mutableStateOf(false) }
    var bodyModeSheetVisible by remember { mutableStateOf(false) }
    var soulModeSheetVisible by remember { mutableStateOf(false) }
    val bottomNavReservedSpace = 104.dp
    val currentNow = LocalDateTime.now()
    val releasePlan = calculateReleasePlan(
        selectedDailyUrgeCount = onboardingState.answers.dailyRelapseUrgeCount,
        now = currentNow,
        activeDayStart = minuteOfDayToLocalTime(onboardingState.answers.activeDayStartMinute),
        activeDayEnd = minuteOfDayToLocalTime(onboardingState.answers.activeDayEndMinute),
    )
    val taskRewardState = taskRewardStoreState.toTaskRewardState(releasePlan)
    val startRecommendedMindTask = {
        when (taskRewardState.recommendedTaskType) {
            PsychologyTaskType.ReflexOverride -> onOpenReflexOverrideTask()
            PsychologyTaskType.BlockCascade -> onOpenBlockCascadeTask()
            PsychologyTaskType.SkylineReset -> onOpenSkylineResetTask()
            PsychologyTaskType.RhythmTiles -> onOpenRhythmTilesTask()
            PsychologyTaskType.ResetRead -> onOpenResetReadTask()
        }
    }
    val appLockGuard = rememberAppLockGuardController()
    val notificationsAvailable = protectionSetupState.notificationPermissionEnabled
    val backupScope = rememberCoroutineScope()
    val manualBackupManager = remember { ManualBackupManager(context) }
    val cloudRecoveryPreferences = remember(context) {
        CloudRecoveryPreferencesDataSource(context)
    }
    val cloudRecoveryCoordinator = remember(context) {
        CloudRecoverySetupCoordinator(context)
    }
    val driveAppDataAuthorization = remember(context) {
        DriveAppDataAuthorization(context)
    }
    val cloudRecoveryDeletionCoordinator =
        remember(context) {
            CloudRecoveryDeletionCoordinator(
                context,
            )
        }
    var cloudRecoveryDeletionInProgress by
        remember {
            mutableStateOf(false)
        }
    val cloudRecoveryEnabled by cloudRecoveryPreferences.enabled
        .collectAsStateWithLifecycle(initialValue = false)
    val cloudRecoveryBackupMetadata by cloudRecoveryPreferences.backupMetadata
        .collectAsStateWithLifecycle(
            initialValue = com.impulsive.app.backend.data.local.preferences.CloudRecoveryBackupMetadata(
                lastAttemptEpochMillis = null,
                lastSuccessfulBackupEpochMillis = null,
                latestOutcome =
                    com.impulsive.app.backend.data.local.preferences.CloudRecoveryStoredUploadOutcome
                        .NeverAttempted,
            ),
        )
    val cloudRecoveryUploadWorkInfos by remember(context) {
        WorkManager
            .getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(
                CloudRecoveryUploadScheduler.UniqueWorkName,
            )
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasRunningCloudRecoveryUpload =
        cloudRecoveryUploadWorkInfos.any {
            it.state == WorkInfo.State.RUNNING
        }
    val hasQueuedCloudRecoveryUpload =
        cloudRecoveryUploadWorkInfos.any {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
    var cloudRecoverySetupInProgress by remember { mutableStateOf(false) }
    val cloudRecoveryTransportKind =
        cloudRecoveryTransportKind(
            hasGoogleProvider =
                authState.user?.linkedProviders?.contains(AuthProvider.Google) == true,
        )
    val cloudRecoveryBackupStatusUiModel =
        cloudRecoveryBackupUiModel(
            transportKind = cloudRecoveryTransportKind,
            cloudRecoveryEnabled = cloudRecoveryEnabled,
            cloudRecoverySetupInProgress = cloudRecoverySetupInProgress,
            hasQueuedUpload = hasQueuedCloudRecoveryUpload,
            hasRunningUpload = hasRunningCloudRecoveryUpload,
            metadata = cloudRecoveryBackupMetadata,
            nowEpochMillis = System.currentTimeMillis(),
        )
    var showCloudRecoveryPasswordDialog by remember { mutableStateOf(false) }
    var cloudRecoveryMessage by remember { mutableStateOf<String?>(null) }

    fun handleDriveAuthorizationResult(result: DriveAuthorizationResult) {
        cloudRecoverySetupInProgress = false
        when (result) {
            is DriveAuthorizationResult.Authorized -> {
                showCloudRecoveryPasswordDialog = true
            }

            is DriveAuthorizationResult.NeedsUserResolution -> {
                cloudRecoveryMessage = "Google Drive needs another confirmation. Please try again."
            }

            DriveAuthorizationResult.Cancelled -> {
                cloudRecoveryMessage = "Google Drive recovery backup was not enabled."
            }

            is DriveAuthorizationResult.Failed -> {
                cloudRecoveryMessage = "Could not authorize Google Drive recovery backup."
            }
        }
    }

    val driveAuthorizationResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data =
            result.data

        if (
            data == null
        ) {
            cloudRecoverySetupInProgress =
                false

            cloudRecoveryMessage =
                "Google Drive recovery backup was not enabled."
        } else {
            try {
                handleDriveAuthorizationResult(
                    driveAppDataAuthorization
                        .resultFromIntent(
                            data,
                        ),
                )
            } catch (
                error:
                    Throwable,
            ) {
                cloudRecoverySetupInProgress =
                    false

                cloudRecoveryMessage =
                    "Could not complete Google Drive authorization."
            }
        }
    }

    suspend fun deleteDriveRecoveryThenStartAccountDeletion(
        accessToken: String?,
        currentActivity: Activity,
    ) {
        try {
            CloudRecoveryUploadScheduler.cancelAndAwait(context)

            when (
                val deletionResult =
                    cloudRecoveryDeletionCoordinator.deleteAllRecoveryFiles(accessToken)
            ) {
                is CloudRecoveryDeletionResult.Success -> {
                    cloudRecoveryDeletionInProgress = false
                    authViewModel.deleteAccount(currentActivity)
                }

                CloudRecoveryDeletionResult.AuthorizationRequired -> {
                    cloudRecoveryDeletionInProgress = false
                    cloudRecoveryMessage =
                        "Google Drive authorization expired before your encrypted " +
                            "recovery backup could be removed. Your Impulsive " +
                            "account has not been deleted. Please try again."
                }

                is CloudRecoveryDeletionResult.Failed -> {
                    cloudRecoveryDeletionInProgress = false
                    cloudRecoveryMessage =
                        "Could not remove your encrypted recovery " +
                            "backup. Your Impulsive account has not been deleted " +
                            "yet. Check your connection and try again."
                }
            }
        } catch (cancellation: CancellationException) {
            cloudRecoveryDeletionInProgress = false
            throw cancellation
        } catch (error: Throwable) {
            cloudRecoveryDeletionInProgress = false
            cloudRecoveryMessage =
                "Could not remove your encrypted recovery backup. " +
                    "Your Impulsive account has not been deleted yet. Check your " +
                    "connection and try again."
        }
    }

    val driveDeletionAuthorizationLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val currentActivity = activity
            val resultIntent = result.data

            if (currentActivity == null || resultIntent == null) {
                cloudRecoveryDeletionInProgress = false
                cloudRecoveryMessage =
                    "Account deletion was cancelled because your encrypted " +
                        "Google Drive recovery backup was not removed."
            } else {
                when (
                    val authorizationResult =
                        cloudRecoveryDeletionCoordinator.resultFromIntent(resultIntent)
                ) {
                    is DriveAuthorizationResult.Authorized -> {
                        backupScope.launch {
                            deleteDriveRecoveryThenStartAccountDeletion(
                                accessToken = authorizationResult.accessToken,
                                currentActivity = currentActivity,
                            )
                        }
                    }

                    is DriveAuthorizationResult.NeedsUserResolution -> {
                        cloudRecoveryDeletionInProgress = false
                        cloudRecoveryMessage =
                            "Google Drive needs another confirmation before your " +
                                "recovery backup can be removed. Your Impulsive " +
                                "account has not been deleted. Please try again."
                    }

                    DriveAuthorizationResult.Cancelled -> {
                        cloudRecoveryDeletionInProgress = false
                        cloudRecoveryMessage =
                            "Account deletion was cancelled because your encrypted " +
                                "Google Drive recovery backup was not removed."
                    }

                    is DriveAuthorizationResult.Failed -> {
                        cloudRecoveryDeletionInProgress = false
                        cloudRecoveryMessage =
                            "Could not authorize removal of your encrypted Google " +
                                "Drive recovery backup. Your Impulsive account has " +
                                "not been deleted."
                    }
                }
            }
        }

    fun startCloudAwareAccountDeletion() {
        val currentActivity = activity ?: return

        if (
            cloudRecoveryDeletionInProgress ||
                deletionState == AccountDeletionUiState.InProgress
        ) {
            return
        }

        cloudRecoveryDeletionInProgress = true

        backupScope.launch {
            try {
                /*
                 * Local flags cannot reliably indicate whether an encrypted Drive recovery
                 * file exists after reinstall. Always perform the narrowly scoped appDataFolder
                 * search before permanent in-app account deletion.
                 *
                 * CloudRecoveryDeletionCoordinator searches only
                 * CloudRecoveryDriveFileName. Zero files is a successful no-op.
                 */

                CloudRecoveryUploadScheduler.cancelAndAwait(context)

                if (!cloudRecoveryDeletionCoordinator.requiresDriveAuthorization()) {
                    deleteDriveRecoveryThenStartAccountDeletion(
                        accessToken = null,
                        currentActivity = currentActivity,
                    )
                    return@launch
                }

                when (
                    val authorizationResult =
                        cloudRecoveryDeletionCoordinator.requestAuthorization()
                ) {
                    is DriveAuthorizationResult.Authorized -> {
                        deleteDriveRecoveryThenStartAccountDeletion(
                            accessToken = authorizationResult.accessToken,
                            currentActivity = currentActivity,
                        )
                    }

                    is DriveAuthorizationResult.NeedsUserResolution -> {
                        driveDeletionAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(
                                authorizationResult.pendingIntent.intentSender,
                            ).build(),
                        )
                    }

                    DriveAuthorizationResult.Cancelled -> {
                        cloudRecoveryDeletionInProgress = false
                        cloudRecoveryMessage =
                            "Account deletion was cancelled because your encrypted " +
                                "Google Drive recovery backup was not removed."
                    }

                    is DriveAuthorizationResult.Failed -> {
                        cloudRecoveryDeletionInProgress = false
                        cloudRecoveryMessage =
                            "Could not authorize removal of your encrypted Google " +
                                "Drive recovery backup. Your Impulsive account has " +
                                "not been deleted."
                    }
                }
            } catch (cancellation: CancellationException) {
                cloudRecoveryDeletionInProgress = false
                throw cancellation
            } catch (error: Throwable) {
                cloudRecoveryDeletionInProgress = false
                cloudRecoveryMessage =
                    "Could not prepare account deletion. Your Impulsive account " +
                        "has not been deleted. Please try again."
            }
        }
    }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var manualBackupMessage by remember { mutableStateOf<String?>(null) }
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = ""
        if (uri != null && password.isNotBlank()) {
            backupScope.launch {
                val success = runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        manualBackupManager.exportTo(output, password.toCharArray())
                        true
                    } ?: false
                }.getOrDefault(false)
                manualBackupMessage = if (success) {
                    "Backup exported. Keep the file and your password safe. " +
                        "Impulsive cannot recover the backup if you lose the password."
                } else {
                    "Could not export the backup file."
                }
            }
        }
    }
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }
    if (showExportPasswordDialog) {
        ManualBackupPasswordDialog(
            title = "Choose a backup password",
            message = "This password encrypts your backup file. Impulsive does not " +
                "store the file or the password on its servers. If you lose the " +
                "password, nobody, including Impulsive, can recover the backup.",
            confirmLabel = "Continue",
            requireConfirmation = true,
            onConfirm = { password ->
                showExportPasswordDialog = false
                pendingExportPassword = password
                exportDocumentLauncher.launch(ManualBackupManager.SuggestedFileName)
            },
            onDismiss = { showExportPasswordDialog = false },
        )
    }
    pendingImportUri?.let { importUri ->
        ManualBackupPasswordDialog(
            title = "Enter your backup password",
            message = "Enter the password you chose when this backup file was created.",
            confirmLabel = "Import",
            requireConfirmation = false,
            onConfirm = { password ->
                pendingImportUri = null
                backupScope.launch {
                    val result = runCatching {
                        context.contentResolver.openInputStream(importUri)?.use { input ->
                            manualBackupManager.importFrom(input, password.toCharArray())
                        } ?: ManualBackupManager.ImportResult.Error(
                            "Could not open the backup file.",
                        )
                    }.getOrElse { error ->
                        ManualBackupManager.ImportResult.Error(
                            error.localizedMessage?.ifBlank { null }
                                ?: "Could not read the backup file.",
                        )
                    }
                    manualBackupMessage = when (result) {
                        ManualBackupManager.ImportResult.Success ->
                            "Backup imported. Your progress has been restored on this device."
                        ManualBackupManager.ImportResult.WrongPasswordOrCorrupted ->
                            "Wrong password, or the backup file is damaged."
                        ManualBackupManager.ImportResult.UnsupportedVersion ->
                            "This backup file was made with a newer version of Impulsive. " +
                                "Update the app and try again."
                        ManualBackupManager.ImportResult.ExistingDataPresent ->
                            "Import is only available before you start using Impulsive " +
                                "on this device, so existing progress is never duplicated " +
                                "or overwritten."
                        is ManualBackupManager.ImportResult.Error -> result.message
                    }
                }
            },
            onDismiss = { pendingImportUri = null },
        )
    }
    if (showCloudRecoveryPasswordDialog) {
        CloudRecoveryPasswordDialog(
            onConfirm = { password ->
                showCloudRecoveryPasswordDialog = false
                cloudRecoverySetupInProgress = true
                backupScope.launch {
                    try {
                        val result =
                            cloudRecoveryCoordinator
                                .createCloudRecovery(
                                    password,
                                )

                        cloudRecoveryMessage =
                            when (
                                result
                            ) {
                                CloudRecoverySetupResult.Success ->
                                    "Backed up successfully. Your encrypted recovery copy is ready to restore."

                                CloudRecoverySetupResult.NotSignedIn ->
                                    "Sign in to a non-guest account to use cloud recovery backup."

                                CloudRecoverySetupResult.GuestNotSupported ->
                                    "Cloud recovery backup is not available for guest accounts."

                                CloudRecoverySetupResult.PasswordTooShort ->
                                    "Choose a recovery password with at least 10 characters."

                                is CloudRecoverySetupResult.InitialUploadFailed ->
                                    "Recovery setup was created, but the encrypted recovery backup did not finish. Keep Impulsive installed, check your connection, and try again."

                                CloudRecoverySetupResult.UnexpectedFailure ->
                                    "Cloud recovery backup could not be fully enabled. Please try again."
                            }
                    } catch (
                        cancellation:
                            CancellationException,
                    ) {
                        throw cancellation
                    } catch (
                        error:
                            Throwable,
                    ) {
                        cloudRecoveryMessage =
                            "Cloud recovery backup could not be fully enabled. Please try again."
                    } finally {
                        cloudRecoverySetupInProgress =
                            false
                    }
                }
            },
            onDismiss = { showCloudRecoveryPasswordDialog = false },
        )
    }
    cloudRecoveryMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { cloudRecoveryMessage = null },
            title = { Text("Cloud recovery backup") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { cloudRecoveryMessage = null }) { Text("OK") }
            },
        )
    }
    manualBackupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { manualBackupMessage = null },
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { manualBackupMessage = null }) { Text("OK") }
            },
        )
    }
    authState.pendingAccountConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { authViewModel.dismissAccountSwitch() },
            title = {
                Text(stringResource(R.string.auth_conflict_title, conflict.providerDisplayName))
            },
            text = {
                val body = stringResource(R.string.auth_conflict_body, conflict.providerDisplayName)
                val emailLine = conflict.existingAccountEmail?.let { email ->
                    "\n\n" + stringResource(R.string.auth_conflict_signed_in_as, email)
                }.orEmpty()
                Text(body + emailLine)
            },
            confirmButton = {
                TextButton(onClick = { authViewModel.confirmAccountSwitch() }) {
                    Text(stringResource(R.string.auth_conflict_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = { authViewModel.dismissAccountSwitch() }) {
                    Text(stringResource(R.string.auth_conflict_cancel))
                }
            },
        )
    }
    LaunchedEffect(Unit) {
        appSettingsViewModel.cleanupLegacyExportFiles()
    }
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader()
            ProfileGroup(
                displayName = displayName,
                savedName = savedProfileName,
                avatar = avatar,
                currentLevel = currentLevel,
                answers = onboardingState.answers,
                haptics = haptics,
                onSaveProfile = { name, avatarId, onSaved ->
                    onboardingViewModel.savePersonalization(name, avatarId) {
                        haptics.confirm()
                        onSaved()
                    }
                },
            )
            if (protectionSetupState.profileBadgeShouldShow) {
                ProtectionSetupIncompleteCard(
                    protectionSetupState = protectionSetupState,
                    onOpenProtectionSetup = { appLockGuard.run(enabled = true) { onOpenProtectionSetupGuide() } },
                )
            }
            PlusGroup(
                haptics = haptics,
                onViewPlus = onOpenWebsiteProtectionPlus,
                premiumEntitlement = premiumEntitlement,
                restoreState = billingRestoreState,
                onRestorePurchases = onRestorePurchases,
                subscriptionCatalogState = subscriptionCatalogState,
                onRetryBilling = onRetryBilling,
            )
            AppearanceGroup(
                selectedMode = selectedMode,
                onModeSelected = themeViewModel::setThemeMode,
                haptics = haptics,
                hapticsEnabled = appSettingsState.hapticsEnabled,
                onHapticsChanged = appSettingsViewModel::setHapticsEnabled,
                onReplayGuide = { homeGuideReplayRequested = true },
            )
            RecoverySetupGroup(
                answers = onboardingState.answers,
                onEditTriggers = { onboardingViewModel.setMultiSelectAnswer(OnboardingQuestionId.Triggers, it) },
                onEditTiming = { onboardingViewModel.setMultiSelectAnswer(OnboardingQuestionId.Timing, it) },
                onEditWeeklyTarget = { onboardingViewModel.setSingleSelectAnswer(OnboardingQuestionId.WeekOneGoal, it) },
                taperSuggestionsEnabled = taperSuggestionsEnabled,
                onTaperSuggestionsChanged = taperViewModel::setTaperSuggestionsEnabled,
                haptics = haptics,
            )
            PersonalSupportSettingsGroup(
                onOpenPlans = {
                    appLockGuard.run(
                        enabled = appLockEnabled,
                        action = onOpenMomentPlans,
                    )
                },
                onOpenWhatWorksForMe = {
                    appLockGuard.run(
                        enabled = appLockEnabled,
                        action = onOpenWhatWorksForMe,
                    )
                },
                onOpenTips = onOpenTips,
                onOpenSuggestionPreferences = {
                    appLockGuard.run(
                        enabled = appLockEnabled,
                        action = onOpenSuggestionPreferences,
                    )
                },
                onOpenPrivacyAndData = {
                    appLockGuard.run(
                        enabled = appLockEnabled,
                        action = onOpenPrivacyAndData,
                    )
                },
                haptics = haptics,
            )
            ProtectionFocusGroup(
                protectionState = protectionSetupState,
                websiteProtectionPlusUnlocked = websiteProtectionPlusUnlocked,
                appLockEnabled = appLockEnabled,
                guard = appLockGuard::run,
                onOpenBlockedApps = { appLockGuard.run(enabled = true) { showBlockedAppsSheet = true } },
                onOpenProtectionSetupGuide = onOpenProtectionSetupGuide,
                onOpenProtectionCoach = onOpenProtectionCoach,
                onOpenUsageAccessPermission = onOpenUsageAccessPermission,
                onOpenInterruptionPermission = onOpenInterruptionPermission,
                onOpenBackgroundActivityPermission = onOpenBackgroundActivityPermission,
                onOpenWebsiteProtectionPlus = onOpenWebsiteProtectionPlus,
                notificationsAvailable = notificationsAvailable,
                onManageProtectionNotifications = onManageProtectionNotifications,
                haptics = haptics,
            )
            PrivacyAccountGroup(
                appLockEnabled = appLockEnabled,
                onDisableAppLock = appLockViewModel::disable,
                hideSensitiveNotifications = appSettingsState.hideSensitiveNotifications,
                onHideSensitiveNotificationsChanged = appSettingsViewModel::setHideSensitiveNotifications,
                cloudRecoveryBackupUiModel = cloudRecoveryBackupStatusUiModel,
                cloudRecoveryEnabled = cloudRecoveryEnabled,
                cloudRecoverySetupInProgress = cloudRecoverySetupInProgress,
                onCloudRecoveryEnabledChanged = { enabled ->
                    if (!enabled) {
                        cloudRecoverySetupInProgress =
                            true

                        backupScope.launch {
                            try {
                                cloudRecoveryCoordinator
                                    .disableCloudRecovery()
                            } catch (
                                cancellation:
                                    CancellationException,
                            ) {
                                throw cancellation
                            } catch (
                                error:
                                    Throwable,
                            ) {
                                cloudRecoveryMessage =
                                    "Cloud recovery backup could not be turned off. Please try again."
                            } finally {
                                cloudRecoverySetupInProgress =
                                    false
                            }
                        }
                    } else {
                        when (cloudRecoveryCoordinator.accountEligibility()) {
                            CloudRecoveryAccountEligibility.Eligible -> {
                                cloudRecoverySetupInProgress =
                                    true

                                backupScope.launch {
                                    try {
                                        if (!currentCloudRecoveryTransportRequiresDriveAuthorization()) {
                                            cloudRecoverySetupInProgress =
                                                false

                                            showCloudRecoveryPasswordDialog = true
                                        } else {
                                        when (
                                            val result =
                                                driveAppDataAuthorization
                                                    .requestAuthorization()
                                        ) {
                                            is DriveAuthorizationResult.NeedsUserResolution -> {
                                                cloudRecoverySetupInProgress =
                                                    false

                                                try {
                                                    driveAuthorizationResolutionLauncher.launch(
                                                        IntentSenderRequest
                                                            .Builder(
                                                                result
                                                                    .pendingIntent
                                                                    .intentSender,
                                                            )
                                                            .build(),
                                                    )
                                                } catch (
                                                    error:
                                                        Throwable,
                                                ) {
                                                    cloudRecoveryMessage =
                                                        "Could not open Google Drive authorization. Google Drive recovery backup was not enabled."
                                                }
                                            }

                                            else -> {
                                                handleDriveAuthorizationResult(
                                                    result,
                                                )
                                            }
                                        }
                                        }
                                    } catch (
                                        cancellation:
                                            CancellationException,
                                    ) {
                                        cloudRecoverySetupInProgress =
                                            false

                                        throw cancellation
                                    } catch (
                                        error:
                                            Throwable,
                                    ) {
                                        cloudRecoverySetupInProgress =
                                            false

                                        cloudRecoveryMessage =
                                            "Could not authorize Google Drive recovery backup."
                                    }
                                }
                            }

                            CloudRecoveryAccountEligibility.NotSignedIn -> {
                                cloudRecoveryMessage = "Sign in to a non-guest account to use cloud recovery backup."
                            }

                            CloudRecoveryAccountEligibility.GuestNotSupported -> {
                                cloudRecoveryMessage = "Cloud recovery backup is not available for guest accounts."
                            }
                        }
                    }
                },
                onCloudRecoveryBackupNow = {
                    CloudRecoveryUploadScheduler.request(context)
                },
                haptics = haptics,
                linkedProviders = authState.user?.linkedProviders.orEmpty(),
                authInFlightProvider = authState.inFlightProvider,
                authErrorMessage = authState.errorMessage,
                onConnectGoogle = {
                    activity?.let { authViewModel.linkGoogleAccount(it) }
                },
                onConnectFacebook = {
                    activity?.let { authViewModel.linkFacebookAccount(it) }
                },
                onDismissAuthError = authViewModel::consumeError,
                onExportData = {
                    showExportPasswordDialog = true
                },
                onImportData = {
                    importDocumentLauncher.launch(arrayOf("*/*"))
                },
                onDeleteAllData = {
                    startCloudAwareAccountDeletion()
                },
            )
            SupportGroup(
                haptics = haptics,
                onOpenHelp = onOpenHelp,
            )
        }

        if (mindModeSheetVisible) {
            MindModeStatusSheet(
                onDismissRequest = { mindModeSheetVisible = false },
                onStartMindTask = startRecommendedMindTask,
                onViewProgress = {
                    mindModeSheetVisible = false
                    onOpenScore()
                },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (bodyModeSheetVisible) {
            BodyModeLockedSheet(
                onDismissRequest = { bodyModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (soulModeSheetVisible) {
            SoulModeLockedSheet(
                onDismissRequest = { soulModeSheetVisible = false },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        if (modeSelectionSheetVisible) {
            ModeSelectionSheet(
                onDismissRequest = { modeSelectionSheetVisible = false },
                onOpenMindMode = {
                    mindModeSheetVisible = true
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = false
                },
                onOpenBodyMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = true
                    soulModeSheetVisible = false
                },
                onOpenSoulMode = {
                    mindModeSheetVisible = false
                    bodyModeSheetVisible = false
                    soulModeSheetVisible = true
                },
                bottomNavReservedSpace = bottomNavReservedSpace,
            )
        }

        AccountDeletionFlow(
            state = deletionState,
            onSubmitPassword = { password ->
                activity?.let { authViewModel.submitPasswordAndDeleteAccount(it, password) }
            },
            onDismiss = { authViewModel.cancelAccountDeletion() },
            onDeleted = {
                appSettingsViewModel.deleteAllData(
                    onComplete = { UserDataManager(context).restartApp() },
                )
            },
        )

        BottomNavBar(
            selected = if (
                modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible
            ) {
                BottomNavItem.Trigger
            } else {
                BottomNavItem.Settings
            },
            onSelect = { item ->
                when (item) {
                    BottomNavItem.Home -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenHome()
                    }
                    BottomNavItem.Progress -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        onOpenScore()
                    }
                    BottomNavItem.Trigger -> {
                        mindModeSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                        modeSelectionSheetVisible = !modeSelectionSheetVisible
                    }
                    BottomNavItem.Settings -> {
                        mindModeSheetVisible = false
                        modeSelectionSheetVisible = false
                        bodyModeSheetVisible = false
                        soulModeSheetVisible = false
                    }
                    BottomNavItem.Focus -> {
                        modeSelectionSheetVisible = false
                        onOpenFocus()
                    }
                }
            },
            onLongSelect = { item ->
                if (item == BottomNavItem.Trigger) {
                    modeSelectionSheetVisible = !modeSelectionSheetVisible
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            hapticsEnabled = appSettingsState.hapticsEnabled,
            settingsBadgeVisible = protectionSetupState.profileBadgeShouldShow,
            modeSelectorOpen = modeSelectionSheetVisible ||
                mindModeSheetVisible ||
                bodyModeSheetVisible ||
                soulModeSheetVisible,
            indicatorState = indicatorState,
            isActive = isActive,
        )

        if (showBlockedAppsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBlockedAppsSheet = false },
            ) {
                BlockedAppsSelectionContent(
                    selectedPackageNames = protectionSetupState.selectedBlockedAppPackageNames,
                    onSelectedPackageNamesChanged = protectionSetupViewModel::setSelectedBlockedAppPackageNames,
                    onDone = { showBlockedAppsSheet = false },
                    allowShowMoreApps = true,
                )
            }
        }

        AppLockGuardHost(controller = appLockGuard)
    }
}

@Composable
private fun ProtectionSetupIncompleteCard(
    protectionSetupState: ProtectionSetupState,
    onOpenProtectionSetup: () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val items = (
        protectionSetupState.incompleteCoreProtectionItems +
            protectionSetupState.skippedCoreProtectionItems
        )
        .distinct()
        .sortedBy { it.ordinal }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isDarkTheme) BorderStroke(1.dp, SettingsBoxBorder.copy(alpha = 0.55f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ImpulsiveFocusMode),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Protection setup incomplete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "These settings help Impulsive step in during difficult habit moments. You can enable them now or later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { item ->
                MissingProtectionItemRow(item = item)
            }
            Button(
                onClick = onOpenProtectionSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Finish protection setup")
            }
        }
    }
}

@Composable
private fun MissingProtectionItemRow(item: ProtectionSetupItem) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = item.protectionReasonText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ProtectionSetupItem.protectionReasonText(): String = when (this) {
    ProtectionSetupItem.BlockedApps ->
        "Choose the apps where Impulsive should create a pause before the loop continues."
    ProtectionSetupItem.UsageAccess ->
        "Allows Impulsive to detect protected apps without reading private content."
    ProtectionSetupItem.Notifications ->
        "Allows Impulsive to show a reset notification, and to report protection status changes."
    ProtectionSetupItem.InterruptionPermission ->
        "Allows Impulsive to open your pause screen the moment a protected app comes to the front."
    ProtectionSetupItem.BackgroundActivity ->
        "Helps Impulsive restart after reboot and avoid being stopped by battery optimization."
    ProtectionSetupItem.WebsiteProtection ->
        "Allows Impulsive to protect selected domains when website blocking is added."
}

@Composable
private fun SettingsHeader() {
    var showSettingsInfo by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showSettingsInfo = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "About Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (showSettingsInfo) {
        SettingsInfoDialog(
            onDismiss = { showSettingsInfo = false },
        )
    }
}

@Composable
private fun SettingsInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Got it")
            }
        },
        title = {
            Text(
                text = "About Settings",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Shape Impulsive around how you Notice, Pivot and Understand.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )

                SettingsInfoItem(
                    title = "Profile",
                    body = "Update your name, avatar, path details, and onboarding answers used to personalise the app.",
                )

                SettingsInfoItem(
                    title = "Appearance",
                    body = "Control light or dark mode, haptics, and guide replay preferences.",
                )

                SettingsInfoItem(
                    title = "Pivot setup",
                    body = "Adjust your trigger cues, timing pattern, weekly target, and daily support estimate.",
                )

                SettingsInfoItem(
                    title = "Protection & Focus",
                    body = "Manage protected apps, permissions, website protection, and Focus-related protection setup.",
                )

                SettingsInfoItem(
                    title = "Privacy & account",
                    body = "Control app lock, notification privacy, export or delete local data, and link supported accounts.",
                )

                SettingsInfoItem(
                    title = "Support",
                    body = "Open Help, contact support, send feedback, report bugs, and review app information.",
                )

                SettingsInfoItem(
                    title = "Impulsive Plus",
                    body = "View premium protection and upgrade options without interrupting recovery moments.",
                )
            }
        },
    )
}

@Composable
private fun SettingsInfoItem(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
        )
    }
}

@Composable
private fun ProfileGroup(
    displayName: String,
    savedName: String,
    avatar: AvatarStyle,
    currentLevel: Int,
    answers: OnboardingAnswers,
    haptics: ImpulsiveHaptics,
    onSaveProfile: (String, String, () -> Unit) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable(savedName) { mutableStateOf(savedName) }
    var draftAvatarId by rememberSaveable(avatar.id) { mutableStateOf(avatar.id) }

    AccordionGroup(
        title = "Profile",
        summary = "$displayName \u2022 Mind mode \u2022 Edit profile",
        icon = Icons.Filled.Person,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(ProfileGlow),
        leadingContent = {
            AvatarCircle(avatar = avatar, size = 38.dp, imageSize = 32.dp)
        },
    ) {
        Text(
            text = displayName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProfileMetric(label = "Level", value = "$currentLevel")
            ProfileMetric(label = "Path", value = "Mind mode")
        }
        Spacer(modifier = Modifier.height(12.dp))
        PillLabel(text = "Private on this device")
        SettingsDivider()
        SettingsRow(
            title = "Edit profile",
            subtext = "Name and avatar",
            onClick = {
                draftName = savedName
                draftAvatarId = avatar.id
                editing = true
            },
        )
        AnimatedVisibility(
            visible = editing,
            enter = settingsExpandEnter(),
            exit = settingsCollapseExit(),
        ) {
            ProfileEditPanel(
                draftName = draftName,
                onDraftNameChanged = { draftName = it },
                draftAvatarId = draftAvatarId,
                onAvatarSelected = {
                    if (it.id != draftAvatarId) {
                        haptics.light()
                        draftAvatarId = it.id
                    }
                },
                onSave = {
                    onSaveProfile(draftName, draftAvatarId) {
                        editing = false
                    }
                },
                onCancel = {
                    draftName = savedName
                    draftAvatarId = AvatarStyle.fromId(answers.avatarId).id
                    editing = false
                },
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileEditPanel(
    draftName: String,
    onDraftNameChanged: (String) -> Unit,
    draftAvatarId: String,
    onAvatarSelected: (AvatarStyle) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = draftName,
            onValueChange = onDraftNameChanged,
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Text(
            text = "Avatar",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        AvatarStyle.entries.chunked(3).forEach { rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowAvatars.forEach { avatar ->
                    AvatarPickerItem(
                        avatar = avatar,
                        selected = avatar.id == draftAvatarId,
                        onClick = { onAvatarSelected(avatar) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
            ) {
                Text(text = "Cancel")
            }
            Button(
                onClick = onSave,
                enabled = draftName.trim().isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImpulsivePsychological,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
private fun AvatarPickerItem(
    avatar: AvatarStyle,
    selected: Boolean,
    haptics: ImpulsiveHaptics? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    if (!selected) {
                        haptics?.light()
                    }
                    onClick()
                },
            )
            .background(if (selected) ImpulsivePsychological.copy(alpha = 0.32f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) ImpulsivePsychological else Color.Transparent,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AvatarCircle(avatar = avatar, size = 58.dp, imageSize = 50.dp)
    }
}

@Composable
private fun AppearanceGroup(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    haptics: ImpulsiveHaptics,
    hapticsEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    onReplayGuide: () -> Unit,
) {
    AccordionGroup(
        title = "Appearance",
        summary = "Theme, Haptics, Home guide",
        icon = Icons.Filled.Palette,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(AppearanceGlow),
    ) {
        Text(
            text = "Theme",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ThemeSegmentedSelector(
            selectedMode = selectedMode,
            haptics = haptics,
            onModeSelected = onModeSelected,
        )
        SettingsDivider()
        SettingsRow(
            title = "Haptics",
            trailing = {
                SettingsSwitch(
                    checked = hapticsEnabled,
                    haptics = haptics,
                    onCheckedChange = onHapticsChanged,
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Replay home guide",
            subtext = "See the quick tour of the home screen again",
            onClick = onReplayGuide,
        )
    }
}

@Composable
private fun ThemeSegmentedSelector(
    selectedMode: ThemeMode,
    haptics: ImpulsiveHaptics,
    onModeSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.Light to "Light",
        ThemeMode.Dark to "Dark",
        ThemeMode.AsPerTime to "Auto by time",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (selectedMode != mode) {
                            haptics.light()
                            onModeSelected(mode)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RecoverySetupGroup(
    answers: OnboardingAnswers,
    onEditTriggers: (List<String>) -> Unit,
    onEditTiming: (List<String>) -> Unit,
    onEditWeeklyTarget: (String?) -> Unit,
    taperSuggestionsEnabled: Boolean,
    onTaperSuggestionsChanged: (Boolean) -> Unit,
    haptics: ImpulsiveHaptics,
) {
    var editing by remember { mutableStateOf<RecoveryEditTarget?>(null) }

    AccordionGroup(
        title = "Pivot setup",
        summary = "Onboarding answers can be updated",
        icon = Icons.Filled.Spa,
        haptics = null,
        glowSpec = SettingsGlowSpec.single(RecoverySetupGlow),
    ) {
        SettingsRow(
            title = "Cues",
            value = answerListSummary(answers.triggers, TriggerLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.Triggers },
        )
        SettingsDivider()
        SettingsRow(
            title = "Timing pattern",
            value = answerListSummary(answers.timing, TimingLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.Timing },
        )
        SettingsDivider()
        SettingsRow(
            title = "Weekly target",
            value = answerLabel(answers.weekOneGoal, WeekOneLabels, "Not configured"),
            onClick = { editing = RecoveryEditTarget.WeeklyTarget },
        )
        SettingsDivider()
        SettingsRow(title = "Daily support estimate", value = "${answers.dailyRelapseUrgeCount} moments per day")
        SettingsDivider()
        SettingsRow(
            title = "Taper suggestions",
            subtext = "Suggest one less moment when your pattern shows progress",
            trailing = {
                SettingsSwitch(
                    checked = taperSuggestionsEnabled,
                    haptics = haptics,
                    onCheckedChange = onTaperSuggestionsChanged,
                )
            },
        )

        when (editing) {
            RecoveryEditTarget.Triggers -> MultiSelectEditDialog(
                title = "Cues",
                options = TriggerLabels,
                selected = answers.triggers,
                onConfirm = { onEditTriggers(it); editing = null },
                onDismiss = { editing = null },
            )
            RecoveryEditTarget.Timing -> MultiSelectEditDialog(
                title = "Timing pattern",
                options = TimingLabels,
                selected = answers.timing,
                onConfirm = { onEditTiming(it); editing = null },
                onDismiss = { editing = null },
            )
            RecoveryEditTarget.WeeklyTarget -> SingleSelectEditDialog(
                title = "Weekly target",
                options = WeekOneLabels,
                selected = answers.weekOneGoal,
                onConfirm = { onEditWeeklyTarget(it); editing = null },
                onDismiss = { editing = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun PersonalSupportSettingsGroup(
    onOpenPlans: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenWhatWorksForMe: () -> Unit,
    onOpenSuggestionPreferences: () -> Unit,
    onOpenPrivacyAndData: () -> Unit,
    haptics: ImpulsiveHaptics,
    viewModel: AdaptivePreferencesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val switchesEnabled = !state.loading && !state.saving
    var pathShiftConsentVisible by remember { mutableStateOf(false) }
    var pathShiftDisableVisible by remember { mutableStateOf(false) }

    if (pathShiftConsentVisible) {
        AlertDialog(
            onDismissRequest = { pathShiftConsentVisible = false },
            title = { Text("Turn on Future Path?") },
            text = {
                Text(
                    "PathShift analyses your encrypted Moment history on this device.\n\n" +
                        "It estimates a range based on recent patterns.\n\n" +
                        "It does not use the protected app or website identity, URLs or domains, " +
                        "journal content, your account email, camera, microphone or location.\n\n" +
                        "You can turn it off, reset the history or delete all Moment " +
                        "data at any time.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pathShiftConsentVisible = false
                        viewModel.update { it.copy(pathShiftEnabled = true) }
                    },
                ) {
                    Text("Turn On Future Path")
                }
            },
            dismissButton = {
                TextButton(onClick = { pathShiftConsentVisible = false }) {
                    Text("Not Now")
                }
            },
        )
    }

    if (pathShiftDisableVisible) {
        AlertDialog(
            onDismissRequest = { pathShiftDisableVisible = false },
            title = { Text("Turn off Future Path?") },
            text = {
                Text(
                    "This cancels the current PathShift and its seven-day comparison. " +
                        "Your underlying Moment history, protection, personal " +
                        "suggestions and LP remain unchanged.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pathShiftDisableVisible = false
                        viewModel.update { it.copy(pathShiftEnabled = false) }
                    },
                ) {
                    Text("Turn Off Future Path")
                }
            },
            dismissButton = {
                TextButton(onClick = { pathShiftDisableVisible = false }) {
                    Text("Keep On")
                }
            },
        )
    }

    AccordionGroup(
        title = stringResource(R.string.personal_support_title),
        summary = stringResource(R.string.personal_support_summary),
        icon = Icons.Filled.AutoAwesome,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(RecoverySetupGlow),
    ) {
        SettingsRow(
            title = stringResource(R.string.personal_support_plans),
            subtext = "Create, practise and manage prepared actions.",
            onClick = onOpenPlans,
            trailingIcon = Icons.Filled.ChevronRight,
        )
        SettingsDivider()
        SettingsRow(
            title = stringResource(R.string.tips_title),
            subtext = stringResource(R.string.tips_settings_summary),
            onClick = onOpenTips,
            trailingIcon = Icons.Filled.ChevronRight,
        )
        SettingsDivider()
        SettingsRow(
            title = "Future Path",
            subtext = "Use encrypted on-device history for cautious estimates.",
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.pathShiftEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    accessibilityLabel =
                        "Future Path. Use encrypted on-device Moment history for " +
                            "cautious estimates.",
                    onCheckedChange = { checked ->
                        if (checked) {
                            pathShiftConsentVisible = true
                        } else {
                            pathShiftDisableVisible = true
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "What Works for Me",
            subtext = "View cautious patterns from your private support history.",
            onClick = onOpenWhatWorksForMe,
            trailingIcon = Icons.Filled.ChevronRight,
        )
        SettingsDivider()
        SettingsRow(
            title = "Suggestion preferences",
            subtext = "Choose which private suggestions can appear.",
            value = suggestionPreferencesSummary(state.preferences),
            onClick = onOpenSuggestionPreferences,
            trailingIcon = Icons.Filled.ChevronRight,
        )
        SettingsDivider()
        SettingsRow(
            title = "Privacy and data",
            subtext = "Screen privacy, retention and personal data controls.",
            value = privacyAndDataSummary(state.preferences),
            onClick = onOpenPrivacyAndData,
            trailingIcon = Icons.Filled.ChevronRight,
        )
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MultiSelectEditDialog(
    title: String,
    options: Map<String, String>,
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = if (id in current) current - id else current + id
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = id in current,
                            onCheckedChange = { checked ->
                                current = if (checked) current + id else current - id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toList()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SingleSelectEditDialog(
    title: String,
    options: Map<String, String>,
    selected: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { current = id }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = current == id, onClick = { current = id })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PersonalSupportSuggestionPreferencesScreen(
    onBack: () -> Unit,
    onOpenHowSuggestionsWork: () -> Unit,
    viewModel: AdaptivePreferencesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appSettingsViewModel: AppSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberImpulsiveHaptics(appSettingsState.hapticsEnabled)
    val switchesEnabled = !state.loading && !state.saving

    PersonalSupportSubScreen(
        title = "Suggestion preferences",
        onBack = onBack,
    ) {
        SettingsRow(
            title = stringResource(R.string.personal_suggestions),
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.personalSuggestionsEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    onCheckedChange = { checked ->
                        viewModel.update {
                            it.copy(personalSuggestionsEnabled = checked)
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = stringResource(R.string.game_suggestions),
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.gameSuggestionsEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    onCheckedChange = { checked ->
                        viewModel.update {
                            it.copy(gameSuggestionsEnabled = checked)
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = stringResource(R.string.reading_suggestions),
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.readingSuggestionsEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    onCheckedChange = { checked ->
                        viewModel.update {
                            it.copy(readingSuggestionsEnabled = checked)
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = stringResource(R.string.moment_plan_suggestions),
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.momentPlanSuggestionsEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    onCheckedChange = { checked ->
                        viewModel.update {
                            it.copy(momentPlanSuggestionsEnabled = checked)
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "About suggestions",
            subtext = "See how private on-device suggestions are selected.",
            trailingIcon = Icons.Filled.ChevronRight,
            onClick = onOpenHowSuggestionsWork,
        )
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun PersonalSupportPrivacyAndDataScreen(
    onBack: () -> Unit,
    viewModel: AdaptivePreferencesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    controlsViewModel: PersonalSupportControlsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controlsState by controlsViewModel.state.collectAsStateWithLifecycle()
    val appSettingsViewModel: AppSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val appSettingsState by appSettingsViewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberImpulsiveHaptics(appSettingsState.hapticsEnabled)
    val switchesEnabled = !state.loading && !state.saving
    var confirmation by remember { mutableStateOf<String?>(null) }
    var retentionPickerVisible by remember { mutableStateOf(false) }

    if (retentionPickerVisible) {
        AlertDialog(
            onDismissRequest = { retentionPickerVisible = false },
            title = { Text("Personal support history") },
            text = {
                Column {
                    AdaptiveHistoryRetentionPolicy.entries.forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = {
                                        retentionPickerVisible = false
                                        viewModel.update {
                                            it.copy(historyRetentionPolicy = policy)
                                        }
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected =
                                    state.preferences.historyRetentionPolicy == policy,
                                onClick = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(policy.consumerLabel)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { retentionPickerVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmation != null) {
        val deleting = confirmation?.startsWith("delete") == true
        val finalDelete = confirmation == "delete-final"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    when {
                        finalDelete -> "Permanently delete all Moment data?"
                        deleting -> "Delete all Moment data?"
                        else -> "Reset personal learning?"
                    },
                )
            },
            text = {
                Text(
                    when {
                        finalDelete ->
                            "This cannot be undone. Only Moment data will be removed."
                        deleting ->
                            "This permanently removes your Moment Plans, practice history, " +
                                "personal suggestion settings and adaptive support history " +
                                "from this device."
                        else ->
                            "This removes your support history, feedback, observations and " +
                                "practice history. Your Moment Plans and suggestion settings " +
                                "will remain."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            finalDelete -> {
                                confirmation = null
                                controlsViewModel.deleteAllMomentData()
                            }
                            deleting -> confirmation = "delete-final"
                            else -> {
                                confirmation = null
                                controlsViewModel.resetPersonalLearning()
                            }
                        }
                    },
                    enabled = !controlsState.busy,
                ) {
                    Text(if (finalDelete) "Delete permanently" else if (deleting) "Continue" else "Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    PersonalSupportSubScreen(
        title = "Privacy and data",
        onBack = onBack,
    ) {
        SettingsRow(
            title = "Screen privacy",
            subtext =
                "Hide Moment Plans, practice and personal patterns from screenshots " +
                    "and screen sharing.",
            trailing = {
                SettingsSwitch(
                    checked = state.preferences.privateScreenProtectionEnabled,
                    haptics = haptics,
                    enabled = switchesEnabled,
                    accessibilityLabel =
                        "Screen privacy. Hide Moment Plans, practice and personal " +
                            "patterns from screenshots and screen sharing.",
                    onCheckedChange = { checked ->
                        viewModel.update {
                            it.copy(privateScreenProtectionEnabled = checked)
                        }
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Personal support history",
            subtext =
                "Older personal support records are removed automatically. " +
                    "Moment Plans stay unless you delete them.",
            value = state.preferences.historyRetentionPolicy.consumerLabel,
            onClick = { retentionPickerVisible = true },
        )
        SettingsDivider()
        Text(
            text = "DATA CONTROL",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        SettingsRow(
            title = "Reset personal learning",
            subtext = "Remove support, feedback, observation and practice history.",
            onClick = { confirmation = "reset" },
        )
        SettingsDivider()
        SettingsRow(
            title = "Delete all Moment data",
            subtext = "Remove plans, settings and all personal support history.",
            valueColor = MaterialTheme.colorScheme.error,
            onClick = { confirmation = "delete-first" },
        )
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        controlsState.completionMessage?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        controlsState.errorMessage?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PersonalSupportSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    content = content,
                )
            }
        }
    }
}

private fun suggestionPreferencesSummary(preferences: AdaptivePreferences): String {
    val enabledCount = listOf(
        preferences.personalSuggestionsEnabled,
        preferences.gameSuggestionsEnabled,
        preferences.readingSuggestionsEnabled,
        preferences.momentPlanSuggestionsEnabled,
    ).count { it }

    return when (enabledCount) {
        0 -> "Off"
        4 -> "All enabled"
        else -> "$enabledCount of 4 enabled"
    }
}

private fun privacyAndDataSummary(preferences: AdaptivePreferences): String =
    preferences.historyRetentionPolicy.consumerLabel +
        " · Screen privacy " +
        if (preferences.privateScreenProtectionEnabled) {
            "on"
        } else {
            "off"
        }

@Composable
private fun ProtectionFocusGroup(
    protectionState: ProtectionSetupState,
    websiteProtectionPlusUnlocked: Boolean,
    appLockEnabled: Boolean,
    guard: (enabled: Boolean, action: () -> Unit) -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenProtectionSetupGuide: () -> Unit,
    onOpenProtectionCoach: () -> Unit,
    onOpenUsageAccessPermission: () -> Unit,
    onOpenInterruptionPermission: () -> Unit,
    onOpenBackgroundActivityPermission: () -> Unit,
    onOpenWebsiteProtectionPlus: () -> Unit,
    notificationsAvailable: Boolean,
    onManageProtectionNotifications: () -> Unit,
    haptics: ImpulsiveHaptics,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val oneMinuteAccessDataSource = remember {
        com.impulsive.app.backend.data.local.preferences.OneMinuteAccessDataSource(context)
    }
    val oneMinuteAccessState by oneMinuteAccessDataSource.state.collectAsStateWithLifecycle(
        initialValue = com.impulsive.app.backend.data.local.preferences.OneMinuteAccessState(),
    )
    var oneMinuteAccessPending by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(oneMinuteAccessPending) {
        val target = oneMinuteAccessPending
        if (target != null) {
            oneMinuteAccessDataSource.setEnabled(target)
            oneMinuteAccessPending = null
        }
    }
    val selectedCount = protectionState.selectedBlockedAppPackageNames.size
    val monitoredAppsValue = if (selectedCount == 0) "Not configured" else "$selectedCount selected"
    val monitoredAppsSubtext = if (selectedCount == 0) {
        "Let Impulsive suggest apps that often lead into the loop."
    } else {
        "Tap to review or change protected apps."
    }
    val notificationValue = if (notificationsAvailable) {
        "Ready"
    } else {
        "Needs attention"
    }
    val runtimeNotificationPermissionGranted =
        isRuntimeNotificationPermissionGranted(context)
    val appProtectionValue = when {
        selectedCount == 0 -> "No protected apps selected"
        !protectionState.usageAccessEnabled ||
            !Settings.canDrawOverlays(context) ||
            !protectionState.backgroundActivityEnabled ||
            !notificationsAvailable -> "Protection needs attention"
        else -> "Active for $selectedCount selected apps"
    }
    val appProtectionSubtext = when {
        selectedCount == 0 -> "Choose protected apps to start app protection."
        appProtectionValue == "Protection needs attention" -> "Fix setup so selected apps can be protected."
        else -> "Manage protected apps or review required permissions."
    }

    AccordionGroup(
        title = "Protection & Focus",
        summary = "Protected apps, permissions",
        icon = Icons.Filled.Security,
        haptics = null,
        glowSpec = SettingsGlowSpec.split(ProtectionGlow, FocusGlow),
    ) {
        SettingsRow(
            title = "Choose apps to protect",
            value = monitoredAppsValue,
            subtext = monitoredAppsSubtext,
            onClick = onOpenBlockedApps,
        )
        SettingsDivider()
        SettingsRow(
            title = "App protection",
            value = appProtectionValue,
            subtext = appProtectionSubtext,
            onClick = onOpenProtectionSetupGuide,
        )
        SettingsDivider()
        SettingsRow(
            title = "Protection Coach",
            value = "Review",
            subtext = stringResource(R.string.protection_coach_description),
            onClick = onOpenProtectionCoach,
        )
        SettingsDivider()
        SettingsRow(
            title = "App detection",
            value = if (protectionState.usageAccessEnabled) "Enabled" else "Not enabled",
            subtext = "Allows Impulsive to notice when you open a protected app.",
            onClick = onOpenUsageAccessPermission,
        )
        SettingsDivider()
        SettingsRow(
            title = "Let Impulsive step in",
            value = if (Settings.canDrawOverlays(context)) "Enabled" else "Not enabled",
            subtext = "Lets the pause screen show on top of a protected app. Your phone calls this Display over other apps. Without it, Impulsive can only send a notification.",
            onClick = onOpenInterruptionPermission,
        )
        SettingsDivider()
        SettingsRow(
            title = "Notifications",
            subtext = "Allows Impulsive to show a reset notification, and to report protection status changes.",
            value = notificationValue,
            onClick = onManageProtectionNotifications,
            trailing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !runtimeNotificationPermissionGranted) {
                {
                    TextButtonPill(
                        text = "Allow",
                        haptics = haptics,
                        onClick = onManageProtectionNotifications,
                    )
                }
            } else {
                null
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "45-second access",
            subtext = if (oneMinuteAccessState.enabled) {
                "Lets you continue for 45 seconds, then protection starts again."
            } else {
                "Off. Protected apps stay at the pause screen."
            },
            trailing = {
                SettingsSwitch(
                    checked = oneMinuteAccessState.enabled,
                    haptics = haptics,
                    onCheckedChange = { oneMinuteAccessPending = it },
                )
            },
        )
        SettingsDivider()
        if (websiteProtectionPlusUnlocked) {
            SettingsRow(
                title = "Website Protection, DNS Blocking, ",
                value = when {
                    !protectionState.websiteProtectionEnabled -> "Off"
                    protectionState.websiteProtectionAlwaysOn -> "Always on"
                    else -> "Protected time"
                },
                valueColor = if (protectionState.websiteProtectionEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                subtext = "Open Website Protection settings, status, and explanation.",
                trailingIcon = Icons.Filled.Security,
                onClick = onOpenWebsiteProtectionPlus,
            )
        } else {
            SettingsRow(
                title = "Website Protection",
                value = "Plus",
                valueColor = ImpulsivePsychological,
                subtext = "Blocks adult and risky websites using local DNS-based filtering.",
                trailingIcon = Icons.Filled.Lock,
                onClick = onOpenWebsiteProtectionPlus,
            )
        }
        SettingsDivider()
        SettingsRow(
            title = "Background protection",
            value = if (protectionState.backgroundActivityEnabled) "Allowed" else "Needs review",
            subtext = "Keeps Impulsive running after a restart. Your phone calls this Battery optimization.",
            onClick = onOpenBackgroundActivityPermission,
        )
    }
}

@Composable
private fun PrivacyAccountGroup(
    appLockEnabled: Boolean,
    onDisableAppLock: () -> Unit,
    hideSensitiveNotifications: Boolean,
    onHideSensitiveNotificationsChanged: (Boolean) -> Unit,
    cloudRecoveryBackupUiModel: CloudRecoveryBackupUiModel,
    cloudRecoveryEnabled: Boolean,
    cloudRecoverySetupInProgress: Boolean,
    onCloudRecoveryEnabledChanged: (Boolean) -> Unit,
    onCloudRecoveryBackupNow: () -> Unit,
    haptics: ImpulsiveHaptics,
    linkedProviders: Set<AuthProvider>,
    authInFlightProvider: AuthProvider?,
    authErrorMessage: String?,
    onConnectGoogle: () -> Unit,
    onConnectFacebook: () -> Unit,
    onDismissAuthError: () -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSetPin by remember { mutableStateOf(false) }
    val googleConnected = linkedProviders.contains(AuthProvider.Google)
    val facebookConnected = linkedProviders.contains(AuthProvider.Facebook)

    AccordionGroup(
        title = "Privacy & account",
        summary = "App lock, Link and Delete Account",
        icon = Icons.Filled.PrivacyTip,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(PrivacyGlow),
    ) {
        SettingsRow(
            title = "App lock",
            subtext = if (appLockEnabled) {
                "Fingerprint or PIN required to open Impulsive"
            } else {
                "Add a fingerprint or PIN to keep Impulsive private"
            },
            trailing = {
                SettingsSwitch(
                    checked = appLockEnabled,
                    haptics = haptics,
                    onCheckedChange = { wantOn ->
                        if (wantOn) showSetPin = true else onDisableAppLock()
                    },
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Hide sensitive notifications",
            trailing = {
                SettingsSwitch(
                    checked = hideSensitiveNotifications,
                    haptics = haptics,
                    onCheckedChange = onHideSensitiveNotificationsChanged,
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Cloud recovery backup",
            subtext = cloudRecoveryBackupUiModel.subtext,
            value = cloudRecoveryBackupUiModel.value,
            trailing = {
                if (cloudRecoveryBackupUiModel.showProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    SettingsSwitch(
                        checked = cloudRecoveryEnabled,
                        haptics = haptics,
                        onCheckedChange = onCloudRecoveryEnabledChanged,
                    )
                }
            },
        )
        if (cloudRecoveryBackupUiModel.showBackupNow) {
            SettingsDivider()
            SettingsRow(
                title = "Back up now",
                subtext = "Update your encrypted recovery copy.",
                onClick = onCloudRecoveryBackupNow,
            )
        }
        SettingsDivider()
        SettingsRow(
            title = "Export my Impulsive backup",
            subtext = "Save an encrypted backup file protected by a password you choose",
            onClick = onExportData,
        )
        SettingsDivider()
        SettingsRow(
            title = "Import Impulsive backup",
            subtext = "Restore progress from an encrypted backup file",
            onClick = onImportData,
        )
        SettingsDivider()
        SettingsRow(
            title = "Delete account and data",
            subtext = "Permanently remove your account and erase this device",
            trailingIcon = Icons.Filled.DeleteOutline,
            onClick = { showDeleteConfirm = true },
        )
        SettingsDivider()
        SettingsRow(
            title = if (googleConnected) "Google account" else "Link Google account",
            subtext = if (googleConnected) "Connected" else "Not connected",
            trailing = {
                when {
                    googleConnected -> PillLabel("Connected")
                    authInFlightProvider == AuthProvider.Google -> PillLabel("Connecting")
                    else -> TextButtonPill(
                        text = "Connect",
                        haptics = haptics,
                        onClick = onConnectGoogle,
                    )
                }
            },
        )
        SettingsDivider()
        SettingsRow(
            title = if (facebookConnected) "Facebook account" else "Link Facebook account",
            subtext = if (facebookConnected) "Connected" else "Not connected",
            trailing = {
                when {
                    facebookConnected -> PillLabel("Connected")
                    authInFlightProvider == AuthProvider.Facebook -> PillLabel("Connecting")
                    else -> TextButtonPill(
                        text = "Connect",
                        haptics = haptics,
                        onClick = onConnectFacebook,
                    )
                }
            },
        )
        if (authErrorMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissAuthError,
                title = { Text("Account connection failed") },
                text = { Text(authErrorMessage) },
                confirmButton = {
                    TextButton(
                        onClick = onDismissAuthError,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = ImpulsivePsychological,
                        ),
                    ) {
                        Text("Got it")
                    }
                },
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete account and data?") },
                text = {
                    Text(
                        "This permanently deletes your Impulsive account, removes your " +
                            "account and subscription records from Impulsive's servers, " +
                            "and erases your notes, sessions, scores, settings, and " +
                            "everything else stored on this device. If your phone has " +
                            "backed up Impulsive through your Google account, you can " +
                            "clear that backup in your device's Google backup settings. " +
                            "This cannot be undone, and the app will restart."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDeleteAllData()
                    }) { Text("Delete everything") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }
        if (showSetPin) {
            Dialog(
                onDismissRequest = { showSetPin = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                SetPinScreen(
                    onPinSet = { showSetPin = false },
                    onCancel = { showSetPin = false },
                )
            }
        }
    }
}

@Composable
private fun SupportGroup(
    haptics: ImpulsiveHaptics,
    onOpenHelp: () -> Unit,
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showDataStorageInfo by remember { mutableStateOf(false) }
    val openLegalLink: (ImpulsiveLegalDestination) -> Unit = { destination ->
        haptics.light()
        openWebPageOrShowError(
            context = context,
            url = impulsiveLegalUrl(destination),
        )
    }

    AccordionGroup(
        title = "Support",
        summary = "Help, Terms, Privacy, Contact",
        icon = Icons.Filled.AutoAwesome,
        haptics = haptics,
        glowSpec = SettingsGlowSpec.single(SupportGlow),
    ) {
        SettingsRow(
            title = "Help",
            trailingIcon = Icons.AutoMirrored.Filled.HelpOutline,
            onClick = {
                haptics.light()
                onOpenHelp()
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Contact support",
            trailingIcon = Icons.Filled.MailOutline,
            onClick = { sendSupportEmail(context, "Impulsive support") },
        )
        SettingsDivider()
        SettingsRow(
            title = "Send feedback",
            trailingIcon = Icons.Filled.ChatBubbleOutline,
            onClick = { sendSupportEmail(context, "Impulsive feedback") },
        )
        SettingsDivider()
        SettingsRow(
            title = "Report a bug",
            trailingIcon = Icons.Filled.BugReport,
            onClick = {
                sendSupportEmail(
                    context,
                    "Impulsive bug report",
                    "\n\n---\nApp version: ${appVersionName(context)}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}",
                )
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Rate Impulsive",
            subtext = "Open the Impulsive listing on Google Play.",
            trailingIcon = Icons.Filled.StarRate,
            onClick = {
                haptics.light()

                val opened = openImpulsivePlayStoreListing(context)

                if (!opened) {
                    Toast.makeText(
                        context,
                        "Google Play could not be opened.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Privacy policy",
            trailingIcon = Icons.Filled.PrivacyTip,
            onClick = { openLegalLink(ImpulsiveLegalDestination.PrivacyPolicy) },
        )
        SettingsDivider()
        SettingsRow(
            title = "How your data is stored",
            subtext = "Where your recovery progress lives and what Impulsive keeps",
            trailingIcon = Icons.Filled.Info,
            onClick = { showDataStorageInfo = true },
        )
        SettingsDivider()
        SettingsRow(
            title = "Terms of Service",
            subtext = "Read the terms that apply to using Impulsive.",
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { openLegalLink(ImpulsiveLegalDestination.TermsOfService) },
        )
        SettingsDivider()
        SettingsRow(
            title = "Account deletion help",
            subtext = "Read how to delete your Impulsive account and associated account data.",
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { openLegalLink(ImpulsiveLegalDestination.AccountDeletionHelp) },
        )
        SettingsDivider()
        SettingsRow(
            title = "About Impulsive",
            trailingIcon = Icons.Filled.Info,
            onClick = { showAbout = true },
        )
        if (showDataStorageInfo) {
            ScrollableSettingsInfoDialog(
                title = "How your data is stored",
                confirmLabel = "Got it",
                onDismissRequest = { showDataStorageInfo = false },
            ) {
                Text(
                    "Impulsive stores your recovery progress on your device. " +
                        "Impulsive does not store recovery progress or behavioural " +
                        "history on its servers. If device backup is enabled, your " +
                        "phone may back up selected app data through your own Google " +
                        "account so your progress can be restored after reinstalling " +
                        "or changing devices. You can also export an encrypted backup " +
                        "file manually. Impulsive keeps only account, subscription, " +
                        "and security records on its servers.",
                )
            }
        }
        if (showAbout) {
            ScrollableSettingsInfoDialog(
                title = "About Impulsive",
                confirmLabel = "Close",
                onDismissRequest = { showAbout = false },
            ) {
                Text("Impulsive helps you notice a difficult moment, create a pause, choose a next step, and understand your patterns.")
                Spacer(Modifier.height(8.dp))
                Text("If your patterns are causing serious distress, harm, or feel difficult to stop despite unwanted consequences, consider speaking with a qualified professional or a trusted support service.")
                Spacer(Modifier.height(8.dp))
                Text("Impulsive is a behaviour-change support tool for adults. It is not a medical device, therapy service, diagnosis tool, crisis-support service, or clinically validated treatment. It does not diagnose, treat, cure, or prevent addiction, compulsions, mental health conditions, or any medical condition. It helps you create a pause, choose a next step, and understand your patterns.")
                Spacer(Modifier.height(8.dp))
                Text("Version ${appVersionName(context)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "useimpulsive.com",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        openImpulsiveWebPage(context, "https://useimpulsive.com")
                    },
                )
                Text("Hello@useimpulsive.com", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScrollableSettingsInfoDialog(
    title: String,
    confirmLabel: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 560.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
                Spacer(Modifier.height(18.dp))
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun PlusGroup(
    haptics: ImpulsiveHaptics,
    onViewPlus: () -> Unit,
    premiumEntitlement: PremiumEntitlement,
    restoreState: BillingRestoreState,
    onRestorePurchases: () -> Unit,
    subscriptionCatalogState: SubscriptionCatalogState,
    onRetryBilling: () -> Unit,
) {
    val context = LocalContext.current
    val activeProductId = activePlaySubscriptionProductId(
        entitlement = premiumEntitlement,
        nowMillis = System.currentTimeMillis(),
    )
    val plusFlow = rememberInfiniteTransition(label = "PlusFlow")
    val plusPhase by plusFlow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "PlusFlowPhase",
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val plusSurfaceAlpha = if (isDarkTheme) 0.42f else 0.34f
    val plusButtonAlpha = if (isDarkTheme) 0.78f else 0.64f

    AccordionGroup(
        title = "Impulsive Plus",
        summary = "Website Protection",
        icon = Icons.Filled.AutoAwesome,
        haptics = haptics,
        leadingContent = {
            PlusLeadingIcon(phase = plusPhase, alpha = plusSurfaceAlpha)
        },
        headerExtra = { PlusBadge(phase = plusPhase) },
        glowSpec = SettingsGlowSpec.rainbow(PlusRainbowGlow),
    ) {
        Text(
            text = "Includes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PlusFeatureRow(
            title = "Website Protection",
            note = "Blocks adult and risky websites using local DNS-based filtering.",
        )
        SettingsDivider()
        PlusFeatureRow(
            title = "Safer browser protection",
            note = "Adds another layer when browser searches or websites become a trigger.",
        )
        SettingsDivider()
        PlusFeatureRow(
            title = "Stronger anti-bypass support",
            note = "Helps reduce access to risky sites during protected windows.",
        )
        SettingsDivider()
        when (subscriptionCatalogState) {
            SubscriptionCatalogState.Loading -> {
                PlusFeatureRow(
                    title = "Subscription pricing",
                    note = "Loading current Google Play pricing\u2026",
                )
            }

            SubscriptionCatalogState.Unavailable -> {
                PlusFeatureRow(
                    title = "Subscription pricing unavailable",
                    note = "Current Google Play pricing could not be loaded.",
                )
                SettingsDivider()
                SettingsRow(
                    title = "Retry billing",
                    subtext = "Try loading current Google Play pricing again.",
                    onClick = {
                        haptics.confirm()
                        onRetryBilling()
                    },
                )
            }

            is SubscriptionCatalogState.Ready -> {
                val monthlyPlan = subscriptionCatalogState.monthly
                val yearlyPlan = subscriptionCatalogState.yearly

                if (monthlyPlan != null) {
                    PlusFeatureRow(
                        title = subscriptionPlanTitle(monthlyPlan),
                        note = subscriptionPlanDisclosure(monthlyPlan),
                    )
                }

                if (monthlyPlan != null && yearlyPlan != null) {
                    SettingsDivider()
                }

                if (yearlyPlan != null) {
                    PlusFeatureRow(
                        title = subscriptionPlanTitle(yearlyPlan),
                        note = subscriptionPlanDisclosure(yearlyPlan),
                    )
                }
            }
        }

        if (activeProductId != null) {
            SettingsDivider()
            SettingsRow(
                title = "Manage subscription",
                subtext = "Open Google Play to manage or cancel your Plus subscription.",
                value = "Google Play",
                onClick = {
                    haptics.confirm()

                    val opened = openGooglePlaySubscriptionManagement(
                        context = context,
                        productId = activeProductId,
                    )

                    if (!opened) {
                        Toast.makeText(
                            context,
                            "Google Play subscriptions could not be opened.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        val restoreSubtext = when (restoreState) {
            BillingRestoreState.Idle ->
                "Check Google Play and restore an existing Plus subscription."

            BillingRestoreState.Loading ->
                "Checking Google Play and verifying your subscription\u2026"

            BillingRestoreState.Success ->
                "Your Plus subscription has been restored."

            BillingRestoreState.NoPurchase ->
                "No active Plus subscription was found on this Google Play account."

            BillingRestoreState.Error ->
                "Restore failed. Check your connection and try again."
        }

        SettingsDivider()
        SettingsRow(
            title = "Restore purchases",
            subtext = restoreSubtext,
            trailing = if (restoreState == BillingRestoreState.Loading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                null
            },
            onClick = if (restoreState == BillingRestoreState.Loading) {
                null
            } else {
                {
                    haptics.confirm()
                    onRestorePurchases()
                }
            },
        )

        Spacer(modifier = Modifier.height(14.dp))

        val plusButtonShape = RoundedCornerShape(16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(plusButtonShape)
                .plusRainbowRoundedBackground(
                    phase = plusPhase,
                    cornerRadius = 16.dp,
                    alpha = plusButtonAlpha,
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.26f),
                    shape = plusButtonShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    haptics.confirm()
                    onViewPlus()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "View Website Protection",
                color = Color(0xFF281D38),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Shown only in calm places like Settings. Never during a difficult habit moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AccordionGroup(
    title: String,
    summary: String,
    icon: ImageVector,
    haptics: ImpulsiveHaptics? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    glowSpec: SettingsGlowSpec? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val borderFlowRotation = if (glowSpec?.animated == true) {
        val infiniteTransition = rememberInfiniteTransition(label = "$title-border-flow")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "$title-border-rotation",
        )
        rotation
    } else {
        0f
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) SettingsArrowExpandMillis else SettingsArrowCollapseMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "$title-arrow-rotation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .settingsGlowBorder(
                isDarkTheme = isDarkTheme,
                glowSpec = glowSpec,
                borderFlowRotation = borderFlowRotation,
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        border = if (isDarkTheme && glowSpec != null && !glowSpec.animated) {
            BorderStroke(1.2.dp, glowSpec.colors.first().copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        haptics?.light()
                        expanded = !expanded
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ImpulsivePsychological.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
                headerExtra?.invoke()
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = settingsExpandEnter(),
                exit = settingsCollapseExit(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    content = content,
                )
            }
        }
    }
}

private fun settingsExpandEnter() =
    expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = tween(durationMillis = SettingsExpandMillis, easing = FastOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SettingsFadeInMillis,
            delayMillis = SettingsFadeInDelayMillis,
            easing = FastOutSlowInEasing,
        ),
    )

private fun settingsCollapseExit() =
    fadeOut(
        animationSpec = tween(durationMillis = SettingsFadeOutMillis, easing = FastOutSlowInEasing),
    ) + shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = tween(durationMillis = SettingsCollapseMillis, easing = FastOutSlowInEasing),
    )

private data class SettingsGlowSpec(
    val colors: List<Color>,
    val animated: Boolean = false,
    val split: Boolean = false,
) {
    companion object {
        fun single(color: Color): SettingsGlowSpec = SettingsGlowSpec(colors = listOf(color))
        fun split(left: Color, right: Color): SettingsGlowSpec = SettingsGlowSpec(
            colors = listOf(left, right),
            split = true,
        )
        fun rainbow(colors: List<Color>): SettingsGlowSpec = SettingsGlowSpec(
            colors = colors,
            animated = true,
        )
    }
}

private fun Modifier.settingsGlowBorder(
    isDarkTheme: Boolean,
    glowSpec: SettingsGlowSpec?,
    borderFlowRotation: Float,
): Modifier {
    if (glowSpec == null) return this
    if (!isDarkTheme && !glowSpec.animated) return this

    val glowAlpha = when {
        isDarkTheme -> 0.20f
        glowSpec.animated -> 0.10f
        else -> 0f
    }

    val borderAlpha = when {
        isDarkTheme && glowSpec.animated -> 0.95f
        isDarkTheme -> 0.78f
        glowSpec.animated -> 0.62f
        else -> 0f
    }

    return drawWithContent {
        drawContent()

        val cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
        val glowWidth = 7.dp.toPx()
        val borderWidth = 1.35.dp.toPx()
        val borderInset = borderWidth / 2f
        val borderSize = Size(
            width = size.width - borderWidth,
            height = size.height - borderWidth,
        )
        val glowInset = glowWidth / 2f
        val glowSize = Size(
            width = size.width - glowWidth,
            height = size.height - glowWidth,
        )

        val glowBrush = settingsGlowBrush(
            spec = glowSpec,
            width = size.width,
            height = size.height,
            rotationDegrees = borderFlowRotation,
            alpha = glowAlpha,
        )
        val borderBrush = settingsGlowBrush(
            spec = glowSpec,
            width = size.width,
            height = size.height,
            rotationDegrees = borderFlowRotation,
            alpha = borderAlpha,
        )

        drawRoundRect(
            brush = glowBrush,
            topLeft = Offset(glowInset, glowInset),
            size = glowSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = glowWidth),
        )
        drawRoundRect(
            brush = borderBrush,
            topLeft = Offset(borderInset, borderInset),
            size = borderSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = borderWidth),
        )
    }
}

private fun Modifier.plusRainbowRoundedBackground(
    phase: Float,
    cornerRadius: androidx.compose.ui.unit.Dp,
    alpha: Float,
): Modifier = drawWithContent {
    val travel = size.width * 1.45f
    val startX = -travel + (travel * 2f * phase)
    val brush = Brush.linearGradient(
        colors = PlusRainbowGlow.map { it.copy(alpha = alpha) },
        start = Offset(startX, 0f),
        end = Offset(startX + travel, size.height),
    )

    drawRoundRect(
        brush = brush,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
    )

    drawContent()
}

private fun settingsGlowBrush(
    spec: SettingsGlowSpec,
    width: Float,
    height: Float,
    rotationDegrees: Float,
    alpha: Float,
): Brush {
    val colours = spec.colors.map { it.copy(alpha = alpha) }

    if (spec.animated) {
        val radians = rotationDegrees / 180f * PI.toFloat()
        val radius = max(width, height)
        val center = Offset(width / 2f, height / 2f)
        val direction = Offset(
            x = cos(radians) * radius,
            y = sin(radians) * radius,
        )
        return Brush.linearGradient(
            colors = colours,
            start = center - direction,
            end = center + direction,
        )
    }

    if (spec.split && colours.size >= 2) {
        return Brush.horizontalGradient(
            colors = listOf(colours[0], colours[0], colours[1], colours[1]),
            startX = 0f,
            endX = width,
        )
    }

    val colour = colours.first()
    return Brush.linearGradient(
        colors = listOf(
            colour.copy(alpha = alpha),
            colour.copy(alpha = alpha * 0.52f),
            colour.copy(alpha = alpha),
        ),
        start = Offset.Zero,
        end = Offset(width, height),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtext: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val minimumRowHeight = if (subtext == null) 48.dp else 64.dp
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minimumRowHeight)
        .then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
            } else {
                Modifier
            },
        )

    Row(
        modifier = rowModifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 14.sp,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .widthIn(max = 140.dp),
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 10.dp)) {
                trailing()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun PillLabel(text: String) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.28f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PlusBadge(
    phase: Float,
) {
    val shape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .clip(shape)
            .plusRainbowRoundedBackground(
                phase = phase,
                cornerRadius = 50.dp,
                alpha = 0.48f,
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = shape,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PLUS",
            color = Color(0xFF281D38),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlusLeadingIcon(
    phase: Float,
    alpha: Float,
) {
    val shape = CircleShape

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .plusRainbowRoundedBackground(
                phase = phase,
                cornerRadius = 50.dp,
                alpha = alpha,
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFF281D38),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun PlusFeatureRow(
    title: String,
    note: String = "Included in Plus",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TextButtonPill(
    text: String,
    haptics: ImpulsiveHaptics? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = ImpulsivePsychological.copy(alpha = 0.28f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable {
            haptics?.light()
            onClick()
        },
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    haptics: ImpulsiveHaptics,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = { next ->
            if (next != checked) {
                haptics.light()
                onCheckedChange(next)
            }
        },
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics {
                        contentDescription = accessibilityLabel
                    }
                } else {
                    Modifier
                },
            ),
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            checkedTrackColor = ImpulsivePsychological,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AvatarCircle(
    avatar: AvatarStyle,
    size: androidx.compose.ui.unit.Dp,
    imageSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatar.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = avatar.drawableResId),
            contentDescription = avatar.contentDescription,
            modifier = Modifier
                .size(imageSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun isRuntimeNotificationPermissionGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun AccountDeletionFlow(
    state: AccountDeletionUiState,
    onSubmitPassword: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    LaunchedEffect(state) {
        if (state == AccountDeletionUiState.Deleted) {
            onDeleted()
        }
    }

    when (state) {
        AccountDeletionUiState.InProgress -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Deleting your account") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("This will only take a moment.")
                    }
                },
            )
        }
        is AccountDeletionUiState.NeedsPassword -> {
            DeleteAccountPasswordDialog(
                email = state.email,
                onConfirm = onSubmitPassword,
                onDismiss = onDismiss,
            )
        }
        is AccountDeletionUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Could not delete account") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                },
            )
        }
        AccountDeletionUiState.Idle,
        AccountDeletionUiState.Deleted -> Unit
    }
}

@Composable
private fun DeleteAccountPasswordDialog(
    email: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm your password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (email.isNullOrBlank()) {
                        "Enter your password to permanently delete your account."
                    } else {
                        "Enter the password for $email to permanently delete your account."
                    }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank(),
            ) { Text("Delete account") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CloudRecoveryPasswordDialog(
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val canConfirm = hasValidCloudRecoveryPassword(password, confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a recovery password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This password protects your encrypted recovery copy. It cannot be reset. If you lose it, nobody, including Impulsive, can recover your backup.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Recovery password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    singleLine = true,
                    label = { Text("Confirm recovery password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text("Use at least 10 characters.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val passwordChars = password.toCharArray()
                    password = ""
                    confirmation = ""
                    onConfirm(passwordChars)
                },
            ) { Text("Turn on") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
@Composable
private fun ManualBackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val canConfirm = password.length >= 6 &&
        (!requireConfirmation || confirmPassword == password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        singleLine = true,
                        label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text("Use at least 6 characters. Impulsive cannot recover this password.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = canConfirm,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun openImpulsiveWebPage(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false

    if (uri.scheme != "https") {
        return false
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun openWebPageOrShowError(context: Context, url: String) {
    if (!openImpulsiveWebPage(context, url)) {
        Toast.makeText(
            context,
            "No browser is available to open this page.",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

internal fun sendSupportEmail(context: Context, subject: String, body: String = "") {
    val uri = Uri.parse(
        "mailto:Hello@useimpulsive.com" +
            "?subject=" + Uri.encode(subject) +
            (if (body.isNotBlank()) "&body=" + Uri.encode(body) else "")
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
}

internal fun appVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

private fun answerListSummary(
    selectedIds: List<String>,
    labels: Map<String, String>,
    emptyText: String,
): String {
    val selected = selectedIds.mapNotNull { labels[it] }
    return when {
        selected.isEmpty() -> emptyText
        selected.size == 1 -> selected.first()
        else -> "${selected.size} saved"
    }
}

private fun answerLabel(
    selectedId: String?,
    labels: Map<String, String>,
    emptyText: String,
): String = selectedId?.let { labels[it] } ?: emptyText

private enum class RecoveryEditTarget { Triggers, Timing, WeeklyTarget }

private const val SettingsExpandMillis = 220
private const val SettingsCollapseMillis = 170
private const val SettingsFadeInMillis = 120
private const val SettingsFadeInDelayMillis = 35
private const val SettingsFadeOutMillis = 85
private const val SettingsArrowExpandMillis = 180
private const val SettingsArrowCollapseMillis = 140

private val SettingsBoxBorder = Color(0xFFD0C3F1)
private val ProfileGlow = SettingsBoxBorder
private val AppearanceGlow = SettingsBoxBorder
private val RecoverySetupGlow = SettingsBoxBorder
private val ProtectionGlow = SettingsBoxBorder
private val FocusGlow = SettingsBoxBorder
private val PrivacyGlow = SettingsBoxBorder
private val SupportGlow = SettingsBoxBorder
private val PlusRainbowGlow = listOf(
    Color(0xFFD0C3F1),
    Color(0xFFBDE0FE),
    Color(0xFFFEF1AB),
    Color(0xFFF5A7A6),
    Color(0xFFD8B0EB),
    Color(0xFFD0C3F1),
)

private val TriggerLabels = mapOf(
    "social_media" to "Social media",
    "browser_search" to "A browser search",
    "memory_or_thought" to "A memory or thought",
    "boredom" to "Boredom",
    "being_alone" to "Being alone",
    "stress" to "Stress",
)

private val TimingLabels = mapOf(
    "late_at_night" to "Late at night",
    "right_after_waking" to "Right after waking",
    "alone_on_phone" to "Alone on my phone",
    "when_bored" to "When bored",
    "when_stressed" to "When stressed",
    "trouble_sleeping" to "Trouble sleeping",
)

private val WeekOneLabels = mapOf(
    "notice_triggers" to "Notice my cues",
    "daily_reset_habit" to "Build one daily reset habit",
    "cut_down_by_half" to "Cut down by half",
    "cut_down_a_little" to "Cut it down fully",
)
