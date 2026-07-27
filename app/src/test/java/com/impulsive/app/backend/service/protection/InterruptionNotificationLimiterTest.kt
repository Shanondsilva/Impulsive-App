package com.impulsive.app.backend.service.protection

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
    fun appIncidentSubmitsOnlyAtZeroTwentyAndFortySeconds() {
        val packageName = "com.example.bounded.fallback"
        val message = InterruptionNotificationLimiter.messageForApp(packageName, 10_000L) {
            "Stable message"
        }

        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 10_000L),
            message,
            InterruptionNotificationStage.Initial,
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 29_999L),
        )
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 30_000L),
            message,
            InterruptionNotificationStage.TwentySeconds,
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 30_000L),
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 49_999L),
        )
        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 50_000L),
            message,
            InterruptionNotificationStage.FortySeconds,
        )
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 200_000L),
        )
    }

    @Test
    fun concurrentEquivalentStageClaimsSubmitOnlyOnce() {
        val packageName = "com.example.concurrent.fallback"
        val message = InterruptionNotificationLimiter.messageForApp(packageName, 1_000L) {
            "Stable message"
        }

        val decisions = Collections.synchronizedList(
            mutableListOf<InterruptionNotificationDecision>(),
        )
        val start = CountDownLatch(1)
        val done = CountDownLatch(20)
        val executor = Executors.newFixedThreadPool(8)
        repeat(20) {
            executor.execute {
                start.await()
                decisions += InterruptionNotificationLimiter
                    .decideNotificationForApp(packageName, 1_000L)
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        executor.shutdownNow()

        assertEquals(1, decisions.count { it is InterruptionNotificationDecision.Post })
        assertPost(
            decisions.first { it is InterruptionNotificationDecision.Post },
            message,
            InterruptionNotificationStage.Initial,
        )
    }

    @Test
    fun persistedIncidentStartDoesNotRestartStagesOnRepeatedEvaluation() {
        val packageName = "com.example.persisted.fallback"
        val message = InterruptionNotificationLimiter.messageForApp(
            packageName = packageName,
            nowMillis = 25_000L,
            incidentStartedAtMillis = 5_000L,
        ) { "Stable message" }

        assertPost(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 25_000L),
            message,
            InterruptionNotificationStage.TwentySeconds,
        )
        InterruptionNotificationLimiter.messageForApp(
            packageName = packageName,
            nowMillis = 25_001L,
            incidentStartedAtMillis = 5_000L,
        ) { "Replacement message" }
        assertSuppressed(
            InterruptionNotificationLimiter.decideNotificationForApp(packageName, 25_001L),
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
        expectedStage: InterruptionNotificationStage =
            InterruptionNotificationStage.Initial,
    ) {
        assertTrue(decision is InterruptionNotificationDecision.Post)
        assertEquals(
            expectedMessage,
            (decision as InterruptionNotificationDecision.Post).message,
        )
        assertEquals(expectedStage, decision.stage)
    }

    private fun assertSuppressed(decision: InterruptionNotificationDecision) {
        assertEquals(InterruptionNotificationDecision.Suppress, decision)
    }
}
