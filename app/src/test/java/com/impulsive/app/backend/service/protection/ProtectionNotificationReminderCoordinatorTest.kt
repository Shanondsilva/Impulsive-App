package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionNotificationReminderCoordinatorTest {
    private val scheduler = TestScheduler()
    private val logs = mutableListOf<String>()
    private val coordinator = InterruptionNotificationReminderCoordinator(
        nowMillis = scheduler::now,
        schedule = scheduler::schedule,
        log = logs::add,
    )
    private val incident = InterruptionNotificationIncidentId(
        packageName = "com.brave.browser",
        startedAtMillis = 0L,
        isWebsiteIncident = false,
        isFocusSession = false,
    )

    @Test
    fun `continuing incident posts zero twenty and forty once only`() {
        val stages = mutableListOf<InterruptionNotificationStage>()

        assertTrue(coordinator.startOrContinue(incident, stages::add))
        scheduler.advanceTo(0L)
        assertEquals(listOf(InterruptionNotificationStage.Initial), stages)

        scheduler.advanceTo(19_999L)
        assertEquals(1, stages.size)
        scheduler.advanceTo(20_000L)
        assertEquals(
            listOf(
                InterruptionNotificationStage.Initial,
                InterruptionNotificationStage.TwentySeconds,
            ),
            stages,
        )

        scheduler.advanceTo(40_000L)
        assertEquals(InterruptionNotificationStage.entries, stages)
        scheduler.advanceTo(120_000L)
        assertEquals(InterruptionNotificationStage.entries, stages)
    }

    @Test
    fun `dismissing initial does not cancel twenty or forty`() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        coordinator.startOrContinue(incident, stages::add)
        scheduler.advanceTo(0L)

        coordinator.recordDismissed(incident, InterruptionNotificationStage.Initial)
        scheduler.advanceTo(40_000L)

        assertEquals(InterruptionNotificationStage.entries, stages)
    }

    @Test
    fun `dismissing twenty does not cancel forty`() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        coordinator.startOrContinue(incident, stages::add)
        scheduler.advanceTo(20_000L)

        coordinator.recordDismissed(incident, InterruptionNotificationStage.TwentySeconds)
        scheduler.advanceTo(40_000L)

        assertEquals(InterruptionNotificationStage.entries, stages)
    }

    @Test
    fun `game selection cancels pending stages`() {
        assertCancellationStopsPendingStages()
    }

    @Test
    fun `reading selection cancels pending stages`() {
        assertCancellationStopsPendingStages()
    }

    @Test
    fun `temporary access selection cancels pending stages`() {
        assertCancellationStopsPendingStages()
    }

    @Test
    fun `incident end cancels pending stages`() {
        assertCancellationStopsPendingStages()
    }

    @Test
    fun `protection shutdown cancels pending stages`() {
        assertCancellationStopsPendingStages()
    }

    @Test
    fun `cancellation logs reason and invalidates scheduled callbacks`() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        coordinator.startOrContinue(incident, stages::add)
        scheduler.advanceTo(0L)

        coordinator.cancel(incident, reason = "browser left foreground")
        scheduler.advanceTo(40_000L)

        assertEquals(listOf(InterruptionNotificationStage.Initial), stages)
        assertTrue(
            logs.any { message ->
                message.contains("cancelled incident=") &&
                    message.contains("reason=browser left foreground")
            },
        )
        assertTrue(logs.any { message -> message.contains("scheduled stage=Initial") })
        assertTrue(logs.any { message -> message.contains("evaluated stage=Initial") })
    }

    @Test
    fun `friction to cooldown transition does not restart website incident clock`() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        val websiteIncident = incident.copy(isWebsiteIncident = true)
        assertTrue(coordinator.startOrContinue(websiteIncident, stages::add))
        scheduler.advanceTo(30_000L)

        assertFalse(coordinator.startOrContinue(websiteIncident, stages::add))
        scheduler.advanceTo(40_000L)

        assertEquals(InterruptionNotificationStage.entries, stages)
    }

    @Test
    fun `repeated monitor polling does not restart the incident clock`() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        assertTrue(coordinator.startOrContinue(incident, stages::add))
        scheduler.advanceTo(10_000L)

        assertFalse(coordinator.startOrContinue(incident, stages::add))
        scheduler.advanceTo(40_000L)

        assertEquals(InterruptionNotificationStage.entries, stages)
    }

    private fun assertCancellationStopsPendingStages() {
        val stages = mutableListOf<InterruptionNotificationStage>()
        coordinator.startOrContinue(incident, stages::add)
        scheduler.advanceTo(0L)

        coordinator.cancel(incident)
        scheduler.advanceTo(40_000L)

        assertEquals(listOf(InterruptionNotificationStage.Initial), stages)
        assertFalse(coordinator.isActive(incident))
    }

    private class TestScheduler {
        private data class ScheduledAction(
            val atMillis: Long,
            val action: () -> Unit,
        )

        private val actions = mutableListOf<ScheduledAction>()
        private var nowMillis = 0L

        fun now(): Long = nowMillis

        fun schedule(delayMillis: Long, action: () -> Unit) {
            actions += ScheduledAction(nowMillis + delayMillis, action)
        }

        fun advanceTo(targetMillis: Long) {
            while (true) {
                val next = actions
                    .filter { scheduled -> scheduled.atMillis <= targetMillis }
                    .minByOrNull(ScheduledAction::atMillis)
                    ?: break

                actions.remove(next)
                nowMillis = next.atMillis
                next.action()
            }
            nowMillis = targetMillis
        }
    }
}
