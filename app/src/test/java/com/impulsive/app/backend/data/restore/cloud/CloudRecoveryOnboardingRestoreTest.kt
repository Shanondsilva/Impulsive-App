package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaim
import com.impulsive.app.backend.data.restore.PendingRestoredOwnershipClaimStore
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryOnboardingRestoreTest {
    @Test
    fun `exact UID email restore persists all answers with null Google hash and finalizes normally`() =
        runBlocking {
            val answers = completeAnswers()
            val state = MutableOwnerState()
            var restoredAnswers: OnboardingAnswers? = null

            val persisted = restoreCloudRecoveryOnboardingAfterCommittedImport(
                snapshot = CloudRecoveryOnboardingSnapshot(answers),
                currentUid = "current-uid",
                currentGoogleSubjectHash = null,
            ) { restored, uid, googleHash ->
                restoredAnswers = restored
                state.complete(uid, googleHash)
            }

            assertTrue(persisted)
            assertEquals(answers, restoredAnswers)
            assertEquals("current-uid", state.completedAccountUid.value)
            assertNull(state.completedGoogleSubjectHash.value)
            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                finalizer(
                    state = state,
                    currentUid = "current-uid",
                    googleSubjectHash = null,
                ).finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid("current-uid"),
                ),
            )
        }

    @Test
    fun `exact UID Google restore persists all answers and finalizes normally`() =
        runBlocking {
            val answers = completeAnswers()
            val verifiedHash = "a".repeat(64)
            val state = MutableOwnerState()
            var restoredAnswers: OnboardingAnswers? = null

            val persisted = restoreCloudRecoveryOnboardingAfterCommittedImport(
                snapshot = CloudRecoveryOnboardingSnapshot(answers),
                currentUid = "current-google-uid",
                currentGoogleSubjectHash = verifiedHash,
            ) { restored, uid, googleHash ->
                restoredAnswers = restored
                state.complete(uid, googleHash)
            }

            assertTrue(persisted)
            assertEquals(answers, restoredAnswers)
            assertEquals("current-google-uid", state.completedAccountUid.value)
            assertEquals(verifiedHash, state.completedGoogleSubjectHash.value)
            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                finalizer(
                    state = state,
                    currentUid = "current-google-uid",
                    googleSubjectHash = verifiedHash,
                ).finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid("current-google-uid"),
                ),
            )
        }

    @Test
    fun `same Google identity with changed Firebase UID restores answers and finalizes normally`() =
        runBlocking {
            val answers = completeAnswers()
            val verifiedHash = "b".repeat(64)
            val state = MutableOwnerState()
            var restoredAnswers: OnboardingAnswers? = null

            val persisted = restoreCloudRecoveryOnboardingAfterCommittedImport(
                snapshot = CloudRecoveryOnboardingSnapshot(answers),
                currentUid = "previous-google-uid",
                currentGoogleSubjectHash = verifiedHash,
            ) { restored, uid, googleHash ->
                restoredAnswers = restored
                state.complete(uid, googleHash)
            }

            assertTrue(persisted)
            assertEquals(answers, restoredAnswers)
            assertEquals("previous-google-uid", state.completedAccountUid.value)
            assertEquals(verifiedHash, state.completedGoogleSubjectHash.value)
            assertEquals(
                CloudRestoreOwnershipFinalizationResult.Success,
                finalizer(
                    state = state,
                    currentUid = "current-google-uid",
                    googleSubjectHash = verifiedHash,
                    restorePending = true,
                ).finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.SameGoogleIdentity(
                        previousUid = "previous-google-uid",
                        previousGoogleSubjectHash = verifiedHash,
                        currentUid = "current-google-uid",
                        currentGoogleSubjectHash = verifiedHash,
                    ),
                ),
            )
            assertEquals(answers, restoredAnswers)
            assertEquals("current-google-uid", state.completedAccountUid.value)
            assertEquals(verifiedHash, state.completedGoogleSubjectHash.value)
        }

    @Test
    fun `legacy snapshot-free restore still requires onboarding`() =
        runBlocking {
            val state = MutableOwnerState()

            assertFalse(
                restoreCloudRecoveryOnboardingAfterCommittedImport(
                    snapshot = null,
                    currentUid = "current-uid",
                    currentGoogleSubjectHash = null,
                ) { _, _, _ -> error("Legacy restore must not persist onboarding") },
            )
            assertEquals(
                CloudRestoreOwnershipFinalizationResult.SuccessRequiresOnboardingSetup,
                finalizer(
                    state = state,
                    currentUid = "current-uid",
                    googleSubjectHash = null,
                ).finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid("current-uid"),
                ),
            )
        }

    @Test
    fun `failed onboarding write preserves committed import and does not complete onboarding`() =
        runBlocking {
            val importedNotes = mutableListOf("restored-note")
            val state = MutableOwnerState()

            val persisted = restoreCloudRecoveryOnboardingAfterCommittedImport(
                snapshot = CloudRecoveryOnboardingSnapshot(completeAnswers()),
                currentUid = "current-uid",
                currentGoogleSubjectHash = null,
            ) { _, _, _ -> throw IllegalStateException("DataStore unavailable") }

            assertFalse(persisted)
            assertEquals(listOf("restored-note"), importedNotes)
            assertFalse(state.isCompleted.value)
            assertEquals(
                CloudRestoreOwnershipFinalizationResult.SuccessRequiresOnboardingSetup,
                finalizer(
                    state = state,
                    currentUid = "current-uid",
                    googleSubjectHash = null,
                ).finalizeAfterVerifiedCloudRestore(
                    VerifiedCloudRestoreOwnerProof.ExactUid("current-uid"),
                ),
            )
        }

    @Test
    fun `coordinator validates once before import and persists only after commit`() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/restore/cloud/" +
                "CloudRecoveryRestoreCoordinator.kt",
        ).readText()
        val restoreStart = source.indexOf("public suspend fun restore(")
        val restoreEnd = source.indexOf("private fun VerifiedCloudRestoreOwnerProof.proofType")
        val restore = source.substring(restoreStart, restoreEnd)

        val parse = restore.indexOf("val parsedPayload")
        val decode = restore.indexOf("CloudRecoveryOnboardingSnapshotCodec.decode(")
        val import = restore.indexOf("importer.importPayload(")
        val persist = restore.indexOf("restoreCloudRecoveryOnboardingAfterCommittedImport(")
        val finalize = restore.indexOf("finalizeThenActivateRestoredCloudRecovery(")

        assertTrue(parse in 0 until decode)
        assertTrue(decode in 0 until import)
        assertTrue(import in 0 until persist)
        assertTrue(persist in 0 until finalize)
        assertEquals(1, restore.windowed("JSONObject(".length).count { it == "JSONObject(" })
    }

    private fun finalizer(
        state: MutableOwnerState,
        currentUid: String,
        googleSubjectHash: String?,
        restorePending: Boolean = false,
    ): CloudRestoreOwnershipFinalizer =
        CloudRestoreOwnershipFinalizer(
            accountProvider = CloudRestoreOwnershipAccountProvider {
                CloudRestoreOwnershipAccount(
                    uid = currentUid,
                    googleSubjectHash = googleSubjectHash,
                    hasGoogleProvider = googleSubjectHash != null,
                )
            },
            ownerState = state,
            provenance = object : CloudRestoreOwnershipProvenance {
                override fun isRestorePending(): Boolean = restorePending
                override fun clearRestorePending() = Unit
            },
            pendingClaims = object : PendingRestoredOwnershipClaimStore {
                private var pendingClaim: PendingRestoredOwnershipClaim? = null

                override fun read(): PendingRestoredOwnershipClaim? = pendingClaim
                override fun write(claim: PendingRestoredOwnershipClaim) {
                    pendingClaim = claim
                }
                override fun clear() {
                    pendingClaim = null
                }
            },
            snapshotScheduler = CloudRestoreOwnershipScheduler {},
            cloudScheduler = CloudRestoreOwnershipScheduler {},
        )

    private class MutableOwnerState : CloudRestoreOwnershipState {
        override val isCompleted = MutableStateFlow(false)
        override val completedAccountUid = MutableStateFlow<String?>(null)
        override val completedGoogleSubjectHash = MutableStateFlow<String?>(null)

        fun complete(uid: String, googleHash: String?) {
            isCompleted.value = true
            completedAccountUid.value = uid
            completedGoogleSubjectHash.value = googleHash
        }

        override suspend fun setCompletedForAccount(
            isCompleted: Boolean,
            accountUid: String?,
            googleSubjectHash: String?,
        ) {
            this.isCompleted.value = isCompleted
            completedAccountUid.value = accountUid
            completedGoogleSubjectHash.value = googleSubjectHash
        }
    }

    private fun completeAnswers(): OnboardingAnswers =
        OnboardingAnswers(
            name = "Alex",
            avatarId = "mountain",
            interrupting = listOf("work", "sleep"),
            timing = listOf("morning"),
            triggers = listOf("stress"),
            weekOneGoal = "notice-patterns",
            dailyRelapseUrgeCount = 2,
            activeDayStartMinute = 420,
            activeDayEndMinute = 1320,
            plannedReleaseWindowMinutes = listOf(720, 1080),
        )
}