package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpulsiveVpnStopSourceTest {
    private val controller =
        File(
            "src/main/java/com/impulsive/app/backend/service/protection/ImpulsiveVpnController.kt",
        ).readText()

    private val service =
        File(
            "src/main/java/com/impulsive/app/backend/service/protection/ImpulsiveVpnService.kt",
        ).readText()

    private val navigation =
        File(
            "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        ).readText()

    private val viewModel =
        File(
            "src/main/java/com/impulsive/app/backend/session/protection/ProtectionSetupViewModel.kt",
        ).readText()

    @Test
    fun `VPN stop requests the active service shutdown with a stale-service fallback`() {
        val startSource =
            controller.substring(
                controller.indexOf("fun start"),
                controller.indexOf("fun stop"),
            )
        val stopSource =
            controller.substring(
                controller.indexOf("fun stop"),
                controller.indexOf("fun refreshAllowedApplications"),
            )

        assertTrue(startSource.contains("ContextCompat.startForegroundService"))
        assertTrue(stopSource.contains("ImpulsiveVpnService.requestStop()"))
        assertTrue(stopSource.contains("context.stopService"))
        assertTrue(stopSource.contains("if (!stopDelivered)"))
        assertTrue(
            stopSource.indexOf("ImpulsiveVpnService.requestStop()") <
                stopSource.indexOf("context.stopService"),
        )
        assertFalse(stopSource.contains("ContextCompat.startForegroundService"))
        assertFalse(stopSource.contains("context.startService"))
        assertFalse(stopSource.contains("ActionStop"))
    }

    @Test
    fun `navigation delegates user requested stop to the ViewModel`() {
        assertFalse(navigation.contains("ImpulsiveVpnController.stop(context)"))
        assertTrue(navigation.contains("onTurnWebsiteProtectionOff = {"))
        assertTrue(navigation.contains("onTurnOff = {"))
        assertTrue(navigation.contains("setWebsiteProtectionEnabled(false)"))
    }

    @Test
    fun `ViewModel persists disabled state before the single VPN stop`() {
        val method =
            viewModel.substring(
                viewModel.indexOf("fun setWebsiteProtectionEnabled"),
                viewModel.indexOf("fun setWebsiteProtectionAlwaysOn"),
            )

        val repositoryCallIndex =
            method.indexOf("repository")
        val falseArgIndex =
            method.indexOf("false,", repositoryCallIndex)
        val vpnStopIndex =
            method.indexOf("ImpulsiveVpnController")

        assertTrue(repositoryCallIndex >= 0)
        assertTrue(falseArgIndex in repositoryCallIndex until vpnStopIndex)
        assertTrue(vpnStopIndex > falseArgIndex)
    }

    @Test
    fun `service tracks the active instance and explicitly tears down its tunnel`() {
        val create =
            service.substring(
                service.indexOf("override fun onCreate"),
                service.indexOf("override fun onStartCommand"),
            )
        val requestStop =
            service.substring(
                service.indexOf("fun requestStop"),
                service.indexOf("const val ActionStart"),
            )
        val stopTunnelAndSelf =
            service.substring(
                service.indexOf("private fun stopTunnelAndSelf"),
                service.indexOf("companion object"),
            )
        val teardown =
            service.substring(
                service.indexOf("private fun teardownTunnel"),
                service.indexOf("private fun stopTunnelAndSelf"),
            )
        val destroy =
            service.substring(
                service.indexOf("override fun onDestroy"),
                service.indexOf("private fun startTunnelIfNeeded"),
            )

        assertFalse(service.contains("ActionStop"))
        assertTrue(create.contains("activeInstance = this"))
        assertTrue(requestStop.contains("Handler(service.mainLooper).post"))
        assertTrue(requestStop.contains("service.stopTunnelAndSelf()"))
        assertTrue(stopTunnelAndSelf.contains("teardownTunnel()"))
        assertTrue(stopTunnelAndSelf.contains("stopForeground("))
        assertTrue(stopTunnelAndSelf.contains("STOP_FOREGROUND_REMOVE"))
        assertTrue(stopTunnelAndSelf.contains("stopSelf()"))
        assertTrue(teardown.contains("tunnelToClose?.close()"))
        assertTrue(teardown.contains("isRunning = false"))
        assertTrue(destroy.contains("if (activeInstance === this)"))
        assertTrue(destroy.contains("activeInstance = null"))
        assertTrue(destroy.indexOf("teardownTunnel()") >= 0)
        assertTrue(
            destroy.indexOf("teardownTunnel()") <
                destroy.indexOf("stopForeground(STOP_FOREGROUND_REMOVE)"),
        )
        assertTrue(destroy.contains("serviceScope.cancel()"))
    }

    @Test
    fun `refresh action remains a foreground service command`() {
        val refresh =
            controller.substring(
                controller.indexOf("fun refreshAllowedApplications"),
            )

        assertTrue(refresh.contains("ActionRefreshAllowedApplications"))
        assertTrue(refresh.contains("ContextCompat.startForegroundService"))
    }
}