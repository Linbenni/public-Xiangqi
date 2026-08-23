package com.sojourners.tchess.ui.board

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardGeometryTest {

    @Test
    fun `布局尺寸与桌面公式一致`() {
        val layout = BoardGeometry.fit(1000f, 1200f)
        // 宽度受限：piece = 1000 / (9+1/3)
        assertEquals(1000f / (9f + 1f / 3f), layout.piece, 0.01f)
        assertEquals(layout.piece / 6f, layout.padding, 0.001f)
        assertTrue(layout.width <= 1000f + 0.01f)
        assertTrue(layout.height <= 1200f + 0.01f)
    }

    @Test
    fun `格心与反查往返（正向）`() {
        val layout = BoardGeometry.fit(900f, 1020f)
        for (col in 0..8) {
            for (row in 0..9) {
                val cx = BoardGeometry.centerX(col, layout, false)
                val cy = BoardGeometry.centerY(row, layout, false)
                val cell = BoardGeometry.cellAt(cx, cy, layout, false)
                assertNotNull(cell)
                assertEquals(col, cell!!.x)
                assertEquals(row, cell.y)
            }
        }
    }

    @Test
    fun `格心与反查往返（黑方视角翻转）`() {
        val layout = BoardGeometry.fit(720f, 960f)
        for (col in 0..8) {
            for (row in 0..9) {
                // 正向坐标 (c,r) 的屏幕位置，用翻转视角反查应得 (8-c, 9-r)
                val cx = BoardGeometry.centerX(col, layout, false)
                val cy = BoardGeometry.centerY(row, layout, false)
                val cell = BoardGeometry.cellAt(cx, cy, layout, true)
                assertNotNull(cell)
                assertEquals(8 - col, cell!!.x)
                assertEquals(9 - row, cell.y)
            }
        }
    }

    @Test
    fun `盘外点击返回null`() {
        val layout = BoardGeometry.fit(600f, 800f)
        assertNull(BoardGeometry.cellAt(-10f, 10f, layout, false))
        assertNull(BoardGeometry.cellAt(layout.width + 50f, 10f, layout, false))
        assertNull(BoardGeometry.cellAt(10f, layout.height + 50f, layout, true))
    }
}
