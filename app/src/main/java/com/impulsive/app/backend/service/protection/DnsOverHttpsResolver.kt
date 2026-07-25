package com.impulsive.app.backend.service.protection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import java.net.InetSocketAddress

internal object CloudflareFamilyDoHEndpoints {

    const val Hostname =
        "family.cloudflare-dns.com"

    const val Url =
        "https://family.cloudflare-dns.com/dns-query"

    val Addresses: List<InetAddress> =
        listOf(
            InetAddress.getByAddress(
                byteArrayOf(
                    1,
                    1,
                    1,
                    3,
                ),
            ),
            InetAddress.getByAddress(
                byteArrayOf(
                    1,
                    0,
                    0,
                    3,
                ),
            ),
            InetAddress.getByAddress(
                byteArrayOf(
                    0x26,
                    0x06,
                    0x47,
                    0x00,
                    0x47,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x11,
                    0x13,
                ),
            ),
            InetAddress.getByAddress(
                byteArrayOf(
                    0x26,
                    0x06,
                    0x47,
                    0x00,
                    0x47,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x10,
                    0x03,
                ),
            ),
        )
}

internal object AdGuardFamilyDoHEndpoints {

    const val Hostname = "family.adguard-dns.com"

    const val Url = "https://family.adguard-dns.com/dns-query"

    val Addresses: List<InetAddress> = listOf(
        InetAddress.getByAddress(byteArrayOf(94, 140.toByte(), 14, 15)),
        InetAddress.getByAddress(byteArrayOf(94, 140.toByte(), 15, 16)),
        InetAddress.getByAddress(
            byteArrayOf(
                0x2a, 0x10, 0x50, 0xc0.toByte(),
                0, 0, 0, 0, 0, 0, 0, 0,
                0xba.toByte(), 0xd1.toByte(), 0x00, 0xff.toByte(),
            ),
        ),
        InetAddress.getByAddress(
            byteArrayOf(
                0x2a, 0x10, 0x50, 0xc0.toByte(),
                0, 0, 0, 0, 0, 0, 0, 0,
                0xba.toByte(), 0xd2.toByte(), 0x00, 0xff.toByte(),
            ),
        ),
    )
}

/**
 * Bootstrap DNS used exclusively by the private DoH client.
 *
 * The DoH hostname must not be resolved through Android's normal DNS path,
 * because DNS from protected applications is routed into ImpulsiveVpnService.
 *
 * Using fixed bootstrap IP addresses prevents the resolver from recursively
 * trying to resolve its own hostname through the VPN.
 *
 * HTTPS still uses the hostname family.cloudflare-dns.com, so normal platform
 * TLS certificate and hostname verification remain active.
 */
internal object CloudflareFamilyBootstrapDns : Dns {

    override fun lookup(
        hostname: String,
    ): List<InetAddress> {
        if (
            !hostname.equals(
                CloudflareFamilyDoHEndpoints
                    .Hostname,
                ignoreCase =
                    true,
            )
        ) {
            throw UnknownHostException(
                "Unexpected DoH hostname",
            )
        }

        return CloudflareFamilyDoHEndpoints
            .Addresses
    }
}

internal object FamilyDoHBootstrapDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> = when {
        hostname.equals(CloudflareFamilyDoHEndpoints.Hostname, ignoreCase = true) ->
            CloudflareFamilyDoHEndpoints.Addresses
        hostname.equals(AdGuardFamilyDoHEndpoints.Hostname, ignoreCase = true) ->
            AdGuardFamilyDoHEndpoints.Addresses
        else -> throw UnknownHostException(
            "DoH bootstrap only resolves the pinned family DoH hostnames, not: $hostname",
        )
    }
}

/**
 * SocketFactory dedicated to the VPN's upstream DoH client.
 *
 * The socket is:
 *
 * 1. created unconnected;
 * 2. protected from VPN routing;
 * 3. returned to OkHttp only after protection succeeds.
 *
 * No connected-socket overload is permitted because that could violate the
 * protect-before-connect guarantee.
 */
