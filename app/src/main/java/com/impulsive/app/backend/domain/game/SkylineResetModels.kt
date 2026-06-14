package com.impulsive.app.backend.domain.game

import kotlin.math.abs
import kotlin.math.sin

const val StackRoundSeconds = 90
const val StackPerPerfectControlPoints = 5

const val StackBaseWidth = 2f
const val StackBaseDepth = 2f
const val StackBlockHeight = 0.42f

const val StackMoveBound = 2.7f
const val StackPerfectTolerance = 0.08f
const val StackPerfectGrow = 0.12f

const val StackStartSpeed = 1.35f
const val StackSpeedPerFourFloors = 0.18f
const val StackSpeedPerPerfect = 0.025f
const val StackPerfectSpeedBonusCap = 0.30f
const val StackMaxSpeed = 2.75f

data class StackBlock(
    val index: Int,
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val hue: Int,
)

enum class StackDropResult { Placed, Perfect, Missed }

data class StackDropOutcome(
    val result: StackDropResult,
    val placed: StackBlock?,
    val axisIsX: Boolean,
    val choppedPresent: Boolean,
    val choppedX: Float,
    val choppedZ: Float,
    val choppedWidth: Float,
    val choppedDepth: Float,
    val choppedDir: Int,
)

fun stackAxisIsX(index: Int): Boolean = index % 2 == 1

fun stackHueFor(index: Int): Int {
    val drift = (sin(index * 0.18f) * 36f).toInt()
    return (246 + drift).coerceIn(210, 282)
}

fun stackSpeedFor(floorsBuilt: Int, perfectCount: Int = 0): Float {
    val floorTier = floorsBuilt.coerceAtLeast(0) / 4
    val floorSpeed = floorTier * StackSpeedPerFourFloors
    val perfectSpeed = minOf(
        StackPerfectSpeedBonusCap,
        perfectCount.coerceAtLeast(0) * StackSpeedPerPerfect,
    )
    return minOf(StackMaxSpeed, StackStartSpeed + floorSpeed + perfectSpeed)
}

fun newStackBaseBlock(): StackBlock =
    StackBlock(
        index = 0,
        x = -StackBaseWidth / 2f,
        z = -StackBaseDepth / 2f,
        width = StackBaseWidth,
        depth = StackBaseDepth,
        hue = stackHueFor(0),
    )

/**
 * Resolves a drop of the active block onto the target block. Movement happens on one axis at a
 * time, x for odd indices and z for even indices. Overlap is measured on the working axis, the
 * other axis is inherited from the target. A near aligned drop counts as Perfect and keeps full
 * width. Any positive overlap places a trimmed block and returns the chopped piece. No overlap is
 * a Missed drop.
 */
fun resolveStackDrop(
    target: StackBlock,
    activeX: Float,
    activeZ: Float,
    hue: Int,
): StackDropOutcome {
    val newIndex = target.index + 1
    val axisIsX = stackAxisIsX(newIndex)

    val targetPos = if (axisIsX) target.x else target.z
    val activePos = if (axisIsX) activeX else activeZ
    val targetDim = if (axisIsX) target.width else target.depth

    val delta = activePos - targetPos
    val overlap = targetDim - abs(delta)
    val chopped = abs(delta)

    if (overlap <= 0f) {
        return StackDropOutcome(
            result = StackDropResult.Missed,
            placed = null,
            axisIsX = axisIsX,
            choppedPresent = false,
            choppedX = 0f,
            choppedZ = 0f,
            choppedWidth = 0f,
            choppedDepth = 0f,
            choppedDir = 0,
        )
    }

    if (chopped < StackPerfectTolerance) {
        val grownWidth: Float
        val grownDepth: Float
        val grownX: Float
        val grownZ: Float
        if (axisIsX) {
            grownWidth = minOf(target.width + StackPerfectGrow, StackBaseWidth)
            grownX = target.x - (grownWidth - target.width) / 2f
            grownDepth = target.depth
            grownZ = target.z
        } else {
            grownDepth = minOf(target.depth + StackPerfectGrow, StackBaseDepth)
            grownZ = target.z - (grownDepth - target.depth) / 2f
            grownWidth = target.width
            grownX = target.x
        }
        return StackDropOutcome(
            result = StackDropResult.Perfect,
            placed = StackBlock(
                index = newIndex,
                x = grownX,
                z = grownZ,
                width = grownWidth,
                depth = grownDepth,
                hue = hue,
            ),
            axisIsX = axisIsX,
            choppedPresent = false,
            choppedX = 0f,
            choppedZ = 0f,
            choppedWidth = 0f,
            choppedDepth = 0f,
            choppedDir = 0,
        )
    }

    val placedPos: Float
    val choppedPos: Float
    val choppedDir: Int
    if (activePos < targetPos) {
        placedPos = targetPos
        choppedPos = activePos
        choppedDir = -1
    } else {
        placedPos = activePos
        choppedPos = targetPos + targetDim
        choppedDir = 1
    }

    val placed: StackBlock
    val choppedX: Float
    val choppedZ: Float
    val choppedWidth: Float
    val choppedDepth: Float
    if (axisIsX) {
        placed = StackBlock(newIndex, placedPos, target.z, overlap, target.depth, hue)
        choppedX = choppedPos
        choppedZ = target.z
        choppedWidth = chopped
        choppedDepth = target.depth
    } else {
        placed = StackBlock(newIndex, target.x, placedPos, target.width, overlap, hue)
        choppedX = target.x
        choppedZ = choppedPos
        choppedWidth = target.width
        choppedDepth = chopped
    }

    return StackDropOutcome(
        result = StackDropResult.Placed,
        placed = placed,
        axisIsX = axisIsX,
        choppedPresent = true,
        choppedX = choppedX,
        choppedZ = choppedZ,
        choppedWidth = choppedWidth,
        choppedDepth = choppedDepth,
        choppedDir = choppedDir,
    )
}
