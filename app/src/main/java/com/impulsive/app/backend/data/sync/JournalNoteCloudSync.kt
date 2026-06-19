package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two-way journal note sync, keyed on createdAtMillis. Notes are mutable, so conflicts
 * resolve by the newer updatedAtMillis. No deletes. Checklist items are handled separately.
 * Not wired to any trigger yet.
 */
class JournalNoteCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun sync(dao: JournalNoteDao, uid: String) {
        if (uid.isBlank()) return
        val collection = firestore
            .collection("users")
            .document(uid)
            .collection("journalNotes")

        val local = dao.getAllNotesForSync()
        val localByKey = local.associateBy { it.createdAtMillis.toString() }

        val remoteSnapshot = suspendCancellableCoroutine { continuation ->
            collection.get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val remoteUpdatedByKey = HashMap<String, Long>()
        for (document in remoteSnapshot.documents) {
            val remote = document.toJournalNoteEntity() ?: continue
            remoteUpdatedByKey[document.id] = remote.updatedAtMillis
            val localNote = localByKey[document.id]
            if (localNote == null) {
                dao.insert(remote)
            } else if (remote.updatedAtMillis > localNote.updatedAtMillis) {
                dao.update(remote.copy(id = localNote.id))
            }
        }

        for (note in local) {
            val key = note.createdAtMillis.toString()
            val remoteUpdated = remoteUpdatedByKey[key]
            if (remoteUpdated == null || note.updatedAtMillis > remoteUpdated) {
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
    "isPinned" to isPinned,
    "category" to category,
    "highlightColor" to highlightColor,
    "sortOrder" to sortOrder,
)

private fun DocumentSnapshot.toJournalNoteEntity(): JournalNoteEntity? {
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
