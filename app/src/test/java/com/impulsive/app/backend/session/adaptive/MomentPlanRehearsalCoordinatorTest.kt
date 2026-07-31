package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRehearsalRepository
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanRehearsalCoordinatorTest {
    @Test
    fun guidedStartCreatesOneEventAndDoubleTapReusesIt() = runBlocking {
        val fixture = fixture()

        val first = fixture.coordinator.startGuided(fixture.planId)
        val second = fixture.coordinator.startGuided(fixture.planId)

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.session?.rehearsal?.rehearsalId, second.session?.rehearsal?.rehearsalId)
        assertEquals(1, fixture.rehearsals.insertCalls)
        assertEquals(MomentPlanRehearsalMode.Guided, first.session?.rehearsal?.mode)
        assertEquals(
            first.session?.plan?.contentRevisionId,
            first.session?.rehearsal?.planContentRevisionId,
        )
    }

    @Test
    fun quickStartCreatesQuickEventOnce() = runBlocking {
        val fixture = fixture()

        val result = fixture.coordinator.startQuick(fixture.planId)

        assertTrue(result.created)
        assertEquals(MomentPlanRehearsalMode.Quick, result.session?.rehearsal?.mode)
        assertEquals(1, fixture.rehearsals.events.size)
    }

    @Test
    fun startRequiresExistingEnabledValidPlan() = runBlocking {
        val missing = fixture()
        val disabled = fixture(enabled = false)

        assertEquals(
            RehearsalStartFailure.PlanUnavailable,
            missing.coordinator.startGuided(UUID.randomUUID().toString()).failure,
        )
        assertEquals(
            RehearsalStartFailure.PlanDisabled,
            disabled.coordinator.startGuided(disabled.planId).failure,
        )
        assertTrue(missing.rehearsals.events.isEmpty())
        assertTrue(disabled.rehearsals.events.isEmpty())
    }

    @Test
    fun completionIsIdempotentAndUpdatesLatestPractice() = runBlocking {
        val fixture = fixture()
        val rehearsalId = checkNotNull(
            fixture.coordinator.startGuided(fixture.planId).session,
        ).rehearsal.rehearsalId
        fixture.clock.current += 2_000L

        assertEquals(RehearsalTerminalResult.Applied, fixture.coordinator.complete(rehearsalId))
        assertEquals(
            RehearsalTerminalResult.AlreadyCompleted,
            fixture.coordinator.complete(rehearsalId),
        )

        val event = fixture.rehearsals.events.single()
        assertEquals(fixture.clock.current, event.completedAtMillis)
        assertNull(event.dismissedAtMillis)
        assertEquals(fixture.clock.current, fixture.plans.plans.value.single().rehearsedAtMillis)
    }

    @Test
    fun dismissalIsIdempotentAndDoesNotUpdateLatestPractice() = runBlocking {
        val fixture = fixture()
        val rehearsalId = checkNotNull(
            fixture.coordinator.startQuick(fixture.planId).session,
        ).rehearsal.rehearsalId

        assertEquals(RehearsalTerminalResult.Applied, fixture.coordinator.dismiss(rehearsalId))
        assertEquals(
            RehearsalTerminalResult.AlreadyDismissed,
            fixture.coordinator.dismiss(rehearsalId),
        )
        assertNull(fixture.rehearsals.events.single().completedAtMillis)
        assertNotNull(fixture.rehearsals.events.single().dismissedAtMillis)
        assertNull(fixture.plans.plans.value.single().rehearsedAtMillis)
    }

    @Test
    fun completedAndDismissedCannotCoexist() = runBlocking {
        val fixture = fixture()
        val rehearsalId = checkNotNull(
            fixture.coordinator.startQuick(fixture.planId).session,
        ).rehearsal.rehearsalId

        fixture.coordinator.complete(rehearsalId)

        assertEquals(
            RehearsalTerminalResult.AlreadyCompleted,
            fixture.coordinator.dismiss(rehearsalId),
        )
        val event = fixture.rehearsals.events.single()
        assertNotNull(event.completedAtMillis)
        assertNull(event.dismissedAtMillis)
    }

    @Test
    fun reloadRestoresEventAfterCoordinatorRecreation() = runBlocking {
        val fixture = fixture()
        val started = checkNotNull(
            fixture.coordinator.startGuided(fixture.planId).session,
        )
        val recreated = MomentPlanRehearsalCoordinator(
            rehearsals = fixture.rehearsals,
            plans = fixture.plans,
            clock = fixture.clock,
        )

        val restored = recreated.reload(started.rehearsal.rehearsalId)

        assertEquals(started, restored)
    }

    @Test
    fun openRehearsalUsesContentRevisionNotMetadataTimestamp() = runBlocking {
        val fixture = fixture()
        val started = checkNotNull(
            fixture.coordinator.startGuided(fixture.planId).session,
        )

        assertEquals(started, fixture.coordinator.recoverOpen())

        fixture.plans.plans.value = fixture.plans.plans.value.map {
            it.copy(updatedAtMillis = it.updatedAtMillis + 1L)
        }
        assertNotNull(fixture.coordinator.recoverOpen())

        fixture.plans.plans.value = fixture.plans.plans.value.map {
            it.copy(contentRevisionId = UUID.randomUUID().toString())
        }
        assertNull(fixture.coordinator.recoverOpen())
        assertNotNull(fixture.rehearsals.events.single().dismissedAtMillis)
    }

    @Test
    fun editedPlanRevisionDoesNotReceiveOldPracticeTimestamp() = runBlocking {
        val fixture = fixture()
        val rehearsalId = checkNotNull(
            fixture.coordinator.startGuided(fixture.planId).session,
        ).rehearsal.rehearsalId
        fixture.plans.plans.value = fixture.plans.plans.value.map {
            it.copy(
                updatedAtMillis = it.updatedAtMillis + 1L,
                contentRevisionId = UUID.randomUUID().toString(),
            )
        }

        fixture.coordinator.complete(rehearsalId)

        assertNull(fixture.plans.plans.value.single().rehearsedAtMillis)
        assertNotNull(fixture.rehearsals.events.single().completedAtMillis)
    }

    @Test
    fun deletedPlanLeavesHistoricalRehearsalIntact() = runBlocking {
        val fixture = fixture()
        val rehearsalId = checkNotNull(
            fixture.coordinator.startGuided(fixture.planId).session,
        ).rehearsal.rehearsalId
        fixture.coordinator.complete(rehearsalId)

        fixture.plans.delete(fixture.planId)

        assertNotNull(fixture.rehearsals.getById(rehearsalId))
        assertNull(fixture.coordinator.reload(rehearsalId))
    }

    private fun fixture(enabled: Boolean = true): Fixture {
        val plan = momentPlan(enabled = enabled)
        val plans = FakeMomentPlanRepository(listOf(plan))
        val rehearsals = FakeRehearsalRepository()
        val clock = FakeClock(current = 5_000L)
        var nextId = 0
        return Fixture(
            planId = plan.planId,
            plans = plans,
            rehearsals = rehearsals,
            clock = clock,
            coordinator = MomentPlanRehearsalCoordinator(
                rehearsals = rehearsals,
                plans = plans,
                clock = clock,
                ids = RehearsalIdSource {
                    UUID.nameUUIDFromBytes("rehearsal-${nextId++}".toByteArray()).toString()
                },
            ),
        )
    }

    private data class Fixture(
        val planId: String,
        val plans: FakeMomentPlanRepository,
        val rehearsals: FakeRehearsalRepository,
        val clock: FakeClock,
        val coordinator: MomentPlanRehearsalCoordinator,
    )
}

