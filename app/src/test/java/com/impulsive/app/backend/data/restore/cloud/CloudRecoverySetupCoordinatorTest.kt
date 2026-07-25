package com.impulsive.app.backend.data.restore.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoverySetupCoordinatorTest {
    @Test
    fun `password confirmation requires an exact match without trimming`() {
        assertFalse(hasValidCloudRecoveryPassword("long-enough", "long-enough "))
        assertFalse(hasValidCloudRecoveryPassword("short", "short"))
        assertTrue(hasValidCloudRecoveryPassword("long-enough", "long-enough"))
    }

    @Test
    fun `short password is rejected and cleared`() = runBlocking {
        val fixture = Fixture()
        val password = "short".toCharArray()

        val result = fixture.coordinator.createCloudRecovery(password)

        assertEquals(CloudRecoverySetupResult.PasswordTooShort, result)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(0, fixture.store.storeDekCalls)
        assertFalse(fixture.preferences.value)
    }

    @Test
    fun `guest account is rejected without storing recovery material`() = runBlocking {
        val fixture = Fixture(account = CloudRecoverySetupAccount("guest", true))
        val password = "long-enough".toCharArray()

        val result = fixture.coordinator.createCloudRecovery(password)

        assertEquals(CloudRecoverySetupResult.GuestNotSupported, result)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(0, fixture.store.storeDekCalls)
        assertEquals(0, fixture.upload.calls)
    }

    @Test
    fun `successful setup stores key material uploads and schedules`() = runBlocking {
        val fixture = Fixture()
        val password = "long-enough".toCharArray()

        val result = fixture.coordinator.createCloudRecovery(password)

        assertEquals(CloudRecoverySetupResult.Success, result)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(1, fixture.store.storeDekCalls)
        assertEquals(1, fixture.store.storeMetadataCalls)
        assertEquals(1, fixture.upload.calls)
        assertEquals(1, fixture.scheduler.requestCalls)
        assertTrue(fixture.preferences.value)
    }

    @Test
    fun `failed initial upload keeps material but leaves opt in off`() = runBlocking {
        val fixture = Fixture(uploadResult = CloudRecoveryUploadResult.RetryableFailure(Exception("offline")))

        val result = fixture.coordinator.createCloudRecovery("long-enough".toCharArray())

        assertTrue(result is CloudRecoverySetupResult.InitialUploadFailed)
        assertEquals(1, fixture.store.storeDekCalls)
        assertEquals(1, fixture.store.storeMetadataCalls)
        assertEquals(0, fixture.scheduler.requestCalls)
        assertFalse(fixture.preferences.value)
    }

    @Test
    fun `disable cancels future scheduling and clears opt in only`() = runBlocking {
        val fixture = Fixture(enabled = true)

        fixture.coordinator.disableCloudRecovery()

        assertEquals(1, fixture.scheduler.cancelCalls)
        assertFalse(fixture.preferences.value)
        assertEquals(1, fixture.preferences.clearBackupStatusCalls)
        assertEquals(0, fixture.store.clearCalls)
    }

    @Test
    fun `retries with complete local material do not create another dek`() = runBlocking {
        val fixture = Fixture(existingMaterial = true)

        val result = fixture.coordinator.createCloudRecovery("long-enough".toCharArray())

        assertEquals(CloudRecoverySetupResult.Success, result)
        assertEquals(0, fixture.store.storeDekCalls)
        assertEquals(0, fixture.store.storeMetadataCalls)
        assertEquals(1, fixture.upload.calls)
    }

    @Test
    fun `local keystore failure returns safe result and clears partial setup`() =
        runBlocking {
            val fixture =
                Fixture(
                    failStoreDek =
                        true,
                )

            val password =
                "long-enough".toCharArray()

            val result =
                fixture
                    .coordinator
                    .createCloudRecovery(
                        password,
                    )

            assertEquals(
                CloudRecoverySetupResult
                    .UnexpectedFailure,
                result,
            )

            assertTrue(
                password.all {
                    it ==
                        '\u0000'
                },
            )

            assertEquals(
                1,
                fixture
                    .store
                    .clearCalls,
            )

            assertEquals(
                0,
                fixture
                    .upload
                    .calls,
            )

            assertFalse(
                fixture
                    .preferences
                    .value,
            )
        }
    @Test
    fun `recovery password is not persisted by setup source`() {
        val source = java.io.File(
            "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoverySetupCoordinator.kt",
        ).readText()

        assertTrue(source.contains("password.fill("))
        assertTrue(source.contains("\\u0000"))
        assertFalse(source.contains("password.toString()"))
        assertFalse(source.contains("setEnabled(password"))
    }

    private class Fixture(
        account: CloudRecoverySetupAccount? = CloudRecoverySetupAccount("user-a", false),
        uploadResult: CloudRecoveryUploadResult = CloudRecoveryUploadResult.Uploaded,
        enabled: Boolean = false,
        existingMaterial: Boolean = false,
        failStoreDek: Boolean = false,
    ) {
val store =
            FakeStore(
                existingMaterial =
                    existingMaterial,
                failStoreDek =
                    failStoreDek,
            )
        val upload = FakeUpload(uploadResult)
        val scheduler = FakeScheduler()
        val preferences = FakePreferences(enabled)
        val coordinator = CloudRecoverySetupCoordinator(
            accountProvider = CloudRecoverySetupAccountProvider { account },
            payloadProvider = CloudRecoverySetupPayloadProvider { "{\"payload\":true}" },
            keyMaterialStore = store,
            crypto = CloudRecoveryCrypto(java.security.SecureRandom()),
            uploadCoordinator = upload,
            scheduler = scheduler,
            preferences = preferences,
        )
    }

    private class FakeStore(
        existingMaterial: Boolean,
        private val failStoreDek: Boolean,
    ) : CloudRecoverySetupKeyMaterialStore {
        private var dek: ByteArray? = if (existingMaterial) ByteArray(CloudRecoveryDekBytes) { 4 } else null
        private var metadata: WrappedKeyMetadata? = if (existingMaterial) validMetadata() else null
        var storeDekCalls = 0
        var storeMetadataCalls = 0
        var clearCalls = 0

        override fun loadDek(): ByteArray? = dek?.copyOf()
        override fun loadWrappedKeyMetadata(): WrappedKeyMetadata? = metadata
        override fun storeDek(dek: ByteArray) {
            if (
                failStoreDek
            ) {
                throw java.security
                    .InvalidAlgorithmParameterException(
                        "Caller-provided IV not permitted",
                    )
            }

            storeDekCalls += 1
            this.dek = dek.copyOf()
        }
        override fun storeWrappedKeyMetadata(metadata: WrappedKeyMetadata) {
            storeMetadataCalls += 1
            this.metadata = metadata
        }
        override fun clear() {
            clearCalls += 1
            dek = null
            metadata = null
        }
    }

    private class FakeUpload(
        private val result: CloudRecoveryUploadResult,
    ) : CloudRecoverySetupUploadCoordinator {
        var calls = 0
        override suspend fun uploadCurrentRecovery(): CloudRecoveryUploadResult {
            calls += 1
            return result
        }
    }

    private class FakeScheduler : CloudRecoverySetupScheduler {
        var requestCalls = 0
        var cancelCalls = 0
        override fun request() { requestCalls += 1 }
        override fun cancel() { cancelCalls += 1 }
    }

    private class FakePreferences(enabled: Boolean) : CloudRecoverySetupPreferences {
        private val state = MutableStateFlow(enabled)
        val value: Boolean get() = state.value
        override val enabled: Flow<Boolean> = state
        var clearBackupStatusCalls = 0
        override suspend fun setEnabled(enabled: Boolean) { state.value = enabled }
        override suspend fun clearBackupStatus() { clearBackupStatusCalls += 1 }
    }

    private companion object {
        fun validMetadata() = WrappedKeyMetadata(
            kdfSalt = ByteArray(CloudRecoverySaltBytes) { 1 },
            wrappedDekIv = ByteArray(CloudRecoveryIvBytes) { 2 },
            wrappedDekCipherText = ByteArray(CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes) { 3 },
        )
    }
}