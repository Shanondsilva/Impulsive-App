package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.room.withTransaction
import com.impulsive.app.backend.data.local.preferences.AdaptiveSupportCyclePreferencesDataSource
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.local.entity.requireValid
import com.impulsive.app.backend.session.adaptive.AdaptivePhase4Dependencies
import com.impulsive.app.backend.session.pathshift.PathShiftDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal sealed interface AutoRestoreOwnerProof {
    data class ExactUid(
        val currentUid: String,
    ) : AutoRestoreOwnerProof

    data class ConfirmedSameGoogleIdentity(
        val currentUid: String,
        val currentGoogleSubjectHash: String,
    ) : AutoRestoreOwnerProof
}

sealed interface AutoRestoreResult {
    data object NoBundle : AutoRestoreResult
    data object Restored : AutoRestoreResult
    data object ExistingDataPresent : AutoRestoreResult
    data object InvalidBundle : AutoRestoreResult
    data object OwnerMismatch : AutoRestoreResult
    data object LegacyUnownedBundle : AutoRestoreResult
    data object LegacyOwnerVerificationRequired : AutoRestoreResult

    data class Failed(
        val cause: Throwable?,
    ) : AutoRestoreResult
}

/**
 * Imports the restore bundle written by [RestoreBundleWriter] after a
 * device change or reinstall where Android Auto Backup has restored the
 * app's private files.
 *
 * The import runs only when a bundle file exists and the local database
 * contains no user data yet. That condition makes the import a no-op on
 * every normal launch and prevents duplicate imports without needing a
 * separate completion marker. A corrupt, unreadable, or wrong-version
 * bundle is deleted and ignored so it can never crash startup. The bundle
 * file is deleted after a successful import; the writer recreates it the
 * next time the app goes to the background.
 */
