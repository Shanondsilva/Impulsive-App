package com.impulsive.app.backend.domain.model.adaptive

enum class MomentPlanRehearsalMode {
    Guided,
    Quick,
}

data class MomentPlanRehearsal(
    val rehearsalId: String,
    val planId: String,
    val planUpdatedAtMillisAtStart: Long,
    val mode: MomentPlanRehearsalMode,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val dismissedAtMillis: Long? = null,
    val planContentRevisionId: String =
        com.impulsive.app.backend.domain.engine.adaptive.MomentPlanContentRevisionIds.Unspecified,
) {
    val isOpen: Boolean
        get() = completedAtMillis == null && dismissedAtMillis == null
}
