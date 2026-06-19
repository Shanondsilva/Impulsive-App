package com.impulsive.app.backend.domain.model.protection

/**
 * Pure helpers for the DNS-only VPN tunnel. Parses an outbound IPv4 + UDP packet captured from the
 * tun device, and builds an IPv4 + UDP reply with correct header and checksum fields. No Android
 * dependencies, so the checksum math is unit tested directly. IPv6 and non-UDP packets are out of
 * scope: parse returns null for them, which is fine because the tunnel only routes the DNS resolver
 * address.
 */
object DnsPacket {
    private const val IPV4_VERSION = 4
    private const val PROTOCOL_UDP = 17

    data class Udp4(
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val payload: ByteArray,
    )

    fun parseIpv4Udp(packet: ByteArray, length: Int): Udp4? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != IPV4_VERSION) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null
        val sourceIp = packet.copyOfRange(12, 16)
        val destIp = packet.copyOfRange(16, 20)
        val udp = ihl
        val sourcePort = readU16(packet, udp)
        val destPort = readU16(packet, udp + 2)
        val udpLength = readU16(packet, udp + 4)
        val payloadStart = udp + 8
        val payloadEnd = minOf(udp + udpLength, length)
        if (payloadEnd < payloadStart) return null
        val payload = packet.copyOfRange(payloadStart, payloadEnd)
        return Udp4(sourceIp, destIp, sourcePort, destPort, payload)
    }

    fun buildIpv4Udp(
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray,
    ): ByteArray {
        require(sourceIp.size == 4 && destIp.size == 4) { "IPv4 addresses must be 4 bytes" }
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = ((IPV4_VERSION shl 4) or 5).toByte()
        packet[1] = 0
        writeU16(packet, 2, totalLength)
        writeU16(packet, 4, 0)
        writeU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = PROTOCOL_UDP.toByte()
        writeU16(packet, 10, 0)
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)
        writeU16(packet, 10, checksum(packet, 0, 20))
        val u = 20
        writeU16(packet, u, sourcePort)
        writeU16(packet, u + 2, destPort)
        writeU16(packet, u + 4, udpLength)
        writeU16(packet, u + 6, 0)
        System.arraycopy(payload, 0, packet, u + 8, payload.size)
        val udpSum = udpChecksum(sourceIp, destIp, packet, u, udpLength)
        writeU16(packet, u + 6, if (udpSum == 0) 0xFFFF else udpSum)
        return packet
    }

    private fun readU16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun writeU16(b: ByteArray, i: Int, value: Int) {
        b[i] = ((value ushr 8) and 0xFF).toByte()
        b[i + 1] = (value and 0xFF).toByte()
    }

    private fun checksum(data: ByteArray, offset: Int, count: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + count
        while (i + 1 < end) {
            sum += readU16(data, i).toLong()
            i += 2
        }
        if (i < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum shr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun udpChecksum(
        sourceIp: ByteArray,
        destIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = 0L
        sum += readU16(sourceIp, 0).toLong() + readU16(sourceIp, 2).toLong()
        sum += readU16(destIp, 0).toLong() + readU16(destIp, 2).toLong()
        sum += PROTOCOL_UDP.toLong()
        sum += udpLength.toLong()
        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            sum += readU16(packet, i).toLong()
            i += 2
        }
        if (i < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum shr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}
