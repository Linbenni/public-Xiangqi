package com.sojourners.tchess.ui.analysis

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.sojourners.chess.board.BoardPoint
import com.sojourners.chess.model.ThinkData
import com.sojourners.chess.util.FenUtils
import com.sojourners.chess.util.XiangqiUtils
import com.sojourners.tchess.TchessApp
import com.sojourners.tchess.analysis.EvalBar
import com.sojourners.tchess.analysis.HistoryEntry
import com.sojourners.tchess.analysis.PvBoard
import com.sojourners.tchess.analysis.PvRow
import com.sojourners.tchess.analysis.generatePvRow
import com.sojourners.tchess.engine.EngineConsumer
import com.sojourners.tchess.game.GameLogic
import com.sojourners.tchess.settings.EngineSettings
import com.sojourners.tchess.settings.TimeControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

data class AnalysisUiState(
    /** 输入框内容（可编辑） */
    val fenInput: String = GameLogic.INITIAL_FEN,
    /** 已装载局面的 FEN（与引擎请求一致） */
    val loadedFen: String? = null,
    val redToGo: Boolean = true,
    val running: Boolean = false,
    val rows: List<PvRow> = emptyList(),
    /** 主变（pv1）title 行：深度/PV/分数/NPS/时间 */
    val statusTitle: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val timeStrategy: String = "",
    val message: String? = null,
    /** 引擎不可用原因（二进制缺失 / 设置中关闭），非空时禁用开始 */
    val engineUnavailableReason: String? = null,
    /** 评估柱：红方占比 0..1；null = 无数据 */
    val evalFraction: Float? = null,
    val evalText: String? = null,
    /** 预览箭头（当前选中变化的前两着） */
    val previewFrom: BoardPoint? = null,
    val previewTo: BoardPoint? = null,
    /** 当前选中的变化序号（列表高亮用） */
    val selectedPv: Int = 1,
) {
    val canStart: Boolean get() = loadedFen != null && engineUnavailableReason == null
}

/**
 * M3 分析模式：MultiPV 变化列表、评估柱、思考数据表格 + 时间控制策略。
 * 数据通路与桌面 Controller 一致：reader 线程 offer → 100ms 节流 flush → generate → UI。
 */
class AnalysisViewModel(app: Application) : AndroidViewModel(app), EngineConsumer {

    companion object {
        private const val FLUSH_INTERVAL_MS = 100L
    }

    private val store = (app as TchessApp).configStore
    private val session = (app as TchessApp).engineSession

    /** 分析局面（复用对弈规则类持有棋盘/行棋方） */
    val logic = GameLogic()

    private val main = Handler(Looper.getMainLooper())
    private val pvBoard = PvBoard()
    private val flushScheduled = AtomicBoolean(false)

    @Volatile private var selectedPv: Int = 1

    private var lastHistDepth = -1
    private var lastHistScore: String? = null

    private val _ui = MutableStateFlow(AnalysisUiState())
    val ui: StateFlow<AnalysisUiState> = _ui.asStateFlow()

    init {
        // 初始即装载标准局面，便于直接开算
        loadPositionFrom(GameLogic.INITIAL_FEN)
        refreshEngineAvailability()
    }

    // ---------------------------------------------------------------- 生命周期

    fun onShown() {
        AnalysisHandoff.poll()?.let { snap ->
            logic.replayTo(snap.moves)
            afterPositionLoaded()
            _ui.update {
                it.copy(fenInput = logic.currentFen(), message = "已导入对局局面")
            }
        }
        refreshEngineAvailability()
    }

    fun onHidden() {
        if (_ui.value.running) stopAnalysis()
        session.unbind(this)
    }

    private fun refreshEngineAvailability() {
        val s = store.snapshotSettings()
        val reason = when {
            !s.engineEnabled -> "引擎已在设置中关闭"
            session.engineMissing -> "未找到内置引擎 libpikafish.so"
            else -> null
        }
        _ui.update { it.copy(engineUnavailableReason = reason) }
    }

    // ---------------------------------------------------------------- 局面装载

    fun onFenInputChanged(text: String) {
        _ui.update { it.copy(fenInput = text, message = null) }
    }

    fun loadFen() {
        loadPositionFrom(_ui.value.fenInput)
    }

    fun resetToInitial() {
        _ui.update { it.copy(fenInput = GameLogic.INITIAL_FEN) }
        loadPositionFrom(GameLogic.INITIAL_FEN)
    }

    private fun loadPositionFrom(fenRaw: String) {
        val fen = fenRaw.trim()
        if (fen.isEmpty()) {
            _ui.update { it.copy(message = "FEN 为空") }
            return
        }
        return try {
            val parts = fen.split(" ")
            val redToGo = parts.size <= 1 || !parts[1].equals("b", ignoreCase = true)
            val tmp = Array(10) { CharArray(9) { ' ' } }
            XiangqiUtils.fenToBoard(tmp, fen)
            val error = XiangqiUtils.getChessBoardValidationError(tmp)
            if (error != null) {
                _ui.update { it.copy(message = "局面非法（$error）") }
                return
            }
            logic.loadPosition(tmp, redToGo)
            val normalized = FenUtils.fenCode(logic.snapshotBoard(), logic.redToGo)
            afterPositionLoaded()
            _ui.update { it.copy(fenInput = normalized) }
        } catch (e: Exception) {
            _ui.update { it.copy(message = "FEN 无法解析") }
        }
    }

