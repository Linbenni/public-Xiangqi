package com.sojourners.tchess.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sojourners.chess.board.BoardPoint
import com.sojourners.chess.board.MoveStep
import kotlin.math.roundToInt

/** 上一步标记色（与桌面一致） */
private val PREV_STEP_COLOR = Color(0xFFBF242A)

/** 选中标记色（与桌面一致） */
private val SELECT_COLOR = Color(0xFF0000FF)

/** 合法着法提示色 */
private val TARGET_COLOR = Color(0xFF1B5E20)

/** 被将军提示色 */
private val CHECK_COLOR = Color(0xFFD32F2F)

/**
 * Compose Canvas 棋盘绘制（几何/标记逻辑移植自桌面 BaseBoardRender）。
 *
 * @param anim 当前走子动画（null 表示无动画）
 * @param animFraction 0→1 的动画进度
 */
fun DrawScope.drawXiangqiBoard(
    layout: BoardLayout,
    board: Array<CharArray>,
    assets: BoardAssets,
    reversed: Boolean,
    selected: BoardPoint?,
    targets: List<BoardPoint>,
    captureTargets: Set<Int>,
    lastMove: MoveStep?,
    anim: MoveStep?,
    animFraction: Float,
) {
    // 背景：board.png 拉伸铺满（含棋盘线、楚河汉界，同桌面自定义样式）
    drawImage(
        image = assets.boardImage,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(assets.boardImage.width, assets.boardImage.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )

    // 上一步标记（四角括号）
    lastMove?.let { step ->
        drawStepBrackets(layout, step.getStart().x, step.getStart().y, reversed, PREV_STEP_COLOR)
        drawStepBrackets(layout, step.getEnd().x, step.getEnd().y, reversed, PREV_STEP_COLOR)
    }

    // 合法着法提示：空点实心圆，吃子点圆环
    targets.forEach { t ->
        val cx = BoardGeometry.centerX(t.x, layout, reversed)
        val cy = BoardGeometry.centerY(t.y, layout, reversed)
        if ((t.y * 9 + t.x) in captureTargets) {
            drawCircle(
                color = TARGET_COLOR.copy(alpha = 0.75f),
                radius = layout.piece / 3.1f,
                center = Offset(cx, cy),
                style = Stroke(width = layout.piece / 14f),
            )
        } else {
            drawCircle(
                color = TARGET_COLOR.copy(alpha = 0.55f),
                radius = layout.piece / 9f,
                center = Offset(cx, cy),
            )
        }
    }

    // 棋子（动画中的终点格暂留空，最后单独插值绘制）
    val half = (layout.piece - layout.piece / 16f) / 2f
    var animPiece: androidx.compose.ui.graphics.ImageBitmap? = null
    for (row in 0..9) {
        for (col in 0..8) {
            val ch = board[row][col]
            val bitmap = assets.pieces[ch] ?: continue
            if (anim != null && col == anim.getEnd().x && row == anim.getEnd().y) {
                animPiece = bitmap
                continue
            }
            val cx = BoardGeometry.centerX(col, layout, reversed)
            val cy = BoardGeometry.centerY(row, layout, reversed)
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()),
                dstSize = IntSize((half * 2).roundToInt(), (half * 2).roundToInt()),
            )
        }
    }

    // 动画棋子浮于最上层
    if (anim != null && animPiece != null && animFraction < 1f) {
        val fromX = BoardGeometry.centerX(anim.getStart().x, layout, reversed)
        val fromY = BoardGeometry.centerY(anim.getStart().y, layout, reversed)
        val toX = BoardGeometry.centerX(anim.getEnd().x, layout, reversed)
        val toY = BoardGeometry.centerY(anim.getEnd().y, layout, reversed)
        val cx = fromX + (toX - fromX) * animFraction
        val cy = fromY + (toY - fromY) * animFraction
        drawImage(
            image = animPiece,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(animPiece.width, animPiece.height),
            dstOffset = IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()),
            dstSize = IntSize((half * 2).roundToInt(), (half * 2).roundToInt()),
        )
    }

    // 选中标记（盖在棋子上）
    selected?.let { drawStepBrackets(layout, it.x, it.y, reversed, SELECT_COLOR) }
}

/**
 * 将军警示环：扫描行棋方将/帅位置绘制红环。
 */
fun DrawScope.drawCheckRing(
    layout: BoardLayout,
    board: Array<CharArray>,
    redToGo: Boolean,
    reversed: Boolean,
) {
    val king = if (redToGo) 'K' else 'k'
    for (row in 0..9) {
        for (col in 0..8) {
            if (board[row][col] == king) {
                val cx = BoardGeometry.centerX(col, layout, reversed)
                val cy = BoardGeometry.centerY(row, layout, reversed)
                drawCircle(
                    color = CHECK_COLOR.copy(alpha = 0.85f),
                    radius = layout.piece / 2.6f,
                    center = Offset(cx, cy),
                    style = Stroke(width = layout.piece / 12f, cap = StrokeCap.Round),
                )
                return
            }
        }
    }
}

/**
 * 四角括号标记（移植自桌面 drawStepRemark）。
 */
private fun DrawScope.drawStepBrackets(
    layout: BoardLayout,
    col: Int,
    row: Int,
    reversed: Boolean,
    color: Color,
) {
    val x = BoardGeometry.centerX(col, layout, reversed)
    val y = BoardGeometry.centerY(row, layout, reversed)
    val len = layout.piece / 1.6f
    val arm = len / 6f
    val h = len / 2f
    val width = layout.piece / 25f

    // 左上
    drawLine(color, Offset(x - h + arm, y - h), Offset(x - h, y - h), width)
    drawLine(color, Offset(x - h, y - h), Offset(x - h, y - h + arm), width)
    // 右上
    drawLine(color, Offset(x + h - arm, y - h), Offset(x + h, y - h), width)
    drawLine(color, Offset(x + h, y - h), Offset(x + h, y - h + arm), width)
    // 左下
    drawLine(color, Offset(x - h + arm, y + h), Offset(x - h, y + h), width)
    drawLine(color, Offset(x - h, y + h), Offset(x - h, y + h - arm), width)
    // 右下
    drawLine(color, Offset(x + h - arm, y + h), Offset(x + h, y + h), width)
    drawLine(color, Offset(x + h, y + h), Offset(x + h, y + h - arm), width)
}
