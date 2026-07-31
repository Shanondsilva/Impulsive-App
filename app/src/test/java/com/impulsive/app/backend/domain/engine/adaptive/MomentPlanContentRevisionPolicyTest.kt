package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanContentRevisionPolicyTest {
    @Test
    fun newPlanUsesInjectedOpaqueUuid() {
        val expected = UUID.randomUUID().toString()

        val revision = policy(expected).revisionForNewPlan()

        assertEquals(expected, revision)
        assertTrue(MomentPlanContentRevisionIds.isOpaqueUuid(revision))
    }

    @Test
    fun metadataOnlyTitleEditPreservesRevision() {
        assertMetadataPreserves { copy(title = "A different display label") }
    }

    @Test
    fun enableDisablePreservesRevision() {
        assertMetadataPreserves { copy(enabled = !enabled) }
    }

    @Test
    fun preferredChangePreservesRevision() {
        assertMetadataPreserves { copy(preferredForCue = !preferredForCue) }
    }

    @Test
    fun rehearsalTimestampChangePreservesRevision() {
        assertMetadataPreserves { copy(rehearsedAtMillis = 9_999L) }
    }

    @Test
    fun metadataUpdatedTimestampChangePreservesRevision() {
        assertMetadataPreserves { copy(updatedAtMillis = updatedAtMillis + 1L) }
    }

    @Test
    fun actionTextEditChangesRevision() {
        assertMeaningfulChangeRotates { copy(actionText = "Walk outside") }
    }

    @Test
    fun cueEditChangesRevision() {
        assertMeaningfulChangeRotates { copy(momentCue = MomentCue.Stress) }
    }

    @Test
    fun futureSelfStatementEditChangesRevision() {
        assertMeaningfulChangeRotates { copy(futureCueText = "I will feel rested tomorrow") }
    }

    @Test
    fun actionTargetEditChangesRevision() {
        val external = plan().copy(
            actionType = MomentPlanActionType.LaunchSelectedApp,
            actionTarget = "com.example.first",
        )
        val next = UUID.randomUUID().toString()

        val revision = policy(next).revisionForEdit(
            external,
            external.copy(actionTarget = "com.example.second"),
        )

        assertEquals(next, revision)
    }

    @Test
    fun approvedDestinationEditChangesRevision() {
        val destination = plan().copy(
            actionType = MomentPlanActionType.OpenImpulsiveDestination,
            actionTarget = ImpulsiveDestination.Focus.storageValue,
        )
        val next = UUID.randomUUID().toString()

        val revision = policy(next).revisionForEdit(
            destination,
            destination.copy(actionTarget = ImpulsiveDestination.Journal.storageValue),
        )

        assertEquals(next, revision)
    }

    @Test
    fun actionTypeEditChangesRevision() {
        assertMeaningfulChangeRotates {
            copy(
                actionType = MomentPlanActionType.OpenImpulsiveDestination,
                actionTarget = ImpulsiveDestination.Focus.storageValue,
            )
        }
    }

    @Test
    fun canonicalLineEndingsAndOuterWhitespaceDoNotRotateRevision() {
        assertMetadataPreserves {
            copy(
                actionText = "  $actionText\r\n",
                futureCueText = "\n$futureCueText  ",
            )
        }
    }

    @Test
    fun legacyFactoryMapsSameHistoricalPairToSameRevision() {
        val first = LegacyMomentPlanContentRevisionFactory.create(PlanId, 1_234L)
        val second = LegacyMomentPlanContentRevisionFactory.create(PlanId, 1_234L)

        assertEquals(first, second)
        assertTrue(MomentPlanContentRevisionIds.isOpaqueUuid(first))
    }

    @Test
    fun legacyFactoryMapsDifferentTimestampsToDifferentRevisions() {
        assertNotEquals(
            LegacyMomentPlanContentRevisionFactory.create(PlanId, 1_234L),
            LegacyMomentPlanContentRevisionFactory.create(PlanId, 1_235L),
        )
    }

    @Test
    fun revisionIdentifiersContainNoRawPlanContent() {
        val rawContent = "Call my private support person"
        val random = policy(UUID.randomUUID().toString()).revisionForNewPlan()
        val legacy = LegacyMomentPlanContentRevisionFactory.create(PlanId, 1_234L)

        assertFalse(random.contains(rawContent, ignoreCase = true))
        assertFalse(legacy.contains(rawContent, ignoreCase = true))
        assertEquals(36, random.length)
        assertEquals(36, legacy.length)
    }

    private fun assertMetadataPreserves(change: MomentPlan.() -> MomentPlan) {
        val existing = plan()
        val result = policy(UUID.randomUUID().toString())
            .revisionForEdit(existing, existing.change())

        assertEquals(existing.contentRevisionId, result)
    }

    private fun assertMeaningfulChangeRotates(change: MomentPlan.() -> MomentPlan) {
        val existing = plan()
        val next = UUID.randomUUID().toString()
        val result = policy(next).revisionForEdit(existing, existing.change())

        assertEquals(next, result)
        assertNotEquals(existing.contentRevisionId, result)
    }

    private fun policy(id: String) =
        MomentPlanContentRevisionPolicy(MomentPlanContentRevisionIdSource { id })

    private fun plan() = MomentPlan(
        planId = PlanId,
        title = "Evening reset",
        momentCue = MomentCue.Boredom,
        actionText = "Take a short walk",
        futureCueText = "I will feel clear tomorrow",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
        contentRevisionId = ExistingRevisionId,
    )

    private companion object {
        const val PlanId = "00000000-0000-4000-8000-000000000100"
        const val ExistingRevisionId = "00000000-0000-4000-8000-000000000200"
    }
}
