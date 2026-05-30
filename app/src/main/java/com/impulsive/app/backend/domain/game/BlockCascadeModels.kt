package com.impulsive.app.backend.domain.game

import kotlin.random.Random

const val BlockCascadeColumns = 8
const val BlockCascadeRows = 14
const val BlockCascadeRoundSeconds = 90
const val BlockCascadeMinimumSeconds = 60
const val BlockCascadeMinimumLines = 2
const val BlockCascadeMinimumMoves = 20

data class BlockCell(
    val x: Int,
    val y: Int,
)

enum class BlockPieceKind(
    val paletteIndex: Int,
    val rotations: List<List<BlockCell>>,
) {
    I(
        paletteIndex = 0,
        rotations = listOf(
            listOf(BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1), BlockCell(3, 1)),
            listOf(BlockCell(2, 0), BlockCell(2, 1), BlockCell(2, 2), BlockCell(2, 3)),
        ),
    ),
    O(
        paletteIndex = 1,
        rotations = listOf(
            listOf(BlockCell(1, 0), BlockCell(2, 0), BlockCell(1, 1), BlockCell(2, 1)),
        ),
    ),
    T(
        paletteIndex = 2,
        rotations = listOf(
            listOf(BlockCell(1, 0), BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1)),
            listOf(BlockCell(1, 0), BlockCell(1, 1), BlockCell(2, 1), BlockCell(1, 2)),
            listOf(BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1), BlockCell(1, 2)),
            listOf(BlockCell(1, 0), BlockCell(0, 1), BlockCell(1, 1), BlockCell(1, 2)),
        ),
    ),
    L(
        paletteIndex = 3,
        rotations = listOf(
            listOf(BlockCell(0, 0), BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1)),
            listOf(BlockCell(1, 0), BlockCell(2, 0), BlockCell(1, 1), BlockCell(1, 2)),
            listOf(BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1), BlockCell(2, 2)),
            listOf(BlockCell(1, 0), BlockCell(1, 1), BlockCell(0, 2), BlockCell(1, 2)),
        ),
    ),
    J(
        paletteIndex = 4,
        rotations = listOf(
            listOf(BlockCell(2, 0), BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1)),
            listOf(BlockCell(1, 0), BlockCell(1, 1), BlockCell(1, 2), BlockCell(2, 2)),
            listOf(BlockCell(0, 1), BlockCell(1, 1), BlockCell(2, 1), BlockCell(0, 2)),
            listOf(BlockCell(0, 0), BlockCell(1, 0), BlockCell(1, 1), BlockCell(1, 2)),
        ),
    ),
    S(
        paletteIndex = 5,
        rotations = listOf(
            listOf(BlockCell(1, 0), BlockCell(2, 0), BlockCell(0, 1), BlockCell(1, 1)),
            listOf(BlockCell(1, 0), BlockCell(1, 1), BlockCell(2, 1), BlockCell(2, 2)),
        ),
    ),
    Z(
        paletteIndex = 6,
        rotations = listOf(
            listOf(BlockCell(0, 0), BlockCell(1, 0), BlockCell(1, 1), BlockCell(2, 1)),
            listOf(BlockCell(2, 0), BlockCell(1, 1), BlockCell(2, 1), BlockCell(1, 2)),
        ),
    ),
}

data class FallingPiece(
    val kind: BlockPieceKind,
    val x: Int,
    val y: Int,
    val rotation: Int,
) {
    val cells: List<BlockCell>
        get() = kind.rotations[rotation % kind.rotations.size].map { cell ->
            BlockCell(x + cell.x, y + cell.y)
        }
}

data class BlockCascadeBoard(
    val cells: List<Int?> = List(BlockCascadeColumns * BlockCascadeRows) { null },
) {
    fun valueAt(x: Int, y: Int): Int? = cells[y * BlockCascadeColumns + x]
}

data class BlockCascadeGameState(
    val board: BlockCascadeBoard,
    val activePiece: FallingPiece,
    val nextPieceKind: BlockPieceKind,
    val linesCleared: Int = 0,
    val lastClearedRows: List<Int> = emptyList(),
    val topOut: Boolean = false,
)

class BlockCascadeBag(seed: Long = System.nanoTime()) {
    private val random = Random(seed)
    private var bag = shuffledKinds()

    fun next(): BlockPieceKind {
        if (bag.isEmpty()) {
            bag = shuffledKinds()
        }
        return bag.removeAt(0)
    }

    private fun shuffledKinds(): MutableList<BlockPieceKind> =
        BlockPieceKind.entries.shuffled(random).toMutableList()
}

