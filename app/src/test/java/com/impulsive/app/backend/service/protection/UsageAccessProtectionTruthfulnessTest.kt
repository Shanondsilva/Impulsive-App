package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP-015: revoking Usage Access in system Settings stops protected-app
 * interception, but the service kept claiming "Impulsive protection is on"
 * because it reasoned from configuration rather than the live permission.
 *
 * Configured and operational are different questions. These lock the runtime
 * reasoning, the warning transitions and the service wiring that separates them.
 */
class UsageAccessProtectionTruthfulnessTest {

    private val service = File(
        "src/main/java/com/impulsive/app/backend/service/protection/AppMonitorService.kt",
    ).readText()

    // ------------------------------------------------------------------
    // M1: operational app protection follows live Usage Access
    // ------------------------------------------------------------------

    private fun operational(
        usageAccessGranted: Boolean,
        selected: Set<String> = setOf("com.example.app"),
        enabled: Boolean = true,
        transitionCompleted: Boolean = false,
    ) = shouldMonitorProtectedApps(
        appProtectionEnabled = enabled,
        selectedPackages = selected,
        usageAccessGranted = usageAccessGranted,
        transitionCompleted = transitionCompleted,
    )

    @Test
    fun `selected apps with Usage Access granted are operational`() {
        assertTrue(operational(usageAccessGranted = true))
    }

    @Test
    fun `selected apps without Usage Access are not operational`() {
        // The whole defect in one assertion.
        assertFalse(operational(usageAccessGranted = false))
    }

    @Test
    fun `no selected apps is never operational`() {
        assertFalse(operational(usageAccessGranted = true, selected = emptySet()))
    }

    @Test
    fun `legacy transition-completed state still monitors`() {
        // Existing compatibility must not regress: enabled=false is fine when
        // the transition already completed.
        assertTrue(
            operational(
                usageAccessGranted = true,
                enabled = false,
                transitionCompleted = true,
            ),
        )
        assertFalse(
            operational(
                usageAccessGranted = false,
                enabled = false,
                transitionCompleted = true,
            ),
        )
    }

    @Test
    fun `Website Protection keeps service recovery valid without Usage Access`() {
        assertTrue(
            shouldRecoverProtectionService(
                appProtectionEnabled = true,
                selectedPackages = setOf("com.example.app"),
                usageAccessGranted = false,
                websiteProtectionEnabled = true,
            ),
        )
        // ...and app protection alone does not, once the permission is gone.
        assertFalse(
            shouldRecoverProtectionService(
                appProtectionEnabled = true,
                selectedPackages = setOf("com.example.app"),
                usageAccessGranted = false,
                websiteProtectionEnabled = false,
            ),
        )
    }

    // ------------------------------------------------------------------
    // M4: warning transitions (mirrors reconcileUsageAccessWarning)
    // ------------------------------------------------------------------

    /** Mirror of the service's reconciler, to test the transition rules. */
    private class WarningReconciler {
        var last: Boolean? = null
        val actions = mutableListOf<String>()

        fun reconcile(configuredAppProtection: Boolean, usageAccessGranted: Boolean) {
            val required = configuredAppProtection && !usageAccessGranted
            if (last == required) return
            last = required
            actions += if (required) "show" else "cancel"
        }
    }

