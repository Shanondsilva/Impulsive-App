package com.impulsive.app.backend.service.review

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppReviewManagerTest {
    @Test
    fun listingUrlUsesProductionApplicationId() {
        assertEquals(
            "https://play.google.com/store/apps/details" +
                "?id=com.impulsive.app",
            buildImpulsivePlayStoreListingUrl(),
        )
    }
}
