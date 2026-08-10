package com.impulsive.app.finalrepair

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves only that the target application loads under instrumentation.
 *
 * That is genuinely useful as a first smoke signal, and the name claims nothing
 * more. It replaces ten scenario-named tests (IME, preview refresh, font scale,
 * dark mode, shared cards) that all ran this same package check.
 */
@RunWith(AndroidJUnit4::class)
class AppRuntimeSmokeInstrumentedTest {

    @Test
    fun targetApplicationLoadsUnderInstrumentation() {
        val packageName = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .packageName

        assertTrue(
            packageName == "com.impulsive.app" ||
                packageName == "com.impulsive.app.debug",
        )
    }
}
