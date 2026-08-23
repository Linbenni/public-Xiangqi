package com.sojourners.tchess.ui.manual

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sojourners.chess.board.MoveStep
import com.sojourners.tchess.ui.board.BoardGeometry
import com.sojourners.tchess.ui.board.drawCheckRing
import com.sojourners.tchess.ui.board.drawXiangqiBoard
import com.sojourners.tchess.ui.board.rememberBoardAssets
import java.io.File

/**
 * M4 棋谱浏览页：SAF 导入/导出 + 盘面 + 着法列表（点击跳转）+ 变着切换 +
 * 前进/后退/开局/终局/上变/下变 + 复盘自动播放 + FileProvider 分享。
 */
@Composable
fun ManualScreen(vm: ManualViewModel) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFromUri)
    }
    val exportPgnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-chess-pgn"),
    ) { uri -> uri?.let { vm.exportToUri(it, "pgn") } }
    val exportTxqLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { vm.exportToUri(it, "txq") } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // ---- 操作行 ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                importLauncher.launch(arrayOf("*/*"))
            }) { Text("打开") }
            OutlinedButton(
                onClick = { exportPgnLauncher.launch(suggestName(ui.fileName, "pgn")) },
                enabled = ui.loaded,
            ) { Text("存PGN") }
            OutlinedButton(
                onClick = { exportTxqLauncher.launch(suggestName(ui.fileName, "txq")) },
                enabled = ui.loaded,
            ) { Text("存TXQ") }
            OutlinedButton(
                onClick = {
                    vm.prepareShareFile(
                        onReady = { f -> shareManual(context, f) },
                        onError = { msg -> vm.showMessage(msg) },
                    )
                },
                enabled = ui.loaded,
            ) { Text("分享") }
        }

        ui.message?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).clickable { vm.dismissMessage() })
            }
        }

        if (!ui.loaded) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("尚未打开棋谱", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(6.dp))
                Text(
                    "支持 ${com.sojourners.tchess.manual.ManualFormats.IMPORT_EXTENSIONS.joinToString(" / ") { ".$it" }} 格式，\n与桌面版生成的文件完全兼容",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        // ---- 赛事信息行 ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = listOfNotNull(ui.title ?: ui.fileName, ui.infoLine).joinToString("   "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }

        // ---- 盘面 ----
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
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
                            lastMove = vm.lastMoveStep(),
                            anim = null,
                            animFraction = 1f,
                        )
                        if (vm.logic.sideToMoveInCheck()) {
                            drawCheckRing(layout, vm.logic.board, vm.logic.redToGo, reversed = false)
                        }
                    }
                }
            }
        }

        // ---- 导航按钮行 ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            NavBtn("开局", enabled = ui.loaded) { vm.toStart() }
            NavBtn("上变", enabled = ui.loaded) { vm.prevBranch() }
            NavBtn("◀", enabled = ui.loaded && !ui.playing) { vm.back() }
            NavBtn(if (ui.playing) "⏸" else "▶ 复盘", enabled = ui.loaded) { vm.togglePlay() }
            NavBtn("▶", enabled = ui.loaded && !ui.playing) { vm.forward() }
            NavBtn("下变", enabled = ui.loaded) { vm.nextBranch() }
            NavBtn("终局", enabled = ui.loaded) { vm.toEnd() }
        }

        // ---- 变着切换行 ----
        if (ui.branches.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                itemsIndexed(ui.branches) { _, b ->
                    BranchChip(label = b.label, selected = b.selected) { vm.switchBranch(b.childIndex) }
                }
            }
        }

        // ---- 着法列表 ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            items(ui.moves) { m ->
                MoveChip(row = m, onClick = { vm.jumpTo(m.index) })
            }
        }

        // ---- 批注 + FEN 行 ----
        ui.remark?.let { remark ->
            Text(
                text = "批注：$remark",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = listOfNotNull(if (ui.redToGo) "红先行" else "黑先行", shortFen(ui.currentFen)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val cmgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cmgr.setPrimaryClip(ClipData.newPlainText("FEN", ui.currentFen))
                copied = true
            }) { Text(if (copied) "已复制" else "复制FEN") }
        }
    }
}

private fun suggestName(fileName: String?, ext: String): String {
    val base = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "manual"
    return "$base.$ext"
}

private fun shortFen(fen: String): String =
    fen.split(" ").firstOrNull() ?: fen

@Composable
private fun NavBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
        Text(label)
    }
}

@Composable
private fun MoveChip(row: MoveRow, onClick: () -> Unit) {
    val bg = if (row.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = (if (row.hasVariations) "${row.index}◆" else row.index.toString()) + " " + row.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BranchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .border(width = if (selected) 2.dp else 1.dp, color = border, shape = RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun shareManual(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享棋谱"))
}
