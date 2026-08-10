package com.impulsive.app.frontend.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.domain.game.SnakeCell
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGameConfig
import com.impulsive.app.backend.domain.game.SnakeGameState
import com.impulsive.app.backend.domain.game.SnakeGameView

/*
 * Playfield palette. These are Impulsive's own values, drawn entirely with
 * Canvas primitives — no external, downloaded or third-party assets.
 */
internal val SnakeBoardLight = Color(0xFFAAD751)
internal val SnakeBoardDark = Color(0xFFA2D149)
internal val SnakeBoardEdge = Color(0xFF578A34)
internal val SnakeBody = Color(0xFF4E7CF6)
internal val SnakeEye = Color(0xFF1C274C)
internal val SnakeEyeWhite = Color.White
internal val SnakeFruit = Color(0xFFE7471D)
internal val SnakeFruitHighlight = Color(0xFFF56642)
internal val SnakeLeaf = Color(0xFF5E8E2E)

private const val SnakeBoardColumns = SnakeGameConfig.DEFAULT_COLUMNS
private const val SnakeBoardRows = SnakeGameConfig.DEFAULT_ROWS
private const val SnakeBoardAspectRatio =
    SnakeBoardColumns.toFloat() / SnakeBoardRows.toFloat()

/**
 * The Snake playfield.
 *
 * The board always keeps the engine's 18:24 ratio and is measured only from the
 * space its parent offers, so growing the snake, eating fruit, a changing score
 * or an overlay appearing can never resize it.
 */
@Composable
internal fun SnakeGameBoard(
    state: SnakeGameState,
    view: SnakeGameView,
    modifier: Modifier = Modifier,
    onDirection: (SnakeDirection) -> Unit,
    onResume: () -> Unit,
) {
    val acceptsInput = view == SnakeGameView.Ready || view == SnakeGameView.Playing

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Largest board that fits while preserving the grid ratio.
        val boardWidth = minOf(maxWidth, maxHeight * SnakeBoardAspectRatio)
        val boardHeight = boardWidth / SnakeBoardAspectRatio

        Box(
            modifier = Modifier
                .size(width = boardWidth, height = boardHeight)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    border = BorderStroke(2.dp, SnakeBoardEdge),
                    shape = RoundedCornerShape(20.dp),
                ),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Snake game board"
                        stateDescription = when (view) {
                            SnakeGameView.Ready -> "Ready"
                            SnakeGameView.Playing -> "Playing"
                            SnakeGameView.Paused -> "Paused"
                            SnakeGameView.Result -> "Round complete"
                            SnakeGameView.Walked -> "Walked away"
                        }
                        /*
                         * The board is one logical control: TalkBack, Switch
                         * Access and Voice Access steer it through these four
                         * actions rather than 432 per-cell nodes.
                         */
                        customActions = listOf(
                            CustomAccessibilityAction("Move up") {
                                if (acceptsInput) {
                                    onDirection(SnakeDirection.Up)
                                    true
                                } else {
                                    false
                                }
                            },
                            CustomAccessibilityAction("Move down") {
                                if (acceptsInput) {
                                    onDirection(SnakeDirection.Down)
                                    true
                                } else {
                                    false
                                }
                            },
                            CustomAccessibilityAction("Move left") {
                                if (acceptsInput) {
                                    onDirection(SnakeDirection.Left)
                                    true
                                } else {
                                    false
                                }
                            },
                            CustomAccessibilityAction("Move right") {
                                if (acceptsInput) {
                                    onDirection(SnakeDirection.Right)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                    }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (!acceptsInput || event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }

                        val direction = when (event.key) {
                            Key.DirectionUp -> SnakeDirection.Up
                            Key.DirectionDown -> SnakeDirection.Down
                            Key.DirectionLeft -> SnakeDirection.Left
                            Key.DirectionRight -> SnakeDirection.Right
                            else -> null
                        } ?: return@onPreviewKeyEvent false

                        onDirection(direction)
                        true
                    }
                    .pointerInput(acceptsInput) {
                        if (!acceptsInput) return@pointerInput

                        // One physical swipe emits at most one direction.
                        var directionSent = false

                        detectDragGestures(
                            onDragStart = { directionSent = false },
                            onDragEnd = { directionSent = false },
                            onDragCancel = { directionSent = false },
                        ) { change, dragAmount ->
                            change.consume()

                            if (directionSent) return@detectDragGestures

                            val direction = snakeDirectionFromDrag(
                                deltaX = dragAmount.x,
                                deltaY = dragAmount.y,
                            ) ?: return@detectDragGestures

                            directionSent = true
                            onDirection(direction)
                        }
                    },
            ) {
                val cellWidth = size.width / SnakeBoardColumns
                val cellHeight = size.height / SnakeBoardRows

                drawCheckerboard(cellWidth, cellHeight)
                state.food?.let { drawFruit(it, cellWidth, cellHeight) }
                drawSnake(state.snake, state.direction, cellWidth, cellHeight)
            }

            if (view == SnakeGameView.Ready) {
                SnakeBoardOverlay {
                    Text(
                        text = "Swipe to start",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Walls wrap around",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (view == SnakeGameView.Paused) {
                SnakeBoardOverlay {
                    Text(
                        text = "Paused",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = SnakeBoardEdge,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Resume")
                    }
                }
            }
        }
    }
}

