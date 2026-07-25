package com.impulsive.app.backend.data.restore.cloud

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val DriveApiBaseUrl = "https://www.googleapis.com/drive/v3"
private const val DriveUploadBaseUrl = "https://www.googleapis.com/upload/drive/v3"
private const val AppDataFolderSpace = "appDataFolder"
private const val MaxAcceptedFindResults = 20
private const val MaxErrorBodyBytes = 2048
private const val DownloadBufferBytes = 8192
private const val InitialDownloadBufferBytes = 8192
private val JsonMediaType = "application/json; charset=utf-8".toMediaType()
private val MultipartRelatedMediaType = "multipart/related".toMediaType()

internal data class DriveAppDataFile(
    val id: String,
    val name: String,
    val modifiedTime: String?,
    val size: Long?,
)

sealed class DriveAppDataHttpException(
    message: String,
    val statusCode: Int,
    val responseBodySnippet: String?,
) : IOException(message) {
    class Unauthorized(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive authorization failed with HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )

    class Forbidden(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive access was forbidden with HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )

    class NotFound(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive file was not found with HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )

    class RateLimited(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive request was rate limited with HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )

    class RetryableServerError(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive server error HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )

    class Other(statusCode: Int, responseBodySnippet: String?) : DriveAppDataHttpException(
        "Google Drive request failed with HTTP $statusCode.",
        statusCode,
        responseBodySnippet,
    )
}

internal class DriveAppDataClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun findByName(
        accessToken: String,
        fileName: String,
    ): List<DriveAppDataFile> {
        val url = DriveApiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("files")
            .addQueryParameter("spaces", AppDataFolderSpace)
            .addQueryParameter("q", "name = '${fileName.toDriveQueryLiteral()}'")
            .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime)")
            .addQueryParameter("pageSize", (MaxAcceptedFindResults + 1).toString())
            .build()
        val request = authenticatedRequest(url.toString(), accessToken)
            .get()
            .build()

        return executeCancellable(request).use { response ->
            response.requireSuccess()
            val files = parseDriveFilesResponse(response.body.string())
            if (files.size > MaxAcceptedFindResults) {
                throw IOException("Google Drive returned too many appDataFolder files with the requested name.")
            }
            files.sortedWith(compareByDescending<DriveAppDataFile> { it.modifiedTime ?: "" })
        }
    }

    suspend fun download(
        accessToken: String,
        fileId: String,
        maxBytes: Int,
    ): ByteArray {
        require(maxBytes >= 0) { "maxBytes must be non-negative." }
        val url = DriveApiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("files")
            .addPathSegment(fileId)
            .addQueryParameter("alt", "media")
            .build()
        val request = authenticatedRequest(url.toString(), accessToken)
            .get()
            .build()

        return executeCancellable(request).use { response ->
            response.requireSuccess()
            val contentLength = response.body.contentLength()
            if (contentLength > maxBytes) {
                throw IOException("Google Drive file exceeds the configured download limit.")
            }
            response.body.byteStream().use { input ->
                val output = ByteArrayOutputStream(minOf(maxBytes, InitialDownloadBufferBytes))
                val buffer = ByteArray(DownloadBufferBytes)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException("Google Drive file exceeds the configured download limit.")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
    }

    suspend fun create(
        accessToken: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): DriveAppDataFile {
        val url = DriveUploadBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("files")
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,mimeType,size,modifiedTime")
            .build()
        val metadata = "{\"name\":\"${fileName.toJsonStringContent()}\",\"parents\":[\"$AppDataFolderSpace\"]}"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartRelatedMediaType)
            .addPart(metadata.toRequestBody(JsonMediaType))
            .addPart(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        val request = authenticatedRequest(url.toString(), accessToken)
            .post(requestBody)
            .build()

        return executeCancellable(request).use { response ->
            response.requireSuccess()
            parseDriveFileObject(response.body.string())
                ?: throw IOException("Google Drive create returned an invalid file response.")
        }
    }

    suspend fun updateContent(
        accessToken: String,
        fileId: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        val url = DriveUploadBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("files")
            .addPathSegment(fileId)
            .addQueryParameter("uploadType", "media")
            .build()
        val request = authenticatedRequest(url.toString(), accessToken)
            .patch(bytes.toRequestBody(contentType.toMediaType()))
            .build()

        executeCancellable(request).use { response ->
            response.requireSuccess()
        }
    }

    suspend fun delete(
        accessToken: String,
        fileId: String,
    ) {
        val url = DriveApiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("files")
            .addPathSegment(fileId)
            .build()
        val request = authenticatedRequest(url.toString(), accessToken)
            .delete()
            .build()

        executeCancellable(request).use { response ->
            response.requireSuccess()
        }
    }

    private fun authenticatedRequest(
        url: String,
        accessToken: String,
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")

    private suspend fun executeCancellable(
        request: Request,
    ): Response = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    continuation.resumeWith(Result.success(response))
                }
            },
        )
    }

    private fun Response.requireSuccess() {
        if (isSuccessful) return
        val snippet = body.boundedErrorSnippet()
        throw when (code) {
            401 -> DriveAppDataHttpException.Unauthorized(code, snippet)
            403 -> DriveAppDataHttpException.Forbidden(code, snippet)
            404 -> DriveAppDataHttpException.NotFound(code, snippet)
            429 -> DriveAppDataHttpException.RateLimited(code, snippet)
            in 500..599 -> DriveAppDataHttpException.RetryableServerError(code, snippet)
            else -> DriveAppDataHttpException.Other(code, snippet)
        }
    }
}

