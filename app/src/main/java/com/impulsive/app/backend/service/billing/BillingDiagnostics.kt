package com.impulsive.app.backend.service.billing

import com.android.billingclient.api.BillingResult
import com.google.firebase.functions.FirebaseFunctionsException

internal fun billingResultFailureMessage(
    operation: String,
    result: BillingResult,
): String =
    "$operation failed with response code ${result.responseCode}: ${result.debugMessage}"

internal fun backendVerificationFailureMessage(throwable: Throwable): String {
    val exceptionName = throwable.javaClass.simpleName.ifBlank { "Throwable" }
    val functionsCode = (throwable as? FirebaseFunctionsException)?.code
    val safeMessage = throwable.message?.toSafeDiagnosticMessage()

    return buildString {
        append("Plus purchase backend verification failed (exception=")
        append(exceptionName)
        if (functionsCode != null) {
            append(", functionsCode=")
            append(functionsCode)
        }
        if (!safeMessage.isNullOrBlank()) {
            append(", message=")
            append(safeMessage)
        }
        append(").")
    }
}

private fun String.toSafeDiagnosticMessage(): String {
    val normalized = replace(Regex("[\\r\\n\\t]+"), " ").trim().take(MaxDiagnosticMessageLength)
    if (SensitiveLabelRegex.containsMatchIn(normalized)) {
        return RedactedValue
    }

    return TokenLikeValueRegex.replace(normalized, RedactedValue)
}

private const val MaxDiagnosticMessageLength = 300
private const val RedactedValue = "[redacted]"
private val SensitiveLabelRegex = Regex(
    pattern = "purchase[ _-]?token|id[ _-]?token|app[ _-]?check|api[ _-]?key|credential|authorization|bearer",
    option = RegexOption.IGNORE_CASE,
)
private val TokenLikeValueRegex = Regex("[A-Za-z0-9_\\-.]{32,}")
