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