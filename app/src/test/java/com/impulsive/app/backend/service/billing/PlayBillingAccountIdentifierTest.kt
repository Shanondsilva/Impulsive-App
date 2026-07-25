package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingAccountIdentifierTest {
    @Test
    fun `identifier is deterministic normalized and non-identifying`() {
        val uid = "firebase-user-123"
        val first = obfuscatedPlayBillingAccountId(uid)

        assertEquals(first, obfuscatedPlayBillingAccountId(uid))
        assertEquals(first, obfuscatedPlayBillingAccountId("  $uid  "))
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
        assertFalse(first.contains(uid))
    }

    @Test
    fun `different Firebase accounts have different identifiers`() {
        assertNotEquals(
            obfuscatedPlayBillingAccountId("firebase-user-123"),
            obfuscatedPlayBillingAccountId("firebase-user-456"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank Firebase UID is rejected`() {
        obfuscatedPlayBillingAccountId("   ")
    }
}
