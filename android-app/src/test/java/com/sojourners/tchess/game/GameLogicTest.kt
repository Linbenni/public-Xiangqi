package com.sojourners.tchess.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameLogicTest {

    private fun loadFen(g: GameLogic, fen: String) {
        g.reset()
        com.sojourners.chess.util.XiangqiUtils.fenToBoard(g.board, fen)
        // 行棋方：FEN 段 w/b
        val redGo = fen.split(" ").getOrNull(1) != "b"
        // 通过重放机制无法直接设置行棋方，这里用反射替代：replay 后手动同步
        setRedToGo(g, redGo)
    }

    private fun setRedToGo(g: GameLogic, redGo: Boolean) {
        val field = GameLogic::class.java.getDeclaredField("redToGo")
        field.isAccessible = true
        field.setBoolean(g, redGo)
    }

    @Test
    fun `初始局面 红马合法落点为2个`() {
        val g = GameLogic()
        val targets = g.legalTargets(1, 9)
        assertEquals(2, targets.size)
        assertTrue(targets.any { it.x == 0 && it.y == 7 })
        assertTrue(targets.any { it.x == 2 && it.y == 7 })
    }

    @Test
    fun `初始红炮合法着法共12种含隔屏吃马`() {
        val g = GameLogic()
        val targets = g.legalTargets(1, 7)
        // 横向 6 格；纵向 r8 与 r6..r3 共 5 格；隔 (c1,r2) 黑炮之屏吃 (c1,r0) 黑马 1 步
        assertEquals(12, targets.size)
        assertTrue(targets.any { it.x == 1 && it.y == 0 }) // 隔屏吃马
        assertFalse(targets.any { it.x == 1 && it.y == 2 }) // 屏风格本身不能落
    }

    @Test
    fun `开局红兵只能前进一格`() {
        val g = GameLogic()
        val targets = g.legalTargets(0, 6)
        assertEquals(1, targets.size)
        assertEquals(0, targets[0].x)
        assertEquals(5, targets[0].y)
    }

    @Test
    fun `被牵制时纵向移动视为送将被拒绝`() {
        val g = GameLogic()
        // 黑车在列4行1 直盯红帅（列4行9），中间全空；红帅纵走仍被将 → 非法
        loadFen(g, "9/4r4/9/9/9/9/9/9/9/4K4 w - - 0 1")
        val outcome = g.tryApply(4, 9, 4, 8)
        assertFalse(outcome.applied)
        assertEquals('K', g.pieceAt(4, 9))
        assertEquals('r', g.pieceAt(4, 1))
    }

    @Test
    fun `吃子产生 CAPTURE 音效事件`() {
        val g = GameLogic()
        // 黑卒在红车上方一格（FEN 第 9 行 = r8）；黑将在原位；红帅避开 c4 列防对将
        loadFen(g, "4k4/9/9/9/9/9/9/9/p8/R2K5 w - - 0 1")
        val outcome = g.tryApply(0, 9, 0, 8)
        assertTrue(outcome.applied)
        assertEquals(SoundCue.CAPTURE, outcome.cue)
        assertEquals('p', outcome.capturedPiece)
    }

    @Test
    fun `双车控线卧槽马绝杀判定`() {
        val g = GameLogic()
        // 黑 lone 将 (c4,r0)；红双车封 r0/r1 两横线，红马 (c3,r2) 跳将；
        // 无子可挡马、无格可逃 → 绝杀
        loadFen(g, "R3k4/R8/3N5/9/9/9/9/9/9/4K4 b - - 0 1")
        assertTrue(g.sideToMoveInCheck())
        assertTrue(g.sideToMoveHasNoLegalMove())
    }

    @Test
    fun `悔棋截断后重放保持一致`() {
        val g = GameLogic()
        val seq = listOf("h2e2", "h9g7", "h0g2", "b9c7", "i0h0")
        assertTrue(seq.all { g.applyEngineMove(it) })
        val fenBefore = g.currentFen()
        val textsBefore = ArrayList(g.moveTexts)

        g.truncateTo(seq.size - 2)
        assertEquals(seq.size - 2, g.moves.size)

        g.replayTo(seq)
        assertEquals(fenBefore, g.currentFen())
        assertEquals(textsBefore, g.moveTexts)
    }

    @Test
    fun `FEN往返与将军查询`() {
        val g = GameLogic()
        assertEquals(GameLogic.INITIAL_FEN, g.currentFen())
        g.applyEngineMove("h2e2")
        assertNotNull(g.currentFen())
        assertFalse(g.sideToMoveHasNoLegalMove())
        assertFalse(g.sideToMoveInCheck())
    }

    @Test
    fun `非法引擎坐标被拒绝`() {
        val g = GameLogic()
        assertNull(g.parseEngineMove("z9z9"))
        assertNull(g.parseEngineMove("h2"))
        assertNull(g.parseEngineMove("h0h9x"))
        assertFalse(g.tryApply(4, 4, 4, 5).applied) // 空格起步
    }

    @Test
    fun `中文着法翻译`() {
        val g = GameLogic()
        g.applyEngineMove("h2e2")
        assertEquals("炮二平五", g.moveTexts.last())
    }
}