/** A scrim inside the measured board, so overlays never change its size. */
@Composable
private fun SnakeBoardOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99202B12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            content()
        }
    }
}

private fun DrawScope.drawCheckerboard(
    cellWidth: Float,
    cellHeight: Float,
) {
    for (y in 0 until SnakeBoardRows) {
        for (x in 0 until SnakeBoardColumns) {
            /*
             * Compute each edge from the cell index rather than accumulating
             * widths, so neighbouring cells meet without rounding seams.
             */
            val left = x * cellWidth
            val top = y * cellHeight
            val right = (x + 1) * cellWidth
            val bottom = (y + 1) * cellHeight

            drawRect(
                color = if ((x + y) % 2 == 0) SnakeBoardLight else SnakeBoardDark,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
            )
        }
    }
}

private fun DrawScope.drawFruit(
    food: SnakeCell,
    cellWidth: Float,
    cellHeight: Float,
) {
    /*
     * Every part is placed from the cell's own top-left, so nothing can reach
     * above its cell — a fruit in row 0 stays fully inside the board.
     */
    val cellLeft = food.x * cellWidth
    val cellTop = food.y * cellHeight
    val unit = minOf(cellWidth, cellHeight)

    val centerX = cellLeft + cellWidth * 0.50f
    val centerY = cellTop + cellHeight * 0.58f
    val radius = unit * 0.30f

    // Stem and leaf first so the body overlaps their base cleanly.
    drawLine(
        color = SnakeLeaf,
        start = Offset(centerX, cellTop + cellHeight * 0.34f),
        end = Offset(centerX, cellTop + cellHeight * 0.17f),
        strokeWidth = unit * 0.07f,
    )
    drawOval(
        color = SnakeLeaf,
        topLeft = Offset(centerX + unit * 0.02f, cellTop + cellHeight * 0.12f),
        size = Size(unit * 0.24f, unit * 0.13f),
    )

    drawCircle(
        color = SnakeFruit,
        radius = radius,
        center = Offset(centerX, centerY),
    )
    drawCircle(
        color = SnakeFruitHighlight,
        radius = radius * 0.24f,
        center = Offset(centerX - radius * 0.30f, centerY - radius * 0.30f),
    )
}

/**
 * Draws one continuous snake: connectors between neighbouring cells, then a
 * body node at each cell centre so straights, elbows and the tail all merge
 * without visible gaps.
 */
private fun DrawScope.drawSnake(
    snake: List<SnakeCell>,
    direction: SnakeDirection?,
    cellWidth: Float,
    cellHeight: Float,
) {
    if (snake.isEmpty()) return

    val unit = minOf(cellWidth, cellHeight)
    val bodyThickness = unit * 0.78f
    val bodyRadius = bodyThickness / 2f

    snake.zipWithNext().forEach { (first, second) ->
        drawSnakeConnector(first, second, cellWidth, cellHeight, bodyThickness)
    }

    // Tail towards head, so the head shape lands on top.
    snake.asReversed().forEach { cell ->
        drawCircle(
            color = SnakeBody,
            radius = bodyRadius,
            center = cellCenter(cell, cellWidth, cellHeight),
        )
    }

    val head = snake.first()
    val headSize = unit * 0.88f
    val headCenter = cellCenter(head, cellWidth, cellHeight)

    drawRoundRect(
        color = SnakeBody,
        topLeft = Offset(headCenter.x - headSize / 2f, headCenter.y - headSize / 2f),
        size = Size(headSize, headSize),
        cornerRadius = CornerRadius(unit * 0.28f, unit * 0.28f),
    )

    drawSnakeEyes(head, direction ?: SnakeDirection.Right, cellWidth, cellHeight)
}

