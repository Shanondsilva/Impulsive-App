package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaim
import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaimStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryRestoreOwnershipFinalizerTest {
    @Test
    fun exactOwnerRefreshesAndCleansWithoutLocalMigration() = runBlocking {
        val fixture = fixture(
            account = CloudRestoreOwnershipAccount(CurrentUid, Hash),
            owner = CloudFinalizerOwner(initialUid = CurrentUid),
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
        )

        assertEquals(CloudRestoreOwnershipFinalizationResult.Success, result)
        assertEquals(0, fixture.owner.writes)
        assertEquals(
            listOf(
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            fixture.events,
        )
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun deferredProvenanceCleanupStillClearsPendingClaimFirst() =
        runBlocking {
            val fixture = fixture(
                owner = CloudFinalizerOwner(initialUid = CurrentUid),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof =
                        VerifiedCloudRestoreOwnerProof
                            .ExactUid(CurrentUid),
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                result,
            )
            assertEquals(
                listOf("snapshot", "cloud", "clearClaim"),
                fixture.events,
            )
            assertNull(fixture.claims.claim)
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactUidRepairsStaleLocalOwnerAndSucceeds() = runBlocking {
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialUid = "different-owner"),
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult.Success,
            result,
        )
        assertEquals(1, fixture.owner.writes)
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertEquals(Hash, fixture.owner.hash.value)
        assertEquals(
            listOf(
                "adopt",
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            fixture.events,
        )
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun exactUidRepairsMissingOwnerAndStoresVerifiedGoogleHash() =
        runBlocking {
            val fixture = fixture(
                owner = CloudFinalizerOwner(
                    initialUid = null,
                    initialHash = null,
                ),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                result,
            )
            assertEquals(1, fixture.owner.writes)
            assertEquals(CurrentUid, fixture.owner.uid.value)
            assertEquals(Hash, fixture.owner.hash.value)
        }

    @Test
    fun exactUidSessionMismatchBeforeWriteLeavesOwnershipUnchanged() =
        runBlocking {
            val accountProvider =
                SequencedCloudFinalizerAccountProvider(
                    listOf(
                        CloudRestoreOwnershipAccount(CurrentUid, Hash),
                        CloudRestoreOwnershipAccount("changed-session", Hash),
                    ),
                )
            val fixture = fixture(
                accountProvider = accountProvider,
                owner = CloudFinalizerOwner(initialUid = "stale-owner"),
            )

            val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
            )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(2, accountProvider.calls)
            assertEquals(0, fixture.owner.writes)
            assertEquals("stale-owner", fixture.owner.uid.value)
            assertTrue(fixture.events.isEmpty())
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactUidSessionChangeAfterWriteLeavesFinalizationPending() =
        runBlocking {
            val accountProvider =
                SequencedCloudFinalizerAccountProvider(
                    listOf(
                        CloudRestoreOwnershipAccount(CurrentUid, Hash),
                        CloudRestoreOwnershipAccount(CurrentUid, Hash),
                        CloudRestoreOwnershipAccount("changed-session", Hash),
                    ),
                )
            val fixture = fixture(
                accountProvider = accountProvider,
                owner = CloudFinalizerOwner(initialUid = "stale-owner"),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(3, accountProvider.calls)
            assertEquals(1, fixture.owner.writes)
            assertEquals(CurrentUid, fixture.owner.uid.value)
            assertEquals(listOf("adopt"), fixture.events)
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactUidOwnershipWriteFailureLeavesFinalizationPending() =
        runBlocking {
            val failure = IllegalStateException("ownership write failed")
            val owner = CloudFinalizerOwner(
                initialUid = "stale-owner",
                writeFailure = failure,
            )
            val fixture = fixture(owner = owner)

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(0, owner.writes)
            assertEquals("stale-owner", owner.uid.value)
            assertEquals(listOf("adopt"), fixture.events)
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactUidReadbackMismatchLeavesFinalizationPending() =
        runBlocking {
            val owner = CloudFinalizerOwner(
                initialUid = "stale-owner",
                ignoreWrites = true,
            )
            val fixture = fixture(owner = owner)

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(1, owner.writes)
            assertEquals("stale-owner", owner.uid.value)
            assertEquals(listOf("adopt"), fixture.events)
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactUidOwnershipWriteCancellationIsRethrown() = runBlocking {
        val cancellation = CancellationException("ownership write cancelled")
        val fixture = fixture(
            owner = CloudFinalizerOwner(
                initialUid = "stale-owner",
                writeFailure = cancellation,
            ),
        )

        try {
            fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
            )
            throw AssertionError("Expected ownership write cancellation")
        } catch (actual: CancellationException) {
            assertEquals(cancellation, actual)
        }

        assertEquals(listOf("adopt"), fixture.events)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun exactOwnerIncompleteOnboardingRequiresSetupWithoutMutations() =
        runBlocking {
            val owner = CloudFinalizerOwner(
                completed = false,
                initialUid = CurrentUid,
            )
            val fixture = fixture(owner = owner)

            val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
            )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .SuccessRequiresOnboardingSetup,
                result,
            )
            assertEquals(0, owner.writes)
            assertTrue(fixture.events.isEmpty())
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun exactOwnerSnapshotFailureStillCleansMarkers() = runBlocking {
        val snapshot = CloudFinalizerScheduler(
            failure = IllegalStateException("snapshot failed"),
        )
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialUid = CurrentUid),
            snapshot = snapshot,
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            result,
        )
        assertEquals(
            listOf(
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            fixture.events,
        )
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun exactOwnerCloudFailureStillCleansMarkers() = runBlocking {
        val cloud = CloudFinalizerScheduler(
            failure = IllegalStateException("cloud failed"),
        )
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialUid = CurrentUid),
            cloud = cloud,
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            result,
        )
        assertEquals(
            listOf(
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            fixture.events,
        )
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun exactOwnerClaimCleanupFailureLeavesProvenancePending() = runBlocking {
        val claims = CloudFinalizerClaims(failClear = true)
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialUid = CurrentUid),
            claims = claims,
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(
            listOf("snapshot", "cloud", "clearClaim"),
            fixture.events,
        )
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun exactOwnerProvenanceCleanupFailureLeavesFinalizationPending() =
        runBlocking {
            val provenance = CloudFinalizerProvenance(failClear = true)
            val fixture = fixture(
                owner = CloudFinalizerOwner(initialUid = CurrentUid),
                provenance = provenance,
            )

            val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
            )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(
                listOf(
                    "snapshot",
                    "cloud",
                    "clearClaim",
                    "clearProvenance",
                ),
                fixture.events,
            )
            assertNull(fixture.claims.claim)
            assertTrue(fixture.provenance.pending)
        }

    @Test
    fun schedulerCancellationIsRethrownWithoutMarkerCleanup() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialUid = CurrentUid),
            snapshot = CloudFinalizerScheduler(failure = cancellation),
        )

        try {
            fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid),
            )
            throw AssertionError("Expected scheduler cancellation")
        } catch (actual: CancellationException) {
            assertEquals(cancellation, actual)
        }

        assertEquals(listOf("snapshot"), fixture.events)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun sameGoogleProofAdoptsOnlyMatchingStaleOwnership() = runBlocking {
        val matching = fixture()
        val mismatched = fixture(
            owner = CloudFinalizerOwner(initialUid = "different-owner"),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult.Success,
            matching.finalizer.finalizeAfterVerifiedCloudRestore(sameGoogleProof()),
        )
        assertEquals(CurrentUid, matching.owner.uid.value)
        assertEquals(Hash, matching.owner.hash.value)

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending,
            mismatched.finalizer.finalizeAfterVerifiedCloudRestore(sameGoogleProof()),
        )
        assertEquals(0, mismatched.owner.writes)
    }

    @Test
    fun alreadyAdoptedSameGoogleOwnerSucceedsWithoutProvenance() =
        runBlocking {
            val fixture = fixture(
                owner = CloudFinalizerOwner(initialUid = CurrentUid),
                provenance = CloudFinalizerProvenance(pending = false),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof = sameGoogleProof(),
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                result,
            )
            assertEquals(0, fixture.owner.writes)
            assertEquals(
                listOf("snapshot", "cloud", "clearClaim"),
                fixture.events,
            )
            assertFalse(fixture.provenance.pending)
        }

    @Test
    fun staleSameGoogleOwnerWithoutProvenanceRemainsBlocked() =
        runBlocking {
            val fixture = fixture(
                provenance = CloudFinalizerProvenance(pending = false),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof = sameGoogleProof(),
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(0, fixture.owner.writes)
            assertTrue(fixture.events.isEmpty())
        }

    @Test
    fun alreadyAdoptedLegacyOwnerSucceedsWithoutProvenance() =
        runBlocking {
            val fixture = fixture(
                owner = CloudFinalizerOwner(initialUid = CurrentUid),
                provenance = CloudFinalizerProvenance(pending = false),
            )
            val proof = VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                previousUid = PreviousUid,
                currentUid = CurrentUid,
                currentGoogleSubjectHash = Hash,
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof = proof,
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                result,
            )
            assertEquals(0, fixture.owner.writes)
            assertEquals(
                listOf("snapshot", "cloud", "clearClaim"),
                fixture.events,
            )
            assertFalse(fixture.provenance.pending)
        }

    @Test
    fun staleLegacyOwnerWithoutProvenanceRemainsBlocked() =
        runBlocking {
            val fixture = fixture(
                owner = CloudFinalizerOwner(initialHash = null),
                provenance = CloudFinalizerProvenance(pending = false),
            )
            val proof = VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                previousUid = PreviousUid,
                currentUid = CurrentUid,
                currentGoogleSubjectHash = Hash,
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof = proof,
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(0, fixture.owner.writes)
            assertTrue(fixture.events.isEmpty())
        }

    @Test
    fun sessionMismatchCannotUseAlreadyAdoptedRetryWithoutProvenance() =
        runBlocking {
            val fixture = fixture(
                account =
                    CloudRestoreOwnershipAccount(
                        uid = "different-session",
                        googleSubjectHash = Hash,
                    ),
                owner = CloudFinalizerOwner(initialUid = CurrentUid),
                provenance = CloudFinalizerProvenance(pending = false),
            )

            val result =
                fixture.finalizer.finalizeAfterVerifiedCloudRestore(
                    proof = sameGoogleProof(),
                    deferProvenanceCleanup = true,
                )

            assertEquals(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                result,
            )
            assertEquals(0, fixture.owner.writes)
            assertTrue(fixture.events.isEmpty())
        }

    @Test
    fun legacyProofAdoptsOnlyThroughLegacyProofPath() = runBlocking {
        val fixture = fixture(owner = CloudFinalizerOwner(initialHash = null))

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                previousUid = PreviousUid,
                currentUid = CurrentUid,
                currentGoogleSubjectHash = Hash,
            ),
        )

        assertEquals(CloudRestoreOwnershipFinalizationResult.Success, result)
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertEquals(Hash, fixture.owner.hash.value)
    }

    @Test
    fun sessionChangeBlocksOwnershipAdoptionAfterImport() = runBlocking {
        val fixture = fixture(
            account = CloudRestoreOwnershipAccount("changed-session", Hash),
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            sameGoogleProof(),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(0, fixture.owner.writes)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun incompleteOnboardingPreservesImportedDataAndRequiresSetup() = runBlocking {
        val owner = CloudFinalizerOwner(completed = false, initialUid = null)
        val fixture = fixture(owner = owner)

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            sameGoogleProof(),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup,
            result,
        )
        assertEquals(0, owner.writes)
        assertTrue(fixture.provenance.pending)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun markerClearingOrderIsClaimThenProvenanceLast() = runBlocking {
        val fixture = fixture()

        fixture.finalizer.finalizeAfterVerifiedCloudRestore(sameGoogleProof())

        assertEquals(
            listOf(
                "writeClaim",
                "adopt",
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            fixture.events,
        )
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun sameGoogleSchedulerFailureStillCleansMarkers() = runBlocking {
        val snapshot = CloudFinalizerScheduler(
            failure = IllegalStateException("snapshot failed"),
        )
        val fixture = fixture(snapshot = snapshot)

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            sameGoogleProof(),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            result,
        )
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
        assertEquals("clearProvenance", fixture.events.last())
    }

    @Test
    fun legacySchedulerFailureStillCleansMarkers() = runBlocking {
        val cloud = CloudFinalizerScheduler(
            failure = IllegalStateException("cloud failed"),
        )
        val fixture = fixture(
            owner = CloudFinalizerOwner(initialHash = null),
            cloud = cloud,
        )

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
                previousUid = PreviousUid,
                currentUid = CurrentUid,
                currentGoogleSubjectHash = Hash,
            ),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            result,
        )
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
        assertEquals("clearProvenance", fixture.events.last())
    }

    @Test
    fun claimCleanupFailureDoesNotClearProvenance() = runBlocking {
        val claims = CloudFinalizerClaims(failClear = true)
        val fixture = fixture(claims = claims)

        val result = fixture.finalizer.finalizeAfterVerifiedCloudRestore(
            sameGoogleProof(),
        )

        assertEquals(
            CloudRestoreOwnershipFinalizationResult
                .RestoredButOwnershipFinalizationPending,
            result,
        )
        assertTrue(fixture.provenance.pending)
        assertFalse(fixture.events.contains("clearProvenance"))
    }

    @Test
    fun postImportCompletionResultsRemainTruthful() {
        assertEquals(
            CloudRecoveryRestoreResult.SuccessRequiresOnboardingSetup,
            combineCloudRestoreCompletion(
                CloudRestoreOwnershipFinalizationResult
                    .SuccessRequiresOnboardingSetup,
                CloudRecoveryRestoreResult.Success,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending,
            combineCloudRestoreCompletion(
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending,
                CloudRecoveryRestoreResult.Success,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult.SuccessBackupRefreshPending,
            combineCloudRestoreCompletion(
                CloudRestoreOwnershipFinalizationResult
                    .SuccessBackupRefreshPending,
                CloudRecoveryRestoreResult.Success,
            ),
        )
    }

    private fun fixture(
        account: CloudRestoreOwnershipAccount? =
            CloudRestoreOwnershipAccount(CurrentUid, Hash),
        accountProvider: CloudRestoreOwnershipAccountProvider =
            CloudRestoreOwnershipAccountProvider { account },
        owner: CloudFinalizerOwner = CloudFinalizerOwner(),
        provenance: CloudFinalizerProvenance = CloudFinalizerProvenance(),
        claims: CloudFinalizerClaims = CloudFinalizerClaims(),
        snapshot: CloudFinalizerScheduler = CloudFinalizerScheduler(),
        cloud: CloudFinalizerScheduler = CloudFinalizerScheduler(),
    ): Fixture {
        val events = mutableListOf<String>()
        owner.events = events
        provenance.events = events
        claims.events = events
        snapshot.events = events
        snapshot.name = "snapshot"
        cloud.events = events
        cloud.name = "cloud"
        return Fixture(
            finalizer = CloudRestoreOwnershipFinalizer(
                accountProvider = accountProvider,
                ownerState = owner,
                provenance = provenance,
                pendingClaims = claims,
                snapshotScheduler = snapshot,
                cloudScheduler = cloud,
            ),
            owner = owner,
            provenance = provenance,
            claims = claims,
            events = events,
        )
    }

    private data class Fixture(
        val finalizer: CloudRestoreOwnershipFinalizer,
        val owner: CloudFinalizerOwner,
        val provenance: CloudFinalizerProvenance,
        val claims: CloudFinalizerClaims,
        val events: MutableList<String>,
    )

    private fun sameGoogleProof() =
        VerifiedCloudRestoreOwnerProof.SameGoogleIdentity(
            previousUid = PreviousUid,
            previousGoogleSubjectHash = Hash,
            currentUid = CurrentUid,
            currentGoogleSubjectHash = Hash,
        )

    private companion object {
        const val PreviousUid = "previous-user"
        const val CurrentUid = "current-user"
        const val Hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}

private class CloudFinalizerOwner(
    completed: Boolean = true,
    initialUid: String? = "previous-user",
    initialHash: String? =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    private val writeFailure: Throwable? = null,
    private val ignoreWrites: Boolean = false,
) : CloudRestoreOwnershipState {
    val completed = MutableStateFlow(completed)
    val uid = MutableStateFlow(initialUid)
    val hash = MutableStateFlow(initialHash)
    var writes = 0
    var events: MutableList<String>? = null

    override val isCompleted: Flow<Boolean> = this.completed
    override val completedAccountUid: Flow<String?> = uid
    override val completedGoogleSubjectHash: Flow<String?> = hash

    override suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String?,
    ) {
        events?.add("adopt")
        writeFailure?.let { throw it }
        writes += 1
        if (ignoreWrites) return
        completed.value = isCompleted
        uid.value = accountUid
        hash.value = googleSubjectHash
    }
}

private class CloudFinalizerProvenance(
    var pending: Boolean = true,
    var failClear: Boolean = false,
) : CloudRestoreOwnershipProvenance {
    var events: MutableList<String>? = null

    override fun isRestorePending(): Boolean = pending
    override fun clearRestorePending() {
        events?.add("clearProvenance")
        if (failClear) throw IllegalStateException("provenance clear failed")
        pending = false
    }
}

private class CloudFinalizerClaims(
    var failClear: Boolean = false,
) : PendingRestoredOwnershipClaimStore {
    var claim: PendingRestoredOwnershipClaim? = null
    var events: MutableList<String>? = null

    override fun read(): PendingRestoredOwnershipClaim? = claim
    override fun write(claim: PendingRestoredOwnershipClaim) {
        events?.add("writeClaim")
        this.claim = claim
    }

    override fun clear() {
        events?.add("clearClaim")
        if (failClear) throw IllegalStateException("clear failed")
        claim = null
    }
}

private class CloudFinalizerScheduler(
    var failure: Throwable? = null,
) : CloudRestoreOwnershipScheduler {
    var events: MutableList<String>? = null
    var name: String = "scheduler"

    override fun request() {
        events?.add(name)
        failure?.let { throw it }
    }
}

private class SequencedCloudFinalizerAccountProvider(
    private val accounts: List<CloudRestoreOwnershipAccount?>,
) : CloudRestoreOwnershipAccountProvider {
    var calls: Int = 0
        private set

    override fun currentAccount(): CloudRestoreOwnershipAccount? {
        val account = accounts[calls.coerceAtMost(accounts.lastIndex)]
        calls += 1
        return account
    }
}
