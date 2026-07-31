package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeToUsePolicyTest {
    @Test
    fun sameRevisionUseWithinSevenDaysIsCountedFactually() {
        val rehearsal = rehearsal(revision = 10L, completedAt = 1_000L)
        val use = use(revision = 10L, startedAt = 2_000L)

        val result = PracticeToUsePolicy.observe(listOf(rehearsal), listOf(use))

        assertEquals(1, result.completedRehearsals)
        assertEquals(setOf(use.decisionId), result.laterRealUseDecisionIds)
    }

    @Test
    fun editedRevisionIsNotMatched() {
        val result = PracticeToUsePolicy.observe(
            listOf(rehearsal(revision = 10L, completedAt = 1_000L)),
            listOf(use(revision = 11L, startedAt = 2_000L)),
        )

        assertEquals(0, result.laterRealUseCount)
    }

    @Test
    fun useOutsideSevenDaysIsNotCounted() {
        val completedAt = 1_000L
        val result = PracticeToUsePolicy.observe(
            listOf(rehearsal(revision = 10L, completedAt = completedAt)),
            listOf(
                use(
                    revision = 10L,
                    startedAt = completedAt + 7L * 86_400_000L + 1L,
                ),
            ),
        )

        assertEquals(0, result.laterRealUseCount)
    }

    @Test
    fun oneRealUseFollowingMultiplePracticesIsCountedOnce() {
        val use = use(revision = 10L, startedAt = 3_000L)
        val result = PracticeToUsePolicy.observe(
            listOf(
                rehearsal(revision = 10L, completedAt = 1_000L, idSuffix = "1"),
                rehearsal(revision = 10L, completedAt = 2_000L, idSuffix = "2"),
            ),
            listOf(use),
        )

        assertEquals(2, result.completedRehearsals)
        assertEquals(1, result.laterRealUseCount)
    }

    private fun rehearsal(
        revision: Long,
        completedAt: Long,
        idSuffix: String = "0",
    ) = MomentPlanRehearsal(
        rehearsalId = "00000000-0000-0000-0000-00000000090$idSuffix",
        planId = PlanId,
        planUpdatedAtMillisAtStart = revision,
        mode = MomentPlanRehearsalMode.Guided,
        startedAtMillis = completedAt - 100L,
        completedAtMillis = completedAt,
        planContentRevisionId = revisionId(revision),
    )

    private fun use(
        revision: Long,
        startedAt: Long,
    ) = MomentPlanUseRecord(
        decisionId = "00000000-0000-0000-0000-000000000800",
        planId = PlanId,
        planUpdatedAtMillis = revision,
        startedAtMillis = startedAt,
        planContentRevisionId = revisionId(revision),
    )

    private fun revisionId(revision: Long): String =
        "00000000-0000-0000-0000-${revision.toString().padStart(12, '0')}"

    private companion object {
        const val PlanId = "00000000-0000-0000-0000-000000000100"
    }
}
