package com.impulsive.app.frontend.pathshift

import com.impulsive.app.core.util.TimeOfDay

enum class PathShiftCharacterState {
    LookingAhead,
    PathPrepared,
    WalkingCurrentPath,
    ReviewingPath,
    NotEnoughHistory,
}

enum class PathShiftExperienceState {
    InsufficientHistory,
    ForecastReady,
    Active,
    AwaitingReview,
    FinalisedReview,
    Unavailable,
}

data class PathShiftCharacterPresentation(
    val level: Int,
    val currentLevelPoints: Int,
    val state: PathShiftCharacterState,
    val timeOfDay: TimeOfDay,
    val reducedMotion: Boolean,
    val contentDescription: String,
) {
    companion object {
        fun create(
            currentLevel: Int,
            currentLevelPoints: Int,
            experienceState: PathShiftExperienceState,
            hasPreparedPlan: Boolean,
            timeOfDay: TimeOfDay,
            reducedMotion: Boolean,
        ): PathShiftCharacterPresentation {
            val state = when {
                experienceState == PathShiftExperienceState.InsufficientHistory ->
                    PathShiftCharacterState.NotEnoughHistory
                experienceState == PathShiftExperienceState.FinalisedReview ||
                    experienceState == PathShiftExperienceState.AwaitingReview ->
                    PathShiftCharacterState.ReviewingPath
                hasPreparedPlan -> PathShiftCharacterState.PathPrepared
                experienceState == PathShiftExperienceState.Active ->
                    PathShiftCharacterState.WalkingCurrentPath
                else -> PathShiftCharacterState.LookingAhead
            }
            return PathShiftCharacterPresentation(
                level = currentLevel.coerceAtLeast(1),
                currentLevelPoints = currentLevelPoints.coerceAtLeast(0),
                state = state,
                timeOfDay = timeOfDay,
                reducedMotion = reducedMotion,
                contentDescription =
                    "Your level $currentLevel character continuing along the current path. " +
                        "The scene reflects participation, not health status or forecast severity.",
            )
        }
    }
}
