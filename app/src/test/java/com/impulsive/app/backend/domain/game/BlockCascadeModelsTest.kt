package com.impulsive.app.backend.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockCascadeModelsTest {
    @Test
    fun canMoveDownAtFloorReturnsFalse() {
        val piece = spawnPiece(BlockPieceKind.O).copy(y = BlockCascadeRows - 2)

        assertFalse(canMoveDown(BlockCascadeBoard(), piece))
    }

    @Test
    fun hardDropSettlesOnTopOfStack() {
        val board = boardWithCells(
            4 to BlockCascadeRows - 1,
            5 to BlockCascadeRows - 1,
        )
        val piece = spawnPiece(BlockPieceKind.O).copy(x = 3)

        val dropped = hardDropPiece(board, piece)

        assertEquals(BlockCascadeRows - 3, dropped.y)
        assertFalse(canMoveDown(board, dropped))

        val lockedBoard = lockPiece(board, dropped)
        assertNotNull(lockedBoard.valueAt(4, BlockCascadeRows - 3))
        assertNotNull(lockedBoard.valueAt(5, BlockCascadeRows - 3))
        assertNotNull(lockedBoard.valueAt(4, BlockCascadeRows - 2))
        assertNotNull(lockedBoard.valueAt(5, BlockCascadeRows - 2))
    }

    @Test
    fun clearFullLinesRemovesSingleFilledRow() {
        val board = boardWithFullRow(BlockCascadeRows - 1)

        val (clearedBoard, clearedRows) = clearFullLines(board)

        assertEquals(listOf(BlockCascadeRows - 1), clearedRows)
        for (x in 0 until BlockCascadeColumns) {
            assertEquals(null, clearedBoard.valueAt(x, BlockCascadeRows - 1))
        }
    }

    @Test
    fun clearFullLinesRemovesMultipleFilledRowsAndDropsAboveRows() {
        val board = boardWithCells(
            2 to 10,
            0 to 12,
            1 to 12,
            2 to 12,
            3 to 12,
            4 to 12,
            5 to 12,
            6 to 12,
            7 to 12,
            0 to 13,
            1 to 13,
            2 to 13,
            3 to 13,
            4 to 13,
            5 to 13,
            6 to 13,
            7 to 13,
        )

        val (clearedBoard, clearedRows) = clearFullLines(board)

        assertEquals(listOf(12, 13), clearedRows)
        assertEquals(1, clearedBoard.valueAt(2, 12))
        for (x in 0 until BlockCascadeColumns) {
            assertEquals(null, clearedBoard.valueAt(x, 13))
        }
    }

    @Test
    fun pieceKeepsMovingWhileSpaceBelowIsReachable() {
        val board = BlockCascadeBoard()
        val startPiece = spawnPiece(BlockPieceKind.I)

        assertTrue(canMoveDown(board, startPiece))

        val restingPiece = hardDropPiece(board, startPiece)

        assertFalse(canMoveDown(board, restingPiece))
        assertTrue(canMoveDown(board, restingPiece.copy(y = restingPiece.y - 1)))
    }

    private fun boardWithFullRow(y: Int): BlockCascadeBoard =
        boardWithCells(*Array(BlockCascadeColumns) { x -> x to y })

    private fun boardWithCells(vararg coords: Pair<Int, Int>): BlockCascadeBoard {
        val cells = MutableList(BlockCascadeColumns * BlockCascadeRows) { null as Int? }
        coords.forEach { (x, y) ->
            cells[y * BlockCascadeColumns + x] = 1
        }
        return BlockCascadeBoard(cells)
    }
}