class RestoreBundleImporter(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(
        context.applicationContext,
    ),
    private val recoverAdaptiveObservations: suspend () -> Unit = {
        AdaptivePhase4Dependencies
            .recovery(context.applicationContext)
            .recover()
    },
    private val recoverPathShift: suspend () -> Unit = {
        PathShiftDependencies
            .recovery(context.applicationContext)
            .recover()
    },
) {

    private val appContext = context.applicationContext

    private companion object {
        const val MaxAutoBundleBytes = 32 * 1024 * 1024
        private const val MaxPayloadBytes =
            RestorePayloadSizePolicy.MaximumPayloadBytes
        private const val MaxOwnerUidChars = 128
        private const val MaxNotes = 10_000
        private const val MaxChecklistItems = 50_000
        private const val MaxRecoverySessions = 100_000
        private const val MaxBlockedDomains = 10_000
        private const val MaxNoteTypeChars = 64
        private const val MaxTitleChars = 512
        private const val MaxBodyChars = 100_000
        private const val MaxChecklistChars = 100_000
        private const val MaxSketchChars = 1_000_000
        private const val MaxSourceChars = 128
        private const val MaxCategoryChars = 128
        private const val MaxHighlightColorChars = 32
        private const val MaxChecklistItemTextChars = 4_096
        private const val MaxTriggerSourceChars = 128
        private const val MaxRecoveryTypeChars = 128
        private const val MaxDomainChars = 253
    }

    private data class ParsedAutomaticBundle(
        val formatVersion: Int,
        val ownerUid: String,
        val ownerGoogleSubjectHash: String?,
        val payload: JSONObject,
    )

    private data class ValidatedRestorePayload(
        val notes: List<ValidatedJournalNote>,
        val checklistItems: List<ValidatedChecklistItem>,
        val recoverySessions: List<RecoverySessionEntity>,
        val blockedDomains: List<BlockedDomainEntity>,
        val safeExit: ValidatedSafeExitRestorePayload?,
        val adaptive: ValidatedAdaptiveRestorePayload?,
    )

    private data class ValidatedJournalNote(
        val originalId: Long,
        val entity: JournalNoteEntity,
    )

    private data class ValidatedChecklistItem(
        val originalNoteId: Long,
        val text: String,
        val isChecked: Boolean,
        val sortOrder: Long,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    )

    enum class ImportOutcome {
        Success,
        ExistingDataPresent,
    }

    enum class ImportMode {
        RejectIfExistingData,
        ReplaceRestoreBundleData,
    }

    internal suspend fun importIfNeeded(
        ownerProof: AutoRestoreOwnerProof,
    ): AutoRestoreResult = withContext(Dispatchers.IO) {
        val bundleFile = File(
            File(appContext.filesDir, RestoreBundleWriter.DirectoryName),
            RestoreBundleWriter.FileName,
        )
        if (!bundleFile.exists()) {
            return@withContext AutoRestoreResult.NoBundle
        }

        val parsed = try {
            val bundleBytes = bundleFile.inputStream().use { input ->
                input.readBounded(MaxAutoBundleBytes)
            }
            val bundle = JSONObject(
                decodeUtf8Strict(bundleBytes),
            )

            if (
                !bundle.has("autoBundleFormatVersion") ||
                !bundle.has("ownerUid")
            ) {
                return@withContext AutoRestoreResult.LegacyUnownedBundle
            }

            parseAutomaticBundle(bundle)
        } catch (error: IllegalArgumentException) {
            bundleFile.delete()
            return@withContext AutoRestoreResult.InvalidBundle
        } catch (error: org.json.JSONException) {
            bundleFile.delete()
            return@withContext AutoRestoreResult.InvalidBundle
        }

        when (val proofResult = validateOwnerProof(ownerProof, parsed)) {
            null -> Unit
            else -> return@withContext proofResult
        }

        try {
            when (importPayload(parsed.payload)) {
                ImportOutcome.Success -> {
                    bundleFile.delete()
                    AutoRestoreResult.Restored
                }

                ImportOutcome.ExistingDataPresent -> {
                    AutoRestoreResult.ExistingDataPresent
                }
            }
        } catch (error: IllegalArgumentException) {
            bundleFile.delete()
            AutoRestoreResult.InvalidBundle
        } catch (error: org.json.JSONException) {
            bundleFile.delete()
            AutoRestoreResult.InvalidBundle
        } catch (error: Exception) {
            AutoRestoreResult.Failed(error)
        }
    }

    private fun parseAutomaticBundle(
        bundle: JSONObject,
    ): ParsedAutomaticBundle {
        val autoBundleFormatVersion = strictRequiredInt(
            bundle,
            "autoBundleFormatVersion",
        )
        val ownerUid = requireBoundedString(
            bundle,
            "ownerUid",
            MaxOwnerUidChars,
        ).trim()

        require(ownerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        val schemaVersion = strictRequiredInt(
            bundle,
            "schemaVersion",
        )
        val payloadJson = requireBoundedString(
            bundle,
            "payloadJson",
            MaxPayloadBytes,
        )
        val checksum = requireBoundedString(
            bundle,
            "checksumSha256",
            64,
        )

        require(checksum.length == 64) {
            "checksumSha256 must be exactly 64 characters"
        }

        require(checksum.all { character ->
            character in '0'..'9' ||
                character in 'a'..'f'
        }) {
            "checksumSha256 must be lowercase hexadecimal"
        }

        require(schemaVersion == RestoreBundleWriter.SchemaVersion) {
            "Unsupported restore bundle schema version"
        }

        val ownerGoogleSubjectHash = when (autoBundleFormatVersion) {
            2 -> null
            3 -> parseOwnerGoogleSubjectHash(bundle)
            else -> throw IllegalArgumentException(
                "Unsupported automatic restore bundle version",
            )
        }

        val checksumMaterial = when (autoBundleFormatVersion) {
            2 -> RestoreBundleWriter.automaticBundleChecksumMaterialV2(
                ownerUid = ownerUid,
                payloadJson = payloadJson,
            )

            3 -> RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = ownerUid,
                ownerGoogleSubjectHash = ownerGoogleSubjectHash,
                payloadJson = payloadJson,
            )

            else -> error("Unsupported format checked above")
        }

        require(sha256Hex(checksumMaterial) == checksum) {
            "Automatic restore bundle checksum mismatch"
        }

        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        require(payloadBytes.size <= MaxPayloadBytes) {
            "payloadJson exceeds maximum allowed size"
        }

        return ParsedAutomaticBundle(
            formatVersion = autoBundleFormatVersion,
            ownerUid = ownerUid,
            ownerGoogleSubjectHash = ownerGoogleSubjectHash,
            payload = JSONObject(payloadJson),
        )
    }

    private fun parseOwnerGoogleSubjectHash(
        bundle: JSONObject,
    ): String? {
        require(bundle.has("ownerGoogleSubjectHash")) {
            "ownerGoogleSubjectHash is required for version 3 bundles"
        }

        if (bundle.isNull("ownerGoogleSubjectHash")) {
            return null
        }

        val value = bundle.get("ownerGoogleSubjectHash")
        require(value is String) {
            "ownerGoogleSubjectHash must be a string or null"
        }
        require(isValidGoogleSubjectHash(value)) {
            "ownerGoogleSubjectHash is invalid"
        }
        return value
    }

    private fun validateOwnerProof(
        ownerProof: AutoRestoreOwnerProof,
        parsed: ParsedAutomaticBundle,
    ): AutoRestoreResult? = when (ownerProof) {
        is AutoRestoreOwnerProof.ExactUid -> {
            val normalizedCurrentUid = ownerProof.currentUid.trim()
            if (
                normalizedCurrentUid.isBlank() ||
                normalizedCurrentUid.length > MaxOwnerUidChars ||
                parsed.ownerUid != normalizedCurrentUid
            ) {
                AutoRestoreResult.OwnerMismatch
            } else {
                null
            }
        }

        is AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity -> {
            val normalizedCurrentUid = ownerProof.currentUid.trim()
            if (
                normalizedCurrentUid.isBlank() ||
                normalizedCurrentUid.length > MaxOwnerUidChars ||
                !isValidGoogleSubjectHash(ownerProof.currentGoogleSubjectHash)
            ) {
                AutoRestoreResult.OwnerMismatch
            } else if (parsed.formatVersion != 3) {
                AutoRestoreResult.LegacyOwnerVerificationRequired
            } else {
                val parsedGoogleSubjectHash = parsed.ownerGoogleSubjectHash
                when {
                    parsedGoogleSubjectHash == null ->
                        AutoRestoreResult.LegacyOwnerVerificationRequired
                    !isValidGoogleSubjectHash(parsedGoogleSubjectHash) ->
                        AutoRestoreResult.LegacyOwnerVerificationRequired
                    parsedGoogleSubjectHash != ownerProof.currentGoogleSubjectHash ->
                        AutoRestoreResult.OwnerMismatch
                    else -> null
                }
            }
        }
    }

    suspend fun hasExistingUserData(): Boolean = withContext(Dispatchers.IO) {
        hasExistingUserData(database)
    }

    private suspend fun hasExistingUserData(
        database: AppDatabase,
    ): Boolean =
        database.journalNoteDao()
            .getAllNotesForSync()
            .isNotEmpty() ||
            database.recoverySessionDao()
                .getAllSessions()
                .isNotEmpty() ||
            database.blockedDomainDao()
                .getAll()
                .any { domain ->
                    domain.addedByUser
                } ||
            database.momentPlanDao().count() > 0 ||
            database.adaptiveDecisionDao().count() > 0 ||
            database.momentPlanRehearsalDao().getAllForBackup().isNotEmpty()
            || database.pathShiftCycleDao().getAllForBackup().isNotEmpty() ||
            database.safeExitDao().count() > 0

    private fun validateJournalNotes(
        notes: JSONArray,
    ): List<ValidatedJournalNote> {
        require(notes.length() <= MaxNotes) {
            "Too many journal notes"
        }

        val seenNoteIds = HashSet<Long>()
        val validatedNotes = ArrayList<ValidatedJournalNote>(
            notes.length(),
        )

        for (index in 0 until notes.length()) {
            val note = notes.getJSONObject(index)

            val originalNoteId = strictRequiredLong(
                note,
                "id",
            )

            require(seenNoteIds.add(originalNoteId)) {
                "Duplicate journal note id"
            }

            val createdAtMillisValue = strictRequiredLong(
                note,
                "createdAtMillis",
            )

            val updatedAtMillisValue = strictRequiredLong(
                note,
                "updatedAtMillis",
            )

            require(updatedAtMillisValue >= createdAtMillisValue) {
                "updatedAtMillis must not be earlier than createdAtMillis"
            }

            val entity = JournalNoteEntity(
                noteType = requireBoundedString(
                    note,
                    "noteType",
                    MaxNoteTypeChars,
                ),
                title = requireBoundedString(
                    note,
                    "title",
                    MaxTitleChars,
                ),
                body = boundedOptionalString(
                    note,
                    "body",
                    "",
                    MaxBodyChars,
                ),
                checklist = boundedOptionalString(
                    note,
                    "checklist",
                    "",
                    MaxChecklistChars,
                ),
                sketch = boundedOptionalString(
                    note,
                    "sketch",
                    "",
                    MaxSketchChars,
                ),
                reminderAtMillis = strictNullableLong(
                    note,
                    "reminderAtMillis",
                ),
                source = boundedOptionalString(
                    note,
                    "source",
                    "normal_journal",
                    MaxSourceChars,
                ),
                createdAtMillis = createdAtMillisValue,
                updatedAtMillis = updatedAtMillisValue,
                isPinned = strictOptionalBoolean(
                    note,
                    "isPinned",
                    false,
                ),
                category = boundedOptionalString(
                    note,
                    "category",
                    "",
                    MaxCategoryChars,
                ),
                highlightColor = boundedNullableString(
                    note,
                    "highlightColor",
                    MaxHighlightColorChars,
                ),
                sortOrder = strictNullableLong(
                    note,
                    "sortOrder",
                ),
            )

            validatedNotes.add(
                ValidatedJournalNote(
                    originalId = originalNoteId,
                    entity = entity,
                ),
            )
        }

        return validatedNotes
    }

    private fun validateChecklistItems(
        checklistItems: JSONArray,
        validNoteIds: Set<Long>,
    ): List<ValidatedChecklistItem> {
        require(checklistItems.length() <= MaxChecklistItems) {
            "Too many checklist items"
        }

        val validatedItems = ArrayList<ValidatedChecklistItem>(
            checklistItems.length(),
        )

        for (index in 0 until checklistItems.length()) {
            val item = checklistItems.getJSONObject(index)

            val originalNoteId = strictRequiredLong(
                item,
                "noteId",
            )

            require(originalNoteId in validNoteIds) {
                "Checklist item references unknown journal note"
            }

            val createdAtMillisValue = strictRequiredLong(
                item,
                "createdAtMillis",
            )

            val updatedAtMillisValue = strictRequiredLong(
                item,
                "updatedAtMillis",
            )

            require(updatedAtMillisValue >= createdAtMillisValue) {
                "updatedAtMillis must not be earlier than createdAtMillis"
            }

            validatedItems.add(
                ValidatedChecklistItem(
                    originalNoteId = originalNoteId,
                    text = requireBoundedString(
                        item,
                        "text",
                        MaxChecklistItemTextChars,
                    ),
                    isChecked = strictOptionalBoolean(
                        item,
                        "isChecked",
                        false,
                    ),
                    sortOrder = strictRequiredLong(
                        item,
                        "sortOrder",
                    ),
                    createdAtMillis = createdAtMillisValue,
                    updatedAtMillis = updatedAtMillisValue,
                ),
            )
        }

        return validatedItems
    }

    private fun validateRecoverySessions(
        recoverySessions: JSONArray,
    ): List<RecoverySessionEntity> {
        require(recoverySessions.length() <= MaxRecoverySessions) {
            "Too many recovery sessions"
        }

        val validatedSessions = ArrayList<RecoverySessionEntity>(
            recoverySessions.length(),
        )

        for (index in 0 until recoverySessions.length()) {
            val session = recoverySessions.getJSONObject(index)

            val startedAtValue = strictRequiredLong(
                session,
                "startedAt",
            )

            val completedAtValue = strictRequiredLong(
                session,
                "completedAt",
            )

            require(completedAtValue >= startedAtValue) {
                "completedAt must not be earlier than startedAt"
            }

            val durationSecondsValue = strictOptionalInt(
                session,
                "durationSeconds",
                90,
            )

            require(durationSecondsValue >= 0) {
                "durationSeconds must not be negative"
            }

            val urgeBeforeValue = strictNullableInt(
                session,
                "urgeBefore",
            )

            val urgeAfterValue = strictNullableInt(
                session,
                "urgeAfter",
            )

            require(urgeBeforeValue == null || urgeBeforeValue in 0..10) {
                "urgeBefore must be between 0 and 10"
            }

            require(urgeAfterValue == null || urgeAfterValue in 0..10) {
                "urgeAfter must be between 0 and 10"
            }

            validatedSessions.add(
                RecoverySessionEntity(
                    startedAt = startedAtValue,
                    completedAt = completedAtValue,
                    durationSeconds = durationSecondsValue,
                    urgeBefore = urgeBeforeValue,
                    urgeAfter = urgeAfterValue,
                    helped = strictNullableBoolean(
                        session,
                        "helped",
                    ),
                    triggerSource = boundedOptionalString(
                        session,
                        "triggerSource",
                        "manual_demo",
                        MaxTriggerSourceChars,
                    ),
                    recoveryType = boundedOptionalString(
                        session,
                        "recoveryType",
                        "psychological_90_second_reset",
                        MaxRecoveryTypeChars,
                    ),
                ),
            )
        }

        return validatedSessions
    }

    private fun validateBlockedDomains(
        blockedDomains: JSONArray,
    ): List<BlockedDomainEntity> {
        require(blockedDomains.length() <= MaxBlockedDomains) {
            "Too many blocked domains"
        }

        val seenDomains = HashSet<String>()
        val validatedDomains = ArrayList<BlockedDomainEntity>(
            blockedDomains.length(),
        )

        for (index in 0 until blockedDomains.length()) {
            val domain = blockedDomains.getJSONObject(index)

            val domainValue = requireBoundedString(
                domain,
                "domain",
                MaxDomainChars,
            )

            require(seenDomains.add(domainValue)) {
                "Duplicate blocked domain"
            }

            validatedDomains.add(
                BlockedDomainEntity(
                    domain = domainValue,
                    category = requireBoundedString(
                        domain,
                        "category",
                        MaxCategoryChars,
                    ),
                    isDefault = strictOptionalBoolean(
                        domain,
                        "isDefault",
                        false,
                    ),
                    addedByUser = strictOptionalBoolean(
                        domain,
                        "addedByUser",
                        true,
                    ),
                    createdAtMillis = strictRequiredLong(
                        domain,
                        "createdAtMillis",
                    ),
                ),
            )
        }

        return validatedDomains
    }

    private fun validatePayload(
        parsed: JSONObject,
    ): ValidatedRestorePayload {
        val notesJson = parsed.getJSONArray(
            "journalNotes",
        )

        val checklistItemsJson = parsed.getJSONArray(
            "checklistItems",
        )

        val recoverySessionsJson = parsed.getJSONArray(
            "recoverySessions",
        )

        val blockedDomainsJson = parsed.getJSONArray(
            "blockedDomains",
        )

        val validatedNotes = validateJournalNotes(
            notesJson,
        )

        val validNoteIds = validatedNotes
            .mapTo(HashSet()) { note ->
                note.originalId
            }

        val validatedChecklistItems = validateChecklistItems(
            checklistItemsJson,
            validNoteIds,
        )

        val validatedRecoverySessions = validateRecoverySessions(
            recoverySessionsJson,
        )

        val validatedBlockedDomains = validateBlockedDomains(
            blockedDomainsJson,
        )

        val validatedSafeExit =
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    parsed,
                )

        return ValidatedRestorePayload(
            notes = validatedNotes,
            checklistItems = validatedChecklistItems,
            recoverySessions = validatedRecoverySessions,
            blockedDomains = validatedBlockedDomains,
            safeExit = validatedSafeExit,
            adaptive = AdaptiveRestorePayloadCodec.decodeIfPresent(
                payload = parsed,
                restoredAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun importPayload(
        parsed: JSONObject,
        mode: ImportMode = ImportMode.RejectIfExistingData,
        cloudRestoreReceipt: CloudRestoreReceiptEntity? = null,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        com.impulsive.app.backend.session.adaptive.AdaptiveRetentionRuntimeState
            .beginRestore()
        try {
            cloudRestoreReceipt?.requireValid()
            val validatedPayload = validatePayload(parsed)

        val outcome = database.withTransaction {
            val journalNoteDao = database.journalNoteDao()
            val recoverySessionDao = database.recoverySessionDao()
            val blockedDomainDao = database.blockedDomainDao()
            val safeExitDao =
                database.safeExitDao()

            if (hasExistingUserData(database)) {
                if (mode == ImportMode.RejectIfExistingData) {
                    return@withTransaction ImportOutcome.ExistingDataPresent
                }

                /*
                 * JournalChecklistItemEntity has an ON DELETE CASCADE foreign key.
                 * Deleting only RestoreBundle-managed user notes therefore removes only
                 * their checklist children while preserving checklist items attached to
                 * feedback/system notes that RestoreBundle intentionally excludes.
                 */
                journalNoteDao.clearAllUserNotesForRestore()
                recoverySessionDao.clearAllForRestore()
                blockedDomainDao.clearAllUserDomainsForRestore()
                validatedPayload
                    .safeExit
                    ?.let {
                        safeExitDao
                            .clearAllForRestore()
                    }
            }

            validatedPayload.adaptive?.let {
                database.adaptiveDecisionDao().clearLearningHistory()
                database.momentPlanRehearsalDao().clearAll()
                database.momentPlanDao().clearAll()
                database.adaptivePreferenceDao().clearAll()
                database.pathShiftCycleDao().clearAll()
                database.protectionCoachSuggestionDao().clearAllCoachData()
            }

            val noteIdMap = HashMap<Long, Long>()

            for (validatedNote in validatedPayload.notes) {
                val newId = journalNoteDao.insert(
                    validatedNote.entity,
                )

                noteIdMap[validatedNote.originalId] = newId
            }

            val itemsToInsert =
                validatedPayload.checklistItems.map { validatedItem ->
                    val newNoteId = requireNotNull(
                        noteIdMap[validatedItem.originalNoteId],
                    ) {
                        "Validated checklist item references missing restored note"
                    }

                    JournalChecklistItemEntity(
                        noteId = newNoteId,
                        text = validatedItem.text,
                        isChecked = validatedItem.isChecked,
                        sortOrder = validatedItem.sortOrder,
                        createdAtMillis = validatedItem.createdAtMillis,
                        updatedAtMillis = validatedItem.updatedAtMillis,
                    )
                }

            if (itemsToInsert.isNotEmpty()) {
                journalNoteDao.insertChecklistItemsForRestore(
                    itemsToInsert,
                )
            }

            for (session in validatedPayload.recoverySessions) {
                recoverySessionDao.insertSession(
                    session,
                )
            }

            validatedPayload
                .safeExit
                ?.records
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?.let { records ->
                    safeExitDao
                        .insertForRestore(
                            records,
                        )
                }

            for (domain in validatedPayload.blockedDomains) {
                blockedDomainDao.insertForRestore(
                    domain,
                )
            }

            validatedPayload.adaptive?.let { adaptive ->
                adaptive.plans.forEach { plan ->
                    database.momentPlanDao().insertForRestore(plan)
                }
                database.adaptivePreferenceDao().insertForRestore(
                    adaptive.preferences,
                )
                adaptive.decisions.forEach { decision ->
                    database.adaptiveDecisionDao().insertForRestore(decision)
                }
                adaptive.rehearsals.forEach { rehearsal ->
                    database.momentPlanRehearsalDao().insertForRestore(rehearsal)
                }
                adaptive.pathShiftCycles.forEach { cycle ->
                    database.pathShiftCycleDao().insertForRestore(cycle)
                }
                adaptive.protectionCoachSuggestions.forEach { suggestion ->
                    database.protectionCoachSuggestionDao().insertForRestore(suggestion)
                }
            }

            cloudRestoreReceipt?.let { receipt ->
                database.cloudRestoreReceiptDao().insert(receipt)
            }

            ImportOutcome.Success
        }

        if (outcome == ImportOutcome.Success) {
            AdaptiveSupportCyclePreferencesDataSource
                .getInstance(appContext)
                .clearAll()
        }

        if (
            outcome == ImportOutcome.Success &&
            validatedPayload.adaptive != null
        ) {
            recoverAdaptiveObservations()
            recoverPathShift()
            val adaptive = validatedPayload.adaptive
            com.impulsive.app.backend.data.local.preferences
                .ProtectionSetupPreferencesDataSource(appContext)
                .setProtectionMonitorTransitionCompleted(
                    adaptive.protectionMonitorTransitionCompleted,
                )
            val coachPreferences =
                com.impulsive.app.backend.data.local.preferences
                    .ProtectionCoachPreferencesDataSource(appContext)
            if (adaptive.suggestedSetupReviewed) {
                coachPreferences.markSuggestedSetupReviewed()
            }
            if (adaptive.onboardingColdStartPriorUsed) {
                coachPreferences.markOnboardingColdStartPriorUsed()
            }
        }
            if (
                outcome == ImportOutcome.Success &&
                validatedPayload.adaptive != null
            ) {
                com.impulsive.app.backend.session.adaptive
                    .AdaptiveHistoryRetentionScheduler
                    .requestCleanup(appContext)
            }
            outcome
        } finally {
            com.impulsive.app.backend.session.adaptive.AdaptiveRetentionRuntimeState
                .endRestore()
        }
    }

    private fun requireBoundedString(
        json: JSONObject,
        name: String,
        maxLength: Int,
    ): String {
        val value = json.get(name)

        require(value is String) {
            "$name must be a string"
        }

        require(value.length <= maxLength) {
            "$name exceeds maximum allowed length"
        }

        return value
    }

    private fun boundedOptionalString(
        json: JSONObject,
        name: String,
        defaultValue: String,
        maxLength: Int,
    ): String {
        if (!json.has(name) || json.isNull(name)) {
            return defaultValue
        }

        val value = json.get(name)

        require(value is String) {
            "$name must be a string"
        }

        require(value.length <= maxLength) {
            "$name exceeds maximum allowed length"
        }

        return value
    }

    private fun boundedNullableString(
        json: JSONObject,
        name: String,
        maxLength: Int,
    ): String? {
        if (!json.has(name) || json.isNull(name)) {
            return null
        }

        val value = json.get(name)

        require(value is String) {
            "$name must be a string"
        }

        require(value.length <= maxLength) {
            "$name exceeds maximum allowed length"
        }

        return value
    }

    private fun strictOptionalBoolean(
        json: JSONObject,
        name: String,
        defaultValue: Boolean,
    ): Boolean {
        if (!json.has(name) || json.isNull(name)) {
            return defaultValue
        }

        val value = json.get(name)

        require(value is Boolean) {
            "$name must be a boolean"
        }

        return value
    }

    private fun strictNullableBoolean(
        json: JSONObject,
        name: String,
    ): Boolean? {
        if (!json.has(name) || json.isNull(name)) {
            return null
        }

        val value = json.get(name)

        require(value is Boolean) {
            "$name must be a boolean"
        }

        return value
    }

    private fun strictRequiredLong(
        json: JSONObject,
        name: String,
    ): Long {
        val value = json.get(name)

        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException(
                "$name must be an integer",
            )
        }
    }

    private fun strictNullableLong(
        json: JSONObject,
        name: String,
    ): Long? {
        if (!json.has(name) || json.isNull(name)) {
            return null
        }

        return strictRequiredLong(json, name)
    }

    private fun strictRequiredInt(
        json: JSONObject,
        name: String,
    ): Int {
        val value = json.get(name)

        return when (value) {
            is Int -> value

            is Long -> {
                require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    "$name is outside the supported integer range"
                }

                value.toInt()
            }

            else -> throw IllegalArgumentException(
                "$name must be an integer",
            )
        }
    }

    private fun strictOptionalInt(
        json: JSONObject,
        name: String,
        defaultValue: Int,
    ): Int {
        if (!json.has(name) || json.isNull(name)) {
            return defaultValue
        }

        return strictRequiredInt(json, name)
    }

    private fun strictNullableInt(
        json: JSONObject,
        name: String,
    ): Int? {
        if (!json.has(name) || json.isNull(name)) {
            return null
        }

        return strictRequiredInt(json, name)
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        require(maxBytes > 0)

        val output = ByteArrayOutputStream(
            minOf(DEFAULT_BUFFER_SIZE, maxBytes),
        )
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read == -1) break
            if (read == 0) continue

            if (read > maxBytes - total) {
                throw IllegalArgumentException(
                    "Restore bundle exceeds maximum allowed size",
                )
            }

            output.write(buffer, 0, read)
            total += read
        }

        return output.toByteArray()
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