private fun cellCenter(
    cell: SnakeCell,
    cellWidth: Float,
    cellHeight: Float,
): Offset = Offset(
    x = (cell.x + 0.5f) * cellWidth,
    y = (cell.y + 0.5f) * cellHeight,
)

/**
 * Joins two consecutive cells.
 *
 * A wrapped pair is drawn as two short stubs running off opposite edges — never
 * as one line spanning the board — so the snake reads as leaving one wall and
 * re-entering the other.
 */
private fun DrawScope.drawSnakeConnector(
    first: SnakeCell,
    second: SnakeCell,
    cellWidth: Float,
    cellHeight: Float,
    bodyThickness: Float,
) {
    val firstCenter = cellCenter(first, cellWidth, cellHeight)
    val secondCenter = cellCenter(second, cellWidth, cellHeight)
    val half = bodyThickness / 2f

    when (snakeCellConnection(first, second, SnakeBoardColumns, SnakeBoardRows)) {
        SnakeCellConnection.Horizontal -> {
            val left = minOf(firstCenter.x, secondCenter.x)
            val right = maxOf(firstCenter.x, secondCenter.x)

            drawRect(
                color = SnakeBody,
                topLeft = Offset(left, firstCenter.y - half),
                size = Size(right - left, bodyThickness),
            )
        }

        SnakeCellConnection.Vertical -> {
            val top = minOf(firstCenter.y, secondCenter.y)
            val bottom = maxOf(firstCenter.y, secondCenter.y)

            drawRect(
                color = SnakeBody,
                topLeft = Offset(firstCenter.x - half, top),
                size = Size(bodyThickness, bottom - top),
            )
        }

        SnakeCellConnection.WrappedHorizontal -> {
            val leftCenter = if (first.x == 0) firstCenter else secondCenter
            val rightCenter = if (first.x == 0) secondCenter else firstCenter

            // Stub running off the left edge.
            drawRect(
                color = SnakeBody,
                topLeft = Offset(0f, leftCenter.y - half),
                size = Size(leftCenter.x, bodyThickness),
            )
            // Stub running off the right edge.
            drawRect(
                color = SnakeBody,
                topLeft = Offset(rightCenter.x, rightCenter.y - half),
                size = Size(size.width - rightCenter.x, bodyThickness),
            )
        }

        SnakeCellConnection.WrappedVertical -> {
            val topCenter = if (first.y == 0) firstCenter else secondCenter
            val bottomCenter = if (first.y == 0) secondCenter else firstCenter

            drawRect(
                color = SnakeBody,
                topLeft = Offset(topCenter.x - half, 0f),
                size = Size(bodyThickness, topCenter.y),
            )
            drawRect(
                color = SnakeBody,
                topLeft = Offset(bottomCenter.x - half, bottomCenter.y),
                size = Size(bodyThickness, size.height - bottomCenter.y),
            )
        }

        // Malformed geometry draws nothing rather than a destructive line.
        SnakeCellConnection.Disconnected -> Unit
    }
}

private fun DrawScope.drawSnakeEyes(
    head: SnakeCell,
    direction: SnakeDirection,
    cellWidth: Float,
    cellHeight: Float,
) {
    val centerX = (head.x + 0.5f) * cellWidth
    val centerY = (head.y + 0.5f) * cellHeight
    val eyeRadius = minOf(cellWidth, cellHeight) * 0.15f
    val pupilRadius = eyeRadius * 0.5f

    // Offsets stay inside the head cell while indicating travel direction.
    val forward = minOf(cellWidth, cellHeight) * 0.16f
    val spread = minOf(cellWidth, cellHeight) * 0.2f

    val (firstEye, secondEye) = when (direction) {
        SnakeDirection.Right -> Offset(centerX + forward, centerY - spread) to
            Offset(centerX + forward, centerY + spread)

        SnakeDirection.Left -> Offset(centerX - forward, centerY - spread) to
            Offset(centerX - forward, centerY + spread)

        SnakeDirection.Up -> Offset(centerX - spread, centerY - forward) to
            Offset(centerX + spread, centerY - forward)

        SnakeDirection.Down -> Offset(centerX - spread, centerY + forward) to
            Offset(centerX + spread, centerY + forward)
    }

    listOf(firstEye, secondEye).forEach { eye ->
        drawCircle(color = SnakeEyeWhite, radius = eyeRadius, center = eye)
        drawCircle(color = SnakeEye, radius = pupilRadius, center = eye)
    }
}
