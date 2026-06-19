package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
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
    }

    private fun readU16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
