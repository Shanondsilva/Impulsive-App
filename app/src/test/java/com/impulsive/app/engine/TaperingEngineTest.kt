package com.impulsive.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaperingEngineTest {

    @Test
    fun `calculateNextTarget decrements by 1`() {
        assertEquals(6, TaperingEngine.calculateNextTarget(7))
        assertEquals(3, TaperingEngine.calculateNextTarget(4))
        assertEquals(2, TaperingEngine.calculateNextTarget(3))
    }

    @Test
    fun `calculateNextTarget floors at 1`() {
        assertEquals(1, TaperingEngine.calculateNextTarget(1))
        assertEquals(1, TaperingEngine.calculateNextTarget(0)) // defensive
    }

    @Test
    fun `shouldTaper returns true when used is at or under limit`() {
        assertTrue(TaperingEngine.shouldTaper(usedSessions = 5, allowedSessions = 7))
        assertTrue(TaperingEngine.shouldTaper(usedSessions = 7, allowedSessions = 7))
        assertTrue(TaperingEngine.shouldTaper(usedSessions = 0, allowedSessions = 7))
    }

    @Test
    fun `shouldTaper returns false when used exceeds limit`() {
        assertFalse(TaperingEngine.shouldTaper(usedSessions = 8, allowedSessions = 7))
        assertFalse(TaperingEngine.shouldTaper(usedSessions = 10, allowedSessions = 3))
    }

    @Test
    fun `resolveNextTarget tapers when under limit`() {
        assertEquals(6, TaperingEngine.resolveNextTarget(currentAllowed = 7, usedSessions = 5))
    }

    @Test
    fun `resolveNextTarget holds when over limit`() {
        assertEquals(7, TaperingEngine.resolveNextTarget(currentAllowed = 7, usedSessions = 9))
    }

    @Test
    fun `resolveNextTarget floors at 1 even when tapering`() {
        assertEquals(1, TaperingEngine.resolveNextTarget(currentAllowed = 1, usedSessions = 0))
    }
}
