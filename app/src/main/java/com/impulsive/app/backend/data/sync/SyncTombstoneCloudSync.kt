package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.SyncTombstoneDao
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SyncTombstoneCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun sync(dao: SyncTombstoneDao, uid: String) {
        if (uid.isBlank()) return

        val collection = firestore
            .collection("users")
            .document(uid)
            .collection("syncTombstones")

        val local = dao.getAllForSync()
        val localById = local.associateBy { it.cloudDocumentId() }

        val remoteSnapshot = suspendCancellableCoroutine { continuation ->
            collection.get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val remoteConflictById = HashMap<String, Long>()

        for (document in remoteSnapshot.documents) {
            val remote = document.toTombstoneEntity() ?: continue
            val remoteConflictMillis = document.deletedConflictMillis(remote.deletedAtMillis)
            remoteConflictById[document.id] = remoteConflictMillis

            val localMatch = localById[document.id]
            if (localMatch == null || remoteConflictMillis > localMatch.deletedAtMillis) {
                dao.upsert(
                    remote.copy(
                        id = localMatch?.id ?: 0,
                        deletedAtMillis = remoteConflictMillis,
                    ),
                )
            }
        }

        for (tombstone in local) {
            val remoteConflictMillis = remoteConflictById[tombstone.cloudDocumentId()]
            if (remoteConflictMillis == null || tombstone.deletedAtMillis > remoteConflictMillis) {
                suspendCancellableCoroutine { continuation ->
                    collection.document(tombstone.cloudDocumentId())
                        .set(tombstone.toFirestoreMap())
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
        }
    }
}

internal fun SyncTombstoneEntity.cloudDocumentId(): String {
    val safeParent = parentKey.ifBlank { "root" }.replace("/", "_")
    val safeRecord = recordKey.replace("/", "_")
    return "${recordType}__${safeParent}__${safeRecord}"
}

private fun SyncTombstoneEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "recordType" to recordType,
    "parentKey" to parentKey,
    "recordKey" to recordKey,
    "deletedAtMillis" to deletedAtMillis,
    "serverDeletedAt" to FieldValue.serverTimestamp(),
)

private fun DocumentSnapshot.toTombstoneEntity(): SyncTombstoneEntity? {
    val recordType = getString("recordType") ?: return null
    val recordKey = getString("recordKey") ?: return null
    return SyncTombstoneEntity(
        recordType = recordType,
        parentKey = getString("parentKey") ?: "",
        recordKey = recordKey,
        deletedAtMillis = getLong("deletedAtMillis") ?: return null,
    )
}

private fun DocumentSnapshot.deletedConflictMillis(fallbackMillis: Long): Long {
    return getTimestamp("serverDeletedAt")?.toDate()?.time ?: fallbackMillis
}
