package com.sojourners.tchess.manual

import com.sojourners.chess.model.ManualRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualNavigatorTest {

    /** 构造树：root -> n1(h2e2) -> [n2a(默认), n2b] ；n2a -> n3(h9g7) */
    private fun buildTree(): ManualRecord {
        val root = ManualRecord(0, "开始局面", 0)
        val n1 = ManualRecord(1, "h2e2", "炮二平五")
        val n2a = ManualRecord(2, "h9g7", "马8进7")
        val n2b = ManualRecord(2, "h9g9", "车9平8")
        val n3 = ManualRecord(3, "b0c2", "马八进七")
        root.list.add(n1)
        root.next = 0
        n1.list.add(n2a)
        n1.list.add(n2b)
        n1.next = 0 // 主线走 n2a
        n2a.list.add(n3)
        return root
    }

    @Test
    fun `open 按 next 链物化主线`() {
        val nav = ManualNavigator()
        nav.open(com.sojourners.chess.manual.ChessManual().apply {
            fenCode = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            head = buildTree()
        })
        assertTrue(nav.isOpen)
        assertEquals(4, nav.size) // 根 + 3 着（主线取 n2a）
        assertEquals("h2e2", nav.nodeAt(1)?.move)
        assertEquals("h9g7", nav.nodeAt(2)?.move)
        assertEquals("b0c2", nav.nodeAt(3)?.move)
        // 打开后指针在开始局面（p=0），与桌面 openFromChessManual 一致
        assertEquals(0, nav.position)
        assertEquals(emptyList<String>(), nav.moveList())
        // 整条线的着法
        assertEquals(listOf("h2e2", "h9g7", "b0c2"), nav.moveListUpTo(nav.size - 1))
    }

    @Test
    fun `行棋方按奇偶交替且起始由FEN决定`() {
        val nav = ManualNavigator()
        nav.open(com.sojourners.chess.manual.ChessManual().apply {
            fenCode = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            head = buildTree()
        })
        assertTrue(nav.redGoAt(0)) // 红先
        assertFalse(nav.redGoAt(1))
        assertTrue(nav.redGoAt(2))
        assertTrue(nav.currentRedToGo()) // p=0 开始局面轮红
    }

    @Test
    fun `前后退与首尾跳转`() {
        val nav = openNav()
        assertFalse(nav.back()) // 已在根
        assertTrue(nav.forward())
        assertEquals(1, nav.position)
        assertTrue(nav.toEnd())
        assertEquals(3, nav.position)
        assertFalse(nav.forward())
        assertTrue(nav.back())
        assertTrue(nav.toStart())
        assertEquals(0, nav.position)
        assertTrue(nav.jumpTo(2))
        assertEquals(2, nav.position)
        assertFalse(nav.jumpTo(99))
    }

    @Test
    fun `切换变着重建线尾并前进到分支首着`() {
        val nav = openNav()
        assertFalse(nav.toStart()) // 打开后已在开局位置
        assertTrue(nav.forward()) // p=1 (n1)，有两个变着
        assertEquals(2, nav.childrenOfCurrent().size)

        assertTrue(nav.switchBranch(1)) // 切到 n2b 车9平8
        assertEquals(0, nav.nodeAt(0)!!.list.indexOf(nav.nodeAt(1))) // 线首仍是原主线第一着 h2e2
        assertEquals("h9g9", nav.moveList()[1])
        assertEquals(2, nav.position) // 前进到新分支首着
        assertEquals(3, nav.size) // n2b 是叶子，线到此为止
        assertEquals("h9g9", nav.currentNode()!!.move)
        // 分支选择已被记录在节点上（导出时保留）
        assertEquals(1, nav.nodeAt(1)!!.next)
    }

    @Test
    fun `上变下变跳转定位最近的多分支节点`() {
        val nav = openNav()
        assertTrue(nav.toEnd())           // p=3（叶子）
        assertTrue(nav.prevBranchJump())  // 向上找到 n1（两个变着）
        assertEquals(1, nav.position)
        assertFalse(nav.prevBranchJump()) // 再向上无多分支节点
        assertFalse(nav.nextBranchJump()) // n1 之后主线（n2a 单子 / n3 叶子）无多分支节点

        // 切到 n2b 后线尾只有一层，next/prev 同样安全
        assertTrue(nav.switchBranch(1))
        assertTrue(nav.prevBranchJump())
        assertEquals(1, nav.position)
    }

    @Test
    fun `空导航操作安全`() {
        val nav = ManualNavigator()
        assertFalse(nav.isOpen)
        assertFalse(nav.forward())
        assertNull(nav.currentNode())
        assertEquals(emptyList<String>(), nav.moveList())
    }

    // ---------------------------------------------------------------- helpers

    private fun openNav(): ManualNavigator {
        val nav = ManualNavigator()
        nav.open(com.sojourners.chess.manual.ChessManual().apply {
            fenCode = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            head = buildTree()
        })
        return nav
    }
}
