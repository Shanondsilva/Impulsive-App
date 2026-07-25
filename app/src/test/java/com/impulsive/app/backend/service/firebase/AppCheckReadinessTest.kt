package com.impulsive.app.backend.service.firebase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppCheckReadinessTest {
    @Test
    fun `ready App Check allows protected call`() = runBlocking {
        var callCount = 0

        val result = runAfterAppCheckReadiness(
            readinessProvider = { AppCheckReadinessResult.Ready },
            call = {
                callCount += 1
                "server-result"
            },
        )

        assertEquals(1, callCount)
        assertEquals(AppCheckGatedCallResult.Executed("server-result"), result)
    }

    @Test
    fun `temporarily unavailable App Check prevents protected call`() = runBlocking {
        var callExecuted = false
        val cause = IllegalStateException("token exchange pending")

        val result = runAfterAppCheckReadiness(
            readinessProvider = {
                AppCheckReadinessResult.TemporarilyUnavailable(cause)
            },
            call = {
                callExecuted = true
            },
        )

        assertFalse(callExecuted)
        assertEquals(AppCheckGatedCallResult.TemporarilyUnavailable(cause), result)
    }

    @Test
    fun `subsequent readiness attempt executes call only after ready`() = runBlocking {
        var readinessAttempt = 0
        var callCount = 0
        val readinessProvider: suspend () -> AppCheckReadinessResult = {
            readinessAttempt += 1
            if (readinessAttempt == 1) {
                AppCheckReadinessResult.TemporarilyUnavailable(null)
            } else {
                AppCheckReadinessResult.Ready
            }
        }
        val protectedCall: suspend () -> Unit = { callCount += 1 }

        val first = runAfterAppCheckReadiness(readinessProvider, protectedCall)
        val second = runAfterAppCheckReadiness(readinessProvider, protectedCall)

        assertTrue(first is AppCheckGatedCallResult.TemporarilyUnavailable)
        assertTrue(second is AppCheckGatedCallResult.Executed)
        assertEquals(1, callCount)
    }

    @Test
    fun `cancellation from readiness provider propagates`() = runBlocking {
        var callExecuted = false
        val cancellation = CancellationException("caller cancelled")

        try {
            runAfterAppCheckReadiness(
                readinessProvider = { throw cancellation },
                call = { callExecuted = true },
            )
            fail("CancellationException should propagate.")
        } catch (caught: CancellationException) {
            assertEquals(cancellation, caught)
        }

        assertFalse(callExecuted)
    }
    @Test
    fun `readiness diagnostic never includes throwable message or token`() {
        val rawToken = "abcdefghijklmnopqrstuvwxyz0123456789-secret"

        val message = appCheckReadinessFailureLogMessage(
            IllegalStateException("App Check token=$rawToken"),
        )

        assertTrue(message.contains("IllegalStateException"))
        assertFalse(message.contains(rawToken))
        assertFalse(message.contains("App Check token="))
    }
}
