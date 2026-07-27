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
    fun `authenticated UID is passed to payload creation`() =
        runBlocking {
            var receivedOwnerUid: String? = null
            val result =
                coordinator(
                    payloadProvider =
                        CloudRecoveryUploadPayloadProvider { ownerUid ->
                            receivedOwnerUid = ownerUid
                            "payload"
                        },
                ).uploadCurrentRecovery()

            assertEquals(CloudRecoveryUploadResult.Uploaded, result)
            assertEquals("user-a", receivedOwnerUid)
        }

    @Test
    fun `account without Google subject hash reaches normal upload path`() =
        runBlocking {
            val authorization = RecordingAuthorizationProvider()

            val result = coordinator(
                account = CloudRecoveryUploadAccount(
                    uid = "user-a",
                    isAnonymous = false,
                    hasGoogleProvider = true,
                    googleSubjectHash = null,
                ),
                authorization = authorization,
            ).uploadCurrentRecovery()

            assertEquals(CloudRecoveryUploadResult.Uploaded, result)
            assertEquals(1, authorization.requests)
        }
    @Test
    fun `non Google account never requests Drive authorization`() =
        runBlocking {
            val authorization = RecordingAuthorizationProvider()
            val transport = RecordingTransport(
                kind = CloudRecoveryTransportKind.FirebaseStorage,
                requiresDriveAuthorization = false,
            )
            val provider = RecordingTransportProvider(transport)

            val result = coordinator(
                account = CloudRecoveryUploadAccount(
                    uid = "user-a",
                    isAnonymous = false,
                    hasGoogleProvider = false,
                    googleSubjectHash = null,
                ),
                authorization = authorization,
                transport = transport,
                transportProvider = provider,
            ).uploadCurrentRecovery()

            assertEquals(CloudRecoveryUploadResult.Uploaded, result)
            assertEquals(0, authorization.requests)
            assertEquals(listOf(CloudRecoveryTransportKind.FirebaseStorage), provider.requestedKinds)
            assertEquals(null, transport.receivedAccessToken)
        }

    @Test
    fun `Google account still requests Drive authorization`() =
        runBlocking {
            val authorization = RecordingAuthorizationProvider()
            val transport = RecordingTransport()
            val provider = RecordingTransportProvider(transport)

            val result = coordinator(
                account = CloudRecoveryUploadAccount(
                    uid = "user-a",
                    isAnonymous = false,
                    hasGoogleProvider = true,
                    googleSubjectHash = null,
                ),
                authorization = authorization,
                transport = transport,
                transportProvider = provider,
            ).uploadCurrentRecovery()

            assertEquals(CloudRecoveryUploadResult.Uploaded, result)
            assertEquals(1, authorization.requests)
            assertEquals(listOf(CloudRecoveryTransportKind.DriveAppData), provider.requestedKinds)
            assertEquals("token", transport.receivedAccessToken)
        }

    @Test
    fun `transport retryable failure maps to upload retryable failure`() =
        runBlocking {
            val failure = IOException("network unavailable")
            val result = coordinator(
                transport = RecordingTransport(
                    uploadOutcome = CloudRecoveryTransportOutcome.RetryableFailure(failure),
                ),
            ).uploadCurrentRecovery()

            assertTrue(result is CloudRecoveryUploadResult.RetryableFailure)
            assertEquals(failure, (result as CloudRecoveryUploadResult.RetryableFailure).cause)
        }
