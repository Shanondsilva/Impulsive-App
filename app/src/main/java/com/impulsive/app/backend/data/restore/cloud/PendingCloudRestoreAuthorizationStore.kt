package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import com.impulsive.app.backend.data.local.entity.requireValidCloudRestoreIdentity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class PendingCloudRestoreAuthorization(
    val receiptId: String,
    val payloadSha256: String,
    val proofType: CloudRestoreProofType,
    val previousUid: String?,
    val previousGoogleSubjectHash: String?,
    val currentUid: String,
    val currentGoogleSubjectHash: String?,
    val authorisedAtMillis: Long,
)

internal fun PendingCloudRestoreAuthorization.requireValid():
    PendingCloudRestoreAuthorization =
    apply {
        requireValidCloudRestoreIdentity(
            receiptId = receiptId,
            payloadSha256 = payloadSha256,
            proofType = proofType,
            previousUid = previousUid,
            previousGoogleSubjectHash = previousGoogleSubjectHash,
            currentUid = currentUid,
            currentGoogleSubjectHash = currentGoogleSubjectHash,
            timestampMillis = authorisedAtMillis,
        )
    }

internal fun PendingCloudRestoreAuthorization.toReceipt(
    importedAtMillis: Long,
): CloudRestoreReceiptEntity =
    CloudRestoreReceiptEntity(
        receiptId = receiptId,
        payloadSha256 = payloadSha256,
        proofType = proofType.persistedValue,
        previousUid = previousUid,
        previousGoogleSubjectHash = previousGoogleSubjectHash,
        currentUid = currentUid,
        currentGoogleSubjectHash = currentGoogleSubjectHash,
        importedAtMillis = importedAtMillis,
    )

internal fun PendingCloudRestoreAuthorization.matches(
    receipt: CloudRestoreReceiptEntity,
): Boolean =
    receiptId == receipt.receiptId &&
        payloadSha256 == receipt.payloadSha256 &&
        proofType.persistedValue == receipt.proofType &&
        previousUid == receipt.previousUid &&
        previousGoogleSubjectHash == receipt.previousGoogleSubjectHash &&
        currentUid == receipt.currentUid &&
        currentGoogleSubjectHash == receipt.currentGoogleSubjectHash

internal interface PendingCloudRestoreAuthorizationStore {
    fun read(): PendingCloudRestoreAuthorization?
    fun write(authorization: PendingCloudRestoreAuthorization)
    fun clear()
}

internal class AndroidPendingCloudRestoreAuthorizationStore(
    context: Context,
) : PendingCloudRestoreAuthorizationStore {
    private val delegate =
        FilePendingCloudRestoreAuthorizationStore(context.noBackupFilesDir)

    override fun read(): PendingCloudRestoreAuthorization? = delegate.read()

    override fun write(authorization: PendingCloudRestoreAuthorization) {
        delegate.write(authorization)
    }

    override fun clear() = delegate.clear()
}

