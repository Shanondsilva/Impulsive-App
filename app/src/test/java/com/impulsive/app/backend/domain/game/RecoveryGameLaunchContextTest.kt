package com.impulsive.app.backend.domain.game

import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecoveryGameLaunchContextTest {
    @Test
    fun standalonePreservesDefaultAndSupportCycleUsesSmallerBound() {
        assertEquals(
            90_000L,
            RecoveryGameLaunchContext.Standalone.boundedDurationMillis(90_000L),
        )
        val support = RecoveryGameLaunchContext.SupportCycle(
            cycleId = "cycle-1",
            decisionId = "decision-1",
            gameType = ScoreGameType.ReflexOverride,
            maxDurationMillis = 50_000L,
        )
        assertEquals(50_000L, support.boundedDurationMillis(90_000L))
    }

    @Test
    fun supportLaunchContractContainsNoProtectedSourceFields() {
        val fieldNames = RecoveryGameLaunchContext.SupportCycle::class.java
            .declaredFields
            .map { it.name.lowercase() }
        listOf("url", "domain", "package", "incident", "source").forEach { prohibited ->
            assertFalse(fieldNames.any { prohibited in it })
        }
    }
}