@Test
    fun `disabled cloud recovery does not inspect account keys or Drive`() =
        runBlocking {
            val keySource =
                RecordingKeyMaterialSource()

            val authorization =
                RecordingAuthorizationProvider()

            val transport =
                RecordingTransport()

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

                    transport =
                        transport,
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
                transport.findCalls,
            )
        }
    @Test
    fun `no authenticated account is a no-op before key or Drive access`() =
        runBlocking {
            val keySource =
                RecordingKeyMaterialSource()

            val authorization =
                RecordingAuthorizationProvider()

            val transport =
                RecordingTransport()

            val result =
                coordinator(
                    account =
                        null,

                    keySource =
                        keySource,

                    authorization =
                        authorization,

                    transport =
                        transport,
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
                transport.findCalls,
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

                            hasGoogleProvider =
                                false,
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

                            hasGoogleProvider =
                                true,

                            googleSubjectHash =
                                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
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

            val transport =
                RecordingTransport(
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

                    transport =
                        transport,

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
                transport.findCalls,
            )

            assertEquals(
                1,
                transport.createCalls,
            )

            assertEquals(
                0,
                transport.updateCalls,
            )

            assertEquals(
                CloudRecoveryDriveFileName,
                transport.lastFileName,
            )

            assertEquals(
                CloudRecoveryDriveContentType,
                transport.lastContentType,
            )

            assertEquals(
                "user-a",
                encryptor.ownerUid,
            )

            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                encryptor.ownerGoogleSubjectHash,
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
            val transport =
                RecordingTransport(
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
                    transport =
                        transport,
                ).uploadCurrentRecovery()

            assertEquals(
                CloudRecoveryUploadResult
                    .Uploaded,
                result,
            )

            assertEquals(
                0,
                transport.createCalls,
            )

            assertEquals(
                1,
                transport.updateCalls,
            )

            assertEquals(
                "newest-id",
                transport.updatedFileId,
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
            val transport =
                RecordingTransport(
                    findFailure =
                        DriveAppDataHttpException
                            .Unauthorized(
                                401,
                                null,
                            ),
                )

            val result =
                coordinator(
                    transport =
                        transport,
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
            val transport =
                RecordingTransport(
                    findFailure =
                        DriveAppDataHttpException
                            .RateLimited(
                                429,
                                null,
                            ),
                )

            val result =
                coordinator(
                    transport =
                        transport,
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
            val transport =
                RecordingTransport(
                    findFailure =
                        IOException(
                            "network unavailable",
                        ),
                )

            val result =
                coordinator(
                    transport =
                        transport,
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

            val transport =
                RecordingTransport(
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

                    transport =
                        transport,

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

            val transport =
                RecordingTransport(
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

                    transport =
                        transport,

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

                    transport =
                        RecordingTransport(
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

                    transport =
                        RecordingTransport(
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

                            hasGoogleProvider =
                                true,

                            googleSubjectHash =
                                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
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
    fun `payload creation failure is never recorded as a successful upload`() =
        runBlocking {
            val failure =
                IllegalStateException(
                    "Completed onboarding snapshot unavailable",
                )

            val recorder =
                RecordingStatusRecorder()

            val transport =
                RecordingTransport()

            val result =
                coordinator(
                    payloadProvider =
                        CloudRecoveryUploadPayloadProvider {
                            throw failure
                        },

                    clock =
                        FakeClock(
                            1000L,
                        ),

                    transport =
                        transport,

                    statusRecorder =
                        recorder,
                ).uploadCurrentRecovery()

            assertTrue(
                result is
                    CloudRecoveryUploadResult.PermanentFailure,
            )

            assertEquals(
                failure,
                (
                    result as
                        CloudRecoveryUploadResult.PermanentFailure
                ).cause,
            )

            assertEquals(
                0,
                transport.findCalls,
            )

            assertEquals(
                listOf(
                    CloudRecoveryStoredUploadOutcome.PermanentFailure to
                        1000L,
                ),
                recorder.outcomes,
            )

            assertFalse(
                recorder.outcomes.any {
                    it.first == CloudRecoveryStoredUploadOutcome.Uploaded
                },
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

                    transport =
                        RecordingTransport(
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

                    transport =
                        RecordingTransport(
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

                hasGoogleProvider =
                    true,

                googleSubjectHash =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
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

        transport:
            RecordingTransport =
            RecordingTransport(),

        transportProvider:
            CloudRecoveryUploadTransportProvider =
            CloudRecoveryUploadTransportProvider { transport },

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

        payloadProvider:
            CloudRecoveryUploadPayloadProvider? =
            null,
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

            payloadProvider = payloadProvider ?:
                CloudRecoveryUploadPayloadProvider {
                    "{\"payload\":true}"
                },

            keyMaterialSource =
                keySource,

            authorizationProvider =
                authorization,

            transportProvider =
                transportProvider,

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

    private class RecordingTransportProvider(
        private val transport: CloudRecoveryTransport,
    ) : CloudRecoveryUploadTransportProvider {
        val requestedKinds = mutableListOf<CloudRecoveryTransportKind>()

        override fun transportFor(
            kind: CloudRecoveryTransportKind,
        ): CloudRecoveryTransport {
            requestedKinds += kind
            return transport
        }
    }
    private class RecordingTransport(
        private val existingFiles:
            List<DriveAppDataFile> =
            emptyList(),

        private val findFailure:
            Throwable? =
            null,

        private val events:
            MutableList<String>? =
            null,

        private val uploadOutcome:
            CloudRecoveryTransportOutcome<Unit> =
            CloudRecoveryTransportOutcome.Success(Unit),

        override val kind: CloudRecoveryTransportKind =
            CloudRecoveryTransportKind.DriveAppData,

        override val requiresDriveAuthorization: Boolean =
            true,
    ) : CloudRecoveryTransport {

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

        var receivedEnvelopeBytes:
            ByteArray? =
            null

        var receivedAccessToken:
            String? =
            null

        override suspend fun upload(
            envelopeBytes: ByteArray,
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<Unit> {
            findCalls += 1
            findFailure?.let { throw it }

            receivedEnvelopeBytes =
                envelopeBytes.copyOf()
            receivedAccessToken =
                driveAccessToken
            lastFileName =
                CloudRecoveryDriveFileName
            lastContentType =
                CloudRecoveryDriveContentType

            if (existingFiles.isEmpty()) {
                createCalls += 1
                events?.add("drive-create")
            } else {
                updateCalls += 1
                events?.add("drive-update")
                updatedFileId =
                    existingFiles.first().id
            }

            return uploadOutcome
        }

        override suspend fun download(
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<ByteArray> =
            CloudRecoveryTransportOutcome.PermanentFailure(
                UnsupportedOperationException(),
            )

        override suspend fun deleteAll(
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<Int> =
            CloudRecoveryTransportOutcome.PermanentFailure(
                UnsupportedOperationException(),
            )
    }
    private class RecordingEncryptor :
        CloudRecoveryUploadEnvelopeEncryptor {
        var ownerUid:
            String? =
            null

        var ownerGoogleSubjectHash:
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
            ownerGoogleSubjectHash: String?,
            payloadJson: String,
            dek: ByteArray,
            wrappedKeyMetadata:
                WrappedKeyMetadata,
        ): ByteArray {
            this.ownerUid =
                ownerUid

            this.ownerGoogleSubjectHash =
                ownerGoogleSubjectHash

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
