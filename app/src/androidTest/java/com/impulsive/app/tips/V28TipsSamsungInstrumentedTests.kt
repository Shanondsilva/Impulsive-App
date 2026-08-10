package com.impulsive.app.tips

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.R
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves only that the tips resources are packaged and that the tip identity
 * and selection policy load on device.
 *
 * The previous scenario names — reduced motion, TalkBack, large font, dark and
 * light mode, process recreation, cross-feature non-interference — all ran this
 * same shallow check, so they claimed evidence that did not exist and have been
 * removed rather than renamed.
 */
@RunWith(AndroidJUnit4::class)
class TipsRuntimeInstrumentedTest {

    @Test
    fun tipsResourcesArePackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertTrue(
            context.packageName == "com.impulsive.app" ||
                context.packageName == "com.impulsive.app.debug",
        )

        assertEquals("Tips", context.getString(R.string.tips_title))
    }

    @Test
    fun stableTipIdentityAndSelectionPolicyLoadOnDevice() {
        assertEquals("stable_tip", ImpulsiveTipId("stable_tip").value)

        assertTrue(
            TipSelectionPolicy()
                .audienceTagsFor(setOf("social_media"))
                .isNotEmpty(),
        )
    }
}
