package com.sojourners.tchess.ui.game

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.sojourners.chess.board.BoardPoint
import com.sojourners.chess.board.MoveStep
import com.sojourners.chess.enginee.Engine
import com.sojourners.chess.model.BookData
import com.sojourners.chess.model.ThinkData
import com.sojourners.tchess.TchessApp
import com.sojourners.tchess.engine.EngineConsumer
import com.sojourners.tchess.engine.EngineSession
import com.sojourners.tchess.game.Difficulty
import com.sojourners.tchess.game.GameLogic
import com.sojourners.tchess.game.GameMode
import com.sojourners.tchess.game.GameOverInfo
import com.sojourners.tchess.game.GameOverReason
import com.sojourners.tchess.game.MoveOutcome
import com.sojourners.tchess.game.PendingAnim
import com.sojourners.tchess.game.SoundCue
import com.sojourners.tchess.sound.SoundManager
import com.sojourners.tchess.ui.analysis.AnalysisHandoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class UiState(
    val started: Boolean = false,
    val mode: GameMode? = null,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val redToGo: Boolean = true,
    val thinking: Boolean = false,
    val selected: BoardPoint? = null,
    val legalTargets: List<BoardPoint> = emptyList(),
    val lastMove: MoveStep? = null,
    val anim: PendingAnim? = null,
    val gameOver: GameOverInfo? = null,
    val engineMissing: Boolean = false,
    /** 行棋方正被将军（棋盘画警示环） */
    val inCheck: Boolean = false,
    /** 递增触发棋盘重绘 */
    val boardVersion: Int = 0,
    val lastMoveText: String? = null,
)

/**
 * M2 对弈闭环 + M3 共享引擎：选子/合法着法提示/走子动画、先后手选择、人机双方、
 * 悔棋、新局、认输、胜负判定；引擎经 EngineSession（对弈/分析共用同一进程）对接；
 * 状态落盘支持进程被杀后恢复。
 */
class GameViewModel(app: Application) : AndroidViewModel(app), EngineConsumer {

    companion object {
        private const val STATE_FILE = "tchess_game.json"
    }

    val logic = GameLogic()
    val sound = SoundManager(app)

    private val appCtx = app as TchessApp
    private val session: EngineSession = appCtx.engineSession
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val stateFile = File(app.filesDir, STATE_FILE)

    private var animId = 0L

    /** 动画结束时播放的音效（走子瞬间不响，落定才响，接近实体棋感） */
    private var pendingCue: SoundCue? = null

    /** 因切到分析页等暂停了引擎思考；回到对弈页时续算 */
    private var suspended = false

    private val _ui = MutableStateFlow(UiState(engineMissing = !session.isAvailable))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // ---------------------------------------------------------------- 引擎回调（reader 线程）

    override fun onBestMove(first: String?, second: String?) {
        main.post { onEngineBestMove(first) }
    }

    override fun onThinkDetail(td: ThinkData) {
        // 对弈中不展示思考面板（M3 分析页负责）；保留挂载点
    }

    override fun onBookResults(list: MutableList<BookData>?) {
        // M4：对弈自动挂库时 core 命中库着法会直接经 bestMove 回调走棋，
        // 着法展示与普通走子一致（lastMoveText），无需额外 UI；分析页负责库着法列表。
    }

    init {
        restore()
    }

    // ---------------------------------------------------------------- 对局入口

    fun newGame(mode: GameMode, difficulty: Difficulty) {
        stopEngineSearch()
        suspended = false
        logic.reset()
        pendingCue = null
        _ui.value = UiState(
            started = true,
            mode = mode,
            difficulty = difficulty,
            engineMissing = _ui.value.engineMissing,
            boardVersion = _ui.value.boardVersion + 1,
        )
        persist()
        if (engineToMoveNow()) {
            requestEngineMove()
        }
    }

    // ---------------------------------------------------------------- 触屏交互

    /** 棋盘点击入口；坐标非法（点在盘外）时传 null */
    fun onCellTap(col: Int?, row: Int?) {
        val s = _ui.value
        if (col == null || row == null || !s.started || s.gameOver != null || s.thinking || s.anim != null) return
        if (!humanToMoveNow()) return

        val sel = s.selected
        if (sel != null) {
            if (sel.x == col && sel.y == row) {
                clearSelection()
                return
            }
            // 换选己方其他棋子
            if (logic.hasSidePiece(col, row, logic.redToGo)) {
                selectPiece(col, row)
                return
            }
            val outcome = logic.tryApply(sel.x, sel.y, col, row)
            if (outcome.applied) {
                afterMoveApplied(outcome)
            } else {
                // 非法落点（含送将）：轻微触感提示，保留选中
                sound.play(SoundCue.CLICK)
            }
        } else {
            if (logic.hasSidePiece(col, row, logic.redToGo)) {
                selectPiece(col, row)
            }
        }
    }

