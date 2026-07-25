package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteProtectionPackageAttributorTest {
    @Test
    fun `exact owner package selected from protected set`() {
        assertEquals(
            "com.android.chrome",
            selectAttributedWebsitePackage(
                ownerPackages = setOf("com.android.chrome"),
                selectedPackages = setOf(
                    "com.android.chrome",
                    "com.sec.android.app.sbrowser",
                ),
            ),
        )
    }

    @Test
    fun `foreground package cannot override exact owner package`() {
        val foregroundPackage = "com.sec.android.app.sbrowser"

        assertEquals(
            "com.android.chrome",
            selectAttributedWebsitePackage(
                ownerPackages = setOf("com.android.chrome"),
                selectedPackages = setOf(
                    "com.android.chrome",
                    foregroundPackage,
                ),
            ),
        )
    }

    @Test
    fun `non selected uid package returns no attribution`() {
        assertNull(
            selectAttributedWebsitePackage(
                ownerPackages = setOf("com.instagram.android"),
                selectedPackages = setOf("com.android.chrome"),
            ),
        )
    }

    @Test
    fun `shared uid with multiple selected packages is ambiguous`() {
        assertNull(
            selectAttributedWebsitePackage(
                ownerPackages = setOf(
                    "com.example.one",
                    "com.example.two",
                ),
                selectedPackages = setOf(
                    "com.example.one",
                    "com.example.two",
                ),
            ),
        )
    }

    @Test
    fun `single selected app fallback is allowed only when vpn allowed set matches`() {
        assertEquals(
            "com.android.chrome",
            selectSingleWebsitePackageFallback(
                selectedPackages = setOf("com.android.chrome"),
                vpnAllowedPackages = setOf("com.android.chrome"),
            ),
        )
    }

    @Test
    fun `multiple selected apps with unknown owner have no fallback attribution`() {
        assertNull(
            selectSingleWebsitePackageFallback(
                selectedPackages = setOf(
                    "com.android.chrome",
                    "com.sec.android.app.sbrowser",
                ),
                vpnAllowedPackages = setOf(
                    "com.android.chrome",
                    "com.sec.android.app.sbrowser",
                ),
            ),
        )
    }

    @Test
    fun `single selected app fallback is rejected when vpn allowed set differs`() {
        assertNull(
            selectSingleWebsitePackageFallback(
                selectedPackages = setOf("com.android.chrome"),
                vpnAllowedPackages = setOf(
                    "com.android.chrome",
                    "com.example.other",
                ),
            ),
        )
    }
}
