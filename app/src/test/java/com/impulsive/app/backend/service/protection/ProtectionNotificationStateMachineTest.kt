package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionNotificationStateMachineTest {
    private val scheduler = TestScheduler()
    private val stateMachine = ProtectionNotificationStateMachine(scheduler::schedule)

    @Test
    fun onQueuesWithoutPosting() {
        var posts = 0
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)

        val result = stateMachine.submit(1) { posts += 1 }

        assertEquals(ProtectionNotificationSubmission.Queued, result)
        assertEquals(0, posts)
    }

    @Test
    fun offPostsImmediately() {
        var posts = 0

        val result = stateMachine.submit(1) { posts += 1 }

        assertEquals(ProtectionNotificationSubmission.Posted, result)
        assertEquals(1, posts)
    }

    @Test
    fun onToOffFlushesQueuedPosts() {
        val posts = mutableListOf<String>()
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.submit(1) { posts += "A" }
        stateMachine.submit(2) { posts += "B" }

        stateMachine.onProtectionScreenOff(ProtectionNotificationOwner.APP_MONITOR)

        assertEquals(listOf("A", "B"), posts)
    }

    @Test
    fun sameNotificationIdCoalescesToLatestPost() {
        val posts = mutableListOf<String>()
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.submit(1) { posts += "old" }
        stateMachine.submit(1) { posts += "new" }

        stateMachine.onProtectionScreenOff(ProtectionNotificationOwner.APP_MONITOR)

        assertEquals(listOf("new"), posts)
    }

    @Test
    fun cancellationRemovesQueuedPost() {
        var posts = 0
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.submit(1) { posts += 1 }

        stateMachine.cancelQueued(1)
        stateMachine.onProtectionScreenOff(ProtectionNotificationOwner.APP_MONITOR)

        assertEquals(0, posts)
    }

    @Test
    fun skippedDoesNotPostBeforeFiveSeconds() {
        var posts = 0
        enterSkipped()
        stateMachine.submit(1) { posts += 1 }

        scheduler.advanceTo(4_999L)

        assertEquals(0, posts)
    }

    @Test
    fun skippedPostsAtFiveAndTenSecondsOnly() {
        var posts = 0
        enterSkipped()
        stateMachine.submit(1) { posts += 1 }

        scheduler.advanceTo(5_000L)
        assertEquals(1, posts)

        stateMachine.submit(1) { posts += 1 }
        scheduler.advanceTo(10_000L)
        assertEquals(2, posts)

        stateMachine.submit(1) { posts += 1 }
        scheduler.advanceTo(60_000L)
        assertEquals(2, posts)
    }

    @Test
    fun newSkippedSessionAllowsTwoNewOccurrences() {
        var posts = 0
        enterSkipped()
        stateMachine.submit(1) { posts += 1 }
        scheduler.advanceTo(10_000L)
        assertEquals(2, posts)

        stateMachine.cancelQueued(1)
        stateMachine.onProtectionScreenOff(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.onProtectionScreenSkipped(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.submit(1) { posts += 1 }
        scheduler.advanceTo(20_000L)

        assertEquals(4, posts)
    }

    @Test
    fun leavingSkippedInvalidatesOldCallbacks() {
        var posts = 0
        enterSkipped()
        stateMachine.submit(1) { posts += 1 }

        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        scheduler.advanceTo(10_000L)

        assertEquals(0, posts)
    }

    @Test
    fun overlayFailureLeavesOnAndAllowsFallbackToPost() {
        var posts = 0
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)

        stateMachine.onProtectionScreenUnavailable(ProtectionNotificationOwner.APP_MONITOR)
        val result = stateMachine.submit(1) { posts += 1 }

        assertEquals(ProtectionNotificationMode.OFF, stateMachine.currentMode())
        assertEquals(ProtectionNotificationSubmission.Posted, result)
        assertEquals(1, posts)
    }

    @Test
    fun stoppingDifferentOwnerDoesNotFlushActiveOverlayQueue() {
        var posts = 0
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.VPN)
        stateMachine.submit(1) { posts += 1 }

        stateMachine.onProtectionScreenOff(ProtectionNotificationOwner.APP_MONITOR)

        assertEquals(ProtectionNotificationMode.ON, stateMachine.currentMode())
        assertEquals(0, posts)
    }

    private fun enterSkipped() {
        stateMachine.onProtectionScreenShown(ProtectionNotificationOwner.APP_MONITOR)
        stateMachine.onProtectionScreenSkipped(ProtectionNotificationOwner.APP_MONITOR)
    }

    private class TestScheduler {
        private data class ScheduledAction(
            val atMillis: Long,
            val action: () -> Unit,
        )

        private val actions = mutableListOf<ScheduledAction>()
        private var nowMillis = 0L

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
