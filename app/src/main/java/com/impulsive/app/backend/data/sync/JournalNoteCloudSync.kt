package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.dao.SyncTombstoneDao
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two-way Personal Notes sync keyed by createdAtMillis.
 *
 * Remote conflicts prefer Firestore serverUpdatedAt when available. Older
 * documents without serverUpdatedAt fall back to updatedAtMillis.
 * Obsolete manual feedback journal rows and old feedback_notification journal
 * copies are excluded locally, rejected during download, and deleted from
 * Firestore.
 *
 * Checklist items are handled separately.
 */
class JournalNoteCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun sync(dao: JournalNoteDao, tombstoneDao: SyncTombstoneDao, uid: String) {
        if (uid.isBlank()) return
        val collection = firestore
            .collection("users")
            .document(uid)
            .collection("journalNotes")

        val local = dao.getAllNotesForSync()
        val tombstones = tombstoneDao
            .getByType(SyncTombstoneEntity.TYPE_JOURNAL_NOTE)
            .associateBy { it.recordKey }
        val activeLocal = local.filter { note ->
            val key = note.createdAtMillis.toString()
            val tombstone = tombstones[key]
            if (tombstone != null && tombstone.deletedAtMillis > note.updatedAtMillis) {
                dao.deleteById(note.id)
                false
            } else {
                true
            }
        }
        val localByKey = activeLocal.associateBy { it.createdAtMillis.toString() }

        val remoteSnapshot = suspendCancellableCoroutine { continuation ->
            collection.get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val remoteConflictUpdatedByKey = HashMap<String, Long>()
        for (document in remoteSnapshot.documents) {
            if (
                document
                    .isObsoleteFeedbackJournalDocument()
            ) {
                document
                    .deleteObsoleteFeedbackJournalDocument()
                continue
            }

            val remote =
                document.toJournalNoteEntity()
                    ?: continue
            val remoteConflictUpdatedAtMillis =
                document.conflictUpdatedAtMillis(fallbackMillis = remote.updatedAtMillis)
            val tombstone = tombstones[document.id]

            if (tombstone != null && tombstone.deletedAtMillis > remoteConflictUpdatedAtMillis) {
                document.deleteNoteAndChecklistSubcollection()
                continue
            }

            remoteConflictUpdatedByKey[document.id] = remoteConflictUpdatedAtMillis

            val localNote = localByKey[document.id]
            if (localNote == null) {
                dao.insert(remote)
            } else if (remoteConflictUpdatedAtMillis > localNote.updatedAtMillis) {
                dao.update(remote.copy(id = localNote.id))
            }
        }

        for (note in activeLocal) {
            val key = note.createdAtMillis.toString()
            val remoteConflictUpdatedAtMillis = remoteConflictUpdatedByKey[key]
            if (remoteConflictUpdatedAtMillis == null || note.updatedAtMillis > remoteConflictUpdatedAtMillis) {
                suspendCancellableCoroutine { continuation ->
                    collection.document(key)
                        .set(note.toFirestoreMap())
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
        }
    }
}

private fun DocumentSnapshot
    .isObsoleteFeedbackJournalDocument():
    Boolean {
    return getString("noteType") == "FEEDBACK" ||
        getString("source") ==
        "feedback_notification"
}

private suspend fun DocumentSnapshot
    .deleteObsoleteFeedbackJournalDocument() {
    suspendCancellableCoroutine<Unit> {
            continuation ->

        reference
            .delete()
            .addOnSuccessListener {
                continuation.resume(Unit)
            }
            .addOnFailureListener { error ->
                continuation
                    .resumeWithException(error)
            }
    }
}

private suspend fun DocumentSnapshot.deleteNoteAndChecklistSubcollection() {
    val checklistSnapshot = suspendCancellableCoroutine { continuation ->
        reference.collection("checklistItems")
            .get()
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    checklistSnapshot.documents.forEach { checklistItem ->
        suspendCancellableCoroutine<Unit> { continuation ->
            checklistItem.reference
                .delete()
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    suspendCancellableCoroutine<Unit> { continuation ->
        reference
            .delete()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}

private fun JournalNoteEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "noteType" to noteType,
    "title" to title,
    "body" to body,
    "checklist" to checklist,
    "sketch" to sketch,
    "reminderAtMillis" to reminderAtMillis,
    "source" to source,
    "createdAtMillis" to createdAtMillis,
    "updatedAtMillis" to updatedAtMillis,
    "serverUpdatedAt" to FieldValue.serverTimestamp(),
    "isPinned" to isPinned,
    "category" to category,
    "highlightColor" to highlightColor,
    "sortOrder" to sortOrder,
)

private fun DocumentSnapshot.toJournalNoteEntity(): JournalNoteEntity? {
    if (
        isObsoleteFeedbackJournalDocument()
    ) {
        return null
    }

    val createdAtMillis = getLong("createdAtMillis") ?: return null
    return JournalNoteEntity(
        noteType = getString("noteType") ?: return null,
        title = getString("title") ?: "",
        body = getString("body") ?: "",
        checklist = getString("checklist") ?: "",
        sketch = getString("sketch") ?: "",
        reminderAtMillis = getLong("reminderAtMillis"),
        source = getString("source") ?: "normal_journal",
        createdAtMillis = createdAtMillis,
        updatedAtMillis = getLong("updatedAtMillis") ?: createdAtMillis,
        isPinned = getBoolean("isPinned") ?: false,
        category = getString("category") ?: "",
        highlightColor = getString("highlightColor"),
        sortOrder = getLong("sortOrder"),
    )
}

private fun DocumentSnapshot.conflictUpdatedAtMillis(fallbackMillis: Long): Long {
    return getTimestamp("serverUpdatedAt")?.toDate()?.time ?: fallbackMillis
}
