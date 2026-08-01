package com.impulsive.app.frontend.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsGroupSummarySourceTest {
    private val settings = File(
        "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
    ).readText()

    private val summariesBlock = settings.section(
        "private object SettingsGroupSummaries {",
        "private val SettingsBoxBorder",
    )

    @Test
    fun containsAllEightConstants() {
        listOf(
            "const val Profile",
            "const val Appearance",
            "const val PivotSetup",
            "const val PersonalSupport",
            "const val ProtectionAndFocus",
            "const val PrivacyAndAccount",
            "const val Support",
            "const val Plus",
        ).forEach { constant ->
            assertTrue(constant, summariesBlock.contains(constant))
        }
    }

    @Test
    fun everySummaryHasOneToThreeMiddleDotSeparatedItemsAndNoCommas() {
        val values = Regex("const val \\w+ = \"([^\"]*)\"")
            .findAll(summariesBlock)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(8, values.size)
        values.forEach { value ->
            assertFalse("no commas: $value", value.contains(","))
            val items = value.split(" • ")
            assertTrue("1..3 items: $value", items.size in 1..3)
            items.forEach { item -> assertTrue(item.isNotBlank()) }
        }
    }

    @Test
    fun plusSummaryIsExact() {
        assertTrue(summariesBlock.contains("const val Plus = \"VPN • Website Protection\""))
    }

    @Test
    fun accordionGroupTitleAndSummaryUseSingleLineEllipsis() {
        val accordionGroup = settings.section(
            "private fun AccordionGroup(",
            "private fun settingsExpandEnter(",
        )
        val header = accordionGroup.section(
            "text = title,",
            "text = summary,",
        )
        val summaryTail = accordionGroup.substring(accordionGroup.indexOf("text = summary,"))

        assertTrue(header.contains("maxLines = 1"))
        assertTrue(header.contains("TextOverflow.Ellipsis"))
        assertTrue(summaryTail.contains("maxLines = 1"))
        assertTrue(summaryTail.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun oldSummaryStringsAreAbsent() {
        listOf(
            "Mind mode • Edit profile\"",
            "\"Theme, Haptics, Home guide\"",
            "\"Onboarding answers can be updated\"",
            "\"Protected apps, permissions\"",
            "\"App lock, Link and Delete Account\"",
            "\"Help, Terms, Privacy, Contact\"",
        ).forEach { stale ->
            assertFalse(stale, settings.contains(stale))
        }
    }

    @Test
    fun protectionAndFocusCommaRoughnessIsFixed() {
        assertFalse(settings.contains("Website Protection, DNS Blocking,"))
        assertTrue(settings.contains("Website Protection & DNS Blocking"))
    }

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
