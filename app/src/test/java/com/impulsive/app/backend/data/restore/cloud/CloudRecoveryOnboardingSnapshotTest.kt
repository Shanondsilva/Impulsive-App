package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryOnboardingSnapshotTest {
    @Test
    fun `codec round trips every onboarding answer field`() {
        val snapshot = CloudRecoveryOnboardingSnapshot(completeAnswers())
        val payload = JSONObject().put(
            CloudRecoveryOnboardingSnapshotJsonKey,
            CloudRecoveryOnboardingSnapshotCodec.encode(snapshot),
        )

        assertEquals(
            CloudRecoveryOnboardingSnapshotDecodeResult.Success(snapshot),
            CloudRecoveryOnboardingSnapshotCodec.decode(payload),
        )
    }

    @Test
    fun `missing snapshot is accepted as legacy payload`() {
        assertEquals(
            CloudRecoveryOnboardingSnapshotDecodeResult.Missing,
            CloudRecoveryOnboardingSnapshotCodec.decode(JSONObject()),
        )
    }

    @Test
    fun `unsupported and structurally malformed snapshots are rejected`() {
        val unsupported = encodedSnapshot().put("version", 2)
        val wrongType = encodedSnapshot().put("dailyRelapseUrgeCount", "3")
        val missingField = encodedSnapshot().apply { remove("avatarId") }
        val unknownField = encodedSnapshot().put("unexpected", true)
        val nonObject = JSONObject().put(
            CloudRecoveryOnboardingSnapshotJsonKey,
            JSONArray(),
        )

        listOf(unsupported, wrongType, missingField, unknownField).forEach { encoded ->
            assertMalformed(
                JSONObject().put(CloudRecoveryOnboardingSnapshotJsonKey, encoded),
            )
        }
        assertMalformed(nonObject)
    }

    @Test
    fun `invalid ranges blank names and invalid list entries are rejected`() {
        val invalidSnapshots =
            listOf(
                encodedSnapshot().put("name", "   "),
                encodedSnapshot().put("dailyRelapseUrgeCount", 0),
                encodedSnapshot().put("dailyRelapseUrgeCount", 11),
                encodedSnapshot().put("activeDayStartMinute", -1),
                encodedSnapshot().put("activeDayEndMinute", 1440),
                encodedSnapshot().put(
                    "plannedReleaseWindowMinutes",
                    JSONArray(listOf(1440)),
                ),
                encodedSnapshot().put(
                    "interrupting",
                    JSONArray(listOf("")),
                ),
                encodedSnapshot().put(
                    "timing",
                    JSONArray(listOf("evening", "evening")),
                ),
                encodedSnapshot().put(
                    "triggers",
                    JSONArray(listOf(7)),
                ),
            )

        invalidSnapshots.forEach { encoded ->
            assertMalformed(
                JSONObject().put(CloudRecoveryOnboardingSnapshotJsonKey, encoded),
            )
        }

        assertEquals(
            CloudRecoveryOnboardingSnapshotDecodeResult.Success(
                CloudRecoveryOnboardingSnapshot(
                    completeAnswers().copy(
                        name = "",
                    ),
                ),
            ),
            CloudRecoveryOnboardingSnapshotCodec.decode(
                JSONObject().put(
                    CloudRecoveryOnboardingSnapshotJsonKey,
                    encodedSnapshot().put("name", ""),
                ),
            ),
        )
    }

    @Test
    fun `matching completed owner creates backup snapshot`() {
        val answers = completeAnswers()

        assertEquals(
            CloudRecoveryOnboardingSnapshot(answers),
            cloudRecoveryOnboardingSnapshotForBackup(
                verifiedOwnerUid = " current-uid ",
                isCompleted = true,
                completedAccountUid = "current-uid",
                answers = answers,
            ),
        )
    }

    @Test
    fun `completed matching owner with blank legacy name creates backup snapshot`() {
        val snapshot =
            cloudRecoveryOnboardingSnapshotForBackup(
                verifiedOwnerUid = "current-uid",
                isCompleted = true,
                completedAccountUid = "current-uid",
                answers = completeAnswers().copy(
                    name = "   ",
                ),
            )

        assertEquals(
            "",
            snapshot?.answers?.name,
        )
    }

    @Test
    fun `empty legacy name encodes decodes and round trips as empty`() {
        val snapshot =
            CloudRecoveryOnboardingSnapshot(
                completeAnswers().copy(
                    name = "",
                ),
            )
        val payload = JSONObject().put(
            CloudRecoveryOnboardingSnapshotJsonKey,
            CloudRecoveryOnboardingSnapshotCodec.encode(snapshot),
        )

        assertEquals(
            CloudRecoveryOnboardingSnapshotDecodeResult.Success(snapshot),
            CloudRecoveryOnboardingSnapshotCodec.decode(payload),
        )
    }

    @Test
    fun `whitespace only encoded name is malformed`() {
        assertMalformed(
            JSONObject().put(
                CloudRecoveryOnboardingSnapshotJsonKey,
                encodedSnapshot().put("name", "   "),
            ),
        )
    }

    @Test
    fun `legacy completed answers are canonicalized deterministically for backup`() {
        val canonical =
            canonicalizeCloudRecoveryOnboardingAnswersForBackup(
                OnboardingAnswers(
                    name = "   ",
                    avatarId = "   ",
                    interrupting = listOf(
                        " work ",
                        "",
                        "work",
                        "sleep\u001Fproblem",
                    ),
                    timing = listOf(
                        " evening ",
                        "evening",
                    ),
                    triggers = listOf(
                        "",
                        " stress ",
                    ),
                    weekOneGoal = "   ",
                    dailyRelapseUrgeCount = 99,
                    activeDayStartMinute = -10,
                    activeDayEndMinute = 2000,
                    plannedReleaseWindowMinutes = listOf(
                        -1,
                        480,
                        480,
                        1440,
                        720,
                    ),
                ),
            )

        assertEquals(
            OnboardingAnswers(
                name = "",
                avatarId = "wave",
                interrupting = listOf(
                    "work",
                    "sleep problem",
                ),
                timing = listOf(
                    "evening",
                ),
                triggers = listOf(
                    "stress",
                ),
                weekOneGoal = null,
                dailyRelapseUrgeCount = 10,
                activeDayStartMinute = 0,
                activeDayEndMinute = 1439,
                plannedReleaseWindowMinutes = listOf(
                    480,
                    720,
                ),
            ),
            canonical,
        )
    }

    @Test
    fun `overlong legacy values are truncated instead of dropping completed owner snapshot`() {
        val snapshot =
            cloudRecoveryOnboardingSnapshotForBackup(
                verifiedOwnerUid = "current-uid",
                isCompleted = true,
                completedAccountUid = "current-uid",
                answers = completeAnswers().copy(
                    name = "n".repeat(150),
                    avatarId = "a".repeat(90),
                    interrupting = listOf("i".repeat(160)),
                    timing = listOf("t".repeat(160)),
                    triggers = listOf("r".repeat(160)),
                    weekOneGoal = "g".repeat(160),
                ),
            )

        val answers = snapshot?.answers
        assertEquals(100, answers?.name?.length)
        assertEquals(64, answers?.avatarId?.length)
        assertEquals(128, answers?.interrupting?.single()?.length)
        assertEquals(128, answers?.timing?.single()?.length)
        assertEquals(128, answers?.triggers?.single()?.length)
        assertEquals(128, answers?.weekOneGoal?.length)
    }

    @Test
    fun `more than sixty four legacy entries are reduced to first sixty four distinct entries`() {
        val values =
            (0 until 70).map {
                "value-$it"
            } + "value-0"

        val canonical =
            canonicalizeCloudRecoveryOnboardingAnswersForBackup(
                completeAnswers().copy(
                    interrupting = values,
                    timing = values,
                    triggers = values,
                    plannedReleaseWindowMinutes =
                        (0 until 70).map {
                            it
                        } + 0,
                ),
            )

        assertEquals((0 until 64).map { "value-$it" }, canonical.interrupting)
        assertEquals((0 until 64).map { "value-$it" }, canonical.timing)
        assertEquals((0 until 64).map { "value-$it" }, canonical.triggers)
        assertEquals((0 until 64).toList(), canonical.plannedReleaseWindowMinutes)
    }

    @Test
    fun `mismatched or incomplete owner never creates backup snapshot`() {
        val answers = completeAnswers()

        assertNull(
            cloudRecoveryOnboardingSnapshotForBackup(
                verifiedOwnerUid = "current-uid",
                isCompleted = true,
                completedAccountUid = "old-uid",
                answers = answers,
            ),
        )
        assertNull(
            cloudRecoveryOnboardingSnapshotForBackup(
                verifiedOwnerUid = "current-uid",
                isCompleted = false,
                completedAccountUid = "current-uid",
                answers = answers,
            ),
        )
    }

    @Test
    fun `writer uses verified owner gating and encrypted payload JSON key`() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt",
        ).readText()

        assertTrue(source.contains("buildPayloadJson(normalizedOwnerUid)"))
        assertTrue(source.contains("val normalizedVerifiedOwnerUid ="))
        assertTrue(source.contains("requireNotNull("))
        assertTrue(source.contains("cloudRecoveryOnboardingSnapshotForBackup("))
        assertTrue(source.contains("CloudRecoveryOnboardingSnapshotJsonKey"))
        assertTrue(source.contains("CloudRecoveryOnboardingSnapshotCodec.encode("))
    }

    @Test
    fun `normal onboarding blank name guard remains present`() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/session/onboarding/" +
                "OnboardingViewModel.kt",
        ).readText()

        assertTrue(
            source.contains(
                "if (state.value.answers.name.isBlank()) return@launch",
            ),
        )
    }

    private fun encodedSnapshot(): JSONObject =
        CloudRecoveryOnboardingSnapshotCodec.encode(
            CloudRecoveryOnboardingSnapshot(completeAnswers()),
        )

    private fun assertMalformed(payload: JSONObject) {
        assertEquals(
            CloudRecoveryOnboardingSnapshotDecodeResult.Malformed,
            CloudRecoveryOnboardingSnapshotCodec.decode(payload),
        )
    }

    private fun completeAnswers(): OnboardingAnswers =
        OnboardingAnswers(
            name = "Alex",
            avatarId = "mountain",
            interrupting = listOf("work", "sleep"),
            timing = listOf("morning", "evening"),
            triggers = listOf("stress", "boredom"),
            weekOneGoal = "notice-patterns",
            dailyRelapseUrgeCount = 4,
            activeDayStartMinute = 390,
            activeDayEndMinute = 1350,
            plannedReleaseWindowMinutes = listOf(480, 720, 960, 1200),
        )
}