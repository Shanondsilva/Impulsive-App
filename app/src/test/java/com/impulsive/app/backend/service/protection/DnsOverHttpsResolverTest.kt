package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking

class DnsOverHttpsResolverTest {

    @Test
    fun `bootstrap DNS returns Cloudflare Family addresses in order`() {
        val addresses =
            CloudflareFamilyBootstrapDns.lookup(
                "family.cloudflare-dns.com",
            )

        assertEquals(
            listOf(
                inetAddressOf(
                    1,
                    1,
                    1,
                    3,
                ),
                inetAddressOf(
                    1,
                    0,
                    0,
                    3,
                ),
                inetAddressOf(
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
                inetAddressOf(
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
            addresses,
        )
    }

    @Test
    fun `bootstrap DNS rejects unexpected hostname`() {
        assertThrows(UnknownHostException::class.java) {
            CloudflareFamilyBootstrapDns.lookup(
                "example.com",
            )
        }
    }

    @Test
    fun `query shorter than DNS header is rejected`() {
        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.validateQuery(
                query = ByteArray(11),
                maxDnsMessageBytes = 4_096,
            )
        }
    }

    @Test
    fun `query larger than maximum is rejected`() {
        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.validateQuery(
                query = ByteArray(13),
                maxDnsMessageBytes = 12,
            )
        }
    }

    @Test
    fun `DoH upstream query uses zero transaction ID without mutating original query`() {
        val query = dnsQuery(id = 0x1234)

        val upstreamQuery =
            DnsOverHttpsMessage.copyQueryWithZeroTransactionId(
                query,
            )

        assertEquals(0x12, query[0].toInt() and 0xFF)
        assertEquals(0x34, query[1].toInt() and 0xFF)
        assertEquals(0, upstreamQuery[0].toInt() and 0xFF)
        assertEquals(0, upstreamQuery[1].toInt() and 0xFF)
    }

    @Test
    fun `restores original transaction ID after validating zero ID DoH response`() {
        val query = dnsQuery(id = 0x1234)
        val upstreamQuery =
            DnsOverHttpsMessage.copyQueryWithZeroTransactionId(
                query,
            )
        val upstreamResponse = dnsResponseFor(upstreamQuery)

        val validatedResponse =
            DnsOverHttpsMessage.readAndValidateResponse(
                query = upstreamQuery,
                input = ByteArrayInputStream(upstreamResponse),
                declaredContentLength = upstreamResponse.size.toLong(),
                maxDnsMessageBytes = 4_096,
            )

        val restoredResponse =
            DnsOverHttpsMessage.restoreTransactionId(
                response = validatedResponse,
                originalQuery = query,
            )

        assertEquals(0x12, query[0].toInt() and 0xFF)
        assertEquals(0x34, query[1].toInt() and 0xFF)
        assertEquals(0x12, restoredResponse[0].toInt() and 0xFF)
        assertEquals(0x34, restoredResponse[1].toInt() and 0xFF)
    }

    @Test
    fun `zero declared response length is rejected`() {
        val query = dnsQuery()

        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = ByteArrayInputStream(
                    dnsResponseFor(query),
                ),
                declaredContentLength = 0,
                maxDnsMessageBytes = 4_096,
            )
        }
    }

    @Test
    fun `oversized declared response length is rejected before reading`() {
        val query = dnsQuery()
        val input =
            object : ByteArrayInputStream(
                dnsResponseFor(query),
            ) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    throw AssertionError(
                        "Response body must not be read",
                    )
                }
            }

        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = input,
                declaredContentLength = 13,
                maxDnsMessageBytes = 12,
            )
        }
    }

    @Test
    fun `oversized unknown-length response is rejected by bounded streaming`() {
        val query = dnsQuery()

        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = ByteArrayInputStream(
                    ByteArray(13),
                ),
                declaredContentLength = -1,
                maxDnsMessageBytes = 12,
            )
        }
    }

    @Test
    fun `response with mismatched transaction ID is rejected`() {
        val query = dnsQuery()
        val response =
            dnsResponseFor(query).apply {
                this[1] =
                    (this[1].toInt() xor 0x01)
                        .toByte()
            }

        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = ByteArrayInputStream(response),
                declaredContentLength = response.size.toLong(),
                maxDnsMessageBytes = 4_096,
            )
        }
    }

    @Test
    fun `message without QR response bit is rejected`() {
        val query = dnsQuery()

        assertThrows(IOException::class.java) {
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = ByteArrayInputStream(query),
                declaredContentLength = query.size.toLong(),
                maxDnsMessageBytes = 4_096,
            )
        }
    }

    @Test
    fun `valid bounded DNS response is accepted`() {
        val query = dnsQuery()
        val response = dnsResponseFor(query)

        val actual =
            DnsOverHttpsMessage.readAndValidateResponse(
                query = query,
                input = ByteArrayInputStream(response),
                declaredContentLength = response.size.toLong(),
                maxDnsMessageBytes = response.size,
            )

        assertArrayEquals(
            response,
            actual,
        )
    }

    @Test
    fun `protected socket factory protects socket before connection`() {
        val factory =
            ProtectedVpnSocketFactory { socket ->
                assertFalse(socket.isConnected)
                true
            }

        factory.createSocket().use { socket ->
            assertFalse(socket.isConnected)
        }
    }

    @Test
    fun `protected socket factory fails closed when protection fails`() {
        val factory =
            ProtectedVpnSocketFactory {
                false
            }

        assertThrows(IOException::class.java) {
            factory.createSocket()
        }
    }

    private fun inetAddressOf(
        vararg bytes: Int,
    ): InetAddress =
        InetAddress.getByAddress(
            bytes.map { byte ->
                byte.toByte()
            }.toByteArray(),
        )

    private fun dnsQuery(
        id: Int = 0x1234,
    ): ByteArray =
        ByteArray(12).apply {
            this[0] =
                ((id ushr 8) and 0xFF)
                    .toByte()

            this[1] =
                (id and 0xFF)
                    .toByte()

            this[2] =
                0x01
        }

    private fun dnsResponseFor(
        query: ByteArray,
    ): ByteArray =
        query.copyOf().apply {
            this[2] =
                (
                    this[2]
                        .toInt() or
                        0x80
                    ).toByte()
        }
}