private class FakeRehearsalRepository : MomentPlanRehearsalRepository {
    val events = mutableListOf<MomentPlanRehearsal>()
    var insertCalls = 0

    override suspend fun insertOnce(rehearsal: MomentPlanRehearsal): Boolean {
        insertCalls++
        if (events.any { it.rehearsalId == rehearsal.rehearsalId }) return false
        events += rehearsal
        return true
    }

    override suspend fun getById(rehearsalId: String): MomentPlanRehearsal? =
        events.firstOrNull { it.rehearsalId == rehearsalId }

    override suspend fun markCompletedOnce(
        rehearsalId: String,
        completedAtMillis: Long,
    ): Boolean = mutateOpen(rehearsalId) {
        copy(completedAtMillis = completedAtMillis)
    }

    override suspend fun markDismissedOnce(
        rehearsalId: String,
        dismissedAtMillis: Long,
    ): Boolean = mutateOpen(rehearsalId) {
        copy(dismissedAtMillis = dismissedAtMillis)
    }

    override suspend fun getOpenRehearsal(): MomentPlanRehearsal? =
        events.filter { it.isOpen }.maxByOrNull { it.startedAtMillis }

    override suspend fun getRecentCompleted(limit: Int): List<MomentPlanRehearsal> =
        events.filter { it.completedAtMillis != null }
            .sortedByDescending { it.completedAtMillis }
            .take(limit)

    override suspend fun getCompletedByPlan(planId: String): List<MomentPlanRehearsal> =
        events.filter { it.planId == planId && it.completedAtMillis != null }

    override suspend fun clearHistory() {
        events.removeAll { !it.isOpen }
    }

    override suspend fun clearAll() {
        events.clear()
    }

    private fun mutateOpen(
        rehearsalId: String,
        transform: MomentPlanRehearsal.() -> MomentPlanRehearsal,
    ): Boolean {
        val index = events.indexOfFirst { it.rehearsalId == rehearsalId && it.isOpen }
        if (index < 0) return false
        events[index] = events[index].transform()
        return true
    }
}
