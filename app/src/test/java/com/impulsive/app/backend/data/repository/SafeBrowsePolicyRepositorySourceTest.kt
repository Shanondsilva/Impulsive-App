package com.impulsive.app.backend.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePolicyRepositorySourceTest {
    private val repositorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/SafeBrowsePolicyRepository.kt",
    ).readText()

    private val policySource = File(
        "src/main/java/com/impulsive/app/backend/domain/model/safebrowse/SafeBrowseNavigationPolicy.kt",
    ).readText()

    @Test
    fun repositoryReusesTheExistingBlockedDomainRepository() {
        assertTrue(repositorySource.contains("BlockedDomainRepository"))
        assertTrue(repositorySource.contains("ensureSeeded()"))
        assertTrue(repositorySource.contains("loadBlockedDomains()"))
        assertTrue(repositorySource.contains("SafeBrowsePolicySnapshot.from"))
    }

    @Test
    fun repositoryDoesNotAccessRoomDirectlyOrSwallowFailures() {
        listOf(
            "AppDatabase",
            "blockedDomainDao",
            "BlockedDomainDao",
            "emptySet()",
            "runCatching",
            "getOrElse",
            "recover",
            "DataStore",
            "Firebase",
        ).forEach { forbidden ->
            assertFalse("repository unexpectedly contains $forbidden", repositorySource.contains(forbidden))
        }
    }

    @Test
    fun repositoryContainsNoNetworkRequestCode() {
        listOf("OkHttp", "HttpURLConnection", "Retrofit", "URLConnection", ".openConnection(").forEach {
            assertFalse(repositorySource.contains(it))
        }
    }

    @Test
    fun policyContainsAllRequiredProductionSymbols() {
        listOf(
            "BlockedDomainMatcher.matchedBlockedEntry",
            "normalizeDomainOrNull",
            "scheme != \"https\"",
            "rawUserInfo",
            "port != 443",
            "InvalidHost",
            "BlockedDomain",
        ).forEach { required ->
            assertTrue("policy missing $required", policySource.contains(required))
        }
    }

    @Test
    fun policyDoesNotLogOrHardcodeDomainSubstringChecks() {
        listOf(
            "Log.",
            "println(",
            "startsWith(\"porn",
            "contains(\"porn",
            "endsWith(\"porn",
        ).forEach { forbidden ->
            assertFalse("policy unexpectedly contains $forbidden", policySource.contains(forbidden))
        }
    }
}
