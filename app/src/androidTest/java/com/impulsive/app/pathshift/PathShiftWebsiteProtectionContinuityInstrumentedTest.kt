package com.impulsive.app.pathshift

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.service.protection.AppMonitorService
import com.impulsive.app.backend.service.protection.ImpulsiveVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftWebsiteProtectionContinuityInstrumentedTest {
    @Test
    fun monitorAndVpnServicesRemainPrivateAndVpnPermissionProtected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val monitor = packageManager.getServiceInfo(
            ComponentName(context, AppMonitorService::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        val vpn = packageManager.getServiceInfo(
            ComponentName(context, ImpulsiveVpnService::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertFalse(monitor.exported)
        assertFalse(vpn.exported)
        assertEquals(Manifest.permission.BIND_VPN_SERVICE, vpn.permission)
    }
}
