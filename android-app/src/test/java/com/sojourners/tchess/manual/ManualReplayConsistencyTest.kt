package com.sojourners.tchess.manual

import com.sojourners.chess.manual.PgnChessManualImpl
import com.sojourners.chess.util.FenUtils
import com.sojourners.chess.util.XiangqiUtils
import com.sojourners.tchess.game.GameLogic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualReplayConsistencyTest {

    companion object {
        /** 桌面「另存为 PGN」的输出形态（Chinese 格式，中文着法 + FEN 头） */
        val DESKTOP_PGN = """
            [Game "Chinese Chess"]
            [Event "测试对局"]
            [Site "北京"]
            [Date "2026/08/01"]
            [Red "红方"]
            [Black "黑方"]
            [Result "*"]
            [FEN "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"]
            [Format "Chinese"]

            1. 炮二平五
                马８进７
            2. 马八进七
                车９平８
            *
        """.trimIndent()
    }

    /** 桌面产生的棋谱在手机端打开：导航重放后的 FEN 与独立推演一致（M4 验收标准） */
    @Test
    fun `桌面PGN导入后逐着重放的FEN与独立推演一致`() {
        val cm = PgnChessManualImpl().getChessManualFromText(DESKTOP_PGN)
        assertNotNull(cm.head)
        assertEquals("测试对局", cm.name)

        val nav = ManualNavigator()
        nav.open(cm)
        assertTrue(nav.isOpen)
        assertTrue(nav.toEnd()) // 推进到终局再取整线着法
        assertEquals(5, nav.size) // 根 + 4 着
        assertEquals(4, nav.moveList().size)
        // 中文着法已由 core 解析并保留，坐标着法同步翻译回填（与桌面同源）
        assertEquals("炮二平五", nav.nodeAt(1)?.cnMove)
        assertEquals("马８进７", nav.nodeAt(2)?.cnMove)
        assertEquals("车９平８", nav.nodeAt(4)?.cnMove)

        // 用 GameLogic（对弈页同一套规则）从起始局面重放
        val startBoard = Array(10) { CharArray(9) { ' ' } }
        XiangqiUtils.fenToBoard(startBoard, nav.fenCode)
        val logic = GameLogic()
        logic.loadPosition(startBoard, nav.startRedToGo())
        for (mv in nav.moveList()) {
            assertTrue(logic.applyEngineMove(mv), "着法应合法: $mv")
        }

        // 独立推演期望局面：直接按 ICCS 坐标搬子后生成 FEN（不经 GameLogic 规则层）
        val board = startBoard.map { it.clone() }.toTypedArray()
        var redGo = true
        nav.moveList().forEach { mv ->
            val c1 = mv[0] - 'a'; val r1 = 9 - (mv[1] - '0')
            val c2 = mv[2] - 'a'; val r2 = 9 - (mv[3] - '0')
            board[r2][c2] = board[r1][c1]
            board[r1][c1] = ' '
            redGo = !redGo
        }
        val expectedFen = FenUtils.fenCode(board, redGo)
        assertEquals(expectedFen, logic.currentFen())
        assertEquals(redGo, logic.redToGo)
        assertEquals(nav.redGoAt(nav.position), logic.redToGo)
    }

    /** ICCS 格式棋谱同样可解析且 cnMove 由 core 补齐 */
    @Test
    fun `ICCS格式PGN解析并翻译中文着法`() {
        val pgn = """
            [FEN "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"]
            [Format "ICCS"]

            1. h2e2 h9g7
            *
        """.trimIndent()
        val cm = PgnChessManualImpl().getChessManualFromText(pgn)
        val nav = ManualNavigator()
        nav.open(cm)
        assertTrue(nav.toEnd())
        assertEquals(2, nav.moveList().size)
        assertEquals(listOf("h2e2", "h9g7"), nav.moveList())
        assertNotNull(nav.nodeAt(1)?.cnMove?.takeIf { it.isNotBlank() })
    }

    /** 起始局面为黑先的自定义 FEN 时行棋方奇偶正确 */
    @Test
    fun `黑先起始局面的行棋方交替`() {
        val pgn = """
            [FEN "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR b - - 0 1"]
            [Format "ICCS"]

            1. h9g7 h2e2
            *
        """.trimIndent()
        val cm = PgnChessManualImpl().getChessManualFromText(pgn)
        val nav = ManualNavigator()
        nav.open(cm)
        assertFalse(nav.startRedToGo())
        assertTrue(nav.redGoAt(1)) // 黑先：第 1 着后轮红
    }
}
