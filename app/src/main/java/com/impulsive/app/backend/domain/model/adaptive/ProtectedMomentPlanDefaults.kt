package com.impulsive.app.backend.domain.model.adaptive

/**
 * Built-in fallback content for the protected Moment's plan step.
 *
 * This is used only when the user has no enabled saved Moment Plan. It is a
 * constant, not a persisted plan: nothing is written into the user's plan
 * database on their behalf, so an empty plan list stays genuinely empty.
 *
 * Defined once here so the wording cannot drift between callers.
 */
object ProtectedMomentPlanDefaults {
    const val DefaultActionText: String =
        "Put your phone down and move it out of reach. " +
            "Do something else for a few minutes."
}