internal class ProtectedVpnSocketFactory(
    private val protectSocket:
        (Socket) -> Boolean,
) : SocketFactory() {

    override fun createSocket(): Socket {
        val socket =
    Socket()

try {
    /*
     * Force creation of the underlying socket/file descriptor before
     * VpnService.protect(Socket), while keeping the socket unconnected.
     *
     * This preserves the required protect-before-connect ordering.
     */
    socket.bind(
        InetSocketAddress(
            0,
        ),
    )
} catch (error: Exception) {
    runCatching {
        socket.close()
    }

    throw IOException(
        "Unable to create DoH socket before VPN protection",
        error,
    )
}

val protected =
    try {
        protectSocket(
            socket,
        )
            } catch (
                error:
                    Exception,
            ) {
                runCatching {
                    socket.close()
                }

                throw IOException(
                    "Unable to protect DoH socket",
                    error,
                )
            }

        if (!protected) {
            runCatching {
                socket.close()
            }

            throw IOException(
                "VpnService.protect(Socket) returned false",
            )
        }

        return socket
    }

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket =
        unsupportedConnectedSocketCreation()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket =
        unsupportedConnectedSocketCreation()

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket =
        unsupportedConnectedSocketCreation()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket =
        unsupportedConnectedSocketCreation()

    private fun unsupportedConnectedSocketCreation(): Nothing {
        throw IOException(
            "Connected socket creation is not permitted for the protected DoH client",
        )
    }
}

/**
 * Pure bounded DNS-over-HTTPS message validation helpers.
 *
 * These methods intentionally contain no Android dependencies so they remain
 * directly JVM-testable.
 */
internal object DnsOverHttpsMessage {

    private const val MinimumDnsHeaderBytes =
        12

    fun validateQuery(
        query: ByteArray,
        maxDnsMessageBytes: Int,
    ) {
        if (
            query.size <
                MinimumDnsHeaderBytes ||
            query.size >
                maxDnsMessageBytes
        ) {
            throw IOException(
                "Invalid DNS query length",
            )
        }
    }

    fun copyQueryWithZeroTransactionId(
        query: ByteArray,
    ): ByteArray =
        query.copyOf().apply {
            this[0] =
                0

            this[1] =
                0
        }

    fun restoreTransactionId(
        response: ByteArray,
        originalQuery: ByteArray,
    ): ByteArray =
        response.apply {
            this[0] =
                originalQuery[0]

            this[1] =
                originalQuery[1]
        }

    fun readAndValidateResponse(
        query: ByteArray,
        input: InputStream,
        declaredContentLength: Long,
        maxDnsMessageBytes: Int,
    ): ByteArray {
        validateQuery(
            query =
                query,
            maxDnsMessageBytes =
                maxDnsMessageBytes,
        )

        /*
         * OkHttp returns -1 when Content-Length is unknown.
         *
         * Unknown length is allowed because readBounded() still enforces the
         * absolute message-size ceiling.
         */
        if (
            declaredContentLength == 0L ||
            declaredContentLength >
                maxDnsMessageBytes
                    .toLong()
        ) {
            throw IOException(
                "Invalid DoH response Content-Length",
            )
        }

        val response =
            readBounded(
                input =
                    input,
                maxBytes =
                    maxDnsMessageBytes,
                declaredContentLength =
                    declaredContentLength,
            )

        if (
            response.size <
            MinimumDnsHeaderBytes
        ) {
            throw IOException(
                "Invalid or empty DNS response",
            )
        }

        /*
         * When Content-Length was known, require the actual body length to
         * match it.
         */
        if (
            declaredContentLength > 0L &&
            response.size.toLong() !=
            declaredContentLength
        ) {
            throw IOException(
                "DoH response length mismatch",
            )
        }

        /*
         * DNS transaction ID must match the original query.
         */
        if (
            response[0] !=
                query[0] ||
            response[1] !=
                query[1]
        ) {
            throw IOException(
                "DNS transaction ID mismatch",
            )
        }

        /*
         * QR bit must indicate a DNS response.
         */
        val qrBit =
            response[2]
                .toInt() and
                0x80

        if (qrBit == 0) {
            throw IOException(
                "DNS message is not a response",
            )
        }

        return response
    }

    private fun readBounded(
        input: InputStream,
        maxBytes: Int,
        declaredContentLength: Long,
    ): ByteArray {
        val initialCapacity =
            if (
                declaredContentLength in
                1..maxBytes.toLong()
            ) {
                declaredContentLength
                    .toInt()
            } else {
                minOf(
                    512,
                    maxBytes,
                )
            }

        val output =
            ByteArrayOutputStream(
                initialCapacity,
            )

        val buffer =
            ByteArray(
                minOf(
                    1_024,
                    maxBytes,
                ),
            )

        var total = 0

        while (true) {
            val read =
                input.read(
                    buffer,
                )

            if (read < 0) {
                break
            }

            if (read == 0) {
                continue
            }

            total += read

            if (
                total >
                maxBytes
            ) {
                throw IOException(
                    "DoH response exceeds maximum DNS message size",
                )
            }

            output.write(
                buffer,
                0,
                read,
            )
        }

        return output.toByteArray()
    }
}

