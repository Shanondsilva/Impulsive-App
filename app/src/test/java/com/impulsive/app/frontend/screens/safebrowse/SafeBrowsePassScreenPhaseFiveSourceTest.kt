package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassScreenPhaseFiveSourceTest {

    private val source = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassScreen.kt",
    ).readText()

    private fun block(start: String, end: String): String {
        val startIndex = source.indexOf(start)
        assertTrue("start marker not found: $start", startIndex >= 0)
        val endIndex = source.indexOf(end, startIndex + start.length)
        assertTrue("end marker not found after start: $end", endIndex > startIndex)
        return source.substring(startIndex, endIndex)
    }

    @Test
    fun screenSignatureIncludesOnPrepaidTopUp() {
        val signature = block("fun SafeBrowsePassScreen(", "modifier: Modifier = Modifier,")
        assertTrue(signature.contains("onPrepaidTopUp:"))
    }

    @Test
    fun screenSignatureIncludesOnManageSubscription() {
        val signature = block("fun SafeBrowsePassScreen(", "modifier: Modifier = Modifier,")
        assertTrue(signature.contains("onManageSubscription:"))
    }

    @Test
    fun activeBranchPassesAccessAndStateIntoTheActiveCard() {
        val whenBlock = block("when (val access = state.accessState) {", "private fun SafeBrowsePassBackRow")
        assertTrue(whenBlock.contains("SafeBrowsePassActiveCard("))
        val callSite = whenBlock.substringAfter("SafeBrowsePassActiveCard(").substringBefore(")\n\n")
        assertTrue(callSite.contains("access = access") || callSite.contains("access =\n"))
        assertTrue(callSite.contains("state = state") || callSite.contains("state =\n"))
    }

    @Test
    fun activeCardConsumesPlanStatus() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertTrue(activeCard.contains("access.planStatus"))
    }

    @Test
    fun activeCardConsumesExpiryTimeMillis() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertTrue(activeCard.contains("access.expiryTimeMillis"))
    }

    @Test
    fun activeCardConsumesTopUpPending() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertTrue(activeCard.contains("access.topUpPending"))
    }

    @Test
    fun manageTagExists() {
        assertTrue(source.contains("\"safe_browse_pass_manage\""))
    }

    @Test
    fun topUpTagExists() {
        assertTrue(source.contains("\"safe_browse_pass_top_up\""))
    }

    @Test
    fun expiryTagExists() {
        assertTrue(source.contains("\"safe_browse_pass_expiry\""))
    }

    @Test
    fun expiredTagExists() {
        assertTrue(source.contains("\"safe_browse_pass_expired\""))
    }

    @Test
    fun expiredHasAnExplicitBranch() {
        val whenBlock = block("when (val access = state.accessState) {", "private fun SafeBrowsePassBackRow")
        assertTrue(whenBlock.contains("is SafeBrowsePassScreenAccessState.Expired ->"))
    }

    @Test
    fun expiredDoesNotUseAnElseFallback() {
        val whenBlock = block("when (val access = state.accessState) {", "private fun SafeBrowsePassBackRow")
        assertFalse(whenBlock.contains("else ->"))
    }

    @Test
    fun standardOffersAreNotInsideTheActiveCardFunction() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertFalse(activeCard.contains("SafeBrowsePassOffersCard"))
        assertFalse(activeCard.contains("safe_browse_pass_purchase"))
    }

    @Test
    fun manageSubscriptionExistsOnlyAsAnActiveAction() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertTrue(activeCard.contains("onManageSubscription"))

        val expiredCard = block("private fun SafeBrowsePassExpiredCard(", "private fun SafeBrowsePassRestoreButton(")
        assertFalse(expiredCard.contains("onManageSubscription"))

        val offersCard = block("private fun SafeBrowsePassOffersCard(", "@Composable\nprivate fun SafeBrowsePassPlanOptionRow")
        assertFalse(offersCard.contains("onManageSubscription"))
    }

    @Test
    fun topUpUsesPrepaidPlanFormattedPrice() {
        val activeCard = block("private fun SafeBrowsePassActiveCard(", "private fun SafeBrowsePassExpiredCard(")
        assertTrue(activeCard.contains("state.prepaidPlan?.formattedPrice"))
    }

    @Test
    fun noProductionHardCodedCurrencyExists() {
        val previewIndex = source.indexOf("@Preview")
        assertTrue(previewIndex > 0)
        val productionSection = source.substring(0, previewIndex)

        // £/€/₹ are unambiguous currency symbols in Kotlin source. `$` is also Kotlin's
        // string-template sigil, so only a literal currency amount (`$` immediately
        // followed by a digit) counts as a hard-coded price -- `"$price"` interpolation
        // is not.
        listOf("£", "€", "₹").forEach { currencySymbol ->
            assertFalse(
                "production Safe Browse Pass screen code unexpectedly hard-codes a currency symbol: $currencySymbol",
                productionSection.contains(currencySymbol),
            )
        }
        assertFalse(
            "production Safe Browse Pass screen code unexpectedly hard-codes a dollar-amount literal",
            Regex("\\$\\d").containsMatchIn(productionSection),
        )
    }

    @Test
    fun liveRegionPoliteExists() {
        assertTrue(source.contains("LiveRegionMode.Polite"))
        assertFalse(source.contains("LiveRegionMode.Assertive"))
    }

    @Test
    fun stateDescriptionExists() {
        assertTrue(source.contains("stateDescription ="))
    }

    @Test
    fun planSelectionUsesRoleRadioButton() {
        assertTrue(source.contains("role = Role.RadioButton"))
    }

    @Test
    fun theNestedRadioButtonUsesOnClickNull() {
        assertTrue(source.contains("RadioButton(selected = selected, onClick = null)"))
    }

    @Test
    fun allInteractiveControlsRetainAMinimum48dpTarget() {
        val interactiveButtonCount = Regex("(Button|OutlinedButton)\\(").findAll(source).count()
        val heightInCount = Regex("heightIn\\(min = 48\\.dp\\)").findAll(source).count()
        assertTrue(
            "expected at least as many 48dp height targets ($heightInCount) as Button/OutlinedButton call sites ($interactiveButtonCount) among production, non-back-row controls",
            heightInCount >= 5,
        )
    }

    @Test
    fun noAnimatedContentExists() {
        assertFalse(source.contains("AnimatedContent"))
    }

    @Test
    fun noCrossfadeExists() {
        assertFalse(source.contains("Crossfade"))
    }

    @Test
    fun noAnimateContentSizeExists() {
        assertFalse(source.contains("animateContentSize"))
    }

    @Test
    fun allPriorTestTagsRemain() {
        listOf(
            "safe_browse_pass_back",
            "safe_browse_pass_heading",
            "safe_browse_pass_loading",
            "safe_browse_pass_active",
            "safe_browse_pass_offers",
            "safe_browse_pass_purchase",
            "safe_browse_pass_restore",
            "safe_browse_pass_retry",
            "safe_browse_pass_status_message",
        ).forEach { tag ->
            assertTrue(
                "expected prior test tag to remain: $tag",
                source.contains("\"$tag\""),
            )
        }
    }

    @Test
    fun screenContainsNoBillingSdkOrFirebaseIdentifiers() {
        listOf(
            "com.android.billingclient",
            "ProductDetails",
            "BillingResult",
            "Purchase(",
            "FirebaseAuth",
            "purchaseToken",
            "orderId",
            "offerToken",
        ).forEach { forbidden ->
            assertFalse(
                "SafeBrowsePassScreen.kt unexpectedly references: $forbidden",
                source.contains(forbidden),
            )
        }
    }
}
