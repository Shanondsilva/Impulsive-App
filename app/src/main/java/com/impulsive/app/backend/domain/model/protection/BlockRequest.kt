package com.impulsive.app.backend.domain.model.protection

data class BlockRequest(
    val sourcePackageName: String,
    val sourceLabel: String,
    val detectedAtMillis: Long,
) {
    companion object {
        const val ExtraSourcePackage = "impulsive.extra.BLOCK_SOURCE_PACKAGE"
        const val ExtraSourceLabel = "impulsive.extra.BLOCK_SOURCE_LABEL"
        const val ExtraDetectedAtMillis = "impulsive.extra.BLOCK_DETECTED_AT_MILLIS"
    }
}
