package com.sojourners.tchess.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sojourners.chess.board.MoveStep
import com.sojourners.tchess.game.Difficulty
import com.sojourners.tchess.game.GameMode
import com.sojourners.tchess.ui.board.BoardGeometry
import com.sojourners.tchess.ui.board.drawCheckRing
import com.sojourners.tchess.ui.board.drawXiangqiBoard
import com.sojourners.tchess.ui.board.rememberBoardAssets

/**
 * M2 主界面：顶部操作栏 + Canvas 棋盘 + 状态栏；新局/终局对话框。
 */
@Composable
fun GameScreen(vm: GameViewModel) {
    val ui by vm.ui.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }

    // 首次进入自动弹出新局设置
    LaunchedEffect(Unit) {
        if (!vm.ui.value.started) showNewGameDialog = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TCHESS",
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                OutlinedButton(onClick = { showNewGameDialog = true }, enabled = ui.anim == null) {
                    Text("新局")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.undo() },
                    enabled = ui.started && ui.gameOver == null && vm.logic.moves.isNotEmpty() && ui.anim == null,
                ) {
                    Text("悔棋")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.resign() },
                    enabled = ui.started && ui.gameOver == null,
                ) {
                    Text("认输")
                }
            }
        }

        // 引擎缺失提示（仍可双人对弈）
        if (ui.engineMissing) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "未找到内置引擎 libpikafish.so，仅可双人对弈",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 棋盘
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            if (maxWidthPx > 0f && maxHeightPx > 0f) {
                val layout = BoardGeometry.fit(maxWidthPx - 8f, maxHeightPx - 8f)
                val assets = rememberBoardAssets()
                if (assets != null) {
                    // 走子动画进度
                    val currentAnim = ui.anim
                    val fraction = remember(currentAnim?.id) {
                        Animatable(if (currentAnim == null) 1f else 0f)
                    }
                    LaunchedEffect(currentAnim?.id) {
                        if (currentAnim != null) {
                            fraction.animateTo(1f, tween(durationMillis = 150, easing = LinearOutSlowInEasing))
                            vm.onAnimFinished(currentAnim.id)
                        } else {
                            fraction.snapTo(1f)
                        }
                    }

                    val animStep: MoveStep? = currentAnim?.let {
                        MoveStep(
                            com.sojourners.chess.board.BoardPoint(it.fromCol, it.fromRow),
                            com.sojourners.chess.board.BoardPoint(it.toCol, it.toRow),
                        )
                    }
                    val reversed = ui.mode == GameMode.HUMAN_BLACK

                    Canvas(
                        modifier = Modifier
                            .size(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() })
                            .pointerInput(ui.started, ui.thinking) {
                                detectTapGestures { offset ->
                                    val cell = BoardGeometry.cellAt(offset.x, offset.y, layout, reversed)
                                    vm.onCellTap(cell?.x, cell?.y)
                                }
                            },
                    ) {
                        drawXiangqiBoard(
                            layout = layout,
                            board = vm.logic.board,
                            assets = assets,
                            reversed = reversed,
                            selected = ui.selected,
                            targets = ui.legalTargets,
                            captureTargets = ui.legalTargets
                                .filter { vm.logic.pieceAt(it.x, it.y) != ' ' }
                                .map { it.y * 9 + it.x }
                                .toSet(),
                            lastMove = ui.lastMove,
                            anim = animStep.takeIf { fraction.value < 1f },
                            animFraction = fraction.value,
                        )
                        if (ui.inCheck && ui.gameOver == null) {
                            drawCheckRing(layout, vm.logic.board, ui.redToGo, reversed)
                        }
                    }
                }
            }
        }

        // 底部状态栏
        Surface(shadowElevation = 4.dp) {
            Text(
                text = statusText(vm, ui),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showNewGameDialog) {
        NewGameDialog(
            initialMode = ui.mode ?: GameMode.HUMAN_RED,
            initialDifficulty = ui.difficulty,
            engineMissing = ui.engineMissing,
            onDismiss = { showNewGameDialog = false },
            onStart = { mode, difficulty ->
                showNewGameDialog = false
                vm.newGame(mode, difficulty)
            },
        )
    }

    // 终局对话框（等动画播完再弹）
    val gameOver = ui.gameOver
    if (gameOver != null && ui.anim == null) {
        ResultDialog(
            gameOver = gameOver,
            mode = ui.mode,
            onAgain = { showNewGameDialog = true },
            onDismiss = { /* 保持结果展示，用户可点新局 */ },
        )
    }
}

private fun statusText(vm: GameViewModel, ui: UiState): String {
    if (!ui.started) return "点击「新局」开始对弈"
    val sb = StringBuilder()
    sb.append(if (ui.redToGo) "红方行棋" else "黑方行棋")
    if (ui.thinking) sb.append(" · 引擎思考中…")
    if (ui.inCheck && ui.gameOver == null) sb.append(" · 将军！")
    ui.lastMoveText?.let { sb.append(" · 上一步：").append(it) }
    return sb.toString()
}
