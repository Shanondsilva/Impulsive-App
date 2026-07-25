package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

internal const val CloudRecoveryFormat = "impulsive-cloud-recovery"
internal const val CloudRecoveryFormatVersion = 1
internal const val CloudRecoveryPayloadVersion = 1
internal const val CloudRecoverySchemaVersion = 1
internal const val CloudRecoveryKdf = "PBKDF2WithHmacSHA256"
internal const val CloudRecoveryKdfIterations = 200000
internal const val CloudRecoveryDekBytes = 32
internal const val CloudRecoverySaltBytes = 16
internal const val CloudRecoveryIvBytes = 12
internal const val CloudRecoveryGcmTagBytes = 16
internal const val CloudRecoveryOwnerUidMaxChars = 128
internal const val CloudRecoveryMaxEnvelopeBytes = 12 * 1024 * 1024
internal const val CloudRecoveryMaxPayloadBytes = 8 * 1024 * 1024
internal const val CloudRecoveryMaxCipherTextBytes = CloudRecoveryMaxPayloadBytes + CloudRecoveryGcmTagBytes
internal val CloudRecoveryWrappedDekAad =
    "$CloudRecoveryFormat|$CloudRecoveryFormatVersion|wrapped-dek".toByteArray(Charsets.UTF_8)
internal val CloudRecoveryPayloadAad =
    "$CloudRecoveryFormat|$CloudRecoveryFormatVersion|payload".toByteArray(Charsets.UTF_8)

public data class WrappedKeyMetadata(
    val kdfSalt: ByteArray,
    val wrappedDekIv: ByteArray,
    val wrappedDekCipherText: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is WrappedKeyMetadata &&
        kdfSalt.contentEquals(other.kdfSalt) &&
        wrappedDekIv.contentEquals(other.wrappedDekIv) &&
        wrappedDekCipherText.contentEquals(other.wrappedDekCipherText)

    override fun hashCode(): Int {
        var result = kdfSalt.contentHashCode()
        result = 31 * result + wrappedDekIv.contentHashCode()
        result = 31 * result + wrappedDekCipherText.contentHashCode()
        return result
    }
}

public data class NewCloudRecovery(
    val envelopeBytes: ByteArray,
    val rawDek: ByteArray,
    val wrappedKeyMetadata: WrappedKeyMetadata,
) {
    override fun equals(other: Any?): Boolean = other is NewCloudRecovery &&
        envelopeBytes.contentEquals(other.envelopeBytes) &&
        rawDek.contentEquals(other.rawDek) &&
        wrappedKeyMetadata == other.wrappedKeyMetadata

    override fun hashCode(): Int {
        var result = envelopeBytes.contentHashCode()
        result = 31 * result + rawDek.contentHashCode()
        result = 31 * result + wrappedKeyMetadata.hashCode()
        return result
    }
}

public data class DecryptedCloudRecovery(
    val ownerUid: String,
    val ownerGoogleSubjectHash: String?,
    val schemaVersion: Int,
    val createdAtMillis: Long,
    val payloadJson: String,
)

public data class RestoredCloudRecovery(
    val recovery: DecryptedCloudRecovery,
    val rawDek: ByteArray,
    val wrappedKeyMetadata: WrappedKeyMetadata,
)

public sealed interface CloudRecoveryRestoreDecryptResult {
    public data class Success(
        val restoredRecovery: RestoredCloudRecovery,
    ) : CloudRecoveryRestoreDecryptResult

    public data object CryptoFailure : CloudRecoveryRestoreDecryptResult
    public data object Malformed : CloudRecoveryRestoreDecryptResult
    public data object UnsupportedVersion : CloudRecoveryRestoreDecryptResult
}
public sealed interface CloudRecoveryDecryptResult {
    data class Success(
        val recovery: DecryptedCloudRecovery,
    ) : CloudRecoveryDecryptResult

    data object OwnerMismatch : CloudRecoveryDecryptResult
    data object CryptoFailure : CloudRecoveryDecryptResult
    data object Malformed : CloudRecoveryDecryptResult
    data object UnsupportedVersion : CloudRecoveryDecryptResult
}

