package com.impulsive.app.performance

import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.R
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapResourceSizingInstrumentedTest {

    private data class Asset(
        val name: String,
        val resourceId: Int,
        val xxxhdpiWidth: Int,
        val xxxhdpiHeight: Int,
    )

    private val assets =
        listOf(
            Asset("avatar_01", R.drawable.avatar_01, 384, 384),
            Asset("avatar_02", R.drawable.avatar_02, 384, 384),
            Asset("avatar_03", R.drawable.avatar_03, 384, 384),
            Asset("avatar_04", R.drawable.avatar_04, 384, 384),
            Asset("avatar_05", R.drawable.avatar_05, 384, 384),
            Asset("avatar_06", R.drawable.avatar_06, 384, 384),
            Asset("impulsive_logo", R.drawable.impulsive_logo, 512, 469),
        )

    @Test
    fun reportBitmapResourceMetrics() {
        val resources =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .resources

        assets.forEach { asset ->
            val value = TypedValue()

            resources.getValue(
                asset.resourceId,
                value,
                true,
            )

            val bitmap =
                BitmapFactory.decodeResource(
                    resources,
                    asset.resourceId,
                )

            assertNotNull(
                "Unable to decode ${asset.name}",
                bitmap,
            )

            requireNotNull(bitmap)

            val message =
                buildString {
                    append("name=${asset.name}")
                    append(" resourceDensity=${value.density}")
                    append(" deviceDensity=${resources.displayMetrics.densityDpi}")
                    append(" width=${bitmap.width}")
                    append(" height=${bitmap.height}")
                    append(" config=${bitmap.config}")
                    append(" byteCount=${bitmap.byteCount}")
                    append(" allocationByteCount=${bitmap.allocationByteCount}")
                    append(" rowBytes=${bitmap.rowBytes}")
                }

            Log.i(
                "ImpulsiveBitmapAudit",
                message,
            )

            println(
                "ImpulsiveBitmapAudit $message",
            )

            bitmap.recycle()
        }
    }

    @Test
    fun optimizedAssetsResolveFromXxxhdpiAtExpectedDimensions() {
        val resources =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .resources

        val deviceDensity =
            resources.displayMetrics.densityDpi

        assets.forEach { asset ->
            val value = TypedValue()

            resources.getValue(
                asset.resourceId,
                value,
                true,
            )

            assertEquals(
                "${asset.name} must resolve from drawable-xxxhdpi",
                DisplayMetrics.DENSITY_XXXHIGH,
                value.density,
            )

            val bitmap =
                BitmapFactory.decodeResource(
                    resources,
                    asset.resourceId,
                )

            assertNotNull(
                "Unable to decode ${asset.name}",
                bitmap,
            )

            requireNotNull(bitmap)

            val expectedWidth =
                (
                    asset.xxxhdpiWidth *
                        deviceDensity.toFloat() /
                        DisplayMetrics.DENSITY_XXXHIGH
                ).roundToInt()

            val expectedHeight =
                (
                    asset.xxxhdpiHeight *
                        deviceDensity.toFloat() /
                        DisplayMetrics.DENSITY_XXXHIGH
                ).roundToInt()

            assertTrue(
                "${asset.name} width was ${bitmap.width}; expected approximately $expectedWidth",
                abs(bitmap.width - expectedWidth) <= 2,
            )

            assertTrue(
                "${asset.name} height was ${bitmap.height}; expected approximately $expectedHeight",
                abs(bitmap.height - expectedHeight) <= 2,
            )

            assertTrue(
                "${asset.name} has an invalid allocation",
                bitmap.allocationByteCount > 0,
            )

            bitmap.recycle()
        }
    }
}
