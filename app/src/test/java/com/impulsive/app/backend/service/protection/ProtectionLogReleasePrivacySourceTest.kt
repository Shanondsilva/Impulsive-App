package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionLogReleasePrivacySourceTest {
    private val protectionLog = source("ProtectionLog.kt")
    private val appMonitorSource = source("AppMonitorService.kt")
    private val vpnSource = source("ImpulsiveVpnService.kt")
    private val overlaySource = source("ProtectionInterruptionOverlay.kt")
    private val reminderCoordinatorSource = source("InterruptionNotificationReminderCoordinator.kt")

    @Test
    fun debugLoggingIsGuardedByBuildConfigDebug() {
        assertTrue(protectionLog.contains("import com.impulsive.app.BuildConfig"))

        val debugFunction = protectionLog.section(
            "fun debug(message: String) {",
            "fun warn(",
        )
        assertTrue(debugFunction.contains("if (BuildConfig.DEBUG)"))
        assertTrue(debugFunction.contains("Log.d(Tag, message)"))

        val debugThrottledFunction = protectionLog.section(
            "fun debugThrottled(",
            "fun warnThrottled(",
        )
        assertTrue(debugThrottledFunction.contains("Log.d(Tag, message)"))

        assertEquals(2, protectionLog.count("Log.d("))
    }

    @Test
    fun debugThrottledReturnsBeforeRecordingReleaseKeys() {
        val debugThrottledFunction = protectionLog.section(
            "fun debugThrottled(",
            "fun warnThrottled(",
        )

        assertTrue(debugThrottledFunction.contains("if (!BuildConfig.DEBUG)"))
        assertTrue(debugThrottledFunction.contains("return false"))
        assertTrue(
            debugThrottledFunction.indexOf("return false") <
                debugThrottledFunction.indexOf("logThrottled("),
        )
        assertTrue(debugThrottledFunction.contains("Log.d(Tag, message)"))
    }

    @Test
    fun warningAndErrorDebugDetailsAreExcludedFromRelease() {
        val warnFunction = protectionLog.section("fun warn(", "fun error(")
        assertTrue(warnFunction.contains("debugDetails: String? = null"))

        val errorFunction = protectionLog.section("fun error(", "fun debugThrottled(")
        assertTrue(errorFunction.contains("debugDetails: String? = null"))

        val warnThrottledFunction = protectionLog.section(
            "fun warnThrottled(",
            "private fun messageWithDebugDetails(",
        )
        assertTrue(warnThrottledFunction.contains("debugDetails: String? = null"))

        val helper = protectionLog.section(
            "private fun messageWithDebugDetails(",
            "private inline fun logThrottled(",
        )
        assertTrue(helper.contains("BuildConfig.DEBUG"))
        assertTrue(helper.contains("!debugDetails.isNullOrBlank()"))
        assertTrue(helper.contains("\"\$message [\$debugDetails]\""))
        assertTrue(helper.contains("message"))
    }

    @Test
    fun overlayTimeoutKeepsPackageOnlyInDebugDetails() {
        val messageIndex = overlaySource.indexOf(
            "\"Protection overlay attachment timed out\"",
        )
        assertTrue(messageIndex >= 0)

        val callStart = overlaySource.lastIndexOf("ProtectionLog.error(", messageIndex)
        val timeoutBlock = overlaySource.substring(
            callStart,
            overlaySource.indexOf("removeViewAfterFailedAdd(windowManager, view)", callStart),
        )

        assertTrue(timeoutBlock.contains("message ="))
        assertTrue(timeoutBlock.contains("debugDetails ="))
        assertTrue(timeoutBlock.contains("\"package=\$sourcePackageName\""))

        assertFalse(
            overlaySource.contains(
                "\"Protection overlay attachment timed out: package=\$sourcePackageName\"",
            ),
        )
    }

    @Test
    fun dohFailureKeepsRawReasonOnlyInDebugDetails() {
        val blockStart = vpnSource.indexOf("key = \"doh_resolve_failure\"")
        assertTrue(blockStart >= 0)
        val resolveFailureBlock = vpnSource.substring(
            blockStart,
            vpnSource.indexOf(
                "notificationHelper.showEncryptedDnsUnreachableNotification()",
                blockStart,
            ),
        )

        assertTrue(
            resolveFailureBlock.contains(
                "\"(consecutiveFailures=\${health.consecutiveFailureCount})\",",
            ),
        )
        assertTrue(resolveFailureBlock.contains("debugDetails ="))
        assertTrue(resolveFailureBlock.contains("\"reason=\$reason\","))
        assertFalse(resolveFailureBlock.contains("reason=\$reason)"))
    }

    @Test
    fun dohSelfTestKeepsRawReasonOnlyInDebugDetails() {
        val selfTestFunction = vpnSource.substring(
            vpnSource.indexOf("private suspend fun runResolverSelfTest"),
            vpnSource.indexOf("private fun buildResolverSelfTestQuery"),
        )

        assertTrue(selfTestFunction.contains("message ="))
        assertTrue(selfTestFunction.contains("\"DoH self-test failed\","))
        assertTrue(selfTestFunction.contains("debugDetails ="))
        assertTrue(
            selfTestFunction.contains(
                "\"reason=\${health.lastFailureReason ?: \"unknown\"}\",",
            ),
        )
        assertFalse(selfTestFunction.contains("\"DoH self-test failed \" +"))
    }

    @Test
    fun sensitiveAppMonitorDiagnosticsRemainDebugOnly() {
        val sensitivePhrases = listOf(
            "Website Protection owns package",
            "Protected package detected",
            "Focus session completed",
            "Overlay display requested",
            "posted stage=",
        )

        sensitivePhrases.forEach { phrase ->
            val phraseIndex = appMonitorSource.indexOf(phrase)
            assertTrue("Expected to find phrase: $phrase", phraseIndex >= 0)

            val callStart = appMonitorSource.lastIndexOf("ProtectionLog.", phraseIndex)
            assertTrue("No ProtectionLog call found before: $phrase", callStart >= 0)

            val callSite = appMonitorSource.substring(callStart, phraseIndex)
            assertTrue(
                "Expected \"$phrase\" to be logged via ProtectionLog.debug/debugThrottled",
                callSite.contains("ProtectionLog.debug"),
            )
        }
    }

    @Test
    fun reminderCoordinatorUsesDebugLogger() {
        assertTrue(
            appMonitorSource.contains(
                "log = { message -> ProtectionLog.debug(message) }",
            ),
        )
        assertFalse(reminderCoordinatorSource.contains("android.util.Log"))
    }

    @Test
    fun websiteAttributionIdentitiesRemainDebugOnly() {
        val phrase = "Website Protection attribution decision:"
        val phraseIndex = vpnSource.indexOf(phrase)
        assertTrue(phraseIndex >= 0)

        val callStart = vpnSource.lastIndexOf("ProtectionLog.", phraseIndex)
        val callSite = vpnSource.substring(callStart, phraseIndex)
        assertTrue(callSite.contains("ProtectionLog.debugThrottled"))

        assertTrue(vpnSource.contains("attributedPackage=\$exactPackage"))
        assertTrue(vpnSource.contains("currentForegroundPackage=\$currentForegroundPackage"))
    }

    @Test
    fun noBlockedDomainIsAddedToProtectionLogs() {
        val forbidden = listOf("blockedDomain=", "blockedEntry=", "queriedDomain=", "host=")

        vpnSource.protectionLogCallBlocks().forEach { block ->
            forbidden.forEach { marker ->
                assertFalse(
                    "Unexpected \"$marker\" inside a ProtectionLog call",
                    block.contains(marker),
                )
            }
        }
    }

    private fun source(fileName: String): String =
        File(
            "src/main/java/com/impulsive/app/backend/service/protection/$fileName",
        ).readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }

    private fun String.protectionLogCallBlocks(): List<String> {
        val starts = Regex("ProtectionLog\\.\\w+\\(").findAll(this).map { it.range.first }.toList()
        return starts.mapIndexed { index, start ->
            val end = if (index + 1 < starts.size) starts[index + 1] else length
            substring(start, end)
        }
    }
}
