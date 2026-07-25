package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Persists only the password-wrapped DEK metadata needed for routine cloud-recovery updates.
 *
 * The recovery password and raw DEK are deliberately not stored here. The raw DEK remains
 * protected by [CloudRecoveryLocalKeyStore]. This file lives under noBackupFilesDir so Android
 * Auto Backup cannot transfer it to another device.
 */
public class CloudRecoveryLocalMetadataStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val metadataFile: File
        get() = File(
            File(appContext.noBackupFilesDir, DirectoryName),
            FileName,
        )

    public fun store(metadata: WrappedKeyMetadata) {
        validateWrappedKeyMetadata(metadata)

        val directory = metadataFile.parentFile
            ?: throw IOException(
                "Cloud recovery metadata directory is unavailable.",
            )

        if (
            !directory.exists() &&
            !directory.mkdirs() &&
            !directory.exists()
        ) {
            throw IOException(
                "Unable to create cloud recovery metadata directory.",
            )
        }

        val temp = File(
            directory,
            TempFileName,
        )

        temp.writeText(
            buildCloudRecoveryLocalMetadataJson(metadata),
            Charsets.UTF_8,
        )

        replaceAtomicallyEnough(
            temp = temp,
            target = metadataFile,
        )
    }

    public fun load(): WrappedKeyMetadata? {
        val file = metadataFile

        if (!file.exists()) {
            return null
        }

        return try {
            val bytes = file.inputStream().use { input ->
                input.readCloudRecoveryMetadataBytesBounded(
                    MaxMetadataFileBytes,
                )
            }

            parseCloudRecoveryLocalMetadataJson(bytes)
                ?: return clearAndNull()
        } catch (error: Exception) {
            clearAndNull()
        }
    }

    public fun clear() {
        metadataFile.delete()

        metadataFile.parentFile?.let { directory ->
            File(
                directory,
                TempFileName,
            ).delete()
        }
    }

    private fun clearAndNull(): WrappedKeyMetadata? {
        clear()
        return null
    }

    private companion object {
        const val DirectoryName = "cloud_recovery"
        const val FileName = "wrapped_key_metadata_v1.json"
        const val TempFileName = "wrapped_key_metadata_v1.json.tmp"
        const val MaxMetadataFileBytes = 4096
    }
}

internal fun buildCloudRecoveryLocalMetadataJson(
    metadata: WrappedKeyMetadata,
): String {
    validateWrappedKeyMetadata(metadata)

    return buildString {
        append("{\"formatVersion\":1")

        append(",\"kdfSaltBase64\":\"")
        append(metadata.kdfSalt.toCanonicalBase64())
        append("\"")

        append(",\"wrappedDekIvBase64\":\"")
        append(metadata.wrappedDekIv.toCanonicalBase64())
        append("\"")

        append(",\"wrappedDekCipherTextBase64\":\"")
        append(
            metadata.wrappedDekCipherText.toCanonicalBase64(),
        )
        append("\"}")
    }
}

internal fun parseCloudRecoveryLocalMetadataJson(
    bytes: ByteArray,
): WrappedKeyMetadata? {
    if (bytes.size > 4096) {
        return null
    }

    val json = runCatching {
        decodeUtf8Strict(bytes)
    }.getOrNull() ?: return null

    val formatVersion =
        Regex("\\\"formatVersion\\\"\\s*:\\s*(-?\\d+)")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return null

    if (formatVersion != 1) {
        return null
    }

    val saltBase64 =
        json.requiredLocalMetadataString(
            "kdfSaltBase64",
        ) ?: return null

    val ivBase64 =
        json.requiredLocalMetadataString(
            "wrappedDekIvBase64",
        ) ?: return null

    val cipherTextBase64 =
        json.requiredLocalMetadataString(
            "wrappedDekCipherTextBase64",
        ) ?: return null

    val metadata = WrappedKeyMetadata(
        kdfSalt = decodeCanonicalBase64(
            saltBase64,
            CloudRecoverySaltBytes,
        ) ?: return null,

        wrappedDekIv = decodeCanonicalBase64(
            ivBase64,
            CloudRecoveryIvBytes,
        ) ?: return null,

        wrappedDekCipherText =
            decodeCanonicalBase64Bytes(
                value = cipherTextBase64,
                minBytes =
                    CloudRecoveryDekBytes +
                        CloudRecoveryGcmTagBytes,
                maxBytes =
                    CloudRecoveryDekBytes +
                        CloudRecoveryGcmTagBytes,
            ) ?: return null,
    )

    return runCatching {
        validateWrappedKeyMetadata(metadata)
        metadata
    }.getOrNull()
}

private fun validateWrappedKeyMetadata(
    metadata: WrappedKeyMetadata,
) {
    require(
        metadata.kdfSalt.size ==
            CloudRecoverySaltBytes,
    ) {
        "Cloud recovery KDF salt has an invalid size."
    }

    require(
        metadata.wrappedDekIv.size ==
            CloudRecoveryIvBytes,
    ) {
        "Cloud recovery wrapped-DEK IV has an invalid size."
    }

    require(
        metadata.wrappedDekCipherText.size ==
            CloudRecoveryDekBytes +
            CloudRecoveryGcmTagBytes,
    ) {
        "Cloud recovery wrapped-DEK ciphertext has an invalid size."
    }
}

private fun String.requiredLocalMetadataString(
    name: String,
): String? {
    val pattern =
        Regex(
            "\\\"${Regex.escape(name)}\\\"" +
                "\\s*:\\s*\\\"([^\\\"]*)\\\"",
        )

    return pattern
        .find(this)
        ?.groupValues
        ?.get(1)
}

private fun replaceAtomicallyEnough(
    temp: File,
    target: File,
) {
    if (temp.renameTo(target)) {
        return
    }

    if (
        target.exists() &&
        !target.delete()
    ) {
        temp.delete()

        throw IOException(
            "Unable to replace cloud recovery metadata.",
        )
    }

    if (!temp.renameTo(target)) {
        temp.delete()

        throw IOException(
            "Unable to finalize cloud recovery metadata.",
        )
    }
}

private fun java.io.InputStream
    .readCloudRecoveryMetadataBytesBounded(
        maxBytes: Int,
    ): ByteArray {
    val output =
        ByteArrayOutputStream(
            minOf(
                DEFAULT_BUFFER_SIZE,
                maxBytes,
            ),
        )

    val buffer =
        ByteArray(
            DEFAULT_BUFFER_SIZE,
        )

    var total = 0

    while (true) {
        val read =
            read(buffer)

        if (read == -1) {
            break
        }

        if (
            read >
            maxBytes - total
        ) {
            throw IllegalArgumentException(
                "Cloud recovery metadata exceeds maximum allowed size.",
            )
        }

        output.write(
            buffer,
            0,
            read,
        )

        total += read
    }

    return output.toByteArray()
}