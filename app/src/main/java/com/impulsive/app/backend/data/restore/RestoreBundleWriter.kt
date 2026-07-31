package com.impulsive.app.backend.data.restore

import android.content.Context
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.ProtectionCoachPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.ProtectionSetupPreferencesDataSource
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOnboardingSnapshotJsonKey
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryOnboardingSnapshotCodec
import com.impulsive.app.backend.data.restore.cloud.cloudRecoveryOnboardingSnapshotForBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Writes the local restore bundle carried by Android Auto Backup.
 *
 * The bundle is a plain JSON file in app-private storage containing the
 * Room data needed to restore user progress on a new device: journal notes,
 * checklist items, recovery sessions, and user-added blocked domains.
 * Feedback notes are excluded by the underlying query. Default blocked
 * domains are excluded because the app seeds them itself.
 *
 * The payload is embedded as an exact string together with a SHA-256
 * checksum over that string, so the importer can verify integrity without
 * depending on JSON key ordering. The file is written atomically through a
 * temp file so an interrupted write never leaves a corrupt bundle.
 *
 * The raw SQLCipher database is never backed up because its passphrase is
 * protected by Android Keystore, and Keystore keys do not transfer to a
 * new device.
 */
class RestoreBundleWriter(context: Context) {

    private val appContext = context.applicationContext

