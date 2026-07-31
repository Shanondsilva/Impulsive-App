package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.frontend.privacy.PrivateScreenRoutePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftPrivacyRouteInstrumentedTest {
    @Test
    fun pathShiftRouteIsOpaqueAndPrivate() {
        assertTrue(PrivateScreenRoutePolicy.isPrivate("path_shift"))
        assertFalse("path_shift".contains("{"))
        assertFalse("path_shift".contains("?"))
    }
}
