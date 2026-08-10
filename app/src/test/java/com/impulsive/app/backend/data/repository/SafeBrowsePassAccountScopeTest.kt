package com.impulsive.app.backend.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.data.local.preferences.SafeBrowsePassEntitlementDataSource
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassAccountScopeTest {
    private class AccountSwitchDuringWriteProvider : SafeBrowsePassAccountProvider {
        private val uidState = MutableStateFlow<String?>("account-b")
        private var readCount = 0

        override val authenticatedUid: Flow<String?> = uidState

        override fun currentAuthenticatedUid(): String? {
            readCount += 1

            return if (readCount == 1) {
                "account-a"
            } else {
                "account-b"
            }
        }
    }
    private fun newFixture(): Triple<SafeBrowsePassRepository, SafeBrowsePassEntitlementDataSource, TestSafeBrowsePassAccountProvider> {
        val directory = Files.createTempDirectory("safe-browse-pass-account-scope").toFile()
        val file = File(directory, "safe_browse_pass_entitlement.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val provider = TestSafeBrowsePassAccountProvider("test-safe-browse-user")
        val dataSource = SafeBrowsePassEntitlementDataSource(dataStore)
        val repository = SafeBrowsePassRepository(dataSource, provider)
        return Triple(repository, dataSource, provider)
    }

    @Test
    fun matchingAuthenticatedUidSeesItsEntitlement() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            val entitlement = SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L)
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), entitlement)
            assertEquals(entitlement, repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun anotherUidReceivesAnInactivePassView() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            provider.setUid("other-user")
            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun nullUidReceivesAnInactivePassView() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            provider.setUid(null)
            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun switchingUidImmediatelyHidesTheOldEntitlement() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            provider.setUid("different-user")
            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun onAccountChangedClearsAMismatchedRecord() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            repository.onAccountChanged("other-user")
            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun anonymousOrAbsentAccountCannotReceiveAnEntitlement() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            provider.setUid(null)
            val accepted = repository.setVerifiedEntitlement("test-safe-browse-user", SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            assertFalse(accepted)
            assertEquals(SafeBrowsePassEntitlement(), repository.currentEntitlement())
        } finally {
            Unit
        }
    }

    @Test
    fun staleExpectedUidIsRejected() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            provider.setUid("fresh-user")
            val accepted = repository.setVerifiedEntitlement("stale-user", SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            assertFalse(accepted)
        } finally {
            Unit
        }
    }

    @Test
    fun currentUidIsAccepted() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            val accepted = repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            assertTrue(accepted)
        } finally {
            Unit
        }
    }

    @Test
    fun staleWriteRemainsInvisibleAfterAccountSwitch() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 123L))
            provider.setUid("other-user")
            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            Unit
        }
    }

    @Test
    fun exactExpiryBecomesInactive() = runBlocking {
        val (repository, _, provider) = newFixture()
        try {
            repository.setVerifiedEntitlement(requireNotNull(provider.currentAuthenticatedUid()), SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 1_000L))
            assertTrue(repository.expireCurrentEntitlementIfRequired(1_500L))
            assertFalse(repository.currentEntitlement().active)
        } finally {
            Unit
        }
    }

    @Test
    fun rawPurchaseTokenOrderIdAndSignatureRemainAbsentFromTheCacheModelAndRepository() {
        val source = File("src/main/java/com/impulsive/app/backend/data/local/preferences/SafeBrowsePassEntitlementDataSource.kt").readText()
        val repository = File("src/main/java/com/impulsive/app/backend/data/repository/SafeBrowsePassRepository.kt").readText()
        listOf("purchaseToken", "orderId", "signature").forEach { sensitive ->
            assertFalse(source.contains(sensitive, ignoreCase = true))
            assertFalse(repository.contains(sensitive, ignoreCase = true))
        }
    }

    @Test
    fun accountSwitchDuringWriteRejectsAndRemovesStaleRecord() = runBlocking {
        val directory = Files.createTempDirectory("safe-browse-pass-stale-write").toFile()
        val file = File(directory, "safe_browse_pass_entitlement.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
            val dataSource = SafeBrowsePassEntitlementDataSource(dataStore)
            val repository = SafeBrowsePassRepository(
                dataSource = dataSource,
                accountProvider = AccountSwitchDuringWriteProvider(),
            )

            val accepted = repository.setVerifiedEntitlement(
                expectedUid = "account-a",
                entitlement = SafeBrowsePassEntitlement(
                    active = true,
                    expiryTimeMillis = 10_000L,
                ),
            )

            assertFalse(accepted)

            val record = dataSource.currentRecord()
            assertNull(record.ownerUid)
            assertEquals(SafeBrowsePassEntitlement(), record.entitlement)
        } finally {
            scope.cancel()
        }
    }
}
