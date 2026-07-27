package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRestorePostImportRecoveryCoordinatorTest {
    @Test
    fun noJournalOrReceiptReturnsNothingPending() = runBlocking {
        val fixture = fixture(receipt = null)

        assertEquals(
            CloudRestorePostImportRecoveryResult.NothingPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun authorizationWithoutReceiptIsCleared() = runBlocking {
        val fixture = fixture(
            receipt = null,
            authorization = authorization(),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult
                .AuthorizationWithoutCommittedImportCleared,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.authorizations.authorization)
        assertEquals(listOf("authorization"), fixture.events)
    }

    @Test
    fun exactUidReceiptResumes() = runBlocking {
        val fixture = fixture()

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertTrue(
            fixture.finalizer.proofs.single() is
                VerifiedCloudRestoreOwnerProof.ExactUid,
        )
    }

    @Test
    fun sameGoogleReceiptResumes() = runBlocking {
        val fixture = fixture(
            receipt = sameGoogleReceipt(),
            account =
                CloudRestoreOwnershipAccount(
                    uid = CurrentUid,
                    googleSubjectHash = Hash,
                    hasGoogleProvider = true,
                ),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertTrue(
            fixture.finalizer.proofs.single() is
                VerifiedCloudRestoreOwnerProof.SameGoogleIdentity,
        )
    }

    @Test
    fun legacyReceiptResumes() = runBlocking {
        val fixture = fixture(
            receipt = legacyReceipt(),
            account =
                CloudRestoreOwnershipAccount(
                    uid = CurrentUid,
                    googleSubjectHash = null,
                    hasGoogleProvider = false,
                ),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertTrue(
            fixture.finalizer.proofs.single() is
                VerifiedCloudRestoreOwnerProof.LegacyEnvelope,
        )
    }

    @Test
    fun differentFirebaseUidIsBlockedAndReceiptIsRetained() = runBlocking {
        val fixture = fixture(
            account =
                CloudRestoreOwnershipAccount(
                    uid = "different-user",
                    googleSubjectHash = Hash,
                ),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.RequiresCorrectAccount,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.finalizer.proofs.isEmpty())
    }

    @Test
    fun differentGoogleSubjectIsBlockedAndReceiptIsRetained() = runBlocking {
        val fixture = fixture(
            receipt = sameGoogleReceipt(),
            account =
                CloudRestoreOwnershipAccount(
                    uid = CurrentUid,
                    googleSubjectHash = OtherHash,
                    hasGoogleProvider = true,
                ),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.RequiresCorrectAccount,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.finalizer.proofs.isEmpty())
    }

    @Test
    fun alreadyAdoptedOwnerCompletesInterruptedCleanup() = runBlocking {
        val fixture = fixture()

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.receipts.receipt)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun finalizerFailurePreservesReceipt() = runBlocking {
        val failure = IllegalStateException("finalizer failed")
        val fixture = fixture(finalizerFailure = failure)

        val result = fixture.coordinator.resumeIfNeeded()

        assertTrue(
            result is CloudRestorePostImportRecoveryResult.Failed &&
                result.cause == failure,
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun schedulerFailureReturnsRefreshPendingAfterCleanup() = runBlocking {
        val fixture = fixture(
            finalization =
                CloudRestoreOwnershipFinalizationResult
                    .SuccessBackupRefreshPending,
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizedRefreshPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.receipts.receipt)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun missingCloudKeyRequiresCloudRecoverySetup() = runBlocking {
        val fixture = fixture(hasCredentials = false)

        assertEquals(
            CloudRestorePostImportRecoveryResult
                .RequiresCloudRecoverySetup,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.receipts.receipt)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun cleanupOrderIsAuthorizationThenProvenanceThenReceipt() = runBlocking {
        val fixture = fixture(authorization = authorization())

        fixture.coordinator.resumeIfNeeded()

        assertEquals(
            listOf("authorization", "provenance", "receipt"),
            fixture.events.takeLast(3),
        )
    }

    @Test
    fun receiptDeletionIsTheFinalDurableCleanupOperation() = runBlocking {
        val fixture = fixture(authorization = authorization())

        fixture.coordinator.resumeIfNeeded()

        assertEquals("receipt", fixture.events.last())
    }

    @Test
    fun authorizationCleanupFailurePreservesReceipt() = runBlocking {
        val fixture = fixture(
            authorization = authorization(),
            authorizationClearFails = true,
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.provenance.pending)
        assertEquals(listOf("authorization"), fixture.events.takeLast(1))
    }

    @Test
    fun receiptDeletionFailurePreservesReceiptAfterProvenanceCleanup() = runBlocking {
        val fixture = fixture(receiptDeleteFails = true)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertFalse(fixture.provenance.pending)
        assertEquals("receiptFind", fixture.events.last())
    }

    @Test
    fun provenanceCleanupFailureDoesNotReportCleanSuccess() = runBlocking {
        val fixture = fixture(provenanceClearFails = true)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.provenance.pending)
        assertEquals("provenance", fixture.events.last())
    }

    @Test
    fun provenanceFailureFollowedByRetryCompletesCleanup() = runBlocking {
        val fixture = fixture(provenanceClearFails = true)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        fixture.provenance.clearFails = false

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.receipts.receipt)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun receiptDeletionFailureFollowedByRetryCompletesCleanup() = runBlocking {
        val importCalls = 0
        val fixture = fixture(receiptDeleteFails = true)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertFalse(fixture.provenance.pending)
        fixture.receipts.deleteFails = false

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertNull(fixture.receipts.receipt)
        assertEquals(0, importCalls)
        assertEquals(2, fixture.finalizer.proofs.size)
    }

    @Test
    fun provenanceAlreadyAbsentWithReceiptPresentStillFinalizes() =
        runBlocking {
            val fixture = fixture(provenancePending = false)

            assertEquals(
                CloudRestorePostImportRecoveryResult.Finalized,
                fixture.coordinator.resumeIfNeeded(),
            )
            assertNull(fixture.receipts.receipt)
            assertFalse(fixture.provenance.pending)
            assertTrue(
                fixture.finalizer.proofs.single() is
                    VerifiedCloudRestoreOwnerProof.ExactUid,
            )
        }

    @Test
    fun zeroDeleteCountWithAbsentReceiptIsAlreadyClean() = runBlocking {
        val fixture = fixture(receipt = null)

        assertTrue(
            fixture.coordinator.cleanupCommittedReceipt(ReceiptId),
        )
        assertEquals(
            listOf("authorization", "provenance", "receipt", "receiptFind"),
            fixture.events,
        )
    }

    @Test
    fun zeroDeleteCountWithExistingReceiptRemainsPending() = runBlocking {
        val fixture = fixture(receiptDeleteFails = true)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
    }

    @Test
    fun cleanupCancellationAtEveryStageIsRethrown() = runBlocking {
        val cancellation = CancellationException("cleanup cancelled")
        val fixtures = listOf(
            fixture(authorizationClearFailure = cancellation),
            fixture(provenanceClearFailure = cancellation),
            fixture(receiptDeleteFailure = cancellation),
        )

        fixtures.forEach { fixture ->
            try {
                fixture.coordinator.resumeIfNeeded()
                throw AssertionError("Expected cleanup cancellation")
            } catch (actual: CancellationException) {
                assertEquals(cancellation, actual)
            }
            assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        }
    }

    @Test
    fun concurrentResumeAttemptIsBlocked() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(
            beforeFinalization = {
                entered.complete(Unit)
                release.await()
            },
        )

        val first = async {
            fixture.coordinator.resumeIfNeeded()
        }
        entered.await()
        val second = fixture.coordinator.resumeIfNeeded()
        release.complete(Unit)

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            second,
        )
        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            first.await(),
        )
    }

    @Test
    fun restartAfterRoomCommitFinalizesWithoutAnotherImport() = runBlocking {
        val importCalls = 0
        val fixture = fixture(authorization = null)

        val result = fixture.coordinator.resumeIfNeeded()

        assertEquals(
            CloudRestorePostImportRecoveryResult.Finalized,
            result,
        )
        assertEquals(0, importCalls)
        assertEquals(1, fixture.finalizer.proofs.size)
    }

    @Test
    fun disagreeingAuthorizationAndReceiptRemainPending() = runBlocking {
        val fixture = fixture(
            authorization =
                authorization(payloadSha256 = OtherPayloadHash),
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.FinalizationPending,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.finalizer.proofs.isEmpty())
    }

    @Test
    fun onboardingRequiredRetainsReceiptForLaterFinalization() = runBlocking {
        val fixture = fixture(
            finalization =
                CloudRestoreOwnershipFinalizationResult
                    .SuccessRequiresOnboardingSetup,
        )

        assertEquals(
            CloudRestorePostImportRecoveryResult.RequiresOnboardingSetup,
            fixture.coordinator.resumeIfNeeded(),
        )
        assertEquals(ReceiptId, fixture.receipts.receipt?.receiptId)
        assertTrue(fixture.provenance.pending)
    }

    private fun fixture(
        receipt: CloudRestoreReceiptEntity? = exactReceipt(),
        authorization: PendingCloudRestoreAuthorization? = null,
        account: CloudRestoreOwnershipAccount? =
            CloudRestoreOwnershipAccount(CurrentUid, Hash),
        finalization: CloudRestoreOwnershipFinalizationResult =
            CloudRestoreOwnershipFinalizationResult.Success,
        finalizerFailure: Throwable? = null,
        hasCredentials: Boolean = true,
        beforeFinalization: suspend () -> Unit = {},
        authorizationClearFails: Boolean = false,
        receiptDeleteFails: Boolean = false,
        provenanceClearFails: Boolean = false,
        authorizationClearFailure: Throwable? = null,
        receiptDeleteFailure: Throwable? = null,
        provenanceClearFailure: Throwable? = null,
        provenancePending: Boolean = true,
    ): Fixture {
        val events = mutableListOf<String>()
        val receiptStore =
            FakeCloudRestoreReceiptStore(
                receipt = receipt,
                events = events,
                deleteFails = receiptDeleteFails,
                deleteFailure = receiptDeleteFailure,
            )
        val authorizationStore =
            FakePendingCloudRestoreAuthorizationStore(
                authorization,
                events,
                clearFails = authorizationClearFails,
                clearFailure = authorizationClearFailure,
            )
        val fakeFinalizer =
            FakeCloudRestorePostImportFinalizer(
                result = finalization,
                failure = finalizerFailure,
                events = events,
                beforeFinalization = beforeFinalization,
            )
        val provenance =
            FakeCloudRestorePostImportProvenance(
                events = events,
                clearFails = provenanceClearFails,
                clearFailure = provenanceClearFailure,
                pending = provenancePending,
            )
        return Fixture(
            coordinator =
                CloudRestorePostImportRecoveryCoordinator(
                    receipts = receiptStore,
                    authorizations = authorizationStore,
                    accountProvider =
                        CloudRestoreOwnershipAccountProvider { account },
                    finalizer = fakeFinalizer,
                    credentials =
                        CloudRestoreActivatedCredentialsProvider {
                            events += "credentials"
                            hasCredentials
                        },
                    provenance = provenance,
                ),
            receipts = receiptStore,
            authorizations = authorizationStore,
            finalizer = fakeFinalizer,
            provenance = provenance,
            events = events,
        )
    }

    private data class Fixture(
        val coordinator: CloudRestorePostImportRecoveryCoordinator,
        val receipts: FakeCloudRestoreReceiptStore,
        val authorizations:
            FakePendingCloudRestoreAuthorizationStore,
        val finalizer: FakeCloudRestorePostImportFinalizer,
        val provenance: FakeCloudRestorePostImportProvenance,
        val events: MutableList<String>,
    )

    private fun exactReceipt() =
        CloudRestoreReceiptEntity(
            receiptId = ReceiptId,
            payloadSha256 = PayloadHash,
            proofType = CloudRestoreProofType.ExactUid.persistedValue,
            previousUid = null,
            previousGoogleSubjectHash = null,
            currentUid = CurrentUid,
            currentGoogleSubjectHash = Hash,
            importedAtMillis = 456L,
        )

    private fun sameGoogleReceipt() =
        CloudRestoreReceiptEntity(
            receiptId = ReceiptId,
            payloadSha256 = PayloadHash,
            proofType =
                CloudRestoreProofType.SameGoogleIdentity.persistedValue,
            previousUid = PreviousUid,
            previousGoogleSubjectHash = Hash,
            currentUid = CurrentUid,
            currentGoogleSubjectHash = Hash,
            importedAtMillis = 456L,
        )

    private fun legacyReceipt() =
        CloudRestoreReceiptEntity(
            receiptId = ReceiptId,
            payloadSha256 = PayloadHash,
            proofType =
                CloudRestoreProofType.LegacyEnvelope.persistedValue,
            previousUid = PreviousUid,
            previousGoogleSubjectHash = null,
            currentUid = CurrentUid,
            currentGoogleSubjectHash = null,
            importedAtMillis = 456L,
        )

    private fun authorization(
        payloadSha256: String = PayloadHash,
    ) = PendingCloudRestoreAuthorization(
        receiptId = ReceiptId,
        payloadSha256 = payloadSha256,
        proofType = CloudRestoreProofType.ExactUid,
        previousUid = null,
        previousGoogleSubjectHash = null,
        currentUid = CurrentUid,
        currentGoogleSubjectHash = Hash,
        authorisedAtMillis = 123L,
    )

    private companion object {
        const val ReceiptId =
            "123e4567-e89b-12d3-a456-426614174000"
        const val PreviousUid = "previous-user"
        const val CurrentUid = "current-user"
        const val PayloadHash =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val OtherPayloadHash =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        const val Hash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}

private class FakeCloudRestoreReceiptStore(
    var receipt: CloudRestoreReceiptEntity?,
    private val events: MutableList<String>,
    var deleteFails: Boolean,
    var deleteFailure: Throwable?,
) : CloudRestoreReceiptStore {
    override suspend fun latest(): CloudRestoreReceiptEntity? = receipt

    override suspend fun find(
        receiptId: String,
    ): CloudRestoreReceiptEntity? {
        events += "receiptFind"
        return receipt?.takeIf { it.receiptId == receiptId }
    }

    override suspend fun delete(receiptId: String): Int {
        events += "receipt"
        deleteFailure?.let { throw it }
        if (deleteFails) return 0
        if (receipt?.receiptId != receiptId) return 0
        receipt = null
        return 1
    }
}

private class FakePendingCloudRestoreAuthorizationStore(
    var authorization: PendingCloudRestoreAuthorization?,
    private val events: MutableList<String>,
    var clearFails: Boolean,
    var clearFailure: Throwable?,
) : PendingCloudRestoreAuthorizationStore {
    override fun read(): PendingCloudRestoreAuthorization? = authorization

    override fun write(
        authorization: PendingCloudRestoreAuthorization,
    ) {
        this.authorization = authorization
    }

    override fun clear() {
        events += "authorization"
        clearFailure?.let { throw it }
        if (clearFails) {
            throw IllegalStateException("authorization clear failed")
        }
        authorization = null
    }
}

private class FakeCloudRestorePostImportFinalizer(
    private val result: CloudRestoreOwnershipFinalizationResult,
    private val failure: Throwable?,
    private val events: MutableList<String>,
    private val beforeFinalization: suspend () -> Unit,
) : CloudRestorePostImportFinalizer {
    val proofs = mutableListOf<VerifiedCloudRestoreOwnerProof>()

    override suspend fun finalize(
        proof: VerifiedCloudRestoreOwnerProof,
    ): CloudRestoreOwnershipFinalizationResult {
        proofs += proof
        beforeFinalization()
        failure?.let { throw it }
        events += "pendingClaim"
        return result
    }
}

private class FakeCloudRestorePostImportProvenance(
    private val events: MutableList<String>,
    var clearFails: Boolean,
    var clearFailure: Throwable?,
    var pending: Boolean,
) : CloudRestorePostImportProvenance {
    override fun clearRestorePending() {
        events += "provenance"
        clearFailure?.let { throw it }
        if (clearFails) {
            throw IllegalStateException("provenance clear failed")
        }
        pending = false
    }
}