internal data class CloudRecoveryEnvelope(
    val kdfSalt: ByteArray,
    val wrappedDekIv: ByteArray,
    val wrappedDekCipherText: ByteArray,
    val payloadIv: ByteArray,
    val payloadCipherText: ByteArray,
)

internal data class CloudRecoveryPlainPayload(
    val ownerUid: String,
    val ownerGoogleSubjectHash: String?,
    val schemaVersion: Int,
    val createdAtMillis: Long,
    val payloadJson: String,
)

internal fun buildCloudRecoveryEnvelopeJson(
    envelope: CloudRecoveryEnvelope,
): String = buildJsonObject(
    "format" to CloudRecoveryFormat,
    "formatVersion" to CloudRecoveryFormatVersion,
    "kdf" to CloudRecoveryKdf,
    "kdfIterations" to CloudRecoveryKdfIterations,
    "kdfSaltBase64" to envelope.kdfSalt.toCanonicalBase64(),
    "wrappedDekIvBase64" to envelope.wrappedDekIv.toCanonicalBase64(),
    "wrappedDekCipherTextBase64" to envelope.wrappedDekCipherText.toCanonicalBase64(),
    "payloadIvBase64" to envelope.payloadIv.toCanonicalBase64(),
    "payloadCipherTextBase64" to envelope.payloadCipherText.toCanonicalBase64(),
)

internal fun buildCloudRecoveryPayloadJson(
    ownerUid: String,
    payloadJson: String,
    createdAtMillis: Long,
): String = buildJsonObject(
    "cloudPayloadVersion" to CloudRecoveryPayloadVersion,
    "ownerUid" to ownerUid,
    "schemaVersion" to CloudRecoverySchemaVersion,
    "createdAtMillis" to createdAtMillis,
    "payloadJson" to payloadJson,
)

internal fun parseCloudRecoveryEnvelope(bytes: ByteArray): CloudRecoveryEnvelopeParseResult {
    if (bytes.size > CloudRecoveryMaxEnvelopeBytes) return CloudRecoveryEnvelopeParseResult.Malformed
    val json = runCatching { decodeUtf8Strict(bytes) }.getOrElse {
        return CloudRecoveryEnvelopeParseResult.Malformed
    }
    val obj = JsonObjectParser(json).parseOrNull()
        ?: return CloudRecoveryEnvelopeParseResult.Malformed

    val format = obj.requiredString("format", 64)
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    if (format != CloudRecoveryFormat) return CloudRecoveryEnvelopeParseResult.Malformed

    val formatVersion = obj.requiredInt("formatVersion")
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    if (formatVersion != CloudRecoveryFormatVersion) return CloudRecoveryEnvelopeParseResult.UnsupportedVersion

    val kdf = obj.requiredString("kdf", 64)
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    if (kdf != CloudRecoveryKdf) return CloudRecoveryEnvelopeParseResult.Malformed

    val iterations = obj.requiredInt("kdfIterations")
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    if (iterations != CloudRecoveryKdfIterations) return CloudRecoveryEnvelopeParseResult.Malformed

    val salt = obj.requiredCanonicalBase64("kdfSaltBase64", 64, CloudRecoverySaltBytes)
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    val wrappedDekIv = obj.requiredCanonicalBase64("wrappedDekIvBase64", 64, CloudRecoveryIvBytes)
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    val wrappedDekCipherText = obj.requiredCanonicalBase64Bytes(
        name = "wrappedDekCipherTextBase64",
        maxChars = maxBase64Chars(CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes),
        minBytes = CloudRecoveryGcmTagBytes,
        maxBytes = CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes,
    ) ?: return CloudRecoveryEnvelopeParseResult.Malformed
    val payloadIv = obj.requiredCanonicalBase64("payloadIvBase64", 64, CloudRecoveryIvBytes)
        ?: return CloudRecoveryEnvelopeParseResult.Malformed
    val payloadCipherText = obj.requiredCanonicalBase64Bytes(
        name = "payloadCipherTextBase64",
        maxChars = maxBase64Chars(CloudRecoveryMaxCipherTextBytes),
        minBytes = CloudRecoveryGcmTagBytes,
        maxBytes = CloudRecoveryMaxCipherTextBytes,
    ) ?: return CloudRecoveryEnvelopeParseResult.Malformed

    return CloudRecoveryEnvelopeParseResult.Success(
        CloudRecoveryEnvelope(
            kdfSalt = salt,
            wrappedDekIv = wrappedDekIv,
            wrappedDekCipherText = wrappedDekCipherText,
            payloadIv = payloadIv,
            payloadCipherText = payloadCipherText,
        ),
    )
}