/**
 * Encrypted DNS resolver for Impulsive Website Protection.
 *
 * Security properties:
 *
 * - DNS-over-HTTPS only;
 * - Cloudflare Family upstream;
 * - fixed bootstrap addresses;
 * - every upstream socket protected before connect;
 * - platform TLS trust and hostname verification retained;
 * - redirects disabled;
 * - system proxy bypassed;
 * - no plaintext DNS fallback.
 *
 * One resolver owns one OkHttpClient for the lifetime of one VPN tunnel
 * generation. This preserves connection pooling and HTTP/2 reuse.
 */
internal class DnsOverHttpsResolver(
    private val protectSocket:
        (Socket) -> Boolean,
    private val maxDnsMessageBytes:
        Int =
        DefaultMaxDnsMessageBytes,
) : Closeable {

    data class Health(
        val successCount: Long,
        val failureCount: Long,
        val consecutiveFailureCount: Long,
        val lastFailureReason: String?,
    )

    private val successCount =
        AtomicLong(0)

    private val failureCount =
        AtomicLong(0)

    private val consecutiveFailureCount =
        AtomicLong(0)

    private val lastFailureReason =
        AtomicReference<String?>(null)

    private val mediaType =
        "application/dns-message"
            .toMediaType()

    private val failoverPolicy = DoHFailoverPolicy()

    /*
     * OkHttp's default maxRequestsPerHost is intentionally conservative for
     * general web traffic. This private client talks to two pinned family DoH endpoints,
     * so explicitly align its request limits with the VPN's bounded DNS
     * concurrency.
     */
    private val dispatcher =
        Dispatcher().apply {
            maxRequests =
                MaxParallelRequests

            maxRequestsPerHost =
                MaxParallelRequests
        }

    private val client =
        OkHttpClient
            .Builder()
            .dispatcher(
                dispatcher,
            )
            .dns(
                FamilyDoHBootstrapDns,
            )
            .socketFactory(
                ProtectedVpnSocketFactory(
                    protectSocket,
                ),
            )
            .proxy(
                Proxy.NO_PROXY,
            )
            .followRedirects(
                false,
            )
            .followSslRedirects(
                false,
            )
            .retryOnConnectionFailure(
                true,
            )
            .connectTimeout(
                ConnectTimeoutSeconds,
                TimeUnit.SECONDS,
            )
            .readTimeout(
                ReadTimeoutSeconds,
                TimeUnit.SECONDS,
            )
            .writeTimeout(
                WriteTimeoutSeconds,
                TimeUnit.SECONDS,
            )
            .callTimeout(
                CallTimeoutSeconds,
                TimeUnit.SECONDS,
            )
            .build()

    init {
        require(
            maxDnsMessageBytes >=
                MinimumDnsMessageBytes,
        )
    }

    /**
     * Resolves one DNS wire-format query through encrypted DoH.
     *
     * The OkHttp Call is cancellation-aware:
     *
     * cancelling the coroutine cancels the underlying network request rather
     * than forcing tunnel shutdown to wait for the full network timeout.
     */
    suspend fun resolve(
        query: ByteArray,
    ): ByteArray? {
        try {
            DnsOverHttpsMessage
                .validateQuery(
                    query =
                        query,
                    maxDnsMessageBytes =
                        maxDnsMessageBytes,
                )
        } catch (error: IOException) {
            recordFailure(error)
            return null
        }

        val upstreamQuery =
            DnsOverHttpsMessage
                .copyQueryWithZeroTransactionId(
                    query,
                )

        val endpoint =
            failoverPolicy.endpointForNextQuery(System.currentTimeMillis())
        val endpointUrl =
            if (endpoint == DoHFailoverPolicy.Endpoint.Primary) {
                CloudflareFamilyDoHEndpoints.Url
            } else {
                AdGuardFamilyDoHEndpoints.Url
            }

        val request =
            Request
                .Builder()
                .url(
                    endpointUrl,
                )
                .header(
                    "Accept",
                    "application/dns-message",
                )
                .post(
                    upstreamQuery.toRequestBody(
                        mediaType,
                    ),
                )
                .build()

        return try {
            executeCancellable(
                request,
            ).use { response ->
                if (
                    !response
                        .isSuccessful
                ) {
                    recordFailure(
                        IOException(
                            "${endpoint.name} DoH HTTP status ${response.code}",
                        ),
                    )
                    failoverPolicy.recordResult(endpoint, success = false, System.currentTimeMillis())
                    return@use null
                }

                val body =
                    response.body

                val contentType =
                    body.contentType()

                if (
                    contentType == null ||
                    contentType.type !=
                        "application" ||
                    contentType.subtype !=
                        "dns-message"
                ) {
                    recordFailure(
                        IOException(
                            "${endpoint.name}: unexpected DoH content type",
                        ),
                    )
                    failoverPolicy.recordResult(endpoint, success = false, System.currentTimeMillis())
                    return@use null
                }

                val contentLength =
                    body.contentLength()

                val upstreamResponse =
                    body.byteStream()
                        .use { stream ->
                            DnsOverHttpsMessage
                                .readAndValidateResponse(
                                    query =
                                        upstreamQuery,
                                    input =
                                        stream,
                                    declaredContentLength =
                                        contentLength,
                                    maxDnsMessageBytes =
                                        maxDnsMessageBytes,
                                )
                        }

                recordSuccess()
                failoverPolicy.recordResult(endpoint, success = true, System.currentTimeMillis())

                DnsOverHttpsMessage
                    .restoreTransactionId(
                        response =
                            upstreamResponse,
                        originalQuery =
                            query,
                    )
            }
        } catch (
            cancellation:
                CancellationException,
        ) {
            /*
             * Never convert coroutine cancellation into an ordinary DNS
             * failure. The tunnel lifecycle needs cancellation to propagate.
             */
            throw cancellation
        } catch (error: IOException) {
            recordFailure(error)
            failoverPolicy.recordResult(endpoint, success = false, System.currentTimeMillis())
            null
        }
    }

    fun healthSnapshot(): Health =
        Health(
            successCount =
                successCount.get(),
            failureCount =
                failureCount.get(),
            consecutiveFailureCount =
                consecutiveFailureCount.get(),
            lastFailureReason =
                lastFailureReason.get(),
        )

    private fun recordSuccess() {
        successCount.incrementAndGet()
        consecutiveFailureCount.set(0)
        lastFailureReason.set(null)
    }

    private fun recordFailure(
        error: IOException,
    ) {
        failureCount.incrementAndGet()
        consecutiveFailureCount.incrementAndGet()
        lastFailureReason.set(
            buildString {
                append(error.javaClass.simpleName)
                val message = error.message
                if (!message.isNullOrBlank()) {
                    append(": ")
                    append(message)
                }
            },
        )
    }

    /**
     * Converts OkHttp's asynchronous Call API into a cancellable suspending
     * operation.
     *
     * Coroutine cancellation immediately invokes Call.cancel().
     */
    private suspend fun executeCancellable(
        request: Request,
    ): Response =
        suspendCancellableCoroutine {
                continuation ->

            val call =
                client.newCall(
                    request,
                )

            continuation
                .invokeOnCancellation {
                    call.cancel()
                }

            call.enqueue(
                object : Callback {

                    override fun onFailure(
                        call: Call,
                        error: IOException,
                    ) {
                        if (
                            continuation
                                .isActive
                        ) {
                            continuation
                                .resumeWith(
                                    Result.failure(
                                        error,
                                    ),
                                )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        if (
                            !continuation
                                .isActive
                        ) {
                            response.close()
                            return
                        }

                        continuation
                            .resumeWith(
                                Result.success(
                                    response,
                                ),
                            )
                    }
                },
            )
        }

    override fun close() {
        /*
         * Cancel active requests before evicting pooled connections.
         */
        client.dispatcher
            .cancelAll()

        client.connectionPool
            .evictAll()

        runCatching {
            client.dispatcher
                .executorService
                .shutdown()
        }
    }

    private companion object {

        private const val MinimumDnsMessageBytes =
            12

        /*
         * Default used only when a caller does not provide the actual
         * tunnel-derived payload limit.
         */
        const val DefaultMaxDnsMessageBytes =
            4_068

        /*
         * Keep this aligned with the VPN service's bounded DNS concurrency.
         */
        const val MaxParallelRequests =
            32

        const val ConnectTimeoutSeconds =
            5L

        const val ReadTimeoutSeconds =
            5L

        const val WriteTimeoutSeconds =
            5L

        /*
         * Kept below Android's typical per-attempt DNS timeout window so a
         * failed upstream request can terminate before the client retry becomes
         * stale behind it.
         */
        const val CallTimeoutSeconds =
            4L
    }
}
