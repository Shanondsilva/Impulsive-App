package com.impulsive.app.backend.data.restore.cloud

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAppDataClientTest {
    @Test
    fun `files list uses appDataFolder space`() = runBlocking {
        val interceptor = RecordingInterceptor(jsonResponse("""{"files": []}"""))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        client.findByName(accessToken = "token", fileName = "backup.bin")

        assertEquals("appDataFolder", interceptor.request.url.queryParameter("spaces"))
        assertEquals("/drive/v3/files", interceptor.request.url.encodedPath)
    }

    @Test
    fun `normal Drive space is never used for find`() = runBlocking {
        val interceptor = RecordingInterceptor(jsonResponse("""{"files": []}"""))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        client.findByName(accessToken = "token", fileName = "backup.bin")

        assertEquals("appDataFolder", interceptor.request.url.queryParameter("spaces"))
        assertFalse(interceptor.request.url.toString().contains("spaces=drive"))
        assertFalse(interceptor.request.url.toString().contains("root"))
    }

    @Test
    fun `create metadata uses appDataFolder parent`() = runBlocking {
        val interceptor = RecordingInterceptor(jsonResponse("""{"id":"id-1","name":"backup.bin"}"""))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        client.create(
            accessToken = "token",
            fileName = "backup.bin",
            contentType = "application/octet-stream",
            bytes = byteArrayOf(1, 2, 3),
        )

        val body = interceptor.requestBodyString()
        assertEquals("/upload/drive/v3/files", interceptor.request.url.encodedPath)
        assertEquals("multipart", interceptor.request.url.queryParameter("uploadType"))
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertFalse(body.contains("\"root\""))
    }

    @Test
    fun `download rejects content length above limit`() = runBlocking {
        val interceptor = RecordingInterceptor(
            response(code = 200, body = "123456".toResponseBody(null)),
        )
        val client = DriveAppDataClient(okHttpClient(interceptor))

        val error = expectThrows<IOException> {
            client.download(accessToken = "token", fileId = "file-1", maxBytes = 5)
        }

        assertTrue(error.message.orEmpty().contains("download limit"))
    }

    @Test
    fun `chunked response is stopped once maxBytes is exceeded`() = runBlocking {
        val interceptor = RecordingInterceptor(
            response(code = 200, body = UnknownLengthBody(byteArrayOf(1, 2, 3, 4, 5, 6))),
        )
        val client = DriveAppDataClient(okHttpClient(interceptor))

        val error = expectThrows<IOException> {
            client.download(accessToken = "token", fileId = "file-1", maxBytes = 5)
        }

        assertTrue(error.message.orEmpty().contains("download limit"))
    }

    @Test
    fun `401 maps to unauthorized`() = runBlocking {
        val interceptor = RecordingInterceptor(errorResponse(401))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        val error = expectThrows<DriveAppDataHttpException.Unauthorized> {
            client.findByName(accessToken = "token", fileName = "backup.bin")
        }

        assertEquals(401, error.statusCode)
    }

    @Test
    fun `429 maps to rate limited`() = runBlocking {
        val interceptor = RecordingInterceptor(errorResponse(429))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        val error = expectThrows<DriveAppDataHttpException.RateLimited> {
            client.findByName(accessToken = "token", fileName = "backup.bin")
        }

        assertEquals(429, error.statusCode)
    }

    @Test
    fun `bearer token never appears in exception text`() = runBlocking {
        val token = "secret-access-token"
        val interceptor = RecordingInterceptor(
            response(
                code = 401,
                body = "server echoed secret-access-token".toResponseBody(null),
            ),
        )
        val client = DriveAppDataClient(okHttpClient(interceptor))

        val error = expectThrows<DriveAppDataHttpException.Unauthorized> {
            client.findByName(accessToken = token, fileName = "backup.bin")
        }

        assertEquals("Bearer $token", interceptor.request.header("Authorization"))
        assertFalse(error.message.orEmpty().contains(token))
        assertFalse(error.toString().contains(token))
    }

    @Test
    fun `file name query escaping is correct`() = runBlocking {
        val interceptor = RecordingInterceptor(jsonResponse("""{"files": []}"""))
        val client = DriveAppDataClient(okHttpClient(interceptor))

        client.findByName(accessToken = "token", fileName = "today's\\backup.bin")

        assertEquals("name = 'today\\'s\\\\backup.bin'", interceptor.request.url.queryParameter("q"))
    }

    private class RecordingInterceptor(
        private val response: Response,
    ) : Interceptor {
        lateinit var request: Request

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            return response.newBuilder()
                .request(request)
                .build()
        }

        fun requestBodyString(): String {
            val body = request.body ?: return ""
            val buffer = Buffer()
            body.writeTo(buffer)
            return buffer.readUtf8()
        }
    }

    private class UnknownLengthBody(
        private val bytes: ByteArray,
    ) : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = Buffer().write(bytes)
    }

    private companion object {
        fun okHttpClient(interceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        fun jsonResponse(json: String): Response = response(
            code = 200,
            body = json.toResponseBody("application/json".toMediaType()),
        )

        fun errorResponse(code: Int): Response = response(
            code = code,
            body = "diagnostic".toResponseBody(null),
        )

        fun response(code: Int, body: ResponseBody): Response = Response.Builder()
            .request(Request.Builder().url("https://www.googleapis.com/drive/v3/files").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body)
            .build()

        inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
            try {
                block()
            } catch (error: Throwable) {
                if (error is T) return error
                throw AssertionError("Expected ${T::class.java.name}, got ${error.javaClass.name}.", error)
            }
            throw AssertionError("Expected ${T::class.java.name} to be thrown.")
        }
    }
}