package com.impulsive.app.backend.service.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingDiagnosticsTest {
    @Test
    fun `billing failure diagnostics include response code and debug message`() {
        val result = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
            .setDebugMessage("The requested offer is not eligible")
            .build()

        val message = billingResultFailureMessage("Purchase flow", result)

        assertTrue(message.contains("response code 5"))
        assertTrue(message.contains("The requested offer is not eligible"))
    }

    @Test
    fun `backend failure diagnostics include safe exception details`() {
        val message = backendVerificationFailureMessage(
            IllegalStateException("Callable service unavailable"),
        )

        assertTrue(message.contains("IllegalStateException"))
        assertTrue(message.contains("Callable service unavailable"))
    }

    @Test
    fun `backend failure diagnostics redact token shaped values`() {
        val rawToken = "abcdefghijklmnopqrstuvwxyz0123456789-secret"
        val message = backendVerificationFailureMessage(
            IllegalStateException("purchaseToken=$rawToken"),
        )

        assertFalse(message.contains(rawToken))
        assertTrue(message.contains("[redacted]"))
    }
}
