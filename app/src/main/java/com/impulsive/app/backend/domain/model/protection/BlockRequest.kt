package com.impulsive.app.backend.domain.model.protection

enum class BlockLaunchTarget {
    BlockScreen,

    /**
     * Manual or historical Adaptive Moment. Retained for compatibility; a
     * protected app/site interruption no longer uses it.
     */
    AdaptiveMoment,

    /**
     * A protected app/site interruption that automatically enters the
     * authoritative game-only Support Cycle.
     *
     * This is deliberately distinct from [AdaptiveMoment]: it never presents a
     * questionnaire or an intervention choice. It is also not a standalone
     * game, not Focus recovery and not a generic recovery task.
     */
    ProtectedMoment,

    RandomRecoveryGame,
    ReadingReset,
    FocusRecovery,
}

data class BlockRequest(
    val sourcePackageName: String,
    val sourceLabel: String,
    val detectedAtMillis: Long,
    val launchTarget: BlockLaunchTarget = BlockLaunchTarget.BlockScreen,
    val adaptiveDecisionId: String? = null,
) {
    companion object {
        const val ExtraSourcePackage = "impulsive.extra.BLOCK_SOURCE_PACKAGE"
        const val ExtraSourceLabel = "impulsive.extra.BLOCK_SOURCE_LABEL"
        const val ExtraDetectedAtMillis = "impulsive.extra.BLOCK_DETECTED_AT_MILLIS"
        const val ExtraLaunchTarget = "impulsive.extra.BLOCK_LAUNCH_TARGET"
        const val ExtraAdaptiveDecisionId = "impulsive.extra.ADAPTIVE_DECISION_ID"
    }
}
