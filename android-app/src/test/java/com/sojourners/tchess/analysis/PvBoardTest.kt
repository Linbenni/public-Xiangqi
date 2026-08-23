package com.sojourners.tchess.analysis

import com.sojourners.chess.model.ThinkData
import com.sojourners.chess.util.XiangqiUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PvBoardTest {

    private fun info(
        pv: Int,
        depth: Int,
        score: Int?,
        mate: Int? = null,
        moves: List<String> = listOf("h2e2"),
    ): ThinkData = ThinkData().apply {
        setPv(pv)
        setDepth(depth)
        if (score != null) setScore(score) else setMate(mate)
        setNps(1_000_000L)
        setTime(1500L)
        setNodes(654_321L)
        setDetail(ArrayList(moves))
    }

    @Test
    fun `offer 同一 pv 只保留最新`() {
        val b = PvBoard()
        b.offer(info(pv = 1, depth = 8, score = 20))
        b.offer(info(pv = 1, depth = 9, score = 25))
        val drained = b.drain()
        assertEquals(1, drained.size)
        assertEquals(9, drained[1]!!.getDepth())
        assertTrue(b.drain().isEmpty())
    }

    @Test
    fun `drain 按 pv 升序返回且清空待处理`() {
        val b = PvBoard()
        b.offer(info(3, 10, -30))
        b.offer(info(1, 10, 40))
        b.offer(info(2, 10, 0))
        val keys = b.drain().keys.toList()
        assertEquals(listOf(1, 2, 3), keys)
    }

    @Test
    fun `rows 保持 pv 升序快照 history 上限截断`() {
        val b = PvBoard()
        repeat(PvBoard.HISTORY_MAX + 10) { i ->
            b.applyGenerated(
                PvRow(1, i, 30, null, "+30", 1000, 1.0, 1000, "炮二平五", "h2e2", null),
                recordHistory = true,
            )
        }
        assertEquals(PvBoard.HISTORY_MAX, b.historySnapshot().size)
        // 最新在前
        assertEquals((PvBoard.HISTORY_MAX + 9).toString(), b.historySnapshot()[0].depth.toString())

        b.applyGenerated(PvRow(2, 5, -10, null, "-10", 900, 0.9, 900, "马8进7", "h9g7", null), false)
        assertEquals(listOf(1, 2), b.rows().map { it.pv })
        b.clear()
        assertTrue(b.rows().isEmpty() && b.historySnapshot().isEmpty())
    }

    @Test
    fun `generatePvRow 与桌面语义一致 常规分数`() {
        val board = XiangqiUtils.fenToBoard(GameLogicFen.INITIAL)
        val row = generatePvRow(info(1, 12, 34, moves = listOf("h2e2", "h9g7")), redGo = true, board = board)
        assertNotNull(row)
        assertEquals(1, row!!.pv)
        assertEquals(12, row.depth)
        assertEquals(34, row.redScore)          // 红先行：红视角 = 原值
        assertNull(row.mateIn)
        assertEquals("+34", row.scoreText)
        assertEquals("h2e2", row.firstMoveUci)
        assertEquals("h9g7", row.secondMoveUci)
        assertEquals(1000L, row.npsK)
        assertEquals(1.5, row.timeS, 1e-9)
        // 中文着法翻译与桌面同源（ThinkData.generate）
        assertTrue(row.movesText.contains("炮"))
    }

    @Test
    fun `generatePvRow 黑行棋时 redScore 取反 绝杀标记`() {
        val board = XiangqiUtils.fenToBoard(GameLogicFen.INITIAL)
        val row = generatePvRow(info(1, 15, score = null, mate = 3, moves = listOf("h2e2")), redGo = false, board = board)
        assertNotNull(row)
        assertTrue(row!!.isMate)
        assertEquals(3, row.mateIn)
        assertEquals(-3, row.redScore)          // 黑先行：红视角取反
        assertEquals("#3", row.scoreText)
    }
}

/** 避免直接依赖 android 模块内 GameLogic 的间接引用，独立声明初始 FEN */
private object GameLogicFen {
    const val INITIAL = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
}
