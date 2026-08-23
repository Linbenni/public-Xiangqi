package com.sojourners.tchess.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sojourners.tchess.game.Difficulty
import com.sojourners.tchess.game.GameMode
import com.sojourners.tchess.game.GameOverInfo
import com.sojourners.tchess.game.GameOverReason

/**
 * 新局设置：先后手选择 + 引擎棋力。
 */
@Composable
fun NewGameDialog(
    initialMode: GameMode,
    initialDifficulty: Difficulty,
    engineMissing: Boolean,
    onDismiss: () -> Unit,
    onStart: (GameMode, Difficulty) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新局设置") },
        text = {
            Column {
                Text("对局方式", style = MaterialTheme.typography.labelLarge)
                ModeRow("玩家执红（先行）", GameMode.HUMAN_RED, mode, engineMissing) { mode = it }
                ModeRow("玩家执黑（后行）", GameMode.HUMAN_BLACK, mode, engineMissing) { mode = it }
                ModeRow("双人对弈", GameMode.TWO_PLAYERS, mode, false) { mode = it }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 6.dp))
                Text("引擎棋力（每步用时）", style = MaterialTheme.typography.labelLarge)
                DifficultyRow("快棋 · 1 秒", Difficulty.FAST, difficulty, enabled = mode != GameMode.TWO_PLAYERS) { difficulty = it }
                DifficultyRow("均衡 · 3 秒", Difficulty.NORMAL, difficulty, enabled = mode != GameMode.TWO_PLAYERS) { difficulty = it }
                DifficultyRow("深思 · 8 秒", Difficulty.STRONG, difficulty, enabled = mode != GameMode.TWO_PLAYERS) { difficulty = it }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(mode, difficulty) }) {
                Text("开始对局")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ModeRow(label: String, value: GameMode, current: GameMode, disabled: Boolean, onSelect: (GameMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = current == value,
            onClick = if (disabled) null else ({ onSelect(value) }),
            enabled = !disabled,
        )
        Text(if (disabled) "$label（不可用：缺引擎）" else label)
    }
}

@Composable
private fun DifficultyRow(label: String, value: Difficulty, current: Difficulty, enabled: Boolean, onSelect: (Difficulty) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = current == value,
            onClick = if (enabled) ({ onSelect(value) }) else null,
            enabled = enabled,
        )
        Text(label)
    }
}

/**
 * 终局结果。
 */
@Composable
fun ResultDialog(
    gameOver: GameOverInfo,
    mode: GameMode?,
    onAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val headline = when {
        gameOver.reason == GameOverReason.RESIGN -> if (gameOver.redWin) "红方胜（黑方认输）" else "黑方胜（红方认输）"
        else -> if (gameOver.redWin) "红方胜" else "黑方胜"
    }
    val detail = when (gameOver.reason) {
        GameOverReason.MATE -> "绝杀"
        GameOverReason.STALEMATE -> "困毙，无子可动"
        GameOverReason.RESIGN -> ""
    } + when (mode) {
        GameMode.HUMAN_RED, GameMode.HUMAN_BLACK ->
            if ((mode == GameMode.HUMAN_RED) == gameOver.redWin) "　恭喜，你赢了！" else "　你输了，再接再厉！"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(headline) },
        text = { Text(detail.ifBlank { "对局结束" }) },
        confirmButton = {
            Button(onClick = onAgain) { Text("再来一局") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("查看棋盘") }
        },
    )
}
