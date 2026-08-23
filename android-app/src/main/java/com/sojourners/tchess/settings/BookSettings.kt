package com.sojourners.tchess.settings

import com.sojourners.chess.openbook.MoveRule

/**
 * 开局库设置快照（M4，纯 JVM 可测）。
 * 字段与 core AppConfig 的开局库部分一一对应；挂库查询由 core Engine/OpenBookManager 执行。
 */
data class BookSettings(
    /** 挂库总开关：关闭后对弈/分析均不查库（core Engine.bookSwitch） */
    val bookSwitch: Boolean = false,
    /** 云端开局库（chessdb.cn） */
    val useCloudBook: Boolean = false,
    /** 本地库优先于云库返回 */
    val localBookFirst: Boolean = false,
    /** 库内着法选择规则 */
    val moveRule: MoveRule = MoveRule.BEST_SCORE,
    /** 超过该回合数后脱离开局库（仅引擎） */
    val offManualSteps: Int = DEFAULT_OFF_MANUAL_STEPS,
    /** 云库只取终局（必胜/必败/必和）着法 */
    val onlyCloudFinalPhase: Boolean = false,
    /** 云库查询超时毫秒 */
    val cloudBookTimeout: Int = DEFAULT_CLOUD_TIMEOUT_MS,
) {
    fun clamp(): BookSettings = copy(
        offManualSteps = offManualSteps.coerceIn(MIN_OFF_MANUAL_STEPS, MAX_OFF_MANUAL_STEPS),
        cloudBookTimeout = cloudBookTimeout.coerceIn(MIN_CLOUD_TIMEOUT_MS, MAX_CLOUD_TIMEOUT_MS),
    )

    companion object {
        const val MIN_OFF_MANUAL_STEPS = 1
        const val MAX_OFF_MANUAL_STEPS = 30
        const val DEFAULT_OFF_MANUAL_STEPS = 6
        const val MIN_CLOUD_TIMEOUT_MS = 1000
        const val MAX_CLOUD_TIMEOUT_MS = 15000
        const val DEFAULT_CLOUD_TIMEOUT_MS = 5000
    }
}
