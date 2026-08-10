package com.impulsive.app.frontend.screens.safebrowse

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassDisplayTextTest {

    private val zone = ZoneId.of("UTC")
    private val locale = Locale.UK

    @Test
    fun validTimestampFormatsDeterministically() {
        // 2023-11-05T14:30:00Z
        val epochMillis = 1_699_194_600_000L

        val formatted = formatSafeBrowsePassDateTime(
            epochMillis = epochMillis,
            zoneId = zone,
            locale = locale,
        )

        assertEquals("5 nov 2023, 2:30 pm", formatted?.lowercase(locale))
    }

    @Test
    fun zeroTimestampReturnsNull() {
        assertNull(
            formatSafeBrowsePassDateTime(
                epochMillis = 0L,
                zoneId = zone,
                locale = locale,
            ),
        )
        assertNull(
            formatSafeBrowsePassDateTime(
                epochMillis = -1L,
                zoneId = zone,
                locale = locale,
            ),
        )
    }

    private fun activePrepaid(topUpPending: Boolean = false) = SafeBrowsePassScreenAccessState.Active(
        expiryTimeMillis = 1_699_194_600_000L,
        planStatus = SafeBrowsePassActivePlanStatus.Prepaid,
        topUpPending = topUpPending,
    )

    private fun activeAutoRenewing() = SafeBrowsePassScreenAccessState.Active(
        expiryTimeMillis = 1_699_194_600_000L,
        planStatus = SafeBrowsePassActivePlanStatus.AutoRenewing,
        topUpPending = false,
    )

    private fun activeCancelled() = SafeBrowsePassScreenAccessState.Active(
        expiryTimeMillis = 1_699_194_600_000L,
        planStatus = SafeBrowsePassActivePlanStatus.CancelledUntilExpiry,
        topUpPending = false,
    )

    @Test
    fun activePrepaidCopyIdentifiesPrepaid() {
        val display = safeBrowsePassActiveDisplayText(
            access = activePrepaid(),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        assertTrue(display.planLabel.contains("prepaid", ignoreCase = true))
        assertTrue(display.stateDescription.contains("Prepaid"))
    }

    @Test
    fun activePrepaidPendingCopyIdentifiesTopUpPending() {
        val display = safeBrowsePassActiveDisplayText(
            access = activePrepaid(topUpPending = true),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        assertTrue(display.supportingText.contains("pending", ignoreCase = true))
        assertTrue(display.stateDescription.contains("Top-up pending"))
    }

    @Test
    fun activeAutoRenewingCopyDoesNotClaimACompletedRenewal() {
        val display = safeBrowsePassActiveDisplayText(
            access = activeAutoRenewing(),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        listOf(display.title, display.planLabel, display.supportingText, display.stateDescription)
            .forEach { text ->
                assertFalse(
                    "unexpectedly claims a completed renewal: $text",
                    text.contains("renewed", ignoreCase = true),
                )
                assertFalse(
                    "unexpectedly claims payment succeeded: $text",
                    text.contains("payment successful", ignoreCase = true) ||
                        text.contains("charged", ignoreCase = true),
                )
            }
    }

    @Test
    fun cancelledCopySaysAccessEndsAndRemainsActive() {
        val display = safeBrowsePassActiveDisplayText(
            access = activeCancelled(),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        assertTrue(display.title.contains("remains active", ignoreCase = true))
        assertTrue(requireNotNull(display.timingLabel).contains("ends", ignoreCase = true))
        assertTrue(display.stateDescription.contains("Subscription cancelled"))
    }

    private fun expiredPrepaid() = SafeBrowsePassScreenAccessState.Expired(
        expiryTimeMillis = 1_699_194_600_000L,
        wasPrepaid = true,
    )

    private fun expiredAutoRenewing() = SafeBrowsePassScreenAccessState.Expired(
        expiryTimeMillis = 1_699_194_600_000L,
        wasPrepaid = false,
    )

    @Test
    fun expiredPrepaidCopyIdentifiesPreviousPrepaidAccess() {
        val display = safeBrowsePassExpiredDisplayText(
            access = expiredPrepaid(),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        assertTrue(display.supportingText.contains("prepaid", ignoreCase = true))
        assertTrue(display.stateDescription.contains("prepaid", ignoreCase = true))
    }

    @Test
    fun expiredAutoRenewingCopyIdentifiesPreviousSubscription() {
        val display = safeBrowsePassExpiredDisplayText(
            access = expiredAutoRenewing(),
            formattedExpiry = "5 Nov 2023, 2:30 PM",
        )

        assertTrue(display.stateDescription.contains("subscription", ignoreCase = true))
        assertFalse(display.supportingText.contains("prepaid", ignoreCase = true))
    }

    @Test
    fun stateDescriptionsIncludeTheVerifiedFormattedTimeWhenAvailable() {
        val formatted = "5 Nov 2023, 2:30 PM"

        val active = safeBrowsePassActiveDisplayText(
            access = activeAutoRenewing(),
            formattedExpiry = formatted,
        )
        assertTrue(active.stateDescription.contains(formatted))

        val expired = safeBrowsePassExpiredDisplayText(
            access = expiredAutoRenewing(),
            formattedExpiry = formatted,
        )
        assertTrue(expired.stateDescription.contains(formatted))

        val activeNoTime = safeBrowsePassActiveDisplayText(
            access = activeAutoRenewing(),
            formattedExpiry = null,
        )
        assertNull(activeNoTime.timingLabel)
    }

    @Test
    fun noCopyContainsProductIdsPurchaseTokensOrRawSubscriptionStates() {
        val forbidden = listOf(
            "safe_browse_pass",
            "purchaseToken",
            "orderId",
            "SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_CANCELED",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
        )

        val allDisplays = listOf(
            safeBrowsePassActiveDisplayText(activePrepaid(), "5 Nov 2023, 2:30 PM"),
            safeBrowsePassActiveDisplayText(activePrepaid(topUpPending = true), "5 Nov 2023, 2:30 PM"),
            safeBrowsePassActiveDisplayText(activeAutoRenewing(), "5 Nov 2023, 2:30 PM"),
            safeBrowsePassActiveDisplayText(activeCancelled(), "5 Nov 2023, 2:30 PM"),
        ).flatMap { display ->
            listOf(display.title, display.planLabel, display.supportingText, display.stateDescription)
        } + listOf(
            safeBrowsePassExpiredDisplayText(expiredPrepaid(), "5 Nov 2023, 2:30 PM"),
            safeBrowsePassExpiredDisplayText(expiredAutoRenewing(), "5 Nov 2023, 2:30 PM"),
        ).flatMap { display ->
            listOf(display.title, display.supportingText, display.stateDescription)
        }

        allDisplays.forEach { text ->
            forbidden.forEach { sensitive ->
                assertFalse(
                    "display text unexpectedly contains sensitive value '$sensitive': $text",
                    text.contains(sensitive),
                )
            }
        }
    }
}