internal sealed interface CloudRecoveryEnvelopeParseResult {
    data class Success(val envelope: CloudRecoveryEnvelope) : CloudRecoveryEnvelopeParseResult
    data object Malformed : CloudRecoveryEnvelopeParseResult
    data object UnsupportedVersion : CloudRecoveryEnvelopeParseResult
}

internal fun parseCloudRecoveryPlainPayload(bytes: ByteArray): CloudRecoveryPlainPayload? {
    if (bytes.size > CloudRecoveryMaxPayloadBytes) return null
    val json = runCatching { decodeUtf8Strict(bytes) }.getOrNull() ?: return null
    val obj = JsonObjectParser(json).parseOrNull() ?: return null
    val payloadVersion = obj.requiredInt("cloudPayloadVersion") ?: return null
    if (payloadVersion != CloudRecoveryPayloadVersion) return null
    val ownerUid = obj.requiredString("ownerUid", CloudRecoveryOwnerUidMaxChars) ?: return null
    if (ownerUid.isBlank()) return null
    val ownerGoogleSubjectHash = when (val value = obj.values["ownerGoogleSubjectHash"]) {
        null -> null
        is JsonValue.StringValue -> value.value.takeIf(::isValidGoogleSubjectHash) ?: return null
        else -> return null
    }
    val schemaVersion = obj.requiredInt("schemaVersion") ?: return null
    if (schemaVersion != CloudRecoverySchemaVersion) return null
    val createdAtMillis = obj.requiredLong("createdAtMillis") ?: return null
    val payloadJson = obj.requiredString("payloadJson", CloudRecoveryMaxPayloadBytes) ?: return null
    return CloudRecoveryPlainPayload(
        ownerUid = ownerUid,
        ownerGoogleSubjectHash = ownerGoogleSubjectHash,
        schemaVersion = schemaVersion,
        createdAtMillis = createdAtMillis,
        payloadJson = payloadJson,
    )
}

internal fun ByteArray.toCanonicalBase64(): String = Base64.getEncoder().encodeToString(this)

internal fun decodeCanonicalBase64(value: String, expectedBytes: Int): ByteArray? =
    decodeCanonicalBase64Bytes(value, expectedBytes, expectedBytes)

internal fun decodeCanonicalBase64Bytes(
    value: String,
    minBytes: Int,
    maxBytes: Int,
): ByteArray? {
    val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
    if (decoded.size !in minBytes..maxBytes) return null
    if (decoded.toCanonicalBase64() != value) return null
    return decoded
}

internal fun decodeUtf8Strict(bytes: ByteArray): String =
    Charsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

internal fun String.toCloudRecoveryJsonStringContent(): String = buildString {
    this@toCloudRecoveryJsonStringContent.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
}

private fun buildJsonObject(
    vararg fields: Pair<String, Any>,
): String = buildString {
    append('{')
    fields.forEachIndexed { index, (name, value) ->
        if (index > 0) append(',')
        append('"')
        append(name.toCloudRecoveryJsonStringContent())
        append("\":")
        when (value) {
            is String -> {
                append('"')
                append(value.toCloudRecoveryJsonStringContent())
                append('"')
            }
            is Int, is Long -> append(value)
            else -> error("Unsupported JSON value type")
        }
    }
    append('}')
}

