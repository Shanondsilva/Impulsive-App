package com.impulsive.app.backend.data.restore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoredAccountMigrationCoordinatorTest {
    @Test
    fun signedOutAccountPerformsNoMutation() = runBlocking {
        val fixture = fixture(account = null)

        assertEquals(
            RestoredAccountMigrationResult.NotAuthenticated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
        assertEquals(0, fixture.owner.writes)
    }

    @Test
    fun anonymousAccountIsNotApplicable() = runBlocking {
        val fixture = fixture(account = account(isAnonymous = true))

        assertEquals(
            RestoredAccountMigrationResult.GuestNotApplicable,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
    }

    @Test
    fun restoreProvenanceAbsentDoesNotImport() = runBlocking {
        val fixture = fixture(provenance = FakeMigrationProvenance(pending = false))

        assertEquals(
            RestoredAccountMigrationResult.RestoreNotPending,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
    }

    @Test
    fun sameUidIsNotAMigration() = runBlocking {
        val fixture = fixture(owner = FakeMigrationOwner(initialUid = CurrentUid))

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
    }

    @Test
    fun missingGoogleProviderDoesNotImport() = runBlocking {
        val fixture = fixture(account = account(hasGoogleProvider = false))

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
    }

    @Test
    fun invalidSavedOrCurrentHashDoesNotImport() = runBlocking {
        val invalidSaved = fixture(owner = FakeMigrationOwner(initialHash = "invalid"))
        val invalidCurrent = fixture(account = account(googleSubjectHash = "invalid"))

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            invalidSaved.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            invalidCurrent.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, invalidSaved.importer.calls)
        assertEquals(0, invalidCurrent.importer.calls)
    }

    @Test
    fun differentValidHashesDoNotImport() = runBlocking {
        val fixture = fixture(account = account(googleSubjectHash = OtherHash))

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.owner.writes)
    }

    @Test
    fun claimIsPersistedBeforeImportAndImportPrecedesAdoption() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = fixture(events = events)

        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(
            listOf(
                "claim",
                "import",
                "adopt",
                "snapshot",
                "cloud",
                "clearClaim",
                "clearProvenance",
            ),
            events,
        )
    }

    @Test
    fun matchingClaimWithDifferentTimestampIsReused() = runBlocking {
        val persisted = claim(createdAtMillis = 7L)
        val claims = FakeMigrationClaims(initial = persisted)
        val importer = FakeMigrationImporter(AutoRestoreResult.Failed(TestFailure))
        val fixture = fixture(claims = claims, importer = importer)

        assertTrue(
            fixture.coordinator.confirmSameGoogleIdentityAndRestore() is
                RestoredAccountMigrationResult.Failed,
        )
        assertSame(persisted, claims.claim)
        assertEquals(0, claims.writes)
    }

    @Test
    fun mismatchedClaimIsClearedWithoutImportOrAdoption() = runBlocking {
        val claims = FakeMigrationClaims(
            initial = claim(currentUid = "different-current"),
        )
        val fixture = fixture(claims = claims)

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertNull(claims.claim)
        assertEquals(0, fixture.importer.calls)
        assertEquals(0, fixture.owner.writes)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun interruptionAfterClaimWriteResumes() = runBlocking {
        val importer = FakeMigrationImporter(
            results = ArrayDeque(
                listOf(
                    AutoRestoreResult.Failed(TestFailure),
                    AutoRestoreResult.Restored,
                ),
            ),
        )
        val fixture = fixture(importer = importer)

        assertTrue(
            fixture.coordinator.confirmSameGoogleIdentityAndRestore() is
                RestoredAccountMigrationResult.Failed,
        )
        assertNotNull(fixture.claims.claim)
        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(2, importer.calls)
    }

    @Test
    fun interruptionAfterImportAndBundleDeletionResumesWithNoBundle() = runBlocking {
        val owner = FakeMigrationOwner(failWrites = 1)
        val importer = FakeMigrationImporter(
            results = ArrayDeque(
                listOf(AutoRestoreResult.Restored, AutoRestoreResult.NoBundle),
            ),
        )
        val fixture = fixture(owner = owner, importer = importer)

        assertTrue(
            fixture.coordinator.confirmSameGoogleIdentityAndRestore() is
                RestoredAccountMigrationResult.Failed,
        )
        assertEquals(PreviousUid, owner.uid.value)
        assertNotNull(fixture.claims.claim)

        assertEquals(
            RestoredAccountMigrationResult.ClaimedWithoutAutomaticBundle,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(CurrentUid, owner.uid.value)
    }

    @Test
    fun interruptionAfterOwnershipAdoptionCompletesCleanupWithoutReimport() = runBlocking {
        val claims = FakeMigrationClaims(initial = claim(), failClears = 1)
        val owner = FakeMigrationOwner(initialUid = CurrentUid)
        val fixture = fixture(owner = owner, claims = claims)

        assertEquals(
            RestoredAccountMigrationResult.MigratedRefreshPending,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertNotNull(claims.claim)
        assertTrue(fixture.provenance.pending)

        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.importer.calls)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun successfulImportAdoptsCurrentUidAndHash() = runBlocking {
        val fixture = fixture()

        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertEquals(Hash, fixture.owner.hash.value)
    }

    @Test
    fun noBundleRetryAdoptsCurrentOwner() = runBlocking {
        val claims = FakeMigrationClaims(initial = claim())
        val fixture = fixture(
            claims = claims,
            importer = FakeMigrationImporter(AutoRestoreResult.NoBundle),
        )

        assertEquals(
            RestoredAccountMigrationResult.ClaimedWithoutAutomaticBundle,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(CurrentUid, fixture.owner.uid.value)
    }

    @Test
    fun legacyMismatchAndInvalidBundlesNeverAdopt() = runBlocking {
        listOf(
            AutoRestoreResult.LegacyUnownedBundle,
            AutoRestoreResult.LegacyOwnerVerificationRequired,
            AutoRestoreResult.OwnerMismatch,
            AutoRestoreResult.InvalidBundle,
        ).forEach { importResult ->
            val fixture = fixture(importer = FakeMigrationImporter(importResult))

            fixture.coordinator.confirmSameGoogleIdentityAndRestore()

            assertEquals("result=$importResult", 0, fixture.owner.writes)
            assertTrue("result=$importResult", fixture.provenance.pending)
        }
    }

    @Test
    fun importerFailurePreservesClaimAndProvenance() = runBlocking {
        val fixture = fixture(
            importer = FakeMigrationImporter(AutoRestoreResult.Failed(TestFailure)),
        )

        val result = fixture.coordinator.confirmSameGoogleIdentityAndRestore()

        assertEquals(RestoredAccountMigrationResult.Failed(TestFailure), result)
        assertNotNull(fixture.claims.claim)
        assertTrue(fixture.provenance.pending)
        assertEquals(0, fixture.owner.writes)
    }

    @Test
    fun existingLocalDataDoesNotAdopt() = runBlocking {
        val fixture = fixture(
            importer = FakeMigrationImporter(AutoRestoreResult.ExistingDataPresent),
        )

        assertEquals(
            RestoredAccountMigrationResult.ExistingLocalData,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.owner.writes)
        assertNotNull(fixture.claims.claim)
    }

    @Test
    fun firebaseSessionChangeAfterImportBlocksAdoption() = runBlocking {
        val accountState = MutableStateFlow<RestoredAccountMigrationAccount?>(account())
        val importer = FakeMigrationImporter(AutoRestoreResult.Restored) {
            accountState.value = account(uid = "changed-session")
        }
        val fixture = fixture(accountState = accountState, importer = importer)

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, fixture.owner.writes)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun localOwnerChangeAfterImportBlocksAdoption() = runBlocking {
        val owner = FakeMigrationOwner()
        val importer = FakeMigrationImporter(AutoRestoreResult.Restored) {
            owner.uid.value = "other-local-owner"
        }
        val fixture = fixture(owner = owner, importer = importer)

        assertEquals(
            RestoredAccountMigrationResult.OwnershipChanged,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(0, owner.writes)
    }

    @Test
    fun ownershipWriteFailurePreservesClaimAndProvenance() = runBlocking {
        val fixture = fixture(owner = FakeMigrationOwner(failWrites = 1))

        assertTrue(
            fixture.coordinator.confirmSameGoogleIdentityAndRestore() is
                RestoredAccountMigrationResult.Failed,
        )
        assertNotNull(fixture.claims.claim)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun snapshotAndCloudRefreshOccurAfterAdoptionAndOnlyWhenEnabled() = runBlocking {
        val enabledEvents = mutableListOf<String>()
        val enabled = fixture(events = enabledEvents)
        val disabledEvents = mutableListOf<String>()
        val disabled = fixture(events = disabledEvents, cloudEnabled = false)

        enabled.coordinator.confirmSameGoogleIdentityAndRestore()
        disabled.coordinator.confirmSameGoogleIdentityAndRestore()

        assertTrue(enabledEvents.indexOf("snapshot") > enabledEvents.indexOf("adopt"))
        assertTrue(enabledEvents.indexOf("cloud") > enabledEvents.indexOf("adopt"))
        assertFalse(disabledEvents.contains("cloud"))
    }

    @Test
    fun schedulerFailureDoesNotRollBackOwnershipAndPreservesRetryMarkers() = runBlocking {
        val snapshot = FakeMigrationScheduler(failRequests = 1)
        val fixture = fixture(snapshot = snapshot)

        assertEquals(
            RestoredAccountMigrationResult.MigratedRefreshPending,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertEquals(CurrentUid, fixture.owner.uid.value)
        assertNotNull(fixture.claims.claim)
        assertTrue(fixture.provenance.pending)

        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
    }

    @Test
    fun pendingClaimClearsBeforeProvenanceAndProvenanceIsFinalMutation() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = fixture(events = events)

        fixture.coordinator.confirmSameGoogleIdentityAndRestore()

        assertTrue(events.indexOf("clearClaim") < events.indexOf("clearProvenance"))
        assertEquals("clearProvenance", events.last())
    }

    @Test
    fun staleClaimDeletionFailureReturnsFailedWithoutImport() = runBlocking {
        val claims = FakeMigrationClaims(
            initial = claim(currentUid = "stale-current"),
            failClears = 1,
        )
        val fixture = fixture(claims = claims)

        assertTrue(
            fixture.coordinator.confirmSameGoogleIdentityAndRestore() is
                RestoredAccountMigrationResult.Failed,
        )
        assertEquals(0, fixture.importer.calls)
        assertEquals(0, fixture.owner.writes)
        assertTrue(fixture.provenance.pending)
    }

    @Test
    fun fullySuccessfulCleanupReturnsFullSuccess() = runBlocking {
        val fixture = fixture()

        assertEquals(
            RestoredAccountMigrationResult.Migrated,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )
        assertNull(fixture.claims.claim)
        assertFalse(fixture.provenance.pending)
    }

    @Test
    fun concurrentInvocationReturnsAlreadyRunning() = runBlocking {
        val enteredImporter = CompletableDeferred<Unit>()
        val releaseImporter = CompletableDeferred<Unit>()
        val importer = object : RestoredAccountMigrationImporter {
            override suspend fun importIfNeeded(
                ownerProof: AutoRestoreOwnerProof,
            ): AutoRestoreResult {
                enteredImporter.complete(Unit)
                releaseImporter.await()
                return AutoRestoreResult.Restored
            }
        }
        val fixture = fixture(importer = importer)
        val first = async {
            fixture.coordinator.confirmSameGoogleIdentityAndRestore()
        }
        enteredImporter.await()

        assertEquals(
            RestoredAccountMigrationResult.AlreadyRunning,
            fixture.coordinator.confirmSameGoogleIdentityAndRestore(),
        )

        releaseImporter.complete(Unit)
        assertEquals(RestoredAccountMigrationResult.Migrated, first.await())
    }

    private fun fixture(
        account: RestoredAccountMigrationAccount? = account(),
        accountState: MutableStateFlow<RestoredAccountMigrationAccount?> =
            MutableStateFlow(account),
        owner: FakeMigrationOwner = FakeMigrationOwner(),
        claims: FakeMigrationClaims = FakeMigrationClaims(),
        importer: RestoredAccountMigrationImporter =
            FakeMigrationImporter(AutoRestoreResult.Restored),
        provenance: FakeMigrationProvenance = FakeMigrationProvenance(),
        snapshot: RestoredAccountMigrationScheduler = FakeMigrationScheduler(),
        cloud: RestoredAccountMigrationScheduler = FakeMigrationScheduler(),
        cloudEnabled: Boolean = true,
        events: MutableList<String>? = null,
    ): Fixture {
        owner.events = events
        claims.events = events
        provenance.events = events
        (importer as? FakeMigrationImporter)?.events = events
        (snapshot as? FakeMigrationScheduler)?.apply {
            name = "snapshot"
            this.events = events
        }
        (cloud as? FakeMigrationScheduler)?.apply {
            name = "cloud"
            this.events = events
        }
        return Fixture(
            coordinator = RestoredAccountMigrationCoordinator(
                accountProvider = RestoredAccountMigrationAccountProvider {
                    accountState.value
                },
                ownerState = owner,
                provenance = provenance,
                pendingClaims = claims,
                importer = importer,
                snapshotScheduler = snapshot,
                cloudUploadScheduler = cloud,
                cloudEnabled = RestoredAccountMigrationCloudEnabled { cloudEnabled },
            ),
            owner = owner,
            claims = claims,
            importer = importer as? FakeMigrationImporter
                ?: FakeMigrationImporter(AutoRestoreResult.Restored),
            provenance = provenance,
        )
    }

    private data class Fixture(
        val coordinator: RestoredAccountMigrationCoordinator,
        val owner: FakeMigrationOwner,
        val claims: FakeMigrationClaims,
        val importer: FakeMigrationImporter,
        val provenance: FakeMigrationProvenance,
    )

    private fun account(
        uid: String = CurrentUid,
        isAnonymous: Boolean = false,
        hasGoogleProvider: Boolean = true,
        googleSubjectHash: String? = Hash,
    ) = RestoredAccountMigrationAccount(
        uid = uid,
        isAnonymous = isAnonymous,
        hasGoogleProvider = hasGoogleProvider,
        googleSubjectHash = googleSubjectHash,
    )

    private fun claim(
        previousOwnerUid: String = PreviousUid,
        previousGoogleSubjectHash: String = Hash,
        currentUid: String = CurrentUid,
        currentGoogleSubjectHash: String = Hash,
        createdAtMillis: Long = 123L,
    ) = PendingRestoredOwnershipClaim(
        previousOwnerUid = previousOwnerUid,
        previousGoogleSubjectHash = previousGoogleSubjectHash,
        currentUid = currentUid,
        currentGoogleSubjectHash = currentGoogleSubjectHash,
        createdAtMillis = createdAtMillis,
    )

    private companion object {
        const val PreviousUid = "previous-user"
        const val CurrentUid = "current-user"
        const val Hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val TestFailure = IllegalStateException("test failure")
    }
}

private class FakeMigrationOwner(
    initialUid: String = "previous-user",
    initialHash: String = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    var failWrites: Int = 0,
) : RestoredAccountMigrationOwnerState {
    val completed = MutableStateFlow(true)
    val uid = MutableStateFlow<String?>(initialUid)
    val hash = MutableStateFlow<String?>(initialHash)
    var events: MutableList<String>? = null
    var writes = 0

    override val isCompleted: Flow<Boolean> = completed
    override val completedAccountUid: Flow<String?> = uid
    override val completedGoogleSubjectHash: Flow<String?> = hash

    override suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String?,
    ) {
        events?.add("adopt")
        if (failWrites > 0) {
            failWrites -= 1
            throw IllegalStateException("owner write failed")
        }
        writes += 1
        completed.value = isCompleted
        uid.value = accountUid
        hash.value = googleSubjectHash
    }
}

private class FakeMigrationClaims(
    initial: PendingRestoredOwnershipClaim? = null,
    var failClears: Int = 0,
) : PendingRestoredOwnershipClaimStore {
    var claim: PendingRestoredOwnershipClaim? = initial
    var events: MutableList<String>? = null
    var writes = 0

    override fun read(): PendingRestoredOwnershipClaim? = claim

    override fun write(claim: PendingRestoredOwnershipClaim) {
        events?.add("claim")
        writes += 1
        this.claim = claim
    }

    override fun clear() {
        events?.add("clearClaim")
        if (failClears > 0) {
            failClears -= 1
            throw IllegalStateException("claim clear failed")
        }
        claim = null
    }
}

private class FakeMigrationImporter(
    private val results: ArrayDeque<AutoRestoreResult>,
    private val afterImport: (() -> Unit)? = null,
) : RestoredAccountMigrationImporter {
    constructor(
        result: AutoRestoreResult,
        afterImport: (() -> Unit)? = null,
    ) : this(ArrayDeque(listOf(result)), afterImport)

    var events: MutableList<String>? = null
    var calls = 0

    override suspend fun importIfNeeded(
        ownerProof: AutoRestoreOwnerProof,
    ): AutoRestoreResult {
        events?.add("import")
        calls += 1
        val result = if (results.size > 1) results.removeFirst() else results.first()
        afterImport?.invoke()
        return result
    }
}

private class FakeMigrationProvenance(
    var pending: Boolean = true,
) : RestoredAccountMigrationProvenance {
    var events: MutableList<String>? = null

    override fun isRestorePending(): Boolean = pending

    override fun clearRestorePending() {
        events?.add("clearProvenance")
        pending = false
    }
}

private class FakeMigrationScheduler(
    var failRequests: Int = 0,
) : RestoredAccountMigrationScheduler {
    var name: String = "scheduler"
    var events: MutableList<String>? = null

    override fun request() {
        events?.add(name)
        if (failRequests > 0) {
            failRequests -= 1
            throw IllegalStateException("$name failed")
        }
    }
}
