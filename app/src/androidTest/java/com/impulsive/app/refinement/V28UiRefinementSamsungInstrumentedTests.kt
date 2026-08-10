package com.impulsive.app.refinement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves only that the v28 refinement resources are packaged into the installed
 * application and read back their expected values.
 *
 * These tests are deliberately named for packaging rather than for UI, layout,
 * font scaling, dark mode or accessibility: they exercise none of those, and
 * naming them so previously made the suite untrustworthy as release evidence.
 * Release identity is not pinned here — a test should not need editing every
 * version bump.
 */
@RunWith(AndroidJUnit4::class)
class RefinementPackagingInstrumentedTest {

    @Test
    fun refinementResourcesArePackagedInTheTargetApplication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertTrue(
            context.packageName == "com.impulsive.app" ||
                context.packageName == "com.impulsive.app.debug",
        )

        assertEquals(
            "RESET READING",
            context.getString(R.string.v28_reset_reading_title),
        )

        assertEquals(
            "Recent session",
            context.getString(R.string.v28_recent_session),
        )

        assertEquals(
            "Personal best",
            context.getString(R.string.v28_personal_best),
        )

        assertTrue(
            context.getString(R.string.v28_personal_best_empty).isNotBlank(),
        )
    }

    @Test
    fun scoreActionAccessibilityLabelsArePackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(
            "Show personal best",
            context.getString(R.string.v28_show_personal_best),
        )

        assertEquals(
            "Show recent session",
            context.getString(R.string.v28_show_recent_session),
        )
    }
}
