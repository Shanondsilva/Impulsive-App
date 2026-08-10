package com.impulsive.app.frontend.screens.onboarding

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionSetupCompletedRevisitSourceTest {
    private val onboardingSource =
        source(
            "frontend/screens/onboarding/ProtectionSetupOnboardingScreens.kt",
        )
    private val appNavHostSource =
        source(
            "frontend/navigation/AppNavHost.kt",
        )
    private val protectionSetupCard =
        onboardingSource.section(
            start = "private fun ProtectionSetupCard(",
            end = "@Composable\nprivate fun ProtectionPrimaryButton(",
        )

    @Test
    fun completedCardsNoLongerHideTheWholeControlRow() {
        assertTrue(protectionSetupCard.contains("val showAction = actionLabel != null && onAction != null"))
        assertTrue(protectionSetupCard.contains("val showSkip = !completed"))
        assertTrue(protectionSetupCard.contains("if (showAction || showSkip)"))
        assertFalse(
            Regex("""if\s*\(\s*!completed\s*\)\s*\{\s*Spacer""")
                .containsMatchIn(protectionSetupCard),
        )
    }

    @Test
    fun actionVisibilityIsIndependentOfCompletion() {
        val showActionLine = protectionSetupCard.lineContaining("val showAction")
        assertTrue(showActionLine.contains("actionLabel != null"))
        assertTrue(showActionLine.contains("onAction != null"))
        assertFalse(showActionLine.contains("completed"))
        assertTrue(protectionSetupCard.contains("if (showAction)"))
        assertTrue(protectionSetupCard.contains("label = requireNotNull(actionLabel)"))
        assertTrue(protectionSetupCard.contains("onClick = requireNotNull(onAction)"))
    }

    @Test
    fun skipVisibilityIsCompletionGated() {
        val skipSection =
            protectionSetupCard.section(
                start = "if (showSkip) {",
                end = "}\n            }",
            )

        assertTrue(protectionSetupCard.contains("val showSkip = !completed"))
        assertTrue(skipSection.contains("TextButton("))
        assertTrue(skipSection.contains("text = \"Do this later\""))
    }

    @Test
    fun completedBlockedAppsRemainEditable() {
        val blockedAppsCard = cardCall(badge = "1", nextBadge = "2")

        assertTrue(blockedAppsCard.contains("completed = state.isComplete(ProtectionSetupItem.BlockedApps)"))
        assertTrue(
            blockedAppsCard.contains(
                "actionLabel = if (state.blockedAppsSelected) \"Edit apps\" else \"Choose apps\"",
            ),
        )
        assertTrue(blockedAppsCard.contains("onAction = onChooseApps"))
    }

    @Test
    fun completedPermissionCardsUseReviewSettingsAndIncompleteCardsRetainAllow() {
        assertPermissionCopy(
            badge = "3",
            nextBadge = "4",
            item = "InterruptionPermission",
        )
        assertPermissionCopy(
            badge = "4",
            nextBadge = "5",
            item = "BackgroundActivity",
        )
        assertPermissionCopy(
            badge = "5",
            nextBadge = null,
            item = "Notifications",
        )
    }

    @Test
    fun usageAccessRemainsRevisitableThroughAuthoritativeCallback() {
        val usageAccessCard = cardCall(badge = "2", nextBadge = "3")

        assertTrue(usageAccessCard.contains("completed = state.isComplete(ProtectionSetupItem.UsageAccess)"))
        assertTrue(usageAccessCard.contains("actionLabel = \"Open settings\""))
        assertTrue(usageAccessCard.contains("onAction = onOpenUsageAccessPermission"))
        assertFalse(usageAccessCard.contains("if (state.isComplete(ProtectionSetupItem.UsageAccess))"))
    }

    @Test
    fun smallActionButtonUsesAccessibleExpandableMinimumHeight() {
        val smallButton =
            onboardingSource.section(
                start = "private fun ProtectionSmallButton(",
                end = "\ninternal val ProtectionPrimaryText",
            )

        assertTrue(onboardingSource.contains("import androidx.compose.foundation.layout.heightIn"))
        assertFalse(smallButton.contains("height(42.dp)"))
        assertTrue(smallButton.contains("heightIn(min = 48.dp)"))
        assertFalse(smallButton.contains("maxHeight"))
    }

    @Test
    fun navigationCallbacksRemainAuthoritative() {
        val protectionSetupRoute =
            appNavHostSource.section(
                start = "composable(OnboardingRoutes.ProtectionSetup)",
                end = "composable(OnboardingRoutes.ProtectionBlockedApps)",
            )

        assertTrue(protectionSetupRoute.contains("onChooseApps = {"))
        assertTrue(
            protectionSetupRoute.contains(
                "navController.navigateOnboarding(OnboardingRoutes.ProtectionBlockedApps)",
            ),
        )
        assertTrue(protectionSetupRoute.contains("onOpenUsageAccessPermission = ::openUsageAccessPermissionSettings"))
        assertTrue(protectionSetupRoute.contains("onOpenInterruptionPermission = ::openInterruptionPermissionSettings"))
        assertTrue(
            protectionSetupRoute.contains(
                "onOpenBackgroundActivityPermission = ::openBackgroundActivityPermissionSettings",
            ),
        )
        assertTrue(protectionSetupRoute.contains("onOpenNotificationPermission = ::manageProtectionNotifications"))
    }

    @Test
    fun deviceStateStillReconcilesOnResume() {
        val syncSection =
            appNavHostSource.section(
                start = "fun syncProtectionSetupFromDevice(",
                end = "if (state.isLoading)",
            )

        assertTrue(syncSection.contains("fun syncProtectionSetupFromDevice(recoverService: Boolean = false)"))
        assertTrue(syncSection.contains("protectionSetupViewModel.setUsageAccessEnabled(usageAccessGranted)"))
        assertTrue(syncSection.contains("protectionSetupViewModel.setInterruptionPermissionEnabled(overlayPermissionGranted)"))
        assertTrue(syncSection.contains("syncBackgroundActivityPermission()"))
        assertTrue(syncSection.contains("syncNotificationPermission()"))
        assertTrue(syncSection.contains("if (event == Lifecycle.Event.ON_RESUME)"))
        assertTrue(syncSection.contains("syncProtectionSetupFromDevice(recoverService = true)"))
    }

    private fun assertPermissionCopy(
        badge: String,
        nextBadge: String?,
        item: String,
    ) {
        val card = cardCall(badge = badge, nextBadge = nextBadge)

        assertTrue(card.contains("completed = state.isComplete(ProtectionSetupItem.$item)"))
        assertTrue(card.contains("actionLabel = if (state.isComplete(ProtectionSetupItem.$item))"))
        assertTrue(card.contains("\"Review settings\""))
        assertTrue(card.contains("\"Allow\""))
    }

    private fun cardCall(badge: String, nextBadge: String?): String {
        val start = "ProtectionSetupCard(\n                badge = \"$badge\""
        val end = nextBadge?.let { "ProtectionSetupCard(\n                badge = \"$it\"" }
            ?: "        }\n    }\n}"
        return onboardingSource.section(start = start, end = end)
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing section start: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing section end: $end" }
        return substring(startIndex, endIndex)
    }

    private fun String.lineContaining(text: String): String =
        lineSequence().first { it.contains(text) }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path")
            .readText()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
}
