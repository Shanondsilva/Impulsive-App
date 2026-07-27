package com.impulsive.app.backend.data.restore

import android.content.Context
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class PendingRestoredOwnershipClaim(
    val previousOwnerUid: String,
    val previousGoogleSubjectHash: String,
    val currentUid: String,
    val currentGoogleSubjectHash: String,
    val createdAtMillis: Long,
)

internal fun PendingRestoredOwnershipClaim.hasSameIdentityAs(
    other: PendingRestoredOwnershipClaim,
): Boolean =
    previousOwnerUid == other.previousOwnerUid &&
        previousGoogleSubjectHash == other.previousGoogleSubjectHash &&
        currentUid == other.currentUid &&
        currentGoogleSubjectHash == other.currentGoogleSubjectHash

internal interface PendingRestoredOwnershipClaimStore {
    fun read(): PendingRestoredOwnershipClaim?
    fun write(claim: PendingRestoredOwnershipClaim)
    fun clear()
}

internal class AndroidPendingRestoredOwnershipClaimStore(
    context: Context,
) : PendingRestoredOwnershipClaimStore {
    private val delegate = FilePendingRestoredOwnershipClaimStore(context.noBackupFilesDir)

    override fun read(): PendingRestoredOwnershipClaim? = delegate.read()
    override fun write(claim: PendingRestoredOwnershipClaim) = delegate.write(claim)
    override fun clear() = delegate.clear()
}

internal class FilePendingRestoredOwnershipClaimStore(
    private val directory: File,
    private val replace: (File, File) -> Unit = { source, target ->
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    },
) : PendingRestoredOwnershipClaimStore {
    override fun read(): PendingRestoredOwnershipClaim? {
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

    override fun write(claim: PendingRestoredOwnershipClaim) {
        validate(claim)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create pending ownership claim directory.")
        }
        val temp = File(directory, TempFileName)
        temp.writeText(encode(claim), Charsets.UTF_8)
        try {
            replace(temp, marker)
        } catch (throwable: Throwable) {
            temp.delete()
            throw IOException("Could not atomically replace pending ownership claim.", throwable)
        }
    }

    override fun clear() {
        if (marker.exists() && !marker.delete()) {
            throw IOException("Could not clear pending ownership claim.")
        }
    }

    private fun parse(json: String): PendingRestoredOwnershipClaim {
        val values = parseJsonObject(json)
        require(values.keys == ExpectedKeys)
        require(values.requiredLong("formatVersion") == FormatVersion.toLong())
        require(values.requiredString("proofType") == ProofType)
        return PendingRestoredOwnershipClaim(
            previousOwnerUid = values.requiredString("previousOwnerUid"),
            previousGoogleSubjectHash =
                values.requiredString("previousGoogleSubjectHash"),
            currentUid = values.requiredString("currentUid"),
            currentGoogleSubjectHash =
                values.requiredString("currentGoogleSubjectHash"),
            createdAtMillis = values.requiredLong("createdAtMillis"),
        ).also(::validate)
    }

    private fun encode(claim: PendingRestoredOwnershipClaim): String =
        buildString {
            append('{')
            append("\"formatVersion\":")
            append(FormatVersion)
            append(",\"proofType\":\"")
            append(ProofType.escapeJson())
            append("\",\"previousOwnerUid\":\"")
            append(claim.previousOwnerUid.escapeJson())
            append("\",\"previousGoogleSubjectHash\":\"")
            append(claim.previousGoogleSubjectHash.escapeJson())
            append("\",\"currentUid\":\"")
            append(claim.currentUid.escapeJson())
            append("\",\"currentGoogleSubjectHash\":\"")
            append(claim.currentGoogleSubjectHash.escapeJson())
            append("\",\"createdAtMillis\":")
            append(claim.createdAtMillis)
            append('}')
        }

    private fun validate(claim: PendingRestoredOwnershipClaim) {
        requireValidUid(claim.previousOwnerUid)
        requireValidUid(claim.currentUid)
        require(isValidGoogleSubjectHash(claim.previousGoogleSubjectHash))
        require(isValidGoogleSubjectHash(claim.currentGoogleSubjectHash))
        require(claim.previousGoogleSubjectHash == claim.currentGoogleSubjectHash)
        require(claim.previousOwnerUid != claim.currentUid)
        require(claim.createdAtMillis >= 0)
    }

    private fun requireValidUid(value: String) {
        require(value == value.trim() && value.isNotBlank() && value.length <= MaxUidChars)
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) {
                throw IOException("Pending ownership claim exceeds maximum size.")
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
            if (index < body.length && body[index] == '"') {
                val (decoded, afterValue) = body.readJsonString(index)
                value = JsonValue.Text(decoded)
                index = afterValue
            } else {
                val start = index
                if (index < body.length && body[index] == '-') index += 1
                while (index < body.length && body[index].isDigit()) index += 1
                require(index > start && body.substring(start, index) != "-")
                value = JsonValue.Integer(body.substring(start, index).toLong())
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
        while (index < length && this[index].isWhitespace()) index += 1
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
                                substring(index, index + 4).toInt(16).toChar(),
                            )
                            index += 4
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape.")
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

    private fun String.escapeJson(): String = buildString {
        this@escapeJson.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }

    private fun Map<String, JsonValue>.requiredString(name: String): String =
        (get(name) as? JsonValue.Text)?.value
            ?: throw IllegalArgumentException("$name must be a string")

    private fun Map<String, JsonValue>.requiredLong(name: String): Long =
        (get(name) as? JsonValue.Integer)?.value
            ?: throw IllegalArgumentException("$name must be an integer")

    private fun deleteInvalidMarker() {
        if (marker.exists() && !marker.delete()) {
            throw IOException("Could not remove invalid pending ownership claim.")
        }
    }

    private val marker: File
        get() = File(directory, FileName)

    private sealed interface JsonValue {
        data class Text(val value: String) : JsonValue
        data class Integer(val value: Long) : JsonValue
    }

    private companion object {
        const val FileName = "pending_restored_ownership_claim_v1.json"
        const val TempFileName = "pending_restored_ownership_claim_v1.json.tmp"
        const val FormatVersion = 1
        const val ProofType = "same_google_identity"
        const val MaxBytes = 4096
        const val MaxUidChars = 128
        val ExpectedKeys = setOf(
            "formatVersion",
            "proofType",
            "previousOwnerUid",
            "previousGoogleSubjectHash",
            "currentUid",
            "currentGoogleSubjectHash",
            "createdAtMillis",
        )
    }
}
