package com.impulsive.app.backend.data.restore.cloud

internal sealed interface CloudRecoveryOwnerVerdict {
    data object ExactUidMatch : CloudRecoveryOwnerVerdict
    data object SameGoogleIdentityNewFirebaseUid : CloudRecoveryOwnerVerdict
    data object LegacyEnvelope : CloudRecoveryOwnerVerdict
    data object DifferentAccount : CloudRecoveryOwnerVerdict
}

internal fun cloudRecoveryOwnerVerdict(
    ownerUid: String,
    ownerGoogleSubjectHash: String?,
    currentFirebaseUid: String,
    currentGoogleSubjectHash: String?,
): CloudRecoveryOwnerVerdict = when {
    ownerUid == currentFirebaseUid -> CloudRecoveryOwnerVerdict.ExactUidMatch
    ownerGoogleSubjectHash != null &&
        ownerGoogleSubjectHash == currentGoogleSubjectHash ->
        CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid
    ownerGoogleSubjectHash == null -> CloudRecoveryOwnerVerdict.LegacyEnvelope
    else -> CloudRecoveryOwnerVerdict.DifferentAccount
}

internal sealed interface CloudRecoveryOwnerAuthorization {
    data object Authorized : CloudRecoveryOwnerAuthorization
    data object Blocked : CloudRecoveryOwnerAuthorization

    data class ConfirmationRequired(
        val kind: CloudRecoveryOwnerConfirmationKind,
    ) : CloudRecoveryOwnerAuthorization
}

internal fun cloudRecoveryOwnerAuthorization(
    verdict: CloudRecoveryOwnerVerdict,
    confirmation: CloudRecoveryOwnerConfirmation,
): CloudRecoveryOwnerAuthorization =
    when (verdict) {
        CloudRecoveryOwnerVerdict.ExactUidMatch ->
            CloudRecoveryOwnerAuthorization.Authorized
        CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid ->
            if (
                confirmation ==
                CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity
            ) {
                CloudRecoveryOwnerAuthorization.Authorized
            } else {
                CloudRecoveryOwnerAuthorization.ConfirmationRequired(
                    CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity,
                )
            }
        CloudRecoveryOwnerVerdict.LegacyEnvelope ->
            if (
                confirmation ==
                CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope
            ) {
                CloudRecoveryOwnerAuthorization.Authorized
            } else {
                CloudRecoveryOwnerAuthorization.ConfirmationRequired(
                    CloudRecoveryOwnerConfirmationKind.LegacyEnvelope,
                )
            }
        CloudRecoveryOwnerVerdict.DifferentAccount ->
            CloudRecoveryOwnerAuthorization.Blocked
    }
