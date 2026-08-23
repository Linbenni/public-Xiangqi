package com.sojourners.tchess.settings

import com.sojourners.chess.enginee.Engine

/**
 * 手机性能档位（ANDROID_PLAN.md M3）。
 * 应用档位 = 一次写入建议的 Threads / Hash（之后可在引擎参数里微调）；
 * 全力档在设备发热时由界面提示降档（见 system.ThermalMonitor）。
 */
enum class PerfProfile(val label: String, val threads: Int, val hashMB: Int, val desc: String) {
    POWER_SAVER("省电", 1, 32, "单线程，发热最低"),
    BALANCED("均衡", 2, 64, "日常推荐"),
    FULL("全力", 4, 128, "最强棋力，发热明显");

    companion object {
        fun of(name: String?): PerfProfile =
            entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

/**
 * 分析时间控制策略（与桌面 TimeSettingController / core Engine.AnalysisModel 一一对应）。
 * 对弈难度仍使用固定时间制（M2 行为），此设置作用于分析模式。
 */
enum class TimeControl(val label: String, val unitHint: String) {
    FIXED_TIME("固定时间", "毫秒"),
    FIXED_STEPS("固定深度", "层"),
    FIXED_NODES("固定节点", "个"),
    INFINITE("无限分析", "手动停止");

    fun toEngineModel(): Engine.AnalysisModel = when (this) {
        FIXED_TIME -> Engine.AnalysisModel.FIXED_TIME
        FIXED_STEPS -> Engine.AnalysisModel.FIXED_STEPS
        FIXED_NODES -> Engine.AnalysisModel.FIXED_NODES
        INFINITE -> Engine.AnalysisModel.INFINITE
    }

    companion object {
        fun of(name: String?): TimeControl =
            entries.firstOrNull { it.name == name } ?: FIXED_TIME
    }
}

/**
 * 引擎相关设置快照（M3：引擎管理页 v1 + 时间控制页的数据模型，纯 JVM 可测）。
 */
data class EngineSettings(
    /** 引擎总开关：关闭后仅双人模式 */
    val engineEnabled: Boolean = true,
    val threads: Int = DEFAULT_THREADS,
    val hashMB: Int = DEFAULT_HASH_MB,
    /** MultiPV 搜索广度（1 = 仅主变） */
    val multiPV: Int = DEFAULT_MULTI_PV,
    val timeControl: TimeControl = TimeControl.FIXED_TIME,
    /** 固定时间(ms)/深度(层)/节点(个) 的值；INFINITE 时忽略 */
    val timeValue: Long = DEFAULT_TIME_VALUE,
    val perfProfile: PerfProfile = PerfProfile.BALANCED,
) {
    /** 应用到 core 引擎的待生效参数（下次 go 前统一下发，与桌面 configureEngineForSearch 一致） */
    fun clamp(): EngineSettings = copy(
        threads = threads.coerceIn(MIN_THREADS, MAX_THREADS),
        hashMB = hashMB.coerceIn(MIN_HASH_MB, MAX_HASH_MB),
        multiPV = multiPV.coerceIn(1, MAX_MULTI_PV),
        timeValue = timeValue.coerceAtLeast(1L),
    )

    companion object {
        const val MIN_THREADS = 1
        const val MAX_THREADS = 4
        const val MIN_HASH_MB = 16
        const val MAX_HASH_MB = 256
        const val MAX_MULTI_PV = 5
        const val DEFAULT_THREADS = 2
        const val DEFAULT_HASH_MB = 64
        const val DEFAULT_MULTI_PV = 3
        const val DEFAULT_TIME_VALUE = 5000L
    }
}
