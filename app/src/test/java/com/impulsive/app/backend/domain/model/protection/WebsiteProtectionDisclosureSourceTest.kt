package com.impulsive.app.backend.domain.model.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionDisclosureSourceTest {

    private val disclosurePolicySource =
        source(
            "src/main/java/com/impulsive/app/backend/domain/model/protection/" +
                "WebsiteProtectionDisclosurePolicy.kt",
        )

    private val protectionSetupModelsSource =
        source(
            "src/main/java/com/impulsive/app/backend/domain/model/protection/" +
                "ProtectionSetupModels.kt",
        )

    private val dataSourceSource =
        source(
            "src/main/java/com/impulsive/app/backend/data/local/preferences/" +
                "ProtectionSetupPreferencesDataSource.kt",
        )

    private val repositorySource =
        source(
            "src/main/java/com/impulsive/app/backend/data/repository/" +
                "ProtectionSetupRepository.kt",
        )

    private val viewModelSource =
        source(
            "src/main/java/com/impulsive/app/backend/session/protection/" +
                "ProtectionSetupViewModel.kt",
        )

    private val appMonitorServiceSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/" +
                "AppMonitorService.kt",
        )

    private val bootCompletedReceiverSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/" +
                "BootCompletedReceiver.kt",
        )

    private val protectionWatchdogSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/" +
                "ProtectionWatchdog.kt",
        )

    private val appNavHostSource =
        source(
            "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        )

    private val dnsFilterGateScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/protection/" +
                "DnsFilterGateScreen.kt",
        )

    private val dnsOverHttpsResolverSource =
        source(
            "src/main/java/com/impulsive/app/backend/service/protection/" +
                "DnsOverHttpsResolver.kt",
        )

    private val websiteProtectionPlusScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/premium/" +
                "WebsiteProtectionPlusScreen.kt",
        )

    private val helpFaqScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/settings/HelpFaqScreen.kt",
        )

    private val settingsScreenSource =
        source(
            "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        )

    private val manifestSource =
        source("src/main/AndroidManifest.xml")

    // Group A: persistence

    @Test
    fun `A1 disclosure consent version key defaults to zero`() {
        assertTrue(
            dataSourceSource.contains(
                "WebsiteProtectionDisclosureConsentVersionKey = \n" +
                    "            intPreferencesKey(",
            ) ||
                dataSourceSource.contains("intPreferencesKey(\n                \"website_protection_disclosure_consent_version\""),
        )
        assertTrue(dataSourceSource.contains("] ?: 0"))
    }

    @Test
    fun `A2 setWebsiteProtectionEnabled returns Boolean`() {
        assertTrue(
            dataSourceSource.contains("suspend fun setWebsiteProtectionEnabled("),
        )
        val setter =
            dataSourceSource.section(
                "suspend fun setWebsiteProtectionEnabled(",
                "suspend fun setWebsiteProtectionDisclosureConsentVersion(",
            )
        assertTrue(setter.contains("): Boolean"))
    }

    @Test
    fun `A3 enabling requires current disclosure version inside the same transaction`() {
        val setter =
            dataSourceSource.section(
                "suspend fun setWebsiteProtectionEnabled(",
                "suspend fun setWebsiteProtectionDisclosureConsentVersion(",
            )

        assertTrue(setter.contains("dataStore.edit"))
        assertTrue(setter.contains("WebsiteProtectionDisclosurePolicy"))
        assertTrue(setter.contains(".isCurrent("))
        assertTrue(setter.contains("return@edit"))
    }

    @Test
    fun `A4 setWebsiteProtectionDisclosureConsentVersion rejects negative versions`() {
        val setter =
            dataSourceSource.section(
                "suspend fun setWebsiteProtectionDisclosureConsentVersion(",
                "suspend fun setWebsiteProtectionAlwaysOn(",
            )

        assertTrue(setter.contains("require("))
        assertTrue(setter.contains("version >= 0"))
    }

    @Test
    fun `A5 repository and ViewModel expose disclosure acceptance path`() {
        assertTrue(
            repositorySource.contains("suspend fun acceptCurrentWebsiteProtectionDisclosure()"),
        )
        assertTrue(
            viewModelSource.contains("suspend fun acceptCurrentWebsiteProtectionDisclosure()"),
        )
        assertTrue(
            viewModelSource.contains("suspend fun enableWebsiteProtectionAfterDisclosure()"),
        )
    }

    // Group B: existing-user safety

    @Test
    fun `B1 AppMonitorService uses runtime authority not raw persisted intent`() {
        assertFalse(appMonitorServiceSource.contains(".websiteProtectionEnabled"))
        assertTrue(appMonitorServiceSource.contains(".websiteProtectionRuntimeEnabled"))
    }

    @Test
    fun `B2 BootCompletedReceiver uses runtime authority`() {
        assertTrue(bootCompletedReceiverSource.contains("setup.websiteProtectionRuntimeEnabled"))
    }

    @Test
    fun `B3 ProtectionWatchdog uses runtime authority`() {
        assertTrue(protectionWatchdogSource.contains("setup.websiteProtectionRuntimeEnabled"))
    }

    @Test
    fun `B4 AppNavHost service recovery uses runtime authority`() {
        assertTrue(
            appNavHostSource.contains("websiteProtectionEnabled = setup.websiteProtectionRuntimeEnabled"),
        )
    }

    @Test
    fun `B5 syncWebsiteProtectionTunnel does not derive VPN authority solely from raw enabled flag`() {
        val syncFunction =
            appMonitorServiceSource.section(
                "private fun syncWebsiteProtectionTunnel(",
                "private fun checkPrivateDnsBypassIfDue(",
            )

        assertTrue(syncFunction.contains("setup.websiteProtectionRuntimeEnabled"))
        assertFalse(syncFunction.contains("setup.websiteProtectionEnabled"))
    }

    // Group C: consent ordering

    @Test
    fun `C1 DnsFilterGateScreen requires disclosure acceptance parameter`() {
        val signature =
            dnsFilterGateScreenSource.section(
                "fun DnsFilterGateScreen(",
                ") {",
            )

        assertTrue(signature.contains("websiteProtectionDisclosureAccepted"))
        assertTrue(signature.contains("continueInProgress"))
        assertTrue(signature.contains("disclosureSaveFailed"))
    }

    @Test
    fun `C2 onContinue signature carries disclosure persistence need`() {
        val signature =
            dnsFilterGateScreenSource.section(
                "fun DnsFilterGateScreen(",
                ") {",
            )

        assertTrue(signature.contains("onContinue"))
        assertTrue(signature.contains("needsDisclosurePersistence"))
        assertTrue(signature.contains(") -> Unit,"))
    }

    @Test
    fun `C3 continue is blocked until disclosure is satisfied`() {
        assertTrue(dnsFilterGateScreenSource.contains("disclosureSatisfied"))
        assertTrue(dnsFilterGateScreenSource.contains("continueEnabled ="))
    }

    @Test
    fun `C4 disclosure names both DNS providers and encrypted resolution`() {
        assertTrue(dnsFilterGateScreenSource.contains("Cloudflare"))
        assertTrue(dnsFilterGateScreenSource.contains("1.1.1.1 for Families"))
        assertTrue(dnsFilterGateScreenSource.contains("AdGuard"))
        assertTrue(dnsFilterGateScreenSource.contains("DNS-over-HTTPS"))
    }

    @Test
    fun `C5 disclosure explicitly states no remote Impulsive VPN server routing`() {
        assertTrue(
            dnsFilterGateScreenSource.contains(
                "does not route your normal web traffic through an Impulsive remote",
            ),
        )
        assertTrue(dnsFilterGateScreenSource.contains("VPN server, and these DNS requests"))
        assertTrue(
            dnsFilterGateScreenSource.contains("are not sent to Impulsive"),
        )
    }

    @Test
    fun `C6 AppNavHost accepts disclosure before evaluating VpnService consent`() {
        val onContinue =
            appNavHostSource.section(
                "onContinue = { needsDisclosurePersistence ->",
                "onTurnOff = {",
            )

        val acceptIndex = onContinue.indexOf("acceptCurrentWebsiteProtectionDisclosure()")
        val consentIndex = onContinue.indexOf("ImpulsiveVpnController")
        val consentIntentIndex = onContinue.indexOf(".consentIntent(", consentIndex)

        assertTrue(acceptIndex >= 0)
        assertTrue(consentIntentIndex > acceptIndex)
    }

    @Test
    fun `C7 enablement happens before VPN start`() {
        val enableFunction =
            appNavHostSource.section(
                "suspend fun enableAndStartWebsiteProtection() {",
                "val vpnConsentLauncher = rememberLauncherForActivityResult(",
            )

        val enableIndex = enableFunction.indexOf("enableWebsiteProtectionAfterDisclosure()")
        val startIndex = enableFunction.indexOf("ImpulsiveVpnController")

        assertTrue(enableIndex >= 0)
        assertTrue(startIndex > enableIndex)
    }

    @Test
    fun `C8 only RESULT_OK proceeds to enable Website Protection`() {
        val launcherCallback =
            appNavHostSource.section(
                "val vpnConsentLauncher = rememberLauncherForActivityResult(",
                "DnsFilterGateScreen(",
            )

        assertTrue(launcherCallback.contains("Activity.RESULT_OK"))
        assertTrue(launcherCallback.contains("enableAndStartWebsiteProtection()"))

        val elseIndex = launcherCallback.indexOf("} else {")
        assertTrue(elseIndex >= 0)
        val elseBranch = launcherCallback.substring(elseIndex)

        assertTrue(elseBranch.contains("dnsGateContinueInProgress"))
        assertFalse(elseBranch.contains("enableAndStartWebsiteProtection()"))
    }

    @Test
    fun `C9 disclosure persistence failure prevents VPN start`() {
        val onContinue =
            appNavHostSource.section(
                "onContinue = { needsDisclosurePersistence ->",
                "onTurnOff = {",
            )

        val failureBlock =
            onContinue.section("if (!accepted) {", "return@launch")

        assertTrue(failureBlock.contains("dnsGateDisclosureSaveFailed"))
        assertFalse(failureBlock.contains("ImpulsiveVpnController"))
    }

    @Test
    fun `C10 Turn Off remains reachable independent of disclosure state`() {
        val onTurnOff =
            appNavHostSource.section(
                "onTurnOff = {",
                "onBack = { navController.safePopBackStack() },",
            )

        assertTrue(onTurnOff.contains("setWebsiteProtectionEnabled(false)"))
        assertFalse(onTurnOff.contains("websiteProtectionDisclosureAccepted"))
    }

    // Group D: provider drift coupling

    @Test
    fun `D1 resolver primary and failover URLs remain unchanged`() {
        assertTrue(
            dnsOverHttpsResolverSource.contains(
                "\"https://family.cloudflare-dns.com/dns-query\"",
            ),
        )
        assertTrue(
            dnsOverHttpsResolverSource.contains(
                "\"https://family.adguard-dns.com/dns-query\"",
            ),
        )
    }

    @Test
    fun `D2 disclosure copy still names both current providers`() {
        assertTrue(dnsFilterGateScreenSource.contains("Cloudflare"))
        assertTrue(dnsFilterGateScreenSource.contains("AdGuard"))
        assertTrue(helpFaqScreenSource.contains("Cloudflare"))
        assertTrue(helpFaqScreenSource.contains("AdGuard"))
    }

    // Group E: copy

    @Test
    fun `E1 no stale on-device-only claims remain in production copy`() {
        val staleClaims =
            listOf(
                "Website filtering should stay on device",
                "filters requests directly on your device",
                "Local DNS-based filtering",
                "local DNS-based filtering",
            )

        val sources =
            listOf(
                websiteProtectionPlusScreenSource,
                helpFaqScreenSource,
                settingsScreenSource,
                dnsFilterGateScreenSource,
            )

        sources.forEach { fileSource ->
            staleClaims.forEach { claim ->
                assertFalse(
                    "Unexpected stale claim \"$claim\"",
                    fileSource.contains(claim),
                )
            }
        }
    }

    @Test
    fun `E2 HelpFaqScreen names both providers and encrypted resolution truthfully`() {
        val questionIndex =
            helpFaqScreenSource.indexOf(
                "Does Impulsive see my browsing when website protection is on?",
            )
        assertTrue(questionIndex >= 0)

        val answerStart =
            helpFaqScreenSource.indexOf("answer = \"", questionIndex)
        val answerEnd =
            helpFaqScreenSource.indexOf("\",\n", answerStart)
        val answer =
            helpFaqScreenSource.substring(answerStart, answerEnd)

        assertTrue(answer.contains("Cloudflare"))
        assertTrue(answer.contains("AdGuard"))
        assertTrue(answer.contains("encrypted DNS-over-HTTPS"))
        assertTrue(
            answer.contains(
                "Normal web traffic is not routed through an Impulsive remote VPN server",
            ),
        )
    }

    @Test
    fun `E3 SettingsScreen describes encrypted DNS resolution`() {
        assertTrue(settingsScreenSource.contains("encrypted DNS resolution"))
    }

    @Test
    fun `E4 AndroidManifest describes encrypted external DNS resolution without remote VPN routing`() {
        assertTrue(manifestSource.contains("encrypted external DNS resolution"))
        assertTrue(manifestSource.contains("not routed to a remote VPN server"))
    }

    @Test
    fun `E5 AndroidManifest special-use declaration preserves permission and service attributes`() {
        val service =
            manifestSource.section(
                "android:name=\"com.impulsive.app.backend.service.protection.ImpulsiveVpnService\"",
                "</service>",
            )

        assertTrue(service.contains("android:permission=\"android.permission.BIND_VPN_SERVICE\""))
        assertTrue(service.contains("android:exported=\"false\""))
        assertTrue(service.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(service.contains("android.net.VpnService"))
    }

    @Test
    fun `E6 WebsiteProtectionPlusScreen discloses providers without new visual components`() {
        assertTrue(websiteProtectionPlusScreenSource.contains("Cloudflare"))
        assertTrue(websiteProtectionPlusScreenSource.contains("AdGuard"))
        assertFalse(websiteProtectionPlusScreenSource.contains("Modifier.styleable"))
    }

    private fun source(relativePath: String): String =
        File(relativePath).readText()

    private fun String.section(from: String, to: String): String {
        val start = indexOf(from)
        require(start >= 0) { "Could not find start marker: $from" }
        val end = indexOf(to, start + from.length)
        require(end >= 0) { "Could not find end marker: $to" }
        return substring(start, end)
    }
}