    suspend fun writeBundle(
        ownerUid: String,
        ownerGoogleSubjectHash: String?,
    ) = withContext(Dispatchers.IO) {
        val normalizedOwnerUid = ownerUid.trim()

        require(normalizedOwnerUid.isNotBlank()) {
            "Automatic restore bundle requires an authenticated owner UID"
        }

        require(normalizedOwnerUid.length <= MaxOwnerUidChars) {
            "Automatic restore bundle owner UID is too long"
        }

        val normalizedGoogleSubjectHash = ownerGoogleSubjectHash?.also { hash ->
            require(isValidGoogleSubjectHash(hash)) {
                "Automatic restore bundle owner Google subject hash is invalid"
            }
        }

        val payloadJson = buildPayloadJson(normalizedOwnerUid)
        val bundleJson = buildAutomaticBundleJson(
            ownerUid = normalizedOwnerUid,
            ownerGoogleSubjectHash = normalizedGoogleSubjectHash,
            payloadJson = payloadJson,
            createdAtMillis = System.currentTimeMillis(),
        )

        val directory = File(appContext.filesDir, DirectoryName)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val target = File(directory, FileName)
        val temp = File(directory, TempFileName)
        temp.writeText(bundleJson, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    suspend fun buildPayloadJson(
        verifiedOwnerUid: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(appContext)
        val journalNoteDao = database.journalNoteDao()

        val notesArray = JSONArray()
        val checklistArray = JSONArray()
        for (note in journalNoteDao.getAllNotesForSync()) {
            notesArray.put(
                JSONObject()
                    .put("id", note.id)
                    .put("noteType", note.noteType)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("checklist", note.checklist)
                    .put("sketch", note.sketch)
                    .put("reminderAtMillis", note.reminderAtMillis ?: JSONObject.NULL)
                    .put("source", note.source)
                    .put("createdAtMillis", note.createdAtMillis)
                    .put("updatedAtMillis", note.updatedAtMillis)
                    .put("isPinned", note.isPinned)
                    .put("category", note.category)
                    .put("highlightColor", note.highlightColor ?: JSONObject.NULL)
                    .put("sortOrder", note.sortOrder ?: JSONObject.NULL),
            )
            for (item in journalNoteDao.getChecklistItems(note.id)) {
                checklistArray.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("noteId", item.noteId)
                        .put("text", item.text)
                        .put("isChecked", item.isChecked)
                        .put("sortOrder", item.sortOrder)
                        .put("createdAtMillis", item.createdAtMillis)
                        .put("updatedAtMillis", item.updatedAtMillis),
                )
            }
        }

        val sessionsArray = JSONArray()
        for (session in database.recoverySessionDao().getAllSessions()) {
            sessionsArray.put(
                JSONObject()
                    .put("startedAt", session.startedAt)
                    .put("completedAt", session.completedAt)
                    .put("durationSeconds", session.durationSeconds)
                    .put("urgeBefore", session.urgeBefore ?: JSONObject.NULL)
                    .put("urgeAfter", session.urgeAfter ?: JSONObject.NULL)
                    .put("helped", session.helped ?: JSONObject.NULL)
                    .put("triggerSource", session.triggerSource)
                    .put("recoveryType", session.recoveryType),
            )
        }

        val domainsArray = JSONArray()
        for (domain in database.blockedDomainDao().getAll()) {
            if (!domain.addedByUser) continue
            domainsArray.put(
                JSONObject()
                    .put("domain", domain.domain)
                    .put("category", domain.category)
                    .put("isDefault", domain.isDefault)
                    .put("addedByUser", domain.addedByUser)
                    .put("createdAtMillis", domain.createdAtMillis),
            )
        }

        val payload = JSONObject()
            .put("journalNotes", notesArray)
            .put("checklistItems", checklistArray)
            .put("recoverySessions", sessionsArray)
            .put("blockedDomains", domainsArray)

        val coachPreferences = ProtectionCoachPreferencesDataSource(appContext).state.first()
        val protectionSetup = ProtectionSetupPreferencesDataSource(appContext).state.first()
        payload.put(
            AdaptiveRestorePayloadCodec.JsonKey,
            AdaptiveRestorePayloadCodec.encode(
                plans = database.momentPlanDao().getAllForBackup(),
                preferences = database.adaptivePreferenceDao().get(),
                decisions = database.adaptiveDecisionDao().getAllForBackup(),
                rehearsals = database.momentPlanRehearsalDao().getAllForBackup(),
                pathShiftCycles = database.pathShiftCycleDao().getAllForBackup(),
                protectionCoachSuggestions =
                    database.protectionCoachSuggestionDao().getAllForBackup(),
                protectionMonitorTransitionCompleted =
                    protectionSetup.protectionMonitorTransitionCompleted,
                suggestedSetupReviewed = coachPreferences.suggestedSetupReviewed,
                onboardingColdStartPriorUsed =
                    coachPreferences.onboardingColdStartPriorUsed,
            ),
        )

        val normalizedVerifiedOwnerUid =
            verifiedOwnerUid
                ?.trim()
                ?.takeIf(String::isNotBlank)

        if (normalizedVerifiedOwnerUid != null) {
            val onboardingPreferences =
                OnboardingPreferencesDataSource(
                    appContext,
                )

            val onboardingCompleted =
                onboardingPreferences
                    .isCompleted
                    .first()

            val completedAccountUid =
                onboardingPreferences
                    .completedAccountUid
                    .first()

            if (
                onboardingCompleted &&
                completedAccountUid ==
                normalizedVerifiedOwnerUid
            ) {
                val onboardingSnapshot =
                    requireNotNull(
                        cloudRecoveryOnboardingSnapshotForBackup(
                            verifiedOwnerUid =
                                normalizedVerifiedOwnerUid,

                            isCompleted =
                                onboardingCompleted,

                            completedAccountUid =
                                completedAccountUid,

                            answers =
                                onboardingPreferences
                                    .answers
                                    .first(),
                        ),
                    ) {
                        "Completed onboarding owned by the verified account " +
                            "could not be encoded for cloud recovery."
                    }

                payload.put(
                    CloudRecoveryOnboardingSnapshotJsonKey,
                    CloudRecoveryOnboardingSnapshotCodec.encode(
                        onboardingSnapshot,
                    ),
                )
            }
        }

        payload.toString()
    }

    companion object {
        private const val MaxOwnerUidChars = 128
        const val SchemaVersion = 1
        private const val AutomaticBundleFormatVersionV2 = 2
        private const val AutomaticBundleFormatVersionV3 = 3
        const val AutoBundleFormatVersion =
            AutomaticBundleFormatVersionV3
        const val DirectoryName = "restore"
        const val FileName = "impulsive_restore_bundle_v1.json"
        const val TempFileName = "impulsive_restore_bundle_v1.json.tmp"

        internal fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }

        internal fun buildAutomaticBundleJson(
            ownerUid: String,
            ownerGoogleSubjectHash: String?,
            payloadJson: String,
            createdAtMillis: Long,
        ): String {
            val normalizedOwnerUid = ownerUid.trim()

            require(normalizedOwnerUid.isNotBlank()) {
                "Automatic restore bundle requires an authenticated owner UID"
            }

            require(normalizedOwnerUid.length <= MaxOwnerUidChars) {
                "Automatic restore bundle owner UID is too long"
            }

            val normalizedGoogleSubjectHash = ownerGoogleSubjectHash?.also { hash ->
                require(isValidGoogleSubjectHash(hash)) {
                    "Automatic restore bundle owner Google subject hash is invalid"
                }
            }

            val checksumMaterial = automaticBundleChecksumMaterialV3(
                ownerUid = normalizedOwnerUid,
                ownerGoogleSubjectHash = normalizedGoogleSubjectHash,
                payloadJson = payloadJson,
            )

            return JSONObject()
                .put("autoBundleFormatVersion", AutoBundleFormatVersion)
                .put("ownerUid", normalizedOwnerUid)
                .put(
                    "ownerGoogleSubjectHash",
                    normalizedGoogleSubjectHash ?: JSONObject.NULL,
                )
                .put("schemaVersion", SchemaVersion)
                .put("createdAtMillis", createdAtMillis)
                .put("checksumSha256", sha256Hex(checksumMaterial))
                .put("payloadJson", payloadJson)
                .toString()
        }

        internal fun automaticBundleChecksumMaterialV2(
            ownerUid: String,
            payloadJson: String,
        ): String =
            buildString(
                ownerUid.length +
                    payloadJson.length +
                    1,
            ) {
                append(ownerUid)
                append('\n')
                append(payloadJson)
            }

        internal fun automaticBundleChecksumMaterialV3(
            ownerUid: String,
            ownerGoogleSubjectHash: String?,
            payloadJson: String,
        ): String =
            buildString {
                append(AutomaticBundleFormatVersionV3)
                append('\n')

                append(ownerUid.length)
                append(':')
                append(ownerUid)
                append('\n')

                if (ownerGoogleSubjectHash == null) {
                    append("-1:")
                } else {
                    append(ownerGoogleSubjectHash.length)
                    append(':')
                    append(ownerGoogleSubjectHash)
                }

                append('\n')
                append(payloadJson)
            }
    }
}
