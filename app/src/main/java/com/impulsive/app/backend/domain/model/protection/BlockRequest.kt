package com.impulsive.app.backend.domain.model.protection

enum class BlockLaunchTarget {
    BlockScreen,
    RandomRecoveryGame,
    ReadingReset,
    FocusRecovery,
}

data class BlockRequest(
    val sourcePackageName: String,
    val sourceLabel: String,
    val detectedAtMillis: Long,
    val launchTarget: BlockLaunchTarget = BlockLaunchTarget.BlockScreen,
) {
    companion object {
        const val ExtraSourcePackage = "impulsive.extra.BLOCK_SOURCE_PACKAGE"
        const val ExtraSourceLabel = "impulsive.extra.BLOCK_SOURCE_LABEL"
        const val ExtraDetectedAtMillis = "impulsive.extra.BLOCK_DETECTED_AT_MILLIS"
        const val ExtraLaunchTarget = "impulsive.extra.BLOCK_LAUNCH_TARGET"
    }
}
