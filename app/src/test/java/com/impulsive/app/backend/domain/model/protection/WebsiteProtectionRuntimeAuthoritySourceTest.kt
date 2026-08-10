package com.impulsive.app.backend.domain.model.protection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the AUD-003 separation between the persisted Website Protection enable
 * intent and runtime authority: no UI surface may describe protection as
 * operational from the raw persisted flag alone.
 */
class WebsiteProtectionRuntimeAuthoritySourceTest {

    private val appNavHostSource =
        source("src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt")

    private val websiteProtectionPlusScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/premium/" +
                "WebsiteProtectionPlusScreen.kt",
        )

    private val settingsScreenSource =
        source("src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt")

    private val homeScreenSource =
        source("src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt")

    private val dnsFilterGateScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/protection/" +
                "DnsFilterGateScreen.kt",
        )

    private val appMonitorServiceSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/AppMonitorService.kt",
        )

    private val bootCompletedReceiverSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/BootCompletedReceiver.kt",
        )

    private val protectionWatchdogSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/ProtectionWatchdog.kt",
        )

    @Test
    fun `AppNavHost passes enable intent and runtime authority under explicit names`() {
        val collapsed = appNavHostSource.collapseWhitespace()

        assertTrue(
            collapsed.contains(
                "websiteProtectionEnableIntent = protectionSetupState.websiteProtectionEnabled,",
            ),
        )
        assertTrue(
            collapsed.contains(
                "websiteProtectionRuntimeEnabled = " +
                    "protectionSetupState.websiteProtectionRuntimeEnabled,",
            ),
        )
        assertFalse(appNavHostSource.contains("isWebsiteProtectionEnabled ="))
    }

    @Test
    fun `ReviewDisclosure routes to the existing DnsFilterGate route`() {
        val dispatcher =
            appNavHostSource.section(
                "onWebsiteSetupAction = { action ->",
                "isPlus = isPlus,",
            )

        val reviewIndex = dispatcher.indexOf("WebsiteProtectionNextAction.ReviewDisclosure")
        assertTrue(reviewIndex >= 0)

        val branch = dispatcher.substring(reviewIndex)
        val gateIndex = branch.indexOf("AppRoutes.DnsFilterGate")
        assertTrue(gateIndex >= 0)
        assertTrue(branch.substring(0, gateIndex).contains("navController.navigate("))
        assertTrue(branch.substring(gateIndex).contains("launchSingleTop = true"))
    }

    @Test
    fun `SettingsScreen ProtectionFocusGroup no longer exposes dedicated Website Protection row`() {
        val group =
            settingsScreenSource.section(
                "private fun ProtectionFocusGroup(",
                "private fun PrivacyAccountGroup(",
            )

        assertFalse(group.contains("Website Protection & DNS Blocking"))
        assertFalse(group.contains("title = \"Website Protection\""))
        assertFalse(group.contains("onOpenWebsiteProtectionPlus"))
        assertFalse(group.contains("websiteProtectionRuntimeEnabled"))
        assertFalse(group.contains("websiteProtectionEnabled"))
    }

    @Test
    fun `SettingsScreen PlusGroup may still describe Website Protection as a Plus benefit`() {
        val plusGroup =
            settingsScreenSource.section(
                "private fun PlusGroup(",
                "private fun SettingsRow(",
        )

        assertTrue(plusGroup.contains("Website Protection"))
        assertTrue(plusGroup.contains("onViewPlus: () -> Unit"))
        assertTrue(plusGroup.contains("onViewPlus()"))
        assertTrue(plusGroup.contains("View Website Protection"))
    }

    @Test
    fun `HomeScreen health and configuration guidance use runtime authority`() {
        val collapsed = homeScreenSource.collapseWhitespace()

        assertTrue(
            collapsed.contains(
                "protectionSetupState.websiteProtectionRuntimeEnabled && " +
                    "ImpulsiveVpnService.isRunning",
            ),
        )
        assertTrue(
            collapsed.contains(
                "if (!protectionSetupState.websiteProtectionRuntimeEnabled) {",
            ),
        )
        assertFalse(homeScreenSource.contains("protectionSetupState.websiteProtectionEnabled"))
    }

    @Test
    fun `WebsiteProtectionPlusScreen operational status is derived from runtime authority`() {
        val statusBlock =
            websiteProtectionPlusScreenSource.section(
                "val operationalStatusText = when {",
                "val statusText = setupPresentation.statusText",
            )

        assertTrue(statusBlock.contains("!runtimeEnabled -> \"Off\""))
        assertTrue(statusBlock.contains("alwaysOn -> \"Always on\""))
        assertTrue(statusBlock.contains("else -> \"Active\""))
        assertFalse(statusBlock.contains("enabledIntent"))
        assertTrue(
            websiteProtectionPlusScreenSource.contains(
                "val pausedByReleaseWindow = runtimeEnabled && releaseWindowActive && !alwaysOn",
            ),
        )
    }

    @Test
    fun `WebsiteProtectionPlusScreen keeps an independent turn off path for enable intent`() {
        assertTrue(
            websiteProtectionPlusScreenSource.contains("showTurnOff = enabledIntent,"),
        )

        val turnOffBlock =
            websiteProtectionPlusScreenSource.section(
                "if (actionPlan.showTurnOff) {",
                "if (actionPlan.showTurnOn) {",
            )

        assertTrue(turnOffBlock.contains("onClick = onTurnOff"))
        assertTrue(turnOffBlock.contains("Turn off Website Protection"))
        assertFalse(turnOffBlock.contains("DisclosureAccepted"))
    }

    @Test
    fun `DnsFilterGate still carries the disclosure and affirmative confirmation`() {
        assertTrue(dnsFilterGateScreenSource.contains("Cloudflare"))
        assertTrue(dnsFilterGateScreenSource.contains("1.1.1.1 for Families"))
        assertTrue(dnsFilterGateScreenSource.contains("AdGuard"))
        assertTrue(dnsFilterGateScreenSource.contains("DNS-over-HTTPS"))
        assertTrue(dnsFilterGateScreenSource.contains("disclosureSatisfied"))
        assertTrue(dnsFilterGateScreenSource.contains("continueEnabled ="))
    }

    @Test
    fun `service recovery paths still use runtime authority only`() {
        assertFalse(appMonitorServiceSource.contains(".websiteProtectionEnabled"))
        assertTrue(appMonitorServiceSource.contains(".websiteProtectionRuntimeEnabled"))
        assertTrue(bootCompletedReceiverSource.contains("setup.websiteProtectionRuntimeEnabled"))
        assertFalse(bootCompletedReceiverSource.contains("setup.websiteProtectionEnabled"))
        assertTrue(protectionWatchdogSource.contains("setup.websiteProtectionRuntimeEnabled"))
        assertFalse(protectionWatchdogSource.contains("setup.websiteProtectionEnabled"))
        assertTrue(
            appNavHostSource.contains(
                "websiteProtectionEnabled = setup.websiteProtectionRuntimeEnabled",
            ),
        )
    }

    @Test
    fun `runtime authority still requires both enable intent and current disclosure`() {
        val modelsSource =
            source(
                "src/main/java/com/impulsive/app/backend/domain/model/protection/" +
                    "ProtectionSetupModels.kt",
            )

        val runtime =
            modelsSource.section(
                "val websiteProtectionRuntimeEnabled:",
                "/**",
            )

        assertTrue(runtime.contains("websiteProtectionEnabled &&"))
        assertTrue(runtime.contains("websiteProtectionDisclosureAccepted"))
    }

    private fun source(relativePath: String): String =
        File(relativePath).readText()

    /** Line-wrapping and CRLF endings must not make these guards brittle. */
    private fun String.collapseWhitespace(): String =
        replace(Regex("\\s+"), " ")

    private fun String.section(from: String, to: String): String {
        val start = indexOf(from)
        require(start >= 0) { "Could not find start marker: $from" }
        val end = indexOf(to, start + from.length)
        require(end >= 0) { "Could not find end marker: $to" }
        return substring(start, end)
    }
}
