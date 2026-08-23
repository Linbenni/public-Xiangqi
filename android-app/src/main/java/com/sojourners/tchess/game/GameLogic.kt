package com.sojourners.tchess.game

import com.sojourners.chess.board.BoardPoint
import com.sojourners.chess.util.FenUtils
import com.sojourners.chess.util.XiangqiUtils

/**
 * 对局规则与局面状态（纯 Kotlin，无 Android 依赖，可直接 JVM 单测）。
 *
 * 坐标约定：
 * - 对外一律使用 [BoardPoint](x=列 0..8, y=行 0..9)，行 0 为黑方底线（与桌面 ChessBoard 一致）；
 * - core 的 XiangqiUtils 内部参数是 (行, 列) 顺序，这里统一封装避免误用。
 */
class GameLogic {

    companion object {
        const val INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
    }

    /** board[行][列] */
    val board: Array<CharArray> = Array(10) { CharArray(9) { ' ' } }

    var redToGo: Boolean = true
        private set

    /** 引擎坐标着法历史（如 h2e2） */
    val moves = ArrayList<String>()

    /** 中文着法记录（炮二平五），与 [moves] 一一对应 */
    val moveTexts = ArrayList<String>()

    init {
        reset()
    }

    fun reset() {
        XiangqiUtils.fenToBoard(board, INITIAL_FEN)
        redToGo = true
        moves.clear()
        moveTexts.clear()
    }

    fun pieceAt(col: Int, row: Int): Char = board[row][col]

    fun snapshotBoard(): Array<CharArray> = Array(10) { r -> board[r].clone() }

    fun currentFen(): String = FenUtils.fenCode(board, redToGo)

    /** 该格是否有指定一方棋子 */
    fun hasSidePiece(col: Int, row: Int, red: Boolean): Boolean =
        board[row][col] != ' ' && XiangqiUtils.isRed(board[row][col]) == red

    /** 走该步后是否置自身于被将（送将） */
    fun leavesSelfInCheck(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int): Boolean {
        val piece = board[fromRow][fromCol]
        if (piece == ' ') return false
        val isRed = XiangqiUtils.isRed(piece)
        val captured = board[toRow][toCol]
        board[toRow][toCol] = piece
        board[fromRow][fromCol] = ' '
        val inCheck = XiangqiUtils.isJiang(board, isRed)
        board[fromRow][fromCol] = piece
        board[toRow][toCol] = captured
        return inCheck
    }

    /**
     * 某格棋子的合法落点列表（含"不可送将"过滤），用于选中提示。
     */
    fun legalTargets(fromCol: Int, fromRow: Int): List<BoardPoint> {
        if (board[fromRow][fromCol] == ' ') return emptyList()
        val out = ArrayList<BoardPoint>()
        for (row in 0..9) {
            for (col in 0..8) {
                if (col == fromCol && row == fromRow) continue
                if (!XiangqiUtils.canGo(board, fromRow, fromCol, row, col)) continue
                if (leavesSelfInCheck(fromCol, fromRow, col, row)) continue
                out.add(BoardPoint(col, row))
            }
        }
        return out
    }

    /**
     * 尝试落子（人走或引擎回包共用）。不符合棋规（含送将）返回 REJECTED。
     */
    fun tryApply(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int): MoveOutcome {
        val piece = board[fromRow][fromCol]
        if (piece == ' ') return MoveOutcome.REJECTED
        val captured = board[toRow][toCol]
        if (captured != ' ' && XiangqiUtils.isRed(captured) == XiangqiUtils.isRed(piece)) {
            return MoveOutcome.REJECTED
        }
        // 棋规校验（兵种走法，与桌面 mouseClick 的 canGo 一致）
        if (!XiangqiUtils.canGo(board, fromRow, fromCol, toRow, toCol)) {
            return MoveOutcome.REJECTED
        }
        val isRed = XiangqiUtils.isRed(piece)

        // 中文着法与引擎坐标都须在落子前翻译（按走子前局面取子）
        val engineMove = stepForEngine(fromCol, fromRow, toCol, toRow)
        val moveText = translateMove(engineMove)

        board[toRow][toCol] = piece
        board[fromRow][fromCol] = ' '
        if (XiangqiUtils.isJiang(board, isRed)) {
            // 不可送将：回退
            board[fromRow][fromCol] = piece
            board[toRow][toCol] = captured
            return MoveOutcome.REJECTED
        }

        moveTexts.add(moveText)
        moves.add(engineMove)
        redToGo = !redToGo

        val cue = when {
            XiangqiUtils.isSha(board, !isRed) -> SoundCue.WIN       // 绝杀对方
            XiangqiUtils.isJiang(board, !isRed) -> SoundCue.CHECK   // 将军
            captured != ' ' -> SoundCue.CAPTURE
            else -> SoundCue.MOVE
        }
        return MoveOutcome(true, cue, engineMove, captured)
    }

    /**
     * 静默应用一条引擎坐标着法并校验（用于悔棋重放/恢复局面）。
     */
    fun applyEngineMove(engineMove: String): Boolean {
        val p = parseEngineMove(engineMove) ?: return false
        return tryApply(p[0], p[1], p[2], p[3]).applied
    }

    /** 重放到给定着法序列（从初始局面开始） */
    fun replayTo(subset: List<String>) {
        reset()
        subset.forEach { applyEngineMove(it) }
    }

    /** 截断到前 n 条着法 */
    fun truncateTo(n: Int) {
        val keep = moves.take(n.coerceAtLeast(0)).toList()
        replayTo(keep)
    }

    /**
     * 行棋方是否已无合法着法（绝杀或困毙均判负）。
     */
    fun sideToMoveHasNoLegalMove(): Boolean {
        val red = redToGo
        for (row in 0..9) {
            for (col in 0..8) {
                if (!hasSidePiece(col, row, red)) continue
                if (legalTargets(col, row).isNotEmpty()) return false
            }
        }
        return true
    }

    /** 行棋方当前是否被将军 */
    fun sideToMoveInCheck(): Boolean = XiangqiUtils.isJiang(board, redToGo)

    /**
     * 引擎坐标 "h2e2" → [fromCol, fromRow, toCol, toRow]，非法返回 null。
     */
    fun parseEngineMove(move: String): IntArray? {
        if (move.length != 4) return null
        val c1 = move[0] - 'a'
        val r1 = 9 - (move[1] - '0')
        val c2 = move[2] - 'a'
        val r2 = 9 - (move[3] - '0')
        if (c1 !in 0..8 || c2 !in 0..8 || r1 !in 0..9 || r2 !in 0..9) return null
        return intArrayOf(c1, r1, c2, r2)
    }

    private fun translateMove(engineMove: String): String {
        val sb = StringBuilder()
        XiangqiUtils.translate(board, sb, engineMove, false)
        return sb.toString()
    }

    private fun stepForEngine(col1: Int, row1: Int, col2: Int, row2: Int): String =
        FenUtils.stepForEngine(col1, row1, col2, row2)
}
