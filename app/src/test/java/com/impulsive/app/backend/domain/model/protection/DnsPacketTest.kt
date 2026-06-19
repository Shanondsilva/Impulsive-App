package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {
    private val sourceIp = byteArrayOf(10, 0, 0, 1)
    private val destIp = byteArrayOf(10, 0, 0, 2)
    private val payload = ByteArray(40) { it.toByte() }

    @Test
    fun roundTripPreservesFields() {
        val packet = DnsPacket.buildIpv4Udp(sourceIp, destIp, 53, 40000, payload)
        val parsed = DnsPacket.parseIpv4Udp(packet, packet.size)
        assertTrue(parsed != null)
        parsed!!
        assertArrayEquals(sourceIp, parsed.sourceIp)
        assertArrayEquals(destIp, parsed.destIp)
        assertEquals(53, parsed.sourcePort)
        assertEquals(40000, parsed.destPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun ipv4HeaderChecksumIsValid() {
        val packet = DnsPacket.buildIpv4Udp(sourceIp, destIp, 53, 40000, payload)
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += readU16(packet, i)
            i += 2
        }
        assertEquals(0xFFFF, fold(sum))
    }

    @Test
    fun udpChecksumIsValid() {
        val packet = DnsPacket.buildIpv4Udp(sourceIp, destIp, 53, 40000, payload)
        val udpLength = readU16(packet, 24)
        var sum = readU16(sourceIp, 0) + readU16(sourceIp, 2) +
            readU16(destIp, 0) + readU16(destIp, 2) + 17 + udpLength
        var i = 20
        val end = 20 + udpLength
        while (i + 1 < end) {
            sum += readU16(packet, i)
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        assertEquals(0xFFFF, fold(sum))
    }

    @Test
    fun parseRejectsNonUdp() {
        val notUdp = byteArrayOf(
            0x45, 0, 0, 28, 0, 0, 0x40, 0, 64, 6, 0, 0,
            10, 0, 0, 1, 10, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0,
        )
        assertNull(DnsPacket.parseIpv4Udp(notUdp, notUdp.size))
    }

    @Test
    fun parseRejectsShortPacket() {
        assertNull(DnsPacket.parseIpv4Udp(ByteArray(10), 10))
    }

    private fun readU16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun fold(value: Int): Int {
        var v = value
        while ((v shr 16) != 0) {
            v = (v and 0xFFFF) + (v shr 16)
        }
        return v
    }
}
