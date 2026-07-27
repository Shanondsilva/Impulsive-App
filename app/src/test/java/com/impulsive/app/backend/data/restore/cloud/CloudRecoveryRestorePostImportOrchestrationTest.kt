package com.impulsive.app.backend.data.restore.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryRestorePostImportOrchestrationTest {
    @Test
    fun ownershipFinalizationOccursBeforeSessionValidationAndActivation() =
        runBlocking {
            val events = mutableListOf<String>()

            val result = orchestrate(
                proof = exactProof(),
                finalize = {
                    events += "finalize"
                    CloudRestoreOwnershipFinalizationResult.Success
                },
                session = {
                    events += "session"
                    exactSession()
                },
                activate = {
                    events += "activate"
                    CloudRecoveryRestoreResult.Success
                },
            )

            assertEquals(CloudRecoveryRestoreResult.Success, result)
            assertEquals(listOf("finalize", "session", "activate"), events)
        }

    @Test
    fun ownershipPendingPerformsZeroCloudActivationMutations() = runBlocking {
        val activation = CountingCloudActivation()

        val result = orchestrate(
            proof = exactProof(),
            finalize = {
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
            },
            session = { exactSession() },
            activate = { activation.activate() },
        )

        assertEquals(
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(0, activation.keyStore.storeCalls)
        assertEquals(0, activation.metadataStore.storeCalls)
        assertEquals(0, activation.preferences.enableCalls)
        assertEquals(0, activation.scheduler.requestCalls)
    }

    @Test
    fun firebaseUidChangeAfterImportBlocksActivation() = runBlocking {
        var activationCalls = 0

        val result = orchestrate(
            proof = exactProof(),
            session = {
                CloudRestoreOwnershipAccount(
                    uid = "changed-uid",
                    googleSubjectHash = Hash,
                )
            },
            activate = {
                activationCalls += 1
                CloudRecoveryRestoreResult.Success
            },
        )

        assertEquals(
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(0, activationCalls)
    }

    @Test
    fun googleSubjectChangeAfterImportBlocksActivation() = runBlocking {
        var activationCalls = 0

        val result = orchestrate(
            proof = sameGoogleProof(),
            session = {
                CloudRestoreOwnershipAccount(
                    uid = CurrentUid,
                    googleSubjectHash = OtherHash,
                    hasGoogleProvider = true,
                )
            },
            activate = {
                activationCalls += 1
                CloudRecoveryRestoreResult.Success
            },
        )

        assertEquals(
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(0, activationCalls)
    }

    @Test
    fun linkedGoogleProviderWithUnresolvedHashBlocksActivation() = runBlocking {
        val unresolved = CloudRestoreOwnershipAccount(
            uid = CurrentUid,
            googleSubjectHash = null,
            hasGoogleProvider = true,
        )

        assertFalse(sameGoogleProof().matchesCurrentSession(unresolved))
        assertFalse(
            legacyGoogleProof().matchesCurrentSession(unresolved),
        )
    }

    @Test
    fun legacyNoGoogleProofRejectsNewlyLinkedGoogleProvider() = runBlocking {
        var activationCalls = 0

        val result = orchestrate(
            proof = legacyNoGoogleProof(),
            session = {
                CloudRestoreOwnershipAccount(
                    uid = CurrentUid,
                    googleSubjectHash = Hash,
                    hasGoogleProvider = true,
                )
            },
            activate = {
                activationCalls += 1
                CloudRecoveryRestoreResult.Success
            },
        )

        assertEquals(
            CloudRecoveryRestoreResult.RestoredButOwnershipFinalizationPending,
            result,
        )
        assertEquals(0, activationCalls)
    }

    @Test
    fun validExactUidProofPermitsActivation() = runBlocking {
        assertActivationPermitted(exactProof(), exactSession())
    }

    @Test
    fun validSameGoogleProofPermitsActivation() = runBlocking {
        assertActivationPermitted(
            sameGoogleProof(),
            CloudRestoreOwnershipAccount(
                uid = CurrentUid,
                googleSubjectHash = Hash,
                hasGoogleProvider = true,
            ),
        )
    }

    @Test
    fun validLegacyProofPermitsActivation() = runBlocking {
        assertActivationPermitted(
            legacyNoGoogleProof(),
            CloudRestoreOwnershipAccount(
                uid = CurrentUid,
                googleSubjectHash = null,
                hasGoogleProvider = false,
            ),
        )
        assertActivationPermitted(
            legacyGoogleProof(),
            CloudRestoreOwnershipAccount(
                uid = CurrentUid,
                googleSubjectHash = Hash,
                hasGoogleProvider = true,
            ),
        )
    }

    @Test
    fun setupRequiredPlusActivationFailureReturnsDistinctTruthfulResult() =
        runBlocking {
            val activation = CountingCloudActivation(keyStoreFails = true)

            val result = orchestrate(
                proof = exactProof(),
                finalize = {
                    CloudRestoreOwnershipFinalizationResult
                        .SuccessRequiresOnboardingSetup
                },
                session = { exactSession() },
                activate = { activation.activate() },
            )

            assertEquals(
                CloudRecoveryRestoreResult
                    .SuccessRequiresOnboardingSetupCloudRecoverySetupFailed,
                result,
            )
            assertEquals(1, activation.keyStore.storeCalls)
        }

    @Test
    fun requiredOwnershipAndActivationCombinationsAreTruthful() {
        val success =
            CloudRestoreOwnershipFinalizationResult.Success
        val refreshPending =
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending
        val requiresSetup =
            CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup
        val activationSuccess =
            CloudRecoveryRestoreResult.Success
        val activationRefreshPending =
            CloudRecoveryRestoreResult.SuccessBackupRefreshPending
        val activationSetupFailed =
            CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed

        assertEquals(
            CloudRecoveryRestoreResult.Success,
            combineCloudRestoreCompletion(success, activationSuccess),
        )
        assertEquals(
            CloudRecoveryRestoreResult.SuccessBackupRefreshPending,
            combineCloudRestoreCompletion(
                success,
                activationRefreshPending,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed,
            combineCloudRestoreCompletion(success, activationSetupFailed),
        )
        assertEquals(
            CloudRecoveryRestoreResult.SuccessBackupRefreshPending,
            combineCloudRestoreCompletion(
                refreshPending,
                activationSuccess,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed,
            combineCloudRestoreCompletion(
                refreshPending,
                activationSetupFailed,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult.SuccessRequiresOnboardingSetup,
            combineCloudRestoreCompletion(
                requiresSetup,
                activationSuccess,
            ),
        )
        assertEquals(
            CloudRecoveryRestoreResult
                .SuccessRequiresOnboardingSetupCloudRecoverySetupFailed,
            combineCloudRestoreCompletion(
                requiresSetup,
                activationSetupFailed,
            ),
        )
    }

    @Test
    fun dekIsZeroedForEveryResultAndExceptionPath() = runBlocking {
        val acceptedResults = listOf(
            CloudRestoreOwnershipFinalizationResult.Success,
            CloudRestoreOwnershipFinalizationResult
                .SuccessBackupRefreshPending,
            CloudRestoreOwnershipFinalizationResult
                .SuccessRequiresOnboardingSetup,
        )
        val activationResults = listOf(
            CloudRecoveryRestoreResult.Success,
            CloudRecoveryRestoreResult.SuccessBackupRefreshPending,
            CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed,
        )
        acceptedResults.forEach { ownership ->
            activationResults.forEach { activation ->
                val dek = ByteArray(32) { 7 }
                finalizeThenActivateRestoredCloudRecovery(
                    rawDek = dek,
                    ownerProof = exactProof(),
                    finalizeOwnership = { ownership },
                    currentSession = { exactSession() },
                    activateCloudRecovery = { activation },
                )
                assertTrue(dek.all { it == 0.toByte() })
            }
        }

        val pendingDek = ByteArray(32) { 7 }
        finalizeThenActivateRestoredCloudRecovery(
            rawDek = pendingDek,
            ownerProof = exactProof(),
            finalizeOwnership = {
                CloudRestoreOwnershipFinalizationResult
                    .RestoredButOwnershipFinalizationPending
            },
            currentSession = { exactSession() },
            activateCloudRecovery = { error("must not activate") },
        )
        assertTrue(pendingDek.all { it == 0.toByte() })

        val invalidSessionDek = ByteArray(32) { 7 }
        finalizeThenActivateRestoredCloudRecovery(
            rawDek = invalidSessionDek,
            ownerProof = exactProof(),
            finalizeOwnership = {
                CloudRestoreOwnershipFinalizationResult.Success
            },
            currentSession = { null },
            activateCloudRecovery = { error("must not activate") },
        )
        assertTrue(invalidSessionDek.all { it == 0.toByte() })

        val finalizerExceptionDek = ByteArray(32) { 7 }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                finalizeThenActivateRestoredCloudRecovery(
                    rawDek = finalizerExceptionDek,
                    ownerProof = exactProof(),
                    finalizeOwnership = {
                        throw IllegalStateException("finalizer failed")
                    },
                    currentSession = { exactSession() },
                    activateCloudRecovery = {
                        CloudRecoveryRestoreResult.Success
                    },
                )
            }
        }
        assertTrue(finalizerExceptionDek.all { it == 0.toByte() })

        val activationExceptionDek = ByteArray(32) { 7 }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                finalizeThenActivateRestoredCloudRecovery(
                    rawDek = activationExceptionDek,
                    ownerProof = exactProof(),
                    finalizeOwnership = {
                        CloudRestoreOwnershipFinalizationResult.Success
                    },
                    currentSession = { exactSession() },
                    activateCloudRecovery = {
                        throw IllegalStateException("activation failed")
                    },
                )
            }
        }
        assertTrue(activationExceptionDek.all { it == 0.toByte() })
    }

    private suspend fun assertActivationPermitted(
        proof: VerifiedCloudRestoreOwnerProof,
        session: CloudRestoreOwnershipAccount,
    ) {
        var activationCalls = 0

        val result = orchestrate(
            proof = proof,
            session = { session },
            activate = {
                activationCalls += 1
                CloudRecoveryRestoreResult.Success
            },
        )

        assertEquals(CloudRecoveryRestoreResult.Success, result)
        assertEquals(1, activationCalls)
    }

    private suspend fun orchestrate(
        proof: VerifiedCloudRestoreOwnerProof,
        finalize:
            suspend (VerifiedCloudRestoreOwnerProof) ->
                CloudRestoreOwnershipFinalizationResult = {
                    CloudRestoreOwnershipFinalizationResult.Success
                },
        session: () -> CloudRestoreOwnershipAccount?,
        activate: suspend () -> CloudRecoveryRestoreResult,
    ): CloudRecoveryRestoreResult {
        val dek = ByteArray(32) { 7 }
        return finalizeThenActivateRestoredCloudRecovery(
            rawDek = dek,
            ownerProof = proof,
            finalizeOwnership = finalize,
            currentSession = session,
            activateCloudRecovery = activate,
        ).also {
            assertTrue(dek.all { byte -> byte == 0.toByte() })
        }
    }

    private fun exactProof() =
        VerifiedCloudRestoreOwnerProof.ExactUid(CurrentUid)

    private fun sameGoogleProof() =
        VerifiedCloudRestoreOwnerProof.SameGoogleIdentity(
            previousUid = "previous-user",
            previousGoogleSubjectHash = Hash,
            currentUid = CurrentUid,
            currentGoogleSubjectHash = Hash,
        )

    private fun legacyNoGoogleProof() =
        VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
            previousUid = "previous-user",
            currentUid = CurrentUid,
            currentGoogleSubjectHash = null,
        )

    private fun legacyGoogleProof() =
        VerifiedCloudRestoreOwnerProof.LegacyEnvelope(
            previousUid = "previous-user",
            currentUid = CurrentUid,
            currentGoogleSubjectHash = Hash,
        )

    private fun exactSession() =
        CloudRestoreOwnershipAccount(
            uid = CurrentUid,
            googleSubjectHash = Hash,
            hasGoogleProvider = true,
        )

    private companion object {
        const val CurrentUid = "current-user"
        const val Hash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}

private class CountingCloudActivation(
    keyStoreFails: Boolean = false,
) {
    val keyStore = CountingCloudKeyStore(keyStoreFails)
    val metadataStore = CountingCloudMetadataStore()
    val preferences = CountingCloudPreferences()
    val scheduler = CountingCloudScheduler()

    suspend fun activate(): CloudRecoveryRestoreResult =
        activateRestoredCloudRecovery(
            rawDek = ByteArray(CloudRecoveryDekBytes) { 3 },
            wrappedKeyMetadata = WrappedKeyMetadata(
                kdfSalt = ByteArray(CloudRecoverySaltBytes) { 1 },
                wrappedDekIv = ByteArray(CloudRecoveryIvBytes) { 2 },
                wrappedDekCipherText =
                    ByteArray(
                        CloudRecoveryDekBytes +
                            CloudRecoveryGcmTagBytes,
                    ) { 3 },
            ),
            keyStore = keyStore,
            metadataStore = metadataStore,
            preferences = preferences,
            scheduler = scheduler,
        )
}

private class CountingCloudKeyStore(
    private val fails: Boolean,
) : CloudRecoveryRestoreKeyStore {
    var storeCalls = 0
    override fun store(rawDek: ByteArray) {
        storeCalls += 1
        if (fails) throw IllegalStateException("key store failed")
    }
    override fun clear() = Unit
}

private class CountingCloudMetadataStore : CloudRecoveryRestoreMetadataStore {
    var storeCalls = 0
    override fun store(metadata: WrappedKeyMetadata) {
        storeCalls += 1
    }
    override fun clear() = Unit
}

private class CountingCloudPreferences : CloudRecoveryRestorePreferences {
    var enableCalls = 0
    override suspend fun setEnabled(enabled: Boolean) {
        if (enabled) enableCalls += 1
    }
}

private class CountingCloudScheduler : CloudRecoveryRestoreScheduler {
    var requestCalls = 0
    override fun request() {
        requestCalls += 1
    }
    override fun cancel() = Unit
}
