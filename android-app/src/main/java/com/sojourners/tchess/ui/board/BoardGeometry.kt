package com.sojourners.tchess.ui.board

import com.sojourners.chess.board.BoardPoint
import kotlin.math.min

/**
 * 棋盘布局参数（几何移植自桌面 BaseBoardRender）：
 * 画布宽 = 2*padding + piece*9，高 = 2*padding + piece*10，
 * 第一交叉点中心位于 (padding + piece/2, padding + piece/2)。
 */
data class BoardLayout(val piece: Float, val padding: Float) {
    val pos: Float get() = padding + piece / 2f
    val width: Float get() = 2 * padding + piece * 9f
    val height: Float get() = 2 * padding + piece * 10f
}

/**
 * 纯几何换算（无 Android 依赖，可 JVM 单测）。桌面/安卓共用同一套数学（ANDROID_PLAN.md §5.4）。
 */
object BoardGeometry {

    private const val COLS = 9f
    private const val ROWS = 10f

    /**
     * 依据可用宽高计算格距，长宽比与桌面 autoFit 一致（9+1/3 : 10+1/3）。
     */
    fun fit(availableWidth: Float, availableHeight: Float): BoardLayout {
        require(availableWidth > 0f && availableHeight > 0f) { "可用区域必须为正数" }
        val byWidth = availableWidth / (COLS + 1f / 3f)
        val byHeight = availableHeight / (ROWS + 1f / 3f)
        val piece = min(byWidth, byHeight)
        return BoardLayout(piece, piece / 6f)
    }

    fun reverseX(col: Int, reversed: Boolean): Int = if (reversed) 8 - col else col

    fun reverseY(row: Int, reversed: Boolean): Int = if (reversed) 9 - row else row

    /** 屏幕坐标 → 棋盘格；越界返回 null（用 floor 取整，避免负偏移被截断进第 0 格） */
    fun cellAt(px: Float, py: Float, layout: BoardLayout, reversed: Boolean): BoardPoint? {
        val col = kotlin.math.floor((px - layout.padding) / layout.piece).toInt()
        val row = kotlin.math.floor((py - layout.padding) / layout.piece).toInt()
        if (col < 0 || col > 8 || row < 0 || row > 9) return null
        return BoardPoint(reverseX(col, reversed), reverseY(row, reversed))
    }

    /** 格中心的屏幕坐标 */
    fun centerX(col: Int, layout: BoardLayout, reversed: Boolean): Float =
        layout.pos + layout.piece * reverseX(col, reversed)

    fun centerY(row: Int, layout: BoardLayout, reversed: Boolean): Float =
        layout.pos + layout.piece * reverseY(row, reversed)
}
