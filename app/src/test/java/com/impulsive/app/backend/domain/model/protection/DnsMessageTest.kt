package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {
    private fun query(name: String, id: Int = 0x1234): ByteArray {
        val out = ArrayList<Byte>()
        out.add((id ushr 8).toByte())
        out.add(id.toByte())
        out.add(0x01.toByte())
        out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x01.toByte())
        out.add(0x00.toByte()); out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x00.toByte())
        for (label in name.split(".")) {
            out.add(label.length.toByte())
            for (c in label) out.add(c.code.toByte())
        }
        out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x01.toByte())
        out.add(0x00.toByte()); out.add(0x01.toByte())
        return out.toByteArray()
    }

    @Test
    fun readsQuestionName() {
        assertEquals("example.com", DnsMessage.readQuestionName(query("example.com")))
    }

    @Test
    fun readsSubdomainName() {
        assertEquals("cdn.example.com", DnsMessage.readQuestionName(query("cdn.example.com")))
    }

    @Test
    fun lowercasesName() {
        assertEquals("example.com", DnsMessage.readQuestionName(query("Example.COM")))
    }

    @Test
    fun buildsNxDomainResponse() {
        val response = DnsMessage.buildNxDomainResponse(query("example.com"))
        assertTrue(response != null)
        response!!
        assertEquals(1, (response[2].toInt() ushr 7) and 0x01)
        assertEquals(3, response[3].toInt() and 0x0F)
        assertEquals(0, readU16(response, 6))
        assertEquals("example.com", DnsMessage.readQuestionName(response))
    }

    @Test
    fun rejectsShortMessage() {
        assertNull(DnsMessage.readQuestionName(ByteArray(5)))
        assertNull(DnsMessage.buildNxDomainResponse(ByteArray(5)))
        assertNull(DnsMessage.buildServFailResponse(ByteArray(5)))
    }

    @Test
    fun buildsServFailResponse() {
        val original =
            query(
                "example.com",
                id = 0xBEEF,
            )

        val response =
            DnsMessage.buildServFailResponse(
                original,
            )

        assertTrue(response != null)
        response!!

        assertEquals(0xBEEF, readU16(response, 0))
        assertEquals(1, (response[2].toInt() ushr 7) and 0x01)
        assertEquals(2, response[3].toInt() and 0x0F)
        assertEquals(0, readU16(response, 6))
        assertEquals(0, readU16(response, 8))
        assertEquals(0, readU16(response, 10))
        assertEquals(
            "example.com",
            DnsMessage.readQuestionName(response),
        )
        assertArrayEquals(
            original.copyOfRange(12, original.size),
            response.copyOfRange(12, response.size),
        )
        assertEquals(
            original[2].toInt() and 0x01,
            response[2].toInt() and 0x01,
        )
    }

    @Test
    fun buildsAndReadsARecordResponse() {
        val original = query("www.google.com", id = 0xBEEF)
        val ipv4 = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)

        val response = DnsMessage.buildARecordResponse(original, ipv4, ttlSeconds = 300)

        assertTrue(response != null)
        response!!
        assertEquals(0xBEEF, readU16(response, 0))
        assertEquals(1, (response[2].toInt() ushr 7) and 0x01)
        assertEquals(1, readU16(response, 4))
        assertEquals(1, readU16(response, 6))
        assertEquals("www.google.com", DnsMessage.readQuestionName(response))
        assertArrayEquals(ipv4, DnsMessage.readFirstARecordIp(response))
    }

    @Test
    fun ARecordResponseRejectsNonAQuestionsAndInvalidAddresses() {
        val aaaaQuery = query("www.google.com").also { message ->
            message[message.size - 3] = 28
        }

        assertNull(DnsMessage.buildARecordResponse(aaaaQuery, byteArrayOf(1, 2, 3, 4)))
        assertNull(DnsMessage.buildARecordResponse(query("www.google.com"), byteArrayOf(1, 2, 3)))
        assertNull(DnsMessage.readFirstARecordIp(query("www.google.com")))
    }

    @Test
    fun buildsSafeSearchHostQueryFromTemplateHeader() {
        val template = query("www.google.com", id = 0xCAFE)

        val rewritten = DnsMessage.buildQueryForName(
            templateQuery = template,
            name = "forcesafesearch.google.com",
        )

        assertTrue(rewritten != null)
        rewritten!!
        assertEquals(0xCAFE, readU16(rewritten, 0))
        assertEquals(template[2], rewritten[2])
        assertEquals(template[3], rewritten[3])
        assertEquals("forcesafesearch.google.com", DnsMessage.readQuestionName(rewritten))
        assertEquals(1, readU16(rewritten, rewritten.size - 4))
        assertEquals(1, readU16(rewritten, rewritten.size - 2))
    }

    private fun rawQuery(qtype: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in listOf("www", "google", "com")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        out.write(byteArrayOf(((qtype shr 8) and 0xFF).toByte(), (qtype and 0xFF).toByte(), 0, 1))
        return out.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    @Test
    fun aaaaQuestionIsNotAInQuestion() {
        assertTrue(DnsMessage.isAInQuestion(rawQuery(1)))
        assertFalse(DnsMessage.isAInQuestion(rawQuery(28)))
        assertFalse(DnsMessage.isAInQuestion(rawQuery(65)))
    }

    @Test
    fun emptyNoErrorResponsePreservesQuestionAndSetsQrRa() {
        val query = rawQuery(28)
        val response = DnsMessage.buildEmptyNoErrorResponse(query)!!
        assertEquals(query.size, response.size)
        assertEquals(0xAB, response[0].toInt() and 0xFF)
        assertEquals(0xCD, response[1].toInt() and 0xFF)
        assertTrue((response[2].toInt() and 0x80) != 0)
        assertEquals(0x80, response[3].toInt() and 0xFF)
        assertEquals(1, u16(response, 4))
        assertEquals(0, u16(response, 6))
        assertEquals(0, u16(response, 8))
        assertEquals(0, u16(response, 10))
    }

    @Test
    fun nxDomainResponseSetsRecursionAvailableBit() {
        val response = DnsMessage.buildNxDomainResponse(rawQuery(1))!!
        assertEquals(0x83, response[3].toInt() and 0xFF)
    }
    private fun readU16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
