package com.impulsive.app.backend.data.restore

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
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
    ) = withContext(Dispatchers.IO) {
        val normalizedOwnerUid = ownerUid.trim()

        require(normalizedOwnerUid.isNotBlank()) {
            "Automatic restore bundle requires an authenticated owner UID"
        }

        require(normalizedOwnerUid.length <= MaxOwnerUidChars) {
            "Automatic restore bundle owner UID is too long"
        }

        val payloadJson = buildPayloadJson()
        val bundleJson = buildAutomaticBundleJson(
            ownerUid = normalizedOwnerUid,
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

    suspend fun buildPayloadJson(): String = withContext(Dispatchers.IO) {
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

        JSONObject()
            .put("journalNotes", notesArray)
            .put("checklistItems", checklistArray)
            .put("recoverySessions", sessionsArray)
            .put("blockedDomains", domainsArray)
            .toString()
    }

    companion object {
        private const val MaxOwnerUidChars = 128
        const val SchemaVersion = 1
        const val AutoBundleFormatVersion = 2
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

            val checksumMaterial = automaticBundleChecksumMaterial(
                ownerUid = normalizedOwnerUid,
                payloadJson = payloadJson,
            )

            return JSONObject()
                .put("autoBundleFormatVersion", AutoBundleFormatVersion)
                .put("ownerUid", normalizedOwnerUid)
                .put("schemaVersion", SchemaVersion)
                .put("createdAtMillis", createdAtMillis)
                .put("checksumSha256", sha256Hex(checksumMaterial))
                .put("payloadJson", payloadJson)
                .toString()
        }
        internal fun automaticBundleChecksumMaterial(
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
    }
}
