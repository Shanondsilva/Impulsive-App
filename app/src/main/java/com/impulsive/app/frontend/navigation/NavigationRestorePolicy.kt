package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.data.repository.AuthenticatedOnboardingResolution
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmation
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOwnerConfirmationKind

internal enum class StartupGraphDecision {
    Main,
    Onboarding,
}

internal sealed interface AuthenticatedOnboardingNavigationDecision {
    data object OpenHome : AuthenticatedOnboardingNavigationDecision
    data object RestoreBeforeHome : AuthenticatedOnboardingNavigationDecision
    data object StartSetup : AuthenticatedOnboardingNavigationDecision
    data object ShowRemoteCompletedWithoutLocalData : AuthenticatedOnboardingNavigationDecision
    data object ShowAccountMismatch : AuthenticatedOnboardingNavigationDecision
    data object ShowRestoredSameGoogleIdentityConfirmation : AuthenticatedOnboardingNavigationDecision
    data object ShowRestoredLegacyDriveVerification : AuthenticatedOnboardingNavigationDecision
    data object ShowLegacyUnownedLocalData : AuthenticatedOnboardingNavigationDecision
    data object AwaitRetry : AuthenticatedOnboardingNavigationDecision
}

internal fun chooseStartupGraph(
    isCompleted: Boolean,
    completedAccountUid: String?,
    authenticatedUid: String?,
    authenticatedIsGuest: Boolean,
): StartupGraphDecision {
    if (!isCompleted) {
        return StartupGraphDecision.Onboarding
    }

    /*
     * Account-bound local data may enter Main only when the currently
     * authenticated Firebase UID is exactly the UID that owns the data.
     */
    if (completedAccountUid != null) {
        return if (authenticatedUid == completedAccountUid) {
            StartupGraphDecision.Main
        } else {
            StartupGraphDecision.Onboarding
        }
    }

    /*
     * A completed state without an owner UID is trusted automatically only
     * for an active Guest/anonymous session.
     *
     * For an authenticated non-Guest account this is legacy/unowned data and
     * must go through account resolution.
     *
     * With no authenticated session it is also ambiguous and must not
     * silently open Main.
     */
    return if (
        authenticatedUid != null &&
        authenticatedIsGuest
    ) {
        StartupGraphDecision.Main
    } else {
        StartupGraphDecision.Onboarding
    }
}

internal fun authenticatedOnboardingNavigationDecision(
    resolution: AuthenticatedOnboardingResolution,
    localOnboardingCompleted: Boolean,
): AuthenticatedOnboardingNavigationDecision = when (resolution) {
    AuthenticatedOnboardingResolution.Completed ->
        AuthenticatedOnboardingNavigationDecision.RestoreBeforeHome

    AuthenticatedOnboardingResolution.Incomplete ->
        AuthenticatedOnboardingNavigationDecision.StartSetup

    AuthenticatedOnboardingResolution.RemoteCompletedWithoutLocalData ->
        AuthenticatedOnboardingNavigationDecision.ShowRemoteCompletedWithoutLocalData

    AuthenticatedOnboardingResolution.AccountMismatch ->
        AuthenticatedOnboardingNavigationDecision.ShowAccountMismatch

    AuthenticatedOnboardingResolution.RestoredSameGoogleIdentityNeedsConfirmation ->
        AuthenticatedOnboardingNavigationDecision.ShowRestoredSameGoogleIdentityConfirmation

    AuthenticatedOnboardingResolution.RestoredLegacyOwnershipNeedsDriveVerification ->
        AuthenticatedOnboardingNavigationDecision.ShowRestoredLegacyDriveVerification

    AuthenticatedOnboardingResolution.LegacyUnownedLocalData ->
        AuthenticatedOnboardingNavigationDecision.ShowLegacyUnownedLocalData

    AuthenticatedOnboardingResolution.NotApplicable ->
        if (localOnboardingCompleted) {
            AuthenticatedOnboardingNavigationDecision.OpenHome
        } else {
            AuthenticatedOnboardingNavigationDecision.StartSetup
        }

    is AuthenticatedOnboardingResolution.RetryableFailure ->
        AuthenticatedOnboardingNavigationDecision.AwaitRetry
}

internal fun cloudRecoveryOwnerConfirmationFor(
    kind: CloudRecoveryOwnerConfirmationKind,
): CloudRecoveryOwnerConfirmation =
    when (kind) {
        CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity ->
            CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity
        CloudRecoveryOwnerConfirmationKind.LegacyEnvelope ->
            CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope
    }

internal fun cloudRecoveryOwnerConfirmationCopy(
    kind: CloudRecoveryOwnerConfirmationKind,
): String =
    when (kind) {
        CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity ->
            "This encrypted recovery copy was created with the same linked " +
                "Google identity, but the Firebase account identifier changed. " +
                "Restore and move it to the currently signed-in account?"
        CloudRecoveryOwnerConfirmationKind.LegacyEnvelope ->
            "This encrypted recovery copy was created before Impulsive stored " +
                "a linked-account identity claim. The recovery password verified " +
                "the copy. Restore and move it to the currently signed-in account?"
    }

internal fun sameGoogleRestoreButtonEnabled(
    migrationInProgress: Boolean,
): Boolean = !migrationInProgress

internal fun performSameGoogleRestore(
    onConfirmSameGoogleRestore: () -> Unit,
) {
    onConfirmSameGoogleRestore()
}

internal fun dispatchCloudRestoreSuccess(
    requiresOnboardingSetup: Boolean,
    onReadyForHome: () -> Unit,
    onRequiresOnboardingSetup: () -> Unit,
) {
    if (requiresOnboardingSetup) {
        onRequiresOnboardingSetup()
    } else {
        onReadyForHome()
    }
}