    @Test
    fun `warning appears once and does not repeat while the outage continues`() {
        val reconciler = WarningReconciler()

        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = false)
        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = false)
        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = false)

        assertEquals(listOf("show"), reconciler.actions)
    }

    @Test
    fun `restoring Usage Access cancels the warning`() {
        val reconciler = WarningReconciler()

        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = false)
        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = true)

        assertEquals(listOf("show", "cancel"), reconciler.actions)
    }

    @Test
    fun `a fresh service cancels a stale warning left by a previous instance`() {
        // Null start state is what makes the first granted tick act at all.
        val reconciler = WarningReconciler()

        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = true)

        assertEquals(listOf("cancel"), reconciler.actions)
    }

    @Test
    fun `no warning when app protection was never configured`() {
        val reconciler = WarningReconciler()

        reconciler.reconcile(configuredAppProtection = false, usageAccessGranted = false)

        // Usage Access is irrelevant if app monitoring is not configured.
        assertEquals(listOf("cancel"), reconciler.actions)
        assertFalse(reconciler.actions.contains("show"))
    }

    @Test
    fun `losing configuration during an outage clears the warning`() {
        val reconciler = WarningReconciler()

        reconciler.reconcile(configuredAppProtection = true, usageAccessGranted = false)
        reconciler.reconcile(configuredAppProtection = false, usageAccessGranted = false)

        assertEquals(listOf("show", "cancel"), reconciler.actions)
    }

    // ------------------------------------------------------------------
    // M3: service wiring that cannot be cheaply instantiated
    // ------------------------------------------------------------------

    /**
     * Extracts exactly one function body by balancing braces.
     *
     * Slicing to the next declaration would overshoot, because the functions
     * this file calls are defined further down than the ones it calls them from.
     */
    private fun functionBody(signature: String): String {
        val start = service.indexOf(signature)
        require(start >= 0) { "Missing function: $signature" }

        val open = service.indexOf('{', start)
        var depth = 0
        var index = open

        while (index < service.length) {
            when (service[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return service.substring(open, index + 1)
                }
            }
            index++
        }

        error("Unbalanced braces after: $signature")
    }

    private fun evaluateForegroundAppBody(): String =
        functionBody("private suspend fun evaluateForegroundApp()")

    @Test
    fun `runtime reasons take a Usage Access parameter`() {
        assertTrue(
            service.contains("private fun hasActiveProtectionReason(\n        usageAccessGranted: Boolean = usageAccessChecker.hasUsageAccess(),"),
        )
        assertTrue(
            service.contains("private fun hasActiveNonFocusProtectionReason(\n        usageAccessGranted: Boolean = usageAccessChecker.hasUsageAccess(),"),
        )
    }

    @Test
    fun `runtime reasons use operational app protection not bare configuration`() {
        val reasons = functionBody("private fun hasActiveProtectionReason(") +
            functionBody("private fun hasActiveNonFocusProtectionReason(")

        assertTrue(reasons.contains("hasOperationalAppProtection(usageAccessGranted)"))
        // Configuration alone must no longer prove runtime authority.
        assertFalse(reasons.contains("configurationDrivenAppProtectionConsented"))
    }

    @Test
    fun `operational app protection defers to the shared policy`() {
        val helper = functionBody("private fun hasOperationalAppProtection(")

        assertTrue(helper.contains("shouldMonitorProtectedApps("))
        assertTrue(helper.contains("usageAccessGranted = usageAccessGranted"))
    }

    @Test
    fun `evaluateForegroundApp reads Usage Access once per iteration`() {
        val evaluate = evaluateForegroundAppBody()

        assertEquals(
            1,
            Regex("""usageAccessChecker\.hasUsageAccess\(\)""").findAll(evaluate).count(),
        )
        assertTrue(evaluate.contains("reconcileUsageAccessWarning(usageAccessGranted)"))
        assertTrue(evaluate.contains("hasActiveProtectionReason(usageAccessGranted)"))
    }

    @Test
    fun `no operational reason stops the service`() {
        val evaluate = evaluateForegroundAppBody()

        assertTrue(
            evaluate.contains("if (!hasActiveProtectionReason(usageAccessGranted)) {"),
        )
        assertTrue(evaluate.contains("stopSelfSafely()"))
    }

    @Test
    fun `missing Usage Access reconciles the notification unless Focus owns it`() {
        val evaluate = evaluateForegroundAppBody()

        assertTrue(evaluate.contains("if (!focusMonitoringNotificationActive) {"))
        assertTrue(
            evaluate.contains("replaceForegroundWithGenericMonitoringNotification()"),
        )
    }

    @Test
    fun `the old one-way warning latch is gone`() {
        // Two competing warning systems would reintroduce the stuck notification.
        assertFalse(service.contains("usageAccessAlertPosted"))
        assertTrue(service.contains("private var lastUsageAccessWarningRequired: Boolean? = null"))
    }

    @Test
    fun `foreground promotion resolves a live mode instead of a fixed claim`() {
        val promote = functionBody("private fun promoteToForegroundMonitor()")

        assertTrue(promote.contains("monitoringMode = currentMonitoringNotificationMode()"))
    }

    @Test
    fun `the generic monitoring notification resolves a live mode`() {
        val replace =
            functionBody("private fun replaceForegroundWithGenericMonitoringNotification()")

        assertTrue(replace.contains("monitoringMode = currentMonitoringNotificationMode()"))
        // The user-dismissal contract must survive.
        assertTrue(replace.contains("shouldPublishMonitoringNotificationUpdate()"))
    }

    @Test
    fun `Focus completion reconciles against live Usage Access`() {
        val focusEnd = functionBody("private fun reconcileMonitoringAfterFocusEnded()")

        assertTrue(focusEnd.contains("val usageAccessGranted = usageAccessChecker.hasUsageAccess()"))
        assertTrue(focusEnd.contains("reconcileUsageAccessWarning(usageAccessGranted)"))
        assertTrue(
            focusEnd.contains("hasActiveNonFocusProtectionReason(usageAccessGranted)"),
        )
    }

    @Test
    fun `permission loss never erases the user's configuration`() {
        val evaluate = evaluateForegroundAppBody()

        listOf(
            "clearSelectedBlockedApps",
            "setAppProtectionMonitorEnabled(false)",
            "setWebsiteProtectionEnabled(false)",
            "resetProtectionSetup",
        ).forEach {
            assertFalse("Permission loss must not call $it", evaluate.contains(it))
        }
    }
}
