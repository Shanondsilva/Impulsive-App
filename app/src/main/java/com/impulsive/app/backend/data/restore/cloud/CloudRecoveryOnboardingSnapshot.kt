package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import org.json.JSONArray
import org.json.JSONObject

internal const val CloudRecoveryOnboardingSnapshotVersion = 1
internal const val CloudRecoveryOnboardingSnapshotJsonKey =
    "onboardingSnapshot"

internal data class CloudRecoveryOnboardingSnapshot(
    val answers: OnboardingAnswers,
)

internal sealed interface CloudRecoveryOnboardingSnapshotDecodeResult {
    data object Missing : CloudRecoveryOnboardingSnapshotDecodeResult

    data class Success(
        val snapshot: CloudRecoveryOnboardingSnapshot,
    ) : CloudRecoveryOnboardingSnapshotDecodeResult

    data object Malformed : CloudRecoveryOnboardingSnapshotDecodeResult
}

internal object CloudRecoveryOnboardingSnapshotCodec {
    fun encode(snapshot: CloudRecoveryOnboardingSnapshot): JSONObject {
        requireValidCloudRecoveryOnboardingAnswers(snapshot.answers)
        val answers = snapshot.answers
        return JSONObject()
            .put("version", CloudRecoveryOnboardingSnapshotVersion)
            .put("name", answers.name)
            .put("avatarId", answers.avatarId)
            .put("interrupting", JSONArray(answers.interrupting))
            .put("timing", JSONArray(answers.timing))
            .put("triggers", JSONArray(answers.triggers))
            .put("weekOneGoal", answers.weekOneGoal ?: JSONObject.NULL)
            .put("dailyRelapseUrgeCount", answers.dailyRelapseUrgeCount)
            .put("activeDayStartMinute", answers.activeDayStartMinute)
            .put("activeDayEndMinute", answers.activeDayEndMinute)
            .put(
                "plannedReleaseWindowMinutes",
                JSONArray(answers.plannedReleaseWindowMinutes),
            )
    }

    fun decode(payload: JSONObject): CloudRecoveryOnboardingSnapshotDecodeResult {
        if (!payload.has(CloudRecoveryOnboardingSnapshotJsonKey)) {
            return CloudRecoveryOnboardingSnapshotDecodeResult.Missing
        }
        val encoded = payload.opt(CloudRecoveryOnboardingSnapshotJsonKey)
        if (encoded !is JSONObject || encoded.keysSet() != RequiredKeys) {
            return CloudRecoveryOnboardingSnapshotDecodeResult.Malformed
        }

        return try {
            val version = encoded.requiredInt("version")
            if (version != CloudRecoveryOnboardingSnapshotVersion) {
                return CloudRecoveryOnboardingSnapshotDecodeResult.Malformed
            }
            val answers = OnboardingAnswers(
                name = encoded.requiredString("name"),
                avatarId = encoded.requiredString("avatarId"),
                interrupting = encoded.requiredStringList("interrupting"),
                timing = encoded.requiredStringList("timing"),
                triggers = encoded.requiredStringList("triggers"),
                weekOneGoal = encoded.requiredNullableString("weekOneGoal"),
                dailyRelapseUrgeCount =
                    encoded.requiredInt("dailyRelapseUrgeCount"),
                activeDayStartMinute = encoded.requiredInt("activeDayStartMinute"),
                activeDayEndMinute = encoded.requiredInt("activeDayEndMinute"),
                plannedReleaseWindowMinutes =
                    encoded.requiredIntList("plannedReleaseWindowMinutes"),
            )
            if (!isValidCloudRecoveryOnboardingAnswers(answers)) {
                CloudRecoveryOnboardingSnapshotDecodeResult.Malformed
            } else {
                CloudRecoveryOnboardingSnapshotDecodeResult.Success(
                    CloudRecoveryOnboardingSnapshot(answers),
                )
            }
        } catch (_: SnapshotDecodeException) {
            CloudRecoveryOnboardingSnapshotDecodeResult.Malformed
        }
    }

    private val RequiredKeys =
        setOf(
            "version",
            "name",
            "avatarId",
            "interrupting",
            "timing",
            "triggers",
            "weekOneGoal",
            "dailyRelapseUrgeCount",
            "activeDayStartMinute",
            "activeDayEndMinute",
            "plannedReleaseWindowMinutes",
        )
}

internal fun cloudRecoveryOnboardingSnapshotForBackup(
    verifiedOwnerUid: String?,
    isCompleted: Boolean,
    completedAccountUid: String?,
    answers: OnboardingAnswers,
): CloudRecoveryOnboardingSnapshot? {
    val normalizedOwnerUid =
        verifiedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)

    if (
        normalizedOwnerUid == null ||
        !isCompleted ||
        completedAccountUid != normalizedOwnerUid
    ) {
        return null
    }

    val canonicalAnswers =
        canonicalizeCloudRecoveryOnboardingAnswersForBackup(
            answers,
        )

    return CloudRecoveryOnboardingSnapshot(
        answers = canonicalAnswers,
    )
}

