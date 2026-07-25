package com.impulsive.app.backend.data.restore.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryRestoreActivationTest {
    @Test
    fun `complete activation requests scheduler and succeeds`() = runBlocking {
        val fixture = Fixture()

        val result = fixture.activate()

        assertEquals(CloudRecoveryRestoreResult.Success, result)
        assertTrue(fixture.preferences.enabled)
        assertEquals(0, fixture.keyStore.clearCalls)
        assertEquals(0, fixture.metadataStore.clearCalls)
        assertEquals(1, fixture.scheduler.requestCalls)
        assertEquals(0, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `scheduler failure keeps restored activation enabled`() = runBlocking {
        val fixture = Fixture(schedulerFails = true)

        val result = fixture.activate()

        assertEquals(CloudRecoveryRestoreResult.SuccessBackupRefreshPending, result)
        assertTrue(fixture.preferences.enabled)
        assertEquals(0, fixture.keyStore.clearCalls)
        assertEquals(0, fixture.metadataStore.clearCalls)
        assertEquals(1, fixture.scheduler.requestCalls)
        assertEquals(0, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `keystore failure rolls back local activation`() = runBlocking {
        val fixture = Fixture(keyStoreFails = true)

        val result = fixture.activate()

        assertEquals(CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed, result)
        assertFalse(fixture.preferences.enabled)
        assertEquals(1, fixture.keyStore.clearCalls)
        assertEquals(1, fixture.metadataStore.clearCalls)
        assertEquals(1, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `metadata failure rolls back local activation`() = runBlocking {
        val fixture = Fixture(metadataFails = true)

        val result = fixture.activate()

        assertEquals(CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed, result)
        assertFalse(fixture.preferences.enabled)
        assertEquals(1, fixture.keyStore.clearCalls)
        assertEquals(1, fixture.metadataStore.clearCalls)
        assertEquals(1, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `enabled preference failure rolls back local activation`() = runBlocking {
        val fixture = Fixture(enableFails = true)

        val result = fixture.activate()

        assertEquals(CloudRecoveryRestoreResult.RestoredButCloudRecoverySetupFailed, result)
        assertFalse(fixture.preferences.enabled)
        assertEquals(2, fixture.preferences.setEnabledCalls)
        assertEquals(1, fixture.keyStore.clearCalls)
        assertEquals(1, fixture.metadataStore.clearCalls)
        assertEquals(1, fixture.scheduler.cancelCalls)
    }

    private class Fixture(
        keyStoreFails: Boolean = false,
        metadataFails: Boolean = false,
        enableFails: Boolean = false,
        schedulerFails: Boolean = false,
    ) {
        val keyStore = FakeKeyStore(keyStoreFails)
        val metadataStore = FakeMetadataStore(metadataFails)
        val preferences = FakePreferences(enableFails)
        val scheduler = FakeScheduler(schedulerFails)

        suspend fun activate(): CloudRecoveryRestoreResult =
            activateRestoredCloudRecovery(
                rawDek = ByteArray(CloudRecoveryDekBytes) { 7 },
                wrappedKeyMetadata = validMetadata(),
                keyStore = keyStore,
                metadataStore = metadataStore,
                preferences = preferences,
                scheduler = scheduler,
            )
    }

    private class FakeKeyStore(
        private val fails: Boolean,
    ) : CloudRecoveryRestoreKeyStore {
        var clearCalls = 0
        override fun store(rawDek: ByteArray) {
            if (fails) throw IllegalStateException("keystore failed")
        }
        override fun clear() { clearCalls += 1 }
    }

    private class FakeMetadataStore(
        private val fails: Boolean,
    ) : CloudRecoveryRestoreMetadataStore {
        var clearCalls = 0
        override fun store(metadata: WrappedKeyMetadata) {
            if (fails) throw IllegalStateException("metadata failed")
        }
        override fun clear() { clearCalls += 1 }
    }

    private class FakePreferences(
        private val failEnable: Boolean,
    ) : CloudRecoveryRestorePreferences {
        var enabled = false
        var setEnabledCalls = 0
        override suspend fun setEnabled(enabled: Boolean) {
            setEnabledCalls += 1
            if (enabled && failEnable) throw IllegalStateException("enabled write failed")
            this.enabled = enabled
        }
    }

    private class FakeScheduler(
        private val fails: Boolean,
    ) : CloudRecoveryRestoreScheduler {
        var requestCalls = 0
        var cancelCalls = 0
        override fun request() {
            requestCalls += 1
            if (fails) throw IllegalStateException("schedule failed")
        }
        override fun cancel() { cancelCalls += 1 }
    }

    private companion object {
        fun validMetadata() = WrappedKeyMetadata(
            kdfSalt = ByteArray(CloudRecoverySaltBytes) { 1 },
            wrappedDekIv = ByteArray(CloudRecoveryIvBytes) { 2 },
            wrappedDekCipherText =
                ByteArray(CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes) { 3 },
        )
    }
}