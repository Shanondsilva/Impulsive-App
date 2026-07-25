package com.impulsive.app.backend.data.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLocalDataResetCoordinatorTest {
    @Test
    fun matchingAuthenticatedAccountDeletesThenRestarts() =
        runBlocking {
            val calls = mutableListOf<String>()
            val operations =
                FakeOperations(
                    onDelete = {
                        calls += "delete"
                    },
                    onRestart = {
                        calls += "restart"
                    },
                )

            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "new-user",
                                isAnonymous = false,
                            ),
                        ),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "new-user",
                )

            assertEquals(
                AccountLocalDataResetResult.RestartRequested,
                result,
            )
            assertEquals(
                listOf("delete", "restart"),
                calls,
            )
        }

    @Test
    fun differentAuthenticatedAccountDoesNotDeleteAnything() =
        runBlocking {
            val operations = FakeOperations()
            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "different-user",
                                isAnonymous = false,
                            ),
                        ),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "expected-user",
                )

            assertEquals(
                AccountLocalDataResetResult.SessionChanged,
                result,
            )
            assertEquals(0, operations.deleteCalls)
            assertEquals(0, operations.restartCalls)
        }

    @Test
    fun missingAccountDoesNotDeleteAnything() =
        runBlocking {
            val operations = FakeOperations()
            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(null),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "expected-user",
                )

            assertEquals(
                AccountLocalDataResetResult.SessionChanged,
                result,
            )
            assertEquals(0, operations.deleteCalls)
            assertEquals(0, operations.restartCalls)
        }

    @Test
    fun anonymousAccountDoesNotDeleteAnything() =
        runBlocking {
            val operations = FakeOperations()
            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "guest-user",
                                isAnonymous = true,
                            ),
                        ),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "guest-user",
                )

            assertEquals(
                AccountLocalDataResetResult.SessionChanged,
                result,
            )
            assertEquals(0, operations.deleteCalls)
            assertEquals(0, operations.restartCalls)
        }

    @Test
    fun deletionFailureNeverRestarts() =
        runBlocking {
            val deletionFailure =
                IllegalStateException("delete failed")

            val operations =
                FakeOperations(
                    onDelete = {
                        throw deletionFailure
                    },
                )

            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "new-user",
                                isAnonymous = false,
                            ),
                        ),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "new-user",
                )

            assertTrue(
                result is AccountLocalDataResetResult.Failed,
            )

            result as AccountLocalDataResetResult.Failed

            assertEquals(
                AccountLocalDataResetFailureStage.DeleteLocalData,
                result.stage,
            )
            assertSame(deletionFailure, result.cause)
            assertEquals(1, operations.deleteCalls)
            assertEquals(0, operations.restartCalls)
        }

    @Test
    fun restartFailureIsReportedAfterSuccessfulDeletion() =
        runBlocking {
            val restartFailure =
                IllegalStateException("restart failed")

            val operations =
                FakeOperations(
                    onRestart = {
                        throw restartFailure
                    },
                )

            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "new-user",
                                isAnonymous = false,
                            ),
                        ),
                    operations = operations,
                )

            val result =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "new-user",
                )

            assertTrue(
                result is AccountLocalDataResetResult.Failed,
            )

            result as AccountLocalDataResetResult.Failed

            assertEquals(
                AccountLocalDataResetFailureStage.RestartApp,
                result.stage,
            )
            assertSame(restartFailure, result.cause)
            assertEquals(1, operations.deleteCalls)
            assertEquals(1, operations.restartCalls)
        }

    @Test
    fun duplicateConcurrentResetIsRejected() =
        runBlocking {
            val deletionStarted =
                CompletableDeferred<Unit>()
            val allowDeletionToFinish =
                CompletableDeferred<Unit>()

            val operations =
                FakeOperations(
                    onDelete = {
                        deletionStarted.complete(Unit)
                        allowDeletionToFinish.await()
                    },
                )

            val coordinator =
                AccountLocalDataResetCoordinator(
                    accountProvider =
                        FakeAccountProvider(
                            AccountLocalDataResetAccount(
                                uid = "new-user",
                                isAnonymous = false,
                            ),
                        ),
                    operations = operations,
                )

            val firstRequest =
                async {
                    coordinator.eraseAndRestart(
                        expectedAccountUid = "new-user",
                    )
                }

            deletionStarted.await()

            val duplicateResult =
                coordinator.eraseAndRestart(
                    expectedAccountUid = "new-user",
                )

            assertEquals(
                AccountLocalDataResetResult.AlreadyRunning,
                duplicateResult,
            )

            allowDeletionToFinish.complete(Unit)

            assertEquals(
                AccountLocalDataResetResult.RestartRequested,
                firstRequest.await(),
            )
            assertEquals(1, operations.deleteCalls)
            assertEquals(1, operations.restartCalls)
        }

    @Test
    fun currentAuthenticatedUidExcludesGuestAndBlankAccounts() {
        val normal =
            AccountLocalDataResetCoordinator(
                accountProvider =
                    FakeAccountProvider(
                        AccountLocalDataResetAccount(
                            uid = " user-a ",
                            isAnonymous = false,
                        ),
                    ),
                operations = FakeOperations(),
            )

        val guest =
            AccountLocalDataResetCoordinator(
                accountProvider =
                    FakeAccountProvider(
                        AccountLocalDataResetAccount(
                            uid = "guest",
                            isAnonymous = true,
                        ),
                    ),
                operations = FakeOperations(),
            )

        val blank =
            AccountLocalDataResetCoordinator(
                accountProvider =
                    FakeAccountProvider(
                        AccountLocalDataResetAccount(
                            uid = " ",
                            isAnonymous = false,
                        ),
                    ),
                operations = FakeOperations(),
            )

        assertEquals(
            "user-a",
            normal.currentAuthenticatedAccountUid(),
        )
        assertEquals(
            null,
            guest.currentAuthenticatedAccountUid(),
        )
        assertEquals(
            null,
            blank.currentAuthenticatedAccountUid(),
        )
    }

    private class FakeAccountProvider(
        var account: AccountLocalDataResetAccount?,
    ) : AccountLocalDataResetAccountProvider {
        override fun currentAccount():
            AccountLocalDataResetAccount? =
            account
    }

    private class FakeOperations(
        private val onDelete: suspend () -> Unit = {},
        private val onRestart: () -> Unit = {},
    ) : AccountLocalDataResetOperations {
        var deleteCalls: Int = 0
            private set

        var restartCalls: Int = 0
            private set

        override suspend fun deleteAllData() {
            deleteCalls += 1
            onDelete()
        }

        override fun restartApp() {
            restartCalls += 1
            onRestart()
        }
    }
}
