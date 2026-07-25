package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

public class CloudRecoveryCrypto(
    private val random: SecureRandom = SecureRandom(),
) {
public fun createNewRecovery(
    ownerUid: String,
    ownerGoogleSubjectHash: String? = null,
    payloadJson: String,
    recoveryPassword: CharArray,
): NewCloudRecovery {
    val normalizedOwnerUid =
        validateOwnerUid(
            ownerUid,
        )

    require(
        payloadJson
            .toByteArray(
                Charsets.UTF_8,
            )
            .size <=
            CloudRecoveryMaxPayloadBytes,
    ) {
        "Cloud recovery payload exceeds maximum allowed size."
    }

    val internalDek =
        randomBytes(
            CloudRecoveryDekBytes,
        )

    var callerOwnedDek:
        ByteArray? =
        null

    return try {
        val wrapped =
            wrapDek(
                dek =
                    internalDek,

                recoveryPassword =
                    recoveryPassword,

                salt =
                    randomBytes(
                        CloudRecoverySaltBytes,
                    ),

                iv =
                    randomBytes(
                        CloudRecoveryIvBytes,
                    ),
            )

        val envelopeBytes =
            encryptPayloadWithExistingDek(
                ownerUid =
                    normalizedOwnerUid,

                ownerGoogleSubjectHash =
                    ownerGoogleSubjectHash,

                payloadJson =
                    payloadJson,

                dek =
                    internalDek,

                existingWrappedKeyMetadata =
                    wrapped,
            )

        callerOwnedDek =
            internalDek.copyOf()

        NewCloudRecovery(
            envelopeBytes =
                envelopeBytes,

            rawDek =
                requireNotNull(
                    callerOwnedDek,
                ),

            wrappedKeyMetadata =
                wrapped,
        )
    } catch (
        error:
            Throwable,
    ) {
        callerOwnedDek?.fill(
            0,
        )

        throw error
    } finally {
        /*
         * The caller receives a separate copy. The internal working DEK must
         * never remain waiting for garbage collection after success.
         */
        internalDek.fill(
            0,
        )
    }
}

    public fun encryptPayloadWithExistingDek(
        ownerUid: String,
        ownerGoogleSubjectHash: String? = null,
        payloadJson: String,
        dek: ByteArray,
        existingWrappedKeyMetadata: WrappedKeyMetadata,
    ): ByteArray {
        val normalizedOwnerUid = validateOwnerUid(ownerUid)
        require(dek.size == CloudRecoveryDekBytes) {
            "Cloud recovery DEK must be 32 bytes."
        }
        require(existingWrappedKeyMetadata.kdfSalt.size == CloudRecoverySaltBytes)
        require(existingWrappedKeyMetadata.wrappedDekIv.size == CloudRecoveryIvBytes)
        require(existingWrappedKeyMetadata.wrappedDekCipherText.size >= CloudRecoveryGcmTagBytes)

        val normalizedSubjectHash = ownerGoogleSubjectHash?.takeIf(::isValidGoogleSubjectHash)
        val plainPayload = buildCloudRecoveryPayloadJson(
            ownerUid = normalizedOwnerUid,
            ownerGoogleSubjectHash = normalizedSubjectHash,
            payloadJson = payloadJson,
            createdAtMillis = System.currentTimeMillis(),
        ).toByteArray(Charsets.UTF_8)
        require(plainPayload.size <= CloudRecoveryMaxPayloadBytes) {
            "Cloud recovery plaintext exceeds maximum allowed size."
        }

        val payloadIv = randomBytes(CloudRecoveryIvBytes)
        val payloadCipherText = aesGcmEncrypt(
            keyBytes = dek,
            iv = payloadIv,
            aad = CloudRecoveryPayloadAad,
            plainText = plainPayload,
        )
        val envelope = CloudRecoveryEnvelope(
            kdfSalt = existingWrappedKeyMetadata.kdfSalt.copyOf(),
            wrappedDekIv = existingWrappedKeyMetadata.wrappedDekIv.copyOf(),
            wrappedDekCipherText = existingWrappedKeyMetadata.wrappedDekCipherText.copyOf(),
            payloadIv = payloadIv,
            payloadCipherText = payloadCipherText,
        )
        return buildCloudRecoveryEnvelopeJson(envelope).toByteArray(Charsets.UTF_8)
    }

    public fun decrypt(
        envelopeBytes: ByteArray,
        recoveryPassword: CharArray,
        expectedOwnerUid: String,
    ): CloudRecoveryDecryptResult {
        val normalizedExpectedOwnerUid = runCatching { validateOwnerUid(expectedOwnerUid) }.getOrElse {
            return CloudRecoveryDecryptResult.Malformed
        }

        return when (val result = decryptForRestore(envelopeBytes, recoveryPassword)) {
            is CloudRecoveryRestoreDecryptResult.Success -> {
                try {
                    if (result.restoredRecovery.recovery.ownerUid != normalizedExpectedOwnerUid) {
                        CloudRecoveryDecryptResult.OwnerMismatch
                    } else {
                        CloudRecoveryDecryptResult.Success(result.restoredRecovery.recovery)
                    }
                } finally {
                    result.restoredRecovery.rawDek.fill(0)
                }
            }

            CloudRecoveryRestoreDecryptResult.CryptoFailure ->
                CloudRecoveryDecryptResult.CryptoFailure
            CloudRecoveryRestoreDecryptResult.Malformed ->
                CloudRecoveryDecryptResult.Malformed
            CloudRecoveryRestoreDecryptResult.UnsupportedVersion ->
                CloudRecoveryDecryptResult.UnsupportedVersion
        }
    }

    public fun decryptForRestore(
        envelopeBytes: ByteArray,
        recoveryPassword: CharArray,
    ): CloudRecoveryRestoreDecryptResult {
        val envelope = when (val parsed = parseCloudRecoveryEnvelope(envelopeBytes)) {
            is CloudRecoveryEnvelopeParseResult.Success -> parsed.envelope
            CloudRecoveryEnvelopeParseResult.Malformed ->
                return CloudRecoveryRestoreDecryptResult.Malformed
            CloudRecoveryEnvelopeParseResult.UnsupportedVersion ->
                return CloudRecoveryRestoreDecryptResult.UnsupportedVersion
        }

        val dek = try {
            val kek = deriveKek(recoveryPassword, envelope.kdfSalt)
            try {
                aesGcmDecrypt(
                    keyBytes = kek.encoded,
                    iv = envelope.wrappedDekIv,
                    aad = CloudRecoveryWrappedDekAad,
                    cipherText = envelope.wrappedDekCipherText,
                    maxPlainBytes = CloudRecoveryDekBytes,
                ).also { unwrappedDek ->
                    if (unwrappedDek.size != CloudRecoveryDekBytes) {
                        unwrappedDek.fill(0)
                        throw IllegalArgumentException("Invalid unwrapped DEK size.")
                    }
                }
            } finally {
                kek.encoded?.fill(0)
            }
        } catch (error: AEADBadTagException) {
            return CloudRecoveryRestoreDecryptResult.CryptoFailure
        } catch (error: Exception) {
            return CloudRecoveryRestoreDecryptResult.CryptoFailure
        }

        var transferDek = false
        return try {
            val payloadBytes = aesGcmDecrypt(
                keyBytes = dek,
                iv = envelope.payloadIv,
                aad = CloudRecoveryPayloadAad,
                cipherText = envelope.payloadCipherText,
                maxPlainBytes = CloudRecoveryMaxPayloadBytes,
            )
            val payload = parseCloudRecoveryPlainPayload(payloadBytes)
                ?: return CloudRecoveryRestoreDecryptResult.Malformed
            val metadata = WrappedKeyMetadata(
                kdfSalt = envelope.kdfSalt.copyOf(),
                wrappedDekIv = envelope.wrappedDekIv.copyOf(),
                wrappedDekCipherText = envelope.wrappedDekCipherText.copyOf(),
            )
            transferDek = true
            CloudRecoveryRestoreDecryptResult.Success(
                RestoredCloudRecovery(
                    recovery = DecryptedCloudRecovery(
                        ownerUid = payload.ownerUid,
                        ownerGoogleSubjectHash = payload.ownerGoogleSubjectHash,
                        schemaVersion = payload.schemaVersion,
                        createdAtMillis = payload.createdAtMillis,
                        payloadJson = payload.payloadJson,
                    ),
                    rawDek = dek,
                    wrappedKeyMetadata = metadata,
                ),
            )
        } catch (error: AEADBadTagException) {
            CloudRecoveryRestoreDecryptResult.CryptoFailure
        } catch (error: Exception) {
            CloudRecoveryRestoreDecryptResult.Malformed
        } finally {
            if (!transferDek) {
                dek.fill(0)
            }
        }
    }
    private fun wrapDek(
        dek: ByteArray,
        recoveryPassword: CharArray,
        salt: ByteArray,
        iv: ByteArray,
    ): WrappedKeyMetadata {
        val kek = deriveKek(recoveryPassword, salt)
        return try {
            WrappedKeyMetadata(
                kdfSalt = salt,
                wrappedDekIv = iv,
                wrappedDekCipherText = aesGcmEncrypt(
                    keyBytes = kek.encoded,
                    iv = iv,
                    aad = CloudRecoveryWrappedDekAad,
                    plainText = dek,
                ),
            )
        } finally {
            kek.encoded?.fill(0)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private companion object {
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
        const val KeyBits = 256

        fun validateOwnerUid(ownerUid: String): String {
            val normalized = ownerUid.trim()
            require(normalized.isNotBlank()) {
                "Cloud recovery owner UID is required."
            }
            require(normalized.length <= CloudRecoveryOwnerUidMaxChars) {
                "Cloud recovery owner UID is too long."
            }
            return normalized
        }

        fun deriveKek(
            password: CharArray,
            salt: ByteArray,
        ): SecretKeySpec {
            require(salt.size == CloudRecoverySaltBytes)
            val spec = PBEKeySpec(
                password,
                salt,
                CloudRecoveryKdfIterations,
                KeyBits,
            )
            return try {
                val keyBytes = SecretKeyFactory.getInstance(CloudRecoveryKdf)
                    .generateSecret(spec)
                    .encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    keyBytes.fill(0)
                }
            } finally {
                spec.clearPassword()
            }
        }

        fun aesGcmEncrypt(
            keyBytes: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            plainText: ByteArray,
        ): ByteArray {
            require(keyBytes.size == CloudRecoveryDekBytes)
            require(iv.size == CloudRecoveryIvBytes)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GcmTagBits, iv),
            )
            cipher.updateAAD(aad)
            return cipher.doFinal(plainText)
        }

        fun aesGcmDecrypt(
            keyBytes: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            cipherText: ByteArray,
            maxPlainBytes: Int,
        ): ByteArray {
            require(keyBytes.size == CloudRecoveryDekBytes)
            require(iv.size == CloudRecoveryIvBytes)
            require(cipherText.size >= CloudRecoveryGcmTagBytes)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GcmTagBits, iv),
            )
            cipher.updateAAD(aad)
            val plainText = cipher.doFinal(cipherText)
            require(plainText.size <= maxPlainBytes)
            return plainText
        }
    }
}