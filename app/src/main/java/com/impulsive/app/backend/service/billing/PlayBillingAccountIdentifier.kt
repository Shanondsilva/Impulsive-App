package com.impulsive.app.backend.service.billing

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val PlayBillingAccountNamespace = "impulsive-play-billing-account-v1:"

internal fun obfuscatedPlayBillingAccountId(firebaseUid: String): String {
    val normalizedUid = firebaseUid.trim()

    require(normalizedUid.isNotEmpty()) {
        "Firebase UID must not be blank."
    }

    val digest = MessageDigest.getInstance("SHA-256").digest(
        (PlayBillingAccountNamespace + normalizedUid).toByteArray(StandardCharsets.UTF_8),
    )
    val hex = CharArray(digest.size * 2)
    val digits = "0123456789abcdef"

    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        hex[index * 2] = digits[value ushr 4]
        hex[index * 2 + 1] = digits[value and 0x0f]
    }

    return String(hex)
}
