package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.local.preferences.CloudRecoveryStoredUploadOutcome
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryUploadCoordinatorTest {
    @Test
    fun `disabled cloud recovery does not inspect account keys or Drive`() =
        runBlocking {
            val keySource =
                RecordingKeyMaterialSource()

            val authorization =
                RecordingAuthorizationProvider()

            val drive =
                RecordingDriveGateway()

            val result =
                coordinator(
                    enabled =
                        false,

                    account =
                        null,

                    keySource =
                        keySource,

                    authorization =
                        authorization,

                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.Disabled,
                result,
            )

            assertEquals(
                0,
                keySource.dekLoads,
            )

            assertEquals(
                0,
                authorization.requests,
            )

            assertEquals(
                0,
                drive.findCalls,
            )
        }
    @Test
    fun `no authenticated account is a no-op before key or Drive access`() =
        runBlocking {
            val keySource =
                RecordingKeyMaterialSource()

            val authorization =
                RecordingAuthorizationProvider()

            val drive =
                RecordingDriveGateway()

            val result =
                coordinator(
                    account =
                        null,

                    keySource =
                        keySource,

                    authorization =
                        authorization,

                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .NoAuthenticatedAccount,
                result,
            )

            assertEquals(
                0,
                keySource.dekLoads,
            )

            assertEquals(
                0,
                authorization.requests,
            )

            assertEquals(
                0,
                drive.findCalls,
            )
        }

    @Test
    fun `guest account is not uploaded`() =
        runBlocking {
            val authorization =
                RecordingAuthorizationProvider()

            val result =
                coordinator(
                    account =
                        CloudRecoveryUploadAccount(
                            uid =
                                "guest",

                            isAnonymous =
                                true,
                        ),

                    authorization =
                        authorization,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .GuestNotApplicable,
                result,
            )

            assertEquals(
                0,
                authorization.requests,
            )
        }

    @Test
    fun `account mismatch is rejected before Drive authorization`() =
        runBlocking {
            val authorization =
                RecordingAuthorizationProvider()

            val result =
                coordinator(
                    account =
                        CloudRecoveryUploadAccount(
                            uid =
                                "user-b",

                            isAnonymous =
                                false,
                        ),

                    ownerUid =
                        "user-a",

                    authorization =
                        authorization,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .AccountMismatch,
                result,
            )

            assertEquals(
                0,
                authorization.requests,
            )
        }

    @Test
    fun `missing setup material does not contact Drive and loaded dek is zeroed`() =
        runBlocking {
            val dek =
                ByteArray(
                    CloudRecoveryDekBytes,
                ) {
                    7
                }

            val keySource =
                RecordingKeyMaterialSource(
                    dek =
                        dek,

                    metadata =
                        null,
                )

            val authorization =
                RecordingAuthorizationProvider()

            val result =
                coordinator(
                    keySource =
                        keySource,

                    authorization =
                        authorization,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .SetupRequired,
                result,
            )

            assertEquals(
                0,
                authorization.requests,
            )

            assertTrue(
                dek.all {
                    it ==
                        0.toByte()
                },
            )
        }

    @Test
    fun `first upload creates one appDataFolder recovery file using existing dek`() =
        runBlocking {
            val dek =
                ByteArray(
                    CloudRecoveryDekBytes,
                ) {
                    5
                }

            val metadata =
                validMetadata()

            val encryptor =
                RecordingEncryptor()

            val drive =
                RecordingDriveGateway(
                    existingFiles =
                        emptyList(),
                )

            val result =
                coordinator(
                    keySource =
                        RecordingKeyMaterialSource(
                            dek =
                                dek,

                            metadata =
                                metadata,
                        ),

                    drive =
                        drive,

                    encryptor =
                        encryptor,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .Uploaded,
                result,
            )

            assertEquals(
                1,
                drive.findCalls,
            )

            assertEquals(
                1,
                drive.createCalls,
            )

            assertEquals(
                0,
                drive.updateCalls,
            )

            assertEquals(
                CloudRecoveryDriveFileName,
                drive.lastFileName,
            )

            assertEquals(
                CloudRecoveryDriveContentType,
                drive.lastContentType,
            )

            assertEquals(
                "user-a",
                encryptor.ownerUid,
            )

            assertEquals(
                "{\"payload\":true}",
                encryptor.payloadJson,
            )

            assertEquals(
                metadata,
                encryptor.metadata,
            )

            assertTrue(
                encryptor
                    .dekSnapshot
                    .contentEquals(
                        ByteArray(
                            CloudRecoveryDekBytes,
                        ) {
                            5
                        },
                    ),
            )

            assertTrue(
                dek.all {
                    it ==
                        0.toByte()
                },
            )
        }

    @Test
    fun `existing newest file is updated instead of creating a duplicate`() =
        runBlocking {
            val drive =
                RecordingDriveGateway(
                    existingFiles =
                        listOf(
                            DriveAppDataFile(
                                "newest-id",
                                CloudRecoveryDriveFileName,
                                "2026-07-23T12:00:00Z",
                                10,
                            ),

                            DriveAppDataFile(
                                "older-id",
                                CloudRecoveryDriveFileName,
                                "2026-07-22T12:00:00Z",
                                10,
                            ),
                        ),
                )

            val result =
                coordinator(
                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .Uploaded,
                result,
            )

            assertEquals(
                0,
                drive.createCalls,
            )

            assertEquals(
                1,
                drive.updateCalls,
            )

            assertEquals(
                "newest-id",
                drive.updatedFileId,
            )
        }

    @Test
    fun `Drive authorization failure does not become an infinite retry`() =
        runBlocking {
            val result =
                coordinator(
                    authorization =
                        RecordingAuthorizationProvider(
                            result =
                                DriveAuthorizationResult
                                    .Failed(
                                        IllegalStateException(
                                            "authorization unavailable",
                                        ),
                                    ),
                        ),
                ).uploadCurrentRecovery()

            assertTrue(
                result is
                    CloudRecoveryUploadResult
                        .PermanentFailure,
            )
        }

    @Test
    fun `Drive 401 becomes authorization required without retry classification`() =
        runBlocking {
            val drive =
                RecordingDriveGateway(
                    findFailure =
                        DriveAppDataHttpException
                            .Unauthorized(
                                401,
                                null,
                            ),
                )

            val result =
                coordinator(
                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .AuthorizationRequired,
                result,
            )
        }

    @Test
    fun `Drive rate limit becomes retryable`() =
        runBlocking {
            val drive =
                RecordingDriveGateway(
                    findFailure =
                        DriveAppDataHttpException
                            .RateLimited(
                                429,
                                null,
                            ),
                )

            val result =
                coordinator(
                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertTrue(
                result is
                    CloudRecoveryUploadResult
                        .RetryableFailure,
            )
        }

    @Test
    fun `ordinary IO failure becomes retryable`() =
        runBlocking {
            val drive =
                RecordingDriveGateway(
                    findFailure =
                        IOException(
                            "network unavailable",
                        ),
                )

            val result =
                coordinator(
                    drive =
                        drive,
                ).uploadCurrentRecovery()

            assertTrue(
                result is
                    CloudRecoveryUploadResult
                        .RetryableFailure,
            )
        }


    @Test
    fun `uploaded records one attempt and uploaded outcome using completion time`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                            2000L,
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.Uploaded,
                result,
            )

            assertEquals(
                listOf(1000L),
                recorder.attempts,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.Uploaded to 2000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                2000L,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `Drive create completes before uploaded outcome is recorded`() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val recorder =
                RecordingStatusRecorder(events)

            val drive =
                RecordingDriveGateway(
                    existingFiles =
                        emptyList(),

                    events =
                        events,
                )

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                            2000L,
                        ),

                    drive =
                        drive,

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.Uploaded,
                result,
            )

            assertEquals(
                listOf(
                    "attempt",
                    "drive-create",
                    "outcome:Uploaded",
                ),
                events,
            )
        }

    @Test
    fun `Drive update completes before uploaded outcome is recorded`() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val recorder =
                RecordingStatusRecorder(events)

            val drive =
                RecordingDriveGateway(
                    existingFiles =
                        listOf(
                            DriveAppDataFile(
                                "existing-id",
                                CloudRecoveryDriveFileName,
                                "2026-07-23T12:00:00Z",
                                10,
                            ),
                        ),

                    events =
                        events,
                )

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                            2000L,
                        ),

                    drive =
                        drive,

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.Uploaded,
                result,
            )

            assertEquals(
                listOf(
                    "attempt",
                    "drive-update",
                    "outcome:Uploaded",
                ),
                events,
            )
        }

    @Test
    fun `retryable failure records attempt timestamp and preserves previous success`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder(
                    previousSuccess =
                        4444L,
                )

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    drive =
                        RecordingDriveGateway(
                            findFailure =
                                IOException(
                                    "network unavailable",
                                ),
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertTrue(
                result is CloudRecoveryUploadResult.RetryableFailure,
            )

            assertEquals(
                listOf(1000L),
                recorder.attempts,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.RetryableFailure to 1000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                4444L,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `authorization required records attempt timestamp without successful timestamp`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    drive =
                        RecordingDriveGateway(
                            findFailure =
                                DriveAppDataHttpException.Unauthorized(
                                    statusCode =
                                        401,

                                    responseBodySnippet =
                                        null,
                                ),
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.AuthorizationRequired,
                result,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.AuthorizationRequired to 1000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                null,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `account mismatch records attempt timestamp without successful timestamp`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    account =
                        CloudRecoveryUploadAccount(
                            uid =
                                "user-b",

                            isAnonymous =
                                false,
                        ),

                    ownerUid =
                        "user-a",

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.AccountMismatch,
                result,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.AccountMismatch to 1000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                null,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `setup required records attempt timestamp without successful timestamp`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    keySource =
                        RecordingKeyMaterialSource(
                            dek =
                                null,
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.SetupRequired,
                result,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.SetupRequired to 1000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                null,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `permanent failure records attempt timestamp without successful timestamp`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    drive =
                        RecordingDriveGateway(
                            findFailure =
                                DriveAppDataHttpException.Other(
                                    statusCode =
                                        500,

                                    responseBodySnippet =
                                        null,
                                ),
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertTrue(
                result is CloudRecoveryUploadResult.PermanentFailure,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.PermanentFailure to 1000L,
                ),
                recorder.outcomes,
            )

            assertEquals(
                null,
                recorder.lastSuccessfulBackupEpochMillis,
            )
        }

    @Test
    fun `disabled records no attempt and no outcome`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            val result =
                coordinator(
                    enabled =
                        false,

                    clock =
                        FakeClock(),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult.Disabled,
                result,
            )

            assertEquals(
                emptyList<Long>(),
                recorder.attempts,
            )

            assertEquals(
                emptyList<Pair<CloudRecoveryStoredUploadOutcome, Long>>(),
                recorder.outcomes,
            )
        }

    @Test
    fun `cancellation is rethrown without uploaded outcome`() =
        runBlocking {
            val recorder =
                RecordingStatusRecorder()

            try {
                coordinator(
                    clock =
                        FakeClock(
                            1000L,
                        ),

                    drive =
                        RecordingDriveGateway(
                            findFailure =
                                CancellationException(
                                    "cancelled",
                                ),
                        ),

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

                throw AssertionError(
                    "Expected CancellationException.",
                )
            } catch (expected: CancellationException) {
                assertEquals(
                    listOf(1000L),
                    recorder.attempts,
                )

                assertFalse(
                    recorder.outcomes.any {
                        it.first == CloudRecoveryStoredUploadOutcome.Uploaded
                    },
                )
            }
        }
    private fun coordinator(
        enabled:
            Boolean =
            true,

        account:
            CloudRecoveryUploadAccount? =
            CloudRecoveryUploadAccount(
                uid =
                    "user-a",

                isAnonymous =
                    false,
            ),

        completed:
            Boolean =
            true,

        ownerUid:
            String? =
            "user-a",

        keySource:
            CloudRecoveryUploadKeyMaterialSource =
            RecordingKeyMaterialSource(),

        authorization:
            CloudRecoveryUploadAuthorizationProvider =
            RecordingAuthorizationProvider(),

        drive:
            CloudRecoveryUploadDriveGateway =
            RecordingDriveGateway(),

        encryptor:
            CloudRecoveryUploadEnvelopeEncryptor =
            RecordingEncryptor(),

        clock:
            CloudRecoveryUploadClock =
            FakeClock(
                1000L,
                2000L,
            ),

        statusRecorder:
            CloudRecoveryUploadStatusRecorder =
            RecordingStatusRecorder(),
    ): CloudRecoveryUploadCoordinator =
        CloudRecoveryUploadCoordinator(
            enabledStateProvider =
                CloudRecoveryUploadEnabledStateProvider {
                    enabled
                },

            accountProvider =
                object :
                    CloudRecoveryUploadAccountProvider {
                    override fun currentAccount():
                        CloudRecoveryUploadAccount? =
                        account
                },

            ownerStateDataSource =
                FakeOwnerStateDataSource(
                    completed,
                    ownerUid,
                ),

            payloadProvider =
                CloudRecoveryUploadPayloadProvider {
                    "{\"payload\":true}"
                },

            keyMaterialSource =
                keySource,

            authorizationProvider =
                authorization,

            driveGateway =
                drive,

            envelopeEncryptor =
                encryptor,

            clock =
                clock,

            statusRecorder =
                statusRecorder,
        )

    private fun validMetadata():
        WrappedKeyMetadata =
        WrappedKeyMetadata(
            kdfSalt =
                ByteArray(
                    CloudRecoverySaltBytes,
                ) {
                    1
                },

            wrappedDekIv =
                ByteArray(
                    CloudRecoveryIvBytes,
                ) {
                    2
                },

            wrappedDekCipherText =
                ByteArray(
                    CloudRecoveryDekBytes +
                        CloudRecoveryGcmTagBytes,
                ) {
                    3
                },
        )


    private class FakeClock(
        vararg values: Long,
    ) : CloudRecoveryUploadClock {
        private val remaining =
            ArrayDeque(
                values.toList(),
            )

        override fun currentTimeMillis(): Long {
            check(
                remaining.isNotEmpty(),
            ) {
                "No fake clock value remains."
            }

            return remaining.removeFirst()
        }
    }

    private class RecordingStatusRecorder(
        private val events: MutableList<String>? = null,
        previousSuccess: Long? = null,
    ) : CloudRecoveryUploadStatusRecorder {
        val attempts = mutableListOf<Long>()
        val outcomes = mutableListOf<Pair<CloudRecoveryStoredUploadOutcome, Long>>()
        var lastSuccessfulBackupEpochMillis = previousSuccess

        override suspend fun recordAttempt(epochMillis: Long) {
            attempts += epochMillis
            events?.add("attempt")
        }

        override suspend fun recordOutcome(
            outcome: CloudRecoveryStoredUploadOutcome,
            epochMillis: Long,
        ) {
            outcomes += outcome to epochMillis
            events?.add("outcome:${outcome.name}")
            if (outcome == CloudRecoveryStoredUploadOutcome.Uploaded) {
                lastSuccessfulBackupEpochMillis = epochMillis
            }
        }
    }
    private class RecordingKeyMaterialSource(
        private val dek:
            ByteArray? =
            ByteArray(
                CloudRecoveryDekBytes,
            ) {
                4
            },

        private val metadata:
            WrappedKeyMetadata? =
            WrappedKeyMetadata(
                kdfSalt =
                    ByteArray(
                        CloudRecoverySaltBytes,
                    ) {
                        1
                    },

                wrappedDekIv =
                    ByteArray(
                        CloudRecoveryIvBytes,
                    ) {
                        2
                    },

                wrappedDekCipherText =
                    ByteArray(
                        CloudRecoveryDekBytes +
                            CloudRecoveryGcmTagBytes,
                    ) {
                        3
                    },
            ),
    ) : CloudRecoveryUploadKeyMaterialSource {
        var dekLoads =
            0

        var metadataLoads =
            0

        override fun loadDek():
            ByteArray? {
            dekLoads += 1
            return dek
        }

        override fun loadWrappedKeyMetadata():
            WrappedKeyMetadata? {
            metadataLoads += 1
            return metadata
        }
    }

    private class FakeOwnerStateDataSource(
        completed:
            Boolean,

        ownerUid:
            String?,
    ) : CloudRecoveryUploadOwnerStateDataSource {
        override val isCompleted:
            Flow<Boolean> =
            MutableStateFlow(
                completed,
            )

        override val completedAccountUid:
            Flow<String?> =
            MutableStateFlow(
                ownerUid,
            )
    }

    private class RecordingAuthorizationProvider(
        private val result:
            DriveAuthorizationResult =
            DriveAuthorizationResult
                .Authorized(
                    "token",
                ),
    ) : CloudRecoveryUploadAuthorizationProvider {
        var requests =
            0

        override suspend fun requestAuthorization():
            DriveAuthorizationResult {
            requests += 1
            return result
        }
    }

    private class RecordingDriveGateway(
        private val existingFiles:
            List<DriveAppDataFile> =
            emptyList(),

        private val findFailure:
            Throwable? =
            null,

        private val events:
            MutableList<String>? =
            null,
    ) : CloudRecoveryUploadDriveGateway {
        var findCalls =
            0

        var createCalls =
            0

        var updateCalls =
            0

        var lastFileName:
            String? =
            null

        var lastContentType:
            String? =
            null

        var updatedFileId:
            String? =
            null

        override suspend fun findByName(
            accessToken: String,
            fileName: String,
        ): List<DriveAppDataFile> {
            findCalls += 1

            findFailure?.let {
                throw it
            }

            lastFileName =
                fileName

            return existingFiles
        }

        override suspend fun create(
            accessToken: String,
            fileName: String,
            contentType: String,
            bytes: ByteArray,
        ) {
            createCalls += 1
            events?.add("drive-create")
            lastFileName =
                fileName

            lastContentType =
                contentType
        }

        override suspend fun updateContent(
            accessToken: String,
            fileId: String,
            contentType: String,
            bytes: ByteArray,
        ) {
            updateCalls += 1
            events?.add("drive-update")
            updatedFileId =
                fileId

            lastContentType =
                contentType
        }
    }

    private class RecordingEncryptor :
        CloudRecoveryUploadEnvelopeEncryptor {
        var ownerUid:
            String? =
            null

        var payloadJson:
            String? =
            null

        var dekSnapshot:
            ByteArray =
            byteArrayOf()

        var metadata:
            WrappedKeyMetadata? =
            null

        override fun encrypt(
            ownerUid: String,
            payloadJson: String,
            dek: ByteArray,
            wrappedKeyMetadata:
                WrappedKeyMetadata,
        ): ByteArray {
            this.ownerUid =
                ownerUid

            this.payloadJson =
                payloadJson

            this.dekSnapshot =
                dek.copyOf()

            this.metadata =
                wrappedKeyMetadata

            return byteArrayOf(
                9,
                8,
                7,
            )
        }
    }
}