package com.sojourners.tchess.ui.manual

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.sojourners.chess.manual.ChessManual
import com.sojourners.chess.model.ManualRecord
import com.sojourners.chess.util.XiangqiUtils
import com.sojourners.tchess.game.GameLogic
import com.sojourners.tchess.manual.ManualFormats
import com.sojourners.tchess.manual.ManualNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** 着法列表一行 */
data class MoveRow(
    val index: Int,
    val label: String,
    val hasVariations: Boolean,
    val isCurrent: Boolean,
)

/** 当前节点的变着选项 */
data class BranchOption(
    val childIndex: Int,
    val label: String,
    val selected: Boolean,
)

data class ManualUiState(
    val loaded: Boolean = false,
    /** 来源文件名（导入后显示） */
    val fileName: String? = null,
    /** 赛事信息 */
    val title: String? = null,
    val infoLine: String? = null,
    /** 当前局面 FEN（与桌面对照抽查用） */
    val currentFen: String = "",
    /** 行棋方红？ */
    val redToGo: Boolean = true,
    /** 着法列表（含「开始局面」头） */
    val moves: List<MoveRow> = emptyList(),
    /** 当前节点变着 */
    val branches: List<BranchOption> = emptyList(),
    /** 当前节点批注 */
    val remark: String? = null,
    /** 复盘播放中 */
    val playing: Boolean = false,
    /** 操作提示（打开失败/导出成功等） */
    val message: String? = null,
)

/**
 * M4 棋谱浏览：SAF 导入（xqf/pgn/cbr/txq）、着法列表、前进/后退/开局/终局、点击跳转、
 * 上变/下变、变着切换、复盘自动播放；导出与分享复用 core 的保存实现。
 * 棋盘状态经 [GameLogic] 重放，保证与对弈页同一套几何/规则。
 */
class ManualViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** 复盘每着间隔（桌面播放默认 1 秒） */
        private const val PLAY_INTERVAL_MS = 1000L
        private const val IMPORT_DIR = "manual_import"
        private const val EXPORT_DIR = "manual_export"
        private const val SHARE_DIR = "manual_share"
    }

    val navigator = ManualNavigator()

    /** 当前重放到的局面（盘面绘制 / FEN 对照用） */
    val logic = GameLogic()

    private val appCtx = app as com.sojourners.tchess.TchessApp
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    /** 原始棋谱元数据（导出时回填） */
    private var sourceMeta: ChessManual? = null
    private var lastImportName: String? = null

    private var playRunnable: Runnable? = null

    private val _ui = MutableStateFlow(ManualUiState())
    val ui: StateFlow<ManualUiState> = _ui.asStateFlow()

    // ---------------------------------------------------------------- 导入

    fun importFromUri(uri: Uri) {
        pause()
        io.execute {
            val resolver = appCtx.contentResolver
            val displayName = queryDisplayName(resolver, uri) ?: "manual.pgn"
            try {
                val dir = File(appCtx.cacheDir, IMPORT_DIR).apply { mkdirs() }
                val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val tmp = File(dir, safeName)
                resolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("无法打开所选文件")

                val ext = ManualFormats.extensionOf(safeName)
                val service = ManualFormats.serviceFor(ext)
                if (service == null) {
                    main.post { notify("不支持的格式：${ext ?: "未知"}（支持 ${ManualFormats.IMPORT_EXTENSIONS.joinToString("/")}）") }
                    return@execute
                }
                val cm = service.openChessManual(tmp)
                tmp.delete()
                if (cm == null || cm.head == null) {
                    main.post { notify("棋谱解析失败：文件可能损坏或不是受支持的编码") }
                    return@execute
                }
                main.post { applyOpened(cm, safeName) }
            } catch (e: Exception) {
                main.post { notify("导入失败：${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOpened(cm: ChessManual, fileName: String) {
        // 校验起始局面
        val fen = cm.fenCode?.takeIf { it.isNotBlank() } ?: GameLogic.INITIAL_FEN
        val tmp = Array(10) { CharArray(9) { ' ' } }
        try {
            XiangqiUtils.fenToBoard(tmp, fen)
        } catch (_: Exception) {
            notify("棋谱起始 FEN 无法解析")
            return
        }
        val err = XiangqiUtils.getChessBoardValidationError(tmp)
        if (err != null) {
            notify("棋谱起始局面非法（$err）")
            return
        }

        navigator.open(cm)
        sourceMeta = cm
        lastImportName = fileName
        replayToCurrent()
        publish(message = null)
    }

    private fun notify(msg: String) {
        _ui.update { it.copy(message = msg) }
    }

    // ---------------------------------------------------------------- 棋盘重放

    private fun startRed(): Boolean {
        val parts = navigator.fenCode.split(" ")
        return parts.size <= 1 || !parts[1].equals("b", ignoreCase = true)
    }

    /** 从起始局面重放到当前导航位置 */
    private fun replayToCurrent(): String? {
        val tmp = Array(10) { CharArray(9) { ' ' } }
        XiangqiUtils.fenToBoard(tmp, navigator.fenCode.ifBlank { GameLogic.INITIAL_FEN })
        logic.loadPosition(tmp, startRed())
        for (mv in navigator.moveList()) {
            if (!logic.applyEngineMove(mv)) {
                return mv
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 导航操作

    private fun navigate(step: () -> Boolean) {
        pause()
        if (!navigator.isOpen) return
        if (step()) {
            replayToCurrent()
            publish()
        }
    }

    fun toStart() = navigate { navigator.toStart() }
    fun back() = navigate { navigator.back() }
    fun forward() = navigate { navigator.forward() }
    fun toEnd() = navigate { navigator.toEnd() }
    fun jumpTo(index: Int) = navigate { navigator.jumpTo(index) }
    fun prevBranch() = navigate { navigator.prevBranchJump() }
    fun nextBranch() = navigate { navigator.nextBranchJump() }

    fun switchBranch(childIndex: Int) {
        pause()
        if (!navigator.switchBranch(childIndex)) return
        replayToCurrent()
        publish()
    }

    // ---------------------------------------------------------------- 复盘播放

    fun togglePlay() {
        if (_ui.value.playing) {
            pause()
            publish()
            return
        }
        if (!navigator.isOpen || navigator.position >= navigator.size - 1) return
        val runnable = object : Runnable {
            override fun run() {
                if (!navigator.forward()) {
                    pause()
                    publish()
                    return
                }
                replayToCurrent()
                publish()
                if (_ui.value.playing && navigator.position < navigator.size - 1) {
                    main.postDelayed(this, PLAY_INTERVAL_MS)
                } else {
                    pause()
                    publish()
                }
            }
        }
        playRunnable = runnable
        _ui.update { it.copy(playing = true) }
        main.postDelayed(runnable, PLAY_INTERVAL_MS)
    }

    /** 停止自动播放（导航操作/切页时调用） */
    fun pause() {
        playRunnable?.let { main.removeCallbacks(it) }
        playRunnable = null
        if (_ui.value.playing) {
            _ui.update { it.copy(playing = false) }
        }
    }

    // ---------------------------------------------------------------- 导出 / 分享

    /** 另存为（SAF CreateDocument 目标）：先写缓存文件再拷贝到目标流 */
    fun exportToUri(uri: Uri, extension: String) {
        io.execute {
            try {
                val service = ManualFormats.serviceFor(extension)
                    ?: throw IllegalArgumentException("不支持的保存格式 .$extension")
                if (!navigator.isOpen) throw IllegalStateException("尚未打开棋谱")
                val cm = buildExportableManual()
                val dir = File(appCtx.cacheDir, EXPORT_DIR).apply { mkdirs() }
                val tmp = File(dir, "export.$extension")
                service.saveChessManual(cm, tmp)
                appCtx.contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("无法写入所选位置")
                tmp.delete()
                main.post { notify("已保存 .$extension") }
            } catch (e: Exception) {
                main.post { notify("导出失败：${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    /** 生成分享文件（pgn），返回可供 FileProvider 使用的路径 */
    fun prepareShareFile(onReady: (File) -> Unit, onError: (String) -> Unit) {
        io.execute {
            try {
                if (!navigator.isOpen) throw IllegalStateException("尚未打开棋谱")
                val cm = buildExportableManual()
                val dir = File(appCtx.cacheDir, SHARE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val base = (sourceMeta?.name ?: lastImportName?.substringBeforeLast('.') ?: "manual")
                    .ifBlank { "manual" }
                val f = File(dir, "$base.pgn")
                ManualFormats.serviceFor("pgn")!!.saveChessManual(cm, f)
                main.post { onReady(f) }
            } catch (e: Exception) {
                main.post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun buildExportableManual(): ChessManual {
        val src = sourceMeta
        val cm = ChessManual()
        cm.fenCode = navigator.fenCode
        cm.head = navigator.nodeAt(0)
        cm.name = src?.name
        cm.date = src?.date
        cm.city = src?.city
        cm.red = src?.red
        cm.black = src?.black
        return cm
    }

    // ---------------------------------------------------------------- 绘制辅助

    /** 当前线的最后一着（盘面高亮用） */
    fun lastMoveStep(): com.sojourners.chess.board.MoveStep? {
        val mv = logic.moves.lastOrNull() ?: return null
        val p = logic.parseEngineMove(mv) ?: return null
        return com.sojourners.chess.board.MoveStep(
            com.sojourners.chess.board.BoardPoint(p[0], p[1]),
            com.sojourners.chess.board.BoardPoint(p[2], p[3]),
        )
    }

    fun showMessage(msg: String) {
        _ui.update { it.copy(message = msg) }
    }

    // ---------------------------------------------------------------- 发布状态

    private fun publish(message: String? = _ui.value.message) {
        val n = navigator
        if (!n.isOpen) {
            _ui.update { it.copy(loaded = false, message = message) }
            return
        }
        val rows = ArrayList<MoveRow>(n.size)
        for (i in 0 until n.size) {
            val node = n.nodeAt(i)!!
            val label = if (i == 0) "开始" else (node.cnMove?.takeIf { it.isNotBlank() } ?: node.move ?: "?")
            rows.add(
                MoveRow(
                    index = i,
                    label = label,
                    hasVariations = node.list.size > 1,
                    isCurrent = i == n.position,
                ),
            )
        }
        val cur = n.currentNode()
        val branches = cur?.list?.mapIndexed { idx, child ->
            BranchOption(
                childIndex = idx,
                label = child.cnMove?.takeIf { it.isNotBlank() } ?: child.move ?: "变${idx + 1}",
                selected = idx == cur.next,
            )
        } ?: emptyList()

        val meta = sourceMeta
        val info = listOfNotNull(meta?.red?.let { "红: $it" }, meta?.black?.let { "黑: $it" })
            .joinToString("   ")
            .ifEmpty { null }

        _ui.update {
            it.copy(
                loaded = true,
                fileName = lastImportName,
                title = meta?.name,
                infoLine = info,
                currentFen = logic.currentFen(),
                redToGo = logic.redToGo,
                moves = rows,
                branches = branches,
                remark = cur?.remark?.takeIf { r -> r.isNotBlank() },
                message = message,
            )
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    override fun onCleared() {
        pause()
        io.shutdown()
        super.onCleared()
    }
}
