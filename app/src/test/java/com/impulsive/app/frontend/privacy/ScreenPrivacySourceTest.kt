package com.impulsive.app.frontend.privacy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPrivacySourceTest {
    private val navigation = source(
        "frontend/navigation/AppNavHost.kt",
    )
    private val privacy = source(
        "frontend/privacy/RouteSensitiveScreenPrivacy.kt",
    )
    private val settings = source(
        "frontend/screens/settings/SettingsScreen.kt",
    )
    private val mainActivity = source("MainActivity.kt")

    @Test
    fun privateContentIsObscuredUntilSecureFlagIsApplied() {
        assertTrue(privacy.contains("mutableStateOf(!shouldProtect)"))
        assertTrue(privacy.contains("controller?.apply(shouldProtect)"))
        assertTrue(navigation.contains("if (!privateContentReady)"))
        assertTrue(navigation.contains("MaterialTheme.colorScheme.background"))
    }

    @Test
    fun implementationUsesOfficialPreventionAndRestoresOwnedState() {
        assertTrue(privacy.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        assertTrue(privacy.contains("window.addFlags"))
        assertTrue(privacy.contains("window.clearFlags"))
        assertTrue(privacy.contains("controller?.release()"))
        assertFalse(privacy.contains("screenshot detection", ignoreCase = true))
    }

    @Test
    fun compactSettingHasRequiredCopyAndTalkBackDescription() {
        assertTrue(settings.contains("title = \"Screen privacy\""))
        assertTrue(
            settings.contains(
                "\"Hide Moment Plans, practice and personal patterns from screenshots \"",
            ),
        )
        assertTrue(settings.contains("contentDescription = accessibilityLabel"))
        assertTrue(settings.contains("privateScreenProtectionEnabled = checked"))
    }

    @Test
    fun screenPrivacyDoesNotModifyProtectionVpnOrExternalLaunchCode() {
        assertFalse(privacy.contains("WebsiteProtection"))
        assertFalse(privacy.contains("Vpn"))
        assertFalse(privacy.contains("startActivity"))
        assertFalse(privacy.contains("PackageManager"))
    }

    @Test
    fun appLockStillDecidesWhetherNavigationContentExists() {
        assertTrue(mainActivity.contains("if (locked)"))
        assertTrue(mainActivity.contains("AppLockGateScreen("))
        assertTrue(mainActivity.contains("} else {"))
        assertTrue(mainActivity.contains("AppNavHost("))
    }

    @Test
    fun composeDialogsUseInheritedSecurePolicy() {
        assertFalse(navigation.contains("SecureFlagPolicy.SecureOff"))
        assertFalse(settings.contains("SecureFlagPolicy.SecureOff"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