    private fun afterPositionLoaded() {
        stopAnalysis()
        selectedPv = 1
        pvBoard.clear()
        lastHistDepth = -1
        lastHistScore = null
        _ui.update {
            it.copy(
                loadedFen = logic.currentFen(),
                redToGo = logic.redToGo,
                running = false,
                rows = emptyList(),
                history = emptyList(),
                statusTitle = null,
                evalFraction = null,
                evalText = null,
                previewFrom = null,
                previewTo = null,
                timeStrategy = "",
                selectedPv = 1,
            )
        }
    }

    // ---------------------------------------------------------------- 开始/停止

    fun startOrStop() {
        if (_ui.value.running) stopAnalysis() else startAnalysis()
    }

    private fun startAnalysis() {
        refreshEngineAvailability()
        if (_ui.value.engineUnavailableReason != null) return
        val e = session.acquire() ?: run { refreshEngineAvailability(); return }
        session.bind(this)

        val s = store.snapshotSettings().clamp()
        session.applySettings(s)
        e.setAnalysisModel(s.timeControl.toEngineModel(), s.timeValue)

        pvBoard.clear()
        _ui.update { it.copy(running = true, timeStrategy = strategyText(s), message = null) }
        e.analysis(logic.currentFen(), logic.moves, logic.snapshotBoard(), logic.redToGo, false)
    }

    private fun stopAnalysis() {
        session.stopSearch()
        _ui.update { it.copy(running = false) }
    }

    private fun strategyText(s: EngineSettings): String = when (s.timeControl) {
        TimeControl.FIXED_TIME -> "固定时间 ${s.timeValue / 1000.0} 秒"
        TimeControl.FIXED_STEPS -> "固定深度 ${s.timeValue} 层"
        TimeControl.FIXED_NODES ->
            if (s.timeValue >= 1000) "固定节点 ${s.timeValue / 1000}K 个" else "固定节点 ${s.timeValue} 个"
        TimeControl.INFINITE -> "无限分析"
    }

    // ---------------------------------------------------------------- 引擎回调（reader 线程）

    override fun onThinkDetail(td: ThinkData) {
        if (!_ui.value.running) return
        pvBoard.offer(td)
        if (flushScheduled.compareAndSet(false, true)) {
            main.postDelayed({ flush() }, FLUSH_INTERVAL_MS)
        }
    }

    // ---------------------------------------------------------------- 节流刷新（主线程）

    private fun flush() {
        flushScheduled.set(false)
        if (!_ui.value.running) {
            pvBoard.drain() // 停止后的残留直接丢弃
            return
        }
        val updates = pvBoard.drain()
        if (updates.isEmpty()) return

        val snapshotBoard = logic.snapshotBoard()
        val redGo = logic.redToGo
        var statusTitle: String? = null
        var mainRow: PvRow? = null
        for ((pv, td) in updates) {
            val row = generatePvRow(td, redGo, snapshotBoard) ?: continue
            // 思考记录只跟踪主变，按深度/分数实质变化去重（一次迭代一行）
            val recordHistory = row.pv == 1 &&
                (row.depth != lastHistDepth || row.scoreText != lastHistScore)
            if (recordHistory) {
                lastHistDepth = row.depth
                lastHistScore = row.scoreText
            }
            pvBoard.applyGenerated(row, recordHistory)
            if (pv == 1) {
                statusTitle = td.getTitle()
                mainRow = row
            }
        }

        _ui.update { cur ->
            cur.copy(
                rows = pvBoard.rows(),
                history = pvBoard.historySnapshot(),
                statusTitle = statusTitle ?: cur.statusTitle,
                evalFraction = mainRow?.let { row ->
                    if (row.isMate) EvalBar.mateFraction(row.redScore > 0) else EvalBar.redFraction(row.redScore)
                } ?: cur.evalFraction,
                evalText = mainRow?.scoreText ?: cur.evalText,
            ).withPreview(selectedPv)
        }
    }

    // ---------------------------------------------------------------- 变化选择 / 箭头预览

    fun selectPv(pv: Int) {
        selectedPv = pv
        _ui.update { it.withPreview(pv).copy(selectedPv = pv) }
    }

    private fun AnalysisUiState.withPreview(pv: Int): AnalysisUiState {
        val row = pvBoard.rows().firstOrNull { it.pv == pv }
        if (row == null) return copy(previewFrom = null, previewTo = null)
        val from = row.firstMoveUci?.let { logic.parseEngineMove(it) }
        val second = from?.let { f -> row.secondMoveUci?.let { m2 -> logic.parseEngineMove(m2) } }
        return copy(
            previewFrom = from?.let { BoardPoint(it[0], it[1]) },
            previewTo = second?.let { BoardPoint(it[2], it[3]) },
        )
    }

    override fun onCleared() {
        session.stopSearch()
        session.unbind(this)
        super.onCleared()
    }
}
