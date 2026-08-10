package com.impulsive.app.backend.session.progress

import androidx.work.Operation
import androidx.work.await
import kotlinx.coroutines.CancellationException

internal fun interface SafeExitWorkEnqueueReceipt {
    suspend fun awaitAccepted():
        Boolean
}

internal fun Operation.toSafeExitWorkEnqueueReceipt():
    SafeExitWorkEnqueueReceipt {
    return SafeExitWorkEnqueueReceipt {
        try {
            await()
            true
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            _: Exception,
        ) {
            false
        }
    }
}