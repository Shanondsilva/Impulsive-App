package com.impulsive.app.frontend.ads

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the debug-only AdMob configuration actually merged into this build's manifest,
 * and the debug rewarded test ad-unit ID Safe Browse uses. Never requests a live ad and
 * never requires network or ad-inventory availability.
 */
@RunWith(AndroidJUnit4::class)
class SafeBrowseRewardedAdDebugConfigInstrumentedTest {

    @Test
    fun debugManifestResolvesToGooglesOfficialSampleApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val metaData = applicationInfo.metaData
        assertNotNull("Missing application meta-data bundle", metaData)

        val resolvedAppId = metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
        assertEquals("ca-app-pub-3940256099942544~3347511713", resolvedAppId)
    }

    @Test
    fun debugRewardedAdUnitIdIsGooglesOfficialTestId() {
        assertEquals(
            "ca-app-pub-3940256099942544/5224354917",
            SafeBrowseDebugRewardedAdUnitId,
        )
    }
}
