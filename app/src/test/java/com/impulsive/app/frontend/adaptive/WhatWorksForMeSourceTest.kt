package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatWorksForMeSourceTest {
    private val screen = source(
        "frontend/screens/adaptive/WhatWorksForMeScreen.kt",
    )
    private val builder = source(
        "backend/domain/engine/adaptive/WhatWorksForMeBuilder.kt",
    )
    private val navigation = source("frontend/navigation/AppNavHost.kt")
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")

    @Test
    fun routeAndSettingsEntryContainNoUserData() {
        assertTrue(navigation.contains("const val WhatWorksForMe = \"what_works_for_me\""))
        assertTrue(settings.contains("\"What Works for Me\""))
        assertTrue(settings.contains("appLockGuard.run("))
        assertFalse(navigation.contains("what_works_for_me/{"))
    }

    @Test
    fun screenDoesNotExposeSourceIdentityUtilityOrProbability() {
        listOf(
            "sourcePackageName",
            "protectedPackage",
            "protectedDomain",
            "sourceDomain",
            "URL",
            "selectionProbability",
            "finalUtility",
            "baselineUrgeRating",
            "momentCue",
            "actionTarget",
        ).forEach { privateOrInternal ->
            assertFalse(screen.contains(privateOrInternal))
        }
    }

    @Test
    fun builderKeepsWrongTimingNotProvidedAndPendingSeparate() {
        assertTrue(builder.contains("wrongTiming ="))
        assertTrue(builder.contains("notAnswered ="))
        assertTrue(builder.contains("awaitingObservation ="))
        assertTrue(builder.contains("FeedbackCode.WrongTiming"))
        assertTrue(builder.contains("FeedbackCode.NotProvided"))
    }

    @Test
    fun copyAvoidsCausalMedicalAndCertaintyClaims() {
        val userCopy = screen.lowercase()
        listOf(
            "clinically effective",
            "best treatment",
            "guarantees",
            "prevents urges",
            "reduces addiction",
            "caused fewer",
        ).forEach { forbidden ->
            assertFalse(userCopy.contains(forbidden))
        }
        assertTrue(userCopy.contains("does not show that practice caused"))
    }

    @Test
    fun withinOptionPatternsLoopRendersExactlyOnce() {
        val content = screen.section(
            "private fun WhatWorksContent(",
            "private fun InterventionCard(",
        )
        assertEquals(1, content.count("report.withinOptionPatterns.forEach"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
