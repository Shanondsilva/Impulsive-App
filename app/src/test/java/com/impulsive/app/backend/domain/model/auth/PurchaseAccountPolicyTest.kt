package com.impulsive.app.backend.domain.model.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAccountPolicyTest {
    private val guest = AuthUser(
        uid = "guest-uid",
        displayName = null,
        email = null,
        provider = AuthProvider.Guest,
        linkedProviders = setOf(AuthProvider.Guest),
    )

    @Test
    fun `guest is not durable and requires an account`() {
        assertFalse(guest.isDurablePurchaseAccount())
        assertEquals(
            PurchaseAccountGatePhase.RequiresDurableAccount,
            phase(user = guest),
        )
    }

    @Test
    fun `supported linked providers are durable`() {
        listOf(
            AuthProvider.Google,
            AuthProvider.Facebook,
            AuthProvider.Email,
        ).forEach { provider ->
            val user = AuthUser(
                uid = "durable-${provider.name}",
                displayName = null,
                email = null,
                provider = provider,
                linkedProviders = setOf(provider),
            )

            assertTrue(user.isDurablePurchaseAccount())
            assertEquals(PurchaseAccountGatePhase.Ready, phase(user = user))
        }
    }

    @Test
    fun `missing user requires a durable account`() {
        assertFalse(null.isDurablePurchaseAccount())
        assertEquals(PurchaseAccountGatePhase.RequiresDurableAccount, phase(user = null))
    }

    @Test
    fun `guest provider flow is linking until it finishes`() {
        assertEquals(
            PurchaseAccountGatePhase.Linking(AuthProvider.Google),
            phase(user = guest, inFlightProvider = AuthProvider.Google),
        )
    }

    @Test
    fun `account conflict has highest priority`() {
        assertEquals(
            PurchaseAccountGatePhase.AccountConflict,
            phase(
                user = guest,
                inFlightProvider = AuthProvider.Google,
                pendingEmail = "person@example.com",
                hasConflict = true,
            ),
        )
    }

    @Test
    fun `cancelled linking returns guest to blocked phase`() {
        assertEquals(
            PurchaseAccountGatePhase.RequiresDurableAccount,
            phase(user = guest),
        )
    }

    @Test
    fun `pending email verification blocks purchase`() {
        assertEquals(
            PurchaseAccountGatePhase.AwaitingEmailVerification("person@example.com"),
            phase(user = guest, pendingEmail = "person@example.com"),
        )
    }

    private fun phase(
        user: AuthUser?,
        inFlightProvider: AuthProvider? = null,
        pendingEmail: String? = null,
        hasConflict: Boolean = false,
    ): PurchaseAccountGatePhase = resolvePurchaseAccountGatePhase(
        user = user,
        inFlightProvider = inFlightProvider,
        pendingEmailVerificationAddress = pendingEmail,
        hasAccountConflict = hasConflict,
    )
}
