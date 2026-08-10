package com.impulsive.app.backend.data.restore

internal object RestorePayloadSizePolicy {
    const val MaximumPayloadBytes =
        8 * 1024 * 1024

    const val OversizedPayloadMessage =
        "Restore payload exceeds maximum allowed size."

    fun requireWithinLimit(
        payloadJson: String,
    ): String {
        val payloadByteCount =
            payloadJson
                .toByteArray(
                    Charsets.UTF_8,
                )
                .size

        require(
            payloadByteCount <=
                MaximumPayloadBytes,
        ) {
            OversizedPayloadMessage
        }

        return payloadJson
    }
}