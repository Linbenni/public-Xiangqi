package com.sojourners.tchess.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sojourners.tchess.analysis.PvRow
import com.sojourners.tchess.ui.board.BoardGeometry
import com.sojourners.tchess.ui.board.drawXiangqiBoard
import com.sojourners.tchess.ui.board.rememberBoardAssets

private val RED_ADVANTAGE = Color(0xFFBF242A)
private val BLACK_ADVANTAGE = Color(0xFF1B5E20)

/**
 * M3 分析页：局面装载（FEN / 对局导入）+ 迷你盘面（主变箭头）+ 评估柱 +
 * MultiPV 变化列表 + 思考记录表格。
 */
@Composable
fun AnalysisScreen(vm: AnalysisViewModel) {
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // ---- 操作行 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { vm.startOrStop() },
                enabled = ui.running || ui.canStart,
            ) {
                Text(if (ui.running) "停止" else "开始分析")
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = listOfNotNull(
                    if (ui.redToGo) "红先行" else "黑先行",
                    ui.timeStrategy.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- 引擎不可用 / 消息提示 ----
        ui.engineUnavailableReason?.let { reason ->
            Banner(reason, errorContainer = true)
        }
        ui.message?.let { msg ->
            Banner(msg, errorContainer = false)
        }

        // ---- FEN 行 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OutlinedTextField(
                value = ui.fenInput,
                onValueChange = vm::onFenInputChanged,
                label = { Text("FEN") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.loadFen() }) { Text("载入") }
        }
        TextButtonRow("重置为初始局面") { vm.resetToInitial() }

        // ---- 盘面 + 评估柱 ----
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvalBarView(fraction = ui.evalFraction, evalText = ui.evalText)
            Spacer(Modifier.width(8.dp))
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val maxWidthPx = with(density) { maxWidth.toPx() }
                val maxHeightPx = with(density) { maxHeight.toPx() }
                if (maxWidthPx > 0f && maxHeightPx > 0f) {
                    val layout = BoardGeometry.fit(maxWidthPx - 8f, maxHeightPx - 8f)
                    val assets = rememberBoardAssets()
                    if (assets != null) {
                        Canvas(
                            modifier = Modifier.size(
                                with(density) { layout.width.toDp() },
                                with(density) { layout.height.toDp() },
                            ),
                        ) {
                            drawXiangqiBoard(
                                layout = layout,
                                board = vm.logic.board,
                                assets = assets,
                                reversed = false,
                                selected = null,
                                targets = emptyList(),
                                captureTargets = emptySet(),
                                lastMove = null,
                                anim = null,
                                animFraction = 1f,
                            )
                            drawPreviewArrow(layout, ui.previewFrom, ui.previewTo)
                        }
                    }
                }
            }
        }

        // ---- 主变状态行 ----
        Text(
            text = ui.statusTitle ?: "尚未开始分析",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        // ---- MultiPV 变化列表 ----
        Text(
            text = "MultiPV 变化（点击查看前两着箭头）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            itemsIndexed(ui.rows, key = { _, r -> r.pv }) { _, row ->
                PvRowView(row = row, selected = row.pv == ui.selectedPv) {
                    vm.selectPv(row.pv)
                }
            }
        }

        // ---- 思考记录表格 ----
        Text(
            text = "思考记录（主变迭代）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        HistoryHeader()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
        ) {
            itemsIndexed(ui.history) { _, h ->
                HistoryRow(h.depth, h.scoreText, h.npsK, h.timeS)
            }
        }
    }
}

@Composable
private fun TextButtonRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun Banner(text: String, errorContainer: Boolean) {
    Surface(
        color = if (errorContainer) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PvRowView(row: PvRow, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${row.pv}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.scoreText,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = if (row.redScore >= 0 || row.isMate && row.redScore > 0) RED_ADVANTAGE else BLACK_ADVANTAGE,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "深度${row.depth}  NPS ${row.npsK}K  ${"%.1f".format(row.timeS)}s",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = row.movesText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 主变前两着预览箭头 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewArrow(
    layout: com.sojourners.tchess.ui.board.BoardLayout,
    from: com.sojourners.chess.board.BoardPoint?,
    to: com.sojourners.chess.board.BoardPoint?,
) {
    if (from == null || to == null) return
    val x1 = BoardGeometry.centerX(from.x, layout, false)
    val y1 = BoardGeometry.centerY(from.y, layout, false)
    val x2 = BoardGeometry.centerX(to.x, layout, false)
    val y2 = BoardGeometry.centerY(to.y, layout, false)
    val color = Color(0xEE1E88E5)
    val strokeWidth = layout.piece / 12f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth, StrokeCap.Round)
    // 箭头三角
    val angle = kotlin.math.atan2(y2 - y1, x2 - x1)
    val head = layout.piece / 5f
    val spread = 0.5f
    fun pt(a: Float, len: Float) = Offset(x2 - len * kotlin.math.cos(angle + a), y2 - len * kotlin.math.sin(angle + a))
    val path = Path().apply {
        moveTo(x2, y2)
        lineTo(pt(+spread, head).x, pt(+spread, head).y)
        lineTo(pt(-spread, head).x, pt(-spread, head).y)
        close()
    }
    drawPath(path, color)
}

// ---------------------------------------------------------------- 评估柱

/**
 * 垂直评估柱：红方占比自下而上填充（红在下，与盘面方位一致）。
 */
@Composable
private fun EvalBarView(fraction: Float?, evalText: String?) {
    val track = Color(0xFF37474F)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Canvas(
            modifier = Modifier
                .width(18.dp)
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            drawRect(track)
            fraction?.let { f ->
                val h = size.height * f.coerceIn(0.02f, 0.98f)
                drawRect(
                    color = RED_ADVANTAGE,
                    topLeft = Offset(0f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(size.width, h),
                )
            }
        }
        Text(
            text = evalText ?: "·",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------------------------------------------------------- 思考记录表

@Composable
private fun HistoryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        TableCell("深度", 0.22f, MaterialTheme.typography.labelSmall)
        TableCell("分数", 0.22f, MaterialTheme.typography.labelSmall)
        TableCell("NPS(K)", 0.30f, MaterialTheme.typography.labelSmall)
        TableCell("时间(s)", 0.26f, MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistoryRow(depth: Int, score: String, npsK: Long, timeS: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        TableCell("$depth", 0.22f, MaterialTheme.typography.bodySmall)
        TableCell(score, 0.22f, MaterialTheme.typography.bodySmall)
        TableCell("$npsK", 0.30f, MaterialTheme.typography.bodySmall)
        TableCell("%.1f".format(timeS), 0.26f, MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(text: String, weight: Float, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = text,
        style = style,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(weight),
    )
}