private fun String.toDriveQueryLiteral(): String = buildString {
    this@toDriveQueryLiteral.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            else -> append(char)
        }
    }
}

private fun parseDriveFilesResponse(json: String): List<DriveAppDataFile> {
    val filesArray = extractJsonArray(json, "files") ?: return emptyList()
    return extractTopLevelJsonObjects(filesArray).mapNotNull(::parseDriveFileObject)
}

private fun parseDriveFileObject(json: String): DriveAppDataFile? {
    val id = json.stringProperty("id")?.takeIf { it.isNotBlank() } ?: return null
    val name = json.stringProperty("name")?.takeIf { it.isNotBlank() } ?: return null
    return DriveAppDataFile(
        id = id,
        name = name,
        modifiedTime = json.stringProperty("modifiedTime")?.takeIf { it.isNotBlank() },
        size = json.stringProperty("size")?.toLongOrNull() ?: json.longProperty("size"),
    )
}

private fun extractJsonArray(json: String, propertyName: String): String? {
    val key = "\"${propertyName.toJsonStringContent()}\""
    val keyIndex = json.indexOf(key)
    if (keyIndex < 0) return null
    val colonIndex = json.indexOf(':', startIndex = keyIndex + key.length)
    if (colonIndex < 0) return null
    val arrayStart = json.indexOf('[', startIndex = colonIndex + 1)
    if (arrayStart < 0) return null
    var inString = false
    var escaping = false
    var depth = 0
    for (index in arrayStart until json.length) {
        val char = json[index]
        if (escaping) {
            escaping = false
            continue
        }
        when {
            char == '\\' && inString -> escaping = true
            char == '"' -> inString = !inString
            !inString && char == '[' -> depth++
            !inString && char == ']' -> {
                depth--
                if (depth == 0) return json.substring(arrayStart + 1, index)
            }
        }
    }
    return null
}

private fun extractTopLevelJsonObjects(jsonArrayContent: String): List<String> {
    val objects = mutableListOf<String>()
    var inString = false
    var escaping = false
    var depth = 0
    var objectStart = -1
    for (index in jsonArrayContent.indices) {
        val char = jsonArrayContent[index]
        if (escaping) {
            escaping = false
            continue
        }
        when {
            char == '\\' && inString -> escaping = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> {
                if (depth == 0) objectStart = index
                depth++
            }
            !inString && char == '}' -> {
                depth--
                if (depth == 0 && objectStart >= 0) {
                    objects += jsonArrayContent.substring(objectStart, index + 1)
                    objectStart = -1
                }
            }
        }
    }
    return objects
}

private fun String.stringProperty(name: String): String? {
    val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    return pattern.find(this)?.groupValues?.get(1)?.fromJsonStringContent()
}

private fun String.longProperty(name: String): Long? {
    val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*(-?\\d+)")
    return pattern.find(this)?.groupValues?.get(1)?.toLongOrNull()
}

private fun String.toJsonStringContent(): String = buildString {
    this@toJsonStringContent.forEach { char ->
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

private fun String.fromJsonStringContent(): String = buildString {
    var index = 0
    while (index < this@fromJsonStringContent.length) {
        val char = this@fromJsonStringContent[index]
        if (char != '\\' || index == this@fromJsonStringContent.lastIndex) {
            append(char)
            index++
            continue
        }
        val escaped = this@fromJsonStringContent[index + 1]
        when (escaped) {
            '"' -> append('"')
            '\\' -> append('\\')
            '/' -> append('/')
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val hex = this@fromJsonStringContent.substring(index + 2, minOf(index + 6, length))
                val value = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                if (value != null) {
                    append(value.toChar())
                    index += 4
                }
            }
            else -> append(escaped)
        }
        index += 2
    }
}

private fun okhttp3.ResponseBody.boundedErrorSnippet(): String? = byteStream().use { input ->
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(512)
    var remaining = MaxErrorBodyBytes
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (read == -1) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    output.toString(Charsets.UTF_8.name()).takeIf { it.isNotBlank() }
}
