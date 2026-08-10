package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP-015 (M5): the mode-to-copy mapping must not regress into contradictory
 * claims. Asserting on Notification internals would need a device, so this
 * locks the string resources and the branch that selects them.
 */
class ProtectionMonitoringNotificationCopyTest {

    private val strings = File("src/main/res/values/strings.xml").readText()

    private val helper = File(
        "src/main/java/com/impulsive/app/backend/service/protection/" +
            "ProtectionNotificationHelper.kt",
    ).readText()

    private fun stringValue(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""")
            .find(strings)?.groupValues?.get(1)
            ?: error("Missing string resource: $name")

    @Test
    fun `every mode has copy and none of it overclaims`() {
        assertEquals("Impulsive protection", stringValue("notif_monitoring_checking_title"))
        assertEquals("Checking protection status.", stringValue("notif_monitoring_checking_body"))

        assertEquals("Website protection is on", stringValue("notif_monitoring_website_title"))
        assertEquals(
            "Protected websites are being checked.",
            stringValue("notif_monitoring_website_body"),
        )

        // Existing app-protection copy is deliberately unchanged.
        assertEquals("Impulsive protection is on", stringValue("notif_monitoring_title"))

        assertEquals(
            "Protected apps and websites are being checked.",
            stringValue("notif_monitoring_app_and_website_body"),
        )
    }

    @Test
    fun `the neutral mode never claims protection is on`() {
        assertFalse(stringValue("notif_monitoring_checking_title").contains("is on"))
        assertFalse(stringValue("notif_monitoring_checking_body").contains("is on"))
    }

    @Test
    fun `website-only copy does not claim apps are protected`() {
        assertFalse(stringValue("notif_monitoring_website_title").contains("apps"))
        assertFalse(stringValue("notif_monitoring_website_body").contains("apps"))
    }

    @Test
    fun `each mode maps to its own title and body`() {
        val generic = helper.substringAfter("val titleRes = when (monitoringMode)")
            .substringBefore(".setContentTitle(")

        listOf(
            "R.string.notif_monitoring_checking_title",
            "R.string.notif_monitoring_website_title",
            "R.string.notif_monitoring_title",
            "R.string.notif_monitoring_checking_body",
            "R.string.notif_monitoring_website_body",
            "R.string.notif_monitoring_body",
            "R.string.notif_monitoring_app_and_website_body",
        ).forEach {
            assertTrue("Monitoring notification must map $it", generic.contains(it))
        }
    }

    @Test
    fun `hide-sensitive mode still exposes no subsystem detail`() {
        val hidden = helper.substringAfter("// Hide-sensitive mode: title only")
            .substringBefore("} else {")

        assertTrue(hidden.contains(""".setContentTitle("Impulsive")"""))
        assertFalse(hidden.contains("setContentText"))
    }

    @Test
    fun `Focus notification copy is untouched`() {
        assertTrue(helper.contains("R.string.notif_focus_active_title"))
        assertTrue(helper.contains("R.string.notif_focus_paused_title"))
    }

    @Test
    fun `the mode defaults to the neutral claim`() {
        assertTrue(
            helper.contains(
                "monitoringMode: ProtectionMonitoringNotificationMode =\n" +
                    "            ProtectionMonitoringNotificationMode.Checking",
            ),
        )
    }
}
