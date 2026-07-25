package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

public class CloudRecoveryLocalKeyStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cacheFile: File
        get() = File(File(appContext.noBackupFilesDir, DirectoryName), FileName)

    public fun store(
    dek: ByteArray,
) {
    require(
        dek.size ==
            CloudRecoveryDekBytes,
    ) {
        "Cloud recovery DEK must be 32 bytes."
    }

    val key =
        getOrCreateWrappingKey()

    val cipher =
        Cipher.getInstance(
            Transformation,
        )

    /*
     * This Android Keystore key requires randomized encryption.
     *
     * Do not provide an IV while encrypting. Android Keystore must generate
     * the AES-GCM IV itself. Supplying a caller-generated IV causes:
     *
     * InvalidAlgorithmParameterException:
     * Caller-provided IV not permitted
     */
    cipher.init(
        Cipher.ENCRYPT_MODE,
        key,
    )

    val iv =
        cipher.iv
            ?.copyOf()
            ?: throw IllegalStateException(
                "Android Keystore did not generate a cloud recovery IV.",
            )

    require(
        iv.size ==
            CloudRecoveryIvBytes,
    ) {
        "Android Keystore generated an unexpected cloud recovery IV size."
    }

    cipher.updateAAD(
        LocalDekAad,
    )

    val cipherText =
        cipher.doFinal(
            dek,
        )

    val json =
        buildLocalDekJson(
            iv =
                iv,

            cipherText =
                cipherText,
        )

    val parent =
        cacheFile.parentFile

    if (
        parent != null &&
        !parent.exists()
    ) {
        check(
            parent.mkdirs() ||
                parent.exists(),
        ) {
            "Could not create the local cloud recovery directory."
        }
    }

    val temp =
        File(
            parent,
            "$FileName.tmp",
        )

    try {
        temp.writeText(
            json,
            Charsets.UTF_8,
        )

        if (
            !temp.renameTo(
                cacheFile,
            )
        ) {
            cacheFile.delete()

            check(
                temp.renameTo(
                    cacheFile,
                ),
            ) {
                "Could not commit the local cloud recovery key cache."
            }
        }
    } finally {
        if (
            temp.exists()
        ) {
            temp.delete()
        }
    }
}
    public fun load(): ByteArray? {
        val file = cacheFile
        if (!file.exists()) return null
        return try {
            val bytes = file.inputStream().use { input -> input.readBytesBounded(MaxLocalDekFileBytes) }
            val local = parseLocalDekJson(bytes) ?: return clearAndNull()
            val key = getExistingWrappingKey() ?: return clearAndNull()
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GcmTagBits, local.iv),
            )
            cipher.updateAAD(LocalDekAad)
            val dek = cipher.doFinal(local.cipherText)
            if (dek.size != CloudRecoveryDekBytes) {
                dek.fill(0)
                return clearAndNull()
            }
            dek
        } catch (error: AEADBadTagException) {
            clearAndNull()
        } catch (error: Exception) {
            clearAndNull()
        }
    }

    public fun clear() {
        cacheFile.delete()
    }

    public fun clearPermanently() {
        clear()
        runCatching {
            val androidKeyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
            if (androidKeyStore.containsAlias(KeyAlias)) {
                androidKeyStore.deleteEntry(KeyAlias)
            }
        }
    }

    private fun clearAndNull(): ByteArray? {
        clear()
        return null
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        getExistingWrappingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(CloudRecoveryDekBytes * 8)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        return keyStore.getKey(KeyAlias, null) as? SecretKey
    }


    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "impulsive_cloud_recovery_local_wrap_v1"
        const val DirectoryName = "cloud_recovery"
        const val FileName = "local_dek_v1.json"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
        const val MaxLocalDekFileBytes = 4096
        val LocalDekAad = "impulsive-cloud-recovery|1|local-dek".toByteArray(Charsets.UTF_8)
    }
}

private data class LocalDekEnvelope(
    val iv: ByteArray,
    val cipherText: ByteArray,
)

private fun buildLocalDekJson(
    iv: ByteArray,
    cipherText: ByteArray,
): String = "{\"formatVersion\":1,\"ivBase64\":\"${iv.toCanonicalBase64()}\",\"cipherTextBase64\":\"${cipherText.toCanonicalBase64()}\"}"

private fun parseLocalDekJson(bytes: ByteArray): LocalDekEnvelope? {
    if (bytes.size > 4096) return null
    val json = runCatching { decodeUtf8Strict(bytes) }.getOrNull() ?: return null
    val version = Regex("\"formatVersion\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
        ?: return null
    if (version != 1) return null
    val ivBase64 = Regex("\"ivBase64\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
        ?: return null
    val cipherTextBase64 = Regex("\"cipherTextBase64\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
        ?: return null
    val iv = decodeCanonicalBase64(ivBase64, CloudRecoveryIvBytes) ?: return null
    val cipherText = decodeCanonicalBase64Bytes(
        value = cipherTextBase64,
        minBytes = CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes,
        maxBytes = CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes,
    ) ?: return null
    return LocalDekEnvelope(iv, cipherText)
}

private fun java.io.InputStream.readBytesBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        if (read > maxBytes - total) {
            throw IllegalArgumentException("Local cloud recovery key cache exceeds maximum allowed size.")
        }
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}