package com.impulsive.app.backend.service.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalReminderNotificationPolicyTest {

    @Test
    fun hiddenRemindersUseGenericTitle() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertEquals("Impulsive", decision.title)
    }

    @Test
    fun hiddenRemindersUseGenericBody() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertEquals("Open Impulsive for your journal reminder.", decision.body)
    }

    @Test
    fun hiddenRemindersResolveSecret() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertEquals(JournalReminderVisibility.Secret, decision.visibility)
    }

    @Test
    fun hiddenRemindersContainNoSuppliedCurrentTitle() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertTrue(!decision.title.contains("Therapy"))
        assertTrue(!decision.body.contains("Therapy"))
    }

    @Test
    fun hiddenRemindersContainNoSuppliedPreview() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertTrue(!decision.title.contains("anxiety"))
        assertTrue(!decision.body.contains("anxiety"))
    }

    @Test
    fun hiddenRemindersExposeNoPublicTitleOrBody() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = true,
            currentTitle = "Therapy session notes",
            currentPreview = "Talked about anxiety triggers",
        )

        assertNull(decision.publicTitle)
        assertNull(decision.publicBody)
    }

    @Test
    fun visibleRemindersUseCurrentTitle() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals("Grocery list", decision.title)
    }

    @Test
    fun visibleRemindersUseCurrentPreview() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals("Milk, eggs, bread", decision.body)
    }

    @Test
    fun visibleRemindersResolvePrivate() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals(JournalReminderVisibility.Private, decision.visibility)
    }

    @Test
    fun visibleRemindersProvideGenericPublicTitle() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals("Impulsive", decision.publicTitle)
    }

    @Test
    fun visibleRemindersProvideGenericPublicBody() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals("Open Impulsive for your journal reminder.", decision.publicBody)
    }

    @Test
    fun blankVisibleTitleFallsBackToJournalReminder() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "   ",
            currentPreview = "Milk, eggs, bread",
        )

        assertEquals("Journal reminder", decision.title)
    }

    @Test
    fun blankVisiblePreviewFallsBackToDefaultBody() {
        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = "   ",
        )

        assertEquals("You asked Impulsive to remind you.", decision.body)
    }

    @Test
    fun previewIsCappedAt120Characters() {
        val longPreview = "a".repeat(500)

        val decision = resolveJournalReminderNotification(
            hideSensitiveNotifications = false,
            currentTitle = "Grocery list",
            currentPreview = longPreview,
        )

        assertEquals(120, decision.body.length)
    }
}
