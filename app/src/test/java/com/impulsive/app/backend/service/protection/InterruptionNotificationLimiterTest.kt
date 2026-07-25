package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptionNotificationLimiterTest {
    @Test
    fun noEncounterSuppresses() {
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(
                packageName = "com.example.no.encounter",
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun firstFallbackClaimPostsImmediately() {
        val packageName = "com.example.first.immediate"
        val message = InterruptionNotificationLimiter.messageForApp(
            packageName = packageName,
            nowMillis = 1_000L,
        ) { "First message" }

        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 1_000L),
            message,
        )
    }

    @Test
    fun laterFallbackClaimsInSameEncounterAreSuppressed() {
        val packageName = "com.example.single.fallback"
        val message = InterruptionNotificationLimiter.messageForApp(packageName, 10_000L) {
            "Stable message"
        }

        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 10_000L),
            message,
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 30_000L),
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 200_000L),
        )
    }

    @Test
    fun encounterReusesItsSelectedMessage() {
        val packageName = "com.example.same.message"
        var selectorCalls = 0
        val message = InterruptionNotificationLimiter.messageForApp(packageName, 10_000L) {
            selectorCalls += 1
            "Stable message"
        }

        assertEquals(
            message,
            InterruptionNotificationLimiter.messageForApp(packageName, 30_000L) {
                selectorCalls += 1
                "Replacement message"
            },
        )
        assertEquals(1, selectorCalls)
    }

    @Test
    fun endingAppEncounterAllowsANewFallbackClaim() {
        val packageName = "com.example.encounter.reset"
        val oldMessage = InterruptionNotificationLimiter.messageForApp(packageName, 10_000L) {
            "Old message"
        }
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 10_000L),
            oldMessage,
        )
        InterruptionNotificationLimiter.endAppEncounter(packageName)

        val newMessage = InterruptionNotificationLimiter.messageForApp(packageName, 10_001L) {
            "New message"
        }
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 10_001L),
            newMessage,
        )
    }

    @Test
    fun domainEncounterExpiresAfterInactivity() {
        val domain = "expiry-test.example"
        val oldMessage = InterruptionNotificationLimiter.messageForDomain(domain, 1_000L) {
            "Old domain message"
        }
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForDomain(domain, 1_000L),
            oldMessage,
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForDomain(domain, 61_000L),
        )

        val newMessage = InterruptionNotificationLimiter.messageForDomain(domain, 61_000L) {
            "New domain message"
        }
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForDomain(domain, 61_000L),
            newMessage,
        )
    }

    private fun assertPost(
        decision: InterruptionNotificationDecision,
        expectedMessage: String,
    ) {
        assertTrue(decision is InterruptionNotificationDecision.Post)
        assertEquals(
            expectedMessage,
            (decision as InterruptionNotificationDecision.Post).message,
        )
    }

    private fun assertSuppressed(decision: InterruptionNotificationDecision) {
        assertEquals(InterruptionNotificationDecision.Suppress, decision)
    }
}
