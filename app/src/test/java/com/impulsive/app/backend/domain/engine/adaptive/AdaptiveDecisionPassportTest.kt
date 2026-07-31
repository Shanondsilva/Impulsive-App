package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDecisionPassportTest {
    @Test
    fun policyVersionIsAuthoritativeAndIndependentOfAppVersion() {
        val source = source(
            "app/src/main/java/com/impulsive/app/backend/domain/engine/adaptive/" +
                "AdaptiveRecommendationPolicyVersion.kt",
        )

        assertEquals(1, AdaptiveRecommendationPolicyVersion.Current)
        assertFalse(source.contains("BuildConfig"))
        assertFalse(source.contains("versionName"))
    }

    @Test
    fun passportContainsNoProtectedSourceIdentityFields() {
        val fieldNames = AdaptiveDecisionEntity::class.java.declaredFields
            .map { it.name.lowercase() }

        listOf(
            "package",
            "url",
            "domain",
            "browser",
            "pagetitle",
            "appLabel",
            "plantext",
            "futuretext",
            "journal",
            "email",
            "uid",
        ).forEach { forbidden ->
            assertFalse(fieldNames.any { forbidden.lowercase() in it })
        }
    }

    @Test
    fun knownFutureProtocolIsHistoricalAndNotExecutable() {
        val historical = InterventionProtocolRegistry.historical(
            InterventionProtocolId("pivot_game"),
            InterventionProtocolVersion(99),
        )

        assertEquals("Pivot Game", historical?.consumerDisplayName)
        assertNull(historical?.executableContract)
        assertNull(
            InterventionProtocolRegistry.resolveExecutable(
                InterventionProtocolId("pivot_game"),
                InterventionProtocolVersion(99),
            ),
        )
    }

    @Test
    fun actualPassportUpdatesAreGuardedBeforeStart() {
        val source = source(
            "app/src/main/java/com/impulsive/app/backend/data/local/dao/" +
                "AdaptiveDecisionDao.kt",
        )
        val actualChoiceQueries = source.substringBefore("recordMomentContextOnce")

        assertTrue(actualChoiceQueries.contains("actualProtocolId"))
        assertTrue(actualChoiceQueries.contains("actualProtocolVersion"))
        assertTrue(actualChoiceQueries.contains("actualPlanContentRevisionId"))
        assertTrue(actualChoiceQueries.contains("startedAtMillis IS NULL"))
    }

    @Test
    fun passportReusesExistingEligibleFamilyMask() {
        val fields = AdaptiveDecisionEntity::class.java.declaredFields.map { it.name }

        assertEquals(1, fields.count { it == "eligibleInterventionsMask" })
        assertFalse(fields.any { it == "eligibleFamiliesMask" })
    }

    private fun source(path: String): String {
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull(File::isFile)
        return requireNotNull(file) { "Could not find $path" }.readText()
    }
}
