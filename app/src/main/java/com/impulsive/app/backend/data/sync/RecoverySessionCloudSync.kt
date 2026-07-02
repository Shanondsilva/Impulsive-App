package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.dao.SyncTombstoneDao
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pushes local recovery sessions to Firestore and pulls remote ones that are missing
 * locally, keyed on startedAt plus completedAt so the same session is never duplicated
 * across devices. Tombstones suppress deleted records while the normal merge remains
 * union by key for active records.
 *
 * Recovery sessions also write serverUpdatedAt so future server-side conflict logic has
 * trustworthy server time available without changing the current union-by-key behavior.
 *
 * This engine is intentionally not wired to any trigger yet.
 */
class RecoverySessionCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun sync(
        dao: RecoverySessionDao,
        tombstoneDao: SyncTombstoneDao,
        uid: String,
    ) {
        if (uid.isBlank()) return
        val collection = firestore
            .collection("users")
            .document(uid)
            .collection("recoverySessions")

        val tombstones = tombstoneDao
            .getByType(SyncTombstoneEntity.TYPE_RECOVERY_SESSION)
            .associateBy { it.recordKey }
        val local = dao.getAllSessions()
        val activeLocal = local.filter { session ->
            val key = session.contentKey()
            val tombstone = tombstones[key]
            if (tombstone != null && tombstone.deletedAtMillis > session.completedAt) {
                dao.deleteByContentKey(
                    startedAt = session.startedAt,
                    completedAt = session.completedAt,
                )
                false
            } else {
                true
            }
        }
        val localKeys = activeLocal.map { it.contentKey() }.toHashSet()

        val remoteSnapshot = suspendCancellableCoroutine { continuation ->
            collection.get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val remoteKeys = HashSet<String>()
        for (document in remoteSnapshot.documents) {
            val remote = document.toRecoverySessionEntity() ?: continue
            val remoteConflictUpdatedAtMillis = document.conflictUpdatedAtMillis(
                fallbackMillis = remote.completedAt,
            )
            val tombstone = tombstones[document.id]
            if (tombstone != null && tombstone.deletedAtMillis > remoteConflictUpdatedAtMillis) {
                document.deleteRemoteDocument()
                continue
            }

            remoteKeys.add(document.id)
            if (!localKeys.contains(document.id)) {
                dao.insertSession(remote)
            }
        }

        for (session in activeLocal) {
            val key = session.contentKey()
            if (!remoteKeys.contains(key)) {
                suspendCancellableCoroutine { continuation ->
                    collection.document(key)
                        .set(session.toFirestoreMap())
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
        }
    }

    suspend fun sync(dao: RecoverySessionDao, uid: String) {
        if (uid.isBlank()) return
        val collection = firestore
            .collection("users")
            .document(uid)
            .collection("recoverySessions")

        val local = dao.getAllSessions()
        val localKeys = local.map { it.contentKey() }.toHashSet()

        val remoteSnapshot = suspendCancellableCoroutine { continuation ->
            collection.get()
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val remoteKeys = HashSet<String>()
        for (document in remoteSnapshot.documents) {
            remoteKeys.add(document.id)
            if (!localKeys.contains(document.id)) {
                document.toRecoverySessionEntity()?.let { dao.insertSession(it) }
            }
        }

        for (session in local) {
            val key = session.contentKey()
            if (!remoteKeys.contains(key)) {
                suspendCancellableCoroutine { continuation ->
                    collection.document(key)
                        .set(session.toFirestoreMap())
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
        }
    }
}

private fun RecoverySessionEntity.contentKey(): String = "${startedAt}_${completedAt}"

private fun RecoverySessionEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "startedAt" to startedAt,
    "completedAt" to completedAt,
    "durationSeconds" to durationSeconds,
    "urgeBefore" to urgeBefore,
    "urgeAfter" to urgeAfter,
    "helped" to helped,
    "triggerSource" to triggerSource,
    "recoveryType" to recoveryType,
    "serverUpdatedAt" to FieldValue.serverTimestamp(),
)

private fun com.google.firebase.firestore.DocumentSnapshot.toRecoverySessionEntity(): RecoverySessionEntity? {
    val startedAt = getLong("startedAt") ?: return null
    val completedAt = getLong("completedAt") ?: return null
    return RecoverySessionEntity(
        startedAt = startedAt,
        completedAt = completedAt,
        durationSeconds = (getLong("durationSeconds") ?: 90L).toInt(),
        urgeBefore = getLong("urgeBefore")?.toInt(),
        urgeAfter = getLong("urgeAfter")?.toInt(),
        helped = getBoolean("helped"),
        triggerSource = getString("triggerSource") ?: "manual_demo",
        recoveryType = getString("recoveryType") ?: "psychological_90_second_reset",
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.conflictUpdatedAtMillis(fallbackMillis: Long): Long {
    return getTimestamp("serverUpdatedAt")?.toDate()?.time ?: fallbackMillis
}

private suspend fun com.google.firebase.firestore.DocumentSnapshot.deleteRemoteDocument() {
    suspendCancellableCoroutine<Unit> { continuation ->
        reference
            .delete()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}