private fun JsonObject.requiredString(name: String, maxChars: Int): String? {
    val value = values[name] as? JsonValue.StringValue ?: return null
    if (value.value.length > maxChars) return null
    return value.value
}

private fun JsonObject.requiredInt(name: String): Int? {
    val number = values[name] as? JsonValue.NumberValue ?: return null
    if (!number.raw.matches(Regex("-?(0|[1-9][0-9]*)"))) return null
    val value = number.raw.toLongOrNull() ?: return null
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    return value.toInt()
}

private fun JsonObject.requiredLong(name: String): Long? {
    val number = values[name] as? JsonValue.NumberValue ?: return null
    if (!number.raw.matches(Regex("-?(0|[1-9][0-9]*)"))) return null
    return number.raw.toLongOrNull()
}

private fun JsonObject.requiredCanonicalBase64(
    name: String,
    maxChars: Int,
    expectedBytes: Int,
): ByteArray? {
    val value = requiredString(name, maxChars) ?: return null
    return decodeCanonicalBase64(value, expectedBytes)
}

private fun JsonObject.requiredCanonicalBase64Bytes(
    name: String,
    maxChars: Int,
    minBytes: Int,
    maxBytes: Int,
): ByteArray? {
    val value = requiredString(name, maxChars) ?: return null
    return decodeCanonicalBase64Bytes(value, minBytes, maxBytes)
}

private fun maxBase64Chars(byteCount: Int): Int = ((byteCount + 2) / 3) * 4

private data class JsonObject(val values: Map<String, JsonValue>)

private sealed interface JsonValue {
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val raw: String) : JsonValue
}

private class JsonObjectParser(
    private val input: String,
) {
    private var index = 0

    fun parseOrNull(): JsonObject? = runCatching {
        skipWhitespace()
        require(readChar() == '{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (peekChar() == '}') {
            index++
            skipWhitespace()
            require(index == input.length)
            return@runCatching JsonObject(values)
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            require(readChar() == ':')
            skipWhitespace()
            values[key] = readValue()
            skipWhitespace()
            when (readChar()) {
                ',' -> continue
                '}' -> break
                else -> error("Invalid JSON object")
            }
        }
        skipWhitespace()
        require(index == input.length)
        JsonObject(values)
    }.getOrNull()

    private fun readValue(): JsonValue = when (peekChar()) {
        '"' -> JsonValue.StringValue(readString())
        '-', in '0'..'9' -> JsonValue.NumberValue(readNumber())
        else -> error("Unsupported JSON value")
    }

    private fun readString(): String {
        require(readChar() == '"')
        val output = StringBuilder()
        while (index < input.length) {
            val char = readChar()
            when (char) {
                '"' -> return output.toString()
                '\\' -> {
                    val escaped = readChar()
                    when (escaped) {
                        '"' -> output.append('"')
                        '\\' -> output.append('\\')
                        '/' -> output.append('/')
                        'b' -> output.append('\b')
                        'f' -> output.append('\u000C')
                        'n' -> output.append('\n')
                        'r' -> output.append('\r')
                        't' -> output.append('\t')
                        'u' -> {
                            val hex = input.substring(index, index + 4)
                            require(hex.matches(Regex("[0-9a-fA-F]{4}")))
                            output.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> error("Invalid escape")
                    }
                }
                else -> {
                    require(char >= ' ')
                    output.append(char)
                }
            }
        }
        error("Unterminated string")
    }

    private fun readNumber(): String {
        val start = index
        if (peekChar() == '-') index++
        while (index < input.length && input[index].isDigit()) index++
        require(start != index)
        return input.substring(start, index)
    }

    private fun skipWhitespace() {
        while (index < input.length && input[index] in charArrayOf(' ', '\n', '\r', '\t')) index++
    }

    private fun readChar(): Char {
        require(index < input.length)
        return input[index++]
    }

    private fun peekChar(): Char {
        require(index < input.length)
        return input[index]
    }
}