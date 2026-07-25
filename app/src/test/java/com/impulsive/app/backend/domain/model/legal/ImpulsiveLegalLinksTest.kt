package com.impulsive.app.backend.domain.model.legal

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpulsiveLegalLinksTest {
    @Test
    fun `destinations map to exact canonical URLs`() {
        assertEquals(
            "https://useimpulsive.com/privacy",
            impulsiveLegalUrl(ImpulsiveLegalDestination.PrivacyPolicy),
        )
        assertEquals(
            "https://useimpulsive.com/terms",
            impulsiveLegalUrl(ImpulsiveLegalDestination.TermsOfService),
        )
        assertEquals(
            "https://useimpulsive.com/delete-account",
            impulsiveLegalUrl(ImpulsiveLegalDestination.AccountDeletionHelp),
        )
    }

    @Test
    fun `all legal URLs are public HTTPS Impulsive URLs without tracking data`() {
        val urls = ImpulsiveLegalDestination.entries.map(::impulsiveLegalUrl)

        urls.forEach { value ->
            val uri = URI(value)
            assertEquals("https", uri.scheme)
            assertEquals("useimpulsive.com", uri.host)
            assertEquals(null, uri.rawQuery)
            assertEquals(null, uri.rawFragment)
            assertTrue(uri.isAbsolute)
            assertFalse(value.contains('@'))
        }

        assertEquals(3, urls.toSet().size)
    }
}