fun newBlockCascadeState(bag: BlockCascadeBag): BlockCascadeGameState {
    val active = spawnPiece(bag.next())
    return BlockCascadeGameState(
        board = BlockCascadeBoard(),
        activePiece = active,
        nextPieceKind = bag.next(),
    )
}

fun spawnPiece(kind: BlockPieceKind): FallingPiece = FallingPiece(
    kind = kind,
    x = (BlockCascadeColumns / 2) - 2,
    y = 0,
    rotation = 0,
)

fun movePiece(
    state: BlockCascadeGameState,
    dx: Int,
    dy: Int,
): Pair<BlockCascadeGameState, Boolean> {
    val moved = state.activePiece.copy(
        x = state.activePiece.x + dx,
        y = state.activePiece.y + dy,
    )
    return if (canPlace(state.board, moved)) {
        state.copy(activePiece = moved, lastClearedRows = emptyList()) to true
    } else {
        state to false
    }
}

fun canMoveDown(board: BlockCascadeBoard, piece: FallingPiece): Boolean =
    canPlace(
        board = board,
        piece = piece.copy(y = piece.y + 1),
    )

fun hardDropPiece(board: BlockCascadeBoard, piece: FallingPiece): FallingPiece {
    var current = piece
    while (canMoveDown(board, current)) {
        current = current.copy(y = current.y + 1)
    }
    return current
}

fun rotatePiece(state: BlockCascadeGameState): Pair<BlockCascadeGameState, Boolean> {
    val rotated = state.activePiece.copy(
        rotation = (state.activePiece.rotation + 1) % state.activePiece.kind.rotations.size,
    )
    val candidates = listOf(
        rotated,
        rotated.copy(x = rotated.x - 1),
        rotated.copy(x = rotated.x + 1),
    )
    val valid = candidates.firstOrNull { canPlace(state.board, it) }
    return if (valid != null) {
        state.copy(activePiece = valid, lastClearedRows = emptyList()) to true
    } else {
        state to false
    }
}

fun lockAndAdvance(
    state: BlockCascadeGameState,
    bag: BlockCascadeBag,
): BlockCascadeGameState {
    val lockedBoard = lockPiece(state.board, state.activePiece)
    val (clearedBoard, clearedRows) = clearFullLines(lockedBoard)
    val nextActive = spawnPiece(state.nextPieceKind)
    val nextKind = bag.next()
    val canSpawnNext = canPlace(clearedBoard, nextActive)
    return state.copy(
        board = clearedBoard,
        activePiece = nextActive,
        nextPieceKind = nextKind,
        linesCleared = state.linesCleared + clearedRows.size,
        lastClearedRows = clearedRows,
        topOut = !canSpawnNext,
    )
}

fun canPlace(board: BlockCascadeBoard, piece: FallingPiece): Boolean =
    piece.cells.all { cell ->
        cell.x in 0 until BlockCascadeColumns &&
            cell.y in 0 until BlockCascadeRows &&
            board.valueAt(cell.x, cell.y) == null
    }

fun lockPiece(board: BlockCascadeBoard, piece: FallingPiece): BlockCascadeBoard {
    val updated = board.cells.toMutableList()
    piece.cells.forEach { cell ->
        if (cell.x in 0 until BlockCascadeColumns && cell.y in 0 until BlockCascadeRows) {
            updated[cell.y * BlockCascadeColumns + cell.x] = piece.kind.paletteIndex
        }
    }
    return BlockCascadeBoard(updated)
}

fun clearFullLines(board: BlockCascadeBoard): Pair<BlockCascadeBoard, List<Int>> {
    val fullRows = (0 until BlockCascadeRows).filter { y ->
        (0 until BlockCascadeColumns).all { x -> board.valueAt(x, y) != null }
    }
    if (fullRows.isEmpty()) return board to emptyList()

    val keptCells = buildList {
        for (y in 0 until BlockCascadeRows) {
            if (y in fullRows) continue
            for (x in 0 until BlockCascadeColumns) {
                add(board.valueAt(x, y))
            }
        }
    }
    val clearedCells = List(fullRows.size * BlockCascadeColumns) { null as Int? }
    return BlockCascadeBoard(clearedCells + keptCells) to fullRows
}

fun recoverFromTopOut(board: BlockCascadeBoard): BlockCascadeBoard {
    val rowsToClear = 4
    val keptRows = (0 until BlockCascadeRows - rowsToClear)
        .flatMap { y -> (0 until BlockCascadeColumns).map { x -> board.valueAt(x, y) } }
    val emptyRows = List(rowsToClear * BlockCascadeColumns) { null as Int? }
    return BlockCascadeBoard(emptyRows + keptRows)
}