internal fun canonicalizeCloudRecoveryOnboardingAnswersForBackup(
    answers: OnboardingAnswers,
): OnboardingAnswers =
    OnboardingAnswers(
        name =
            answers.name.canonicalizeCloudRecoveryBackupText(
                maxChars = MaxNameChars,
            ),

        avatarId =
            answers.avatarId
                .canonicalizeCloudRecoveryBackupText(
                    maxChars = MaxAvatarIdChars,
                )
                .ifBlank {
                    DefaultCloudRecoveryAvatarId
                },

        interrupting =
            answers.interrupting
                .canonicalizeCloudRecoveryBackupList(),

        timing =
            answers.timing
                .canonicalizeCloudRecoveryBackupList(),

        triggers =
            answers.triggers
                .canonicalizeCloudRecoveryBackupList(),

        weekOneGoal =
            answers.weekOneGoal
                ?.canonicalizeCloudRecoveryBackupText(
                    maxChars = MaxAnswerEntryChars,
                )
                ?.takeIf(String::isNotEmpty),

        dailyRelapseUrgeCount =
            answers.dailyRelapseUrgeCount
                .coerceIn(1, 10),

        activeDayStartMinute =
            answers.activeDayStartMinute
                .coerceIn(MinuteOfDayRange),

        activeDayEndMinute =
            answers.activeDayEndMinute
                .coerceIn(MinuteOfDayRange),

        plannedReleaseWindowMinutes =
            answers.plannedReleaseWindowMinutes
                .asSequence()
                .filter {
                    it in MinuteOfDayRange
                }
                .distinct()
                .take(MaxCollectionEntries)
                .toList(),
    ).also(
        ::requireValidCloudRecoveryOnboardingAnswers,
    )

internal fun requireValidCloudRecoveryOnboardingAnswers(answers: OnboardingAnswers) {
    require(isValidCloudRecoveryOnboardingAnswers(answers)) {
        "Cloud recovery onboarding snapshot is invalid."
    }
}

private fun isValidCloudRecoveryOnboardingAnswers(
    answers: OnboardingAnswers,
): Boolean =
    answers.name.isValidLegacyCompletedName(MaxNameChars) &&
        answers.avatarId.isValidRequiredText(MaxAvatarIdChars) &&
        answers.interrupting.isValidStringList() &&
        answers.timing.isValidStringList() &&
        answers.triggers.isValidStringList() &&
        (
            answers.weekOneGoal == null ||
                answers.weekOneGoal.isValidRequiredText(MaxAnswerEntryChars)
        ) &&
        answers.dailyRelapseUrgeCount in 1..10 &&
        answers.activeDayStartMinute in MinuteOfDayRange &&
        answers.activeDayEndMinute in MinuteOfDayRange &&
        answers.plannedReleaseWindowMinutes.size <= MaxCollectionEntries &&
        answers.plannedReleaseWindowMinutes.all { it in MinuteOfDayRange } &&
        answers.plannedReleaseWindowMinutes.distinct().size ==
        answers.plannedReleaseWindowMinutes.size

private fun String.isValidRequiredText(maxChars: Int): Boolean =
    isNotBlank() && length <= maxChars && StoredListSeparator !in this

private fun String.isValidLegacyCompletedName(
    maxChars: Int,
): Boolean =
    length <= maxChars &&
        StoredListSeparator !in this &&
        (
            isEmpty() ||
                isNotBlank()
        )

private fun List<String>.isValidStringList(): Boolean =
    size <= MaxCollectionEntries &&
        all { it.isValidRequiredText(MaxAnswerEntryChars) } &&
        distinct().size == size

private fun String.canonicalizeCloudRecoveryBackupText(
    maxChars: Int,
): String =
    replace(
        oldChar = StoredListSeparator,
        newChar = ' ',
    )
        .trim()
        .take(maxChars)
        .trim()

private fun List<String>.canonicalizeCloudRecoveryBackupList():
    List<String> =
    asSequence()
        .map {
            it.canonicalizeCloudRecoveryBackupText(
                maxChars = MaxAnswerEntryChars,
            )
        }
        .filter(String::isNotEmpty)
        .distinct()
        .take(MaxCollectionEntries)
        .toList()

private fun JSONObject.keysSet(): Set<String> = buildSet {
    val keys = keys()
    while (keys.hasNext()) add(keys.next())
}

private fun JSONObject.requiredString(key: String): String =
    (opt(key) as? String) ?: throw SnapshotDecodeException

private fun JSONObject.requiredNullableString(key: String): String? {
    if (!has(key)) throw SnapshotDecodeException
    val value = opt(key)
    return when {
        value === JSONObject.NULL -> null
        value is String -> value
        else -> throw SnapshotDecodeException
    }
}

private fun JSONObject.requiredInt(key: String): Int =
    when (val value = opt(key)) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            ?: throw SnapshotDecodeException
        else -> throw SnapshotDecodeException
    }

private fun JSONObject.requiredStringList(key: String): List<String> {
    val array = opt(key) as? JSONArray ?: throw SnapshotDecodeException
    if (array.length() > MaxCollectionEntries) throw SnapshotDecodeException
    return List(array.length()) { index ->
        (array.opt(index) as? String) ?: throw SnapshotDecodeException
    }
}

private fun JSONObject.requiredIntList(key: String): List<Int> {
    val array = opt(key) as? JSONArray ?: throw SnapshotDecodeException
    if (array.length() > MaxCollectionEntries) throw SnapshotDecodeException
    return List(array.length()) { index ->
        when (val value = array.opt(index)) {
            is Int -> value
            is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                ?: throw SnapshotDecodeException
            else -> throw SnapshotDecodeException
        }
    }
}

private data object SnapshotDecodeException : Exception()

private const val MaxNameChars = 100
private const val MaxAvatarIdChars = 64
private const val MaxAnswerEntryChars = 128
private const val MaxCollectionEntries = 64
private const val DefaultCloudRecoveryAvatarId = "wave"
private const val StoredListSeparator = '\u001F'
private val MinuteOfDayRange = 0 until 24 * 60
