package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.dao.SyncTombstoneDao
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two-way checklist item sync. Items live under their parent note's stable key
 * (note.createdAtMillis) and are remapped to the local note id on insert, since the id differs
 * per device. Per item conflicts prefer Firestore serverUpdatedAt when available
 * and fall back to updatedAtMillis for older documents. No deletes. Run this
 * after the journal note sync so pulled notes already exist locally.
 */
class JournalChecklistCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun sync(dao: JournalNoteDao, tombstoneDao: SyncTombstoneDao, uid: String) {
        if (uid.isBlank()) return
        val notes = dao.getAllNotesForSync()
        for (note in notes) {
            val noteKey = note.createdAtMillis.toString()
            val itemsCollection = firestore
                .collection("users")
                .document(uid)
                .collection("journalNotes")
                .document(noteKey)
                .collection("checklistItems")

            val localItems = dao.getChecklistItems(note.id)
            val checklistTombstones = tombstoneDao
                .getByTypeAndParent(
                    recordType = SyncTombstoneEntity.TYPE_CHECKLIST_ITEM,
                    parentKey = noteKey,
                )
                .associateBy { it.recordKey }
            val activeLocalItems = localItems.filter { item ->
                val tombstone = checklistTombstones[item.createdAtMillis.toString()]
                tombstone == null || tombstone.deletedAtMillis <= item.updatedAtMillis
            }
            val localByKey = activeLocalItems.associateBy { it.createdAtMillis.toString() }

            val remoteSnapshot = suspendCancellableCoroutine { continuation ->
                itemsCollection.get()
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val remoteByKey = HashMap<String, RemoteChecklistItem>()
            for (document in remoteSnapshot.documents) {
                val item = document.toChecklistItem(noteId = note.id) ?: continue
                val remoteConflictUpdatedAtMillis = document.conflictUpdatedAtMillis(
                    fallbackMillis = item.updatedAtMillis,
                )
                val tombstone = checklistTombstones[document.id]
                if (tombstone != null && tombstone.deletedAtMillis > remoteConflictUpdatedAtMillis) {
                    document.deleteRemoteDocument()
                    continue
                }
                remoteByKey[document.id] = RemoteChecklistItem(
                    item = item,
                    conflictUpdatedAtMillis = remoteConflictUpdatedAtMillis,
                )
            }

            val merged = ArrayList<JournalChecklistItemEntity>()
            val toPush = ArrayList<JournalChecklistItemEntity>()
            var localChanged = activeLocalItems.size != localItems.size
            val keys = localByKey.keys + remoteByKey.keys
            for (key in keys) {
                val local = localByKey[key]
                val remote = remoteByKey[key]
                when {
                    local != null && remote != null -> {
                        if (local.updatedAtMillis >= remote.conflictUpdatedAtMillis) {
                            merged.add(local)
                            toPush.add(local)
                        } else {
                            merged.add(remote.item)
                            localChanged = true
                        }
                    }
                    local != null -> {
                        merged.add(local)
                        toPush.add(local)
                    }
                    remote != null -> {
                        merged.add(remote.item)
                        localChanged = true
                    }
                }
            }

            if (localChanged) {
                dao.replaceChecklistItems(note.id, merged)
            }
            for (item in toPush) {
                suspendCancellableCoroutine { continuation ->
                    itemsCollection.document(item.createdAtMillis.toString())
                        .set(item.toFirestoreMap())
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
        }
    }
}

private data class RemoteChecklistItem(
    val item: JournalChecklistItemEntity,
    val conflictUpdatedAtMillis: Long,
)

private fun JournalChecklistItemEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "text" to text,
    "isChecked" to isChecked,
    "sortOrder" to sortOrder,
    "createdAtMillis" to createdAtMillis,
    "updatedAtMillis" to updatedAtMillis,
    "serverUpdatedAt" to FieldValue.serverTimestamp(),
)

private fun DocumentSnapshot.toChecklistItem(noteId: Long): JournalChecklistItemEntity? {
    val createdAtMillis = getLong("createdAtMillis") ?: return null
    return JournalChecklistItemEntity(
        noteId = noteId,
        text = getString("text") ?: return null,
        isChecked = getBoolean("isChecked") ?: false,
        sortOrder = getLong("sortOrder") ?: 0L,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = getLong("updatedAtMillis") ?: createdAtMillis,
    )
}

private fun DocumentSnapshot.conflictUpdatedAtMillis(fallbackMillis: Long): Long {
    return getTimestamp("serverUpdatedAt")?.toDate()?.time ?: fallbackMillis
}

private suspend fun DocumentSnapshot.deleteRemoteDocument() {
    suspendCancellableCoroutine<Unit> { continuation ->
        reference
            .delete()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}
