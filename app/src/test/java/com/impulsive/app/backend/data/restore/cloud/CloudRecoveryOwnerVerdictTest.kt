package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudRecoveryOwnerVerdictTest {
    @Test fun matchingUidIsExactMatch() = assertVerdict(
        CloudRecoveryOwnerVerdict.ExactUidMatch, "owner", "a".repeat(64), "owner", "b".repeat(64),
    )

    @Test fun matchingGoogleHashesAcrossUidsAllowSameGoogleIdentity() = assertVerdict(
        CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid, "old", "a".repeat(64), "new", "a".repeat(64),
    )

    @Test fun missingStoredHashIsLegacyEnvelope() = assertVerdict(
        CloudRecoveryOwnerVerdict.LegacyEnvelope, "old", null, "new", "a".repeat(64),
    )

    @Test fun differentGoogleHashesAreDifferentAccounts() = assertVerdict(
        CloudRecoveryOwnerVerdict.DifferentAccount, "old", "a".repeat(64), "new", "b".repeat(64),
    )

    @Test fun nullCurrentHashDoesNotMatchStoredHash() = assertVerdict(
        CloudRecoveryOwnerVerdict.DifferentAccount, "old", "a".repeat(64), "new", null,
    )

    @Test fun nullStoredHashDoesNotMatchCurrentHash() = assertVerdict(
        CloudRecoveryOwnerVerdict.LegacyEnvelope, "old", null, "new", "a".repeat(64),
    )

    private fun assertVerdict(expected: CloudRecoveryOwnerVerdict, ownerUid: String, ownerHash: String?, currentUid: String, currentHash: String?) {
        assertEquals(expected, cloudRecoveryOwnerVerdict(ownerUid, ownerHash, currentUid, currentHash))
    }
}