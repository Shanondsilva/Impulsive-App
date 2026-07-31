package com.impulsive.app.frontend.screens.progress

internal data class ResetReadingFlipFaceContent(
    val firstValue: String,
    val firstLabel: String,
    val secondValue: String,
    val secondLabel: String,
)

internal data class ResetReadingFlipContent(
    val front: ResetReadingFlipFaceContent,
    val back: ResetReadingFlipFaceContent,
)

internal fun buildResetReadingFlipContent(
    lastCompletedValue: String,
    helpfulValue: String,
    completedValue: String,
    abandonedValue: String,
): ResetReadingFlipContent =
    ResetReadingFlipContent(
        front = ResetReadingFlipFaceContent(
            firstValue = lastCompletedValue,
            firstLabel = "Last completed",
            secondValue = helpfulValue,
            secondLabel = "Helpful",
        ),
        back = ResetReadingFlipFaceContent(
            firstValue = completedValue,
            firstLabel = "Completed",
            secondValue = abandonedValue,
            secondLabel = "Abandoned",
        ),
    )
