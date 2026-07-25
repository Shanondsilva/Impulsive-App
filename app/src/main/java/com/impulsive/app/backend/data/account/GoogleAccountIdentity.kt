package com.impulsive.app.backend.data.account

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.security.MessageDigest

internal data class GoogleAccountIdentity(
    val accountName: String,
    val subjectHash: String,
)

internal fun resolveGoogleAccountIdentity(
    user: FirebaseUser,
): GoogleAccountIdentity? {
    val profile = user.providerData.firstOrNull { provider ->
        provider.providerId == GoogleAuthProvider.PROVIDER_ID
    } ?: return null
    val providerUid = profile.uid.takeIf { it.isNotBlank() } ?: return null
    val accountName = profile.email?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val subjectHash = googleSubjectHash(providerUid) ?: return null
    return GoogleAccountIdentity(accountName, subjectHash)
}

internal fun googleSubjectHash(
    providerSpecificUid: String,
): String? {
    if (providerSpecificUid.isBlank()) return null
    val material = "google.com\u0000$providerSpecificUid".toByteArray(Charsets.UTF_8)
    val hash = MessageDigest.getInstance("SHA-256").digest(material)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    return hash.takeIf(::isValidGoogleSubjectHash)
}

internal fun isValidGoogleSubjectHash(
    value: String,
): Boolean = value.matches(Regex("[0-9a-f]{64}"))