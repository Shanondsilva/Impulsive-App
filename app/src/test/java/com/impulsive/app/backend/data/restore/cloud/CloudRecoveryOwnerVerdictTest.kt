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

    @Test
    fun exactUidNeedsNoConfirmation() {
        assertEquals(
            CloudRecoveryOwnerAuthorization.Authorized,
            cloudRecoveryOwnerAuthorization(
                CloudRecoveryOwnerVerdict.ExactUidMatch,
                CloudRecoveryOwnerConfirmation.None,
            ),
        )
    }

    @Test
    fun sameGoogleAcceptsOnlySameGoogleConfirmation() {
        assertEquals(
            CloudRecoveryOwnerAuthorization.Authorized,
            cloudRecoveryOwnerAuthorization(
                CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid,
                CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity,
            ),
        )
        assertEquals(
            CloudRecoveryOwnerAuthorization.ConfirmationRequired(
                CloudRecoveryOwnerConfirmationKind.SameGoogleIdentity,
            ),
            cloudRecoveryOwnerAuthorization(
                CloudRecoveryOwnerVerdict.SameGoogleIdentityNewFirebaseUid,
                CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope,
            ),
        )
    }

    @Test
    fun legacyAcceptsOnlyLegacyConfirmation() {
        assertEquals(
            CloudRecoveryOwnerAuthorization.Authorized,
            cloudRecoveryOwnerAuthorization(
                CloudRecoveryOwnerVerdict.LegacyEnvelope,
                CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope,
            ),
        )
        assertEquals(
            CloudRecoveryOwnerAuthorization.ConfirmationRequired(
                CloudRecoveryOwnerConfirmationKind.LegacyEnvelope,
            ),
            cloudRecoveryOwnerAuthorization(
                CloudRecoveryOwnerVerdict.LegacyEnvelope,
                CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity,
            ),
        )
    }

    @Test
    fun differentAccountCannotBeBypassedByEitherConfirmation() {
        listOf(
            CloudRecoveryOwnerConfirmation.None,
            CloudRecoveryOwnerConfirmation.ConfirmedSameGoogleIdentity,
            CloudRecoveryOwnerConfirmation.ConfirmedLegacyEnvelope,
        ).forEach { confirmation ->
            assertEquals(
                CloudRecoveryOwnerAuthorization.Blocked,
                cloudRecoveryOwnerAuthorization(
                    CloudRecoveryOwnerVerdict.DifferentAccount,
                    confirmation,
                ),
            )
        }
    }

    private fun assertVerdict(expected: CloudRecoveryOwnerVerdict, ownerUid: String, ownerHash: String?, currentUid: String, currentHash: String?) {
        assertEquals(expected, cloudRecoveryOwnerVerdict(ownerUid, ownerHash, currentUid, currentHash))
    }
}
