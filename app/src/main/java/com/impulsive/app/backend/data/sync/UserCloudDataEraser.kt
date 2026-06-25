package com.impulsive.app.backend.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Deletes all of a user's Firestore data. Firestore does not remove subcollections when a
 * document is deleted, so this walks the known collections explicitly before removing the
 * user document itself. It must run while the user is still authenticated.
 */
class UserCloudDataEraser(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun eraseAll(uid: String) {
        if (uid.isBlank()) return
        val userDoc = firestore.collection("users").document(uid)

        val notes = userDoc.collection("journalNotes").get().await()
        for (note in notes.documents) {
            val items = note.reference.collection("checklistItems").get().await()
            for (item in items.documents) {
                item.reference.delete().await()
            }
            note.reference.delete().await()
        }

        val sessions = userDoc.collection("recoverySessions").get().await()
        for (session in sessions.documents) {
            session.reference.delete().await()
        }

        userDoc.delete().await()
    }
}
