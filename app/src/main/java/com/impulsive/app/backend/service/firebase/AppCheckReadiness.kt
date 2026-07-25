package com.impulsive.app.backend.service.firebase

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal sealed interface AppCheckReadinessResult {
    data object Ready : AppCheckReadinessResult

    data class TemporarilyUnavailable(
        val cause: Throwable?,
    ) : AppCheckReadinessResult
}

internal suspend fun awaitAppCheckReadiness(): AppCheckReadinessResult {
    return try {
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .await()

        AppCheckReadinessResult.Ready
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        AppCheckReadinessResult.TemporarilyUnavailable(exception)
    }
}

internal sealed interface AppCheckGatedCallResult<out T> {
    data class Executed<T>(val value: T) : AppCheckGatedCallResult<T>

    data class TemporarilyUnavailable(
        val cause: Throwable?,
    ) : AppCheckGatedCallResult<Nothing>
}

/**
 * Runs [call] only after App Check can supply a token to Firebase SDK integrations.
 * The token value is deliberately neither returned nor passed to [call].
 */
internal suspend fun <T> runAfterAppCheckReadiness(
    readinessProvider: suspend () -> AppCheckReadinessResult = ::awaitAppCheckReadiness,
    call: suspend () -> T,
): AppCheckGatedCallResult<T> {
    return when (val readiness = readinessProvider()) {
        AppCheckReadinessResult.Ready -> AppCheckGatedCallResult.Executed(call())
        is AppCheckReadinessResult.TemporarilyUnavailable ->
            AppCheckGatedCallResult.TemporarilyUnavailable(readiness.cause)
    }
}

internal fun appCheckReadinessFailureLogMessage(cause: Throwable?): String {
    val exceptionName = cause?.javaClass?.simpleName ?: "unknown"
    return "App Check token unavailable before server entitlement refresh; " +
        "retry may follow (exception=$exceptionName)."
}
