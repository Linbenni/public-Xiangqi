package com.sojourners.tchess.analysis

import androidx.annotation.AnyThread
import com.sojourners.chess.model.ThinkData
import java.util.TreeMap

/**
 * MultiPV 变化行（由一条 info 生成的不可变快照）。
 *
 * @param pv 序号（1 = 主变）
 * @param depth 搜索深度
 * @param redScore 红方视角分值（厘兵）；绝杀时为 ±(大值)
 * @param mateIn 非空表示绝杀：值为步数
 * @param scoreText 展示用分数
 * @param npsK/timeS/nodes 思考数据
 * @param movesText 中文着法串（ThinkData.generate 产物，与桌面输出一致）
 * @param firstMoveUci/secondMoveUci 前两着引擎坐标（盘面画箭头用，桌面 setTip 对应物）
 */
data class PvRow(
    val pv: Int,
    val depth: Int,
    val redScore: Int,
    val mateIn: Int?,
    val scoreText: String,
    val npsK: Long,
    val timeS: Double,
    val nodes: Long,
    val movesText: String,
    val firstMoveUci: String?,
    val secondMoveUci: String?,
) {
    val isMate: Boolean get() = mateIn != null
}

/** 一条思考历史记录（主变每次实质更新记一条，用于思考数据表格） */
data class HistoryEntry(
    val depth: Int,
    val scoreText: String,
    val npsK: Long,
    val timeS: Double,
)

/**
 * MultiPV 数据板：复刻桌面 Controller 的 pending → 节流 flush → latest/history 模式。
 * reader 线程只 offer 原始数据；flush 由调用方批量取走并 generate。
 */
class PvBoard {

    private val pending = LinkedHashMap<Int, ThinkData>()
    private val latest = sortedMapOf<Int, PvRow>()
    private val history = ArrayList<HistoryEntry>()

    @AnyThread
    fun offer(td: ThinkData) {
        synchronized(pending) {
            pending[td.getPv() ?: 1] = td
        }
    }

    /**
     * 取走全部待处理数据（按 pv 升序）。generate 由调用方完成后再 [applyGenerated]。
     */
    fun drain(): Map<Int, ThinkData> {
        synchronized(pending) {
            if (pending.isEmpty()) return emptyMap()
            val out = TreeMap(pending)
            pending.clear()
            return out
        }
    }

    /** flush 结果回写（保持 pv 升序快照） */
    @Synchronized
    fun applyGenerated(row: PvRow, recordHistory: Boolean) {
        latest[row.pv] = row
        if (recordHistory) {
            history.add(0, HistoryEntry(row.depth, row.scoreText, row.npsK, row.timeS))
            while (history.size > HISTORY_MAX) history.removeAt(history.size - 1)
        }
    }

    @Synchronized
    fun rows(): List<PvRow> = ArrayList(latest.values)

    @Synchronized
    fun historySnapshot(): List<HistoryEntry> = ArrayList(history)

    @Synchronized
    fun clear() {
        synchronized(pending) { pending.clear() }
        latest.clear()
        history.clear()
    }

    companion object {
        const val HISTORY_MAX = 128
    }
}

/**
 * 对一条 info 生成展示行。内部调用 [ThinkData.generate]（与桌面同一套翻译/文案逻辑，
 * 保证"同一 FEN 与桌面版 MultiPV 输出一致"），并在此之前快照绝杀信息
 * （generate 会把 score 置为 mate 值，事后无法区分）。
 *
 * 分数语义（isReverse=false 时）：title/展示分恒为红方视角；[PvRow.redScore] 同为红方视角。
 */
fun generatePvRow(
    td: ThinkData,
    redGo: Boolean,
    board: Array<CharArray>,
    isReverse: Boolean = false,
): PvRow? {
    val wasMate = td.getScore() == null && td.getMate() != null
    td.generate(redGo, isReverse, board)
    if (td.getValid() != true) return null
    val depth = td.getDepth() ?: return null
    if (td.getDetail().isNullOrEmpty()) return null
    val redScore = td.getRedScore() ?: return null
    val detail = td.getDetail().orEmpty()
    return PvRow(
        pv = td.getPv() ?: 1,
        depth = depth,
        redScore = redScore,
        mateIn = if (wasMate) kotlin.math.abs(td.getMate() ?: 0) else null,
        scoreText = formatScore(redScore, wasMate),
        npsK = (td.getNps() ?: 0L) / 1000L,
        timeS = (td.getTime() ?: 0L) / 1000.0,
        nodes = td.getNodes() ?: 0L,
        movesText = td.getBody().orEmpty(),
        firstMoveUci = detail.getOrNull(0),
        secondMoveUci = detail.getOrNull(1),
    )
}

private fun formatScore(redScore: Int, mate: Boolean): String =
    if (mate) "#${kotlin.math.abs(redScore)}" else String.format("%+d", redScore)

/**
 * 评估柱数学（纯函数）：把红方视角分值映射为 0..1 的红色占比，
 * sigmoid 让 ±300cp 左右进入可视敏感区，两端保留 5% 余量避免完全消失。
 */
object EvalBar {
    private const val SCALE_CP = 400.0

    fun redFraction(redScoreCp: Int): Float {
        val p = 1.0 / (1.0 + Math.pow(10.0, -redScoreCp / SCALE_CP))
        return (0.05 + 0.9 * p).toFloat()
    }

    /** 绝杀时直接给到接近满/空 */
    fun mateFraction(redWins: Boolean): Float = if (redWins) 0.98f else 0.02f
}
