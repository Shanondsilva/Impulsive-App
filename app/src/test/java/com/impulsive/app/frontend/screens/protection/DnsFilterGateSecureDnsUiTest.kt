package com.impulsive.app.frontend.screens.protection

import com.impulsive.app.backend.session.protection.DnsFilterGateUiState
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterGateSecureDnsUiTest {
    @Test
    fun `continue requires local confirmation and every existing gate requirement`() {
        val ready =
            DnsFilterGateUiState(
                hasChecked = true,
                canEnable = true,
            )

        assertFalse(
            canContinueDnsFilterGate(
                state = ready,
                browserSecureDnsConfirmed = false,
            ),
        )
        assertTrue(
            canContinueDnsFilterGate(
                state = ready,
                browserSecureDnsConfirmed = true,
            ),
        )
        assertFalse(
            canContinueDnsFilterGate(
                state = ready.copy(hasChecked = false),
                browserSecureDnsConfirmed = true,
            ),
        )
        assertFalse(
            canContinueDnsFilterGate(
                state = ready.copy(
                    canEnable = false,
                    privateDnsActive = true,
                ),
                browserSecureDnsConfirmed = true,
            ),
        )
        assertFalse(
            canContinueDnsFilterGate(
                state = ready.copy(
                    canEnable = false,
                    anotherVpnActive = true,
                ),
                browserSecureDnsConfirmed = true,
            ),
        )
    }

    @Test
    fun `gate presents truthful browser instructions and ephemeral confirmation`() {
        val source = gateSource()

        assertTrue(source.contains("Browser Secure DNS"))
        assertTrue(source.contains("Privacy and security → Use Secure DNS → Off"))
        assertTrue(source.contains("Brave Shields & privacy → Use Secure DNS → Off"))
        assertTrue(source.contains("mutableStateOf(false)"))
        assertTrue(source.contains("enabled = continueEnabled"))
        assertTrue(source.contains("I turned off Secure DNS in my protected browsers"))
        assertFalse(source.contains("browser Secure DNS was detected", ignoreCase = true))
        assertFalse(source.contains("Secure DNS is verified", ignoreCase = true))
    }

    @Test
    fun `Website Protection status discloses browser Secure DNS limitation`() {
        val source = File(
            "src/main/java/com/impulsive/app/frontend/screens/premium/" +
                "WebsiteProtectionPlusScreen.kt",
        ).readText()

        assertTrue(source.contains("Browser Secure DNS must remain off for website blocking and "))
        assertTrue(source.contains("SafeSearch enforcement to work."))
    }

    @Test
    fun `user facing copy never recommends Automatic Private DNS`() {
        val userFacingSource = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(userFacingSource.contains("Off or Automatic"))
        assertTrue(gateSource().contains("Set Private DNS to Off, then come back."))
    }

    @Test
    fun `Automatic Private DNS remains blocked by existing policy`() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/device/PrivateDnsChecker.kt",
        ).readText()

        assertTrue(source.contains("State.Opportunistic -> true"))
    }

    private fun gateSource(): String =
        File(
            "src/main/java/com/impulsive/app/frontend/screens/protection/" +
                "DnsFilterGateScreen.kt",
        ).readText()
}
