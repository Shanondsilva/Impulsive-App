package com.impulsive.app.backend.data.remote.onboarding

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.impulsive.app.backend.service.firebase.AppCheckGatedCallResult
import com.impulsive.app.backend.service.firebase.runAfterAppCheckReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal sealed interface RemoteOnboardingCompletionResult {
    data object Completed : RemoteOnboardingCompletionResult
    data object Incomplete : RemoteOnboardingCompletionResult
    data object NotApplicable : RemoteOnboardingCompletionResult

    data class RetryableFailure(
        val cause: Throwable?,
    ) : RemoteOnboardingCompletionResult
}

internal sealed interface RemoteOnboardingMarkResult {
    data object Completed : RemoteOnboardingMarkResult
    data object NotApplicable : RemoteOnboardingMarkResult

    data class RetryableFailure(
        val cause: Throwable?,
    ) : RemoteOnboardingMarkResult
}

internal interface OnboardingRemoteAccountStateDataSource {
    suspend fun getCompletion(): RemoteOnboardingCompletionResult

    suspend fun markCompleted(): RemoteOnboardingMarkResult
}

internal class FirebaseOnboardingAccountStateDataSource(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance(FunctionsRegion),
) : OnboardingRemoteAccountStateDataSource {

    override suspend fun getCompletion(): RemoteOnboardingCompletionResult {
        val user = firebaseAuth.currentUser
            ?: return RemoteOnboardingCompletionResult.NotApplicable

        if (user.isAnonymous) {
            return RemoteOnboardingCompletionResult.NotApplicable
        }

        return try {
            user.getIdToken(false).await()

            val gatedResult = runAfterAppCheckReadiness {
                functions
                    .getHttpsCallable(GetOnboardingCompletionFunction)
                    .call()
                    .await()
                    .getData()
            }

            val data = when (gatedResult) {
                is AppCheckGatedCallResult.Executed -> gatedResult.value

                is AppCheckGatedCallResult.TemporarilyUnavailable ->
                    return RemoteOnboardingCompletionResult.RetryableFailure(
                        gatedResult.cause,
                    )
            }

            val payload = data as? Map<*, *>
                ?: return RemoteOnboardingCompletionResult.RetryableFailure(
                    IllegalStateException(
                        "The onboarding service returned an invalid response.",
                    ),
                )

            if (payload["onboardingCompleted"] == true) {
                RemoteOnboardingCompletionResult.Completed
            } else {
                RemoteOnboardingCompletionResult.Incomplete
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            RemoteOnboardingCompletionResult.RetryableFailure(throwable)
        }
    }

    override suspend fun markCompleted(): RemoteOnboardingMarkResult {
        val user = firebaseAuth.currentUser
            ?: return RemoteOnboardingMarkResult.NotApplicable

        if (user.isAnonymous) {
            return RemoteOnboardingMarkResult.NotApplicable
        }

        return try {
            user.getIdToken(false).await()

            val gatedResult = runAfterAppCheckReadiness {
                functions
                    .getHttpsCallable(MarkOnboardingCompletedFunction)
                    .call()
                    .await()
                    .getData()
            }

            when (gatedResult) {
                is AppCheckGatedCallResult.Executed -> {
                    val payload = gatedResult.value as? Map<*, *>
                        ?: return RemoteOnboardingMarkResult.RetryableFailure(
                            IllegalStateException(
                                "The onboarding service returned an invalid response.",
                            ),
                        )

                    if (payload["onboardingCompleted"] == true) {
                        RemoteOnboardingMarkResult.Completed
                    } else {
                        RemoteOnboardingMarkResult.RetryableFailure(
                            IllegalStateException(
                                "The onboarding service did not confirm completion.",
                            ),
                        )
                    }
                }

                is AppCheckGatedCallResult.TemporarilyUnavailable ->
                    RemoteOnboardingMarkResult.RetryableFailure(gatedResult.cause)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            RemoteOnboardingMarkResult.RetryableFailure(throwable)
        }
    }

    private companion object {
        const val FunctionsRegion = "us-central1"
        const val GetOnboardingCompletionFunction =
            "getOnboardingCompletion"
        const val MarkOnboardingCompletedFunction =
            "markOnboardingCompleted"
    }
}

