package com.sojourners.tchess.game

/**
 * 对局模式（M2：先后手选择、人机双方、双人）。
 */
enum class GameMode(val vsEngine: Boolean) {
    /** 玩家执红先行 */
    HUMAN_RED(true),

    /** 玩家执黑后行 */
    HUMAN_BLACK(true),

    /** 双人对弈 */
    TWO_PLAYERS(false);
}

/**
 * 引擎棋力档位 = 每步思考时间（固定时间制，与桌面 AnalysisModel.FIXED_TIME 对应）。
 */
enum class Difficulty(val label: String, val moveTimeMillis: Long) {
    FAST("快棋", 1_000L),
    NORMAL("均衡", 3_000L),
    STRONG("深思", 8_000L),
}

/**
 * 走子音效事件（与桌面 SoundPlayer 的 click/move/capture/check/win 一一对应）。
 */
enum class SoundCue { CLICK, MOVE, CAPTURE, CHECK, WIN }

/** 终局原因 */
enum class GameOverReason { MATE, STALEMATE, RESIGN }

data class GameOverInfo(
    val redWin: Boolean,
    val reason: GameOverReason,
)

/**
 * 一次落子的结果。
 * @param applied 是否生效（送将等非法走子返回 false）
 */
class MoveOutcome(
    val applied: Boolean,
    val cue: SoundCue?,
    val engineMove: String?,
    val capturedPiece: Char,
) {
    companion object {
        val REJECTED = MoveOutcome(false, null, null, ' ')
    }
}

/**
 * 待播动画（Compose 层消费）。
 */
data class PendingAnim(
    val id: Long,
    val fromCol: Int,
    val fromRow: Int,
    val toCol: Int,
    val toRow: Int,
)
