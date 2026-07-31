package com.impulsive.app.backend.protectioncoach

import com.impulsive.app.backend.analytics.ImpulsiveAnalyticsEvent
import com.impulsive.app.backend.analytics.ImpulsiveAnalyticsParam
import com.impulsive.app.backend.analytics.ImpulsiveAnalyticsPrivacyPolicy
import com.impulsive.app.backend.domain.protectioncoach.PlusPromotionEligibilityReason
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachPromotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionCoachAnalyticsAndPromotionTest {
    @Test
    fun allowedAnalyticsEventUsesOnlyApprovedParameters() {
        assertTrue(
            ImpulsiveAnalyticsPrivacyPolicy.isAllowed(
                ImpulsiveAnalyticsEvent.TimingSuggestionShown.eventName,
                setOf(
                    ImpulsiveAnalyticsParam.EntryPoint.key,
                    ImpulsiveAnalyticsParam.SuggestionType.key,
                    ImpulsiveAnalyticsParam.PolicyVersion.key,
                ),
            ),
        )
    }

    @Test
    fun disallowedBehaviouralFieldsAreRejected() {
        assertFalse(
            ImpulsiveAnalyticsPrivacyPolicy.isAllowed(
                ImpulsiveAnalyticsEvent.TimingSuggestionShown.eventName,
                setOf("suggestion_id"),
            ),
        )
        assertFalse(
            ImpulsiveAnalyticsPrivacyPolicy.isAllowed(
                "protected_package_seen",
                setOf(ImpulsiveAnalyticsParam.Result.key),
            ),
        )
    }

    @Test
    fun plusPromotionDoesNotBlockDismissedWrongTimingOrRepeatSession() {
        val now = 10_000L
        assertEquals(
            PlusPromotionEligibilityReason.DismissedOrWrongTiming,
            ProtectionCoachPromotionPolicy.shouldShowPostSupportPromotion(
                completedSupportAction = true,
                wasDismissed = false,
                wasWrongTiming = true,
                shownThisSession = false,
                lastShownAtMillis = null,
                nowMillis = now,
            ).reason,
        )
        assertEquals(
            PlusPromotionEligibilityReason.AlreadyShownThisSession,
            ProtectionCoachPromotionPolicy.shouldShowPostSupportPromotion(
                completedSupportAction = true,
                wasDismissed = false,
                wasWrongTiming = false,
                shownThisSession = true,
                lastShownAtMillis = null,
                nowMillis = now,
            ).reason,
        )
    }

    @Test
    fun plusPromotionUsesFourteenDayCap() {
        val now = 20L * 24L * 60L * 60L * 1_000L
        assertFalse(
            ProtectionCoachPromotionPolicy.shouldShowPostSupportPromotion(
                completedSupportAction = true,
                wasDismissed = false,
                wasWrongTiming = false,
                shownThisSession = false,
                lastShownAtMillis = now - 1L,
                nowMillis = now,
            ).show,
        )
        assertTrue(
            ProtectionCoachPromotionPolicy.shouldShowPostSupportPromotion(
                completedSupportAction = true,
                wasDismissed = false,
                wasWrongTiming = false,
                shownThisSession = false,
                lastShownAtMillis = 0L,
                nowMillis = now,
            ).show,
        )
    }
}
