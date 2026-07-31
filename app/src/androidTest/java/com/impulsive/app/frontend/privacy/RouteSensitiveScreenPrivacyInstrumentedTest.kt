package com.impulsive.app.frontend.privacy

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteSensitiveScreenPrivacyInstrumentedTest {
    @Test
    fun privateThenPublicRouteAppliesAndClearsSecureWindowOnSamsungCompatibleWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val controller = RouteSensitiveScreenPrivacyController(
                    AndroidSecureWindowHandle(activity.window),
                )

                controller.apply(
                    protect = PrivateScreenRoutePolicy.isPrivate(
                        "adaptive_explanation/{decisionId}",
                    ),
                )
                assertTrue(activity.window.hasSecureFlag())

                controller.apply(
                    protect = PrivateScreenRoutePolicy.isPrivate("level_one_reveal"),
                )
                assertFalse(activity.window.hasSecureFlag())
                controller.release()
            }
        }
    }

    private fun android.view.Window.hasSecureFlag(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
