package com.impulsive.app.backend.data.restore

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports and imports the manual encrypted .impulsivebackup file.
 *
 * The file is a JSON envelope holding an AES-256-GCM ciphertext of the same
 * payload format used by the Auto Backup restore bundle. The key is derived
 * from a password chosen by the user with PBKDF2WithHmacSHA256 and a random
 * salt, so the file works on any device and any platform. It never depends
 * on Android Keystore. Impulsive never uploads or stores this file, and
 * nobody, including Impulsive, can recover it if the password is lost. The
 * GCM authentication tag makes a wrong password or a tampered file fail
 * cleanly instead of producing corrupt data.
 */
class ManualBackupManager(context: Context) {

    private val appContext = context.applicationContext

    sealed interface ImportResult {
        data object Success : ImportResult
        data object WrongPasswordOrCorrupted : ImportResult
        data object UnsupportedVersion : ImportResult
        data object ExistingDataPresent : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun exportTo(output: OutputStream, password: CharArray) =
        withContext(Dispatchers.IO) {
            val rawPayloadJson =
                RestoreBundleWriter(
                    appContext,
                )
                    .buildPayloadJson()

            val payloadJson =
                RestorePayloadSizePolicy
                    .requireWithinLimit(
                        rawPayloadJson,
                    )

            val random = SecureRandom()
            val salt = ByteArray(SaltBytes).also { bytes -> random.nextBytes(bytes) }
            val iv = ByteArray(IvBytes).also { bytes -> random.nextBytes(bytes) }

            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(GcmTagBits, iv),
            )
            val cipherText = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))

            val envelope = JSONObject()
                .put("format", FormatName)
                .put("formatVersion", FormatVersion)
                .put("schemaVersion", RestoreBundleWriter.SchemaVersion)
                .put("createdAtMillis", System.currentTimeMillis())
                .put("kdf", KdfName)
                .put("kdfIterations", KdfIterations)
                .put("saltBase64", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("ivBase64", Base64.encodeToString(iv, Base64.NO_WRAP))
                .put("cipherTextBase64", Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .toString()

            output.write(envelope.toByteArray(Charsets.UTF_8))
            output.flush()
        }

    suspend fun importFrom(input: InputStream, password: CharArray): ImportResult =
        withContext(Dispatchers.IO) {
            val envelope = try {
                val envelopeBytes = input.readBounded(MaxManualEnvelopeBytes)

                JSONObject(
                    decodeUtf8Strict(envelopeBytes),
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            val format = try {
                requireBoundedString(
                    envelope,
                    "format",
                    64,
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            if (format != FormatName) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }
            val formatVersion = try {
                requireExactInt(
                    envelope,
                    "formatVersion",
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            if (formatVersion != FormatVersion) {
                return@withContext ImportResult.UnsupportedVersion
            }

            val schemaVersion = try {
                requireExactInt(
                    envelope,
                    "schemaVersion",
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            if (schemaVersion != RestoreBundleWriter.SchemaVersion) {
                return@withContext ImportResult.UnsupportedVersion
            }
            val kdf = try {
                requireBoundedString(
                    envelope,
                    "kdf",
                    64,
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            if (kdf != KdfName) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            val kdfIterations = try {
                requireExactInt(
                    envelope,
                    "kdfIterations",
                )
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            if (kdfIterations != KdfIterations) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            val payloadJson = try {
                val salt = decodeCanonicalBase64(
                    value = requireBoundedString(
                        envelope,
                        "saltBase64",
                        64,
                    ),
                    expectedBytes = SaltBytes,
                )
                val iv = decodeCanonicalBase64(
                    value = requireBoundedString(
                        envelope,
                        "ivBase64",
                        64,
                    ),
                    expectedBytes = IvBytes,
                )
                val cipherText = Base64.decode(
                    requireBoundedString(
                        envelope,
                        "cipherTextBase64",
                        MaxCipherTextBase64Chars,
                    ),
                    Base64.NO_WRAP,
                )
                require(cipherText.size >= GcmTagBytes) {
                    "Invalid backup ciphertext size"
                }

                require(cipherText.size <= MaxCipherTextBytes) {
                    "Backup ciphertext exceeds maximum allowed size"
                }
                val cipher = Cipher.getInstance(Transformation)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(password, salt),
                    GCMParameterSpec(GcmTagBits, iv),
                )
                val payloadBytes = cipher.doFinal(cipherText)

                require(payloadBytes.size <= MaxPayloadBytes) {
                    "Restore payload exceeds maximum allowed size"
                }

                decodeUtf8Strict(payloadBytes)
            } catch (error: Exception) {
                return@withContext ImportResult.WrongPasswordOrCorrupted
            }

            val importer = RestoreBundleImporter(appContext)

            try {
                when (
                    importer.importPayload(
                        JSONObject(payloadJson),
                    )
                ) {
                    RestoreBundleImporter.ImportOutcome.Success ->
                        ImportResult.Success

                    RestoreBundleImporter.ImportOutcome.ExistingDataPresent ->
                        ImportResult.ExistingDataPresent
                }
            } catch (error: Exception) {
                ImportResult.Error(
                    error.localizedMessage?.ifBlank { null }
                        ?: "Could not import the backup file.",
                )
            }
        }

    private fun requireExactInt(
        json: JSONObject,
        name: String,
    ): Int {
        val value = json.get(name)

        return when (value) {
            is Int -> value

            is Long -> {
                require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    "$name is outside the supported integer range"
                }

                value.toInt()
            }

            else -> throw IllegalArgumentException(
                "$name must be an integer",
            )
        }
    }

    private fun requireBoundedString(
        json: JSONObject,
        name: String,
        maxLength: Int,
    ): String {
        val value = json.get(name)

        require(value is String) {
            "$name must be a string"
        }

        require(value.length <= maxLength) {
            "$name exceeds maximum allowed length"
        }

        return value
    }

    private fun decodeCanonicalBase64(
        value: String,
        expectedBytes: Int,
    ): ByteArray {
        val decoded = Base64.decode(
            value,
            Base64.NO_WRAP,
        )

        require(decoded.size == expectedBytes) {
            "Invalid decoded Base64 size"
        }

        val canonical = Base64.encodeToString(
            decoded,
            Base64.NO_WRAP,
        )

        require(canonical == value) {
            "Non-canonical Base64 encoding"
        }

        return decoded
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        require(maxBytes > 0)

        val output = ByteArrayOutputStream(
            minOf(DEFAULT_BUFFER_SIZE, maxBytes),
        )
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read == -1) break
            if (read == 0) continue

            if (read > maxBytes - total) {
                throw IllegalArgumentException("Restore file exceeds maximum allowed size")
            }

            output.write(buffer, 0, read)
            total += read
        }

        return output.toByteArray()
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(
            password,
            salt,
            KdfIterations,
            KeyBits,
        )
        val factory = SecretKeyFactory.getInstance(KdfName)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        const val FileExtension = "impulsivebackup"
        const val SuggestedFileName = "impulsive-backup-v1.impulsivebackup"
        private const val MaxManualEnvelopeBytes = 12 * 1024 * 1024
        private const val MaxPayloadBytes =
            RestorePayloadSizePolicy.MaximumPayloadBytes

        private const val FormatName = "impulsive-backup"
        private const val FormatVersion = 1
        private const val KdfName = "PBKDF2WithHmacSHA256"
        private const val KdfIterations = 200000
        private const val KeyBits = 256
        private const val SaltBytes = 16
        private const val IvBytes = 12
        private const val GcmTagBits = 128
        private const val GcmTagBytes = GcmTagBits / 8
        private const val MaxCipherTextBytes = MaxPayloadBytes + GcmTagBytes
        private const val MaxCipherTextBase64Chars =
            ((MaxCipherTextBytes + 2) / 3) * 4
        private const val Transformation = "AES/GCM/NoPadding"
    }
}