    private fun selectPiece(col: Int, row: Int) {
        sound.play(SoundCue.CLICK)
        _ui.update {
            it.copy(
                selected = BoardPoint(col, row),
                legalTargets = logic.legalTargets(col, row),
            )
        }
    }

    private fun clearSelection() {
        _ui.update { it.copy(selected = null, legalTargets = emptyList()) }
    }

    // ---------------------------------------------------------------- 悔棋/认输

    fun undo() {
        val s = _ui.value
        if (!s.started || s.anim != null || logic.moves.isEmpty()) return
        stopEngineSearch()

        val engineParity = engineFirstPlyParity(s.mode)
        var keep = logic.moves.size - 1
        if (engineParity != null) {
            // 撤到轮到人类行棋（至少撤一手）
            while (keep > 0 && keep % 2 == engineParity) keep--
        }
        logic.truncateTo(keep)
        pendingCue = null
        publishBoardState(gameOver = null)
        persist()
        // 边界：撤到引擎开局手之前（如玩家执黑且引擎只走了开局一手），重新请求引擎走子
        if (engineToMoveNow()) {
            requestEngineMove()
        }
    }

    fun resign() {
        val s = _ui.value
        if (!s.started || s.gameOver != null) return
        stopEngineSearch()
        val redWin = when (s.mode) {
            GameMode.TWO_PLAYERS, null -> !logic.redToGo
            GameMode.HUMAN_RED -> false
            GameMode.HUMAN_BLACK -> true
        }
        setGameOver(GameOverInfo(redWin, GameOverReason.RESIGN))
        persist()
    }

    // ---------------------------------------------------------------- 引擎对接

    private fun ensureEngine(): Engine? {
        val e = session.acquire() ?: run {
            _ui.update { it.copy(engineMissing = true) }
            return null
        }
        return e
    }

    private fun requestEngineMove() {
        val e = ensureEngine() ?: return
        _ui.update { it.copy(thinking = true) }
        // 与桌面 configureEngineForSearch 同序：参数 → 搜索策略 → go
        session.applySettings(appCtx.configStore.snapshotSettings())
        e.setAnalysisModel(Engine.AnalysisModel.FIXED_TIME, _ui.value.difficulty.moveTimeMillis)
        // M4 自动挂库：对弈模式允许开局库直接走棋（桌面 engineGo 的 allowBookMove=!分析模式）。
        // 挂库开关/云库/本地库由 core 经 ConfigProvider + OpenBookManager 处理。
        e.analysis(logic.currentFen(), logic.moves, logic.snapshotBoard(), logic.redToGo, true)
    }

    private fun onEngineBestMove(move: String?) {
        val s = _ui.value
        if (!s.started || s.gameOver != null || !s.thinking) return
        _ui.update { it.copy(thinking = false) }
        val p = move?.let { logic.parseEngineMove(it) }
        if (p == null) {
            // 引擎异常回包：极罕见；保持等待，用户可悔棋/认输
            return
        }
        val outcome = logic.tryApply(p[0], p[1], p[2], p[3])
        if (outcome.applied) {
            afterMoveApplied(outcome)
        }
    }

    private fun stopEngineSearch() {
        session.stopSearch()
        _ui.update { it.copy(thinking = false) }
    }

    // ---------------------------------------------------------------- 走子后处理

    private fun afterMoveApplied(outcome: MoveOutcome) {
        val p = logic.parseEngineMove(outcome.engineMove!!)!!
        clearSelection()
        pendingCue = outcome.cue
        _ui.update {
            it.copy(
                redToGo = logic.redToGo,
                lastMove = MoveStep(BoardPoint(p[0], p[1]), BoardPoint(p[2], p[3])),
                anim = PendingAnim(animId++, p[0], p[1], p[2], p[3]),
                boardVersion = it.boardVersion + 1,
                lastMoveText = logic.moveTexts.lastOrNull(),
                inCheck = logic.sideToMoveInCheck(),
            )
        }
        persist()

        if (logic.sideToMoveHasNoLegalMove()) {
            val reason = if (logic.sideToMoveInCheck()) GameOverReason.MATE else GameOverReason.STALEMATE
            setGameOver(GameOverInfo(!logic.redToGo, reason))
            persist()
        } else if (engineToMoveNow()) {
            requestEngineMove()
        }
    }

    /** 动画播完回调（Compose 层调用）：播音效 */
    fun onAnimFinished(id: Long) {
        val anim = _ui.value.anim ?: return
        if (anim.id != id) return
        _ui.update { it.copy(anim = null) }
        pendingCue?.let { sound.play(it) }
        pendingCue = null
    }

    private fun setGameOver(info: GameOverInfo) {
        stopEngineSearch()
        _ui.update { it.copy(gameOver = info) }
    }

