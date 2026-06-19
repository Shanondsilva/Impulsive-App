package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pushes local recovery sessions to Firestore and pulls remote ones that are missing
 * locally, keyed on startedAt plus completedAt so the same session is never duplicated
 * across devices. Merge is union by key with no deletes, which keeps it safe and idempotent.
 *
 * This engine is intentionally not wired to any trigger yet.
 */
class RecoverySessionCloudSync(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

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
