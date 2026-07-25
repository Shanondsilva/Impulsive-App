package com.impulsive.app.backend.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseAuthRepositoryMappingTest {
    @Test
    fun `maps every linked Firebase provider`() {
        val providers = linkedAuthProviders(
            providerIds = listOf(
                GoogleAuthProvider.PROVIDER_ID,
                EmailAuthProvider.PROVIDER_ID,
                FacebookAuthProvider.PROVIDER_ID,
            ),
            isAnonymous = false,
        )

        assertEquals(
            setOf(AuthProvider.Google, AuthProvider.Email, AuthProvider.Facebook),
            providers,
        )
    }

    @Test
    fun `forced provider fills an incompletely hydrated provider list`() {
        val providers = linkedAuthProviders(
            providerIds = emptyList(),
            isAnonymous = false,
            forced = AuthProvider.Google,
        )

        assertEquals(setOf(AuthProvider.Google), providers)
    }

    @Test
    fun `accepts complete deletion response`() {
        requireSuccessfulAccountDeletionResponse(
            mapOf("success" to true, "authUserDeleted" to true),
        )
    }

    @Test
    fun `rejects partial deletion response`() {
        assertThrows(IllegalStateException::class.java) {
            requireSuccessfulAccountDeletionResponse(
                mapOf("success" to true, "authUserDeleted" to false),
            )
        }
    }

    @Test
    fun `rejects invalid deletion responses`() {
        val invalidResponses = listOf(
            emptyMap<String, Any?>(),
            mapOf("success" to false, "authUserDeleted" to true),
            mapOf("success" to true),
            mapOf("success" to true, "authUserDeleted" to false),
            "not-a-map-response",
        )

        invalidResponses.forEach { response ->
            assertThrows(IllegalStateException::class.java) {
                requireSuccessfulAccountDeletionResponse(response)
            }
        }
    }
}
