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
    private const val SERVFAIL_RCODE = 0x02
    private const val NXDOMAIN_RCODE = 0x03
    private const val TYPE_A = 0x0001
    private const val CLASS_IN = 0x0001

    // Compression-pointer name (2) + type (2) + class (2) + ttl (4) + rdlength (2) + IPv4 (4).
    private const val ANSWER_BYTES = 16

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
        response[3] = (0x80 or NXDOMAIN_RCODE).toByte()
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    /**
     * True when the single question in [query] is an A/IN question. SafeSearch
     * rewriting only produces IPv4 answers; every other qtype must get an
     * empty NOERROR so clients fall back to the rewritten A record.
     */
    fun isAInQuestion(query: ByteArray): Boolean {
        val questionEnd = questionEndOffset(query) ?: return false
        val qType = ((query[questionEnd - 4].toInt() and 0xFF) shl 8) or
            (query[questionEnd - 3].toInt() and 0xFF)
        val qClass = ((query[questionEnd - 2].toInt() and 0xFF) shl 8) or
            (query[questionEnd - 1].toInt() and 0xFF)
        return qType == TYPE_A && qClass == CLASS_IN
    }

    /**
     * NOERROR response with zero answers: QR=1, RA=1, opcode and RD copied
     * from the query, question preserved, ANCOUNT/NSCOUNT/ARCOUNT zeroed.
     * Returned for non-A questions (AAAA, HTTPS/type 65) to SafeSearch hosts
     * so IPv6 cannot bypass the rewritten A record.
     */
    fun buildEmptyNoErrorResponse(query: ByteArray): ByteArray? {
        if (query.size < HEADER_BYTES) return null
        val questionEnd = questionEndOffset(query) ?: return null
        val response = query.copyOfRange(0, questionEnd)
        val flagsByte = query[2].toInt() and 0xFF
        val opcode = (flagsByte ushr 3) and 0x0F
        val recursionDesired = flagsByte and 0x01
        response[2] = ((1 shl 7) or (opcode shl 3) or recursionDesired).toByte()
        response[3] = 0x80.toByte()
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    fun buildServFailResponse(
        query: ByteArray,
    ): ByteArray? {
        if (
            query.size < HEADER_BYTES
        ) {
            return null
        }

        val questionCount =
            readU16(
                query,
                4,
            )

        var questionSectionEnd =
            HEADER_BYTES

        repeat(questionCount) {
            questionSectionEnd =
                nameEndOffset(
                    query,
                    questionSectionEnd,
                )
                    ?: return null

            if (
                questionSectionEnd + 4 >
                query.size
            ) {
                return null
            }

            questionSectionEnd += 4
        }

        val response =
            query.copyOfRange(
                0,
                questionSectionEnd,
            )

        val originalFlagsHigh =
            query[2].toInt() and 0xFF

        val opcode =
            originalFlagsHigh and 0x78

        val recursionDesired =
            originalFlagsHigh and 0x01

        response[2] =
            (
                0x80 or
                    opcode or
                    recursionDesired
                ).toByte()

        // RA = 1 because this local resolver normally provides recursive
        // resolution through an upstream recursive DNS service.
        // RCODE = SERVFAIL (2).
        response[3] =
            (
                0x80 or
                    SERVFAIL_RCODE
                ).toByte()

        writeU16(
            response,
            6,
            0,
        )

        writeU16(
            response,
            8,
            0,
        )

        writeU16(
            response,
            10,
            0,
        )

        return response
    }

    /**
     * Builds a positive DNS response that answers [query] with a single A record
     * pointing at [ipv4] (a 4-byte IPv4 address). Only answers A/IN questions;
     * returns null otherwise (e.g. AAAA) so the caller falls back to normal
     * forwarding. Uses a 0xC00C compression pointer to the question name.
     */
    fun buildARecordResponse(
        query: ByteArray,
        ipv4: ByteArray,
        ttlSeconds: Int = 300,
    ): ByteArray? {
        if (ipv4.size != 4) return null
        val questionEnd = questionEndOffset(query) ?: return null
        val qType = ((query[questionEnd - 4].toInt() and 0xFF) shl 8) or
            (query[questionEnd - 3].toInt() and 0xFF)
        val qClass = ((query[questionEnd - 2].toInt() and 0xFF) shl 8) or
            (query[questionEnd - 1].toInt() and 0xFF)
        if (qType != TYPE_A || qClass != CLASS_IN) return null

        val answer = ByteArray(ANSWER_BYTES)
        answer[0] = POINTER_MASK.toByte()
        answer[1] = 0x0C
        writeU16(answer, 2, TYPE_A)
        writeU16(answer, 4, CLASS_IN)
        answer[6] = ((ttlSeconds ushr 24) and 0xFF).toByte()
        answer[7] = ((ttlSeconds ushr 16) and 0xFF).toByte()
        answer[8] = ((ttlSeconds ushr 8) and 0xFF).toByte()
        answer[9] = (ttlSeconds and 0xFF).toByte()
        writeU16(answer, 10, 4)
        System.arraycopy(ipv4, 0, answer, 12, 4)

        val response = ByteArray(questionEnd + ANSWER_BYTES)
        System.arraycopy(query, 0, response, 0, questionEnd)
        System.arraycopy(answer, 0, response, questionEnd, ANSWER_BYTES)

        val flagsByte = query[2].toInt() and 0xFF
        val opcode = (flagsByte ushr 3) and 0x0F
        val recursionDesired = flagsByte and 0x01
        response[2] = ((1 shl 7) or (opcode shl 3) or recursionDesired).toByte()
        response[3] = 0x80.toByte()
        writeU16(response, 6, 1)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    /**
     * Returns the first A-record IPv4 (4 bytes) from a DNS response, or null if
     * there is none. Handles compressed or literal answer names.
     */
    fun readFirstARecordIp(message: ByteArray): ByteArray? {
        if (message.size < HEADER_BYTES) return null
        val questionCount = readU16(message, 4)
        val answerCount = readU16(message, 6)
        if (answerCount < 1) return null

        var offset = HEADER_BYTES
        repeat(questionCount) {
            offset = nameEndOffset(message, offset) ?: return null
            if (offset + 4 > message.size) return null
            offset += 4
        }

        repeat(answerCount) {
            offset = nameEndOffset(message, offset) ?: return null
            if (offset + 10 > message.size) return null

            val answerType = readU16(message, offset)
            val dataLength = readU16(message, offset + 8)
            val dataStart = offset + 10
            if (dataStart + dataLength > message.size) return null
            if (answerType == TYPE_A && dataLength == 4) {
                return message.copyOfRange(dataStart, dataStart + 4)
            }
            offset = dataStart + dataLength
        }
        return null
    }

    /**
     * Builds a new A/IN query for [name] reusing the header (id + flags) of
     * [templateQuery]. Returns null if the template has no valid header.
     */
    fun buildQueryForName(templateQuery: ByteArray, name: String): ByteArray? {
        if (templateQuery.size < HEADER_BYTES) return null
        val header = templateQuery.copyOfRange(0, HEADER_BYTES)
        writeU16(header, 4, 1)
        writeU16(header, 6, 0)
        writeU16(header, 8, 0)
        writeU16(header, 10, 0)
        val out = ArrayList<Byte>(header.size + name.length + 6)
        out.addAll(header.toList())
        for (label in name.split('.')) {
            if (label.isEmpty() || label.length > 63) return null
            out.add(label.length.toByte())
            for (character in label) out.add(character.code.toByte())
        }
        out.add(0)
        out.add(0)
        out.add(1)
        out.add(0)
        out.add(1)
        return out.toByteArray()
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

    private fun nameEndOffset(message: ByteArray, start: Int): Int? {
        var i = start
        while (i < message.size) {
            val length = message[i].toInt() and 0xFF
            if (length == 0) return i + 1
            if (length and POINTER_MASK != 0) {
                return if (i + 2 <= message.size) i + 2 else null
            }
            if (i + 1 + length > message.size) return null
            i += 1 + length
        }
        return null
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)

    private fun writeU16(b: ByteArray, i: Int, value: Int) {
        b[i] = ((value ushr 8) and 0xFF).toByte()
        b[i + 1] = (value and 0xFF).toByte()
    }
}