    /** 以 logic 当前局面为准刷新界面状态 */
    private fun publishBoardState(gameOver: GameOverInfo?) {
        val lastP = logic.moves.lastOrNull()?.let { logic.parseEngineMove(it) }
        _ui.update {
            it.copy(
                selected = null,
                legalTargets = emptyList(),
                redToGo = logic.redToGo,
                lastMove = lastP?.let { p -> MoveStep(BoardPoint(p[0], p[1]), BoardPoint(p[2], p[3])) },
                gameOver = gameOver,
                thinking = false,
                boardVersion = it.boardVersion + 1,
                lastMoveText = logic.moveTexts.lastOrNull(),
                inCheck = logic.sideToMoveInCheck(),
            )
        }
    }

    // ---------------------------------------------------------------- 状态判定

    /** 当前是否轮到引擎 */
    private fun engineToMoveNow(): Boolean {
        val s = _ui.value
        if (!s.started || s.gameOver != null || s.mode?.vsEngine != true) return false
        if (s.engineMissing || !appCtx.configStore.snapshotSettings().engineEnabled) return false
        return logic.redToGo == enginePlaysRed(s.mode)
    }

    private fun humanToMoveNow(): Boolean {
        val s = _ui.value
        if (s.mode?.vsEngine != true) return true
        return logic.redToGo != enginePlaysRed(s.mode)
    }

    private fun enginePlaysRed(mode: GameMode?): Boolean = mode == GameMode.HUMAN_BLACK

    /** 引擎执先手（红）时其着法在 moves 中的下标奇偶 */
    private fun engineFirstPlyParity(mode: GameMode?): Int? =
        if (mode?.vsEngine != true) null else if (enginePlaysRed(mode)) 0 else 1

    fun hasActiveGame(): Boolean {
        val s = _ui.value
        return s.started && s.gameOver == null
    }

    // ---------------------------------------------------------------- 页面切换（M3 底部导航）

    /** 离开对弈页：停掉思考中的搜索（分析页要独占引擎），回页后自动续算 */
    fun onScreenHidden() {
        if (_ui.value.thinking) {
            session.stopSearch()
            _ui.update { it.copy(thinking = false) }
            suspended = true
        }
        session.unbind(this)
    }

    fun onScreenShown() {
        session.bind(this)
        _ui.update { it.copy(engineMissing = session.engineMissing) }
        if (suspended && engineToMoveNow()) {
            requestEngineMove()
        }
        suspended = false
    }

    // ---------------------------------------------------------------- 导出局面（→ 分析页）

    fun exportForAnalysis(): AnalysisHandoff.PositionSnapshot =
        AnalysisHandoff.PositionSnapshot(
            fen = logic.currentFen(),
            moves = ArrayList(logic.moves),
            redToGo = logic.redToGo,
        )

    // ---------------------------------------------------------------- 持久化恢复

    private fun persist() {
        val s = _ui.value
        val snapshot = JSONObject().apply {
            put("mode", s.mode?.name)
            put("difficulty", s.difficulty.name)
            put("moves", JSONArray(logic.moves))
            s.gameOver?.let {
                put("gameOver", JSONObject().put("redWin", it.redWin).put("reason", it.reason.name))
            } ?: put("gameOver", JSONObject.NULL)
        }.toString()
        io.execute {
            try {
                stateFile.writeText(snapshot, Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
    }

    private fun restore() {
        try {
            if (!stateFile.exists()) return
            val json = JSONObject(stateFile.readText(Charsets.UTF_8))
            val modeName = json.optString("mode", "")
            if (modeName.isEmpty()) return
            val mode = runCatching { GameMode.valueOf(modeName) }.getOrNull() ?: return
            val difficulty = runCatching { Difficulty.valueOf(json.optString("difficulty", Difficulty.NORMAL.name)) }
                .getOrDefault(Difficulty.NORMAL)
            val movesJson = json.optJSONArray("moves") ?: return
            val moves = (0 until movesJson.length()).map { movesJson.getString(it) }
            val gameOver = json.optJSONObject("gameOver")?.let {
                GameOverInfo(
                    it.optBoolean("redWin", true),
                    runCatching { GameOverReason.valueOf(it.optString("reason")) }
                        .getOrDefault(GameOverReason.RESIGN),
                )
            }

            logic.replayTo(moves)
            _ui.value = UiState(
                started = true,
                mode = mode,
                difficulty = difficulty,
                redToGo = logic.redToGo,
                lastMove = moves.lastOrNull()?.let { mv ->
                    logic.parseEngineMove(mv)?.let { p ->
                        MoveStep(BoardPoint(p[0], p[1]), BoardPoint(p[2], p[3]))
                    }
                },
                gameOver = gameOver,
                engineMissing = _ui.value.engineMissing,
                boardVersion = _ui.value.boardVersion + 1,
                lastMoveText = logic.moveTexts.lastOrNull(),
                inCheck = logic.sideToMoveInCheck(),
            )
            if (gameOver == null && engineToMoveNow()) {
                requestEngineMove()
            }
        } catch (_: Exception) {
            // 损坏的存档按新安装处理
        }
    }

    override fun onCleared() {
        session.unbind(this)
        io.shutdown()
        sound.release()
        super.onCleared()
    }
}
