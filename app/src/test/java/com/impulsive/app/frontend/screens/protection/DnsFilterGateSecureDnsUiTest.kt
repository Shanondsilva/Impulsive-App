package com.impulsive.app.frontend.screens.protection

import com.impulsive.app.backend.session.protection.DnsFilterGateUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterGateSecureDnsUiTest {

    @Test
    fun `Chrome packages require only Chrome guidance`() {
        val chromePackages =
            setOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
            )

        chromePackages
            .forEach { packageName ->
                assertEquals(
                    listOf(
                        BrowserSecureDnsGuide.Chrome,
                    ),
                    BrowserSecureDnsGuidancePolicy
                        .requiredGuides(
                            setOf(
                                packageName,
                            ),
                        ),
                )
            }
    }

    @Test
    fun `Brave packages require only Brave guidance`() {
        val bravePackages =
            setOf(
                "com.brave.browser",
                "com.brave.browser_beta",
                "com.brave.browser_nightly",
            )

        bravePackages
            .forEach { packageName ->
                assertEquals(
                    listOf(
                        BrowserSecureDnsGuide.Brave,
                    ),
                    BrowserSecureDnsGuidancePolicy
                        .requiredGuides(
                            setOf(
                                packageName,
                            ),
                        ),
                )
            }
    }

    @Test
    fun `mixed browser selection returns deterministic separate guidance`() {
        assertEquals(
            listOf(
                BrowserSecureDnsGuide.Chrome,
                BrowserSecureDnsGuide.Brave,
                BrowserSecureDnsGuide.OtherBrowsers,
            ),
            BrowserSecureDnsGuidancePolicy
                .requiredGuides(
                    setOf(
                        "com.brave.browser",
                        "org.mozilla.firefox",
                        "com.android.chrome",
                    ),
                ),
        )
    }

    @Test
    fun `other browser selection produces one generic requirement`() {
        assertEquals(
            listOf(
                BrowserSecureDnsGuide.OtherBrowsers,
            ),
            BrowserSecureDnsGuidancePolicy
                .requiredGuides(
                    setOf(
                        "org.mozilla.firefox",
                        "com.sec.android.app.sbrowser",
                    ),
                ),
        )
    }

    @Test
    fun `empty and blank package names require no browser confirmation`() {
        assertTrue(
            BrowserSecureDnsGuidancePolicy
                .requiredGuides(
                    emptySet(),
                )
                .isEmpty(),
        )

        assertTrue(
            BrowserSecureDnsGuidancePolicy
                .requiredGuides(
                    setOf(
                        "",
                        "   ",
                    ),
                )
                .isEmpty(),
        )
    }

    @Test
    fun `continue requires every displayed browser confirmation`() {
        val ready =
            DnsFilterGateUiState(
                hasChecked =
                    true,
                canEnable =
                    true,
            )

        val required =
            listOf(
                BrowserSecureDnsGuide.Chrome,
                BrowserSecureDnsGuide.Brave,
            )

        assertFalse(
            canContinueDnsFilterGate(
                state =
                    ready,
                requiredBrowserSecureDnsGuides =
                    required,
                confirmedBrowserSecureDnsGuides =
                    emptySet(),
            ),
        )

        assertFalse(
            canContinueDnsFilterGate(
                state =
                    ready,
                requiredBrowserSecureDnsGuides =
                    required,
                confirmedBrowserSecureDnsGuides =
                    setOf(
                        BrowserSecureDnsGuide.Chrome,
                    ),
            ),
        )

        assertTrue(
            canContinueDnsFilterGate(
                state =
                    ready,
                requiredBrowserSecureDnsGuides =
                    required,
                confirmedBrowserSecureDnsGuides =
                    setOf(
                        BrowserSecureDnsGuide.Chrome,
                        BrowserSecureDnsGuide.Brave,
                    ),
            ),
        )
    }

    @Test
    fun `existing DNS gate requirements still block continue`() {
        val confirmed =
            setOf(
                BrowserSecureDnsGuide.Chrome,
            )

        assertFalse(
            canContinueDnsFilterGate(
                state =
                    DnsFilterGateUiState(
                        hasChecked =
                            false,
                        canEnable =
                            true,
                    ),
                requiredBrowserSecureDnsGuides =
                    listOf(
                        BrowserSecureDnsGuide.Chrome,
                    ),
                confirmedBrowserSecureDnsGuides =
                    confirmed,
            ),
        )

        assertFalse(
            canContinueDnsFilterGate(
                state =
                    DnsFilterGateUiState(
                        hasChecked =
                            true,
                        canEnable =
                            false,
                        privateDnsActive =
                            true,
                    ),
                requiredBrowserSecureDnsGuides =
                    listOf(
                        BrowserSecureDnsGuide.Chrome,
                    ),
                confirmedBrowserSecureDnsGuides =
                    confirmed,
            ),
        )

        assertFalse(
            canContinueDnsFilterGate(
                state =
                    DnsFilterGateUiState(
                        hasChecked =
                            true,
                        canEnable =
                            false,
                        anotherVpnActive =
                            true,
                    ),
                requiredBrowserSecureDnsGuides =
                    listOf(
                        BrowserSecureDnsGuide.Chrome,
                    ),
                confirmedBrowserSecureDnsGuides =
                    confirmed,
            ),
        )
    }

    @Test
    fun `no selected browser adds no artificial confirmation blocker`() {
        assertTrue(
            canContinueDnsFilterGate(
                state =
                    DnsFilterGateUiState(
                        hasChecked =
                            true,
                        canEnable =
                            true,
                    ),
                requiredBrowserSecureDnsGuides =
                    emptyList(),
                confirmedBrowserSecureDnsGuides =
                    emptySet(),
            ),
        )
    }

    @Test
    fun `gate presents truthful browser specific instructions`() {
        val source =
            gateSource()

        assertTrue(
            source.contains(
                "Chrome Secure DNS",
            ),
        )

        assertTrue(
            source.contains(
                "Settings → Privacy and security → Use Secure DNS → Off",
            ),
        )

        assertTrue(
            source.contains(
                "I turned off Secure DNS in Chrome",
            ),
        )

        assertTrue(
            source.contains(
                "Brave Secure DNS",
            ),
        )

        assertTrue(
            source.contains(
                "Settings → Brave Shields & privacy → Use Secure DNS → Off",
            ),
        )

        assertTrue(
            source.contains(
                "I turned off Secure DNS in Brave",
            ),
        )

        assertTrue(
            source.contains(
                "Secure DNS in other browsers",
            ),
        )

        assertTrue(
            source.contains(
                "I checked Secure DNS in my other protected browsers",
            ),
        )

        assertTrue(
            source.contains(
                "Impulsive cannot read this Chrome setting directly.",
            ),
        )

        assertTrue(
            source.contains(
                "Impulsive cannot read this Brave setting directly.",
            ),
        )

        assertTrue(
            source.contains(
                "Impulsive cannot read those browser settings",
            ),
        )
    }

    @Test
    fun `gate does not claim browser setting detection or verification`() {
        val source =
            gateSource()

        assertFalse(
            source.contains(
                "browser Secure DNS was detected",
                ignoreCase =
                    true,
            ),
        )

        assertFalse(
            source.contains(
                "Secure DNS is verified",
                ignoreCase =
                    true,
            ),
        )

        assertFalse(
            source.contains(
                "Secure DNS is currently on",
                ignoreCase =
                    true,
            ),
        )

        assertFalse(
            source.contains(
                "Chrome and Brave can use their own encrypted DNS setting",
            ),
        )

        assertFalse(
            source.contains(
                "I turned off Secure DNS in my protected browsers",
            ),
        )

        assertFalse(
            source.contains(
                "browserSecureDnsConfirmed",
            ),
        )
    }

    @Test
    fun `browser confirmations remain ephemeral and scoped to required guides`() {
        val source =
            gateSource()

        assertTrue(
            source.contains(
                "remember(",
            ),
        )

        assertTrue(
            source.contains(
                "browserSecureDnsGuides",
            ),
        )

        assertTrue(
            source.contains(
                "emptySet<BrowserSecureDnsGuide>()",
            ),
        )

        assertTrue(
            source.contains(
                "confirmedBrowserSecureDnsGuides",
            ),
        )

        assertTrue(
            source.contains(
                ".containsAll(",
            ),
        )

        assertFalse(
            source.contains(
                "DataStore",
            ),
        )

        assertFalse(
            source.contains(
                "SharedPreferences",
            ),
        )
    }

    @Test
    fun `navigation passes selected Website Protection packages`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/" +
                    "AppNavHost.kt",
            )
                .readText()

        assertTrue(
            source.contains(
                "protectedBrowserPackageNames =",
            ),
        )

        assertTrue(
            source.contains(
                ".websiteProtectedAppPackageNames",
            ),
        )
    }

    @Test
    fun `Website Protection status still discloses browser Secure DNS limitation`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/frontend/screens/premium/" +
                    "WebsiteProtectionPlusScreen.kt",
            )
                .readText()

        assertTrue(
            source.contains(
                "Browser Secure DNS must remain off for website blocking and ",
            ),
        )

        assertTrue(
            source.contains(
                "SafeSearch enforcement to work.",
            ),
        )
    }

    @Test
    fun `user facing copy never recommends Automatic Private DNS`() {
        val userFacingSource =
            File(
                "src/main/java",
            )
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension ==
                        "kt"
                }
                .joinToString(
                    separator =
                        "\n",
                ) {
                    it.readText()
                }

        assertFalse(
            userFacingSource.contains(
                "Off or Automatic",
            ),
        )

        assertTrue(
            gateSource().contains(
                "Set Private DNS to Off, then come back.",
            ),
        )
    }

    @Test
    fun `Automatic Private DNS remains blocked by existing policy`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/local/device/" +
                    "PrivateDnsChecker.kt",
            )
                .readText()

        assertTrue(
            source.contains(
                "State.Opportunistic -> true",
            ),
        )
    }

    @Test
    fun `already accepted disclosure satisfies continue regardless of checkbox`() {
        assertTrue(
            websiteProtectionDisclosureSatisfied(
                alreadyAccepted = true,
                acknowledgedNow = false,
            ),
        )

        assertTrue(
            websiteProtectionDisclosureSatisfied(
                alreadyAccepted = true,
                acknowledgedNow = true,
            ),
        )
    }

    @Test
    fun `not yet accepted disclosure with unchecked box does not satisfy continue`() {
        assertFalse(
            websiteProtectionDisclosureSatisfied(
                alreadyAccepted = false,
                acknowledgedNow = false,
            ),
        )
    }

    @Test
    fun `not yet accepted disclosure with checked box satisfies continue`() {
        assertTrue(
            websiteProtectionDisclosureSatisfied(
                alreadyAccepted = false,
                acknowledgedNow = true,
            ),
        )
    }

    private fun gateSource(): String =
        File(
            "src/main/java/com/impulsive/app/frontend/screens/protection/" +
                "DnsFilterGateScreen.kt",
        )
            .readText()
}
