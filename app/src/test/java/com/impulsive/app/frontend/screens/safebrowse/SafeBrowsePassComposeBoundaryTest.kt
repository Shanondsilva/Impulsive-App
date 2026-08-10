package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassComposeBoundaryTest {
    private val uiStateSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassUiState.kt",
    ).readText()
    private val routeSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassRoute.kt",
    ).readText()
    private val screenSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassScreen.kt",
    ).readText()

    @Test
    fun composeBoundaryContainsNoRawBillingIdentifiers() {
        listOf("ProductDetails", "BillingResult", "purchaseToken", "orderId", "originalJson", "Firebase UID", "offerToken").forEach { forbidden ->
            assertFalse(uiStateSource.contains(forbidden))
            assertFalse(routeSource.contains(forbidden))
            assertFalse(screenSource.contains(forbidden))
        }
    }

    @Test
    fun screenAndUiStateDoNotUseInternalPlanModels() {
        listOf("productId", "basePlanId", "offerId", "SelectedSafeBrowsePassPlan").forEach { forbidden ->
            assertFalse(uiStateSource.contains(forbidden))
            assertFalse(screenSource.contains(forbidden))
        }
    }

    @Test
    fun safePlanModelAndLifecycleCollectionAreUsed() {
        listOf("SafeBrowsePassPlanUiModel", "formattedPrice", "periodLabel", "disclosure").forEach { marker ->
            assertTrue(uiStateSource.contains(marker) || screenSource.contains(marker))
        }
        assertTrue(routeSource.contains("collectAsStateWithLifecycle"))
    }
}
