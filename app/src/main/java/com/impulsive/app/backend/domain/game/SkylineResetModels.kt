package com.impulsive.app.backend.domain.game

import kotlin.math.abs

const val SkylineResetTargetFloors = 10
const val SkylineResetMinimumFloors = 3
const val SkylineResetPerfectTolerance = 0.02f
const val SkylineResetPerfectGrow = 0.018f
const val SkylineResetBaseWidthFraction = 0.56f
const val SkylineResetStartSpeed = 1.7f
const val SkylineResetMaxSpeed = 4.4f
const val SkylineResetSpeedStep = 0.06f
const val SkylineResetRoundSeconds = 90
const val SkylineResetPerPerfectControlPoints = 5

data class SkylineFloor(
    val left: Float,
    val width: Float,
    val hue: Int,
)

enum class SkylineDropResult { Placed, Perfect, Missed }

data class SkylineDropOutcome(
    val result: SkylineDropResult,
    val placedFloor: SkylineFloor?,
    val trimLeft: Float,
    val trimWidth: Float,
)

fun skylineHueFor(index: Int): Int = (258 + index * 8) % 360

fun skylineSpeedFor(floorsBuilt: Int): Float =
    minOf(SkylineResetMaxSpeed, SkylineResetStartSpeed + floorsBuilt * SkylineResetSpeedStep)

fun maxSkylineWidth(fieldWidth: Float): Float = fieldWidth * SkylineResetBaseWidthFraction

fun newSkylineBaseFloor(fieldWidth: Float): SkylineFloor {
    val w = maxSkylineWidth(fieldWidth)
    return SkylineFloor(left = (fieldWidth - w) / 2f, width = w, hue = skylineHueFor(0))
}

fun resolveSkylineDrop(
    top: SkylineFloor,
    movingLeft: Float,
    movingWidth: Float,
    hue: Int,
    fieldWidth: Float,
): SkylineDropOutcome {
    val delta = movingLeft - top.left
    val overlap = top.width - abs(delta)
    if (overlap <= 0f) {
        return SkylineDropOutcome(
            result = SkylineDropResult.Missed,
            placedFloor = null,
            trimLeft = movingLeft,
            trimWidth = movingWidth,
        )
    }
    if (abs(delta) <= SkylineResetPerfectTolerance) {
        val newWidth = minOf(top.width + SkylineResetPerfectGrow, maxSkylineWidth(fieldWidth))
        val newLeft = (top.left - (newWidth - top.width) / 2f)
            .coerceIn(0f, (fieldWidth - newWidth).coerceAtLeast(0f))
        return SkylineDropOutcome(
            result = SkylineDropResult.Perfect,
            placedFloor = SkylineFloor(newLeft, newWidth, hue),
            trimLeft = 0f,
            trimWidth = 0f,
        )
    }
    val newLeft = maxOf(movingLeft, top.left)
    val newRight = minOf(movingLeft + movingWidth, top.left + top.width)
    val newWidth = newRight - newLeft
    val trimLeft: Float
    val trimWidth: Float
    if (movingLeft < top.left) {
        trimLeft = movingLeft
        trimWidth = top.left - movingLeft
    } else {
        trimLeft = top.left + top.width
        trimWidth = (movingLeft + movingWidth) - (top.left + top.width)
    }
    return SkylineDropOutcome(
        result = SkylineDropResult.Placed,
        placedFloor = SkylineFloor(newLeft, newWidth, hue),
        trimLeft = trimLeft,
        trimWidth = trimWidth,
    )
}
