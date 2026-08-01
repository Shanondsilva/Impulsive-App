package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMonitorServiceFallbackNotificationSourceTest {
    private val helperSource = source(
        "src/main/java/com/impulsive/app/backend/service/protection/ProtectionNotificationHelper.kt",
    )
    private val serviceSource = source(
        "src/main/java/com/impulsive/app/backend/service/protection/AppMonitorService.kt",
    )
    private val vpnSource = source(
        "src/main/java/com/impulsive/app/backend/service/protection/ImpulsiveVpnService.kt",
    )
    private val coordinatorSource = source(
        "src/main/java/com/impulsive/app/backend/service/protection/" +
            "InterruptionNotificationReminderCoordinator.kt",
    )
    private val mainActivitySource = source(
        "src/main/java/com/impulsive/app/MainActivity.kt",
    )
    private val setupViewModelSource = source(
        "src/main/java/com/impulsive/app/backend/session/protection/ProtectionSetupViewModel.kt",
    )
    private val dayColors = source("src/main/res/values/colors.xml")
    private val stringsSource = source("src/main/res/values/strings.xml")

    @Test
    fun notificationUsesExactCopyStableIdAndAlertOnceUpdates() {
        val fallback = fallbackHelperSource()

        assertTrue(helperSource.contains("Pause before you continue"))
        assertTrue(helperSource.contains(
            "Protected content was detected. Choose one quick reset before continuing.",
        ))
        assertTrue(fallback.contains("BlockedAttemptNotificationId"))
        assertTrue(fallback.contains(".setOnlyAlertOnce(true)"))
        assertTrue(fallback.contains("NotificationCompat.BigTextStyle()"))
        assertTrue(fallback.contains(".setContentIntent(destination.contentIntent)"))
        assertTrue(fallback.contains(".setDeleteIntent(deletePendingIntent)"))
        assertFalse(fallback.contains("NotificationCompat.DecoratedCustomViewStyle()"))
        assertFalse(fallback.contains("setCustomContentView"))
        assertFalse(fallback.contains("setCustomBigContentView"))
        assertFalse(fallback.contains("setCustomHeadsUpContentView"))
        assertFalse(fallback.contains("setFullScreenIntent"))
    }

    @Test
    fun genericDestinationBranchConstructsBothOrdinaryActionsAndDestinations() {
        val fallback = fallbackHelperSource()
        val genericBranch = genericDestinationBranch(fallback)

        assertTrue(fallback.contains("InterruptionGameRequestCode"))
        assertTrue(fallback.contains("InterruptionReadingRequestCode"))
        assertTrue(genericBranch.contains("gamePendingIntent"))
        assertTrue(genericBranch.contains("readingPendingIntent"))
        assertTrue(genericBranch.contains("launchTarget = BlockLaunchTarget.RandomRecoveryGame"))
        assertTrue(genericBranch.contains("launchTarget = BlockLaunchTarget.ReadingReset"))
        assertEquals(2, genericBranch.split("InterruptionNotificationAction(").size - 1)
        assertTrue(genericBranch.contains("R.string.notif_action_game"))
        assertTrue(genericBranch.contains("R.string.notif_action_reading"))
        assertFalse(genericBranch.contains("BlockLaunchTarget.FocusRecovery"))
        assertFalse(genericBranch.contains("InterruptionFocusOptionsRequestCode"))
        assertTrue(helperSource.contains("ActionOpenInterruptionGame"))
        assertTrue(helperSource.contains("ActionOpenInterruptionReading"))
        assertFalse(fallback.contains("interruption_notification_game"))
        assertFalse(fallback.contains("interruption_notification_reading"))
        assertFalse(helperSource.contains("RemoteViews"))
    }

    @Test
    fun notificationUsesSystemTemplateWithoutDuplicatedCustomControls() {
        val fallback = fallbackHelperSource()

        assertFalse(helperSource.contains("import android.widget.RemoteViews"))
        assertFalse(fallback.contains("RemoteViews("))
        assertFalse(fallback.contains("R.layout.notification_interruption_fallback"))
        assertTrue(fallback.contains("R.color.protection_notification_accent"))
        assertTrue(dayColors.contains(
            "<color name=\"protection_notification_accent\">#D0C3F1</color>",
        ))
        assertFalse(dayColors.contains("protection_notification_text_primary"))
        assertFalse(dayColors.contains("protection_notification_text_secondary"))
        assertFalse(
            File(
                "src/main/res/layout/notification_interruption_fallback.xml",
            ).exists(),
        )
    }

    @Test
    fun notificationUsesExactUserFacingActionLabels() {
        assertTrue(stringsSource.contains(
            "<string name=\"notif_action_game\">Quick game</string>",
        ))
        assertTrue(stringsSource.contains(
            "<string name=\"notif_action_reading\">Reset reading</string>",
        ))
    }

    @Test
    fun focusFallbackHasItsOwnTruthfulTitleAndBody() {
        assertTrue(stringsSource.contains(
            "<string name=\"notif_focus_active_title\">Focus Mode is active</string>",
        ))
        assertTrue(stringsSource.contains(
            "notif_focus_fallback_body\">%1\$s is blocked during this focus session. " +
                "Open Focus options to continue.</string>",
        ))
        assertTrue(stringsSource.contains(
            "<string name=\"notif_action_focus_options\">Focus options</string>",
        ))
        assertTrue(helperSource.contains("val isFocusFallback = isFocusSession && adaptiveDecisionId == null"))
        assertTrue(helperSource.contains("isFocusFallback -> context.getString(R.string.notif_focus_active_title)"))
        assertTrue(
            helperSource.contains(
                "isFocusFallback -> context.getString(R.string.notif_focus_fallback_body, sourceLabel)",
            ),
        )
    }

    @Test
    fun focusDestinationBranchConstructsOnlyFocusRecoveryWithOneAction() {
        val fallback = fallbackHelperSource()
        val focusBranch = focusDestinationBranch(fallback)

        assertTrue(fallback.contains("InterruptionFocusOptionsRequestCode"))
        assertTrue(fallback.contains(".setContentIntent(destination.contentIntent)"))
        assertTrue(fallback.contains("destination.actions.forEach { action ->"))

        assertTrue(focusBranch.contains("val focusPendingIntent"))
        assertTrue(focusBranch.contains("launchTarget = BlockLaunchTarget.FocusRecovery"))
        assertTrue(focusBranch.contains("ActionOpenInterruptionFocusOptions"))
        assertTrue(focusBranch.contains("contentIntent = focusPendingIntent"))
        assertTrue(focusBranch.contains("R.string.notif_action_focus_options"))
        assertEquals(1, focusBranch.split("InterruptionNotificationAction(").size - 1)

        // The Focus branch must neither construct nor expose the ordinary
        // Game/Reading actions or destinations.
        assertFalse(focusBranch.contains("BlockLaunchTarget.RandomRecoveryGame"))
        assertFalse(focusBranch.contains("BlockLaunchTarget.ReadingReset"))
        assertFalse(focusBranch.contains("InterruptionGameRequestCode"))
        assertFalse(focusBranch.contains("InterruptionReadingRequestCode"))
        assertFalse(focusBranch.contains("R.string.notif_action_game"))
        assertFalse(focusBranch.contains("R.string.notif_action_reading"))
        assertFalse(focusBranch.contains("gamePendingIntent"))
        assertFalse(focusBranch.contains("readingPendingIntent"))
    }

    @Test
    fun adaptiveDestinationBranchRemainsSeparateWithNoActions() {
        val fallback = fallbackHelperSource()
        val adaptiveBranch = adaptiveDestinationBranch(fallback)

        assertTrue(adaptiveBranch.contains("createAdaptiveMomentIntent"))
        assertTrue(adaptiveBranch.contains("actions = emptyList()"))
        assertFalse(adaptiveBranch.contains("BlockLaunchTarget.FocusRecovery"))
        assertFalse(adaptiveBranch.contains("BlockLaunchTarget.RandomRecoveryGame"))
        assertFalse(adaptiveBranch.contains("BlockLaunchTarget.ReadingReset"))
    }

    @Test
    fun focusFallbackHideSensitiveCopyNeverExposesSourceLabel() {
        val fallback = fallbackHelperSource()
        val bodyBlock = fallback.substring(
            fallback.indexOf("val displayedBody = when {"),
            fallback.indexOf("val builder ="),
        )

        assertTrue(bodyBlock.contains("hideSensitive -> \"Open Impulsive to continue.\""))
        assertTrue(
            bodyBlock.contains(
                "isFocusFallback -> context.getString(R.string.notif_focus_fallback_body, sourceLabel)",
            ),
        )
        // hideSensitive must be evaluated before the Focus/sourceLabel branch so a
        // hidden-sensitive Focus fallback can never leak sourceLabel.
        assertTrue(bodyBlock.indexOf("hideSensitive ->") < bodyBlock.indexOf("isFocusFallback ->"))
    }

    @Test
    fun websitePollUsesFrictionTriggerBoundedScheduleAndImmutableStart() {
        val websiteSurface = serviceSource.substring(
            serviceSource.indexOf("private fun launchWebsiteProtectionIncidentSurface"),
            serviceSource.indexOf("private fun handleFocusInterruption"),
        )

        assertTrue(serviceSource.contains("canStartWebsiteInterruption(currentWebsiteIncident.phase)"))
        assertTrue(serviceSource.contains("currentWebsiteIncident != null"))
        assertTrue(websiteSurface.contains("incident.incidentStartedAtEpochMillis"))
        assertTrue(websiteSurface.contains("beginFallbackNotificationIncident"))
        assertTrue(websiteSurface.contains("scheduleFallbackNotificationStages"))
        assertTrue(websiteSurface.contains("InterruptionNotificationIncidentId"))
        assertTrue(websiteSurface.contains("isWebsiteIncident = true"))
        assertFalse(serviceSource.contains("launchWebsiteProtectionCooldownOverlay"))
        assertFalse(serviceSource.contains("Impulsive caught the pattern"))
    }

    @Test
    fun normalManagedBrowserBypassesGenericInterceptionWithoutCreatingIncident() {
        val bypass = serviceSource.indexOf(
            "shouldBypassGenericAppInterceptionForWebsiteProtection(",
        )
        val genericHandling = serviceSource.indexOf("handleBlockedAppOpen(")

        assertTrue(bypass >= 0)
        assertTrue(bypass < genericHandling)
        assertTrue(serviceSource.contains("currentSetup.websiteProtectedAppPackageNames"))
        assertTrue(serviceSource.contains("currentWebsiteIncident != null"))
        assertFalse(serviceSource.contains("ForegroundInterruptionOwner"))
        assertFalse(serviceSource.contains("foregroundInterruptionOwner("))
    }

    @Test
    fun establishedWebsiteFallbackEligibilityDoesNotReadIncidentLeaseOrRequireCooldown() {
        val eligibility = serviceSource.substring(
            serviceSource.indexOf("private fun fallbackIncidentStillEligible"),
            serviceSource.indexOf("private fun currentFallbackIncidentId"),
        )

        assertTrue(eligibility.contains("isWebsiteFallbackIncidentEligible"))
        assertTrue(eligibility.contains("websiteProtectedAppPackageNames"))
        assertFalse(eligibility.contains("reconcileForegroundPackage"))
        assertFalse(eligibility.contains("WebsiteProtectionIncidentPhase.Cooldown"))
        assertFalse(eligibility.contains("isCooldownActive"))
        assertFalse(serviceSource.contains("if (activeFallbackIncidentIsWebsite)"))
    }

    @Test
    fun attributionUsesExactOwnerAndFreshRegistryWithNonSensitiveDiagnostics() {
        val diagnostic = vpnSource.substring(
            vpnSource.indexOf("ProtectionLog.debugThrottled(", vpnSource.indexOf("exactPackage")),
            vpnSource.indexOf("attributionDecision.packageName"),
        )

        assertTrue(vpnSource.contains("resolveExact("))
        assertTrue(vpnSource.contains("RecentForegroundWebsiteBrowserRegistry.freshObservation"))
        assertTrue(diagnostic.contains("attributedPackage="))
        assertTrue(diagnostic.contains("currentForegroundPackage="))
        assertTrue(diagnostic.contains("recentForegroundPackage="))
        assertTrue(diagnostic.contains("reason="))
        assertFalse(diagnostic.contains("blockedEntry"))
        assertFalse(diagnostic.contains("domain"))
    }

    @Test
    fun reminderStagesLogBoundedLifecycleEventsWithoutPollLogging() {
        assertTrue(coordinatorSource.contains("scheduled stage="))
        assertTrue(coordinatorSource.contains("evaluated stage="))
        assertTrue(coordinatorSource.contains("cancelled stage="))
        assertTrue(coordinatorSource.contains("reason="))
        assertTrue(serviceSource.contains("posted stage="))
    }

    @Test
    fun notificationDismissalRecordsStageWithoutEndingIncident() {
        val fallback = fallbackHelperSource()
        val dismissalAction = serviceSource.substring(
            serviceSource.indexOf("ActionFallbackNotificationDismissed ->"),
            serviceSource.indexOf("ActionStop ->"),
        )

        assertTrue(fallback.contains(".setDeleteIntent(deletePendingIntent)"))
        assertTrue(fallback.contains("BlockedAttemptNotificationId"))
        assertTrue(helperSource.contains("const val BlockedAttemptNotificationId = 4202"))
        assertTrue(dismissalAction.contains("recordFallbackNotificationDismissed(intent)"))
        assertFalse(dismissalAction.contains("endFallbackNotificationIncident"))
        assertTrue(serviceSource.contains("fallbackReminderCoordinator.recordDismissed"))
    }

    @Test
    fun incidentEndPathsDismissNotificationAndInvalidateStages() {
        val endIncident = serviceSource.substring(
            serviceSource.indexOf("private fun endFallbackNotificationIncident"),
            serviceSource.indexOf("private fun emptyTaskRewardStoreState"),
        )

        assertTrue(endIncident.contains("InterruptionNotificationLimiter.endAppEncounter"))
        assertTrue(endIncident.contains("fallbackReminderCoordinator.cancel"))
        assertTrue(endIncident.contains("notificationHelper.cancelBlockedAttemptNotification()"))
        assertTrue(serviceSource.contains("foregroundPackage != activeFallbackIncidentPackageName"))
        assertTrue(serviceSource.contains("!usageAccessChecker.hasUsageAccess()"))
        assertTrue(serviceSource.contains("windowSnapshot.isProtectionPaused"))
        assertTrue(serviceSource.contains("isAllowActiveImmediately"))
        assertTrue(serviceSource.contains("override fun onDestroy()"))
        assertTrue(mainActivitySource.contains("InterruptionNotificationLimiter.endAppEncounter"))
        assertTrue(mainActivitySource.contains("ActionEndFallbackNotificationIncident"))
        assertTrue(setupViewModelSource.contains("fun setWebsiteProtectionEnabled"))
        assertTrue(setupViewModelSource.contains("InterruptionNotificationLimiter.clearAppEncounters()"))
    }

    private fun fallbackHelperSource(): String = helperSource.substring(
        helperSource.indexOf("fun showInterruptionFallback"),
        helperSource.indexOf("fun showProtectionRecoveryNotification"),
    )

    /**
     * The `val destination = when { ... }` block: each branch below constructs
     * only the PendingIntents that branch actually needs, so scoping assertions
     * to one branch proves the others were never touched.
     */
    private fun destinationBlock(fallback: String): String = fallback.substring(
        fallback.indexOf("val destination = when {"),
        fallback.indexOf("val deletePendingIntent = PendingIntent.getService("),
    )

    private fun adaptiveDestinationBranch(fallback: String): String {
        val block = destinationBlock(fallback)
        return block.substring(
            block.indexOf("adaptiveDecisionId != null -> InterruptionNotificationDestinationConfig("),
            block.indexOf("isFocusFallback -> {"),
        )
    }

    private fun focusDestinationBranch(fallback: String): String {
        val block = destinationBlock(fallback)
        return block.substring(block.indexOf("isFocusFallback -> {"), block.indexOf("else -> {"))
    }

    private fun genericDestinationBranch(fallback: String): String {
        val block = destinationBlock(fallback)
        return block.substring(block.indexOf("else -> {"))
    }

    private fun source(relativePath: String): String =
        File(relativePath).readText()
}
