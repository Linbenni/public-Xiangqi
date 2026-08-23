package com.sojourners.tchess.ui.analysis

/**
 * 对弈页 → 分析页 的局面交接（"分析此局面"入口）。
 * 简单进程内单槽：后写覆盖，读后即清。
 */
object AnalysisHandoff {

    data class PositionSnapshot(
        val fen: String,
        val moves: List<String>,
        val redToGo: Boolean,
    )

    @Volatile private var pending: PositionSnapshot? = null

    fun offer(snapshot: PositionSnapshot) {
        pending = snapshot
    }

    fun poll(): PositionSnapshot? {
        val p = pending
        pending = null
        return p
    }
}
