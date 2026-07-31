package com.impulsive.app.backend.session.adaptive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveDecisionExplanationLoaderTest {
    @Test
    fun missingDecisionFailsSafely() = runBlocking {
        val requested = mutableListOf<String>()
        val loader = AdaptiveDecisionExplanationLoader { id ->
            requested += id
            null
        }

        assertNull(loader.load(DecisionId))
        assertEquals(listOf(DecisionId), requested)
    }

    @Test
    fun malformedIdentifierFailsBeforePersistenceLookup() = runBlocking {
        var persistenceWasCalled = false
        val loader = AdaptiveDecisionExplanationLoader {
            persistenceWasCalled = true
            null
        }

        assertNull(loader.load("not-a-decision-id"))
        assertEquals(false, persistenceWasCalled)
    }

    private companion object {
        const val DecisionId = "00000000-0000-0000-0000-000000000402"
    }
}
