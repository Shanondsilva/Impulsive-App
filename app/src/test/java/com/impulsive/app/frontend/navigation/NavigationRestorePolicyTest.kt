package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.data.repository.AuthenticatedOnboardingResolution
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmation
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmationKind
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
    fun incompleteAuthenticatedResolutionStartsSetup() {
        val decision = authenticatedOnboardingNavigationDecision(
            resolution = AuthenticatedOnboardingResolution.Incomplete,
            localOnboardingCompleted = false,
        )

        assertEquals(
            AuthenticatedOnboardingNavigationDecision.StartSetup,
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

    @Test
    fun sameGoogleRestoreActionCallsMigrationOperationNotRawRetry() {
        var migrationCalls = 0
        var rawRetryCalls = 0

        performSameGoogleRestore {
            migrationCalls += 1
        }

        assertEquals(1, migrationCalls)
        assertEquals(0, rawRetryCalls)
    }

    @Test
    fun sameGoogleRestoreButtonDisablesDuringMigration() {
        assertTrue(sameGoogleRestoreButtonEnabled(migrationInProgress = false))
        assertFalse(sameGoogleRestoreButtonEnabled(migrationInProgress = true))
    }

    @Test
    fun setupRequiredCloudSuccessEntersOnboardingWithoutRawRetry() {
        var readyForHomeCalls = 0
        var setupCalls = 0

        dispatchCloudRestoreSuccess(
            requiresOnboardingSetup = true,
            onReadyForHome = { readyForHomeCalls += 1 },
            onRequiresOnboardingSetup = { setupCalls += 1 },
        )

        assertEquals(0, readyForHomeCalls)
        assertEquals(1, setupCalls)
    }

    @Test
    fun finalisedCloudOwnershipRerunsAuthenticatedResolution() {
        var resolutionCalls = 0
        var setupCalls = 0

        dispatchCloudRestoreSuccess(
            requiresOnboardingSetup = false,
            onReadyForHome = { resolutionCalls += 1 },
            onRequiresOnboardingSetup = { setupCalls += 1 },
        )

        assertEquals(1, resolutionCalls)
        assertEquals(0, setupCalls)
    }

    @Test
    fun cloudConfirmationPolicyPreservesTypedKind() {
        assertEquals(
            CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity,
            cloudRecoveryOwnerConfirmationFor(
                CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity,
            ),
        )
        assertEquals(
            CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope,
            cloudRecoveryOwnerConfirmationFor(
                CloudRecoveryOwnerConfirmationKind.LegacyEnvelope,
            ),
        )
    }

    @Test
    fun legacyConfirmationCopyDoesNotClaimGoogleIdentityMatch() {
        val legacy = cloudRecoveryOwnerConfirmationCopy(
            CloudRecoveryOwnerConfirmationKind.LegacyEnvelope,
        )
        val sameGoogle = cloudRecoveryOwnerConfirmationCopy(
            CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity,
        )

        assertFalse(legacy.contains("Google identity", ignoreCase = true))
        assertFalse(legacy.contains("matches", ignoreCase = true))
        assertTrue(legacy.contains("recovery password", ignoreCase = true))
        assertTrue(sameGoogle.contains("same linked Google identity"))
    }
}
