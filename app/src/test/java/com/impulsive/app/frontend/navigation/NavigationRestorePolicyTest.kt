package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.data.repository.AuthenticatedOnboardingResolution
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRestorePolicyTest {
    @Test
    fun accountBoundCompletionWithSameAuthenticatedUidStartsMain() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = "user-a",
            authenticatedUid = "user-a",
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Main, decision)
    }

    @Test
    fun accountBoundCompletionWithDifferentAuthenticatedUidStartsOnboarding() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = "user-a",
            authenticatedUid = "user-b",
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Onboarding, decision)
    }

    @Test
    fun accountBoundCompletionWithNoFirebaseSessionStartsOnboarding() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = "user-a",
            authenticatedUid = null,
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Onboarding, decision)
    }

    @Test
    fun unownedCompletionWithNormalAuthenticatedAccountStartsOnboarding() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = null,
            authenticatedUid = "normal-user",
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Onboarding, decision)
    }

    @Test
    fun unownedCompletionWithGuestSessionStartsMain() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = null,
            authenticatedUid = "anonymous-guest-uid",
            authenticatedIsGuest = true,
        )

        assertEquals(StartupGraphDecision.Main, decision)
    }

    @Test
    fun unownedCompletionWithNoFirebaseSessionStartsOnboarding() {
        val decision = chooseStartupGraph(
            isCompleted = true,
            completedAccountUid = null,
            authenticatedUid = null,
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Onboarding, decision)
    }

    @Test
    fun incompleteStateStartsOnboardingRegardlessOfAccountInformation() {
        val decision = chooseStartupGraph(
            isCompleted = false,
            completedAccountUid = "user-a",
            authenticatedUid = "user-a",
            authenticatedIsGuest = false,
        )

        assertEquals(StartupGraphDecision.Onboarding, decision)
    }

    @Test
    fun accountSwitchPathDoesNotNavigateDirectlyHome() {
        val source = File(
            "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        ).readText()
        val accountSwitchStart = source.indexOf(
            "LaunchedEffect(authState.accountSwitchCompleted)",
        )
        assertTrue(accountSwitchStart >= 0)
        val accountSwitchEnd = source.indexOf("    fun syncInterruptionPermission", accountSwitchStart)
        assertTrue(accountSwitchEnd > accountSwitchStart)
        val accountSwitchBlock = source.substring(accountSwitchStart, accountSwitchEnd)

        assertFalse(accountSwitchBlock.contains("navController.navigate(AppRoutes.Home)"))
        assertTrue(accountSwitchBlock.contains("OnboardingRoutes.LoginSignupGuest"))
        assertTrue(accountSwitchBlock.contains("consumeAccountSwitchNavigation()"))
    }

    @Test
    fun sameUidCompletedResolutionRestoresBeforeHome() {
        val decision = authenticatedOnboardingNavigationDecision(
            resolution = AuthenticatedOnboardingResolution.Completed,
            localOnboardingCompleted = true,
        )

        assertEquals(
            AuthenticatedOnboardingNavigationDecision.RestoreBeforeHome,
            decision,
        )
    }

    @Test
    fun remoteCompletionOnlyDoesNotOpenHome() {
        val decision = authenticatedOnboardingNavigationDecision(
            resolution = AuthenticatedOnboardingResolution.RemoteCompletedWithoutLocalData,
            localOnboardingCompleted = false,
        )

        assertEquals(
            AuthenticatedOnboardingNavigationDecision.ShowRemoteCompletedWithoutLocalData,
            decision,
        )
    }

    @Test
    fun differentUidDoesNotOpenHome() {
        val decision = authenticatedOnboardingNavigationDecision(
            resolution = AuthenticatedOnboardingResolution.AccountMismatch,
            localOnboardingCompleted = true,
        )

        assertEquals(
            AuthenticatedOnboardingNavigationDecision.ShowAccountMismatch,
            decision,
        )
    }

    @Test
    fun guestCompletionCanOpenHomeAfterAuthenticatedResolutionPath() {
        val decision = authenticatedOnboardingNavigationDecision(
            resolution = AuthenticatedOnboardingResolution.NotApplicable,
            localOnboardingCompleted = true,
        )

        assertEquals(AuthenticatedOnboardingNavigationDecision.OpenHome, decision)
    }
}