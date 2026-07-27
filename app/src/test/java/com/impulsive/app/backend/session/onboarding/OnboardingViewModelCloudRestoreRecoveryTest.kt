package com.impulsive.app.backend.session.onboarding

import com.impulsive.app.backend.data.restore.cloud.CloudRestorePostImportRecoveryResult
import com.impulsive.app.backend.data.repository.AuthenticatedOnboardingResolution
import com.impulsive.app.backend.data.repository.CompleteOnboardingResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelCloudRestoreRecoveryTest {
    @Test
    fun cleanAndAuthorizationOnlyResultsContinueNormally() {
        listOf(
            CloudRestorePostImportRecoveryResult.NothingPending,
            CloudRestorePostImportRecoveryResult.Finalized,
            CloudRestorePostImportRecoveryResult
                .AuthorizationWithoutCommittedImportCleared,
        ).forEach { result ->
            val decision = result.toStartupDecision()

            assertTrue(
                decision is
                    CloudRestorePostImportStartupDecision.Continue,
            )
            assertNull(
                (
                    decision as
                        CloudRestorePostImportStartupDecision.Continue
                ).message,
            )
    }
}

private suspend fun completeRestoredOnboarding(
    finalization: CloudRestorePostImportRecoveryResult,
): CompletionProbe {
    val operation =
        QueuedRecoveryOperation(
            CloudRestorePostImportRecoveryResult
                .RequiresOnboardingSetup,
            finalization,
        )
    val recovery = CloudRestoreOnboardingRecovery(operation)
    recovery.resolveAuthenticatedOnboarding {
        error("Remote account resolution must be bypassed")
    }
    var accountCompletionCalls = 0
    val result =
        recovery.completeOnboarding {
            accountCompletionCalls += 1
            CompleteOnboardingResult.Completed
        }
    val dispatch = dispatch(result)
    return CompletionProbe(
        recoveryCalls = operation.calls,
        accountCompletionCalls = accountCompletionCalls,
        completedCalls = dispatch.completedCalls,
        completionState = dispatch.completionState,
        accountResolutionState = dispatch.accountResolutionState,
    )
}

private suspend fun retryBlockedFinalization(
    blockedResult: CloudRestorePostImportRecoveryResult,
): RetryProbe {
    val operation =
        QueuedRecoveryOperation(
            CloudRestorePostImportRecoveryResult
                .RequiresOnboardingSetup,
            blockedResult,
            CloudRestorePostImportRecoveryResult.Finalized,
        )
    val recovery = CloudRestoreOnboardingRecovery(operation)
    recovery.resolveAuthenticatedOnboarding {
        error("Remote account resolution must be bypassed")
    }
    var accountCompletionCalls = 0
    val first =
        dispatch(
            recovery.completeOnboarding {
                accountCompletionCalls += 1
                CompleteOnboardingResult.Completed
            },
        )
    val retry =
        dispatch(
            recovery.completeOnboarding {
                accountCompletionCalls += 1
                CompleteOnboardingResult.Completed
            },
        )
    return RetryProbe(
        recoveryCalls = operation.calls,
        accountCompletionCalls = accountCompletionCalls,
        firstCompletedCalls = first.completedCalls,
        firstCompletionState = first.completionState,
        finalCompletedCalls = retry.completedCalls,
    )
}

private fun dispatch(
    result: CloudRestoreOnboardingCompletionResult,
): DispatchProbe {
    var completionState: OnboardingCompletionState =
        OnboardingCompletionState.Saving
    var accountResolutionState: OnboardingAccountResolutionState =
        OnboardingAccountResolutionState.Loading
    var completedCalls = 0
    dispatchCloudRestoreOnboardingCompletion(
        result = result,
        onCompletionStateChanged = { completionState = it },
        onAccountResolutionStateChanged = {
            accountResolutionState = it
        },
        onCompleted = { completedCalls += 1 },
    )
    return DispatchProbe(
        completedCalls = completedCalls,
        completionState = completionState,
        accountResolutionState = accountResolutionState,
    )
}

private class QueuedRecoveryOperation(
    vararg results: CloudRestorePostImportRecoveryResult,
) : CloudRestorePostImportRecoveryOperation {
    private val results = ArrayDeque(results.toList())
    var calls: Int = 0
        private set

    override suspend fun resumeIfNeeded():
        CloudRestorePostImportRecoveryResult {
        calls += 1
        return results.removeFirst()
    }
}

private data class CompletionProbe(
    val recoveryCalls: Int,
    val accountCompletionCalls: Int,
    val completedCalls: Int,
    val completionState: OnboardingCompletionState,
    val accountResolutionState: OnboardingAccountResolutionState,
)

private data class RetryProbe(
    val recoveryCalls: Int,
    val accountCompletionCalls: Int,
    val firstCompletedCalls: Int,
    val firstCompletionState: OnboardingCompletionState,
    val finalCompletedCalls: Int,
)

private data class DispatchProbe(
    val completedCalls: Int,
    val completionState: OnboardingCompletionState,
    val accountResolutionState: OnboardingAccountResolutionState,
)

    @Test
    fun onboardingSetupRequiredStartsOnboardingWithRestoredData() {
        assertEquals(
            CloudRestorePostImportStartupDecision
                .StartOnboardingWithRestoredData,
            CloudRestorePostImportRecoveryResult
                .RequiresOnboardingSetup
                .toStartupDecision(),
        )
    }

    @Test
    fun refreshPendingContinuesWithNonBlockingMessage() {
        assertEquals(
            CloudRestorePostImportStartupDecision.Continue(
                CloudRestorePostImportStartupMessage.RefreshPending,
            ),
            CloudRestorePostImportRecoveryResult
                .FinalizedRefreshPending
                .toStartupDecision(),
        )
    }

    @Test
    fun missingCloudCredentialsContinuesWithSetupPrompt() {
        assertEquals(
            CloudRestorePostImportStartupDecision.Continue(
                CloudRestorePostImportStartupMessage
                    .CloudRecoverySetupRequired,
            ),
            CloudRestorePostImportRecoveryResult
                .RequiresCloudRecoverySetup
                .toStartupDecision(),
        )
    }

    @Test
    fun differentAccountMapsToAccountMismatchBlock() {
        assertEquals(
            CloudRestorePostImportStartupDecision.AccountMismatch,
            CloudRestorePostImportRecoveryResult
                .RequiresCorrectAccount
                .toStartupDecision(),
        )
    }

    @Test
    fun pendingAndFailureMapToRetryWithoutErasingData() {
        assertEquals(
            CloudRestorePostImportStartupDecision.Retryable(null),
            CloudRestorePostImportRecoveryResult
                .FinalizationPending
                .toStartupDecision(),
        )

        val failure = IllegalStateException("temporary")
        assertEquals(
            CloudRestorePostImportStartupDecision.Retryable(failure),
            CloudRestorePostImportRecoveryResult.Failed(failure)
                .toStartupDecision(),
        )
    }

    @Test
    fun setupRequiredBypassesRemoteResolutionAndPreservesImportedData() =
        runBlocking {
            val operation =
                QueuedRecoveryOperation(
                    CloudRestorePostImportRecoveryResult
                        .RequiresOnboardingSetup,
                )
            val recovery = CloudRestoreOnboardingRecovery(operation)
            var accountResolutionCalls = 0
            var importedDataPresent = true

            val result =
                recovery.resolveAuthenticatedOnboarding {
                    accountResolutionCalls += 1
                    importedDataPresent = false
                    AuthenticatedOnboardingResolution
                        .RemoteCompletedWithoutLocalData
                }

            assertEquals(0, accountResolutionCalls)
            assertTrue(importedDataPresent)
            assertEquals(
                AuthenticatedOnboardingResolution.Incomplete,
                result.resolution,
            )
            assertTrue(
                result.resolution !=
                    AuthenticatedOnboardingResolution
                        .RemoteCompletedWithoutLocalData,
            )
        }

    @Test
    fun completingRestoredOnboardingResumesReceiptAgain() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult.Finalized,
                )

            assertEquals(2, probe.recoveryCalls)
            assertEquals(1, probe.accountCompletionCalls)
        }

    @Test
    fun finalizedInvokesCompletion() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult.Finalized,
                )

            assertEquals(1, probe.completedCalls)
            assertEquals(
                OnboardingCompletionState.Idle,
                probe.completionState,
            )
        }

    @Test
    fun terminalCleanResultsInvokeCompletion() =
        runBlocking {
            listOf(
                CloudRestorePostImportRecoveryResult.NothingPending,
                CloudRestorePostImportRecoveryResult
                    .AuthorizationWithoutCommittedImportCleared,
            ).forEach { finalization ->
                val probe = completeRestoredOnboarding(finalization)

                assertEquals(1, probe.completedCalls)
                assertEquals(
                    OnboardingAccountResolutionState.Idle,
                    probe.accountResolutionState,
                )
            }
        }

    @Test
    fun refreshPendingInvokesCompletionAndExposesMessageState() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult
                        .FinalizedRefreshPending,
                )

            assertEquals(1, probe.completedCalls)
            assertEquals(
                OnboardingAccountResolutionState
                    .CloudRestoreRefreshPending,
                probe.accountResolutionState,
            )
        }

    @Test
    fun cloudRecoverySetupRequiredInvokesCompletionAndExposesMessageState() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult
                        .RequiresCloudRecoverySetup,
                )

            assertEquals(1, probe.completedCalls)
            assertEquals(
                OnboardingAccountResolutionState
                    .CloudRecoverySetupRequired,
                probe.accountResolutionState,
            )
        }

    @Test
    fun correctAccountRequiredDoesNotInvokeCompletion() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult
                        .RequiresCorrectAccount,
                )

            assertEquals(0, probe.completedCalls)
            assertTrue(
                probe.completionState is
                    OnboardingCompletionState.RetryableFailure,
            )
        }

    @Test
    fun onboardingStillRequiredAfterCompletionDoesNotNavigateBack() =
        runBlocking {
            val probe =
                completeRestoredOnboarding(
                    CloudRestorePostImportRecoveryResult
                        .RequiresOnboardingSetup,
                )

            assertEquals(0, probe.completedCalls)
            assertTrue(
                probe.completionState is
                    OnboardingCompletionState.RetryableFailure,
            )
        }

    @Test
    fun finalizationPendingRemainsRetryableWithoutRepeatingAccountCompletion() =
        runBlocking {
            val retry =
                retryBlockedFinalization(
                    CloudRestorePostImportRecoveryResult
                        .FinalizationPending,
                )

            assertEquals(0, retry.firstCompletedCalls)
            assertTrue(
                retry.firstCompletionState is
                    OnboardingCompletionState.RetryableFailure,
            )
            assertEquals(1, retry.finalCompletedCalls)
            assertEquals(1, retry.accountCompletionCalls)
            assertEquals(3, retry.recoveryCalls)
        }

    @Test
    fun failedFinalizationRemainsRetryableWithoutRepeatingAccountCompletion() =
        runBlocking {
            val cause = IllegalStateException("temporary finalization failure")
            val retry =
                retryBlockedFinalization(
                    CloudRestorePostImportRecoveryResult.Failed(cause),
                )

            assertEquals(0, retry.firstCompletedCalls)
            assertTrue(
                retry.firstCompletionState is
                    OnboardingCompletionState.RetryableFailure,
            )
            assertEquals(1, retry.finalCompletedCalls)
            assertEquals(1, retry.accountCompletionCalls)
            assertEquals(3, retry.recoveryCalls)
        }

    @Test
    fun duplicateCompletionTapsRunOnlyOneFinalizationResume() =
        runBlocking {
            val finalizationEntered = CompletableDeferred<Unit>()
            val releaseFinalization = CompletableDeferred<Unit>()
            var recoveryCalls = 0
            var accountCompletionCalls = 0
            var cloudImportCalls = 0
            val recovery =
                CloudRestoreOnboardingRecovery(
                    CloudRestorePostImportRecoveryOperation {
                        recoveryCalls += 1
                        if (recoveryCalls == 1) {
                            CloudRestorePostImportRecoveryResult
                                .RequiresOnboardingSetup
                        } else {
                            finalizationEntered.complete(Unit)
                            releaseFinalization.await()
                            CloudRestorePostImportRecoveryResult.Finalized
                        }
                    },
                )
            recovery.resolveAuthenticatedOnboarding {
                AuthenticatedOnboardingResolution
                    .RemoteCompletedWithoutLocalData
            }
            val results =
                mutableListOf<CloudRestoreOnboardingCompletionResult>()

            val first = launch {
                results += recovery.completeOnboarding {
                    accountCompletionCalls += 1
                    CompleteOnboardingResult.Completed
                }
            }
            finalizationEntered.await()
            val duplicate = launch {
                results += recovery.completeOnboarding {
                    accountCompletionCalls += 1
                    CompleteOnboardingResult.Completed
                }
            }
            duplicate.join()
            releaseFinalization.complete(Unit)
            first.join()

            assertEquals(2, recoveryCalls)
            assertEquals(1, accountCompletionCalls)
            assertEquals(0, cloudImportCalls)
            assertEquals(
                1,
                results.count {
                    it is
                        CloudRestoreOnboardingCompletionResult.Completed
                },
            )
            assertEquals(
                1,
                results.count {
                    it ==
                        CloudRestoreOnboardingCompletionResult
                            .AlreadyRunning
                },
            )
        }

    @Test
    fun accountResolutionAndCompletionNeverResumeReceiptConcurrently() =
        runBlocking {
            val startupEntered = CompletableDeferred<Unit>()
            val releaseStartup = CompletableDeferred<Unit>()
            var recoveryCalls = 0
            var activeRecoveryCalls = 0
            var maximumConcurrentRecoveryCalls = 0
            val recovery =
                CloudRestoreOnboardingRecovery(
                    CloudRestorePostImportRecoveryOperation {
                        recoveryCalls += 1
                        activeRecoveryCalls += 1
                        maximumConcurrentRecoveryCalls =
                            maxOf(
                                maximumConcurrentRecoveryCalls,
                                activeRecoveryCalls,
                            )
                        try {
                            if (recoveryCalls == 1) {
                                startupEntered.complete(Unit)
                                releaseStartup.await()
                                CloudRestorePostImportRecoveryResult
                                    .RequiresOnboardingSetup
                            } else {
                                CloudRestorePostImportRecoveryResult.Finalized
                            }
                        } finally {
                            activeRecoveryCalls -= 1
                        }
                    },
                )
            val startup = launch {
                recovery.resolveAuthenticatedOnboarding {
                    error("Remote account resolution must be bypassed")
                }
            }
            startupEntered.await()
            val completion = launch {
                recovery.completeOnboarding {
                    CompleteOnboardingResult.Completed
                }
            }

            releaseStartup.complete(Unit)
            startup.join()
            completion.join()

            assertEquals(2, recoveryCalls)
            assertEquals(1, maximumConcurrentRecoveryCalls)
        }

    @Test
    fun finalizationCancellationIsRethrownAndUnlocksRetry() =
        runBlocking {
            var recoveryCalls = 0
            val recovery =
                CloudRestoreOnboardingRecovery(
                    CloudRestorePostImportRecoveryOperation {
                        recoveryCalls += 1
                        when (recoveryCalls) {
                            1 ->
                                CloudRestorePostImportRecoveryResult
                                    .RequiresOnboardingSetup
                            2 -> throw CancellationException("cancelled")
                            else ->
                                CloudRestorePostImportRecoveryResult.Finalized
                        }
                    },
                )
            recovery.resolveAuthenticatedOnboarding {
                error("Remote account resolution must be bypassed")
            }
            var cancellationRethrown = false

            try {
                recovery.completeOnboarding {
                    CompleteOnboardingResult.Completed
                }
            } catch (_: CancellationException) {
                cancellationRethrown = true
            }
            val retry =
                recovery.completeOnboarding {
                    error("Completed onboarding must not be repeated")
                }

            assertTrue(cancellationRethrown)
            assertTrue(
                retry is
                    CloudRestoreOnboardingCompletionResult.Completed,
            )
            assertEquals(3, recoveryCalls)
        }
}
