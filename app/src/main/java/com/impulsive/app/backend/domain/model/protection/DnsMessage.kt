package com.impulsive.app.backend.domain.model.protection

/**
 * Pure helpers for reading and answering DNS messages. readQuestionName pulls the queried hostname
 * out of a DNS query, and buildNxDomainResponse turns a query into a name-does-not-exist reply so a
 * blocked lookup fails cleanly. No Android dependencies. A query question name is never compressed,
 * so a compression pointer is treated as unsupported and returns null.
 */
object DnsMessage {
    private const val HEADER_BYTES = 12
    private const val POINTER_MASK = 0xC0
    private const val NXDOMAIN_RCODE = 0x03

    fun readQuestionName(message: ByteArray): String? {
        if (message.size < HEADER_BYTES) return null
        val builder = StringBuilder()
        var i = HEADER_BYTES
        while (i < message.size) {
            val length = message[i].toInt() and 0xFF
            if (length == 0) break
            if (length and POINTER_MASK != 0) return null
            if (i + 1 + length > message.size) return null
            if (builder.isNotEmpty()) builder.append('.')
            for (j in 0 until length) {
                builder.append((message[i + 1 + j].toInt() and 0xFF).toChar())
            }
            i += 1 + length
        }
        if (builder.isEmpty()) return null
        return builder.toString().lowercase()
    }

    fun buildNxDomainResponse(query: ByteArray): ByteArray? {
        if (query.size < HEADER_BYTES) return null
        val questionEnd = questionEndOffset(query) ?: return null
        val response = query.copyOfRange(0, questionEnd)
        val flagsByte = query[2].toInt() and 0xFF
        val opcode = (flagsByte ushr 3) and 0x0F
        val recursionDesired = flagsByte and 0x01
        response[2] = ((1 shl 7) or (opcode shl 3) or recursionDesired).toByte()
        response[3] = NXDOMAIN_RCODE.toByte()
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    private fun questionEndOffset(message: ByteArray): Int? {
        var i = HEADER_BYTES
        while (i < message.size) {
            val length = message[i].toInt() and 0xFF
            if (length == 0) {
                i += 1
                break
            }
            if (length and POINTER_MASK != 0) return null
            i += 1 + length
        }
        val end = i + 4
        if (end > message.size) return null
        return end
    }

    private fun writeU16(b: ByteArray, i: Int, value: Int) {
        b[i] = ((value ushr 8) and 0xFF).toByte()
        b[i + 1] = (value and 0xFF).toByte()
    }
}