internal class FilePendingCloudRestoreAuthorizationStore(
    private val directory: File,
    private val replace: (File, File) -> Unit = { source, target ->
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    },
    private val delete: (File) -> Boolean = File::delete,
) : PendingCloudRestoreAuthorizationStore {
    override fun read(): PendingCloudRestoreAuthorization? {
        if (!marker.exists()) return null
        return try {
            val bytes = marker.inputStream().use { input ->
                input.readBounded(MaxBytes)
            }
            parse(String(bytes, StandardCharsets.UTF_8))
        } catch (_: Throwable) {
            deleteInvalidMarker()
            null
        }
    }

    override fun write(authorization: PendingCloudRestoreAuthorization) {
        authorization.requireValid()
        if (
            !directory.exists() &&
            !directory.mkdirs() &&
            !directory.exists()
        ) {
            throw IOException(
                "Could not create pending cloud restore authorization directory.",
            )
        }

        val encoded = encode(authorization)
        require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MaxBytes) {
            "Pending cloud restore authorization exceeds maximum size."
        }
        val temp = File(directory, TempFileName)
        temp.writeText(encoded, Charsets.UTF_8)
        try {
            replace(temp, marker)
        } catch (throwable: Throwable) {
            if (temp.exists()) {
                delete(temp)
            }
            throw IOException(
                "Could not atomically replace pending cloud restore authorization.",
                throwable,
            )
        }
    }

    override fun clear() {
        deleteExistingOrThrow(marker)
        deleteExistingOrThrow(File(directory, TempFileName))
    }

    private fun parse(json: String): PendingCloudRestoreAuthorization {
        val values = parseJsonObject(json)
        require(values.keys == ExpectedKeys)
        require(values.requiredLong("formatVersion") == FormatVersion.toLong())
        val proofType =
            CloudRestoreProofType.fromPersistedValue(
                values.requiredString("proofType"),
            ) ?: throw IllegalArgumentException(
                "Unsupported cloud restore proof type.",
            )
        return PendingCloudRestoreAuthorization(
            receiptId = values.requiredString("receiptId"),
            payloadSha256 = values.requiredString("payloadSha256"),
            proofType = proofType,
            previousUid = values.nullableString("previousUid"),
            previousGoogleSubjectHash =
                values.nullableString("previousGoogleSubjectHash"),
            currentUid = values.requiredString("currentUid"),
            currentGoogleSubjectHash =
                values.nullableString("currentGoogleSubjectHash"),
            authorisedAtMillis = values.requiredLong("authorisedAtMillis"),
        ).requireValid()
    }

    private fun encode(
        authorization: PendingCloudRestoreAuthorization,
    ): String =
        buildString {
            append('{')
            append("\"formatVersion\":")
            append(FormatVersion)
            append(",\"receiptId\":\"")
            append(authorization.receiptId.escapeJson())
            append("\",\"payloadSha256\":\"")
            append(authorization.payloadSha256.escapeJson())
            append("\",\"proofType\":\"")
            append(authorization.proofType.persistedValue.escapeJson())
            append("\",\"previousUid\":")
            appendNullableJsonString(authorization.previousUid)
            append(",\"previousGoogleSubjectHash\":")
            appendNullableJsonString(
                authorization.previousGoogleSubjectHash,
            )
            append(",\"currentUid\":\"")
            append(authorization.currentUid.escapeJson())
            append("\",\"currentGoogleSubjectHash\":")
            appendNullableJsonString(
                authorization.currentGoogleSubjectHash,
            )
            append(",\"authorisedAtMillis\":")
            append(authorization.authorisedAtMillis)
            append('}')
        }

    private fun StringBuilder.appendNullableJsonString(value: String?) {
        if (value == null) {
            append("null")
        } else {
            append('"')
            append(value.escapeJson())
            append('"')
        }
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) {
                throw IOException(
                    "Pending cloud restore authorization exceeds maximum size.",
                )
            }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private fun parseJsonObject(json: String): Map<String, JsonValue> {
        val trimmed = json.trim()
        require(trimmed.startsWith('{') && trimmed.endsWith('}'))
        val body = trimmed.substring(1, trimmed.lastIndex).trim()
        if (body.isEmpty()) return emptyMap()

        val values = linkedMapOf<String, JsonValue>()
        var index = 0
        while (index < body.length) {
            index = body.skipWhitespace(index)
            val (key, afterKey) = body.readJsonString(index)
            index = body.skipWhitespace(afterKey)
            require(index < body.length && body[index] == ':')
            index = body.skipWhitespace(index + 1)

            val value: JsonValue
            when {
                index < body.length && body[index] == '"' -> {
                    val (decoded, afterValue) =
                        body.readJsonString(index)
                    value = JsonValue.Text(decoded)
                    index = afterValue
                }

                body.startsWith("null", index) -> {
                    value = JsonValue.Null
                    index += "null".length
                }

                else -> {
                    val start = index
                    if (index < body.length && body[index] == '-') {
                        index += 1
                    }
                    while (
                        index < body.length &&
                        body[index].isDigit()
                    ) {
                        index += 1
                    }
                    require(
                        index > start &&
                            body.substring(start, index) != "-",
                    )
                    value =
                        JsonValue.Integer(
                            body.substring(start, index).toLong(),
                        )
                }
            }
            require(values.put(key, value) == null)
            index = body.skipWhitespace(index)
            if (index == body.length) break
            require(body[index] == ',')
            index += 1
        }
        return values
    }

    private fun String.skipWhitespace(start: Int): Int {
        var index = start
        while (index < length && this[index].isWhitespace()) {
            index += 1
        }
        return index
    }

    private fun String.readJsonString(start: Int): Pair<String, Int> {
        require(start < length && this[start] == '"')
        val decoded = StringBuilder()
        var index = start + 1
        while (index < length) {
            when (val character = this[index++]) {
                '"' -> return decoded.toString() to index
                '\\' -> {
                    require(index < length)
                    when (val escaped = this[index++]) {
                        '"', '\\', '/' -> decoded.append(escaped)
                        'b' -> decoded.append('\b')
                        'f' -> decoded.append('\u000C')
                        'n' -> decoded.append('\n')
                        'r' -> decoded.append('\r')
                        't' -> decoded.append('\t')
                        'u' -> {
                            require(index + 4 <= length)
                            decoded.append(
                                substring(index, index + 4)
                                    .toInt(16)
                                    .toChar(),
                            )
                            index += 4
                        }

                        else -> throw IllegalArgumentException(
                            "Invalid JSON escape.",
                        )
                    }
                }

                else -> {
                    require(character.code >= 0x20)
                    decoded.append(character)
                }
            }
        }
        throw IllegalArgumentException("Unterminated JSON string.")
    }

    private fun String.escapeJson(): String =
        buildString {
            this@escapeJson.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (character.code < 0x20) {
                            append("\\u")
                            append(
                                character.code
                                    .toString(16)
                                    .padStart(4, '0'),
                            )
                        } else {
                            append(character)
                        }
                }
            }
        }

    private fun Map<String, JsonValue>.requiredString(
        name: String,
    ): String =
        (get(name) as? JsonValue.Text)?.value
            ?: throw IllegalArgumentException("$name must be a string")

    private fun Map<String, JsonValue>.nullableString(
        name: String,
    ): String? =
        when (val value = get(name)) {
            JsonValue.Null -> null
            is JsonValue.Text -> value.value
            else -> throw IllegalArgumentException(
                "$name must be a string or null",
            )
        }

    private fun Map<String, JsonValue>.requiredLong(
        name: String,
    ): Long =
        (get(name) as? JsonValue.Integer)?.value
            ?: throw IllegalArgumentException("$name must be an integer")

    private fun deleteInvalidMarker() {
        deleteExistingOrThrow(marker)
    }

    private fun deleteExistingOrThrow(file: File) {
        if (file.exists() && !delete(file)) {
            throw IOException(
                "Could not clear pending cloud restore authorization.",
            )
        }
    }

    private val marker: File
        get() = File(directory, FileName)

    private sealed interface JsonValue {
        data class Text(val value: String) : JsonValue
        data class Integer(val value: Long) : JsonValue
        data object Null : JsonValue
    }

    private companion object {
        const val FileName =
            "pending_cloud_restore_authorization_v1.json"
        const val TempFileName =
            "pending_cloud_restore_authorization_v1.json.tmp"
        const val FormatVersion = 1
        const val MaxBytes = 4096
        val ExpectedKeys =
            setOf(
                "formatVersion",
                "receiptId",
                "payloadSha256",
                "proofType",
                "previousUid",
                "previousGoogleSubjectHash",
                "currentUid",
                "currentGoogleSubjectHash",
                "authorisedAtMillis",
            )
    }
}
